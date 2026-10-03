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
    private static volatile String status = "正在准备游戏资源…";

    public static boolean isRunning() {
        return running;
    }

    public static String getLastError() {
        return lastError;
    }

    public static String getStatus() {
        return status;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running || !STARTING.compareAndSet(false, true)) {
            return START_STICKY;
        }

        new Thread(() -> {
            try {
                lastError = null;
                status = "正在准备游戏资源…";

                File root = GameInstaller.ensureInstalled(this, text -> {
                    status = text;
                    Log.i(TAG, text);
                });

                File cache = getCacheDir();
                File home = getFilesDir();
                File entry = new File(root, "server/index.js");

                if (!entry.isFile()) throw new IllegalStateException("server/index.js 不存在");

                status = "正在启动本地游戏服务器…";
                running = true;

                int code = NativeNode.start(
                        root.getAbsolutePath(),
                        cache.getAbsolutePath(),
                        home.getAbsolutePath(),
                        new String[]{"node", entry.getAbsolutePath()}
                );

                running = false;
                lastError = "Node 服务已退出，代码 " + code;
                status = lastError;
                Log.e(TAG, lastError);
            } catch (Throwable t) {
                running = false;
                lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
                status = lastError;
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
