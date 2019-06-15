package cn.nlifew.scrollcollapsinglayout.application;

import android.app.Application;
import android.content.Context;

public class ThisApp extends Application {

    public static Context getContext() {
        return sContext;
    }

    private static Context sContext;

    @Override
    public void onCreate() {
        super.onCreate();

        sContext = this;
    }
}
