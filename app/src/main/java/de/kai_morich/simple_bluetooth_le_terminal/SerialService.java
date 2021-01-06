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
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
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
    public SunionToken secret_lock_token = new SunionToken(0,new byte[]{});
    private byte[] app_random_aes_key;
    private byte[] device_random_aes_key;
    private SecretKey connection_aes_key = null;
    private byte current_command = 0x00;
    private String command_step = CodeUtils.Command_Initialization;
    private int retry = 0;
    private int retry_wait = 0;
    private int command_iv = 1;

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
                            listener.onSerialRead(data);
                            if ((data.length % 16) == 0){
                                sunionCommandHandler(data);
                            }else{
                                printMessage("wait command");
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

    public SecretKey getConnectionAESKey(){
        synchronized (this) {
            if (this.connection_aes_key == null){
                SecretKey key = new SecretKeySpec(this.lock_aes_key.getBytes(), 0, this.lock_aes_key.getBytes().length, "AES");
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

    public void setSecretLockToken(SunionToken token){
        synchronized (this) {
            secret_lock_token = token;
        }
    }

    public SunionToken getSecretLockToken(){
        synchronized (this) {
            return secret_lock_token;
        }
    }

    public void printMessage(String message){
        String mymessage = Constants.EXCHANGE_MESSAGE_PREFIX + message + Constants.EXCHANGE_MESSAGE_PREFIX;
        listener.onSerialRead(message.getBytes());
    }

    public void exchangeToken(String token){
        String mytoken = Constants.EXCHANGE_LOCKTOKEN_PREFIX + token + Constants.EXCHANGE_LOCKTOKEN_PREFIX;
        listener.onSerialRead(mytoken.getBytes());
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
                if (commandPackage.getCommand() == CodeUtils.BLE_Connect) {
                    //decode data payload and xor c0 sent and receive aes key to new connection key.
                    this.sunionDeviceRandomAESKey(commandPackage.getData());
                    byte[] xorkey = this.sunionConnectionAESKey();
                    setConnectionAESKey(new SecretKeySpec(xorkey, 0, xorkey.length, "AES"));
                    resetCommandState();
                } else {
                    if (incrRetry() > CodeUtils.Retry){
                        resetCommandState();
                        Log.e(Constants.DEBUG_TAG, "release resetCommandState when receive message are not target");
                    }
                }
                break;
            case CodeUtils.Connect :  // same CodeUtils.UsingOnceTokenConnect
                switch(getCurrentCommandStep()){
                    case CodeUtils.Connect_UsingTokenConnect:
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
                                resetCommandState();
                                Log.i(Constants.DEBUG_TAG, "release resetCommandState where receive InquireToken in Connect_UsingTokenConnect at Connect");
                            }
                        } else {
                            if (incrRetry() > CodeUtils.Retry){
                                resetCommandState();
                                Log.e(Constants.DEBUG_TAG, "release resetCommandState when receive message are not target");
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
                            SunionToken tmp = SunionToken.decodeTokenPayload(payload);
                            setSecretLockToken(new SunionToken(1, tmp.getToken()));
                            exchangeToken("Exchange");
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
                if (commandPackage.getCommand() == CodeUtils.InquireLockState) {
                    SunionLockStatus status =  SunionLockStatus.decodeLockStatusPayload(commandPackage.getData());
                    if (status.getDeadBolt() == SunionLockStatus.DEAD_BOLT_LOCK) {
                        printMessage("Deadbolt state is lock");
                    } else if (status.getDeadBolt() == SunionLockStatus.DEAD_BOLT_UNLOCK) {
                        printMessage("Deadbolt state is unlock");
                    } else {
                        printMessage("Deadbolt state is unknown");
                    }
                    resetCommandState();
                } else {
                    if (incrRetry() > CodeUtils.Retry){
                        resetCommandState();
                        Log.e(Constants.DEBUG_TAG, "release resetCommandState when receive message are not target");
                    }
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
                listener.onSerialRead("wait command".getBytes());
                // not to do.
                break;
        }
    }
}
