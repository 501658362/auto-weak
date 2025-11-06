package top.chenyanjin;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.EditText;
import android.widget.Button;
import android.content.Intent;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.app.ActivityManager;
import android.widget.CheckBox;
import android.os.Handler;
import android.os.Looper;

public class MainActivity extends AppCompatActivity {

    private boolean isRunning = false;
    private BroadcastReceiver vibrateFinishReceiver;
    private Button btnStartStop; // 声明为成员变量
    private static final String TAG = "MainActivity";

    private BroadcastReceiver buttonUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("top.chenyanjin.UPDATE_BUTTON".equals(intent.getAction())) {
                String text = intent.getStringExtra("button_text");
                // 假设按钮变量名为 vibrateButton
                btnStartStop.setText(text);
            }
        }
    };

    private Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private long nextExecuteTimestamp = 0;
    private int currentInterval = 0;
    private int currentLoop = 0;
    private int currentCount = 0;
    private int currentUnitPos = 0;
    private int executedTimes = 0;

    private BroadcastReceiver vibrateNextReceiver; // 新增
    private boolean needResetButton = false; // 新增标志

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText editInterval = findViewById(R.id.edit_interval);
        EditText editCount = findViewById(R.id.edit_count);
        EditText editLoop = findViewById(R.id.edit_loop);
        EditText editToast = findViewById(R.id.edit_toast);
        Spinner spinnerUnit = findViewById(R.id.spinner_unit);
        TextView tvEstimate = findViewById(R.id.tv_estimate);
        btnStartStop = findViewById(R.id.btn_start_stop);
        CheckBox cbShake = findViewById(R.id.cb_shake); // 新增
        TextView tvNextTime = findViewById(R.id.tv_next_time); // 新增
        TextView tvCountdown = findViewById(R.id.tv_countdown); // 新增

        // 恢复参数加载
        loadParams(editInterval, editCount, spinnerUnit, editLoop, editToast, cbShake);

        // 每次进入页面时检查服务是否在运行，刷新按钮文案
        if (isVibrateServiceRunning()) {
            btnStartStop.setText("停止");
            isRunning = true;
        } else {
            btnStartStop.setText("开始");
            isRunning = false;
        }

        // 注册循环结束广播，只注册一次
        vibrateFinishReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                android.util.Log.d(TAG, "收到 VIBRATE_FINISH_UI 广播，自动停止任务 onReceive");
                runOnUiThread(() -> {
                    android.util.Log.d(TAG, "runOnUiThread 执行");
                    if (btnStartStop == null) {
                        btnStartStop = findViewById(R.id.btn_start_stop);
                        android.util.Log.d(TAG, "btnStartStop 重新获取引用: " + (btnStartStop != null));
                    }
                    if (btnStartStop != null) {
                        btnStartStop.setText("开始");
                        android.util.Log.d(TAG, "按钮文案已改为开始（自动停止）");
                        needResetButton = true;
                    } else {
                        android.util.Log.e(TAG, "btnStartStop 仍然为 null，无法修改文案");
                    }
                    isRunning = false;
                    stopVibrateService(); // 确保服务彻底停止
                    stopCountdown();
                    Toast.makeText(MainActivity.this, "循环已结束", Toast.LENGTH_SHORT).show();
                });
            }
        };
        // 注册新的 UI 广播
        registerReceiver(vibrateFinishReceiver, new IntentFilter("top.chenyanjin.VIBRATE_FINISH_UI"));

        // 新增：注册每次震动完成广播
        vibrateNextReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("top.chenyanjin.VIBRATE_NEXT".equals(intent.getAction())) {
                    long nextTime = intent.getLongExtra("next_execute_time", System.currentTimeMillis());
                    nextExecuteTimestamp = nextTime;
                    // 立即刷新倒计时显示
                    TextView tvNextTime = findViewById(R.id.tv_next_time);
                    TextView tvCountdown = findViewById(R.id.tv_countdown);
                    startCountdown(tvNextTime, tvCountdown);
                }
            }
        };
        registerReceiver(vibrateNextReceiver, new IntentFilter("top.chenyanjin.VIBRATE_NEXT"));

        // 注册按钮文案更新广播（移到onCreate，保证后台也能接收）
        IntentFilter filter = new IntentFilter("top.chenyanjin.UPDATE_BUTTON");
        registerReceiver(buttonUpdateReceiver, filter);

        // 预计停止时间逻辑
        TextWatcher watcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
                updateEstimate(editInterval, spinnerUnit, editLoop, tvEstimate);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        };
        editInterval.addTextChangedListener(watcher);
        editLoop.addTextChangedListener(watcher);
        spinnerUnit.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                updateEstimate(editInterval, spinnerUnit, editLoop, tvEstimate);
            }

            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        btnStartStop.setOnClickListener(v -> {
            android.util.Log.d(TAG, "按钮点击时 isRunning=" + isRunning);
            if (!isRunning) {
                // 恢复参数保存
                saveParams(editInterval, editCount, spinnerUnit, editLoop, editToast, cbShake);
                startVibrateService(editInterval, editCount, spinnerUnit, editLoop, editToast, cbShake);

                // 新增：初始化倒计时参数
                currentInterval = parseInt(editInterval.getText().toString(), 60);
                currentUnitPos = spinnerUnit.getSelectedItemPosition();
                if (currentUnitPos == 1) currentInterval = currentInterval * 60;
                currentLoop = parseInt(editLoop.getText().toString(), -1);
                executedTimes = 0;
                nextExecuteTimestamp = System.currentTimeMillis() + currentInterval * 1000L;
                startCountdown(tvNextTime, tvCountdown);

                btnStartStop.setText("停止");
                android.util.Log.d(TAG, "按钮文案已改为停止（手动开始）");
                isRunning = true;
            } else {
                stopVibrateService();
                btnStartStop.setText("开始");
                android.util.Log.d(TAG, "按钮文案已改为开始（手动停止）");
                isRunning = false;
                stopCountdown();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d(TAG, "onResume: needResetButton=" + needResetButton + ", isVibrateServiceRunning=" + isVibrateServiceRunning());
        // 优先处理自动结束标志，防止被服务状态覆盖
        if (needResetButton) {
            btnStartStop.setText("开始");
            android.util.Log.d(TAG, "onResume: 按钮文案已改为开始（自动结束标志）");
            isRunning = false;
            needResetButton = false;
        } else {
            // 只在服务真的运行时才设置为“停止”
            if (isVibrateServiceRunning()) {
                btnStartStop.setText("停止");
                android.util.Log.d(TAG, "onResume: 按钮文案已改为停止（服务运行）");
                isRunning = true;
            } else {
                btnStartStop.setText("开始");
                android.util.Log.d(TAG, "onResume: 按钮文案已改为开始（服务未运行）");
                isRunning = false;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 只注销 vibrateNextReceiver，不注销 vibrateFinishReceiver
        // if (vibrateFinishReceiver != null) {
        //     android.util.Log.d(TAG, "注销循环结束广播");
        //     unregisterReceiver(vibrateFinishReceiver);
        //     vibrateFinishReceiver = null;
        // }
        if (vibrateNextReceiver != null) {
            unregisterReceiver(vibrateNextReceiver);
            vibrateNextReceiver = null;
        }
        stopCountdown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 在 onDestroy 时注销 vibrateFinishReceiver
        if (vibrateFinishReceiver != null) {
            android.util.Log.d(TAG, "onDestroy: 注销循环结束广播");
            unregisterReceiver(vibrateFinishReceiver);
            vibrateFinishReceiver = null;
        }
        if (vibrateNextReceiver != null) {
            unregisterReceiver(vibrateNextReceiver);
            vibrateNextReceiver = null;
        }
        // 新增：注销按钮文案更新广播
        if (buttonUpdateReceiver != null) {
            unregisterReceiver(buttonUpdateReceiver);
        }
    }

    private void updateEstimate(EditText editInterval, Spinner spinnerUnit, EditText editLoop, TextView tvEstimate) {
        int interval = parseInt(editInterval.getText().toString(), 60);
        int unitPos = spinnerUnit.getSelectedItemPosition();
        if (unitPos == 1) interval *= 60;
        int loop = parseInt(editLoop.getText().toString(), -1);
        if (loop > 0) {
            long totalSec = (long) interval * loop;
            long now = System.currentTimeMillis();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
            String endTime = sdf.format(new java.util.Date(now + totalSec * 1000));
            tvEstimate.setText("预计停止时间：" + endTime);
        } else {
            tvEstimate.setText("预计停止时间：无限循环");
        }
    }

    // 新增：倒计时刷新逻辑
    private void startCountdown(TextView tvNextTime, TextView tvCountdown) {
        stopCountdown();
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long remain = (nextExecuteTimestamp - now) / 1000;
                if (remain < 0) remain = 0;
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
                tvNextTime.setText("下一次执行时间：" + sdf.format(new java.util.Date(nextExecuteTimestamp)));
                tvCountdown.setText("倒计时：" + remain + " 秒");
                countdownHandler.postDelayed(this, 1000);
            }
        };
        countdownHandler.post(countdownRunnable);
    }

    private void stopCountdown() {
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
    }

    private void startVibrateService(EditText editInterval, EditText editCount, Spinner spinnerUnit, EditText editLoop, EditText editToast, CheckBox cbShake) {
        int interval = parseInt(editInterval.getText().toString(), 60);
        int count = parseInt(editCount.getText().toString(), 1);
        int unitPos = spinnerUnit.getSelectedItemPosition();
        if (unitPos == 1) interval = interval * 60;
        int loop = parseInt(editLoop.getText().toString(), -1);
        String toastText = editToast.getText().toString();
        if (toastText.isEmpty()) toastText = "该翻身了";
        boolean shake = cbShake.isChecked();
        Intent intent = new Intent(this, VibrateService.class);
        intent.putExtra(VibrateService.EXTRA_INTERVAL, interval);
        intent.putExtra(VibrateService.EXTRA_COUNT, count);
        intent.putExtra(VibrateService.EXTRA_LOOP, loop);
        intent.putExtra(VibrateService.EXTRA_TOAST, toastText);
        intent.putExtra(VibrateService.EXTRA_SHAKE, shake);
        intent.putExtra("next_execute_time", System.currentTimeMillis() + interval * 1000L); // 新增
        intent.putExtra("interval", interval); // 新增
        intent.putExtra("loop", loop); // 新增
        startService(intent);
    }

    private void stopVibrateService() {
        Intent intent = new Intent(this, VibrateService.class);
        intent.setAction(VibrateService.ACTION_STOP);
        startService(intent);
    }

    private int parseInt(String s, int def) {
        try {
            int v = Integer.parseInt(s.replaceAll("[^0-9]", ""));
            return Math.max(v, 0); // 最小为0
        } catch (Exception e) {
            return Math.max(def, 0);
        }
    }

    // 判断服务是否在运行
    private boolean isVibrateServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (VibrateService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // 恢复参数保存方法
    private void saveParams(EditText editInterval, EditText editCount, Spinner spinnerUnit, EditText editLoop, EditText editToast, CheckBox cbShake) {
        android.content.SharedPreferences sp = getSharedPreferences("params", MODE_PRIVATE);
        sp.edit()
                .putString("interval", editInterval.getText().toString())
                .putString("count", editCount.getText().toString())
                .putInt("unit", spinnerUnit.getSelectedItemPosition())
                .putString("loop", editLoop.getText().toString())
                .putString("toast", editToast.getText().toString())
                .putBoolean("shake", cbShake.isChecked())
                .apply();
    }

    // 恢复参数加载方法
    private void loadParams(EditText editInterval, EditText editCount, Spinner spinnerUnit, EditText editLoop, EditText editToast, CheckBox cbShake) {
        android.content.SharedPreferences sp = getSharedPreferences("params", MODE_PRIVATE);
        editInterval.setText(sp.getString("interval", "60"));
        editCount.setText(sp.getString("count", "1"));
        spinnerUnit.setSelection(sp.getInt("unit", 0));
        editLoop.setText(sp.getString("loop", "-1"));
        editToast.setText(sp.getString("toast", ""));
        cbShake.setChecked(sp.getBoolean("shake", false));
    }
}