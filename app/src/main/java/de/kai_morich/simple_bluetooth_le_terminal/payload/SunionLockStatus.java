package de.kai_morich.simple_bluetooth_le_terminal.payload;

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
    public static SunionLockStatus decodeLockStatusPayload(byte[] data){
//        1	1	鎖體方向 0xA0:右鎖, 0xA1:左鎖, 0xA2:未知
//        2	1	聲音 1:開啟, 0:關閉
//        3	1	假期模式 1:開啟, 0:關閉
//        4	1	自動上鎖 1:開啟, 0:關閉
//        5	1	自動上鎖時間 10~99
//        6	1	是否上鎖 1:開啟, 0:關閉, other:未知
//        7	1	電池電量 0 ~ 100
//        8	1	電量警告 2:危險, 1:弱電, 0:正常
//        9 ~ 12	4	TimeStamp (Unix)
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

    public byte getDeadBolt(){
        return dead_bolt;
    }
}
