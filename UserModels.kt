package com.example.shoura_app

data class User(
    val id: Int?,
    val username: String,
    val password: String,
    val email: String,
    val phoneNumber: String
)

data class LoginDto(
    val username: String,
    val password: String
)

data class UpdateUserDto(
    val email: String,
    val phoneNumber: String
)