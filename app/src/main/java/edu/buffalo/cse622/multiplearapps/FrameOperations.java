package edu.buffalo.cse622.multiplearapps;

import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

public class FrameOperations {

    Node processFrame(Frame frame, ViewRenderable textRenderable) {
        Node node = null;
        for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
            Anchor anchor = plane.createAnchor(plane.getCenterPose());
            AnchorNode anchorNode = new AnchorNode(anchor);
            node = new Node();
            node.setParent(anchorNode);
            node.setRenderable(textRenderable);
            TextView textView = (TextView) textRenderable.getView();
            textView.setText("Works!");

            break;
            /*
            anchorNode.setParent(arFragment.getArSceneView().getScene());
            TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());
            transformableNode.setParent(anchorNode);
            transformableNode.setRenderable(textRenderable);
            TextView textView = (TextView) textRenderable.getView();
            textView.setText("Works!");
            */
        }

        return node;
    }
}
