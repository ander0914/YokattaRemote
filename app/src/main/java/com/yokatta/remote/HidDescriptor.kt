package com.yokatta.remote

object HidDescriptor {
    const val REPORT_ID_KEYBOARD: Int = 1
    const val REPORT_ID_CONSUMER: Int = 2

    // Standard USB HID Descriptor combining Keyboard (ID 1) + 16-bit Consumer Control (ID 2)
    val DESCRIPTOR: ByteArray = byteArrayOf(
        // === Report ID 1: Keyboard ===
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),       // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), 0x01.toByte(),       //   Report ID (1)
        0x05.toByte(), 0x07.toByte(),       //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(),       //   Usage Minimum (224)
        0x29.toByte(), 0xE7.toByte(),       //   Usage Maximum (231)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1)
        0x95.toByte(), 0x08.toByte(),       //   Report Count (8)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data, Variable, Absolute) - Modifier
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x81.toByte(), 0x01.toByte(),       //   Input (Constant) - Reserved
        0x95.toByte(), 0x06.toByte(),       //   Report Count (6)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(),       //   Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(),       //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(),       //   Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(),       //   Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(),       //   Input (Data, Array) - Keycodes
        0xC0.toByte(),                      // End Collection

        // === Report ID 2: Consumer Control (Media, System, Brightness, Voice, Power) ===
        0x05.toByte(), 0x0C.toByte(),       // Usage Page (Consumer)
        0x09.toByte(), 0x01.toByte(),       // Usage (Consumer Control)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), 0x02.toByte(),       //   Report ID (2)
        0x19.toByte(), 0x00.toByte(),       //   Usage Minimum (0x0000)
        0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(), // Usage Maximum (0x03FF)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0x0000)
        0x26.toByte(), 0xFF.toByte(), 0x03.toByte(), // Logical Maximum (0x03FF)
        0x75.toByte(), 0x10.toByte(),       //   Report Size (16)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x81.toByte(), 0x00.toByte(),       //   Input (Data, Array)
        0xC0.toByte()                       // End Collection
    )

    // Keyboard Modifiers
    const val MOD_NONE: Byte = 0x00
    const val MOD_LEFT_SHIFT: Byte = 0x02
    const val MOD_LEFT_GUI: Byte = 0x08     // Meta / Android key

    // Keyboard Usage IDs (Page 0x07)
    const val KEY_J: Byte = 0x0D            // YouTube Rewind 10s
    const val KEY_L: Byte = 0x0F            // YouTube Fast Forward 10s
    const val KEY_ENTER: Byte = 0x28        // OK / Confirm
    const val KEY_TAB: Byte = 0x2B          // Used with MOD_LEFT_GUI for Recents
    const val KEY_RIGHT: Byte = 0x4F
    const val KEY_LEFT: Byte = 0x50
    const val KEY_DOWN: Byte = 0x51
    const val KEY_UP: Byte = 0x52

    // Consumer Control Usage IDs (Page 0x0C, mapped in Android Generic.kl)
    const val CONSUMER_POWER: Int = 0x0030           // Sleep / Power Toggle
    const val CONSUMER_BRIGHTNESS_UP: Int = 0x006F   // Brightness +
    const val CONSUMER_BRIGHTNESS_DOWN: Int = 0x0070 // Brightness -
    const val CONSUMER_NEXT_TRACK: Int = 0x00B5      // Next Track
    const val CONSUMER_PREV_TRACK: Int = 0x00B6      // Previous Track
    const val CONSUMER_PLAY_PAUSE: Int = 0x00CD      // Play / Pause
    const val CONSUMER_VOICE_ASSIST: Int = 0x00CF    // Voice Command / Google Assistant
    const val CONSUMER_MUTE: Int = 0x00E2            // Mute
    const val CONSUMER_VOLUME_UP: Int = 0x00E9       // Volume +
    const val CONSUMER_VOLUME_DOWN: Int = 0x00EA     // Volume -
    const val CONSUMER_HOME: Int = 0x0223            // AC Home
    const val CONSUMER_BACK: Int = 0x0224            // AC Back
}
