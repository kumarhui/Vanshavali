package com.shivam.vanshavali.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shivam.vanshavali.model.SavedFamilyTree
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SAMPLE_TREE_JSON = """{
  "id": "1",
  "name": "Ramprasad Gupta",
  "gender": "male",
  "children": [
    {
      "id": "2",
      "name": "Suresh Gupta",
      "gender": "male",
      "children": [
        { "id": "4", "name": "Aman Gupta", "gender": "male", "children": [] },
        { "id": "5", "name": "Priya Gupta", "gender": "female", "children": [] }
      ]
    },
    {
      "id": "3",
      "name": "Sunita Devi",
      "gender": "female",
      "children": []
    }
  ]
}"""

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    trees: List<SavedFamilyTree>,
    onTreeClick: (String) -> Unit,
    onAddTree: (title: String, json: String) -> Boolean,
    onUpdateTree: (treeId: String, title: String, json: String) -> Boolean,
    onDeleteTree: (treeId: String) -> Unit,
    onExportBackup: () -> String,
    onImportJson: (String) -> Unit,
    onCheckForUpdate: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSampleDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Long press action states
    var activeTreeForAction by remember { mutableStateOf<SavedFamilyTree?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var treeToReplace by remember { mutableStateOf<SavedFamilyTree?>(null) }
    var treeToDelete by remember { mutableStateOf<SavedFamilyTree?>(null) }

    BackHandler {
        if (selectedTab != 0) {
            selectedTab = 0
            return@BackHandler
        }
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    reader.readText()
                }
                if (!content.isNullOrBlank()) {
                    onImportJson(content)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "Vanshavali" else "Settings", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "App Info")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                    label = { Text("Family Trees") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Family Tree")
                }
            }
        }
    ) { padding ->
        if (selectedTab == 0) {
            if (trees.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No family trees yet. Tap + to add one!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trees, key = { it.id }) { tree ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onTreeClick(tree.id) },
                                    onLongClick = {
                                        activeTreeForAction = tree
                                        showActionSheet = true
                                    }
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.AccountTree,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tree.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Root: ${tree.root.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Data Management",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                SettingsOptionCard(
                    title = "Backup JSONs",
                    subtitle = "Export all saved family trees to a backup JSON",
                    icon = Icons.Default.CloudUpload,
                    onClick = {
                        val backupData = onExportBackup()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_SUBJECT, "Vanshavali_Backup.json")
                            putExtra(Intent.EXTRA_TEXT, backupData)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Save or Share Backup"))
                    }
                )

                SettingsOptionCard(
                    title = "Import JSONs",
                    subtitle = "Restore trees from a file or import a lineage",
                    icon = Icons.Default.CloudDownload,
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/*"))
                    }
                )

                SettingsOptionCard(
                    title = "Copy JSON Example Format",
                    subtitle = "Inspect or copy reference schema to your clipboard",
                    icon = Icons.Default.ContentCopy,
                    onClick = { showSampleDialog = true }
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "App Information",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                SettingsOptionCard(
                    title = "Check for Updates",
                    subtitle = "Look for newer releases on Google Play",
                    icon = Icons.Default.SystemUpdate,
                    onClick = onCheckForUpdate
                )
            }
        }

        // Long Press Action Bottom Sheet
        if (showActionSheet && activeTreeForAction != null) {
            val selectedTree = activeTreeForAction!!
            ModalBottomSheet(
                onDismissRequest = {
                    showActionSheet = false
                    activeTreeForAction = null
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = selectedTree.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Root: ${selectedTree.root.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(Modifier.height(16.dp))

                    ListItem(
                        headlineContent = { Text("Replace JSON") },
                        supportingContent = { Text("Overwrite this lineage with new JSON data") },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showActionSheet = false
                            treeToReplace = selectedTree
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Delete Family Tree", color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("Permanently remove this lineage") },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            showActionSheet = false
                            treeToDelete = selectedTree
                        }
                    )

                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // Replace JSON Dialog
        if (treeToReplace != null) {
            ReplaceTreeDialog(
                tree = treeToReplace!!,
                onDismiss = { treeToReplace = null },
                onConfirm = { newTitle, newJson ->
                    val success = onUpdateTree(treeToReplace!!.id, newTitle, newJson)
                    if (success) treeToReplace = null
                }
            )
        }

        // Delete Confirmation Dialog
        if (treeToDelete != null) {
            AlertDialog(
                onDismissRequest = { treeToDelete = null },
                title = { Text("Delete Family Tree?") },
                text = { Text("Are you sure you want to delete \"${treeToDelete!!.title}\"? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            onDeleteTree(treeToDelete!!.id)
                            treeToDelete = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { treeToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddDialog) {
            AddTreeDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { title, jsonText ->
                    val success = onAddTree(title, jsonText)
                    if (success) showAddDialog = false
                }
            )
        }

        if (showInfoDialog) {
            AppInfoDialog(
                onDismiss = { showInfoDialog = false },
                onCheckForUpdate = {
                    showInfoDialog = false
                    onCheckForUpdate()
                }
            )
        }

        if (showSampleDialog) {
            SampleJsonDialog(
                onDismiss = { showSampleDialog = false },
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Vanshavali Sample JSON", SAMPLE_TREE_JSON))
                    Toast.makeText(context, "Sample JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                    showSampleDialog = false
                }
            )
        }
    }
}

@Composable
fun ReplaceTreeDialog(
    tree: SavedFamilyTree,
    onDismiss: () -> Unit,
    onConfirm: (title: String, json: String) -> Unit
) {
    var title by remember { mutableStateOf(tree.title) }
    var jsonText by remember {
        mutableStateOf(Json { prettyPrint = true }.encodeToString(tree.root))
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Replace Family JSON",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Paste updated JSON below to overwrite this tree.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tree Title") },
                    leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        errorMsg = null
                    },
                    label = { Text("Lineage JSON *") },
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                    minLines = 6,
                    maxLines = 10,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMsg != null,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        shape = RoundedCornerShape(10.dp),
                        onClick = {
                            if (jsonText.isBlank()) {
                                errorMsg = "JSON cannot be empty."
                            } else {
                                onConfirm(title, jsonText)
                            }
                        }
                    ) {
                        Text("Update Tree")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SampleJsonDialog(
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Text(
                    text = "Reference JSON Format",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "You can copy and tweak this sample hierarchy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    Text(
                        text = SAMPLE_TREE_JSON,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onCopy, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy JSON")
                    }
                }
            }
        }
    }
}

@Composable
fun AppInfoDialog(
    onDismiss: () -> Unit,
    onCheckForUpdate: () -> Unit
) {
    val context = LocalContext.current
    val packageInfo = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    val versionName = packageInfo?.versionName ?: "1.0"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode ?: 1L
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toLong() ?: 1L
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Vanshavali",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Version $versionName (Build $versionCode)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onCheckForUpdate,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Check for Update")
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun AddTreeDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, json: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var jsonText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "New Family Tree",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Provide a lineage name and paste your tree's JSON definition below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tree Title (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        errorMsg = null
                    },
                    label = { Text("Lineage JSON *") },
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                    placeholder = { Text("{\"id\":\"1\",\"name\":\"Root Name\",\"gender\":\"male\",\"children\":[]}") },
                    minLines = 5,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMsg != null,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        shape = RoundedCornerShape(10.dp),
                        onClick = {
                            if (jsonText.isBlank()) {
                                errorMsg = "JSON payload cannot be empty."
                            } else {
                                onConfirm(title, jsonText)
                            }
                        }
                    ) {
                        Text("Generate Tree")
                    }
                }
            }
        }
    }
}
