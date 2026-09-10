package com.shivam.vanshavali.data

import android.content.Context
import com.shivam.vanshavali.model.PersonNode
import com.shivam.vanshavali.model.SavedFamilyTree
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class TreeRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val storageDir = File(context.filesDir, "family_trees").apply { mkdirs() }

    fun getTrees(): List<SavedFamilyTree> {
        return storageDir.listFiles { file -> file.extension == "json" }?.mapNotNull { file ->
            try {
                json.decodeFromString<SavedFamilyTree>(file.readText())
            } catch (_: Exception) {
                null
            }
        } ?: emptyList()
    }

    fun saveTree(rawJson: String, customTitle: String?): Result<SavedFamilyTree> {
        return runCatching {
            val root = json.decodeFromString<PersonNode>(rawJson)
            val treeId = UUID.randomUUID().toString()
            val title = if (!customTitle.isNullOrBlank()) customTitle.trim() else "${root.name}'s Family"
            val savedTree = SavedFamilyTree(id = treeId, title = title, root = root)
            val targetFile = File(storageDir, "$treeId.json")
            targetFile.writeText(json.encodeToString(savedTree))
            savedTree
        }
    }

    fun updateTree(treeId: String, newRawJson: String, newTitle: String?): Result<SavedFamilyTree> {
        return runCatching {
            val root = json.decodeFromString<PersonNode>(newRawJson)
            val existing = getTreeById(treeId)
            val title = if (!newTitle.isNullOrBlank()) newTitle.trim() else (existing?.title ?: "${root.name}'s Family")
            val updatedTree = SavedFamilyTree(id = treeId, title = title, root = root)
            val targetFile = File(storageDir, "$treeId.json")
            targetFile.writeText(json.encodeToString(updatedTree))
            updatedTree
        }
    }

    fun deleteTree(treeId: String): Boolean {
        val file = File(storageDir, "$treeId.json")
        return if (file.exists()) file.delete() else false
    }

    fun exportAllTreesJson(): String {
        val allTrees = getTrees()
        return json.encodeToString(allTrees)
    }

    fun importTreesJson(content: String): Result<Int> {
        return runCatching {
            val batchTrees = runCatching { json.decodeFromString<List<SavedFamilyTree>>(content) }.getOrNull()
            if (batchTrees != null) {
                var count = 0
                batchTrees.forEach { tree ->
                    val file = File(storageDir, "${tree.id}.json")
                    file.writeText(json.encodeToString(tree))
                    count++
                }
                return@runCatching count
            }

            val singleRoot = json.decodeFromString<PersonNode>(content)
            val treeId = UUID.randomUUID().toString()
            val savedTree = SavedFamilyTree(id = treeId, title = "${singleRoot.name}'s Family", root = singleRoot)
            File(storageDir, "$treeId.json").writeText(json.encodeToString(savedTree))
            1
        }
    }

    fun getTreeById(id: String): SavedFamilyTree? {
        val file = File(storageDir, "$id.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<SavedFamilyTree>(file.readText()) }.getOrNull()
    }
}
