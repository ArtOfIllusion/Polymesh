/* Copyright (C) 2006-2007 by Francois Guillet

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
import artofillusion.ui.ComponentsDialog;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.MeshEditController;
import artofillusion.ui.Translate;
import buoy.widget.BComboBox;
import buoy.widget.Widget;

/** AdvancedExtrudeTool is the stool used to extrude selection.
 * In addition, it can scale/rotate the selection (e.g. extruded faces.*/

public class AdvancedExtrudeTool extends AdvancedEditingTool
{
    private Vec3 baseVertPos[];
    private UndoRecord undo;
    private HashMap manip3dHashMap;
    private boolean selected[], separateFaces;
    private PolyMesh origMesh;
    private short NO_EXTRUDE = 0;
    private short EXTRUDE_FACES = 1;
    private short EXTRUDE_FACE_GROUPS = 2;
    private short EXTRUDE_EDGES = 3;
    private short EXTRUDE_EDGE_GROUPS = 4;
    private int mode;

    public AdvancedExtrudeTool(EditingWindow fr, MeshEditController controller)
    {
        super(fr, controller);
        initButton("polymesh:extrude");
        manip3dHashMap = new HashMap();
    }

    public void activateManipulators(ViewerCanvas view)
    {
        if (! manip3dHashMap.containsKey(view))
        {
            PolyMeshValueWidget valueWidget = null;
            if (controller instanceof PolyMeshEditorWindow)
                valueWidget = ((PolyMeshEditorWindow)controller).getValueWidget();
            Manipulator manip3d = new SSMR3DManipulator(this, view, valueWidget);
            manip3d.addEventLink(Manipulator.ManipulatorPrepareChangingEvent.class, this, "doManipulatorPrepareShapingMesh");
            manip3d.addEventLink(SSMRManipulator.ManipulatorScalingEvent.class, this, "doManipulatorScalingMesh");
            manip3d.addEventLink(Manipulator.ManipulatorCompletedEvent.class, this, "doManipulatorShapedMesh");
            manip3d.addEventLink(SSMRManipulator.ManipulatorRotatingEvent.class, this, "doManipulatorRotatingMesh");
            manip3d.addEventLink(SSMRManipulator.ManipulatorMovingEvent.class, this, "doManipulatorMovingMesh");
            manip3d.addEventLink(Manipulator.ManipulatorAbortChangingEvent.class, this, "doAbortChangingMesh");
            manip3d.setActive(true);
            ((PolyMeshViewer)view).setManipulator(manip3d);
            manip3dHashMap.put(view, manip3d);
            selectionModeChanged(((MeshViewer)view).getController().getSelectionMode());
        }
        else
        {
            ((PolyMeshViewer)view).setManipulator((Manipulator)manip3dHashMap.get(view));
        }
    }

    public void activate()
    {
        super.activate();
        theWindow.setHelpText(Translate.text("polymesh:advancedExtrudeTool.helpText"));
        ViewerCanvas view = theWindow.getView();
    }

    public void deactivate()
    {
    	super.deactivate();
    	Iterator iter = manip3dHashMap.keySet().iterator();
        PolyMeshViewer view;
        while (iter.hasNext())
        {
            view = (PolyMeshViewer)iter.next();
            view.removeManipulator((Manipulator)manip3dHashMap.get(view));
        }
    }

    public int whichClicks()
    {
        return ALL_CLICKS;
    }

    public String getToolTipText()
    {
        return Translate.text("polymesh:advancedExtrudeTool.tipText");
    }

    private void doManipulatorPrepareShapingMesh(Manipulator.ManipulatorEvent e)
    {
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        baseVertPos = mesh.getVertexPositions();
        origMesh = (PolyMesh) mesh.duplicate();
        selected = controller.getSelection();
        int selectMode = controller.getSelectionMode();
        if ( selectMode == PolyMeshEditorWindow.FACE_MODE )
            mode = ( separateFaces ? EXTRUDE_FACES : EXTRUDE_FACE_GROUPS );
        else if  ( selectMode == PolyMeshEditorWindow.EDGE_MODE )
            mode = ( separateFaces ? EXTRUDE_EDGES : EXTRUDE_EDGE_GROUPS );
        else
            mode = NO_EXTRUDE;
    }

    private void doAbortChangingMesh()
    {
        if (origMesh != null)
        {
        	PolyMesh mesh = (PolyMesh) controller.getObject().object;
        	mesh.copyObject(origMesh);
        	controller.objectChanged();
        }
        origMesh = null;
        baseVertPos = null;
        theWindow.setHelpText(Translate.text("polymesh:advancedExtrudeTool.helpText"));
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
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        undo = new UndoRecord(theWindow, false, UndoRecord.COPY_OBJECT, new Object [] {mesh, origMesh});
        theWindow.setUndoRecord(undo);
        baseVertPos = null;
        origMesh = null;
        theWindow.setHelpText(Translate.text("polymesh:advancedExtrudeTool.helpText"));
        theWindow.updateImage();
    }

    private void doManipulatorMovingMesh(SSMR2DManipulator.ManipulatorMovingEvent e)
    {
        MeshViewer mv = (MeshViewer) e.getView();
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        Vec3 drag = e.getDrag();

        if (mode != NO_EXTRUDE)
        {
            double value = drag.length();
            drag.normalize();
            mesh.copyObject(origMesh);
            if ( mode == EXTRUDE_FACES )
                mesh.extrudeFaces( selected, value, drag );
            else if (mode == EXTRUDE_FACE_GROUPS)
                mesh.extrudeRegion( selected, value, drag );
            else if (mode == EXTRUDE_EDGES)
                mesh.extrudeEdges( selected, value, drag );
            else if (mode == EXTRUDE_EDGE_GROUPS)
                mesh.extrudeEdgeRegion( selected, value, drag );
            //undo = new UndoRecord(theWindow, false, UndoRecord.COPY_OBJECT, new Object [] {mesh, origMesh});
            boolean[] sel = null;
            if (mode == EXTRUDE_FACES || mode == EXTRUDE_FACE_GROUPS)
                sel = new boolean[mesh.getFaces().length];
            else
                sel = new boolean[mesh.getEdges().length/2];
            for ( int i = 0; i < selected.length; ++i )
                sel[i] = selected[i];
            controller.objectChanged();
            controller.setSelection( sel );
        }
        else
        {
            Vec3[] v = findDraggedPositions(drag, baseVertPos, mv, controller.getSelectionDistance());
            mesh.setVertexPositions(v);
        }
        controller.objectChanged();
        theWindow.updateImage();
    }

    public void iconDoubleClicked()
    {
        BComboBox c = new BComboBox( new String[]{
                Translate.text("polymesh:selectionAsWhole" ),
                Translate.text("polymesh:individualFaces" )
                } );
        c.setSelectedIndex( separateFaces ? 1 : 0 );
        ComponentsDialog dlg = new ComponentsDialog( theFrame, Translate.text( "applyExtrudeTo" ),
                new Widget[]{c}, new String[]{null} );
        if ( dlg.clickedOk() )
            separateFaces = ( c.getSelectedIndex() == 1 );
    }

}
