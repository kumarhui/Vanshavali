package com.shivam.vanshavali.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shivam.vanshavali.model.PersonNode
import com.shivam.vanshavali.model.SavedFamilyTree
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CIRCLE_RADIUS = 18f
private const val NODE_BOX_WIDTH = 64f
private const val LEVEL_HEIGHT = 100f
private const val SIBLING_DISTANCE = 74f

private val GenerationColors = listOf(
    Color(0xFF00BFA5),
    Color(0xFFFFA000),
    Color(0xFF7E57C2),
    Color(0xFFEC407A),
    Color(0xFF26A69A),
    Color(0xFF5C6BC0)
)

data class RenderableNode(
    val node: PersonNode,
    val x: Float,
    val y: Float,
    val depth: Int,
    val children: List<RenderableNode>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyTreeViewerScreen(
    tree: SavedFamilyTree,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val collapsedIds = remember { mutableStateMapOf<String, Boolean>() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isInteracting by remember { mutableStateOf(false) }
    var hasAutoCentered by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isInteracting) {
        if (isInteracting) {
            delay(1500)
            isInteracting = false
        }
    }

    val layoutRoot = remember(tree, collapsedIds.toMap()) {
        buildBalancedTree(tree.root, collapsedIds)
    }

    val treeBounds = remember(layoutRoot) { computeTreeBounds(layoutRoot) }

    fun fitTreeToScreen() {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val padding = 40f
            val availW = canvasSize.width.toFloat() - (padding * 2)
            val availH = canvasSize.height.toFloat() - (padding * 2)
            val fitScale = min(availW / max(treeBounds.width, 1f), availH / max(treeBounds.height, 1f)).coerceIn(0.2f, 1.2f)
            scale = fitScale
            offset = Offset(
                x = ((canvasSize.width - (treeBounds.width * fitScale)) / 2f) - (treeBounds.minX * fitScale),
                y = padding + 15f - (treeBounds.minY * fitScale)
            )
        }
    }

    LaunchedEffect(canvasSize, layoutRoot) {
        if (!hasAutoCentered && canvasSize.width > 0) {
            fitTreeToScreen()
            hasAutoCentered = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tree.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { fitTreeToScreen() }) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Fit to Screen")
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share JSON") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val json = Json { prettyPrint = true }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, tree.title)
                                    putExtra(Intent.EXTRA_TEXT, "${tree.title}\n\n" + json.encodeToString(tree.root))
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Tree JSON"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Print / PDF") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showPrintDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFFBFBFB))
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.12f, 3.5f)
                        offset += pan
                        isInteracting = true
                    }
                }
                .pointerInput(layoutRoot, scale, offset) {
                    detectTapGestures { tapOffset ->
                        fun checkHit(node: RenderableNode): Boolean {
                            val screenCenterX = node.x * scale + offset.x
                            val screenCenterY = (node.y + CIRCLE_RADIUS) * scale + offset.y
                            val hitRadius = (CIRCLE_RADIUS + 12f) * scale

                            val dx = tapOffset.x - screenCenterX
                            val dy = tapOffset.y - screenCenterY
                            if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                                if (node.node.children.isNotEmpty()) {
                                    val isCollapsed = collapsedIds[node.node.id] == true
                                    collapsedIds[node.node.id] = !isCollapsed
                                }
                                return true
                            }
                            return node.children.any { checkHit(it) }
                        }
                        checkHit(layoutRoot)
                    }
                }
        ) {
            val wireColor = Color(0xFF90A4AE)

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTreeOnCanvas(
                    scope = this,
                    root = layoutRoot,
                    scale = scale,
                    offset = offset,
                    wireColor = wireColor,
                    collapsedIds = collapsedIds
                )
            }

            AnimatedVisibility(
                visible = isInteracting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                ) {
                    Text(
                        text = "${(scale * 100).roundToInt()}%",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showPrintDialog) {
            val fullLayoutRoot = remember(tree) {
                buildBalancedTree(tree.root, emptyMap())
            }

            A4PrintPreviewDialog(
                title = tree.title,
                layoutRoot = fullLayoutRoot,
                onDismiss = { showPrintDialog = false },
                onPrint = {
                    printFamilyTree(context, tree.title, fullLayoutRoot)
                }
            )
        }
    }
}

private fun drawTreeOnCanvas(
    scope: DrawScope,
    root: RenderableNode,
    scale: Float,
    offset: Offset,
    wireColor: Color,
    collapsedIds: Map<String, Boolean>
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
        if (parent.children.isNotEmpty()) {
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

        node.children.forEach { drawNodes(it) }
    }
    drawNodes(root)
}

private data class TreeBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

private fun computeTreeBounds(root: RenderableNode): TreeBounds {
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

@Composable
fun A4PrintPreviewDialog(
    title: String,
    layoutRoot: RenderableNode,
    onDismiss: () -> Unit,
    onPrint: () -> Unit
) {
    val bounds = remember(layoutRoot) { computeTreeBounds(layoutRoot) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "A4 Print Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "210 × 297 mm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f / 1.414f)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val sheetW = size.width
                            val sheetH = size.height
                            val margin = 20f
                            val usableW = sheetW - (margin * 2)
                            val usableH = sheetH - (margin * 2)

                            val autoScale = min(usableW / max(bounds.width, 1f), usableH / max(bounds.height, 1f))
                            val fittedW = bounds.width * autoScale
                            val fittedH = bounds.height * autoScale
                            val offsetX = margin + ((usableW - fittedW) / 2f) - (bounds.minX * autoScale)
                            val offsetY = margin + ((usableH - fittedH) / 2f) - (bounds.minY * autoScale)

                            drawTreeOnCanvas(
                                scope = this,
                                root = layoutRoot,
                                scale = autoScale,
                                offset = Offset(offsetX, offsetY),
                                wireColor = Color(0xFF546E7A),
                                collapsedIds = emptyMap()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onPrint()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print / Save PDF")
                    }
                }
            }
        }
    }
}

private fun printFamilyTree(context: Context, title: String, root: RenderableNode) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "${title.replace("\\s+".toRegex(), "_")}_A4_Print"

    printManager.print(jobName, object : PrintDocumentAdapter() {
        private var pdfDocument: PrintedPdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            pdfDocument = PrintedPdfDocument(context, newAttributes ?: return)
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            val page = pdfDocument?.startPage(0) ?: return
            val pdfCanvas = page.canvas

            val bounds = computeTreeBounds(root)
            val margin = 36f
            val usableW = pdfCanvas.width - (margin * 2)
            val usableH = pdfCanvas.height - (margin * 2)

            val autoScale = min(usableW / max(bounds.width, 1f), usableH / max(bounds.height, 1f))
            val fittedW = bounds.width * autoScale
            val fittedH = bounds.height * autoScale
            val offsetX = margin + ((usableW - fittedW) / 2f) - (bounds.minX * autoScale)
            val offsetY = margin + ((usableH - fittedH) / 2f) - (bounds.minY * autoScale)

            val composeCanvas = Canvas(pdfCanvas)
            val drawScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
            drawScope.draw(
                density = androidx.compose.ui.unit.Density(1f),
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
                canvas = composeCanvas,
                size = androidx.compose.ui.geometry.Size(pdfCanvas.width.toFloat(), pdfCanvas.height.toFloat())
            ) {
                drawTreeOnCanvas(
                    scope = this,
                    root = root,
                    scale = autoScale,
                    offset = Offset(offsetX, offsetY),
                    wireColor = Color(0xFF455A64),
                    collapsedIds = emptyMap()
                )
            }

            pdfDocument?.finishPage(page)

            try {
                destination?.fileDescriptor?.let { fd ->
                    FileOutputStream(fd).use { out ->
                        pdfDocument?.writeTo(out)
                    }
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.localizedMessage)
            } finally {
                pdfDocument?.close()
                pdfDocument = null
            }
        }
    }, PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .build()
    )
}

// ---------------- Buchheim-Walker Balanced Tree Layout Engine ----------------
private class BNode(
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

private fun buildBalancedTree(
    root: PersonNode,
    collapsedMap: Map<String, Boolean>
): RenderableNode {
    fun createTree(person: PersonNode, depth: Int, parent: BNode?, index: Int): BNode {
        val bNode = BNode(person, depth, parent, index)
        val isCollapsed = collapsedMap[person.id] == true
        bNode.children = if (isCollapsed) emptyList() else person.children.mapIndexed { i, child ->
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
