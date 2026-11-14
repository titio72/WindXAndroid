package com.aboni.n2kRouter

import com.aboni.marine.link.CommandResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalCoroutinesApi::class)
class CorrectionControllerTest {

    private fun flat(tws: Int) = CorrectionRow(tws, listOf(Anchor(40, -35, 0), Anchor(95, -10, 0), Anchor(150, 20, 0)))

    private val initial = CorrectionTable(true, listOf(flat(60), flat(100)))

    /** A sensor that applies table commands and announces the new table_id, like the firmware and the link do. */
    private class Sensor(var table: CorrectionTable) {
        val data = MutableStateFlow<Data?>(null)
        val sent = mutableListOf<WindCommand>()
        var reads = 0
        var ignoreCommands = false
        var failWith: CommandResult? = null
        var straddle = false
        var readFails = false
        var sendDelayMs = 0L

        private fun frame(id: Long, known: CorrectionTable?) = Data().apply {
            tableId.parseValue(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(id.toInt()).array(), 0)
            this.table = known
        }

        /** The data packet shows the sensor's table_id; the app has not read the table yet. */
        fun announce() { data.value = frame(table.id, data.value?.table) }

        suspend fun read() {
            reads++
            if (readFails) throw IOException("Not connected")
            val shown = if (straddle) table.copy(enabled = !table.enabled) else table
            data.value = frame(table.id, shown)
        }

        suspend fun send(cmd: WindCommand): CommandResult {
            sent += cmd
            if (sendDelayMs > 0) delay(sendDelayMs)
            failWith?.let { return it }
            if (ignoreCommands) return CommandResult.Sent
            val next = when (cmd) {
                is WindCommand.TableEnable -> table.copy(enabled = cmd.on)
                is WindCommand.TableUpsert -> {
                    val rows = (table.rows.filter { it.tws != cmd.row.tws } + cmd.row).sortedBy { it.tws }
                    if (validateRows(rows) == null) table.copy(rows = rows) else table
                }
                is WindCommand.TableDelete -> table.copy(rows = table.rows.filter { it.tws != cmd.tws })
                WindCommand.TableClear -> table.copy(rows = emptyList())
                else -> table
            }
            if (next != table) { table = next; announce() }
            return CommandResult.Sent
        }
    }

    /**
     * The controller runs in its own foreground scope on the test's scheduler: `advanceUntilIdle` does not run
     * `backgroundScope` work or its delays. The scope is cancelled after each test.
     */
    private val scopes = mutableListOf<CoroutineScope>()

    @After fun cancelScopes() = stop()

    /** A controller that ends a test out of sync keeps retrying its read, which `runTest` would wait for forever. */
    private fun stop() = scopes.forEach { it.cancel() }

    private fun TestScope.controller(sensor: Sensor): CorrectionController {
        val scope = CoroutineScope(coroutineContext + Job()).also { scopes += it }
        return CorrectionController(scope, sensor.data, sensor::read, sensor::send)
    }

    /** A controller whose table has been read and is in sync. */
    private fun TestScope.synced(table: CorrectionTable = initial): Pair<Sensor, CorrectionController> {
        val sensor = Sensor(table)
        val c = controller(sensor)
        sensor.announce()
        advanceUntilIdle()
        assertEquals(Sync.InSync, c.ui.value.sync)
        sensor.reads = 0
        return sensor to c
    }

    private fun upsert(row: CorrectionRow) = WindCommand.TableUpsert(row)

    // --- reading ---

    @Test fun readsTheTableWhenTheTableIdIsUnseen() = runTest {
        val sensor = Sensor(initial)
        val c = controller(sensor)
        sensor.announce()
        advanceUntilIdle()
        assertEquals(1, sensor.reads)
        assertEquals(Sync.InSync, c.ui.value.sync)
        assertEquals(initial, c.ui.value.table)
        assertEquals(initial.id, c.ui.value.tableId)
    }

    @Test fun readsAgainWhenTheTableIdChanges() = runTest {
        val (sensor, c) = synced()
        sensor.table = sensor.table.copy(rows = listOf(flat(60)))   // changed by someone else
        sensor.announce()
        advanceUntilIdle()
        assertEquals(1, sensor.reads)
        assertEquals(listOf(60), c.ui.value.table!!.rows.map { it.tws })
    }

    @Test fun retriesAStraddledReadThenGivesUp() = runTest {
        val sensor = Sensor(initial).apply { straddle = true }
        val c = controller(sensor)
        sensor.announce()
        advanceTimeBy(5_000); runCurrent()   // the read is retried later, so the clock is not run to idle
        assertEquals(3, sensor.reads)
        assertEquals(Sync.OutOfSync, c.ui.value.sync)
        stop()
    }

    @Test fun aReadThatFailsLeavesItOutOfSyncWithTheReason() = runTest {
        val sensor = Sensor(initial).apply { readFails = true }
        val c = controller(sensor)
        sensor.announce()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(Sync.OutOfSync, c.ui.value.sync)
        assertEquals("Not connected", c.ui.value.message)
        stop()
    }

    @Test fun unavailableWithoutCorrectionFields() = runTest {
        val sensor = Sensor(initial)
        val c = controller(sensor)
        sensor.data.value = Data()
        advanceUntilIdle()
        assertEquals(Sync.Unavailable, c.ui.value.sync)
        assertEquals(0, sensor.reads)
        assertNull(c.ui.value.table)
    }

    // --- editing ---

    private val sel = Selection(60, 0, Field.DA)

    @Test fun coalescesTapsIntoOneUpsert() = runTest {
        val (sensor, c) = synced()
        repeat(3) { assertTrue(c.adjust(sel, +1) is Adjusted.Ok) }
        runCurrent()
        // Shown at once, marked as pending, and nothing is sent yet.
        assertEquals(-32, c.ui.value.table!!.rows[0].anchors[0].dA)
        assertEquals(setOf(60), c.ui.value.pendingTws)
        assertEquals(Sync.Pending, c.ui.value.sync)
        assertTrue(sensor.sent.isEmpty())
        advanceUntilIdle()
        val expected = flat(60).let { it.copy(anchors = listOf(it.anchors[0].copy(dA = -32)) + it.anchors.drop(1)) }
        assertEquals(listOf<WindCommand>(upsert(expected)), sensor.sent)
        assertTrue(c.ui.value.pendingTws.isEmpty())
        assertEquals(Sync.InSync, c.ui.value.sync)
        assertEquals(expected, c.ui.value.table!!.rows[0])
    }

    @Test fun blocksWhatTheSensorWouldReject() = runTest {
        val (sensor, c) = synced()
        val r = c.adjust(Selection(60, 0, Field.DS), +31)   // dS above half the row's TWS
        assertTrue(r is Adjusted.Blocked)
        advanceUntilIdle()
        assertTrue(sensor.sent.isEmpty())
        assertTrue(c.ui.value.pendingTws.isEmpty())
    }

    @Test fun refusesEditsUntilTheTableHasBeenRead() = runTest {
        val sensor = Sensor(initial)
        val c = controller(sensor)
        sensor.data.value = Data()   // no correction fields: older firmware
        advanceUntilIdle()
        assertTrue(c.adjust(sel, +1) is Adjusted.Blocked)
        assertTrue(sensor.sent.isEmpty())
    }

    @Test fun revertsWhenTheSensorDoesNotConfirm() = runTest {
        val (sensor, c) = synced()
        sensor.ignoreCommands = true
        c.adjust(sel, +1)
        advanceUntilIdle()
        assertEquals(1, sensor.sent.size)
        assertEquals(initial, c.ui.value.table)
        assertTrue(c.ui.value.pendingTws.isEmpty())
        assertNotNull(c.ui.value.message)
        assertEquals(Sync.InSync, c.ui.value.sync)
    }

    @Test fun aFailedSendStopsAndReverts() = runTest {
        val (sensor, c) = synced()
        sensor.failWith = CommandResult.NotConnected
        c.importRows(listOf(flat(70), flat(80)))
        advanceUntilIdle()
        assertEquals(1, sensor.sent.size)                  // stopped at the first failure
        assertEquals("Not connected", c.ui.value.message)
        assertEquals(initial, c.ui.value.table)
        assertTrue(c.ui.value.pendingTws.isEmpty())
    }

    @Test fun renameSendsUpsertThenDelete() = runTest {
        val (sensor, c) = synced()
        c.adjust(Selection(60, 0, Field.Tws), +5)
        advanceUntilIdle()
        assertEquals(listOf<WindCommand>(upsert(flat(65)), WindCommand.TableDelete(60)), sensor.sent)
        assertEquals(listOf(65, 100), c.ui.value.table!!.rows.map { it.tws })
        assertEquals(Sync.InSync, c.ui.value.sync)
    }

    @Test fun addAndDeleteRowsGoThroughTheSameDiff() = runTest {
        val (sensor, c) = synced()
        c.addRow(60)
        advanceUntilIdle()
        assertEquals(listOf<WindCommand>(upsert(flat(65))), sensor.sent)
        sensor.sent.clear()
        c.deleteRow(100)
        advanceUntilIdle()
        assertEquals(listOf<WindCommand>(WindCommand.TableDelete(100)), sensor.sent)
        assertEquals(listOf(60, 65), c.ui.value.table!!.rows.map { it.tws })
    }

    // --- import ---

    @Test fun importSendsUpsertsThenDeletesTheMissingRows() = runTest {
        val (sensor, c) = synced()
        val changed = flat(100).let { it.copy(anchors = listOf(it.anchors[0].copy(dA = -30)) + it.anchors.drop(1)) }
        c.importRows(listOf(changed, flat(140)))
        advanceUntilIdle()
        assertEquals(
            listOf<WindCommand>(upsert(changed), upsert(flat(140)), WindCommand.TableDelete(60)),
            sensor.sent,
        )
        assertEquals(listOf(100, 140), c.ui.value.table!!.rows.map { it.tws })
        assertEquals(Sync.InSync, c.ui.value.sync)
    }

    @Test fun importDeletesFirstWhenUpsertsWouldExceedTenRows() = runTest {
        val full = CorrectionTable(false, (1..10).map { flat(it * 10) })
        val (sensor, c) = synced(full)
        c.importRows((2..11).map { flat(it * 10) })
        advanceUntilIdle()
        assertEquals(listOf<WindCommand>(WindCommand.TableDelete(10), upsert(flat(110))), sensor.sent)
        assertEquals(Sync.InSync, c.ui.value.sync)
    }

    @Test fun importOfAnInvalidTableSendsNothing() = runTest {
        val (sensor, c) = synced()
        c.importRows(listOf(flat(100), flat(60)))   // not in TWS order
        advanceUntilIdle()
        assertTrue(sensor.sent.isEmpty())
        assertNotNull(c.ui.value.message)
    }

    // --- switch and clear ---

    @Test fun enableAndClearSendCAndY() = runTest {
        val (sensor, c) = synced()
        c.setEnabled(false)
        advanceUntilIdle()
        assertEquals(WindCommand.TableEnable(false), sensor.sent.single())
        assertEquals(false, c.ui.value.table!!.enabled)
        sensor.sent.clear()
        c.clear()
        advanceUntilIdle()
        assertEquals(listOf<WindCommand>(WindCommand.TableClear), sensor.sent)
        assertTrue(c.ui.value.table!!.rows.isEmpty())
    }

    @Test fun nothingIsSentBeforeTheDebounceElapses() = runTest {
        val (sensor, c) = synced()
        c.adjust(sel, +1)
        advanceTimeBy(499); runCurrent()
        assertTrue(sensor.sent.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(1, sensor.sent.size)
    }

    // --- found by the whole-branch review ---

    private fun withDs(tws: Int, dS: Int) =
        CorrectionRow(tws, listOf(Anchor(40, -35, dS), Anchor(95, -10, 0), Anchor(150, 20, 0)))

    @Test fun anImportWhoseRowsConflictWithTheOldOnesStillEndsAsImported() = runTest {
        // Every imported row is invalid next to a row the sensor has now, so the order of the commands matters.
        val (sensor, c) = synced(CorrectionTable(true, listOf(withDs(60, 0), withDs(100, 20))))
        val imported = listOf(withDs(80, -5), withDs(120, 0))
        assertEquals(null, validateRows(imported))
        c.importRows(imported)
        advanceUntilIdle()
        assertEquals(imported, sensor.table.rows)
        assertEquals(Sync.InSync, c.ui.value.sync)
        assertEquals(null, c.ui.value.message)
    }

    @Test fun clearWaitsForARunningFlush() = runTest {
        val (sensor, c) = synced()
        sensor.sendDelayMs = 100
        c.importRows(listOf(flat(70), flat(80), flat(90)))
        advanceTimeBy(150); runCurrent()   // the first upsert is done, the second is in flight
        c.clear()
        advanceUntilIdle()
        assertEquals(WindCommand.TableClear, sensor.sent.last())
        assertEquals(1, sensor.sent.count { it == WindCommand.TableClear })
        assertTrue(sensor.table.rows.isEmpty())
        assertEquals(null, c.ui.value.message)
    }

    @Test fun aFailedReadIsRetriedLater() = runTest {
        val sensor = Sensor(initial).apply { readFails = true }
        val c = controller(sensor)
        sensor.announce()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(Sync.OutOfSync, c.ui.value.sync)
        sensor.readFails = false
        advanceTimeBy(15_000); runCurrent()
        assertEquals(Sync.InSync, c.ui.value.sync)
        assertEquals(initial, c.ui.value.table)
        assertEquals(null, c.ui.value.message)
    }

    @Test fun aTableThatDoesNotMatchTheSensorIsNotShown() = runTest {
        val sensor = Sensor(initial).apply { straddle = true }
        val c = controller(sensor)
        sensor.announce()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(Sync.OutOfSync, c.ui.value.sync)
        assertNull(c.ui.value.table)
        stop()
    }

    @Test fun anEditMadeWhileAFlushAwaitsItsConfirmationIsNotLost() = runTest {
        val (sensor, c) = synced()
        sensor.sendDelayMs = 100
        c.adjust(sel, +1)
        advanceTimeBy(600); runCurrent()   // the first upsert was sent and its confirmation is awaited
        c.adjust(sel, +1)
        advanceUntilIdle()
        assertEquals(-33, sensor.table.rows[0].anchors[0].dA)
        assertEquals(2, sensor.sent.size)
        assertEquals(Sync.InSync, c.ui.value.sync)
        assertEquals(null, c.ui.value.message)
        assertTrue(c.ui.value.pendingTws.isEmpty())
    }
}
