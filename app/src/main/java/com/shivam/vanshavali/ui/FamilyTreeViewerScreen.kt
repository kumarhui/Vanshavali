package com.shivam.vanshavali.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.vanshavali.data.TranslationHelper
import com.shivam.vanshavali.model.PersonNode
import com.shivam.vanshavali.model.SavedFamilyTree
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun getMaxTreeDepth(node: PersonNode, currentDepth: Int = 0): Int {
    if (node.children.isEmpty()) return currentDepth
    return node.children.maxOf { getMaxTreeDepth(it, currentDepth + 1) }
}

private fun applyLevelExpansion(
    node: PersonNode,
    targetLevel: Int,
    currentDepth: Int,
    collapsedMap: MutableMap<String, Boolean>
) {
    if (node.children.isNotEmpty()) {
        collapsedMap[node.id] = currentDepth >= targetLevel
        node.children.forEach { child ->
            applyLevelExpansion(child, targetLevel, currentDepth + 1, collapsedMap)
        }
    }
}

private fun expandSingleLevelOnly(node: PersonNode, collapsedMap: MutableMap<String, Boolean>) {
    collapsedMap[node.id] = false
    node.children.forEach { child ->
        if (child.children.isNotEmpty()) {
            collapsedMap[child.id] = true
        }
    }
}

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

    var isHierarchyView by remember { mutableStateOf(true) }
    var showLevelSlider by remember { mutableStateOf(false) }

    var currentRootNode by remember(tree) { mutableStateOf(tree.root) }
    var isHindiActive by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }

    val maxDepth = remember(currentRootNode) { max(1, getMaxTreeDepth(currentRootNode)) }
    var currentDepthSliderValue by remember(maxDepth) { mutableFloatStateOf(maxDepth.toFloat()) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isInteracting by remember { mutableStateOf(false) }
    var hasAutoCentered by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var showMenu by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isInteracting) {
        if (isInteracting) {
            delay(1500)
            isInteracting = false
        }
    }

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
                    ActionIconButtonWithTooltip(
                        tooltipText = if (isHierarchyView) "Switch to Diagram View" else "Switch to Hierarchy View",
                        icon = if (isHierarchyView) Icons.Default.AccountTree else Icons.Default.ViewList,
                        onClick = { isHierarchyView = !isHierarchyView }
                    )

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
                        tooltipText = "Options",
                        icon = Icons.Default.MoreVert,
                        onClick = { showMenu = !showMenu }
                    )

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy JSON") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val json = Json { prettyPrint = true }.encodeToString(currentRootNode)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Family Tree JSON", json))
                                Toast.makeText(context, "Tree JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Download JSON") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    TreeExportHelper.downloadJsonFile(context, tree.title, Json { prettyPrint = true }.encodeToString(currentRootNode))
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
        ) {
            if (isHierarchyView) {
                HierarchyTreeList(
                    rootNode = currentRootNode,
                    collapsedIds = collapsedIds,
                    selectedPersonId = selectedPersonId,
                    onNodeClick = { clicked ->
                        selectedPersonId = clicked.id
                        if (clicked.children.isNotEmpty()) {
                            val isCollapsed = collapsedIds[clicked.id] == true
                            if (isCollapsed) {
                                expandSingleLevelOnly(clicked, collapsedIds)
                            } else {
                                collapsedIds[clicked.id] = true
                            }
                        }
                    },
                    onToggleCollapse = { id ->
                        val isCollapsed = collapsedIds[id] == true
                        collapsedIds[id] = !isCollapsed
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                                            if (isCollapsed) {
                                                expandSingleLevelOnly(node.node, collapsedIds)
                                            } else {
                                                collapsedIds[node.node.id] = true
                                            }
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

                    AnimatedVisibility(
                        visible = isInteracting,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
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
            }

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

            // Outside scrim tap detection to dismiss the slider card
            if (showLevelSlider) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                showLevelSlider = false
                            }
                        }
                )
            }

            // Bottom Floating Controls Section
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Depth Slider Card
                AnimatedVisibility(
                    visible = showLevelSlider,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .fillMaxWidth(0.92f)
                            .pointerInput(Unit) {
                                // Consumes taps inside the card so it does not trigger outside dismiss
                                detectTapGestures { }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val activeLevel = currentDepthSliderValue.roundToInt()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Generation Depth",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = when (activeLevel) {
                                            0 -> "Root Only"
                                            maxDepth -> "All ($activeLevel)"
                                            else -> "Level $activeLevel"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            Slider(
                                value = currentDepthSliderValue,
                                onValueChange = { newValue ->
                                    currentDepthSliderValue = newValue
                                    val level = newValue.roundToInt()
                                    collapsedIds.clear()
                                    if (level < maxDepth) {
                                        applyLevelExpansion(
                                            node = currentRootNode,
                                            targetLevel = level,
                                            currentDepth = 0,
                                            collapsedMap = collapsedIds
                                        )
                                    }
                                },
                                valueRange = 0f..maxDepth.toFloat(),
                                steps = max(0, maxDepth - 1),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Root",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "Gen $maxDepth (All)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                // Main Floating Action Pill
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledIconButton(
                            onClick = { showLevelSlider = !showLevelSlider },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (showLevelSlider) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                contentColor = if (showLevelSlider) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Depth Slider",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        ActionIconButtonWithTooltip(
                            tooltipText = "Collapse Selected",
                            icon = Icons.Default.UnfoldLess,
                            enabled = selectedPersonId != null,
                            onClick = {
                                selectedPersonId?.let { id -> collapsedIds[id] = true }
                            }
                        )

                        // Global Unfold All Icon
                        ActionIconButtonWithTooltip(
                            tooltipText = "Unfold All Levels",
                            icon = Icons.Default.UnfoldMore,
                            onClick = {
                                collapsedIds.clear()
                                currentDepthSliderValue = maxDepth.toFloat()
                                Toast.makeText(context, "Unfolded all levels", Toast.LENGTH_SHORT).show()
                            }
                        )

                        if (!isHierarchyView) {
                            VerticalDivider(
                                modifier = Modifier
                                    .height(24.dp)
                                    .padding(horizontal = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            ActionIconButtonWithTooltip(
                                tooltipText = "Fit to Screen",
                                icon = Icons.Default.CenterFocusStrong,
                                onClick = { fitTreeToScreen() }
                            )
                        }
                    }
                }
            }
        }

        if (showPrintDialog) {
            A4PrintPreviewDialog(
                title = tree.title,
                rootNode = currentRootNode,
                layoutRoot = staticLayoutRoot,
                isHierarchyView = isHierarchyView,
                collapsedIds = collapsedIds.toMap(),
                onDismiss = { showPrintDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionIconButtonWithTooltip(
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
