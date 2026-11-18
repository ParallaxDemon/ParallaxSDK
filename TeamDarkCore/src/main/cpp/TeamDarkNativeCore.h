//
// Created by Milk on 4/9/21.
//

#ifndef TEAMDARK_NATIVE_CORE_H
#define TEAMDARK_NATIVE_CORE_H

#include <jni.h>
#include <unistd.h>
//#include <fb/include/fb/ALog.h>
//#include <fb/include/fb/fbjni.h>
#define VMCORE_CLASS "com/teamdark/core/core/RNative"

class TeamDarkNativeCore {
public:
    static JavaVM *getJavaVM();
    static int getApiLevel();
    static int getCallingUid(JNIEnv *env, int orig);
    static jstring redirectPathString(JNIEnv *env, jstring path);
    static jobject redirectPathFile(JNIEnv *env, jobject path);
    static jlongArray loadEmptyDex(JNIEnv *env);
};

#endif // TEAMDARK_NATIVE_CORE_H
