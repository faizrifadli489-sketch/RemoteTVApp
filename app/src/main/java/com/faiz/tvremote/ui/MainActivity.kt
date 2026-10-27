package com.faiz.tvremote.ui

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faiz.tvremote.R
import com.faiz.tvremote.pairing.CertManager
import com.faiz.tvremote.remote.APP_LINKS
import com.faiz.tvremote.remote.KEY_MAP
import com.faiz.tvremote.remote.KeyCodes
import com.faiz.tvremote.remote.KeyDirection
import com.faiz.tvremote.remote.TvConnection
import com.faiz.tvremote.remote.charToKeyName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Layar remote utama. Mirror dari panel Remote + Tools di public/index.html:
 * D-pad, volume, media, channel, Live TV, apps, YouTube search, ketik teks,
 * buka URL, sleep timer, WoL, batas volume.
 */
class MainActivity : AppCompatActivity() {

    private var conn: TvConnection? = null
    private lateinit var tvStatus: TextView
    private var maxVolume: Int? = null
    private var sleepTimer: CountDownTimer? = null

    private val pairLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == RESULT_OK) connect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        tvStatus = TextView(this).apply {
            text = "Belum terhubung"
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val btnConnect = Button(this).apply { text = "Hubungkan / Pairing" }
        btnConnect.setOnClickListener {
            pairLauncher.launch(Intent(this, PairActivity::class.java))
        }

        body.addView(btnConnect)
        body.addView(section("Navigasi"))
        body.addView(dpadRow("up"))
        body.addView(dpadRow3("left", "select", "right"))
        body.addView(dpadRow("down"))
        body.addView(row(
            keyBtn("⏮ Prev", "previous"), keyBtn("⏪", "rewind"),
            keyBtn("▶/⏸", "play_pause"), keyBtn("⏩", "fast_forward"),
            keyBtn("Next ⏭", "next"),
        ))
        body.addView(row(keyBtn("⬅ Back", "back"), keyBtn("🏠 Home", "home"), keyBtn("☰ Menu", "menu"), keyBtn("⏻ Power", "power")))
        body.addView(section("Volume"))
        body.addView(row(keyBtn("Vol +", "volume_up"), keyBtn("Mute", "mute"), keyBtn("Vol −", "volume_down")))
        body.addView(section("Channel"))
        body.addView(row(keyBtn("CH −", "channel_down"), keyBtn("CH +", "channel_up"), keyBtn("📺 Live TV", "live_tv")))
        body.addView(row(keyBtn("⚙ Settings", "settings"), keyBtn("🔍 Search", "search")))
        body.addView(section("Apps"))
        val appsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((name, url) in APP_LINKS) {
            appsRow.addView(Button(this).apply {
                text = name
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setOnClickListener { conn?.sendAppLink(url) }
            })
        }
        body.addView(appsRow)
        body.addView(section("Cari YouTube"))
        body.addView(inputRow("Kata kunci...") { q ->
            conn?.sendAppLink("https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(q, "UTF-8")}")
        })
        body.addView(section("Ketik ke TV"))
        body.addView(inputRow("Teks...") { t -> typeText(t) })
        body.addView(section("Buka URL"))
        body.addView(inputRow("https://...") { u -> conn?.sendAppLink(u) })
        body.addView(section("Sleep Timer (menit)"))
        body.addView(inputRow("Menit (0 = batal)") { m ->
            val min = m.toIntOrNull() ?: 0
            setSleep(if (min > 0) min * 60_000L else 0L)
        })
        body.addView(section("Batas Volume"))
        body.addView(inputRow("1-100 (0 = off)") { v ->
            maxVolume = v.toIntOrNull()?.takeIf { it in 1..100 }
            Toast.makeText(this, "Batas: ${maxVolume ?: "off"}", Toast.LENGTH_SHORT).show()
        })

        scroll.addView(body)
        root.addView(tvStatus)
        root.addView(scroll)
        setContentView(root)

        // Pantau status koneksi
        lifecycleScope.launch {
            var last: TvConnection? = null
            while (true) {
                val c = conn
                if (c != last) {
                    last = c
                    if (c != null) launch { c.state.collect { s -> renderState(s) } }
                }
                delay(500)
            }
        }

        if (CertManager.hasCert()) connect()
    }

    private fun section(t: String) = TextView(this).apply {
        text = t
        setPadding(0, 24, 0, 8)
    }

    private fun row(vararg views: android.view.View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in views) {
            v.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(v)
        }
    }

    private fun dpadRow(key: String) = row(keyBtn("▲ $key", key))
    private fun dpadRow3(a: String, b: String, c: String) = row(keyBtn("◀", a), keyBtn("OK", b), keyBtn("▶", c))

    private fun keyBtn(label: String, key: String) = Button(this).apply {
        text = label
        setOnClickListener { pressKey(key) }
    }

    private fun inputRow(hint: String, onGo: (String) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val et = EditText(this@MainActivity).apply {
            this.hint = hint
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val go = Button(this@MainActivity).apply {
            text = "GO"
            setOnClickListener { onGo(et.text.toString()) }
        }
        addView(et)
        addView(go)
    }

    private fun pressKey(key: String) {
        val c = conn
        if (c == null || !c.isReady) {
            Toast.makeText(this, "TV belum terhubung", Toast.LENGTH_SHORT).show()
            return
        }
        // Penjagaan batas volume (mirror server.js)
        if (key == "volume_up" && maxVolume != null) {
            // level dicek dari state terakhir; kirim volume_down bila sudah di batas
            val st = lastState
            if (st?.volumeLevel != null && st.volumeLevel!! >= maxVolume!!) {
                c.sendKeyByName("volume_down")
                return
            }
        }
        c.sendKeyByName(key)
    }

    private var lastState: TvConnection.TvState? = null

    private fun renderState(s: TvConnection.TvState) {
        lastState = s
        // Penjagaan realtime: TV naik melewati batas -> turunkan otomatis (mirror server.js)
        if (maxVolume != null && s.volumeLevel != null && !s.volumeMuted &&
            s.volumeLevel!! > maxVolume!!
        ) {
            conn?.sendKey(KeyCodes.VOLUME_DOWN)
        }
        val dot = if (s.connected) "🟢" else "🔴"
        tvStatus.text = "$dot ${if (s.connected) "Terhubung" else "Terputus"}" +
            (s.volumeLevel?.let { " · Vol $it" } ?: "") +
            (s.currentApp?.let { " · $it" } ?: "")
    }

    private var typeJob: Job? = null
    private fun typeText(text: String) {
        val c = conn ?: return
        typeJob?.cancel()
        typeJob = lifecycleScope.launch(Dispatchers.IO) {
            for (ch in text) {
                val keyName = charToKeyName(ch) ?: continue
                val code = KEY_MAP[keyName] ?: continue
                c.sendKey(code, KeyDirection.SHORT)
                delay(90)
            }
        }
    }

    private fun setSleep(ms: Long) {
        sleepTimer?.cancel()
        sleepTimer = null
        if (ms <= 0) {
            Toast.makeText(this, "Sleep timer batal", Toast.LENGTH_SHORT).show()
            return
        }
        sleepTimer = object : CountDownTimer(ms, 1000) {
            override fun onTick(m: Long) {}
            override fun onFinish() {
                conn?.sendPower()
                Toast.makeText(this@MainActivity, "TV dimatikan (sleep timer)", Toast.LENGTH_LONG).show()
            }
        }.also { it.start() }
        Toast.makeText(this, "TV mati dalam ${ms / 60000} menit", Toast.LENGTH_SHORT).show()
    }

    private fun connect() {
        val prefs = getSharedPreferences("tvremote", MODE_PRIVATE)
        val host = prefs.getString("host", null)
        if (host.isNullOrEmpty()) {
            pairLauncher.launch(Intent(this, PairActivity::class.java))
            return
        }
        conn?.stop()
        conn = TvConnection(host).also { it.start() }
        tvStatus.text = "Menghubungi $host ..."
    }

    override fun onDestroy() {
        conn?.stop()
        super.onDestroy()
    }
}
