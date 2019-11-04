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
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;

import com.google.ar.core.Frame;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import dalvik.system.PathClassLoader;

public class MainActivity extends AppCompatActivity {

    Context context = null;
    // Map with {Key:Value} pair as {PluginName:HashMap of class instances}.
    // Inner map with {Key:Value} pair as {ClassName:Object array of size 2 - [Class, Object instance of class]}
    Map<String, HashMap<String, Object[]>> pluginMap = new HashMap();
    private ArFragment arFragment;

    // To control the refresh rate of frame
    private long frameTimestamp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestAppPermissions();

        Toolbar mainToolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(mainToolbar);
        context = this;

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);

        // Add a frame update listener to the scene to control the state of the buttons.
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onFrameUpdate);

        pluginMap = new HashMap<>();
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
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            startActivityForResult(Intent.createChooser(intent, "Choose apk to import"), 102);
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 102 && resultCode == RESULT_OK) {
            Uri selectedFile = data.getData(); //The uri with the location of the file

            try {
                // Get file name and file InputStream
                Cursor returnCursor =
                        getContentResolver().query(selectedFile, null, null, null, null);
                returnCursor.moveToFirst();
                int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                String pluginName = returnCursor.getString(nameIndex);
                InputStream apkInputStream = getContentResolver().openInputStream(selectedFile);

                File targetApk = loadApk(apkInputStream);
                Resources dynamicResources = loadResources(targetApk);

                PathClassLoader loader = new PathClassLoader(
                        targetApk.getAbsolutePath(), getClassLoader());
                Class<?> dynamicClass = loader.loadClass("edu.buffalo.cse622.pottedplantplugin.FrameOperations");
                Constructor<?> ctor = dynamicClass.getConstructor(Resources.class, Context.class);
                Object dynamicInstance = ctor.newInstance(dynamicResources, context);

                // Add the class instances to pluginMap
                if (pluginMap.containsKey(pluginName)) {
                    HashMap<String, Object[]> instanceMap = pluginMap.get(pluginName);
                    instanceMap.put("FrameOperations", new Object[]{dynamicClass, dynamicInstance});
                    pluginMap.put(pluginName, instanceMap);
                } else {
                    HashMap<String, Object[]> instanceMap = new HashMap<>();
                    instanceMap.put("FrameOperations", new Object[]{dynamicClass, dynamicInstance});
                    pluginMap.put(pluginName, instanceMap);
                }
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
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void onFrameUpdate(FrameTime unusedframeTime) {

        Frame frame = arFragment.getArSceneView().getArFrame();
        // Frame refresh rate 5 seconds
        if (frame == null || ((frame.getTimestamp() - frameTimestamp) / 1000000000) < 5) {
            return;
        }

        // Invoke processFrame() on all plugins
        for (Map.Entry<String, HashMap<String, Object[]>> entry : pluginMap.entrySet()) {
            Object[] instanceClassAndObject = entry.getValue().get("FrameOperations");
            Class<?> dynamicClass = (Class<?>) instanceClassAndObject[0];
            Object dynamicInstance = instanceClassAndObject[1];

            try {
                Method processFrame = dynamicClass.getDeclaredMethod("processFrame", Frame.class);
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

            frameTimestamp = frame.getTimestamp();
        }

    }

    /**
     * This method loads the apk using the file's InputStream.
     *
     * @param apkInputStream
     * @return
     */
    File loadApk(InputStream apkInputStream) {
        File targetApk = new File(getDir("dex", Context.MODE_PRIVATE), "app.apk");

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

        return targetApk;
    }

    /**
     * Initialize the dynamicResources object that is used for loading all the plugin resources.
     *
     * @param targetApk
     * @return
     */
    Resources loadResources(File targetApk) {
        Resources dynamicResources = null;

        PathClassLoader loader = new PathClassLoader(
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

        return dynamicResources;
    }

}
