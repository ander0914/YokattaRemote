package com.yokatta.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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

    companion object {
        private const val PREFS_NAME = "yokatta_remote_prefs"
        private const val KEY_OK_MODE = "ok_mode"
        private const val OK_MODE_ENTER = 0
        private const val OK_MODE_DPAD_CENTER = 1
        private const val OK_MODE_SPACE = 2
        private const val OK_MODE_MOUSE_CLICK = 3
    }

    private var hidService: HidForegroundService? = null
    private var isBound = false
    private var okMode: Int = OK_MODE_ENTER

    private lateinit var tvStatus: TextView
    private lateinit var statusDot: View
    private lateinit var btnConnect: MaterialButton
    private lateinit var tvOkMode: TextView

    private val okModeLabels = arrayOf(
        "Enter 確認鍵（方向鍵選取後按）",
        "遙控器 DPAD_CENTER 確認鍵",
        "空白鍵 Space（播放器確認）",
        "滑鼠左鍵點擊（需重新配對藍牙）"
    )

    private val okModeShortLabels = arrayOf(
        "Enter 確認",
        "DPAD_CENTER",
        "空白鍵 Space",
        "滑鼠左鍵點擊"
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HidForegroundService.LocalBinder
            hidService = binder.getService()
            isBound = true
            hidService?.uiListener = this@MainActivity
            syncUiWithService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            hidService?.uiListener = null
            hidService = null
            isBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            results[Manifest.permission.BLUETOOTH_CONNECT] == true ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

        if (btGranted) {
            startAndBindHidService()
        } else {
            Toast.makeText(this, "需要藍牙權限才能連線好咖機", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(R.id.rootScroll)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tvStatus)
        statusDot = findViewById(R.id.statusDot)
        btnConnect = findViewById(R.id.btnConnect)
        tvOkMode = findViewById(R.id.tvOkMode)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        okMode = prefs.getInt(KEY_OK_MODE, OK_MODE_ENTER).coerceIn(0, 3)
        updateOkModeLabel()

        tvOkMode.setOnClickListener {
            vibrate(it)
            showOkModeDialog()
        }

        setupRemoteButtons()
        btnConnect.setOnClickListener {
            vibrate(it)
            val manager = hidService?.hidManager
            if (manager == null) {
                Toast.makeText(this, "藍牙服務啟動中，請稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (manager.isConnected()) {
                showConnectedMenu()
            } else {
                showDevicePickerDialog()
            }
        }

        checkPermissionsAndStart()
    }

    override fun onStart() {
        super.onStart()
        if (!isBound && hasBluetoothPermission()) {
            bindService(
                Intent(this, HidForegroundService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            hidService?.uiListener = null
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun updateOkModeLabel() {
        tvOkMode.text = "OK 模式：${okModeShortLabels[okMode]} ▾"
    }

    private fun showOkModeDialog() {
        AlertDialog.Builder(this)
            .setTitle("選擇 OK 鍵發送模式")
            .setSingleChoiceItems(okModeLabels, okMode) { dialog, which ->
                okMode = which
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_OK_MODE, okMode)
                    .apply()
                updateOkModeLabel()
                Toast.makeText(this, "OK 鍵已切換為：${okModeShortLabels[okMode]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkPermissionsAndStart() {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.add(Manifest.permission.BLUETOOTH_CONNECT)
            required.add(Manifest.permission.BLUETOOTH_SCAN)
            required.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startAndBindHidService()
        }
    }

    private fun startAndBindHidService() {
        val intent = Intent(this, HidForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        if (!isBound) {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun syncUiWithService() {
        val manager = hidService?.hidManager ?: return
        if (manager.isConnected()) {
            onConnectionStateChanged(manager.getConnectedDevice(), BluetoothProfile.STATE_CONNECTED)
        } else if (manager.isAppRegistered) {
            onServiceReady(true)
        } else {
            tvStatus.text = "初始化藍牙服務..."
            statusDot.setBackgroundResource(R.drawable.bg_dot_idle)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRemoteButtons() {
        // 1. 電源/休眠、喚醒、語音
        bindConsumerTap(R.id.btnPower, HidDescriptor.CONSUMER_POWER)
        findViewById<View>(R.id.btnWake).setOnClickListener {
            vibrate(it)
            if (!ensureConnected()) return@setOnClickListener
            hidService?.hidManager?.tapKeyboardKey(0x00, HidDescriptor.MOD_LEFT_SHIFT)
        }
        bindConsumerTap(R.id.btnVoice, HidDescriptor.CONSUMER_VOICE_ASSIST)

        // 2. 亮度、靜音、音量（支援長按連發）
        bindConsumerHold(R.id.btnBrightUp, HidDescriptor.CONSUMER_BRIGHTNESS_UP)
        bindConsumerHold(R.id.btnBrightDown, HidDescriptor.CONSUMER_BRIGHTNESS_DOWN)
        bindConsumerTap(R.id.btnMute, HidDescriptor.CONSUMER_MUTE)
        bindConsumerHold(R.id.btnVolUp, HidDescriptor.CONSUMER_VOLUME_UP)
        bindConsumerHold(R.id.btnVolDown, HidDescriptor.CONSUMER_VOLUME_DOWN)

        // 3. 方向鍵（按壓同步送出）
        bindKeyboardHold(R.id.btnUp, HidDescriptor.KEY_UP)
        bindKeyboardHold(R.id.btnDown, HidDescriptor.KEY_DOWN)
        bindKeyboardHold(R.id.btnLeft, HidDescriptor.KEY_LEFT)
        bindKeyboardHold(R.id.btnRight, HidDescriptor.KEY_RIGHT)

        // OK 鍵：與方向鍵採用完全相同的按壓同步機制（ACTION_DOWN 送出按下、ACTION_UP 送出放開）
        findViewById<View>(R.id.btnOk).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    vibrate(v)
                    if (ensureConnected()) {
                        val manager = hidService?.hidManager
                        val sent = when (okMode) {
                            OK_MODE_ENTER -> manager?.sendKeyboardDown(HidDescriptor.KEY_ENTER) == true
                            OK_MODE_DPAD_CENTER -> manager?.sendConsumerDown(HidDescriptor.CONSUMER_DPAD_CENTER) == true
                            OK_MODE_SPACE -> manager?.sendKeyboardDown(HidDescriptor.KEY_SPACE) == true
                            OK_MODE_MOUSE_CLICK -> manager?.sendMouseLeftDown() == true
                            else -> false
                        }
                        if (!sent) {
                            Toast.makeText(this, "OK 指令傳送失敗，請在藍牙設定取消配對後重新配對", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    val manager = hidService?.hidManager
                    when (okMode) {
                        OK_MODE_ENTER, OK_MODE_SPACE -> manager?.sendKeyboardUp()
                        OK_MODE_DPAD_CENTER -> manager?.sendConsumerUp()
                        OK_MODE_MOUSE_CLICK -> manager?.sendMouseLeftUp()
                    }
                    true
                }
                else -> false
            }
        }

        // 4. Android 系統導覽列（返回、首頁、最近使用程式）
        bindConsumerTap(R.id.btnBack, HidDescriptor.CONSUMER_BACK)
        bindConsumerTap(R.id.btnHome, HidDescriptor.CONSUMER_HOME)
        bindKeyboardTap(R.id.btnRecents, HidDescriptor.KEY_TAB, HidDescriptor.MOD_LEFT_GUI)

        // 5. 媒體播放控制
        bindConsumerTap(R.id.btnPrev, HidDescriptor.CONSUMER_PREV_TRACK)
        bindConsumerTap(R.id.btnPlayPause, HidDescriptor.CONSUMER_PLAY_PAUSE)
        bindConsumerTap(R.id.btnNext, HidDescriptor.CONSUMER_NEXT_TRACK)

        // 6. 倒退 10 秒 / 快進 10 秒
        bindConsumerTap(R.id.btnYtRewind, HidDescriptor.CONSUMER_REWIND)
        bindConsumerTap(R.id.btnYtForward, HidDescriptor.CONSUMER_FAST_FORWARD)
    }

    private fun bindConsumerTap(viewId: Int, usageId: Int) {
        findViewById<View>(viewId).setOnClickListener {
            vibrate(it)
            if (!ensureConnected()) return@setOnClickListener
            hidService?.hidManager?.tapConsumerKey(usageId)
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
                        hidService?.hidManager?.sendConsumerDown(usageId)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    hidService?.hidManager?.sendConsumerUp()
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
            hidService?.hidManager?.tapKeyboardKey(keyCode, modifier)
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
                        hidService?.hidManager?.sendKeyboardDown(keyCode, modifier)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    hidService?.hidManager?.sendKeyboardUp()
                    true
                }
                else -> false
            }
        }
    }

    private fun ensureConnected(): Boolean {
        if (hidService?.hidManager?.isConnected() != true) {
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
        val manager = hidService?.hidManager ?: return
        val devices = manager.getBondedDevices()
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
                manager.connect(target)
            }
            .setNeutralButton("開放手機被搜尋") { _, _ ->
                requestDiscoverable()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showConnectedMenu() {
        val manager = hidService?.hidManager ?: return
        AlertDialog.Builder(this)
            .setTitle("已連線：${manager.getConnectedDeviceName()}")
            .setMessage("藍牙已在背景持續維持中，要中斷連線或切換其他裝置嗎？")
            .setPositiveButton("中斷連線") { _, _ ->
                manager.disconnect()
            }
            .setNeutralButton("切換裝置") { _, _ ->
                manager.disconnect()
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

    override fun onServiceReady(registered: Boolean) {
        if (registered && hidService?.hidManager?.isConnected() != true) {
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
