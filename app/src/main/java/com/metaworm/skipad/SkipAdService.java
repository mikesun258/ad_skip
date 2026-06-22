package com.metaworm.skipad;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.GestureDescription;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class SkipAdService extends AccessibilityService {
    private final String configPath = "/storage/emulated/0/000设置参数备份/自己的app/default.json";
    private List<RuleItem> whiteRuleList = new ArrayList<>();
    private final Gson gson = new Gson();

    // 防抖锁：记录上次执行时间，冷却1.5s内不再处理同一包广告
    private long lastExecTime = 0;
    private final long COOL_TIME = 1500;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        loadWhiteListRule();
    }

    private void loadWhiteListRule() {
        File cfgFile = new File(configPath);
        File parent = cfgFile.getParentFile();
        if (!parent.exists()) parent.mkdirs();

        if (!cfgFile.exists()) {
            List<RuleItem> demoList = new ArrayList<>();
            RuleItem r1 = new RuleItem();
            r1.packageName = "com.kylin.read";
            r1.text = "跳过";
            demoList.add(r1);

            RuleItem r2 = new RuleItem();
            r2.packageName = "com.kylin.read";
            r2.text = "关闭广告";
            r2.useDelay = true;
            r2.delay = 1500;
            demoList.add(r2);

            RuleItem r3 = new RuleItem();
            r3.packageName = "com.ss.android.ugc.aweme";
            r3.text = "跳过";
            r3.useDelay = true;
            r3.delay = 1000;
            demoList.add(r3);

            RuleItem r4 = new RuleItem();
            r4.packageName = "com.douban.frodo";
            r4.viewId = "btn_skip_ad";
            demoList.add(r4);

            RuleItem r5 = new RuleItem();
            r5.packageName = "com.xxx.app";
            r5.needSwipeUp = true;
            r5.delay = 800;
            demoList.add(r5);

            try (java.io.FileWriter w = new java.io.FileWriter(cfgFile)){
                w.write(gson.toJson(demoList));
            }catch (Exception e){e.printStackTrace();}
            whiteRuleList = demoList;
        }else{
            try(FileReader fr = new FileReader(cfgFile)){
                whiteRuleList = gson.fromJson(fr, new TypeToken<List<RuleItem>>(){}.getType());
            }catch (Exception e){e.printStackTrace();}
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(whiteRuleList.isEmpty()) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;

        long now = System.currentTimeMillis();
        // 冷却期直接返回，防止短时间重复触发
        if (now - lastExecTime < COOL_TIME) return;

        String currentPkg = event.getPackageName().toString();
        AccessibilityNodeInfo root = getRootNode();
        if(root == null) return;

        boolean hasExec = false;
        for(RuleItem rule : whiteRuleList){
            if(!rule.packageName.equals(currentPkg)) continue;

            if(rule.needSwipeUp){
                swipeUpScreen(rule.delay);
                hasExec = true;
                continue;
            }

            List<AccessibilityNodeInfo> matchNodes = new ArrayList<>();
            if(rule.viewId != null && !rule.viewId.isEmpty()){
                List<AccessibilityNodeInfo> ids = root.findAccessibilityNodeInfosByViewId(currentPkg+":id/"+rule.viewId);
                matchNodes.addAll(ids);
            }
            if(rule.text != null && !rule.text.isEmpty()){
                List<AccessibilityNodeInfo> texts = root.findNodesByText(rule.text);
                matchNodes.addAll(texts);
            }

            for(AccessibilityNodeInfo node : matchNodes){
                doClick(node, rule.useDelay, rule.delay);
                hasExec = true;
                node.recycle();
            }
        }
        // 本次执行了动作，刷新冷却时间
        if (hasExec) lastExecTime = now;
        root.recycle();
    }

    private void doClick(AccessibilityNodeInfo node, boolean useDelay, int delay){
        long wait = useDelay ? delay : 0;
        new Thread(() -> {
            try {Thread.sleep(wait);} catch (InterruptedException e) {}
            getServiceHandler().post(()->{
                if(node.isClickable()) node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                else{
                    AccessibilityNodeInfo p = node.getParent();
                    while (p != null){
                        if(p.isClickable()){
                            p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            break;
                        }
                        p = p.getParent();
                    }
                }
            });
        }).start();
    }

    private void swipeUpScreen(int delayMs){
        new Thread(() -> {
            try {Thread.sleep(delayMs);} catch (InterruptedException e) {}
            GestureDescription.Builder builder = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(500,1600);
            path.lineTo(500,400);
            builder.addStroke(new GestureDescription.StrokeDescription(path,0,250));
            dispatchGesture(builder.build(),null,null);
        }).start();
    }

    private AccessibilityNodeInfo getRootNode() {
        if(getWindows().isEmpty()) return null;
        return getWindows().get(0).getRoot();
    }

    @Override
    public void onInterrupt() {}
}
