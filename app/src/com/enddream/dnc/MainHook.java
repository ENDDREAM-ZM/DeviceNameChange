package com.enddream.dnc;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET = "com.android.settings";
    private static final String PKG = "com.enddream.dnc";
    private static final String P_DEVICE = "persist.private.device_name";
    private static final String P_BT = "persist.sys.bt_local_name";
    private static final String P_P2P1 = "persist.sys.p2p1_local_name";
    private static final String OV = "dnc_ov_";
    private static final String OV_SSID = "dnc_ov_ssid";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) {
            return;
        }
        ClassLoader cl = lp.classLoader;
        hookLengthWatcher(cl);
        hookTextViews(cl);
        hookSsidValidators(cl);
        hookCloudCheck(cl);
        hookResetGuard(cl);
        hookWrites(cl);
        hookReads(cl);
        reportLoaded();
    }

    private static void log(String msg) {
        XposedBridge.log("DNC " + msg);
    }

    private static Class<?> cls(String name, ClassLoader cl) {
        return XposedHelpers.findClassIfExists(name, cl);
    }

    private static Method m(Class<?> c, String name, Class<?>... types) {
        try {
            Method x = c.getDeclaredMethod(name, types);
            x.setAccessible(true);
            return x;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Constructor<?> ctor(Class<?> c, Class<?>... types) {
        try {
            Constructor<?> x = c.getDeclaredConstructor(types);
            x.setAccessible(true);
            return x;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field field(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable t) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private static Object getField(Object o, String name) {
        Field f = field(o.getClass(), name);
        try {
            return f == null ? null : f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setField(Object o, String name, Object v) {
        Field f = field(o.getClass(), name);
        try {
            if (f != null) {
                f.set(o, v);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object call(Object target, String name, Object... args) {
        Method mm = findMethod(target.getClass(), name, args);
        try {
            return mm == null ? null : mm.invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object callStatic(Class<?> c, String name, Object... args) {
        Method mm = findMethod(c, name, args);
        try {
            return mm == null ? null : mm.invoke(null, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findMethod(Class<?> c, String name, Object[] args) {
        Class<?> cur = c;
        while (cur != null) {
            for (Method mm : cur.getDeclaredMethods()) {
                if (!mm.getName().equals(name) || mm.getParameterCount() != args.length) {
                    continue;
                }
                Class<?>[] types = mm.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < types.length; i++) {
                    Object a = args[i];
                    if (a == null) {
                        if (types[i].isPrimitive()) {
                            ok = false;
                            break;
                        }
                        continue;
                    }
                    Class<?> boxed = types[i].isPrimitive() ? wrap(types[i]) : types[i];
                    if (!boxed.isInstance(a)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    mm.setAccessible(true);
                    return mm;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Class<?> wrap(Class<?> t) {
        if (t == int.class) return Integer.class;
        if (t == boolean.class) return Boolean.class;
        if (t == long.class) return Long.class;
        if (t == short.class) return Short.class;
        if (t == byte.class) return Byte.class;
        if (t == char.class) return Character.class;
        if (t == float.class) return Float.class;
        if (t == double.class) return Double.class;
        return t;
    }

    private static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v instanceof String ? (String) v : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static void setProp(String key, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, key, value);
        } catch (Throwable ignored) {
        }
    }

    private static String fit(String name, int max) {
        if (name == null) {
            return "";
        }
        if (name.getBytes().length <= max) {
            return name;
        }
        int end = name.length();
        while (end > 0 && name.substring(0, end).getBytes().length > max) {
            end--;
        }
        return name.substring(0, end);
    }

    private static Context appContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = callStatic(at, "currentApplication");
            return app instanceof Context ? (Context) app : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String readOv(String key) {
        Context ctx = appContext();
        if (ctx == null) {
            return null;
        }
        try {
            String v = Settings.Global.getString(ctx.getContentResolver(), key);
            return TextUtils.isEmpty(v) ? null : v;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void putOv(String key, String value) {
        Context ctx = appContext();
        if (ctx == null) {
            return;
        }
        try {
            Settings.Global.putString(ctx.getContentResolver(), key, value);
        } catch (Throwable ignored) {
        }
    }

    private static void hookLengthWatcher(ClassLoader cl) {
        try {
            Class<?> w = cls("com.android.settings.MiuiDeviceNameEditFragment$LengthTextWatcher", cl);
            if (w == null) {
                return;
            }
            XposedBridge.hookAllConstructors(w, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0 && param.args[0] instanceof Integer) {
                        param.args[0] = Integer.MAX_VALUE / 2;
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    setField(param.thisObject, "mMaxLength", Integer.MAX_VALUE / 2);
                }
            });
        } catch (Throwable t) {
            log("length watcher hook failed");
        }
    }

    private static void hookTextViews(ClassLoader cl) {
        try {
            Class<?> tv = cls("android.widget.TextView", cl);
            if (tv == null) {
                return;
            }
            XposedBridge.hookAllMethods(tv, "setFilters", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length != 1
                                || !(param.args[0] instanceof InputFilter[])) {
                            return;
                        }
                        Object self = param.thisObject;
                        if (self instanceof View && isNameField((View) self)) {
                            param.args[0] = withoutLength((InputFilter[]) param.args[0]);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });
            XposedBridge.hookAllMethods(tv, "setText", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (!(self instanceof View) || !isNameField((View) self)) {
                            return;
                        }
                        Object f = getField(self, "mFilters");
                        if (f instanceof InputFilter[]) {
                            setField(self, "mFilters", withoutLength((InputFilter[]) f));
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });
        } catch (Throwable t) {
            log("text view hook failed");
        }
    }

    private static boolean isNameField(View v) {
        int id = v.getId();
        if (id == View.NO_ID) {
            return false;
        }
        try {
            String entry = v.getResources().getResourceEntryName(id);
            if (entry == null) {
                return false;
            }
            return "device_name".equals(entry) || "ssid".equals(entry) || "edittext".equals(entry);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isLengthFilter(InputFilter f) {
        String n = f.getClass().getSimpleName();
        return n.contains("LengthFilter") || n.contains("ByteLength");
    }

    private static InputFilter[] withoutLength(InputFilter[] input) {
        int keep = 0;
        for (InputFilter f : input) {
            if (!isLengthFilter(f)) {
                keep++;
            }
        }
        if (keep == input.length) {
            return input;
        }
        InputFilter[] out = new InputFilter[keep];
        int i = 0;
        for (InputFilter f : input) {
            if (!isLengthFilter(f)) {
                out[i++] = f;
            }
        }
        return out;
    }

    private static void hookSsidValidators(ClassLoader cl) {
        try {
            Class<?> utils = cls("com.android.settings.wifi.WifiUtils", cl);
            if (utils != null) {
                XposedHelpers.findAndHookMethod(utils, "isSSIDTooLong", String.class,
                        XC_MethodReplacement.returnConstant(false));
            }
            Class<?> validator = cls("com.android.settings.wifi.tether.WifiDeviceNameTextValidator", cl);
            if (validator != null) {
                XposedHelpers.findAndHookMethod(validator, "isTextValid", String.class,
                        new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) {
                                String v = (String) param.args[0];
                                return v != null && !TextUtils.isEmpty(v.trim());
                            }
                        });
            }
        } catch (Throwable t) {
            log("ssid validator hook failed");
        }
    }

    private static void hookCloudCheck(ClassLoader cl) {
        try {
            Class<?> mgr = cls("com.android.settings.DeviceNameCheckManager", cl);
            if (mgr == null) {
                return;
            }
            XposedHelpers.findAndHookMethod(mgr, "getDeviceNameCheckResult",
                    Context.class, String.class, int.class,
                    "com.android.settings.DeviceNameCheckManager$GetResultSuccessCallback",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            final Object cb = param.args[3];
                            final String name = (String) param.args[1];
                            if (cb == null || name == null) {
                                return null;
                            }
                            new Handler(Looper.getMainLooper()).post(() ->
                                    call(cb, "resolveResult", name));
                            return null;
                        }
                    });
        } catch (Throwable t) {
            log("cloud hook failed");
        }
    }

    private static void hookResetGuard(ClassLoader cl) {
        try {
            Class<?> utils = cls("com.android.settings.MiuiUtils", cl);
            if (utils != null) {
                XposedHelpers.findAndHookMethod(utils, "checkDeviceNameIsillegalSync",
                        Context.class, int.class, XC_MethodReplacement.DO_NOTHING);
            }
            Class<?> fragment = cls("com.android.settings.MiuiDeviceNameEditFragment", cl);
            if (fragment != null) {
                XposedHelpers.findAndHookMethod(fragment, "resetToDefault",
                        Context.class, int.class, XC_MethodReplacement.DO_NOTHING);
            }
        } catch (Throwable t) {
            log("reset guard hook failed");
        }
    }

    private static void hookWrites(ClassLoader cl) {
        try {
            Class<?> utils = cls("com.android.settings.MiuiUtils", cl);
            if (utils != null) {
                XposedHelpers.findAndHookMethod(utils, "setDeviceName", Context.class, String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String name = (String) param.args[1];
                                if (TextUtils.isEmpty(name)) {
                                    return;
                                }
                                putOv(OV + P_DEVICE, name);
                                param.args[1] = fit(name, 31);
                            }
                        });
                XposedHelpers.findAndHookMethod(utils, "setP2p1DeviceName", String.class,
                        new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) {
                                String name = (String) param.args[0];
                                if (!TextUtils.isEmpty(name)) {
                                    setProp(P_P2P1, fit(name, 30));
                                    putOv(OV + P_P2P1, name);
                                }
                                return null;
                            }
                        });
            }
            Class<?> bt = cls("com.android.settings.bluetooth.MiuiBTUtils", cl);
            if (bt != null) {
                XposedHelpers.findAndHookMethod(bt, "setBluetoothName", String.class,
                        new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) {
                                String name = (String) param.args[0];
                                if (!TextUtils.isEmpty(name)) {
                                    setProp(P_BT, fit(name, 31));
                                    putOv(OV + P_BT, name);
                                }
                                return null;
                            }
                        });
            }
            Class<?> wm = cls("android.net.wifi.WifiManager", cl);
            Class<?> softAp = cls("android.net.wifi.SoftApConfiguration", cl);
            if (wm != null && softAp != null) {
                XposedHelpers.findAndHookMethod(wm, "setSoftApConfiguration", softAp,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    Object cfg = param.args[0];
                                    if (cfg == null) {
                                        return;
                                    }
                                    String name = null;
                                    Object ws = getField(cfg, "mWifiSsid");
                                    if (ws != null) {
                                        Object s = call(ws, "toString");
                                        if (s instanceof CharSequence) {
                                            name = s.toString();
                                        }
                                    }
                                    if (TextUtils.isEmpty(name)) {
                                        Object s2 = call(cfg, "getSsid");
                                        if (s2 instanceof CharSequence) {
                                            name = s2.toString();
                                        }
                                    }
                                    if (TextUtils.isEmpty(name)) {
                                        return;
                                    }
                                    putOv(OV_SSID, name);
                                    String safe = fit(name, 32);
                                    if (safe.equals(name)) {
                                        return;
                                    }
                                    Constructor<?> bc = ctor(
                                            cls("android.net.wifi.SoftApConfiguration$Builder", cl),
                                            softAp);
                                    if (bc == null) {
                                        return;
                                    }
                                    Object b = bc.newInstance(cfg);
                                    call(b, "setSsid", safe);
                                    Object built = call(b, "build");
                                    if (built != null) {
                                        param.args[0] = built;
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log(t);
                                }
                            }
                        });
            }
        } catch (Throwable t) {
            log("write hook failed");
        }
    }

    private static void hookReads(ClassLoader cl) {
        try {
            Class<?> miui = cls("android.provider.MiuiSettings$System", cl);
            if (miui != null) {
                XposedHelpers.findAndHookMethod(miui, "getDeviceName", Context.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                String ov = readOv(OV + P_DEVICE);
                                if (ov != null) {
                                    param.setResult(ov);
                                }
                            }
                        });
            }
            Class<?> bt = cls("com.android.settings.bluetooth.MiuiBTUtils", cl);
            if (bt != null) {
                XposedHelpers.findAndHookMethod(bt, "getBluetoothName", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String ov = readOv(OV + P_BT);
                        if (ov != null) {
                            param.setResult(ov);
                        }
                    }
                });
            }
            Class<?> utils = cls("com.android.settings.MiuiUtils", cl);
            if (utils != null) {
                XposedHelpers.findAndHookMethod(utils, "getP2p1DeviceName", Context.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                String ov = readOv(OV + P_P2P1);
                                if (ov != null) {
                                    param.setResult(ov);
                                }
                            }
                        });
            }
            Class<?> ctxs = cls("com.android.settings.wifi.utils.ContextsKt", cl);
            if (ctxs != null) {
                XposedHelpers.findAndHookMethod(ctxs, "getWifiSoftApSsid", Context.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Object real = param.getResult();
                                String ov = readOv(OV_SSID);
                                if (ov != null && (real == null
                                        || ov.startsWith(String.valueOf(real)))) {
                                    param.setResult(ov);
                                }
                            }
                        });
            }
        } catch (Throwable t) {
            log("read hook failed");
        }
    }

    private static void reportLoaded() {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            int tries = 0;

            @Override
            public void run() {
                try {
                    Context ctx = appContext();
                    if (ctx == null) {
                        if (++tries < 12) {
                            handler.postDelayed(this, 1000);
                        }
                        return;
                    }
                    Intent i = new Intent("com.enddream.dnc.ACTION_HOOK_LOADED");
                    i.setPackage(PKG);
                    i.putExtra("time", System.currentTimeMillis());
                    ctx.sendBroadcast(i);
                    log("loaded reported");
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        }, 2000);
    }
}
