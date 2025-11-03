package top.chenyanjin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;
import top.chenyanjin.LogUtil;
import android.util.Log;
import android.widget.Toast;

public class VibrateService extends Service {
    public static final String EXTRA_INTERVAL = "interval";
    public static final String EXTRA_COUNT = "count";
    public static final String EXTRA_LOOP = "loop";
    public static final String EXTRA_TOAST = "toast";
    public static final String ACTION_STOP = "stop";

    private Handler handler;
    private Runnable vibrateRunnable;
    private int intervalSec = 60;
    private int count = 1;
    private boolean running = false;
    private int loop = -1; // -1 表示无限
    private String toastText = "该翻身了";
    private int currentLoop = 0;

    private static final String CHANNEL_ID = "vibrate_service_channel";
    private static final int NOTIFY_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null) {
            // 新参数
            int newInterval = intent.getIntExtra(EXTRA_INTERVAL, intervalSec);
            int newCount = intent.getIntExtra(EXTRA_COUNT, count);
            int newLoop = intent.getIntExtra(EXTRA_LOOP, loop);
            String newToastText = intent.getStringExtra(EXTRA_TOAST);
            if (newToastText == null || newToastText.isEmpty()) newToastText = toastText;

            boolean needRestart = false;
            // 检查参数是否变化
            if (newInterval != intervalSec || newCount != count || newLoop != loop || !newToastText.equals(toastText)) {
                intervalSec = newInterval;
                count = newCount;
                loop = newLoop;
                toastText = newToastText;
                needRestart = true;
            }
            // 若服务未运行，初始化 currentLoop
            if (!running) {
                currentLoop = 0;
                startForegroundService();
                startVibrateLoop();
            } else if (needRestart) {
                // 参数变化时重启震动循环，但不停止服务
                stopVibrateLoop();
                currentLoop = 0; // 参数变化时重置循环计数
                startVibrateLoop();
                showToast("参数已应用并生效"); // 可选：提示参数已应用
            }
        }
        return START_STICKY;
    }

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "定时震动服务",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("定时震动已开启")
            .setContentText("震动间隔：" + intervalSec + "秒，每次" + count + "下")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build();
        startForeground(NOTIFY_ID, notification);
    }

    private void startVibrateLoop() {
        stopVibrateLoop();
        running = true;
        vibrateRunnable = new Runnable() {
            @Override
            public void run() {

                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (count > 0 && vibrator != null) {
                    vibrateMultipleTimes(vibrator, count, 0);
                } else {
                    // 震动次数为0时，仅toast和日志
                    LogUtil.i("VibrateService", "震动第" + (currentLoop + 1) + "次, 未震动（次数为0），仅提示");
                    showToast(toastText);
                }
                currentLoop++;
                if (loop > 0 && currentLoop >= loop) {
                    // 循环结束时发送显式广播
                    LogUtil.i("info", "循环结束时发送显式广播");
                    Intent finishIntent = new Intent("top.chenyanjin.VIBRATE_FINISH");
                    finishIntent.setClassName(getPackageName(), "top.chenyanjin.VibrateReceiver");
                    finishIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES); // 确保广播能发给未启动的接收方
                    LogUtil.i("VibrateService", "发送结束广播: " + finishIntent);
                    sendBroadcast(finishIntent);
                    stopSelf();
                    return;
                }
                if (running && (loop < 0 || currentLoop < loop)) {
                    handler.postDelayed(this, intervalSec * 1000L);
                }
            }
        };
        handler.post(vibrateRunnable);
    }

    // 连续震动 count 次，每次间隔 300ms
    private void vibrateMultipleTimes(Vibrator vibrator, int count, int index) {
        if (index >= count) return;
        // 震动
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(300);
        }
        LogUtil.i("VibrateService", "震动第" + (currentLoop + 1) + "次, 连续震动第" + (index + 1) + "下");
        showToast(toastText);
        // 下一个震动
        if (index + 1 < count) {
            handler.postDelayed(() -> vibrateMultipleTimes(vibrator, count, index + 1), 350);
        }
    }

    private void stopVibrateLoop() {
        running = false;
        if (handler != null && vibrateRunnable != null) {
            handler.removeCallbacks(vibrateRunnable);
        }
    }

    private void showToast(final String text) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroy() {
        stopVibrateLoop();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
