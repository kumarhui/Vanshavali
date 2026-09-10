package com.shivam.vanshavali.model

import kotlinx.serialization.Serializable

@Serializable
data class PersonNode(
    val id: String,
    val name: String,
    val gender: String = "male",
    val children: List<PersonNode> = emptyList()
)

@Serializable
data class SavedFamilyTree(
    val id: String,
    val title: String,
    val root: PersonNode
)
