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
        int data_index = 0;
        int pincode_len = 0;
        byte[] tmp_name = new byte[20];
        pincode.row_schedule = new byte[12];
        String step = DATA_TAG_ENABLE;
        for( byte b : data ){
            switch(step){
                case DATA_TAG_ENABLE:
                    pincode.enable = (b == (byte) 0x01) ? true : false;
                    // next
                    data_index = 0;
                    step = DATA_TAG_PINCODE_LEN;
                    break;
                case DATA_TAG_PINCODE_LEN:
                    pincode_len = b & 0xff;
                    // next
                    if (pincode_len > 0 && pincode_len <= 6){
                        if ( (2 + pincode_len + 12) <= data.length) {
                            pincode.pincode = new byte[pincode_len];
                            data_index = 0;
                            step = DATA_TAG_PINCODE;
                        } else {
                            throw new Exception("data.length not make sense with this pincode size, missing schedule data");
                        }
                    } else if (pincode_len > 6){
                        throw new Exception("pincode size error");
                    } else {
                        //not have pincode
                        step = DATA_TAG_ROW_SCHEDULE;
                    }
                    break;
                case DATA_TAG_PINCODE:
                    if (data_index < pincode_len) {
                        pincode.pincode[data_index++] = b;
                    } else {
                        // next
                        data_index = 0;
                        step = DATA_TAG_ROW_SCHEDULE;
                    }
                    break;
                case DATA_TAG_ROW_SCHEDULE:
                    if (data_index < 12) {
                        pincode.row_schedule[data_index++] = b;
                    } else {
                        // next
                        data_index = 0;
                        step = DATA_TAG_NAME;
                    }
                    break;
                case DATA_TAG_NAME:
                    tmp_name[data_index++] = b;
                    break;
            }
        }
        if (data_index > 0) {
            pincode.name = new byte[data_index];
            for ( int i = 0 ; i < data_index ; i++ ) {
                pincode.name[i] = tmp_name[i];
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
