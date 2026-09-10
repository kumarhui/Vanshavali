package com.shivam.vanshavali.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shivam.vanshavali.model.PersonNode

@Composable
fun HierarchyTreeList(
    rootNode: PersonNode,
    collapsedIds: Map<String, Boolean>,
    selectedPersonId: String?,
    onNodeClick: (PersonNode) -> Unit,
    onToggleCollapse: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFBFBFB)),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 96.dp)
    ) {
        item {
            HierarchyNodeItem(
                node = rootNode,
                depth = 0,
                collapsedIds = collapsedIds,
                selectedPersonId = selectedPersonId,
                onNodeClick = onNodeClick,
                onToggleCollapse = onToggleCollapse
            )
        }
    }
}

@Composable
private fun HierarchyNodeItem(
    node: PersonNode,
    depth: Int,
    collapsedIds: Map<String, Boolean>,
    selectedPersonId: String?,
    onNodeClick: (PersonNode) -> Unit,
    onToggleCollapse: (String) -> Unit
) {
    val isCollapsed = collapsedIds[node.id] == true
    val isSelected = node.id == selectedPersonId
    val hasChildren = node.children.isNotEmpty()
    val themeColor = GenerationColors[depth % GenerationColors.size]

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 20).dp, top = 3.dp, bottom = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else Color.Transparent
                )
                .clickable { onNodeClick(node) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/Collapse Chevron (Clickable)
            if (hasChildren) {
                IconButton(
                    onClick = { onToggleCollapse(node.id) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isCollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isCollapsed) "Expand" else "Collapse",
                        tint = themeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(28.dp))
            }

            Spacer(Modifier.width(4.dp))

            // Avatar circle badge
            Surface(
                shape = CircleShape,
                color = themeColor.copy(alpha = 0.15f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name & child count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Medium
                )
                if (hasChildren) {
                    Text(
                        text = if (isCollapsed) "${node.children.size} child(ren) • Tap to expand" else "${node.children.size} child(ren)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCollapsed) themeColor else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Animated Children
        AnimatedVisibility(
            visible = !isCollapsed && hasChildren,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                node.children.forEach { child ->
                    HierarchyNodeItem(
                        node = child,
                        depth = depth + 1,
                        collapsedIds = collapsedIds,
                        selectedPersonId = selectedPersonId,
                        onNodeClick = onNodeClick,
                        onToggleCollapse = onToggleCollapse
                    )
                }
            }
        }
    }
}
