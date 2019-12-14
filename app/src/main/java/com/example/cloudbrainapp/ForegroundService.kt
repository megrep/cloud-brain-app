package com.example.cloudbrainapp

import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.max
import kotlin.math.sqrt

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

    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "Cloud Brain App").apply {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentText("Running Cloud Brain App")
            setContentTitle("Cloud Brain App")
        }.build()

        Thread(
            Runnable {
                val audioRecord = startRecording()

                Thread.sleep(1000 * 10)

                audioRecord.stop()

                stopForeground(true)
                // もしくは
                // stopSelf()

            }).start()

        startForeground(1, notification)

        return START_STICKY
    }

    fun startRecording() : AudioRecord {
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
                val rms = rms(audioDataArray)
                sendMessage("updateRMS", rms.toString())
            }

            // マーカータイミングの処理.
            // notificationMarkerPosition に到達した際に呼ばれる
            override fun onMarkerReached(recorder: AudioRecord) {
            }
        })

        audioRecord.startRecording()

        return audioRecord
    }

    fun rms(audioDataArray: ShortArray): Double {
        return sqrt( audioDataArray.map{v -> v.toDouble() / Short.MAX_VALUE}.map{v -> v*v}.average() )
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