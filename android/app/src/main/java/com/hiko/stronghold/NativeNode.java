package com.hiko.stronghold;

public final class NativeNode {
    static {
        System.loadLibrary("node");
        System.loadLibrary("stronghold_node_bridge");
    }

    private NativeNode() {}

    public static native int start(
            String workDir,
            String cacheDir,
            String homeDir,
            String[] arguments
    );
}
