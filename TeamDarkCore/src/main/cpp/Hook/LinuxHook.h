#ifndef TEAMDARKCORE_LINUXHOOK_H
#define TEAMDARKCORE_LINUXHOOK_H

#include "BaseHook.h"

class LinuxHook : public BaseHook {
public:
    static void init(JNIEnv *env);
};

#endif // TEAMDARKCORE_LINUXHOOK_H
