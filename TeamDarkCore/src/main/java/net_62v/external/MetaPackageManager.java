package net_62v.external;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import androidx.annotation.Keep;
import java.util.ArrayList;
import java.util.List;
import com.teamdark.core.TeamDarkCore;

/**
 * Drop-in compatibility bridge for Loaders built on net_62v.external.MetaPackageManager.
 */
@Keep
public class MetaPackageManager {

    public static boolean isInnerAppInstalled(String packageName) {
        return TeamDarkCore.get().isInstalled(packageName, 0);
    }

    public static boolean isInnerAppInstalled(String packageName, int userId) {
        return TeamDarkCore.get().isInstalled(packageName, userId);
    }

    public static void uninstallApp(String packageName) {
        TeamDarkCore.get().uninstallPackage(packageName);
    }

    public static void uninstallApp(String packageName, int userId) {
        TeamDarkCore.get().uninstallPackageAsUser(packageName, userId);
    }

    public static void cleanAppData(String packageName) {
        TeamDarkCore.get().clearPackage(packageName, 0);
    }

    public static void cleanAppData(String packageName, int userId) {
        TeamDarkCore.get().clearPackage(packageName, userId);
    }

    public static void cleanAppCache(String packageName) {
        TeamDarkCore.get().clearAppCache(packageName, 0);
    }

    public static List<String> getInstalledInnerApps() {
        List<String> result = new ArrayList<>();
        try {
            List<ApplicationInfo> apps = TeamDarkCore.get().getInstalledApplications(0, 0);
            if (apps != null) {
                for (ApplicationInfo ai : apps) {
                    if (ai != null && ai.packageName != null) {
                        result.add(ai.packageName);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    public static PackageInfo getPackageInfo(String packageName) {
        return TeamDarkCore.getBPackageManager().getPackageInfo(packageName, 0, 0);
    }
}
