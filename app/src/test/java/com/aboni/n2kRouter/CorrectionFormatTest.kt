package com.aboni.n2kRouter

import org.junit.Assert.assertEquals
import org.junit.Test

class CorrectionFormatTest {

    private fun le(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    private fun data(enabled: Int, corrected: Int, awa: Int, aws: Int) = Data().apply {
        correctionEnabled.parseValue(le(enabled), 0)
        this.corrected.parseValue(le(corrected), 0)
        correctedAwa.parseValue(le(awa and 0xFF, awa shr 8), 0)
        correctedAws.parseValue(le(aws and 0xFF, aws shr 8), 0)
    }

    private fun ui(sync: Sync, id: Long? = null) = CorrectionUi(sync, null, id, emptySet(), null)

    @Test fun tenthsAreShownWithOneDecimal() {
        assertEquals("-3.5", tenths(-35))
        assertEquals("10.0", tenths(100))
        assertEquals("0.0", tenths(0))
    }

    @Test fun signedTenthsShowThePlus() {
        assertEquals("+2.0", tenths(20, signed = true))
        assertEquals("-0.5", tenths(-5, signed = true))
    }

    @Test fun tenthsUseADotWhateverTheLocale() {
        val saved = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            assertEquals("10.5", tenths(105))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test fun syncTexts() {
        assertEquals("In sync · 5330A1DE", syncText(ui(Sync.InSync, 0x5330A1DEL)))
        assertEquals("In sync · 0000ABCD", syncText(ui(Sync.InSync, 0xABCDL)))
        assertEquals("Sending…", syncText(ui(Sync.Pending)))
        assertEquals("Reading the table…", syncText(ui(Sync.Reading)))
        assertEquals("Out of sync", syncText(ui(Sync.OutOfSync)))
        assertEquals("This sensor's firmware has no correction table", syncText(ui(Sync.Unavailable)))
    }

    @Test fun correctingTexts() {
        assertEquals("Off", correctingText(data(0, 0, 0xFFFF, 0xFFFF)))
        assertEquals("Enabled, not correcting (no boat speed)", correctingText(data(1, 0, 0xFFFF, 0xFFFF)))
        assertEquals("Correcting now: AWA 38.2° · AWS 15.2 kn", correctingText(data(1, 1, 382, 152)))
        assertEquals("Correcting now", correctingText(data(1, 1, 0xFFFF, 0xFFFF)))
        assertEquals(NO_VALUE, correctingText(null))
        assertEquals(NO_VALUE, correctingText(Data()))
    }

    @Test fun stepsFollowTheField() {
        assertEquals(listOf(1, 5, 10), correctionSteps(Field.DA))
        assertEquals(listOf(1, 5, 10), correctionSteps(Field.DS))
        assertEquals(listOf(1, 5), correctionSteps(Field.Angle))
        assertEquals(listOf(5, 10), correctionSteps(Field.Tws))
    }

    @Test fun stepTextsAreInTheFieldsUnit() {
        assertEquals("0.5", stepText(Field.DA, 5))
        assertEquals("1.0", stepText(Field.Tws, 10))
        assertEquals("5", stepText(Field.Angle, 5))
    }

    @Test fun aTooLargeStepIndexUsesTheLargestStep() = assertEquals(5, stepFor(Field.Angle, 2))

    @Test fun theDefaultRowIsAValidFirstRow() =
        assertEquals(null, validateRows(listOf(DEFAULT_ROW)))

    private val row = CorrectionRow(100, listOf(Anchor(40, -35, 0), Anchor(95, -10, 5), Anchor(150, 20, 0)))

    @Test fun selectedValuesAreShownInTheirOwnUnit() {
        assertEquals("10.0", selectionValueText(row, Selection(100, 0, Field.Tws)))
        assertEquals("95", selectionValueText(row, Selection(100, 1, Field.Angle)))
        assertEquals("-3.5", selectionValueText(row, Selection(100, 0, Field.DA)))
        assertEquals("+0.5", selectionValueText(row, Selection(100, 1, Field.DS)))
    }

    @Test fun selectionLabelsNameTheRowTheAnchorAndTheUnit() {
        assertEquals("Row 10.0 kn · TWS (kn)", selectionLabel(Selection(100, 0, Field.Tws)))
        assertEquals("Row 10.0 kn · anchor 2 angle (°)", selectionLabel(Selection(100, 1, Field.Angle)))
        assertEquals("Row 10.0 kn · anchor 1 dA (°)", selectionLabel(Selection(100, 0, Field.DA)))
        assertEquals("Row 10.0 kn · anchor 3 dS (kn)", selectionLabel(Selection(100, 2, Field.DS)))
    }

    @Test fun aRowIsSummarisedOnOneLine() =
        assertEquals("10.0 kn: 40° -3.5/+0.0 · 95° -1.0/+0.5 · 150° +2.0/+0.0", rowSummary(row))
}
