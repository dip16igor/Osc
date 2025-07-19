package com.dip16.oscilloscop

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import okhttp3.*
import okio.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttMessage

class WebSocketService(
    private val onMessageReceived: (MqttData) -> Unit,
    private val onConnectionStatusChanged: (Boolean) -> Unit,
    private val onRX: (Boolean) -> Unit,
    private val onTX: (Boolean) -> Unit,
    private val IPstr: (String) -> Unit,
    private val pingRx: () -> Unit,
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    private var pingTime: Long = 0
    private var pongTime: Long = 0
    private val pingJob = Job()
    private val pingScope = CoroutineScope(Dispatchers.IO + pingJob)
    private var isPingRunning = false // Флаг для отслеживания состояния пинга

    suspend fun connect(url: String) = withContext(Dispatchers.IO) {
        Log.d("dip171", "Connecting to WebSocket: $url")

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnectionStatusChanged(true)
                Log.d("dip171", "Connection to WebSocket established")
                //startPing() // Запускаем отправку PING
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onRX(true)
                // Обработка текстовых сообщений
                //Log.i("dip171", "WebSocket Message received: $text")
                // Здесь вы можете преобразовать текстовое сообщение в MqttData
                // и вызвать onMessageReceived(data)
                if (text == "PONG") {
                    pingRx()
//                    pongTime = System.currentTimeMillis() // Сохраняем время получения PONG
//                    val pingDuration = pongTime - pingTime // Вычисляем задержку
//                    Log.d("dip171", "WSS PING duration: $pingDuration ms")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onRX(true)
                // Обработка бинарных сообщений
                //Log.i("dip171", "WebSocket Binary message received")
                // Здесь вы можете преобразовать бинарные данные в MqttData
                // и вызвать onMessageReceived(data)
                val data = processMessage(bytes)
                onMessageReceived(data)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                //stopPing()  // Останавливаем PING при закрытии
                webSocket.close(1000, null)
                onConnectionStatusChanged(false)
                Log.d("dip171", "WebSocket Connection closing: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onConnectionStatusChanged(false)
                Log.e("dip171", "WebSocket Error: ${t.message}")
                //stopPing()  // Останавливаем PING при закрытии
            }

//            override fun onPong(webSocket: WebSocket, payload: ByteString) {
//                pongTime = System.currentTimeMillis() // Сохраняем время PONG
//                Log.d("dip171", "PONG received at: $pongTime")
//                // Можно обработать время между PING и PONG здесь, если нужно
//            }
        })
    }

    private fun startPing() {
        Log.d("dip171", "startPing: $isPingRunning")
        if (!isPingRunning) { // Проверяем, запущен ли уже пинг
            isPingRunning = true // Устанавливаем флаг
            Log.d("dip171", "new: $isPingRunning")
            pingScope.launch {
                Log.d("dip171", "pingScope.launch $isPingRunning")
                while (isActive) {
                    pingTime = System.currentTimeMillis() // Сохраняем время PING
                    //Log.d("dip171", "Sending WSS PING at: $pingTime")
                    onTX(true)
                    webSocket?.send("PING") // Отправляем PING
                    delay(10000) // Задержка 5 секунд
                }
                Log.d("dip171", "pingScope.launch finished") // Лог после завершения корутины
            }
        }
        else {
            Log.d("dip171", "Ping already running, not starting a new one.")
        }
    }

    private fun stopPing() {
        Log.d("dip171", "WSS stopPing()")
        pingJob.cancel() // Останавливаем корутину PING
        isPingRunning = false // Сбрасываем флаг
    }

    fun sendMessage(message: String) {
        onTX(true)
        webSocket?.send(message)
    }

    fun disconnect() {
        //stopPing() // Останавливаем PING при отключении
        webSocket?.close(1000, "Disconnecting")
        onConnectionStatusChanged(false)
        Log.d("dip171", "WebSocket disconnected")
    }

    private fun processMessage(message: ByteString?): MqttData {
        // Получаем массив байтов из ByteString
        val payload = message?.toByteArray() ?: ByteArray(0)

        // Устанавливаем значения startTime, samplingFrequency и sampleSize равными 0
        val startTime = 0L
        val samplingFrequency = 2048
        val sampleSize = payload.size / 2 // Каждый Int16 занимает 2 байта

        // Создаем массив для хранения значений Int16
        val samples = ShortArray(sampleSize)

        // Преобразуем массив байтов в массив Int16
        for (i in 0 until sampleSize) {
            // Порядок байтов может быть важен (Little Endian)
            val byte2 = payload[i * 2].toInt() and 0xFF // Первый байт
            val byte1 = payload[i * 2 + 1].toInt() and 0xFF // Второй байт
            samples[i] = (byte1 or (byte2 shl 8)).toShort() // Объединяем байты в Int16
        }

        // Создаем экземпляр SamplingData
        val samplingData = SamplingData(
            startTime = startTime,
            samplingFrequency = samplingFrequency,
            sampleSize = sampleSize,
            samples = samples.toList() // Преобразуем массив Short в List для SamplingData
        )

        // Возвращаем MqttData с заполненными данными
        return MqttData(samplingData = samplingData)
    }
}