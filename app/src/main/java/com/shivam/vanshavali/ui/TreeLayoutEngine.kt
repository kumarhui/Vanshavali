package com.shivam.vanshavali.ui

import androidx.compose.ui.graphics.Color
import com.shivam.vanshavali.model.PersonNode
import kotlin.math.max
import kotlin.math.min

const val CIRCLE_RADIUS = 18f
const val NODE_BOX_WIDTH = 64f
const val LEVEL_HEIGHT = 100f
const val SIBLING_DISTANCE = 74f

val GenerationColors = listOf(
    Color(0xFF00BFA5), // Teal
    Color(0xFFFFA000), // Amber
    Color(0xFF7E57C2), // Purple
    Color(0xFFEC407A), // Rose
    Color(0xFF26A69A), // Cyan
    Color(0xFF5C6BC0)  // Indigo
)

data class RenderableNode(
    val node: PersonNode,
    val x: Float,
    val y: Float,
    val depth: Int,
    val children: List<RenderableNode>
)

data class TreeBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

fun computeTreeBounds(root: RenderableNode): TreeBounds {
    var minX = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE

    fun walk(n: RenderableNode) {
        minX = min(minX, n.x - (NODE_BOX_WIDTH / 2f))
        maxX = max(maxX, n.x + (NODE_BOX_WIDTH / 2f))
        minY = min(minY, n.y)
        maxY = max(maxY, n.y + (CIRCLE_RADIUS * 2) + 26f)
        n.children.forEach { walk(it) }
    }
    walk(root)
    return TreeBounds(minX, maxX, minY, maxY)
}

internal class BNode(
    val person: PersonNode,
    val depth: Int,
    val parent: BNode?,
    val number: Int,
    var children: List<BNode> = emptyList()
) {
    var x: Float = -1f
    var mod: Float = 0f
    var prelim: Float = 0f
    var change: Float = 0f
    var shift: Float = 0f
    var ancestor: BNode = this
    var thread: BNode? = null

    fun left(): BNode? = thread ?: children.firstOrNull()
    fun right(): BNode? = thread ?: children.lastOrNull()
    fun leftSibling(): BNode? = if (parent != null && number > 0) parent.children[number - 1] else null
}

fun buildBalancedTree(root: PersonNode): RenderableNode {
    fun createTree(person: PersonNode, depth: Int, parent: BNode?, index: Int): BNode {
        val bNode = BNode(person, depth, parent, index)
        bNode.children = person.children.mapIndexed { i, child ->
            createTree(child, depth + 1, bNode, i)
        }
        return bNode
    }

    val bRoot = createTree(root, 0, null, 0)
    firstWalkBW(bRoot)
    secondWalkBW(bRoot, -bRoot.prelim)

    fun toRenderable(bn: BNode): RenderableNode {
        return RenderableNode(
            node = bn.person,
            x = bn.x,
            y = bn.depth * LEVEL_HEIGHT + 30f,
            depth = bn.depth,
            children = bn.children.map { toRenderable(it) }
        )
    }

    return toRenderable(bRoot)
}

private fun firstWalkBW(v: BNode) {
    if (v.children.isEmpty()) {
        val left = v.leftSibling()
        v.prelim = if (left != null) left.prelim + SIBLING_DISTANCE else 0f
    } else {
        var defaultAncestor = v.children.first()
        v.children.forEach { w ->
            firstWalkBW(w)
            defaultAncestor = apportion(w, defaultAncestor)
        }
        executeShifts(v)
        val midpoint = (v.children.first().prelim + v.children.last().prelim) / 2f
        val left = v.leftSibling()
        if (left != null) {
            v.prelim = left.prelim + SIBLING_DISTANCE
            v.mod = v.prelim - midpoint
        } else {
            v.prelim = midpoint
        }
    }
}

private fun apportion(v: BNode, defaultAncestor: BNode): BNode {
    val leftSibling = v.leftSibling() ?: return defaultAncestor

    var vip: BNode? = v
    var vop: BNode? = v
    var vim: BNode? = leftSibling
    var vom: BNode? = vip?.parent?.children?.firstOrNull()

    var sip = vip?.mod ?: 0f
    var sop = vop?.mod ?: 0f
    var sim = vim?.mod ?: 0f
    var som = vom?.mod ?: 0f

    while (vim?.right() != null && vip?.left() != null) {
        val nextVim = vim?.right()
        val nextVip = vip?.left()
        val nextVom = vom?.left()
        val nextVop = vop?.right()

        vim = nextVim
        vip = nextVip
        vom = nextVom
        vop = nextVop

        vop?.ancestor = v
        val vimPrelim = vim?.prelim ?: 0f
        val vipPrelim = vip?.prelim ?: 0f
        val shift = (vimPrelim + sim) - (vipPrelim + sip) + SIBLING_DISTANCE

        if (shift > 0f) {
            val ancestorNode = vim?.let { ancestor(it, v, defaultAncestor) } ?: defaultAncestor
            moveSubtree(ancestorNode, v, shift)
            sip += shift
            sop += shift
        }

        sim += vim?.mod ?: 0f
        sip += vip?.mod ?: 0f
        som += vom?.mod ?: 0f
        sop += vop?.mod ?: 0f
    }

    if (vim?.right() != null && vop?.right() == null) {
        vop?.thread = vim?.right()
        vop?.mod = (vop?.mod ?: 0f) + sim - sop
    }
    if (vip?.left() != null && vom?.left() == null) {
        vom?.thread = vip?.left()
        vom?.mod = (vom?.mod ?: 0f) + sip - som
        return v
    }
    return defaultAncestor
}

private fun moveSubtree(wl: BNode, wr: BNode, shift: Float) {
    val subtrees = wr.number - wl.number
    if (subtrees > 0) {
        wr.change -= shift / subtrees
        wr.shift += shift
        wl.change += shift / subtrees
        wr.prelim += shift
        wr.mod += shift
    }
}

private fun executeShifts(v: BNode) {
    var shift = 0f
    var change = 0f
    for (i in v.children.indices.reversed()) {
        val w = v.children[i]
        w.prelim += shift
        w.mod += shift
        change += w.change
        shift += w.shift + change
    }
}

private fun ancestor(vim: BNode, v: BNode, defaultAncestor: BNode): BNode {
    return if (v.parent != null && v.parent.children.contains(vim.ancestor)) {
        vim.ancestor
    } else {
        defaultAncestor
    }
}

private fun secondWalkBW(v: BNode, m: Float) {
    v.x = v.prelim + m
    v.children.forEach { secondWalkBW(it, m + v.mod) }
}
