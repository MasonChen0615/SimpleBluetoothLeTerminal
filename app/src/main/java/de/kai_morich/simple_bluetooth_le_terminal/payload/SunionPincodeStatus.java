package de.kai_morich.simple_bluetooth_le_terminal.payload;

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

    public SunionPincodeStatus (){}
    public static SunionPincodeStatus decodePincodePayload(byte[] data){
        SunionPincodeStatus pincode = new SunionPincodeStatus();
        int index = 0;
        int point = 0;
        int name_point = 0;
        for (byte b : data) {
            if (index == 0) {
                // PinCode Enable
                pincode.enable = (b == (byte) 0x01) ? true : false;
            } else if (index == 1) {
                // PinCode len
                int len = data[index] & 0xff;
                if (len < 4 || len > 6 || ( len + 1 ) >= data.length ) {
                    break;
                }
                point = len + 2;
                pincode.pincode = new byte[len];
                pincode.name = new byte[data.length - len - 12];
                pincode.row_schedule = new byte[12];
                name_point = point + 12 - 1 ;
            } else if (index > 1 &&  index < point) {
                // PinCode
                pincode.pincode[index-2] = b;
            } else if ( index >= point && index <= name_point ) {
                // row_schedule
                pincode.row_schedule[index-point] = b;
            } else if ( index > name_point && index < data.length) {
                // Name
                pincode.name[index-name_point-1] = b;
            } else {
                //nap
            }
            index++;
        }
        if (pincode.row_schedule.length > 0) {
            pincode.schedule = SunionPincodeSchedule.decodePincodeSchedulePayload(pincode.row_schedule);
        }
        return new SunionPincodeStatus();
    }
}
