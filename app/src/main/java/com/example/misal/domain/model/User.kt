package com.example.misal.domain.model

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val publicKey: String = "",
    val profilePictureBase64: String? = null,
    val bio: String = "Merhaba, ben Misal kullanıyorum!"
)
