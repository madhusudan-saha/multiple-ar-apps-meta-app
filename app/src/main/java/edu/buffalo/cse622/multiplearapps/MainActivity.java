package edu.buffalo.cse622.multiplearapps;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.ar.core.Frame;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

public class MainActivity extends AppCompatActivity {

    Context context = null;
    Map<Class<Object>, Object> pluginMap = new HashMap();
    private ArFragment arFragment;

    File targetApk = null;
    Resources dynamicResources = null;
    PathClassLoader loader = null;

    Class<?> dynamicClass = null;
    Object dynamicInstance = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestAppPermissions();

        Toolbar mainToolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(mainToolbar);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);

        // Add a frame update listener to the scene to control the state of the buttons.
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onFrameUpdate);

        context = this;
        /*
        ViewRenderable.builder()
                .setView(this, R.layout.text_view)
                .build()
                .thenAccept(
                        (renderable) -> {
                            textRenderable = renderable;
                        });
        */
        targetApk = loadApk();
        loadResources(targetApk);

        try {
            // Get the resources
            int someStringId = dynamicResources.getIdentifier(
                    "someString", "string", "edu.buffalo.cse622.pottedplantplugin");
            String someString = dynamicResources.getString(someStringId);

            Class<?> rString = Class.forName("edu.buffalo.cse622.pottedplantplugin.R$string", true, loader);
            int anotherStringId = rString.getField("anotherString").getInt(null);
            String anotherString = dynamicResources.getString(anotherStringId);

            Toast.makeText(this, someString, Toast.LENGTH_LONG).show();
            Toast.makeText(this, anotherString, Toast.LENGTH_LONG).show();

            /*
            int layoutId = dynamicResources.getIdentifier("text_view", "layout","edu.buffalo.cse622.pottedplantplugin");
            XmlResourceParser textViewXml = dynamicResources.getLayout(layoutId);
            View view = getLayoutInflater().inflate(textViewXml, null);

            ViewRenderable.builder()
                    .setView(this, view)
                    .build()
                    .thenAccept(
                            (renderable) -> {
                                textRenderable = renderable;
                            });
            */

            PathClassLoader loader = new PathClassLoader(
                    targetApk.getAbsolutePath(), getClassLoader());
            dynamicClass = loader.loadClass("edu.buffalo.cse622.pottedplantplugin.FrameOperations");
            Constructor<?> ctor = dynamicClass.getConstructor(Resources.class, Context.class);
            dynamicInstance = ctor.newInstance(dynamicResources, context);

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void requestAppPermissions() {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        if (hasReadPermissions() && hasWritePermissions()) {
            return;
        }

        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 101);
    }

    private boolean hasReadPermissions() {
        return (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean hasWritePermissions() {
        return (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.import_app) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            startActivityForResult(Intent.createChooser(intent, "Choose jar to import"), 102);

            //return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 102 && resultCode == RESULT_OK) {
            Uri selectedfile = data.getData(); //The uri with the location of the file

            String path = Environment.getExternalStorageDirectory() + "/" + selectedfile.getPath().split(":")[1];
            Log.d("onActivityResult", path);

            Class<Object> classToLoad = getAppInstance(path);
        }
    }

    Class<Object> getAppInstance(String jarPath) {
        Class<Object> classToLoad = null;

        try {
            File tmpDir = getDir("dex", 0);
            DexClassLoader classloader = new DexClassLoader(jarPath, tmpDir.getAbsolutePath(), null, this.getClass().getClassLoader());
            classToLoad = (Class<Object>) classloader.loadClass("edu.buffalo.cse622.pottedplant.FrameOperations");
            Object instance = classToLoad.newInstance();

            if (!pluginMap.containsKey(classToLoad)) {
                pluginMap.put(classToLoad, instance);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return classToLoad;
    }

    private void onFrameUpdate(FrameTime unusedframeTime) {

        Frame frame = arFragment.getArSceneView().getArFrame();

        /*
        FrameOperations frameOperations = new FrameOperations();
        Node node = frameOperations.processFrame(frame, textRenderable);
        if (node != null) {
            node.getParent().setParent(arFragment.getArSceneView().getScene());
        }
        */
        try {
            Class[] classArguments = new Class[1];
            classArguments[0] = Frame.class;

            Method processFrame = dynamicClass.getDeclaredMethod("processFrame", classArguments);
            processFrame.setAccessible(true);  // To invoke protected or private methods
            AnchorNode anchorNode = (AnchorNode) processFrame.invoke(dynamicInstance, frame);
            if (anchorNode != null) {
                anchorNode.setParent(arFragment.getArSceneView().getScene());
            }
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        /*
        for (Map.Entry<Class<Object>, Object> entry : pluginMap.entrySet()) {
            Class<Object> classToLoad = entry.getKey();
            Object instance = entry.getValue();

            try {
                Class[] classArguments = new Class[2];
                classArguments[0] = Frame.class;
                classArguments[1] = ViewRenderable.class;
                System.out.println(classToLoad.getDeclaredMethods()[0]);
                Method processFrame = classToLoad.getDeclaredMethod("processFrame", classArguments);
                processFrame.setAccessible(true);  // To invoke protected or private methods
                Node node = (Node) processFrame.invoke(instance, frame, textRenderable);
                if (node != null) {
                    node.getParent().setParent(arFragment.getArSceneView().getScene());
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        */
    }

    File loadApk() {
        File targetApk = new File(getDir("dex", Context.MODE_PRIVATE), "app.apk");
        // copy APK from assets to private directory
        // remove this condition in order to keep dynamic APK fresh
        if (!targetApk.exists() || !targetApk.isFile()) {
            try (BufferedInputStream bis = new BufferedInputStream(
                    getAssets().open("app-debug.apk"));
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

        return targetApk;
    }

    void loadResources(File targetApk) {
        loader = new PathClassLoader(
                targetApk.getAbsolutePath(), getClassLoader());
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
                    .newInstance(assets, getResources().getDisplayMetrics(),
                            getResources().getConfiguration(), daj.newInstance());

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
    }

}
