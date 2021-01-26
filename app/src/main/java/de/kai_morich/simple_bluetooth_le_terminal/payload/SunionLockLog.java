package de.kai_morich.simple_bluetooth_le_terminal.payload;

import de.kai_morich.simple_bluetooth_le_terminal.CodeUtils;

public class SunionLockLog {
    public static final int MAX_LOG_COUNT = 250;

    //LOCK EVENT
    public static final byte AUTO_LOCK = (byte) 0x00; //Auto 關門成功
    public static final byte AUTO_LOCK_FAIL = (byte) 0x01; //Auto 關門失敗
    public static final byte APP_LOCK = (byte) 0x02; //App 關門成功
    public static final byte APP_LOCK_FAIL = (byte) 0x03; //App 關門失敗
    public static final byte PRESSKEY_LOCK = (byte) 0x04; //按鍵關門成功
    public static final byte PRESSKEY_LOCK_FAIL = (byte) 0x05; //按鍵關門失敗
    public static final byte MANUAL_LOCK = (byte) 0x06; //手動關門成功
    public static final byte MANUAL_LOCK_FAIL = (byte) 0x07; //手動關門失敗
    //UNLOCK EVENT
    public static final byte APP_UNLOCK = (byte) 0x08; //App 開門成功
    public static final byte APP_UNLOCK_FAIL = (byte) 0x09; //App 開門失敗
    public static final byte PRESSKEY_UNLOCK = (byte) 0x0a; //按鍵開門成功
    public static final byte PRESSKEY_UNLOCK_FAIL = (byte) 0x0b; //按鍵開門失敗
    public static final byte MANUAL_UNLOCK = (byte) 0x0c; //手動開門成功
    public static final byte MANUAL_UNLOCK_FAIL = (byte) 0x0d; //手動開門失敗
    //OTHER EVENT
    public static final byte NEW_TOKEN = (byte) 0x40; //64	新增 Token
    public static final byte MODIFY_TOKEN = (byte) 0x41; //65	修改 Token
    public static final byte DELETE_TOKEN = (byte) 0x42; //66	刪除 Token
    public static final byte NEW_PINCODE = (byte) 0x50; //80	新增 PinCode
    public static final byte MODIFY_PINCODE = (byte) 0x51; //81	修改 PinCode
    public static final byte DELETE_PINCODE = (byte) 0x52; //82	刪除 PinCode
    //CRITICAL ERROR
    public static final byte ERROR_PASSWORD = (byte) 0x80; //錯誤密碼
    public static final byte ERROR_CONNECTION = (byte) 0x81; //錯誤連線

    //LOCK EVENT STRING
    public static final String STR_AUTO_LOCK = "AUTO_LOCK";
    public static final String STR_AUTO_LOCK_FAIL = "AUTO_LOCK_FAIL";
    public static final String STR_APP_LOCK = "APP_LOCK";
    public static final String STR_APP_LOCK_FAIL = "APP_LOCK_FAIL";
    public static final String STR_PRESSKEY_LOCK = "PRESSKEY_LOCK";
    public static final String STR_PRESSKEY_LOCK_FAIL = "PRESSKEY_LOCK_FAIL";
    public static final String STR_MANUAL_LOCK = "MANUAL_LOCK";
    public static final String STR_MANUAL_LOCK_FAIL = "MANUAL_LOCK_FAIL";
    //UNLOCK EVEN"NG
    public static final String STR_APP_UNLOCK = "APP_UNLOCK";
    public static final String STR_APP_UNLOCK_FAIL = "APP_UNLOCK_FAIL";
    public static final String STR_PRESSKEY_UNLOCK = "PRESSKEY_UNLOCK";
    public static final String STR_PRESSKEY_UNLOCK_FAIL = "PRESSKEY_UNLOCK_FAIL";
    public static final String STR_MANUAL_UNLOCK = "MANUAL_UNLOCK";
    public static final String STR_MANUAL_UNLOCK_FAIL = "MANUAL_UNLOCK_FAIL";
    //OTHER EVEN"NG
    public static final String STR_NEW_TOKEN = "NEW_TOKEN";
    public static final String STR_MODIFY_TOKEN = "MODIFY_TOKEN";
    public static final String STR_DELETE_TOKEN = "DELETE_TOKEN";
    public static final String STR_NEW_PINCODE = "NEW_PINCODE";
    public static final String STR_MODIFY_PINCODE = "MODIFY_PINCODE";
    public static final String STR_DELETE_PINCODE = "DELETE_PINCODE";
    //CRITICAL ERRO"NG
    public static final String STR_ERROR_PASSWORD = "ERROR_PASSWORD";
    public static final String STR_ERROR_CONNECTION = "ERROR_CONNECTION";

    public static final String STR_UNKNOWN = "UNKNOWN";

    public static String getLogTypeString(byte type){
        int i2 = type & 0xFF;
        String message = " " + i2 + " ";
        switch(type){
            case AUTO_LOCK:
                message += STR_AUTO_LOCK;
                break;
            case AUTO_LOCK_FAIL:
                message += STR_AUTO_LOCK_FAIL;
                break;
            case APP_LOCK:
                message += STR_APP_LOCK;
                break;
            case APP_LOCK_FAIL:
                message += STR_APP_LOCK_FAIL;
                break;
            case PRESSKEY_LOCK:
                message += STR_PRESSKEY_LOCK;
                break;
            case PRESSKEY_LOCK_FAIL:
                message += STR_PRESSKEY_LOCK_FAIL;
                break;
            case MANUAL_LOCK:
                message += STR_MANUAL_LOCK;
                break;
            case MANUAL_LOCK_FAIL:
                message += STR_MANUAL_LOCK_FAIL;
                break;
            case APP_UNLOCK:
                message += STR_APP_UNLOCK;
                break;
            case APP_UNLOCK_FAIL:
                message += STR_APP_UNLOCK_FAIL;
                break;
            case PRESSKEY_UNLOCK:
                message += STR_PRESSKEY_UNLOCK;
                break;
            case PRESSKEY_UNLOCK_FAIL:
                message += STR_PRESSKEY_UNLOCK_FAIL;
                break;
            case MANUAL_UNLOCK:
                message += STR_MANUAL_UNLOCK;
                break;
            case MANUAL_UNLOCK_FAIL:
                message += MANUAL_UNLOCK_FAIL;
                break;
            case NEW_TOKEN:
                message += STR_NEW_TOKEN;
                break;
            case MODIFY_TOKEN:
                message += STR_MODIFY_TOKEN;
                break;
            case DELETE_TOKEN:
                message += STR_DELETE_TOKEN;
                break;
            case NEW_PINCODE:
                message += STR_NEW_PINCODE;
                break;
            case MODIFY_PINCODE:
                message += STR_MODIFY_PINCODE;
                break;
            case DELETE_PINCODE:
                message += STR_DELETE_PINCODE;
                break;
            case ERROR_PASSWORD:
                message += STR_ERROR_PASSWORD;
                break;
            case ERROR_CONNECTION:
                message += STR_ERROR_CONNECTION;
                break;
            default:
                message += STR_UNKNOWN;
                break;
        }
        return message;
    }
}
