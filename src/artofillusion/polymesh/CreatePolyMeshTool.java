/* Copyright (C) 2001-2004 by Peter Eastman, 2005 by Francois Guillet
   Changes copyright (C) 2023 by Maksim Khramov
This program is free software; you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation; either version 2 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY 
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.polymesh;

import java.awt.Point;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;

import artofillusion.ArtOfIllusion;
import artofillusion.Camera;
import artofillusion.LayoutWindow;
import artofillusion.Scene;
import artofillusion.SceneViewer;
import artofillusion.UndoRecord;
import artofillusion.ViewerCanvas;
import artofillusion.animation.PositionTrack;
import artofillusion.animation.RotationTrack;
import artofillusion.math.CoordinateSystem;
import artofillusion.math.Vec3;
import artofillusion.object.Mesh;
import artofillusion.object.ObjectInfo;
import artofillusion.ui.EditingTool;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.Translate;
import artofillusion.ui.UIUtilities;
import buoy.event.CommandEvent;
import buoy.event.ValueChangedEvent;
import buoy.event.WidgetMouseEvent;
import buoy.event.WindowClosingEvent;
import buoy.widget.BButton;
import buoy.widget.BComboBox;
import buoy.widget.BDialog;
import buoy.widget.BFrame;
import buoy.widget.BLabel;
import buoy.widget.BSpinner;
import buoy.widget.BorderContainer;
import buoy.xml.WidgetDecoder;

/** CreatePolyMeshTool is an EditingTool used for creating PolyMesh objects. */

public class CreatePolyMeshTool extends EditingTool
{
    private EditingWindow edw;
    private int counter = 1;
    private int shape = 0;
    private int usize =3;
    private int vsize = 3;
    private int smoothingMethod;
    private PolyMesh templateMesh;
    
    boolean shiftDown;
    Point clickPoint;
    
    public CreatePolyMeshTool(EditingWindow fr)
    {
        super(fr);
        edw = fr;
        initButton("polymesh:polymesh");
    }
    
    public void activate()
    {
        super.activate();
        setHelpText();
    }
    
    private void setHelpText()
    {
        String shapeDesc, smoothingDesc;
        if (shape == 0)
            shapeDesc = "cube";
        else if (shape == 2)
            shapeDesc = "octahedron";
        else if (shape == 3)
            shapeDesc = "cylinder";
        else if (shape == 1)
            shapeDesc = "single face";
        else
            shapeDesc = "flat";
        switch(smoothingMethod)
        {
            default:
            case 0:
                smoothingDesc = "none";
                break;
            case 1:
                smoothingDesc = "shading";
                break;
            case 2:
                smoothingDesc = "approximating";
                break;
            case 3:
                smoothingDesc = "interpolating";
                break; 
        }
        if ( "none".equals(smoothingDesc))
            smoothingDesc = Translate.text("polymesh:none" );
        else
            smoothingDesc = Translate.text("menu."+smoothingDesc).toLowerCase();
        if ( shape <= 2 )
            theWindow.setHelpText(Translate.text("polymesh:createPolyMeshTool.helpText1",
                new Object [] { Translate.text("polymesh:createPolyMeshTool."+shapeDesc), smoothingDesc}));
        else
            theWindow.setHelpText(Translate.text("polymesh:createPolyMeshTool.helpText2",
                new Object [] { Translate.text("polymesh:createPolyMeshTool."+shapeDesc),
                                Integer.toString(usize), Integer.toString(vsize), smoothingDesc}));
    }
    
    public String getToolTipText()
    {
        return Translate.text("polymesh:createPolyMeshTool.tipText");
    }
    
    public void mousePressed(WidgetMouseEvent e, ViewerCanvas view)
    {
        clickPoint = e.getPoint();
        shiftDown = e.isShiftDown();
        ((SceneViewer) view).beginDraggingBox(clickPoint, shiftDown);
    }
    
    public void mouseReleased(WidgetMouseEvent e, ViewerCanvas view)
    {
        Scene theScene = ((LayoutWindow) theWindow).getScene();
        Camera cam = view.getCamera();
        Point dragPoint = e.getPoint();
        Vec3 v1, v2, v3, orig, xdir, ydir, zdir;
        double xsize, ysize, zsize;
        int i;
        
        if (shiftDown)
        {
            if (Math.abs(dragPoint.x-clickPoint.x) > Math.abs(dragPoint.y-clickPoint.y))
            {
                if (dragPoint.y < clickPoint.y)
                    dragPoint.y = clickPoint.y - Math.abs(dragPoint.x-clickPoint.x);
                else
                    dragPoint.y = clickPoint.y + Math.abs(dragPoint.x-clickPoint.x);
            }
            else
            {
                if (dragPoint.x < clickPoint.x)
                    dragPoint.x = clickPoint.x - Math.abs(dragPoint.y-clickPoint.y);
                else
                    dragPoint.x = clickPoint.x + Math.abs(dragPoint.y-clickPoint.y);
            }
        }
        if (dragPoint.x == clickPoint.x || dragPoint.y == clickPoint.y)
        {
            ((SceneViewer) view).repaint();
            return;
        }
        v1 = cam.convertScreenToWorld(clickPoint, cam.getDistToScreen());
        v2 = cam.convertScreenToWorld(new Point(dragPoint.x, clickPoint.y), cam.getDistToScreen());
        v3 = cam.convertScreenToWorld(dragPoint, cam.getDistToScreen());
        orig = v1.plus(v3).times(0.5);
        if (dragPoint.x < clickPoint.x)
            xdir = v1.minus(v2);
        else
            xdir = v2.minus(v1);
        if (dragPoint.y < clickPoint.y)
            ydir = v3.minus(v2);
        else
            ydir = v2.minus(v3);
        xsize = xdir.length();
        ysize = ydir.length();
        xdir = xdir.times(1.0/xsize);
        ydir = ydir.times(1.0/ysize);
        zdir = xdir.cross(ydir);
        zsize = Math.min(xsize, ysize);
        
        //SplineMesh obj = new SplineMesh(v, usmoothness, vsmoothness, smoothing, shape != FLAT, shape == TORUS);
        PolyMesh obj = null;
        if (templateMesh == null)
            obj = new PolyMesh( shape, usize, vsize, xsize, ysize, zsize);
        else
        {
            obj = (PolyMesh) templateMesh.duplicate();
            obj.setSize( xsize, ysize, zsize);
        }
        obj.setSmoothingMethod(smoothingMethod);
        ObjectInfo info = new ObjectInfo(obj, new CoordinateSystem(orig, zdir, ydir), "PolyMesh "+(counter++));
        info.addTrack(new PositionTrack(info), 0);
        info.addTrack(new RotationTrack(info), 1);
        UndoRecord undo = new UndoRecord(theWindow, false);
        undo.addCommandAtBeginning(UndoRecord.SET_SCENE_SELECTION, new Object [] {((LayoutWindow) theWindow).getSelectedIndices()});
        ((LayoutWindow) theWindow).addObject(info, undo);
        theWindow.setUndoRecord(undo);
        ((LayoutWindow) theWindow).setSelection(theScene.getNumObjects()-1);
        theWindow.updateImage();
        theWindow.setModified();
    }
    
    
    
   public void iconDoubleClicked()
  {
    new PolyMeshToolDialog( edw.getFrame() );
    setHelpText();
  }
    
    /**
     *  Dialog for entering mesh type
     *
     *@author     francois
     *@created    24 janvier 2005
     */
    public class PolyMeshToolDialog extends BDialog
    {
        private BComboBox typeCombo;
        private BLabel sizeLabel;
        private BSpinner xSpinner;
        private BLabel byLabel;
        private BSpinner ySpinner;
        private BLabel meshType;
        private BButton okButton;
        private BButton cancelButton;
        private BComboBox smoothCombo;
        private BLabel smoothLabel;
        private int templateStart;
        
        /**
         *  Constructor for the PolyMeshToolDialog object
         *
         *@param  parent  Description of the Parameter
         */
        public PolyMeshToolDialog( BFrame parent )
        {
            super( parent, Translate.text("polymesh:polyMeshToolDialogTitle" ), true );
            InputStream is = null;
            try
            {
                WidgetDecoder decoder = new WidgetDecoder( is = getClass().getResource( "interfaces/createTool.xml" ).openStream() );
                setContent( (BorderContainer) decoder.getRootObject() );
                typeCombo = ( (BComboBox) decoder.getObject( "typeCombo" ) );
                typeCombo.add( Translate.text("polymesh:cube" ) );
                typeCombo.add( Translate.text("polymesh:face" ) );
                typeCombo.add( Translate.text("polymesh:octahedron" ) );
                typeCombo.add( Translate.text("polymesh:cylinder" ) );
                typeCombo.add( Translate.text("polymesh:flatMesh" ) );
                templateStart = 5;
                File templateDir = new File( ArtOfIllusion.PLUGIN_DIRECTORY + File.separator + "PolyMeshTemplates" + File.separator );
                if ( templateDir.isDirectory() )
                {
                    String[] files = templateDir.list();
                    for (int i = 0; i < files.length; i++)
                        typeCombo.add( files[i] );
                }
                sizeLabel = ( (BLabel) decoder.getObject( "sizeLabel" ) );
                sizeLabel.setText( Translate.text( "polymesh:" + sizeLabel.getText() ) );
                xSpinner = ( (BSpinner) decoder.getObject( "xSpinner" ) );
                xSpinner.setValue(usize);
                byLabel = ( (BLabel) decoder.getObject( "byLabel" ) );
                byLabel.setText( Translate.text("polymesh:" +  byLabel.getText() ) );
                ySpinner = ( (BSpinner) decoder.getObject( "ySpinner" ) );
                ySpinner.setValue(vsize);
                meshType = ( (BLabel) decoder.getObject( "meshType" ) );
                meshType.setText( Translate.text("polymesh:" +  meshType.getText() ) );
                okButton = ( (BButton) decoder.getObject( "okButton" ) );
                okButton.setText( Translate.text("polymesh:" +  okButton.getText() ) );
                cancelButton = ( (BButton) decoder.getObject( "cancelButton" ) );
                cancelButton.setText( Translate.text("polymesh:" +  cancelButton.getText() ) );
                okButton.addEventLink( CommandEvent.class, this, "doOK" );
                typeCombo.setSelectedIndex( shape );
                typeCombo.addEventLink( ValueChangedEvent.class, this, "doComboChanged" );
                typeCombo.setPreferredVisibleRows( typeCombo.getItemCount() );
                smoothCombo = ( (BComboBox) decoder.getObject( "smoothCombo" ) );
                smoothCombo.add( Translate.text( "menu.none" ) );
                smoothCombo.add( Translate.text( "menu.shading" ) );
                smoothCombo.add( Translate.text( "menu.approximating" ) );
                smoothCombo.add( Translate.text( "menu.interpolating" ) );
                smoothLabel = ( (BLabel) decoder.getObject( "smoothLabel" ) );
                smoothLabel.setText( Translate.text( "SmoothingMethod") + ":" );
                smoothCombo.addEventLink( ValueChangedEvent.class, this, "doSmoothComboChanged" );
                smoothCombo.setSelectedIndex( getSmoothComboIndex() );
                setSpinnerColumns( xSpinner, 2 );
                setSpinnerColumns( ySpinner, 2 );
                if ( typeCombo.getSelectedIndex() >= 2 )
                    enableSize();
                else
                    disableSize();
                Object closeObj =
                        new Object()
                    {
                            void processEvent()
                            {
                                dispose();
                            }
                        };
                cancelButton.addEventLink( CommandEvent.class, closeObj );
                this.addEventLink( WindowClosingEvent.class, closeObj );
            }
            catch ( IOException ex )
            {
                ex.printStackTrace();
            }
            finally
            {
                if (is != null)
                try
                {
                    is.close();
                }
                catch ( IOException ex )
                {
                    ex.printStackTrace();
                }
            }
            pack();
            UIUtilities.centerWindow( this );
            setVisible( true );
        }
        
        
        /**
         *  Description of the Method
         */
        private void disableSize()
        {
            sizeLabel.setEnabled( false );
            byLabel.setEnabled( false );
            xSpinner.setEnabled( false );
            ySpinner.setEnabled( false );
        }
        
        
        /**
         *  Description of the Method
         */
        private void enableSize()
        {
            sizeLabel.setEnabled( true );
            byLabel.setEnabled( true );
            xSpinner.setEnabled( true );
            ySpinner.setEnabled( true );
        }
        
        
        /**
         *  Description of the Method
         */
        private void doComboChanged()
        {
            if ( typeCombo.getSelectedIndex() >= 3 && typeCombo.getSelectedIndex() < templateStart)
            {
                enableSize();
                switch ( typeCombo.getSelectedIndex() )
                {
                    case 3:
                        xSpinner.setValue(12);
                        ySpinner.setValue(1);
                        break;
                    case 4:
                        xSpinner.setValue(3);
                        ySpinner.setValue(3);
                        break;
                }
            }
            else
                disableSize();
            
        }
        
        private void doSmoothComboChanged()
        {
            switch (smoothCombo.getSelectedIndex())
            {
                default:
                case 0:
                    smoothingMethod = Mesh.NO_SMOOTHING;
                    break;
                case 1:
                    smoothingMethod = Mesh.SMOOTH_SHADING;
                    break;
                case 2:
                    smoothingMethod = Mesh.APPROXIMATING;
                    break;
                case 3:
                    smoothingMethod = Mesh.INTERPOLATING;
                    break;
            }
        }
        
        private int getSmoothComboIndex()
        {
            switch (smoothingMethod)
            {
                default:
                case Mesh.NO_SMOOTHING:
                    return 0;
                case Mesh.SMOOTH_SHADING:
                    return 1;
                case Mesh.APPROXIMATING:
                    return 2;
                case Mesh.INTERPOLATING:
                    return 3;
            }
        }
        
        
        /**
         *  Description of the Method
         */
        private void doOK()
        {
            shape = typeCombo.getSelectedIndex();
            usize = (Integer) xSpinner.getValue();
            vsize = (Integer) ySpinner.getValue();
            int type = typeCombo.getSelectedIndex();
            if (type < templateStart)
                templateMesh = null;
            else
            {
                templateMesh = null;
                try
                {
                    File file = new File( ArtOfIllusion.PLUGIN_DIRECTORY + File.separator + "PolyMeshTemplates" + File.separator + (String)typeCombo.getSelectedValue() );
                    DataInputStream dis = new DataInputStream( new FileInputStream( file ) );
                    templateMesh = new PolyMesh( dis );
                    templateMesh.setSmoothingMethod( smoothingMethod );
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            dispose();
        }
        
        /**
         *  Sets the number of columns displayed by a spinner
         *
         *@param  spinner  The concerned BSpinner
         *@param  numCol   The new number of columns to show
         */
        public void setSpinnerColumns( BSpinner spinner, int numCol )
        {
            JSpinner.NumberEditor ed = (JSpinner.NumberEditor) spinner.getComponent().getEditor();
            JFormattedTextField field = ed.getTextField();
            field.setColumns( numCol );
            spinner.getComponent().setEditor( ed );
        }
    }
}