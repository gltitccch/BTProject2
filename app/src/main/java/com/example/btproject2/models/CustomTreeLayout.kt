package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Custom node position coordinate override for an individual member card.
 */
@IgnoreExtraProperties
data class CustomNodePosition(
    val x: Float = 0f,
    val y: Float = 0f
)

/**
 * Custom bus shelf height and elbow routing for a family union bus line.
 * @param busId The primary person ID representing this parental union.
 * @param yBus The vertical Y level of the horizontal sibling distribution shelf.
 * @param dropX Optional horizontal X offset for custom elbow routing.
 */
@IgnoreExtraProperties
data class CustomBusOffset(
    val busId: String = "",
    val yBus: Float = 0f,
    val dropX: Float? = null
)

/**
 * Top-level custom layout configuration persisted in Cloud Firestore.
 * Single source of truth for customized card placements and line bends.
 */
@IgnoreExtraProperties
data class CustomTreeLayout(
    val treeId: String = "",
    val nodePositions: Map<String, CustomNodePosition> = emptyMap(),
    val busOffsets: Map<String, CustomBusOffset> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Step-by-step layout history actions recorded for atomic 1-step Undo operations.
 */
sealed class LayoutHistoryAction {
    data class MoveNode(
        val personId: String,
        val spouseId: String?,
        val prevX: Float,
        val prevY: Float,
        val newX: Float,
        val newY: Float,
        val prevSpouseX: Float? = null,
        val prevSpouseY: Float? = null,
        val newSpouseX: Float? = null,
        val newSpouseY: Float? = null
    ) : LayoutHistoryAction()

    data class BendLine(
        val busId: String,
        val prevYBus: Float,
        val newYBus: Float,
        val prevDropX: Float? = null,
        val newDropX: Float? = null
    ) : LayoutHistoryAction()
}
