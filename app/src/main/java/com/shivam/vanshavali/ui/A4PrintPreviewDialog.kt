package com.shivam.vanshavali.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

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
                                    TreeExportHelper.saveBitmapToGallery(context, title, bmp)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download Image")
                        }

                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    val bmp = generateTreeBitmap(layoutRoot, bounds, isLandscape)
                                    val file = TreeExportHelper.saveCacheBitmap(context, bmp, title)
                                    TreeExportHelper.shareToWhatsApp(context, file)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "WhatsApp Share")
                        }

                        FilledIconButton(
                            onClick = {
                                scope.launch {
                                    val bmp = generateTreeBitmap(layoutRoot, bounds, isLandscape)
                                    val file = TreeExportHelper.saveCacheBitmap(context, bmp, title)
                                    TreeExportHelper.printWithNokoPrint(context, file)
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
