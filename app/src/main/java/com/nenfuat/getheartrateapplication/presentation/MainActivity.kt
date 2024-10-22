package com.nenfuat.getheartrateapplication.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.nenfuat.getheartrateapplication.presentation.theme.GetHeartRateApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity(), SensorEventListener {
    // センサマネージャ
    private lateinit var sensorManager: SensorManager
    private var HeartRateSensor: Sensor? = null
    // 心拍数表示用
    var heartRateData: MutableState<String> = mutableStateOf("タップしてスタート！")

    // レスポンスを受け取ったかどうかの状態
    private var isResponseReceived: MutableState<Boolean> = mutableStateOf(false)

    // タップの制御
    private var isTap: MutableState<Boolean> = mutableStateOf(false)

    // タップの制御
    private var isSendError: MutableState<Boolean> = mutableStateOf(false)

    private var player:String = ""

    // Wear OS固有のID(Android ID)
    private val androidId by lazy { Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) }
    // 心拍数の取得フラグ
    private var isHeartRateMonitoring: MutableState<Boolean> = mutableStateOf(false)



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
            WearApp(heartRateData,isResponseReceived,isTap, ::sendAndroidIdToServer, ::syncStatus)
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
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // パーミッションをリクエスト
            requestPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        }
    }

    // Android IDをサーバに送信するメソッド
    private fun sendAndroidIdToServer() {
        //エラー画面が表示されている場合
        if(isSendError.value){
            heartRateData.value = "タップしてスタート！"
            isSendError.value = false
            isTap.value = false
        }

        //リクエスト送信画面
        else if(!isTap.value) {
            isTap.value = true
            CoroutineScope(Dispatchers.IO).launch {
                try {

                    val url = URL("https://hartlink-api.onrender.com/id")
                    with(url.openConnection() as HttpURLConnection) {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; utf-8")

                        //jsonオブジェクトにAndroidIdを追加
                        val jsonInputString = JSONObject().apply {
                            put("id", androidId)
                        }.toString()

                        outputStream.use { os ->
                            val input = jsonInputString.toByteArray(Charsets.UTF_8)
                            os.write(input, 0, input.size)
                        }

                        val response = inputStream.bufferedReader(Charsets.UTF_8).use { br ->
                            br.readLines().joinToString("")
                        }

                        // レスポンスの解析
                        val jsonResponse = JSONObject(response)
                        player = jsonResponse.optString("player", "不明なプレイヤー")

                        withContext(Dispatchers.Main) {
                            heartRateData.value = "あなたは\nプレイヤー$player"
                            isResponseReceived.value = true
                        }
                    }
                }
                //送信できなかったらエラー画面を表示
                catch (e: Exception) {
                    Log.e("HTTP Error", e.message ?: "Unknown error")
                    withContext(Dispatchers.Main) {
                        heartRateData.value = "接続できません\nタップで戻る"
                        isSendError.value = true
                    }
                }
            }
        }
    }

    private fun syncStatus() {
        heartRateData.value = "待機中…"
        isResponseReceived.value = false

        CoroutineScope(Dispatchers.IO).launch {
            var retryCount = 0
            val retryMax = 20
            try {
                while (retryCount < retryMax) {
                    Log.i("Syncing Status", "Trying... (${retryCount + 1}/$retryMax)")
                    withContext(Dispatchers.Main) {
                        heartRateData.value = "同期待ち... (${retryCount + 1}/$retryMax s)"
                    }

                    val url = URL("https://hartlink-api.onrender.com/status")
                    with(url.openConnection() as HttpURLConnection) {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; utf-8")

                        val jsonInputString = JSONObject().apply {
                            put("status", "ok")
                        }.toString()

                        outputStream.use { os ->
                            val input = jsonInputString.toByteArray(Charsets.UTF_8)
                            os.write(input, 0, input.size)
                        }

                        val response = inputStream.bufferedReader(Charsets.UTF_8).use { br ->
                            br.readLines().joinToString("")
                        }

                        val jsonResponse = JSONObject(response)
                        if (jsonResponse.getString("status") == "ok") {
                            Log.i("Syncing Status", "   -> Status `OK` received!; Escaping...")
                            withContext(Dispatchers.Main) {
                                heartRateData.value = "計測開始!"
                                delay(2000)
                                startHeartRateMonitoring()
                            }
                            return@launch
                        }
                        retryCount++;
                        Log.i("Syncing Status", "   -> Status `OK` not received...")
                        delay(1000)
                    }
                }
            }
            catch (e: Exception) {
                Log.e("HTTP Error", e.message ?: "Unknown error")
                withContext(Dispatchers.Main) {
                    heartRateData.value = "あなたは\nプレイヤー$player"
                    isResponseReceived.value = true
                }
            }

            if (!isResponseReceived.value) {
                Log.e("Syncing Status", "なんの成果も得られませんでした！！！ ($retryMax)")
                withContext(Dispatchers.Main) {
                    heartRateData.value = "接続できませんでした"
                    delay(2000)
                    heartRateData.value = "あなたは\nプレイヤー$player"
                    isResponseReceived.value = true
                }
                return@launch
            }
        }
    }

    // 心拍数の取得を開始するメソッド
    private fun startHeartRateMonitoring() {
        heartRateData.value = "0.0"
        if (!isHeartRateMonitoring.value) {
            HeartRateSensor?.also { heartRateSensor ->
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
            isHeartRateMonitoring.value = true
        }
    }

    //心拍の取得
    // センサの値が変更されたときに呼ばれる
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && isHeartRateMonitoring.value) {
            if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
                val heartRate = "${event.values[0]}"
                heartRateData.value = heartRate

                // 心拍数が変化した際にサーバーに送信
                sendHeartRateToServer(heartRate)
            }
        }
    }

    // センサの精度が変更されたときに呼ばれる(今回は何もしない)
    override fun onAccuracyChanged(event: Sensor?, p1: Int) {}

    override fun onResume() {
        super.onResume()
        if (isHeartRateMonitoring.value) {
            HeartRateSensor?.also { heartRateSensor ->
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    //他アプリを開いた時の処理
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // 心拍情報を送信する処理
    private fun sendHeartRateToServer(heartRate: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://hartlink-api.onrender.com/data")

                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; utf-8")

                    // JSONオブジェクトに整数型で心拍数を追加
                    val jsonInputString = JSONObject().apply {
                        put("id", androidId)
                        put("heartRate", heartRate)
                    }.toString()


                    outputStream.use { os ->
                        val input = jsonInputString.toByteArray(Charsets.UTF_8)
                        os.write(input, 0, input.size)
                    }

                    //心拍情報に対するレスポンス
                    val response = inputStream.bufferedReader(Charsets.UTF_8).use { br ->
                        br.readLines().joinToString("0")
                    }

                    val jsonResponse = JSONObject(response)



                    if (jsonResponse.getString("status") == "end") {

                        heartRateData.value = "終了"
                        isHeartRateMonitoring.value = false
                        delay(2000)
                        heartRateData.value = "タップでスタート！"
                        isResponseReceived.value = false
                        isTap.value = false
                        isSendError.value =false
                        player = ""

                    }
                }
            }
            catch (e: Exception) {
                Log.e("HTTP Error", e.message ?: "Unknown error")
            }
        }
    }
}

//　
@Composable
fun WearApp(
    heartRateData: MutableState<String>,
    isResponseReceived: MutableState<Boolean>,
    isTap: MutableState<Boolean>,
    sendAndroidIdToServer: () -> Unit,
    syncStatus: () -> Unit
) {
    //基本画面
    GetHeartRateApplicationTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .clickable { sendAndroidIdToServer() }, // タップで心拍数取得を開始
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            if(!isResponseReceived.value) {
                Greeting(heartRateData = heartRateData)
            }
            if (isResponseReceived.value) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center, // 縦方向に中央配置
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = heartRateData.value, // プレイヤー番号
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                    // レスポンスがあった場合に表示するボタン
                    OKButton(syncStatus)
                }
            }
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

@Composable
fun OKButton(
    syncStatus: () -> Unit
){
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .fillMaxHeight(0.2f)
            .background(MaterialTheme.colors.primary)
            .clickable {
                syncStatus()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "OK",
            color = MaterialTheme.colors.onPrimary,
            textAlign = TextAlign.Center
        )
    }
}