package net_62v.external;

import android.content.Context;
import androidx.annotation.Keep;
import com.teamdark.core.TeamDarkCore;

/**
 * Drop-in compatibility bridge for Loaders built on net_62v.external.MetaCore.
 */
@Keep
public class MetaCore {

    public static void attachBaseContext(Context context) {
        TeamDarkCore.get().ensureInitialized(context);
    }

    public static void onCreate(Context context) {
        TeamDarkCore.get().ensureInitialized(context);
    }

    public static void init(Context context) {
        TeamDarkCore.get().ensureInitialized(context);
    }
}
