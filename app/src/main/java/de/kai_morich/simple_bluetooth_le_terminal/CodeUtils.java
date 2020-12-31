package de.kai_morich.simple_bluetooth_le_terminal;

import android.util.Log;

/**
 * Created by Maya on 2020/12/16.
 */
public class CodeUtils {
        /**
         * connection token
         */
        public static final String  ConnectionTokenFileName = "connection_token.key";
        /**
         * Command step 0 , init step.
         */
        public static final String Command_Initialization = "Command_Initialization";
        /**
        * 連線亂數
        */
        public static final byte BLE_Connect = (byte) 0xC0;
        public static final String Command_BLE_Connect_C0 = "BLE_Connect_AES_C0";
        public static final String Command_BLE_Connect_C1 = "BLE_Connect_AES_C1";
        public static final String Command_BLE_Connect_C2 = "BLE_Connect_AES_XOR";  // C0 XOR C1
        /**
        * 連線永久性Token
        */
        public static final byte Connect = (byte) 0xC1;
        /**
        * 連線一次性Token
        */
        public static final byte UsingOnceTokenConnect = (byte) 0xC1;
        /**
        * 左右判定
        */
        public static final byte DirectionCheck = (byte) 0xCC;
        /**
        * 回復原廠設定
        */
        public static final byte FactoryReset = (byte) 0xCE;
        /**
        * 查詢鎖體名稱
        */
        public static final byte InquireLockName = (byte) 0xD0;
        /**
        * 設定鎖體名稱
        */
        public static final byte SetLockName = (byte) 0xD1;
        /**
        * 查詢鎖體時間
        */
        public static final byte InquireLockTime = (byte) 0xD2;
        /**
        * 設定鎖體時間
        */
        public static final byte SetLockTime = (byte) 0xD3;
        /**
        * 查詢鎖體設定檔
        */
        public static final byte InquireLockConfig = (byte) 0xD4;
        /**
        * 設定鎖體設定檔
        */
        public static final byte SetLockConfig = (byte) 0xD5;
        /**
        * 查詢鎖體狀態
        */
        public static final byte InquireLockState = (byte) 0xD6;
        /**
        * 設定鎖體狀態
        */
        public static final byte SetLockState = (byte) 0xD7;

        /**
        * 查詢 Log 數量
        */
        public static final byte InquireLogCount = (byte) 0xE0;
        /**
        * 查詢 Log
        */
        public static final byte InquireLog = (byte) 0xE1;
        /**
        * 刪除 Log
        */
        public static final byte DeleteLog = (byte) 0xE2;
        /**
        * 查詢 Token Array
        */
        public static final byte InquireTokenArray = (byte) 0xE4;
        /**
        * 查詢 Token
        */
        public static final byte InquireToken = (byte) 0xE5;
        /**
        * 新增一次性 Token
        */
        public static final byte NewOnceToken = (byte) 0xE6;
        /**
        * 修改 Token
        */
        public static final byte ModifyToken = (byte) 0xE7;
        /**
        * 刪除 Token
        */
        public static final byte DeleteToken = (byte) 0xE8;
        /**
        * 查詢 PinCode Array
        */
        public static final byte InquirePinCodeArray = (byte) 0xEA;
        /**
        * 查詢 PinCode
        */
        public static final byte InquirePinCode = (byte) 0xEB;
        /**
        * 新增 PinCode
        */
        public static final byte NewPinCode = (byte) 0xEC;
        /**
        * 修改 PinCode
        */
        public static final byte ModifyPinCode = (byte) 0xED;
        /**
        * 刪除 PinCode
        */
        public static final byte DeletePinCode = (byte) 0xEE;

        public static byte[] getCommandPackage(byte action , byte len , byte[] data){
                byte[] print;
                byte[] command = new byte[data.length + 2];
                command[0] = action; // command of 連線亂數
                command[1] = len; // command data size
                for(int i = 0 ; i < data.length ; i++) {
                        command[i+2] = data[i];
                }
                Log.i("AAA","command data size is  : " + command.length);
                int currten_len = command.length;
                if (currten_len <= 16) {
                        print = new byte[16];
                        int i;
                        for( i = 0 ; i < currten_len ; i++){
                                print[i] = command[i];
                        }
                        for( int j = 0 ; i < print.length ; j++){
                                print[j] = (byte)0x3D; // = padding text
                        }
                        Log.i("AAA","command package size " + print.length);
                        Log.i("AAA","command in hex : " + CodeUtils.bytesToHex(print));
                        return print;
                }else if (currten_len > 16 && currten_len <= 32) {
                        print = new byte[32];
                        int i;
                        for( i = 0 ; i < currten_len ; i++){
                                print[i] = command[i];
                        }
                        for( int j = i ; j < print.length ; j++){
                                print[j] = (byte)0x3D; // = padding text
                        }
                        Log.i("AAA","command package size " + print.length);
                        Log.i("AAA","command in hex : " + CodeUtils.bytesToHex(print));
                        return print;
                }else if (currten_len > 32 && currten_len <= 64){
                        print = new byte[64];
                        int i;
                        for( i = 0 ; i < currten_len ; i++){
                                print[i] = command[i];
                        }
                        for( int j = i ; j < print.length ; j++){
                                print[j] = (byte)0x3D; // = padding text
                        }
                        Log.i("AAA","command package size " + print.length);
                        Log.i("AAA","command in hex : " + CodeUtils.bytesToHex(print));
                        return print;
                }else if (currten_len > 64 && currten_len <= 128){
                        print = new byte[128];
                        int i;
                        for( i = 0 ; i < currten_len ; i++){
                                print[i] = command[i];
                        }
                        for( int j = i ; j < print.length ; j++){
                                print[j] = (byte)0x3D; // = padding text
                        }
                        Log.i("AAA","command package size " + print.length);
                        Log.i("AAA","command in hex : " + CodeUtils.bytesToHex(print));
                        return print;
                }else{
                        Log.e("AAA","command data size is  : " + command.length + " too big");
                        return new byte[0];
                }
        }

        public static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        public static String bytesToHex(byte[] bytes) {
                char[] hexChars = new char[bytes.length * 2];
                for (int j = 0; j < bytes.length; j++) {
                        int v = bytes[j] & 0xFF;
                        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
                }
                return new String(hexChars);
        }
}
