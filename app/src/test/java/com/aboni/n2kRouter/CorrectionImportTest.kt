package com.aboni.n2kRouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionImportTest {

    private val line10 = "10.0,40,-3.5,0.0,95,-1.0,0.0,150,2.0,0.0"
    private val row10 = CorrectionRow(100, listOf(Anchor(40, -35, 0), Anchor(95, -10, 0), Anchor(150, 20, 0)))

    private fun lineAt(tws: Double) = "$tws,40,-3.5,0.0,95,-1.0,0.0,150,2.0,0.0"

    private fun assertOneError(text: String, line: Int) {
        val r = parseImport(text)
        assertEquals("errors for <$text>: ${r.errors}", 1, r.errors.size)
        assertEquals(line, r.errors.single().line)
        assertTrue(r.errors.single().reason.isNotBlank())
        assertTrue("nothing is returned for sending when there is an error", r.rows.isEmpty())
    }

    @Test fun parsesARow() {
        val r = parseImport(line10)
        assertEquals(listOf(row10), r.rows)
        assertTrue(r.errors.isEmpty())
    }

    @Test fun acceptsTheUCommandForm() = assertEquals(listOf(row10), parseImport("U$line10").rows)

    @Test fun ignoresBlankLinesCommentsAndSpaces() {
        val r = parseImport("# table\n\n   $line10   \r\n# end\n")
        assertEquals(listOf(row10), r.rows)
        assertTrue(r.errors.isEmpty())
    }

    @Test fun errorLinesCountTheIgnoredOnes() = assertOneError("# header\n\nabc\n", 3)

    @Test fun sortsRowsByTws() {
        val r = parseImport("${lineAt(14.0)}\n${lineAt(6.0)}\n${lineAt(10.0)}")
        assertEquals(listOf(60, 100, 140), r.rows.map { it.tws })
    }

    @Test fun aCommaDecimalIsTwelveNumbers() = assertOneError("10,0,40,-3.5,0,95,-1,0,150,2,0", 1)

    @Test fun nineNumbersIsTooFew() = assertOneError("10.0,40,-3.5,0.0,95,-1.0,0.0,150,2.0", 1)

    @Test fun aNonNumberIsReported() = assertOneError("10.0,40,x,0.0,95,-1.0,0.0,150,2.0,0.0", 1)

    @Test fun valuesOutOfRangeAreReported() {
        assertOneError("10.0,40,31.0,0.0,95,-1.0,0.0,150,2.0,0.0", 1)   // dA
        assertOneError("30.0,40,-3.5,10.1,95,-1.0,0.0,150,2.0,0.0", 1)  // dS
        assertOneError("100.1,40,-3.5,0.0,95,-1.0,0.0,150,2.0,0.0", 1)  // TWS above 100.0 kn
        assertOneError("10.0,0,-3.5,0.0,95,-1.0,0.0,150,2.0,0.0", 1)    // angle 0
    }

    @Test fun theSameTwsTwiceIsReportedOnTheSecondLine() = assertOneError("${lineAt(10.0)}\n${lineAt(10.0)}", 2)

    @Test fun moreThanTenRowsIsReportedForTheTable() =
        assertOneError((1..11).joinToString("\n") { lineAt(it * 5.0) }, 0)

    @Test fun aTableThatBreaksTheSlopeRuleIsReportedForTheTable() {
        val a = "6.0,40,0.0,0.0,95,0.0,0.0,150,0.0,0.0"
        val b = "10.0,40,0.0,2.1,95,0.0,0.0,150,0.0,0.0"   // dS 2.1 kn over a 4.0 kn gap
        assertOneError("$a\n$b", 0)
    }

    @Test fun roundsHalfAwayFromZeroLikeTheFirmware() {
        val r = parseImport("10.0,40,-3.55,0.0,95,-1.0,0.0,150,2.0,0.0")
        assertEquals(-36, r.rows.single().anchors[0].dA)
    }
}
