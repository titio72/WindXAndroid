package com.aboni.n2kRouter

import com.aboni.marine.link.CommandResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Where the card's table stands against the sensor's. */
enum class Sync {
    /** The sensor's firmware has no correction table. */
    Unavailable,
    /** The sensor's table is being read. */
    Reading,
    /** What is shown is what the sensor reports. */
    InSync,
    /** The user's edits are shown and on their way to the sensor. */
    Pending,
    /** The sensor's table could not be read consistently. */
    OutOfSync,
}

/**
 * What the Correction card shows. [table] is the sensor's table with the user's pending rows laid over it;
 * [pendingTws] are the rows that differ from the sensor's own.
 */
data class CorrectionUi(
    val sync: Sync,
    val table: CorrectionTable?,
    val tableId: Long?,
    val pendingTws: Set<Int>,
    val message: String?,
)

private enum class ReadState { Idle, Reading, Failed }

private const val READ_ATTEMPTS = 3
private const val READ_WAIT_MS = 1_000L
private const val READ_RETRY_MS = 10_000L

/**
 * Keeps the app's view of the sensor's correction table honest. The table is only ever learned by reading it from the
 * sensor; edits are shown at once as pending, sent as the difference between the two tables after [debounceMs], and
 * dropped (with a message) unless the sensor's own table shows them within [confirmMs].
 *
 * [readTable] asks the link to read the table characteristic; the table then arrives in [data].
 */
class CorrectionController(
    private val scope: CoroutineScope,
    private val data: StateFlow<Data?>,
    private val readTable: suspend () -> Unit,
    private val send: suspend (WindCommand) -> CommandResult,
    private val debounceMs: Long = 500,
    private val confirmMs: Long = 3_000,
) {
    private val working = MutableStateFlow<List<CorrectionRow>?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val readState = MutableStateFlow(ReadState.Idle)
    private val flushLock = Mutex()
    private var debounce: Job? = null
    private var readError: String? = null

    val ui: StateFlow<CorrectionUi> = combine(data, working, message, readState) { d, w, m, r -> buildUi(d, w, m, r) }
        .stateIn(scope, SharingStarted.Eagerly, buildUi(data.value, null, null, ReadState.Idle))

    init {
        scope.launch {
            data.map { it?.tableId?.takeIf { id -> id.valid }?.value }
                .distinctUntilChanged()
                .collectLatest { id -> syncTable(id) }
        }
    }

    fun adjust(sel: Selection, delta: Int): Adjusted = edit { adjustRows(it, sel, delta) }

    fun addRow(likeTws: Int): Adjusted = edit { addRowLike(it, likeTws) }

    fun deleteRow(tws: Int): Adjusted = edit { deleteRow(it, tws) }

    /** Sends [rows] as the whole table at once, no debounce: upserts for what differs, deletes for what is missing. */
    fun importRows(rows: List<CorrectionRow>) {
        val problem = validateRows(rows)
            ?: if (working.value == null && confirmedRows() == null) NOT_READ else null
        if (problem != null) {
            message.value = problem
            return
        }
        message.value = null
        debounce?.cancel()
        working.value = rows
        scope.launch { flush() }
    }

    fun setEnabled(on: Boolean) {
        message.value = null
        scope.launch { flushLock.withLock { report(send(WindCommand.TableEnable(on))) } }
    }

    fun clear() {
        debounce?.cancel()
        working.value = null
        message.value = null
        scope.launch { flushLock.withLock { report(send(WindCommand.TableClear)) } }
    }

    private fun report(r: CommandResult) {
        if (!r.ok) message.value = r.text
    }

    // --- editing ---

    private fun edit(op: (List<CorrectionRow>) -> Adjusted): Adjusted {
        val base = working.value ?: confirmedRows() ?: return Adjusted.Blocked(NOT_READ)
        val result = op(base)
        if (result is Adjusted.Ok) {
            message.value = null
            working.value = result.rows
            debounce?.cancel()   // only the wait: a flush already running is a separate job and is never cancelled
            debounce = scope.launch {
                delay(debounceMs)
                scope.launch { flush() }
            }
        }
        return result
    }

    /** The sensor's rows, but only while the table read matches the `table_id` the sensor reports. */
    private fun confirmedRows(): List<CorrectionRow>? {
        val d = data.value ?: return null
        val t = d.table ?: return null
        return t.rows.takeIf { d.tableId.valid && t.id == d.tableId.value }
    }

    private suspend fun flush() = flushLock.withLock {
        val target = working.value ?: return@withLock
        val base = confirmedRows()
        if (base == null) {
            message.value = NOT_READ
            drop(target)
            return@withLock
        }
        val commands = commandsFor(base, target)
        if (commands == null) {
            message.value = "This table can't be sent to the sensor in valid steps"
            drop(target)
            return@withLock
        }
        for (cmd in commands) {
            val r = send(cmd)
            if (!r.ok) {
                message.value = r.text
                drop(target)
                return@withLock
            }
        }
        // A Clear issued meanwhile has dropped the pending rows: not being matched is then expected.
        if (!awaitRows(target) && working.value == target) message.value = "The sensor did not accept the change"
        drop(target)
    }

    /** Forgets the pending rows, unless the user has edited again meanwhile. */
    private fun drop(target: List<CorrectionRow>) {
        if (working.value == target) working.value = null
    }

    /** Waits until the sensor's own table has [target]'s rows; the wait restarts whenever the table changes. */
    private suspend fun awaitRows(target: List<CorrectionRow>): Boolean {
        fun idOf(d: Data) = d.tableId.takeIf { it.valid }?.value
        var seen = data.value?.let(::idOf)
        while (true) {
            val d = withTimeoutOrNull(confirmMs) {
                data.filterNotNull().first { it.table?.rows == target || idOf(it) != seen }
            } ?: return false
            if (d.table?.rows == target) return true
            seen = idOf(d)
        }
    }

    // --- reading ---

    private suspend fun syncTable(id: Long?) {
        if (id == null || data.value?.table?.id == id) {
            readState.value = ReadState.Idle
            return
        }
        readState.value = ReadState.Reading
        // The sensor keeps its table_id across a dropped link, so nothing else would ask again: retry until it works
        // or the table_id changes (which restarts this).
        while (true) {
            if (readOnce(id)) {
                readState.value = ReadState.Idle
                return
            }
            readState.value = ReadState.Failed
            delay(READ_RETRY_MS)
            readState.value = ReadState.Reading
        }
    }

    /** Up to [READ_ATTEMPTS] reads, each given [READ_WAIT_MS] to bring a table that matches [id]. */
    private suspend fun readOnce(id: Long): Boolean {
        repeat(READ_ATTEMPTS) {
            try {
                readTable()
            } catch (e: IOException) {
                readError = e.message ?: "Can't read the table"
                message.value = readError
                return false
            }
            if (withTimeoutOrNull(READ_WAIT_MS) { data.filterNotNull().first { it.table?.id == id } } != null) {
                if (message.value == readError) message.value = null
                return true
            }
        }
        return false
    }

    private fun buildUi(d: Data?, pending: List<CorrectionRow>?, message: String?, read: ReadState): CorrectionUi {
        val id = d?.tableId?.takeIf { it.valid }?.value
        val confirmed = d?.table
        val inSync = id != null && confirmed != null && confirmed.id == id
        val table = when {
            pending != null -> CorrectionTable(confirmed?.enabled ?: false, pending)
            inSync -> confirmed
            else -> null   // a table that is not the one the sensor reports is not the sensor's table
        }
        val differing = pending?.filter { row -> confirmed?.rows?.none { it == row } ?: true }?.map { it.tws }?.toSet()
        val sync = when {
            id == null -> Sync.Unavailable
            pending != null -> Sync.Pending
            inSync -> Sync.InSync
            read == ReadState.Failed -> Sync.OutOfSync
            else -> Sync.Reading
        }
        return CorrectionUi(sync, table, id, differing ?: emptySet(), message)
    }

    private companion object {
        const val NOT_READ = "The table has not been read from the sensor yet"
    }
}

/**
 * The commands that turn [base] into [target], in an order the firmware accepts. The firmware checks every command
 * against the table as it stands, so an upsert that is fine in the final table can be refused while old rows are still
 * there. Upserts go first (a renamed row then never leaves a gap); when none is accepted yet, an old row that is in the
 * way is deleted (rows missing from [target] first), then upserts are tried again. Null if no order works.
 */
private fun commandsFor(base: List<CorrectionRow>, target: List<CorrectionRow>): List<WindCommand>? {
    fun with(rows: List<CorrectionRow>, row: CorrectionRow) = (rows.filter { it.tws != row.tws } + row).sortedBy { it.tws }
    var state = base
    val commands = mutableListOf<WindCommand>()
    var upserts = target.filter { it !in base }
    val gone = base.filter { b -> target.none { it.tws == b.tws } }.toMutableList()
    while (upserts.isNotEmpty()) {
        val next = upserts.firstOrNull { validateRows(with(state, it)) == null }
        if (next != null) {
            state = with(state, next)
            commands += WindCommand.TableUpsert(next)
            upserts = upserts - next
            continue
        }
        val victim = gone.firstOrNull() ?: state.firstOrNull { it !in target } ?: return null
        gone.remove(victim)
        state = state - victim
        commands += WindCommand.TableDelete(victim.tws)
    }
    // Deletes never break the firmware's rules.
    gone.filter { it in state }.forEach { commands += WindCommand.TableDelete(it.tws) }
    return commands
}
