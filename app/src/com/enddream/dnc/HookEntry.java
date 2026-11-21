package com.enddream.dnc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class HookEntry extends XposedModule {

    public static volatile boolean sActive;

    private static final String SELF = "com.enddream.dnc";
    private static final String SETTINGS = "com.android.settings";

    private static final String P_DEVICE = "persist.private.device_name";

    private static final String OV = "dnc_ov_";

    private Map<String, Object> cfg = new HashMap<>();
    private volatile long cfgAt;

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if (SELF.equals(pkg)) {
            sActive = true;
            return;
        }
        if (!SETTINGS.equals(pkg)) {
            return;
        }
        loadCfg(true);
        ClassLoader cl = param.getDefaultClassLoader();
        int n = 0;
        n += hookTextFilters();
        n += hookWatcher(cl);
        n += hookValidators(cl);
        n += hookCloud(cl);
        n += hookResetGuard(cl);
        n += hookWrites(cl);
        n += hookReads(cl);
        syncOverrides();
        log("hooks=" + n + " agreed=" + agreed() + " dev=" + on(Keys.EN_DEVICE));
        beat();
    }

    private void log(String msg) {
        try {
            log(android.util.Log.INFO, "DNC", msg);
        } catch (Throwable ignored) {
        }
    }

    private boolean agreed() {
        return bool(Keys.AGREED);
    }

    private boolean on(String key) {
        return bool(Keys.AGREED) && bool(key);
    }

    private boolean bool(String key) {
        String live = readOv(Keys.CFG + key);
        if (live != null) {
            return "1".equals(live);
        }
        loadCfg(false);
        Object v = cfg.get(key);
        return v instanceof Boolean && (Boolean) v;
    }

    private void loadCfg(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - cfgAt < 800L) {
            return;
        }
        cfgAt = now;
        Map<String, Object> out = new HashMap<>();
        try {
            android.content.SharedPreferences sp = getRemotePreferences(Keys.PREFS);
            Map<String, ?> all = sp == null ? null : sp.getAll();
            if (all != null) {
                for (Map.Entry<String, ?> e : all.entrySet()) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        } catch (Throwable t) {
            log("remote prefs failed: " + t);
        }
        if (out.isEmpty()) {
            out.putAll(readPrefsFile());
        }
        if (out.isEmpty()) {
            out.putAll(readConfFile());
        }
        cfg = out;
    }

    private Map<String, Object> readPrefsFile() {
        Map<String, Object> out = new HashMap<>();
        String[] paths = {
                "/data/user/0/" + SELF + "/shared_prefs/" + Keys.PREFS + ".xml",
                "/data/data/" + SELF + "/shared_prefs/" + Keys.PREFS + ".xml",
                "/data/user_de/0/" + SELF + "/shared_prefs/" + Keys.PREFS + ".xml",
                "/storage/emulated/0/Android/data/" + SELF + "/files/" + Keys.CONF_FILE,
                "/sdcard/Android/data/" + SELF + "/files/" + Keys.CONF_FILE,
        };
        for (String path : paths) {
            java.io.FileInputStream in = null;
            try {
                in = new java.io.FileInputStream(path);
                byte[] buf = new byte[8192];
                int len = in.read(buf);
                if (len <= 0) {
                    continue;
                }
                parsePrefsXml(new String(buf, 0, len, "UTF-8"), out);
                if (!out.isEmpty()) {
                    log("prefs from " + path);
                    return out;
                }
            } catch (Throwable t) {
                // try next path
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    private static void parsePrefsXml(String xml, Map<String, Object> out) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<(boolean|string|int|long)[^>]*name=\"([^\"]+)\"[^>]*value=\"([^\"]*)\"")
                .matcher(xml);
        while (m.find()) {
            String type = m.group(1);
            String key = m.group(2);
            String value = m.group(3);
            if ("boolean".equals(type)) {
                out.put(key, Boolean.valueOf(value));
            } else if ("int".equals(type)) {
                try {
                    out.put(key, Integer.valueOf(value));
                } catch (Throwable ignored) {
                }
            } else if ("long".equals(type)) {
                try {
                    out.put(key, Long.valueOf(value));
                } catch (Throwable ignored) {
                }
            } else {
                out.put(key, value);
            }
        }
    }

    private Map<String, Object> readConfFile() {
        Map<String, Object> out = new HashMap<>();
        ParcelFileDescriptor pfd = null;
        FileInputStream in = null;
        try {
            pfd = openRemoteFile(Keys.CONF_FILE);
            if (pfd == null) {
                return out;
            }
            in = new FileInputStream(pfd.getFileDescriptor());
            byte[] buf = new byte[4096];
            int len = in.read(buf);
            if (len <= 0) {
                return out;
            }
            String text = new String(buf, 0, len, "UTF-8");
            for (String line : text.split("\n")) {
                int i = line.indexOf('=');
                if (i <= 0) {
                    continue;
                }
                String k = line.substring(0, i).trim();
                String v = line.substring(i + 1).trim();
                out.put(k, "true".equals(v) ? Boolean.TRUE : "false".equals(v) ? Boolean.FALSE : v);
            }
        } catch (Throwable t) {
            log("conf file failed: " + t);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (pfd != null) {
                    pfd.close();
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static Class<?> cls(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    private int hookNamed(ClassLoader cl, String className, String methodName,
                          XposedInterface.Hooker hooker) {
        Class<?> c = cls(className, cl);
        if (c == null) {
            return 0;
        }
        int count = 0;
        Method[] all;
        try {
            all = c.getDeclaredMethods();
        } catch (Throwable t) {
            return 0;
        }
        for (Method m : all) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            try {
                m.setAccessible(true);
                hook(m).intercept(hooker);
                count++;
            } catch (Throwable t) {
                log("hook failed " + className + "#" + methodName);
            }
        }
        return count;
    }

    private static Method m(Class<?> c, String name, Class<?>... types) {
        if (c == null) {
            return null;
        }
        try {
            Method x = c.getDeclaredMethod(name, types);
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
        if (o == null) {
            return null;
        }
        Field f = field(o.getClass(), name);
        if (f == null) {
            return null;
        }
        try {
            return f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setField(Object o, String name, Object v) {
        if (o == null) {
            return;
        }
        Field f = field(o.getClass(), name);
        if (f == null) {
            return;
        }
        try {
            f.set(o, v);
        } catch (Throwable ignored) {
        }
    }

    private static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        Method mm = findMethod(target.getClass(), name, args);
        if (mm == null) {
            return null;
        }
        try {
            return mm.invoke(target, args);
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

    private static String str(Object o) {
        return o instanceof String ? (String) o : null;
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

    private static int bytes(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static String fit(String name, int max) {
        if (name == null) {
            return "";
        }
        if (bytes(name) <= max) {
            return name;
        }
        int end = name.length();
        while (end > 0 && bytes(name.substring(0, end)) > max) {
            end--;
        }
        return name.substring(0, end);
    }

    private static Context appContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method cur = at.getDeclaredMethod("currentActivityThread");
            cur.setAccessible(true);
            Object th = cur.invoke(null);
            if (th != null) {
                Method ga = at.getDeclaredMethod("getApplication");
                ga.setAccessible(true);
                Object app = ga.invoke(th);
                if (app instanceof Context) {
                    return (Context) app;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
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

    private void beat() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            private int tries;

            @Override
            public void run() {
                final Context ctx = appContext();
                if (ctx == null) {
                    if (++tries < 10) {
                        new Handler(Looper.getMainLooper()).postDelayed(this, 1000L);
                    }
                    return;
                }
                try {
                    BroadcastReceiver r = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context c, Intent i) {
                            long now = System.currentTimeMillis();
                            if (Keys.CONFIG.equals(i.getAction())) {
                                for (String key : Keys.CONFIG_KEYS) {
                                    if (i.hasExtra(key)) {
                                        putOv(Keys.CFG + key,
                                                i.getBooleanExtra(key, false) ? "1" : "0");
                                    }
                                }
                                cfgAt = 0L;
                                syncOverrides();
                                log("cfg applied agreed=" + bool(Keys.AGREED)
                                        + " dev=" + bool(Keys.EN_DEVICE));
                            }
                            putOv(Keys.BEAT, String.valueOf(now));
                            putOv(Keys.HOOKED, Keys.VERSION + ":" + bootTime());
                        }
                    };
                    IntentFilter f = new IntentFilter(Keys.PING);
                    f.addAction(Keys.CONFIG);
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
                    } else {
                        ctx.registerReceiver(r, f);
                    }
                } catch (Throwable t) {
                    log("receiver failed: " + t);
                }
                long now = System.currentTimeMillis();
                putOv(Keys.BEAT, String.valueOf(now));
                putOv(Keys.HOOKED, Keys.VERSION + ":" + bootTime());
            }
        }, 1200L);
    }

    private static long bootTime() {
        return System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
    }

    private void syncOverrides() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!on(Keys.EN_DEVICE)) {
                        putOv(OV + P_DEVICE, null);
                        String d = prop(P_DEVICE);
                        if (!TextUtils.isEmpty(d) && bytes(d) > 31) {
                            setProp(P_DEVICE, fit(d, 31));
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }, 2000L);
    }

    private static String pageOfView(View v) {
        int id = v.getId();
        if (id == View.NO_ID) {
            return null;
        }
        String entry;
        try {
            entry = v.getResources().getResourceEntryName(id);
        } catch (Throwable t) {
            return null;
        }
        if (entry == null) {
            return null;
        }
        if ("edittext".equals(entry)) {
            return Keys.EN_DEVICE;
        }
        if ("device_name".equals(entry)) {
            return Keys.EN_DEVICE;
        }
        return null;
    }

    private static String pageOf(Object callback) {
        if (callback == null) {
            return null;
        }
        String n = callback.getClass().getName();
        return Keys.EN_DEVICE;
    }

    private static boolean isOverlengthToast(Context ctx, List<Object> args) {
        try {
            for (Object a : args) {
                if (a instanceof Integer) {
                    int id = (Integer) a;
                    if (id <= 0) {
                        continue;
                    }
                    String name = ctx.getResources().getResourceEntryName(id);
                    if ("device_name_input_overlength".equals(name)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
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

    private int hookTextFilters() {
        int n = 0;
        Method setFilters = m(TextView.class, "setFilters", InputFilter[].class);
        if (setFilters != null) {
            try {
                hook(setFilters).intercept(chain -> {
                    Object self = chain.getThisObject();
                    Object a = chain.getArg(0);
                    if (self instanceof View && a instanceof InputFilter[]) {
                        String page = pageOfView((View) self);
                        if (page != null && on(Keys.EN_DEVICE)) {
                            InputFilter[] out = withoutLength((InputFilter[]) a);
                            if (out != a) {
                                return chain.proceed(new Object[]{out});
                            }
                        }
                    }
                    return chain.proceed();
                });
                n++;
            } catch (Throwable t) {
                log("setFilters failed");
            }
        }
        Method setText = m(TextView.class, "setText", CharSequence.class);
        if (setText != null) {
            try {
                hook(setText).intercept(chain -> {
                    Object self = chain.getThisObject();
                    if (self instanceof View) {
                        String page = pageOfView((View) self);
                        if (page != null && (on(Keys.EN_DEVICE) || on(Keys.EN_DEVICE)
                                || on(page))) {
                            Object f = getField(self, "mFilters");
                            if (f instanceof InputFilter[]) {
                                setField(self, "mFilters", withoutLength((InputFilter[]) f));
                            }
                        }
                    }
                    return chain.proceed();
                });
                n++;
            } catch (Throwable t) {
                log("setText failed");
            }
        }
        return n;
    }

    private int hookWatcher(ClassLoader cl) {
        Class<?> w = cls("com.android.settings.MiuiDeviceNameEditFragment$LengthTextWatcher", cl);
        if (w == null) {
            return 0;
        }
        int n = 0;
        Constructor<?>[] ctors;
        try {
            ctors = w.getDeclaredConstructors();
        } catch (Throwable t) {
            return 0;
        }
        for (Constructor<?> k : ctors) {
            try {
                k.setAccessible(true);
                hook(k).intercept(chain -> {
                    List<Object> args = chain.getArgs();
                    if (args.size() < 2 || !(args.get(0) instanceof Integer)) {
                        return chain.proceed();
                    }
                    Object callback = args.get(1);
                    String page = pageOf(callback);
                    if (page == null || !on(page)) {
                        return chain.proceed();
                    }
                    Object[] out = args.toArray();
                    out[0] = Integer.MAX_VALUE / 2;
                    return chain.proceed(out);
                });
                n++;
            } catch (Throwable t) {
                log("watcher ctor failed");
            }
        }
        return n;
    }

    private int hookValidators(ClassLoader cl) {
        int n = 0;
        n += hookNamed(cl, "com.android.settingslib.util.ToastUtil", "show", chain -> {
            if (!on(Keys.EN_DEVICE) && !on(Keys.EN_DEVICE)) {
                return chain.proceed();
            }
            for (Object a : chain.getArgs()) {
                if (a instanceof Context && isOverlengthToast((Context) a, chain.getArgs())) {
                    return null;
                }
            }
            return chain.proceed();
        });
        n += hookNamed(cl, "com.android.settings.MiuiDeviceNameEditFragment$2", "beyondLimit",
                chain -> on(Keys.EN_DEVICE) ? null : chain.proceed());
        return n;
    }

    private int hookCloud(ClassLoader cl) {
        int n = 0;
        n += hookNamed(cl, "com.android.settings.DeviceNameCheckManager", "getDeviceNameCheckResult", chain -> {
            if (!on(Keys.EN_CLOUD)) {
                return chain.proceed();
            }
            Object callback = chain.getArg(3);
            String name = str(chain.getArg(1));
            if (callback != null && name != null) {
                new Handler(Looper.getMainLooper()).post(() -> call(callback, "resolveResult", name));
            }
            return null;
        });
        return n;
    }

    private int hookResetGuard(ClassLoader cl) {
        int n = 0;
        n += hookNamed(cl, "com.android.settings.MiuiUtils", "checkDeviceNameIsillegalSync",
                chain -> on(Keys.EN_RESET) ? null : chain.proceed());
        n += hookNamed(cl, "com.android.settings.MiuiDeviceNameEditFragment", "resetToDefault",
                chain -> on(Keys.EN_RESET) ? null : chain.proceed());
        return n;
    }

    private int hookWrites(ClassLoader cl) {
        int n = 0;
        n += hookNamed(cl, "com.android.settings.MiuiUtils", "setDeviceName", chain -> {
            List<Object> args = chain.getArgs();
            if (args.size() < 2) {
                return chain.proceed();
            }
            String name = str(args.get(1));
            if (TextUtils.isEmpty(name)) {
                return chain.proceed();
            }
            if (!on(Keys.EN_DEVICE)) {
                putOv(OV + P_DEVICE, null);
                Object[] out = args.toArray();
                out[1] = name;
                return chain.proceed(out);
            }
            putOv(OV + P_DEVICE, name);
            return chain.proceed();
        });
        return n;
    }

    private int hookReads(ClassLoader cl) {
        int n = 0;
        n += hookNamed(cl, "android.provider.MiuiSettings$System", "getDeviceName", chain -> {
            Object r = chain.proceed();
            if (!on(Keys.EN_DEVICE)) {
                return r;
            }
            String ov = readOv(OV + P_DEVICE);
            return ov != null ? ov : r;
        });
        return n;
    }
}
