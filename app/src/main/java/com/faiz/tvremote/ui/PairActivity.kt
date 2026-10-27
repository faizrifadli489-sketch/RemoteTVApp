package com.faiz.tvremote.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faiz.tvremote.R
import com.faiz.tvremote.pairing.CertManager
import com.faiz.tvremote.pairing.PairingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Layar pairing: masukkan IP TV -> konek -> masukkan PIN dari layar TV. */
class PairActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etIp: EditText
    private lateinit var etPin: EditText
    private lateinit var btnPair: Button
    private lateinit var btnSendPin: Button
    private lateinit var pinRow: LinearLayout
    private lateinit var progress: ProgressBar

    private var session: PairingSession? = null
    private var pinResult: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        tvStatus = TextView(this).apply {
            text = "Pairing dengan TV.\nPastikan HP & TV satu WiFi."
            setPadding(0, 0, 0, 32)
        }
        etIp = EditText(this).apply { hint = "IP TV (mis. 192.168.1.4)" }
        btnPair = Button(this).apply { text = "Pair" }
        pinRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        etPin = EditText(this).apply {
            hint = "PIN dari layar TV"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        btnSendPin = Button(this).apply { text = "OK" }
        pinRow.addView(etPin)
        pinRow.addView(btnSendPin)
        progress = ProgressBar(this).apply { visibility = View.GONE }

        root.addView(tvStatus)
        root.addView(etIp)
        root.addView(btnPair)
        root.addView(pinRow)
        root.addView(progress)
        setContentView(root)

        btnPair.setOnClickListener { startPair() }
        btnSendPin.setOnClickListener {
            pinResult = etPin.text.toString().trim()
        }
    }

    private fun startPair() {
        val ip = etIp.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Isi IP TV dulu", Toast.LENGTH_SHORT).show()
            return
        }
        btnPair.isEnabled = false
        progress.visibility = View.VISIBLE
        getSharedPreferences("tvremote", MODE_PRIVATE).edit().putString("host", ip).apply()

        lifecycleScope.launch(Dispatchers.IO) {
            val s = PairingSession(ip)
            session = s
            withContext(Dispatchers.Main) {
                tvStatus.text = "Menghubungi $ip ..."
            }
            s.onPinRequest = {
                lifecycleScope.launch(Dispatchers.Main) {
                    tvStatus.text = "PIN muncul di layar TV.\nMasukkan PIN lalu OK."
                    pinRow.visibility = View.VISIBLE
                    progress.visibility = View.GONE
                }
            }
            val ok = s.run(onCodeNeeded = {
                // Tunggu user menekan OK
                while (pinResult == null) Thread.sleep(300)
                pinResult!!
            })
            withContext(Dispatchers.Main) {
                progress.visibility = View.GONE
                if (ok) {
                    Toast.makeText(this@PairActivity, "Pairing berhasil!", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    tvStatus.text = "Pairing gagal. Coba lagi."
                    btnPair.isEnabled = true
                    pinRow.visibility = View.GONE
                    pinResult = null
                }
            }
        }
    }

    override fun onDestroy() {
        session?.cancel()
        super.onDestroy()
    }
}
