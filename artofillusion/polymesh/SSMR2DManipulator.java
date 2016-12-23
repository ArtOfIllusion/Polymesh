package artofillusion.polymesh;

import java.awt.Color;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;

import artofillusion.Camera;
import artofillusion.MeshEditorWindow;
import artofillusion.MeshViewer;
import artofillusion.ViewerCanvas;
import artofillusion.math.BoundingBox;
import artofillusion.math.CoordinateSystem;
import artofillusion.math.Mat4;
import artofillusion.math.Vec3;
import artofillusion.ui.MeshEditController;
import artofillusion.ui.ThemeManager;
import artofillusion.ui.Translate;
import buoy.event.KeyPressedEvent;
import buoy.event.ToolTipEvent;
import buoy.event.WidgetMouseEvent;
import buoy.widget.BToolTip;

/**
 * This is the manipulator responsible for moving, resizing and rotating mesh selections (2D).
 * SSMR = Select Scale Move Rotate
 */
public class SSMR2DManipulator
extends SSMRManipulator
{
    private Rectangle[] boxes;
    private final static int HANDLE_SIZE = 12;
    private int handle;
    private boolean dragging = false;
    private boolean drawBounds = false;
    private Rectangle baseBounds;
    private Point baseClick;
    private int dragX, dragY;
    private int rotateX, rotateY;
    private Runnable valueWidgetCallback, validateWidgetValue, abortWidgetValue;
    private boolean isCtrlDown, isShiftDown;
    private Vec3 rotateCenter;
    private Vec3 toolHandlePos;
    private static Image topleftIcon, topbottomIcon, toprightIcon, leftrightIcon, centerIcon;
    private static Image scaleHandleImages[] = new Image[9];
    private static Image rotateHandleImages[] = new Image[9];
    private static BToolTip moveToolTip, scaleToolTip, rotateToolTip, centerToolTip;

    public final static short TOP_LEFT = 0;
    public final static short TOP_RIGHT = 1;
    public final static short BOTTOM_LEFT = 2;
    public final static short BOTTOM_RIGHT = 3;
    public final static short TOP = 4;
    public final static short LEFT = 5;
    public final static short BOTTOM = 6;
    public final static short RIGHT = 7;
    public final static short CENTER = 8;
    public final static short TOOL_HANDLE = 9;

    private static final double DRAG_SCALE = Math.PI/360.0;

    short state;

    public SSMR2DManipulator(AdvancedEditingTool tool, ViewerCanvas view, PolyMeshValueWidget valueWidget)
    {
        super(tool, view, valueWidget);
        MARGIN = HANDLE_SIZE;
        if (topleftIcon == null)
        {
            topleftIcon = ThemeManager.getIcon( "polymesh:scaletopleft" ).getImage();
            toprightIcon = ThemeManager.getIcon( "polymesh:scaletopright" ).getImage();
            topbottomIcon = ThemeManager.getIcon( "polymesh:scaletopbottom" ).getImage();
            leftrightIcon = ThemeManager.getIcon( "polymesh:scaleleftright" ).getImage();
            centerIcon = ThemeManager.getIcon( "polymesh:scalecenter" ).getImage();
            scaleHandleImages[TOP_LEFT] = topleftIcon;
            scaleHandleImages[BOTTOM_RIGHT] = topleftIcon;
            scaleHandleImages[TOP_RIGHT] = toprightIcon;
            scaleHandleImages[BOTTOM_LEFT] = toprightIcon;
            scaleHandleImages[TOP] = topbottomIcon;
            scaleHandleImages[BOTTOM] = topbottomIcon;
            scaleHandleImages[RIGHT] = leftrightIcon;
            scaleHandleImages[LEFT] = leftrightIcon;
            scaleHandleImages[CENTER] = centerIcon;
            topleftIcon = ThemeManager.getIcon( "polymesh:rotatetopleft" ).getImage();
            toprightIcon = ThemeManager.getIcon( "polymesh:rotatetopright" ).getImage();
            rotateHandleImages[TOP_LEFT] = ThemeManager.getIcon( "polymesh:rotatetopleft" ).getImage();
            rotateHandleImages[BOTTOM_RIGHT] = ThemeManager.getIcon( "polymesh:rotatebottomright" ).getImage();
            rotateHandleImages[TOP_RIGHT] = ThemeManager.getIcon( "polymesh:rotatetopright" ).getImage();
            rotateHandleImages[BOTTOM_LEFT] = ThemeManager.getIcon( "polymesh:rotatebottomleft" ).getImage();
            rotateHandleImages[TOP] = ThemeManager.getIcon( "polymesh:rotatetop" ).getImage();
            rotateHandleImages[BOTTOM] = ThemeManager.getIcon( "polymesh:rotatebottom" ).getImage();
            rotateHandleImages[RIGHT] = ThemeManager.getIcon( "polymesh:rotateright" ).getImage();
            rotateHandleImages[LEFT] = ThemeManager.getIcon( "polymesh:rotateleft" ).getImage();
            rotateHandleImages[CENTER] = ThemeManager.getIcon( "polymesh:rotatecenter" ).getImage();
            moveToolTip = PMToolTip.areaToolTip(Translate.text("polymesh:moveToolTip2d.tipText"),40);
            scaleToolTip = PMToolTip.areaToolTip(Translate.text("polymesh:scaleToolTip2d.tipText"),40);
            rotateToolTip = PMToolTip.areaToolTip(Translate.text("polymesh:rotateToolTip2d.tipText"),40);
            centerToolTip = PMToolTip.areaToolTip(Translate.text("polymesh:centerToolTip2d.tipText"),40);
        }
        boxes = new Rectangle[9];
        for (int i = 0; i < boxes.length; ++i)
            boxes[i] = new Rectangle(0,0,HANDLE_SIZE, HANDLE_SIZE);
        state = SCALE;
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
        view.addEventLink(ToolTipEvent.class, this, "doTooltip");
        if (view.isPerspective())
            active = false;
        else
            active = true;
    }

    public void setPerspective(boolean perspective)
    {
        //this manipulator is not active in perspective views
        if (perspective)
            active = false;
    }

    public void draw()
    {
        if (! active)
            return;

        MeshEditController controller = ((MeshViewer) view).getController();
        Camera cam = view.getCamera();
        AdvancedEditingTool.SelectionProperties props =  tool.findSelectionProperties(cam);
        bounds = findScreenBounds(props.bounds, cam, (MeshViewer) view, controller);
        boolean drawImagesHorizontal = false;
        boolean drawImagesVertical = false;
        if (bounds != null && bounds.height > 0)
            drawImagesVertical = true;
        if (bounds != null && bounds.width > 0 )
            drawImagesHorizontal = true;
        setBounds(bounds);
        Image[] handles;
        if (state == SCALE)
            handles = scaleHandleImages;
        else
            handles = rotateHandleImages;
        if (bounds != null )
        {
            if (!drawBounds)
            {
                if (drawImagesVertical)
                {
                    view.drawImage(handles[4],bounds.x+(bounds.width-HANDLE_SIZE)/2, bounds.y);
                    boxes[4].x = bounds.x+(bounds.width-HANDLE_SIZE)/2;
                    boxes[4].y = bounds.y;
                    view.drawImage(handles[6],bounds.x+(bounds.width-HANDLE_SIZE)/2, bounds.y+bounds.height-HANDLE_SIZE+1);
                    boxes[6].x = bounds.x+(bounds.width-HANDLE_SIZE)/2;
                    boxes[6].y = bounds.y+bounds.height-HANDLE_SIZE+1;
                }
                if (drawImagesHorizontal)
                {
                    view.drawImage(handles[5],bounds.x, bounds.y+(bounds.height-HANDLE_SIZE)/2);
                    boxes[5].x = bounds.x;
                    boxes[5].y = bounds.y+(bounds.height-HANDLE_SIZE)/2;
                    view.drawImage(handles[7],bounds.x+bounds.width-HANDLE_SIZE+1, bounds.y+(bounds.height-HANDLE_SIZE)/2);
                    boxes[7].x = bounds.x+bounds.width-HANDLE_SIZE+1;
                    boxes[7].y = bounds.y+(bounds.height-HANDLE_SIZE)/2;
                }
                if (drawImagesVertical && drawImagesHorizontal)
                {
                    view.drawImage(handles[0],bounds.x, bounds.y);
                    boxes[0].x = bounds.x;
                    boxes[0].y = bounds.y;
                    view.drawImage(handles[1],bounds.x+bounds.width-HANDLE_SIZE+1,bounds.y);
                    boxes[1].x = bounds.x+bounds.width-HANDLE_SIZE+1;
                    boxes[1].y = bounds.y;
                    view.drawImage(handles[2],bounds.x, bounds.y+bounds.height-HANDLE_SIZE+1);
                    boxes[2].x = bounds.x;
                    boxes[2].y = bounds.y+bounds.height-HANDLE_SIZE+1;
                    view.drawImage(handles[3],bounds.x+bounds.width-HANDLE_SIZE+1, bounds.y+bounds.height-HANDLE_SIZE+1);
                    boxes[3].x = bounds.x+bounds.width-HANDLE_SIZE+1;
                    boxes[3].y = bounds.y+bounds.height-HANDLE_SIZE+1;
                    view.drawImage(handles[4],bounds.x+(bounds.width-HANDLE_SIZE)/2, bounds.y);
                    
                }
                if (state == SCALE)
                {
                    view.drawImage(handles[8],bounds.x + (bounds.width-HANDLE_SIZE)/2, bounds.y + (bounds.height-HANDLE_SIZE)/2);
                    boxes[8].x = bounds.x + (bounds.width-HANDLE_SIZE)/2;
                    boxes[8].y = bounds.y + (bounds.height-HANDLE_SIZE)/2;
                }
                else
                {
                    view.drawImage(handles[8],dragX + rotateX -HANDLE_SIZE/2, dragY + rotateY -HANDLE_SIZE/2);
                    boxes[8].x = dragX + rotateX - HANDLE_SIZE/2;
                    boxes[8].y = dragY + rotateY - HANDLE_SIZE/2;
                }
            }
            else
            {
                view.drawShape( getOriginalBounds(), Color.gray );
            }
        }

    }

    public boolean mousePressedOnHandle(WidgetMouseEvent e, int handle, Vec3 pos)
    {
        if (! active)
            return false;
        toolHandlePos = pos;
        this.handle = TOOL_HANDLE;
        baseClick = new Point(e.getPoint());
        dragging = true;
        dispatchEvent(new ManipulatorPrepareChangingEvent(this, view) );
        return true;
    }

    public boolean mousePressed(WidgetMouseEvent e)
    {
        if (! active)
            return false;
        if (bounds == null)
            return false;
        Point p = e.getPoint();
        for (int i = boxes.length -1; i >= 0; --i)
        {
            if (boxes[i].contains(p))
            {
                handle = i;
                dragging = true;
                if (state == ROTATE && i == CENTER)
                    drawBounds = false;
                else
                    drawBounds = true;
                baseBounds = new Rectangle(getOriginalBounds());
                baseClick = new Point(e.getPoint());
                dispatchEvent(new ManipulatorPrepareChangingEvent(this, view) );
                return true;
            }
        }
        return false;
    }

    public void doValueWidgetCallback()
    {
        double value = valueWidget.getValue();
        if ( state == SCALE )
            scaleDragged(value);
        else
            rotateDragged(value);
    }

    public void scaleDragged(double value)
    {
        double scaleX, scaleY, scaleZ;
        scaleX = scaleY = scaleZ = 1.0;
        short anchorX = 0;
        short anchorY = 0;

        if (isCtrlDown)
        {
            anchorX = ANCHOR_CENTER;
            anchorY = ANCHOR_CENTER;
            switch(handle)
            {
                case TOP_LEFT :
                case TOP_RIGHT :
                case BOTTOM_LEFT :
                case BOTTOM_RIGHT :
                    scaleX = scaleY = valueWidget.getValue();
                    break;
                case LEFT :
                case RIGHT :
                    scaleX = valueWidget.getValue();
                    break;
                case TOP :
                case BOTTOM :
                    scaleY = valueWidget.getValue();
                    break;
                case TOOL_HANDLE :
                case CENTER :
                    break;
            }
        }
        else
        {
            switch(handle)
            {
                case TOP_LEFT :
                    scaleX = scaleY = valueWidget.getValue();
                    anchorX = ANCHOR_RIGHT;
                    anchorY = ANCHOR_BOTTOM;
                    break;
                case TOP_RIGHT :
                    scaleX = scaleY = valueWidget.getValue();
                    anchorX = ANCHOR_LEFT;
                    anchorY = ANCHOR_BOTTOM;
                    break;
                case BOTTOM_LEFT :
                    scaleX = scaleY = valueWidget.getValue();
                    anchorX = ANCHOR_RIGHT;
                    anchorY = ANCHOR_TOP;
                    break;
                case BOTTOM_RIGHT :
                    scaleX = scaleY = valueWidget.getValue();
                    anchorX = ANCHOR_LEFT;
                    anchorY = ANCHOR_TOP;
                    break;
                case LEFT :
                    scaleX = valueWidget.getValue();
                    anchorX = ANCHOR_RIGHT;
                    anchorY = ANCHOR_BOTTOM;
                    break;
                case RIGHT :
                    scaleX = valueWidget.getValue();
                    anchorX = ANCHOR_LEFT;
                    anchorY = ANCHOR_BOTTOM;
                    break;
                case TOP :
                    scaleY = valueWidget.getValue();
                    anchorX = ANCHOR_LEFT;
                    anchorY = ANCHOR_BOTTOM;
                    break;
                case BOTTOM :
                    scaleY = valueWidget.getValue();
                    anchorX = ANCHOR_LEFT;
                    anchorY = ANCHOR_TOP;
                    break;
                case TOOL_HANDLE :
                case CENTER :
                    break;
            }
        }
        Vec3 scaleCenter = new Vec3();
        AdvancedEditingTool.SelectionProperties props =  tool.findSelectionProperties(view.getCamera());
        BoundingBox bounds = props.bounds;
        switch (anchorX)
        {
            case ANCHOR_LEFT :
                scaleCenter.x = bounds.maxx;
                break;
            case ANCHOR_RIGHT :
                scaleCenter.x = bounds.minx;
                break;
            case ANCHOR_CENTER :
                scaleCenter.x = (bounds.maxx+bounds.minx)/2.0;
                break;
        }
        switch (anchorY)
        {
            case ANCHOR_TOP :
                scaleCenter.y = bounds.maxy;
                break;
            case ANCHOR_BOTTOM :
                scaleCenter.y = bounds.miny;
                break;
            case ANCHOR_CENTER :
                scaleCenter.y = (bounds.maxy+bounds.miny)/2.0;
                break;
        }
        scaleCenter.z = (bounds.minz + bounds.maxz)/2.0;
        scaleZ = 1.0;
        if ( isShiftDown )
        {
            if (scaleX != 1.0 )
                scaleZ = scaleY = scaleX;
            else
                scaleZ = scaleX = scaleY;
        }
        Camera cam = view.getCamera();
        Mat4 m = cam.getObjectToView();
        m = Mat4.translation(-scaleCenter.x, -scaleCenter.y, -scaleCenter.z).times(m);
        m = Mat4.scale(scaleX, scaleY, scaleZ).times(m);
        m = Mat4.translation(scaleCenter.x, scaleCenter.y, scaleCenter.z).times(m);
        m = cam.getViewToWorld().times(m);
        m = ((MeshViewer)view).getDisplayCoordinates().toLocal().times(m);
        dispatchEvent(new ManipulatorScalingEvent(this, m, view) );
    }

    public void rotateDragged(double value)
    {
        double angle = 0;
        short axis = ZAXIS;
        switch(handle)
        {
            case TOP_LEFT :
                angle = valueWidget.getValue()*Math.PI/180.0;
                axis = ZAXIS;
                break;
            case TOP_RIGHT :
                angle = valueWidget.getValue()*Math.PI/180.0;
                axis = ZAXIS;
                break;
            case BOTTOM_LEFT :
                angle = valueWidget.getValue()*Math.PI/180.0;
                axis = ZAXIS;
                break;
            case BOTTOM_RIGHT :
                angle = valueWidget.getValue()*Math.PI/180.0;
                axis = ZAXIS;
                break;
            case LEFT :
                angle = valueWidget.getValue()*Math.PI/180.0;
                axis = YAXIS;
                break;
            case RIGHT :
                angle = valueWidget.getValue()*Math.PI/180.0;
                axis = YAXIS;
                break;
            case TOP :
                angle = valueWidget.getValue()*Math.PI/180.0;
                axis = XAXIS;
                break;
            case BOTTOM :
                angle = valueWidget.getValue()*Math.PI/180.0;
                axis = XAXIS;
                break;
            case CENTER :
                break;

        }
        Camera cam = view.getCamera();
        if (rotateCenter == null)
        {
            AdvancedEditingTool.SelectionProperties props =  tool.findSelectionProperties(view.getCamera());
            BoundingBox bounds = props.bounds;
            rotateCenter = new Vec3((bounds.minx+bounds.maxx)/2.0, (bounds.miny+bounds.maxy)/2.0, (bounds.minz+bounds.maxz)/2.0);
            rotateCenter = cam.getViewToWorld().times(rotateCenter);
            double depth = cam.getWorldToView().times(rotateCenter).z;
            rotateCenter = cam.convertScreenToWorld(new Point(rotateX, rotateY), depth);
        }
        Vec3 xdir = cam.getWorldToView().timesDirection(Vec3.vx());
        Vec3 ydir = cam.getWorldToView().timesDirection(Vec3.vy());
        Vec3 zdir = cam.getWorldToView().timesDirection(Vec3.vz());
        if (xdir.cross(ydir).dot(zdir) < 0.0)
            angle = -angle;

        // Find the transformation matrix.
        CoordinateSystem coords = ((MeshViewer)view).getDisplayCoordinates();
        Mat4 m = coords.fromLocal();
        m = Mat4.translation(-rotateCenter.x, -rotateCenter.y, -rotateCenter.z).times(m);
        Vec3 rotAxis;
        if (axis == XAXIS)
            rotAxis = cam.getViewToWorld().timesDirection(Vec3.vx());
        else if (axis == YAXIS)
            rotAxis = cam.getViewToWorld().timesDirection(Vec3.vy());
        else
            rotAxis = cam.getViewToWorld().timesDirection(Vec3.vz());
        m = Mat4.axisRotation(rotAxis, angle).times(m);
        m = Mat4.translation(rotateCenter.x, rotateCenter.y, rotateCenter.z).times(m);
        m = coords.toLocal().times(m);
        dispatchEvent(new ManipulatorRotatingEvent(this, m, view) );
    }


    public boolean mouseDragged(WidgetMouseEvent e)
    {
        if (! active)
            return false;
        if (!dragging)
            return false;
        if (state == SCALE || handle == TOOL_HANDLE )
            return scaleDragged( e );
        else
            return rotateDragged( e );
    }

    public boolean scaleDragged(WidgetMouseEvent e)
    {
        //if (view instanceof PolyMeshViewer)
        //    ((PolyMeshViewer)view).moveToGrid(e);
        Point p = e.getPoint();
        Rectangle newBounds = new Rectangle();
        boolean move = false;
        boolean isShiftDown = e.isShiftDown();
        boolean isCtrlDown = ( e.getModifiers() & ActionEvent.CTRL_MASK ) != 0;
        double scaleX, scaleY, scaleZ;
        Vec3 drag = null;
        short anchorX = 0;
        short anchorY = 0;
        double gridSize = view.getGridSpacing()/view.getSnapToSubdivisions();
        if (isCtrlDown)
        {
            anchorX = ANCHOR_CENTER;
            anchorY = ANCHOR_CENTER;
            switch(handle)
            {
                case TOP_LEFT :
                    newBounds.x = p.x + baseBounds.x - baseClick.x;
                    newBounds.y = p.y + baseBounds.y - baseClick.y;
                    newBounds.width =  baseBounds.width + 2 * ( - p.x + baseClick.x );
                    newBounds.height = baseBounds.height + 2 * ( - p.y + baseClick.y );
                    break;
                case TOP_RIGHT :
                    newBounds.x = baseBounds.x - p.x + baseClick.x;
                    newBounds.y = p.y + baseBounds.y - baseClick.y;
                    newBounds.width =  baseBounds.width + 2 * ( p.x - baseClick.x );
                    newBounds.height = baseBounds.height + 2 * ( - p.y + baseClick.y );
                    break;
                case BOTTOM_LEFT :
                    newBounds.x = p.x + baseBounds.x - baseClick.x;
                    newBounds.y = baseBounds.y - p.y + baseClick.y;
                    newBounds.width =  baseBounds.width + 2 * ( - p.x + baseClick.x );
                    newBounds.height = baseBounds.height + 2 * ( p.y - baseClick.y );
                    break;
                case BOTTOM_RIGHT :
                    newBounds.x = baseBounds.x - p.x + baseClick.x;
                    newBounds.y = baseBounds.y - p.y + baseClick.y;
                    newBounds.width = baseBounds.width + 2 * ( p.x - baseClick.x );
                    newBounds.height = baseBounds.height + 2 * ( p.y - baseClick.y );
                    break;
                case LEFT :
                    newBounds.x = p.x + baseBounds.x - baseClick.x;
                    newBounds.y = baseBounds.y;
                    newBounds.width =  baseBounds.width + 2 *( - p.x + baseClick.x );
                    newBounds.height = baseBounds.height;
                    break;
                case RIGHT :
                    newBounds.x = baseBounds.x - p.x + baseClick.x;
                    newBounds.y = baseBounds.y;
                    newBounds.width =  baseBounds.width + 2 * ( p.x - baseClick.x );
                    newBounds.height = baseBounds.height;
                    break;
                case TOP :
                    newBounds.x = baseBounds.x;
                    newBounds.y = p.y + baseBounds.y - baseClick.y;
                    newBounds.width =  baseBounds.width;
                    newBounds.height = baseBounds.height + 2 * ( - p.y + baseClick.y );
                    break;
                case BOTTOM :
                    newBounds.x = baseBounds.x;
                    newBounds.y = baseBounds.y - p.y + baseClick.y;
                    newBounds.width = baseBounds.width;
                    newBounds.height = baseBounds.height + 2 * ( p.y - baseClick.y );
                    break;
                case TOOL_HANDLE :
                case CENTER :
                    move = true;
                    dragX = p.x - baseClick.x;
                    dragY = p.y - baseClick.y;
                    double amplitude = dragY*0.01;
                    if (isShiftDown)
                    {
                        amplitude /= gridSize;
                        amplitude = Math.round(amplitude);
                        amplitude *= gridSize;
                    }
                    drag = view.getCamera().getCameraCoordinates().getZDirection().times(amplitude);
                    ((MeshEditorWindow)((MeshViewer)view).getController()).setHelpText(Translate.text("polymesh:moveCenterBy", new String[] { String.valueOf(Math.round(drag.x*1e5)/1e5), String.valueOf(Math.round(drag.y*1e5)/1e5), String.valueOf(Math.round(drag.z*1e5)/1e5) } ));
                    break;
            }
        }
        else
        {
            switch(handle)
            {
                case TOP_LEFT :
                    newBounds.x = p.x + baseBounds.x - baseClick.x;
                    newBounds.y = p.y + baseBounds.y - baseClick.y;
                    newBounds.width =  baseBounds.width - p.x + baseClick.x;
                    newBounds.height = baseBounds.height - p.y + baseClick.y;
                    anchorX = ANCHOR_RIGHT;
                    anchorY = ANCHOR_BOTTOM;
                    break;
                case TOP_RIGHT :
                    newBounds.x = baseBounds.x;
                    newBounds.y = p.y + baseBounds.y - baseClick.y;
                    newBounds.width =  baseBounds.width + p.x - baseClick.x;
                    newBounds.height = baseBounds.height - p.y + baseClick.y;
                    anchorX = ANCHOR_LEFT;
                    anchorY = ANCHOR_BOTTOM;
                    break;
                case BOTTOM_LEFT :
                    newBounds.x = p.x + baseBounds.x - baseClick.x;
                    newBounds.y = baseBounds.y;
                    newBounds.width =  baseBounds.width - p.x + baseClick.x;
                    newBounds.height = baseBounds.height + p.y - baseClick.y;
                    anchorX = ANCHOR_RIGHT;
                    anchorY = ANCHOR_TOP;
                    break;
                case BOTTOM_RIGHT :
                    newBounds.x = baseBounds.x;
                    newBounds.y = baseBounds.y;
                    newBounds.width = baseBounds.width + p.x - baseClick.x;
                    newBounds.height = baseBounds.height + p.y - baseClick.y;
                    anchorX = ANCHOR_LEFT;
                    anchorY = ANCHOR_TOP;
                    break;
                case LEFT :
                    newBounds.x = p.x + baseBounds.x - baseClick.x;
                    newBounds.y = baseBounds.y;
                    newBounds.width =  baseBounds.width - p.x + baseClick.x;
                    newBounds.height = baseBounds.height;
                    anchorX = ANCHOR_RIGHT;
                    anchorY = ANCHOR_BOTTOM;
                    break;
                case RIGHT :
                    newBounds.x = baseBounds.x;
                    newBounds.y = baseBounds.y;
                    newBounds.width =  baseBounds.width + p.x - baseClick.x;
                    newBounds.height = baseBounds.height;
                    anchorX = ANCHOR_LEFT;
                    anchorY = ANCHOR_BOTTOM;
                    break;
                case TOP :
                    newBounds.x = baseBounds.x;
                    newBounds.y = p.y + baseBounds.y - baseClick.y;
                    newBounds.width =  baseBounds.width;
                    newBounds.height = baseBounds.height - p.y + baseClick.y;
                    anchorX = ANCHOR_LEFT;
                    anchorY = ANCHOR_BOTTOM;
                    break;
                case BOTTOM :
                    newBounds.x = baseBounds.x;
                    newBounds.y = baseBounds.y;
                    newBounds.width = baseBounds.width;
                    newBounds.height = baseBounds.height + p.y - baseClick.y;
                    anchorX = ANCHOR_LEFT;
                    anchorY = ANCHOR_TOP;
                    break;
                case TOOL_HANDLE :
                case CENTER :
                    move = true;
                    dragX = p.x - baseClick.x;
                    dragY = p.y - baseClick.y;
                    AdvancedEditingTool.SelectionProperties props =  tool.findSelectionProperties(view.getCamera());
                    BoundingBox bounds = props.bounds;
                    Vec3 moveCenter = new Vec3((bounds.minx + bounds.maxx)/2.0, (bounds.miny+bounds.maxy)/2.0, (bounds.minz + bounds.maxz)/2.0);
                    if (handle == TOOL_HANDLE)
                        moveCenter = toolHandlePos;
                    if (isShiftDown)
                    {
                        double d = ((float) dragX) / view.getScale();
                        d /= gridSize;
                        d = Math.round(d);
                        d *= gridSize;
                        dragX = (int)Math.round( d*view.getScale() );
                        d = ((float) dragY) / view.getScale();
                        d /= gridSize;
                        d = Math.round(d);
                        d *= gridSize;
                        dragY = (int)Math.round( d*view.getScale() );
                    }
                    drag = view.getCamera().findDragVector(moveCenter, dragX, dragY);
                    ((MeshEditorWindow)((MeshViewer)view).getController()).setHelpText(Translate.text("polymesh:moveCenterBy", new String[] { String.valueOf(Math.round(drag.x*1e5)/1e5), String.valueOf(Math.round(drag.y*1e5)/1e5), String.valueOf(Math.round(drag.z*1e5)/1e5) } ));
                    break;
            }
        }
        if (!move)
        {
            Vec3 scaleCenter = new Vec3();
            AdvancedEditingTool.SelectionProperties props =  tool.findSelectionProperties(view.getCamera());
            BoundingBox bounds = props.bounds;
            switch (anchorX)
            {
                case ANCHOR_LEFT :
                    scaleCenter.x = bounds.maxx;
                    break;
                case ANCHOR_RIGHT :
                    scaleCenter.x = bounds.minx;
                    break;
                case ANCHOR_CENTER :
                    scaleCenter.x = (bounds.maxx+bounds.minx)/2.0;
                    break;
            }
            switch (anchorY)
            {
                case ANCHOR_TOP :
                    scaleCenter.y = bounds.maxy;
                    break;
                case ANCHOR_BOTTOM :
                    scaleCenter.y = bounds.miny;
                    break;
                case ANCHOR_CENTER :
                    scaleCenter.y = (bounds.maxy+bounds.miny)/2.0;
                    break;
            }
            scaleCenter.z = (bounds.minz + bounds.maxz)/2.0;
            scaleZ = 1;
            if (baseBounds.width != 0)
                scaleX = ( (double) newBounds.width) / ( (double) baseBounds.width);
            else
                scaleX = 1.0;
            if (baseBounds.height != 0)
                scaleY = ( (double) newBounds.height) / ( (double) baseBounds.height);
            else
                scaleY = 1.0;
            if (scaleX < 0)
                scaleX = 0;
            if (scaleY < 0)
                scaleY= 0;
            if ( isShiftDown )
            {
                if (scaleX > 1.0 || scaleY > 1.0 )
                {
                    if (scaleX > scaleY)
                        scaleY = scaleX;
                    else
                        scaleX = scaleY;
                }
                else
                {
                    if (scaleX < scaleY)
                        scaleY = scaleX;
                    else
                        scaleX = scaleY;
                }
                scaleZ = scaleX;
            }
            else
                scaleZ = 1.0;
            Camera cam = view.getCamera();
            Mat4 m = cam.getObjectToView();
            m = Mat4.translation(-scaleCenter.x, -scaleCenter.y, -scaleCenter.z).times(m);
            m = Mat4.scale(scaleX, scaleY, scaleZ).times(m);
            m = Mat4.translation(scaleCenter.x, scaleCenter.y, scaleCenter.z).times(m);
            m = cam.getViewToWorld().times(m);
            m = ((MeshViewer)view).getDisplayCoordinates().toLocal().times(m);
            ((MeshEditorWindow)((MeshViewer)view).getController()).setHelpText(Translate.text("polymesh:scaleUVBy", new String[] { String.valueOf(Math.round(scaleX*1e5)/1e5), String.valueOf(Math.round(scaleY*1e5)/1e5) } ));
            dispatchEvent(new ManipulatorScalingEvent(this, m, view) );
        }
        else
            dispatchEvent(new ManipulatorMovingEvent(this, drag, view) );
        return true;
    }

    public boolean rotateDragged(WidgetMouseEvent e)
    {
        Point p = e.getPoint();
        boolean move = false;

        double angle = 0;
        short axis = ZAXIS;
        switch(handle)
        {
            case TOP_LEFT :
                angle = DRAG_SCALE*((p.x-baseClick.x)*1 + (p.y-baseClick.y)*-1);
                axis = ZAXIS;
                break;
            case TOP_RIGHT :
                angle = DRAG_SCALE*((p.x-baseClick.x)*1 + (p.y-baseClick.y)*1);
                axis = ZAXIS;
                break;
            case BOTTOM_LEFT :
                angle = DRAG_SCALE*((p.x-baseClick.x)*-1 + (p.y-baseClick.y)*-1);
                axis = ZAXIS;
                break;
            case BOTTOM_RIGHT :
                angle = DRAG_SCALE*((p.x-baseClick.x)*-1 + (p.y-baseClick.y)*1);
                axis = ZAXIS;
                break;
            case LEFT :
                angle = DRAG_SCALE*((p.x-baseClick.x)*-1);
                axis = YAXIS;
                break;
            case RIGHT :
                angle = DRAG_SCALE*((p.x-baseClick.x)*1);
                axis = YAXIS;
                break;
            case TOP :
                angle = DRAG_SCALE*((p.y-baseClick.y)*-1);
                axis = XAXIS;
                break;
            case BOTTOM :
                angle = DRAG_SCALE*((p.y-baseClick.y)*1);
                axis = XAXIS;
                break;
            case CENTER :
                move = true;
                dragX = p.x - baseClick.x;
                dragY = p.y - baseClick.y;
                break;

        }
        if (e.isShiftDown())
        {
            angle *= (180.0/(5*Math.PI));
            angle = Math.round(angle);
            angle *= (5*Math.PI)/180;
        }
        Camera cam = view.getCamera();
        if (rotateCenter == null)
        {
            AdvancedEditingTool.SelectionProperties props =  tool.findSelectionProperties(view.getCamera());
            BoundingBox bounds = props.bounds;
            rotateCenter = new Vec3((bounds.minx+bounds.maxx)/2.0, (bounds.miny+bounds.maxy)/2.0, (bounds.minz+bounds.maxz)/2.0);
            rotateCenter = cam.getViewToWorld().times(rotateCenter);
            double depth = cam.getWorldToView().times(rotateCenter).z;
            rotateCenter = cam.convertScreenToWorld(new Point(rotateX, rotateY), depth);
        }
        // Find the transformation matrix.
        CoordinateSystem coords = ((MeshViewer)view).getDisplayCoordinates();
        Mat4 m = coords.fromLocal();
        m = Mat4.translation(-rotateCenter.x, -rotateCenter.y, -rotateCenter.z).times(m);
        Vec3 rotAxis;
        if (axis == XAXIS)
            rotAxis = cam.getViewToWorld().timesDirection(Vec3.vx());
        else if (axis == YAXIS)
            rotAxis = cam.getViewToWorld().timesDirection(Vec3.vy());
        else
            rotAxis = cam.getViewToWorld().timesDirection(Vec3.vz());
        m = Mat4.axisRotation(rotAxis, angle).times(m);
        m = Mat4.translation(rotateCenter.x, rotateCenter.y, rotateCenter.z).times(m);
        m = coords.toLocal().times(m);
        if (!move)
        {
            dispatchEvent(new ManipulatorRotatingEvent(this, m, view) );
            ((MeshEditorWindow)((MeshViewer)view).getController()).setHelpText(Translate.text("polymesh:rotateBy", new String[] { String.valueOf(Math.round(angle*180*1e5/Math.PI)/1e5) } ));
        }
        else
            view.repaint();
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
            if (e.getButton() == MouseEvent.BUTTON2 && handle != CENTER)
            {
                if (valueWidget != null)
                {
                    isCtrlDown = ( e.getModifiers() & ActionEvent.CTRL_MASK ) != 0;
                    isShiftDown = e.isShiftDown();
                    if (state == ROTATE)
                    {
                        valueWidget.setTempValueRange(-180, 180);
                        valueWidget.activate( valueWidgetCallback);
                    }
                    else
                    {
                        valueWidget.setTempValueRange(0, valueWidget.getValueMax());
                        valueWidget.activate( 1.0, valueWidgetCallback);
                    }
                    dragging = false;
                    return true;
                }
            }
            /*else if (e.getButton() == MouseEvent.BUTTON3)
            {
                if (!e.isControlDown() && !e.isShiftDown())
                    ((PolyMeshEditorWindow)((PolyMeshViewer)view).getController()).triggerPopupEvent(e);
            }*/
            else
            {
                rotateX = bounds.x + bounds.width/2;
                rotateY = bounds.y + bounds.height/2;
                dragX = dragY = 0;
                if (state == SCALE)
                    state = ROTATE;
                else
                    state = SCALE;
                view.repaint();
                dispatchEvent(new ManipulatorAbortChangingEvent(this, view) );
            }
        }
        else
        {
            //mouseDragged(e);
            if (handle == CENTER || handle == TOOL_HANDLE)
            {
                if (state == SCALE || handle == TOOL_HANDLE)
                    dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
                else
                {
                    rotateX += dragX;
                    rotateY += dragY;
                    dragX = dragY = 0;
                }
            }
            else
            {
                if (state == SCALE)
                    dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
                else
                    dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
                rotateX = bounds.x + bounds.width/2;
                rotateY = bounds.y + bounds.height/2;
                dragX = dragY = 0;
            }
        }
        dragging = false;
        drawBounds = false;
        handle = -1;
        rotateCenter = null;
        return true;
    }

    public void doValueWidgetValidate()
    {
        dragging = false;
        drawBounds = false;
        handle = -1;
        rotateCenter = null;
        if (state == SCALE)
            dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
        else
            dispatchEvent(new ManipulatorCompletedEvent(this, view ) );
    }

    public void doValueWidgetAbort()
    {
        dragging = false;
        drawBounds = false;
        handle = -1;
        rotateCenter = null;
        dispatchEvent(new ManipulatorAbortChangingEvent(this, view ));
    }

    public boolean doTooltip(ToolTipEvent e)
    {
        if (!active || !helpModeOn)
            return false;
        if (bounds == null)
            return false;
        Point p = e.getPoint();
        for (int i = boxes.length -1; i >= 0; --i)
        {
            if (boxes[i].contains(p))
            if (i != CENTER)
            {
                if (state == SCALE)
                    scaleToolTip.processEvent(e);
                else
                    rotateToolTip.processEvent(e);
            }
            else
            {
                if (state == SCALE)
                    moveToolTip.processEvent(e);
                else
                    centerToolTip.processEvent(e);
            }
        }
        return false;
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
        BoundingBox bounds = props.bounds;
        Vec3 moveCenter = new Vec3(bounds.minx, bounds.miny, bounds.minz);
        Vec3 drag = view.getCamera().findDragVector(moveCenter, dx, dy);
        dispatchEvent(new ManipulatorMovingEvent(this, drag, view) );
        dispatchEvent(new ManipulatorCompletedEvent(this, view) );
        return true;
    }

}
