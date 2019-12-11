package edu.buffalo.cse622.metaapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.Button;

import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import dalvik.system.PathClassLoader;
import edu.buffalo.cse622.plugins.Operations;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MetaApp:" + MainActivity.class.getSimpleName();

    private Context context = null;
    private ArFragment arFragment;
    // Map with {Key:Value} pair as {PluginName:HashMap of class instances}.
    // Inner map with {Key:Value} pair as {ClassName:Class instance}
    // (Inner HashMap is used in case we want to have multiple classes in the plugin in future)
    private Map<String, HashMap<String, Object>> pluginInstanceMap;
    // Map to keep track of objects belonging to plugins that can be used for cleanup or occlusion management if we want to do in the future.
    private Map<String, HashSet<AnchorNode>> pluginObjectsMap;
    private String activePlugin;

    private Button clearButton, loadButton, unloadButton, enableInputButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        pluginInstanceMap = new HashMap<>();
        pluginObjectsMap = new HashMap<>();

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);

        // Add a frame update listener to the scene to control the state of the buttons.
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onFrameUpdate);
        // Add a listener to user taps.
        arFragment.setOnTapArPlaneListener(this::onPlaneTap);

        pluginInstanceMap = new HashMap<>();

        clearButton = findViewById(R.id.clearButton);
        loadButton = findViewById(R.id.loadButton);
        unloadButton = findViewById(R.id.unloadButton);
        enableInputButton = findViewById(R.id.enableInputButton);

        clearButton.setOnClickListener(this::clear);
        loadButton.setOnClickListener(this::load);
        unloadButton.setOnClickListener(this::disable);
        enableInputButton.setOnClickListener(this::enableInput);

        Session session = null;
        try {
            session = new Session(this);
        } catch (UnavailableArcoreNotInstalledException | UnavailableApkTooOldException |
                UnavailableSdkTooOldException | UnavailableDeviceNotCompatibleException e) {
            e.printStackTrace();
        }

        Config config = new Config(session);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        config.setFocusMode(Config.FocusMode.AUTO);
        session.configure(config);
        arFragment.getArSceneView().setupSession(session);

        config.setAugmentedImageDatabase(new AugmentedImageDatabase(session));
    }

    @Override
    protected void onPause() {
        super.onPause();

        deleteDir(context.getDir("dex", Context.MODE_PRIVATE));
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        invokePluginDestroyAll();
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
                if (!pluginInstanceMap.containsKey(pluginName)) {
                    InputStream apkInputStream = getContentResolver().openInputStream(selectedFile);

                    File targetApk = DynamicLoadingUtils.loadApk(context, apkInputStream, pluginName);
                    Resources dynamicResources = DynamicLoadingUtils.loadResources(context, targetApk);

                    pluginObjectsMap.put(pluginName, new HashSet<>());

                    PathClassLoader loader = new PathClassLoader(
                            targetApk.getAbsolutePath(), getClassLoader());
                    Class<?> dynamicClass = loader.loadClass("edu.buffalo.cse622.plugins.FrameOperations");
                    Constructor<?> ctor = dynamicClass.getConstructor(Resources.class, ArFragment.class, HashSet.class);
                    Operations dynamicInstance = (Operations) ctor.newInstance(dynamicResources, arFragment, pluginObjectsMap.get(pluginName));

                    // Add the class instances to pluginInstanceMap
                    HashMap<String, Object> instanceMap = new HashMap<>();
                    instanceMap.put("FrameOperations", dynamicInstance);
                    pluginInstanceMap.put(pluginName, instanceMap);
                } else {
                    Log.d(TAG, "onActivityResult: " + "Plugin " + pluginName + " was already loaded.");
                    Toast.makeText(context, "Plugin already loaded!", Toast.LENGTH_LONG).show();
                }
            } catch (InstantiationException | InvocationTargetException | NoSuchMethodException |
                    IllegalAccessException | ClassNotFoundException | FileNotFoundException e) {
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
        for (Map.Entry<String, HashMap<String, Object>> entry : pluginInstanceMap.entrySet()) {
            Operations dynamicInstance = (Operations) entry.getValue().get("FrameOperations");
            Class<?> dynamicClass = dynamicInstance.getClass();

            try {
                Method processFrame = dynamicClass.getDeclaredMethod("processFrame", Frame.class);
                //processFrame.setAccessible(true);  // To invoke protected or private methods
                processFrame.invoke(dynamicInstance, frame);
            } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                e.printStackTrace();
            }

        }

    }

    private void onPlaneTap(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        if (activePlugin == null) {
            Log.d(TAG, "onPlaneTap: " + "Input for a plugin needs to be activated before providing user input.");
            Toast.makeText(context, "Activate input for a plugin first!", Toast.LENGTH_LONG).show();

            return;
        }

        Operations dynamicInstance = (Operations) pluginInstanceMap.get(activePlugin).get("FrameOperations");
        Class<?> dynamicClass = dynamicInstance.getClass();
        Log.e(TAG, "onPlaneTap: " + dynamicInstance.getClass() + ": " + dynamicClass.getMethods());
        try {
            Method planeTap = dynamicClass.getDeclaredMethod("planeTap", HitResult.class);
            //planeTap.setAccessible(true);  // To invoke protected or private methods
            planeTap.invoke(dynamicInstance, hitResult);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void clear(View v) {
        invokePluginDestroyAll();

        List<Node> arFragmentChildren = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());
        for (Node childNode : arFragmentChildren) {
            if (childNode instanceof AnchorNode) {
                if (((AnchorNode) childNode).getAnchor() != null) {
                    arFragment.getArSceneView().getScene().removeChild(childNode);
                    ((AnchorNode) childNode).getAnchor().detach();
                    childNode.setParent(null);
                }
            }
        }

        Toast.makeText(context, "Removed all AR objects.", Toast.LENGTH_LONG).show();
    }

    private void load(View v) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        Uri uri = Uri.parse(Environment.getExternalStorageDirectory().getPath() + "/622-PluginsDir");
        intent.setDataAndType(uri, "application/vnd.android.package-archive");

        startActivityForResult(Intent.createChooser(intent, "Choose APK file to import"), 102);
    }

    private void disable(View v) {
        PopupMenu menu = new PopupMenu(this, v);

        for (String pluginName : pluginInstanceMap.keySet()) {
            menu.getMenu().add(pluginName);
        }

        menu.setOnMenuItemClickListener(item -> {
            String pluginToDisable = item.getTitle().toString();

            //Invoke onDestroy() for the disabled plugin
            Operations dynamicInstance = (Operations) pluginInstanceMap.get(pluginToDisable).get("FrameOperations");
            invokePluginDestroy(dynamicInstance);

            pluginInstanceMap.remove(pluginToDisable);

            HashSet<AnchorNode> pluginObjects = pluginObjectsMap.get(pluginToDisable);
            for (AnchorNode unloadedPluginNode : pluginObjects) {
                arFragment.getArSceneView().getScene().removeChild(unloadedPluginNode);
                unloadedPluginNode.getAnchor().detach();
                unloadedPluginNode.setParent(null);
            }
            pluginObjectsMap.remove(pluginToDisable);

            if (pluginToDisable.equals(activePlugin)) {
                activePlugin = null;
                enableInputButton.setText(getString(R.string.enable_input));
                enableInputButton.setBackgroundColor(0x66FF0000);
                enableInputButton.setAlpha(0.7f);
            }

            Log.d(TAG, "disable: " + "Plugin " + pluginToDisable + " disabled (need to load again to use).");
            Toast.makeText(context, "Plugin " + pluginToDisable + " disabled.", Toast.LENGTH_LONG).show();
            return true;
        });

        menu.show();
    }

    private void enableInput(View v) {
        PopupMenu menu = new PopupMenu(this, v);

        for (String pluginName : pluginInstanceMap.keySet()) {
            menu.getMenu().add(pluginName);
        }

        menu.setOnMenuItemClickListener(item -> {
            activePlugin = item.getTitle().toString();
            enableInputButton.setText(activePlugin.replace(".apk", ""));
            enableInputButton.setBackgroundColor(Color.GREEN);
            enableInputButton.setAlpha(0.7f);

            return true;
        });

        menu.show();
    }

    private void invokePluginDestroyAll() {
        // Invoke onDestroy() on all plugins
        for (Map.Entry<String, HashMap<String, Object>> entry : pluginInstanceMap.entrySet()) {
            Operations dynamicInstance = (Operations) entry.getValue().get("FrameOperations");
            invokePluginDestroy(dynamicInstance);
        }
    }

    private void invokePluginDestroy(Operations dynamicInstance) {
        Class<?> dynamicClass = dynamicInstance.getClass();

        try {
            Method planeTap = dynamicClass.getDeclaredMethod("onDestroy");
            //planeTap.setAccessible(true);  // To invoke protected or private methods
            planeTap.invoke(dynamicInstance);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
