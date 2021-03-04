package de.kai_morich.simple_bluetooth_le_terminal.payload;

import android.util.Log;

import de.kai_morich.simple_bluetooth_le_terminal.CodeUtils;

/**
 *
 * |       index       | Length | Decription                                                                                                            |
 * |:-----------------:|:------:|:--------------------------------------------------------------------------------------------------------------------- |
 * |      (4+len)      |   1    | A: 所有時間皆可使用<br/> N: 所有時間皆不可使用<br/> O: 只能使用一次<br/> W: 每周固定時間使用<br/> S: 從TimeA到TimeB可使用  |
 * |      (5+len)      |   1    | 星期 Mon ~ Sun 分別對應到bit 0 ~ 6<br/>1:可用 0:不可用<br/>**(僅限W使用，其餘請塞亂數)**                                 |
 * |      (6+len)      |   1    | 時段  0 ~ 95<br/>將一天分為 96 等分, 15分鐘為一間隔<br/>**(僅限W使用，其餘請塞亂數)**                                    |
 * |      (7+len)      |   1    | 時段  0 ~ 95<br/>將一天分為 96 等分, 15分鐘為一間隔<br/>**(僅限W使用，其餘請塞亂數)**                                    |
 * | (8+len)~(11+len)  |   4    | timeA<br/>TimeStamp (Unix), uint32_t Little-endian<br/>**(僅限S使用，其餘請塞亂數)**                                   |
 * | (12+len)~(15+len) |   4    | timeB (需大於timeA)<br/>TimeStamp (Unix), uint32_t Little-endian<br/>**(僅限S使用，其餘請塞亂數)**                     |
 */
public class SunionPincodeSchedule {

//    A: 所有時間皆可使用
//    N: 所有時間皆不可使用
//    O: 只能使用一次
//    W: 每周固定時間使用
//    S: 從TimeA到TimeB可使用
    public static final byte ALL_DAY = (byte) 0x41;  // A
    public static final byte ALL_DAY_DENY = (byte) 0x4E;  // N
    public static final byte ONCE_USE = (byte) 0x4F;  // O
    public static final byte WEEK_ROUTINE = (byte) 0x57;  // W
    public static final byte SEEK_TIME = (byte) 0x53;  // S
    public static final String[] Schedule_NAME_GROUP = new String[]{"ALL DAY" , "ALL DAY DENY" , "ONCE USE" , "WEEK ROUTINE" , "SEEK TIME"};
    public static final byte[] Schedule_Type_GROUP = new byte[]{     ALL_DAY,    ALL_DAY_DENY,    ONCE_USE,    WEEK_ROUTINE,    SEEK_TIME };

    // only W can use, if not in W mode , WEEK DAY default value is WEEK_NOT_USE
    /**
     *  n | NaN | SUN | SAT | FRI | THUR | WED | TUE | MON |
     *  b |  7  |  6  |  5  |  4  |  3   |  2  |  1  |  0  |
     *  week MON WED FRI -> (Use Bitwise operators)   WEEK_MON | WEEK_WED | WEEK_FRI
     */
    /**
     *  n | NaN | SAT | FRI | THUR | WED | TUE | MON | SUN |
     *  b |  7  |  6  |  5  |  4  |  3   |  2  |  1  |  0  |
     *  week MON WED FRI -> (Use Bitwise operators)   WEEK_MON | WEEK_WED | WEEK_FRI
     */
    public static final byte WEEK_SUN = (byte) 0x01;
    public static final byte WEEK_MON = (byte) 0x02;
    public static final byte WEEK_TUE = (byte) 0x04;
    public static final byte WEEK_WED = (byte) 0x08;
    public static final byte WEEK_THUR = (byte) 0x10;
    public static final byte WEEK_FRI = (byte) 0x20;
    public static final byte WEEK_SAT = (byte) 0x40;
    public static final byte WEEK_NOT_USE = (byte) 0x80;
    public static final byte[] WEEK_GROUP = new byte[]{WEEK_MON, WEEK_TUE, WEEK_WED, WEEK_THUR, WEEK_FRI, WEEK_SAT, WEEK_SUN};
    public static final String[] WEEK_NAME_GROUP = new String[]{"MON", "TUE", "WED", "THUR", "FRI", "SAT", "SUN"};
    // only W can use, if not in W mode , WEEK_TIME default value is WEEK_TIME_NOT_USE
    public static final byte WEEK_TIME_START = (byte) 0x02;
    public static final byte WEEK_TIME_END = (byte) 0x03;
    public static final byte WEEK_TIME_MIN = (byte) 0x00;
    public static final byte WEEK_TIME_MAX = (byte) 0x5F;
    public static final byte WEEK_TIME_NOT_USE = (byte) 0xAA;

    public static final byte SEEK_TIME_START = (byte) 0x04;
    public static final byte SEEK_TIME_END = (byte) 0x08;
    public static final int SEEK_TIME_MIN = 0;
    public static final int SEEK_TIME_MAX = 86399;
    public static final byte SEEK_TIME_NOT_USE = (byte) 0xAA;

    private byte[] row_schedule;
    public byte schedule_type;
    public byte weekday;
    public byte weektime_start;
    public byte weektime_end;
    public int seektime_start;
    public int seektime_end;

    public SunionPincodeSchedule(){}
    public SunionPincodeSchedule(byte schedule_type, byte weekday, byte weektime_start, byte weektime_end, int seektime_start, int seektime_end){
        this.schedule_type = schedule_type;
        this.weekday = weekday;
        this.weektime_start = weektime_start;
        this.weektime_end = weektime_end;
        this.seektime_start = seektime_start;
        this.seektime_end = seektime_end;
        encodePincodeSchedulePayload();
    }
    /**
     * convertWeekTime to 0 ~ 95 pick
     * @param value HH:mm
     */
    public static byte convertWeekTime(String value){
        byte weektime = 0x00;
        if(value.length() == 5){
            int h = Integer.parseInt(value.substring(0,2));
            int m = Integer.parseInt(value.substring(3,5));
            weektime = (byte) ((h * 4) + (m / 15));
        }
        return weektime;
    }
    public byte[] encodePincodeSchedulePayload(){
        this.row_schedule = new byte[12];
        this.row_schedule[0] = this.schedule_type;
        this.row_schedule[1] = this.weekday;
        this.row_schedule[2] = this.weektime_start;
        this.row_schedule[3] = this.weektime_end;
        byte[] tmp;
        tmp = CodeUtils.intToLittleEndian(this.seektime_start);
        for(int i = 0 ; i < tmp.length ; i++ ){
            this.row_schedule[4+i] = tmp[i];
        }
        tmp = CodeUtils.intToLittleEndian(this.seektime_end);
        for(int i = 0 ; i < tmp.length ; i++ ){
            this.row_schedule[8+i] = tmp[i];
        }
        return this.row_schedule;
    }
    public static SunionPincodeSchedule decodePincodeSchedulePayload(byte[] row_schedule){
        SunionPincodeSchedule schedule = new SunionPincodeSchedule();
        schedule.row_schedule = row_schedule;
        byte[] row_seektime_start = new byte[4];
        byte[] row_seektime_end = new byte[4];
        for ( int i = 0 ; i < row_schedule.length ; i++ ){
            if ( i == 0 ) {
                schedule.schedule_type = row_schedule[i];
            } else if ( i == 1 ) {
                schedule.weekday = row_schedule[i];
            } else if ( i == 2 ) {
                schedule.setWeekTime(WEEK_TIME_START , row_schedule[i]);
            } else if ( i == 3 ) {
                schedule.setWeekTime(WEEK_TIME_END , row_schedule[i]);
            } else if ( i > 3 &&  i < 8 ) {
                row_seektime_start[i-4] = row_schedule[i];
            } else if ( i >= 8 &&  i <= 11 ) {
                row_seektime_end[i-8] = row_schedule[i];
            } else {
                //NaN
            }
        }
        schedule.setSeekTime(SEEK_TIME_START,row_seektime_start);
        schedule.setSeekTime(SEEK_TIME_END,row_seektime_end);
        return schedule;
    }
    public byte getWeekDay(){
        return this.weekday;
    }
    public byte[] decodeWeekDay(byte weekday){
        if (this.schedule_type != WEEK_ROUTINE) {
            return new byte[]{};
        }
        byte[] tmp = new byte[7];
        int count = 0;
        for (int i = 0 ; i < tmp.length ; i++){
            if((weekday & WEEK_GROUP[i]) == WEEK_GROUP[i] ){
                tmp[count] = WEEK_GROUP[i];
                count++;
            }
        }
        if ( count > 0 ) {
            byte[] week = new byte[count];
            for (int i = 0 ; i < count ; i++ ) {
                week[i] = tmp[i];
            }
            return week;
        } else {
            return new byte[]{};
        }
    }
    public String weekdayToString(byte decodeweekday){
        for (int i = 0 ; i < WEEK_GROUP.length ; i ++) {
            if ((WEEK_GROUP[i] & decodeweekday) == WEEK_GROUP[i]) {
                return WEEK_NAME_GROUP[i];
            }
        }
        return "NaN";
    }
    public void setSeekTime(byte ent , byte[] value){
        switch(ent){
            case SEEK_TIME_START:
                seektime_start = CodeUtils.littleEndianToInt(value);
                break;
            case SEEK_TIME_END:
                seektime_end = CodeUtils.littleEndianToInt(value);
                break;
        }
    }
    public int getSeekTime(byte ent){
        switch(ent){
            case SEEK_TIME_START:
                return seektime_start;
            case SEEK_TIME_END:
                return seektime_end;
            default:
                return (byte)0x00;
        }
    }


    public void setWeekTime(byte ent , byte value){
        switch(ent){
            case WEEK_TIME_START:
                weektime_start = value;
                break;
            case WEEK_TIME_END:
                weektime_end = value;
                break;
        }
    }
    public byte getWeekTime(byte ent){
        switch(ent){
            case WEEK_TIME_START:
                return weektime_start;
            case WEEK_TIME_END:
                return weektime_end;
            default:
                return (byte)0x00;
        }
    }
    public String getWeekTimeConvert(byte weektime){
        // p 0 ~ 95
        int p = (weektime & 0xff) + 1;
        // p 1 ~ 96
        p = p * 900;
        int h = p / 3600;
        int m = p % 60;
        return String.format("%02d", h) + ":" + String.format("%02d", m);
    }
    @Override
    public String toString(){
        String message = "schedule type:" + CodeUtils.bytesToHex(new byte[]{this.schedule_type}) + "\n";
        switch(this.schedule_type){
            case ALL_DAY:
                message += "time range : all day";
                break;
            case ALL_DAY_DENY:
                message += "time range : all day deny";
                break;
            case ONCE_USE:
                message += "time range : all day but once use";
                break;
            case WEEK_ROUTINE:
                message += "time range : in week ";
                for (byte day : this.decodeWeekDay(this.getWeekDay())){
                    message += this.weekdayToString(day) + " ";
                }
                message += "\nfrom " + this.getWeekTimeConvert(this.getWeekTime(WEEK_TIME_START)) + "\nto " + this.getWeekTimeConvert(this.getWeekTime(WEEK_TIME_END));
                break;
            case SEEK_TIME:
                message += "time range : \nfrom " + CodeUtils.convertDate(this.getSeekTime(SEEK_TIME_START)).toString() + "\nto " + CodeUtils.convertDate(this.getSeekTime(SEEK_TIME_END)).toString();
                break;
            default:
                message += "time range : unknown schedule type";
                break;
        }
        return message;
    }
}

