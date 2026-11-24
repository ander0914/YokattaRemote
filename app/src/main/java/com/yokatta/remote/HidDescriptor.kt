package com.yokatta.remote

object HidDescriptor {
    const val REPORT_ID_KEYBOARD: Int = 1
    const val REPORT_ID_CONSUMER: Int = 2
    const val REPORT_ID_MOUSE: Int = 3

    // Standard USB HID Descriptor combining Keyboard (ID 1) + Consumer Control (ID 2) + Mouse (ID 3)
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

        // === Report ID 2: Consumer Control ===
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
        0xC0.toByte(),                      // End Collection

        // === Report ID 3: Mouse (Left Click = 畫面點擊一下) ===
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop)
        0x09.toByte(), 0x02.toByte(),       // Usage (Mouse)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), 0x03.toByte(),       //   Report ID (3)
        0x09.toByte(), 0x01.toByte(),       //   Usage (Pointer)
        0xA1.toByte(), 0x00.toByte(),       //   Collection (Physical)
        0x05.toByte(), 0x09.toByte(),       //     Usage Page (Buttons)
        0x19.toByte(), 0x01.toByte(),       //     Usage Minimum (Button 1 - Left)
        0x29.toByte(), 0x03.toByte(),       //     Usage Maximum (Button 3 - Middle)
        0x15.toByte(), 0x00.toByte(),       //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //     Logical Maximum (1)
        0x95.toByte(), 0x03.toByte(),       //     Report Count (3)
        0x75.toByte(), 0x01.toByte(),       //     Report Size (1)
        0x81.toByte(), 0x02.toByte(),       //     Input (Data, Variable, Absolute)
        0x95.toByte(), 0x01.toByte(),       //     Report Count (1)
        0x75.toByte(), 0x05.toByte(),       //     Report Size (5)
        0x81.toByte(), 0x01.toByte(),       //     Input (Constant) - Padding
        0x05.toByte(), 0x01.toByte(),       //     Usage Page (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),       //     Usage (X)
        0x09.toByte(), 0x31.toByte(),       //     Usage (Y)
        0x15.toByte(), 0x81.toByte(),       //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),       //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),       //     Report Size (8)
        0x95.toByte(), 0x02.toByte(),       //     Report Count (2)
        0x81.toByte(), 0x06.toByte(),       //     Input (Data, Variable, Relative)
        0xC0.toByte(),                      //   End Collection
        0xC0.toByte()                       // End Collection
    )

    // Keyboard Modifiers
    const val MOD_NONE: Byte = 0x00
    const val MOD_LEFT_SHIFT: Byte = 0x02
    const val MOD_LEFT_GUI: Byte = 0x08

    // Keyboard Usage IDs (Page 0x07)
    const val KEY_ENTER: Byte = 0x28
    const val KEY_TAB: Byte = 0x2B
    const val KEY_F12: Byte = 0x45          // 專屬快捷觸發鍵（搭配好咖機 Key Mapper 開啟 YouTube）
    const val KEY_RIGHT: Byte = 0x4F
    const val KEY_LEFT: Byte = 0x50
    const val KEY_DOWN: Byte = 0x51
    const val KEY_UP: Byte = 0x52

    // Consumer Control Usage IDs (Page 0x0C)
    const val CONSUMER_POWER: Int = 0x0030
    const val CONSUMER_DPAD_CENTER: Int = 0x0041
    const val CONSUMER_BRIGHTNESS_UP: Int = 0x006F
    const val CONSUMER_BRIGHTNESS_DOWN: Int = 0x0070
    const val CONSUMER_FAST_FORWARD: Int = 0x00B3
    const val CONSUMER_REWIND: Int = 0x00B4
    const val CONSUMER_NEXT_TRACK: Int = 0x00B5
    const val CONSUMER_PREV_TRACK: Int = 0x00B6
    const val CONSUMER_PLAY_PAUSE: Int = 0x00CD
    const val CONSUMER_VOICE_ASSIST: Int = 0x00CF
    const val CONSUMER_MUTE: Int = 0x00E2
    const val CONSUMER_VOLUME_UP: Int = 0x00E9
    const val CONSUMER_VOLUME_DOWN: Int = 0x00EA
    const val CONSUMER_HOME: Int = 0x0223
    const val CONSUMER_BACK: Int = 0x0224
}
