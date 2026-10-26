package com.wrist.tingji;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.wrist.tingji.core.NoteStore;
import com.wrist.tingji.core.RecorderState;
import com.wrist.tingji.core.Settings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {
    private static App instance;

    public Settings settings;
    public NoteStore store;

    public static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static final ExecutorService IO = Executors.newFixedThreadPool(3);

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        settings = new Settings(this);
        store = new NoteStore(this);
        store.recoverInterrupted();
        RecorderState.serviceRunning = false;
    }

    public static App get() {
        return instance;
    }
}
