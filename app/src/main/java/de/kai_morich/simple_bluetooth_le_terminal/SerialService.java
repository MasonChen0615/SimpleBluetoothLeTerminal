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

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Queue;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * create notification and queue serial data while activity is not in the foreground
 * use listener chain: SerialSocket -> SerialService -> UI fragment
 */
public class SerialService extends Service implements SerialListener {

    public String lock_token = "85121456";
    public String lock_aes_key = "SUNION_8512-6108";
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
        if(!connected)
            throw new IOException("not connected");
        socket.write(data);
        current_command = command;
    }

    private void setCommandStep(String step) throws IOException {
        Object someObject = new CodeUtils();
        Class<?> someClass = someObject.getClass();
        try {
            Field someField = someClass.getField(step);
            command_step = step;
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            throw new IOException("NoSuchFieldException in this command" + CodeUtils.bytesToHex(new byte[] { current_command }) + "" );
        }
    }

    private void ResetCommandStep(){
        command_step = CodeUtils.Command_Initialization;
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
                            if ((data.length % 16) == 0){
                                sunionCommandHandler(data);
                            }
                            listener.onSerialRead(data);
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
        this.app_random_aes_key = data;
    }

    public void sunionDeviceRandomAESKey(byte[] data){
        this.device_random_aes_key = data;
    }

    public byte[] sunionConnectionAESKey(){
        return xor(this.app_random_aes_key,this.device_random_aes_key);
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

    public int resetCommandIV(){
        synchronized (this) {
            this.command_iv = 1;
            return this.command_iv;
        }
    }

    private void resetCommandState(){
        this.current_command = CodeUtils.Command_Initialization_Code;
        this.command_step = CodeUtils.Command_Initialization;
    }

    private void sunionCommandHandler(byte[] data){
        SunionCommandPayload commandPackage;
        if (this.connection_aes_key == null){
            SecretKey key = new SecretKeySpec(this.lock_aes_key.getBytes(), 0, this.lock_aes_key.getBytes().length, "AES");
            commandPackage = CodeUtils.decodeCommandPackage(CodeUtils.decodeAES(key,CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small, data));
        } else {
            commandPackage = CodeUtils.decodeCommandPackage(CodeUtils.decodeAES(this.connection_aes_key,CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small, data));
        }
        switch(current_command){
            case CodeUtils.BLE_Connect:
                switch(command_step){
                    case CodeUtils.Command_Initialization:
                    case CodeUtils.Command_BLE_Connect_C0:
                        //TODO: decode data payload and xor c0 sent and receive aes key to new connection key.
                        this.sunionDeviceRandomAESKey(commandPackage.getData());
                        byte[] xorkey = this.sunionConnectionAESKey();
                        this.connection_aes_key = new SecretKeySpec(xorkey, 0, xorkey.length, "AES");
                        command_step = CodeUtils.Command_BLE_Connect_C1;
                        //TODO: send this.lock_token with this.connection_aes_key to device.
                        byte[] command = CodeUtils.encodeAES(
                                this.connection_aes_key,
                                CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small,
                                CodeUtils.getCommandPackage(
                                        CodeUtils.UsingOnceTokenConnect,
                                        (byte) this.lock_token.getBytes().length,
                                        this.lock_token.getBytes(),
                                        this.incrCommandIV()
                                )
                        );
                        try{
                            this.write(command,current_command);
                        } catch (Exception e) {
                            //command write fail, reset command step.
                            resetCommandState();
                            onSerialIoError(e);
                        }
                    case CodeUtils.Command_BLE_Connect_C1:
                        //TODO: receive C1 1 byte data return , receive token , receive lock status
                        switch(commandPackage.getCommand()){
                            case CodeUtils.InquireToken:
                                //TODO: receive C1 1 byte data return , receive token , save token.
                                resetCommandState();
                                break;
                            case CodeUtils.InquireLockState:
                                //TODO: lock statis in here , current_command state must release in CodeUtils.InquireToken.
                                break;
                        }
                    default:
                        //retry c0.
                        command_step = CodeUtils.Command_Initialization;
                        break;
                }
                break;
            default:
//                current_command = CodeUtils.Command_Initialization_Code;
//                command_step = CodeUtils.Command_Initialization;
                // not to do.
                break;
        }
    }
}
