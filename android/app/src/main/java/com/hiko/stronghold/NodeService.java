package com.hiko.stronghold;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NodeService extends Service {
    private static final String TAG = "StrongholdNode";
    private static final AtomicBoolean STARTING = new AtomicBoolean(false);
    private static volatile boolean running = false;
    private static volatile String lastError = null;

    public static boolean isRunning() {
        return running;
    }

    public static String getLastError() {
        return lastError;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running || !STARTING.compareAndSet(false, true)) {
            return START_STICKY;
        }

        new Thread(() -> {
            try {
                File root = GameInstaller.ensureInstalled(this, text -> Log.i(TAG, text));
                File cache = getCacheDir();
                File home = getFilesDir();
                File entry = new File(root, "server/index.js");

                if (!entry.isFile()) throw new IllegalStateException("server/index.js 不存在");

                running = true;
                lastError = null;

                int code = NativeNode.start(
                        root.getAbsolutePath(),
                        cache.getAbsolutePath(),
                        home.getAbsolutePath(),
                        new String[]{"node", entry.getAbsolutePath()}
                );

                running = false;
                lastError = "Node 服务已退出，代码 " + code;
                Log.e(TAG, lastError);
            } catch (Throwable t) {
                running = false;
                lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
                Log.e(TAG, "Node startup failed", t);
            } finally {
                STARTING.set(false);
            }
        }, "Stronghold-Node").start();

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
