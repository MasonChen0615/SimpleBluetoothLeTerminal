package de.kai_morich.simple_bluetooth_le_terminal;

class Constants {

    // values have to be globally unique
    static final String INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect";
    static final String NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel";
    static final String INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity";

    // values have to be unique within each app
    static final int NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001;
    static final String SELF_TEST = "TEST";
    public static final Boolean AUTO_RETRY_CONNECTION = false;
    public static final String DEBUG_TAG = "AAA";
    static final String EXCHANGE_LOCKTOKEN_PREFIX = "@LOCKTOKEN@";
    static final String EXCHANGE_MESSAGE_PREFIX = "@MESSAGE@";
    static final String EXCHANGE_DATA_PREFIX = "@BASE64DATA@";
    static final String EXCHANGE_DATA_0xE0_PREFIX = "@" + Constants.CMD_NAME_0xE0 + "@";
    static final String EXCHANGE_DATA_0xE4_PREFIX = "@" + Constants.CMD_NAME_0xE4 + "@";
    static final String EXCHANGE_DATA_0xEA_PREFIX = "@" + Constants.CMD_NAME_0xEA + "@";

    static final String EXCHANGE_TAG_0xE0_PREFIX = EXCHANGE_DATA_0xE0_PREFIX;
    static final String EXCHANGE_TAG_0xE4_PREFIX = EXCHANGE_DATA_0xE4_PREFIX;
    static final String EXCHANGE_TAG_0xEA_PREFIX = EXCHANGE_DATA_0xEA_PREFIX;

    static final int CONNECT_RETRY_DELAY = 3 ; //	連線亂數
    static final int CONNECT_RETRY = 3 ; //	連線亂數

    // command list
    static final int CMD_0xC0 = 0 ; //	連線亂數
    static final int CMD_0xC1 = CMD_0xC0 + 1 ; //	連線 Token
    static final int CMD_0xC7 = CMD_0xC1 + 1 ; //	新增管理者PinCode
    static final int CMD_0xC8 = CMD_0xC7 + 1 ; //	修改管理者PinCode
    static final int CMD_0xCC = CMD_0xC8 + 1 ; //	左右判定
    static final int CMD_0xCE = CMD_0xCC + 1 ; //	回復原廠設定
    static final int CMD_0xD0 = CMD_0xCE + 1 ; //	查詢鎖體名稱
    static final int CMD_0xD1 = CMD_0xD0 + 1 ; //	設定鎖體名稱
    static final int CMD_0xD2 = CMD_0xD1 + 1 ; //	查詢鎖體時間
    static final int CMD_0xD3 = CMD_0xD2 + 1 ; //	設定鎖體時間
    static final int CMD_0xD4 = CMD_0xD3 + 1 ; //	查詢鎖體設定檔
    static final int CMD_0xD5 = CMD_0xD4 + 1 ; //	設定鎖體設定檔
    static final int CMD_0xD6 = CMD_0xD5 + 1 ; //	查詢鎖體狀態
    static final int CMD_0xD7 = CMD_0xD6 + 1 ; //	設定鎖體狀態
    static final int CMD_0xD8 = CMD_0xD7 + 1 ; //	查詢鎖體時區
    static final int CMD_0xD9 = CMD_0xD8 + 1 ; //	設定鎖體時區
    static final int CMD_0xE0 = CMD_0xD9 + 1 ; //	查詢 Log 數量
    static final int CMD_0xE1 = CMD_0xE0 + 1 ; //	查詢 Log
    static final int CMD_0xE2 = CMD_0xE1 + 1 ; //	刪除 Log
    static final int CMD_0xE3 = CMD_0xE2 + 1 ; //	保留未使用
    static final int CMD_0xE4 = CMD_0xE3 + 1 ; //	查詢 Token Array
    static final int CMD_0xE5 = CMD_0xE4 + 1 ; //	查詢 Token
    static final int CMD_0xE6 = CMD_0xE5 + 1 ; //	新增一次性 Token
    static final int CMD_0xE7 = CMD_0xE6 + 1 ; //	修改 Token
    static final int CMD_0xE8 = CMD_0xE7 + 1 ; //	刪除 Token
    static final int CMD_0xE9 = CMD_0xE8 + 1 ; //	保留未使用
    static final int CMD_0xEA = CMD_0xE9 + 1 ; //	查詢 PinCode Array
    static final int CMD_0xEB = CMD_0xEA + 1 ; //	查詢 PinCode
    static final int CMD_0xEC = CMD_0xEB + 1 ; //	新增 PinCode
    static final int CMD_0xED = CMD_0xEC + 1 ; //	修改 PinCode
    static final int CMD_0xEE = CMD_0xED + 1 ; //	刪除 PinCode
    static final int CMD_0xEF = CMD_0xEE + 1 ; //	刪除 PinCode

    static final int CMD_Run_Read50000 = CMD_0xEF + 1 ; //	命令流水號上限測試
    static final int CMD_Run_Read_Duplicate_SN = CMD_Run_Read50000 + 1 ; //	命令流水號重複測試
//    0xC7	新增管理者PinCode
//    0xC8	修改管理者PinCode
//    static final int CMD_0xCC = 2 ; //	左右判定
//    static final int CMD_0xCE = 3 ; //	回復原廠設定
//    static final int CMD_0xD0 = 4 ; //	查詢鎖體名稱
//    static final int CMD_0xD1 = 5 ; //	設定鎖體名稱
//    static final int CMD_0xD2 = 6 ; //	查詢鎖體時間
//    static final int CMD_0xD3 = 7 ; //	設定鎖體時間
//    static final int CMD_0xD4 = 8 ; //	查詢鎖體設定檔
//    static final int CMD_0xD5 = 9 ; //	設定鎖體設定檔
//    static final int CMD_0xD6 = 10 ; //	查詢鎖體狀態
//    static final int CMD_0xD7 = 11 ; //	設定鎖體狀態
//    static final int CMD_0xE0 = 12 ; //	查詢 Log 數量
//    static final int CMD_0xE1 = 13 ; //	查詢 Log
//    static final int CMD_0xE2 = 14 ; //	刪除 Log
//    static final int CMD_0xE3 = 15 ; //	保留未使用
//    static final int CMD_0xE4 = 16 ; //	查詢 Token Array
//    static final int CMD_0xE5 = 17 ; //	查詢 Token
//    static final int CMD_0xE6 = 18 ; //	新增一次性 Token
//    static final int CMD_0xE7 = 19 ; //	修改 Token
//    static final int CMD_0xE8 = 20 ; //	刪除 Token
//    static final int CMD_0xE9 = 21 ; //	保留未使用
//    static final int CMD_0xEA = 22 ; //	查詢 PinCode Array
//    static final int CMD_0xEB = 23 ; //	查詢 PinCode
//    static final int CMD_0xEC = 24 ; //	新增 PinCode
//    static final int CMD_0xED = 25 ; //	修改 PinCode
//    static final int CMD_0xEE = 26 ; //	刪除 PinCode
//    static final int CMD_0xEF = 26 ; //	刪除 PinCode

    public static final String CMD_NAME_0xC0 = "BLE_Connect";
    public static final String CMD_NAME_0xC1 = "Connect";
    public static final String CMD_NAME_0xC7 = "NewAdminPinCode";
    public static final String CMD_NAME_0xC8 = "ModifyAdminPinCode";
    public static final String CMD_NAME_0xCC = "DirectionCheck";
    public static final String CMD_NAME_0xCE = "FactoryReset";
    public static final String CMD_NAME_0xD0 = "InquireLockName";
    public static final String CMD_NAME_0xD1 = "SetLockName";
    public static final String CMD_NAME_0xD2 = "InquireLockTime";
    public static final String CMD_NAME_0xD3 = "SetLockTime";
    public static final String CMD_NAME_0xD4 = "InquireLockConfig";
    public static final String CMD_NAME_0xD5 = "SetLockConfig";
    public static final String CMD_NAME_0xD6 = "InquireLockState";
    public static final String CMD_NAME_0xD7 = "SetLockState";
    public static final String CMD_NAME_0xD8 = "InquireLockTimeZone";
    public static final String CMD_NAME_0xD9 = "SetLockTimeZone";
    public static final String CMD_NAME_0xE0 = "InquireLogCount";
    public static final String CMD_NAME_0xE1 = "InquireLog";
    public static final String CMD_NAME_0xE2 = "DeleteLog";
    public static final String CMD_NAME_0xE3 = "";
    public static final String CMD_NAME_0xE4 = "InquireTokenArray";
    public static final String CMD_NAME_0xE5 = "InquireToken";
    public static final String CMD_NAME_0xE6 = "NewOnceToken";
    public static final String CMD_NAME_0xE7 = "ModifyToken";
    public static final String CMD_NAME_0xE8 = "DeleteToken";
    public static final String CMD_NAME_0xE9 = "";
    public static final String CMD_NAME_0xEA = "InquirePinCodeArray";
    public static final String CMD_NAME_0xEB = "InquirePinCode";
    public static final String CMD_NAME_0xEC = "NewPinCode";
    public static final String CMD_NAME_0xED = "ModifyPinCode";
    public static final String CMD_NAME_0xEE = "DeletePinCode";
    public static final String CMD_NAME_0xEF = "HaveMangerPinCode";
}
