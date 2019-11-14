package edu.buffalo.cse622.metaapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
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
    ArFragment arFragment;
    String activePlugin;

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
        // Add a listener to user taps.
        arFragment.setOnTapArPlaneListener(this::onPlaneTap);

        pluginMap = new HashMap<>();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        deleteDir(context.getDir("dex", Context.MODE_PRIVATE));
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        return dir.delete();
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

        switch (id) {
            case R.id.import_app:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");

                startActivityForResult(Intent.createChooser(intent, "Choose APK file to import"), 102);

                break;

            case R.id.activate_plugin_input:
                // Creates a popup with the list of loaded plugins
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Activate input for plugin:");

                View viewInflated = LayoutInflater.from(context).inflate(R.layout.activate_plugin_input, (ViewGroup) findViewById(android.R.id.content), false);
                final RadioGroup pluginsGroup = viewInflated.findViewById(R.id.pluginsGroup);

                for (String pluginName : pluginMap.keySet()) {
                    RadioButton radioButton = new RadioButton(this);
                    radioButton.setId(View.generateViewId());
                    radioButton.setText(pluginName);
                    pluginsGroup.addView(radioButton);
                }

                builder.setView(viewInflated);

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int radioButtonID = pluginsGroup.getCheckedRadioButtonId();
                        View radioButtonView = pluginsGroup.findViewById(radioButtonID);
                        int selectedIndex = pluginsGroup.indexOfChild(radioButtonView);

                        RadioButton radioButton = (RadioButton) pluginsGroup.getChildAt(selectedIndex);
                        activePlugin = radioButton.getText().toString();
                    }
                });

                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();

                break;
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

                File targetApk = DynamicLoadingUtils.loadApk(context, apkInputStream, pluginName);
                Resources dynamicResources = DynamicLoadingUtils.loadResources(context, targetApk);

                PathClassLoader loader = new PathClassLoader(
                        targetApk.getAbsolutePath(), getClassLoader());
                Class<?> dynamicClass = loader.loadClass("edu.buffalo.cse622.plugins.FrameOperations");
                Constructor<?> ctor = dynamicClass.getConstructor(Resources.class, ArFragment.class);
                Object dynamicInstance = ctor.newInstance(dynamicResources, arFragment);

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

    private void onPlaneTap(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        if (activePlugin == null) {
            Toast.makeText(context, "Activate input for a plugin first!", Toast.LENGTH_LONG).show();

            return;
        }

        Object[] instanceClassAndObject = pluginMap.get(activePlugin).get("FrameOperations");
        Class<?> dynamicClass = (Class<?>) instanceClassAndObject[0];
        Object dynamicInstance = instanceClassAndObject[1];

        try {
            Method planeTap = dynamicClass.getDeclaredMethod("planeTap", HitResult.class);
            planeTap.setAccessible(true);  // To invoke protected or private methods
            AnchorNode anchorNode = (AnchorNode) planeTap.invoke(dynamicInstance, hitResult);
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
