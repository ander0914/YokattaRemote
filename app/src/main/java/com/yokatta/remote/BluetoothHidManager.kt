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
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onServiceReady(registered: Boolean)
        fun onConnectionStateChanged(device: BluetoothDevice?, state: Int)
        fun onError(message: String)
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var isAppRegistered = false

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
                mainHandler.post { listener.onServiceReady(false) }
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isAppRegistered = registered
            mainHandler.post {
                listener.onServiceReady(registered)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            connectedHost = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            mainHandler.post {
                listener.onConnectionStateChanged(device, state)
            }
        }
    }

    fun start() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            listener.onError("請先開啟手機藍牙功能")
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
            listener.onError("藍牙 HID 服務尚未就緒，請稍候再試")
            return
        }
        hid.connect(device)
    }

    fun disconnect() {
        val host = connectedHost ?: return
        hidDevice?.disconnect(host)
    }

    fun isConnected(): Boolean = connectedHost != null

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

    fun tapKeyboardKey(keyCode: Byte, modifier: Byte = HidDescriptor.MOD_NONE) {
        if (!sendKeyboardDown(keyCode, modifier)) return
        mainHandler.postDelayed({ sendKeyboardUp() }, 80)
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

    fun tapConsumerKey(usageId: Int) {
        if (!sendConsumerDown(usageId)) return
        mainHandler.postDelayed({ sendConsumerUp() }, 80)
    }
}
