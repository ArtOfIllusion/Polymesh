package artofillusion.polymesh;

import java.awt.Color;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.util.Vector;

import javax.swing.ImageIcon;

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
import buoy.event.KeyPressedEvent;
import buoy.event.WidgetMouseEvent;

/** PMExtrudeCurveTool lets the user extrude faces along a curve. */

public class PMExtrudeCurveTool extends EditingTool
{
    private Vector clickPoints;
    private PolyMesh orMesh;
    private boolean[] orSel;
    private MeshEditController controller;
    private ViewerCanvas canvas;
    private Vec3 fromPoint, currentPoint;
    Vec3[] pr;
    boolean constantSize;
    int dragging;
    boolean previewMode = true;
    private static final int HANDLE_SIZE = 7;

    public PMExtrudeCurveTool(EditingWindow fr, MeshEditController controller)
    {
        super(fr);
        clickPoints= new Vector();
        fromPoint = null;
        this.controller = controller;
        initButton("polymesh:extrudecurve");
    }

    public void activate()
    {
        super.activate();
        theWindow.setHelpText(Translate.text("polymesh:extrudeCurveTool.helpText"));
        clickPoints.clear();
        fromPoint = null;
    }

    public int whichClicks()
    {
        return ALL_CLICKS;
    }

    public String getToolTipText()
    {
        return Translate.text("polymesh:extrudeCurveTool.tipText");
    }

    public void mousePressed(WidgetMouseEvent ev, ViewerCanvas view)
    {
        dragging = -1;
        if (clickPoints.size() == 0)
            return;
        if (canvas == view)
        {
            Point e = ev.getPoint();
            for (int i = 0; i < clickPoints.size(); i++ )
            {
                if (!((CurvePoint)clickPoints.elementAt(i)).clickedOnto(ev, view))
                    continue;
                dragging  = i + 1;
                if ( ( ev.getModifiers() & ActionEvent.CTRL_MASK ) != 0 )
                {
                    clickPoints.remove(i);
                    if (previewMode)
                        extrudeFaces(false);
                    theWindow.updateImage();
                    dragging =0;
                }
                return;
            }
        }

    }

    public void mouseDragged(WidgetMouseEvent ev, ViewerCanvas view)
    {
         if (dragging < 1)
            return;
        CurvePoint cp = (CurvePoint)clickPoints.elementAt(dragging-1);
        if (dragging == 1)
            cp.mouseDragged(fromPoint, ev.getPoint());
        else
        {
            Vec3 p =  ((CurvePoint)clickPoints.elementAt(dragging-2)).position;
            cp.mouseDragged(p, ev.getPoint());
        }
        if (previewMode)
            extrudeFaces(false);
        theWindow.updateImage();
    }


    public void mouseReleased(WidgetMouseEvent ev, ViewerCanvas view)
    {
        Point e = ev.getPoint();
        canvas = view;
        if ( clickPoints.size() == 0 && fromPoint == null)
        {
            fromPoint = getInitialPoint();
            if (fromPoint == null)
                return;
            clickPoints.add(new CurvePoint(currentPoint = get3DPoint(fromPoint, e), 1.0));
            if ( ( ev.getModifiers() & ActionEvent.SHIFT_MASK ) != 0 )
                constantSize = true;
            else
                constantSize = false;
            PolyMesh mesh = (PolyMesh)controller.getObject().object;
            orMesh = (PolyMesh) mesh.duplicate();
            orSel = controller.getSelection();
            if (!constantSize)
                computeScales();
            if (previewMode)
                extrudeFaces(false);
            return;
        }
        if ( canvas == view  )
        {
            if (dragging > -1)
            {
                dragging = -1;
                return;
            }
            if ( ( ev.getModifiers() & ActionEvent.CTRL_MASK ) != 0 )
            {
                doCancel();
                return;
            }
            clickPoints.add(  new CurvePoint(currentPoint = get3DPoint(currentPoint, e), 1.0 ) );
            if (!constantSize)
                computeScales();
            if (previewMode)
                extrudeFaces(false);
            theWindow.updateImage();
        }
    }

    private Vec3 getInitialPoint()
    {
        if (clickPoints.size() == 0)
        {
            orSel = controller.getSelection();
            orMesh = (PolyMesh)controller.getObject().object;
        }
        return getInitialPoint(orSel, orMesh);
    }

    private Vec3 getInitialPoint(boolean[] sel, PolyMesh mesh)
    {
        Vec3 fromPoint;
        if (controller.getSelectionMode() != PolyMeshEditorWindow.FACE_MODE)
            return null;
        boolean nonZeroSel = false;
        for (int i = 0; i < sel.length; i++)
        {
            nonZeroSel |= sel[i];
            if (nonZeroSel)
                break;
        }
        if (!nonZeroSel)
            return null;
        PolyMesh.Wface[] faces = mesh.getFaces();
        MeshVertex[] verts = mesh.getVertices();
        fromPoint = new Vec3();
        int count = 0;
        for (int i = 0; i < faces.length; i++)
        {
            if (!sel[i])
                continue;
            ++count;
            Vec3 p = new Vec3();
            int[] fe = mesh.getFaceVertices(faces[i]);
            for (int j = 0; j < fe.length; j++)
                p.add(verts[fe[j]].r);
            p.scale(1.0/(float)fe.length);
            fromPoint.add(p);
        }
        fromPoint.scale(1.0/(float)count);
        return fromPoint;
    }

    private void applyRotationMatrix(boolean[] sel, Mat4 m, PolyMesh mesh)
    {
        PolyMesh.Wface[] faces = mesh.getFaces();
        MeshVertex[] verts = mesh.getVertices();
        boolean[] rotated = new boolean[verts.length];
        for (int i = 0; i < faces.length; i++)
        {
            if (!sel[i])
                continue;
            Vec3 p = new Vec3();
            int[] fe = mesh.getFaceVertices(faces[i]);
            for (int j = 0; j < fe.length; j++)
            {
                if (!rotated[fe[j]])
                {
                    verts[fe[j]].r = m.times(verts[fe[j]].r);
                    rotated[fe[j]] = true;
                }
            }
        }
    }

    public void keyPressed(KeyPressedEvent e, ViewerCanvas view)
    {
        if (! (canvas == view) )
            return;
        int key = e.getKeyCode();
        if (fromPoint != null)
        {
            switch (key)
            {
                case KeyPressedEvent.VK_ESCAPE :
                    System.out.println("escape");
                    doCancel();
                    break;
                case KeyPressedEvent.VK_W :
                    if (clickPoints.size() > 0)
                        clickPoints.remove(clickPoints.size()-1);
                    theWindow.updateImage();
                    break;
                case KeyPressedEvent.VK_ENTER :
                    extrudeFaces(true);
                    break;
                case KeyPressedEvent.VK_J :
                    previewMode = !previewMode;
                    if (previewMode && clickPoints.size()!=0)
                            extrudeFaces(false);
                    else if (clickPoints.size()!=0)
                    {
                        controller.setMesh((PolyMesh)orMesh.duplicate());
                        controller.setSelection(orSel);
                    }
                    theWindow.updateImage();
                    break;
            }
        }
    }

    private void doCancel()
    {
        if (previewMode && clickPoints.size()!=0)
        {
            controller.setMesh((PolyMesh)orMesh.duplicate());
            controller.setSelection(orSel);
        }
        fromPoint = null;
        clickPoints.clear();
        theWindow.updateImage();
    }

    private void computeScales()
    {
        double[] sizes = null;
        sizes = new double[clickPoints.size()];
        double length = 0;
        double cumul = 0;
        Vec3 previous = fromPoint;
        for (int i = 0; i < clickPoints.size(); i++)
        {
            length += ((CurvePoint)clickPoints.get(i)).position.minus(previous).length();
            previous = ((CurvePoint)clickPoints.get(i)).position;
        }
        if (length < 0.005)
            for (int i = 0; i < clickPoints.size(); i++)
            {
                ((CurvePoint)clickPoints.elementAt(i)).amplitude = 1.0;
            }
        else
        {
            previous = fromPoint;
            for (int i = 0; i < clickPoints.size(); i++)
            {
                cumul += ((CurvePoint)clickPoints.get(i)).position.minus(previous).length();
                ((CurvePoint)clickPoints.elementAt(i)).amplitude = 1.0 - cumul/length;
                previous = ((CurvePoint)clickPoints.get(i)).position;
            }
        }
    }

    private void extrudeFaces(boolean done)
    {
        boolean sel[] = orSel;
        if (clickPoints.size() < 1)
            return;
        Vec3 previous;
        PolyMesh mesh = (PolyMesh) orMesh.duplicate();
        Vec3 extdir, nextdir, normal;
        double scale, angle;
        previous = fromPoint;
        double size;
        for (int i = 0; i < clickPoints.size(); i++)
        {
            Vec3[] normals = mesh.getFaceNormals();
            normal = new Vec3();
            for (int j = 0; j < normals.length; j++)
            {
                if (sel[j])
                    normal.add(normals[j]);
            }
            normal.normalize();
            extdir = ((CurvePoint)clickPoints.get(i)).position.minus(previous);
            scale = extdir.length();
            extdir.normalize();
            angle = 0;
            if (i < clickPoints.size() - 1)
            {
                nextdir = ((CurvePoint)clickPoints.get(i+1)).position.minus(((CurvePoint)clickPoints.get(i)).position);
                nextdir.normalize();
                nextdir.add(extdir);
                nextdir.normalize();
            }
            else
                nextdir = extdir;
            angle = Math.acos(extdir.dot(normal));
            nextdir = normal.cross(nextdir);
            if (nextdir.length() < 0.005)
                nextdir = null;
            else
                nextdir.normalize();
            mesh.extrudeRegion(sel, scale, extdir);
            boolean[] newSel = new boolean[mesh.getFaces().length];
            for (int j = 0; j < sel.length; j++)
                newSel[j] = sel[j];
            sel = newSel;
            previous = ((CurvePoint)clickPoints.get(i)).position;
            size = ((CurvePoint)clickPoints.get(i)).amplitude;
            if (nextdir != null)
            {
                Vec3 trans = getInitialPoint(sel, mesh);
                Mat4 m = Mat4.translation(-trans.x, -trans.y, -trans.z);
                if (size > 1e-6 )
                    m = Mat4.scale(size,size,size).times(m);
                m = Mat4.axisRotation(nextdir, angle).times(m);
                m = Mat4.translation(trans.x, trans.y, trans.z).times(m);
                applyRotationMatrix(sel, m, mesh);
                if (size < 1e-6 && i == clickPoints.size() -1)
                {
                    mesh.collapseFaces(sel);
                    sel = new boolean[mesh.getFaces().length];
                }
            }
        }
        controller.setMesh(mesh);
        controller.setSelection(sel);
        if (done)
        {
            fromPoint = null;
            clickPoints.clear();
            theWindow.setUndoRecord( new UndoRecord( theWindow, false, UndoRecord.COPY_OBJECT, new Object[]{mesh, orMesh} ) );
        }
    }

    Vec3 get3DPoint(Vec3 ref, Point clickPoint)
    {
        Vec2 pf = canvas.getCamera().getObjectToScreen().timesXY( ref );
        return ref.plus(canvas.getCamera().findDragVector(ref, (int) Math.round(clickPoint.x - pf.x) , (int)Math.round(clickPoint.y - pf.y) ));
    }


    /** Draw any graphics that this tool overlays on top of the view. */

    public void drawOverlay(ViewerCanvas view)
    {
        Vec3 aPoint = getInitialPoint();
        if (aPoint == null)
            return;
        Vec2 p = view.getCamera().getObjectToScreen().timesXY( aPoint  );
        Point pf = new Point( (int) p.x, (int) p.y );
        view.drawBox( pf.x - HANDLE_SIZE/2, pf.y - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE, Color.red);
        if ( canvas == view )
        {
           if ( clickPoints.size() > 0)
            {
                Vec3 v = ((CurvePoint)clickPoints.get(0)).position;
                Vec2 vp = canvas.getCamera().getObjectToScreen().timesXY( v );
                Point vpp = new Point( (int)Math.round(vp.x), (int)Math.round(vp.y) );
                Point vppt;
                view.drawLine( pf, vpp, Color.black );
                for (int k = 0; k < clickPoints.size() - 1 ; ++k)
                {
                    v = ((CurvePoint)clickPoints.get(k)).position;
                    vp = canvas.getCamera().getObjectToScreen().timesXY( v );
                    vpp = new Point( (int)Math.round(vp.x), (int)Math.round(vp.y) );
                    v = ((CurvePoint)clickPoints.get(k+1)).position;
                    vp = canvas.getCamera().getObjectToScreen().timesXY( v );
                    vppt = new Point( (int)Math.round(vp.x), (int)Math.round(vp.y) );
                    view.drawLine( vpp, vppt, Color.black );
                }
                for (int k = 0; k < clickPoints.size() ; ++k)
                {
                    ((CurvePoint)clickPoints.get(k)).draw(view);
                    //v = ((CurvePoint)clickPoints.get(k)).position;
                    //vp = canvas.getCamera().getObjectToScreen().timesXY( v );
                    //vpp = new Point( (int)Math.round(vp.x), (int)Math.round(vp.y) );
                    //view.drawBox( vpp.x - HANDLE_SIZE/2, vpp.y - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE, Color.red);
                }
            }
        }
    }

    private class CurvePoint
    {
        Vec3 position;
        double angle;
        double amplitude;
        double devAngle;
        short dragging;
        static final int SCALE_HEIGHT = 30;
        static final int NONE = -1;
        static final int MOVING = 0;
        static final int HANDLE_UP = 1;
        static final int HANDLE_DOWN = 2;

        public CurvePoint(Vec3 position ,double amplitude )
        {
            this.position = position;
            amplitude = 1;
            //this.angle = angle;
            this.amplitude = amplitude;
        }

        public void draw(ViewerCanvas view)
        {
            Vec2 p = view.getCamera().getObjectToScreen().timesXY( position  );
            Point pf = new Point( (int) p.x, (int) p.y );
            view.drawBox( pf.x - HANDLE_SIZE/2, pf.y - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE, Color.red);
            double scaleFactor = view.getScale();
            Point handleup = new Point(pf.x, (int)Math.round(pf.y + amplitude*SCALE_HEIGHT));
            Point handledown = new Point(pf.x, (int)Math.round(pf.y - amplitude*SCALE_HEIGHT));
            //Shape dot = new Ellipse2D.Float(handleup.x,handleup.y,8,8);
            //view.fillShape(dot, Color.blue);
            view.drawBox( handleup.x - HANDLE_SIZE/2, handleup.y - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE, Color.blue);
            view.drawBox( handledown.x - HANDLE_SIZE/2, handledown.y - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE, Color.blue);
            view.drawLine(pf, handleup, Color.black);
            view.drawLine(pf, handledown, Color.black);
        }

        public boolean clickedOnto(WidgetMouseEvent ev, ViewerCanvas view)
        {
            Point e = ev.getPoint();
            Vec2 ps =  canvas.getCamera().getObjectToScreen().timesXY( position );
            if (!( e.x < ps.x - HANDLE_SIZE / 2 || e.x > ps.x + HANDLE_SIZE / 2 ||
                 e.y < ps.y - HANDLE_SIZE / 2 || e.y > ps.y + HANDLE_SIZE / 2 ) )
            {
                if ( ( ev.getModifiers() & ActionEvent.SHIFT_MASK ) != 0 )
                {
                    dragging = NONE;
                    amplitude = 1.0;
                }
                else
                    dragging = MOVING;
                return true;
            }
            Point hpt = new Point((int)Math.round(ps.x), (int)Math.round(ps.y + amplitude*SCALE_HEIGHT));
            if (!( e.x < hpt.x - HANDLE_SIZE / 2 || e.x > hpt.x + HANDLE_SIZE / 2 ||
                 e.y < hpt.y - HANDLE_SIZE / 2 || e.y > hpt.y + HANDLE_SIZE / 2 ) )
            {
                dragging = HANDLE_UP;
                return true;
            }
            hpt = new Point((int)Math.round(ps.x), (int)Math.round(ps.y - amplitude*SCALE_HEIGHT));
            if (!( e.x < hpt.x - HANDLE_SIZE / 2 || e.x > hpt.x + HANDLE_SIZE / 2 ||
                 e.y < hpt.y - HANDLE_SIZE / 2 || e.y > hpt.y + HANDLE_SIZE / 2 ) )
            {
                dragging = HANDLE_DOWN;
                return true;
            }
            dragging = NONE;
            return false;
        }

        public void mouseDragged(Vec3 p, Point e)
        {
            Vec2 pt;
            switch (dragging)
            {
                case MOVING :
                    position = get3DPoint(p, e);
                    break;
                case HANDLE_UP :
                    pt =  canvas.getCamera().getObjectToScreen().timesXY( position );
                    amplitude = (e.y - pt.y)/SCALE_HEIGHT;
                    if (amplitude < 0)
                        amplitude = 0;
                    break;
                case HANDLE_DOWN :
                    pt =  canvas.getCamera().getObjectToScreen().timesXY( position );
                    amplitude = (-e.y + pt.y)/SCALE_HEIGHT;
                    if (amplitude < 0)
                        amplitude = 0;
                    break;
                default:
                    break;

            }

        }
    }
}
