package de.kai_morich.simple_bluetooth_le_terminal;

public class SunionToken {
    private int token_type;
    private byte[] token;
    public SunionToken(int type, byte[] token){
        this.token_type = type;
        this.token = token;
    }
    public int getTokenType(){
        return this.token_type;
    }
    public byte[] getToken(){
        return this.token;
    }
}
