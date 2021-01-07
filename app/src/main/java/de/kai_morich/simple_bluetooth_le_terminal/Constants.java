package de.kai_morich.simple_bluetooth_le_terminal;

class Constants {

    // values have to be globally unique
    static final String INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect";
    static final String NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel";
    static final String INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity";

    // values have to be unique within each app
    static final int NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001;
    static final String SELF_TEST = "TEST";
    static final String DEBUG_TAG = "AAA";
    static final String EXCHANGE_LOCKTOKEN_PREFIX = "@LOCKTOKEN@";
    static final String EXCHANGE_MESSAGE_PREFIX = "@MESSAGE@";
    static final String EXCHANGE_DATA_PREFIX = "@BASE64DATA@";
    static final String EXCHANGE_DATA_0xE0_PREFIX = "@" + Constants.CMD_NAME_0xE0 + "@";
    static final String EXCHANGE_DATA_0xE4_PREFIX = "@" + Constants.CMD_NAME_0xE4 + "@";
    static final String EXCHANGE_DATA_0xEA_PREFIX = "@" + Constants.CMD_NAME_0xEA + "@";

    static final String EXCHANGE_TAG_0xE0_PREFIX = EXCHANGE_DATA_0xE0_PREFIX;
    static final String EXCHANGE_TAG_0xE4_PREFIX = EXCHANGE_DATA_0xE4_PREFIX;
    static final String EXCHANGE_TAG_0xEA_PREFIX = EXCHANGE_DATA_0xEA_PREFIX;

    // command list
    static final int CMD_0xC0 = 0 ; //	連線亂數
    static final int CMD_0xC1 = 1 ; //	連線 Token
    static final int CMD_0xCC = 2 ; //	左右判定
    static final int CMD_0xCE = 3 ; //	回復原廠設定
    static final int CMD_0xD0 = 4 ; //	查詢鎖體名稱
    static final int CMD_0xD1 = 5 ; //	設定鎖體名稱
    static final int CMD_0xD2 = 6 ; //	查詢鎖體時間
    static final int CMD_0xD3 = 7 ; //	設定鎖體時間
    static final int CMD_0xD4 = 8 ; //	查詢鎖體設定檔
    static final int CMD_0xD5 = 9 ; //	設定鎖體設定檔
    static final int CMD_0xD6 = 10 ; //	查詢鎖體狀態
    static final int CMD_0xD7 = 11 ; //	設定鎖體狀態
    static final int CMD_0xE0 = 12 ; //	查詢 Log 數量
    static final int CMD_0xE1 = 13 ; //	查詢 Log
    static final int CMD_0xE2 = 14 ; //	刪除 Log
    static final int CMD_0xE3 = 15 ; //	保留未使用
    static final int CMD_0xE4 = 16 ; //	查詢 Token Array
    static final int CMD_0xE5 = 17 ; //	查詢 Token
    static final int CMD_0xE6 = 18 ; //	新增一次性 Token
    static final int CMD_0xE7 = 19 ; //	修改 Token
    static final int CMD_0xE8 = 20 ; //	刪除 Token
    static final int CMD_0xE9 = 21 ; //	保留未使用
    static final int CMD_0xEA = 22 ; //	查詢 PinCode Array
    static final int CMD_0xEB = 23 ; //	查詢 PinCode
    static final int CMD_0xEC = 24 ; //	新增 PinCode
    static final int CMD_0xED = 25 ; //	修改 PinCode
    static final int CMD_0xEE = 26 ; //	刪除 PinCode

    static final int CMD_0xD8 = 0 ; // ~ 0xDF	保留未使用

    static final String CMD_NAME_0xC0 = "BLE_Connect";
    static final String CMD_NAME_0xC1 = "Connect";
    static final String CMD_NAME_0xCC = "DirectionCheck";
    static final String CMD_NAME_0xCE = "FactoryReset";
    static final String CMD_NAME_0xD0 = "InquireLockName";
    static final String CMD_NAME_0xD1 = "SetLockName";
    static final String CMD_NAME_0xD2 = "InquireLockTime";
    static final String CMD_NAME_0xD3 = "SetLockTime";
    static final String CMD_NAME_0xD4 = "InquireLockConfig";
    static final String CMD_NAME_0xD5 = "SetLockConfig";
    static final String CMD_NAME_0xD6 = "InquireLockState";
    static final String CMD_NAME_0xD7 = "SetLockState";
    static final String CMD_NAME_0xE0 = "InquireLogCount";
    static final String CMD_NAME_0xE1 = "InquireLog";
    static final String CMD_NAME_0xE2 = "DeleteLog";
    static final String CMD_NAME_0xE3 = "";
    static final String CMD_NAME_0xE4 = "InquireTokenArray";
    static final String CMD_NAME_0xE5 = "InquireToken";
    static final String CMD_NAME_0xE6 = "NewOnceToken";
    static final String CMD_NAME_0xE7 = "ModifyToken";
    static final String CMD_NAME_0xE8 = "DeleteToken";
    static final String CMD_NAME_0xE9 = "";
    static final String CMD_NAME_0xEA = "InquirePinCodeArray";
    static final String CMD_NAME_0xEB = "InquirePinCode";
    static final String CMD_NAME_0xEC = "NewPinCode";
    static final String CMD_NAME_0xED = "ModifyPinCode";
    static final String CMD_NAME_0xEE = "DeletePinCode";

    private Constants() {}
}
