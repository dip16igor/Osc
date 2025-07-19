package com.dip16.oscilloscop

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence


class MqttService(
    private val onMessageReceived: (MqttData) -> Unit,
    private val onConnectionStatusChanged: (Boolean) -> Unit,
    private val onRX: (Boolean) -> Unit,
    private val onTX: (Boolean) -> Unit,
    private val ipStr: (String) -> Unit,
    private val ssidStr: (String) -> Unit,
    private val rssiStr: (String) -> Unit,
    private val vbatStr: (String) -> Unit,
    private val pingRx: () -> Unit,
    private val cmdAsk: () -> Unit
) {
    private val mqttClient: MqttClient
    private val mqttOptions: MqttConnectOptions

    private var pingTime: Long = 0
    private var pongTime: Long = 0
    private val pingJob = Job()
    private val pingScope = CoroutineScope(Dispatchers.IO + pingJob)
    private var isPingRunning = false // Флаг для отслеживания состояния пинга

    init {
        //val mqttClientId = "Oscilloscope1"
        val mqttClientId = MqttClient.generateClientId()
        //mqttClient = MqttClient("tcp://46.8.233.146:1883", mqttClientId, MemoryPersistence()) // Fin
        mqttClient = MqttClient("tcp://80.211.205.234:1883", mqttClientId, MemoryPersistence()) // Czech
        mqttOptions = MqttConnectOptions().apply {
            isCleanSession = true
            userName = "dip16"
            password = "nirvana7".toCharArray()
            isAutomaticReconnect = true
            maxReconnectDelay = 2000
        }
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        Log.i("dip171", "MQTT connect()")

        if (mqttClient.isConnected) {
            //startPing()
            sendData("Osc/Cmd", "IP?")
            Log.d("dip171", "Already connected, return")
            return@withContext
        }

        try {
            mqttClient.setCallback(object : MqttCallbackExtended {

                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    onConnectionStatusChanged(true)
                    Log.d("dip171", "connectComplete!")
                    //startPing()
                }

                override fun connectionLost(cause: Throwable?) {
                    onConnectionStatusChanged(false)
                    Log.d("dip171", "connectionLost! $cause")
                    //stopPing()
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    onRX(true)
                    //Log.d("dip171", "$message")
                    when (topic) {
                        "Osc/Data" -> {
                            val data = processMessage(message)
                            onMessageReceived(data)
                        }
                        "Osc/IP" -> {
                            Log.d("dip171", "IP: $message")
                            ipStr(message.toString())
                        }
                        "Osc/SSID" -> {
                            Log.d("dip171", "SSID: $message")
                            ssidStr(message.toString())
                        }
                        "Osc/Rssi" -> {
                            Log.d("dip171", "Rssi: $message dBm")
                            rssiStr(message.toString())
                        }
                        "Osc/Vbat" -> {
                            Log.d("dip171", "Vbat: $message V")
                            vbatStr(message.toString())
                        }
                        "Osc/System" -> {
                            Log.d("dip171", "System: $message")
                        }
                        "Osc/PingAsk" -> {
                            //Log.d("dip171", "Ping: $message")
                            if (message.toString() == "PONG") {
                                pingRx()
//                                pongTime = System.currentTimeMillis() // Сохраняем время получения PONG
//                                val pingDuration = pongTime - pingTime // Вычисляем задержку
//                                Log.d("dip171", "MQTT PING duration: $pingDuration ms")
                            }
                        }
                        "Osc/CmdAsk" -> {
                            //Log.d("dip171", "CmdAsk: $message")
                            if (message.toString() == "?"){
                                cmdAsk()
                            }
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Обработка завершения доставки
                    //Log.i("dip171", "mqtt deliveryComplete")
                }
            })

            Log.i("dip171", "mqttClient.connect..")
            mqttClient.connect(mqttOptions) // Подключение к MQTT

            Log.i("dip171", "Connected to MQTT")
            mqttClient.subscribe("Osc/Data", 0)
            mqttClient.subscribe("Osc/IP", 0)
            mqttClient.subscribe("Osc/SSID", 0)
            mqttClient.subscribe("Osc/System", 0)
            mqttClient.subscribe("Osc/PingAsk", 0)
            mqttClient.subscribe("Osc/Rssi", 0)
            mqttClient.subscribe("Osc/Vbat", 0)
            mqttClient.subscribe("Osc/CmdAsk", 0)
            //onConnectionStatusChanged(true)
            Log.i("dip171", "Subscribed")
        } catch (e: MqttException) {
            Log.e("dip171", "MqttException.. $e")
        }
    }

    fun sendData(topic: String, data: String) {
        if (!mqttClient.isConnected) {
            Log.e("dip171", "MQTT is not connected!")
            return
        }
        onTX(true)
        //Log.i("dip171", "Try to send")
        // Преобразуем MqttData в массив байтов (предполагается, что у вас есть метод для этого)
        val payload = data.toByteArray() // Преобразование строки в массив байтов

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val message = MqttMessage(payload).apply {
                    qos = 0 // Уровень качества обслуживания (QoS)
                    isRetained = false // Указывает, нужно ли сохранять сообщение
                }
                mqttClient.publish(topic, message) // Отправляем сообщение в указанный топик
                //Log.i("dip171", "Message sent to topic: $topic")
            } catch (e: MqttException) {
                Log.e("dip171", "Failed to send message: $e")
            }
        }
    }

    private fun startPing() {
        if (!isPingRunning) { // Проверяем, запущен ли уже пинг
            isPingRunning = true // Устанавливаем флаг
            pingScope.launch {
                while (isActive) {
                    pingTime = System.currentTimeMillis() // Сохраняем время PING
                    //Log.d("dip171", "Sending PING at: $pingTime")
                    sendData("Osc/Ping", "PING") // Отправляем PING
                    delay(10000) // Задержка 5 секунд
                }
            }
        }
    }

    private fun stopPing() {
        Log.d("dip171", "MQTT stopPing()")
        pingJob.cancel() // Останавливаем корутину PING
        isPingRunning = false // Сбрасываем флаг
    }

    fun disconnect() {
        Log.d("dip171", "MQTT disconnect()")
        if (!mqttClient.isConnected) return
        Log.i("dip171", "MQTT Disconnect..")
        mqttClient.disconnect()
        onConnectionStatusChanged(false) // Уведомляем о потере соединения
        //stopPing()
    }

    fun closeConnection() {
        Log.d("dip171", "MQTT closeConnection()")
        mqttClient.close()
    }

    private fun processMessage(message: MqttMessage?): MqttData {
        // Получаем массив байтов из сообщения
        val payload = message?.payload ?: ByteArray(0)

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