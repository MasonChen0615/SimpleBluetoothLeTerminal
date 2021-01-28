package de.kai_morich.simple_bluetooth_le_terminal.payload;

import java.text.DecimalFormat;
import java.util.Date;

import de.kai_morich.simple_bluetooth_le_terminal.CodeUtils;

public class SunionLockStatus {
    public byte lock_status;
    public byte dead_bolt;
    public byte autolock;
    public byte autolock_delay;
    public byte vacation_mode;
    public byte keypress_beep;

    private byte preamble;
    private byte firmware_version;
    private byte battery;
    private byte low_battery;
    private int timestamp;

    public double latitude;
    public double longitude;


    public static final byte LOCK_STATUS_RIGHT = (byte) 0xA0;
    public static final byte LOCK_STATUS_LEFT = (byte) 0xA1;
    public static final byte LOCK_STATUS_UNKNOWN = (byte) 0xA2;
    public static final byte LOCK_STATUS_NOT_TO_DO = (byte) 0xA3;
    public static final byte DEAD_BOLT_LOCK = (byte) 0x01;
    public static final byte DEAD_BOLT_UNLOCK = (byte) 0x00;
//    public static final byte DEAD_BOLT_UNKNOWN = any;
    public static final byte LOW_BATTERY_NORMAL = (byte) 0x00;
    public static final byte LOW_BATTERY_SUGGESTION = (byte) 0x01;
    public static final byte LOW_BATTERY_REQUIRED = (byte) 0x02;

    public static final byte MAX_AUTOLOCK_DELAY_TIME = (byte) 0x63;
    public static final byte MIN_AUTOLOCK_DELAY_TIME = (byte) 0x0a;

    public static final byte COMMON_ON = (byte) 0x01;
    public static final byte COMMON_OFF = (byte) 0x00;

    public static final int LOCATION_ACCURACY = 100000;
    public static final int LATITUDE = 0;
    public static final int LONGITUDE = 1;
    public static final double DEFAULT_LATITUDE = 25.0562565;
    public static final double DEFAULT_LONGITUDE = 121.4729115;
    public static final double LATITUDE_MIN = -90.0;
    public static final double LATITUDE_MAX = 90.0;
    public static final double LONGITUDE_MIN = -180.0;
    public static final double LONGITUDE_MAX = 180.0;

    public SunionLockStatus(){
        // not to do.
    }
    public SunionLockStatus(byte lock_status, Boolean keypress_beep, Boolean vacation_mode, Boolean autolock, int autolock_delay , byte dead_bolt){
        this.lock_status = lock_status;
        this.keypress_beep = (keypress_beep) ? (byte)0x01 : (byte)0x00;
        this.vacation_mode = (vacation_mode) ? (byte)0x01 : (byte)0x00;
        this.autolock = (autolock) ? (byte)0x01 : (byte)0x00;
        setAutoLockDelay(autolock_delay);
        this.dead_bolt = dead_bolt;
        this.setGeographicLocation(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
    }
    public void setAutoLockDelay(int autolock_delay){
        if (autolock_delay > 99) {
            this.autolock_delay = MAX_AUTOLOCK_DELAY_TIME;
        } else if (autolock_delay < 10) {
            this.autolock_delay = MIN_AUTOLOCK_DELAY_TIME;
        } else {
            this.autolock_delay = (byte)autolock_delay;
        }
    }

    public void setGeographicLocation(double latitude , double longitude){
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double[] getGeographicLocation(){
        return new double[]{this.latitude, this.longitude};
    }

    public static double[] decodeGeographicLocation(byte[] location){
        if (location.length == 16){
            byte[] h_bytes,l_bytes;
            h_bytes = new byte[]{location[0], location[1], location[2], location[3]};
            l_bytes = new byte[]{location[4], location[5], location[6], location[7]};
            double latitude = (double) CodeUtils.littleEndianToInt(h_bytes) + ((double) CodeUtils.littleEndianToInt(l_bytes) / (double) LOCATION_ACCURACY );
            h_bytes = new byte[]{location[8], location[9], location[10], location[11]};
            l_bytes = new byte[]{location[12], location[13], location[14], location[15]};
            double longitude = (double) CodeUtils.littleEndianToInt(h_bytes) + ((double) CodeUtils.littleEndianToInt(l_bytes) / (double) LOCATION_ACCURACY );
            return new double[]{latitude, longitude};
        } else {
            return new double[]{DEFAULT_LATITUDE, DEFAULT_LONGITUDE};
        }
    }

    public byte[] getGeographicLocation(int ent){
        byte[] location = new byte[8];  // FF FF FF FF . FF FF FF FF
        double target = this.latitude;
        switch(ent){
            case LATITUDE:
                target = this.latitude;
                break;
            case LONGITUDE:
                target = this.longitude;
                break;
        }
        byte[] tmp;
        tmp = CodeUtils.intToLittleEndian((long) target);
        for(int i = 0; i < 4 ; i++){
            location[i] = tmp[i];
        }
        double tmp_d = target;
        // 0.0562565  -> 5625.65
        tmp_d = (tmp_d - (long) target) * LOCATION_ACCURACY;
        tmp = CodeUtils.intToLittleEndian((long) tmp_d);
        for(int i = 4; i < 8 ; i++){
            location[i] = tmp[i-4];
        }
        return location;
    }

    public static SunionLockStatus decodeLockStatusPayload(byte[] data){
//       1	1	鎖體方向 0xA0:右鎖, 0xA1:左鎖, 0xA2:未知, 0xA3 忽視
//       2	1	聲音 1:開啟, 0:關閉
//       3	1	假期模式 1:開啟, 0:關閉
//       4	1	自動上鎖 1:開啟, 0:關閉
//       5	1	自動上鎖時間 10~99
//       6	1	是否上鎖 1:開啟, 0:關閉, other:未知
//       7	1	電池電量 0 ~ 100
//       8	1	電量警告 2:危險, 1:弱電, 0:正常
//       9 ~ 12	4	TimeStamp (Unix)
        SunionLockStatus status = new SunionLockStatus();
        if (data.length < 12) {
            return status;
        }
        status.lock_status = data[0];
        status.keypress_beep = data[1];
        status.vacation_mode = data[2];
        status.autolock = data[3];
        status.autolock_delay = data[4];
        status.dead_bolt = data[5];
        status.battery = data[6];
        status.low_battery = data[7];
        byte[] array= {data[8], data[9], data[10], data[11]};
        status.timestamp = CodeUtils.littleEndianToInt(array);
        return status;
    }
    public static SunionLockStatus decodeLockConfigPayload(byte[] data){
//      1	1	鎖體方向 0xA0:右鎖, 0xA1:左鎖, 0xA2:未知
//      2	1	聲音 1:開啟, 0:關閉
//      3	1	假期模式 1:開啟, 0:關閉
//      4	1	自動上鎖 1:開啟, 0:關閉
//      5	1	自動上鎖時間 10~99
//      6 ~ 21 location
        SunionLockStatus config = new SunionLockStatus();
        if (data.length < 21) {
            return config;
        }
        config.lock_status = data[0];
        config.keypress_beep = data[1];
        config.vacation_mode = data[2];
        config.autolock = data[3];
        config.autolock_delay = data[4];
        byte[] location = new byte[16];
        for (int i = 0 ; i < 16 ; i++){
            location[i] = data[i+5];
        }
        double[] decode_location = decodeGeographicLocation(location);
        config.latitude = decode_location[SunionLockStatus.LATITUDE];
        config.longitude = decode_location[SunionLockStatus.LONGITUDE];
        return config;
    }

    public byte getDeadBolt(){
        return dead_bolt;
    }
    @Override
    public String toString(){
        String notice = "鎖體方向:";
        switch(lock_status){
            case (byte)0xA0:
                notice += "右鎖";
                break;
            case (byte)0xA1:
                notice += "左鎖";
                break;
            case (byte)0xA2:
                notice += "未知";
                break;
            case (byte)0xA3:
            default:
                notice += "忽視";
                break;
        }
        notice += " 聲音:" + ((keypress_beep == (byte)0x01)?"開啟":"關閉");
        notice += " 假期模式:" + ((vacation_mode == (byte)0x01)?"開啟":"關閉");
        notice += " 自動上鎖:" + ((autolock == (byte)0x01)?"開啟":"關閉");
        int delay = autolock_delay & 0xff;
        notice += " 自動上鎖時間:" + delay + " sec";
        DecimalFormat df = new DecimalFormat("###.#####");
        notice += "latitude:" + df.format(this.latitude) + " " + "longitude:" + df.format(this.longitude);
        return notice;
    }

    public String configToString(){
        String notice = "";
        switch(lock_status){
            case (byte)0xA0:
                notice += "lock status: right hand lock\n";
                break;
            case (byte)0xA1:
                notice += "lock status: left hand lock\n";
                break;
            case (byte)0xA2:
                notice += "lock status: need check right/left hand lock\n";
                break;
            case (byte)0xA3:
            default:
                notice += "lock status: skip\n";
                break;
        }
        notice += "keypress beep: " + ((this.keypress_beep == (byte)0x01)?"on":"off") + "\n";
        notice += "vacation mode: " + ((this.vacation_mode == (byte)0x01)?"on":"off")+ "\n";
        notice += "autolock: " + ((this.autolock == (byte)0x01)?"on":"off")+ "\n";
        int delay = this.autolock_delay & 0xff;
        notice += "autolock delay: " + delay + " sec\n";
        DecimalFormat df = new DecimalFormat("###.#####");
        notice += "latitude:" + df.format(this.latitude)+ "\n";
        notice += "longitude:" + df.format(this.longitude);
        return notice;
    }
    public String statusToString(){
        String notice = "";
        switch(lock_status){
            case (byte)0xA0:
                notice += "lock status: right hand lock\n";
                break;
            case (byte)0xA1:
                notice += "lock status: left hand lock\n";
                break;
            case (byte)0xA2:
                notice += "lock status: need check right/left hand lock\n";
                break;
            case (byte)0xA3:
            default:
                notice += "lock status: skip\n";
                break;
        }
        notice += "keypress beep: " + ((this.keypress_beep == (byte)0x01)?"on":"off") + "\n";
        notice += "vacation mode: " + ((this.vacation_mode == (byte)0x01)?"on":"off")+ "\n";
        notice += "autolock: " + ((this.autolock == (byte)0x01)?"on":"off")+ "\n";
        int delay = this.autolock_delay & 0xff;
        notice += "autolock delay: " + delay + " sec\n";
        switch(this.dead_bolt){
            case (byte)0x00:
                notice += "deadbolt: unlock\n";
                break;
            case (byte)0x01:
                notice += "deadbolt: lock\n";
                break;
            default:
                notice += "deadbolt: unknown\n";
                break;
        }
//        byte[] array= {data[8], data[9], data[10], data[11]};
//        status.timestamp = CodeUtils.littleEndianToInt(array);
        int i2_battery = this.battery & 0xff;
        notice += "battery: " + i2_battery + " %\n";
        switch(this.low_battery){
            case (byte) 0x00:
                notice += "battery alarm: normal\n";
                break;
            case (byte) 0x01:
                notice += "battery alarm: low battery\n";
                break;
            case (byte) 0x02:
                notice += "battery alarm: need replacement\n";
                break;
            default:
                notice += "battery alarm: unknown\n";
                break;
        }
        Date time = CodeUtils.convertDate(this.timestamp);
        notice += "timestamp: " + time.toString();
        return notice;
    }
}
