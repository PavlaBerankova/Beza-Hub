package com.berpod.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOrderTest {

    private val order = listOf(1L, 2L, 3L, 4L, 5L)

    @Test
    fun `moves an entry down after its new predecessor`() {
        assertEquals(listOf(2L, 3L, 1L, 4L, 5L), reorderQueue(order, movedId = 1, afterId = 3, beforeId = 4))
    }

    @Test
    fun `moves an entry up after its new predecessor`() {
        assertEquals(listOf(1L, 4L, 2L, 3L, 5L), reorderQueue(order, movedId = 4, afterId = 1, beforeId = 2))
    }

    @Test
    fun `drop at the top of the visible list goes right before the successor`() {
        // Visible (filtered) entries are 2, 4, 5; 5 is dropped above 2.
        assertEquals(listOf(1L, 5L, 2L, 3L, 4L), reorderQueue(order, movedId = 5, afterId = null, beforeId = 2))
    }

    @Test
    fun `filtered move keeps hidden entries in relative order`() {
        // Visible entries are 1, 3, 5 (2 and 4 hidden); 1 is dropped between 3 and 5.
        val result = reorderQueue(order, movedId = 1, afterId = 3, beforeId = 5)
        assertEquals(listOf(2L, 3L, 1L, 4L, 5L), result)
        assertEquals(listOf(2L, 4L), result.filter { it == 2L || it == 4L })
    }

    @Test
    fun `unknown moved id leaves order unchanged`() {
        assertEquals(order, reorderQueue(order, movedId = 99, afterId = 1, beforeId = 2))
    }

    @Test
    fun `unknown anchors leave order unchanged`() {
        assertEquals(order, reorderQueue(order, movedId = 3, afterId = 98, beforeId = 99))
        assertEquals(order, reorderQueue(order, movedId = 3, afterId = null, beforeId = null))
    }

    @Test
    fun `anchorsFor returns the neighbours in the given order`() {
        assertEquals(3L to 4L, anchorsFor(listOf(2L, 3L, 1L, 4L), movedId = 1))
        assertEquals(null to 2L, anchorsFor(listOf(1L, 2L), movedId = 1))
        assertEquals(1L to null, anchorsFor(listOf(1L, 2L), movedId = 2))
        assertEquals(null to null, anchorsFor(listOf(1L, 2L), movedId = 9))
    }

    @Test
    fun `moveAfterDrag stores a drag back to the original place`() {
        // First drag stored B,C,A; the second drag moves A back to the top.
        assertEquals(null to 2L, moveAfterDrag(startOrder = listOf(2L, 3L, 1L), endOrder = listOf(1L, 2L, 3L), movedId = 1))
    }

    @Test
    fun `moveAfterDrag returns null when the drag ended where it started`() {
        assertEquals(null, moveAfterDrag(startOrder = listOf(1L, 2L, 3L), endOrder = listOf(1L, 2L, 3L), movedId = 2))
    }

    @Test
    fun `stepAnchors moves one place within the given order`() {
        val visible = listOf(10L, 20L, 30L)
        assertEquals(null to 10L, stepAnchors(visible, movedId = 20, up = true))
        assertEquals(30L to null, stepAnchors(visible, movedId = 20, up = false))
        assertEquals(null, stepAnchors(visible, movedId = 10, up = true))
        assertEquals(null, stepAnchors(visible, movedId = 30, up = false))
        assertEquals(null, stepAnchors(visible, movedId = 99, up = true))
    }
}
