package de.kai_morich.simple_bluetooth_le_terminal.payload;

public class SunionTokenStatus {

    private Boolean enable = false;
    private Boolean once_use = false;
    private byte[] token;
    private byte[] name;

    public static final String ENABLE = "ENABLE";
    public static final String ONCE_USE = "ONCE_USE";
    public static final String TOKEN = "TOKEN";
    public static final String NAME = "NAME";

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
