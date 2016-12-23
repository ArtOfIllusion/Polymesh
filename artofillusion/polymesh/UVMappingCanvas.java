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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;

import artofillusion.TextureParameter;
import artofillusion.math.BoundingBox;
import artofillusion.math.Vec2;
import artofillusion.math.Vec3;
import artofillusion.object.FacetedMesh;
import artofillusion.object.Mesh;
import artofillusion.object.MeshVertex;
import artofillusion.object.TriangleMesh;
import artofillusion.polymesh.UVMappingData.UVMeshMapping;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedEdge;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedFace;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedVertex;
import artofillusion.texture.Texture;
import artofillusion.texture.Texture2D;
import artofillusion.texture.UVMapping;
import buoy.event.RepaintEvent;
import buoy.widget.BScrollPane;
import buoy.widget.CustomWidget;

/**
 * This canvas displays several mesh pieces over a bitmap image. The goal is to
 * define UV mapping of meshes as the location of the mesh vertices over the
 * background image. Editing tools allow to move, rotate, resize meshes.
 * 
 * @author Francois Guillet
 * 
 */
public class UVMappingCanvas extends CustomWidget {

	/**
	 * Undo/Redo command for whole set of mesh pieces vertices change
	 * 
	 */
	public class MappingPositionsCommand implements Command {

		private Vec2[][] oldPos;

		private Vec2[][] newPos;

		private double oldUmin, oldUmax, oldVmin, oldVmax;

		private double newUmin, newUmax, newVmin, newVmax;

		public MappingPositionsCommand() {
			oldPos = newPos = null;
		}

		public MappingPositionsCommand(Vec2[][] oldPos, Vec2[][] newPos) {
			super();
			this.oldPos = oldPos;
			this.newPos = newPos;
		}

		public void setOldRange(double oldUmin, double oldUmax, double oldVmin,
				double oldVmax) {
			this.oldUmin = oldUmin;
			this.oldUmax = oldUmax;
			this.oldVmin = oldVmin;
			this.oldVmax = oldVmax;
		}

		public void setNewRange(double newUmin, double newUmax, double newVmin,
				double newVmax) {
			this.newUmin = newUmin;
			this.newUmax = newUmax;
			this.newVmin = newVmin;
			this.newVmax = newVmax;
		}

		/**
		 * @return the newPos
		 */
		public Vec2[][] getNewPos() {
			return newPos;
		}

		/**
		 * @param newPos
		 *            the newPos to set
		 */
		public void setNewPos(Vec2[][] newPos) {
			this.newPos = new Vec2[newPos.length][];
			for (int i = 0; i < newPos.length; i++) {
				this.newPos[i] = new Vec2[newPos[i].length];
				for (int j = 0; j < newPos[i].length; j++) {
					this.newPos[i][j] = new Vec2(newPos[i][j]);
				}
			}
		}

		/**
		 * @return the oldPos
		 */
		public Vec2[][] getOldPos() {
			return oldPos;
		}

		/**
		 * @param oldPos
		 *            the oldPos to set
		 */
		public void setOldPos(Vec2[][] oldPos) {
			this.oldPos = new Vec2[oldPos.length][];
			for (int i = 0; i < oldPos.length; i++) {
				this.oldPos[i] = new Vec2[oldPos[i].length];
				for (int j = 0; j < oldPos[i].length; j++) {
					this.oldPos[i][j] = new Vec2(oldPos[i][j]);
				}
			}
		}

		public void execute() {
			redo();
		}

		public void redo() {
			for (int i = 0; i < mapping.v.length; i++) {
				for (int j = 0; j < mapping.v[i].length; j++) {
					mapping.v[i][j] = new Vec2(newPos[i][j]);
				}
			}
			setRange(newUmin, newUmax, newVmin, newVmax);
			manipulator.selectionUpdated();
			repaint();
		}

		public void undo() {
			for (int i = 0; i < mapping.v.length; i++) {
				for (int j = 0; j < mapping.v[i].length; j++) {
					mapping.v[i][j] = new Vec2(oldPos[i][j]);
				}
			}
			setRange(oldUmin, oldUmax, oldVmin, oldVmax);
			manipulator.selectionUpdated();
			repaint();
		}

	}

	/**
	 * Undo/Redo command for pinning vertices
	 * 
	 */
	public class PinCommand implements Command {

		public int[] selection;

		public PinCommand(int[] selection) {
			super();
			this.selection = selection;
		}

		public void execute() {
			redo();
		}

		public void redo() {
			for (int i = 0; i < selection.length; i++) {
				mappingData.meshes[currentPiece].vertices[mappingData.verticesTable[currentPiece][selection[i]]].pinned = !mappingData.meshes[currentPiece].vertices[mappingData.verticesTable[currentPiece][selection[i]]].pinned;
			}
			repaint();
		}

		public void undo() {
			redo();
		}

	}

	/**
	 * Undo/Redo command for selected vertices
	 * 
	 */
	public class SelectionCommand implements Command {

		private int[] selection;

		public SelectionCommand(int[] selection) {
			super();
			this.selection = selection;
		}

		public void execute() {
			redo();
		}

		public void redo() {
			for (int i = 0; i < selection.length; i++) {
				selected[selection[i]] = !selected[selection[i]];
			}
			manipulator.selectionUpdated();
			repaint();
		}

		public void undo() {
			redo();
		}

	}

	/**
	 * Undo/Redo command for dragged vertices
	 * 
	 * @author Francois Guillet
	 * 
	 */
	public class DragMappingVerticesCommand implements Command {

		private int[] vertIndices;

		private Vec2[] undoPositions;

		private Vec2[] redoPositions;

		private UVMeshMapping mapping;

		private int piece;

		/**
		 * Creates a DragMappingVerticesCommand
		 * 
		 * @param vertIndexes
		 *            The indexes of vertices to move
		 * @param undoPositions
		 *            The original positions
		 * @param redoPositions
		 *            The positions to move to
		 */

		public DragMappingVerticesCommand(int[] vertIndexes,
				Vec2[] undoPositions, Vec2[] redoPositions,
				UVMeshMapping mapping, int piece) {
			super();
			this.vertIndices = vertIndexes;
			this.undoPositions = undoPositions;
			this.redoPositions = redoPositions;
			this.mapping = mapping;
			this.piece = piece;
		}

		public void execute() {
			redo();

		}

		public void redo() {
			Vec2[] v = mapping.v[piece];
			for (int i = 0; i < vertIndices.length; i++) {
				v[vertIndices[i]] = new Vec2(redoPositions[i]);
			}
			refreshVerticesPoints();
			manipulator.selectionUpdated();
			repaint();
		}

		public void undo() {
			Vec2[] v = mapping.v[piece];
			for (int i = 0; i < vertIndices.length; i++) {
				v[vertIndices[i]] = new Vec2(undoPositions[i]);
			}
			refreshVerticesPoints();
			manipulator.selectionUpdated();
			repaint();
		}
	}

	public class Range {
		public double umin;

		public double umax;

		public double vmin;

		public double vmax;
	}

	private Dimension size; // widget size

	private Dimension oldSize; // old widget size

	// used to track size change

	private UnfoldedMesh[] meshes; // the mesh pieces to display

	private boolean[] selected;

	private int[] selectionDistance;

	private int currentPiece; // only one piece can be selected for edition

	private UVMappingEditorDialog parent;

	private Point[] verticesPoints; // vertices locations

	// only vertices with an id != -1 are displayed

	private UVMappingData.UVMeshMapping mapping; // current mapping

	private Rectangle dragBoxRect;

	private UVMappingManipulator manipulator;

	private UVMappingData mappingData;

	private Vec2 origin;

	private double scale;

	private double umin, umax, vmin, vmax;

	private Image textureImage;

	private int component;

	private boolean disableImageDisplay;

	private MeshPreviewer preview;

	private int[][] vertIndexes;

	private int[][] vertMeshes;

	private Texture texture;

	private UVMapping texMapping;

	private boolean boldEdges;

	private final static Stroke normal = new BasicStroke();

	private final static Stroke bold = new BasicStroke(2.0f);

	private final static Dimension minSize = new Dimension(512, 512);

	private final static Dimension maxSize = new Dimension(5000, 5000);

	private final static Color unselectedColor = new Color(0, 180, 0);

	private final static Color selectedColor = Color.red;

	private final static Color pinnedColor = new Color(182, 0, 185);

	private final static Color pinnedSelectedColor = new Color(255, 142, 255);

	public UVMappingCanvas(UVMappingEditorDialog window,
			UVMappingData mappingData, MeshPreviewer preview, Texture texture,
			UVMapping texMapping) {
		super();
		parent = window;
		this.preview = preview;
		this.texture = texture;
		this.texMapping = texMapping;
		this.mapping = mappingData.mappings.get(0);
		setBackground(Color.white);
		size = new Dimension(512, 512);
		oldSize = new Dimension(0, 0);
		origin = new Vec2();
		this.mappingData = mappingData;
		meshes = mappingData.getMeshes();
		boldEdges = true;
		addEventLink(RepaintEvent.class, this, "doRepaint");
		if (meshes == null)
			return;
		currentPiece = 0;
		component = 0;
		resetMeshLayout();
		createImage();
		setSelectedPiece(0);
		initializeTexCoordsIndex();
		updateTextureCoords();
		// repaint();
	}

	public boolean isBoldEdges() {
		return boldEdges;
	}

	public void setBoldEdges(boolean boldEdges) {
		this.boldEdges = boldEdges;
		repaint();
	}

	/**
	 * @return the current mesh mapping
	 */
	public UVMappingData.UVMeshMapping getMapping() {
		return mapping;
	}

	/**
	 * @param mapping
	 *            The mapping to set
	 */
	public void setMapping(UVMappingData.UVMeshMapping mapping) {
		clearSelection();
		this.mapping = mapping;
		resetMeshLayout();
		update();
	}

	/**
	 * @return the current texture mapping
	 */
	public UVMapping getTexMapping() {
		return texMapping;
	}

	private void update() {
		createImage();

		updateTextureCoords();
		clearSelection();
		repaint();
		preview.render();
	}

	/**
	 * @return the current texture
	 */
	public Texture getTexture() {
		return texture;
	}

	/**
	 * @param texture
	 *            The texture to set
	 * @param texMapping
	 *            the texture mapping to set
	 */
	public void setTexture(Texture texture, UVMapping texMapping) {
		this.texture = texture;
		this.texMapping = texMapping;
		update();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see buoy.widget.Widget#getPreferredSize()
	 */
	/**
	 * The widget dimension adapts itself to the current size except if it's too
	 * small
	 */
	@Override
	public Dimension getPreferredSize() {
		Dimension viewSize = ((BScrollPane) getParent()).getComponent()
				.getSize();
		size.width = viewSize.width;
		size.height = viewSize.height;
		if (size.width < minSize.width) {
			size.width = minSize.width;
		} else if (size.width > maxSize.width) {
			size.width = maxSize.width;
		}
		if (size.height < minSize.height) {
			size.height = minSize.height;
		} else if (size.height > maxSize.height) {
			size.height = maxSize.height;
		}
		return size;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see buoy.widget.Widget#getMinimumSize()
	 */
	@Override
	public Dimension getMinimumSize() {
		return minSize;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see buoy.widget.Widget#getMaximumSize()
	 */
	@Override
	public Dimension getMaximumSize() {
		return maxSize;
	}

	@SuppressWarnings("unused")
	private void doRepaint(RepaintEvent evt) {
		Graphics2D g = evt.getGraphics();
		doPaint(g, false);
	}

	/**
	 * Draws the mesh pieces, either in the current canvas or for export
	 * purposes
	 * 
	 * @param g
	 *            The graphics to draw on
	 * @param export
	 *            True if it's for export purposes
	 */
	private void doPaint(Graphics2D g, boolean export) {
		if (meshes == null)
			return;
		if (oldSize.width != size.width || oldSize.height != size.height) {
			vmax = origin.y + (size.height ) / (2 * scale);
			vmin = origin.y - (size.height ) / (2 * scale);
			umax = origin.x + (size.width ) / (2 * scale);
			umin = origin.x - (size.width ) / (2 * scale);
			parent.displayUVMinMax(umin, umax, vmin, vmax);
			createImage();
			refreshVerticesPoints();
			oldSize = new Dimension(size);
		}
		if (textureImage != null) {
			g.drawImage(textureImage, 0, 0, null);
		}
		for (int i = 0; i < meshes.length; i++) {
			UnfoldedMesh mesh = meshes[i];
			Vec2[] v = mapping.v[i];
			UnfoldedEdge[] e = mesh.getEdges();
			Point p1;
			Point p2;
			if (export || currentPiece == i) {
				g.setColor(mapping.edgeColor);
				if (boldEdges) {
					g.setStroke(bold);
				}
			} else {
				g.setColor(Color.gray);
				g.setStroke(normal);
			}
			for (int j = 0; j < e.length; j++) {
				if (e[j].hidden) {
					continue;
				}
				p1 = VertexToLayout(v[e[j].v1]);
				p2 = VertexToLayout(v[e[j].v2]);
				g.drawLine(p1.x, p1.y, p2.x, p2.y);
			}
		}
		g.setStroke(normal);
		if (!export) {
			for (int i = 0; i < verticesPoints.length; i++) {
				if (selected[i]) {
					if (mappingData.meshes[currentPiece].vertices[mappingData.verticesTable[currentPiece][i]].pinned) {
						g.setColor(pinnedSelectedColor);
					} else {
						g.setColor(selectedColor);
					}
					g.drawOval(verticesPoints[i].x - 3,
							verticesPoints[i].y - 3, 6, 6);
				} else {
					if (mappingData.meshes[currentPiece].vertices[mappingData.verticesTable[currentPiece][i]].pinned) {
						g.setColor(pinnedColor);
					} else {
						g.setColor(unselectedColor);
					}
					g.fillOval(verticesPoints[i].x - 3,
							verticesPoints[i].y - 3, 6, 6);
				}
			}
			if (dragBoxRect != null) {
				g.setColor(Color.black);
				g.drawRect(dragBoxRect.x, dragBoxRect.y, dragBoxRect.width,
						dragBoxRect.height);
			}
			if (manipulator != null)
				manipulator.paint(g);
		}
	}

	/**
	 * Computes default range from mesh sizes
	 * 
	 */
	public void resetMeshLayout() {
		vmin = umin = Double.MAX_VALUE;
		vmax = umax = -Double.MAX_VALUE;
		Vec2[] v;
		UnfoldedVertex[] vert;
		for (int i = 0; i < meshes.length; i++) {
			v = mapping.v[i];
			vert = meshes[i].vertices;
			for (int j = 0; j < v.length; j++) {
				if (vert[j].id == -1) {
					continue;
				}
				if (v[j].x < umin) {
					umin = v[j].x;
				} else if (v[j].x > umax) {
					umax = v[j].x;
				}
				if (v[j].y < vmin) {
					vmin = v[j].y;
				} else if (v[j].y > vmax) {
					vmax = v[j].y;
				}
			}
		}
		double deltau = umax - umin;
		double deltav = vmax - vmin;
		umin -= 0.05 * deltau;
		vmin -= 0.05 * deltav;
		umax += 0.05 * deltau;
		vmax += 0.05 * deltav;
		setRange(umin, umax, vmin, vmax);
	}

	/**
	 * Sets the displayed UV range
	 * 
	 * @param umin
	 *            Low U limit
	 * @param umax
	 *            High U limit
	 * @param vmin
	 *            Low V Limit
	 * @param vmax
	 *            Hich V Limit
	 */
	public void setRange(double umin, double umax, double vmin, double vmax) {
		this.umin = umin;
		this.umax = umax;
		this.vmin = vmin;
		this.vmax = vmax;
		scale = ((double) (size.width)) / (umax - umin);
		double scaley = ((double) (size.height)) / (vmax - vmin);
		if (scaley < scale)
			scale = scaley;
		origin.x = (umax + umin) / 2;
		origin.y = (vmax + vmin) / 2;
		this.vmax = origin.y + (size.height ) / (2 * scale);
		this.vmin = origin.y - (size.height ) / (2 * scale);
		this.umax = origin.x + (size.width ) / (2 * scale);
		this.umin = origin.x - (size.width ) / (2 * scale);
		createImage();
		refreshVerticesPoints();
		parent.displayUVMinMax(this.umin, this.umax, this.vmin, this.vmax);
	}

	@SuppressWarnings("unused")
	// for debugging purposes
	private void dumpParams() {
		System.out.println("setRange size : " + size);
		System.out.println("scale : " + scale);
		System.out.println(umin + " " + umax + " " + vmin + " " + vmax);
		System.out.println(origin.x + " " + origin.y);
	}

	public Range getRange() {
		Range range = new Range();
		range.umin = umin;
		range.umax = umax;
		range.vmin = vmin;
		range.vmax = vmax;
		return range;
	}

	/**
	 * Recomputes mesh vertices positions whenever origin or scaling has changed
	 * 
	 */
	public void refreshVerticesPoints() {
		Vec2[] v = mapping.v[currentPiece];
		int count = mappingData.displayed[currentPiece];
		if (verticesPoints == null || verticesPoints.length != count) {
			verticesPoints = new Point[count];
		}
		for (int j = 0; j < count; j++) {
			verticesPoints[j] = VertexToLayout(v[mappingData.verticesTable[currentPiece][j]]);
		}
	}

	/**
	 * This method updates the preview when the selection has been changed
	 * 
	 */
	public void updatePreview() {
		Mesh mesh = (Mesh) preview.getObject().object;
		MeshVertex[] vert = mesh.getVertices();
		UnfoldedMesh umesh = meshes[currentPiece];
		UnfoldedVertex[] uvert = umesh.getVertices();
		boolean[] meshSel = new boolean[vert.length];
		for (int i = 0; i < selected.length; i++) {
			if (selected[i]) {
				meshSel[uvert[mappingData.verticesTable[currentPiece][i]].id] = true;
			}
		}
		preview.setVertexSelection(meshSel);
		preview.render();
	}

	/**
	 * @return the vertex selection
	 */
	public boolean[] getSelection() {
		return selected;
	}

	public void setSelection(boolean[] selected) {
		setSelection(selected, true);
	}

	/**
	 * Sets the selected vertices
	 * 
	 * @param selected
	 */
	public void setSelection(boolean[] selected, boolean render) {

		if (selected.length == verticesPoints.length) {
			int[] selChange = checkSelectionChange(this.selected, selected);
			if (selChange != null) {
				parent.addUndoCommand(new SelectionCommand(selChange));
			}
			this.selected = selected;
			if (render) {
				updatePreview();
			} else {
				preview.clearVertexSelection();
			}
			if (parent.tensionOn()) {
				findSelectionDistance();
			}
			repaint();
		}
	}

	/**
	 * Computes the difference vetween to vertex selections
	 * 
	 * @param sel1
	 * @param sel2
	 * @return An array describing the selection differences
	 */
	public int[] checkSelectionChange(boolean[] sel1, boolean[] sel2) {
		if (sel1.length != sel2.length) {
			return null;
		}
		int count = 0;
		for (int i = 0; i < sel1.length; i++) {
			if (sel1[i] != sel2[i]) {
				count++;
			}
		}
		if (count != 0) {
			int[] selChange = new int[count];
			count = 0;
			for (int i = 0; i < sel1.length; i++) {
				if (sel1[i] != sel2[i]) {
					selChange[count] = i;
					count++;
				}
			}
			return selChange;

		} else {
			return null;
		}
	}

	public void clearSelection() {
		int count = 0;
		for (int i = 0; i < selected.length; i++) {
			if (selected[i]) {
				count++;
			}
		}
		int[] selChange = null;
		if (count > 0) {
			selChange = new int[count];
			count = 0;
			for (int i = 0; i < selected.length; i++) {
				if (selected[i]) {
					selChange[count] = i;
					selected[i] = false;
					count++;
				}
			}
			parent.addUndoCommand(new SelectionCommand(selChange));
		}
		preview.clearVertexSelection();
	}

	/**
	 * @return the selected piece index
	 */
	public int getSelectedPiece() {
		return currentPiece;
	}

	/**
	 * Sets the piece selected for edition
	 * 
	 * @param currentPiece
	 *            the piece currently selected
	 */
	public void setSelectedPiece(int currentPiece) {
		this.currentPiece = currentPiece;
		refreshVerticesPoints();
		selected = new boolean[verticesPoints.length];
		repaint();
	}

	/**
	 * @return the vertices 2D points on canvas (only for displayed vertices)
	 */
	public Point[] getVerticesPoints() {
		return verticesPoints;
	}

	/**
	 * Sets the current drag box and repaints the mesh. Set the drag box to null
	 * in order to stop the drag box display.
	 * 
	 * @param dragBoxRect
	 *            The drag box to display
	 */
	public void setDragBox(Rectangle dragBoxRect) {
		this.dragBoxRect = dragBoxRect;
		repaint();
	}

	/**
	 * Sets the manipulator that manipulates mesh pieces
	 * 
	 * @param manipulator
	 */
	public void setManipulator(UVMappingManipulator manipulator) {
		this.manipulator = manipulator;
	}

	/**
	 * Sets the positions of vertices relative to view window (not to UV
	 * values). If mask is not null, change is applied only for points for which
	 * mask is true.
	 * 
	 * @param newPos
	 * @param mask
	 */
	public void setPositions(Point[] newPos, boolean[] mask) {
		Vec2[] v = mapping.v[currentPiece];
		for (int i = 0; i < newPos.length; i++) {
			if (mask == null || mask[i]) {
				LayoutToVertex(v[mappingData.verticesTable[currentPiece][i]], newPos[i]);
			}
		}

	}

	/**
	 * This function returns the rectangle that encloses the mesh, taking into
	 * accout mesh origin, orientation and scale
	 * 
	 * @return mesh bounds
	 */
	public BoundingBox getBounds(UnfoldedMesh mesh) {
		int xmin, xmax, ymin, ymax;
		xmax = ymax = Integer.MIN_VALUE;
		xmin = ymin = Integer.MAX_VALUE;
		Point p;
		Vec2[] v = mapping.v[currentPiece];
		for (int i = 0; i < v.length; i++) {
			p = VertexToLayout(v[i]);
			if (xmax < p.x)
				xmax = p.x;
			if (xmin > p.x)
				xmin = p.x;
			if (ymax < p.y)
				ymax = p.y;
			if (ymin > p.y)
				ymin = p.y;
		}
		BoundingBox b = new BoundingBox(xmin, xmax, ymin, ymax, 0, 0);
		return b;
	}

	/**
	 * Computes the position of a vertex on the layout
	 * 
	 * @param r
	 *            The vertex position
	 * @return The position on the layout
	 */
	public Point VertexToLayout(Vec2 r) {
		Point p = new Point();
		p.x = (int) Math.round((r.x - origin.x) * scale);
		p.y = (int) Math.round((r.y - origin.y) * scale);
		p.x += size.width / 2;
		p.y = size.height / 2 - p.y;
		return p;
	}

	/**
	 * Computes the position of a vertex given its position on the layout
	 * 
	 * @param p
	 *            The new vertex position
	 * @param index
	 *            The vertex index
	 */
	public void LayoutToVertex(Vec2 v, Point p) {
		v.x = (p.x - size.width / 2) / scale + origin.x;
		v.y = (size.height / 2 - p.y) / scale + origin.y;
	}

	public Vec2 LayoutToVertex(Point p) {
		Vec2 v = new Vec2();
		v.x = (p.x - size.width / 2) / scale + origin.x;
		v.y = (size.height / 2 - p.y) / scale + origin.y;
		return v;
	}

	public void setComponent(int component) {
		this.component = component;
		createImage();
		repaint();
	}

	/** Recalculate the texture image. */

	private void createImage() {
		textureImage = null;
		if (disableImageDisplay) {
			return;
		}
		if (texture == null)
			return;
		int sampling = mappingData.sampling;
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		double uoffset = 0; // 0.5 * sampling * (umax - umin) / size.width;
		double voffset = 0; // 0.5 * sampling * (vmax - vmin) / size.height;
		TextureParameter param[] = texMapping.getParameters();
		double paramVal[] = null;
		if (param != null) {
			paramVal = new double[param.length];
			for (int i = 0; i < param.length; i++)
				paramVal[i] = param[i].defaultVal;
		}
		textureImage = ((Texture2D) texture.duplicate()).createComponentImage(
				umin + uoffset, umax + uoffset, vmin - voffset, vmax - voffset,
				size.width / sampling, size.height / sampling, component, 0.0,
				paramVal);
		if (sampling > 1)
			textureImage = textureImage.getScaledInstance(size.width,
					size.height, Image.SCALE_SMOOTH);
		setCursor(Cursor.getDefaultCursor());
	}

	/**
	 * Sets the texture resolution
	 * 
	 * @param sampling
	 */
	public void setSampling(int sampling) {
		mappingData.sampling = sampling;
		createImage();
		repaint();
	}

	/**
	 * Returns the current texture resolution
	 * 
	 * @return Sampling
	 */
	public int getSampling() {
		return mappingData.sampling;
	}

	/**
	 * Scales the selected piece
	 * 
	 * @param sc
	 */
	public void scale(double sc) {
		umin = sc * (umin - origin.x) + origin.x;
		umax = sc * (umax - origin.x) + origin.x;
		vmin = sc * (vmin - origin.y) + origin.y;
		vmax = sc * (vmax - origin.y) + origin.y;
		scale /= sc;
		createImage();
		refreshVerticesPoints();
		repaint();
		parent.displayUVMinMax(umin, umax, vmin, vmax);
	}

	/**
	 * Disables texture display in order to speed up operations
	 * 
	 */
	public void disableImageDisplay() {
		disableImageDisplay = true;
		textureImage = null;
	}

	/**
	 * Enables image display
	 * 
	 */
	public void enableImageDisplay() {
		disableImageDisplay = false;
		createImage();
		repaint();
	}

	/**
	 * @return the scale
	 */
	public double getScale() {
		return scale;
	}

	/**
	 * @param scale
	 *            the scale to set
	 */
	public void setScale(double sc) {
		double f = scale / sc;
		umin = f * (umin - origin.x) + origin.x;
		umax = f * (umax - origin.x) + origin.x;
		vmin = f * (vmin - origin.y) + origin.y;
		vmax = f * (vmax - origin.y) + origin.y;
		scale = sc;
		createImage();
		refreshVerticesPoints();
		repaint();
		parent.displayUVMinMax(umin, umax, vmin, vmax);
	}

	/**
	 * Displaces displayed uv range
	 * 
	 * @param du
	 *            Shift along U
	 * @param dv
	 *            Shift along V
	 */
	public void moveOrigin(double du, double dv) {
		umin += du;
		umax += du;
		vmin += dv;
		vmax += dv;
		origin.x += du;
		origin.y += dv;
		createImage();
		refreshVerticesPoints();
		repaint();
		parent.displayUVMinMax(umin, umax, vmin, vmax);
	}

	/**
	 * @return the origin
	 */
	public Vec2 getOrigin() {
		return new Vec2(origin);
	}

	/**
	 * @param origin
	 *            the origin to set
	 */
	public void setOrigin(Vec2 origin) {
		setOrigin(origin.x, origin.y);
	}

	/**
	 * @param origin
	 *            the origin to set
	 */
	public void setOrigin(double u, double v) {
		moveOrigin(u - origin.x, v - origin.y);
	}

	/**
	 * Sets texture coordinate indices for later use
	 */
	public void initializeTexCoordsIndex() {
		FacetedMesh mesh = (FacetedMesh) preview.getObject().object;
		int[][] texCoordIndex = new int[mesh.getFaceCount()][];
		vertIndexes = new int[texCoordIndex.length][];
		vertMeshes = new int[texCoordIndex.length][];
		int n;
		for (int i = 0; i < texCoordIndex.length; i++) {
			n = mesh.getFaceVertexCount(i);
			texCoordIndex[i] = new int[n];
			vertIndexes[i] = new int[n];
			vertMeshes[i] = new int[n];
			for (int j = 0; j < texCoordIndex[i].length; j++) {
				texCoordIndex[i][j] = mesh.getFaceVertexIndex(i, j);
				vertIndexes[i][j] = -1;
				vertMeshes[i][j] = -1;
			}
		}
		UnfoldedFace[] f;
		UnfoldedVertex[] v;
		int count = 0;
		for (int i = 0; i < meshes.length; i++) {
			v = meshes[i].getVertices();
			count += v.length;
		}
		count = 0;
		for (int i = 0; i < meshes.length; i++) {
			f = meshes[i].getFaces();
			v = meshes[i].getVertices();
			for (int j = 0; j < f.length; j++) {
				if (f[j].id >= 0 && f[j].id < texCoordIndex.length) {
					for (int k = 0; k < texCoordIndex[f[j].id].length; k++) {
						if (f[j].v1 >= 0
								&& v[f[j].v1].id == texCoordIndex[f[j].id][k]) {
							vertIndexes[f[j].id][k] = f[j].v1;
							vertMeshes[f[j].id][k] = i;
						}
						if (f[j].v2 >= 0
								&& v[f[j].v2].id == texCoordIndex[f[j].id][k]) {
							vertIndexes[f[j].id][k] = f[j].v2;
							vertMeshes[f[j].id][k] = i;
						}
						if (f[j].v3 >= 0
								&& v[f[j].v3].id == texCoordIndex[f[j].id][k]) {
							vertIndexes[f[j].id][k] = f[j].v3;
							vertMeshes[f[j].id][k] = i;
						}
					}
				}
			}
			count += v.length;
		}
	}

	/**
	 * Updates texture coordinates to reflect the mapping
	 * 
	 */
	public void updateTextureCoords() {

		if (texture == null)
			return;
		FacetedMesh mesh = (FacetedMesh) preview.getObject().object;
		Vec2 texCoord[][] = new Vec2[mesh.getFaceCount()][];
		for (int i = 0; i < texCoord.length; i++) {
			texCoord[i] = new Vec2[mesh.getFaceVertexCount(i)];
			for (int j = 0; j < texCoord[i].length; j++) {
				texCoord[i][j] = new Vec2(
						mapping.v[vertMeshes[i][j]][vertIndexes[i][j]]);
			}
		}
		texMapping.setFaceTextureCoordinates(preview.getObject().object,
				texCoord);
		preview.render();
	}

	public void selectAll() {
		for (int i = 0; i < verticesPoints.length; i++) {
			selected[i] = true;
		}
		setSelection(selected);
		manipulator.selectionUpdated();
	}

	public UnfoldedMesh[] getMeshes() {
		return meshes;
	}

	/**
	 * Offscreen drawing for exporting images
	 * 
	 * @param g
	 *            The graphics to draw onto
	 * @param width
	 *            Width of the image to draw
	 * @param height
	 *            Height of the image to draw
	 */
	public void drawOnto(Graphics2D g, int width, int height) {
		g.setColor(Color.white);
		g.fillRect(0, 0, width, height);
		g.setColor(Color.black);
		double oldScale = scale;
		double oldUmin = umin;
		double oldUmax = umax;
		double oldVmin = vmin;
		double oldVmax = vmax;
		Vec2 oldOrigin = new Vec2(origin);
		Dimension tmpSize = size;
		Dimension tmpOldSize = oldSize;
		oldSize = size = new Dimension(width, height);
		Image oldTextureImage = textureImage;

		textureImage = null;
		scale = ((double) (width)) / (umax - umin);
		double scaley = ((double) (height)) / (vmax - vmin);
		if (scaley < scale)
			scale = scaley;
		origin.x = (umax + umin) / 2;
		origin.y = (vmax + vmin) / 2;
		vmax = origin.y + height / (2 * scale);
		vmin = origin.y - height / (2 * scale);
		umax = origin.x + width / (2 * scale);
		umin = origin.x - width / (2 * scale);
		refreshVerticesPoints();
		doPaint(g, true);
		textureImage = oldTextureImage;
		scale = oldScale;
		umin = oldUmin;
		umax = oldUmax;
		vmin = oldVmin;
		vmax = oldVmax;
		origin = new Vec2(oldOrigin);
		size = tmpSize;
		oldSize = tmpOldSize;
		refreshVerticesPoints();
	}

	public void pinSelection(boolean state) {
		int count = 0;
		for (int i = 0; i < verticesPoints.length; i++) {
			if (selected[i]
					&& mappingData.meshes[currentPiece].vertices[mappingData.verticesTable[currentPiece][i]].pinned != state) {
				count++;
			}
		}
		if (count != 0) {
			int[] pinChange = new int[count];
			count = 0;
			for (int i = 0; i < verticesPoints.length; i++) {
				if (selected[i]
						&& mappingData.meshes[currentPiece].vertices[mappingData.verticesTable[currentPiece][i]].pinned != state) {
					mappingData.meshes[currentPiece].vertices[mappingData.verticesTable[currentPiece][i]].pinned = state;
					pinChange[count] = i;
					count++;
				}
			}
			parent.addUndoCommand(new PinCommand(pinChange));
			repaint();
		}
	}

	public boolean isPinned(int i) {
		return mappingData.meshes[currentPiece].vertices[mappingData.verticesTable[currentPiece][i]].pinned;
	}

	/**
	 * Given the index of a displayed vertex, returns the index of this vertex
	 * with respect to the unfolded piece of mesh.
	 * 
	 * This comes form the fact that not all vertices are displayed when the
	 * original mesh is not a triangle mesh
	 * 
	 * @param index
	 *            The index of the displayed vertex
	 * @return The index within the unfolded mesh
	 */
	public int getTrueIndex(int index) {
		return mappingData.verticesTable[currentPiece][index];
	}

	public UVMappingEditorDialog getParentDialog() {
		return parent;
	}

	/**
	 * Calculate the distance (in edges) between each vertex and the nearest
	 * selected vertex.
	 */
	// from triangle mesh editor
	public void findSelectionDistance() {
		int i, j;
		UnfoldedMesh mesh = meshes[currentPiece];
		int dist[] = new int[mesh.getVertices().length];
		UnfoldedEdge e[] = mesh.getEdges();

		int maxDistance = parent.getMaxTensionDistance();
		// First, set each distance to 0 or -1, depending on whether that vertex
		// is part of the
		// current selection.

		for (i = 0; i < selected.length; i++)
			dist[mappingData.verticesTable[currentPiece][i]] = selected[i] ? 0 : -1;
		// Now extend this outward up to maxDistance.
		for (i = 0; i < maxDistance; i++)
			for (j = 0; j < e.length; j++) {
				if (e[j].hidden)
					continue;
				if (dist[e[j].v1] == -1 && dist[e[j].v2] == i)
					dist[e[j].v1] = i + 1;
				else if (dist[e[j].v2] == -1 && dist[e[j].v1] == i)
					dist[e[j].v2] = i + 1;
			}
		selectionDistance = new int[selected.length];
		for (i = 0; i < selected.length; i++) {
			selectionDistance[i] = dist[mappingData.verticesTable[currentPiece][i]];
		}
	}

	/**
	 * Given a list of deltas which will be added to the selected vertices,
	 * calculate the corresponding deltas for the unselected vertices according
	 * to the mesh tension.
	 */

	public void adjustDeltas(Vec2 delta[]) {
		int dist[] = getSelectionDistance();
		int count[] = new int[delta.length];
		UnfoldedMesh mesh = meshes[currentPiece];
		UnfoldedEdge edge[] = mesh.getEdges();
		int maxDistance = parent.getMaxTensionDistance();
		double tension = parent.getTensionValue();
		double scale[] = new double[maxDistance + 1];

		for (int i = 0; i < delta.length; i++)
			if (dist[i] != 0)
				delta[i].set(0.0, 0.0);
		int v1, v2;
		for (int i = 0; i < maxDistance; i++) {
			for (int j = 0; j < count.length; j++)
				count[j] = 0;
			for (int j = 0; j < edge.length; j++) {
				v1 = mappingData.invVerticesTable[currentPiece][edge[j].v1];
				v2 = mappingData.invVerticesTable[currentPiece][edge[j].v2];
				if (v1 == -1 || v2 == -1) {
					continue;
				}
				if (dist[v1] == i && dist[v2] == i + 1) {
					count[v2]++;
					delta[v2].add(delta[v1]);
				} else if (dist[v2] == i && dist[v1] == i + 1) {
					count[v1]++;
					delta[v1].add(delta[v2]);
				}
			}
			for (int j = 0; j < count.length; j++)
				if (count[j] > 1)
					delta[j].scale(1.0 / count[j]);
		}
		for (int i = 0; i < scale.length; i++)
			scale[i] = Math.pow((maxDistance - i + 1.0) / (maxDistance + 1.0),
					tension);
		for (int i = 0; i < delta.length; i++)
			if (dist[i] > 0)
				delta[i].scale(scale[dist[i]]);
	}

	public int[] getSelectionDistance() {
		return selectionDistance;
	}
}
