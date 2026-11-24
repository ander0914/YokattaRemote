package com.yokatta.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class BluetoothHidManager(
    private val context: Context
) {
    interface Listener {
        fun onServiceReady(registered: Boolean)
        fun onConnectionStateChanged(device: BluetoothDevice?, state: Int)
        fun onError(message: String)
    }

    var listener: Listener? = null

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    var isAppRegistered: Boolean = false
        private set
    var currentState: Int = BluetoothProfile.STATE_DISCONNECTED
        private set

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                connectedHost = null
                isAppRegistered = false
                currentState = BluetoothProfile.STATE_DISCONNECTED
                mainHandler.post { listener?.onServiceReady(false) }
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isAppRegistered = registered
            mainHandler.post {
                listener?.onServiceReady(registered)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            currentState = state
            connectedHost = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            mainHandler.post {
                listener?.onConnectionStateChanged(device, state)
            }
        }
    }

    fun start() {
        if (hidDevice != null && isAppRegistered) {
            mainHandler.post { listener?.onServiceReady(true) }
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            mainHandler.post { listener?.onError("請先開啟手機藍牙功能") }
            return
        }
        adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        try {
            if (isAppRegistered) {
                hidDevice?.unregisterApp()
            }
            hidDevice?.let {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
            }
        } catch (_: Exception) {
        } finally {
            hidDevice = null
            connectedHost = null
            isAppRegistered = false
            currentState = BluetoothProfile.STATE_DISCONNECTED
        }
    }

    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Yokatta 好咖機遙控器",
            "Bluetooth HID Remote Control",
            "YokattaRemote",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.DESCRIPTOR
        )
        hidDevice?.registerApp(sdp, null, null, executor, hidCallback)
    }

    fun getBondedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun connect(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null || !isAppRegistered) {
            mainHandler.post { listener?.onError("藍牙 HID 服務尚未就緒，請稍候再試") }
            return
        }
        hid.connect(device)
    }

    fun disconnect() {
        val host = connectedHost ?: return
        hidDevice?.disconnect(host)
    }

    fun isConnected(): Boolean = connectedHost != null

    fun getConnectedDevice(): BluetoothDevice? = connectedHost

    fun getConnectedDeviceName(): String? = connectedHost?.name ?: connectedHost?.address

    // === Keyboard Reports ===
    fun sendKeyboardDown(keyCode: Byte, modifier: Byte = HidDescriptor.MOD_NONE): Boolean {
        val host = connectedHost ?: return false
        val report = byteArrayOf(modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00)
        return hidDevice?.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, report) == true
    }

    fun sendKeyboardUp(): Boolean {
        val host = connectedHost ?: return false
        val report = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        return hidDevice?.sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, report) == true
    }

    fun tapKeyboardKey(keyCode: Byte, modifier: Byte = HidDescriptor.MOD_NONE): Boolean {
        if (!sendKeyboardDown(keyCode, modifier)) return false
        mainHandler.postDelayed({ sendKeyboardUp() }, 80)
        return true
    }

    // === Consumer Control Reports ===
    fun sendConsumerDown(usageId: Int): Boolean {
        val host = connectedHost ?: return false
        val low = (usageId and 0xFF).toByte()
        val high = ((usageId shr 8) and 0xFF).toByte()
        val report = byteArrayOf(low, high)
        return hidDevice?.sendReport(host, HidDescriptor.REPORT_ID_CONSUMER, report) == true
    }

    fun sendConsumerUp(): Boolean {
        val host = connectedHost ?: return false
        val report = byteArrayOf(0x00, 0x00)
        return hidDevice?.sendReport(host, HidDescriptor.REPORT_ID_CONSUMER, report) == true
    }

    fun tapConsumerKey(usageId: Int): Boolean {
        if (!sendConsumerDown(usageId)) return false
        mainHandler.postDelayed({ sendConsumerUp() }, 80)
        return true
    }

    // === Mouse Reports (先微移 1px 喚醒隱藏游標再點擊左鍵) ===
    fun sendMouseLeftDown(): Boolean {
        val host = connectedHost ?: return false
        val hid = hidDevice ?: return false
        hid.sendReport(host, HidDescriptor.REPORT_ID_MOUSE, byteArrayOf(0x00, 0x01, 0x00))
        hid.sendReport(host, HidDescriptor.REPORT_ID_MOUSE, byteArrayOf(0x00, 0xFF.toByte(), 0x00))
        return hid.sendReport(host, HidDescriptor.REPORT_ID_MOUSE, byteArrayOf(0x01, 0x00, 0x00))
    }

    fun sendMouseLeftUp(): Boolean {
        val host = connectedHost ?: return false
        return hidDevice?.sendReport(host, HidDescriptor.REPORT_ID_MOUSE, byteArrayOf(0x00, 0x00, 0x00)) == true
    }
}
