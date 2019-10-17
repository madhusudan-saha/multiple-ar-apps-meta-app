package edu.buffalo.cse622.multiplearapps;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.ar.core.Frame;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import dalvik.system.DexClassLoader;

public class MainActivity extends AppCompatActivity {

    Map<Class<Object>, Object> pluginMap = new HashMap();
    private ArFragment arFragment;
    private ViewRenderable textRenderable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar mainToolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(mainToolbar);

        requestAppPermissions();

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);

        // Add a frame update listener to the scene to control the state of the buttons.
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onFrameUpdate);

        ViewRenderable.builder()
                .setView(this, R.layout.text_view)
                .build()
                .thenAccept(
                        (renderable) -> {
                            textRenderable = renderable;
                        });
    }

    private void requestAppPermissions() {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        if (hasReadPermissions() && hasWritePermissions()) {
            return;
        }

        ActivityCompat.requestPermissions(this,
                new String[] {
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

        if(requestCode == 102 && resultCode == RESULT_OK) {
            Uri selectedfile = data.getData(); //The uri with the location of the file

            String path = Environment.getExternalStorageDirectory() +"/"+selectedfile.getPath().split(":")[1];
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

    }
}
