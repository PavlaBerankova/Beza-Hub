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
