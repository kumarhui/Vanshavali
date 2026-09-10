package com.shivam.vanshavali.data

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.shivam.vanshavali.model.PersonNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object TranslationHelper {
    private var hiToEnTranslator: Translator? = null
    private var enToHiTranslator: Translator? = null

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
    }

    private suspend fun getTranslator(toHindi: Boolean): Translator = withContext(Dispatchers.IO) {
        val conditions = DownloadConditions.Builder().build()
        if (toHindi) {
            if (enToHiTranslator == null) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.HINDI)
                    .build()
                enToHiTranslator = Translation.getClient(options).apply {
                    downloadModelIfNeeded(conditions).awaitTask()
                }
            }
            enToHiTranslator!!
        } else {
            if (hiToEnTranslator == null) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.HINDI)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
                hiToEnTranslator = Translation.getClient(options).apply {
                    downloadModelIfNeeded(conditions).awaitTask()
                }
            }
            hiToEnTranslator!!
        }
    }

    suspend fun translateTree(root: PersonNode, toHindi: Boolean): PersonNode = withContext(Dispatchers.Default) {
        val translator = getTranslator(toHindi)

        suspend fun mapNode(node: PersonNode): PersonNode {
            val translatedName = try {
                translator.translate(node.name).awaitTask()
            } catch (_: Exception) {
                node.name
            }
            val children = node.children.map { mapNode(it) }
            return node.copy(name = translatedName, children = children)
        }

        mapNode(root)
    }
}
