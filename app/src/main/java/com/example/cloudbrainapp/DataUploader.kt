package com.example.cloudbrainapp

import android.content.Context
import android.util.Base64
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.*
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*


class DataUploader {
    companion object {
        const val extension = "bin"
        const val prefix = "recorded_"

        private const val certFileName = "server.crt"

        private const val host = "10.11.13.129"
        // private const val host = "raspberrypi.local"
        private const val port = 5000

        private val url = "https://${host}:${port}/api/"
    }

    fun startUploading(currentDir: String, context: Context) {
        val files = dataFiles(currentDir + "/files")
        ForegroundService.writeLog("startUploading()")
        files?.forEach{f -> ForegroundService.writeLog(f.name)}
        files?.filter{ f -> f.isFile && f.name.endsWith(".${extension}") && f.name.startsWith(prefix) }?.forEach{ f -> upload(f, context) }
    }

    private fun dataFiles(currentDir: String) : Array<File>? {
        return File(currentDir).listFiles()
    }

    private fun upload(file: File, context: Context) {
        ForegroundService.writeLog("Uploading ${file.name}")

        val data = file.readBytes()
        val encoded = Base64.encodeToString(data, Base64.NO_WRAP)
        val date = file.name.removePrefix(prefix).removeSuffix(".${extension}")

        val jsonString =
            "{\n" +
            "    \"data\": \"${encoded}\",\n" +
            "    \"speaked_at\": \"${date}\"\n" +
            "}"
        ForegroundService.writeLog(jsonString.take(100) + "~" + jsonString.takeLast(100))

        Thread {
            doPost(url, mapOf(), jsonString, context)
        }.start()
    }

    @Throws(IOException::class)
    private fun doPost(
        url: String,
        headers: Map<String, String>,
        jsonString: String,
        context: Context
    ): String {
        val mediaTypeJson = okhttp3.MediaType.parse("application/json; charset=utf-8")

        val requestBody = RequestBody.create(mediaTypeJson, jsonString)

        val request = Request.Builder()
            .url(url)
            .headers(Headers.of(headers))
            .post(requestBody)
            .build()

        val ks = KeyStore.getInstance("BKS")
        ks.load(null)
        val factory = CertificateFactory.getInstance("X509")
        val x509 = factory.generateCertificate(context.getResources().getAssets().open(certFileName)) as X509Certificate
        val alias = x509.subjectDN.name
        ks.setCertificateEntry(alias, x509)

        val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManager.init(ks)

        val tlsCon = SSLContext.getInstance("TLS")
        tlsCon.init(null, trustManager.trustManagers, SecureRandom())

        val client = OkHttpClient.Builder()
            .hostnameVerifier { hostname, session -> hostname == session.peerHost }
            .sslSocketFactory(tlsCon.socketFactory, object : X509TrustManager{
                override fun checkServerTrusted( chain: Array<out X509Certificate>?, authType: String? ) {
                }

                override fun checkClientTrusted( chain: Array<out X509Certificate>?, authType: String? ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf(x509)
                }
            })
            .build()
        val response = client.newCall(request).execute()
        return response.body()!!.string()
    }
}

// 参考文献:
//  https://qiita.com/riversun/items/4e0a1b6bea42ae1405c4
//  https://mussyu1204.myhome.cx/wordpress/it/?p=66
//  http://www.jssec.org/dl/android_securecoding_20180201/5_how_to_use_security_functions.html