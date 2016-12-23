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

public class PMSewTool extends EditingTool
{
    private Point clickPoint;
    private UndoRecord undo;
    private MeshEditController controller;
    private PolyMesh originalMesh;
    private boolean dragging;
    private Point dragPoint;
    private ViewerCanvas canvas;
    private Point screenVert[];
    private boolean[] selection;
    
    public PMSewTool(EditingWindow fr, MeshEditController controller)
    {
        super(fr);
        this.controller = controller;
        initButton("polymesh:sew");
    }
    
    public void activate()
    {
        super.activate();
        theWindow.setHelpText(Translate.text("polymesh:sewTool.helpText"));
    }
    
    public int whichClicks()
    {
        return ALL_CLICKS;
    }
    
    public String getToolTipText()
    {
        return Translate.text("polymesh:sewTool.tipText");
    }
    
    public void mousePressed(WidgetMouseEvent e, ViewerCanvas view)
    {    
        if (!dragging)
        {
            dragging = true;
            selection = null;
            originalMesh = (PolyMesh) ( (PolyMesh) controller.getObject().object ).duplicate();
            PolyMesh.Wedge[] edges = originalMesh.getEdges();
            MeshVertex[] v = originalMesh.getVertices();
            Vec3[] pr =new Vec3[v.length];
            for (int i = 0; i < pr.length; ++i)
                pr[i] = v[i].r;
            if (! (controller.getSelectionMode() == PolyMeshEditorWindow.EDGE_MODE) )
                controller.setSelectionMode( PolyMeshEditorWindow.EDGE_MODE);
            else
                selection = controller.getSelection();
            int projectedEdge[] = null;
            if (controller instanceof PolyMeshEditorWindow)
                projectedEdge = ((PolyMeshEditorWindow) controller).findProjectedEdges();
            if (projectedEdge != null)
            {
                QuadMesh mesh = ((PolyMeshEditorWindow) controller).getSubdividedPolyMesh();
                MeshVertex[] vs = mesh.getVertices();
                for (int i = 0; i < pr.length; ++i)
                    pr[i] = vs[i].r;
            }
            int selNum = 0;
            if (selection != null)
                for (int i = 0; i < selection.length; ++i)
                if (selection[i])
                {
                    if (edges[i].face != -1 && edges[edges[i].hedge].face != -1)
                    {
                        selNum = -1;
                        break;
                    }
                    ++selNum;
                }
            if (selNum < 2)
                selection = null;
            clickPoint = new Point( e.getPoint() );
            dragPoint = e.getPoint();
            //controller.setSelection( new boolean[originalMesh.getVertices().length] );
            screenVert = new Point[v.length];
            Vec2 p;
            for ( int i = 0; i < v.length; i++ )
            {
                p = view.getCamera().getObjectToScreen().timesXY( pr[i] );
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
        dragPoint = new Point( e.getPoint() );
        
        PolyMesh.Wedge[] edges = originalMesh.getEdges();
        PolyMesh.Wvertex[] v = (PolyMesh.Wvertex[]) originalMesh.getVertices();
        Vec3[] normals = originalMesh.getFaceNormals();
        int[] sewEdges = new int[2];
        double f, dist;
        Camera theCamera = mv.getCamera();
        Vec3 zdir = theCamera.getCameraCoordinates().getZDirection();
        Mat4 m = theCamera.getObjectToWorld();
        double minDist = Double.MAX_VALUE;
       /* if ( ( e.getModifiers() & ActionEvent.SHIFT_MASK ) == 0 )
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
            if ( selection != null && ! selection[i] )
                continue;*/
        sewEdges[0] = sewEdges[1] = -1;
        for (int i = 0; i < edges.length/2; i++)
        {
            if (edges[i].face != -1 && edges[edges[i].hedge].face != -1)
                continue;
            f = findIntersection( screenVert[edges[i].vertex], screenVert[edges[edges[i].hedge].vertex], clickPoint, dragPoint);
            if ( f > 0 )
            {
                if( f < minDist)
                {
                    sewEdges[0] = i;
                    minDist = f;
                }

            }
        }
        //System.out.println( sewEdges[0] );
        if ( ( e.getModifiers() & ActionEvent.CTRL_MASK ) == 0 )
        {
            if (sewEdges[0] >= 0)
            {
                minDist = Double.MAX_VALUE;
                double z;
                for (int i = 0; i < edges.length/2; i++)
                {
                    if (edges[i].face != -1 && edges[edges[i].hedge].face != -1)
                        continue;
                    if (i == sewEdges[0])
                        continue;
                    f = findIntersection( screenVert[edges[i].vertex], screenVert[edges[edges[i].hedge].vertex], clickPoint, dragPoint);
                    if ( f > 0 )
                    {
                        if( (1 - f) < minDist)
                        {
                            sewEdges[1] = i;
                            minDist = 1-f;
                        }

                    }
                }
            }
            mesh.copyObject( originalMesh );
            boolean[] sel = null;
            if ( sewEdges[1] >=0  )
                sel = mesh.mergeEdges( sewEdges[0], sewEdges[1], selection, ( e.getModifiers() & ActionEvent.SHIFT_MASK ) == 1 );
            controller.objectChanged();
            if ( selection != null && sel != null)
                controller.setSelection( sel );
            theWindow.updateImage();
            theWindow.setHelpText(Translate.text("polymesh:sewTool.dragText") );
        }
        else
        {
            if (sewEdges[0] >= 0)
            {
                mesh.copyObject( originalMesh );
                boolean[] sel = new boolean[edges.length/2];
                sel[sewEdges[0]] = true;
                sel = mesh.closeBoundary(sel);
                controller.objectChanged();
                if ( selection != null && sel != null)
                    controller.setSelection( sel );
                theWindow.updateImage();
                theWindow.setHelpText(Translate.text("polymesh:sewTool.dragText") );
            }
        }
    }
    
    public void mouseReleased(WidgetMouseEvent e, ViewerCanvas view)
    {
        PolyMesh mesh = (PolyMesh) controller.getObject().object;
        dragging = false;
        controller.objectChanged();
        theWindow.updateImage();
        theWindow.setHelpText(Translate.text("polymesh:sewTool.helpText"));
        theWindow.setUndoRecord( new UndoRecord( theWindow, false, UndoRecord.COPY_OBJECT, new Object[]{mesh, originalMesh} ) );
        undo = null;

    }
    
    /** Draw any graphics that this tool overlays on top of the view. */
    
    public void drawOverlay(ViewerCanvas view)
    {
        if (dragging && canvas == view)
        {
            view.drawLine( clickPoint, dragPoint, Color.black );
            Point p1 = new Point( clickPoint );
            Point p2 = new Point( p1 );
            p1.x += 5;
            p2.x -= 5;
            view.drawLine( p1, p2, Color.black);
            p1.x -= 5;
            p1.y += 5;
            p2.x += 5;
            p2.y -= 5;
            view.drawLine( p1, p2, Color.black);
            p1 = new Point( dragPoint );
            p2 = new Point( p1 );
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
            if ( s < 0 || s > 1)
                return -1;
            s = e.cross(d0v) / kross;
            if ( s < 0 || s > 1)
                return -1;
            return 1 - s;
        }
        return -1;
    }
}