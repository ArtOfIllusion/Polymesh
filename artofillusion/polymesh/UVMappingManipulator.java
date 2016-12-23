/*
 *  Copyright (C) 2007 by Francois Guillet
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package artofillusion.polymesh;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;

import javax.swing.ImageIcon;

import artofillusion.math.Mat4;
import artofillusion.math.Vec2;
import artofillusion.math.Vec3;
import artofillusion.object.TriangleMesh;
import artofillusion.object.TriangleMesh.Edge;
import artofillusion.object.TriangleMesh.Face;
import artofillusion.polymesh.UVMappingCanvas.Range;
import artofillusion.polymesh.UVMappingCanvas.SelectionCommand;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedEdge;
import artofillusion.ui.ThemeManager;
import buoy.event.MouseScrolledEvent;
import buoy.event.WidgetMouseEvent;

/**
 * Manipulator used to manipulate mesh pieces
 * 
 * @author Francois Guillet
 *
 */
public class UVMappingManipulator {

    protected UVMappingCanvas canvas;

    protected UVMappingEditorDialog window;

    protected Point click;

    private boolean dragBox, dragging;

    private boolean[] originalSelection;

    private Point[] originalPositions;

    private Vec2[][] originalVertices;
    
    private Vec2[] deltas;

    private Point center;

    private Point originalCenter;

    private static Image ghostscale;

    private static Image centerhandle;

    private static Image uvHandleImages[] = new Image[3];

    private Rectangle[] boxes = new Rectangle[6];

    private UVRotationHandle rotationHandle;

    private int handle;

    private int dx;

    private int dy;

    private int rotSegment;

    private double rotAngle;

    private double scalex, scaley;

    private int axisLength = 75;

    private int originalAxisLength;

    private boolean scaling;

    private boolean moving;

    private double originalScale;

    private Vec2 originalOrigin;

    private int numSel; //used to keep track if more than one vertex is selected

    //manipulator is displayed only if numSel >= 2

    private boolean liveUpdate = true;

    private boolean draggingHandle;

    private int anchor; //index of the anchor vertex, when set using

    //ctrl drag center handle

    private boolean globalScaling; //true if the user moves all pieces at once

    //(shift right drag)

    private boolean globalMoving;//true if the user scales all pieces at once

    //(ctrl right drag)

    private boolean[] curSelection;

    private ArrayList<Integer> selection;

    public final static short U_MOVE = 0;

    public final static short U_SCALE = 1;

    public final static short V_MOVE = 2;

    public final static short V_SCALE = 3;

    private static final int HANDLE_SIZE = 12;

    private static final int IMAGE_MARGIN = 6;

    private static final int CENTER_HANDLE = 0;

    private static final int U_HANDLE = 1;

    private static final int US_HANDLE = 2;

    private static final int V_HANDLE = 3;

    private static final int VS_HANDLE = 4;

    private static final int UVS_HANDLE = 5;

    private static final int ROTATE_HANDLE = 6;

    public UVMappingManipulator(UVMappingCanvas canvas,
	    UVMappingEditorDialog window) {
	super();
	this.canvas = canvas;
	canvas.setManipulator(this);
	this.window = window;
	selection = new ArrayList();
	for (int i = 0; i < 6; i++) {
	    boxes[i] = new Rectangle(0, 0, HANDLE_SIZE / 2, HANDLE_SIZE / 2);
	}
	rotationHandle = new UVRotationHandle(64, new Point(), Color.orange);
	if (centerhandle == null) {
	    uvHandleImages[U_MOVE] = ThemeManager.getIcon( "polymesh:uhandle").getImage();
	    uvHandleImages[U_SCALE] = ThemeManager.getIcon( "polymesh:uvscale").getImage();
	    uvHandleImages[V_MOVE] = ThemeManager.getIcon( "polymesh:vhandle").getImage();
	    ghostscale = ThemeManager.getIcon( "polymesh:ghostscale").getImage();
	    centerhandle = ThemeManager.getIcon( "polymesh:centerhandle").getImage();

	}
	anchor = -1;
    }

    public void mousePressed(WidgetMouseEvent ev) {
	boolean[] selected = canvas.getSelection();
	recordSelection(selected);
	click = ev.getPoint();
	if (ev.isControlDown()
		&& boxes[CENTER_HANDLE].x <= click.x + 3
		&& boxes[CENTER_HANDLE].width + 6 >= click.x
			- boxes[CENTER_HANDLE].x
		&& boxes[CENTER_HANDLE].y <= click.y + 3
		&& boxes[CENTER_HANDLE].height + 6 >= click.y
			- boxes[CENTER_HANDLE].y) {
	    originalCenter = new Point(center);
	    draggingHandle = true;
	    return;
	}
	Point[] v = canvas.getVerticesPoints();
	boolean hit = false;
	for (int i = 0; i < v.length; i++) {
	    if (click.x > v[i].x - 3 && click.x < v[i].x + 3
		    && click.y > v[i].y - 3 && click.y < v[i].y + 3) {
		hit = true;
		if (selected[i]) {
		    if (!ev.isShiftDown() && !ev.isControlDown()) {
			dragging = true;
			handle = CENTER_HANDLE;
		    }
		}
		break;
	    }
	}
	if (hit && !dragging) {
	    for (int i = 0; i < v.length; i++) {
		if (click.x > v[i].x - 3 && click.x < v[i].x + 3
			&& click.y > v[i].y - 3 && click.y < v[i].y + 3) {
		    if (!ev.isControlDown()) {
			selected[i] = true;
		    } else {
			selected[i] = !selected[i];
		    }
		} else if (!ev.isControlDown() && !ev.isShiftDown()) {
		    selected[i] = false;
		}
	    }
	    checkSelectionNumber(selected);
	    canvas.setSelection(selected, false);
	    computeCenter();
	    handle = 0;
	    dragging = true;
	} else if (!dragging) {
	    for (int i = 0; i < boxes.length; i++) {
		if (boxes[i].x <= click.x + 3
			&& boxes[i].width + 6 >= click.x - boxes[i].x
			&& boxes[i].y <= click.y + 3
			&& boxes[i].height + 6 >= click.y - boxes[i].y) {
		    handle = i;
		    if (handle == US_HANDLE || handle == VS_HANDLE || 
			    handle == UVS_HANDLE) {
			deltas = new Vec2[canvas.getVerticesPoints().length];
			for (int j = 0; j < deltas.length; j++) {
			    deltas[j] = new Vec2();
			}
		    }
		    dragging = true;
		    break;
		}
	    }
	    if (!dragging) {
		rotSegment = rotationHandle.findClickTarget(click);
		if (rotSegment != -1) {
		    handle = ROTATE_HANDLE;
		    dragging = true;
		    deltas = new Vec2[canvas.getVerticesPoints().length];
		    for (int i = 0; i < deltas.length; i++) {
			deltas[i] = new Vec2();
		    }
		}
	    }
	}
	if (dragging == true) {
	    if (center == null) {
		computeCenter();
		if (center == null) {
		    return;
		}
	    }
	    originalAxisLength = axisLength;
	    originalCenter = new Point(center);
	    originalPositions = new Point[v.length];
	    for (int i = 0; i < selected.length; i++) {
		originalPositions[i] = new Point(v[i]);
	    }
	}
	if (!dragging) {
	    if (ev.getButton() == 3) {
		if (ev.isControlDown()) {
		    scaling = true;
		    if (!ev.isShiftDown()) {
			originalScale = canvas.getScale();
			canvas.disableImageDisplay();
			globalScaling = false;
		    } else {
			Vec2[][] verts = canvas.getMapping().v;
			originalVertices = new Vec2[verts.length][];
			for (int i = 0; i < verts.length; i++) {
			    originalVertices[i] = new Vec2[verts[i].length];
			    for (int j = 0; j < verts[i].length; j++) {
				originalVertices[i][j] = new Vec2(verts[i][j]);
			    }
			}
			center = null;
			globalScaling = true;
		    }
		} else {
		    moving = true;
		    originalScale = canvas.getScale();
		    if (!ev.isShiftDown()) {
			canvas.disableImageDisplay();
			originalOrigin = canvas.getOrigin();
			globalMoving = false;
		    } else {
			Vec2[][] verts = canvas.getMapping().v;
			originalVertices = new Vec2[verts.length][];
			for (int i = 0; i < verts.length; i++) {
			    originalVertices[i] = new Vec2[verts[i].length];
			    for (int j = 0; j < verts[i].length; j++) {
				originalVertices[i][j] = new Vec2(verts[i][j]);
			    }
			}
			center = null;
			globalMoving = true;
		    }
		}
	    } else {
		if (!ev.isShiftDown() && !ev.isControlDown()) {
		    for (int i = 0; i < selected.length; i++) {
			selected[i] = false;
		    }
		    canvas.setSelection(selected, false);
		    anchor = -1;
		    center = null;
		}
		dragBox = true;
		checkSelectionNumber(selected);
		originalSelection = new boolean[selected.length];
		for (int i = 0; i < selected.length; i++) {
		    originalSelection[i] = selected[i];
		}
	    }
	}
    }

    private void recordSelection(boolean[] selected) {
	if (curSelection == null || selected.length != curSelection.length) {
	    curSelection = new boolean[selected.length];
	}
	if (window.tensionOn()) {
	    selection.clear();
	}
	for (int i = 0; i < selected.length; i++) {
	    curSelection[i] = selected[i];
	    if (window.tensionOn() && selected[i]) {
		selection.add(i);
	    }
	}

    }

    /**
     * Computes the position of the manipulator
     *
     */
    private void computeCenter() {
	Point[] v = canvas.getVerticesPoints();
	boolean[] selected = canvas.getSelection();
	center = new Point();
	if (anchor > -1) {
	    //manipulator is anchored on a vertex
	    center.x = v[anchor].x;
	    center.y = v[anchor].y;
	} else {
	    int count = 0;
	    for (int i = 0; i < v.length; i++) {
		if (selected[i]) {
		    center.x += v[i].x;
		    center.y += v[i].y;
		    count++;
		}
	    }
	    if (count == 0) {
		center = null;
		return;
	    }
	    double m = 1.0 / ((double) count);
	    center.x = (int) Math.round(center.x * m);
	    center.y = (int) Math.round(center.y * m);
	}
	setCenter();
    }

    private void setCenter() {
	boxes[0].x = center.x - HANDLE_SIZE / 2;
	boxes[0].y = center.y - HANDLE_SIZE / 2;
	boxes[1].x = center.x + axisLength + IMAGE_MARGIN;
	boxes[1].y = center.y - HANDLE_SIZE / 2;
	boxes[2].x = center.x + axisLength + HANDLE_SIZE + 2 * IMAGE_MARGIN;
	boxes[2].y = center.y - HANDLE_SIZE / 2;
	boxes[3].x = center.x - HANDLE_SIZE / 2;
	boxes[3].y = center.y - axisLength - HANDLE_SIZE - IMAGE_MARGIN;
	boxes[4].x = center.x - HANDLE_SIZE / 2;
	boxes[4].y = center.y - axisLength - 2 * HANDLE_SIZE - 2 * IMAGE_MARGIN;
	boxes[5].x = center.x + axisLength + HANDLE_SIZE + 2 * IMAGE_MARGIN;
	boxes[5].y = center.y - axisLength - 2 * HANDLE_SIZE - 2 * IMAGE_MARGIN;
	rotationHandle.setCenter(center);
    }

    public void mouseMoved(WidgetMouseEvent ev) {
	//Mac workaround for Ctrl-click drag
	if (draggingHandle) {
	    mouseDragged(ev);
	}
    }

    public void mouseDragged(WidgetMouseEvent ev) {
	Point currentPt = ev.getPoint();
	Point[] v = canvas.getVerticesPoints();
	boolean[] selected = canvas.getSelection();
	if (dragging) {
	    switch (handle) {
	    case CENTER_HANDLE:
		dx = currentPt.x - click.x;
		dy = currentPt.y - click.y;
		moveVertices(v, dx, dy);
		break;
	    case U_HANDLE:
		dx = currentPt.x - click.x;
		moveVertices(v, dx, 0);
		break;
	    case V_HANDLE:
		dy = currentPt.y - click.y;
		moveVertices(v, 0, dy);
		break;
	    case US_HANDLE:
	    case VS_HANDLE:
	    case UVS_HANDLE:
		Vec2 base = new Vec2(click.x - center.x, click.y - center.y);
		Vec2 current = new Vec2(currentPt.x - center.x, currentPt.y
			- center.y);
		double scale = base.dot(current);
		if (base.length() < 1)
		    scale = 1;
		else
		    scale /= (base.length() * base.length());
		scalex = base.x * current.x;
		if (base.x * base.x > 1) {
		    scalex /= base.x * base.x;
		} else
		    scalex = 1;
		scaley = base.y * current.y;
		if (base.y * base.y > 1) {
		    scaley /= base.y * base.y;
		} else
		    scaley = 1;
		if (ev.isControlDown()) {
		    axisLength = (int) Math.round(originalAxisLength * scale);
		    if (axisLength <= 5) {
			axisLength = 5;
		    }
		    canvas.repaint();
		} else {
		    switch (handle) {
		    case US_HANDLE:
			if (ev.isShiftDown()) {
			    scaley = scalex;
			} else
			    scaley = 1.0;
			break;
		    case VS_HANDLE:
			if (ev.isShiftDown()) {
			    scalex = scaley;
			} else
			    scalex = 1.0;
			break;
		    case UVS_HANDLE:
			if (ev.isShiftDown()) {
			    if (scalex < 1 && scaley < 1)
				scalex = scaley = Math.min(scalex, scaley);
			    else
				scalex = scaley = Math.max(scalex, scaley);
			}
			break;
		    default:
			break;
		    }
		    double x;
		    double y;
		    if (window.tensionOn()) {
			for (int i = 0; i < v.length; i++) {
				if (selected[i] && !canvas.isPinned(i)) {
				    x = scalex * (originalPositions[i].x - center.x);
				    y = scaley * (originalPositions[i].y - center.y);
				    deltas[i].x = x + center.x - originalPositions[i].x;
				    deltas[i].y = y + center.y - originalPositions[i].y;
				}
				else {
				    deltas[i].x = 0;
				    deltas[i].y = 0;
				}
			    }
			    canvas.adjustDeltas(deltas);
			    for (int i = 0; i < v.length; i++) {
				v[i].x = originalPositions[i].x + (int)Math.round(deltas[i].x);
				v[i].y = originalPositions[i].y + (int)Math.round(deltas[i].y);
			    }
			    canvas.setPositions(v, null);
		    } else {
			for (int i = 0; i < v.length; i++) {
			    if (selected[i] && !canvas.isPinned(i)) {
				x = scalex * (originalPositions[i].x - center.x);
				y = scaley * (originalPositions[i].y - center.y);
				v[i].x = (int) Math.round(x + center.x);
				v[i].y = (int) Math.round(y + center.y);
			    }
			}
			canvas.setPositions(v, selected);
		    }
		    if (liveUpdate) {
			canvas.updateTextureCoords();
		    }
		    canvas.repaint();
		}
		break;
	    case ROTATE_HANDLE:
		Vec2 disp = new Vec2(currentPt.x - click.x, currentPt.y
			- click.y);
		Polygon p = rotationHandle.handle;
		Vec2 vector = new Vec2();
		vector.x = p.xpoints[rotSegment + 1] - p.xpoints[rotSegment];
		vector.y = p.ypoints[rotSegment + 1] - p.ypoints[rotSegment];
		vector.normalize();
		rotAngle = vector.dot(disp) / 100;
		if (ev.isShiftDown()) {
		    rotAngle *= (180.0 / (5 * Math.PI));
		    rotAngle = Math.round(rotAngle);
		    rotAngle *= (5 * Math.PI) / 180;
		}
		double cs = Math.cos(rotAngle);
		double sn = Math.sin(rotAngle);
		double x;
		double y;
		if (window.tensionOn()) {
		    for (int i = 0; i < v.length; i++) {
			if (selected[i] && !canvas.isPinned(i)) {
			    x = cs * (originalPositions[i].x - center.x) + sn
			    * (originalPositions[i].y - center.y);
			    y = -sn * (originalPositions[i].x - center.x) + cs
			    * (originalPositions[i].y - center.y);
			    deltas[i].x = x + center.x - originalPositions[i].x;
			    deltas[i].y = y + center.y - originalPositions[i].y;
			}
			else {
			    deltas[i].x = 0;
			    deltas[i].y = 0;
			}
		    }
		    canvas.adjustDeltas(deltas);
		    for (int i = 0; i < v.length; i++) {
			v[i].x = originalPositions[i].x + (int)Math.round(deltas[i].x);
			v[i].y = originalPositions[i].y + (int)Math.round(deltas[i].y);
		    }
		    canvas.setPositions(v, null);
		} else {
		    for (int i = 0; i < v.length; i++) {
			if (selected[i] && !canvas.isPinned(i)) {
			    x = cs * (originalPositions[i].x - center.x) + sn
			    * (originalPositions[i].y - center.y);
			    y = -sn * (originalPositions[i].x - center.x) + cs
			    * (originalPositions[i].y - center.y);
			    v[i].x = (int)Math.round(x + center.x);
			    v[i].y = (int)Math.round(y + center.y);
			}
			else {
			    v[i].x = originalPositions[i].x;
			    v[i].y = originalPositions[i].y;
			}
		    }
		    canvas.setPositions(v, selected);
		}
		canvas.repaint();
		if (liveUpdate) {
		    canvas.updateTextureCoords();
		}
		break;
	    }
	} else if (dragBox) {
	    Rectangle dragBoxRect = getDragBoxRect(click, currentPt);
	    for (int i = 0; i < v.length; i++) {
		if (dragBoxRect.contains(v[i])) {
		    if ((ev.getModifiers() & WidgetMouseEvent.CTRL_MASK) == 0) {
			selected[i] = true;
		    } else {
			selected[i] = !originalSelection[i];
		    }
		} else if (!ev.isShiftDown() && !ev.isControlDown()) {
		    selected[i] = false;
		} else {
		    selected[i] = originalSelection[i];
		}
	    }
	    checkSelectionNumber(selected);
	    canvas.setSelection(selected, false);
	    computeCenter();
	    canvas.setDragBox(dragBoxRect);
	} else if (scaling) {
	    dy = currentPt.y - click.y;
	    if (dy < -499)
		dy = -499;
	    double scale = 1.0 + dy * 0.002;
	    if (!globalScaling) {
		canvas.setScale(originalScale * scale);
		canvas.repaint();
	    } else {
		Vec2[][] verts = canvas.getMapping().v;
		Vec2 origin = canvas.getOrigin();
		for (int i = 0; i < verts.length; i++) {
		    for (int j = 0; j < verts[i].length; j++) {
			verts[i][j].x = (originalVertices[i][j].x - origin.x)
				* scale + origin.x;
			verts[i][j].y = (originalVertices[i][j].y - origin.y)
				* scale + origin.y;
		    }
		}
		canvas.refreshVerticesPoints();
		canvas.repaint();
	    }
	} else if (moving) {
	    if (!globalMoving) {
		canvas.setOrigin(originalOrigin.x - (currentPt.x - click.x)
			/ originalScale, originalOrigin.y
			+ (currentPt.y - click.y) / originalScale);
		canvas.repaint();
	    } else {
		double deltax = (currentPt.x - click.x) / originalScale;
		double deltay = (currentPt.y - click.y) / originalScale;
		Vec2[][] verts = canvas.getMapping().v;
		for (int i = 0; i < verts.length; i++) {
		    for (int j = 0; j < verts[i].length; j++) {
			verts[i][j].x = originalVertices[i][j].x + deltax;
			verts[i][j].y = originalVertices[i][j].y - deltay;
		    }
		}
		canvas.refreshVerticesPoints();
		canvas.repaint();
	    }
	} else if (draggingHandle) {
	    center.x = originalCenter.x + currentPt.x - click.x;
	    center.y = originalCenter.y + currentPt.y - click.y;
	    snapToVertex();
	    setCenter();
	    canvas.repaint();
	}
    }

    private void moveVertices(Point[] v, int dx, int dy) {
	if (window.tensionOn()) {
	    int[] selectionDist = canvas.getSelectionDistance();
	    int maxDistance = window.getMaxTensionDistance();
	    double tension = window.getTensionValue();
	    Vec2 [] drag = new Vec2[maxDistance+1];
	    drag[0] = new Vec2(dx,dy);
	    for (int i = 1; i <= maxDistance; i++) {
		drag[i] = drag[0].times(Math.pow((maxDistance-i+1.0)/(maxDistance+1.0), tension));
		drag[i].x = (int) Math.round(drag[i].x);
		drag[i].y = (int) Math.round(drag[i].y);
	    }
	    for (int i = 0; i < v.length; i++)
	    {
		if (selectionDist[i] > -1 && !canvas.isPinned(i)) {
		    v[i].x = originalPositions[i].x + (int) drag[selectionDist[i]].x;
		    v[i].y = originalPositions[i].y + (int) drag[selectionDist[i]].y;
		} else {
		    v[i].x = originalPositions[i].x;
		    v[i].y = originalPositions[i].y;
		}
	    }
	    canvas.setPositions(v, null);
	}
	else {
	    boolean[] selected = canvas.getSelection();
	    for (int i = 0; i < v.length; i++)
	    {
		if (selected[i] && !canvas.isPinned(i)) {
		    v[i].x = originalPositions[i].x + dx;
		    v[i].y = originalPositions[i].y + dy;
		} else {
		    v[i].x = originalPositions[i].x;
		    v[i].y = originalPositions[i].y;
		}
	    }
	    canvas.setPositions(v, selected);
	}
	canvas.repaint();
	if (liveUpdate) {
	    canvas.updateTextureCoords();
	}
    }
    
    private void snapToVertex() {
	anchor = -1;
	Point[] v = canvas.getVerticesPoints();
	for (int i = 0; i < v.length; i++) {
	    if (center.x > v[i].x - 3 && center.x < v[i].x + 3
		    && center.y > v[i].y - 3 && center.y < v[i].y + 3) {
		center.x = v[i].x;
		center.y = v[i].y;
		anchor = i;
		break;
	    }
	}
    }

    private void checkSelectionNumber(boolean[] selected) {
	numSel = 0;
	for (int i = 0; i < selected.length; i++) {
	    if (selected[i] && !canvas.isPinned(i)) {
		++numSel;
	    }
	}
    }

    private Rectangle getDragBoxRect(Point p1, Point p2) {
	int x, y, w, h;
	if (p1.x < p2.x) {
	    x = p1.x;
	    w = p2.x - p1.x;
	} else {
	    x = p2.x;
	    w = p1.x - p2.x;
	}
	if (p1.y < p2.y) {
	    y = p1.y;
	    h = p2.y - p1.y;
	} else {
	    y = p2.y;
	    h = p1.y - p2.y;
	}
	return new Rectangle(x, y, w, h);
    }

    public void mouseReleased(WidgetMouseEvent ev) {
	dx = dy = 0;
	draggingHandle = false;
	boolean[] selected = canvas.getSelection();
	if (scaling || moving) {
	    if (globalMoving || globalScaling) {
		UVMappingCanvas.MappingPositionsCommand cmd = canvas.new MappingPositionsCommand(
			originalVertices, null);
		//force vector duplicate for current positions
		cmd.setNewPos(canvas.getMapping().v);
		Range range = canvas.getRange();
		cmd.setOldRange(range.umin, range.umax, range.umin, range.umax);
		cmd.setNewRange(range.umin, range.umax, range.umin, range.umax);
		window.addUndoCommand(cmd);
	    }
	    computeCenter();
	    scaling = moving = false;
	    canvas.enableImageDisplay();
	    canvas.updatePreview();
	    return;
	}
	int[] selChange = canvas.checkSelectionChange(curSelection, selected);
	if (selChange != null) {
	    canvas.getParentDialog().addUndoCommand(
		    canvas.new SelectionCommand(selChange));
	}
	if (dragBox || dragging) {
	    if (dragging) {
		int count = 0;
		Point[] pt = canvas.getVerticesPoints(); 
		for (int i = 0; i < selected.length; i++) {
		    if (pt[i].x != originalPositions[i].x ||
			    pt[i].y != originalPositions[i].y) {
			count++;
		    }

		}
		int[] vertIndices = new int[count];
		Vec2[] undoPositions = new Vec2[count];
		Vec2[] redoPositions = new Vec2[count];
		Vec2[] v = canvas.getMapping().v[canvas.getSelectedPiece()];
		count = 0;
		for (int i = 0; i < selected.length; i++) {
		    if (pt[i].x != originalPositions[i].x ||
			    pt[i].y != originalPositions[i].y) {
			vertIndices[count] = canvas.getTrueIndex(i);
			undoPositions[count] = canvas
				.LayoutToVertex(originalPositions[i]);
			redoPositions[count] = new Vec2(v[vertIndices[count]]);
			count++;
		    }

		}
		window.addUndoCommand(canvas.new DragMappingVerticesCommand(
			vertIndices, undoPositions, redoPositions, canvas
				.getMapping(), canvas.getSelectedPiece()));
	    }
	    canvas.setDragBox(null);
	    dragBox = dragging = false;
	    originalSelection = null;
	    computeCenter();
	    canvas.updatePreview();
	} else {
	    if ((ev.getModifiers() & WidgetMouseEvent.SHIFT_MASK) == 0
		    && (ev.getModifiers() & WidgetMouseEvent.CTRL_MASK) == 0) {
		for (int i = 0; i < selected.length; i++) {
		    selected[i] = false;
		}
		canvas.setSelection(selected);
		computeCenter();
	    }
	}
	canvas.repaint();
	canvas.updateTextureCoords();
    }

    public void selectionUpdated() {
	checkSelectionNumber(canvas.getSelection());
	computeCenter();
    }

    public void mouseScrolled(MouseScrolledEvent ev) {
	int amount = ev.getWheelRotation();
	if (ev.isAltDown())
	    amount *= 10;
	canvas.scale(Math.pow(0.99, amount));
    }

    public void paint(Graphics2D g) {
	if (numSel < 2) {
	    return;
	}
	if (center == null) {
	    return;
	}
	if (scaling || moving) {
	    return;
	}
	g.setColor(Color.orange);
	g.drawLine(center.x + dx, center.y + dy, center.x + dx + axisLength,
		center.y + dy);
	g.drawLine(center.x + dx, center.y + dy, center.x + dx, center.y
		- axisLength + dy);

	g.drawImage(centerhandle, boxes[0].x + dx, boxes[0].y + dy, canvas
		.getComponent());
	g.drawImage(uvHandleImages[0], boxes[1].x + dx, boxes[1].y + dy, canvas
		.getComponent());
	g.drawImage(uvHandleImages[1], boxes[2].x + dx, boxes[2].y + dy, canvas
		.getComponent());
	g.drawImage(uvHandleImages[2], boxes[3].x + dx, boxes[3].y + dy, canvas
		.getComponent());
	g.drawImage(uvHandleImages[1], boxes[4].x + dx, boxes[4].y + dy, canvas
		.getComponent());
	g.drawImage(uvHandleImages[1], boxes[5].x + dx, boxes[5].y + dy, canvas
		.getComponent());

	if (dragging) {
	    rotationHandle.setCenter(new Point(center.x + dx, center.y + dy));
	}
	g.drawPolygon(rotationHandle.handle);
	if (dragging) {
	    g.setColor(Color.darkGray);
	    int sdx;
	    int sdy;
	    switch (handle) {
	    case ROTATE_HANDLE:
		Polygon p = rotationHandle.getRotationFeedback(rotAngle);
		g.drawPolygon(p);
		g.setColor(Color.gray);
		g.fillPolygon(p);
		break;
	    case US_HANDLE:
		sdx = (int) Math.round(axisLength * scalex);
		g.drawLine(center.x, center.y, center.x + sdx, center.y);
		g.drawImage(ghostscale, center.x + sdx + 2 * IMAGE_MARGIN
			+ HANDLE_SIZE, center.y - HANDLE_SIZE / 2, canvas
			.getComponent());
		break;
	    case VS_HANDLE:
		sdy = (int) Math.round(axisLength * scaley);
		g.drawLine(center.x, center.y, center.x, center.y - sdy);
		g.drawImage(ghostscale, center.x - HANDLE_SIZE / 2, center.y
			- sdy - 2 * IMAGE_MARGIN - 2 * HANDLE_SIZE, canvas
			.getComponent());
		break;
	    case UVS_HANDLE:
		sdx = (int) Math.round(axisLength * scalex);
		sdy = (int) Math.round(axisLength * scaley);
		int deltax;
		int deltay;
		double sx = scalex;
		if (sx > 1) {
		    sx = 1;
		} else if (sx < -1) {
		    sx = -1;
		}
		double sy = scaley;
		if (sy > 1) {
		    sy = 1;
		} else if (sy < -1) {
		    sy = -1;
		}
		deltax = (int) Math.round((2 * IMAGE_MARGIN + HANDLE_SIZE) * sx
			- (1 - Math.abs(sy)) * HANDLE_SIZE / 2);
		deltay = (int) Math.round((2 * IMAGE_MARGIN + 2 * HANDLE_SIZE)
			* sy + (1 - Math.abs(sx)) * HANDLE_SIZE / 2);
		g.drawLine(center.x, center.y, center.x + sdx, center.y - sdy);
		g.drawImage(ghostscale, center.x + sdx + deltax, center.y - sdy
			- deltay, canvas.getComponent());
		break;

	    }
	}
    }

    public class UVRotationHandle {
	private int segments;

	protected Color color;

	protected Polygon handle;

	protected Point center;

	/**
	 * Creates a 2D Rotation Handle with a given number of segments
	 * 
	 * @param segments
	 *                The number of segments that describe the rotation
	 *                circle
	 */
	public UVRotationHandle(int segments, Point center, Color color) {
	    this.segments = segments;
	    this.color = color;
	    setCenter(center);
	}

	/**
	 * @return the center
	 */
	public Point getCenter() {
	    return center;
	}

	/**
	 * @param center
	 *                The center to set
	 */
	public void setCenter(Point center) {
	    this.center = center;
	    int[] px = new int[segments + 1];
	    int[] py = new int[segments + 1];
	    double delta = 2 * Math.PI / segments;
	    double cs = Math.cos(delta);
	    double sn = Math.sin(delta);
	    double x, y, nx, ny;
	    px[0] = center.x + axisLength;
	    py[0] = center.y;
	    x = axisLength;
	    y = 0;
	    for (int i = 1; i < segments; i++) {
		nx = x * cs + y * sn;
		ny = -x * sn + y * cs;
		x = nx;
		y = ny;
		px[i] = (int) Math.round(x + center.x);
		py[i] = (int) Math.round(y + center.y);
	    }
	    px[segments] = center.x + axisLength;
	    py[segments] = center.y;
	    handle = new Polygon(px, py, segments + 1);
	}

	/**
	 * Given an angle, this method returns a 2D polygon which can be used to
	 * tell the user the rotation amount when drawn on the canvas
	 * 
	 * @param angle
	 * @return The polygon
	 */

	public Polygon getRotationFeedback(double angle) {
	    int[] px = new int[segments + 1];
	    int[] py = new int[segments + 1];
	    double delta = angle / (segments - 1);
	    double cs = Math.cos(delta);
	    double sn = Math.sin(delta);
	    double x, y, nx, ny;
	    px[0] = center.x;
	    py[0] = center.y;
	    px[1] = center.x + axisLength;
	    py[1] = center.y;
	    x = axisLength;
	    y = 0;
	    for (int i = 2; i < segments + 1; i++) {
		nx = x * cs + y * sn;
		ny = -x * sn + y * cs;
		x = nx;
		y = ny;
		px[i] = (int) Math.round(x + center.x);
		py[i] = (int) Math.round(y + center.y);
	    }
	    return new Polygon(px, py, segments + 1);
	}

	/**
	 * This method tells if the mouse has been been clicked on a rotation
	 * handle
	 * 
	 * @param pos
	 *                The point where the mouse was clicked
	 * @return The number of the segment being clicked on or -1 if the mouse
	 *         has not been clicked on the handle
	 */
	public int findClickTarget(Point pos) {
	    double u, v, w;
	    double maxdist = Double.MAX_VALUE;
	    int which = -1;
	    for (int i = 0; i < handle.npoints - 1; i++) {
		Vec2 v1 = new Vec2(handle.xpoints[i], handle.ypoints[i]);
		Vec2 v2 = new Vec2(handle.xpoints[i + 1], handle.ypoints[i + 1]);
		if ((pos.x < v1.x - HANDLE_SIZE / 4 && pos.x < v2.x
			- HANDLE_SIZE / 4)
			|| (pos.x > v1.x + HANDLE_SIZE / 4 && pos.x > v2.x
				+ HANDLE_SIZE / 4)
			|| (pos.y < v1.y - HANDLE_SIZE / 4 && pos.y < v2.y
				- HANDLE_SIZE / 4)
			|| (pos.y > v1.y + HANDLE_SIZE / 4 && pos.y > v2.y
				+ HANDLE_SIZE / 4))
		    continue;

		// Determine the distance of the click point from the line.

		if (Math.abs(v1.x - v2.x) > Math.abs(v1.y - v2.y)) {
		    if (v2.x > v1.x) {
			v = ((double) pos.x - v1.x) / (v2.x - v1.x);
			u = 1.0 - v;
		    } else {
			u = ((double) pos.x - v2.x) / (v1.x - v2.x);
			v = 1.0 - u;
		    }
		    w = u * v1.y + v * v2.y - pos.y;
		} else {
		    if (v2.y > v1.y) {
			v = ((double) pos.y - v1.y) / (v2.y - v1.y);
			u = 1.0 - v;
		    } else {
			u = ((double) pos.y - v2.y) / (v1.y - v2.y);
			v = 1.0 - u;
		    }
		    w = u * v1.x + v * v2.x - pos.x;
		}
		if (Math.abs(w) > HANDLE_SIZE / 2)
		    continue;
		if (w < maxdist) {
		    maxdist = w;
		    which = i;
		}
	    }
	    return which;
	}
    }

    public boolean isLiveUpdate() {
	return liveUpdate;
    }

    public void setLiveUpdate(boolean liveUpdate) {
	this.liveUpdate = liveUpdate;
    }
}
