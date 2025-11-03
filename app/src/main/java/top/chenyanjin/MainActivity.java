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
        Button btnApply = findViewById(R.id.btn_apply);

        // 新增：读取参数并填充
        loadParams(editInterval, editCount, spinnerUnit, editLoop, editToast);

        // 每次进入页面时检查服务是否在运行，刷新按钮文案
        if (isVibrateServiceRunning()) {
            btnStartStop.setText("停止");
            isRunning = true;
        } else {
            btnStartStop.setText("开始");
            isRunning = false;
        }

        // 注册循环结束广播移到onResume
        vibrateFinishReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("top.chenyanjin.VIBRATE_FINISH".equals(intent.getAction())) {
                    runOnUiThread(() -> {
                        btnStartStop.setText("开始");
                        isRunning = false;
                        Toast.makeText(MainActivity.this, "循环已结束", Toast.LENGTH_SHORT).show();
                        stopVibrateService(); // 新增：确保通知也被取消
                    });
                }
            }
        };
        registerReceiver(vibrateFinishReceiver, new IntentFilter("top.chenyanjin.VIBRATE_FINISH"));

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

        btnApply.setOnClickListener(v -> {
            if (isVibrateServiceRunning()) {
                // 服务正在运行，发送带参数的 intent 让参数立即生效
                int interval = parseInt(editInterval.getText().toString(), 60);
                int count = parseInt(editCount.getText().toString(), 1);
                int unitPos = spinnerUnit.getSelectedItemPosition();
                if (unitPos == 1) interval = interval * 60;
                int loop = parseInt(editLoop.getText().toString(), -1);
                String toastText = editToast.getText().toString();
                if (toastText.isEmpty()) toastText = "该翻身了";
                Intent intent = new Intent(this, VibrateService.class);
                intent.putExtra(VibrateService.EXTRA_INTERVAL, interval);
                intent.putExtra(VibrateService.EXTRA_COUNT, count);
                intent.putExtra(VibrateService.EXTRA_LOOP, loop);
                intent.putExtra(VibrateService.EXTRA_TOAST, toastText);
                startService(intent);
                Toast.makeText(this, "设置已应用并生效", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "服务未运行，设置仅保存", Toast.LENGTH_SHORT).show();
            }
        });

        btnStartStop.setOnClickListener(v -> {
            if (!isRunning) {
                // 新增：保存参数
                saveParams(editInterval, editCount, spinnerUnit, editLoop, editToast);
                startVibrateService(editInterval, editCount, spinnerUnit, editLoop, editToast);
                btnStartStop.setText("停止");
                isRunning = true;
            } else {
                stopVibrateService();
                btnStartStop.setText("开始");
                isRunning = false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("top.chenyanjin.UPDATE_BUTTON");
        registerReceiver(buttonUpdateReceiver, filter);

        if (vibrateFinishReceiver == null) {
            vibrateFinishReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("top.chenyanjin.VIBRATE_FINISH".equals(intent.getAction())) {
                        android.util.Log.d(TAG, "收到循环结束广播");
                        runOnUiThread(() -> {
                            btnStartStop.setText("开始");
                            isRunning = false;
                            Toast.makeText(MainActivity.this, "循环已结束", Toast.LENGTH_SHORT).show();
                            stopVibrateService();
                        });
                    }
                }
            };
            android.util.Log.d(TAG, "注册循环结束广播");
            registerReceiver(vibrateFinishReceiver, new IntentFilter("top.chenyanjin.VIBRATE_FINISH"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(buttonUpdateReceiver);
        if (vibrateFinishReceiver != null) {
            android.util.Log.d(TAG, "注销循环结束广播");
            unregisterReceiver(vibrateFinishReceiver);
            vibrateFinishReceiver = null;
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

    private void startVibrateService(EditText editInterval, EditText editCount, Spinner spinnerUnit, EditText editLoop, EditText editToast) {
        int interval = parseInt(editInterval.getText().toString(), 60);
        int count = parseInt(editCount.getText().toString(), 1);
        int unitPos = spinnerUnit.getSelectedItemPosition();
        if (unitPos == 1) interval = interval * 60;
        int loop = parseInt(editLoop.getText().toString(), -1);
        String toastText = editToast.getText().toString();
        if (toastText.isEmpty()) toastText = "该翻身了";
        Intent intent = new Intent(this, VibrateService.class);
        intent.putExtra(VibrateService.EXTRA_INTERVAL, interval);
        intent.putExtra(VibrateService.EXTRA_COUNT, count);
        intent.putExtra(VibrateService.EXTRA_LOOP, loop);
        intent.putExtra(VibrateService.EXTRA_TOAST, toastText);
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

    // 新增：保存参数到 SharedPreferences
    private void saveParams(EditText editInterval, EditText editCount, Spinner spinnerUnit, EditText editLoop, EditText editToast) {
        getSharedPreferences("vibrate_params", MODE_PRIVATE)
            .edit()
            .putInt("interval", parseInt(editInterval.getText().toString(), 60))
            .putInt("count", parseInt(editCount.getText().toString(), 1))
            .putInt("unit", spinnerUnit.getSelectedItemPosition())
            .putInt("loop", parseInt(editLoop.getText().toString(), -1))
            .putString("toast", editToast.getText().toString())
            .apply();
    }

    // 新增：读取参数并填充到输入框
    private void loadParams(EditText editInterval, EditText editCount, Spinner spinnerUnit, EditText editLoop, EditText editToast) {
        android.content.SharedPreferences sp = getSharedPreferences("vibrate_params", MODE_PRIVATE);
        int interval = sp.getInt("interval", 60);
        int count = sp.getInt("count", 1);
        int unit = sp.getInt("unit", 0);
        int loop = sp.getInt("loop", 0);
        String toast = sp.getString("toast", "");
        editInterval.setText(String.valueOf(interval));
        editCount.setText(String.valueOf(count));
        spinnerUnit.setSelection(unit);
        editLoop.setText(String.valueOf(loop));
        editToast.setText(toast);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 删除原onDestroy中的unregisterReceiver逻辑
    }
}