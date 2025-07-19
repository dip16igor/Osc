package com.dip16.oscilloscop

import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.dip16.oscilloscop.ui.theme.OscilloscopTheme

class MainActivity : ComponentActivity() {
    private val viewModel: OscVM by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    private lateinit var networkChangeReceiver: NetworkChangeReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Установка ориентации экрана на ландшафтную
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()

        setContent {
            OscilloscopTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OscScreen(viewModel)
                }
            }
        }

        // Инициализация и регистрация BroadcastReceiver
        networkChangeReceiver = NetworkChangeReceiver { isConnected ->
            if (isConnected) {
                Log.i("dip171", "Сеть доступна, подключение к MQTT")
                viewModel.connectMQTT() // подключение к MQTT
            } else {
                Log.i("dip171", "Сеть недоступна, отключение от MQTT и WSS ")
                viewModel.disconnectMQTT() // отключение от MQTT
                viewModel.disconnectWSS() // отключение от WSS
            }
        }
    }
//    private val viewModel: OscVM by viewModels()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // Установка ориентации экрана на ландшафтную
//        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//        enableEdgeToEdge()
//        setContent {
//            OscilloscopTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    OscScreen(viewModel)
//                }
//            }
//        }
//    }

    override fun onStart() {
        super.onStart()
        Log.d("dip171", "onStart()")
        // Подключение к MQTT
        viewModel.connectMQTT()
        // Подключение к WebSocket серверу
        //viewModel.connect2()
        val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkChangeReceiver, intentFilter)
    }

    override fun onResume() {
        super.onResume()
        Log.d("dip171", "onResume()")
    }

    override fun onStop() {
        super.onStop()
        Log.d("dip171", "onStop()")
        unregisterReceiver(networkChangeReceiver)
        // Отключение от MQTT
        viewModel.disconnectMQTT()
        viewModel.disconnectWSS()
        // Отключение от MQTT только если Activity действительно уничтожается
        if (isFinishing) { // Здесь используется свойство
            //viewModel.disconnect()
            viewModel.closeConnectionMQTT()
        }
    }

}

