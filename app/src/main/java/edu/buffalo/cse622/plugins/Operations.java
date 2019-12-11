package edu.buffalo.cse622.plugins;

import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ux.ArFragment;

import java.util.HashSet;

public interface Operations {

    // Declare the following attributes in the plugin
    /*
    // dynamicResources object is a Resources object initialized and passed by the MetaApp
    private Resources dynamicResources;
    // This is the main ArFragment object of the MetaApp
    private ArFragment arFragment;
    // This is the Activity context of the MetaApp
    private Context context;
    // A reference to the HashSet object that is used by the MetaApp to keep track of this plugin's objects
    private HashSet<AnchorNode> pluginObjects;
     */

    // Write the constructor like this
    /*
    public FrameOperations(Resources dynamicResources, ArFragment arFragment, HashSet<AnchorNode> pluginObjects) {
        this.dynamicResources = dynamicResources;
        this.arFragment = arFragment;
        this.context = arFragment.getContext();
        this.pluginObjects = pluginObjects;

        // Load all plugin resources using dynamicResources. Check our sample plugin applications to see how to do that.
        // Any other plugin initialization that maybe required.
        // Add logic to add images to AugmentedImageDatabase if required.
    }
    */

    // One thing to take care of when implementing processFrame() and planeTap().
    // Need to call renderObject() when plugin wants to render an object to MetaApp.

    // This is invoked by the MetaApp on every frame.
    public void processFrame(Frame frame);
    // This is invoked whenever the user taps on a plane.
    public void planeTap(HitResult hitResult);
    // This is invoked when MetaApp tries to disable the plugin.
    public void onDestroy();

    // This default method implementation is used to render objects and to add the object to MetaApp's Map of plugin objects.
    default void renderObject(ArFragment arFragment, HashSet<AnchorNode> pluginObjects, AnchorNode anchorNode) {
        if (anchorNode != null) {
            anchorNode.setParent(arFragment.getArSceneView().getScene());
            pluginObjects.add(anchorNode);
        }
    }
}
