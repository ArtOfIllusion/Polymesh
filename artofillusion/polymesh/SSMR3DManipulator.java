package artofillusion.polymesh;

import java.awt.Color;
import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;

import artofillusion.Camera;
import artofillusion.MeshEditorWindow;
import artofillusion.MeshViewer;
import artofillusion.ViewerCanvas;
import artofillusion.math.CoordinateSystem;
import artofillusion.math.Mat4;
import artofillusion.math.Vec2;
import artofillusion.math.Vec3;
import artofillusion.object.Mesh;
import artofillusion.object.MeshVertex;
import artofillusion.ui.MeshEditController;
import artofillusion.ui.ThemeManager;
import artofillusion.ui.Translate;
import buoy.event.KeyPressedEvent;
import buoy.event.ToolTipEvent;
import buoy.event.WidgetMouseEvent;
import buoy.widget.BToolTip;

/**
 * This is the manipulator responsible for moving, resizing and rotating selections (2D).
 * SSMR = Select Scale Move Rotate
 */
public class SSMR3DManipulator
extends SSMRManipulator
{
    private Rectangle[] boxes;
    private Rectangle extraUVBox;
    private final static int HANDLE_SIZE = 12;
    private int handle;
    private boolean dragging = false;
    private Point baseClick;
    private Runnable valueWidgetCallback, validateWidgetValue, abortWidgetValue;
    private boolean isCtrlDown, isShiftDown;
    private int button;
    private Vec3 rotateCenter;
    private Vec3 xaxis, yaxis, zaxis;
    private Vec2 x2Daxis, y2Daxis, z2Daxis;
    private Vec2 x2DaxisNormed, y2DaxisNormed, z2DaxisNormed;
    private int rotSegment;
    private double rotAngle;
    private Point centerPoint, centerLocation;
    private Vec3 center;
    private double scale, extraScaleX, extraScaleY;
    private int selCenter, selNum;
    private Vec3 toolHandePos;
    private Vec2[] featurePoints2d;
    private double axisLength, orAxisLength;
    private RotationHandle[] xyzRotHandles;
    private RotationHandle[] specificRotHandles;
    private RotationHandle[] uvRotationHandle;
    private RotationHandle currentRotationHandle;
    private short viewMode;

    private CoordinateSystem oldCoords;
    private Mat4 viewToWorld;

    private static BToolTip moveToolTip, scaleToolTip, rotateToolTip, centerToolTip;
    private static Image ghostscale;
    private static Image centerhandle;
    private static Image xyzHandleImages[] = new Image[6];
    private static Image uvHandleImages[] = new Image[4];
    private static Image specificHandleImages[] = new Image[6];

    public final static short X_MOVE = 0;
    public final static short X_SCALE = 1;
    public final static short Y_MOVE = 2;
    public final static short Y_SCALE = 3;
    public final static short Z_MOVE = 4;
    public final static short Z_SCALE = 5;
    public final static short CENTER = 6;
    public final static short ROTATE = 7;
    public final static short TOOL_HANDLE = 8;
    public final static short UV_EXTRA = 9;

    public final static short XYZ_MODE = 0;
    public final static short UV_MODE = 1;
    public final static short SPECIFIC_MODE = 2;

    private static final double DRAG_SCALE = 0.01;

    public SSMR3DManipulator(AdvancedEditingTool tool, ViewerCanvas view, PolyMeshValueWidget valueWidget)
    {
        super(tool, view, valueWidget);
        MARGIN = HANDLE_SIZE;
        if (xyzHandleImages[0] == null)
        {
            xyzHandleImages[X_MOVE] = ThemeManager.getIcon( "polymesh:xhandle" ).getImage();
            xyzHandleImages[X_SCALE] = ThemeManager.getIcon( "polymesh:xscale" ).getImage();
            xyzHandleImages[Y_MOVE] = ThemeManager.getIcon( "polymesh:yhandle" ).getImage();
            xyzHandleImages[Y_SCALE] = ThemeManager.getIcon( "polymesh:yscale" ).getImage();
            xyzHandleImages[Z_MOVE] = ThemeManager.getIcon( "polymesh:zhandle" ).getImage();
            xyzHandleImages[Z_SCALE] = ThemeManager.getIcon( "polymesh:zscale" ).getImage();
            uvHandleImages[X_MOVE] = ThemeManager.getIcon( "polymesh:uhandle" ).getImage();
            uvHandleImages[X_SCALE] = ThemeManager.getIcon( "polymesh:uvscale" ).getImage();
            uvHandleImages[Y_MOVE] = ThemeManager.getIcon( "polymesh:vhandle" ).getImage();
            uvHandleImages[Y_SCALE] = ThemeManager.getIcon( "polymesh:uvscale" ).getImage();
            ghostscale = ThemeManager.getIcon( "polymesh:ghostscale" ).getImage();
            centerhandle = ThemeManager.getIcon( "polymesh:centerhandle" ).getImage();
            specificHandleImages[X_MOVE] = ThemeManager.getIcon( "polymesh:phandle" ).getImage();
            specificHandleImages[X_SCALE] = ThemeManager.getIcon( "polymesh:xscale" ).getImage();
            specificHandleImages[Y_MOVE] = ThemeManager.getIcon( "polymesh:qhandle" ).getImage();
            specificHandleImages[Y_SCALE] = ThemeManager.getIcon( "polymesh:yscale" ).getImage();
            specificHandleImages[Z_MOVE] = ThemeManager.getIcon( "polymesh:nhandle" ).getImage();
            specificHandleImages[Z_SCALE] = ThemeManager.getIcon( "polymesh:zscale" ).getImage();
            /*moveToolTip = PMToolTip.areaToolTip(Translate.text("polymesh:moveToolTip3d.tipText"),40);
            scaleToolTip = PMToolTip.areaToolTip(Translate.text("polymesh:scaleToolTip3d.tipText"),40);
            rotateToolTip = PMToolTip.areaToolTip(Translate.text("polymesh:rotateToolTip3d.tipText"),40);
            centerToolTip = PMToolTip.areaToolTip(Translate.text("polymesh:centerToolTip3d.tipText"),40);*/
            moveToolTip = new BToolTip(Translate.text("polymesh:moveToolTip3d.tipText"));
            scaleToolTip = new BToolTip(Translate.text("polymesh:scaleToolTip3d.tipText"));
            rotateToolTip = new BToolTip(Translate.text("polymesh:rotateToolTip3d.tipText"));
            centerToolTip = new BToolTip(Translate.text("polymesh:centerToolTip3d.tipText"));
        }
        xaxis = Vec3.vx();
        yaxis = Vec3.vy();
        zaxis = Vec3.vz();
        xyzRotHandles = new RotationHandle[3];
        boxes = new Rectangle[7];
        extraUVBox = new Rectangle();
        for (int i = 0; i < boxes.length; ++i)
            boxes[i] = new Rectangle(0,0,HANDLE_SIZE, HANDLE_SIZE);
        extraUVBox = new Rectangle(0,0,HANDLE_SIZE, HANDLE_SIZE);
        xyzRotHandles[0] = new RotationHandle(64, XAXIS, Color.blue );
        xyzRotHandles[1] = new RotationHandle(64, YAXIS, Color.green );
        xyzRotHandles[2] = new RotationHandle(64, ZAXIS, Color.red);
        uvRotationHandle = new RotationHandle[1];
        uvRotationHandle[0] = new RotationHandle(64, XAXIS, Color.orange);
        specificRotHandles = new RotationHandle[3];
        specificRotHandles[0] = new RotationHandle(64, XAXIS, Color.blue );
        specificRotHandles[1] = new RotationHandle(64, YAXIS, Color.green );
        specificRotHandles[2] = new RotationHandle(64, ZAXIS, Color.red);
        valueWidgetCallback =
            new Runnable()
            {

                public void run()
                {
                    doValueWidgetCallback();
                }
            };
        validateWidgetValue =
            new Runnable()
            {

                public void run()
                {
                    doValueWidgetValidate();
                }
            };
        abortWidgetValue =
            new Runnable()
            {

                public void run()
                {
                    doValueWidgetAbort();
                }
            };
        axisLength = 80;
        view.addEventLink(ToolTipEvent.class, this, "doTooltip");
        if (view.isPerspective())
            active = true;
        else
            active = false;
    }

    public void setPerspective(boolean perspective)
    {
        //this manipulator is active in perspective views
        if (perspective)
            active = true;
    }

    public void draw()
    {
        if (! active)
            return;
        MeshEditController controller = ((MeshViewer) view).getController();
        Mesh mesh = (Mesh) controller.getObject().object;
        MeshVertex v[] = mesh.getVertices();
        Camera cam = view.getCamera();
        AdvancedEditingTool.SelectionProperties props =  tool.findSelectionProperties(view.getCamera());
        bounds = findScreenBounds(props.bounds, cam, (MeshViewer) view, controller);
        setBounds(bounds);
        if (bounds == null)
        {
            //not a valid selection, do not draw onto screen
            selCenter = 0;
            return;
        }

        // Calculate the screen positions of the axis ends.
        double len = axisLength / view.getScale();
        if (props.featurePoints.length != selNum)
        {
            selCenter = 0;
            selNum = props.featurePoints.length;
        }
        //when in NPQ mode, the manipulator must not change position during dragging
        boolean freezeManipulator = ( dragging && (handle == ROTATE | handle == X_SCALE || handle == Y_SCALE || handle == Z_SCALE ) );
        if (!freezeManipulator)
            center = new Vec3( props.featurePoints[selCenter]);
        //if in UV or NPQ mode, recompute axis system
        if (viewMode == UV_MODE)
        {
            CoordinateSystem coords =  view.getCamera().getCameraCoordinates();
            zaxis = new Vec3(coords.getZDirection());
            zaxis.scale(-1);
            yaxis = coords.getUpDirection();
            xaxis = yaxis.cross(zaxis);
        }
        else if (viewMode == SPECIFIC_MODE && !dragging)
        {
            CoordinateSystem coords = props.specificCoordinateSystem;
            if (coords == null)
            {
                viewMode = XYZ_MODE;
                xaxis = Vec3.vx();
                yaxis = Vec3.vy();
                zaxis = Vec3.vz();
            }
            else
            {
                zaxis = coords.getZDirection();
                yaxis = coords.getUpDirection();
                xaxis = yaxis.cross(zaxis);
            }
            specificRotHandles[0].setAxis(xaxis, yaxis);
            specificRotHandles[1].setAxis(yaxis, zaxis);
            specificRotHandles[2].setAxis(zaxis, xaxis);
        }
        //now compute axis extremities and widget positions
        double handleSize = HANDLE_SIZE / view.getScale();
        Vec3 xpos = center.plus(xaxis.times(len));
        Vec3 ypos = center.plus(yaxis.times(len));
        Vec3 zpos = center.plus(zaxis.times(len));
        Vec3 xHandlePos = center.plus(xaxis.times(len + handleSize/2.0));
        Vec3 yHandlePos = center.plus(yaxis.times(len + handleSize/2.0));
        Vec3 zHandlePos = center.plus(zaxis.times(len + handleSize/2.0));
        Vec3 xHandleOffset = center.plus(xaxis.times(len + handleSize*1.5) );
        Vec3 yHandleOffset = center.plus(yaxis.times(len + handleSize*1.5) );
        Vec3 zHandleOffset = center.plus(zaxis.times(len + handleSize*1.5) );
        Vec2 x2DHandleOffset = cam.getObjectToScreen().timesXY(xHandleOffset);
        Vec2 y2DHandleOffset = cam.getObjectToScreen().timesXY(yHandleOffset);
        Vec2 z2DHandleOffset = cam.getObjectToScreen().timesXY(zHandleOffset);
        Vec2 axisCenter = cam.getObjectToScreen().timesXY(center);
        Vec2 screenX = cam.getObjectToScreen().timesXY(xpos);
        Vec2 screenY = cam.getObjectToScreen().timesXY(ypos);
        Vec2 screenZ = cam.getObjectToScreen().timesXY(zpos);
        Vec2 screenXHandle = cam.getObjectToScreen().timesXY(xHandlePos);
        Vec2 screenYHandle = cam.getObjectToScreen().timesXY(yHandlePos);
        Vec2 screenZHandle = cam.getObjectToScreen().timesXY(zHandlePos);
        x2DHandleOffset.subtract(screenX);
        y2DHandleOffset.subtract(screenY);
        z2DHandleOffset.subtract(screenZ);
        x2Daxis = screenX.minus(axisCenter);
        y2Daxis = screenY.minus(axisCenter);
        z2Daxis = screenZ.minus(axisCenter);
        x2DaxisNormed = new Vec2( x2Daxis );
        y2DaxisNormed = new Vec2( y2Daxis );
        z2DaxisNormed = new Vec2( z2Daxis );
        x2DaxisNormed.normalize();
        y2DaxisNormed.normalize();
        z2DaxisNormed.normalize();
        if (!freezeManipulator)
            centerPoint = new Point((int) Math.round(axisCenter.x), (int) Math.round(axisCenter.y));

        //draw rotation feedback if appropriate
        if (dragging && handle == ROTATE)
        {
            Vec3[] pt = currentRotationHandle.getRotationFeedback(rotAngle);
            Vec2 pt2d;
            int[] x = new int[pt.length];
            int[] y = new int[pt.length];
            for (int j = 0; j < pt.length; j++)
            {
                pt2d = cam.getObjectToScreen().timesXY(center.plus(pt[j].times(len)));
                x[j] = (int) pt2d.x;
                y[j] = (int) pt2d.y;
            }
            Polygon p = new Polygon(x, y, x.length);
            view.drawShape(p, Color.darkGray);
            view.fillShape( p, Color.gray);
        }
        //draw scale feedback if appropriate
        else if (dragging && (handle == X_SCALE || handle == Y_SCALE || handle == Z_SCALE ) )
        {
            Vec3 pos = null;
            Vec3 handlePos = null;
            Vec2 screenPos, screenHandle;
            double sign = 1.0;
            if (scale < 0)
                sign = -1;
            switch(handle)
            {
                case X_SCALE:
                    pos = center.plus(xaxis.times(len*scale));
                    handlePos = center.plus(xaxis.times(len*scale + handleSize*sign*2.0));
                    break;
                case Y_SCALE:
                    pos = center.plus(yaxis.times(len*scale));
                    handlePos = center.plus(yaxis.times(len*scale + handleSize*sign*2.0));
                    break;
                case Z_SCALE:
                    pos = center.plus(zaxis.times(len*scale));
                    handlePos = center.plus(zaxis.times(len*scale + handleSize*sign*2.0));
                    break;
                default:
                    break;
            }
            screenPos = cam.getObjectToScreen().timesXY(pos);
            screenHandle = cam.getObjectToScreen().timesXY(handlePos);
            view.drawLine(centerPoint, new Point((int) screenPos.x, (int) screenPos.y), Color.black);
            view.drawImage(ghostscale, (int)(screenHandle.x - HANDLE_SIZE/2), (int)(screenHandle.y - HANDLE_SIZE/2));
        }
        else if (dragging && handle == UV_EXTRA)
        {
            Vec3 pos = null;
            Vec3 handlePos = null;
            Vec2 screenPos, screenHandle;
            double signX = 1.0;
            double signY = 1.0;
            if (extraScaleX < 0)
                signX = -1;
            if (extraScaleY < 0)
                signY = -1;
            pos = center.plus(xaxis.times(len*extraScaleX).plus(yaxis.times(len*extraScaleY)));
            double handlex = handleSize*signX*2.0;
            if (extraScaleX < 1)
                handlex *= extraScaleX;
            double handley = handleSize*signY*2.0;
            if (extraScaleY < 1)
                handley *= extraScaleY;
            handlePos = center.plus(xaxis.times(len*extraScaleX + handlex)).plus(yaxis.times(len*extraScaleY + handley));
            screenPos = cam.getObjectToScreen().timesXY(pos);
            screenHandle = cam.getObjectToScreen().timesXY(handlePos);
            view.drawLine(centerPoint, new Point((int) screenPos.x, (int) screenPos.y), Color.black);
            view.drawImage(ghostscale, (int)(screenHandle.x - HANDLE_SIZE/2), (int)(screenHandle.y - HANDLE_SIZE/2));
        }
        //center drag
        else if (dragging && handle == CENTER)
        {
            view.drawImage(centerhandle, centerLocation.x - HANDLE_SIZE/2, centerLocation.y - HANDLE_SIZE/2);

        }

        // Draw the axes.
        Color xColor, yColor, zColor;
        Image[] handles = null;
        switch (viewMode)
        {
            default:
            case XYZ_MODE :
                xColor = Color.blue;
                yColor = Color.green;
                zColor = Color.red;
                handles = xyzHandleImages;
                break;
            case UV_MODE :
                xColor = Color.orange;
                yColor = Color.orange;
                zColor = Color.red;
                handles = uvHandleImages;
                break;
            case SPECIFIC_MODE :
                xColor = Color.blue;
                yColor = Color.green;
                zColor = Color.red;
                handles = specificHandleImages;
                break;
        }
        view.drawLine(centerPoint, new Point((int) screenX.x, (int) screenX.y), xColor);
        view.drawLine(centerPoint, new Point((int) screenY.x, (int) screenY.y), yColor);
        view.drawLine(centerPoint, new Point((int) screenZ.x, (int) screenZ.y), zColor);

        // Draw the handles.
        boxes[CENTER].x = (int)(centerPoint.x - HANDLE_SIZE/2);
        boxes[CENTER].y = (int)(centerPoint.y - HANDLE_SIZE/2);
        view.drawImage(centerhandle, boxes[CENTER].x, boxes[CENTER].y);
        for (int i = 0; i < 2; i++)
        {
            boxes[X_MOVE+i].x = (int)( screenXHandle.x - HANDLE_SIZE/2  + i * x2DHandleOffset.x);
            boxes[X_MOVE+i].y = (int)( screenXHandle.y - HANDLE_SIZE/2  + i * x2DHandleOffset.y);
            view.drawImage(handles[X_MOVE+i], boxes[X_MOVE+i].x, boxes[X_MOVE+i].y);

        }
        for (int i = 0; i < 2; i++)
        {
            boxes[Y_MOVE+i].x = (int)( screenYHandle.x - HANDLE_SIZE/2  + i * y2DHandleOffset.x);
            boxes[Y_MOVE+i].y = (int)( screenYHandle.y - HANDLE_SIZE/2  + i * y2DHandleOffset.y);
            view.drawImage(handles[Y_MOVE+i], boxes[Y_MOVE+i].x, boxes[Y_MOVE+i].y);
        }
        if (viewMode != UV_MODE)
            for (int i = 0; i < 2; i++)
        {
            boxes[Z_MOVE+i].x = (int)( screenZHandle.x - HANDLE_SIZE/2  + i * z2DHandleOffset.x);
            boxes[Z_MOVE+i].y = (int)( screenZHandle.y - HANDLE_SIZE/2  + i * z2DHandleOffset.y);
            view.drawImage(handles[Z_MOVE+i], boxes[Z_MOVE+i].x, boxes[Z_MOVE+i].y);
        }
        else
        {
            int udeltax =  boxes[X_SCALE].x + HANDLE_SIZE/2 - centerPoint.x;
            int udeltay =  boxes[X_SCALE].y + HANDLE_SIZE/2 - centerPoint.y;
            int vdeltax =  boxes[Y_SCALE].x + HANDLE_SIZE/2 - centerPoint.x;
            int vdeltay =  boxes[Y_SCALE].y + HANDLE_SIZE/2 - centerPoint.y;
            extraUVBox.x = udeltax + vdeltax + centerPoint.x - HANDLE_SIZE/2;
            extraUVBox.y = udeltay + vdeltay + centerPoint.y - HANDLE_SIZE/2;
            Vec3 handlePos = center.plus(xaxis.times(len + handleSize*2.0)).plus(yaxis.times(len + handleSize*2.0));
            Vec2 screenHandle = cam.getObjectToScreen().timesXY(handlePos);
            extraUVBox.x = (int) screenHandle.x - HANDLE_SIZE/2;
            extraUVBox.y = (int) screenHandle.y - HANDLE_SIZE/2;
            view.drawImage(handles[X_SCALE], extraUVBox.x, extraUVBox.y);
            /*extraUVBoxes[UV_TOPRIGHT_BOX].x = udeltax + vdeltax + centerPoint.x - HANDLE_SIZE/2;
            extraUVBoxes[UV_TOPRIGHT_BOX].y = udeltay + vdeltay + centerPoint.y - HANDLE_SIZE/2;
            view.drawImage(handles[X_SCALE], boxes[UV_TOPRIGHT_BOX].x, boxes[UV_TOPRIGHT_BOX].y);
            extraUVBoxes[UV_BOTTOMRIGHT_BOX].x = udeltax - vdeltax + centerPoint.x - HANDLE_SIZE/2;
            extraUVBoxes[UV_BOTTOMRIGHT_BOX].y = udeltay - vdeltay + centerPoint.y - HANDLE_SIZE/2;
            view.drawImage(handles[X_SCALE], boxes[UV_BOTTOMRIGHT_BOX].x, boxes[UV_BOTTOMRIGHT_BOX].y);
            extraUVBoxes[UV_BOTTOMLEFT_BOX].x = -udeltax - vdeltax + centerPoint.x - HANDLE_SIZE/2;
            extraUVBoxes[UV_BOTTOMLEFT_BOX].y = -udeltay - vdeltay + centerPoint.y - HANDLE_SIZE/2;
            view.drawImage(handles[X_SCALE], boxes[UV_BOTTOMLEFT_BOX].x, boxes[UV_BOTTOMLEFT_BOX].y);*/
        }

        //draw the rotation handles
        RotationHandle[] rotHandles = null;
        switch (viewMode)
        {
            case XYZ_MODE:
                rotHandles = xyzRotHandles;
                break;
            case UV_MODE:
                rotHandles = uvRotationHandle;
                rotHandles[0].setAxis(zaxis, xaxis);
                break;
            case SPECIFIC_MODE:
                rotHandles = specificRotHandles;
                break;
        }
        RotationHandle rotHandle;
        for (int i = 0; i < rotHandles.length; ++i)
        {
            rotHandle = rotHandles[i];
            for (int j = 0; j < rotHandle.points3d.length; j++)
                rotHandle.points2d[j] = cam.getObjectToScreen().timesXY(center.plus(rotHandle.points3d[j].times(len)));
            for (int j = 0; j < rotHandle.points3d.length-1; j++)
                view.drawLine(new Point((int) rotHandle.points2d[j].x, (int) rotHandle.points2d[j].y),
                        new Point((int) rotHandle.points2d[j+1].x, (int) rotHandle.points2d[j+1].y), rotHandle.color);
        }
    }

    public boolean mousePressedOnHandle(WidgetMouseEvent e, int handle, Vec3 pos)
    {
        if (! active)
            return false;
        toolHandePos = pos;
        this.handle = TOOL_HANDLE;
        isShiftDown = e.isShiftDown();
        baseClick = new Point(e.getPoint());
        dragging = true;
        dispatchEvent(new ManipulatorPrepareChangingEvent(this, view) );
        return false;
    }

    public boolean mousePressed(WidgetMouseEvent e)
    {
        if (! active)
            return false;
        //3D manipulators don't draw the bounds, but bounds is used to detect
        //a valid selection
        if (bounds == null)
            return false;
        button = e.getButton();
        //ignore MMB events
        if (e.getButton() == MouseEvent.BUTTON2 ) // && ( e.getModifiers() & ActionEvent.CTRL_MASK) == 0)
        {
            Camera cam = view.getCamera();
            baseClick = e.getPoint();
            oldCoords = cam.getCameraCoordinates().duplicate();
            viewToWorld = cam.getViewToWorld();
            dragging = true;
            return true;
        }
        Camera camera = view.getCamera();
        Point p = e.getPoint();
        for (int i = 6; i >= 0; i--)
        {
            if (viewMode == UV_MODE && ( i == Z_MOVE || i == Z_SCALE) )
                continue;
            if (boxes[i].contains(p))
            {
                /*
                if (e.getButton() == MouseEvent.BUTTON3)
                {
                    ((PolyMeshEditorWindow)((PolyMeshViewer)view).getController()).triggerPopupEvent(e);
                    return true;
                }
                else
                {*/
                    if (i == CENTER)
                    {
                        if ( ( e.getModifiers() & ActionEvent.CTRL_MASK) != 0 )
                        {
                            //center drag on feature points
                            AdvancedEditingTool.SelectionProperties props =  tool.findSelectionProperties(view.getCamera());
                            featurePoints2d = new Vec2[props.featurePoints.length];
                            for (int j = 0; j < featurePoints2d.length; j++)
                                featurePoints2d[j] = camera.getObjectToScreen().timesXY(props.featurePoints[j]);
                            centerLocation = baseClick;
                            handle = i;
                        }
                        else
                        {
                            //selection drag
                            handle = TOOL_HANDLE;
                            toolHandePos = center;
                        }
                    }
                    else
                        handle = i;
                    orAxisLength = axisLength;
                    dragging = true;
                    baseClick = new Point(e.getPoint());
                    dispatchEvent(new ManipulatorPrepareChangingEvent(this, view) );
                    return true;
                //}
            }
        }
        //select proper rotation handles
        RotationHandle[] rotHandles = null;
        switch (viewMode)
        {
            case XYZ_MODE :
                rotHandles = xyzRotHandles;
                break;
            case UV_MODE :
                rotHandles = uvRotationHandle;
                break;
            case SPECIFIC_MODE :
                rotHandles = specificRotHandles;
                break;
        }
        //and detect if click happened in one of them
        for (int i = 0; i < rotHandles.length; i++)
        {
            if ( (rotSegment = rotHandles[i].findClickTarget(p, view.getCamera())) != -1)
            {
                currentRotationHandle = rotHandles[i];
                handle = ROTATE;
                dragging = true;
                baseClick = new Point(e.getPoint());
                dispatchEvent(new ManipulatorPrepareChangingEvent(this, view) );
                rotAngle = 0;
                return true;
            }
        }
        //check for extra UV handle
        if (viewMode == UV_MODE && extraUVBox.contains(p))
        {
            handle = UV_EXTRA;
            orAxisLength = axisLength;
            dragging = true;
            baseClick = new Point(e.getPoint());
            dispatchEvent(new ManipulatorPrepareChangingEvent(this, view) );
            return true;
        }
        return false;
    }

    public boolean doTooltip(ToolTipEvent e)
    {
        if (!active || !helpModeOn)
            return false;
        //3D manipulators don't draw the bounds, but bounds is used to detect
        //a valid selection
        if (bounds == null)
            return false;
        Camera camera = view.getCamera();
        Point p = e.getPoint();
        for (int i = 6; i >= 0; i--)
        {
            if (viewMode == UV_MODE && ( i == Z_MOVE || i == Z_SCALE) )
                continue;
            if (boxes[i].contains(p))
            {

                        switch(i)
                        {
                            case X_MOVE:
                            case Y_MOVE:
                            case Z_MOVE:
                                moveToolTip.processEvent(e);
                                break;
                            case X_SCALE:
                            case Y_SCALE:
                            case Z_SCALE:
                                scaleToolTip.processEvent(e);
                                break;
                            case CENTER:
                                centerToolTip.processEvent(e);
                                break;
                            default :
                        }

            }
        }
        //select proper rotation handles
        RotationHandle[] rotHandles = null;
        switch (viewMode)
        {
            case XYZ_MODE :
                rotHandles = xyzRotHandles;
                break;
            case UV_MODE :
                rotHandles = uvRotationHandle;
                break;
            case SPECIFIC_MODE :
                rotHandles = specificRotHandles;
                break;
        }
        //and detect if movement happened in one of them
        for (int i = 0; i < rotHandles.length; i++)
        {
            if ( (rotSegment = rotHandles[i].findClickTarget(p, view.getCamera())) != -1)
            {
                rotateToolTip.processEvent(e);
                return true;
            }
        }
        return false;
    }

    public void doValueWidgetCallback()
    {
	double value = valueWidget.getValue();
        if ( handle == X_MOVE || handle == Y_MOVE || handle == Z_MOVE)
            moveDragged(value);
        else if ( handle == X_SCALE || handle == Y_SCALE || handle == Z_SCALE)
            scaleDragged(value);
        else if (handle == ROTATE)
            rotateDragged(value);
    }

    public void moveDragged(double value)
    {
        Vec3 drag = null;
        switch (handle)
        {
            case X_MOVE:
                drag = xaxis.times(value);
                break;
            case Y_MOVE:
                drag = yaxis.times(value);
                break;
            case Z_MOVE:
                drag = zaxis.times(value);
                break;
            default:
                break;

        }
        dispatchEvent(new ManipulatorMovingEvent(this, drag, view) );
    }

    public void scaleDragged(double value)
    {
        double scaleX, scaleY, scaleZ;
        scale = value;
        scaleX = scaleY = scaleZ = 1;
        switch(handle)
        {
            case X_SCALE :
                scaleX = scale;
                if (isShiftDown && !(viewMode == UV_MODE) )
                    scaleY = scaleZ = scaleX;
                else if (isShiftDown)
                    scaleY = scaleX;
                break;
            case Y_SCALE :
                scaleY = scale;
                if (isShiftDown && !(viewMode == UV_MODE) )
                    scaleX = scaleZ = scaleY;
                else if (isShiftDown)
                    scaleX = scaleY;
                break;
            case Z_SCALE :
                scaleZ = scale;
                if (isShiftDown)
                    scaleY = scaleX = scaleZ;
                break;
            default:
                break;
        }
        CoordinateSystem coords = new CoordinateSystem(center, zaxis, yaxis);
        Mat4 m = Mat4.scale(scaleX, scaleY, scaleZ).times(coords.toLocal());
        m = coords.fromLocal().times(m);
        dispatchEvent(new ManipulatorScalingEvent(this, m, view) );
    }

    public void rotateDragged(double value)
    {

        Mat4 m = null;
        rotAngle = value*Math.PI/180.0;
        m = Mat4.axisRotation(currentRotationHandle.rotAxis, rotAngle);
        AdvancedEditingTool.SelectionProperties props =  tool.findSelectionProperties(view.getCamera());
        rotateCenter = props.featurePoints[selCenter];
        Mat4 mat = Mat4.translation(-rotateCenter.x, -rotateCenter.y, -rotateCenter.z);
        mat = m.times(mat);
        mat = Mat4.translation(rotateCenter.x, rotateCenter.y, rotateCenter.z).times(mat);
        dispatchEvent(new ManipulatorRotatingEvent(this, mat, view) );
    }


    public boolean mouseDragged(WidgetMouseEvent e)
    {
        if (! active)
            return false;
        if (!dragging)
            return false;
        if (button == MouseEvent.BUTTON2)
            viewDragged(e);
        else
            switch (handle)
            {
                case X_MOVE:
                case Y_MOVE:
                case Z_MOVE:
                    moveDragged(e);
                    break;
                case X_SCALE:
                case Y_SCALE:
                case Z_SCALE:
                case UV_EXTRA:
                    scaleDragged(e);
                    break;
                case ROTATE:
                    rotateDragged(e);
                    break;
                case CENTER:
                    centerDragged(e);
                    break;
                case TOOL_HANDLE:
                    toolHandleDragged(e);
                default:
                    break;

            }
        return true;
    }


    private void viewDragged(WidgetMouseEvent e)
    {
        Camera cam = view.getCamera();
        Point dragPoint = e.getPoint();
        CoordinateSystem c = oldCoords.duplicate();
        int dx, dy;
        double angle;
        Vec3 axis;

        dx = dragPoint.x-baseClick.x;
        dy = dragPoint.y-baseClick.y;
        if (e.isControlDown())
        {
            axis = viewToWorld.timesDirection(Vec3.vz());
            angle = dx * DRAG_SCALE;
        }
        else if (e.isShiftDown())
        {
            if (Math.abs(dx) > Math.abs(dy))
            {
                axis = viewToWorld.timesDirection(Vec3.vy());
                angle = dx * DRAG_SCALE;
            }
            else
            {
                axis = viewToWorld.timesDirection(Vec3.vx());
                angle = -dy * DRAG_SCALE;
            }
        }
        else
        {
            axis = new Vec3(-dy*DRAG_SCALE, dx*DRAG_SCALE, 0.0);
            angle = axis.length();
            axis = axis.times(1.0/angle);
            axis = viewToWorld.timesDirection(axis);
        }
        if (angle != 0.0)
        {
            c.transformCoordinates(Mat4.translation(-center.x, -center.y, -center.z));
            c.transformCoordinates(Mat4.axisRotation(axis, -angle));
            c.transformCoordinates(Mat4.translation(center.x, center.y, center.z));
            cam.setCameraCoordinates(c);
            view.repaint();
        }
    }

    public void toolHandleDragged(WidgetMouseEvent e)
    {
        Point p = e.getPoint();
        int dragX = p.x - baseClick.x;
        int dragY = p.y - baseClick.y;
        Vec3 drag = view.getCamera().findDragVector(toolHandePos, dragX, dragY);
        if (e.isShiftDown())
        {
            double gridSize = view.getSnapToSubdivisions();
            drag.x *= gridSize;
            drag.x = Math.round(drag.x);
            drag.x /= gridSize;
            drag.y *= gridSize;
            drag.y = Math.round(drag.y);
            drag.y /= gridSize;
            drag.z *= gridSize;
            drag.z = Math.round(drag.z);
            drag.z /= gridSize;
        }
        ((MeshEditorWindow)((MeshViewer)view).getController()).setHelpText(Translate.text("polymesh:moveCenterBy", new String[] { String.valueOf(Math.round(drag.x*1e5)/1e5), String.valueOf(Math.round(drag.y*1e5)/1e5), String.valueOf(Math.round(drag.z*1e5)/1e5) } ));
        dispatchEvent(new ManipulatorMovingEvent(this, drag, view) );
    }

    public void centerDragged(WidgetMouseEvent e)
    {
        centerLocation = e.getPoint();
        for (int i = 0; i < featurePoints2d.length; i++)
        {
            if (centerLocation.x > featurePoints2d[i].x - HANDLE_SIZE/2 && centerLocation.x < featurePoints2d[i].x + HANDLE_SIZE/2
            && centerLocation.y > featurePoints2d[i].y - HANDLE_SIZE/2 && centerLocation.y < featurePoints2d[i].y + HANDLE_SIZE/2)
            {
                selCenter = i;
                centerLocation.x = (int) featurePoints2d[i].x;
                centerLocation.y = (int) featurePoints2d[i].y;
            }
        }
        view.repaint();
    }

    public void moveDragged(WidgetMouseEvent e)
    {
        boolean isShiftDown = e.isShiftDown();
        boolean isCtrlDown = ( e.getModifiers() & ActionEvent.CTRL_MASK ) != 0;
        double gridSize = view.getGridSpacing()/view.getSnapToSubdivisions();
        Vec2 disp = new Vec2(e.getPoint().x - baseClick.x, e.getPoint().y - baseClick.y );
        Vec3 drag = null;
        double amplitude = 0;
        switch (handle)
        {
            case X_MOVE:
                amplitude = disp.dot(x2DaxisNormed);
                amplitude /= view.getScale();
                if (isShiftDown)
                {
                    amplitude /= gridSize;
                    amplitude = Math.round(amplitude);
                    amplitude *= gridSize;
                }
                drag = xaxis.times(amplitude);
                break;
            case Y_MOVE:
                amplitude = disp.dot(y2DaxisNormed);
                amplitude /= view.getScale();
                if (isShiftDown)
                {
                    amplitude /= gridSize;
                    amplitude = Math.round(amplitude);
                    amplitude *= gridSize;
                }
                drag = yaxis.times(amplitude);
                break;
            case Z_MOVE:
                amplitude = disp.dot(z2DaxisNormed);
                amplitude /= view.getScale();
                if (isShiftDown)
                {
                    amplitude /= gridSize;
                    amplitude = Math.round(amplitude);
                    amplitude *= gridSize;
                }
                drag = zaxis.times(amplitude);
                break;
            default:
                break;

        }
        ((MeshEditorWindow)((MeshViewer)view).getController()).setHelpText(Translate.text("polymesh:moveBy", new String[] { String.valueOf(Math.round(amplitude*1e5)/1e5) } ));
        dispatchEvent(new ManipulatorMovingEvent(this, drag, view) );
    }

    public boolean scaleDragged(WidgetMouseEvent e)
    {
        Point p = e.getPoint();
        boolean isShiftDown = e.isShiftDown();
        boolean isCtrlDown = ( e.getModifiers() & ActionEvent.CTRL_MASK ) != 0;
        double scaleX, scaleY, scaleZ;

        Vec2 base = new Vec2(baseClick.x - centerPoint.x, baseClick.y - centerPoint.y);
        Vec2 current = new Vec2(p.x - centerPoint.x, p.y - centerPoint.y);
        scale = base.dot(current);
        if (base.length() < 1)
            scale = 1;
        else
            scale /= (base.length()*base.length());
        if (isCtrlDown)
        {
            axisLength = orAxisLength*scale;
            scale = 1;
            view.repaint();
        }
        else
        {
            scaleX = scaleY = scaleZ = 1;
            switch(handle)
            {
                case X_SCALE :
                    scaleX = scale;
                    if (isShiftDown && !(viewMode == UV_MODE) )
                        scaleY = scaleZ = scaleX;
                    else if (isShiftDown)
                        scaleY = scaleX;
                    break;
                case Y_SCALE :
                    scaleY = scale;
                    if (isShiftDown && !(viewMode == UV_MODE) )
                        scaleX = scaleZ = scaleY;
                    else if (isShiftDown)
                        scaleX = scaleY;
                    break;
                case Z_SCALE :
                    scaleZ = scale;
                    if (isShiftDown)
                        scaleY = scaleX = scaleZ;
                    break;
                case UV_EXTRA:
                    scaleX = x2DaxisNormed.dot(current)/x2DaxisNormed.dot(base);
                    scaleY = y2DaxisNormed.dot(current)/y2DaxisNormed.dot(base);
                    if (isShiftDown)
                    {
                        if (scaleX < 1 && scaleY < 1)
                          scaleX = scaleY = Math.min(scaleX, scaleY);
                        else
                          scaleX = scaleY = Math.max(scaleX, scaleY);
                    }
                    extraScaleX = scaleX;
                    extraScaleY = scaleY;
                    break;
                default:
                    break;
            }
            CoordinateSystem coords = new CoordinateSystem(center, zaxis, yaxis);
            Mat4 m = Mat4.scale(scaleX, scaleY, scaleZ).times(coords.toLocal());
            m = coords.fromLocal().times(m);
            if (handle != UV_EXTRA)
                ((MeshEditorWindow)((MeshViewer)view).getController()).setHelpText(Translate.text("polymesh:scaleBy", new String[] { String.valueOf(Math.round(scale*1e5)/1e5) } ));
            else
                ((MeshEditorWindow)((MeshViewer)view).getController()).setHelpText(Translate.text("polymesh:scaleUVBy", new String[] { String.valueOf(Math.round(scaleX*1e5)/1e5), String.valueOf(Math.round(scaleY*1e5)/1e5) } ));

            dispatchEvent(new ManipulatorScalingEvent(this, m, view) );
        }
        return true;
    }

    public boolean rotateDragged(WidgetMouseEvent e)
    {
        Point p = e.getPoint();
        boolean isShiftDown = e.isShiftDown();
        boolean isCtrlDown = ( e.getModifiers() & ActionEvent.CTRL_MASK ) != 0;

        Vec2 disp = new Vec2(e.getPoint().x - baseClick.x, e.getPoint().y - baseClick.y );
        Vec2 vector = currentRotationHandle.points2d[rotSegment+1].minus(currentRotationHandle.points2d[rotSegment]);
        vector.normalize();
        Mat4 m = null;
        rotAngle = vector.dot(disp)/70;
        if (isShiftDown)
        {
            rotAngle *= (180.0/(5*Math.PI));
            rotAngle = Math.round(rotAngle);
            rotAngle *= (5*Math.PI)/180;
        }
        m = Mat4.axisRotation(currentRotationHandle.rotAxis, rotAngle);
        Mat4 mat = Mat4.translation(-center.x, -center.y, -center.z);
        mat = m.times(mat);
        mat = Mat4.translation(center.x, center.y, center.z).times(mat);
        //System.out.println("center: " + rotateCenter);
        //System.out.println("angle: " + ( rotAngle * 180 / Math.PI) );
        dispatchEvent(new ManipulatorRotatingEvent(this, mat, view) );
        ((MeshEditorWindow)((MeshViewer)view).getController()).setHelpText(Translate.text("polymesh:rotateBy", new String[] { String.valueOf(Math.round(rotAngle*180*1e5/Math.PI)/1e5) } ));
        return true;
    }

    public boolean mouseReleased(WidgetMouseEvent e)
    {
        if (! active)
            return false;
        if (!dragging)
            return false;
        if (baseClick.x == e.getPoint().x && baseClick.y == e.getPoint().y && handle != TOOL_HANDLE)
        {
            Camera camera = view.getCamera();
            Point p = e.getPoint();
            //find scale/move handle in which click occured.
            //the handle choice is slightly different from mousePressed
            //center handle is ignored
            for (int i = 0; i < 6; i++)
            {
                if (viewMode == UV_MODE && ( i == Z_MOVE || i == Z_SCALE) )
                    continue;
                if (boxes[i].contains(p))
                    handle = i;
            }
            RotationHandle[] rotHandles = null;
            switch (viewMode)
            {
                case XYZ_MODE :
                    rotHandles = xyzRotHandles;
                    break;
                case UV_MODE :
                    rotHandles = uvRotationHandle;
                    break;
                case SPECIFIC_MODE :
                    rotHandles = specificRotHandles;
                    break;
            }
            for (int i = 0; i < rotHandles.length; i++)
            {
                if ( (rotSegment = rotHandles[i].findClickTarget(p, view.getCamera())) != -1)
                {
                    currentRotationHandle = rotHandles[i];
                    handle = ROTATE;
                }
            }
            if (e.getButton() == MouseEvent.BUTTON2 && handle != CENTER && e.isControlDown())
            {
                if (valueWidget != null)
                {
                    dispatchEvent(new ManipulatorPrepareChangingEvent(this, view) );
                    isCtrlDown = ( e.getModifiers() & ActionEvent.CTRL_MASK ) != 0;
                    isShiftDown = e.isShiftDown();
                    if (handle == ROTATE)
                    {
                        valueWidget.setTempValueRange(-180, 180);
                        valueWidget.activate( valueWidgetCallback);
                    }
                    else if (handle == X_MOVE || handle == Y_MOVE || handle == Z_MOVE)
                    {
                        valueWidget.setTempValueRange(-valueWidget.getValueMax(), valueWidget.getValueMax());
                        valueWidget.activate( 0.0, valueWidgetCallback);
                    }
                    else
                    {
                        valueWidget.setTempValueRange(-valueWidget.getValueMax(), valueWidget.getValueMax());
                        valueWidget.activate( 1.0, valueWidgetCallback);
                    }
                    return true;
                }
            }
            else if (e.getButton() == MouseEvent.BUTTON1 && ( handle == X_MOVE || handle == Y_MOVE || handle == Z_MOVE ||
                    handle == X_SCALE || handle == Y_SCALE || handle == Z_SCALE) )
            {
                if ( ( e.getModifiers() & ActionEvent.CTRL_MASK ) != 0 )
                {
                    if (e.isShiftDown())
                    {
                        if (viewMode == XYZ_MODE)
                        {
                            switch(handle)
                            {
                                case X_MOVE:
                                case X_SCALE:
                                    view.setOrientation(ViewerCanvas.VIEW_LEFT);
                                    break;
                                case Y_MOVE:
                                case Y_SCALE:
                                    view.setOrientation(ViewerCanvas.VIEW_BOTTOM);
                                    break;
                                case Z_MOVE:
                                case Z_SCALE:
                                    view.setOrientation(ViewerCanvas.VIEW_BACK);
                                    break;
                            }
                            CoordinateSystem coords = camera.getCameraCoordinates();
                            coords.setOrigin(center);
                            camera.setCameraCoordinates(coords);
                            view.repaint();
                        }
                        else
                        {
                            Vec3 orig = new Vec3(center);
                            Vec3 zdir = null;
                            Vec3 updir = null;
                            double distance = (view.isPerspective() ? camera.getCameraCoordinates().getOrigin().length() : camera.getDistToScreen());
                            switch(handle)
                            {
                                case X_MOVE:
                                case X_SCALE:
                                    orig.add(xaxis.times(distance) );
                                    zdir = new Vec3(xaxis);
                                    updir = new Vec3( zaxis );
                                    break;
                                case Y_MOVE:
                                case Y_SCALE:
                                    orig.add(yaxis.times(distance) );
                                    zdir = new Vec3(yaxis);
                                    updir = new Vec3( xaxis );
                                    break;
                                case Z_MOVE:
                                case Z_SCALE:
                                    orig.add(zaxis.times(distance) );
                                    zdir = new Vec3(zaxis);
                                    updir = new Vec3( yaxis );
                                    break;
                            }
                            camera.setCameraCoordinates( new CoordinateSystem( orig, zdir, updir ) );
                            view.setOrientation(ViewerCanvas.VIEW_OTHER);
                            view.updateImage();
                        }
                    }
                    else
                    {
                        if (viewMode == XYZ_MODE)
                        {
                            switch(handle)
                            {
                                case X_MOVE:
                                case X_SCALE:
                                    view.setOrientation(ViewerCanvas.VIEW_RIGHT);
                                    break;
                                case Y_MOVE:
                                case Y_SCALE:
                                    view.setOrientation(ViewerCanvas.VIEW_TOP);
                                    break;
                                case Z_MOVE:
                                case Z_SCALE:
                                    view.setOrientation(ViewerCanvas.VIEW_FRONT);
                                    break;

                            }
                            CoordinateSystem coords = camera.getCameraCoordinates();
                            coords.setOrigin(center);
                            camera.setCameraCoordinates(coords);
                            view.repaint();
                        }
                        else
                        {
                            Vec3 orig = new Vec3(center);
                            Vec3 zdir = null;
                            Vec3 updir = null;
                            double distance = (view.isPerspective() ? camera.getCameraCoordinates().getOrigin().length() : camera.getDistToScreen());
                            switch(handle)
                            {
                                case X_MOVE:
                                case X_SCALE:
                                    orig.add(xaxis.times(distance) );
                                    zdir = xaxis.times(-1);
                                    updir = new Vec3( zaxis );
                                    break;
                                case Y_MOVE:
                                case Y_SCALE:
                                    orig.add(yaxis.times(distance) );
                                    zdir = yaxis.times(-1);
                                    updir = new Vec3( xaxis );
                                    break;
                                case Z_MOVE:
                                case Z_SCALE:
                                    orig.add(zaxis.times(distance) );
                                    zdir = zaxis.times(-1);
                                    updir = new Vec3( yaxis );
                                    break;
                            }
                            camera.setCameraCoordinates( new CoordinateSystem( orig, zdir, updir ) );
                            view.setOrientation(ViewerCanvas.VIEW_OTHER);
                            view.updateImage();
                        }
                    }
                    view.repaint();
                }
            }
        }
        else if (e.getButton() == MouseEvent.BUTTON2)
        {
        	view.setOrientation(ViewerCanvas.VIEW_OTHER);
            view.updateImage();
        }
        /*else if (e.getButton() == MouseEvent.BUTTON3)
        {
            if (!e.isControlDown() && !e.isShiftDown())
                ((PolyMeshEditorWindow)((PolyMeshViewer)view).getController()).triggerPopupEvent(e);
        }*/
        else
        {
            switch(handle)
            {
                case X_MOVE:
                case Y_MOVE:
                case Z_MOVE:
                case TOOL_HANDLE:
                    dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
                    break;
                case X_SCALE:
                case Y_SCALE:
                case Z_SCALE:
                    dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
                    break;
                case ROTATE:
                    dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
                    break;
                default:
                    break;
            }
        }
        dragging = false;
        handle = -1;
        rotateCenter = null;
        return true;
    }

    public void doValueWidgetValidate()
    {
        dragging = false;
        if (handle == X_SCALE || handle == Y_SCALE || handle == Z_SCALE)
            dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
        else if (handle == X_MOVE || handle == Y_MOVE || handle == Z_MOVE)
            dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
        else if (handle == ROTATE)
            dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
        handle = -1;
    }

    public void doValueWidgetAbort()
    {
        dragging = false;
        handle = -1;
        dispatchEvent(new ManipulatorAbortChangingEvent(this, view ));
    }

    public boolean keyPressed(KeyPressedEvent e)
    {
        if (! active)
            return false;
        if (bounds == null)
            return false;
        int key = e.getKeyCode();
        int dx = 0;
        int dy = 0;
        // Pressing an arrow key is equivalent to dragging the first selected point by one pixel.
        if (key == KeyPressedEvent.VK_UP)
        {
          dx = 0;
          dy = -1;
        }
        else if (key == KeyPressedEvent.VK_DOWN)
        {
          dx = 0;
          dy = 1;
        }
        else if (key == KeyPressedEvent.VK_LEFT)
        {
          dx = -1;
          dy = 0;
        }
        else if (key == KeyPressedEvent.VK_RIGHT)
        {
          dx = 1;
          dy = 0;
        }
        else
          return false;
        dispatchEvent(new ManipulatorPrepareChangingEvent(this, view) );
        if (e.isAltDown())
        {
          dx *= 10;
          dy *= 10;
        }
        AdvancedEditingTool.SelectionProperties props =  tool.findSelectionProperties(view.getCamera());
        Vec3 moveCenter = new Vec3(props.bounds.minx, props.bounds.miny, props.bounds.minz);
        Vec3 drag = view.getCamera().findDragVector(moveCenter, dx, dy);
        dispatchEvent(new ManipulatorMovingEvent(this, drag, view) );
        dispatchEvent(new ManipulatorCompletedEvent(this, view) );
        return true;
    }

    /**
     * toggles the view mode, selecting the next view mode available
     */
    public void toggleViewMode()
    {
        viewMode++;
        if (viewMode > 2)
            viewMode = 0;
        if (viewMode == XYZ_MODE)
        {
            xaxis = Vec3.vx();
            yaxis = Vec3.vy();
            zaxis = Vec3.vz();
        }
        view.repaint();
    }

    /**
     * Sets the view mode
     * @param mode View mode
     */
    public void setViewMode(short mode)
    {
        if (mode > 2)
            mode = 0;
        if (viewMode == XYZ_MODE)
        {
            xaxis = Vec3.vx();
            yaxis = Vec3.vy();
            zaxis = Vec3.vz();
        }
    }

    public class RotationHandle
    {
        private int segments;
        protected Color color;
        protected Vec3[] points3d;
        protected Vec2[] points2d;
        protected Vec3 rotAxis, refAxis;
        /**
         * Creates a Rotation Handle with a given number of segments
         *
         * @param segments The number of segmetns that describe the rotation circle
         * @param axis The rotation axis
         */
        public RotationHandle(int segments, short axis, Color color )
        {
            this.segments = segments;
            this.color = color;
            points3d = new Vec3[segments+1];
            points2d = new Vec2[segments+1];
            switch (axis)
            {
                case XAXIS :
                    setAxis(xaxis, yaxis);
                    break;
                case YAXIS :
                    setAxis(yaxis, zaxis);
                    break;
                case ZAXIS :
                    setAxis(zaxis, xaxis);
                    break;
            }
        }

        /**
         * sets the axis of the handle
         * @param rotAxis The rotation axis
         * @param refAxis The axis where arc drawing begins.
         * Used when asking for a rotation feedback polygon
         */
        public void setAxis(Vec3 rotAxis, Vec3 refAxis)
        {
            this.rotAxis = rotAxis;
            this.refAxis = refAxis;
            Mat4 m = Mat4.axisRotation(rotAxis, 2*Math.PI/segments);
            Vec3 v = new Vec3(refAxis);
            for (int i = 0; i <= segments; i++)
                 points3d[i] = v = m.times(v);
        }


        /**
         * Given an angle, this method returns a 3D polygon which can be used to
         * tell the user the rotation amount when drawn on the canvas
         * @param angle
         * @return The 2d points deinfing the polygon
         */

        public Vec3[] getRotationFeedback(double angle)
        {
            Vec3[] points = new Vec3[segments+1];

            points[0] = new Vec3();
            Mat4 m = null;
            Vec3 v = null;
            m = Mat4.axisRotation(rotAxis, angle/segments);
            v = new Vec3(refAxis);
            points[1] = v;
            for (int i = 1; i < segments; i++)
                points[i+1] = v = m.times(v);
            return points;
        }

        /**
         * This method tells if the mouse has been been clicked on a rotation handle
         *
         * @param pos The point where the mouse was clicked
         * @param camera The view camera
         * @return The number of the segment being clicked on or -1 if the mouse has not been
         * clicked on the handle
         */
        public int findClickTarget(Point pos, Camera camera)
        {
            double u, v, w, z;
            double closestz = Double.MAX_VALUE;
            int which = -1;
            for ( int i = 0; i < points2d.length - 1; i++ )
            {
                int orig;
                Vec2 v1 = points2d[i];
                Vec2 v2 = points2d[i+1];
                if ( ( pos.x < v1.x - HANDLE_SIZE / 4 && pos.x < v2.x - HANDLE_SIZE / 4 ) ||
                        ( pos.x > v1.x + HANDLE_SIZE / 4 && pos.x > v2.x + HANDLE_SIZE / 4 ) ||
                        ( pos.y < v1.y - HANDLE_SIZE / 4 && pos.y < v2.y - HANDLE_SIZE / 4 ) ||
                        ( pos.y > v1.y + HANDLE_SIZE / 4 && pos.y > v2.y + HANDLE_SIZE / 4 ) )
                    continue;

                // Determine the distance of the click point from the line.

                if ( Math.abs( v1.x - v2.x ) > Math.abs( v1.y - v2.y ) )
                {
                    if ( v2.x > v1.x )
                    {
                        v = ( (double) pos.x - v1.x ) / ( v2.x - v1.x );
                        u = 1.0 - v;
                    }
                    else
                    {
                        u = ( (double) pos.x - v2.x ) / ( v1.x - v2.x );
                        v = 1.0 - u;
                    }
                    w = u * v1.y + v * v2.y - pos.y;
                }
                else
                {
                    if ( v2.y > v1.y )
                    {
                        v = ( (double) pos.y - v1.y ) / ( v2.y - v1.y );
                        u = 1.0 - v;
                    }
                    else
                    {
                        u = ( (double) pos.y - v2.y ) / ( v1.y - v2.y );
                        v = 1.0 - u;
                    }
                    w = u * v1.x + v * v2.x - pos.x;
                }
                if ( Math.abs( w ) > HANDLE_SIZE / 2 )
                    continue;
                z = u * camera.getObjectToView().timesZ( points3d[i] ) +
                        v * camera.getObjectToView().timesZ( points3d[i+1] );
                if ( z < closestz )
                {
                    closestz = z;
                    which = i;
                }
            }
            return which;
        }
    }

}
