package app.ownplay.mobile.feature.settings.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualOrderPolicyTest {
    @Test
    fun `move swaps one position in requested direction`() {
        assertEquals(listOf("b", "a", "c"), ManualOrderPolicy.move(listOf("a", "b", "c"), "a", 1))
        assertEquals(listOf("a", "c", "b"), ManualOrderPolicy.move(listOf("a", "b", "c"), "c", -1))
    }

    @Test
    fun `move is stable at boundaries and for unknown ids`() {
        val original = listOf("a", "b", "c")
        assertEquals(original, ManualOrderPolicy.move(original, "a", -1))
        assertEquals(original, ManualOrderPolicy.move(original, "c", 1))
        assertEquals(original, ManualOrderPolicy.move(original, "missing", 1))
    }
}
