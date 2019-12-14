package com.example.cloudbrainapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import java.nio.ByteBuffer

class ForegroundService : Service() {
    private val samplingRate = 44100
    private val frameRate = 10
    private val oneFrameDataCount = samplingRate / frameRate
    private val oneFrameSizeInByte = oneFrameDataCount * 2
    private val audioBufferSizeInByte =
        max(oneFrameSizeInByte * 10, // 適当に10フレーム分のバッファを持たせた
            AudioRecord.getMinBufferSize(samplingRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT))
    private val powerThreshold = 0.01
    private val minRecordTime = 10.0
    private val uploader = DataUploader()

    private var recordingCount = 0
    private var isRecording = false
    private var fos: FileOutputStream? = null

    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException("Not yet implemented")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String{
        val channelId = "cloud_brain"
        val channelName = "Cloud Brain"
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                //If earlier version channel ID is not used
                //https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        val notification = NotificationCompat.Builder(this, channelId).apply {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentText("Running Cloud Brain App")
            setContentTitle("Cloud Brain App")
        }.build()

        Thread(
            Runnable {
                val audioRecord = startSensing()

                Thread.sleep(1000 * 100)

                audioRecord.stop()

                stopForeground(true)
                // もしくは
                // stopSelf()

            }).start()

        startForeground(1, notification)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                uploader.startUploading(applicationInfo.dataDir, baseContext)
            }
        }

        val filter = IntentFilter()
        filter.addAction("startUploading")
        registerReceiver(receiver, filter)

        return START_STICKY
    }

    fun startSensing() : AudioRecord {
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            samplingRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            audioBufferSizeInByte
        )

        audioRecord.positionNotificationPeriod = oneFrameDataCount

        val audioDataArray = ShortArray(oneFrameDataCount)

        audioRecord.setRecordPositionUpdateListener(object : AudioRecord.OnRecordPositionUpdateListener {
            // フレームごとの処理
            override fun onPeriodicNotification(recorder: AudioRecord) {
                recorder.read(audioDataArray, 0, oneFrameDataCount) // 音声データ読込
                sensingProcess(audioDataArray)
            }

            // マーカータイミングの処理.
            // notificationMarkerPosition に到達した際に呼ばれる
            override fun onMarkerReached(recorder: AudioRecord) {
            }
        })

        audioRecord.startRecording()

        return audioRecord
    }

    private fun sensingProcess(data: ShortArray) {
        val power = power(data)
        // sendMessage("updatePower", power.toString())
        // writeLog("RMS: ${power}")

        if (isRecording) {
            // 録音中

            recordingCount++
            val recordingTime = recordingCount.toDouble() / frameRate.toDouble();

            val buf = ByteBuffer.allocate(oneFrameSizeInByte)
            for (datum in data) {
                buf.putShort(datum)
            }
            fos!!.write(buf.array())

            // 一定時間録音が続き、しきい値より音圧が小さくなったら録音停止
            // 録音を一定時間続けるのは音声が断片化しすぎないようにするため
            if (recordingTime > minRecordTime && power <= powerThreshold) {
                isRecording = false
                onStopRecording()
            }
        }
        else {
            // 録音待機中

            // しきい値より音圧が大きくなったら録音開始
            if (power > powerThreshold) {
                isRecording = true
                recordingCount = 0
                onStartRecording()
            }
        }
    }

    private fun onStartRecording(): FileOutputStream {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.UK).format(Date())
        val filename = "${DataUploader.prefix}${date}.${DataUploader.extension}"
        fos = baseContext.openFileOutput(filename, 0)

        writeLog("onStartRecording(): date=${date}")
        writeLog("current path: ${getApplicationInfo().dataDir}")

        return fos!!
    }

    private fun onStopRecording() {
        writeLog("onStopRecording()")

        fos?.close()
        fos = null
    }

    companion object {
        fun writeLog(msg: String) {
            Log.v("Brain Cloud Service", msg)
        }
    }

    fun power(audioDataArray: ShortArray): Double {
        return audioDataArray.map{v -> v.toDouble() / Short.MAX_VALUE}.map{v -> v*v}.average()
    }

    fun sendMessage(action: String, msg: String) {
        val broadcast = Intent()
        broadcast.setAction(action)
        broadcast.putExtra("message", msg)
        baseContext.sendBroadcast(broadcast)
    }
}

// 参考文献:
//  https://qiita.com/naoi/items/03e76d10948fe0d45597
//  https://qiita.com/ino-shin/items/214dba25f49fa098402f
