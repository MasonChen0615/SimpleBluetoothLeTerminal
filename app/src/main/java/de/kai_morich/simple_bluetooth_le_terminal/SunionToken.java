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
    public static SunionToken decodeTokenPayload(byte[] data){
//        0	1	是否可用 1:可用, 0:不可用
//        1	1	是否是永久 Token 1:永久, 0:一次性
//        2 ~ 9	8	Token
//        11 ~	(最多 20 Byte)	Name
        byte type = (byte)0x00;
        byte[] token = new byte[8];
        int count = 0;
        for(byte b : data){
            if(count == 0){
                //token state.
            } else if (count == 1){
                type = b;
            } else if (count >= 2 && count <= 9){
                token[count-2] = b;
            } else {
                //token name.
            }
            count++;
        }
        return new SunionToken((int)type , token);
    }
}
