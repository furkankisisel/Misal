package com.example.misal.domain.model

data class Call(
    val id: String = "",
    val callerId: String = "",
    val receiverId: String = "",
    val type: String = "audio", // "audio" or "video"
    val status: String = "ringing", // "ringing", "accepted", "rejected", "ended", "missed"
    val offer: Map<String, Any>? = null,
    val answer: Map<String, Any>? = null,
    val timestamp: Long = 0L
)
