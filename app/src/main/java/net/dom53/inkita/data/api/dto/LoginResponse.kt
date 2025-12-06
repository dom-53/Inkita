package net.dom53.inkita.data.api.dto

data class LoginResponse(
    val id: Int,
    val username: String,
    val email: String?,
    val roles: List<String>?,
    val token: String,
    val refreshToken: String,
    val apiKey: String?,
)
