package com.dip16.oscilloscop

import android.annotation.SuppressLint
import android.graphics.Paint
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlin.math.*

import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.FastFourierTransformer
import kotlin.properties.Delegates

private var scaleX by mutableFloatStateOf(1f)
private var scaleY by mutableFloatStateOf(1f)
private var offsetXOSCdef by mutableFloatStateOf(0f)
private var offsetYOSCdef by mutableFloatStateOf(-70f)
private var offsetX by mutableFloatStateOf(0f)
private var offsetY by mutableFloatStateOf(-70f)
private var centerX by mutableFloatStateOf(0f)
private var centerY by mutableFloatStateOf(0f)

private var maxVolts by mutableFloatStateOf(5f)
private var minVolts by mutableFloatStateOf(0f)
private var maxTime by mutableFloatStateOf(1f)
private var minTime by mutableFloatStateOf(0f)

private var offsetXFFTdef by mutableFloatStateOf(0f)

//private var offsetYFFTdef by mutableFloatStateOf(1100f)
var offsetYFFTdef by Delegates.notNull<Float>()
//private set

private var offsetXFFT by mutableFloatStateOf(0f)
private var offsetYFFT by mutableFloatStateOf(1100f)

private var max_dB by mutableFloatStateOf(0f)
private var min_dB by mutableFloatStateOf(-120f)
private var maxFreqFFT by mutableFloatStateOf(1024f)
private var minFreqFFT by mutableFloatStateOf(0f)

// Определяем шаги сетки
private var yStep by mutableFloatStateOf(0.5f) // 500 мВ в Вольтах
private var xStep by mutableFloatStateOf(0.1f) // 100 ms в секундах

private var yStepFFT by mutableFloatStateOf(10f) // 10 dB
private var xStepFFT by mutableFloatStateOf(100f) // 100 Hz


private val spectrumQueue = mutableListOf<FloatArray>()
private var currentDataSize: Int = 0

//private var voltageData: List<Float> = emptyList() // Глобальная переменная

private val fpsHistory = mutableListOf<Float>() // Хранит последние 100 значений FPS
private val opsHistory = mutableListOf<Float>() // Хранит последние 100 значений OPS

private var startOffset = false

private var showSettings by mutableStateOf(false)

//@Preview(showBackground = true)
@Composable
fun OscScreen(viewModel: OscVM = viewModel()) {
    val isConnected by viewModel.isConnected.observeAsState(false)
    val isConnectedWS by viewModel.isConnectedWS.observeAsState(false)
    val isTX by viewModel.isTx.observeAsState(false)
    val isTXWS by viewModel.isTxWS.observeAsState(false)
    val isRX by viewModel.isRx.observeAsState(false)
    val isRXWS by viewModel.isRxWS.observeAsState(false)

    val ip by viewModel.ip.observeAsState("--.--.--.--")
    val ip2 by viewModel.ip2.observeAsState("--.--.--.--")
    val ssid by viewModel.ssid.observeAsState("---")
    val rssi by viewModel.rssi.observeAsState("---")
    val vbat by viewModel.vbat.observeAsState("---")

    val sampleSize by viewModel.samplesSize.observeAsState(1024) // Observe LiveData
    var sliderSampleSizeValue by remember { mutableIntStateOf(sampleSize) } // Start with current value

    //val ops by viewModel.ops.observeAsState(0)
    val ops by viewModel.averageUpdatesPerSecond.observeAsState(0f)

    var isCheckedConnect by remember { mutableStateOf(true) }

    val mqttData by viewModel.mqttData.collectAsState()

    Column {

        HeaderRow(
            isConnected = isConnected,
            isConnected2 = isConnectedWS,
            isTX = isTX,
            isRX = isRX,
            isTX2 = isTXWS,
            isRX2 = isRXWS,
            isCheckedConnect = isCheckedConnect,
            onCheckedChange = {
                isCheckedConnect = it
//                if (isCheckedConnect)
//                //viewModel.connect()
//                else {
//                    //viewModel.disconnect()
//                    //isConnected = false
//                }
            }
        )

        //HorizontalDivider(thickness = 1.dp, color = Color.Gray)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000030)) // Темно-синий цвет
                .height(IntrinsicSize.Min)
                .padding(horizontal = (32).dp),
            //horizontalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            //horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            //Text(text = "StartTime: ${mqttData.samplingData.startTime}")
            Text(
                text = formatSamplingFrequency(mqttData.samplingData.samplingFrequency),
                //modifier = Modifier.weight(0.3f), // Take up available space
                style = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Start
            )
            Text(
                text = formatSampleSize(mqttData.samplingData.sampleSize),
                //modifier = Modifier.weight(0.3f), // Take up available space
                style = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Start
            )
            Text(
                text = "Y: $yStep V/div",
                //modifier = Modifier.weight(0.3f), // Take up available space
                style = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Start
            )
            Text(
                text = "X: $xStep s/div",
                //modifier = Modifier.weight(0.3f), // Take up available space
                style = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Start
            )
//            Text(
//                text = "$ip2 ->",
//                modifier = Modifier.weight(1f), // Take up available space
//                style = LocalTextStyle.current.copy(
//                    fontSize = 12.sp,
//                    fontWeight = FontWeight.Normal
//                ),
//                textAlign = TextAlign.Start
//            )
//            Text(
//                text = "$ip2 ->[$ip",
//                modifier = Modifier.weight(1f), // Take up available space
//                style = LocalTextStyle.current.copy(
//                    fontSize = 12.sp,
//                    fontWeight = FontWeight.Normal
//                ),
//                textAlign = TextAlign.Start
//            )
            Text(
                text = "$ip2 ->[$ip $ssid $rssi dBm $vbat V]",
                //modifier = Modifier.weight(1f), // Take up available space
                style = LocalTextStyle.current.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                ),
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

//        Button(
//            onClick = {
//                viewModel.updateSamplesSize(100)
//                // Handle FFT button click here
//            },
//            modifier = Modifier
//                .padding(end = 16.dp)
//        ) {
//            Text("SEND")
//        }

        //HorizontalDivider(thickness = 1.dp, color = Color.Gray)
        maxFreqFFT = mqttData.samplingData.samplingFrequency.toFloat() / 2

        Graph(
            mqttData.samplingData.samples,
            mqttData.samplingData.samplingFrequency.toFloat(),
            ops,
            viewModel
        )
    }
}

fun formatSamplingFrequency(frequency: Int): String {
    return when {
        frequency >= 1_000_000 -> "${frequency / 1_000_000} Ms/s" // Мегагерцы
        frequency >= 10_000 -> "${frequency / 1_000} ks/s" // Килогерцы
        else -> "$frequency s/s" // Стандартный вывод
    }
}

fun formatSampleSize(size: Int): String {
    return when {
        size >= 1_000_000 -> "${size / 1_000_000} Ms" // Мегасэмплы
        size >= 10_000 -> "${size / 1_000} ks" // Килосэмплы
        else -> "$size s" // Стандартный вывод
    }
}

@SuppressLint("DefaultLocale")
private fun DrawScope.drawGridOsc(sampleRate: Float, sampleLength: Int) {
    val width = size.width
    val height = size.height

    // Рисуем горизонтальные линии
    val voltageRange = maxVolts - minVolts

    val yCount = (voltageRange / yStep).toInt() + 1

    for (i in 1 until yCount) {
        val yValue = minVolts + i * yStep
        val y = height - ((yValue - minVolts) / voltageRange * height) * scaleY + offsetY
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(20f, y),
            end = Offset(width * scaleX + offsetX, y),
            strokeWidth = 1f
        )
        val roundedValueY = String.format("%.2f", yValue)
        // Рисуем текст
        drawContext.canvas.nativeCanvas.apply {
            drawText(
                roundedValueY,
                3f, // Смещение по X
                y, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    textSize = 40f
                    textAlign = Paint.Align.LEFT
                }
            )
        }

    }

    // Рисуем вертикальные линии
    //val xCount = (width / ((xStep / sampleRate) * scaleX)).toInt() + 1
    //val xCount = (1 / sampleRate * sampleLength / xStep).toInt()
    val xCount = (maxTime / xStep).toInt()
    //Log.d("dip171", "xCount = $xCount")

    for (i in 0 until xCount + 1) {
        val xValue = i * xStep
        //val x = (xValue / (sampleRate * scaleX) * width) + offsetX
        val x = xValue * width / maxTime * scaleX + offsetX
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(x, height + offsetY),
            end = Offset(x, height - height * scaleY + offsetY),
            strokeWidth = 2f
        )
        //       val roundedValue = String.format("%.2f", xValue)
        // Рисуем текст
//        drawContext.canvas.nativeCanvas.apply {
//            drawText(
//                roundedValue,
//                x + 3, // Смещение по X
//                height, // Смещение по Y
//                Paint().apply {
//                    color = android.graphics.Color.LTGRAY
//                    textSize = 40f
//                    textAlign = Paint.Align.CENTER
//                }
//            )
//        }
    }
}

@SuppressLint("DefaultLocale")
private fun DrawScope.drawGridFFT(sampleRate: Float, sampleLength: Int) {
    val width = size.width
    val height = size.height

    // Рисуем горизонтальные линии

    val dBRange = max_dB - min_dB

    val yCount = (dBRange / yStepFFT).toInt() + 1

    for (i in 1 until yCount) {
        val yValue = min_dB + i * yStepFFT
        val y = height - ((yValue - min_dB) / dBRange * height) * scaleY + offsetYFFT
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(20f, y),
            end = Offset(width * scaleX + offsetXFFT, y),
            strokeWidth = 1f
        )
        val roundedValueY = String.format("%.0f", yValue)
        // Рисуем текст
        drawContext.canvas.nativeCanvas.apply {
            drawText(
                roundedValueY,
                3f, // Смещение по X
                y, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    textSize = 40f
                    textAlign = Paint.Align.LEFT
                }
            )
        }

    }

    // Рисуем вертикальные линии

    val xCount = (maxFreqFFT / xStepFFT).toInt()
    //Log.d("dip171", "xCount = $xCount")

    for (i in 0 until xCount + 1) {
        val xValue = i * xStepFFT
        //val x = (xValue / (sampleRate * scaleX) * width) + offsetX
        val x = xValue * width / maxFreqFFT * scaleX + offsetXFFT
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(x, height - 20f),
            end = Offset(x, height - height * scaleY + offsetYFFT),
            strokeWidth = 2f
        )
        var yy = height + 100
        if (offsetYFFT < offsetYFFTdef - 100)
            yy = height - 10
        val roundedValue = String.format("%.2f", xValue)
        // Рисуем текст
        drawContext.canvas.nativeCanvas.apply {
            drawText(
                roundedValue,
                x + 3, // Смещение по X
                yy, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    textSize = 40f
                    textAlign = Paint.Align.CENTER
                }
            )
        }
    }
}

private fun DrawScope.drawAxesOsc() {
    val width = size.width
    val height = size.height

    // Ось X
    drawLine(
        color = Color.LightGray,
        start = Offset(
            0f + offsetX,
            height - ((0 - minVolts) / (maxVolts - minVolts) * height) * scaleY + offsetY
        ),
        end = Offset(
            width * scaleX + offsetX,
            height - ((0 - minVolts) / (maxVolts - minVolts) * height) * scaleY + offsetY
        ),
        strokeWidth = 2f
    )
    /*
        // Ось 1V
        drawLine(
            color = Color.Gray,
            start = Offset(0f + offsetX, height - ((1f - minVolts) / (maxVolts - minVolts) * height) * scaleY + offsetY),
            end = Offset(width * scaleX + offsetX, height - ((1f - minVolts) / (maxVolts - minVolts) * height) * scaleY + offsetY),
            strokeWidth = 1f
        )
        // Ось 2V
        drawLine(
            color = Color.Gray,
            start = Offset(0f + offsetX, height - ((2f - minVolts) / (maxVolts - minVolts) * height) * scaleY + offsetY),
            end = Offset(width * scaleX + offsetX, height - ((2f - minVolts) / (maxVolts - minVolts) * height) * scaleY + offsetY),
            strokeWidth = 1f
        )
    */
    // y = height - ((value - minVolts) / (maxVolts - minVolts) * height) * scaleY + offsetY
//    drawLine(
//        color = Color.Cyan,
//        start = Offset(0f, height +  offsetY - scaleY * height/2 ),
//        end = Offset(width, height + offsetY - scaleY * height/2),
//        strokeWidth = 2f
//    )

    // Ось Y
    drawLine(
        color = Color.LightGray,
        start = Offset(offsetX, height + offsetY),
        end = Offset(
            offsetX + 2,
            height - ((maxVolts - minVolts) / (maxVolts - minVolts) * height) * scaleY + offsetY
        ),
        strokeWidth = 2f
    )
}

@SuppressLint("DefaultLocale")
private fun DrawScope.drawFFT(dataSize: Int, sampleRate: Float, voltageData: List<Float>) {
    if (voltageData.isEmpty()) return
    val width = size.width
    val height = size.height

    // Check if dataSize has changed, and reset if it has
    if (currentDataSize != dataSize) {
        spectrumQueue.clear()
        currentDataSize = dataSize
    }

    // Преобразуем данные в Вольты
    //val voltageData = data.map { (it.toFloat() / 4095f) * 3.3000f }

    val signalWithoutDC = removeDCOffset(voltageData.toFloatArray())

    // Применение оконной функции Ханнинга
    val windowedData = applyHanningWindow(signalWithoutDC)

    // Выполнение БПФ
    val spectrumData = fourierTransform(windowedData)

    val halfLength = spectrumData.size / 2 // Вычисляем длину половины спектра
    val halfSpectrumData =
        spectrumData.take(halfLength).toTypedArray() // Берем только первую половину

    val maxSpec = halfSpectrumData.maxByOrNull { it.abs() } // Модуль комплексного числа }
    val minSpec = halfSpectrumData.minByOrNull { it.abs() } // Модуль комплексного числа }
    //val logScaleSpectrum = calculateLogScaleSpectrum(spectrumData)
    val logScaleSpectrum1 = calculateDBRelativeToReference(halfSpectrumData, 3.3000f / 4)

    // Добавление усреднения спектра
    //spectrumQueue = mutableListOf<FloatArray>()
    spectrumQueue.add(logScaleSpectrum1)

    if (spectrumQueue.size > 100) {
        spectrumQueue.removeAt(0) // Удаляем старый спектр, если больше 100
    }

    // Вычисляем среднее значение спектров
    val averagedSpectrum = FloatArray(logScaleSpectrum1.size) { 0f }
    for (spectrum in spectrumQueue) {
        for (i in spectrum.indices) {
            averagedSpectrum[i] += spectrum[i]
        }
    }
    averagedSpectrum.forEachIndexed { index, value ->
        averagedSpectrum[index] = value / spectrumQueue.size
    }

    // Находим максимум
    val maxDbValue = logScaleSpectrum1.maxOrNull()
    val minDbValue = logScaleSpectrum1.minOrNull()

    //val maxIndex = logScaleSpectrum1.indexOf(maxDbValue)
    val maxIndex = indexOfMax(logScaleSpectrum1) // Получаем индекс максимального значения


    // вычисление частоты
    val sigma =
        (logScaleSpectrum1[maxIndex?.plus(1)!!] - logScaleSpectrum1[maxIndex.minus(1)]) / (logScaleSpectrum1[maxIndex.plus(
            1
        )] + logScaleSpectrum1[maxIndex.minus(1)])

    // val sigmaKorr = sigma * (2 - sigma) / (1 + sigma)
    val sigmaKorr = -sigma * 1

    val freqStep = sampleRate / dataSize

    val freqFine = (maxIndex + sigmaKorr) * freqStep

    var spuriousLevel = -200f
    for (i in logScaleSpectrum1.indices) {
        if (i > 10 && (i < maxIndex - 50 || i > maxIndex + 50)) {
            spuriousLevel = maxOf(spuriousLevel, logScaleSpectrum1[i])
        }
    }

    val sfdr = maxDbValue?.minus(spuriousLevel)

    // Вырезаем срез
    val slice = logScaleSpectrum1.copyOfRange(halfLength / 2 - 100, halfLength / 2 + 100)

    // Вычисляем среднее значение
    val avgNoise = slice.average()
    val signalToNoise = maxDbValue?.minus(avgNoise)

    val scaleYFactor = 1f // Коэффициент масштабирования по Y
    val offsetYValue = 0f // Смещение по Y

    val mindB = min_dB
    val maxdB = max_dB
    //val halfSize = logScaleSpectrum1.size / 2

    val normalizedSpectrum1 = logScaleSpectrum1.mapIndexed { index, value ->
        //val magnitude = value.abs() // Получаем модуль комплексного числа
        val x = (index.toFloat() / (logScaleSpectrum1.size - 1) * width) * scaleX + offsetXFFT
        val y =
            height - ((value - mindB) / (maxdB - mindB) * height) * scaleY * scaleYFactor + offsetYFFT + offsetYValue
        Offset(x, y)
    }

    val normalizedSpectrum2 = averagedSpectrum.mapIndexed { index, value ->
        //val magnitude = value.abs() // Получаем модуль комплексного числа
        val x = (index.toFloat() / (averagedSpectrum.size - 1) * width) * scaleX + offsetXFFT
        val y =
            height - ((value - mindB) / (maxdB - mindB) * height) * scaleY * scaleYFactor + offsetYFFT + offsetYValue
        Offset(x, y)
    }

    val baseAlpha = 0.4f // Базовое значение альфа
    val maxDataSize = 8192 // Максимальная длина данных, при которой альфа будет минимальной
    val alphaReductionFactor =
        (baseAlpha / maxDataSize) // Коэффициент уменьшения альфа на единицу длины
    val currentAlpha1 = maxOf(
        0.1f,
        0.4f - alphaReductionFactor * normalizedSpectrum2.size
    ) //минимальный 0.1, не более 0.5
    //Log.d("dip171", "currentAlpha1: $currentAlpha1")
// Рисуем линии между точками спектра
    for (i in 0 until normalizedSpectrum2.size - 1) {
        drawLine(
            //color = Color(0xFF00F0F0).copy(alpha = 0.1f),
            color = Color(0xFF00F0F0).copy(alpha = currentAlpha1),
            start = normalizedSpectrum1[i],
            end = normalizedSpectrum1[i + 1],
            strokeWidth = 2f
        )
        drawLine(
            //color = Color.Cyan.copy(alpha = 0.2f),
            color = Color(0xFF08F000).copy(alpha = currentAlpha1 + 0.2f),
            start = normalizedSpectrum2[i],
            end = normalizedSpectrum2[i + 1],
            strokeWidth = 2f
        )
        if (scaleX > 4) {
            drawCircle(
                color = Color.Green.copy(alpha = 1f),
                radius = 4f,
                center = normalizedSpectrum2[i + 1]
            )
        }
    }

    if (maxDbValue != null) {
        if (maxIndex != null) {
            drawLine(
                color = Color.Green.copy(alpha = 0.8f),
                start = Offset(
                    (maxIndex.toFloat() / (logScaleSpectrum1.size - 1) * width) * scaleX + offsetXFFT,
                    height - ((maxDbValue - mindB) / (maxdB - mindB) * height) * scaleY * scaleYFactor + offsetYFFT + offsetYValue
                ),
                end = Offset(
                    (maxIndex.toFloat() / (logScaleSpectrum1.size - 1) * width) * scaleX + offsetXFFT + 100,
                    height - ((maxDbValue - mindB) / (maxdB - mindB) * height) * scaleY * scaleYFactor + offsetYFFT + offsetYValue
                ),
                strokeWidth = 2f
            )
        }
    }

    if (maxIndex != null) {
        drawLine(
            color = Color.Green.copy(alpha = 0.8f),
            start = Offset(
                (maxIndex.toFloat() / (logScaleSpectrum1.size - 1) * width) * scaleX + offsetXFFT + 100,
                height - ((spuriousLevel - mindB) / (maxdB - mindB) * height) * scaleY * scaleYFactor + offsetYFFT + offsetYValue
            ),
            end = Offset(
                (maxIndex.toFloat() / (logScaleSpectrum1.size - 1) * width) * scaleX + offsetXFFT + 200,
                height - ((spuriousLevel - mindB) / (maxdB - mindB) * height) * scaleY * scaleYFactor + offsetYFFT + offsetYValue
            ),
            strokeWidth = 2f
        )
    }

    drawLine(
        color = Color.White.copy(alpha = 1f),
        start = Offset(
            ((halfLength / 2 - 100).toFloat() / (logScaleSpectrum1.size - 1) * width) * scaleX + offsetXFFT + 100,
            height - ((avgNoise.toFloat() - mindB) / (maxdB - mindB) * height) * scaleY * scaleYFactor + offsetYFFT + offsetYValue
        ),
        end = Offset(
            ((halfLength / 2 + 100).toFloat() / (logScaleSpectrum1.size - 1) * width) * scaleX + offsetXFFT + 200,
            height - ((avgNoise.toFloat() - mindB) / (maxdB - mindB) * height) * scaleY * scaleYFactor + offsetYFFT + offsetYValue
        ),
        strokeWidth = 2f
    )

    // Рисуем текст около точки с максимальным значением
    drawContext.canvas.nativeCanvas.apply {
        if (maxSpec != null) {
            drawText(
                "Max: ${String.format("%.8f V", maxSpec.abs())} ${
                    String.format(
                        "%.2f dB",
                        maxDbValue
                    )
                }",
                width - 5, // Смещение по X
                300f, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.CYAN
                    textSize = 40f
                    textAlign = Paint.Align.RIGHT
                }
            )
        }

        if (minSpec != null) {
            drawText(
                "Spur: ${
                    String.format(
                        "%.2f dB",
                        spuriousLevel
                    )
                }",
                width - 5, // Смещение по X
                340f, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.MAGENTA
                    textSize = 40f
                    textAlign = Paint.Align.RIGHT
                }
            )
        }

        if (maxIndex != null) {
            drawText(
                "MaxIdx:  $maxIndex",
                width - 5, // Смещение по X
                260f, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.CYAN
                    textSize = 40f
                    textAlign = Paint.Align.RIGHT
                }
            )
            drawText(
                "SFDR: ${String.format("%.2f dB", sfdr)}",
                width - 5, // Смещение по X
                380f, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.GREEN
                    textSize = 40f
                    textAlign = Paint.Align.RIGHT
                }
            )
            drawText(
                "Noise: ${String.format("%.2f dB", avgNoise)}",
                width - 5, // Смещение по X
                420f, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.RED
                    textSize = 40f
                    textAlign = Paint.Align.RIGHT
                }
            )
            drawText(
                "S/N: ${String.format("%.2f dB", signalToNoise)}",
                width - 5, // Смещение по X
                460f, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.RED
                    textSize = 40f
                    textAlign = Paint.Align.RIGHT
                }
            )
            drawText(
                "Step: ${String.format("%.4f", freqStep)} Freq: ${
                    String.format(
                        "%.4f Hz",
                        freqFine
                    )
                }",
                width - 5, // Смещение по X
                500f, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.GREEN
                    textSize = 40f
                    textAlign = Paint.Align.RIGHT
                }
            )
            drawText(
                "SigmaK: ${String.format("%.4f", sigmaKorr)}",
                width - 5, // Смещение по X
                540f, // Смещение по Y
                Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    textSize = 40f
                    textAlign = Paint.Align.RIGHT
                }
            )
        }
    }
}

fun indexOfMax(array: FloatArray): Int? {
    if (array.isEmpty()) return null
    var maxIndex = 0
    for (i in 1 until array.size) {
        if (array[i] > array[maxIndex]) {
            maxIndex = i
        }
    }
    return maxIndex
}

@SuppressLint("DefaultLocale")
private fun DrawScope.drawOsc(sampleRate: Float, voltageData: List<Float>) {

    if (voltageData.isEmpty()) return

    val width = size.width
    val height = size.height

    val minValue = voltageData.minOrNull() ?: 0f
    val maxValue = voltageData.maxOrNull() ?: 1f
    val averageValue = voltageData.average() // Вычисляем среднее значение

    //val signalWithoutDC = removeDCOffset(voltageData.toFloatArray())

    //val windowedData = applyHanningWindow(signalWithoutDC)

    val minValue1 = voltageData.minOrNull()
    val maxValue1 = voltageData.maxOrNull()
    // Преобразуем данные в точки
//    val normalizedData = voltageData.mapIndexed { index, value ->
//        val x = (index.toFloat() / (data.size - 1) * width) * scaleX + offsetX
//        val y = height - ((value - minValue) / (maxValue - minValue) * height) * scaleY + offsetY
//        Offset(x, y)
//    }
    // Масшабирование - шкала от minVolts до maxVolts переводится в пиксели поля экрана
    val normalizedData = voltageData.mapIndexed { index, value ->
        //val x = (index.toFloat() / (data.size - 1) * width) * scaleX + offsetX
        val x = (index.toFloat() * width / sampleRate * maxTime) * scaleX + offsetX
        val y = height - ((value - minVolts) / (maxVolts - minVolts) * height) * scaleY + offsetY
        Offset(x, y)
    }

    // Находим индекс максимального значения
    val maxIndex = voltageData.indexOf(maxValue)
    val maxPoint = normalizedData[maxIndex]

    val minIndex = voltageData.indexOf(minValue)
    val minPoint = normalizedData[minIndex]


    if (scaleX > 4f) {
        xStep = 0.01f
        yStep = 0.1f
        // Рисуем ступенчатые линии между точками
        for (i in 0 until normalizedData.size - 1) {
            val start = normalizedData[i]
            val end = normalizedData[i + 1]

            // Рисуем горизонтальную линию к следующей точке
            drawLine(
                color = Color.Yellow.copy(alpha = 0.5f),
                start = start,
                end = Offset(end.x, start.y), // Перемещаем по горизонтали до уровня следующей точки
                strokeWidth = 3f
            )

            // Рисуем вертикальную линию до уровня следующей точки
            drawLine(
                color = Color.Yellow.copy(alpha = 0.5f),
                start = Offset(end.x, start.y), // Начинаем с точки на уровне следующей точки
                end = end,
                strokeWidth = 3f
            )
        }
    } else {
        xStep = 0.1f
        yStep = 0.5f
        // Рисуем линии между точками
        for (i in 0 until normalizedData.size - 1) {
            drawLine(
                color = Color.Yellow.copy(alpha = 0.5f),
                start = normalizedData[i],
                end = normalizedData[i + 1],
                strokeWidth = 3f
            )
        }
    }

    if (scaleX > 4f) {
        // Рисуем точки
        normalizedData.forEach { point ->
            drawCircle(
                color = Color.Yellow.copy(alpha = 0.5f),
                radius = 8f,
                center = point
            )
        }
    }

//    drawCircle(
//        color = Color.Red.copy(alpha = 1f),
//        radius = 8f,
//        center = Offset(maxPoint.x, maxPoint.y)
//    )

    // Рисуем текст около точки с максимальным значением
    drawContext.canvas.nativeCanvas.apply {
        drawText(
            "Max: ${String.format("%.3f", maxValue1)} V",
            width - 5, // Смещение по X
            50f, // Смещение по Y
            Paint().apply {
                color = android.graphics.Color.RED
                textSize = 40f
                textAlign = Paint.Align.RIGHT
            }
        )
    }
//    drawCircle(
//        color = Color.Cyan.copy(alpha = 1f),
//        radius = 8f,
//        center = Offset(minPoint.x, minPoint.y)
//    )
    // Рисуем текст около точки с минимальным значением
    drawContext.canvas.nativeCanvas.apply {
        drawText(
            "Min: ${String.format("%.3f", minValue1)} V",
            width - 5, // Смещение по X
            100f, // Смещение по Y
            Paint().apply {
                color = android.graphics.Color.CYAN
                textSize = 40f
                textAlign = Paint.Align.RIGHT
            }
        )
    }

    // Рисуем текст около точки с минимальным значением
    drawContext.canvas.nativeCanvas.apply {
        drawText(
            "Avg: ${String.format("%.6f", averageValue)} V",
            width - 5, // Смещение по X
            200f, // Смещение по Y
            Paint().apply {
                color = android.graphics.Color.YELLOW
                textSize = 80f
                textAlign = Paint.Align.RIGHT
            }
        )
    }
}

// Функция для применения оконной функции Ханнинга
fun applyHanningWindow(data: FloatArray): FloatArray {
    val windowedData = FloatArray(data.size)
    for (i in data.indices) {
        windowedData[i] = (data[i] * (0.5f * (1 - cos(2 * PI * i / (data.size - 1))))).toFloat()
    }
    // Применение поправочного коэффициента
    val correctionFactor = 2.0f
    for (i in windowedData.indices) {
        windowedData[i] *= correctionFactor
    }
    return windowedData
}

fun removeDCOffset(signal: FloatArray): FloatArray {
    val mean = calculateMean(signal)
    return signal.map { it - mean }.toFloatArray()
}

fun calculateMean(signal: FloatArray): Float {
    return signal.average().toFloat()
}

// Функция для выполнения БПФ
fun fourierTransform(data: FloatArray): Array<Complex> {
    return try {
        val transformer =
            FastFourierTransformer(org.apache.commons.math3.transform.DftNormalization.STANDARD)
        val complexData = data.map { Complex(it.toDouble(), 0.0) }.toTypedArray()

        // Выполнение преобразования Фурье
        val result = transformer.transform(
            complexData,
            org.apache.commons.math3.transform.TransformType.FORWARD
        )
        // Масштабирование амплитуд
        val sizeData = data.size.toDouble()
        result.map { it.multiply(Complex(1 / (sizeData), 0.0)) }.toTypedArray()
    } catch (e: IllegalArgumentException) {
        // Обработка исключения, если входные данные некорректны
        Log.e("dip171", "Ошибка преобразования Фурье: ${e.message}")
        arrayOf() // Возвращаем null в случае ошибки
    } catch (e: Exception) {
        // Обработка других возможных исключений
        Log.e("dip171", "Неизвестная ошибка: ${e.message}")
        arrayOf() // Возвращаем null в случае ошибки
    }
}

fun calculateDBRelativeToReference(
    spectrumData: Array<Complex>,
    referenceVoltage: Float
): FloatArray {
    return spectrumData.map {
        val magnitude = it.abs() // Получаем модуль (амплитуду) в вольтах
        //val magnitude = it.abs() / spectrumData.size // Нормируем по размеру
        if (magnitude > 0) {
            20 * log10(magnitude / referenceVoltage).toFloat() // Преобразуем в dB относительно V_ref
        } else {
            Float.NEGATIVE_INFINITY // Для амплитуды 0 возвращаем -∞
        }
    }.toFloatArray()
}


@SuppressLint("DefaultLocale")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Graph(data: List<Short>, sampleRate: Float, ops: Float, viewModel: OscVM) {

    // Меморизуем вычисленные напряжения, если данные не изменились
    val voltageData2 by remember(data) {
        derivedStateOf { data.map { (it.toFloat() / 4095f) * 3.3f } }
    }

    // Состояния для FPS – вместо списка fpsList можно хранить сумму и счётчик,
    // чтобы вычислить среднее значение без операций удаления.
    var lastFrameTime by remember { mutableLongStateOf(System.nanoTime()) }
    var fps by remember { mutableFloatStateOf(0f) }
    var fpsSum by remember { mutableFloatStateOf(0f) }
    var fpsCount by remember { mutableIntStateOf(0) }
    var averageFps by remember { mutableFloatStateOf(0f) }

    //val textMeasurer = rememberTextMeasurer()
    var isStreamOn by remember { mutableStateOf(true) }
    var isFFTActive by remember { mutableStateOf(false) } // To control FFT mode.
    var isOSCActive by remember { mutableStateOf(true) } // To control FFT mode.
    var isFPSActive by remember { mutableStateOf(true) } // To control FFT mode.

    val frequencyValues =
        remember { listOf(2048, 10000, 20000, 50000, 100000, 200000, 500000, 1000000, 2000000) }

    val sliderPositions = remember { List(frequencyValues.size) { it.toFloat() } }
    var selectedFrequencyIndex by remember { mutableIntStateOf(7) } // Start with 100 kHz
    var currentFrequency by remember { mutableIntStateOf(frequencyValues[selectedFrequencyIndex]) }
    var signalFrequency by remember { mutableFloatStateOf(50f) }
    var signalAmplitude by remember { mutableIntStateOf(2045) }
    var noiseAmplitude by remember { mutableIntStateOf(1) }

    val sampleSizes = remember { listOf(1024, 2048, 4096, 8192, 16384) }
    val sampleSliderPositions = remember { List(sampleSizes.size) { it.toFloat() } }
    var selectedSampleSizeIndex by remember { mutableIntStateOf(1) } // Start with 4096
    var currentSampleSize by remember { mutableIntStateOf(sampleSizes[selectedSampleSizeIndex]) }

    // Меморизуем вычисленные напряжения, если данные не изменились
//    val voltageData by remember(data) {
//        mutableStateOf(data.map { (it.toFloat() / 4095f) * 3.3f })
//    }

    val minFrequency = 10f
    val maxFrequency = 1000f

    val minAmplitude = 1
    val maxAmplitude = 4095

    val minNoiseAmplitude = 0
    val maxNoiseAmplitude = 2047

    // Меморизированные Paint'ы для отрисовки текста
    val fpsPaint = remember {
        Paint().apply {
            color = android.graphics.Color.GREEN
            textSize = 40f
            textAlign = Paint.Align.LEFT
        }
    }
    val opsPaint = remember {
        Paint().apply {
            color = android.graphics.Color.YELLOW
            textSize = 40f
            textAlign = Paint.Align.LEFT
        }
    }
    val infoPaint = remember {
        Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 40f
            textAlign = Paint.Align.LEFT
        }
    }

    // Function to update the frequency
    fun updateSignalFrequency(newFrequency: Float) {
        signalFrequency = newFrequency
        //viewModel.updateSignalFreq(signalFrequency) // Update ViewModel
        Log.d("dip171", "New signal frequency selected: $signalFrequency Hz")
    }

    fun updateSignalAmplitude(newAmplitude: Int) {
        signalAmplitude = newAmplitude
        //viewModel.updateSignalFreq(signalFrequency) // Update ViewModel
        Log.d("dip171", "New signal amplitude selected: $signalAmplitude")
    }

    fun updateNoiseAmplitude(newAmplitude: Int) {
        noiseAmplitude = newAmplitude
        //viewModel.updateSignalFreq(signalFrequency) // Update ViewModel
        Log.d("dip171", "New noise amplitude selected: $noiseAmplitude")
    }

    // Function to update the frequency when the slider changes
    fun updateSampleFrequency(index: Int) {
        selectedFrequencyIndex = index
        currentFrequency = frequencyValues[index]
        // Here, you can call a function to update something else that relies on currentFrequency
        Log.d("dip171", "New frequency selected: $currentFrequency Hz")
    }


    fun updateSampleSize(index: Int) {
        selectedSampleSizeIndex = index
        currentSampleSize = sampleSizes[index]
        // Here you can call a function to handle the new sample size
        Log.d("dip171", "New sample size selected: $currentSampleSize")
        viewModel.updateSamplesSize(currentSampleSize) // Call within the function
    }
    // --- End of New code for Sample size Slider---

    Box(
        modifier = Modifier
            .background(Color(0xFF000030)) // Темно-синий цвет
            //.background(Color.Black) // Заливаем цветом
            //.offset(animatedOffsetX.dp, animatedOffsetY.dp)
            //.scale(animatedScaleX, animatedScaleY)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    Log.d("dip171", "detectTapGestures onTap")
                },
                onLongClick = {
                    Log.d("dip171", "detectTapGestures onLongTap")
                },
                onDoubleClick = {
                    Log.d("dip171", "detectTapGestures onDoubleTap")
                    //AnimateToNewValues(newScaleX = 1f, newScaleY = 1f, newOffsetX = 0f, newOffsetY = 0f)
                    scaleX = 1f
                    scaleY = 1f
                    offsetX = offsetXOSCdef
                    offsetY = offsetYOSCdef
                    offsetXFFT = offsetXFFTdef
                    offsetYFFT = offsetYFFTdef - 70f
                },
            )
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->

                    // Log.d("dip171", "centroid: $centroid pan: $pan zoom: $zoom")
                    // Обновляем масштаб отдельно по осям X и Y
                    scaleX *= zoom
                    scaleY *= zoom

                    // Получаем центр жеста
                    centerX = centroid.x
                    centerY = -centroid.y
                    //val centerY = size.height / 2 + offsetY

                    // Обновляем смещение на основе панорамирования
                    offsetX += pan.x
                    offsetY += pan.y

                    // Обновляем смещение на основе панорамирования
                    offsetXFFT += pan.x
                    offsetYFFT += pan.y

                    // Корректируем смещение для зума
                    // Вычисляем новое смещение с учетом зума
                    offsetX -= (centerX - offsetX) * (zoom - 1)
                    offsetY -= (centerY - offsetY) * (zoom - 1)

                    offsetXFFT -= (centerX - offsetXFFT) * (zoom - 1)
                    offsetYFFT -= (centerY - offsetYFFT) * (zoom - 1)
                    //Log.d("dip171", "scaleX: $scaleX offsetX: $offsetX offsetY: $offsetY")
                }
            }

    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width // Ширина Canvas
            val canvasHeight = size.height // Высота Canvas

            if (!startOffset) {
                startOffset = true
                offsetYFFTdef = canvasHeight
                offsetYFFT = offsetYFFTdef - 70f
            }


            // Измерение времени отрисовки и расчет FPS
            val currentTime = System.nanoTime()
            val deltaTime = currentTime - lastFrameTime
            lastFrameTime = currentTime
            fps = 1_000_000_000f / deltaTime

            // Обновление среднего FPS без коллекции
            fpsSum += fps
            fpsCount++
            if (fpsCount >= 100) {
                averageFps = fpsSum / fpsCount
                fpsSum = 0f
                fpsCount = 0
            }

//            // Измеряем время отрисовки
//            val currentTime = System.nanoTime()
//            val deltaTime = currentTime - lastFrameTime
//            lastFrameTime = currentTime
//
//            // Вычисление FPS
//            fps = (1_000_000_000f / deltaTime)
//
//            // Обновляем список FPS
//            if (fpsList.size >= 100) {
//                fpsList.removeAt(0) // Удаляем старое значение
//            }
//            fpsList.add(fps) // Добавляем новое значение

            // Вычисляем среднее значение FPS
            //averageFps = fpsList.average().toFloat()
            val roundedFps = String.format("%.1f", averageFps)
            val roundedOps = String.format("%.1f", ops)

            // Преобразуем данные в Вольты
            //voltageData = data.map { (it.toFloat() / 4095f) * 3.3f }

            if (isOSCActive) {
                drawGridOsc(sampleRate, data.size)
                drawAxesOsc()
                drawOsc(sampleRate, voltageData2)
            }
            if (isFFTActive) {
                drawGridFFT(sampleRate, data.size)
                drawFFT(data.size, sampleRate, voltageData2)
            }
            if (isFPSActive) {
                // Рисуем график FPS
                drawFpsGraph(fps, ops)
                // Вывод FPS на экран
                drawContext.canvas.nativeCanvas.apply {
                    drawText(
                        "$roundedFps FPS",
                        width - 400f, // Смещение по X
                        50f, // Смещение по Y
                        fpsPaint
                    )
                    drawText(
                        "$roundedOps OPS",
                        width - 400f, // Смещение по X
                        100f, // Смещение по Y
                        opsPaint
                    )
//                    drawText(
//                        "Scale: $scaleX, OffsetX: $offsetX, OffsetY: $offsetY, offsetYFFTdef: $offsetYFFTdef ",
//                        150f, // Смещение по X
//                        30f, // Смещение по Y
//                        infoPaint
//                    )

                }
            }
        }



        if (showSettings) {
            // Switches  Osc & FFT & Sliders
            Column(
                modifier = Modifier
                    .align(Alignment.Center) // Align to the bottom-right
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(bottom = 24.dp, end = 8.dp), // Отступ от нижнего и правого края
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
            ) {


                Row(
                    modifier = Modifier
                        .padding(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {


                    Text("OSC", color = Color(0xFFE0F000).copy(alpha = 0.8f))
                    Switch(
                        checked = isOSCActive,
                        onCheckedChange = { isOSCActive = it },
                        modifier = Modifier
                            .padding(end = 0.dp)
                            .size(width = 60.dp, height = 16.dp), // Change size here
                        thumbContent = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFF0D800).copy(alpha = 0.8f), // Customize colors if needed
                            uncheckedThumbColor = Color(0xFF6C6700).copy(alpha = 0.5f),
                            checkedTrackColor = Color(0xC6021D6C).copy(alpha = 0.8f),
                            uncheckedTrackColor = Color(0xC6021D6C).copy(alpha = 0.5f),
                        )
                    )
                    Text("FFT", color = Color(0xFF00F0F0).copy(alpha = 0.8f))
                    Switch(
                        checked = isFFTActive,
                        onCheckedChange = { isFFTActive = it },
                        modifier = Modifier
                            .padding(end = 0.dp)
                            .size(width = 60.dp, height = 16.dp), // Change size here
                        thumbContent = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00F0F0).copy(alpha = 0.8f), // Customize colors if needed
                            uncheckedThumbColor = Color(0xFF006C6C).copy(alpha = 0.5f),
                            checkedTrackColor = Color(0xC6021D6C).copy(alpha = 0.8f),
                            uncheckedTrackColor = Color(0xC6021D6C).copy(alpha = 0.5f),
                        )
                    )
                    Text("FPS", color = Color(0xFF00F00C).copy(alpha = 0.8f))
                    Switch(
                        checked = isFPSActive,
                        onCheckedChange = { isFPSActive = it },
                        modifier = Modifier
                            .padding(end = 0.dp)
                            .size(width = 60.dp, height = 16.dp), // Change size here
                        thumbContent = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF10F000).copy(alpha = 0.8f), // Customize colors if needed
                            uncheckedThumbColor = Color(0xFF106C00).copy(alpha = 0.5f),
                            checkedTrackColor = Color(0xC6021D6C).copy(alpha = 0.8f),
                            uncheckedTrackColor = Color(0xC6021D6C).copy(alpha = 0.5f),
                        )
                    )

                    IconButton( // CLOSE
                        onClick = {
                            showSettings = false
                        },
                        modifier = Modifier
                            .size(36.dp) // Set the size of the button (width and height)
                            .clip(RectangleShape) // Clip the button to a circle shape
                            .background(Color(0xFF3D0000).copy(alpha = 0.3f)) // Set the background color
                            .padding(1.dp) // Add some padding around the icon
                        //.size(24.dp)
                        //.align(Alignment.BottomEnd) // Align to bottom right
                        //.padding(end = 70.dp, bottom = 24.dp)

                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_close_24), // Используем иконку из drawable
                            contentDescription = if (showSettings) stringResource(id = R.string.hide_settings) else stringResource(
                                id = R.string.show_settings
                            ),
                            tint = Color.Red // Customize icon color
                        )
                        //Text("+", color = Color.White, fontSize = 16.sp)
                    }

                }


                // --- The Sample Size Slider ---
                Row(
                    modifier = Modifier
                        .padding(2.dp)
                        .align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Samples:", color = Color.White)
                    Text("${currentSampleSize}", color = Color.White)
                    Slider(
                        value = selectedSampleSizeIndex.toFloat(),
                        onValueChange = { index ->
                            updateSampleSize(index.toInt())
                            viewModel.updateSamplesSize(currentSampleSize)
                        },
                        valueRange = sampleSliderPositions.first()..sampleSliderPositions.last(),
                        steps = sampleSizes.size - 2,
                        modifier = Modifier.size(250.dp, 30.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Green,
                            activeTrackColor = Color.Green.copy(alpha = 0.7f),
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.3f),
                        )
                    )
                }
                // --- SFreq Slider ---
                Row(
                    modifier = Modifier
                        .padding(2.dp)
                        .align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("SFreq:", color = Color.White)
                    Text("${currentFrequency / 1000} kHz", color = Color.White)
                    Slider(
                        value = selectedFrequencyIndex.toFloat(),
                        onValueChange = { index ->
                            updateSampleFrequency(index.toInt())
                            viewModel.updateSamplesFreq(currentFrequency)
                        },
                        valueRange = sliderPositions.first()..sliderPositions.last(),
                        steps = frequencyValues.size - 2, // Number of steps
                        modifier = Modifier.size(250.dp, 30.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Yellow,
                            activeTrackColor = Color.Yellow.copy(alpha = 0.5f),
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.3f),
                        )
                    )
                }

                // --- Freq Slider ---
                Row(
                    modifier = Modifier
                        .padding(2.dp)
                        .align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Freq:", color = Color.White)
                    Text(String.format("%.4f Hz", signalFrequency))

                    IconButton(
                        onClick = {
                            updateSignalFrequency(signalFrequency - 0.01f)
                            viewModel.updateSignalFreq(signalFrequency)
                        },
                        modifier = Modifier
                            .size(24.dp)
                    ) {
                        Text("-", color = Color.White, fontSize = 16.sp)
                    }

                    Slider(
                        value = signalFrequency,
                        onValueChange = { frequency ->
                            updateSignalFrequency(frequency)
                            viewModel.updateSignalFreq(frequency)
                        },
                        valueRange = minFrequency..maxFrequency,
                        steps = 0, // Number of steps
                        modifier = Modifier.size(250.dp, 30.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Blue,
                            activeTrackColor = Color.Blue.copy(alpha = 0.5f),
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.3f),
                        )
                    )

                    IconButton(
                        onClick = {
                            updateSignalFrequency(signalFrequency + 0.01f)
                            viewModel.updateSignalFreq(signalFrequency)
                        },
                        modifier = Modifier
                            .size(24.dp)
                    ) {
                        Text("+", color = Color.White, fontSize = 16.sp)
                    }

                }

                // --- Ampl Slider ---
                Row(
                    modifier = Modifier
                        .padding(2.dp)
                        .align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Ampl:", color = Color.White)
                    Text("$signalAmplitude", color = Color.White)

                    IconButton(
                        onClick = {
                            if (signalAmplitude > 0) {
                                updateSignalAmplitude(signalAmplitude - 1)
                                viewModel.updateSignalAmpl(signalAmplitude)
                            }
                        },
                        modifier = Modifier
                            .size(24.dp)
                    ) {
                        Text("-", color = Color.White, fontSize = 16.sp)
                    }

                    Slider(
                        value = signalAmplitude.toFloat(),
                        onValueChange = { ampl ->
                            updateSignalAmplitude(ampl.toInt())
                            viewModel.updateSignalAmpl(ampl.toInt())
                        },
                        valueRange = minAmplitude.toFloat()..maxAmplitude.toFloat(),
                        steps = 0, // Number of steps
                        modifier = Modifier.size(250.dp, 30.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Magenta,
                            activeTrackColor = Color.Magenta.copy(alpha = 0.5f),
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.3f),
                        )
                    )

                    IconButton(
                        onClick = {
                            if (signalAmplitude < 4095) {
                                updateSignalAmplitude(signalAmplitude + 1)
                                viewModel.updateSignalAmpl(signalAmplitude)
                            }
                        },
                        modifier = Modifier
                            .size(24.dp)
                    ) {
                        Text("+", color = Color.White, fontSize = 16.sp)
                    }


                }

                // --- Noise Ampl Slider ---
                Row(
                    modifier = Modifier
                        .padding(2.dp)
                        .align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Noise:", color = Color.White)
                    Text("$noiseAmplitude", color = Color.White)

                    IconButton(
                        onClick = {
                            if (noiseAmplitude > 0) {
                                updateNoiseAmplitude(noiseAmplitude - 1)
                                viewModel.updateNoiseAmpl(noiseAmplitude)
                            }
                        },
                        modifier = Modifier
                            .size(24.dp)
                    ) {
                        Text("-", color = Color.White, fontSize = 16.sp)
                    }

                    Slider(
                        value = noiseAmplitude.toFloat(),
                        onValueChange = { ampl ->
                            updateNoiseAmplitude(ampl.toInt())
                            viewModel.updateNoiseAmpl(ampl.toInt())
                        },
                        valueRange = minNoiseAmplitude.toFloat()..maxNoiseAmplitude.toFloat(),
                        steps = 0, // Number of steps
                        modifier = Modifier.size(250.dp, 30.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.LightGray,
                            activeTrackColor = Color.LightGray.copy(alpha = 0.5f),
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.3f),
                        )
                    )

                    IconButton(
                        onClick = {
                            if (noiseAmplitude < 2047) {
                                updateNoiseAmplitude(noiseAmplitude + 1)
                                viewModel.updateNoiseAmpl(noiseAmplitude)
                            }
                        },
                        modifier = Modifier
                            .size(24.dp)
                    ) {
                        Text("+", color = Color.White, fontSize = 16.sp)
                    }


                }

            }
        }

        IconButton( // RECONNECT
            onClick = {
                viewModel.connectWSS2()
            },
            modifier = Modifier
                //.size(24.dp)
                .align(Alignment.BottomEnd) // Align to bottom right
                .padding(end = 70.dp, bottom = 24.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_autorenew_24), // Используем иконку из drawable
                contentDescription = if (showSettings) stringResource(id = R.string.hide_settings) else stringResource(
                    id = R.string.show_settings
                ),
                tint = Color.Red // Customize icon color
            )
            //Text("+", color = Color.White, fontSize = 16.sp)
        }

        IconButton( // SETTINGS
            onClick = {
                showSettings = !showSettings
                // Handle FFT button click here
            },
            modifier = Modifier
                .align(Alignment.BottomEnd) // Align to bottom right
                .padding(end = 24.dp, bottom = 24.dp)
        ) {
            //Text(if (showSettings) "SHOW" else "show")
            Icon(
                painter = painterResource(id = R.drawable.baseline_display_settings_24), // Используем иконку из drawable
                contentDescription = if (showSettings) stringResource(id = R.string.hide_settings) else stringResource(
                    id = R.string.show_settings
                ),
                tint = if (showSettings) Color.Yellow else Color.White // Customize icon color
            )

        }
    }
}

private fun DrawScope.drawFpsGraph(currentFps: Float, currentOps: Float) {
    val widthBox = size.width
    val heightBox = size.height
    // Ограничиваем размер списка до 200 значений
    if (fpsHistory.size >= 200) {
        fpsHistory.removeAt(0)
    }
    fpsHistory.add(currentFps)

    if (opsHistory.size >= 200) {
        opsHistory.removeAt(0)
    }
    opsHistory.add(currentOps)

    val width = 200f // Ширина графика
    val height = 140f // Высота графика
    val graphXOffset = widthBox - 610f // Смещение по X для графика
    val graphYOffset = 140f // Смещение по Y для графика

    drawRect(
        color = Color.Green.copy(alpha = 0.5f),
        topLeft = Offset(graphXOffset, graphYOffset - height),
        size = Size(width, height),
        style = Stroke(width = 2f) // Установка ширины линии
    )

    // Рисуем заливку между графиком и осью X
    for (i in 0 until fpsHistory.size - 1) {
        val startX = graphXOffset + i * (width / 200)
        val endX = graphXOffset + (i + 1) * (width / 200)
        val startY = graphYOffset - (fpsHistory[i].coerceIn(0f, 70f) / 70f) * height
        val endY = graphYOffset - (fpsHistory[i + 1].coerceIn(0f, 70f) / 70f) * height
        val startY1 = graphYOffset - (opsHistory[i].coerceIn(0f, 70f) / 70f) * height
        val endY1 = graphYOffset - (opsHistory[i + 1].coerceIn(0f, 70f) / 70f) * height

        drawRect(
            color = Color.Green.copy(alpha = 0.3f),
            topLeft = Offset(startX, graphYOffset),
            size = Size(endX - startX, startY - graphYOffset)
        )
        drawLine(
            color = Color.Green,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color.Yellow,
            start = Offset(startX, startY1),
            end = Offset(endX, endY1),
            strokeWidth = 1.5f
        )
    }
}

@Composable
fun HeaderRow(
    isConnected: Boolean,
    isConnected2: Boolean,
    isTX: Boolean,
    isRX: Boolean,
    isTX2: Boolean,
    isRX2: Boolean,
    isCheckedConnect: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF000030)) // Темно-синий цвет
            .height(IntrinsicSize.Min),
        //.padding(vertical = (-4).dp),
        horizontalArrangement = Arrangement.Center,
        //horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                //.background(color = Color.Red) // Устанавливаем цвет фона
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
//            Text(text = "Connect", modifier = Modifier.padding(start = 8.dp))
//            Switch(
//                checked = isCheckedConnect,
//                onCheckedChange = onCheckedChange,
//                modifier = Modifier
//                    .scale(0.8f)
//                    .height(16.dp)
//            )
            Text(text = if (isConnected) "MQTT" else "mqtt")
            LedIndicatorGreen(isActive = isConnected)
        }
        Row(
            modifier = Modifier
                .padding(start = 4.dp)
                //.background(color = Color.Green) // Устанавливаем цвет фона
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "TX")
            LedIndicatorRed(isActive = isTX)
            Text(text = "RX")
            LedIndicatorGreen(isActive = isRX)
            Text(text = if (isConnected2) "  WSS" else "  wss")
            LedIndicatorGreen(isActive = isConnected2)
            Text(text = "TX")
            LedIndicatorRed(isActive = isTX2)
            Text(text = "RX")
            LedIndicatorGreen(isActive = isRX2)
        }

//        IconButton(
//            onClick = {
//                showSettings = !showSettings
//                // Handle FFT button click here
//            },
//            modifier = Modifier
//                .padding(end = 16.dp)
//        ) {
//            //Text(if (showSettings) "SHOW" else "show")
//            Icon(
//                painter = painterResource(id = R.drawable.baseline_blur_on_24), // Используем иконку из drawable
//                contentDescription = if (showSettings) stringResource(id = R.string.hide_settings) else stringResource(
//                    id = R.string.show_settings
//                ),
//                tint = if (showSettings) Color.Yellow else Color.White // Customize icon color
//            )
//
//        }

    }
}

@Composable
fun LedIndicatorGreen(isActive: Boolean) {
    // Определяем анимированный цвет
    val animatedColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF00FF00) else Color(0xFF006400), // ярко-зеленый и темно-зеленый
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "" // Настройка анимации
    )

    Box(
        modifier = Modifier
            .size(18.dp) // Размер светодиода
            //.background(animatedColor, shape = CircleShape)
            .border(1.dp, Color.Gray, shape = CircleShape) // Обводка
            .padding(1.5.dp) // Отступ для обводки
    ) {

        Box(
            modifier = Modifier
                .size(16.dp) // Размер светодиода
                .background(animatedColor, shape = CircleShape)
        )
    }
}

@Composable
fun LedIndicatorRed(isActive: Boolean) {
    // Определяем анимированный цвет
    val animatedColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFFFF0000) else Color(0xFF440000), // ярко-зеленый и темно-зеленый
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "" // Настройка анимации
    )

    Box(
        modifier = Modifier
            .size(18.dp) // Размер светодиода
            //.background(animatedColor, shape = CircleShape)
            .border(1.dp, Color.Gray, shape = CircleShape) // Обводка
            .padding(1.5.dp) // Отступ для обводки
    ) {

        Box(
            modifier = Modifier
                .size(16.dp) // Размер светодиода
                .background(animatedColor, shape = CircleShape)
        )
    }
}


