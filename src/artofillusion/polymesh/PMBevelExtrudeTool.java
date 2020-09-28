/*
 *  Copyright (C) 2003-2005 by Peter Eastman, (C) 2005 by Francois Guillet for PolyMesh adaptation
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;

import artofillusion.Camera;
import artofillusion.UndoRecord;
import artofillusion.ViewerCanvas;
import artofillusion.math.Vec3;
import artofillusion.ui.ComponentsDialog;
import artofillusion.ui.EditingTool;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.MeshEditController;
import artofillusion.ui.Translate;
import buoy.event.WidgetMouseEvent;
import buoy.widget.BComboBox;
import buoy.widget.Widget;

/**
 *  PMBevelExtrudeTool is an EditingTool used for beveling and extruding
 *  PolyMesh objects.
 *
 *@author     Peter Eastman, modifications by Francois Guillet
 *@created    april, 26 2005
 */

public class PMBevelExtrudeTool extends EditingTool
{
    private boolean selected[], noSelection, separateFaces;
    private PolyMesh origMesh;
    private Point clickPoint;
    private double width, height;
    private MeshEditController controller;


    /**
     *  Constructor for the BevelExtrudeTool object
     *
     *@param  fr          Description of the Parameter
     *@param  controller  Description of the Parameter
     */
    public PMBevelExtrudeTool( EditingWindow fr, MeshEditController controller )
    {
        super( fr );
        this.controller = controller;
        initButton( "polymesh:bevel" );
    }


    /**
     *  Record the current selection.
     */

    private void recordSelection()
    {
        selected = controller.getSelection();
        noSelection = false;
        for ( int i = 0; i < selected.length; i++ )
            if ( selected[i] )
                return;
        noSelection = true;
    }


    /**
     *  Description of the Method
     */
    public void activate()
    {
        super.activate();
        recordSelection();
        if ( noSelection )
            theWindow.setHelpText( Translate.text( "bevelExtrudeTool.errorText" ) );
        else
            theWindow.setHelpText( Translate.text( "bevelExtrudeTool.helpText" ) );
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public int whichClicks()
    {
        return ALL_CLICKS;
    }


    /**
     *  Gets the toolTipText attribute of the BevelExtrudeTool object
     *
     *@return    The toolTipText value
     */
    public String getToolTipText()
    {
        return Translate.text( "bevelExtrudeTool.tipText" );
    }


    /**
     *  Description of the Method
     *
     *@param  e     Description of the Parameter
     *@param  view  Description of the Parameter
     */
    public void mousePressed( WidgetMouseEvent e, ViewerCanvas view )
    {
        recordSelection();
        if ( noSelection )
            return;
        PolyMeshViewer mv = (PolyMeshViewer) view;
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        origMesh = (PolyMesh) mesh.duplicate();
        //beveler = new PolyMeshBeveler( origMesh, selected, mode );
        clickPoint = e.getPoint();
    }


    /**
     *  Description of the Method
     *
     *@param  e     Description of the Parameter
     *@param  view  Description of the Parameter
     */
    public void mouseDragged( WidgetMouseEvent e, ViewerCanvas view )
    {
        if ( noSelection )
            return;
        PolyMeshViewer mv = (PolyMeshViewer) view;
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        Camera cam = view.getCamera();
        Point dragPoint = e.getPoint();
        // Determine the bevel width and extrude height.

        //Vec3 dragVec = cam.convertScreenToWorld( dragPoint, cam.getDistToScreen() ).minus( cam.convertScreenToWorld( clickPoint, cam.getDistToScreen() ) );
        Vec3 camZ = view.getCamera().getCameraCoordinates().getZDirection();
        //width = 0.5 * dragVec.x;
        //height = dragVec.y;
        width = ( dragPoint.x - clickPoint.x ) / view.getScale();
        height = ( clickPoint.y - dragPoint.y ) / view.getScale();
        boolean shiftMod =  ( ( e.getModifiers() & ActionEvent.SHIFT_MASK ) != 0 ) &&  ( ( e.getModifiers() & ActionEvent.CTRL_MASK ) != 0);
        boolean ctrlMod = ( ( e.getModifiers() & ActionEvent.SHIFT_MASK ) == 0 ) &&  ( ( e.getModifiers() & ActionEvent.CTRL_MASK ) != 0);
        /*if ( controller.getSelectionMode() == PolyMeshEditorWindow.FACE_MODE && ctrlMod )
        {
            width = height;
            height = 0;
        }*/
        if ( controller.getSelectionMode() == PolyMeshEditorWindow.FACE_MODE )
        {
            if ( ( ( e.getModifiers() & ActionEvent.SHIFT_MASK ) != 0 ) &&  ( ( e.getModifiers() & ActionEvent.CTRL_MASK ) == 0) )
            {
                if ( Math.abs( width ) > Math.abs( height ) )
                    height = 0.0;
                else
                    width = 0.0;
            }
        }
        else
        {
            if ( ( ( e.getModifiers() & ActionEvent.SHIFT_MASK ) != 0 ) &&  ( ( e.getModifiers() & ActionEvent.CTRL_MASK ) == 0) )
                height = 0.0;
            if ( width < 0.0 )
                width = 0.0;
        }

        // Update the mesh and redisplay.
        int selectMode = controller.getSelectionMode();
        boolean[] sel = null;
        mesh.copyObject( origMesh );
        if ( selectMode == PolyMeshEditorWindow.POINT_MODE )
        {
            sel = mesh.bevelVertices( selected, height );
            theWindow.setHelpText( Translate.text( "bevelExtrudeTool.dragText", new Double( 1.0 - width ), new Double( height ) ) );
        }
        else if ( selectMode == PolyMeshEditorWindow.EDGE_MODE )
        {
            sel = mesh.bevelEdges( selected, height );
            theWindow.setHelpText( Translate.text( "bevelExtrudeTool.dragText", new Double( 1.0 - width ), new Double( height ) ) );
        }
        else
        {
            if ( separateFaces )
                mesh.extrudeFaces( selected, height, (Vec3) null, Math.abs( 1.0 - width ), camZ, ctrlMod, shiftMod );
            else
                mesh.extrudeRegion( selected, height, (Vec3) null, Math.abs( 1.0 - width ), camZ, ctrlMod, shiftMod  );
            sel = new boolean[mesh.getFaces().length];
            if ( Math.abs( 1.0 - width ) > 0.05 )
                for ( int i = 0; i < selected.length; ++i )
                    sel[i] = selected[i];
            theWindow.setHelpText( Translate.text( "bevelExtrudeTool.dragText", new Double( 1.0 - width ), new Double( height ) ) );
        }
        //mesh.copyObject( beveler.bevelMesh( height, width ) );
        controller.setMesh( mesh );
        controller.setSelection( sel );
        //theWindow.setHelpText( Translate.text( "bevelExtrudeTool.dragText", new Double( width ), new Double( height ) ) );
    }


    /**
     *  Description of the Method
     *
     *@param  e     Description of the Parameter
     *@param  view  Description of the Parameter
     */
    public void mouseReleased( WidgetMouseEvent e, ViewerCanvas view )
    {
        if ( noSelection || ( width == 0.0 && height == 0.0 ) )
            return;
        PolyMeshViewer mv = (PolyMeshViewer) view;
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        theWindow.setUndoRecord( new UndoRecord( theWindow, false, UndoRecord.COPY_OBJECT, new Object[]{mesh, origMesh} ) );
        controller.objectChanged();
        theWindow.updateImage();
        theWindow.setHelpText( Translate.text( "bevelExtrudeTool.helpText" ) );
    }


    /**
     *  Description of the Method
     */
    public void iconDoubleClicked()
    {
        BComboBox c = new BComboBox( new String[]{
                Translate.text( "selectionAsWhole" ),
                Translate.text( "individualFaces" )
                } );
        c.setSelectedIndex( separateFaces ? 1 : 0 );
        ComponentsDialog dlg = new ComponentsDialog( theFrame, Translate.text( "applyExtrudeTo" ),
                new Widget[]{c}, new String[]{null} );
        if ( dlg.clickedOk() )
            separateFaces = ( c.getSelectedIndex() == 1 );
    }
}

