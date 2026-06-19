package com.metaworm.skipad;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class SkipAdService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 你的广告点击逻辑
    }

    @Override
    public void onInterrupt() {}

    // 正确重写服务断开回调，固定签名
    @Override
    protected void onServiceDisconnected() {
        super.onServiceDisconnected();
        // 资源释放代码
    }
}
