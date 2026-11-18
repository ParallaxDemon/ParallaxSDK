package com.teamdark.core;

import android.content.Context;
import androidx.annotation.Keep;
import java.io.File;
import com.teamdark.core.entity.pm.InstallResult;
import net_62v.external.MetaActivationManager;

/**
 * 1-Line Universal Loader Helper for any Android Loader APK.
 *
 * Usage in ANY Activity or Application:
 *   // 1. Optional init (also happens automatically on app start!):
 *   TeamDarkLoader.init(this);
 *
 *   // 2. One-click install + OBB auto-link + launch:
 *   TeamDarkLoader.launchGame(this, "com.pubg.imobile");
 */
@Keep
public final class TeamDarkLoader {

    private TeamDarkLoader() {
    }

    public static void init(Context context) {
        TeamDarkCore.get().ensureInitialized(context);
    }

    public static boolean launchGame(Context context, String packageName) {
        return launchGame(context, packageName, 0);
    }

    public static boolean launchGame(Context context, String packageName, int userId) {
        if (context != null) {
            TeamDarkCore.get().ensureInitialized(context);
        }
        return TeamDarkCore.get().launchApk(packageName, userId);
    }

    public static boolean installAndLaunch(Context context, String packageName) {
        return launchGame(context, packageName, 0);
    }

    public static boolean installAndLaunch(Context context, File apkOrBundleFile, int userId) {
        if (context != null) {
            TeamDarkCore.get().ensureInitialized(context);
        }
        InstallResult res = TeamDarkCore.get().installBundleAsUser(apkOrBundleFile, userId);
        if (res != null && res.success && res.packageName != null) {
            return TeamDarkCore.get().launchApk(res.packageName, userId);
        }
        return false;
    }

    public static boolean stopGame(String packageName) {
        return stopGame(packageName, 0);
    }

    public static boolean stopGame(String packageName, int userId) {
        try {
            TeamDarkCore.get().stopPackage(packageName, userId);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean loadPluginSo(String pathOrLibName) {
        return TeamDarkCore.loadCustomLibrary(pathOrLibName);
    }

    public static boolean resetGuestAccount(String packageName) {
        return TeamDarkCore.get().resetGuestAccountKeepResources(packageName, 0, false);
    }

    public static String resetDeviceId() {
        return MetaActivationManager.resetVirtualDeviceIdentity();
    }
}
