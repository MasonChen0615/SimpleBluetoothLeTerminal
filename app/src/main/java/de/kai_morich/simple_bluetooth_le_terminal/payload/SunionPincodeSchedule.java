package de.kai_morich.simple_bluetooth_le_terminal.payload;

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
    public static final byte SEEK_TIME = (byte) 0x57;  // S

    // only W can use, if not in W mode , WEEK DAY default value is WEEK_NOT_USE
/**
 *  n | NaN | SUN | SAT | FRI | THUR | WED | TUE | MON |
 *  b |  7  |  6  |  5  |  4  |  3   |  2  |  1  |  0  |
 *  week MON WED FRI -> (use or Bitwise operators)   WEEK_MON | WEEK_WED | WEEK_FRI
 */
    public static final byte WEEK_MON = (byte) 0x01;
    public static final byte WEEK_TUE = (byte) 0x02;
    public static final byte WEEK_WED = (byte) 0x04;
    public static final byte WEEK_THUR = (byte) 0x08;
    public static final byte WEEK_FRI = (byte) 0x10;
    public static final byte WEEK_SAT = (byte) 0x20;
    public static final byte WEEK_SUN = (byte) 0x40;
    public static final byte WEEK_NOT_USE = (byte) 0x80;
    public static final byte[] WEEK_GROUP = new byte[]{WEEK_MON, WEEK_TUE, WEEK_WED, WEEK_THUR, WEEK_FRI, WEEK_SAT, WEEK_SUN};
    public static final String[] WEEK_NAME_GROUP = new String[]{"MON", "TUE", "WED", "THUR", "FRI", "SAT", "SUN"};
    // only W can use, if not in W mode , WEEK_TIME default value is WEEK_TIME_NOT_USE
    public static final byte WEEK_TIME_MIN = (byte) 0x00;
    public static final byte WEEK_TIME_MAX = (byte) 0x95;
    public static final byte WEEK_TIME_NOT_USE = (byte) 0xAA;

//    public static final byte WEEK_TIME_MIN = (byte) 0x00;
//    public static final byte WEEK_TIME_MAX = (byte) 0x95;
//    public static final byte WEEK_TIME_NOT_USE = (byte) 0xAA;


    private byte schedule_type;
    private byte weekday;


    public static SunionPincodeSchedule decodePincodeSchedulePayload(byte[] row_schedule){
        for ( int i = 0 ; i < row_schedule.length ; i++ ){

        }
        return new SunionPincodeSchedule();
    }
    public byte getWeekDay(){
        return this.weekday;
    }
    public byte[] decodeWeekDay(byte weekday){
        if (this.schedule_type != WEEK_ROUTINE) {
            return new byte[]{};
        }
        int count = 0;
        byte[] tmp = new byte[7];
        for (byte cmp : WEEK_GROUP){
            if ((weekday & cmp) == cmp) {
                count++;
                tmp[count] = cmp;
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
}
