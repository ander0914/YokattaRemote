# 好咖機萬能遙控器 (Yokatta Remote) v1.1.0

專為 **IDEAVISION Yokatta 好咖機** 打造的 Android 藍牙 HID 萬能遙控器（深色夜間模式）。

## v1.1.0 更新內容
- 移除「YouTube 快捷控制」標題文字，版面更精簡。
- 修正按鈕字體過大與系統字體縮放造成的換行截斷問題，並修正頂部狀態列／底部導覽列重疊。
- `OK` 鍵改送出 Android 標準遙控器確認鍵 `KEYCODE_DPAD_CENTER`（HID Consumer `0x0041`），等同直接點擊選取項目。
- `倒退 10 秒` 與 `快進 10 秒` 改送出 Android 原生媒體快退（`0x00B4`）與快進（`0x00B3`）指令。
