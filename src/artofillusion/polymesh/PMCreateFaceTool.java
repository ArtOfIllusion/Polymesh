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

import artofillusion.UndoRecord;
import artofillusion.ViewerCanvas;
import artofillusion.math.Vec2;
import artofillusion.math.Vec3;
import artofillusion.object.MeshVertex;
import artofillusion.ui.EditingTool;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.MeshEditController;
import artofillusion.ui.Translate;
import buoy.event.KeyPressedEvent;
import buoy.event.WidgetMouseEvent;

/** PMKnifeTool is an EditingTool used fto divide edges of PolyMesh objects. */

public class PMCreateFaceTool extends EditingTool
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
    private boolean eligible;
    private int boundaryEdge;
    private int from, to;
    private Vec3 fromPoint;
    Vec3[] pr;
    
    public PMCreateFaceTool(EditingWindow fr, MeshEditController controller)
    {
        super(fr);
        clickPoints= new Vector();
        from = to = -1;
        fromPoint = null;
        this.controller = controller;
        initButton("polymesh:createface");
    }
    
    public void activate()
    {
        super.activate();
        theWindow.setHelpText(Translate.text("polymesh:createFaceTool.helpText"));
        clickPoints.clear();
        from = to = -1;
        fromPoint = null;
    }
    
    public int whichClicks()
    {
        return ALL_CLICKS;
    }
    
    public String getToolTipText()
    {
        return Translate.text("polymesh:createFaceTool.tipText");
    }
    
    public void mouseReleased(WidgetMouseEvent ev, ViewerCanvas view)
    {    
        Point e = ev.getPoint();
        PolyMesh mesh, viewMesh;
        viewMesh = mesh = (PolyMesh)controller.getObject().object;
        boolean mirror;
        int[] invVertTable = null;
        int length = mesh.getVertices().length;
        if ( mesh.getMirrorState() != PolyMesh.NO_MIRROR )
        {
            mirror = true;
            viewMesh = mesh.getMirroredMesh();
            invVertTable = mesh.getInvMirroredVerts();
            length = invVertTable.length;
        }
        QuadMesh subMesh = null;
        MeshVertex[] v = null;
        boolean project = (controller instanceof PolyMeshEditorWindow ? ((PolyMeshEditorWindow) controller).getProjectOntoSurface() : false);
        if (project && mesh.getSmoothingMethod() != PolyMesh.NO_SMOOTHING && viewMesh.getSubdividedMesh() != null) {
        	subMesh = viewMesh.getSubdividedMesh();
        	v = subMesh.getVertices();
    	} else {
    		v = mesh.getVertices();
    	}
        pr = new Vec3[length];
        for (int i = 0; i < length; ++i)
            pr[i] = v[i].r;
        Vec2 p;
        canvas = view;
        double closestz = Double.MAX_VALUE;
        int which = -1;
        for ( int i = 0; i < pr.length; i++ )
        {
            p = canvas.getCamera().getObjectToScreen().timesXY( pr[i] );
            Point ps = new Point( (int) p.x, (int) p.y );
            if ( e.x < ps.x - PolyMeshViewer.HANDLE_SIZE / 2 || e.x > ps.x + PolyMeshViewer.HANDLE_SIZE / 2 ||
                    e.y < ps.y - PolyMeshViewer.HANDLE_SIZE / 2 || e.y > ps.y + PolyMeshViewer.HANDLE_SIZE / 2 )
                continue;
            double z = canvas.getCamera().getObjectToView().timesZ( pr[i] );
            if ( z < closestz )
            {
                if (invVertTable != null)
                    which = invVertTable[i];
                else
                    which = i;
                closestz = z;
            }
        }
        if ( clickPoints.size() == 0 && from == -1 && which != -1)
        {
            from = which;
            fromPoint = pr[from];
            return;
        }
        if ( canvas == view && from != -1 )
        {
            if ( which == -1 )
            {
                if (clickPoints.size() == 0)
                {
                    clickPoints.add( fromPoint = get3DPoint(fromPoint, e) );
                    return;
                }
                for (int i = 0; i < clickPoints.size(); i++ )
                {
                    Vec3 cpv = (Vec3) clickPoints.get(i);
                    Vec2 ps =  canvas.getCamera().getObjectToScreen().timesXY( cpv );
                    if ( e.x < ps.x - PolyMeshViewer.HANDLE_SIZE / 2 || e.x > ps.x + PolyMeshViewer.HANDLE_SIZE / 2 ||
                            e.y < ps.y - PolyMeshViewer.HANDLE_SIZE / 2 || e.y > ps.y + PolyMeshViewer.HANDLE_SIZE / 2 )
                        continue;
                    if ( ( ev.getModifiers() & ActionEvent.CTRL_MASK ) != 0 )
                    {
                        clickPoints.remove(i);
                        theWindow.updateImage();
                        return;
                    }
                    else if ( i == 0)
                    {
                        addFace();
                        return;
                    }
                }
                clickPoints.add(  fromPoint = get3DPoint(fromPoint, e) );
            }
            else
            {
                to = which;
                addFace();
            }
        }
    }

    public void keyPressed(KeyPressedEvent e, ViewerCanvas view)
    {
        if (! (canvas == view) )
            return;
        int key = e.getKeyCode();
        if (from != -1)
        {
            switch (key)
            {
                case KeyPressedEvent.VK_ESCAPE :
                    from = -1;
                    clickPoints.clear();
                    theWindow.updateImage();
                    break;
                case KeyPressedEvent.VK_W :
                    if (clickPoints.size() > 0)
                        clickPoints.remove(clickPoints.size()-1);
                    theWindow.updateImage();
                    break;
            }
        }
    }

    private void addFace()
    {
        if (clickPoints.size() < 1)
            return;
        Vec3[] newPoints = new Vec3[clickPoints.size()];
        for (int i = 0; i < clickPoints.size(); i++)
            newPoints[i] = (Vec3)clickPoints.elementAt(i);
        PolyMesh mesh = (PolyMesh)controller.getObject().object;
        PolyMesh origMesh = (PolyMesh) mesh.duplicate();
        if (to != -1)
        {
            if (!mesh.addFaceFromPoints(from, to, newPoints))
            {
                to = -1;
                return;
            }

        }
        else
        {
            mesh.addStandaloneFace(newPoints);
        }
        controller.setMesh(mesh);
        theWindow.setUndoRecord( new UndoRecord( theWindow, false, UndoRecord.COPY_OBJECT, new Object[]{mesh, origMesh} ) );
        clickPoints.clear();
        fromPoint = null;
        from = to = -1;
    }

    Vec3 get3DPoint(Vec3 ref, Point clickPoint)
    {
        Vec2 pf = canvas.getCamera().getObjectToScreen().timesXY( ref );
        return ref.plus(canvas.getCamera().findDragVector(ref, (int) Math.round(clickPoint.x - pf.x) , (int)Math.round(clickPoint.y - pf.y) ));
    }

    
    /** Draw any graphics that this tool overlays on top of the view. */
    
    public void drawOverlay(ViewerCanvas view)
    {
        if ( canvas == view && from != -1)
        {
            Vec2 p = view.getCamera().getObjectToScreen().timesXY( pr[from]  );
            Point pf = new Point( (int) p.x, (int) p.y );
            view.drawBox( pf.x - PolyMeshViewer.HANDLE_SIZE/2, pf.y - PolyMeshViewer.HANDLE_SIZE/2, PolyMeshViewer.HANDLE_SIZE, PolyMeshViewer.HANDLE_SIZE, Color.red);
            if ( clickPoints.size() > 0)
            {
                Vec3 v = (Vec3)clickPoints.elementAt(0);
                Vec2 vp = canvas.getCamera().getObjectToScreen().timesXY( v );
                Point vpp = new Point( (int)Math.round(vp.x), (int)Math.round(vp.y) );
                Point vppt;
                view.drawLine( pf, vpp, Color.black );
                for (int k = 0; k < clickPoints.size() - 1 ; ++k)
                {
                    v = (Vec3)clickPoints.elementAt(k);
                    vp = canvas.getCamera().getObjectToScreen().timesXY( v );
                    vpp = new Point( (int)Math.round(vp.x), (int)Math.round(vp.y) );
                    v = (Vec3)clickPoints.elementAt(k+1);
                    vp = canvas.getCamera().getObjectToScreen().timesXY( v );
                    vppt = new Point( (int)Math.round(vp.x), (int)Math.round(vp.y) );
                    view.drawLine( vpp, vppt, Color.black );
                }
                for (int k = 0; k < clickPoints.size() ; ++k)
                {
                    v = (Vec3)clickPoints.elementAt(k);
                    vp = canvas.getCamera().getObjectToScreen().timesXY( v );
                    vpp = new Point( (int)Math.round(vp.x), (int)Math.round(vp.y) );
                    view.drawBox( vpp.x - PolyMeshViewer.HANDLE_SIZE/2, vpp.y - PolyMeshViewer.HANDLE_SIZE/2, PolyMeshViewer.HANDLE_SIZE, PolyMeshViewer.HANDLE_SIZE, Color.red);
                }
            }
        }
    }
}