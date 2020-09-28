/* Copyright (C) 2006-2007 by Francois Guillet

 This program is free software; you can redistribute it and/or modify it under the
 terms of the GNU General Public License as published by the Free Software
 Foundation; either version 2 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY 
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.polymesh;

import java.util.ArrayList;
import java.util.Iterator;

import artofillusion.Camera;
import artofillusion.MeshEditorWindow;
import artofillusion.MeshViewer;
import artofillusion.ViewerCanvas;
import artofillusion.math.BoundingBox;
import artofillusion.math.CoordinateSystem;
import artofillusion.math.Mat4;
import artofillusion.math.Vec3;
import artofillusion.object.Mesh;
import artofillusion.object.MeshVertex;
import artofillusion.ui.EditingTool;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.MeshEditController;

/**
 * This class provides an advanced behavior over standard editing tools.
 */
public abstract class AdvancedEditingTool extends EditingTool
{
    protected MeshEditController controller;

    public AdvancedEditingTool(EditingWindow fr, MeshEditController controller)
    {
        super(fr);
        this.controller = controller;
    }

    /**
     * Override this method if you want to be notified of every selection update.
     */
    public void selectionUpdate(MeshViewer meshViewer)
    {

    }

    /**
     * sets first inactive manipulator active
     *
     * @param view The view to toggle active manipulator for
     */
    public void toggleManipulator(ViewerCanvas view)
    {
        ArrayList manipulators = ((PolyMeshViewer)view).getManipulators();
        if ( manipulators.size() == 1 )
            return;
        Iterator iter = manipulators.iterator();
        Manipulator manipulator;
        Manipulator newActive = null;
        while (iter.hasNext())
        {
            manipulator = (Manipulator)iter.next();
            if (!manipulator.isActive() && newActive == null)
            {
                manipulator.setActive(true);
                newActive = manipulator;
            }
            else
                manipulator.setActive(false);
        }
    }

    public abstract void activateManipulators(ViewerCanvas view);

    /* This method returns a bounding box for the selected vertices in view coordinates. */

    public SelectionProperties findSelectionProperties(Camera cam)
    {
        int selected[] = controller.getSelectionDistance();
        MeshVertex[] vert  = ((Mesh)controller.getObject().object).getVertices();
        double minx, miny, minz, maxx, maxy, maxz;
        Vec3 v;
        Vec3[] features = new Vec3[1];
        int i;

        Vec3 center = new Vec3();
        minx = miny = minz = Double.MAX_VALUE;
        maxx = maxy = maxz = -Double.MAX_VALUE;
        boolean nullSel = true;
        int count = 0;
        for (i = 0; i < vert.length; i++)
        {
            if (selected[i] == 0)
            {
                count++;
                nullSel = false;
                center.add(vert[i].r);
                v = cam.getObjectToView().times(vert[i].r);
                if (v.x < minx) minx = v.x;
                if (v.x > maxx) maxx = v.x;
                if (v.y < miny) miny = v.y;
                if (v.y > maxy) maxy = v.y;
                if (v.z < minz) minz = v.z;
                if (v.z > maxz) maxz = v.z;
            }
        }
        if (nullSel)
            return new SelectionProperties();
        else
        {
            SelectionProperties props;
            if (controller instanceof PolyMeshEditorWindow)
                props = ((PolyMeshEditorWindow)controller).getSelectionProperties();
            else
            {
                center.scale(1.0/(double)count);
                features = new Vec3[2];
                features[0] = center;
                features[1] = new Vec3();
                props = new SelectionProperties();
            }
            props.bounds = new BoundingBox(minx, maxx, miny, maxy, minz, maxz);
            return props;
        }
    }

    /**
     *  Call this method when the selection mode has changed
     *  Depending on the tool specialization, display can be updated
     *  depending on the selection mode (whatever the mode really, e.g.
     *  face, edge, vertex mode for meshes, etc.
     */
    public void selectionModeChanged(int selectionMode)
    {
    }
    
    /* Utility methods */

    protected Vec3 [] findDraggedPositions(Vec3 dragVec, Vec3 vert[], MeshViewer view, int selectDist[])
    {
        int maxDistance = view.getController().getTensionDistance();
        double tension = view.getController().getMeshTension();
        Vec3 drag[] = new Vec3 [maxDistance+1];
        Vec3 v[] = new Vec3 [vert.length];

        drag[0] = dragVec;
        for (int i = 1; i <= maxDistance; i++)
            drag[i] = drag[0].times(Math.pow((maxDistance-i+1.0)/(maxDistance+1.0), tension));
        /*if (view.getUseWorldCoords())
        {
            Mat4 trans = view.getDisplayCoordinates().toLocal();
            for (int i = 0; i < drag.length; i++)
                trans.transformDirection(drag[i]);
        }*/
        for (int i = 0; i < vert.length; i++)
        {
            if (selectDist[i] > -1)
                v[i] = vert[i].plus(drag[selectDist[i]]);
            else
                v[i] = new Vec3(vert[i]);
        }
        return v;
    }

    /* Find the new positions of the vertices after scaling. */
     protected Vec3 [] findScaledPositions(Vec3 vert[], Mat4 m, MeshViewer view)
    {
        Vec3 v[] = new Vec3 [vert.length];
        int selected[] = controller.getSelectionDistance();
        int i;

        // Determine the deltas.

        for (i = 0; i < vert.length; i++)
        {
            if (selected[i] == 0)
                v[i] = m.times(vert[i]).minus(vert[i]);
            else
                v[i] = new Vec3();
        }
        if (theFrame instanceof MeshEditorWindow)
            ((MeshEditorWindow) theFrame).adjustDeltas(v);
        for (i = 0; i < vert.length; i++)
            v[i].add(vert[i]);
        return v;
    }

    /* Find the new positions of the vertices after scaling. */
    protected Vec3 [] findRotatedPositions(Vec3 vert[], Mat4 mat, MeshViewer view)
    {
        Vec3 v[] = new Vec3 [vert.length], axis;
        int selected[] = controller.getSelectionDistance();
        Camera cam = view.getCamera();
        CoordinateSystem coords = view.getDisplayCoordinates();
        Mat4 m;
        int i;

        // Determine the deltas.
        for (i = 0; i < vert.length; i++)
        {
            if (selected[i] == 0)
                v[i] = mat.times(vert[i]).minus(vert[i]);
            else
                v[i] = new Vec3();
        }
        if (theFrame instanceof MeshEditorWindow)
            ((MeshEditorWindow) theFrame).adjustDeltas(v);
        for (i = 0; i < vert.length; i++)
            v[i].add(vert[i]);
        return v;
    }

    protected Vec3 [] findDraggedPositions(Vec3 pos, Vec3 vert[], double dx, double dy, MeshViewer view, boolean controlDown, int selectDist[])
    {
        int maxDistance = view.getController().getTensionDistance();
        double tension = view.getController().getMeshTension();
        Vec3 drag[] = new Vec3 [maxDistance+1], v[] = new Vec3 [vert.length];

        if (controlDown)
            drag[0] = view.getCamera().getCameraCoordinates().getZDirection().times(-dy*0.01);
        else
            drag[0] = view.getCamera().findDragVector(pos, dx, dy);
        for (int i = 1; i <= maxDistance; i++)
            drag[i] = drag[0].times(Math.pow((maxDistance-i+1.0)/(maxDistance+1.0), tension));
        System.out.println("dragging");
        if (view.getUseWorldCoords())
        {
            System.out.println("use world coords");
            Mat4 trans = view.getDisplayCoordinates().toLocal();
            for (int i = 0; i < drag.length; i++)
                trans.transformDirection(drag[i]);
        }
        for (int i = 0; i < vert.length; i++)
        {
            if (selectDist[i] > -1)
                v[i] = vert[i].plus(drag[selectDist[i]]);
            else
                v[i] = new Vec3(vert[i]);
        }
        return v;
    }

    public static class SelectionProperties
    {
        public BoundingBox bounds;
        public Vec3[] featurePoints;
        public CoordinateSystem specificCoordinateSystem;
    }
}
