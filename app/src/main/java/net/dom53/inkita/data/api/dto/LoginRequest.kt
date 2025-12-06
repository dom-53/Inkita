package net.dom53.inkita.data.api.dto

data class LoginRequest(
    val username: String,
    val password: String,
    val apiKey: String,
)
