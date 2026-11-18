package com.teamdark.core.fake.service;

import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import black.android.content.BRAttributionSource;
import black.android.media.session.BRISessionManagerStub;
import black.android.os.BRServiceManager;
import com.teamdark.core.TeamDarkCore;
import com.teamdark.core.fake.hook.BinderInvocationStub;
import com.teamdark.core.fake.hook.MethodHook;
import com.teamdark.core.fake.hook.ProxyMethod;
import com.teamdark.core.utils.MethodParameterUtils;
import com.teamdark.core.utils.compat.ContextCompat;

/**
 * Created by @TeamDarkCore on 3/30/21.
 */
public class IMediaSessionManagerProxy extends BinderInvocationStub {

    public IMediaSessionManagerProxy() {
        super(BRServiceManager.get().getService(Context.MEDIA_SESSION_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRISessionManagerStub.get().asInterface(BRServiceManager.get().getService(Context.MEDIA_SESSION_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceAllAppPkg(args);
        MethodParameterUtils.replaceFirstUid(args);
        MethodParameterUtils.replaceLastUid(args);
        if (method != null && method.getName().equals("createSession")) {
            MethodParameterUtils.replaceLastUserId(args);
        }
        if (args != null && BRAttributionSource.getRealClass() != null) {
            String attrClassName = BRAttributionSource.getRealClass().getName();
            for (Object arg : args) {
                if (arg != null && attrClassName.equals(arg.getClass().getName())) {
                    ContextCompat.fixAttributionSourceState(arg, TeamDarkCore.getHostUid());
                }
            }
        }
        try {
            return super.invoke(proxy, method, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException() != null ? e.getTargetException() : e;
        }
    }

    @ProxyMethod("createSession")
    public static class CreateSession extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof String) {
                args[0] = TeamDarkCore.getHostPkg();
            }
            MethodParameterUtils.replaceLastUserId(args);
            return method.invoke(who, args);
        }
    }
}
