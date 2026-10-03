package com.hiko.stronghold;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GameInstaller {
    // v2 deliberately invalidates any possibly-corrupted v1 install left by
    // the first Android build's concurrent extraction race.
    private static final String VERSION = "stronghold-android-v2";

    private GameInstaller() {}

    /**
     * Installation is process-wide single-flight. MainActivity and NodeService
     * may be created close together by Android, so never allow two extract/delete
     * passes to touch the same game directory at once.
     */
    public static synchronized File ensureInstalled(Context context, Progress progress) throws IOException {
        File root = new File(context.getFilesDir(), "game");
        File marker = new File(root, ".installed-version");
        if (marker.isFile()) {
            String existing = new String(java.nio.file.Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim();
            if (VERSION.equals(existing) && new File(root, "server/index.js").isFile()) {
                progress.onProgress("游戏资源已就绪");
                return root;
            }
        }

        progress.onProgress("正在清理旧资源…");
        deleteRecursively(root);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IOException("无法创建游戏目录: " + root);
        }

        progress.onProgress("正在初始化游戏资源…");
        try (InputStream raw = context.getAssets().open("game.zip");
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[128 * 1024];
            int count = 0;
            while ((entry = zip.getNextEntry()) != null) {
                File out = safeResolve(root, entry.getName());
                if (entry.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory()) {
                        throw new IOException("无法创建目录: " + out);
                    }
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                        throw new IOException("无法创建目录: " + parent);
                    }
                    try (BufferedOutputStream dest = new BufferedOutputStream(new FileOutputStream(out))) {
                        int n;
                        while ((n = zip.read(buffer)) > 0) {
                            dest.write(buffer, 0, n);
                        }
                    }
                }
                count++;
                if (count % 120 == 0) {
                    progress.onProgress("正在解压游戏资源… " + count);
                }
                zip.closeEntry();
            }
        }

        // The marker is written last: its presence means the extraction finished.
        try (FileOutputStream out = new FileOutputStream(marker)) {
            out.write(VERSION.getBytes(StandardCharsets.UTF_8));
        }
        progress.onProgress("游戏资源初始化完成");
        return root;
    }

    private static File safeResolve(File root, String name) throws IOException {
        File out = new File(root, name);
        String rootPath = root.getCanonicalPath() + File.separator;
        String outPath = out.getCanonicalPath();
        if (!outPath.startsWith(rootPath)) {
            throw new IOException("非法压缩包路径: " + name);
        }
        return out;
    }

    private static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("无法删除旧文件: " + file);
        }
    }

    public interface Progress {
        void onProgress(String text);
    }
}
