package de.kai_morich.simple_bluetooth_le_terminal;

import android.util.Base64;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;


/**
 * Created by Maya on 2020/12/16.
 */
public class CodeUtils {
        public static final String AES_Cipher_DL02_H2MB_KPD_Small = "AES/ECB/NoPadding";
        /**
         * connection token
         */
        public static final String ConnectionTokenFileName = "connection_token.key";
        /**
         * Command step 0 , init step.
         */
        public static final int Retry = 5;
        public static final int Retry_Wait = 30;
        public static final int Command_Max_Size = 128;
        public static final byte Command_Initialization_Code = (byte) 0x00;
        public static final String Command_Initialization = "Command_Initialization";
        /**
        * 連線亂數
        */
        public static final byte BLE_Connect = (byte) 0xC0;
        /**
        * 連線永久性Token
        */
        public static final byte Connect = (byte) 0xC1;
        public static final String Connect_UsingTokenConnect = "Use Token";
        public static final String Connect_UsingOnceTokenConnect = "Use Once Token";
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

        public static String encodeBase64(String data){
                return Base64.encodeToString(data.getBytes(), Base64.DEFAULT);
        }

        public static String decodeBase64(String data){
                // Receiving side
                Base64.decode(data.getBytes(), Base64.DEFAULT);
                try {
                        String text = new String(Base64.decode(data.getBytes(), Base64.DEFAULT), "UTF-8");
                        return text;
                } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        return "";
                }
        }

        public static byte[] intToLittleEndian(long numero) {
                ByteBuffer bb = ByteBuffer.allocate(4);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                bb.putInt((int) numero);
                return bb.array();
        }

        public static int littleEndianToInt(byte[] data) {
                return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }

        public static SunionCommandPayload decodeCommandPackage(byte[] data){
//                index	說明
//                01	流水號 low byte   0
//                02	流水號 high byte  1
//                03	Fuction           2
//                04	Data Len          3
//                05	Data Start        4
//                .......
//                DataLen + 4	Data End
//                .......	亂數
//                16x	亂數
                int command_iv = 0;
                byte command = 0x00;
                int command_len = 0;
                byte[] command_data = new byte[CodeUtils.Command_Max_Size];

                int count = 0;
                for(byte b : data){
                        if (count == 0) {  // 0 ~ 1
                                command_iv = command_iv | b;  //low byte
                        } else if (count == 1) {
                                command_iv = command_iv | b << 8; //high byte
                        } else if (count == 2) {
                                command = b;
                        } else if (count == 3) {
                                command_len = b;
                                if (data.length < command_len) {
                                        //some receive error in here.
                                        return new SunionCommandPayload((byte)0x00,0,new byte[]{},0);
                                } else {
                                        command_data = new byte[command_len]; //resize
                                }
                        } else {
                                int padding = count - 4;
                                if (padding < command_len) {
                                        command_data[padding] = b;
                                }
                        }
                        count++;
                }
                return new SunionCommandPayload(command,command_len,command_data,command_iv);
        }

        public static byte[] getCommandPackage(byte action , byte len , byte[] data , int command_iv){
                byte[] print;
                byte[] command = new byte[data.length + 4];

                command[0] = (byte) (command_iv % 256) ; // low byte
                command[1] = (byte) (command_iv / 256); // high byte
                command[2] = action; // command of 連線亂數
                command[3] = len; // command data size
                for(int i = 0 ; i < data.length ; i++) {
                        command[i+4] = data[i];
                }
                Log.i(Constants.DEBUG_TAG,"command data size is  : " + command.length);
                int currten_len = command.length;
                int tank_size = ( (currten_len / 16) + ( ( (currten_len % 16) > 0 ) ? 1 : 0 ) ) * 16;

                if (tank_size >= 128 ){
                        Log.e(Constants.DEBUG_TAG,"command data size is  : " + command.length + " too big");
                        return new byte[0];
                } else {
                        print = new byte[tank_size];
                        int i;
                        for( i = 0 ; i < currten_len ; i++){
                                print[i] = command[i];
                        }
                        for( int j = i ; j < print.length ; j++){
                                print[j] = (byte)0x3D; // = padding text
                        }
                        Log.i(Constants.DEBUG_TAG,"command package size " + print.length);
                        Log.i(Constants.DEBUG_TAG,"command in hex : " + CodeUtils.bytesToHex(print));
                        return print;
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
        public static byte[] hexStringToBytes(String message) {
                int len = message.length() / 2;
                char[] chars = message.toCharArray();

                String[] hexStr = new String[len];

                byte[] bytes = new byte[len];

                for (int i = 0, j = 0; j < len; i += 2, j++) {
                        hexStr[j] = "" + chars[i] + chars[i + 1];
                        bytes[j] = (byte) Integer.parseInt(hexStr[j], 16);
                }
                return bytes;
        }
        public static Boolean isHexString(String message){
                if (message.length() > 0) {
                        if (message.length() % 2 != 0) {
                                return false;
                        }
                        char[] message_char = message.toCharArray();
                        for (char c : message_char) {
                                Boolean found = false;
                                for (char e : HEX_ARRAY) {
                                        if ( c == e ) {
                                                //check pass
                                                found = true;
                                        }
                                }
                                if (!found) {
                                        return false;
                                }
                        }
                        return true;
                } else {
                        return false;
                }
        }

        /**
         *  encodeAES need package message in 16 / 32 / 64 / 128 len size
         * @param key
         * @param cipher_code
         * @param data
         * @return
         */
        public static byte[] encodeAES(SecretKey key , String cipher_code , byte[] data) {
                try {
                        Cipher cipher = Cipher.getInstance(cipher_code);
                        cipher.init(Cipher.ENCRYPT_MODE, key);
                        byte[] ciphertext = cipher.doFinal(data);
                        return ciphertext;
                } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                } catch (NoSuchPaddingException e) {
                        e.printStackTrace();
                } catch (BadPaddingException e) {
                        e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                        e.printStackTrace();
                } catch (InvalidKeyException e) {
                        e.printStackTrace();
                }
                return new byte[]{};
        }

        /**
         * decodeAES need package message in 16 / 32 / 64 / 128 len size
         * @param key
         * @param cipher_code
         * @param data
         * @return
         */
        public static byte[] decodeAES(SecretKey key , String cipher_code , byte[] data){
                //      default use AES_Cipher_DL02_H2MB_KPD_Small
                try {
                        Cipher cipher = Cipher.getInstance(cipher_code);
                        cipher.init(Cipher.DECRYPT_MODE, key);
                        byte[] ciphertext = cipher.doFinal(data);
                        return ciphertext;
                } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                } catch (NoSuchPaddingException e) {
                        e.printStackTrace();
                } catch (BadPaddingException e) {
                        e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                        e.printStackTrace();
                } catch (InvalidKeyException e) {
                        e.printStackTrace();
                }
                return new byte[]{};
        }

        public static Boolean isBytesCanRead(byte[] data){
                for(byte b : data){
                        if ( b < (byte)0x20 || b > (byte)0x7E){
                                return false;
                        }
                }
                return true;
        }

        public static void selfTest(){
                try {
                        KeyGenerator keygen = KeyGenerator.getInstance("AES");
                        keygen.init(128);
                        SecretKey key = keygen.generateKey();
                        final int min = 0;
                        final int max = 65535;
                        String[] testarr = {
                                "", // use 16 tank
                                "thisis 8", // use 16 tank
                                "this is 10",  // use 16 tank
                                "this is 16 bytes",  // use 32 tank
                                "this is 32 bytes Hello world!~~~", // use 48 tank
                                "this is 43 bytes Hello world! ~~~ All that ",  // use 48 tank
                                "this is 48 bytes Hello world! ~~~ All that glist",  // use 64 tank
                                "this is 64 bytes Hello world! ~~~ All that glisters is not gold.", // use 80 tank
                                "this is 70 bytes Hello world! There are more things in heaven and eart", // use 80 tank
                                "this is 90 bytes Hello world! There are more things in heaven and earth, Horatio, than are", // use 96 tank
                                "this is 108 bytes Hello world!There are more things in heaven and earth, Horatio, than are dreamt of in your", //use 112 tank
                                "this is 128 bytes Hello world! ~~~~~~ There are more things in heaven and earth, Horatio, than are dreamt of in your philosophy.", // use 128 tank , overflow
                        };
                        int[] ans_size = {
                                16,16,16,32,48,48,64,80,80,96,112,0
                        };
                        int index = 0;
                        for(String test : testarr){
                                int random = new Random().nextInt((max - min) + 1) + min;
                                byte[] tmp = CodeUtils.encodeAES(
                                        key,
                                        CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small,
                                        CodeUtils.getCommandPackage(
                                                CodeUtils.BLE_Connect,
                                                (byte) test.length(),
                                                test.getBytes(),
                                                random
                                        )
                                );
                                Log.i(Constants.SELF_TEST,"test message in byte:" + CodeUtils.bytesToHex(tmp));
                                if(tmp.length != ans_size[index]){
                                        Log.e(Constants.SELF_TEST,"test message size fail , exp " + ans_size[index] + " but got " + tmp.length);
                                }
                                index++;
                        }
                        Log.i(Constants.SELF_TEST,"test message size pass");
                        String[] testarr2 = {
                                "010203", //true
                                " ", //false
                                "010203-", //false
                                "ABC", //false
                        };
                        Boolean[] ans_op = {
                                true,false,false,false
                        };
                        index = 0;
                        for(String test : testarr2){
                                Boolean tmp  = isHexString(test);
                                if(tmp != ans_op[index]){
                                        Log.e(Constants.SELF_TEST,"test isHexString op fail , exp " + ans_size[index] + " but got " + tmp);
                                }
                                index++;
                        }
                        Log.i(Constants.SELF_TEST,"test isHexString pass");
                } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                }
        }
}
