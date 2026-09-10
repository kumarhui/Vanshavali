package com.shivam.vanshavali

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.shivam.vanshavali.data.TreeRepository
import com.shivam.vanshavali.model.SavedFamilyTree
import com.shivam.vanshavali.ui.FamilyTreeViewerScreen
import com.shivam.vanshavali.ui.HomeScreen
import com.shivam.vanshavali.ui.buildBalancedTree
import com.shivam.vanshavali.ui.theme.VanshavaliTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val repository by lazy { TreeRepository(applicationContext) }
    private lateinit var appUpdateManager: AppUpdateManager

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            // Update cancelled or failed
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForAppUpdates(silentCheck = true)

        setContent {
            VanshavaliTheme {
                val scope = rememberCoroutineScope()
                var currentTreeId by remember { mutableStateOf<String?>(null) }
                var trees by remember { mutableStateOf(repository.getTrees()) }
                var isLoadingTree by remember { mutableStateOf(false) }

                val activeTree: SavedFamilyTree? = remember(currentTreeId, trees) {
                    currentTreeId?.let { id -> trees.find { it.id == id } }
                }

                if (activeTree != null) {
                    FamilyTreeViewerScreen(
                        tree = activeTree,
                        onBack = { currentTreeId = null }
                    )
                } else {
                    HomeScreen(
                        trees = trees,
                        onTreeClick = { id ->
                            val selected = trees.find { it.id == id }
                            if (selected != null) {
                                isLoadingTree = true
                                scope.launch {
                                    // Heavy calculation executed on background thread
                                    withContext(Dispatchers.Default) {
                                        buildBalancedTree(selected.root)
                                    }
                                    isLoadingTree = false
                                    currentTreeId = id
                                }
                            }
                        },
                        onAddTree = { title, json ->
                            val result = repository.saveTree(json, title)
                            result.onSuccess {
                                trees = repository.getTrees()
                                Toast.makeText(this, "Tree saved successfully!", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Toast.makeText(this, "JSON parse failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                            result.isSuccess
                        },
                        onUpdateTree = { treeId, title, json ->
                            val result = repository.updateTree(treeId, json, title)
                            result.onSuccess {
                                trees = repository.getTrees()
                                Toast.makeText(this, "Tree updated successfully!", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Toast.makeText(this, "JSON parse failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                            result.isSuccess
                        },
                        onDeleteTree = { treeId ->
                            val success = repository.deleteTree(treeId)
                            if (success) {
                                trees = repository.getTrees()
                                Toast.makeText(this, "Family tree deleted.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onExportBackup = {
                            repository.exportAllTreesJson()
                        },
                        onImportJson = { content ->
                            val res = repository.importTreesJson(content)
                            res.onSuccess { count ->
                                trees = repository.getTrees()
                                Toast.makeText(this, "Imported $count family tree(s) successfully!", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Toast.makeText(this, "Import failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        onCheckForUpdate = {
                            checkForAppUpdates(silentCheck = false)
                        }
                    )
                }

                // Loading Progress Dialog
                if (isLoadingTree) {
                    Dialog(onDismissRequest = { /* Prevent dismiss while loading */ }) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            shadowElevation = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column {
                                    Text(
                                        text = "Loading Lineage...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Preparing tree layout",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                }
            }
        }
    }

    private fun checkForAppUpdates(silentCheck: Boolean) {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            } else {
                if (!silentCheck) {
                    Toast.makeText(this, "Your app is up to date!", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            if (!silentCheck) {
                Toast.makeText(this, "Failed to check for updates.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
