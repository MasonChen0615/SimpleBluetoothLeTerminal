package de.kai_morich.simple_bluetooth_le_terminal.payload;

public class SunionLockLog {
    public static final int MAX_LOG_COUNT = 250;

    public static final byte AUTO_LOCK = (byte) 0x00; //Auto 關門
    public static final byte APP_LOCK = (byte) 0x01; //App 關門
    public static final byte PRESSKEY_LOCK = (byte) 0x02; //按鍵關門
    public static final byte MANUAL_LOCK = (byte) 0x03; //手動關門
    public static final byte APP_UNLOCK = (byte) 0x04; //App 開門
    public static final byte PRESSKEY_UNLOCK = (byte) 0x05; //按鍵開門
    public static final byte MANUAL_UNLOCK = (byte) 0x06; //手動開門
    public static final byte ERROR_PASSWORD = (byte) 0x50; //錯誤密碼
    public static final byte ERROR_CONNECTION = (byte) 0x51; //錯誤連線
}
