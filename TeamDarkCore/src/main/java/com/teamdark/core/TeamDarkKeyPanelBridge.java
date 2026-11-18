package com.teamdark.core;

import android.MetaCore.AdvancedPopupHelper;
import android.content.Context;

import androidx.annotation.Keep;

import com.teamdark.core.TeamDarkCore;
import com.teamdark.core.utils.compat.NetworkSecurityGuard;

/**
 * 1-Line Plug-and-Play Key Panel Bridge for TeamDarkCore.
 *
 * By default, keylessTestingMode is TRUE so the SDK runs 100% without a key
 * during development and testing. When your custom Key Panel is ready, call
 * enableCustomKeyPanel(...) or setKeyValidator(...) to enforce your license check.
 */
@Keep
public final class TeamDarkKeyPanelBridge {
    public interface KeyValidator {
        boolean validateKey(Context context, String userKey);
    }

    private static volatile boolean sKeylessTestingMode = true;
    private static volatile String sPanelEndpointUrl = "";
    private static volatile String sPanelPublicKeyBase64 = "";
    private static volatile KeyValidator sCustomValidator;

    private TeamDarkKeyPanelBridge() {
    }

    public static boolean isKeylessTestingMode() {
        return sKeylessTestingMode;
    }

    public static void setKeylessTestingMode(boolean keyless) {
        sKeylessTestingMode = keyless;
    }

    public static void configureCustomKeyPanel(
            String panelEndpointUrl, String publicKeyBase64, boolean enforceKeyCheck) {
        sPanelEndpointUrl = panelEndpointUrl != null ? panelEndpointUrl.trim() : "";
        sPanelPublicKeyBase64 = publicKeyBase64 != null ? publicKeyBase64.trim() : "";
        sKeylessTestingMode = !enforceKeyCheck;
    }

    public static void setCustomKeyValidator(KeyValidator validator) {
        sCustomValidator = validator;
    }

    public static String getPanelEndpointUrl() {
        return sPanelEndpointUrl;
    }

    public static String getPanelPublicKeyBase64() {
        return sPanelPublicKeyBase64;
    }

    public static boolean verifyKeyOrTestingMode(Context context, String userKey) {
        Context ctx = context != null ? context : TeamDarkCore.getContext();
        if (!TeamDarkAntiCrackVault.verifyEnvironmentAndApkIntegrity(ctx)) {
            showNeonStatusPopup();
            return false;
        }
        if (sKeylessTestingMode) {
            TeamDarkAntiCrackVault.unlockLoaderLoginGate(ctx, userKey);
            return true;
        }
        if (!NetworkSecurityGuard.isNetworkEnvironmentSafe(ctx)) {
            showNeonStatusPopup();
            return false;
        }
        KeyValidator validator = sCustomValidator;
        if (validator != null) {
            boolean valid = validator.validateKey(ctx, userKey);
            if (!valid) {
                showNeonStatusPopup();
                return false;
            }
            return TeamDarkAntiCrackVault.unlockLoaderLoginGate(ctx, userKey);
        }
        return true;
    }

    public static void showNeonStatusPopup() {
        try {
            AdvancedPopupHelper.showAuto();
        } catch (Throwable ignored) {
        }
    }

    public static void dismissNeonStatusPopup() {
        try {
            AdvancedPopupHelper.dismiss();
        } catch (Throwable ignored) {
        }
    }
}
