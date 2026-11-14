package com.aboni.n2kRouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionEditTest {

    private fun row(tws: Int, a: Anchor, b: Anchor, c: Anchor) = CorrectionRow(tws, listOf(a, b, c))
    private fun flat(tws: Int) = row(tws, Anchor(40, -35, 0), Anchor(95, -10, 0), Anchor(150, 20, 0))

    private val t1 = listOf(
        row(60, Anchor(45, -60, 0), Anchor(95, -40, 0), Anchor(150, -20, 0)),
        row(100, Anchor(40, -35, 0), Anchor(95, -10, 0), Anchor(150, 20, 5)),
    )

    private fun blocked(rows: List<CorrectionRow>, contains: String? = null) {
        val reason = validateRows(rows)
        assertNotNull("expected the table to be invalid: $rows", reason)
        if (contains != null) assertTrue("'$reason' should mention $contains", reason!!.contains(contains))
    }

    // --- validateRows ---

    @Test fun theFirmwareGoldenTableIsValid() = assertNull(validateRows(t1))

    @Test fun anEmptyTableIsValid() = assertNull(validateRows(emptyList()))

    @Test fun moreThanTenRowsIsBlocked() = blocked((1..11).map { flat(it * 10) }, "10 rows")

    @Test fun theFirstAnchorTapersFromZeroAtTheBow() {
        fun r(dA: Int) = listOf(row(100, Anchor(40, dA, 0), Anchor(95, 0, 0), Anchor(150, 0, 0)))
        assertNull(validateRows(r(-200)))
        blocked(r(-201), "dA")
    }

    @Test fun theLastAnchorTapersToZeroAtTheStern() {
        fun r(dA: Int) = listOf(row(100, Anchor(40, 0, 0), Anchor(95, 0, 0), Anchor(150, dA, 0)))
        assertNull(validateRows(r(150)))
        blocked(r(151), "dA")
    }

    @Test fun dSIsBoundedByHalfTheRowsOwnTws() {
        fun r(dS: Int) = listOf(row(60, Anchor(40, 0, dS), Anchor(95, 0, 0), Anchor(150, 0, 0)))
        assertNull(validateRows(r(30)))
        blocked(r(31), "dS")
    }

    @Test fun dSBetweenNeighbouringRowsIsBoundedByHalfTheTwsGap() {
        fun r(dS: Int) = listOf(
            row(60, Anchor(40, 0, 0), Anchor(95, 0, 0), Anchor(150, 0, 0)),
            row(100, Anchor(40, 0, dS), Anchor(95, 0, 0), Anchor(150, 0, 0)),
        )
        assertNull(validateRows(r(20)))
        blocked(r(21), "dS")
    }

    @Test fun structureRules() {
        blocked(listOf(flat(60), flat(60)), "TWS")                       // duplicate
        blocked(listOf(flat(100), flat(60)), "TWS")                      // descending
        blocked(listOf(flat(1001)), "TWS")                               // above 100.0 kn
        blocked(listOf(row(100, Anchor(0, 0, 0), Anchor(95, 0, 0), Anchor(150, 0, 0))), "angle")
        blocked(listOf(row(100, Anchor(40, 0, 0), Anchor(95, 0, 0), Anchor(180, 0, 0))), "angle")
        blocked(listOf(row(100, Anchor(95, 0, 0), Anchor(95, 0, 0), Anchor(150, 0, 0))), "angle")
        blocked(listOf(row(100, Anchor(40, 301, 0), Anchor(95, 0, 0), Anchor(150, 0, 0))), "dA")
        blocked(listOf(row(1000, Anchor(40, 0, 101), Anchor(95, 0, 0), Anchor(150, 0, 0))), "dS")
        blocked(listOf(CorrectionRow(100, listOf(Anchor(40, 0, 0), Anchor(95, 0, 0)))), "anchors")
    }

    // --- adjustRows ---

    private fun ok(r: Adjusted) = r as Adjusted.Ok
    private fun blockedResult(r: Adjusted) = (r as Adjusted.Blocked).reason

    @Test fun adjustsADaByTenthsOfADegree() {
        val r = ok(adjustRows(t1, Selection(60, 0, Field.DA), +1))
        assertEquals(-59, r.rows[0].anchors[0].dA)
        assertEquals(Selection(60, 0, Field.DA), r.selection)
    }

    @Test fun adjustsADsUpToWhatTheNeighbouringRowAllows() {
        assertEquals(20, ok(adjustRows(t1, Selection(60, 0, Field.DS), +20)).rows[0].anchors[0].dS)
        assertTrue(blockedResult(adjustRows(t1, Selection(60, 0, Field.DS), +21)).contains("dS"))
    }

    @Test fun movesARowsTwsAndTheSelectionFollows() {
        val r = ok(adjustRows(t1, Selection(60, 0, Field.Tws), +5))
        assertEquals(65, r.rows[0].tws)
        assertEquals(65, r.selection!!.rowTws)
    }

    @Test fun aTwsThatCollidesWithAnotherRowIsBlocked() =
        assertTrue(blockedResult(adjustRows(t1, Selection(60, 0, Field.Tws), +40)).contains("TWS"))

    @Test fun aTwsMovedPastANeighbourReSortsTheRows() {
        val r = ok(adjustRows(t1, Selection(60, 0, Field.Tws), +60))
        assertEquals(listOf(100, 120), r.rows.map { it.tws })
        assertEquals(120, r.selection!!.rowTws)
    }

    @Test fun anAngleThatPassesItsNeighbourIsBlocked() =
        assertTrue(blockedResult(adjustRows(t1, Selection(60, 1, Field.Angle), +100)).contains("angle"))

    @Test fun adjustingARowThatDoesNotExistIsBlocked() =
        assertTrue(blockedResult(adjustRows(t1, Selection(70, 0, Field.DA), +1)).contains("TWS"))

    // --- addRowLike / deleteRow ---

    @Test fun addsACopyAtTheNextFreeTws() {
        val r = ok(addRowLike(t1, 60))
        assertEquals(listOf(60, 65, 100), r.rows.map { it.tws })
        assertEquals(t1[0].anchors, r.rows[1].anchors)
        assertEquals(Selection(65, 0, Field.Tws), r.selection)
    }

    @Test fun addSkipsTwsValuesThatAreTaken() {
        val rows = listOf(flat(60), flat(65), flat(70))
        assertEquals(listOf(60, 65, 70, 75), ok(addRowLike(rows, 60)).rows.map { it.tws })
    }

    @Test fun addIsBlockedAtTenRows() {
        val full = (1..10).map { flat(it * 10) }
        assertTrue(blockedResult(addRowLike(full, 10)).contains("10 rows"))
    }

    @Test fun deletesARowAndSelectsTheOneThatTakesItsPlace() {
        val r = ok(deleteRow(t1, 60))
        assertEquals(listOf(100), r.rows.map { it.tws })
        assertEquals(Selection(100, 0, Field.Tws), r.selection)
    }

    @Test fun deletingTheLastRowLeavesAnEmptyTable() {
        val r = ok(deleteRow(listOf(flat(60)), 60))
        assertTrue(r.rows.isEmpty())
        assertNull(r.selection)
    }

    @Test fun deletingARowThatDoesNotExistIsBlocked() =
        assertTrue(blockedResult(deleteRow(t1, 70)).contains("TWS"))
}
