package de.kai_morich.simple_bluetooth_le_terminal;

public class SunionCommandPayload {
    private byte command;
    private int len;
    private byte[] data;
    private int command_iv;
    public SunionCommandPayload(byte command , int len ,byte[] data , int command_iv){
        this.command = command;
        this.len = len;
        this.data = data;
        this.command_iv = command_iv;
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
    public int getCommandVI(){
        return command_iv;
    }
}
