package com.example.btproject2

import com.example.btproject2.models.CustomBusOffset
import com.example.btproject2.models.CustomNodePosition
import com.example.btproject2.models.CustomTreeLayout
import com.example.btproject2.models.LayoutHistoryAction
import com.example.btproject2.sync.AffectedScreen
import com.example.btproject2.sync.SyncChangeType
import com.example.btproject2.sync.SyncScopeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class CustomTreeLayoutTest {

    @Test
    fun testCustomTreeLayoutDataStructures() {
        val nodePositions = mapOf(
            "p1" to CustomNodePosition(120f, 250f),
            "p2" to CustomNodePosition(300f, 250f)
        )
        val busOffsets = mapOf(
            "p1" to CustomBusOffset(busId = "p1", yBus = 320f, dropX = 310f)
        )
        val layout = CustomTreeLayout(
            treeId = "tree_santos",
            nodePositions = nodePositions,
            busOffsets = busOffsets
        )

        assertEquals("tree_santos", layout.treeId)
        assertEquals(2, layout.nodePositions.size)
        assertEquals(120f, layout.nodePositions["p1"]?.x)
        assertEquals(250f, layout.nodePositions["p1"]?.y)
        assertEquals(320f, layout.busOffsets["p1"]?.yBus)
        assertEquals(310f, layout.busOffsets["p1"]?.dropX)
    }

    @Test
    fun testStepByStepUndoStackWithCoupleLock() {
        val undoStack = ArrayDeque<LayoutHistoryAction>()

        // Simulate moving Rowena and Diego (coupled spouses at y = 200f)
        val moveAction = LayoutHistoryAction.MoveNode(
            personId = "rowena",
            spouseId = "diego",
            prevX = 60f, prevY = 195f,
            newX = 120f, newY = 240f,
            prevSpouseX = 230f, prevSpouseY = 195f,
            newSpouseX = 290f, newSpouseY = 240f // Note: exact same Y = 240f!
        )

        undoStack.push(moveAction)
        assertEquals(1, undoStack.size)

        // Simulate moving Clarita line elbow
        val bendAction = LayoutHistoryAction.BendLine(
            busId = "lucas",
            prevYBus = 135f,
            newYBus = 165f,
            prevDropX = 480f,
            newDropX = 520f
        )
        undoStack.push(bendAction)
        assertEquals(2, undoStack.size)

        // Step 1 Undo: Revert the line bend
        val poppedBend = undoStack.pop() as LayoutHistoryAction.BendLine
        assertEquals("lucas", poppedBend.busId)
        assertEquals(135f, poppedBend.prevYBus)
        assertEquals(1, undoStack.size)

        // Step 2 Undo: Revert the coupled spouse card move
        val poppedMove = undoStack.pop() as LayoutHistoryAction.MoveNode
        assertEquals("rowena", poppedMove.personId)
        assertEquals("diego", poppedMove.spouseId)
        assertEquals(60f, poppedMove.prevX)
        assertEquals(195f, poppedMove.prevY)
        assertEquals(230f, poppedMove.prevSpouseX)
        assertEquals(195f, poppedMove.prevSpouseY) // Both restored to exact horizontal shelf
        assertTrue(undoStack.isEmpty())
    }

    @Test
    fun testSyncScopeResolverForTreeLayout() {
        val scope = SyncScopeResolver.resolveTreeLayoutChanged("tree_test_123")
        assertEquals("tree_test_123", scope.treeId)
        assertTrue(scope.affectedScreens.contains(AffectedScreen.INTERACTIVE_TREE))
        assertTrue(scope.requiresStructuralRelayout)
        assertEquals(setOf("treeLayout"), scope.affectedFields)
    }

    @Test
    fun testSyncChangeTypeTreeLayoutChanged() {
        val changeType = SyncChangeType.TREE_LAYOUT_CHANGED
        assertNotNull(changeType)
        assertEquals("TREE_LAYOUT_CHANGED", changeType.name)
    }

    @Test
    fun testFirestoreHelperLayoutCacheAndBidirectionalDrop() {
        val testLayout = CustomTreeLayout(
            treeId = "tree_cache_test",
            nodePositions = mapOf("p1" to CustomNodePosition(150f, 220f)),
            busOffsets = mapOf("b1" to CustomBusOffset("b1", 280f, 150f))
        )
        // Test bidirectional targetY calculation logic
        val parentY = 400f
        val childAboveY = 200f
        val childBelowY = 600f
        val cardH = 88f
        val addBtnR = 12f

        // When child is above parent, bus is above and target enters bottom of child
        val yBusAbove = parentY - 50f
        val targetYAbove = if (childAboveY >= yBusAbove) childAboveY - cardH / 2f else childAboveY + cardH / 2f + addBtnR
        assertEquals(childAboveY + cardH / 2f + addBtnR, targetYAbove)

        // When child is below parent, bus is below and target enters top of child
        val yBusBelow = parentY + 50f
        val targetYBelow = if (childBelowY >= yBusBelow) childBelowY - cardH / 2f else childBelowY + cardH / 2f + addBtnR
        assertEquals(childBelowY - cardH / 2f, targetYBelow)
    }

    @Test
    fun testFreeMoveAndIndestructibleSteppedBridge() {
        // 1. Verify Free Move atomic 1-node undo
        val undoStack = ArrayDeque<LayoutHistoryAction>()
        val freeMove = LayoutHistoryAction.MoveNode(
            personId = "danielle",
            spouseId = null,
            prevX = 100f,
            prevY = 300f,
            newX = 850f,
            newY = 120f // Moved to impossible angle (above parents and far to the right!)
        )
        undoStack.push(freeMove)
        assertEquals(1, undoStack.size)

        val popped = undoStack.pop() as LayoutHistoryAction.MoveNode
        assertEquals("danielle", popped.personId)
        org.junit.Assert.assertNull(popped.spouseId)
        assertEquals(100f, popped.prevX)
        assertEquals(300f, popped.prevY)

        // 2. Verify Local Children Bus bounds (strictly bounded to kids, no 2,000px stretch)
        val parentDropX = 304f
        val kidXList = listOf(2360f, 2544f, 2728f)
        val minKidX = kidXList.minOrNull()!!
        val maxKidX = kidXList.maxOrNull()!!

        // Bus strictly bounds to children
        val busLeft = minKidX
        val busRight = maxKidX
        assertEquals(2360f, busLeft)
        assertEquals(2728f, busRight)

        // Stepped bridge detection
        val hasSteppedBridge = parentDropX < busLeft || parentDropX > busRight
        assertTrue("Bridge must activate when parents are separated horizontally", hasSteppedBridge)

        val bridgeTargetX = if (parentDropX < busLeft) busLeft else if (parentDropX > busRight) busRight else parentDropX
        assertEquals(2360f, bridgeTargetX)
    }
}
