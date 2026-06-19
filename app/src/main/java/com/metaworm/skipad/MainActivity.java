package com.metaworm.skipad;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {
    private EditText editLog;
    private EditText editText;
    private CheckBox checkFilter;
    private CheckBox ckGlobalDelay;

    // 外部规则固定路径
    private final String EXTERNAL_RULE_FILE = Environment.getExternalStorageDirectory()
            + "/000设置参数备份/自己的app/default.json";
    // 权限请求码
    private static final int NOTIFICATION_PERM_CODE = 1001;
    private static final int FILE_PERM_CODE = 1002;

    // 检测无障碍权限是否开启
    private boolean isAccessibilityEnabled() {
        String fullServiceName = getPackageName() + "/" + SkipAdService.class.getName();
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null || enabledServices.isEmpty()) return false;
        String[] enabledList = enabledServices.split(":");
        for(String s : enabledList){
            if(s.equals(fullServiceName)) return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editLog = findViewById(R.id.editLog);
        editText = findViewById(R.id.editText);
        checkFilter = findViewById(R.id.checkFilter);
        ckGlobalDelay = findViewById(R.id.ckGlobalDelay);
        SkipAdService.editLog = editLog;

        // 依次申请权限：通知权限、全部文件访问权限
        requestNotificationPermission();
        requestFileManagePermission();
        // 加载规则（外部优先，内置兜底）
        loadAllRuleSource();
    }

    // 安卓13+通知权限申请（前台保活必备）
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERM_CODE);
            }
        }
    }

    // 安卓11+全部文件访问权限，用来读写自定义外层文件夹
    private void requestFileManagePermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            if(!Environment.isExternalStorageManager()){
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:"+getPackageName()));
                startActivity(intent);
            }
        }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},FILE_PERM_CODE);
            }
        }
    }

    // 读取外部存储json规则
    private String readExternalRule(){
        File ruleFile = new File(EXTERNAL_RULE_FILE);
        if(!ruleFile.exists()) return null;
        StringBuilder jsonSb = new StringBuilder();
        try(BufferedReader br = new BufferedReader(new FileReader(ruleFile))){
            String line;
            while ((line = br.readLine()) != null){
                jsonSb.append(line);
            }
            return jsonSb.toString();
        }catch (Exception e){
            Log.e("FILE_LOAD_ERR","外部规则读取失败："+e.getMessage());
            runOnUiThread(()->Toast.makeText(this,"外部规则文件读取异常",Toast.LENGTH_SHORT).show());
            return null;
        }
    }

    // 读取assets内置默认规则
    private String readAssetsDefaultRule(){
        AssetManager mgr = getAssets();
        StringBuilder jsonSb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(mgr.open("default.json")))){
            String line;
            while ((line = br.readLine()) != null) {
                jsonSb.append(line);
            }
            return jsonSb.toString();
        } catch (Exception e) {
            Log.e("ASSET_LOAD_ERR", "内置规则缺失: "+e);
            return "[]"; // 空规则数组兜底，防止崩溃
        }
    }

    // 加载总逻辑：外部文件存在则加载外部，否则加载内置
    private void loadAllRuleSource(){
        String externalJson = readExternalRule();
        if(externalJson != null){
            SkipAdService.setConfig(externalJson);
            runOnUiThread(()->Toast.makeText(this,"已加载外部自定义规则",Toast.LENGTH_SHORT).show());
            return;
        }
        String assetsJson = readAssetsDefaultRule();
        SkipAdService.setConfig(assetsJson);
        runOnUiThread(()->Toast.makeText(this,"外部规则不存在，加载内置规则",Toast.LENGTH_SHORT).show());
    }

    // 调试抓取关键词开关
    public void toggleFilter(View view) {
        if (checkFilter.isChecked()) {
            String keyword = editText.getText().toString().trim();
            if(keyword.isEmpty()){
                Toast.makeText(this, "请输入关键词", Toast.LENGTH_SHORT).show();
                checkFilter.setChecked(false);
                SkipAdService.filter = null;
                return;
            }
            SkipAdService.filter = keyword;
            Toast.makeText(this, "调试抓取:"+keyword, Toast.LENGTH_SHORT).show();
        } else {
            SkipAdService.filter = null;
            Toast.makeText(this, "已关闭调试抓取", Toast.LENGTH_SHORT).show();
        }
    }

    // 全局延时总开关
    public void toggleGlobalDelay(View v){
        SkipAdService.globalDelaySwitch = ckGlobalDelay.isChecked();
        Toast.makeText(this, "全局延时已"+(ckGlobalDelay.isChecked()?"开启":"关闭"), Toast.LENGTH_SHORT).show();
    }

    // 跳转无障碍设置页，高版本直达本应用
    public void goAccess(View view) {
        Intent intent;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            intent = new Intent(Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:"+getPackageName()));
        }else{
            intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // 加载输入框粘贴的自定义JSON规则
    public void doConfig(View v) {
        String jsonRule = editText.getText().toString().trim();
        if(jsonRule.isEmpty()){
            Toast.makeText(this, "请粘贴广告规则JSON", Toast.LENGTH_SHORT).show();
            return;
        }
        SkipAdService.setConfig(jsonRule);
        Toast.makeText(this, "自定义规则已生效", Toast.LENGTH_SHORT).show();
    }

    // 重载外部规则按钮
    public void reloadExternalRule(View v){
        loadAllRuleSource();
    }

    // 清空日志
    public void clearLog(View v){
        editLog.setText("");
    }

    // 权限申请回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == NOTIFICATION_PERM_CODE){
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this,"通知权限已授予，前台保活生效",Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(this,"未授予通知权限，服务易被后台杀死",Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 从系统设置返回刷新无障碍状态
    @Override
    protected void onResume() {
        super.onResume();
        boolean accessEnable = isAccessibilityEnabled();
        Log.d("权限状态", accessEnable ? "无障碍已开启" : "无障碍未开启");
    }
}
