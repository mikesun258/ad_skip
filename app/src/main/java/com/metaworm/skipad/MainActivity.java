package com.metaworm.skipad;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    // API33通知权限常量，SDK29无内置常量，手写字符串
    private final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
    private final int NOTIFICATION_PERM_CODE = 1;

    private EditText editLogin;
    private EditText editText;
    private CheckBox checkFilter;
    private CheckBox ckGlobalDelay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 绑定全部布局控件
        editLogin = findViewById(R.id.editLogin);
        editText = findViewById(R.id.editText);
        checkFilter = findViewById(R.id.checkFilter);
        ckGlobalDelay = findViewById(R.id.ckGlobalDelay);

        // Android13 通知权限申请 用数字33替代TIRAMISU常量
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{POST_NOTIFICATIONS}, NOTIFICATION_PERM_CODE);
            }
        }

        // 全部文件访问权限跳转，手写Action字符串
        if (Build.VERSION.SDK_INT >= 30) {
            Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
            startActivity(intent);
        }

        // 无障碍服务详情页跳转
        Intent intent = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
        startActivity(intent);
    }

    // 子线程吐司示例（Java8 Lambda已开，可正常使用）
    private void showToast(String msg){
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }
}
