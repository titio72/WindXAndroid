# Correction Table UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Correction card that shows the sensor's whole wind-correction table, lets the user tweak one value at a time while sailing, and bulk-imports a table at the dock.

**Architecture:** The table is read from the sensor's new read-only `table` BLE characteristic and merged into the app's `Data` state, so the card always shows what the sensor reports. Edits go through a `CorrectionController` that keeps the user's edited rows apart from the sensor's, sends the difference as `U`/`Z` commands after a 500 ms debounce, and waits for the sensor's own table to show the change. A pure-Kotlin model mirrors the firmware's validation so a rejected edit is blocked before sending. marine-kit gets an optional characteristic and an on-demand read.

**Tech Stack:** Kotlin, Jetpack Compose, marine-kit (`link`, `ui`), JUnit4 + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-14-correction-table-ui-design.md`. Firmware contract: `~/Documents/PlatformIO/Projects/n2k_wind/docs/superpowers/specs/2026-11-12-wind-correction-table-design.md` (commit `19feaaa`).

## Global Constraints

- Package `com.aboni.n2kRouter`; app code in `app/src/main/java/com/aboni/n2kRouter/`, tests in `app/src/test/java/com/aboni/n2kRouter/`.
- marine-kit is changed **in the app's submodule `marine-kit/`** (not `~/dev/marine-kit`, which is behind at 0.2.0), with tests, then released and the submodule pointer bumped. **Pushing a branch or tag is outward-facing: ask the user before the push steps.**
- JDK 21. Test commands: marine-kit `cd marine-kit && ./gradlew :link:test`; app `./gradlew testDebugUnitTest`; build `./gradlew assembleDebug`.
- All values are integers: TWS in 0.1 kn, angle in whole degrees, dA in 0.1°, dS in 0.1 kn.
- Firmware limits: ≤ 10 rows; row TWS ≤ 1000 and strictly increasing across rows; each row has exactly 3 anchors with angle strictly increasing, 1..179; |dA| ≤ 300, |dS| ≤ 100. Slope rule `slopeOk(dv, dx) = 2·|dv| ≤ dx` (both in 0.1 units): dA between anchors against `10·Δangle` (taper from 0 at angle 0 and to 0 at angle 180 included); dS of each anchor against the row's TWS; dS between neighbouring rows, same anchor index, against ΔTWS.
- Table payload (little-endian): bytes 0–1 = `0x9FFE` (manufacturer 2046, 2 reserved bits set, industry 4), byte 2 = 1 (type), byte 3 = 1 (version), bytes 4–7 = `table_id` = CRC-32 (IEEE) of bytes 8..end, byte 8 = enabled, byte 9 = row count N (0–10), then N × 17 bytes: TWS (u16) and 3 × [angle (u8), dA (i16), dS (i16)]. Length is exactly 10 + 17·N.
- Commands: `C0`/`C1`, `U<tws>,<a1>,<dA1>,<dS1>,<a2>,<dA2>,<dS2>,<a3>,<dA3>,<dS3>` (kn and degrees with one decimal, `.` separator whatever the locale, e.g. `U10.0,40,-3.5,0.0,95,-1.0,0.0,150,2.0,0.0`), `Z<tws>` (e.g. `Z10.0`), `Y`. The sensor does not reply; a rejected command only leaves `table_id` unchanged.
- BLE: characteristic `b0ff19bd-7c2d-4215-8cc0-2a6db52cdf4f`, read not subscribed, may lag a command by one main-loop pass. Data-packet tail after the Wind360 part: `table_id` u32, enabled u8, corrected u8, corrected AWA u16 (0.1°), corrected AWS u16 (0.1 kn), `0xFFFF` = n/a.
- UI: marine-kit tokens only (`Marine.colors.*`, `Marine.type.*`, `Themed(light, dark, night)` for domain colours, Night red-only); no hard-coded colours or text sizes; no horizontal scrolling for the table.
- Commit messages end with the `Co-Authored-By` line the session specifies.

## Review Focus

1. **A table read that straddles a change** (CRC of the payload differs from the `table_id` in the data packet): not shown, read again up to 3 times, then "out of sync". Tests in Tasks 3 and 7.
2. **Import text with wrong shape**: comma decimals (`10,0,40,…` = 12 numbers), a non-number, out-of-range values, the same TWS twice, more than 10 rows, a table that breaks the slope rule: each is reported with its line, nothing is sent. Task 5.
3. **Older firmware** (data packet without the tail, no `table` characteristic): no crash, other cards unaffected, correction card says so and its controls are disabled. Tasks 6 and 7.
4. **The sensor rejects an edit, or the link drops mid-edit**: pending values never linger or get sent late; the card returns to the sensor's table with a plain message. Task 7.
5. **Empty (0 rows) and full (10 rows) tables**: render, Add row blocked at 10, deleting the last row works. Tasks 4 and 8.

---

### Task 1: marine-kit — on-demand read and optional characteristics

**Files:**
- Modify: `marine-kit/link/src/main/kotlin/com/aboni/marine/link/ble/BleProfile.kt`, `.../link/Pipe.kt`, `.../link/ble/BlePipe.kt`, `.../link/ble/AndroidBleRadio.kt`
- Test: `marine-kit/link/src/test/kotlin/com/aboni/marine/link/ble/BlePipeTest.kt`
- Modify: `marine-kit/docs/MIGRATION.md`

**Interfaces:**
- Produces: `BleProfile.onDemand: Map<UUID, Int> = emptyMap()` (last constructor parameter, so existing callers compile); `suspend fun Pipe.read(channel: Int)` with a default body that throws `IOException("This transport can't read on request")`; `BlePipe.read(channel)` reads the characteristic mapped to `channel` in `onDemand`, and emits `Packet(channel, bytes)` on `inbound`.

- [ ] **Step 1: Write failing tests** in `BlePipeTest.kt` (profile gets `onDemand = mapOf(table to 4)` with a new `table` UUID; the fake connection has `values[table] = byteArrayOf(9, 9)`):
  - `readOnDemandDeliversAPacket`: pipe up, `pipe.read(4)` → `packets.last()` has channel 4 and bytes `[9, 9]`.
  - `readOnDemandOfAChannelWithoutCharacteristicThrows`: `read(77)` throws `IOException` mentioning the channel.
  - `readOnDemandWhenTheDeviceLacksTheCharacteristicThrowsIOException`: remove `values[table]`, `read(4)` throws `IOException`, and the pipe stays `Up`.
  - `readOnDemandWhenNotUpThrowsNotConnected`: before connect, `read(4)` throws `IOException("Not connected")`.
- [ ] **Step 2: Run** `cd marine-kit && ./gradlew :link:test --tests '*BlePipeTest*'`. Expected: FAIL (no `read`/`onDemand`).
- [ ] **Step 3: Implement.** Add `onDemand` to `BleProfile` with a KDoc ("optional: a device without it is accepted; reading it fails with a plain IOException"). Add the default `read` to `Pipe`. Implement `BlePipe.read` next to `write` (same Up/`conn` guard, uuid lookup throws `IOException("No characteristic for channel $channel")`). `AndroidBleRadio.setup` must **not** check `onDemand` characteristics (leave the existing `readOnConnect + write` check as is); `AndroidBleRadio`'s `read` already throws the plain "lacks characteristic" error for an absent one.
- [ ] **Step 4: Run** `./gradlew :link:test`. Expected: all pass.
- [ ] **Step 5: Add a `## 0.4.0` entry** to `docs/MIGRATION.md`: "Additive only, nothing to migrate. New: `BleProfile.onDemand` (optional characteristics, read with `Pipe.read(channel)`)." Commit in the submodule: `git -C marine-kit add -A && git -C marine-kit commit -m "feat(link): optional on-demand characteristics and Pipe.read"`.

---

### Task 2: marine-kit release and app bump

**Files:** submodule pointer `marine-kit` in the app repo.

**Interfaces:** Consumes Task 1. Produces: the app builds against marine-kit with `onDemand` and `Pipe.read`.

- [ ] **Step 1: Ask the user** to confirm pushing marine-kit and tagging `0.4.0` (outward-facing). On yes: `git -C marine-kit push origin HEAD && git -C marine-kit tag 0.4.0 && git -C marine-kit push origin 0.4.0`.
- [ ] **Step 2: Verify** `./gradlew assembleDebug` in the app repo still builds (the app uses the submodule through `includeBuild`).
- [ ] **Step 3: Commit the pointer** in the app repo: `git add marine-kit && git commit -m "Bump marine-kit to 0.4.0 (on-demand characteristic read)"`.

---

### Task 3: Table model, payload decode/encode, CRC

**Files:**
- Create: `app/src/main/java/com/aboni/n2kRouter/CorrectionTable.kt`
- Test: `app/src/test/java/com/aboni/n2kRouter/CorrectionTableTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class Anchor(val angle: Int, val dA: Int, val dS: Int)           // deg, 0.1 deg, 0.1 kn
  data class CorrectionRow(val tws: Int, val anchors: List<Anchor>)     // tws 0.1 kn; exactly 3 anchors
  data class CorrectionTable(val enabled: Boolean, val rows: List<CorrectionRow>) {
      fun encode(): ByteArray                       // the payload above
      val id: Long                                  // CRC-32 of encode() from byte 8, 0..0xFFFFFFFF
      companion object { fun decode(payload: ByteArray): CorrectionTable? }  // null on any violation
  }
  fun crc32(bytes: ByteArray, from: Int): Long
  const val MAX_ROWS = 10; const val MAX_TWS = 1000; const val MAX_DA = 300; const val MAX_DS = 100
  ```
- [ ] **Step 1: Write failing tests.** Use the firmware's golden vector T1 = `enabled=true`, rows `(60, [(45,-60,0),(95,-40,0),(150,-20,0)])` and `(100, [(40,-35,0),(95,-10,0),(150,20,5)])`, payload hex
  `FE 9F 01 01 DE A1 30 53 01 02 3C 00 2D C4 FF 00 00 5F D8 FF 00 00 96 EC FF 00 00 64 00 28 DD FF 00 00 5F F6 FF 00 00 96 14 00 05 00` (44 bytes):
  - `crc32` of `"123456789"` from 0 = `0xCBF43926`.
  - `encode()` of T1 equals the payload; `T1.id == 0x5330A1DE`; the same table with `enabled=false` has id `0xC640754B`; the empty disabled table has id `0x41D912FF` and a 10-byte payload.
  - `decode(payload) == T1`; decode(encode(t)) == t for the empty table and for a 10-row table (180 bytes).
  - `decode` returns null for: one payload byte changed after byte 8 (CRC), a changed byte 4 (stored CRC), manufacturer code ≠ 2046, type ≠ 1, version ≠ 1, row count 11, a length ≠ 10 + 17·N, fewer than 10 bytes.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CorrectionTableTest*'`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** in `CorrectionTable.kt`. `crc32` wraps `java.util.zip.CRC32` (same polynomial and finalisation as the firmware's `crc32_ieee`), returning an unsigned `Long`. Use `java.nio.ByteBuffer` little-endian for reading and writing. `decode` must not validate the table's *rules* (that is Task 4), only the framing and CRC.
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `git add app/src && git commit -m "Add the correction table model and payload codec"`.

---

### Task 4: Validation and edit operations

**Files:**
- Create: `app/src/main/java/com/aboni/n2kRouter/CorrectionEdit.kt`
- Test: `app/src/test/java/com/aboni/n2kRouter/CorrectionEditTest.kt`

**Interfaces:**
- Consumes: Task 3 model.
- Produces:
  ```kotlin
  fun validateRows(rows: List<CorrectionRow>): String?   // null = valid, else the first problem in plain words
  enum class Field { Tws, Angle, DA, DS }
  data class Selection(val rowTws: Int, val anchor: Int, val field: Field)   // anchor 0..2, ignored for Tws
  sealed interface Adjusted {
      data class Ok(val rows: List<CorrectionRow>, val selection: Selection) : Adjusted
      data class Blocked(val reason: String) : Adjusted
  }
  fun adjustRows(rows: List<CorrectionRow>, sel: Selection, delta: Int): Adjusted  // delta in the field's unit
  fun addRowLike(rows: List<CorrectionRow>, tws: Int): Adjusted   // copy of the row at [tws]
  fun deleteRow(rows: List<CorrectionRow>, tws: Int): Adjusted
  ```
  Reasons name the offending thing ("dS", "dA", "TWS", "angle", "10 rows") so tests can check them.
- [ ] **Step 1: Write failing tests** (T1 rows as in Task 3):
  - `validateRows(T1.rows) == null`; empty list valid; 11 rows → reason contains "10 rows".
  - Boundaries: first anchor `(40, -200, 0)` valid, `(40, -201, 0)` blocked (contains "dA"); row TWS 60 with dS 30 valid, 31 blocked (contains "dS"); last anchor `(150, 150, 0)` valid, `151` blocked (taper to 180); neighbouring rows TWS 60/100 with dS 0 and 20 at the same anchor valid, 0 and 21 blocked.
  - Structure: duplicate TWS, descending TWS, TWS 1001, angle 0 or 180, non-increasing angles, |dA| 301, |dS| 101, an anchor list not of size 3 → each blocked.
  - `adjustRows(T1.rows, Selection(60, 0, Field.DA), +1)` → `Ok`, that dA is −59. `adjustRows(..., Selection(60, 0, Field.DS), +31)` → `Blocked` (contains "dS"), `+30` → `Ok`.
  - `adjustRows(..., Selection(60, 0, Field.Tws), +5)` → `Ok` with the row at TWS 65 and `selection.rowTws == 65`. `+40` (collides with TWS 100) → `Blocked` (contains "TWS"). `+60` (moves past 100) → `Ok`, rows re-sorted by TWS, selection follows the row.
  - `adjustRows(..., Selection(60, 1, Field.Angle), +100)` → `Blocked` (contains "angle").
  - `addRowLike(T1.rows, 60)` → `Ok`, 3 rows, new row TWS 65 (first free of +5, +10, …) with 60's anchors, selection `(65, 0, Field.Tws)`; on 10 rows → `Blocked` ("10 rows").
  - `deleteRow` of one of two rows → `Ok`; deleting the last remaining row → `Ok` with an empty list; deleting a row whose removal makes neighbours break the dS slope → `Blocked`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CorrectionEditTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** One private `slopeOk(dv: Int, dx: Int) = 2 * abs(dv) <= dx`. `adjustRows` changes the one field, re-sorts rows by TWS, runs `validateRows`, and returns `Blocked(reason)` if invalid; the selection after a TWS edit follows the moved row. `addRowLike`/`deleteRow` also validate the result.
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `git add app/src && git commit -m "Validate correction tables like the firmware and add edit operations"`.

---

### Task 5: Import parser

**Files:**
- Create: `app/src/main/java/com/aboni/n2kRouter/CorrectionImport.kt`
- Test: `app/src/test/java/com/aboni/n2kRouter/CorrectionImportTest.kt`

**Interfaces:**
- Consumes: `CorrectionRow`, `validateRows` (Tasks 3, 4).
- Produces:
  ```kotlin
  data class ImportError(val line: Int, val reason: String)   // line is 1-based; 0 = about the table as a whole
  data class ImportResult(val rows: List<CorrectionRow>, val errors: List<ImportError>)
  fun parseImport(text: String): ImportResult
  ```
- [ ] **Step 1: Write failing tests:**
  - `"10.0,40,-3.5,0.0,95,-1.0,0.0,150,2.0,0.0"` and the same line with a leading `U` both give `CorrectionRow(100, [Anchor(40,-35,0), Anchor(95,-10,0), Anchor(150,20,0)])`, no errors.
  - Blank lines, `#` comment lines and surrounding spaces are ignored; line numbers in errors still count them.
  - Lines out of TWS order come back sorted by TWS.
  - Errors, each with the right line number and no rows sent on: `10,0,40,-3.5,…` (too many numbers, comma decimal), `abc`, 9 numbers, dA `31.0`, dS `10.1`, TWS `100.1` (> 100.0 kn), angle `0`; the same TWS on two lines (error on the second); 11 rows (line 0); two rows that break the dS slope (line 0).
  - `-3.55` for dA rounds half away from zero to −36 tenths (like the firmware's `lround`).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CorrectionImportTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Per line: optional leading `U`, split on `,`, expect 10 numbers (`toDoubleOrNull`, `.` only), scale and round (tws and dA, dS ×10; angle ×1), range-check, then check the row alone with `validateRows(listOf(row))`. After all lines: sort, reject duplicate TWS, then `validateRows` on the whole. `ImportResult.rows` is empty whenever `errors` is not.
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `git add app/src && git commit -m "Parse a pasted or opened correction table"`.

---

### Task 6: Data fields, table channel, commands

**Files:**
- Modify: `Data.kt`, `WindLink.kt`
- Test: `WindProtocolTest.kt`, `DataTest.kt`

**Interfaces:**
- Consumes: `CorrectionTable.decode` (Task 3); `BleProfile.onDemand` (Task 2).
- Produces: `Data.tableId: IntValue(4,false)`, `Data.correctionEnabled: BooleanValue`, `Data.corrected: BooleanValue`, `Data.correctedAwa: IntValue(2,false)` and `Data.correctedAws: IntValue(2,false)` (0.1 units; `0xFFFF` parses as invalid), all appended to `fields` after `calibrationProgress`; `Data.table: CorrectionTable?` (copied by `copyFrom`); `const val CHANNEL_TABLE = 2`; `WIND_PROFILE.onDemand = mapOf(CHARACTERISTIC_TABLE_UUID to CHANNEL_TABLE)`; `WindCommand.TableEnable(on: Boolean)`, `TableUpsert(row: CorrectionRow)`, `TableDelete(tws: Int)`, `data object TableClear`.
- [ ] **Step 1: Write failing tests** in `WindProtocolTest.kt` (extend the `packet()` helper with an optional 9-byte tail):
  - With tail (`0x5330A1DE`, 1, 1, 382, 152): `tableId.value == 0x5330A1DE`, `correctionEnabled.asBoolean() == true`, `corrected.asBoolean() == true`, `correctedAwa.value == 382`, `correctedAws.value == 152`. With `0xFFFF` for both: `correctedAwa.valid == false`, `correctedAws.valid == false`.
  - A packet without the tail (older firmware): decodes, `tableId.valid == false`, no exception.
  - `decode(Packet(CHANNEL_TABLE, T1 payload), current)` returns `Inbound.Data` whose `table.id == 0x5330A1DE` and whose other fields (e.g. `wind`) come from `current`; a following data packet decoded with that result as `current` still has `table`. A payload with a bad CRC returns null. A table packet with `current == null` also works.
  - Command texts: `TableEnable(true)` → `C1`, `false` → `C0`, `TableUpsert(CorrectionRow(100, [(40,-35,0),(95,-10,0),(150,20,0)]))` → `U10.0,40,-3.5,0.0,95,-1.0,0.0,150,2.0,0.0`, `TableDelete(100)` → `Z10.0`, `TableClear` → `Y`; a row with dA −5 tenths formats as `-0.5` (not `0.5`), and the text is the same under `Locale.GERMANY`.
  - `WIND_PROFILE.onDemand` maps UUID `b0ff19bd-7c2d-4215-8cc0-2a6db52cdf4f` to channel 2.
  - `DataTest.testCopyFrom` also checks `tableId`, `corrected` and `table`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest`. Expected: new tests FAIL.
- [ ] **Step 3: Implement.** Data packet decode: `d.parse(bytes)` then `d.table = current?.table`. Table packet decode: `CorrectionTable.decode(p.bytes)` (null → `appendLog` and return null), else a new `Data().apply { current?.let(::copyFrom); table = t }` as `Inbound.Data`. Format decimals with `String.format(Locale.ROOT, "%.1f", v / 10.0)`.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest`. Expected: all pass.
- [ ] **Step 5: Commit** `git add app/src && git commit -m "Parse the correction status and table; add the table commands"`.

---

### Task 7: CorrectionController and ViewModel wiring

**Files:**
- Create: `app/src/main/java/com/aboni/n2kRouter/CorrectionController.kt`
- Modify: `WindViewModel.kt`, `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Test: `app/src/test/java/com/aboni/n2kRouter/CorrectionControllerTest.kt`

**Interfaces:**
- Consumes: Tasks 3–6 (`Data.table`, `Data.tableId`, `WindCommand.Table*`, `adjustRows`, `addRowLike`, `deleteRow`, `CommandResult.ok`/`.text`).
- Produces:
  ```kotlin
  enum class Sync { Unavailable, Reading, InSync, Pending, OutOfSync }
  data class CorrectionUi(val sync: Sync, val table: CorrectionTable?, val tableId: Long?, val pendingTws: Set<Int>, val message: String?)
  class CorrectionController(
      scope: CoroutineScope, data: StateFlow<Data?>,
      readTable: suspend () -> Unit,                         // Pipe.read(CHANNEL_TABLE)
      send: suspend (WindCommand) -> CommandResult,
      debounceMs: Long = 500, confirmMs: Long = 3_000,
  ) {
      val ui: StateFlow<CorrectionUi>
      fun adjust(sel: Selection, delta: Int): Adjusted      // Blocked sends nothing
      fun addRow(likeTws: Int): Adjusted
      fun deleteRow(tws: Int): Adjusted
      fun importRows(rows: List<CorrectionRow>)             // flushes at once
      fun setEnabled(on: Boolean)
      fun clear()
  }
  ```
  `ui.table` is the confirmed table with the user's pending rows laid over it; `pendingTws` are the rows that differ from the confirmed table. Enabled state is `ui.table.enabled`.
- [ ] **Step 1: Add the test dependency** `kotlinx-coroutines-test` (same version as marine-kit's coroutines, see `marine-kit/gradle/libs.versions.toml`) to `libs.versions.toml` and `testImplementation` in `app/build.gradle.kts`.
- [ ] **Step 2: Write failing tests** with `runTest`, a `MutableStateFlow<Data?>` and a fake sensor: the test's `send` applies `U`/`Z`/`C`/`Y` to its own table (using `CorrectionTable.id` for the new `tableId`), and its `readTable` publishes `Data` with `table` set; the controller runs in `backgroundScope`:
  - `readsTheTableWhenTheTableIdIsUnseen`: sensor shows `tableId` X with no table → `readTable` called once, then `sync == InSync` and `ui.table` equals the sensor's.
  - `retriesAStraddledReadThenGivesUp`: reads that publish a table whose id ≠ `tableId` → 3 reads, then `sync == OutOfSync`.
  - `unavailableWithoutCorrectionFields`: `tableId.valid == false` → `Unavailable`, no read.
  - `coalescesTapsIntoOneUpsert`: 3 `adjust` calls within 500 ms → exactly one `U` sent, carrying the final row; `ui.table` shows the pending value and `pendingTws` contains the row at once; after the sensor confirms, `pendingTws` is empty and `sync == InSync`.
  - `blocksWhatTheSensorWouldReject`: `adjust` returns `Blocked` and `send` is never called.
  - `revertsWhenTheSensorDoesNotConfirm`: the fake sensor ignores the `U` → after `confirmMs` the card shows the sensor's table again, `pendingTws` empty, `message != null`.
  - `aFailedSendStopsAndReverts`: `send` returns `CommandResult.NotConnected` → no further sends, reverts, `message` is the result's `text`.
  - `renameSendsUpsertThenDelete`: changing a row's TWS sends `U` (new) before `Z` (old).
  - `importSendsUpsertsThenDeletesTheMissingRows`; and `importDeletesFirstWhenUpsertsWouldExceedTenRows`.
  - `enableAndClearSendCAndY`.
- [ ] **Step 3: Run** `./gradlew testDebugUnitTest --tests '*CorrectionControllerTest*'`. Expected: FAIL.
- [ ] **Step 4: Implement `CorrectionController`.**
  - *Sync:* collect `data`. If `tableId` is invalid → `Unavailable`. If it is valid and `data.table?.id != tableId` and no read is running → run up to 3 `readTable()` attempts, waiting up to 1 s after each for a matching table; `IOException` or three misses → `OutOfSync` with a message.
  - *Edits:* `adjust`/`addRow`/`deleteRow` call the Task 4 functions on `working ?: confirmedRows`; on `Ok` store `working`, restart a `debounceMs` timer, return the result.
  - *Flush* (serialised by a `Mutex`; also called directly by `importRows`): diff the confirmed rows against `working`: `U` for each row not identical to the confirmed row with the same TWS, `Z` for each confirmed TWS missing from `working`. Send `U`s first, unless `confirmed.size + new rows > 10`, then `Z`s first. Stop at the first result that is not `ok` (message = its `text`, drop `working`). Then wait until the confirmed rows equal `working`, with a `confirmMs` timeout that restarts whenever the confirmed `tableId` changes; on success or timeout set `working = null` (on timeout with message "The sensor did not accept the change").
  - `setEnabled` and `clear` send `C`/`Y` immediately (no debounce); their confirmation is the next `tableId`.
- [ ] **Step 5: Wire it in `WindViewModel`:** `val correction = CorrectionController(viewModelScope, link.data, { link.pipe.read(CHANNEL_TABLE) }, link::send)`.
- [ ] **Step 6: Run** `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`. Expected: pass / builds.
- [ ] **Step 7: Commit** `git add app gradle && git commit -m "Keep the correction table in sync and send edits"`.

---

### Task 8: Correction card

**Files:**
- Create: `app/src/main/java/com/aboni/n2kRouter/CorrectionFormat.kt`, `app/src/main/java/com/aboni/n2kRouter/ui/CorrectionCard.kt`
- Modify: `MainActivity.kt`
- Test: `app/src/test/java/com/aboni/n2kRouter/CorrectionFormatTest.kt`

**Interfaces:**
- Consumes: `CorrectionUi`, `CorrectionController`, `Selection`, `Field` (Tasks 4, 7); `Data` (Task 6).
- Produces:
  ```kotlin
  fun tenths(v: Int, signed: Boolean = false): String      // -35 → "-3.5"; 20, signed → "+2.0"; 100 → "10.0"
  fun syncText(ui: CorrectionUi): String
  fun correctingText(d: Data?): String
  @Composable fun ColumnScope.CorrectionCard(d: Data?, ui: CorrectionUi, controller: CorrectionController, onImport: () -> Unit)
  ```
- [ ] **Step 1: Write failing tests** for the formatters: the `tenths` examples above, and `tenths(-5, signed = true) == "-0.5"`; `syncText`: `InSync` with id `0x5330A1DE` → `"In sync · 5330A1DE"`, `Pending` → `"Sending…"`, `Reading` → `"Reading the table…"`, `OutOfSync` → `"Out of sync"`, `Unavailable` → `"This sensor's firmware has no correction table"`; `correctingText`: not enabled → `"Off"`, enabled and `corrected` false → `"Enabled, not correcting (no boat speed)"`, corrected → `"Correcting now: AWA 38.2° · AWS 15.2 kn"` for AWA 382 / AWS 152, `null` data → `NO_VALUE`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CorrectionFormatTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement the formatters**, then the card following the design sketch in the spec: status lines on top with the enable `SwitchRow` (sends `controller.setEnabled`); all rows in a column (TWS, then three `angle dA dS` cells), the selected value highlighted and pending rows marked using `Themed` colours added to `WindColors.kt`; a stepper pinned at the bottom for the selected value with a step selector (dA/dS 1/5/10, angle 1/5, TWS 5/10, in each field's own unit) whose index is persisted in `PrefsStore` like `calibPrecisionProgress`; `Add row`, `Delete row`, `Import…`, and `Clear…` (a confirmation `AlertDialog`). `Blocked.reason` is shown in the scaffold snackbar like `rememberSender`. Controls are disabled when `sync` is `Unavailable` or the link is not connected. A selection whose row no longer exists is dropped. Text sizes small enough for 10 rows × 3 anchors without horizontal scroll.
- [ ] **Step 4: Add the page** to the `CardPager` in `MainActivity.kt` after Adjust (`CardPage("correction", "Correction", CardAction.icon(...))`), update `PAGES` and `DEVICES_PAGE`, and collect `vm.correction.ui`.
- [ ] **Step 5: Run** `./gradlew testDebugUnitTest assembleDebug`. Expected: pass / builds. Then run the app with the `run` skill, open the Correction card, and check all three themes (Light, Dark, Night: Night shows red only) with a table of 0, 3 and 10 rows.
- [ ] **Step 6: Commit** `git add app && git commit -m "Add the Correction card"`.

---

### Task 9: Import dialog

**Files:**
- Create: `app/src/main/java/com/aboni/n2kRouter/ui/CorrectionImportDialog.kt`
- Modify: `MainActivity.kt` (hosts the dialog state and the file picker)

**Interfaces:**
- Consumes: `parseImport`, `ImportResult` (Task 5); `CorrectionController.importRows` (Task 7).
- Produces: `@Composable fun CorrectionImportDialog(onSend: (List<CorrectionRow>) -> Unit, onDismiss: () -> Unit)`.
- [ ] **Step 1: Implement** a dialog with a multi-line text field (paste), an `Open file…` button (`ActivityResultContracts.OpenDocument` for `text/*`, the file read as UTF-8 into the field), a live preview (`parseImport` on every change): the rows in the card's own format and the errors listed as "line N: reason" (line 0 as "table: reason"), and a `Send` button that is enabled only when there are rows and no errors. `Send` calls `onSend(rows)` and closes. Use marine-kit tokens only.
- [ ] **Step 2: Wire** the card's `Import…` button (`onImport`) to show it, and `onSend` to `vm.correction::importRows`.
- [ ] **Step 3: Run** `./gradlew testDebugUnitTest assembleDebug`. Expected: pass / builds.
- [ ] **Step 4: Commit** `git add app && git commit -m "Add the correction table import dialog"`.

---

### Task 10: On-device verification

**Files:** none (a checklist; record the outcome in the PR or commit message).

- [ ] **Step 1: Connect to the sensor** with the new firmware. Expected: the Correction card reads the table, shows "In sync · <id>", and the id matches the firmware's `table_id` in the Wind card data.
- [ ] **Step 2: Edit one dA with the stepper** (a few quick taps). Expected: one change on the sensor (the `table_id` changes once), the value comes back as confirmed, the pending marker disappears.
- [ ] **Step 3: Try an edit the firmware would reject** (dS above 0.5 × TWS). Expected: blocked in the app with the reason, nothing sent.
- [ ] **Step 4: Import a 10-row table** (the router script's `U` lines). Expected: preview, send, final table equals the file. Note whether two quick writes are ever lost; if so, make the controller wait for each `tableId` change between sends (Left to the builder in the spec).
- [ ] **Step 5: Enable/disable and clear.** Expected: the switch follows the sensor; Clear asks for confirmation and the table empties.
- [ ] **Step 6: Connect to a sensor with older firmware** (or one without the `table` characteristic). Expected: it connects, the other cards work, the Correction card says the firmware has no table.
- [ ] **Step 7: Drop the connection mid-edit** (switch the sensor off right after a tap). Expected: the card returns to the last confirmed table with a plain message, nothing is sent after reconnect.
