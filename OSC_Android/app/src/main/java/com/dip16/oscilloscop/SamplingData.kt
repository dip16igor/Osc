package com.dip16.oscilloscop

data class SamplingData(
    val startTime: Long, // Время начала выборки в миллисекундах
    val samplingFrequency: Int, // Частота семплирования
    val sampleSize: Int, // Количество точек выборки
    val samples: List<Short> // Сама выборка в формате Int
)
