package de.kai_morich.simple_bluetooth_le_terminal;

public class SunionCommandPayload {
    private byte command;
    private int len;
    private byte[] data;
    public SunionCommandPayload(byte command , int len ,byte[] data){
        this.command = command;
        this.len = len;
        this.data = data;
    }
    public int getLength(){
        return len;
    }
    public byte[] getData(){
        return data;
    }
    public byte getCommand(){
        return command;
    }
}
