package com.yokatta.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity(), BluetoothHidManager.Listener {

    private lateinit var hidManager: BluetoothHidManager
    private lateinit var tvStatus: TextView
    private lateinit var statusDot: View
    private lateinit var btnConnect: MaterialButton

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            hidManager.start()
        } else {
            Toast.makeText(this, "需要藍牙權限才能連線好咖機", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 避開 Android 15 頂部狀態列與底部導覽列重疊
        val rootView = findViewById<View>(R.id.rootScroll)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tvStatus)
        statusDot = findViewById(R.id.statusDot)
        btnConnect = findViewById(R.id.btnConnect)

        hidManager = BluetoothHidManager(this, this)

        setupRemoteButtons()
        btnConnect.setOnClickListener {
            vibrate(it)
            if (hidManager.isConnected()) {
                showConnectedMenu()
            } else {
                showDevicePickerDialog()
            }
        }

        checkPermissionsAndStart()
    }

    override fun onDestroy() {
        super.onDestroy()
        hidManager.stop()
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val required = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
            val missing = required.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing.toTypedArray())
                return
            }
        }
        hidManager.start()
    }

    private fun setupRemoteButtons() {
        // 1. 電源/休眠、喚醒、語音
        bindConsumerTap(R.id.btnPower, HidDescriptor.CONSUMER_POWER)
        findViewById<View>(R.id.btnWake).setOnClickListener {
            vibrate(it)
            if (!ensureConnected()) return@setOnClickListener
            hidManager.tapKeyboardKey(0x00, HidDescriptor.MOD_LEFT_SHIFT)
        }
        bindConsumerTap(R.id.btnVoice, HidDescriptor.CONSUMER_VOICE_ASSIST)

        // 2. 亮度、靜音、音量（支援長按連發）
        bindConsumerHold(R.id.btnBrightUp, HidDescriptor.CONSUMER_BRIGHTNESS_UP)
        bindConsumerHold(R.id.btnBrightDown, HidDescriptor.CONSUMER_BRIGHTNESS_DOWN)
        bindConsumerTap(R.id.btnMute, HidDescriptor.CONSUMER_MUTE)
        bindConsumerHold(R.id.btnVolUp, HidDescriptor.CONSUMER_VOLUME_UP)
        bindConsumerHold(R.id.btnVolDown, HidDescriptor.CONSUMER_VOLUME_DOWN)

        // 3. 方向鍵與 OK
        bindKeyboardHold(R.id.btnUp, HidDescriptor.KEY_UP)
        bindKeyboardHold(R.id.btnDown, HidDescriptor.KEY_DOWN)
        bindKeyboardHold(R.id.btnLeft, HidDescriptor.KEY_LEFT)
        bindKeyboardHold(R.id.btnRight, HidDescriptor.KEY_RIGHT)
        // OK 改用標準 HID Menu Pick (0x0041 -> KEYCODE_DPAD_CENTER)，等同點擊目前選取的項目
        bindConsumerTap(R.id.btnOk, HidDescriptor.CONSUMER_DPAD_CENTER)

        // 4. Android 系統導覽列（返回、首頁、最近使用程式）
        bindConsumerTap(R.id.btnBack, HidDescriptor.CONSUMER_BACK)
        bindConsumerTap(R.id.btnHome, HidDescriptor.CONSUMER_HOME)
        bindKeyboardTap(R.id.btnRecents, HidDescriptor.KEY_TAB, HidDescriptor.MOD_LEFT_GUI)

        // 5. 媒體播放控制
        bindConsumerTap(R.id.btnPrev, HidDescriptor.CONSUMER_PREV_TRACK)
        bindConsumerTap(R.id.btnPlayPause, HidDescriptor.CONSUMER_PLAY_PAUSE)
        bindConsumerTap(R.id.btnNext, HidDescriptor.CONSUMER_NEXT_TRACK)

        // 6. 倒退 10 秒 / 快進 10 秒（改用 Android 原生媒體快退 0x00B4 / 快進 0x00B3）
        bindConsumerTap(R.id.btnYtRewind, HidDescriptor.CONSUMER_REWIND)
        bindConsumerTap(R.id.btnYtForward, HidDescriptor.CONSUMER_FAST_FORWARD)
    }

    private fun bindConsumerTap(viewId: Int, usageId: Int) {
        findViewById<View>(viewId).setOnClickListener {
            vibrate(it)
            if (!ensureConnected()) return@setOnClickListener
            hidManager.tapConsumerKey(usageId)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindConsumerHold(viewId: Int, usageId: Int) {
        findViewById<View>(viewId).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    vibrate(v)
                    if (ensureConnected()) {
                        hidManager.sendConsumerDown(usageId)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    hidManager.sendConsumerUp()
                    true
                }
                else -> false
            }
        }
    }

    private fun bindKeyboardTap(viewId: Int, keyCode: Byte, modifier: Byte = HidDescriptor.MOD_NONE) {
        findViewById<View>(viewId).setOnClickListener {
            vibrate(it)
            if (!ensureConnected()) return@setOnClickListener
            hidManager.tapKeyboardKey(keyCode, modifier)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindKeyboardHold(viewId: Int, keyCode: Byte, modifier: Byte = HidDescriptor.MOD_NONE) {
        findViewById<View>(viewId).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    vibrate(v)
                    if (ensureConnected()) {
                        hidManager.sendKeyboardDown(keyCode, modifier)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    hidManager.sendKeyboardUp()
                    true
                }
                else -> false
            }
        }
    }

    private fun ensureConnected(): Boolean {
        if (!hidManager.isConnected()) {
            Toast.makeText(this, "尚未連線到好咖機，請先點擊右上角「連線」", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun vibrate(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    @SuppressLint("MissingPermission")
    private fun showDevicePickerDialog() {
        val devices = hidManager.getBondedDevices()
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("尚未配對任何藍牙裝置")
                .setMessage("請先讓好咖機搜尋並配對您的手機，或先在手機「系統藍牙設定」中與好咖機完成配對，再回來點擊連線。")
                .setPositiveButton("開放手機被搜尋 (120秒)") { _, _ ->
                    requestDiscoverable()
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        val names = devices.map { "${it.name ?: "未知裝置"}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("選擇要連線的好咖機")
            .setItems(names) { _, which ->
                val target = devices[which]
                tvStatus.text = "連線中：${target.name ?: target.address}..."
                hidManager.connect(target)
            }
            .setNeutralButton("開放手機被搜尋") { _, _ ->
                requestDiscoverable()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showConnectedMenu() {
        AlertDialog.Builder(this)
            .setTitle("已連線：${hidManager.getConnectedDeviceName()}")
            .setMessage("目前遙控器運作正常，要中斷連線或切換其他裝置嗎？")
            .setPositiveButton("中斷連線") { _, _ ->
                hidManager.disconnect()
            }
            .setNeutralButton("切換裝置") { _, _ ->
                hidManager.disconnect()
                showDevicePickerDialog()
            }
            .setNegativeButton("繼續使用", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun requestDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        }
        startActivity(intent)
    }

    // === BluetoothHidManager.Listener ===
    override fun onServiceReady(registered: Boolean) {
        if (registered && !hidManager.isConnected()) {
            tvStatus.text = "待命中（請點右上角連線）"
            statusDot.setBackgroundResource(R.drawable.bg_dot_idle)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                val name = device?.name ?: device?.address ?: "好咖機"
                tvStatus.text = "已連線：$name"
                statusDot.setBackgroundResource(R.drawable.bg_dot_connected)
                btnConnect.text = "已連線"
                Toast.makeText(this, "已成功連線至 $name", Toast.LENGTH_SHORT).show()
            }
            BluetoothProfile.STATE_CONNECTING -> {
                tvStatus.text = "連線中..."
                statusDot.setBackgroundResource(R.drawable.bg_dot_idle)
            }
            else -> {
                tvStatus.text = "未連線"
                statusDot.setBackgroundResource(R.drawable.bg_dot_disconnected)
                btnConnect.text = "連線"
            }
        }
    }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
