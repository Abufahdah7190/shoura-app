package com.example.shoura_app

data class Post(
    val id: Int?,
    val content: String,
    val mediaUrl: String?,
    val mediaType: String,
    val userId: Int,
    val createdAt: String?
)