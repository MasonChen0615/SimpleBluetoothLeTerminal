package de.kai_morich.simple_bluetooth_le_terminal.payload;

import android.os.Build;

import androidx.annotation.RequiresApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

import de.kai_morich.simple_bluetooth_le_terminal.CodeUtils;

public class SunionTokenStatus {

    public int exchange_index= 0;
    private Boolean enable = false;
    private Boolean once_use = false;
    private Boolean owner_token = false;
    private byte token_permission = TOKEN_PERMISSION_UNKNOWN;
    private byte[] token;
    private byte[] name;

    public static final String ENABLE = "ENABLE";
    public static final String ONCE_USE = "ONCE_USE";
    public static final String TOKEN = "TOKEN";
    public static final String NAME = "NAME";
    public static final int NAME_MAX_SIZE = 20;

    public static final int EXCHANGE_VERSION = 1;
    public static final String EXCHANGE_TAG_VERSION = "V";
    public static final String EXCHANGE_TAG_ENABLE = "E";
    public static final String EXCHANGE_TAG_ONCE_USE = "O";
    public static final String EXCHANGE_TAG_OWNER_TOKEN = "OW";
    public static final String EXCHANGE_TAG_TOKEN = "T";
    public static final String EXCHANGE_TAG_NAME = "N";
    public static final String EXCHANGE_TAG_INDEX = "I";

    //    Token 權限 'A':全部, 'L':有限, 'N':無
    public static final byte TOKEN_PERMISSION_ALL = (byte)0x41;
    public static final byte TOKEN_PERMISSION_LIMIT = (byte)0x4C;
    public static final byte TOKEN_PERMISSION_NONE = (byte)0x4E;
    public static final byte TOKEN_PERMISSION_UNKNOWN = (byte)0x00;


    public SunionTokenStatus(){
        this.enable = false;
        this.once_use = false;
        this.token = new byte[]{};
        this.name = new byte[]{};
    }
    public SunionTokenStatus(Boolean enable){
        this.enable = enable;
        this.once_use = false;
        this.token = new byte[]{};
        this.name = new byte[]{};
    }
    public SunionTokenStatus(Boolean enable, Boolean once_use, byte[] token, byte[] name){
        this.enable = enable;
        this.once_use = once_use;
        this.token = token;
        this.name = name;
    }
    public SunionTokenStatus(Boolean enable, Boolean once_use, byte[] token, byte[] name , Boolean owner_token){
        this.enable = enable;
        this.once_use = once_use;
        this.token = token;
        this.name = name;
        this.owner_token = owner_token;
    }
    //0    1	1	是否成功 1:成功, 0:失敗
    //1    2	1	Index 0~9

    //2    3	1	是否是永久 Token 1:永久, 0:一次性
    //3    4	1	Token 權限 'A':全部, 'L':有限, 'N':無
    //4 ~ 11    5 ~ 12	8	Token
    //12    13 ~	(最多 20 Byte)	Name
    public static SunionTokenStatus decodeModifyTokenPayload(byte[] payload) throws Exception{
        SunionTokenStatus new_token = new SunionTokenStatus();
        final int token_head = 12;
        int name_offset = 4;
        if (payload.length >= token_head) {
            new_token.enable = true;
            new_token.once_use = (payload[2] == ((byte) 0x00)) ? true : false; //1:永久, 0:一次性
            new_token.owner_token = false;
            switch(payload[3]){
                case TOKEN_PERMISSION_ALL:
                case TOKEN_PERMISSION_LIMIT:
                case TOKEN_PERMISSION_NONE:
                    new_token.token_permission = payload[3];
                    break;
                default:
                    new_token.token_permission = TOKEN_PERMISSION_UNKNOWN;
                    break;
            }
            new_token.token = new byte[8];
            for (int i = 0 ; i < new_token.token.length ; i++){
                new_token.token[i] = payload[name_offset+i];
            }
            if ((payload.length - token_head) > 0){
                new_token.name = new byte[payload.length - token_head];
                for (int i = token_head; i < payload.length; i++) {
                    new_token.name[i - token_head] = payload[i];
                }
            }
        } else {
            throw new Exception(" unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
        }
        return new_token;
    }
    public static SunionTokenStatus decodeTokenPayload(byte[] payload) throws Exception{
        SunionTokenStatus new_token = new SunionTokenStatus();
        final int token_head = 12;
        int name_offset = 4;
        if (payload.length >= token_head) {
            new_token.enable = (payload[0] == ((byte) 0x01)) ? true : false;
            new_token.once_use = (payload[1] == ((byte) 0x00)) ? true : false; //1:永久, 0:一次性
            new_token.owner_token = (payload[2] == ((byte) 0x01)) ? true : false;
            switch(payload[3]){
                case TOKEN_PERMISSION_ALL:
                case TOKEN_PERMISSION_LIMIT:
                case TOKEN_PERMISSION_NONE:
                    new_token.token_permission = payload[3];
                    break;
                default:
                    new_token.token_permission = TOKEN_PERMISSION_UNKNOWN;
                    break;
            }
            new_token.token = new byte[8];
            for (int i = 0 ; i < new_token.token.length ; i++){
                new_token.token[i] = payload[name_offset+i];
            }
            if ((payload.length - token_head) > 0){
                new_token.name = new byte[payload.length - token_head];
                for (int i = token_head; i < payload.length; i++) {
                    new_token.name[i - token_head] = payload[i];
                }
            }
        } else {
            throw new Exception(" unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
        }
        return new_token;
    }
    public byte[] getToken(){
        return this.token;
    }
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void setTokenName(String name){
        if (name.length() > SunionTokenStatus.NAME_MAX_SIZE){
            String tmp  = name.substring(0,19);
            this.name = tmp.getBytes(StandardCharsets.US_ASCII);
        } else if (name.length() <= 0) {
            this.name = "".getBytes(StandardCharsets.US_ASCII);
        } else {
            this.name = name.getBytes(StandardCharsets.US_ASCII);
        }
    }
    public byte[] getTokenName(){
        return this.name;
    }
    public Boolean getTokenEnable(){
        return this.enable;
    }
    public Boolean isTokenOnceUse(){
        return this.once_use;
    }

    public void setTokenPermission(byte permission){
        this.token_permission = permission;
    }

    public byte getTokenPermission(){
        return this.token_permission;
    }

    @Override
    public String toString(){
        String message = "enable:" + ( enable ? "true" : "false" ) + "\n";
        message += "once_use:" + ( once_use ? "true" : "false" ) + "\n";
        message += "owner_token:" + ( owner_token ? "true" : "false" ) + "\n";
        message += "token_permission:";
        switch(token_permission){
            case TOKEN_PERMISSION_ALL:
                message += "ALL";
                break;
            case TOKEN_PERMISSION_LIMIT:
                message += "LIMIT";
                break;
            case TOKEN_PERMISSION_NONE:
                message += "NONE";
                break;
            default:
                message += "UNKNOWN";
                break;
        }
        message += "\n";
        message += "token:" + CodeUtils.bytesToHex(token);
        if (name != null) {
            if (name.length > 0) {
                message += "\nname:" + new String(name, StandardCharsets.US_ASCII);
            }
        }
        return message;
    }
    public String modifyTokenToString(){
        String message = "";
        message += "once_use:" + ( once_use ? "true" : "false" ) + "\n";
        message += "token_permission:";
        switch(token_permission){
            case TOKEN_PERMISSION_ALL:
                message += "ALL";
                break;
            case TOKEN_PERMISSION_LIMIT:
                message += "LIMIT";
                break;
            case TOKEN_PERMISSION_NONE:
                message += "NONE";
                break;
            default:
                message += "UNKNOWN";
                break;
        }
        message += "\n";
        message += "token:" + CodeUtils.bytesToHex(token);
        if (name != null) {
            if (name.length > 0) {
                message += "\nname:" + new String(name, StandardCharsets.US_ASCII);
            }
        }
        return message;
    }
    public String getTokenAd(){
        String message = "";
        if (this.token != null) {
            if (this.token.length > 0){
                if (this.enable) {
                    if (this.once_use) {
                        message = "once use " + "token: " + CodeUtils.bytesToHex(token).substring(0,6) + "...";
                    } else {
                        message = "token: " + CodeUtils.bytesToHex(token).substring(0,12) + "...";
                    }
                } else {
                    message = "disable";
                }
            } else {
                message = "none";
            }
        } else {
            message = "none";
        }
        return message;
    }
    private String getTokenExchangeDataV1(){
        try {
            JSONObject data = new JSONObject();
            data.put(EXCHANGE_TAG_VERSION,EXCHANGE_VERSION);
            data.put(EXCHANGE_TAG_ENABLE,this.enable);
            data.put(EXCHANGE_TAG_ONCE_USE,this.once_use);
            data.put(EXCHANGE_TAG_OWNER_TOKEN,this.owner_token);
            data.put(EXCHANGE_TAG_TOKEN,CodeUtils.bytesToHex(this.token));
            data.put(EXCHANGE_TAG_NAME,CodeUtils.bytesToHex(this.name));
            data.put(EXCHANGE_TAG_INDEX,this.exchange_index);
            return data.toString();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return "{}";
        }
    }
    public String getTokenExchangeData(){
        return getTokenExchangeDataV1();
    }
    public static SunionTokenStatus decodeTokenExchangeData(JSONObject json) throws JSONException {
        SunionTokenStatus new_token = new SunionTokenStatus();
        new_token.enable = json.getBoolean(EXCHANGE_TAG_ENABLE);
        new_token.once_use = json.getBoolean(EXCHANGE_TAG_ONCE_USE);
        new_token.owner_token = json.getBoolean(EXCHANGE_TAG_OWNER_TOKEN);
        new_token.token = CodeUtils.hexStringToBytes(json.getString(EXCHANGE_TAG_TOKEN));
        new_token.name = CodeUtils.hexStringToBytes(json.getString(EXCHANGE_TAG_NAME));
        new_token.exchange_index = json.getInt(EXCHANGE_TAG_INDEX);
        return new_token;
    }
}
