package de.kai_morich.simple_bluetooth_le_terminal.payload;

import de.kai_morich.simple_bluetooth_le_terminal.CodeUtils;

//    1	1	Enable 1:可使用, 0:不可使用
//    2	1	PinCode長度 4 ~ 6
//    3 ~	len	PinCode
//    (4+len) ~ (15+len)	12	Schedule
//    (16+len) ~	(最多 20 Byte)	Name
public class SunionPincodeStatus {
    private Boolean enable;
    private byte[] pincode;
    private byte[] row_schedule;
    private SunionPincodeSchedule schedule;
    private byte[] name;

    private static final String DATA_TAG_ENABLE = "DATA_TAG_ENABLE";
    private static final String DATA_TAG_PINCODE_LEN = "DATA_TAG_PINCODE_LEN";
    private static final String DATA_TAG_PINCODE = "DATA_TAG_PINCODE";
    private static final String DATA_TAG_ROW_SCHEDULE = "DATA_TAG_ROW_SCHEDULE";
    private static final String DATA_TAG_NAME = "DATA_TAG_NAME";

    public static final byte PWD_0 = (byte) 0x00;
    public static final byte PWD_1 = (byte) 0x01;
    public static final byte PWD_2 = (byte) 0x02;
    public static final byte PWD_3 = (byte) 0x03;
    public static final byte PWD_4 = (byte) 0x04;
    public static final byte PWD_5 = (byte) 0x05;
    public static final byte PWD_6 = (byte) 0x06;
    public static final byte PWD_7 = (byte) 0x07;
    public static final byte PWD_8 = (byte) 0x08;
    public static final byte PWD_9 = (byte) 0x09;

    public SunionPincodeStatus (){}
    public SunionPincodeStatus (Boolean enable){
        this.enable = enable;
    }
    public SunionPincodeStatus (Boolean enable , byte[] pincode , SunionPincodeSchedule schedule ,byte[] name){
        this.enable = enable;
        this.pincode = pincode;
        this.schedule = schedule;
        this.row_schedule = schedule.encodePincodeSchedulePayload();
        this.name = name;
    }
    public byte[] encodePincodePayload(){
        row_schedule = schedule.encodePincodeSchedulePayload();
        byte[] data = new byte[ 1 + 1 + pincode.length + row_schedule.length + name.length ];
        for (int i = 0 ; i < data.length ; i++) {
            if (i == 0) {
                data[i] = enable ? (byte) 0x01 : (byte) 0x00;
            } else if (i == 1) {
                data[i] = (byte)pincode.length;
            } else if (i > 1 && i < (2 + pincode.length)) {
                data[i] = pincode[i-2];
            } else if (i > (1 + pincode.length) && i < (2 + pincode.length + row_schedule.length)) {
                data[i] = row_schedule[i-2-pincode.length];
            } else if (i > (1 + pincode.length + row_schedule.length) && i < (2 + pincode.length + row_schedule.length + name.length)) {
                data[i] = name[i-2-pincode.length-row_schedule.length];
            }
        }
        return data;
    }
    public static SunionPincodeStatus decodePincodePayload(byte[] data) throws Exception{
        /**
         * TODO: debug this : 0900EC1900010400000000577F00950000000095FFFFFF48656C6C6F213D3D3D
         * name : Hello!
         * pin code : 0 0 0 0
         * schedule is wrong.
         */
        SunionPincodeStatus pincode = new SunionPincodeStatus();
        int index = 0;
        int pincode_len = 0;
        pincode.row_schedule = new byte[12];
        while (index < data.length) {
            if(index == 0){
                pincode.enable = (data[index] == (byte) 0x01) ? true : false;
                index++;
                pincode_len = data[index] & 0xff;
                index++;
                if(pincode_len > 6){
                    throw new Exception("pincode length not make sense");
                }
                if ( (2 + pincode_len + 12) >= data.length) {
                    throw new Exception("data.length not make sense with this pincode size, missing schedule data");
                }
                if(pincode_len > 0 && pincode_len <= 6) {
                    pincode.pincode = new byte[pincode_len];
                    for(int i = 0 ; i < pincode_len ; i++){
                        pincode.pincode[i] = data[index];
                        index++;
                    }
                }
                for(int i = 0 ; i < pincode.row_schedule.length ; i++ ){
                    pincode.row_schedule[i] = data[index];
                    index++;
                }
                continue;
            } else {
                // name
                pincode.name = new byte[data.length - index];
                for(int i = 0 ; i < pincode.name.length ; i++){
                    pincode.name[i] = data[index];
                    index++;
                }
            }
        }
        pincode.schedule = SunionPincodeSchedule.decodePincodeSchedulePayload(pincode.row_schedule);
        return pincode;
    }

    @Override
    public String toString(){
        String message = "enable:" + (this.enable?"true":"false") + "\n";
        if (this.name != null) {
            message += "name:" + CodeUtils.bytesToHex(this.name)+ "\n";
        }
        if (this.pincode != null) {
            message += "pincode:" + CodeUtils.bytesToHex(this.pincode)+ "\n";
        }
        message += "schedule:" + this.schedule.toString();
        return message;
    }
}
