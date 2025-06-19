package com.example.feeltherythm

data class Song(
    val title: String,
    val audioPath: String,
    val imagePath: String?, // poate fi null
    val tempo: Double?,
    val beatsCount: Int
)
