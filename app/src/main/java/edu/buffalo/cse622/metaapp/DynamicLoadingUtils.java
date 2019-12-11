package edu.buffalo.cse622.metaapp;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import dalvik.system.PathClassLoader;

/**
 * This class provides the methods that help initialize a Resources object to dynamically load resources from the plugins.
 *
 * This link is a good resource to learn how to load classes and resources dynamically:
 * // @see <a href="https://stackoverflow.com/questions/7483568/dynamic-resource-loading-from-other-apk">Dynamically load resources (Stack Overflow link)</a>
 */

class DynamicLoadingUtils {

    private static final String TAG = DynamicLoadingUtils.class.getSimpleName();
    /**
     * This method loads the apk using the file's InputStream.
     *
     * @param apkInputStream
     * @return
     */
    static File loadApk(Context context, InputStream apkInputStream, String apkName) {
        File targetApk = new File(context.getDir("dex", Context.MODE_PRIVATE), apkName);

        if (!targetApk.exists() || !targetApk.isFile()) {
            try (BufferedInputStream bis = new BufferedInputStream(apkInputStream);
                 OutputStream dexWriter = new BufferedOutputStream(
                         new FileOutputStream(targetApk))) {

                byte[] buf = new byte[4096];
                int len;
                while ((len = bis.read(buf, 0, 4096)) > 0) {
                    dexWriter.write(buf, 0, len);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Log.d(TAG, "loadApk: " + "APK file " + apkName + " loaded.");
        return targetApk;
    }

    /**
     * Initialize the dynamicResources object that is used for loading all the plugin resources.
     *
     * @param targetApk
     * @return
     */
    static Resources loadResources(Context context, File targetApk) {
        Resources dynamicResources = null;

        PathClassLoader loader = new PathClassLoader(
                targetApk.getAbsolutePath(), context.getClassLoader());
        try {
            AssetManager assets = AssetManager.class.newInstance();
            Method addAssetPath = AssetManager.class
                    .getMethod("addAssetPath", String.class);
            if (addAssetPath.invoke(assets, targetApk.getAbsolutePath()) ==
                    Integer.valueOf(0)) {
                throw new RuntimeException();
            }

            Class<?> resourcesImpl = Class.forName("android.content.res.ResourcesImpl");
            Class<?> daj = Class.forName("android.view.DisplayAdjustments");
            Object impl = resourcesImpl
                    .getConstructor(AssetManager.class, DisplayMetrics.class,
                            Configuration.class, daj)
                    .newInstance(assets, context.getResources().getDisplayMetrics(),
                            context.getResources().getConfiguration(), daj.newInstance());

            dynamicResources = Resources.class.getConstructor(ClassLoader.class)
                    .newInstance(loader);
            Method setImpl = Resources.class.getMethod("setImpl",
                    Class.forName("android.content.res.ResourcesImpl"));
            setImpl.invoke(dynamicResources, impl);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "loadResources: " + "dynamicResources initialized.");
        return dynamicResources;
    }
}
