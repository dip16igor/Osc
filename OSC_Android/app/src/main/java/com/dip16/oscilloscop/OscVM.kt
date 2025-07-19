package com.dip16.oscilloscop

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OscVM(application: Application) : AndroidViewModel(application) {

    private val _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean> get() = _isConnected
    private var _isTx = MutableLiveData(false)
    val isTx: LiveData<Boolean> get() = _isTx
    private var _isRx = MutableLiveData(false)
    val isRx: LiveData<Boolean> get() = _isRx
    private var _ssid = MutableLiveData<String>()
    val ssid: LiveData<String> get() = _ssid
    private var _rssi = MutableLiveData<String>()
    val rssi: LiveData<String> get() = _rssi
    private var _vbat = MutableLiveData<String>()
    val vbat: LiveData<String> get() = _vbat
    private var _ip = MutableLiveData<String>()
    val ip: LiveData<String> get() = _ip
    private var _ip2 = MutableLiveData<String>()
    val ip2: LiveData<String> get() = _ip2

    private val _isConnectedWS = MutableLiveData(false)
    val isConnectedWS: LiveData<Boolean> get() = _isConnectedWS
    private var _isRxWS = MutableLiveData(false)
    val isRxWS: LiveData<Boolean> get() = _isRxWS
    private var _isTxWS = MutableLiveData(false)
    val isTxWS: LiveData<Boolean> get() = _isTxWS

    private var _updateDataCount = MutableLiveData(0)
    val updateDataCount: LiveData<Int> get() = _updateDataCount
    private var _ops = MutableLiveData(0)
    val ops: LiveData<Int> get() = _ops

    private var pingTimeMQTT: Long = 0
    private var pongTimeMQTT: Long = 0
    private var pingJobMQTT: Job? = null // Изменяем на nullable
    private val pingScopeMQTT = CoroutineScope(Dispatchers.IO) // Убираем Job из CoroutineScope
    private var isPingRunningMQTT = false // Флаг для отслеживания состояния пинга
    private var pingTimeWS: Long = 0
    private var pongTimeWS: Long = 0
    private var pingJobWS: Job? = null // Изменяем на nullable
    private val pingScopeWS = CoroutineScope(Dispatchers.IO) // Убираем Job из CoroutineScope
    private var isPingRunningWS = false // Флаг для отслеживания состояния пинга

    @SuppressLint("StaticFieldLeak")
    private val context = application.applicationContext

    private var updateCounterJob: Job? = null

    //private var updateDataCount = 0
    private var lastUpdateTime = System.currentTimeMillis()

    // Переменные для хранения времени и FPS
    private var lastDataTime: Long? = null
    private val updateCountList = mutableListOf<Long>()

    private var _averageUpdatesPerSecond = MutableLiveData(0f)
    val averageUpdatesPerSecond: LiveData<Float> get() = _averageUpdatesPerSecond

    //private var _averageUpdatesPerSecond: Float = 0f

    private val _mqttData = MutableStateFlow(
        MqttData(
            samplingData = SamplingData(
                startTime = 0L,
                samplingFrequency = 0,
                sampleSize = 0,
                samples = emptyList()
            )
        )
    )
    val mqttData: StateFlow<MqttData> get() = _mqttData

    // Add LiveData for xStep and yStep
    private val _samplesSize = MutableLiveData<Int>(1024) // Default xStep value
    val samplesSize: LiveData<Int> = _samplesSize
    private val _samplesFreq = MutableLiveData<Int>(10000) // Default xStep value
    val samplesFreq: LiveData<Int> = _samplesFreq


    private var simFreq = 0f //
    private var simAmpl = 0 //
    private var noiseAmpl = 0 //


    private val mqttService: MqttService = MqttService({ data ->
        _mqttData.value = data
        //_updateDataCount.postValue((_updateDataCount.value ?: 0) + 1)
        //NewDataCame()
        handleNewData()
    }, { isConnected ->
        updateConnectionStatus(isConnected) // Обновляем состояние соединения
    }, { isRX ->
        updateRX(isRX)
    }, { isTX ->
        updateTX(isTX)
    }, { ip ->
        updateIP(ip)
    }, { ssid ->
        updateSSID(ssid)
    }, { rssi ->
        updateRSSI(rssi)
    }, { vbat ->
        updateVbat(vbat)
    }, {
        pingRXmqtt()
    }, {
        cmdAsk()
    })

    private val wsService: WebSocketService = WebSocketService({ data ->
        _mqttData.value = data
        //_updateDataCount.postValue((_updateDataCount.value ?: 0) + 1)
        //NewDataCame()
        handleNewData()
    }, { isConnected ->
        updateConnectionStatus2(isConnected) // Обновляем состояние соединения
    }, { isRX ->
        updateRX2(isRX)
    }, { isTX ->
        updateTX2(isTX)
    }, { ip ->
        updateIP(ip)
    }, {
        pingRXws()
    })

    fun updateSamplesSize(newSamplesSize: Int) {
        _samplesSize.value = newSamplesSize
        mqttService.sendData("Osc/SetSSize", newSamplesSize.toString())
    }

    fun updateSamplesFreq(newFreq: Int) {
        //_samplesSize.value = newFreq
        _samplesFreq.value = newFreq
        mqttService.sendData("Osc/SetSFreq", newFreq.toString())
    }

    fun updateSignalFreq(newFreq: Float) {
        //_samplesSize.value = newFreq
        simFreq = newFreq
        mqttService.sendData("Osc/SetFreq", newFreq.toString())
    }

    fun updateSignalAmpl(newAmpl: Int) {
        //_samplesSize.value = newFreq
        simAmpl = newAmpl
        mqttService.sendData("Osc/SetAmpl", newAmpl.toString())
    }

    fun updateNoiseAmpl(newAmpl: Int) {
        //_samplesSize.value = newFreq
        noiseAmpl = newAmpl
        mqttService.sendData("Osc/SetNoise", newAmpl.toString())
    }

    private fun cmdAsk() {
        Log.i("dip171", "cmdAsk ?")
        viewModelScope.launch {
            mqttService.sendData("Osc/SetSSize", _samplesSize.value.toString())
            delay(200)
            mqttService.sendData("Osc/SetSFreq", _samplesFreq.value.toString())
            delay(200)
            mqttService.sendData("Osc/SetFreq", simFreq.toString())
            delay(200)
            mqttService.sendData("Osc/SetAmpl", simAmpl.toString())
            delay(200)
            mqttService.sendData("Osc/SetNoise", noiseAmpl.toString())
        }
    }

    private fun handleNewData() {
        // Получаем текущее время
        //val currentTime = System.nanoTime()

        // Вычисляем время между приходами новых значений
        //val timeDiff = currentTime - (lastDataTime ?: currentTime)
        //lastDataTime = currentTime // Обновляем время последнего получения данных

        // Преобразуем время в миллисекунды
        //val timeDiffInMillis = timeDiff / 1_000_000

        // Логируем время между приходами данных
        //Log.d("dip171", "Time between data updates: $timeDiffInMillis ms")

        // Обновляем FPS
        updateCountList.add(System.currentTimeMillis())
        if (updateCountList.size > 100) {
            updateCountList.removeAt(0) // Удаляем старое значение, если список больше 100
        }

        // Вычисляем FPS
        val elapsedTime =
            (updateCountList.last() - updateCountList.first()) / 1000f // время в секундах
        _averageUpdatesPerSecond.postValue(updateCountList.size / elapsedTime)

        // Логируем FPS
        //Log.d("dip171", "Average updates per second: $averageUpdatesPerSecond")
    }

    @SuppressLint("DefaultLocale")
    @Composable
    private fun NewDataCame() {
        var lastFrameTime by remember { mutableLongStateOf(System.nanoTime()) }
        var fps by remember { mutableFloatStateOf(0f) }
        // Список для хранения значений FPS
        val fpsList = remember { mutableStateListOf<Float>() }
        var averageFps by remember { mutableFloatStateOf(0f) }

        val currentTime = System.nanoTime()
        val deltaTime = currentTime - lastFrameTime
        lastFrameTime = currentTime

        // Вычисление FPS
        fps = (1_000_000_000f / deltaTime)

        // Обновляем список FPS
        if (fpsList.size >= 200) {
            fpsList.removeAt(0) // Удаляем старое значение
        }
        fpsList.add(fps) // Добавляем новое значение

        // Вычисляем среднее значение FPS
        averageFps = fpsList.average().toFloat()
        val roundedFps = String.format("%.1f", averageFps)
    }

//    init {
//        // Запускаем корутину для подсчета обновлений mqttData
//        startUpdateCounter()
//    }

    fun connectMQTT() {
        viewModelScope.launch {
            mqttService.connect()
        }
    }

    fun connectWSS(ip: String) {
        viewModelScope.launch {
            wsService.connect(ip)
        }
    }

    fun connectWSS2() {
        viewModelScope.launch {
            disconnectWSS()
            wsService.connect("ws://${_ip.value}:81")
        }
    }

    fun disconnectMQTT() {
        mqttService.disconnect()
        stopPingMQTT()
    }

    fun disconnectWSS() {
        wsService.disconnect()
        stopPingWS()
    }

    fun closeConnectionMQTT() {
        mqttService.closeConnection()
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        _isConnected.postValue(isConnected)
        Log.d("dip171", "isConnected = $isConnected")
        if (isConnected) {
            //updateTX(true)
            startPingMQTT()
            //startUpdateCounter()
            viewModelScope.launch {
                mqttService.sendData("Osc/Cmd", "IP?")
                delay(1000) // Задержка 1 секунда (1000 миллисекунд)
                mqttService.sendData("Osc/Cmd", "SSID?")
            }

        } else {
            stopPingMQTT()
            //connect()
        }
        _ip2.postValue(getLocalIpAddress(context).toString())
    }

    private fun updateConnectionStatus2(isConnected: Boolean) {
        _isConnectedWS.postValue(isConnected)
        Log.d("dip171", "isConnectedWS = $isConnected")
        if (isConnected) {
            //updateTX(true)
            startPingWS()
            //startUpdateCounter()
        } else {
            stopPingWS()
        }
    }

    private fun pingRXmqtt() {
        pongTimeMQTT = System.currentTimeMillis() // Сохраняем время получения PONG
        val pingDuration = pongTimeMQTT - pingTimeMQTT // Вычисляем задержку
        Log.d("dip171", "MQTT PING duration: $pingDuration ms")
    }

    private fun pingRXws() {
        pongTimeWS = System.currentTimeMillis() // Сохраняем время получения PONG
        val pingDuration = pongTimeWS - pingTimeWS // Вычисляем задержку
        Log.d("dip171", "WS PING duration: $pingDuration ms")
    }

    private fun startPingMQTT() {
        Log.d("dip171", "MQTT startPing()")
        if (!isPingRunningMQTT) { // Проверяем, запущен ли уже пинг
            isPingRunningMQTT = true // Устанавливаем флаг
            Log.d("dip171", "mqtt pingScope.launch")
            pingJobMQTT = Job() // Создаем новый Job
            pingScopeMQTT.launch(pingJobMQTT!!) {
                while (isActive) {
                    //Log.d("dip171", "mqtt isActive")
                    pingTimeMQTT = System.currentTimeMillis() // Сохраняем время PING
                    //Log.d("dip171", "Sending PING at: $pingTime")
                    mqttService.sendData("Osc/PingRequest", "PING") // Отправляем PING
                    delay(10000) // Задержка 10 секунд
                }
            }
        }
    }

    private fun stopPingMQTT() {
        Log.d("dip171", "MQTT stopPing()")
        pingJobMQTT?.cancel() // Останавливаем корутину PING
        pingJobMQTT = null // Сбрасываем Job
        isPingRunningMQTT = false // Сбрасываем флаг
    }

    private fun startPingWS() {
        Log.d("dip171", "WS startPing()")
        if (!isPingRunningWS) { // Проверяем, запущен ли уже пинг
            isPingRunningWS = true // Устанавливаем флаг
            Log.d("dip171", "ws pingScope.launch")
            pingJobWS = Job() // Создаем новый Job
            pingScopeWS.launch(pingJobWS!!) {
                while (isActive) {
                    //Log.d("dip171", "ws isActive")
                    pingTimeWS = System.currentTimeMillis() // Сохраняем время PING
                    //Log.d("dip171", "Sending PING at: $pingTime")
                    wsService.sendMessage("PING") // Отправляем PING
                    delay(10000) // Задержка 10 секунд
                }
            }
        }
    }

    private fun stopPingWS() {
        Log.d("dip171", "WS stopPing()")
        pingJobWS?.cancel() // Останавливаем корутину PING
        pingJobWS = null // Сбрасываем Job
        isPingRunningWS = false // Сбрасываем флаг
    }

    private val handler1 = Handler(Looper.getMainLooper())

    private fun updateRX(isRX: Boolean) {
        _isRx.postValue(isRX)
        //Log.d("dip171", "_isRX = $isRX")
        handler1.removeCallbacksAndMessages(null)
        //val handler1 = Handler(Looper.getMainLooper())
        handler1.postDelayed({ _isRx.value = false }, 250)
    }

    private val handler2 = Handler(Looper.getMainLooper())

    private fun updateRX2(isRX: Boolean) {
        _isRxWS.postValue(isRX)
        //Log.d("dip171", "_isRX = $isRX")
        handler2.removeCallbacksAndMessages(null)
        //val handler1 = Handler(Looper.getMainLooper())
        handler2.postDelayed({ _isRxWS.value = false }, 250)
    }

    private val handler3 = Handler(Looper.getMainLooper())

    private fun updateTX(isTX: Boolean) {
        _isTx.postValue(isTX)
        //Log.d("dip171", "_isTX = $isRX")
        handler3.removeCallbacksAndMessages(null)
        //val handler1 = Handler(Looper.getMainLooper())
        handler3.postDelayed({ _isTx.value = false }, 250)
    }

    private val handler4 = Handler(Looper.getMainLooper())

    private fun updateTX2(isTX: Boolean) {
        _isTxWS.postValue(isTX)
        //Log.d("dip171", "_isTX = $isRX")
        handler4.removeCallbacksAndMessages(null)
        //val handler1 = Handler(Looper.getMainLooper())
        handler4.postDelayed({ _isTxWS.value = false }, 250)
    }

    private fun updateIP(ip: String) {
        _ip.postValue(ip)
        if (isValidIP(ip)) {
            Log.d("dip171", "Try to connect to WS server ws://$ip:81")
            connectWSS("ws://$ip:81")
        }
        //Log.d("dip171", "_IP = $ip")
    }

    private fun updateSSID(ip: String) {
        _ssid.postValue(ip)
        //Log.d("dip171", "_IP = $ip")
    }

    private fun updateRSSI(rssi: String) {
        _rssi.postValue(rssi)
        //Log.d("dip171", "_IP = $ip")
    }

    private fun updateVbat(vbat: String) {
        _vbat.postValue(vbat)
        //Log.d("dip171", "_IP = $ip")
    }

    override fun onCleared() {
        Log.d("dip171", "onCleared()")
        super.onCleared()

        disconnectMQTT()
        closeConnectionMQTT()
    }

    private fun isValidIP(ip: String): Boolean {
        val ipPattern = """^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(
                      |25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(
                      |25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(
                      |25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""".trimMargin().replace("\n", "")

        return ip.matches(ipPattern.toRegex())
    }

    @SuppressLint("DefaultLocale")
    private fun getLocalIpAddress(context: Context): String? {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

    @SuppressLint("DefaultLocale")
    fun getLocalIpAddress2(context: Context): String? {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

}