package de.kai_morich.simple_bluetooth_le_terminal.payload;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.nio.charset.StandardCharsets;

public class SunionTokenStatus {

    private Boolean enable = false;
    private Boolean once_use = false;
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
}
