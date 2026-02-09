package io.github.jogakdal.tbeg.async

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ProgressInfoTest {

    @Test
    fun `ProgressInfo should hold processedRows and totalRows`() {
        val info = ProgressInfo(processedRows = 50, totalRows = 100)

        assertEquals(50, info.processedRows)
        assertEquals(100, info.totalRows)
    }

    @Test
    fun `ProgressInfo should allow null totalRows for streaming`() {
        val info = ProgressInfo(processedRows = 1234)

        assertEquals(1234, info.processedRows)
        assertNull(info.totalRows)
    }

    @Test
    fun `ProgressInfo should support data class features`() {
        val info1 = ProgressInfo(50, 100)
        val info2 = ProgressInfo(50, 100)
        val info3 = ProgressInfo(50, 200)

        // equals
        assertEquals(info1, info2)
        assertNotEquals(info1, info3)

        // copy
        val copied = info1.copy(processedRows = 75)
        assertEquals(75, copied.processedRows)
        assertEquals(100, copied.totalRows)

        // toString
        assertTrue(info1.toString().contains("processedRows=50"))
        assertTrue(info1.toString().contains("totalRows=100"))
    }
}
