package de.kai_morich.simple_bluetooth_le_terminal.payload;

public class SunionControlStatus {
    private byte config_type = 0;
    private int index = 0;
    private int count = 0;
    private byte[] pincode = new byte[]{SunionPincodeStatus.PWD_0, SunionPincodeStatus.PWD_0, SunionPincodeStatus.PWD_0, SunionPincodeStatus.PWD_0};
    public SunionPincodeSchedule schedule;
    public SunionControlStatus(){
        byte weekday = SunionPincodeSchedule.WEEK_MON | SunionPincodeSchedule.WEEK_TUE | SunionPincodeSchedule.WEEK_WED | SunionPincodeSchedule.WEEK_THUR | SunionPincodeSchedule.WEEK_FRI | SunionPincodeSchedule.WEEK_SAT | SunionPincodeSchedule.WEEK_SUN;
        this.schedule = new SunionPincodeSchedule(
                SunionPincodeSchedule.WEEK_ROUTINE,
                weekday,
                SunionPincodeSchedule.WEEK_TIME_MIN,
                SunionPincodeSchedule.WEEK_TIME_MAX,
                SunionPincodeSchedule.SEEK_TIME_MIN,
                SunionPincodeSchedule.SEEK_TIME_MAX
        );
    }
}
