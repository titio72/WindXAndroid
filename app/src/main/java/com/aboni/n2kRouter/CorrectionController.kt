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
        scope.launch { report(send(WindCommand.TableEnable(on))) }
    }

    fun clear() {
        debounce?.cancel()
        working.value = null
        message.value = null
        scope.launch { report(send(WindCommand.TableClear)) }
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
        for (cmd in commandsFor(base, target)) {
            val r = send(cmd)
            if (!r.ok) {
                message.value = r.text
                drop(target)
                return@withLock
            }
        }
        if (!awaitRows(target)) message.value = "The sensor did not accept the change"
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
        repeat(READ_ATTEMPTS) {
            try {
                readTable()
            } catch (e: IOException) {
                readError = e.message ?: "Can't read the table"
                message.value = readError
                readState.value = ReadState.Failed
                return
            }
            val got = withTimeoutOrNull(READ_WAIT_MS) { data.filterNotNull().first { it.table?.id == id } }
            if (got != null) {
                if (message.value == readError) message.value = null
                readState.value = ReadState.Idle
                return
            }
        }
        readState.value = ReadState.Failed
    }

    private fun buildUi(d: Data?, pending: List<CorrectionRow>?, message: String?, read: ReadState): CorrectionUi {
        val id = d?.tableId?.takeIf { it.valid }?.value
        val confirmed = d?.table
        val inSync = id != null && confirmed != null && confirmed.id == id
        val table = if (pending != null) CorrectionTable(confirmed?.enabled ?: false, pending) else confirmed
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
 * The commands that turn [base] into [target]: an upsert for each row that is new or different, a delete for each
 * row that is gone. Upserts go first, so a renamed row never leaves a gap, unless the table would then pass
 * [MAX_ROWS] rows.
 */
private fun commandsFor(base: List<CorrectionRow>, target: List<CorrectionRow>): List<WindCommand> {
    val before = base.associateBy { it.tws }
    val upserts = target.filter { before[it.tws] != it }.map { WindCommand.TableUpsert(it) }
    val deletes = base.filter { b -> target.none { it.tws == b.tws } }.map { WindCommand.TableDelete(it.tws) }
    val added = target.count { it.tws !in before }
    return if (base.size + added > MAX_ROWS) deletes + upserts else upserts + deletes
}
