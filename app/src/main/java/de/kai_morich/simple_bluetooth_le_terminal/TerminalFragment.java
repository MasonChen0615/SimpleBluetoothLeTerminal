package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.kai_morich.simple_bluetooth_le_terminal.payload.SunionLockStatus;
import de.kai_morich.simple_bluetooth_le_terminal.payload.SunionPincodeSchedule;
import de.kai_morich.simple_bluetooth_le_terminal.payload.SunionPincodeStatus;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;
    private Spinner mSpn;
    private PopupWindow popupWindow;
    private Button command_button, btnConfirm;
    private Menu menu;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = true;
    private boolean pendingNewline = false;
//    private String newline = TextUtil.newline_crlf;
    private String newline = "";

    private final String NAME = "name";
    private final String CREATION = "creation";
    private String current_command_select = "";
    private int current_command_select_position = 0;
    private byte current_command = CodeUtils.Command_Initialization_Code;
    private String command_step = CodeUtils.Command_Initialization;
    private boolean wish_set_lock_state = true;
//    private int current_get_log_index = -1;
    private int get_log_index = 0;
    private int storage_token_index = 0;
    private int storage_pincode_index = 0;
    ArrayList<HashMap<String, String>> leaders;

    private int wait_reconnection_delay = 0;

    synchronized private int getReconnectionDelay(){
        return wait_reconnection_delay;
    }

    synchronized private void setReconnectionDelay(int delay){
        wait_reconnection_delay = delay;
    }

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    private void initPopupWindow() {
        View view = LayoutInflater.from(this.getContext()) .inflate(R.layout.popupwindow_layout, null);
        popupWindow = new PopupWindow(view); popupWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT); popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        TextView command_setting_head = (TextView) view.findViewById(R.id.command_setting_head);
        command_setting_head.setText(leaders.get(current_command_select_position).get(NAME) + " " + getResources().getString(R.string.CommandArgs));
        TextView[] command_arg = {
                (TextView) view.findViewById(R.id.command_arg_1),
                (TextView) view.findViewById(R.id.command_arg_2),
                (TextView) view.findViewById(R.id.command_arg_3),
                (TextView) view.findViewById(R.id.command_arg_4),
                (TextView) view.findViewById(R.id.command_arg_5)
        };
        EditText[] edit_command_arg = {
                (EditText) view.findViewById(R.id.edit_command_arg_1),
                (EditText) view.findViewById(R.id.edit_command_arg_2),
                (EditText) view.findViewById(R.id.edit_command_arg_3),
                (EditText) view.findViewById(R.id.edit_command_arg_4),
                (EditText) view.findViewById(R.id.edit_command_arg_5)
        };
        LinearLayout[] args_group = {
                (LinearLayout) view.findViewById(R.id.group_command_arg_1),
                (LinearLayout) view.findViewById(R.id.group_command_arg_2),
                (LinearLayout) view.findViewById(R.id.group_command_arg_3),
                (LinearLayout) view.findViewById(R.id.group_command_arg_4),
                (LinearLayout) view.findViewById(R.id.group_command_arg_5),
        };
        Spinner[] dy_mSpn = {
                new Spinner(this.getContext()),
                new Spinner(this.getContext()),
                new Spinner(this.getContext()),
                new Spinner(this.getContext()),
                new Spinner(this.getContext()),
        };
        for(int i = 0; i < dy_mSpn.length ; i++){
            dy_mSpn[i].setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int position, long id)
                {
                    // TODO Auto-generated method stub
                    Log.i(Constants.DEBUG_TAG,"command args selected:" + parent.getItemAtPosition(position).toString());
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0)
                {
                    // TODO Auto-generated method stub
                }
            });
            args_group[i].addView(dy_mSpn[i]);
        }
        for(int i = 0 ; i < 5 ; i++){
            command_arg[i].setVisibility(View.GONE);
            edit_command_arg[i].setVisibility(View.GONE);
            edit_command_arg[i].setText("");
            edit_command_arg[i].setInputType(InputType.TYPE_CLASS_TEXT);
            dy_mSpn[i].setVisibility(View.GONE);
        }
        switch(current_command_select_position){
            case Constants.CMD_0xC0:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xC0_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xC0_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_NULL);
                break;
            case Constants.CMD_0xC1:
                break;
            case Constants.CMD_0xCC:
                break;
            case Constants.CMD_0xCE:
                break;
            case Constants.CMD_0xD0:
                break;
            case Constants.CMD_0xD1:
                break;
            case Constants.CMD_0xD2:
                break;
            case Constants.CMD_0xD3:
                break;
            case Constants.CMD_0xD4:
                break;
            case Constants.CMD_0xD5:
                break;
            case Constants.CMD_0xD6:
                break;
            case Constants.CMD_0xD7:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xD7_Arg0);
                String[] names = getResources().getStringArray(R.array.command_arg_D7);
                String[] creation = getResources().getStringArray(R.array.command_arg_D7);
                ArrayList<String> leaders_D7 = new ArrayList<String>();
                for(int i = 0; i < names.length; i++){
                    leaders_D7.add(names[i]);
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this.getContext().getApplicationContext(),  R.layout.command_args_spinner_item, leaders_D7);
                adapter.setDropDownViewResource( R.layout.command_args_spinner_item);
                dy_mSpn[0].setAdapter(adapter);
                dy_mSpn[0].setVisibility(View.VISIBLE);
                break;
            case Constants.CMD_0xE0:
                break;
            case Constants.CMD_0xE1:
                break;
            case Constants.CMD_0xE2:
                break;
            case Constants.CMD_0xE3:
                break;
            case Constants.CMD_0xE4:
                break;
            case Constants.CMD_0xE5:
                break;
            case Constants.CMD_0xE6:
                break;
            case Constants.CMD_0xE7:
                break;
            case Constants.CMD_0xE8:
                break;
            case Constants.CMD_0xE9:
                break;
            case Constants.CMD_0xEA:
                break;
            case Constants.CMD_0xEB:
                break;
            case Constants.CMD_0xEC:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xEC_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xEC_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_NUMBER);
                break;
            case Constants.CMD_0xED:
                break;
            case Constants.CMD_0xEE:
                break;
            case Constants.CMD_0xEF:
                break;
            default:
                break;
        }
        btnConfirm = (Button) view.findViewById(R.id.btnConform);
        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.btnConform:
                        popupWindow.dismiss();
                        break;
                }
            }
        });
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");

        Spinner mSpn = (Spinner) view.findViewById(R.id.CommandSpinner);
        mSpn.setOnItemSelectedListener(spnOnItemSelected);

        String[] names = getResources().getStringArray(R.array.command_names);
        String[] creation = getResources().getStringArray(R.array.command_values);
        leaders = new ArrayList<HashMap<String, String>>();
        for(int i = 0; i < names.length; i++){
            HashMap<String, String> leader = new HashMap<String, String>();
            leader.put(NAME, names[i]);
            leader.put(CREATION, creation[i]);
            leaders.add(leader);
        }

        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        command_button= (Button)view.findViewById(R.id.Command);
        command_button.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view){ //實作onLongClick介面定義的方法
                //        popupWindow
                initPopupWindow();
                popupWindow.showAtLocation(view, Gravity.CENTER_HORIZONTAL, 0, 0);
                Log.i(Constants.DEBUG_TAG,"onLongClick:" + leaders.get(current_command_select_position).get(NAME));
                return true;
            }
        });
        command_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendText.setText("");
                resetState();
                byte[] tmp;
                byte[] data;
                int random_name;
                String test_name;
                Log.i(Constants.DEBUG_TAG,"command prepare:" + leaders.get(current_command_select_position).get(NAME));
                switch(current_command_select_position){
                    case Constants.CMD_0xC0:  // 連線亂數
                        commandC0();
                        break;
                    case Constants.CMD_0xC1:  // 連線 Token
                        readToken();
                        SunionToken token  = service.getSecretLockToken();
                        if (token.getToken().length != 0){
                            SpannableStringBuilder spn = new SpannableStringBuilder("Using Token"+'\n');
                            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            receiveText.append(spn);
//                            commandC1(token);
                            commandWithStep(
                                    CodeUtils.Connect,
                                    CodeUtils.Connect_UsingTokenConnect,
                                    (byte)token.getToken().length,
                                    token.getToken()
                            );
                        }else{
                            SpannableStringBuilder spn = new SpannableStringBuilder("Using Once Token"+'\n');
                            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            receiveText.append(spn);
//                            commandC1UsingOnce();
                            commandWithStep(
                                    CodeUtils.Connect,
                                    CodeUtils.Connect_UsingOnceTokenConnect,
                                    (byte)service.lock_token.length(),
                                    service.lock_token.getBytes()
                            );
                        }
                        break;
                    case Constants.CMD_0xCC:
                        commandNormal(CodeUtils.DirectionCheck,(byte) 0x00,new byte[]{});
                        break;
                    case Constants.CMD_0xCE:
                        commandNormal(CodeUtils.FactoryReset,(byte) 0x00,new byte[]{});
                        break;
                    case Constants.CMD_0xD0:
                        commandNormal(CodeUtils.InquireLockName,(byte) 0x00,new byte[]{});
                        break;
                    case Constants.CMD_0xD1:
                        random_name = new Random().nextInt((999 - 100) + 1) + 100;
                        test_name = "Name-" + random_name;
                        data = test_name.getBytes();
                        commandNormal(CodeUtils.SetLockName,(byte) data.length,data);
                        break;
                    case Constants.CMD_0xD2:
                        commandNormal(CodeUtils.InquireLockTime,(byte) 0x00,new byte[]{});
                        break;
                    case Constants.CMD_0xD3:
                        Long tsLong = System.currentTimeMillis()/1000;
                        data = CodeUtils.intToLittleEndian(tsLong);
                        commandNormal(CodeUtils.SetLockTime,(byte) data.length,data);
                        break;
                    case Constants.CMD_0xD4:
                        commandNormal(CodeUtils.InquireLockConfig,(byte) 0x00,new byte[]{});
                        break;
                    case Constants.CMD_0xD5:
                        data = new byte[5];
//                        1	1	鎖體方向 0xA0:右鎖, 0xA1:左鎖, 0xA2:未知 , Other 0xA3 忽視(建議)
//                        2	1	聲音 1:開啟, 0:關閉
//                        3	1	假期模式 1:開啟, 0:關閉
//                        4	1	自動上鎖 1:開啟, 0:關閉
//                        5	1	自動上鎖時間 10~99
                        int random_autolock_delay = new Random().nextInt((99 - 10) + 1) + 10;
                        data[0] = SunionLockStatus.LOCK_STATUS_NOT_TO_DO;
                        data[1] = new Random().nextBoolean() ? (byte)0x01 : (byte)0x00;
                        data[2] = new Random().nextBoolean() ? (byte)0x01 : (byte)0x00;
                        data[3] = new Random().nextBoolean() ? (byte)0x01 : (byte)0x00;
                        data[4] = (byte) random_autolock_delay;
                        String notice = "鎖體方向:忽視,";
                        notice += " 聲音:" + CodeUtils.bytesToHex(new byte[]{data[1]});
                        notice += " 假期模式:" + CodeUtils.bytesToHex(new byte[]{data[2]});
                        notice += " 自動上鎖:" + CodeUtils.bytesToHex(new byte[]{data[3]});
                        notice += " 自動上鎖時間:" + CodeUtils.bytesToHex(new byte[]{data[4]});
                        popNotice(notice);
                        commandNormal(CodeUtils.SetLockConfig,(byte) data.length,data);
                        break;
                    case Constants.CMD_0xD6:
                        commandNormal(CodeUtils.InquireLockState,(byte) 0x00,new byte[]{});
                        break;
                    case Constants.CMD_0xD7:
                        data = new byte[1];
                        data[0] = wish_set_lock_state ? (byte) 0x01 : (byte) 0x00 ;
                        commandNormal(CodeUtils.SetLockState,(byte) data.length,data);
                        wish_set_lock_state = !wish_set_lock_state;
                        break;
                    case Constants.CMD_0xE0:
                        commandNormal(CodeUtils.InquireLogCount,(byte) 0x00,new byte[]{});
                        break;
                    case Constants.CMD_0xE1:
                        if (service.getLockLogCurrentNumber() < 0) {
                            popNotice("you need to run 0xE0 InquireLogCount to get total log size");
                        } else {
                            data = new byte[1];
                            data[0] = (byte) getLogIndex();
                            commandNormal(CodeUtils.InquireLog,(byte) data.length,data);
                        }
                        break;
                    case Constants.CMD_0xE2:
                        if (service.getLockLogCurrentNumber() < 0) {
                            popNotice("you need to run 0xE0 InquireLogCount to get total log size");
                        } else {
                            data = new byte[1];
//                            data[0] = (byte) getLogIndex();
                            // is count not index. one click delete one log.
                            data[0] = (byte) 0x01;
                            commandNormal(CodeUtils.DeleteLog,(byte) data.length,data);
                        }
                        break;
//                    case Constants.CMD_0xE3:
//                        break;
                    case Constants.CMD_0xE4:
                        commandNormal(CodeUtils.InquireTokenArray,(byte) 0x00,new byte[]{});
                        storage_token_index = 0;
                        break;
                    case Constants.CMD_0xE5:
                        if (!service.getLockStorageTokenISSet()) {
                            popNotice("you need to run 0xE4 InquireTokenArray to get token status");
                        } else {
                            data = new byte[1];
                            int run_storage_token_index = getStorageTokenIndex();
                            data[0] = (byte) run_storage_token_index;
                            popNotice("Token index is " + ((int)(data[0] & (byte)0xff)));
                            commandWithStep(
                                    CodeUtils.InquireToken,
                                    run_storage_token_index + "",
                                    (byte)data.length,
                                    data
                            );
                        }
                        break;
                    case Constants.CMD_0xE6:
                        if (!service.getLockStorageTokenISSet()) {
                            popNotice("you need to run 0xE4 InquireTokenArray to get token status");
                        } else {
                            random_name = new Random().nextInt((999 - 100) + 1) + 100;
                            test_name = "New Once Token-" + random_name ;
                            commandWithStep(
                                    CodeUtils.NewOnceToken,
                                    test_name,
                                    (byte)test_name.length(),
                                    test_name.getBytes()
                            );
                        }
                        break;
                    case Constants.CMD_0xE7:
                        if (!service.getLockStorageTokenISSet()) {
                            popNotice("you need to run 0xE4 InquireTokenArray to get token status");
                        } else {
                            random_name = new Random().nextInt((999 - 100) + 1) + 100;
                            test_name = "Modify Token-" + random_name ;
                            tmp = test_name.getBytes();
                            data = new byte[test_name.length() + 1];
                            data[0] = (byte) getStorageTokenIndex();
                            popNotice("Modify Token index is " + ((int)(data[0] & (byte)0xff)));
                            for ( int i = 1 ; i < data.length ; i++ ) {
                                data[i] = tmp[i-1];
                            }
                            commandWithStep(
                                    CodeUtils.ModifyToken,
                                    ((int)(data[0] & (byte)0xff)) + "",
                                    (byte)data.length,
                                    data
                            );
                        }
                        break;
                    case Constants.CMD_0xE8:
                        if (!service.getLockStorageTokenISSet()) {
                            popNotice("you need to run 0xE4 InquireTokenArray to get token status");
                        } else {
                            data = new byte[1];
                            data[0] = (byte) getStorageTokenIndex();
                            popNotice("Delete Token index is " + ((int)(data[0] & (byte)0xff)));
                            commandWithStep(
                                    CodeUtils.DeleteToken,
                                    ((int)(data[0] & (byte)0xff)) + "",
                                    (byte)data.length,
                                    data
                            );
                        }
                        break;
//                    case Constants.CMD_0xE9:
//                        break;
                    case Constants.CMD_0xEA:
                        commandNormal(CodeUtils.InquirePinCodeArray,(byte) 0x00,new byte[]{});
                        storage_pincode_index = 0;
                        break;
                    case Constants.CMD_0xEB:
                        if (!service.getLockStoragePincodeISSet()) {
                            popNotice("you need to run 0xEA InquirePinCodeArray to get pincode status");
                        } else {
                            data = new byte[1];
                            data[0] = (byte) getStoragePincodeIndex();
                            popNotice("PinCode index is " + ((int)(data[0] & (byte)0xff)));
                            commandWithStep(
                                    CodeUtils.InquirePinCode,
                                    ((int)(data[0] & (byte)0xff)) + "",
                                    (byte)data.length,
                                    data
                            );
                        }
                        break;
                    case Constants.CMD_0xEC:
                        if (!service.getLockStoragePincodeISSet()) {
                            popNotice("you need to run 0xEA InquirePinCodeArray to get pincode status");
                        } else {
                            // TODO: Add ui to control.
//                            1	1	Index 0 ~ 200
//                            2	1	Enable 1:可使用, 0:不可使用
//                            3	1	PinCode長度 4 ~ 6
//                            4 ~	len	PinCode
//                            (5+len) ~ (16+len)	12	Schedule
//                            (17+len) ~	(最多 20 Byte)	Name
                            byte weekday = SunionPincodeSchedule.WEEK_MON | SunionPincodeSchedule.WEEK_TUE | SunionPincodeSchedule.WEEK_WED | SunionPincodeSchedule.WEEK_THUR | SunionPincodeSchedule.WEEK_FRI | SunionPincodeSchedule.WEEK_SAT | SunionPincodeSchedule.WEEK_SUN;
                            SunionPincodeStatus pincode = new SunionPincodeStatus(
                                    true,
                                    new byte[]{SunionPincodeStatus.PWD_1,SunionPincodeStatus.PWD_2,SunionPincodeStatus.PWD_3,SunionPincodeStatus.PWD_4},
                                    new SunionPincodeSchedule(
                                            SunionPincodeSchedule.WEEK_ROUTINE,
                                            weekday,
                                            SunionPincodeSchedule.WEEK_TIME_MIN,
                                            SunionPincodeSchedule.WEEK_TIME_MAX,
                                            SunionPincodeSchedule.SEEK_TIME_MIN,
                                            SunionPincodeSchedule.SEEK_TIME_MAX
                                    ),
                                    "Hello!".getBytes()
                            );
                            tmp = pincode.encodePincodePayload();
                            data = new byte[1+tmp.length];
                            data[0] = (byte) getStoragePincodeIndex();
                            popNotice("PinCode index is " + ((int)(data[0] & (byte)0xff)));
                            for (int i = 1 ; i < data.length ; i++) {
                                data[i] = tmp[i-1];
                            }
                            commandWithStep(
                                    CodeUtils.NewPinCode,
                                    ((int)(data[0] & (byte)0xff)) + "",
                                    (byte)data.length,
                                    data
                            );
                        }
                        break;
                    case Constants.CMD_0xED:
                        if (!service.getLockStoragePincodeISSet()) {
                            popNotice("you need to run 0xEA InquirePinCodeArray to get pincode status");
                        } else {
                            // TODO: Add ui to control.
                            byte weekday = SunionPincodeSchedule.WEEK_MON | SunionPincodeSchedule.WEEK_TUE | SunionPincodeSchedule.WEEK_WED | SunionPincodeSchedule.WEEK_THUR | SunionPincodeSchedule.WEEK_FRI | SunionPincodeSchedule.WEEK_SAT | SunionPincodeSchedule.WEEK_SUN;
                            SunionPincodeStatus pincode = new SunionPincodeStatus(
                                    true,
                                    new byte[]{SunionPincodeStatus.PWD_5,SunionPincodeStatus.PWD_6,SunionPincodeStatus.PWD_7,SunionPincodeStatus.PWD_8},
                                    new SunionPincodeSchedule(
                                            SunionPincodeSchedule.WEEK_ROUTINE,
                                            weekday,
                                            SunionPincodeSchedule.WEEK_TIME_MIN,
                                            SunionPincodeSchedule.WEEK_TIME_MAX,
                                            SunionPincodeSchedule.SEEK_TIME_MIN,
                                            SunionPincodeSchedule.SEEK_TIME_MAX
                                    ),
                                    "Modify Code".getBytes()
                            );
                            tmp = pincode.encodePincodePayload();
                            data = new byte[1+tmp.length];
                            data[0] = (byte) getStoragePincodeIndex();
                            popNotice("PinCode index is " + ((int)(data[0] & (byte)0xff)));
                            for (int i = 1 ; i < data.length ; i++) {
                                data[i] = tmp[i-1];
                            }
                            commandWithStep(
                                    CodeUtils.ModifyPinCode,
                                    ((int)(data[0] & (byte)0xff)) + "",
                                    (byte)data.length,
                                    data
                            );
                        }
                        break;
                    case Constants.CMD_0xEE:
                        if (!service.getLockStoragePincodeISSet()) {
                            popNotice("you need to run 0xEA InquirePinCodeArray to get pincode status");
                        } else {
                            data = new byte[1];
                            data[0] = (byte) getStoragePincodeIndex();
                            popNotice("PinCode index is " + ((int)(data[0] & (byte)0xff)));
                            commandWithStep(
                                    CodeUtils.DeletePinCode,
                                    ((int)(data[0] & (byte)0xff)) + "",
                                    (byte)data.length,
                                    data
                            );
                        }
                        break;
                    case Constants.CMD_0xEF:
                        commandNormal(CodeUtils.HaveMangerPinCode,(byte) 0x00,new byte[]{});
                        break;
                    default:
                        break;
                }

            }
        });
        return view;
    }

    private void resetState(){
        current_command = CodeUtils.Command_Initialization_Code;
        command_step = CodeUtils.Command_Initialization;
    }

    private void commandC0(){
        try {
            KeyGenerator keygen = KeyGenerator.getInstance("AES");
            keygen.init(128);
            SecretKey randomkey = keygen.generateKey();
            SecretKey key = new SecretKeySpec(service.lock_aes_key.getBytes(), 0, service.lock_aes_key.getBytes().length, "AES");
            byte[] command = CodeUtils.encodeAES(
                    key,
                    CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small,
                    CodeUtils.getCommandPackage(
                            CodeUtils.BLE_Connect,
                            (byte) randomkey.getEncoded().length,
                            randomkey.getEncoded(),
                            service.incrCommandIV()
                    )
            );
            byte[] message = CodeUtils.decodeAES(
                    key,
                    CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small,
                    command
            );
            sendText.setText(CodeUtils.bytesToHex(command));
            current_command = CodeUtils.BLE_Connect;
            service.sunionAppRandomAESKey(randomkey.getEncoded());
            Log.i(Constants.DEBUG_TAG,"prepare message in byte:" + CodeUtils.bytesToHex(command));
            Log.i(Constants.DEBUG_TAG,"prepare message aes decode in byte:" + CodeUtils.bytesToHex(message));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private void commandWithStep(byte cmd , String step , byte size , byte[] data){
        SecretKey key = service.getConnectionAESKey();
        if ( key != null ){
            byte[] command = CodeUtils.encodeAES(
                    key,
                    CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small,
                    CodeUtils.getCommandPackage(
                            cmd,
                            size,
                            data,
                            service.incrCommandIV()
                    )
            );
            current_command = cmd;
            command_step = step;
            sendText.setText(CodeUtils.bytesToHex(command));
        } else {
            errorToast("Missing Connection AES Key");
            sendText.setText("");
        }
    }

    private void commandNormal(byte cmd , byte size , byte[] data){
        SecretKey key = service.getConnectionAESKey();
        if ( key != null ){
            byte[] command = CodeUtils.encodeAES(
                    key,
                    CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small,
                    CodeUtils.getCommandPackage(
                            cmd,
                            size,
                            data,
                            service.incrCommandIV()
                    )
            );
            current_command = cmd;
            sendText.setText(CodeUtils.bytesToHex(command));
        } else {
            errorToast("Missing Connection AES Key");
            sendText.setText("");
        }
    }

    private int getLogIndex(){
        if ( get_log_index >= service.getLockLogCurrentNumber() ) {
            get_log_index = 0;
            return get_log_index;
        } else {
            return get_log_index++;
        }
    }

    private int getStorageTokenIndex(){
        if ( storage_token_index >= 10 ) {
            storage_token_index = 0;
            return storage_token_index;
        } else {
            return storage_token_index++;
        }
    }

    private int getStoragePincodeIndex(){
        if ( storage_pincode_index > 201 ) {
            storage_pincode_index = 0;
            return storage_pincode_index;
        } else {
            return storage_pincode_index++;
        }
    }

    private void popNotice(String message){
        Toast toast = Toast.makeText(this.getContext(), message , Toast.LENGTH_SHORT);
        toast.show();
    }

    private AdapterView.OnItemSelectedListener spnOnItemSelected = new AdapterView.OnItemSelectedListener()
    {
        @Override
        public void onItemSelected(AdapterView<?> parent, View v, int position, long id)
        {
            // TODO Auto-generated method stub
            current_command_select = parent.getItemAtPosition(position).toString();
            current_command_select_position = position;
            Log.i(Constants.DEBUG_TAG,"command selected:" + parent.getItemAtPosition(position).toString());
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0)
        {
            // TODO Auto-generated method stub
        }
    };

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
        this.menu = menu;
        setConnectStatusBtn(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            //hexEnabled = !hexEnabled;
            //sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else if (id == R.id.token) {
            deleteToken();
            popNotice("token delete");
            return true;
        } else if (id == R.id.reconnect) {
            int my_delay = getReconnectionDelay();
            if (my_delay > 0){
                popNotice("Wait cooldown in " + my_delay  + " sec");
                return true;
            }
            if (connected == Connected.False) {
                getActivity().runOnUiThread(this::connect);
            } else {
                if (connected == Connected.Pending) {
                    popNotice("Connection Pending");
                }
                if (connected == Connected.True) {
                    popNotice("Connection connected");
                }
            }
            return true;
        } else if (id == R.id.disconnect) {
            status("connection lost: Manual" );
            disconnect();
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        for(int i = 0 ; i < 10 ; i++){
                            sleep(1000);
                            setReconnectionDelay(9-i);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            thread.start();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void errorToast(String message){
        Toast toast = Toast.makeText(this.getContext(), "Error :" + message , Toast.LENGTH_SHORT);
        toast.show();
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            setConnectStatusBtn(true);
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        setConnectStatusBtn(false);
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        service.killWatchRead();
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                Log.i(Constants.DEBUG_TAG,"prepare message");
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
                Log.i(Constants.DEBUG_TAG,"message in byte:" + CodeUtils.bytesToHex(data));
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            if ( current_command != CodeUtils.Command_Initialization_Code && (data.length % 16 == 0) && data.length > 0 ) {
                SpannableStringBuilder spn = new SpannableStringBuilder(CodeUtils.bytesToHex(CodeUtils.decodeAES(service.getConnectionAESKey(),CodeUtils.AES_Cipher_DL02_H2MB_KPD_Small, data))+'\n');
                spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                receiveText.append(spn);
            } else {
                SpannableStringBuilder spn = new SpannableStringBuilder(msg+'\n');
                spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                receiveText.append(spn);
            }
            service.write(data,current_command,command_step);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        if (receiveText.length() > 5000) {
            CharSequence tmp = receiveText.getText();
            receiveText.setText(tmp.subSequence(receiveText.length()/2 , receiveText.length()-1));
        }
        if(hexEnabled) {
            if(CodeUtils.isBytesCanRead(data)){
                String s = new String(data);
                if (s.indexOf(Constants.EXCHANGE_MESSAGE_PREFIX) == 0) { // must in head
                    int prefix = Constants.EXCHANGE_MESSAGE_PREFIX.length();
                    String message = s.substring(prefix, s.length() - prefix);
                    SpannableStringBuilder spn = new SpannableStringBuilder(message+'\n');
                    spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    receiveText.append(spn);
                } else if (s.indexOf(Constants.EXCHANGE_LOCKTOKEN_PREFIX) == 0) {
                    saveToken(service.getSecretLockToken().getToken());
                    String token = new String(service.getSecretLockToken().getToken());
                    SpannableStringBuilder spn = new SpannableStringBuilder("receive token : " + token +'\n');
                    spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    receiveText.append(spn);
                } else if (s.indexOf(Constants.EXCHANGE_DATA_PREFIX) == 0) {
//                    int prefix = Constants.EXCHANGE_MESSAGE_PREFIX.length();
//                    String message = s.substring(prefix, s.length() - prefix);
//                    if (message.indexOf(Constants.EXCHANGE_DATA_0xE0_PREFIX) == 0) {
//                        int tag_prefix = Constants.EXCHANGE_DATA_0xE0_PREFIX.length();
//                        String base64 = message.substring(tag_prefix, message.length() - tag_prefix);
//                        try {
//                            JSONObject json = new JSONObject(CodeUtils.decodeBase64(base64));
//                            // try CodeUtils.InquireLogCount
//                                int InquireLogCount = json.getInt(Constants.CMD_NAME_0xE0);
//                                if (InquireLogCount >= 0) {
//                                    current_get_log_index = InquireLogCount;
//                                }
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//                    } else if (message.indexOf(Constants.EXCHANGE_DATA_0xE4_PREFIX) == 0) {
//                        int tag_prefix = Constants.EXCHANGE_DATA_0xE4_PREFIX.length();
//                    } else if (message.indexOf(Constants.EXCHANGE_DATA_0xEA_PREFIX) == 0) {
//                        int tag_prefix = Constants.EXCHANGE_DATA_0xEA_PREFIX.length();
//                    } else {
//                        // skip
//                    }
                } else if (s.indexOf(Constants.EXCHANGE_DATA_0xE0_PREFIX) == 0) {
//                    int prefix = Constants.EXCHANGE_DATA_0xE0_PREFIX.length();
                    // not need to do.
                } else {
                    SpannableStringBuilder spn = new SpannableStringBuilder(s+'\n');
                    spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    receiveText.append(spn);
                }
            }else{
                receiveText.append(TextUtil.toHexString(data) + '\n');
            }
        } else {
            String msg = new String(data);
            if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();
                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    private void saveToken(byte[] token){
        this.getContext().getSharedPreferences(CodeUtils.ConnectionTokenFileName, this.getContext().MODE_PRIVATE).edit().putString(CodeUtils.ConnectionTokenFileName,CodeUtils.bytesToHex(token)).commit();
    }

    private byte[] readToken(){
        String s = this.getContext().getSharedPreferences(CodeUtils.ConnectionTokenFileName, this.getContext().MODE_PRIVATE).getString(CodeUtils.ConnectionTokenFileName, "");
        if ( CodeUtils.isHexString(s) ){
            byte[] token = CodeUtils.hexStringToBytes(s);
            if (token.length > 0){
                service.setSecretLockToken(new SunionToken(1,token));
                return token;
            } else {
                return service.lock_token.getBytes();
            }
        } else {
            return service.lock_token.getBytes();
        }
    }

    private void deleteToken(){
        this.getContext().getSharedPreferences(CodeUtils.ConnectionTokenFileName, this.getContext().MODE_PRIVATE).edit().putString(CodeUtils.ConnectionTokenFileName,"").commit();
        service.setSecretLockToken(new SunionToken(0,new byte[]{}));
    }

    private void setConnectStatusBtn(Boolean status) {
        if (status) {
            menu.findItem(R.id.disconnect).setVisible(true);
            menu.findItem(R.id.disconnect).setEnabled(true);
            menu.findItem(R.id.reconnect).setVisible(false);
            menu.findItem(R.id.reconnect).setEnabled(false);
        } else {
            menu.findItem(R.id.disconnect).setVisible(false);
            menu.findItem(R.id.disconnect).setEnabled(false);
            menu.findItem(R.id.reconnect).setVisible(true);
            menu.findItem(R.id.reconnect).setEnabled(true);
        }
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
        setConnectStatusBtn(true);
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) { //Override listener onSerialRead for ui , can run handle in service.
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}
