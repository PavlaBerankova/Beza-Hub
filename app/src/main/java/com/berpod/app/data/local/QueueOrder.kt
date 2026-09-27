package com.berpod.app.data.local

/**
 * New full queue order after moving [movedId]: right after [afterId] if present, otherwise right
 * before [beforeId]. Returns [currentOrder] unchanged when the moved id or both anchors are missing.
 */
fun reorderQueue(currentOrder: List<Long>, movedId: Long, afterId: Long?, beforeId: Long?): List<Long> {
    if (movedId !in currentOrder) return currentOrder
    val rest = currentOrder - movedId
    val insertAt = when {
        afterId != null && afterId in rest -> rest.indexOf(afterId) + 1
        beforeId != null && beforeId in rest -> rest.indexOf(beforeId)
        else -> return currentOrder
    }
    return rest.toMutableList().apply { add(insertAt, movedId) }
}

/** (afterId, beforeId): the neighbours of [movedId] in [order], or (null, null) if it's absent. */
fun anchorsFor(order: List<Long>, movedId: Long): Pair<Long?, Long?> {
    val index = order.indexOf(movedId)
    if (index < 0) return null to null
    return order.getOrNull(index - 1) to order.getOrNull(index + 1)
}

/**
 * The (afterId, beforeId) move to store after a drag, or null when the drag ended where it started.
 * [startOrder] must be the order captured when the drag began, not an older list.
 */
fun moveAfterDrag(startOrder: List<Long>, endOrder: List<Long>, movedId: Long): Pair<Long?, Long?>? =
    if (startOrder == endOrder) null else anchorsFor(endOrder, movedId)

/** The (afterId, beforeId) move one place up or down within [order], or null at the edge. */
fun stepAnchors(order: List<Long>, movedId: Long, up: Boolean): Pair<Long?, Long?>? {
    val index = order.indexOf(movedId)
    if (index < 0) return null
    return if (up) {
        order.getOrNull(index - 1)?.let { above -> null to above }
    } else {
        order.getOrNull(index + 1)?.let { below -> below to null }
    }
}
