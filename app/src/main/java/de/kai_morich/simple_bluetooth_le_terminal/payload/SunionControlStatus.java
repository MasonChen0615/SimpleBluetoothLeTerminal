package de.kai_morich.simple_bluetooth_le_terminal.payload;

import java.util.Random;

import de.kai_morich.simple_bluetooth_le_terminal.CodeUtils;

public class SunionControlStatus {
    private byte config_type = 0;
    private int index = 0;
    private int count = 0;
    private int storage_pincode_index;
    private int storage_token_index;
    private int storage_log_index = 0;
    private int storage_log_index_current_number = 0;
    private byte[] pincode = new byte[]{SunionPincodeStatus.PWD_0, SunionPincodeStatus.PWD_0, SunionPincodeStatus.PWD_0, SunionPincodeStatus.PWD_0};

    public SunionPincodeSchedule schedule;
    public SunionLockStatus config_status;
    public String device_name = "Default-Device";

    public static final int max_pincode_index = 201;
    public static final int max_token_index = 10;

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
        this.config_status = new SunionLockStatus(
                SunionLockStatus.LOCK_STATUS_NOT_TO_DO,
                true,
                true,
                true,
                30,
                SunionLockStatus.DEAD_BOLT_LOCK
        );
    }

    public int getLogCount(){
        return this.count;
    }

    public void setLogCount(int count){
        if (count > SunionLockLog.MAX_LOG_COUNT) {
            this.count = SunionLockLog.MAX_LOG_COUNT;
        } else if (count <= 0){
            this.count = 1;
        } else {
            this.count = count;
        }
    }

    private int getLogIndex(Boolean auto_inc){
        if (auto_inc){
            if ( storage_log_index >= storage_log_index_current_number ) {
                storage_log_index = 0;
                return storage_log_index;
            } else {
                return storage_log_index++;
            }
        } else {
            return storage_log_index;
        }
    }

    public void setLogIndex(int index){
        if (index >= storage_log_index_current_number) {
            storage_log_index = 0;
        } else {
            storage_log_index = index;
        }
    }

    public void setLogCurrentNumber(int index){
        storage_log_index_current_number = index;
    }

    public int getTokenIndex(Boolean auto_inc){
        if (auto_inc){
            if ( storage_token_index >= this.max_token_index ) {
                storage_token_index = 0;
                return storage_token_index;
            } else {
                return storage_token_index++;
            }
        } else {
            return storage_token_index;
        }
    }

    public int getPincodeIndex(Boolean auto_inc){
        if (auto_inc) {
            if (storage_pincode_index > this.max_pincode_index) {
                storage_pincode_index = 0;
                return storage_pincode_index;
            } else {
                return storage_pincode_index++;
            }
        } else {
            return storage_pincode_index;
        }
    }

    public void setDeviceName(String device_name){
        this.device_name = device_name;
    }

    public String getDeviceName(Boolean auto_random){
        if (auto_random) {
            int random_name = new Random().nextInt((999 - 100) + 1) + 100;
            this.device_name = "Name-" + random_name;
        }
        return this.device_name;
    }

    public byte[] getTime() {
        Long tsLong = System.currentTimeMillis()/1000;
        byte[] data = CodeUtils.intToLittleEndian(tsLong);
        return data;
    }
}
