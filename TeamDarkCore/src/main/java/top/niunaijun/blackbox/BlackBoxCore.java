package top.niunaijun.blackbox;

import androidx.annotation.Keep;
import com.teamdark.core.TeamDarkCore;

/**
 * Drop-in compatibility alias for Loaders importing top.niunaijun.blackbox.BlackBoxCore.
 */
@Keep
public class BlackBoxCore extends TeamDarkCore {
    private static final BlackBoxCore sInstance = new BlackBoxCore();

    public static TeamDarkCore get() {
        return TeamDarkCore.get();
    }

    public static BlackBoxCore getBlackBoxCore() {
        return sInstance;
    }
}
