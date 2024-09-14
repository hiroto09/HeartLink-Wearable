package com.nenfuat.getheartrateapplication.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.nenfuat.getheartrateapplication.presentation.theme.GetHeartRateApplicationTheme

class MainActivity : ComponentActivity(), SensorEventListener {
    // センサマネージャ
    private lateinit var sensorManager: SensorManager
    private var HeartRateSensor: Sensor? = null
    // 心拍数表示用
    var heartRateData: MutableState<String> = mutableStateOf("データがありません")
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // パーミッションが許可された場合、センサーの登録を行う
                HeartRateSensor?.also { heartRateSensor ->
                    sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
                }
            } else {
                // パーミッションが拒否された場合
                Log.e("MainActivity", "パーミッションが拒否されました。")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp(heartRateData)
        }
        //画面が勝手に切れないように
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // センサの初期設定
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        HeartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        // パーミッションの確認とリクエスト
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BODY_SENSORS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // パーミッションが既に許可されている場合、センサーの登録
            HeartRateSensor?.also { heartRateSensor ->
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else {
            // パーミッションをリクエスト
            requestPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        }
    }

    // センサの値が変更されたときに呼ばれる
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
                heartRateData.value = "${event.values[0]}"
            }
        }
    }

    // センサの精度が変更されたときに呼ばれる(今回は何もしない)
    override fun onAccuracyChanged(event: Sensor?, p1: Int) {}

    override fun onResume() {
        super.onResume()
        HeartRateSensor?.also { heartRateSensor ->
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}

@Composable
fun WearApp(heartRateData: MutableState<String>) {
    GetHeartRateApplicationTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            Greeting(heartRateData = heartRateData)
        }
    }
}

@Composable
fun Greeting(heartRateData: MutableState<String>) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = heartRateData.value
    )
}
