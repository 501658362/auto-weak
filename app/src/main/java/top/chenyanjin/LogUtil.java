package top.chenyanjin;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogUtil {
    public static void i(String tag, String msg) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        Log.i(tag, "[" + time + "] " + msg);
    }
    // 可扩展 d, e, w 等方法
}
