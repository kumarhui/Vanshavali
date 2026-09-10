package com.shivam.vanshavali.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

fun drawTreeOnCanvas(
    scope: DrawScope,
    root: RenderableNode,
    scale: Float,
    offset: Offset,
    wireColor: Color,
    collapsedIds: Map<String, Boolean>,
    selectedPersonId: String?
) {
    val nativeCanvas = scope.drawContext.canvas.nativeCanvas

    val textPaint = Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 10f * scale
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    val indicatorPaint = Paint().apply {
        textSize = 8.5f * scale
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    fun drawConnectors(parent: RenderableNode) {
        val isCollapsed = collapsedIds[parent.node.id] == true
        if (parent.children.isNotEmpty() && !isCollapsed) {
            val parentX = parent.x * scale + offset.x
            val parentBottomY = (parent.y + (CIRCLE_RADIUS * 2) + 20f) * scale + offset.y
            val busY = (parent.y + (CIRCLE_RADIUS * 2) + 36f) * scale + offset.y

            scope.drawLine(
                color = wireColor,
                start = Offset(parentX, parentBottomY),
                end = Offset(parentX, busY),
                strokeWidth = 1.8f * scale
            )

            val firstChildX = parent.children.first().x * scale + offset.x
            val lastChildX = parent.children.last().x * scale + offset.x

            if (parent.children.size > 1) {
                scope.drawLine(
                    color = wireColor,
                    start = Offset(firstChildX, busY),
                    end = Offset(lastChildX, busY),
                    strokeWidth = 1.8f * scale
                )
            }

            parent.children.forEach { child ->
                val childX = child.x * scale + offset.x
                val childCircleTop = child.y * scale + offset.y

                scope.drawLine(
                    color = wireColor,
                    start = Offset(childX, busY),
                    end = Offset(childX, childCircleTop),
                    strokeWidth = 1.8f * scale
                )

                val s = 4.5f * scale
                val arrowPath = Path().apply {
                    moveTo(childX - s, childCircleTop - s * 1.5f)
                    lineTo(childX, childCircleTop)
                    lineTo(childX + s, childCircleTop - s * 1.5f)
                }
                scope.drawPath(
                    path = arrowPath,
                    color = wireColor,
                    style = Stroke(width = 1.8f * scale)
                )

                drawConnectors(child)
            }
        }
    }
    drawConnectors(root)

    fun drawNodes(node: RenderableNode) {
        val cx = node.x * scale + offset.x
        val cy = (node.y + CIRCLE_RADIUS) * scale + offset.y
        val r = CIRCLE_RADIUS * scale
        val themeColor = GenerationColors[node.depth % GenerationColors.size]
        val isCollapsed = collapsedIds[node.node.id] == true
        val isSelected = node.node.id == selectedPersonId

        if (isSelected) {
            scope.drawCircle(
                color = Color(0xFF2979FF).copy(alpha = 0.25f),
                radius = r + (8f * scale),
                center = Offset(cx, cy)
            )
            scope.drawCircle(
                color = Color(0xFF2979FF),
                radius = r + (5f * scale),
                center = Offset(cx, cy),
                style = Stroke(width = 2.2f * scale)
            )
        }

        scope.drawCircle(
            color = Color.White,
            radius = r,
            center = Offset(cx, cy)
        )

        scope.drawCircle(
            color = themeColor,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 2.2f * scale)
        )

        val headR = 3.8f * scale
        scope.drawCircle(
            color = themeColor,
            radius = headR,
            center = Offset(cx, cy - 2.8f * scale)
        )
        scope.drawArc(
            color = themeColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - 6.5f * scale, cy + 1.8f * scale),
            size = androidx.compose.ui.geometry.Size(13f * scale, 11f * scale)
        )

        val textY = cy + r + 12f * scale
        scope.drawIntoCanvas {
            nativeCanvas.drawText(node.node.name, cx, textY, textPaint)
            if (node.node.children.isNotEmpty() && isCollapsed) {
                indicatorPaint.color = themeColor.toArgb()
                nativeCanvas.drawText("+${node.node.children.size}", cx, textY + 10f * scale, indicatorPaint)
            }
        }

        if (!isCollapsed) {
            node.children.forEach { drawNodes(it) }
        }
    }
    drawNodes(root)
}

suspend fun generateTreeBitmap(
    root: RenderableNode,
    bounds: TreeBounds,
    isLandscape: Boolean
): Bitmap = withContext(Dispatchers.Default) {
    val width = if (isLandscape) 2480 else 1754
    val height = if (isLandscape) 1754 else 2480

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val margin = 80f
    val usableW = width - (margin * 2)
    val usableH = height - (margin * 2)

    val autoScale = min(usableW / max(bounds.width, 1f), usableH / max(bounds.height, 1f))
    val fittedW = bounds.width * autoScale
    val fittedH = bounds.height * autoScale
    val offsetX = margin + ((usableW - fittedW) / 2f) - (bounds.minX * autoScale)
    val offsetY = margin + ((usableH - fittedH) / 2f) - (bounds.minY * autoScale)

    val composeCanvas = Canvas(canvas)
    val drawScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    drawScope.draw(
        density = androidx.compose.ui.unit.Density(2f),
        layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        canvas = composeCanvas,
        size = androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())
    ) {
        drawTreeOnCanvas(
            scope = this,
            root = root,
            scale = autoScale,
            offset = Offset(offsetX, offsetY),
            wireColor = Color(0xFF455A64),
            collapsedIds = emptyMap(),
            selectedPersonId = null
        )
    }

    bitmap
}
