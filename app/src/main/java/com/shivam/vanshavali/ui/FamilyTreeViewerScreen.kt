package com.shivam.vanshavali.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.core.content.FileProvider
import com.shivam.vanshavali.data.TranslationHelper
import com.shivam.vanshavali.model.PersonNode
import com.shivam.vanshavali.model.SavedFamilyTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CIRCLE_RADIUS = 18f
private const val NODE_BOX_WIDTH = 64f
private const val LEVEL_HEIGHT = 100f
private const val SIBLING_DISTANCE = 74f

private val GenerationColors = listOf(
    Color(0xFF00BFA5), // Teal
    Color(0xFFFFA000), // Amber
    Color(0xFF7E57C2), // Purple
    Color(0xFFEC407A), // Rose / Pink
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyTreeViewerScreen(
    tree: SavedFamilyTree,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val collapsedIds = remember { mutableStateMapOf<String, Boolean>() }
    var selectedPersonId by remember { mutableStateOf<String?>(tree.root.id) }

    // Translation States
    var currentRootNode by remember(tree) { mutableStateOf(tree.root) }
    var isHindiActive by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }

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

    // Geometry is computed from currentRootNode (coordinates stay stable)
    val staticLayoutRoot = remember(currentRootNode) {
        buildBalancedTree(currentRootNode)
    }

    val treeBounds = remember(staticLayoutRoot) { computeTreeBounds(staticLayoutRoot) }

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

    LaunchedEffect(canvasSize, staticLayoutRoot) {
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
                    // Language Toggle Icon (EN <-> HI)
                    ActionIconButtonWithTooltip(
                        tooltipText = if (isHindiActive) "Switch to English" else "Translate to Hindi",
                        icon = Icons.Default.Translate,
                        enabled = !isTranslating,
                        onClick = {
                            scope.launch {
                                isTranslating = true
                                Toast.makeText(
                                    context,
                                    if (!isHindiActive) "Translating to Hindi..." else "Translating to English...",
                                    Toast.LENGTH_SHORT
                                ).show()
                                val targetHindi = !isHindiActive
                                val translatedRoot = TranslationHelper.translateTree(tree.root, toHindi = targetHindi)
                                currentRootNode = translatedRoot
                                isHindiActive = targetHindi
                                isTranslating = false
                            }
                        }
                    )

                    ActionIconButtonWithTooltip(
                        tooltipText = "Collapse Selected",
                        icon = Icons.Default.UnfoldLess,
                        enabled = selectedPersonId != null,
                        onClick = {
                            selectedPersonId?.let { id -> collapsedIds[id] = true }
                        }
                    )

                    ActionIconButtonWithTooltip(
                        tooltipText = "Expand Selected",
                        icon = Icons.Default.UnfoldMore,
                        enabled = selectedPersonId != null,
                        onClick = {
                            selectedPersonId?.let { id -> collapsedIds[id] = false }
                        }
                    )

                    ActionIconButtonWithTooltip(
                        tooltipText = "Fit to Screen",
                        icon = Icons.Default.CenterFocusStrong,
                        onClick = { fitTreeToScreen() }
                    )

                    ActionIconButtonWithTooltip(
                        tooltipText = "Options",
                        icon = Icons.Default.MoreVert,
                        onClick = { showMenu = !showMenu }
                    )

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
                                    putExtra(Intent.EXTRA_TEXT, "${tree.title}\n\n" + json.encodeToString(currentRootNode))
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Tree JSON"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Download JSON") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    downloadJsonFile(context, tree.title, Json { prettyPrint = true }.encodeToString(currentRootNode))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Print / Export") },
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
                .pointerInput(staticLayoutRoot, scale, offset) {
                    detectTapGestures { tapOffset ->
                        fun checkHit(node: RenderableNode): Boolean {
                            val screenCenterX = node.x * scale + offset.x
                            val screenCenterY = (node.y + CIRCLE_RADIUS) * scale + offset.y
                            val hitRadius = (CIRCLE_RADIUS + 14f) * scale

                            val dx = tapOffset.x - screenCenterX
                            val dy = tapOffset.y - screenCenterY
                            if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                                selectedPersonId = node.node.id
                                if (node.node.children.isNotEmpty()) {
                                    val isCollapsed = collapsedIds[node.node.id] == true
                                    collapsedIds[node.node.id] = !isCollapsed
                                }
                                return true
                            }
                            val isNodeCollapsed = collapsedIds[node.node.id] == true
                            if (!isNodeCollapsed) {
                                return node.children.any { checkHit(it) }
                            }
                            return false
                        }
                        checkHit(staticLayoutRoot)
                    }
                }
        ) {
            val wireColor = Color(0xFF90A4AE)

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTreeOnCanvas(
                    scope = this,
                    root = staticLayoutRoot,
                    scale = scale,
                    offset = offset,
                    wireColor = wireColor,
                    collapsedIds = collapsedIds,
                    selectedPersonId = selectedPersonId
                )
            }

            // Translation in-progress badge
            if (isTranslating) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(
                            text = "Translating names...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
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
            A4PrintPreviewDialog(
                title = tree.title,
                layoutRoot = staticLayoutRoot,
                onDismiss = { showPrintDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionIconButtonWithTooltip(
    tooltipText: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltipText)
            }
        },
        state = tooltipState
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltipText,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bounds = remember(layoutRoot) { computeTreeBounds(layoutRoot) }
    var isLandscape by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .padding(vertical = 10.dp)
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
                    Column {
                        Text(
                            text = "Export & Print",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isLandscape) "A4 Landscape (297 × 210 mm)" else "A4 Portrait (210 × 297 mm)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    OutlinedButton(
                        onClick = { isLandscape = !isLandscape },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isLandscape) Icons.Default.CropPortrait else Icons.Default.CropLandscape,
                            contentDescription = "Change Layout",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isLandscape) "Portrait" else "Landscape")
                    }
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
                    val aspectRatio = if (isLandscape) 1.414f / 1f else 1f / 1.414f

                    Surface(
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(aspectRatio, matchHeightConstraintsFirst = !isLandscape)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val sheetW = size.width
                            val sheetH = size.height
                            val margin = 16f
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
                                collapsedIds = emptyMap(),
                                selectedPersonId = null
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    val bmp = generateTreeBitmap(layoutRoot, bounds, isLandscape)
                                    saveBitmapToGallery(context, title, bmp)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download Image")
                        }

                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    val bmp = generateTreeBitmap(layoutRoot, bounds, isLandscape)
                                    val file = saveCacheBitmap(context, bmp, title)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        setPackage("com.whatsapp")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(shareIntent)
                                    } catch (_: Exception) {
                                        shareIntent.setPackage("com.whatsapp.w4b")
                                        try {
                                            context.startActivity(shareIntent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "WhatsApp Share")
                        }

                        FilledIconButton(
                            onClick = {
                                scope.launch {
                                    val bmp = generateTreeBitmap(layoutRoot, bounds, isLandscape)
                                    val file = saveCacheBitmap(context, bmp, title)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val printIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        setPackage("com.nokoprint")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(printIntent)
                                    } catch (_: Exception) {
                                        printIntent.setPackage("com.noco.print")
                                        try {
                                            context.startActivity(printIntent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "NokoPrint app not installed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Print Page")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun generateTreeBitmap(
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

private suspend fun saveCacheBitmap(context: Context, bitmap: Bitmap, title: String): File = withContext(Dispatchers.IO) {
    val cleanTitle = title.replace("\\s+".toRegex(), "_")
    val file = File(context.cacheDir, "${cleanTitle}_print.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    file
}

private suspend fun saveBitmapToGallery(context: Context, title: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
    val filename = "${title.replace("\\s+".toRegex(), "_")}_${System.currentTimeMillis()}.png"
    var outputStream: OutputStream? = null

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Vanshavali")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                outputStream = context.contentResolver.openOutputStream(uri)
            }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/Vanshavali"
            val dir = File(imagesDir).apply { mkdirs() }
            val file = File(dir, filename)
            outputStream = FileOutputStream(file)
        }

        outputStream?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Image saved to Pictures/Vanshavali!", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to save image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun downloadJsonFile(context: Context, title: String, jsonContent: String) = withContext(Dispatchers.IO) {
    val filename = "${title.replace("\\s+".toRegex(), "_")}_tree.json"
    var outputStream: OutputStream? = null

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                outputStream = context.contentResolver.openOutputStream(uri)
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)
            outputStream = FileOutputStream(file)
        }

        outputStream?.use { out ->
            out.write(jsonContent.toByteArray())
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "JSON saved to Downloads/$filename", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to save JSON: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

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

private fun buildBalancedTree(root: PersonNode): RenderableNode {
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
