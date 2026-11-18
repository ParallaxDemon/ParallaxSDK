package com.teamdark.core.utils.compat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;

import org.lsposed.lsparanoid.Obfuscate;

import com.teamdark.core.TeamDarkCore;
import com.teamdark.core.core.env.BEnvironment;
import com.teamdark.core.entity.pm.InstallOption;
import com.teamdark.core.entity.pm.InstallResult;
import com.teamdark.core.utils.FileUtils;
import com.teamdark.core.utils.NativeUtils;

/**
 * Auto Play-Store Game Update & Hot-Patch Synchronizer.
 *
 * Detects when a game installed on the host phone (e.g. BGMI / PUBG / Free Fire / CODM)
 * has been updated on Google Play Store (higher versionCode or newer APK timestamp)
 * than the virtual copy inside TeamDarkCore, and seamlessly syncs the updated Base APK,
 * Split APKs (split_config.arm64_v8a.apk, etc.), and new .obb files WITHOUT deleting
 * the user's virtual login session, shared_prefs, or custom settings!
 */
@Obfuscate
public final class TeamDarkHotUpdateSync {
    private static final String TAG = "TeamDarkHotSync";
    private static volatile boolean sAutoSyncEnabled = true;

    private TeamDarkHotUpdateSync() {
    }

    public static void setAutoSyncEnabled(boolean enabled) {
        sAutoSyncEnabled = enabled;
    }

    public static boolean isAutoSyncEnabled() {
        return sAutoSyncEnabled;
    }

    public static boolean syncIfHostUpdated(String packageName, int userId) {
        if (!sAutoSyncEnabled || packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        try {
            Context hostCtx = TeamDarkCore.getContext();
            if (hostCtx == null) return false;

            PackageManager hostPm = hostCtx.getPackageManager();
            PackageInfo hostInfo;
            try {
                hostInfo = hostPm.getPackageInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException notOnHost) {
                return false; // Game is installed only inside virtual container via APK file
            }
            if (hostInfo == null || hostInfo.applicationInfo == null || hostInfo.applicationInfo.sourceDir == null) {
                return false;
            }

            PackageInfo virtualInfo = TeamDarkCore.getBPackageManager().getPackageInfo(packageName, 0, userId);
            if (virtualInfo == null) {
                return false;
            }

            long hostVer = getLongVersionCodeCompat(hostInfo);
            long virtVer = getLongVersionCodeCompat(virtualInfo);

            File hostBaseApk = new File(hostInfo.applicationInfo.sourceDir);
            File virtBaseApk = new File(BEnvironment.getAppDir(packageName), "base.apk");
            boolean apkSizeChanged = hostBaseApk.isFile()
                    && virtBaseApk.isFile()
                    && hostBaseApk.length() > 0
                    && hostBaseApk.length() != virtBaseApk.length()
                    && hostBaseApk.lastModified() > virtBaseApk.lastModified();

            if (hostVer <= virtVer && !apkSizeChanged) {
                // Still ensure any newly downloaded Play Store .obb file is linked
                com.teamdark.core.TeamDarkAccountSwitcher.ensureSharedObbForSlot(packageName, 0, userId);
                return false;
            }

            Log.i(TAG, "Detected host game update for " + packageName
                    + " (virtual=" + virtVer + " -> host=" + hostVer + "). Syncing in-place...");

            // 1. Update base APK via installBySystem (preserves virtual data/shared_prefs/cookies)
            InstallResult res = TeamDarkCore.getBPackageManager().installPackageAsUser(
                    hostInfo.applicationInfo.sourceDir,
                    InstallOption.installBySystem(),
                    userId);

            // 2. Sync any split APKs (e.g. split_config.arm64_v8a.apk) from host ApplicationInfo
            ApplicationInfo hostAppInfo = hostInfo.applicationInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    && hostAppInfo.splitSourceDirs != null
                    && hostAppInfo.splitSourceDirs.length > 0) {
                File appDir = BEnvironment.getAppDir(packageName);
                File appLibDir = BEnvironment.getAppLibDir(packageName);
                FileUtils.mkdirs(appDir);
                FileUtils.mkdirs(appLibDir);
                for (String splitPath : hostAppInfo.splitSourceDirs) {
                    if (splitPath == null) continue;
                    File splitFile = new File(splitPath);
                    if (splitFile.isFile() && splitFile.length() > 0) {
                        File dstSplit = new File(appDir, splitFile.getName());
                        FileUtils.copyFile(splitFile, dstSplit);
                        try {
                            NativeUtils.copyNativeLib(dstSplit, appLibDir);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            // 3. Re-link updated .obb files and purge stale shader cache so the updated game doesn't black-screen
            com.teamdark.core.TeamDarkAccountSwitcher.ensureSharedObbForSlot(packageName, 0, userId);
            CrashShieldAndResourceHealer.healGameResources(hostCtx, packageName, userId);
            return res != null && res.success;
        } catch (Throwable t) {
            Log.w(TAG, "Hot update sync skipped: " + t.getMessage());
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static long getLongVersionCodeCompat(PackageInfo info) {
        if (info == null) return 0L;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        return info.versionCode;
    }
}
