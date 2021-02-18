package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.kai_morich.simple_bluetooth_le_terminal.payload.*;

/**
 * create notification and queue serial data while activity is not in the foreground
 * use listener chain: SerialSocket -> SerialService -> UI fragment
 */
public class SerialService extends Service implements SerialListener {

    public String lock_token = "85121456";
    public String lock_aes_key = "SUNION_8512-6108";
    public SunionTokenStatus secret_lock_token = new SunionTokenStatus();
    private byte[] app_random_aes_key;
    private byte[] device_random_aes_key;
    private SecretKey connection_aes_key = null;
    private byte current_command = 0x00;
    private String command_step = CodeUtils.Command_Initialization;
    private int retry = 0;
    private int retry_wait = 0;
    private int command_iv = 1;

    private int lock_log_current_number = -1;
    private SunionTokenStatus[] storage_token = new SunionTokenStatus[10];
    private Boolean storage_token_isset = false;
    private SunionPincodeStatus[] storage_pincode = new SunionPincodeStatus[250];
    private Boolean storage_pincode_isset = false;
    private Boolean storage_pincode_admin_require = false;

    class SerialBinder extends Binder {
        SerialService getService() { return SerialService.this; }
    }

    private enum QueueType {Connect, ConnectError, Read, IoError}

    private class QueueItem {
        QueueType type;
        byte[] data;
        Exception e;

        QueueItem(QueueType type, byte[] data, Exception e) { this.type=type; this.data=data; this.e=e; }
    }

    private final Handler mainLooper;
    private final IBinder binder;
    private final Queue<QueueItem> queue1, queue2;

    private SerialSocket socket;
    private SerialListener listener;
    private boolean connected;

    public void killWatchRead(){
        socket.killWatchRead();
    }

    /**
     * Lifecylce
     */
    public SerialService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SerialBinder();
        queue1 = new LinkedList<>();
        queue2 = new LinkedList<>();
    }

    @Override
    public void onDestroy() {
        cancelNotification();
        disconnect();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Api
     */
    public void connect(SerialSocket socket) throws IOException {
        socket.connect(this);
        this.socket = socket;
        connected = true;
    }

    public void disconnect() {
        this.resetCommandIV();
        connected = false; // ignore data,errors while disconnecting
        cancelNotification();
        if(socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if(!connected)
            throw new IOException("not connected");
        socket.write(data);
    }

    public void write(byte[] data , byte command) throws IOException {
        setCurrentCommand(command);
        if(!connected)
            throw new IOException("not connected");
        socket.write(data);
    }

    public void write(byte[] data , byte command , String step) throws IOException {
        setCurrentCommand(command);
        setCurrentCommandStep(step);
        if(!connected)
            throw new IOException("not connected");
        socket.write(data);
    }

    public void attach(SerialListener listener) {
        if(Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        cancelNotification();
        // use synchronized() to prevent new items in queue2
        // new items will not be added to queue1 because mainLooper.post and attach() run in main thread
        synchronized (this) {
            this.listener = listener;
        }
        for(QueueItem item : queue1) {
            switch(item.type) {
                case Connect:       listener.onSerialConnect      (); break;
                case ConnectError:  listener.onSerialConnectError (item.e); break;
                case Read:          listener.onSerialRead         (item.data); break;
                case IoError:       listener.onSerialIoError      (item.e); break;
            }
        }
        for(QueueItem item : queue2) {
            switch(item.type) {
                case Connect:       listener.onSerialConnect      (); break;
                case ConnectError:  listener.onSerialConnectError (item.e); break;
                case Read:          listener.onSerialRead         (item.data); break;
                case IoError:       listener.onSerialIoError      (item.e); break;
            }
        }
        queue1.clear();
        queue2.clear();
    }

    public void detach() {
        if(connected)
            createNotification();
        // items already in event queue (posted before detach() to mainLooper) will end up in queue1
        // items occurring later, will be moved directly to queue2
        // detach() and mainLooper.post run in the main thread, so all items are caught
        listener = null;
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(Constants.NOTIFICATION_CHANNEL, "Background service", NotificationManager.IMPORTANCE_LOW);
            nc.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(nc);
        }
        Intent disconnectIntent = new Intent()
                .setAction(Constants.INTENT_ACTION_DISCONNECT);
        Intent restartIntent = new Intent()
                .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent disconnectPendingIntent = PendingIntent.getBroadcast(this, 1, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent restartPendingIntent = PendingIntent.getActivity(this, 1, restartIntent,  PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(socket != null ? "Connected to "+socket.getName() : "Background Service")
                .setContentIntent(restartPendingIntent)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(R.drawable.ic_clear_white_24dp, "Disconnect", disconnectPendingIntent));
        // @drawable/ic_notification created with Android Studio -> New -> Image Asset using @color/colorPrimaryDark as background color
        // Android < API 21 does not support vectorDrawables in notifications, so both drawables used here, are created as .png instead of .xml
        Notification notification = builder.build();
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
    }

    private void cancelNotification() {
        stopForeground(true);
    }

    /**
     * SerialListener
     */
    public void onSerialConnect() {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialConnect();
                        } else {
                            queue1.add(new QueueItem(QueueType.Connect, null, null));
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.Connect, null, null));
                }
            }
        }
    }

    public void onSerialConnectError(Exception e) {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialConnectError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.ConnectError, null, e));
                            cancelNotification();
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.ConnectError, null, e));
                    cancelNotification();
                    disconnect();
                }
            }
        }
    }

    public void onSerialRead(byte[] data) {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) { //Run command logic in here
                            if ((data.length % 16) == 0 && !socket.checkWatchRead()){
                                byte[] decode = CodeUtils.decodeAES(getConnectionAESKey(),CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small, data);
                                listener.onSerialRead(decode);
                                sunionCommandHandler(data);
                            }else{
                                listener.onSerialRead(data);
                                //printMessage("wait command");
                            }
                        } else {
                            queue1.add(new QueueItem(QueueType.Read, data, null));
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.Read, data, null));
                }
            }
        }
    }

    public void onSerialIoError(Exception e) {
        if(connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(() -> {
                        if (listener != null) {
                            listener.onSerialIoError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.IoError, null, e));
                            cancelNotification();
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.IoError, null, e));
                    cancelNotification();
                    disconnect();
                }
            }
        }
    }

    public static void sortDecendingSize(byte[]... arrays) {
        Arrays.sort(arrays, new Comparator<byte[]>() {
            @Override
            public int compare(byte[] lhs, byte[] rhs) {
                if (lhs.length > rhs.length)
                    return -1;
                if (lhs.length < rhs.length)
                    return 1;
                else
                    return 0;
            }
        });
    }

    public static byte[] xor(byte[]... arrays) {
        if (arrays.length == 0) {
            return null;
        }
        sortDecendingSize(arrays);
        byte[] result = new byte[arrays[0].length];
        for (byte[] array : arrays) {
            for (int i = 0; i < array.length; i++) {
                result[i] ^= array[i];
            }
        }
        return result;
    }

    public void sunionAppRandomAESKey(byte[] data){
        synchronized (this) {
            this.app_random_aes_key = data;
        }
    }

    public void sunionDeviceRandomAESKey(byte[] data){
        synchronized (this) {
            this.device_random_aes_key = data;
        }
    }

    public byte[] sunionConnectionAESKey(){
        return xor(this.app_random_aes_key,this.device_random_aes_key);
    }


    public void resetRetry(){
        synchronized (this) {
            this.retry = 0;
        }
    }

    public int incrRetry(){
        synchronized (this) {
            this.retry++;
            if (this.retry > CodeUtils.Retry){
                Log.i(Constants.DEBUG_TAG,"retry wait over " + CodeUtils.Retry + " message.");
            }
            return this.retry;
        }
    }

    public int incrCommandIV(){
        synchronized (this) {
            this.command_iv++;
            if (this.command_iv > 65536){
                Log.i(Constants.DEBUG_TAG,"command_iv over 65536!");
            }
            return this.command_iv;
        }
    }

    public int incrCommandIV(int new_command_iv){
        synchronized (this) {
            if (new_command_iv > this.command_iv) {
                this.command_iv = new_command_iv + 1;
            }else{
                this.command_iv++;
            }
            if (this.command_iv > 65536){
                Log.i(Constants.DEBUG_TAG,"command_iv over 65536!");
            }
            return this.command_iv;
        }
    }

    public int resetCommandIV(){
        synchronized (this) {
            this.command_iv = 1;
            return this.command_iv;
        }
    }

    private void resetCommandState(){
        synchronized (this) {
            this.current_command = CodeUtils.Command_Initialization_Code;
            this.command_step = CodeUtils.Command_Initialization;
            resetRetry();
        }
    }

    public void setConnectionAESKey(SecretKey key){
        synchronized (this) {
            this.connection_aes_key = key;
        }
    }

    public void resetConnectionAESKey(){
        synchronized (this) {
            this.connection_aes_key = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public SecretKey getConnectionAESKey(){
        synchronized (this) {
            if (this.connection_aes_key == null){
                SecretKey key = new SecretKeySpec(this.lock_aes_key.getBytes(StandardCharsets.US_ASCII), 0, this.lock_aes_key.getBytes(StandardCharsets.US_ASCII).length, "AES");
                return key;
            }else{
                return this.connection_aes_key;
            }
        }
    }

    public void setCurrentCommand(byte command){
        synchronized (this) {
            current_command = command;
        }
    }

    public byte getCurrentCommand(){
        synchronized (this) {
            return current_command;
        }
    }

    public void setCurrentCommandStep(String step){
        synchronized (this) {
            command_step = step;
        }
    }

    public String getCurrentCommandStep(){
        synchronized (this) {
            return command_step;
        }
    }

    public void setSecretLockToken(SunionTokenStatus token){
        synchronized (this) {
            secret_lock_token = token;
        }
    }

    public SunionTokenStatus getSecretLockToken(){
        synchronized (this) {
            return secret_lock_token;
        }
    }

    public void setLockLogCurrentNumber(int count){
        synchronized (this) {
            lock_log_current_number = count;
        }
    }

    public int getLockLogCurrentNumber(){
        synchronized (this) {
            return lock_log_current_number;
        }
    }

    public void setLockStorageToken(int index , SunionTokenStatus s_token){
        synchronized (this) {
            storage_token[index] = s_token;
        }
    }

    public SunionTokenStatus getLockStorageToken(int index){
        synchronized (this) {
            return storage_token[index];
        }
    }

    public void setLockStorageTokenISSet(Boolean isset){
        synchronized (this) {
            storage_token_isset = isset;
        }
    }

    public Boolean getLockStorageTokenISSet(){
        synchronized (this) {
            return storage_token_isset;
        }
    }

    public void setLockStoragePincode(int index , SunionPincodeStatus pincode){
        synchronized (this) {
            storage_pincode[index] = pincode;
        }
    }

    public SunionPincodeStatus getLockStoragePincode(int index){
        synchronized (this) {
            return storage_pincode[index];
        }
    }

    public void setLockStoragePincodeISSet(Boolean isset){
        synchronized (this) {
            storage_pincode_isset = isset;
        }
    }

    public Boolean getLockStoragePincodeISSet(){
        synchronized (this) {
            return storage_pincode_isset;
        }
    }

    public void setLockStoragePincodeAdminRequire(Boolean require){
        synchronized (this) {
            storage_pincode_admin_require = require;
        }
    }

    public Boolean getLockStoragePincodeAdminRequire(){
        synchronized (this) {
            return storage_pincode_admin_require;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void printMessage(String message){
        String mymessage = Constants.EXCHANGE_MESSAGE_PREFIX + message + Constants.EXCHANGE_MESSAGE_PREFIX;
        listener.onSerialRead(message.getBytes(StandardCharsets.US_ASCII));
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void exchangeToken(String token){
        String mytoken = Constants.EXCHANGE_LOCKTOKEN_PREFIX + token + Constants.EXCHANGE_LOCKTOKEN_PREFIX;
        listener.onSerialRead(mytoken.getBytes(StandardCharsets.US_ASCII));
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void exchangeTag(String tag){
        String mytag = tag + "TAG" + tag;
        listener.onSerialRead(mytag.getBytes(StandardCharsets.US_ASCII));
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void exchangeData(String tag, String data){
        String mybase64 = Constants.EXCHANGE_DATA_PREFIX + tag + CodeUtils.encodeBase64(data) + tag + Constants.EXCHANGE_DATA_PREFIX;
        listener.onSerialRead(mybase64.getBytes(StandardCharsets.US_ASCII));
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private Boolean checkCommandIncome(SunionCommandPayload commandPackage , byte target){
        if (commandPackage.getCommand() == target) {
            return true;
        } else if (commandPackage.getCommand() == CodeUtils.HaveMangerPinCode) {
            byte[] payload = commandPackage.getData();
            if (payload.length == 1) {
                if (payload[0] == (byte) 0x00) {
                    setLockStoragePincodeAdminRequire(true);
                    printMessage(Constants.CMD_NAME_0xEF + " need set admin pincode.");
                } else {
                    setLockStoragePincodeAdminRequire(false);
                }
            } else {
                printMessage(Constants.CMD_NAME_0xEF + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
            }
            resetCommandState();
            return false;
        } else {
            if (incrRetry() > CodeUtils.Retry){
                resetCommandState();
                Log.e(Constants.DEBUG_TAG, "release resetCommandState when receive message are not target");
            }
            return false;
        }
    }

    synchronized public void sunionCommandHandler(byte[] data){
        SunionCommandPayload commandPackage = CodeUtils.decodeCommandPackage(CodeUtils.decodeAES(getConnectionAESKey(),CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small, data));
        if (commandPackage.getCommand() != CodeUtils.Command_Initialization_Code){
            incrCommandIV(commandPackage.getCommandVI());
            Log.i(Constants.DEBUG_TAG,"sunionCommandHandler decode aes :" + CodeUtils.bytesToHex(CodeUtils.decodeAES(getConnectionAESKey(),CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small, data)));
            String message = "Decode : command " + CodeUtils.bytesToHex(new byte[]{commandPackage.getCommand()}) + " len " + commandPackage.getLength() + " sn " + commandPackage.getCommandVI() + " data " + CodeUtils.bytesToHex(commandPackage.getData());
            Log.i(Constants.DEBUG_TAG,message);
//            listener.onSerialRead(message.getBytes());
        }
        switch(getCurrentCommand()){
            case CodeUtils.BLE_Connect:
                if (checkCommandIncome(commandPackage,CodeUtils.BLE_Connect)){
                    //decode data payload and xor c0 sent and receive aes key to new connection key.
                    this.sunionDeviceRandomAESKey(commandPackage.getData());
                    byte[] xorkey = this.sunionConnectionAESKey();
                    setConnectionAESKey(new SecretKeySpec(xorkey, 0, xorkey.length, "AES"));
                    resetCommandState();
                    Log.i(Constants.DEBUG_TAG, "row vi:" + commandPackage.getCommandVI());
                    Log.i(Constants.DEBUG_TAG, "row command:" + CodeUtils.bytesToHex(new byte[]{commandPackage.getCommand()}));
                    Log.i(Constants.DEBUG_TAG, "row payload:" + CodeUtils.bytesToHex(commandPackage.getData()));
                }
                break;
            case CodeUtils.Connect :  // same CodeUtils.UsingOnceTokenConnect
                switch(getCurrentCommandStep()){
                    case CodeUtils.Connect_UsingTokenConnect:
                        if (checkCommandIncome(commandPackage,CodeUtils.Connect)){
                            //receive C1 1 byte data return , receive token , check and save token.
                            byte[] payload = commandPackage.getData();
                            if (payload.length > 0) {
                                //  3:一次性, 2:拒絕, 1:合法, 0:不合法
                                switch (payload[0]) {
                                    case (byte) 0x00:
                                        // illegal
                                        printMessage("token illegal 0x00");
                                        break;
                                    case (byte) 0x01:
                                        // allow (legal)
                                        printMessage("token allow 0x01");
                                        break;
                                    case (byte) 0x02:
                                        // reject
                                        printMessage("token reject 0x02");
                                        break;
                                    case (byte) 0x03:
                                        // one-time pass
                                        printMessage("token one-time pass 0x03");
                                        break;
                                    default:
                                        //noop
                                        printMessage("token unknown state " + CodeUtils.bytesToHex(new byte[]{payload[0]}));
                                        Log.i(Constants.DEBUG_TAG, "token type check but payload value not in 0x00 ~ 0x03 , row payload:" + CodeUtils.bytesToHex(payload));
                                        break;
                                }
                                resetCommandState();
                                Log.i(Constants.DEBUG_TAG, "release resetCommandState where receive InquireToken in Connect_UsingTokenConnect at Connect");
                            }
                        }
                        break;
                    case CodeUtils.Connect_UsingOnceTokenConnect:
                        if (commandPackage.getCommand() == CodeUtils.Connect) {
                            //receive C1 1 byte data return , receive token , check and save token.
                            byte[] payload = commandPackage.getData();
                            if (payload.length > 0) {
                                //  3:一次性, 2:拒絕, 1:合法, 0:不合法
                                switch (payload[0]) {
                                    case (byte) 0x00:
                                        // illegal
                                        printMessage("token illegal 0x00");
                                        break;
                                    case (byte) 0x01:
                                        // allow (legal)
                                        printMessage("token allow 0x01");
                                        break;
                                    case (byte) 0x02:
                                        // reject
                                        printMessage("token reject 0x02");
                                        break;
                                    case (byte) 0x03:
                                        // one-time pass
                                        printMessage("token one-time pass 0x03");
                                        break;
                                    default:
                                        //noop
                                        printMessage("token unknown state " + CodeUtils.bytesToHex(new byte[]{payload[0]}));
                                        Log.i(Constants.DEBUG_TAG, "token type check but payload value not in 0x00 ~ 0x03 , row payload:" + CodeUtils.bytesToHex(payload));
                                        break;
                                }
                            }
                        } else if (commandPackage.getCommand() == CodeUtils.InquireToken) {
                            byte[] payload = commandPackage.getData();
                            try {
                                SunionTokenStatus secretlocktoken = SunionTokenStatus.decodeTokenPayload(payload);
                                setSecretLockToken(secretlocktoken);
                                exchangeToken("Exchange");
                            } catch (Exception e) {
                                e.printStackTrace();
                                printMessage(CodeUtils.Connect_UsingOnceTokenConnect + e.getMessage());
                            }
                            resetCommandState();
                            Log.i(Constants.DEBUG_TAG, "release resetCommandState where receive InquireToken in Connect_UsingOnceTokenConnect at InquireToken");
                        } else {
                            if (incrRetry() > CodeUtils.Retry){
                                resetCommandState();
                                Log.e(Constants.DEBUG_TAG, "release resetCommandState when receive message are not target");
                            }
                        }
                        break;
                }
                break;
            case CodeUtils.DirectionCheck:
                if (checkCommandIncome(commandPackage,CodeUtils.InquireLockState)){
                    SunionLockStatus status =  SunionLockStatus.decodeLockStatusPayload(commandPackage.getData());
                    if (status.getDeadBolt() == SunionLockStatus.DEAD_BOLT_LOCK) {
                        printMessage(Constants.CMD_NAME_0xCC + " state is lock");
                    } else if (status.getDeadBolt() == SunionLockStatus.DEAD_BOLT_UNLOCK) {
                        printMessage(Constants.CMD_NAME_0xCC + " state is unlock");
                    } else {
                        printMessage(Constants.CMD_NAME_0xCC + " state is unknown");
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.FactoryReset:
                if (checkCommandIncome(commandPackage,CodeUtils.FactoryReset)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xCE + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xCE + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xCE + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xCE + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquireLockName:
                if (checkCommandIncome(commandPackage,CodeUtils.InquireLockName)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length > 0) {
                        String s = new String(payload);
                        printMessage(Constants.CMD_NAME_0xD0 + " : " + s );
                    } else {
                        printMessage(Constants.CMD_NAME_0xD0 + " but length is 0 : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.SetLockName:
                if (checkCommandIncome(commandPackage,CodeUtils.SetLockName)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xD1 + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xD1 + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xD1 + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xD1 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquireLockTime:
                if (checkCommandIncome(commandPackage,CodeUtils.InquireLockTime)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 4) {
                        int timestamp = CodeUtils.littleEndianToInt(payload);
                        Date time = CodeUtils.convertDate(timestamp);
                        printMessage(Constants.CMD_NAME_0xD2 + " timestamp is " + timestamp  + " convert to localtime " + time.toString());
                    } else {
                        printMessage(Constants.CMD_NAME_0xD2 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.SetLockTime:
                if (checkCommandIncome(commandPackage,CodeUtils.SetLockTime)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xD3 + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xD3 + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xD3 + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xD3 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquireLockConfig:
                if (checkCommandIncome(commandPackage,CodeUtils.InquireLockConfig)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 21) {

                        SunionLockStatus config = SunionLockStatus.decodeLockConfigPayload(payload);
                        printMessage(Constants.CMD_NAME_0xD4 + " config start");
                        printMessage(config.configToString());
                        printMessage(Constants.CMD_NAME_0xD4 + " config end");
                    } else {
                        printMessage(Constants.CMD_NAME_0xD4 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.SetLockConfig:
                if (checkCommandIncome(commandPackage,CodeUtils.SetLockConfig)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xD5 + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xD5 + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xD5 + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xD5 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquireLockState:
                if (commandPackage.getCommand() == CodeUtils.InquireLockState) {  // default action.
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 12) {
                        SunionLockStatus status = SunionLockStatus.decodeLockStatusPayload(payload);
                        printMessage(Constants.CMD_NAME_0xD6 + " status report start");
                        printMessage(status.statusToString());
                        printMessage(Constants.CMD_NAME_0xD6 + " status report end");
                    } else {
                        printMessage(Constants.CMD_NAME_0xD6 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.SetLockState:
                if (commandPackage.getCommand() == CodeUtils.InquireLockState) {  // default action.
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 12) {
                        SunionLockStatus status = SunionLockStatus.decodeLockStatusPayload(payload);
                        printMessage(Constants.CMD_NAME_0xD7 + " status report start");
                        printMessage(status.statusToString());
                        printMessage(Constants.CMD_NAME_0xD7 + " status report end");
                    } else {
                        printMessage(Constants.CMD_NAME_0xD7 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquireLockTimeZone:
                if (commandPackage.getCommand() == CodeUtils.InquireLockTimeZone) {  // default action.
                    byte[] payload = commandPackage.getData();
                    if (payload.length >= 4) {
                        printMessage(Constants.CMD_NAME_0xD8 + " status report start");
                        printMessage(SunionLockStatus.decodeTimeZonePayloadToString(payload));
                        printMessage(Constants.CMD_NAME_0xD8 + " status report end");
                    } else {
                        printMessage(Constants.CMD_NAME_0xD8 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.SetLockTimeZone:
                if (commandPackage.getCommand() == CodeUtils.SetLockTimeZone) {  // default action.
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xD9 + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xD9 + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xD9 + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xD9 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquireLogCount:
                if (checkCommandIncome(commandPackage,CodeUtils.InquireLogCount)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        int i2 = payload[0] & 0xFF;  // default byte in java is sign value , but our byte are un-sign , so we need convert it with 0xff.
                        printMessage(Constants.CMD_NAME_0xE0 + " allow , history size :" + i2 + " items");
                        setLockLogCurrentNumber(i2);
                        exchangeTag(Constants.EXCHANGE_TAG_0xE0_PREFIX);
                    } else {
                        printMessage(Constants.CMD_NAME_0xE0 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquireLog:
                if (commandPackage.getCommand() == CodeUtils.InquireLog) {  // default action.
//                    1 ~ 4	4	發生時間 TimeStamp (Unix)
//                    5	1	Event (請見下表)
//                    6 ~	(最多 24 Byte)	Name
                    byte[] payload = commandPackage.getData();
                    if (payload.length >= 5) {
                        printMessage(Constants.CMD_NAME_0xE1 + " status report start");
                        Date time = CodeUtils.convertDate(CodeUtils.littleEndianToInt(new byte[]{payload[0],payload[1],payload[2],payload[3]}));
                        printMessage("timestamp:" + time.toString());
                        printMessage("event:" + SunionLockLog.getLogTypeString(payload[4]));
                        if (payload.length > 5) {
                            byte[] name = new byte[payload.length - 5];
                            for(int i = 5 ; i < payload.length ; i++){
                                name[i-5] = payload[i];
                            }
                            printMessage("name:" + new String(name,StandardCharsets.US_ASCII));
                        }
                        printMessage(Constants.CMD_NAME_0xE1 + " status report end");
                    } else {
                        printMessage(Constants.CMD_NAME_0xE1 + " but length short than 5 : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.DeleteLog:
                if (checkCommandIncome(commandPackage,CodeUtils.DeleteLog)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xE2 + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xE2 + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xE2 + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xE2 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquireTokenArray:
                if (checkCommandIncome(commandPackage,CodeUtils.InquireTokenArray)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 10) {
                        printMessage(Constants.CMD_NAME_0xE4 + " status report start");
                        for(int i = 0 ; i < 10 ; i++){
                            printMessage("Token[" + i + "] status:" + CodeUtils.bytesToHex(new byte[]{payload[i]}));
                            setLockStorageToken(i,new SunionTokenStatus((payload[i] == ((byte) 0x01)) ? true : false));
                        }
                        setLockStorageTokenISSet(true);
                        printMessage(Constants.CMD_NAME_0xE4 + " status report end");
                    } else {
                        printMessage(Constants.CMD_NAME_0xE4 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquireToken:
                if (checkCommandIncome(commandPackage,CodeUtils.InquireToken)){
                    byte[] payload = commandPackage.getData();
                    int index = Integer.parseInt(getCurrentCommandStep());
                    printMessage( "index:" + index );
                    try {
                        SunionTokenStatus my_token = SunionTokenStatus.decodeTokenPayload(payload);
                        if (index >= 0 && index < 10){
                            setLockStorageToken(
                                    index,
                                    my_token
                            );
                        }
                        printMessage(Constants.CMD_NAME_0xE5 + " status report start");
                        printMessage(my_token.toString());
                        printMessage(Constants.CMD_NAME_0xE5 + " status report end");
                    } catch (Exception e) {
                        e.printStackTrace();
                        printMessage(Constants.CMD_NAME_0xE5 + e.getMessage());
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.NewOnceToken:
                if (checkCommandIncome(commandPackage,CodeUtils.NewOnceToken)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 10) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xE6 + " allow");
                            int i2 = payload[1] & 0xFF;
                            byte[] token = new byte[]{payload[2],payload[3],payload[4],payload[5],payload[6],payload[7],payload[8],payload[9]};
                            if (i2 >= 0 && i2 < 10) {
                                setLockStorageToken(
                                        i2,
                                        new SunionTokenStatus(
                                                true,
                                                true,
                                                token,
                                                getCurrentCommandStep().getBytes(StandardCharsets.US_ASCII)
                                        )
                                );
                                setLockStorageTokenISSet(false);
                            }
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xE6 + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xE6 + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xE6 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.ModifyToken:
                if (checkCommandIncome(commandPackage,CodeUtils.ModifyToken)){
                    int index = Integer.parseInt(getCurrentCommandStep());
                    printMessage( "index:" + index );
                    byte[] payload = commandPackage.getData();
                    if (payload.length >= 10) {
//                        1	1	是否可用 1:可用, 0:不可用
//                        2	1	是否是永久 Token 1:永久, 0:一次性
//                        3 ~10	8	Token
//                        11 ~	(最多 20 Byte)	Name
                        Boolean enable = (payload[0] == ((byte) 0x01)) ? true : false;
                        Boolean once_use = (payload[1] == ((byte) 0x00)) ? true : false;
                        byte[] token = new byte[]{payload[2],payload[3],payload[4],payload[5],payload[6],payload[7],payload[8],payload[9]};
                        byte[] name = new byte[payload.length - 10];
                        for(int i = 10 ; i < payload.length ; i++ ){
                            name[i-10] = payload[i];
                        }
                        if (index >= 0 && index < 10){
                            setLockStorageToken(
                                    index,
                                    new SunionTokenStatus(
                                            enable,
                                            once_use,
                                            token,
                                            name
                                    )
                            );
                        }
                        printMessage(Constants.CMD_NAME_0xE7 + " status report start");
                        printMessage( "enable:" + ( enable ? "true" : "false" ) );
                        printMessage( "once_use:" + ( once_use ? "true" : "false" ));
                        printMessage( "token:" + CodeUtils.bytesToHex(token));
                        printMessage( "name:" + new String(name,StandardCharsets.US_ASCII));
                        printMessage(Constants.CMD_NAME_0xE7 + " status report end");
                    } else {
                        printMessage(Constants.CMD_NAME_0xE7 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.DeleteToken:
                if (checkCommandIncome(commandPackage,CodeUtils.DeleteToken)){
                    int index = Integer.parseInt(getCurrentCommandStep());
                    printMessage( "index:" + index );
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xE8 + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xE8 + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xE8 + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xE8 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquirePinCodeArray:
                if (checkCommandIncome(commandPackage,CodeUtils.InquirePinCodeArray)){
                    byte[] payload = commandPackage.getData();
                    byte[] check_enable = new byte[]{(byte)0x01, (byte)0x02, (byte)0x04, (byte)0x08, (byte)0x10, (byte)0x20, (byte)0x40, (byte)0x80};
                    if (payload.length == 26) {
                        printMessage(Constants.CMD_NAME_0xEA + " status report start");
                        for(int i = 0 ; i < 26 ; i++){
                            int count  = 0;
                            for (byte check : check_enable) {
                                if ( (payload[i] & check) == check ) {
                                    int index = (i * 8 + count);
                                    setLockStoragePincode(index,new SunionPincodeStatus(true));
                                    printMessage(Constants.CMD_NAME_0xEA + " PinCode[" + index + "] is enable" );
                                }
                                count++;
                            }
                        }
                        setLockStoragePincodeISSet(true);
                        printMessage(Constants.CMD_NAME_0xEA + " status report end");
                    } else {
                        printMessage(Constants.CMD_NAME_0xEA + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.InquirePinCode:
                if (checkCommandIncome(commandPackage,CodeUtils.InquirePinCode)){
                    int index = Integer.parseInt(getCurrentCommandStep());
                    byte[] payload = commandPackage.getData();
                    if (payload.length >= 14) {
                        printMessage(Constants.CMD_NAME_0xEB + " status report start");
                        try {
                            SunionPincodeStatus pincode = SunionPincodeStatus.decodePincodePayload(payload);
                            setLockStoragePincode(index,pincode);
                            printMessage(Constants.CMD_NAME_0xEB + " PinCode[" + index + "] data:\n" + pincode.toString() );
                        } catch (Exception e) {
                            e.printStackTrace();
                            printMessage(Constants.CMD_NAME_0xEB + " PinCode[" + index + "] data decode fail got message " + e.getMessage());
                        }
                        printMessage(Constants.CMD_NAME_0xEB + " status report end");
                    } else {
                        printMessage(Constants.CMD_NAME_0xEB + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.NewAdminPinCode:
                if (checkCommandIncome(commandPackage,CodeUtils.NewAdminPinCode)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xC7 + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xC7 + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xC7 + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xC7 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.ModifyAdminPinCode:
                if (checkCommandIncome(commandPackage,CodeUtils.ModifyAdminPinCode)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xC8 + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xC8 + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xC8 + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xC8 + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.NewPinCode:
                if (checkCommandIncome(commandPackage,CodeUtils.NewPinCode)){
                    int index = Integer.parseInt(getCurrentCommandStep());
                    printMessage( "index:" + index );
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xEC + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xEC + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xEC + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xEC + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.ModifyPinCode:
                if (checkCommandIncome(commandPackage,CodeUtils.ModifyPinCode)){
                    int index = Integer.parseInt(getCurrentCommandStep());
                    printMessage( "index:" + index );
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xED + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xED + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xED + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xED + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.DeletePinCode:
                if (checkCommandIncome(commandPackage,CodeUtils.DeletePinCode)){
                    int index = Integer.parseInt(getCurrentCommandStep());
                    printMessage( "index:" + index );
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xEE + " allow");
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xEE + " reject");
                        } else {
                            printMessage(Constants.CMD_NAME_0xEE + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xEE + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            case CodeUtils.HaveMangerPinCode:
                if (checkCommandIncome(commandPackage,CodeUtils.HaveMangerPinCode)){
                    byte[] payload = commandPackage.getData();
                    if (payload.length == 1) {
                        if ( payload[0] == (byte) 0x01 ) {
                            printMessage(Constants.CMD_NAME_0xEF + " have");
                            setLockStoragePincodeAdminRequire(false);
                        } else if ( payload[0] == (byte) 0x00 ) {
                            printMessage(Constants.CMD_NAME_0xEF + " not have");
                            setLockStoragePincodeAdminRequire(true);
                        } else {
                            printMessage(Constants.CMD_NAME_0xEF + " unknown return : " + CodeUtils.bytesToHex(payload));
                        }
                    } else {
                        printMessage(Constants.CMD_NAME_0xEF + " unknown return (size not match doc) : " + CodeUtils.bytesToHex(payload));
                    }
                    resetCommandState();
                }
                break;
            default:
                if (commandPackage.getCommand() == CodeUtils.InquireLockState) {  // default action.
                    SunionLockStatus status =  SunionLockStatus.decodeLockStatusPayload(commandPackage.getData());
                    if (status.getDeadBolt() == SunionLockStatus.DEAD_BOLT_LOCK) {
                        printMessage("state machine default : " + "Deadbolt state is lock");
                    } else if (status.getDeadBolt() == SunionLockStatus.DEAD_BOLT_UNLOCK) {
                        printMessage("state machine default : " + "Deadbolt state is unlock");
                    } else {
                        printMessage("state machine default : " + "Deadbolt state is unknown");
                    }
                }
//                listener.onSerialRead("wait command".getBytes());
                // not to do.
                break;
        }
    }
}
