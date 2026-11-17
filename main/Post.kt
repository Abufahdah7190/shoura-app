package com.shoura.app.models

data class User(
    val id: Int,
    val name: String,
    val handle: String,
    val email: String,
    val bio: String
)

data class Post(
    val id: Int,
    val content: String,
    val mediaUrl: String?,
    val mediaType: String,
    val likesCount: Int,
    val dislikesCount: Int,
    val createdAt: String,
    val userId: Int,
    val user: User?
)