# Wind Correction Table UI — Design

**Date:** 2026-11-14
**Status:** draft for review
**Repos touched:** `NMEAWind2` (this app) and `marine-kit` (`~/dev/marine-kit`, a new release).
**Firmware contract:** `~/Documents/PlatformIO/Projects/n2k_wind/docs/superpowers/specs/2026-11-12-wind-correction-table-design.md`, as built up to commit `19feaaa`.

## Why

The sensor now applies an empirical true-wind correction table to its output (upwash and flow acceleration around the rig). The owner needs to:

- **Tweak single values while sailing**, one hand, on a moving boat, to tune the table tack to tack.
- **Bulk-load a whole table at the dock**, from the numbers the router analysis produces.

Today the app can do neither: it has no screen for the table, and its data parser stops before the correction fields.

## What the firmware gives us

- **Commands** (text, written to the command characteristic; the sensor does not reply):
  - `C0` / `C1`: table off / on.
  - `U tws,a1,dA1,dS1,a2,dA2,dS2,a3,dA3,dS3`: upsert the row with that TWS (replaces the row with the same TWS, else inserts in order; rejected if 10 rows already exist).
  - `Z tws`: delete a row.
  - `Y`: clear the table.
  - A rejected command leaves the table as it was. The only visible sign is that `table_id` does not change.
- **Data packet**, appended after the variable-length Wind360 part: `table_id` (u32), enabled (u8), corrected (u8), corrected AWA (u16, 0.1°, `0xFFFF` = n/a), corrected AWS (u16, 0.1 kn, `0xFFFF` = n/a).
- **`table` characteristic** (read-only, `b0ff19bd-7c2d-4215-8cc0-2a6db52cdf4f`): the PGN 130900 payload byte for byte, 10–180 bytes, little-endian.
  - Layout: manufacturer code (u16, 11 bits = 2046, 2 reserved bits, industry 4 in the top 3 bits), message type = 1, format version = 1, `table_id` (u32), enabled (u8), row count N (u8), then N rows of 17 bytes.
  - Row: TWS (u16, 0.1 kn), then 3 × [angle (u8, deg), dA (i16, 0.1°), dS (i16, 0.1 kn)].
  - Bytes 4–7 are the CRC-32 (IEEE 802.3) of bytes 8 to the end, and equal the `table_id` in the data packet.
  - It is read, not subscribed (a notification would be cut at 125 bytes). It can lag a command by one main-loop pass.
- **Table limits** (the firmware rejects violations): ≤ 10 rows, rows sorted by TWS, anchors sorted by angle; |dA| ≤ 30°, |dS| ≤ 10 kn; slope ≤ 0.5 for dA between anchors and on the tapers to 0° and 180° (|ΔdA| ≤ 0.5·Δangle), for dS within a row (|dS| ≤ 0.5·TWS) and between rows at the same anchor index (|ΔdS| ≤ 0.5·ΔTWS). Resolution: TWS 0.1 kn, angle 1°, dA 0.1°, dS 0.1 kn.

## Goals

1. A new **Correction** card shows the sensor's whole table, all rows at once, always as the sensor reports it (never from an app-side copy).
2. The user picks one value and changes it with a large stepper; the change reaches the sensor and the card shows the sensor's own confirmation.
3. The user can enable/disable the table, add and delete rows, clear the table, and **import** a whole table from the text the router script prints (`U` lines).
4. Edits the firmware would reject are blocked in the app, with the reason shown, because a rejected command produces no reply.
5. The app keeps connecting to sensors with older firmware (no `table` characteristic).

## Non-goals

- Highlighting the row/anchor the boat is in right now. Needs STW, which the data packet does not carry. The user decides which row to edit.
- An app-side copy of the table as the source of truth.
- Changing the firmware.
- Exporting the table, or producing table rows from telemetry (router-side work).
- Per-anchor-angle editing across rows in bulk (angles are edited one value at a time, like everything else).

## Anti-goals (failure even if it "works")

- Showing values that the sensor is not actually using, or showing an edit as applied when it was rejected.
- Sending an edit built from a stale table, so that it silently overwrites other values in the row.
- Flooding the sensor with one command per tap.
- A new look: everything uses marine-kit tokens, and the Night theme stays red-only.
- Breaking connection to sensors that don't have the `table` characteristic.

## Approaches considered

For editing a row once it is picked:

- **A. Select a value, shared stepper (chosen).** All rows on screen; tapping one number highlights it, and a stepper pinned at the bottom edits it with a step size chosen by the user (like the Adjust card). Large controls, one-handed, neighbouring rows stay visible, which matters for the slope rule. One number at a time.
- **B. Select a row, panel with all of its values.** All 10 values of the row with their own − / +. Sees an anchor set at once, but about 10 small controls on a moving boat and half the screen lost to the editor.
- *Dropped:* a full-screen editor per row. It hides the neighbouring rows needed to respect the slope limit.

For where the table comes from: the app reads it from the new characteristic (chosen) rather than keeping its own copy, which would go stale after a reinstall or on a second device. The firmware owner already added the characteristic.

## Design

### marine-kit (new release, `0.4.0` or as the next version is)

Per the marine-ui rules, link gaps go into marine-kit, with tests, then a release and an app bump (`docs/MIGRATION.md` entry).

1. **Optional characteristics.** `BleProfile` gets a set of characteristics that are used when present and ignored when absent. Today `AndroidBleRadio.setup` fails if any `readOnConnect` characteristic is missing, so the table cannot be listed there without breaking older firmware.
2. **On-demand read.** A way to read one characteristic on request and deliver it as a packet on a channel, like `readOnConnect` does once at connect. Suggested shape: `Pipe.read(channel)` (throws `IOException` like `write`), backed by `BleRadio.read` which already exists. The app needs it each time `table_id` changes.
3. Tests with the fake radio: read of a present characteristic, read of an absent optional one (clean "not available"), read while disconnected.

### App: protocol and data

- `Data.kt` parses the new fields after the Wind360 block: `tableId` (u32), `correctionEnabled`, `corrected`, `correctedAwa`, `correctedAws`. Packets from older firmware end earlier: those fields stay invalid and the card says the sensor firmware has no correction table.
- `WindProfile`: add the `table` characteristic as an optional one on a new channel (`CHANNEL_TABLE`). `WindProtocol.decode` turns a table packet into a decoded `CorrectionTable` (or drops it, logged, if the magic, type, version or CRC is wrong).
- `WindCommand` gets `TableEnable(on)`, `TableUpsert(row)`, `TableDelete(tws)`, `TableClear`, with the text formats above (decimals in display units, `.` separator regardless of locale).
- **`CorrectionTable` model** (pure Kotlin, unit-tested, no Android types): rows of `tws` + 3 anchors, decoding of the characteristic payload, the CRC-32, and a **validator that mirrors the firmware rules** above and returns a plain-language reason when a change is not allowed. Tested against the firmware's golden bytes and test vectors (copy a few into `app/src/test`).

### App: keeping the table in sync

The ViewModel holds `tableState`:

- **Read triggers:** after connect (once the data packet shows a `tableId`), and whenever `tableId` in the data packet changes. If the CRC of what was read does not match both bytes 4–7 and the current `tableId` (a read that straddled a change), read again, at most 3 times, then show "out of sync".
- **Edits** are computed from the last confirmed table plus the user's pending changes. Pending changes are shown in the card (marked) until the sensor's table has them.
- **Debounce:** taps on one row are coalesced for about 500 ms and sent as one `U` for that row. A later tap on the same row restarts the timer; the next `U` always carries the full row as the user sees it.
- **Confirmation:** after sending, wait for a new `tableId` (up to 3 s). If it arrives, re-read; if not, drop the pending change and tell the user the sensor did not accept it, showing the sensor's table again.
- **Not connected, or older firmware:** the last read table is shown but greyed, and the editing controls are disabled.

### The card

New `CorrectionCard`, added to the `CardPager` after Adjust (`MainActivity.kt`). It uses the same building blocks as the other cards (marine-kit tokens, `Controls.kt` helpers).

```
 Correction        [ ON ]     ✓ in sync · 5330A1DE
 correcting now: yes (AWA 48.2° · AWS 9.4 kn)

 TWS   anchor 1        anchor 2        anchor 3
 6.0   40° -4.0 0.0    95° -1.5 0.0   150° +1.0 0.0
▶10.0  40° [-3.5] 0.0  95° -1.0 0.0   150° +2.0 0.0
 14.0  ...
 ─────────────────────────────────────────────
 Row 10.0 · anchor 1 · dA        [ − ]  -3.5  [ + ]
 step: 0.1 | 0.5 | 1.0        [Add row] [Delete row]
 [Import…]                                [Clear…]
```

- **Rows:** all rows (≤ 10) in a vertical list, TWS first, then the three anchors, each showing `angle dA dS`. The selected value is highlighted. A pending (unconfirmed) value is marked.
- **Stepper:** pinned at the bottom, edits the selected value (TWS, angle, dA or dS). Step choices follow the field: dA/dS 0.1 / 0.5 / 1.0; angle 1 / 5; TWS 0.5 / 1. The step choice is remembered like the Adjust card's.
- **Status line:** `✓ in sync` when the table read matches the data packet's `tableId`; "reading…", "not confirmed", "out of sync" otherwise. The second line says whether the sample is being corrected right now: enabled but no boat speed → "enabled, not correcting (no boat speed)".
- **Add row** (*my call*): a new row duplicates the selected row at a TWS the user then edits; it is blocked at 10 rows. **Delete row** asks no confirmation (one row, recoverable with the stepper); **Clear** asks for confirmation.
- **Import** (*my call*): accepts pasted text or a text file (Android's file picker), with `U` lines like `10.0,40,-3.5,0.0,95,-1.0,0.0,150,2.0,0.0` (with or without the leading `U`; blank lines and lines starting with `#` ignored). The whole text is validated with the same validator first and shown as a preview with errors per line; nothing is sent until the user confirms. Sending is a *sync*: a `U` per imported row, then a `Z` for every existing row that is not in the import. The table is never cleared first, so an interrupted import leaves a mix rather than an empty table. The enable switch is not touched. The card ends on the re-read table.
- **Theming:** only `Marine.colors.*` / `Marine.type.*`; the selection and "pending" markers use `Themed(light, dark, night)` colours with a red-only Night value.

### Validation in the UI

The stepper blocks a change that breaks a firmware rule and says which (e.g. "dS would exceed 0.5 × the TWS gap to the next row"). Limits are enforced at the stepper (|dA| ≤ 30, |dS| ≤ 10) and structure rules (sorted TWS, sorted angles, 10 rows) at add/edit/import.

## Constraints

- BLE: write of a command is fire-and-forget; the table is only ever learned by reading it.
- Every accepted `U`/`Z` changes the table, triggers a 130900 broadcast on the N2K bus (about 26 frames) and a table read in the app. An import of 10 rows is therefore about 10 broadcasts; acceptable at the dock, no pacing beyond what is below.
- The data packet is capped at 128 bytes; the table read is a long read (up to 180 bytes), which Android does on its own.

## Testing

- **marine-kit:** the tests listed above, plus the existing suites.
- **App, unit tests:** table decoder (golden bytes, bad magic, bad CRC, 0 and 10 rows), CRC-32, validator (each rule, limits at the boundary), command text formats, import parser (valid, comments, bad lines), debounce coalescing, sync state machine (new `tableId` → re-read, straddled read, rejected change times out).
- **On the boat / bench:** against the real sensor: edit one value and see `table_id` change and the value come back; try a value the firmware rejects and see the app block it first; import a 10-row table; connect to a sensor with older firmware and see the other cards work.

## Left to the builder

- Code structure inside the app (ViewModel vs a small repository class), exact composable layout and spacing so 10 rows × 3 anchors fits a phone width (a horizontal scroll is *not* wanted; shrink to caption size first).
- Whether the step selector is shared with the Adjust card or separate.
- Pacing of import sends. The firmware handles one command per write; check on the device that two writes in quick succession are not lost, and wait for each `tableId` change if they are.
- Wording of the status and error messages.
