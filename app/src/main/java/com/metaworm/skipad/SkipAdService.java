package com.metaworm.skipad;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class SkipAdService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 你的广告点击逻辑
    }

    @Override
    public void onInterrupt() {

    }

    // 删掉整段onServiceDisconnected代码，父类不存在该方法，无需重写
}
