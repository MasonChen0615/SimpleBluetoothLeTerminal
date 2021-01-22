package de.kai_morich.simple_bluetooth_le_terminal.payload;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.nio.charset.StandardCharsets;

import de.kai_morich.simple_bluetooth_le_terminal.CodeUtils;

public class SunionTokenStatus {

    private Boolean enable = false;
    private Boolean once_use = false;
    private Boolean owner_token = false;
    private byte[] token;
    private byte[] name;

    public static final String ENABLE = "ENABLE";
    public static final String ONCE_USE = "ONCE_USE";
    public static final String TOKEN = "TOKEN";
    public static final String NAME = "NAME";
    public static final int NAME_MAX_SIZE = 20;

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
    public static SunionTokenStatus decodeTokenPayload(byte[] payload) throws Exception{
        SunionTokenStatus new_token = new SunionTokenStatus();
        final int token_head = 10;
        int name_offset = 2;
        if (payload.length >= token_head) {
            new_token.enable = (payload[0] == ((byte) 0x01)) ? true : false;
            new_token.once_use = (payload[1] == ((byte) 0x00)) ? true : false;
            //TODO : need to delete when deivce use new token_head size(11)
            if (token_head == 10){
                name_offset = 2;
            } else if (token_head == 11){
                new_token.owner_token = (payload[2] == ((byte) 0x01)) ? true : false;
                name_offset = 3;
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

    @Override
    public String toString(){
        String message = "enable:" + ( enable ? "true" : "false" ) + "\n";
        message += "once_use:" + ( once_use ? "true" : "false" ) + "\n";
        //TODO : wait for deivce update new version.
//        message += "owner token:" + ( owner_token ? "true" : "false" ) + "\n";
        message += "token:" + CodeUtils.bytesToHex(token);
        if (name != null) {
            if (name.length > 0) {
                message += "\nname:" + new String(name, StandardCharsets.US_ASCII);
            }
        }
        return message;
    }
}
