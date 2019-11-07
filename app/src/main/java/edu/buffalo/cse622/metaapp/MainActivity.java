package edu.buffalo.cse622.metaapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;

import com.google.ar.core.Frame;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar mainToolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(mainToolbar);
        context = this;

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);

        // Add a frame update listener to the scene to control the state of the buttons.
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onFrameUpdate);

        pluginMap = new HashMap<>();

        //requestAppPermissions();
    }

    @Override
    protected void onPause(){
        super.onPause();
        if (arFragment.getArSceneView().getSession() != null) {
            arFragment.getArSceneView().getSession().pause();
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        try {
            if (arFragment.getArSceneView().getSession() != null) {
                arFragment.getArSceneView().getSession().resume();
            }
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

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

            startActivityForResult(Intent.createChooser(intent, "Choose APK file to import"), 102);
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

                File targetApk = DynamicLoadingUtils.loadApk(context, apkInputStream);
                Resources dynamicResources = DynamicLoadingUtils.loadResources(context, targetApk);

                PathClassLoader loader = new PathClassLoader(
                        targetApk.getAbsolutePath(), getClassLoader());
                Class<?> dynamicClass = loader.loadClass("edu.buffalo.cse622.plugins.FrameOperations");
                Constructor<?> ctor = dynamicClass.getConstructor(Context.class, Resources.class);
                Object dynamicInstance = ctor.newInstance(context, dynamicResources);

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

        if (frame == null) {
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

        }

    }

    /*
    private void requestAppPermissions() {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        if (hasWritePermissions() && hasReadPermissions() && hasCameraPermission()) {
            return;
        }

        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA
                }, 101);
    }

    private boolean hasReadPermissions() {
        return (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean hasWritePermissions() {
        return (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    public boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == 101) {
            if (grantResults.length > 0 && permissions.length==grantResults.length) {
                Log.d("Permissions", "Camera and storage permissions granted.");
            }
            else {
                Toast.makeText(this, "Camera and storage permissions not granted!", Toast.LENGTH_LONG);
            }
        }
    }
    */

}
