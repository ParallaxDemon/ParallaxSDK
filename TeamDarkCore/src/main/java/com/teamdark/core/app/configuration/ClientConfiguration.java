package com.teamdark.core.app.configuration;

import android.content.Context;
import java.io.File;
import com.teamdark.core.TeamDarkCore;

public abstract class ClientConfiguration {

    public String getHostPackageName() {
        Context ctx = TeamDarkCore.getContext();
        return ctx != null ? ctx.getPackageName() : "";
    }

    public boolean setHideRoot() {
        return true;
    }

    public boolean isHideRoot() {
        return setHideRoot();
    }

    public boolean setHideXposed() {
        return true;
    }

    public boolean isHideXposed() {
        return setHideXposed();
    }

    public boolean isEnableDaemonService() {
        return true;
    }

    public boolean isEnableLauncherActivity() {
        return true;
    }

    public boolean requestInstallPackage(File file) {
        return false;
    }
}
