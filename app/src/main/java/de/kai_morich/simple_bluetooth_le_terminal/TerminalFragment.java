package de.kai_morich.simple_bluetooth_le_terminal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.kai_morich.simple_bluetooth_le_terminal.payload.SunionControlStatus;
import de.kai_morich.simple_bluetooth_le_terminal.payload.SunionLockStatus;
import de.kai_morich.simple_bluetooth_le_terminal.payload.SunionPincodeSchedule;
import de.kai_morich.simple_bluetooth_le_terminal.payload.SunionPincodeStatus;

import static android.content.Context.LOCATION_SERVICE;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener, LocationListener {

    private enum Connected {False, Pending, True}

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
    ArrayList<HashMap<String, String>> leaders;
    private SunionControlStatus command_args = new SunionControlStatus();

    private int wait_reconnection_delay = 0;
    private Boolean wait_connection_counter = false;
    private LocationManager mLocationManager;

    @Override
    public void onLocationChanged(final Location location) {
        //your code here
        command_args.config_status.setGeographicLocation(location.getLatitude(),location.getLongitude());
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    synchronized private int getReconnectionDelay() {
        return wait_reconnection_delay;
    }

    synchronized private void setReconnectionDelay(int delay) {
        wait_reconnection_delay = delay;
    }

    synchronized private void setWaitConnectionCounter(Boolean enable) {
        wait_connection_counter = enable;
    }

    synchronized private Boolean getWaitConnectionCounter() {
        return wait_connection_counter;
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

    private void scheduleDialogSeekEnd(){
        Calendar mCalendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(getActivity(), new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth){
                mCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mCalendar.set(Calendar.MONTH, month);
                mCalendar.set(Calendar.YEAR, year);
                SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd");
                popNotice(getString(R.string.time_end) + format.format(mCalendar.getTime()));
                TimePickerDialog pickerDialog = new TimePickerDialog(getActivity(), new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker arg0, int hour, int minite) {
                        mCalendar.set(Calendar.HOUR_OF_DAY, hour);
                        mCalendar.set(Calendar.MINUTE, minite);
                        SimpleDateFormat format = new SimpleDateFormat("HH:mm");
                        popNotice(getString(R.string.time_end) + format.format(mCalendar.getTime()));
                        command_args.schedule.seektime_end =  (int) (mCalendar.getTimeInMillis() / 1000);
                        command_args.schedule.encodePincodeSchedulePayload();
                        popNotice(command_args.schedule.toString());
                        Log.i(Constants.DEBUG_TAG,command_args.schedule.toString());
                    }
                }, mCalendar.get(Calendar.HOUR_OF_DAY), mCalendar.get(Calendar.MINUTE), true);
                pickerDialog.setTitle(getString(R.string.time_end));
                pickerDialog.show();
            }
        }, mCalendar.get(Calendar.YEAR),  mCalendar.get(Calendar.MONTH),  mCalendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.setTitle(getString(R.string.time_end));
        datePickerDialog.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void scheduleDialogSeek(){
        Calendar mCalendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(getActivity(), new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth){
                mCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mCalendar.set(Calendar.MONTH, month);
                mCalendar.set(Calendar.YEAR, year);
                SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd");
                popNotice(getString(R.string.time_start) + format.format(mCalendar.getTime()));
                TimePickerDialog pickerDialog = new TimePickerDialog(getActivity(), new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker arg0, int hour, int minite) {
                        mCalendar.set(Calendar.HOUR_OF_DAY, hour);//設定時間的另一種方式
                        mCalendar.set(Calendar.MINUTE, minite);
                        SimpleDateFormat format = new SimpleDateFormat("HH:mm");
                        popNotice(getString(R.string.time_start) + format.format(mCalendar.getTime()));
                        command_args.schedule.seektime_start =  (int) (mCalendar.getTimeInMillis() / 1000);
                        command_args.schedule.encodePincodeSchedulePayload();
                        scheduleDialogSeekEnd();
                    }
                }, mCalendar.get(Calendar.HOUR_OF_DAY), mCalendar.get(Calendar.MINUTE), true);
                pickerDialog.setTitle(getString(R.string.time_start));
                pickerDialog.show();
            }
        }, mCalendar.get(Calendar.YEAR),  mCalendar.get(Calendar.MONTH),  mCalendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.setTitle(getString(R.string.time_start));
        datePickerDialog.show();
    }

    private void scheduleDialogWeekTimeRangeEnd(){
        Calendar mCalendar = Calendar.getInstance();
        TimePickerDialog pickerDialog = new TimePickerDialog(getActivity(), new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker arg0, int hour, int minite) {
                mCalendar.set(Calendar.HOUR_OF_DAY, hour);
                mCalendar.set(Calendar.MINUTE, minite);
                SimpleDateFormat format = new SimpleDateFormat("HH:mm");
                popNotice(getString(R.string.time_end) + format.format(mCalendar.getTime()));
                Log.i(Constants.DEBUG_TAG,CodeUtils.bytesToHex(new byte[]{SunionPincodeSchedule.convertWeekTime(format.format(mCalendar.getTime()))}));
                command_args.schedule.setWeekTime(SunionPincodeSchedule.WEEK_TIME_END,SunionPincodeSchedule.convertWeekTime(format.format(mCalendar.getTime())));
                command_args.schedule.encodePincodeSchedulePayload();
                popNotice(command_args.schedule.toString());
                Log.i(Constants.DEBUG_TAG,command_args.schedule.toString());
            }
        }, mCalendar.get(Calendar.HOUR_OF_DAY), mCalendar.get(Calendar.MINUTE), true);
        pickerDialog.setTitle(getString(R.string.time_end));
        pickerDialog.show();
    }

    private void scheduleDialogWeekTimeRange(){
        Calendar mCalendar = Calendar.getInstance();
        TimePickerDialog pickerDialog = new TimePickerDialog(getActivity(), new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker arg0, int hour, int minite) {
                mCalendar.set(Calendar.HOUR_OF_DAY, hour);
                mCalendar.set(Calendar.MINUTE, minite);
                SimpleDateFormat format = new SimpleDateFormat("HH:mm");
                popNotice(getString(R.string.time_start) + format.format(mCalendar.getTime()));
                Log.i(Constants.DEBUG_TAG,CodeUtils.bytesToHex(new byte[]{SunionPincodeSchedule.convertWeekTime(format.format(mCalendar.getTime()))}));
                command_args.schedule.setWeekTime(SunionPincodeSchedule.WEEK_TIME_START,SunionPincodeSchedule.convertWeekTime(format.format(mCalendar.getTime())));
                command_args.schedule.encodePincodeSchedulePayload();
                scheduleDialogWeekTimeRangeEnd();
            }
        }, mCalendar.get(Calendar.HOUR_OF_DAY), mCalendar.get(Calendar.MINUTE), true);
        pickerDialog.setTitle(getString(R.string.time_start));
        pickerDialog.show();
    }

    private void scheduleDialogWeek(){
        ArrayList<Integer> slist = new ArrayList();
        boolean icount[] = new boolean[SunionPincodeSchedule.WEEK_NAME_GROUP.length];

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Week Setting")
                .setMultiChoiceItems(SunionPincodeSchedule.WEEK_NAME_GROUP,icount, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1, boolean arg2) {
                        if (arg2) {
                            // If user select a item then add it in selected items
                            slist.add(arg1);
                        } else if (slist.contains(arg1)) {
                            // if the item is already selected then remove it
                            slist.remove(Integer.valueOf(arg1));
                        }
                    }
                })
                .setCancelable(false)
                .setPositiveButton("Next", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String msg = "";
                        byte weekday = 0x00;
                        for (int i = 0; i < slist.size(); i++) {
                            msg = msg + "\n" + SunionPincodeSchedule.WEEK_NAME_GROUP[slist.get(i)];
                            weekday = (byte) (weekday | SunionPincodeSchedule.WEEK_GROUP[slist.get(i)]);
                        }
                        command_args.schedule.weekday = weekday;
                        command_args.schedule.encodePincodeSchedulePayload();
                        scheduleDialogWeekTimeRange();
                        dialog.dismiss();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void scheduleDialog0(){
        int default_item = 0;
        command_args.schedule.schedule_type = SunionPincodeSchedule.Schedule_Type_GROUP[default_item];
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Schedule type")
        .setSingleChoiceItems(SunionPincodeSchedule.Schedule_NAME_GROUP, default_item, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                command_args.schedule.schedule_type = SunionPincodeSchedule.Schedule_Type_GROUP[which];
            }
        }).setPositiveButton("Next", new DialogInterface.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch(command_args.schedule.schedule_type){
                    case SunionPincodeSchedule.ALL_DAY:
                    case SunionPincodeSchedule.ALL_DAY_DENY:
                    case SunionPincodeSchedule.ONCE_USE:
                        command_args.schedule.encodePincodeSchedulePayload();
                        break;
                    case SunionPincodeSchedule.WEEK_ROUTINE:
                        scheduleDialogWeek();
                        break;
                    case SunionPincodeSchedule.SEEK_TIME:
                        scheduleDialogSeek();
                        break;
                }
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private View.OnClickListener command_customer_func = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void initPopupWindow() {
        View view = LayoutInflater.from(this.getContext()) .inflate(R.layout.popupwindow_layout, null);
        popupWindow = new PopupWindow(view); popupWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT); popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        btnConfirm = (Button) view.findViewById(R.id.btnConform);

        View.OnClickListener btnConfirm_func = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.btnConform:
                        //default
                        command_customer_func.onClick(view);
                        command_click.onClick(view);
                        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        popupWindow.dismiss();
                        break;
                }
            }
        };
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
        Button[] arg_button = {
                new Button(this.getContext()),
                new Button(this.getContext()),
                new Button(this.getContext()),
                new Button(this.getContext()),
                new Button(this.getContext()),
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
            arg_button[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.i(Constants.DEBUG_TAG,"command args call sub pop windows:");
                }
            });
            args_group[i].addView(dy_mSpn[i]);
            args_group[i].addView(arg_button[i]);
        }
        for(int i = 0 ; i < 5 ; i++){
            command_arg[i].setVisibility(View.GONE);
            command_arg[i].setTextSize(16);
            edit_command_arg[i].setVisibility(View.GONE);
            edit_command_arg[i].setText("");
            edit_command_arg[i].setInputType(InputType.TYPE_CLASS_TEXT);
            edit_command_arg[i].setTextSize(16);
            dy_mSpn[i].setVisibility(View.GONE);
            arg_button[i].setTextSize(16);
            arg_button[i].setVisibility(View.GONE);
            arg_button[i].setGravity(Gravity.CENTER);
        }
        String[] names;
        ArrayList<String> leaders_String;
        ArrayAdapter<String> adapter_String;
        switch(current_command_select_position){
            case Constants.CMD_0xC0:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xC0_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xC0_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_NULL);
                break;
            case Constants.CMD_0xC1:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xC1_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xC1_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_NULL);
                break;
            case Constants.CMD_0xD1:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xD1_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xD1_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_TEXT);
                edit_command_arg[0].setText(command_args.getDeviceName(true));
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        command_args.setDeviceName(edit_command_arg[0].getText().toString());
                    }
                };
                break;
            case Constants.CMD_0xD3:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xD3_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xD3_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_NULL);
                break;
            case Constants.CMD_0xD5:
                names = getResources().getStringArray(R.array.command_arg_D5_arg0);
                leaders_String = new ArrayList<String>();
                for(int i = 0; i < names.length; i++){
                    leaders_String.add(names[i]);
                }
                adapter_String = new ArrayAdapter<String>(this.getContext().getApplicationContext(),  R.layout.command_args_spinner_item, leaders_String);
                adapter_String.setDropDownViewResource( R.layout.command_args_spinner_item);
                dy_mSpn[0].setAdapter(adapter_String);
                dy_mSpn[0].setVisibility(View.VISIBLE);
                dy_mSpn[0].setSelection(3);
                names = getResources().getStringArray(R.array.command_arg_common_boolean);
                leaders_String = new ArrayList<String>();
                for(int i = 0; i < names.length; i++){
                    leaders_String.add(names[i]);
                }
                for(int i = 0 ; i < 5 ; i++){
                    command_arg[i].setVisibility(View.VISIBLE);
                    if ( i < 4 && i > 0){
                        adapter_String = new ArrayAdapter<String>(this.getContext().getApplicationContext(),  R.layout.command_args_spinner_item, leaders_String);
                        adapter_String.setDropDownViewResource( R.layout.command_args_spinner_item);
                        dy_mSpn[i].setAdapter(adapter_String);
                        dy_mSpn[i].setVisibility(View.VISIBLE);
                    }
                }
                command_arg[0].setText(R.string.Command_CMD_0xD5_Arg0);
                command_arg[1].setText(R.string.Command_CMD_0xD5_Arg1);
                command_arg[2].setText(R.string.Command_CMD_0xD5_Arg2);
                dy_mSpn[2].setSelection(1);
                command_arg[3].setText(R.string.Command_CMD_0xD5_Arg3);
                command_arg[4].setText(R.string.Command_CMD_0xD5_Arg4);
                edit_command_arg[4].setVisibility(View.VISIBLE);
                edit_command_arg[4].setHint(R.string.Command_CMD_0xD5_Arg4_Message);
                edit_command_arg[4].setInputType(InputType.TYPE_CLASS_NUMBER);
                edit_command_arg[4].setText("30");
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        switch (dy_mSpn[0].getSelectedItemPosition()){
                            case 0:
                                command_args.config_status.lock_status = SunionLockStatus.LOCK_STATUS_RIGHT;
                                break;
                            case 1:
                                command_args.config_status.lock_status = SunionLockStatus.LOCK_STATUS_LEFT;
                                break;
                            case 2:
                                command_args.config_status.lock_status = SunionLockStatus.LOCK_STATUS_UNKNOWN;
                                break;
                            case 3:
                                command_args.config_status.lock_status = SunionLockStatus.LOCK_STATUS_NOT_TO_DO;
                                break;
                        }
                        for(int i = 1 ; i < 4 ; i++){
                            switch(i){
                                case 1:
                                    switch(dy_mSpn[i].getSelectedItemPosition()){
                                        case 0:
                                            command_args.config_status.keypress_beep = SunionLockStatus.COMMON_ON;
                                            break;
                                        case 1:
                                            command_args.config_status.keypress_beep = SunionLockStatus.COMMON_OFF;
                                            break;
                                    }
                                    break;
                                case 2:
                                    switch(dy_mSpn[i].getSelectedItemPosition()){
                                        case 0:
                                            command_args.config_status.vacation_mode = SunionLockStatus.COMMON_ON;
                                            break;
                                        case 1:
                                            command_args.config_status.vacation_mode = SunionLockStatus.COMMON_OFF;
                                            break;
                                    }
                                    break;
                                case 3:
                                    switch(dy_mSpn[i].getSelectedItemPosition()){
                                        case 0:
                                            command_args.config_status.autolock = SunionLockStatus.COMMON_ON;
                                            break;
                                        case 1:
                                            command_args.config_status.autolock = SunionLockStatus.COMMON_OFF;
                                            break;
                                    }
                                    break;
                            }
                        }
                        try {
                            command_args.config_status.setAutoLockDelay(Integer.parseInt(edit_command_arg[4].getText().toString()));
                        } catch (NumberFormatException e){
                            command_args.config_status.setAutoLockDelay(30);
                        }
                    }
                };
                break;
            case Constants.CMD_0xD7:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xD7_Arg0);
                names = getResources().getStringArray(R.array.command_arg_D7);
                leaders_String = new ArrayList<String>();
                for(int i = 0; i < names.length; i++){
                    leaders_String.add(names[i]);
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(this.getContext().getApplicationContext(),  R.layout.command_args_spinner_item, leaders_String);
                adapter.setDropDownViewResource( R.layout.command_args_spinner_item);
                dy_mSpn[0].setAdapter(adapter);
                dy_mSpn[0].setVisibility(View.VISIBLE);
                dy_mSpn[0].setSelection(0);
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        switch (dy_mSpn[0].getSelectedItemPosition()){
                            case 0:
                                command_args.config_status.dead_bolt = SunionLockStatus.DEAD_BOLT_LOCK;
                                break;
                            case 1:
                                command_args.config_status.dead_bolt = SunionLockStatus.DEAD_BOLT_UNLOCK;
                                break;
                        }
                    }
                };
                break;
            case Constants.CMD_0xE1:
                if (service.getLockLogCurrentNumber() >= 0) {
                    command_args.setLogCurrentNumber(service.getLockLogCurrentNumber());
                }
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xE1_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xE1_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_NUMBER);
                edit_command_arg[0].setText(command_args.getLogIndex(true)+"");
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            command_args.setLogIndex(Integer.parseInt(edit_command_arg[0].getText().toString()));
                        } catch (NumberFormatException e){
                            command_args.setLogIndex(0);
                        }
                    }
                };
                break;
            case Constants.CMD_0xE2:
                if (service.getLockLogCurrentNumber() >= 0) {
                    command_args.setLogCurrentNumber(service.getLockLogCurrentNumber());
                }
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xE2_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xE2_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_NUMBER);
                edit_command_arg[0].setText(command_args.getLogCount()+"");
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            command_args.setLogCount(Integer.parseInt(edit_command_arg[0].getText().toString()));
                        } catch (NumberFormatException e){
                            command_args.setLogCount(1);
                        }
                    }
                };
                break;
            case Constants.CMD_0xE5:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xE5_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xE5_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_NUMBER);
                edit_command_arg[0].setText(command_args.getTokenIndex(true)+"");
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            command_args.setTokenIndex(Integer.parseInt(edit_command_arg[0].getText().toString()));
                        } catch (NumberFormatException e){
                            command_args.setTokenIndex(0);
                        }
                    }
                };
                break;
            case Constants.CMD_0xE6:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xE6_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xE6_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_TEXT);
                edit_command_arg[0].setText(command_args.getRandomTokenName(SunionControlStatus.NEW_PREFIX));
                command_customer_func = new View.OnClickListener() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void onClick(View view) {
                        command_args.token.setTokenName(edit_command_arg[0].getText().toString());
                    }
                };
                break;
            case Constants.CMD_0xE7:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xE7_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xE7_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_NUMBER);
                edit_command_arg[0].setText(command_args.getTokenIndex(false)+"");
                command_arg[1].setVisibility(View.VISIBLE);
                command_arg[1].setText(R.string.Command_CMD_0xE7_Arg1);
                edit_command_arg[1].setVisibility(View.VISIBLE);
                edit_command_arg[1].setHint(R.string.Command_CMD_0xE7_Arg1_Message);
                edit_command_arg[1].setInputType(InputType.TYPE_CLASS_TEXT);
                edit_command_arg[1].setText(command_args.getRandomTokenName(SunionControlStatus.MODIFY_PREFIX));
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            command_args.setTokenIndex(Integer.parseInt(edit_command_arg[0].getText().toString()));
                        } catch (NumberFormatException e){
                            command_args.setTokenIndex(0);
                        }
                        command_args.token.setTokenName(edit_command_arg[1].getText().toString());
                    }
                };
                break;
            case Constants.CMD_0xE8:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xE8_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xE8_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_NUMBER);
                edit_command_arg[0].setText(command_args.getTokenIndex(false)+"");
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            command_args.setTokenIndex(Integer.parseInt(edit_command_arg[0].getText().toString()));
                        } catch (NumberFormatException e){
                            command_args.setTokenIndex(0);
                        }
                    }
                };
                break;
            case Constants.CMD_0xEB:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xEB_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xEB_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_NUMBER);
                edit_command_arg[0].setText(command_args.getPincodeIndex(true)+"");
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            command_args.setPincodeIndex(Integer.parseInt(edit_command_arg[0].getText().toString()));
                        } catch (NumberFormatException e){
                            command_args.setPincodeIndex(0);
                        }
                    }
                };
                break;
            case Constants.CMD_0xEC:
            case Constants.CMD_0xED:
                //Index
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xEC_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xEC_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_NUMBER);
                edit_command_arg[0].setText(command_args.getPincodeIndex(false)+"");
                //Enable
                command_arg[1].setVisibility(View.VISIBLE);
                command_arg[1].setText(R.string.Command_CMD_0xEC_Arg1);
                names = getResources().getStringArray(R.array.command_arg_common_boolean);
                leaders_String = new ArrayList<String>();
                for(int i = 0; i < names.length; i++){
                    leaders_String.add(names[i]);
                }
                adapter_String = new ArrayAdapter<String>(this.getContext().getApplicationContext(),  R.layout.command_args_spinner_item, leaders_String);
                adapter_String.setDropDownViewResource( R.layout.command_args_spinner_item);
                dy_mSpn[1].setAdapter(adapter_String);
                dy_mSpn[1].setVisibility(View.VISIBLE);
                //PinCode
                command_arg[2].setVisibility(View.VISIBLE);
                command_arg[2].setText(R.string.Command_CMD_0xEC_Arg2);
                edit_command_arg[2].setVisibility(View.VISIBLE);
                edit_command_arg[2].setHint(R.string.Command_CMD_0xEC_Arg2_Message);
                edit_command_arg[2].setInputType(InputType.TYPE_CLASS_NUMBER);
                edit_command_arg[2].setText(command_args.pincode.getReadablePincode());
                //Schedule
                command_arg[3].setVisibility(View.VISIBLE);
                command_arg[3].setText(R.string.Command_CMD_0xEC_Arg3);
                arg_button[3].setVisibility(View.VISIBLE);
                arg_button[3].setText(R.string.Command_CMD_0xEC_Arg3_Message);
                arg_button[3].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        scheduleDialog0();
                        Log.i(Constants.DEBUG_TAG,"command args call sub pop windows");
                    }
                });
                //Name
                command_arg[4].setVisibility(View.VISIBLE);
                command_arg[4].setText(R.string.Command_CMD_0xEC_Arg4);
                edit_command_arg[4].setVisibility(View.VISIBLE);
                edit_command_arg[4].setHint(R.string.Command_CMD_0xEC_Arg4_Message);
                edit_command_arg[4].setInputType(InputType.TYPE_CLASS_TEXT);
                if (current_command_select_position == Constants.CMD_0xEC){
                    edit_command_arg[4].setText(command_args.getRandomPincodeName(SunionControlStatus.NEW_PREFIX));
                } else {
                    edit_command_arg[4].setText(command_args.getRandomPincodeName(SunionControlStatus.MODIFY_PREFIX));
                }
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            command_args.setPincodeIndex(Integer.parseInt(edit_command_arg[0].getText().toString()));
                        } catch (NumberFormatException e){
                            command_args.setPincodeIndex(0);
                        }
                        command_args.pincode.enable  = (dy_mSpn[1].getSelectedItemPosition() == 0)? true : false;
                        command_args.pincode.setPincode(edit_command_arg[2].getText().toString());
                        command_args.pincode.schedule = command_args.schedule;
                        command_args.pincode.setName(edit_command_arg[4].getText().toString());
                    }
                };
                break;
            case Constants.CMD_0xEE:
                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_0xEE_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_0xEE_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_CLASS_NUMBER);
                edit_command_arg[0].setText(command_args.getPincodeIndex(false)+"");
                command_customer_func = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try {
                            command_args.setPincodeIndex(Integer.parseInt(edit_command_arg[0].getText().toString()));
                        } catch (NumberFormatException e){
                            command_args.setPincodeIndex(0);
                        }
                    }
                };
                break;
            case Constants.CMD_0xCC:
            case Constants.CMD_0xCE:
            case Constants.CMD_0xD0:
            case Constants.CMD_0xD2:
            case Constants.CMD_0xD4:
            case Constants.CMD_0xD6:
            case Constants.CMD_0xE0:
            case Constants.CMD_0xE4:
            case Constants.CMD_0xEA:
            case Constants.CMD_0xEF:
            default:
                if (service.getLockLogCurrentNumber() >= 0) {
                    command_args.setLogCurrentNumber(service.getLockLogCurrentNumber());
                }

                command_arg[0].setVisibility(View.VISIBLE);
                command_arg[0].setText(R.string.Command_CMD_Common_None_Arg0);
                edit_command_arg[0].setVisibility(View.VISIBLE);
                edit_command_arg[0].setHint(R.string.Command_CMD_Common_None_Arg0_Message);
                edit_command_arg[0].setInputType(InputType.TYPE_NULL);
                break;
        }
        btnConfirm.setOnClickListener(btnConfirm_func);
    }

    private View.OnClickListener command_click = new View.OnClickListener() {
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
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
                        commandWithStep(
                                CodeUtils.Connect,
                                CodeUtils.Connect_UsingOnceTokenConnect,
                                (byte)service.lock_token.getBytes(StandardCharsets.US_ASCII).length,
                                service.lock_token.getBytes(StandardCharsets.US_ASCII)
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
                    data = command_args.getDeviceName(false).getBytes(StandardCharsets.US_ASCII);
                    commandNormal(CodeUtils.SetLockName,(byte) data.length,data);
                    popNotice("lock name: " + command_args.getDeviceName(false));
                    break;
                case Constants.CMD_0xD2:
                    commandNormal(CodeUtils.InquireLockTime,(byte) 0x00,new byte[]{});
                    break;
                case Constants.CMD_0xD3:
                    data = command_args.getTime();
                    commandNormal(CodeUtils.SetLockTime,(byte) data.length,data);
                    int timestamp = CodeUtils.littleEndianToInt(data);
                    Date time = CodeUtils.convertDate(timestamp);
                    popNotice("lock time: " + time.toString());
                    break;
                case Constants.CMD_0xD4:
                    commandNormal(CodeUtils.InquireLockConfig,(byte) 0x00,new byte[]{});
                    break;
                case Constants.CMD_0xD5:
                    data = new byte[21];
                    data[0] = command_args.config_status.lock_status;
                    data[1] = command_args.config_status.keypress_beep;  // keypressbee
                    data[2] = command_args.config_status.vacation_mode;  // vacation mode
                    data[3] = command_args.config_status.autolock;
                    data[4] = command_args.config_status.autolock_delay;
                    Random r = new Random();
                    command_args.config_status.setGeographicLocation(
                        SunionLockStatus.LATITUDE_MIN + (SunionLockStatus.LATITUDE_MAX - SunionLockStatus.LATITUDE_MIN) * r.nextDouble(),
                        SunionLockStatus.LONGITUDE_MIN + (SunionLockStatus.LONGITUDE_MAX - SunionLockStatus.LONGITUDE_MIN) * r.nextDouble()
                    );
                    byte[] latitude  = command_args.config_status.getGeographicLocation(SunionLockStatus.LATITUDE);
                    byte[] longitude  = command_args.config_status.getGeographicLocation(SunionLockStatus.LONGITUDE);
                    for(int i = 5 ; i < 13 ; i++){
                        data[i] = latitude[i-5];
                    }
                    for(int i = 13 ; i < 21 ; i++){
                        data[i] = longitude[i-13];
                    }
                    popNotice(command_args.config_status.toString());
                    commandNormal(CodeUtils.SetLockConfig,(byte) data.length,data);
                    break;
                case Constants.CMD_0xD6:
                    commandNormal(CodeUtils.InquireLockState,(byte) 0x00,new byte[]{});
                    break;
                case Constants.CMD_0xD7:
                    data = new byte[1];
                    data[0] = command_args.config_status.dead_bolt;
                    commandNormal(CodeUtils.SetLockState,(byte) data.length,data);
                    popNotice((command_args.config_status.dead_bolt == SunionLockStatus.DEAD_BOLT_LOCK)?"lock":"unlock");
                    break;
                case Constants.CMD_0xE0:
                    commandNormal(CodeUtils.InquireLogCount,(byte) 0x00,new byte[]{});
                    break;
                case Constants.CMD_0xE1:
                    data = new byte[1];
                    data[0] = (byte)command_args.getLogIndex(false);
                    commandNormal(CodeUtils.InquireLog,(byte) data.length,data);
                    popNotice("log index :" + command_args.getLogIndex(false));
                    break;
                case Constants.CMD_0xE2:
                    data = new byte[1];
                    data[0] = (byte)command_args.getLogCount();
                    commandNormal(CodeUtils.DeleteLog,(byte) data.length,data);
                    popNotice("quantity be delete :" + command_args.getLogCount());
                    break;
                case Constants.CMD_0xE4:
                    commandNormal(CodeUtils.InquireTokenArray,(byte) 0x00,new byte[]{});
                    break;
                case Constants.CMD_0xE5:
                    data = new byte[1];
                    int run_storage_token_index = command_args.getTokenIndex(false);
                    data[0] = (byte) run_storage_token_index;
                    popNotice("Token index is " + ((int)(data[0] & (byte)0xff)));
                    commandWithStep(
                            CodeUtils.InquireToken,
                            run_storage_token_index + "",
                            (byte)data.length,
                            data
                    );
                    break;
                case Constants.CMD_0xE6:
                    test_name = new String (command_args.token.getTokenName(), StandardCharsets.US_ASCII) ;
                    popNotice("new once token name: " + test_name);
                    commandWithStep(
                            CodeUtils.NewOnceToken,
                            test_name,
                            (byte)test_name.getBytes(StandardCharsets.US_ASCII).length,
                            test_name.getBytes(StandardCharsets.US_ASCII)
                    );
                    break;
                case Constants.CMD_0xE7:
                    test_name = new String (command_args.token.getTokenName(), StandardCharsets.US_ASCII) ;
                    tmp = test_name.getBytes(StandardCharsets.US_ASCII);
                    data = new byte[test_name.length() + 1];
                    data[0] = (byte) command_args.getTokenIndex(false);
                    popNotice("modify token index is " + ((int)(data[0] & (byte)0xff)) + " and name: " + test_name);
                    for ( int i = 1 ; i < data.length ; i++ ) {
                        data[i] = tmp[i-1];
                    }
                    commandWithStep(
                            CodeUtils.ModifyToken,
                            ((int)(data[0] & (byte)0xff)) + "",
                            (byte)data.length,
                            data
                    );
                    break;
                case Constants.CMD_0xE8:
                    data = new byte[1];
                    data[0] = (byte) command_args.getTokenIndex(false);
                    popNotice("delete token index is " + ((int)(data[0] & (byte)0xff)));
                    commandWithStep(
                            CodeUtils.DeleteToken,
                            ((int)(data[0] & (byte)0xff)) + "",
                            (byte)data.length,
                            data
                    );
                    break;
                case Constants.CMD_0xEA:
                    commandNormal(CodeUtils.InquirePinCodeArray,(byte) 0x00,new byte[]{});
                    command_args.setPincodeIndex(0);
                    popNotice("auto reset pincode index to 0");
                    break;
                case Constants.CMD_0xEB:
                    data = new byte[1];
                    data[0] = (byte) command_args.getPincodeIndex(false);
                    popNotice("pincode index is " + ((int)(data[0] & (byte)0xff)));
                    commandWithStep(
                            CodeUtils.InquirePinCode,
                            ((int)(data[0] & (byte)0xff)) + "",
                            (byte)data.length,
                            data
                    );
                    break;
                case Constants.CMD_0xEC:
                    tmp = command_args.pincode.encodePincodePayload();
                    data = new byte[1+tmp.length];
                    data[0] = (byte) command_args.getPincodeIndex(false);
                    if (service.getLockStoragePincodeAdminRequire()){
                        popNotice("pincode admin require, pincode index is " + ((int)(data[0] & (byte)0xff)) + " and " + command_args.pincode.toString());
                    } else {
                        popNotice("create pincode index is " + ((int)(data[0] & (byte)0xff)) + " and " + command_args.pincode.toString());
                    }
                    for (int i = 1 ; i < data.length ; i++) {
                        data[i] = tmp[i-1];
                    }
                    commandWithStep(
                            CodeUtils.NewPinCode,
                            ((int)(data[0] & (byte)0xff)) + "",
                            (byte)data.length,
                            data
                    );
                    break;
                case Constants.CMD_0xED:
                    tmp = command_args.pincode.encodePincodePayload();
                    data = new byte[1+tmp.length];
                    data[0] = (byte) command_args.getPincodeIndex(false);
                    popNotice("modify pincode index is " + ((int)(data[0] & (byte)0xff)) + " and " + command_args.pincode.toString());
                    for (int i = 1 ; i < data.length ; i++) {
                        data[i] = tmp[i-1];
                    }
                    commandWithStep(
                            CodeUtils.ModifyPinCode,
                            ((int)(data[0] & (byte)0xff)) + "",
                            (byte)data.length,
                            data
                    );
                    break;
                case Constants.CMD_0xEE:
                    data = new byte[1];
                    data[0] = (byte) command_args.getPincodeIndex(false);
                    popNotice("delete pincode index is " + ((int)(data[0] & (byte)0xff)));
                    commandWithStep(
                            CodeUtils.DeletePinCode,
                            ((int)(data[0] & (byte)0xff)) + "",
                            (byte)data.length,
                            data
                    );
                    break;
                case Constants.CMD_0xEF:
                    commandNormal(CodeUtils.HaveMangerPinCode,(byte) 0x00,new byte[]{});
                    break;
                default:
                    break;
            }

        }
    };

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
        sendText.setInputType(InputType.TYPE_NULL);

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
                //        popupWindow for SchedulePopupWindow when first popupWindow click Schedule button.

                popupWindow.showAtLocation(view, Gravity.CENTER_HORIZONTAL, 0, 0);
                Log.i(Constants.DEBUG_TAG,"onLongClick:" + leaders.get(current_command_select_position).get(NAME));
                return true;
            }
        });

        command_button.setOnClickListener(command_click);
        return view;
    }

    private void resetState(){
        current_command = CodeUtils.Command_Initialization_Code;
        command_step = CodeUtils.Command_Initialization;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void commandC0(){
        try {
            KeyGenerator keygen = KeyGenerator.getInstance("AES");
            keygen.init(128);
            SecretKey randomkey = keygen.generateKey();
            SecretKey key = new SecretKeySpec(service.lock_aes_key.getBytes(StandardCharsets.US_ASCII), 0, service.lock_aes_key.getBytes(StandardCharsets.US_ASCII).length, "AES");
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

    private void popNotice(String message){
        Toast toast = Toast.makeText(this.getContext(), message , Toast.LENGTH_LONG);
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
                        for(int i = 0 ; i < Constants.CONNECT_RETRY_DELAY ; i++){
                            sleep(1000);
                            setReconnectionDelay((Constants.CONNECT_RETRY_DELAY-1)-i);
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

    synchronized private void sync_connect(){
        getActivity().runOnUiThread(this::connect);
    }

    synchronized private void sync_disconnect(){
        getActivity().runOnUiThread(this::disconnect);
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
            if(!getWaitConnectionCounter()){
                setWaitConnectionCounter(true);
                Thread thread = new Thread() {
                    @Override
                    public void run() {
                    try {
                        int count = 0;
                        if (Constants.AUTO_RETRY_CONNECTION) {
                            if (count > Constants.CONNECT_RETRY){
                                status("connection retry over " + Constants.CONNECT_RETRY);
                                sync_disconnect();
                            }
                        }
                        while(connected == Connected.Pending){
                            sleep(5000);
                            if (connected == Connected.Pending) {
                                status("connection time >" + (5 * (count+1)) + " sec");
                                count++;
                                if (Constants.AUTO_RETRY_CONNECTION) {
                                    sync_disconnect();
                                    sync_connect();
                                }
                            } else {
                                break;
                            }
                        }
                        setWaitConnectionCounter(false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    }
                };
                thread.start();
            }
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
                    // not need to do.
                } else if (s.indexOf(Constants.EXCHANGE_DATA_0xE0_PREFIX) == 0) {
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

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private byte[] readToken(){
        String s = this.getContext().getSharedPreferences(CodeUtils.ConnectionTokenFileName, this.getContext().MODE_PRIVATE).getString(CodeUtils.ConnectionTokenFileName, "");
        if ( CodeUtils.isHexString(s) ){
            byte[] token = CodeUtils.hexStringToBytes(s);
            if (token.length > 0){
                service.setSecretLockToken(new SunionToken(1,token));
                return token;
            } else {
                return service.lock_token.getBytes(StandardCharsets.US_ASCII);
            }
        } else {
            return service.lock_token.getBytes(StandardCharsets.US_ASCII);
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
