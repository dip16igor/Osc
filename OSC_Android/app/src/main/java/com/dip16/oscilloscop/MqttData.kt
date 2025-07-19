package com.dip16.oscilloscop

data class MqttData(
    val samplingData: SamplingData  // Теперь MqttData содержит SamplingData
)