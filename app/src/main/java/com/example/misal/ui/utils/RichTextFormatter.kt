package com.example.misal.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

fun formatRichText(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        // Basit bir regex (çok komplex içiçe kullanım desteklemez, temel seviye)
        val pattern = Regex("(\\*\\*(.*?)\\*\\*)|(\\*(.*?)\\*)|(_(.*?)_)|(~(.*?)~)")
        val matches = pattern.findAll(text)
        
        for (match in matches) {
            val startIndex = match.range.first
            if (startIndex > currentIndex) {
                append(text.substring(currentIndex, startIndex))
            }
            
            val value = match.value
            when {
                value.startsWith("**") && value.endsWith("**") -> {
                    val content = value.substring(2, value.length - 2)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(content)
                    pop()
                }
                value.startsWith("*") && value.endsWith("*") -> {
                    val content = value.substring(1, value.length - 1)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(content)
                    pop()
                }
                value.startsWith("_") && value.endsWith("_") -> {
                    val content = value.substring(1, value.length - 1)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(content)
                    pop()
                }
                value.startsWith("~") && value.endsWith("~") -> {
                    val content = value.substring(1, value.length - 1)
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(content)
                    pop()
                }
            }
            currentIndex = match.range.last + 1
        }
        
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
