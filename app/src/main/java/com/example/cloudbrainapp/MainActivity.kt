package com.example.cloudbrainapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    var serviceIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.button).setOnClickListener {
            if (serviceIntent != null) stopService(serviceIntent)
            serviceIntent = Intent(this, ForegroundService::class.java)
            startService(serviceIntent)
        }

        findViewById<Button>(R.id.buttonUpload).setOnClickListener {
            val broadcast = Intent()
            broadcast.setAction("startUploading")
            sendBroadcast(broadcast)

        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val extras = intent?.getExtras()
                val power = extras?.getString("message")
                findViewById<TextView>(R.id.textView).setText("Power: ${power}")
            }
        }

        val filter = IntentFilter()
        filter.addAction("updatePower")
        registerReceiver(receiver, filter)
    }
}

// 参考文献:
//  https://sites.google.com/site/androiddevnote/20-zhong-ji/service-to-activity