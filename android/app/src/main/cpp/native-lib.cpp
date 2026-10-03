#include <jni.h>
#include <node.h>

#include <cstdlib>
#include <string>
#include <vector>
#include <unistd.h>

namespace {

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void setEnv(const char* name, const std::string& value) {
    if (!value.empty()) {
        setenv(name, value.c_str(), 1);
    }
}

}  // namespace

extern "C"
JNIEXPORT jint JNICALL
Java_com_hiko_stronghold_NativeNode_start(
        JNIEnv* env,
        jclass,
        jstring workDirValue,
        jstring cacheDirValue,
        jstring homeDirValue,
        jobjectArray arguments) {

    const std::string workDir = toString(env, workDirValue);
    const std::string cacheDir = toString(env, cacheDirValue);
    const std::string homeDir = toString(env, homeDirValue);

    if (workDir.empty() || cacheDir.empty() || arguments == nullptr) {
        return -10;
    }

    setEnv("TMPDIR", cacheDir);
    setEnv("HOME", homeDir);
    setEnv("PORT", "3000");
    setEnv("HOST", "127.0.0.1");
    setEnv("SP_COMBAT", "client");
    setEnv("SP_VERIFY", "off");
    setEnv("NODE_COMPILE_CACHE", cacheDir + "/node-compile-cache");
    setEnv("NODE_COMPILE_CACHE_PORTABLE", "1");
    setEnv("NODE_OPTIONS", "--max-old-space-size-percentage=30");

    if (chdir(workDir.c_str()) != 0) {
        return -11;
    }

    const jsize count = env->GetArrayLength(arguments);
    std::vector<std::string> owned;
    owned.reserve(static_cast<size_t>(count));

    for (jsize i = 0; i < count; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(arguments, i));
        owned.push_back(toString(env, value));
        env->DeleteLocalRef(value);
    }

    std::vector<char*> argv;
    argv.reserve(owned.size());
    for (auto& arg : owned) {
        argv.push_back(arg.data());
    }

    return static_cast<jint>(node::Start(static_cast<int>(argv.size()), argv.data()));
}
