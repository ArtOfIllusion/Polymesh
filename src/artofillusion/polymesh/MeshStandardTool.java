/* Copyright (C) 1999-2005 by Peter Eastman (ReshapeMeshTool) 2006 by Francois Guillet (MeshStandardTool)

   This program is free software; you can redistribute it and/or modify it under the
   terms of the GNU General Public License as published by the Free Software
   Foundation; either version 2 of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT ANY 
   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
   PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.polymesh;

import java.awt.Image;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.ImageIcon;

import artofillusion.MeshViewer;
import artofillusion.UndoRecord;
import artofillusion.ViewerCanvas;
import artofillusion.math.Vec3;
import artofillusion.object.Mesh;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.MeshEditController;
import artofillusion.ui.Translate;

/** MeshStandardTool is the standard, default, editing tool. It can be used to move, scale and rotate selections.*/

public class MeshStandardTool extends AdvancedEditingTool
{
    private Vec3 baseVertPos[];
    private UndoRecord undo;
    private static Image icon, selectedIcon;
    /** hash maps are used to store manipulators for views */
    private HashMap manip2dHashMap, manip3dHashMap;

    public MeshStandardTool(EditingWindow fr, MeshEditController controller)
    {
        super(fr, controller);
        
        initButton("polymesh:movePoints");
        manip2dHashMap = new HashMap();
        manip3dHashMap = new HashMap();
    }

    public void activateManipulators(ViewerCanvas view)
    {
        if (! manip2dHashMap.containsKey(view))
        {
            PolyMeshValueWidget valueWidget = null;
            if (controller instanceof PolyMeshEditorWindow)
                valueWidget = ((PolyMeshEditorWindow)controller).getValueWidget();
            Manipulator manip2d = new SSMR2DManipulator(this, view, valueWidget);
            manip2d.addEventLink(Manipulator.ManipulatorPrepareChangingEvent.class, this, "doManipulatorPrepareShapingMesh");
            manip2d.addEventLink(SSMRManipulator.ManipulatorScalingEvent.class, this, "doManipulatorScalingMesh");
            manip2d.addEventLink(Manipulator.ManipulatorCompletedEvent.class, this, "doManipulatorShapedMesh");
            manip2d.addEventLink(SSMRManipulator.ManipulatorRotatingEvent.class, this, "doManipulatorRotatingMesh");
            manip2d.addEventLink(SSMRManipulator.ManipulatorMovingEvent.class, this, "doManipulatorMovingMesh");
            manip2d.addEventLink(Manipulator.ManipulatorAbortChangingEvent.class, this, "doAbortChangingMesh");
            manip2d.setActive(false);
            Manipulator manip3d = new SSMR3DManipulator(this, view, valueWidget);
            manip3d.addEventLink(Manipulator.ManipulatorPrepareChangingEvent.class, this, "doManipulatorPrepareShapingMesh");
            manip3d.addEventLink(SSMRManipulator.ManipulatorScalingEvent.class, this, "doManipulatorScalingMesh");
            manip3d.addEventLink(Manipulator.ManipulatorCompletedEvent.class, this, "doManipulatorShapedMesh");
            manip3d.addEventLink(SSMRManipulator.ManipulatorRotatingEvent.class, this, "doManipulatorRotatingMesh");
            manip3d.addEventLink(SSMRManipulator.ManipulatorMovingEvent.class, this, "doManipulatorMovingMesh");
            manip3d.addEventLink(Manipulator.ManipulatorAbortChangingEvent.class, this, "doAbortChangingMesh");
            manip3d.setActive(true);
            ((PolyMeshViewer)view).setManipulator(manip2d);
            ((PolyMeshViewer)view).addManipulator(manip3d);
            manip2dHashMap.put(view, manip2d);
            manip3dHashMap.put(view, manip3d);
        }
        else
        {
            ((PolyMeshViewer)view).setManipulator((Manipulator)manip2dHashMap.get(view));
            ((PolyMeshViewer)view).addManipulator((Manipulator)manip3dHashMap.get(view));
        }
    }

    public void activate()
    {
        super.activate();
        theWindow.setHelpText(Translate.text("polymesh:meshStandardTool.helpText"));
        ViewerCanvas view = theWindow.getView();
    }

    public void deactivate()
    {
    	super.deactivate();
    	Iterator iter = manip2dHashMap.keySet().iterator();
        PolyMeshViewer view;
        while (iter.hasNext())
        {
            view = (PolyMeshViewer)iter.next();
            view.removeManipulator((Manipulator)manip2dHashMap.get(view));
            view.removeManipulator((Manipulator)manip3dHashMap.get(view));
        }
    }

    public int whichClicks()
    {
        return ALL_CLICKS;
    }

    public Image getIcon()
    {
        return icon;
    }

    public Image getSelectedIcon()
    {
        return selectedIcon;
    }

    public String getToolTipText()
    {
        return Translate.text("polymesh:meshStandardTool.tipText");
    }

    private void doManipulatorPrepareShapingMesh(Manipulator.ManipulatorEvent e)
    {
        Mesh mesh = (Mesh) controller.getObject().object;
        if (undo == null)
            undo = new UndoRecord(theWindow, false, UndoRecord.COPY_VERTEX_POSITIONS, new Object [] {mesh, mesh.getVertexPositions()});
        baseVertPos = mesh.getVertexPositions();
    }

    private void doAbortChangingMesh()
    {
        Mesh mesh = (Mesh) controller.getObject().object;
        mesh.setVertexPositions(baseVertPos);
        baseVertPos = null;
        theWindow.setHelpText(Translate.text("polymesh:meshStandardTool.helpText"));
        controller.objectChanged();
        theWindow.updateImage();
    }

    private void doManipulatorScalingMesh(SSMR2DManipulator.ManipulatorScalingEvent e)
    {
        Mesh mesh = (Mesh) controller.getObject().object;
        Vec3[] v = findScaledPositions(baseVertPos, e.getScaleMatrix(), (MeshViewer) e.getView());
        mesh.setVertexPositions(v);
        controller.objectChanged();
        theWindow.updateImage();
    }

    private void doManipulatorRotatingMesh(SSMR2DManipulator.ManipulatorRotatingEvent e)
    {
        Mesh mesh = (Mesh) controller.getObject().object;
        Vec3[] v = null;
        v = findRotatedPositions(baseVertPos, e.getMatrix(), (MeshViewer)e.getView());
        if (v != null)
        {
            mesh.setVertexPositions(v);
            controller.objectChanged();
            theWindow.updateImage();
        }
    }

    private void doManipulatorShapedMesh(Manipulator.ManipulatorEvent e)
    {
        if (undo != null)
        {
            theWindow.setUndoRecord(undo);
            undo = null;
        }
        baseVertPos = null;
        theWindow.setHelpText(Translate.text("polymesh:meshStandardTool.helpText"));
        controller.objectChanged();
        theWindow.updateImage();
    }

    private void doManipulatorMovingMesh(SSMR2DManipulator.ManipulatorMovingEvent e)
    {
        MeshViewer mv = (MeshViewer) e.getView();
        Mesh mesh = (Mesh) controller.getObject().object;
        Vec3 v[], drag;

        drag = e.getDrag();
        v = findDraggedPositions(drag, baseVertPos, mv, controller.getSelectionDistance());
        mesh.setVertexPositions(v);
        controller.objectChanged();
        theWindow.updateImage();
    }
}