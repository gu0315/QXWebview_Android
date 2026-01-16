package com.energy.chery_android.QRWebView;

import android.os.Bundle;
import androidx.annotation.Nullable;

/**
 * 继承自 qx_hybrid 模块的 QXWebViewActivity
 * 可在此添加应用特定的定制逻辑
 */
public class QXWebViewActivity extends com.jd.hybrid.QXWebViewActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置默认配置
        setImmersiveStatusBar(true, true);
        setNavBarVisible(false);
        loadUrl("https://fr.dongxie.top/fr/#/");
    }
}
