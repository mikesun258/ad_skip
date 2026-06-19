package com.metaworm.skipad;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkipAdService extends AccessibilityService {
    public static SkipAdService instance = null;
    public static boolean log = false;
    public static String filter = null;
    public static EditText editLog = null;
    private static JSONArray config;
    // 白名单包名集合，只有在白名单内的APP才会执行规则匹配
    private final List<String> pkgWhitelist = new ArrayList<>();

    // 全局延时总开关：true=只有规则标记useDelay=true才会延时；false=所有规则立即点击
    public static boolean globalDelaySwitch = true;
    // 点击冷却、Handler任务队列
    private static final long CLICK_COOL = 350;
    private long lastClickTime = 0L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler clickHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> delayTasks = new ArrayList<>();
    private static final String CHANNEL_ID = "SkipAdForeground";

    // 正则Pattern：匹配 数字+s跳过 / 数字+秒关闭 等倒计时文本
    private static final Pattern countDownPattern = Pattern.compile("\\d+[s秒].*(跳过|关闭|继续)");

    public JSONArray getConfig() { return config; }

    // 加载规则 + 自动提取白名单包名
    public static void setConfig(String json) {
        try {
            JSONArray c = new JSONArray(json);
            for (int i = 0; i < c.length(); i++) {
                if (!(c.get(i) instanceof JSONObject))
                    throw new Exception("规则项必须为JSON对象");
            }
            config = c;
            // 刷新白名单
            if(instance != null) instance.refreshWhitelist();
        } catch (Exception e) {
            if(instance != null) {
                mainHandler.post(() -> Toast.makeText(instance, e.getMessage(), Toast.LENGTH_SHORT).show());
            }
            Log.d("EXCEPTION", e.toString());
        }
    }

    // 从所有规则提取不重复包名，生成白名单
    private void refreshWhitelist(){
        pkgWhitelist.clear();
        if(config == null) return;
        for(int i=0;i<config.length();i++){
            JSONObject rule = config.optJSONObject(i);
            String pkg = rule.optString("package","");
            if(!pkg.isEmpty() && !pkgWhitelist.contains(pkg)){
                pkgWhitelist.add(pkg);
            }
        }
    }

    // 正则+文本双重查找节点：精确文本 / 正则倒计时文本 都能匹配
    private AccessibilityNodeInfo findNodeByTextOrRegex(AccessibilityNodeInfo root, String targetText) {
        if (root == null) return null;
        CharSequence text = root.getText();
        if (text != null) {
            String txt = text.toString();
            // 精确包含匹配 || 正则倒计时匹配
            if(txt.contains(targetText) || countDownPattern.matcher(txt).find()){
                return root;
            }
        }
        int childCnt = root.getChildCount();
        for (int i = 0; i < childCnt; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            AccessibilityNodeInfo res = findNodeByTextOrRegex(child, targetText);
            if(child != null) child.recycle();
            if(res != null) return res;
        }
        return null;
    }

    // 延时点击方法，复制节点避免系统回收
    private void delayClick(AccessibilityNodeInfo node, long delayMs){
        Runnable task = ()->{
            if(node != null && node.isClickable()){
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if(log) Log.d("SKIP_DELAY","延时点击执行成功");
            }
            node.recycle();
            delayTasks.remove(this);
        };
        delayTasks.add(task);
        clickHandler.postDelayed(task, delayMs);
    }

    // 核心业务逻辑
    private void handleAdClickLogic(AccessibilityNodeInfo rootNode, String pkgName, String activityCls) {
        if(rootNode == null || config == null) return;
        long now = System.currentTimeMillis();
        if(now - lastClickTime < CLICK_COOL) return;

        // 调试抓取逻辑
        if(filter != null) {
            AccessibilityNodeInfo target = findNodeByTextOrRegex(rootNode, filter);
            if(target != null) {
                String info = target.getClassName() + "\n" + target.getPackageName() + "\n" + target.getText();
                mainHandler.post(() -> Toast.makeText(this, info, Toast.LENGTH_LONG).show());
                target.recycle();
            }
        }

        // 1.白名单校验：不在白名单直接终止，完全不扫描（等效LSP作用域）
        if(pkgWhitelist.size()>0 && !pkgWhitelist.contains(pkgName)) return;
        // 排除自身APP
        if("com.metaworm.skipad".equals(pkgName)) return;

        // 遍历全部规则
        for (int i = 0; i < config.length(); i++) {
            JSONObject rule = config.getJSONObject(i);
            boolean pkgMatch = !rule.has("package") || rule.getString("package").equals(pkgName);
            boolean actMatch = !rule.has("activity") || rule.getString("activity").equals(activityCls);
            if(!pkgMatch || !actMatch) continue;

            String viewId = rule.optString("viewId", null);
            String ruleText = rule.optString("text", null);
            // 单条规则标记：是否启用延时
            boolean ruleUseDelay = rule.optBoolean("useDelay",false);
            // 该规则延时毫秒数
            long ruleDelayMs = rule.optLong("delay",1200);

            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            if(viewId != null && !viewId.isEmpty()) {
                nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId);
            } else if(ruleText != null && !ruleText.isEmpty()) {
                AccessibilityNodeInfo node = findNodeByTextOrRegex(rootNode, ruleText);
                if(node != null) nodes.add(node);
            }

            for (AccessibilityNodeInfo n : nodes) {
                if(n.isClickable()) {
                    lastClickTime = now;
                    // 判断是否需要延时：全局开关开启 + 本条规则标记useDelay=true
                    if(globalDelaySwitch && ruleUseDelay){
                        AccessibilityNodeInfo copyNode = n.obtain(n);
                        delayClick(copyNode,ruleDelayMs);
                    }else{
                        boolean ok = n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        if(log) Log.d("SKIP", "立即点击成功:"+ok+" pkg:"+pkgName);
                    }
                    n.recycle();
                    break;
                }
                n.recycle();
            }
        }
        rootNode.recycle();
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        int type = e.getEventType();
        if(type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        AccessibilityNodeInfo source = e.getSource();
        if(source == null) return;
        AccessibilityNodeInfo root = source.getRoot();
        source.recycle();
        if(root == null) return;

        String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
        String act = root.getClassName() != null ? root.getClassName().toString() : "";
        handleAdClickLogic(root, pkg, act);
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.packageNames = null;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 150;
        setServiceInfo(info);

        createNotificationChannel();
        Notification notify = buildForegroundNotification();
        startForeground(1, notify);
        // 初始化白名单
        refreshWhitelist();
    }

    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "广告跳过服务", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildForegroundNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setOngoing(true)
                .setContentTitle("广告自动跳过已运行")
                .setContentText("无障碍服务正在后台工作")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        return builder.build();
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onServiceDisconnected() {
        clickHandler.removeCallbacksAndMessages(null);
        delayTasks.clear();
        pkgWhitelist.clear();
        instance = null;
        super.onServiceDisconnected();
    }
}
