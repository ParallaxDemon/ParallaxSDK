#ifndef TEAMDARKCORE_SYSTEMPROPERTIESHOOK_H
#define TEAMDARKCORE_SYSTEMPROPERTIESHOOK_H

#include <map>
#include "BaseHook.h"
#include <string>

class SystemPropertiesHook : public BaseHook{
public:
    static void init(JNIEnv *env);
    static void setProp(const char *key, const char *value);
};

#endif // TEAMDARKCORE_SYSTEMPROPERTIESHOOK_H
