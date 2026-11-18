package com.vcore;

import androidx.annotation.Keep;
import com.teamdark.core.TeamDarkCore;

/**
 * Drop-in compatibility alias for Loaders importing com.vcore.BlackBoxCore.
 */
@Keep
public class BlackBoxCore extends TeamDarkCore {
    public static TeamDarkCore get() {
        return TeamDarkCore.get();
    }
}
