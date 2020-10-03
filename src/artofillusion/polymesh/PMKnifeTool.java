/* Copyright (C) 1999-2004 by Peter Eastman

This program is free software; you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation; either version 2 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY 
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.polymesh;

import java.awt.Color;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.util.Vector;

import javax.swing.ImageIcon;

import artofillusion.Camera;
import artofillusion.MeshViewer;
import artofillusion.UndoRecord;
import artofillusion.ViewerCanvas;
import artofillusion.math.Mat4;
import artofillusion.math.Vec2;
import artofillusion.math.Vec3;
import artofillusion.object.MeshVertex;
import artofillusion.ui.EditingTool;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.MeshEditController;
import artofillusion.ui.Translate;
import buoy.event.WidgetMouseEvent;

/** PMKnifeTool is an EditingTool used fto divide edges of PolyMesh objects. */

public class PMKnifeTool extends EditingTool
{
    private Vector clickPoints;
    private UndoRecord undo;
    private MeshEditController controller;
    private PolyMesh originalMesh;
    private boolean dragging;
    private Point dragPoint;
    private ViewerCanvas canvas;
    private Point screenVert[];
    private boolean[] selection, vertSelection;
    
    public PMKnifeTool(EditingWindow fr, MeshEditController controller)
    {
        super(fr);
        clickPoints= new Vector();
        this.controller = controller;
        initButton("polymesh:knife");
    }
    
    public void activate()
    {
        super.activate();
        theWindow.setHelpText(Translate.text("polymesh:knifeTool.helpText"));
    }
    
    public int whichClicks()
    {
        return ALL_CLICKS;
    }
    
    public String getToolTipText()
    {
        return Translate.text("polymesh:knifeTool.tipText");
    }
    
    public void mousePressed(WidgetMouseEvent e, ViewerCanvas view)
    {    
        if (!dragging)
        {
            dragging = true;
            if (controller.getSelectionMode() == PolyMeshEditorWindow.EDGE_MODE)
                selection = controller.getSelection();
            else
            {
                controller.setSelectionMode( PolyMeshEditorWindow.EDGE_MODE);
                selection = controller.getSelection();
            }
            int sel = 0;
            for (int i = 0; i < selection.length; i++)
                if (selection[i])
                {
                    ++sel;
                    break;
                }
            if (sel == 0)
                selection = null;
            controller.setSelectionMode( PolyMeshEditorWindow.POINT_MODE );
            vertSelection = controller.getSelection();
            clickPoints.clear();
            clickPoints.add( e.getPoint() );
            dragPoint = e.getPoint();
            originalMesh = (PolyMesh) ( (PolyMesh) controller.getObject().object ).duplicate();
            //controller.setSelection( new boolean[originalMesh.getVertices().length] );
            MeshVertex[] v = originalMesh.getVertices();
            screenVert = new Point[v.length];
            Vec2 p;
            for ( int i = 0; i < v.length; i++ )
            {
                p = view.getCamera().getObjectToScreen().timesXY( v[i].r );
                screenVert[i] = new Point( (int) p.x, (int) p.y );
            }
            canvas = view;
        }
    }
    
    public void mouseDragged(WidgetMouseEvent e, ViewerCanvas view)
    {
        MeshViewer mv = (MeshViewer) view;
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        Camera cam = view.getCamera();
        dragPoint = e.getPoint();
        
        PolyMesh.Wedge[] edges = originalMesh.getEdges();
        Vec3[] normals = originalMesh.getFaceNormals();
        double[] fraction = new double[edges.length/2];
        double f, s1, s2;
        Camera theCamera = mv.getCamera();
        Vec3 zdir = theCamera.getCameraCoordinates().getZDirection();
        Mat4 m = theCamera.getObjectToWorld();
        for (int i = 0; i < edges.length/2; i++)
        {
            if ( ( e.getModifiers() & ActionEvent.SHIFT_MASK ) == 0 )
            {
                if (edges[i].face > -1)
                    s1 = m.times(normals[edges[i].face]).dot(zdir);
                else
                    s1 = -1;
                if ( edges[edges[i].hedge].face > -1)
                    s2 = m.times(normals[edges[edges[i].hedge].face]).dot(zdir);
                else
                    s2 = -1;
                if ( s1 > 0 && s2 > 0)
                    continue;
            }
            fraction[i] = -1;
            if ( selection != null && ! selection[i] )
                continue;
            for (int k = 0; k < clickPoints.size() - 1 ; ++k)
            {
                f = findIntersection( (Point)clickPoints.elementAt(k), (Point)clickPoints.elementAt(k+1), screenVert[edges[i].vertex], screenVert[edges[edges[i].hedge].vertex]);
                if ( f > 0 )
                    fraction[i] = f;
            }
            f = findIntersection( (Point)clickPoints.elementAt(clickPoints.size()-1), dragPoint, screenVert[edges[i].vertex], screenVert[edges[edges[i].hedge].vertex]);
            if ( f > 0 )
                fraction[i] = f;
        }
        mesh.copyObject( originalMesh );
        boolean[] sel = mesh.divideEdges(fraction);
        mesh.connectVertices( sel );
        controller.objectChanged();
        for (int i = 0; i < vertSelection.length; ++i)
            sel[i] |= vertSelection[i];
        controller.setSelection( sel );
        theWindow.updateImage();
        theWindow.setHelpText(Translate.text("polymesh:knifeTool.dragText") );
    }
    
    public void mouseReleased(WidgetMouseEvent e, ViewerCanvas view)
    {
        if ( ( e.getModifiers() & ActionEvent.CTRL_MASK ) == 0 )
        {
            dragPoint = e.getPoint();
            PolyMesh mesh = (PolyMesh) controller.getObject().object;
            dragging = false;
            controller.objectChanged();
            theWindow.updateImage();
            theWindow.setHelpText(Translate.text("polymesh:knifeTool.helpText"));
            boolean[] sel = controller.getSelection();
            for (int i = 0; i < vertSelection.length; ++i)
                sel[i] &= !vertSelection[i];
            controller.setSelection( sel );
            theWindow.updateImage();
            theWindow.setUndoRecord( new UndoRecord( theWindow, false, UndoRecord.COPY_OBJECT, new Object[]{mesh, originalMesh} ) );
            undo = null;
        }
        else
        {
            clickPoints.add( dragPoint );
        }

    }
    
    /** Draw any graphics that this tool overlays on top of the view. */
    
    public void drawOverlay(ViewerCanvas view)
    {
        if (dragging && canvas == view)
        {
            for (int k = 0; k < clickPoints.size() - 1 ; ++k)
                view.drawLine( (Point)clickPoints.elementAt(k), (Point)clickPoints.elementAt(k+1), Color.black );
            view.drawLine( (Point)clickPoints.elementAt(clickPoints.size()-1), dragPoint, Color.black );
            for (int k = 0; k < clickPoints.size() ; ++k)
            {
                Point p1 = new Point( (Point)clickPoints.elementAt(k) );
                Point p2 = new Point( p1 );
                p1.x += 5;
                p2.x -= 5;
                view.drawLine( p1, p2, Color.black);
                p1.x -= 5;
                p1.y += 5;
                p2.x += 5;
                p2.y -= 5;
                view.drawLine( p1, p2, Color.black);
            }
            Point p1 = new Point( dragPoint );
            Point p2 = new Point( p1 );
            p1.x += 5;
            p2.x -= 5;
            view.drawLine( p1, p2, Color.black);
            p1.x -= 5;
            p1.y += 5;
            p2.x += 5;
            p2.y -= 5;
            view.drawLine( p1, p2, Color.black);
        }
    }
    
    private double findIntersection( Point p0, Point d0, Point p1, Point d1 )
    {
        //System.out.println( p0.x + " " + p0.y );
        //System.out.println( d0.x + " " + d0.y );
        //System.out.println( p1.x + " " + p1.y );
        //System.out.println( d1.x + " " + d1.y );
        Vec2 p0v = new Vec2( p0.x, p0.y );
        Vec2 d0v = new Vec2( d0.x, d0.y ).minus( p0v);
        Vec2 p1v = new Vec2( p1.x, p1.y );
        Vec2 d1v = new Vec2( d1.x, d1.y ).minus( p1v);
        Vec2 e = p1v.minus(p0v);
        double kross = d0v.cross(d1v);
        double sqrkross = kross*kross;
        double len0 = d0v.length2();
        double len1 = d1v.length2();
        if ( sqrkross > 1.0e-12 * len0 * len1 )
        {
            double s = e.cross(d1v) / kross;
            //System.out.println( "s:" + s);
            if ( s < 0 || s > 1)
                return -1;
            s = e.cross(d0v) / kross;
            //System.out.println( "s:" + s);
            if ( s < 0 || s > 1)
                return -1;
            return 1 - s;
        }
        //parallel lines        
        return -1;
    }
}