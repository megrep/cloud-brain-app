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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.button).setOnClickListener {
            val serviceIntent = Intent(this, ForegroundService::class.java)
            startService(serviceIntent)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val extras = intent?.getExtras()
                val rms = extras?.getString("message")
                findViewById<TextView>(R.id.textView).setText("RMS: ${rms}")
            }
        }

        val filter = IntentFilter()
        filter.addAction("updateRMS")
        registerReceiver(receiver, filter)
    }
}

// 参考文献:
//  https://sites.google.com/site/androiddevnote/20-zhong-ji/service-to-activity