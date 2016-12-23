/*
 *  Copyright (C) 2005-2007 by Francois Guillet
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;
import java.util.prefs.Preferences;

import artofillusion.MeshViewer;
import artofillusion.ObjectViewer;
import artofillusion.Property;
import artofillusion.RenderingMesh;
import artofillusion.RenderingTriangle;
import artofillusion.Scene;
import artofillusion.TextureParameter;
import artofillusion.ViewerCanvas;
import artofillusion.WireframeMesh;
import artofillusion.animation.Actor;
import artofillusion.animation.Joint;
import artofillusion.animation.Keyframe;
import artofillusion.animation.MeshGesture;
import artofillusion.animation.Skeleton;
import artofillusion.math.BoundingBox;
import artofillusion.math.CoordinateSystem;
import artofillusion.math.RGBColor;
import artofillusion.math.Vec3;
import artofillusion.object.FacetedMesh;
import artofillusion.object.Mesh;
import artofillusion.object.MeshVertex;
import artofillusion.object.Object3D;
import artofillusion.object.ObjectInfo;
import artofillusion.object.SplineMesh;
import artofillusion.object.TriangleMesh;
import artofillusion.object.TriangleMesh.Edge;
import artofillusion.object.TriangleMesh.Face;
import artofillusion.object.TriangleMesh.Vertex;
import artofillusion.polymesh.QuadMesh.QuadEdge;
import artofillusion.polymesh.QuadMesh.QuadVertex;
import artofillusion.polymesh.QuadMesh.QuadFace;
import artofillusion.texture.FaceParameterValue;
import artofillusion.texture.FaceVertexParameterValue;
import artofillusion.texture.ParameterValue;
import artofillusion.texture.Texture;
import artofillusion.texture.TextureMapping;
import artofillusion.texture.VertexParameterValue;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.MeshEditController;
import artofillusion.ui.Translate;
import artofillusion.ui.UIUtilities;
import buoy.widget.BStandardDialog;
import buoy.widget.RowContainer;

/**
 * Winged edge mesh implementation for Art of Illusion.
 * 
 * @author Francois Guillet
 */

public class PolyMesh extends Object3D implements Mesh, FacetedMesh {

	private BoundingBox bounds; //the bounds enclosing the mesh

	private int smoothingMethod;

	private RenderingMesh cachedMesh;

	private WireframeMesh cachedWire;

	private Vec3[] cachedNormals; //vertices normals

	private Vec3[] cachedEdgeNormals;

	private Vec3[] cachedFaceNormals;

	private Wvertex[] vertices;

	private Wedge[] edges;

	private Wface[] faces;

	private boolean closed;

	private Skeleton skeleton;

	private Vector v1; //vectors used to store triangulation information
	//persistent data, must de declared as fields

	private Vector v2;

	private Vector v3;

	private Vector vert;

	private Vector vertInfo;

	private Vector faceInfo;

	private short mirrorState; //live mirrors

	private PolyMesh mirroredMesh;

	private boolean controlledSmoothing;

	private double minAngle, maxAngle; //data for auto smoothness

	private float minSmoothness, maxSmoothness;

	private boolean[] seams; //true if an edge is a seam

	private int[] polyedge; //see getPolyEdge()

	private TriangleMesh triangleMesh; //the triangulated mesh

	private int interactiveSmoothLevel;
	//smoothnes levels applied before display (interactive) or triangular smoothing (rendering)
	
	private boolean[] subdivideFaces;

	private int[] projectedEdges; //original edges in the case of a smoothed mesh

	private QuadMesh subdividedMesh; //the subdivided mesh when smoothed

	protected int[] mirroredVerts; //vert

	protected int[] mirroredFaces; //tables that relate mirrored mesh to original mesh

	protected int[] mirroredEdges;

	protected int[] invMirroredVerts;

	protected int[] invMirroredFaces;

	protected int[] invMirroredEdges;

	protected UVMappingData mappingData; //UV Mapping

	private int mappingVerts, mappingEdges, mappingFaces; //markers to check if UVMapping data
	//is still valid
	
	//colors and preferences
	private boolean useCustomColors;
	
	private Color vertColor;
	
	private Color selectedVertColor;
	
	private Color edgeColor;
	
	private Color selectedEdgeColor;
	
	private Color meshColor;

	private Color selectedFaceColor;
	
	private RGBColor meshRGBColor;

	private RGBColor selectedFaceRGBColor;

	private Color seamColor;

	private Color selectedSeamColor;
	
	private int handleSize;
	
	//preferences
	
	private static Preferences preferences = Preferences.userRoot().node("artofillusion.polymesh");

	// direction constants
	/**
	 * Action along the normal
	 */
	public final static short NORMAL = 0;

	/**
	 * Action along X axis
	 */
	public final static short X = 1;

	/**
	 * Action along Y axis
	 */
	public final static short Y = 2;

	/**
	 * Action along Z axis
	 */
	public final static short Z = 3;
	
	//Bevel markers

	private final static short VERTEX_BEVEL = 1;

	private final static short ONE_BEVEL = 2;

	private final static short ONE_BEVEL_NEXT = 3;

	private final static short TWO_BEVEL = 4;
	
	//smoothing constants

//	private final static short APPROXIMATING_CM = 10;
//
//	private final static short APPROXIMATING_BLZ = 11;

	/**
	 * Area under which faces are collapsed if applyEdgeLengthLimit is selected
	 */
	public static double edgeLengthLimit = 5e-3;

	/**
	 * If true, faces under a certain size are collapsed.
	 */
	public static boolean applyEdgeLengthLimit = true;

	/**
	 * No mirror applied to the mesh
	 */
	public final static short NO_MIRROR = 0;

	/**
	 * Mirror on xy
	 */
	public final static short MIRROR_ON_XY = 1;

	/**
	 * Mirror on yz
	 */
	public final static short MIRROR_ON_YZ = 2;

	/**
	 * Mirror on xz
	 */
	public final static short MIRROR_ON_XZ = 4;

	/* Properties */
	private static final Property PROPERTIES[] = new Property[] {
			new Property(Translate.text("polymesh:intersubdiv"), 1, 6, 1) };

	/**
	 * Constructor for the PolyMesh object
	 * 
	 * @param type
	 *            Mesh type (e.g. cylinder, flat plane, etc.)
	 * @param u
	 *            Size of the mesh (U direction)
	 * @param v
	 *            Position of the mesh (V direction)
	 * @param sx
	 *            Mesh size along x
	 * @param sy
	 *            Mesh size along y
	 * @param sz
	 *            Mesh size along z
	 */
	public PolyMesh(int type, int u, int v, double sx, double sy, double sz) {
		super();
		initialize();
		switch (type) {
		case 0:
			// Cube
			vertices = new Wvertex[8];
			vertices[0] = new Wvertex(new Vec3(0.5, -0.5, -0.5), 0);
			vertices[1] = new Wvertex(new Vec3(0.5, 0.5, -0.5), 1);
			vertices[2] = new Wvertex(new Vec3(-0.5, 0.5, -0.5), 2);
			vertices[3] = new Wvertex(new Vec3(-0.5, -0.5, -0.5), 3);
			vertices[4] = new Wvertex(new Vec3(-0.5, -0.5, 0.5), 11);
			vertices[5] = new Wvertex(new Vec3(0.5, -0.5, 0.5), 8);
			vertices[6] = new Wvertex(new Vec3(0.5, 0.5, 0.5), 9);
			vertices[7] = new Wvertex(new Vec3(-0.5, 0.5, 0.5), 10);
			faces = new Wface[6];
			faces[0] = new Wface(14);
			faces[1] = new Wface(0);
			faces[2] = new Wface(1);
			faces[3] = new Wface(2);
			faces[4] = new Wface(3);
			faces[5] = new Wface(8);

			edges = new Wedge[24];
			edges[0] = new Wedge(1, 12, 1, 5);
			edges[1] = new Wedge(2, 13, 2, 6);
			edges[2] = new Wedge(3, 14, 3, 7);
			edges[3] = new Wedge(0, 15, 4, 4);
			edges[4] = new Wedge(5, 16, 4, 23);
			edges[5] = new Wedge(6, 17, 1, 20);
			edges[6] = new Wedge(7, 18, 2, 21);
			edges[7] = new Wedge(4, 19, 3, 22);
			edges[8] = new Wedge(6, 20, 5, 9);
			edges[9] = new Wedge(7, 21, 5, 10);
			edges[10] = new Wedge(4, 22, 5, 11);
			edges[11] = new Wedge(5, 23, 5, 8);

			edges[12] = new Wedge(0, 0, 0, 15);
			edges[13] = new Wedge(1, 1, 0, 12);
			edges[14] = new Wedge(2, 2, 0, 13);
			edges[15] = new Wedge(3, 3, 0, 14);
			edges[16] = new Wedge(0, 4, 1, 0);
			edges[17] = new Wedge(1, 5, 2, 1);
			edges[18] = new Wedge(2, 6, 3, 2);
			edges[19] = new Wedge(3, 7, 4, 3);
			edges[20] = new Wedge(5, 8, 1, 16);
			edges[21] = new Wedge(6, 9, 2, 17);
			edges[22] = new Wedge(7, 10, 3, 18);
			edges[23] = new Wedge(4, 11, 4, 19);
			break;
		case 2:
			// octahedron
			vertices = new Wvertex[6];
			vertices[0] = new Wvertex(new Vec3(0.0, -0.707107, 0.0), 0);
			vertices[1] = new Wvertex(new Vec3(0.5, 0.0, -0.5), 11);
			vertices[2] = new Wvertex(new Vec3(-0.5, 0.0, -0.5), 8);
			vertices[3] = new Wvertex(new Vec3(0.5, 0.0, 0.5), 10);
			vertices[4] = new Wvertex(new Vec3(-0.5, 0.0, 0.5), 9);
			vertices[5] = new Wvertex(new Vec3(0.0, 0.707107, 0.0), 22);
			edges = new Wedge[24];
			edges[0] = new Wedge(1, 12, 1, 3);
			edges[1] = new Wedge(2, 13, 4, 8);
			edges[2] = new Wedge(0, 14, 2, 19);
			edges[3] = new Wedge(3, 15, 1, 17);
			edges[4] = new Wedge(4, 16, 5, 9);
			edges[5] = new Wedge(3, 17, 3, 6);
			edges[6] = new Wedge(4, 18, 3, 7);
			edges[7] = new Wedge(0, 19, 3, 5);
			edges[8] = new Wedge(5, 20, 4, 23);
			edges[9] = new Wedge(5, 21, 5, 20);
			edges[10] = new Wedge(5, 22, 6, 21);
			edges[11] = new Wedge(5, 23, 7, 22);
			edges[12] = new Wedge(0, 0, 0, 14);
			edges[13] = new Wedge(1, 1, 0, 12);
			edges[14] = new Wedge(2, 2, 0, 13);
			edges[15] = new Wedge(1, 3, 7, 11);
			edges[16] = new Wedge(2, 4, 2, 2);
			edges[17] = new Wedge(0, 5, 1, 0);
			edges[18] = new Wedge(3, 6, 6, 10);
			edges[19] = new Wedge(4, 7, 2, 16);
			edges[20] = new Wedge(2, 8, 5, 4);
			edges[21] = new Wedge(4, 9, 6, 18);
			edges[22] = new Wedge(3, 10, 7, 15);
			edges[23] = new Wedge(1, 11, 4, 1);
			faces = new Wface[8];
			faces[0] = new Wface(14);
			faces[1] = new Wface(0);
			faces[2] = new Wface(2);
			faces[3] = new Wface(5);
			faces[4] = new Wface(8);
			faces[5] = new Wface(9);
			faces[6] = new Wface(10);
			faces[7] = new Wface(11);
			break;
		case 3:
			// cylinder
			if (u < 3)
				u = 3;
			vertices = new Wvertex[u * 2];
			edges = new Wedge[6 * u];
			faces = new Wface[u + 2];
			for (int i = 0; i < u; ++i) {
				vertices[i] = new Wvertex(new Vec3(0.5 * Math.cos(2 * i
						* Math.PI / u), 0.5, -0.5
						* Math.sin(2 * i * Math.PI / u)), i);
				vertices[u + i] = new Wvertex(new Vec3(0.5 * Math.cos(2 * i
						* Math.PI / u), -0.5, -0.5
						* Math.sin(2 * i * Math.PI / u)), i + u + edges.length
						/ 2);
			}
			for (int i = 0; i < u; ++i) {
				edges[i] = new Wedge(i + 1, i + edges.length / 2, 0, i + 1);
				if (i == u - 1) {
					edges[i].vertex = 0;
					edges[i].next = 0;
				}
				edges[i + edges.length / 2] = new Wedge(i, i, i + 2, i + 2 * u);
				edges[i + u] = new Wedge(i + u, i + u + edges.length / 2, 1, i
						- 1 + u);
				if (i == 0)
					edges[i + u].next = 2 * u - 1;
				edges[i + u + edges.length / 2] = new Wedge(i + 1 + u, i + u,
						i + 2, i + 1 + 2 * u + edges.length / 2);
				if (i == u - 1) {
					edges[i + u + edges.length / 2].vertex = u;
					edges[i + u + edges.length / 2].next = 2 * u + edges.length
							/ 2;
				}
				edges[i + 2 * u] = new Wedge(i + u, i + 2 * u + edges.length
						/ 2, i + 2, i + u + edges.length / 2);
				edges[i + 2 * u + edges.length / 2] = new Wedge(i, i + 2 * u,
						i + 2 - 1, i - 1 + edges.length / 2);
				if (i == 0) {
					edges[i + 2 * u + edges.length / 2].face = u + 1;
					edges[i + 2 * u + edges.length / 2].next = u - 1
							+ edges.length / 2;
				}
			}
			faces[0] = new Wface(0);
			faces[1] = new Wface(u);
			for (int i = 0; i < u; ++i)
				faces[i + 2] = new Wface(2 * u + i);
			if (v > 1) {
				int count;
				int[] indices;
				boolean[] selected = new boolean[edges.length / 2];
				for (int i = 0; i < edges.length / 2; ++i) {
					Vec3 v1 = vertices[edges[i].vertex].r;
					Vec3 v2 = vertices[edges[edges[i].hedge].vertex].r;
					if ((v1.y > 0 && v2.y < 0) || (v2.y > 0 && v1.y < 0))
						selected[i] = true;
				}
				selected = divideEdges(selected, v);
				count = 0;
				for (int i = 0; i < selected.length; ++i)
					if (selected[i])
						++count;
				indices = new int[count];
				count = 0;
				for (int i = 0; i < selected.length; ++i)
					if (selected[i])
						indices[count++] = i;
				connectVertices(indices);
			}
			// dumpMesh();
			break;
		case 1:
			u = 1;
			v = 1;
		case 4:
			// planar mesh, single face;
			planarMesh(u + 1, v + 1, 1.0, 1.0);
			for (int i = 0; i < vertices.length; ++i) {
				vertices[i].r.x -= 0.5;
				vertices[i].r.y -= 0.5;
			}
			break;
		}
		for (int i = 0; i < vertices.length; ++i) {
			vertices[i].r.x *= sx;
			vertices[i].r.y *= sy;
			vertices[i].r.z *= sz;
		}
		smoothingMethod = Mesh.NO_SMOOTHING;
		setSkeleton(new Skeleton());
	}
	
	private PolyMesh() {
		smoothingMethod = Mesh.NO_SMOOTHING;
		initialize();
		setSkeleton(new Skeleton());
	}

	/**
	 * Initialization stuff common to several constructors
	 */
	private void initialize() {
		controlledSmoothing = false;
		minAngle = 0;
		maxAngle = 90;
		minSmoothness = 1.0f;
		maxSmoothness = 0.0f;
		interactiveSmoothLevel = 1;
		//display properties
		loadFromDisplayPropertiesPreferences();	
	}
	
	/**
	 * Sets the mesh display properties using stored preferences
	 *
	 */
	public void loadFromDisplayPropertiesPreferences() {
		vertColor = new Color( preferences.getInt("vertColor_red", ViewerCanvas.lineColor.getRed()),
				preferences.getInt("vertColor_green", ViewerCanvas.lineColor.getGreen()),
				preferences.getInt("vertColor_blue", ViewerCanvas.lineColor.getBlue()));
		selectedVertColor = new Color( preferences.getInt("selectedVertColor_red", ViewerCanvas.highlightColor.getRed()),
				preferences.getInt("selectedVertColor_green", ViewerCanvas.highlightColor.getGreen()),
				preferences.getInt("selectedVertColor_blue", ViewerCanvas.highlightColor.getBlue()));
		edgeColor = new Color( preferences.getInt("edgeColor_red", ViewerCanvas.lineColor.getRed()),
				preferences.getInt("edgeColor_green", ViewerCanvas.lineColor.getGreen()),
				preferences.getInt("edgeColor_blue", ViewerCanvas.lineColor.getBlue()));
		selectedEdgeColor = new Color( preferences.getInt("selectedEdgeColor_red", ViewerCanvas.highlightColor.getRed()),
				preferences.getInt("selectedEdgeColor_green", ViewerCanvas.highlightColor.getGreen()),
				preferences.getInt("selectedEdgeColor_blue", ViewerCanvas.highlightColor.getBlue()));
		seamColor = new Color( preferences.getInt("seamColor_red", 0),
				preferences.getInt("seamColor_green", 0),
				preferences.getInt("seamColor_blue", 255));
		selectedSeamColor = new Color( preferences.getInt("selectedSeamColor_red", 0),
				preferences.getInt("selectedSeamColor_green", 162),
				preferences.getInt("selectedSeamColor_blue", 255));
		Color transparent = ViewerCanvas.transparentColor.getColor();
		meshColor = new Color( preferences.getInt("meshColor_red",transparent.getRed()),
				preferences.getInt("meshColor_green", transparent.getGreen()),
				preferences.getInt("meshColor_blue", transparent.getBlue()));
		selectedFaceColor = new Color( preferences.getInt("selectedFaceColor_red", 255),
				preferences.getInt("selectedFaceColor_green", 102),
				preferences.getInt("selectedFaceColor_blue", 255));
		meshRGBColor = ColorToRGB(meshColor);
		selectedFaceRGBColor = ColorToRGB(selectedFaceColor);
		handleSize = preferences.getInt("handleSize", 3);
		String useCustom = preferences.get("useCustomColors", "true");
		useCustomColors = Boolean.parseBoolean(useCustom);
	}
	
	/**
	 * Uses current display properties to store them as new default properties
	 *
	 */
	public void resetDisplayPropertiesPreferences() {
		
		preferences.putInt("vertColor_red", ViewerCanvas.lineColor.getRed());
		preferences.putInt("vertColor_green", ViewerCanvas.lineColor.getGreen());
		preferences.putInt("vertColor_blue", ViewerCanvas.lineColor.getBlue());
		preferences.putInt("selectedVertColor_red", ViewerCanvas.highlightColor.getRed());
		preferences.putInt("selectedVertColor_green", ViewerCanvas.highlightColor.getGreen());
		preferences.putInt("selectedVertColor_blue", ViewerCanvas.highlightColor.getBlue());
		preferences.putInt("edgeColor_red", ViewerCanvas.lineColor.getRed());
		preferences.putInt("edgeColor_green", ViewerCanvas.lineColor.getGreen());
		preferences.putInt("edgeColor_blue", ViewerCanvas.lineColor.getBlue());
		preferences.putInt("selectedEdgeColor_red", ViewerCanvas.highlightColor.getRed());
		preferences.putInt("selectedEdgeColor_green", ViewerCanvas.highlightColor.getGreen());
		preferences.putInt("selectedEdgeColor_blue", ViewerCanvas.highlightColor.getBlue());
		preferences.putInt("seamColor_red", 0);
		preferences.putInt("seamColor_green", 0);
		preferences.putInt("seamColor_blue", 255);
		preferences.putInt("selectedSeamColor_red", 0);
		preferences.putInt("selectedSeamColor_green", 162);
		preferences.putInt("selectedSeamColor_blue", 255);
		Color transparent = ViewerCanvas.transparentColor.getColor();
		preferences.putInt("meshColor_red",transparent.getRed());
		preferences.putInt("meshColor_green", transparent.getGreen());
		preferences.putInt("meshColor_blue", transparent.getBlue());
		preferences.putInt("selectedFaceColor_red", 255);
		preferences.putInt("selectedFaceColor_green", 102);
		preferences.putInt("selectedFaceColor_blue", 255);
		preferences.putInt("handleSize", 3);
		preferences.put("useCustomColors", "false");
		loadFromDisplayPropertiesPreferences();
	}
	
	/**
	 * Uses current display properties to store them as new default properties
	 *
	 */
	public void storeDisplayPropertiesAsReferences() {
		preferences.putInt("vertColor_red", vertColor.getRed());
		preferences.putInt("vertColor_green", vertColor.getGreen());
		preferences.putInt("vertColor_blue", vertColor.getBlue());
		preferences.putInt("selectedVertColor_red", selectedVertColor.getRed());
		preferences.putInt("selectedVertColor_green", selectedVertColor.getGreen());
		preferences.putInt("selectedVertColor_blue", selectedVertColor.getBlue());
		preferences.putInt("edgeColor_red", edgeColor.getRed());
		preferences.putInt("edgeColor_green", edgeColor.getGreen());
		preferences.putInt("edgeColor_blue", edgeColor.getBlue());
		preferences.putInt("selectedEdgeColor_red", selectedEdgeColor.getRed());
		preferences.putInt("selectedEdgeColor_green", selectedEdgeColor.getGreen());
		preferences.putInt("selectedEdgeColor_blue", selectedEdgeColor.getBlue());
		preferences.putInt("seamColor_red", seamColor.getRed());
		preferences.putInt("seamColor_green", seamColor.getGreen());
		preferences.putInt("seamColor_blue", seamColor.getBlue());
		preferences.putInt("selectedSeamColor_red", selectedSeamColor.getRed());
		preferences.putInt("selectedSeamColor_green", selectedSeamColor.getGreen());
		preferences.putInt("selectedSeamColor_blue", selectedSeamColor.getBlue());
		preferences.putInt("meshColor_red", meshColor.getRed());
		preferences.putInt("meshColor_green", meshColor.getGreen());
		preferences.putInt("meshColor_blue", meshColor.getBlue());
		preferences.putInt("selectedFaceColor_red", selectedFaceColor.getRed());
		preferences.putInt("selectedFaceColor_green", selectedFaceColor.getGreen());
		preferences.putInt("selectedFaceColor_blue", selectedFaceColor.getBlue());
		preferences.putInt("handleSize", handleSize);
		preferences.put("useCustomColors", Boolean.toString(useCustomColors));
	}

	/**
	 * Use this constructor to create a PolyMesh directly from a
	 * vertices/edges/faces structure
	 * 
	 * @param v
	 *            The vertices list
	 * @param e
	 *            The edges list
	 * @param f
	 *            The faces list
	 */
	public PolyMesh(Wvertex[] v, Wedge[] e, Wface[] f) {
		vertices = v;
		edges = e;
		faces = f;
		initialize();
		setSkeleton(new Skeleton());
	}

	/**
	 * Constructor for the PolyMesh object The spline mesh given as parameter is
	 * converted to a polymesh
	 * 
	 * @param smesh
	 *            Spline Mesh
	 */
	public PolyMesh(SplineMesh smesh) {
		initialize();
		int uSize = smesh.getUSize();
		int vSize = smesh.getVSize();
		if (smesh.isUClosed())
			++uSize;
		if (smesh.isVClosed())
			++vSize;
		// System.out.println( uSize + " " + vSize );
		smoothingMethod = smesh.getSmoothingMethod();
		Vec3[] vp = smesh.getVertexPositions();
		planarMesh(uSize, vSize, uSize - 1, vSize - 1);
		int u;
		int v;
		int vertTable[][] = new int[uSize][vSize];
		for (int i = 0; i < uSize * vSize; ++i) {
			u = (int) Math.round(vertices[i].r.x);
			v = (int) Math.round(vertices[i].r.y);
			if (i != u + uSize * v)
				System.out.println("pb");
			vertTable[u][v] = u + uSize * v;
		}
		if (smesh.isUClosed()) {
			Wvertex[] newVertices = new Wvertex[vertices.length];
			Wedge[] newEdges = new Wedge[edges.length];
			for (int i = 0; i < vertices.length; i++)
				newVertices[i] = new Wvertex(vertices[i]);
			for (int i = 0; i < edges.length; i++) {
				newEdges[i] = new Wedge(edges[i]);
				u = (int) Math.round(vertices[newEdges[i].vertex].r.x);
				v = (int) Math.round(vertices[newEdges[i].vertex].r.y);
				if (u == uSize - 1)
					newEdges[i].vertex = vertTable[0][v];
			}
			for (int i = 0; i < vSize - 1; i++) {
				int[] el = getVertexEdges(vertices[vertTable[0][i + 1]]);
				int[] er = getVertexEdges(vertices[vertTable[uSize - 1][i]]);
				int ele = -1;
				for (int j = 0; j < el.length; ++j) {
					if (edges[el[j]].vertex == vertTable[0][i])
						ele = el[j];
				}
				int ere = -1;
				for (int j = 0; j < er.length; ++j) {
					if (edges[er[j]].vertex == vertTable[uSize - 1][i + 1])
						ere = er[j];
				}
				int n = edges[ere].hedge;
				while (edges[n].next != ere)
					n = edges[edges[n].next].hedge;
				newEdges[n].next = edges[ele].hedge;

				newEdges[newEdges[ele].hedge] = new Wedge(newEdges[ere]);
				newEdges[newEdges[ele].hedge].hedge = ele;
				newEdges[newEdges[ele].hedge].vertex = vertTable[0][i + 1];
				newVertices[newEdges[ele].vertex].edge = newEdges[ele].hedge;
				newVertices[newEdges[newEdges[ele].hedge].vertex].edge = ele;
				newEdges[ere].vertex = -1;
				newEdges[newEdges[ere].hedge].vertex = -1;
			}
			int[] eleft = getVertexEdges(vertices[vertTable[0][0]]);
			int[] eright = getVertexEdges(vertices[vertTable[uSize - 1][0]]);
			int elefte = -1;
			for (int j = 0; j < eleft.length; ++j) {
				if (edges[eleft[j]].vertex == vertTable[1][0])
					elefte = eleft[j];
			}
			int erighte = -1;
			for (int j = 0; j < eright.length; ++j) {
				if (edges[eright[j]].vertex == vertTable[uSize - 2][0])
					erighte = eright[j];
			}
			newEdges[newEdges[elefte].hedge].next = erighte;
			eleft = getVertexEdges(vertices[vertTable[0][vSize - 1]]);
			eright = getVertexEdges(vertices[vertTable[uSize - 1][vSize - 1]]);
			elefte = -1;
			for (int j = 0; j < eleft.length; ++j) {
				if (edges[eleft[j]].vertex == vertTable[1][vSize - 1])
					elefte = eleft[j];
			}
			erighte = -1;
			for (int j = 0; j < eright.length; ++j) {
				if (edges[eright[j]].vertex == vertTable[uSize - 2][vSize - 1])
					erighte = eright[j];
			}
			newEdges[newEdges[erighte].hedge].next = elefte;

			for (int i = 0; i < vSize; ++i)
				newVertices[vertTable[uSize - 1][i]].edge = -1;
			--uSize;
			vertices = newVertices;
			edges = newEdges;
			updateResizedMesh();
			for (int i = 0; i < uSize * vSize; ++i) {
				u = (int) Math.round(vertices[i].r.x);
				v = (int) Math.round(vertices[i].r.y);
				vertTable[u][v] = i;
			}

		}
		if (smesh.isVClosed()) {
			Wvertex[] newVertices = new Wvertex[vertices.length];
			Wedge[] newEdges = new Wedge[edges.length];
			for (int i = 0; i < vertices.length; i++)
				newVertices[i] = new Wvertex(vertices[i]);
			for (int i = 0; i < edges.length; i++) {
				newEdges[i] = new Wedge(edges[i]);
				u = (int) Math.round(vertices[newEdges[i].vertex].r.x);
				v = (int) Math.round(vertices[newEdges[i].vertex].r.y);
				if (v == vSize - 1)
					newEdges[i].vertex = vertTable[u][0];
			}
			int us = uSize - 1;
			if (smesh.isUClosed())
				++us;
			for (int i = 0; i < us; i++) {
				int k = i + 1;
				if (k == uSize)
					k = 0;
				int[] ed = getVertexEdges(vertices[vertTable[i][0]]);
				int[] eu = getVertexEdges(vertices[vertTable[k][vSize - 1]]);
				int ede = -1;
				for (int j = 0; j < ed.length; ++j) {
					if (edges[ed[j]].vertex == vertTable[k][0])
						ede = ed[j];
				}
				int eue = -1;
				for (int j = 0; j < eu.length; ++j) {
					if (edges[eu[j]].vertex == vertTable[i][vSize - 1])
						eue = eu[j];
				}
				int n = edges[eue].hedge;
				while (edges[n].next != eue)
					n = edges[edges[n].next].hedge;
				newEdges[n].next = edges[ede].hedge;
				newEdges[newEdges[ede].hedge] = new Wedge(newEdges[eue]);
				newEdges[newEdges[ede].hedge].hedge = ede;
				newEdges[newEdges[ede].hedge].vertex = vertTable[i][0];
				newVertices[newEdges[ede].vertex].edge = newEdges[ede].hedge;
				newVertices[newEdges[newEdges[ede].hedge].vertex].edge = ede;
				newEdges[eue].vertex = -1;
				newEdges[newEdges[eue].hedge].vertex = -1;
			}
			if (!smesh.isUClosed()) {
				int[] edown = getVertexEdges(vertices[vertTable[0][0]]);
				int[] eup = getVertexEdges(vertices[vertTable[0][vSize - 1]]);
				int edowne = -1;
				for (int j = 0; j < edown.length; ++j) {
					if (edges[edown[j]].vertex == vertTable[0][1])
						edowne = edown[j];
				}
				int eupe = -1;
				for (int j = 0; j < eup.length; ++j) {
					if (edges[eup[j]].vertex == vertTable[0][vSize - 2])
						eupe = eup[j];
				}
				newEdges[newEdges[eupe].hedge].next = edowne;
				edown = getVertexEdges(vertices[vertTable[uSize - 1][0]]);
				eup = getVertexEdges(vertices[vertTable[uSize - 1][vSize - 1]]);
				edowne = -1;
				for (int j = 0; j < edown.length; ++j) {
					if (edges[edown[j]].vertex == vertTable[uSize - 1][1])
						edowne = edown[j];
				}
				eupe = -1;
				for (int j = 0; j < eup.length; ++j) {
					if (edges[eup[j]].vertex == vertTable[uSize - 1][vSize - 2])
						eupe = eup[j];
				}
				newEdges[newEdges[edowne].hedge].next = eupe;
			}

			for (int i = 0; i < uSize; ++i)
				newVertices[vertTable[i][vSize - 1]].edge = -1;
			--vSize;
			vertices = newVertices;
			edges = newEdges;
			updateResizedMesh();

		}
		for (int i = 0; i < vertices.length; ++i) {
			u = (int) Math.round(vertices[i].r.x);
			v = (int) Math.round(vertices[i].r.y);
			vertices[i].r = new Vec3(vp[u + uSize * v]);
		}
		setSkeleton(new Skeleton());
		if (smesh.getSmoothingMethod() == Mesh.APPROXIMATING || smesh.getSmoothingMethod() == Mesh.INTERPOLATING) {
			setSmoothingMethod(Mesh.APPROXIMATING);
		} else {
			setSmoothingMethod(Mesh.NO_SMOOTHING);
		}
		copyTextureAndMaterial(smesh);
		resetMesh();
	}

	/**
	 * Constructor for the PolyMesh object The triangle mesh given as parameter
	 * is converted to a polymesh
	 * 
	 * @param trimesh
	 *            Traingle Mesh
	 */
	public PolyMesh(TriangleMesh trimesh, boolean findQuads,
			boolean angularSearch) {
		initialize();

		Edge[] triEdges = trimesh.getEdges();
		Face[] triFaces = trimesh.getFaces();
		Vertex[] triVerts = (Vertex[]) trimesh.getVertices();

		edges = new Wedge[triEdges.length * 2];
		faces = new Wface[triFaces.length];
		vertices = new Wvertex[triVerts.length];

		for (int i = 0; i < triVerts.length; ++i) {
			vertices[i] = new Wvertex(triVerts[i].r, -1);
			// vertices[i].smoothness = triVerts[i].smoothness;
		}
		for (int i = 0; i < triEdges.length; ++i) {
			edges[i] = new Wedge(triEdges[i].v2, i + triEdges.length, -1, -1);
			edges[i + triEdges.length] = new Wedge(triEdges[i].v1, i, -1, -1);
			vertices[triEdges[i].v1].edge = i;
			vertices[triEdges[i].v2].edge = i + triEdges.length;
			edges[i].smoothness = edges[i + triEdges.length].smoothness = triEdges[i].smoothness;
		}
		for (int i = 0; i < triFaces.length; ++i) {
			faces[i] = new Wface(-1);
			int e1 = triFaces[i].e1;
			if (edges[e1].vertex != triFaces[i].v2)
				e1 += triEdges.length;
			int e2 = triFaces[i].e2;
			if (edges[e2].vertex != triFaces[i].v3)
				e2 += triEdges.length;
			int e3 = triFaces[i].e3;
			if (edges[e3].vertex != triFaces[i].v1)
				e3 += triEdges.length;
			edges[e1].face = i;
			edges[e2].face = i;
			edges[e3].face = i;
			edges[e1].next = e2;
			edges[e2].next = e3;
			edges[e3].next = e1;
			faces[i].edge = e1;
		}
		for (int i = 0; i < edges.length; ++i) {
			if (edges[i].face == -1) {
				for (int j = 0; j < edges.length; ++j) {
					if (edges[j].vertex == edges[i].vertex
							&& edges[edges[j].hedge].face == -1) {
						edges[i].next = edges[j].hedge;
						break;
					}
				}
			}
		}
		boolean doQuads = findQuads;
		while (doQuads) {
			doQuads = false;
			Vec3[] normals = getFaceNormals();
			for (int i = 0; i < faces.length; ++i) {
				int fe[] = getFaceEdges(faces[i]);
				if (fe.length != 3)
					continue;
				double maxLength = -1;
				double length;
				double minAngle = Double.MAX_VALUE;
				int f;
				int face = -1;
				for (int j = 0; j < fe.length; ++j) {
					length = vertices[edges[fe[j]].vertex].r
							.distance(vertices[edges[edges[fe[j]].hedge].vertex].r);
					f = edges[edges[fe[j]].hedge].face;
					if (f == -1)
						continue;
					int[] fe2 = getFaceEdges(faces[f]);
					if (fe2.length != 3)
						continue;
					double angle = Double.MAX_VALUE;
					angle = Math.abs(Math.acos(normals[i].dot(normals[f])));
					if (angularSearch) {
						if (angle < minAngle) {
							minAngle = angle;
							maxLength = length;
							face = f;
						} else if ((angle < minAngle + 0.003)
								&& (length > maxLength)) {
							maxLength = length;
							face = f;
						}
					} else if (length > maxLength) {
						maxLength = length;
						face = f;
					}
				}
				if (face != -1) {
					boolean[] sel = new boolean[faces.length];
					sel[i] = true;
					sel[face] = true;
					mergeFaces(sel);
					doQuads = true;
					break;
				}
			}
		}
		setSkeleton(new Skeleton());
		setSmoothingMethod(trimesh.getSmoothingMethod());
		copyTextureAndMaterial(trimesh);
	}

	// faceted mesh interface
	/**
	 * Get the number of faces in this mesh.
	 */

	public int getFaceCount() {
		return faces.length;
	}

	/**
	 * Get the number of vertices in a particular face.
	 * 
	 * @param face
	 *            the index of the face
	 */

	public int getFaceVertexCount(int face) {
		return getFaceVertices(faces[face]).length;
	}

	/**
	 * Get the index of a particular vertex in a particular face.
	 * 
	 * @param face
	 *            the index of the face
	 * @param vertex
	 *            the index of the vertex within the face (between 0 and
	 *            getFaceVertexCount(face)-1 inclusive)
	 * @return the index of the corresponding vertex in the list returned by
	 *         getVertices()
	 */
	public int getFaceVertexIndex(int face, int vertex) {
		return getFaceVertices(faces[face])[vertex];
	}

	/**
	 * A utility function for resizing a mesh for which some vertices/edges have
	 * been deleted.
	 */
	private void updateResizedMesh() {
		// vertex reduction
		int vertCount = 0;
		int[] vertTable = new int[vertices.length];
		for (int i = 0; i < vertices.length; ++i) {
			if (vertices[i].edge != -1)
				vertTable[i] = vertCount++;
			else
				vertTable[i] = -1;
		}
		Wvertex newVertices[] = new Wvertex[vertCount];
		vertCount = 0;
		for (int i = 0; i < vertices.length; ++i) {
			if (vertices[i].edge != -1)
				newVertices[vertCount++] = new Wvertex(vertices[i]);
		}
		for (int i = 0; i < edges.length; ++i)
			if (edges[i].vertex != -1)
				edges[i].vertex = vertTable[edges[i].vertex];
		// edges reduction
		int edgeCount = 0;
		int[] edgeTable = new int[edges.length];
		for (int i = 0; i < edges.length; ++i) {
			if (edges[i].vertex != -1)
				edgeTable[i] = edgeCount++;
			else
				edgeTable[i] = -1;

		}
		Wedge[] newEdges = new Wedge[edgeCount];
		for (int i = 0; i < edges.length; ++i) {
			if (edges[i].vertex != -1) {
				newEdges[edgeTable[i]] = new Wedge(edges[i]);
				newEdges[edgeTable[i]].hedge = edgeTable[edges[i].hedge];
				newEdges[edgeTable[i]].next = edgeTable[edges[i].next];
			}
		}
		for (int i = 0; i < newVertices.length; ++i)
			newVertices[i].edge = edgeTable[newVertices[i].edge];
		for (int i = 0; i < faces.length; ++i)
			faces[i].edge = edgeTable[faces[i].edge];
		vertices = newVertices;
		edges = newEdges;
	}

	/**
	 * Creates a planar, grid-like mesh.
	 * 
	 * @param uSize
	 *            Number of points along x (u)
	 * @param vSize
	 *            Number of points along y (v)
	 * @param uMax
	 *            Max coordinate along x (mesh starts at 0)
	 * @param vMax
	 *            Max coordinate along y (mesh starts at 0)
	 */
	private void planarMesh(int uSize, int vSize, double uMax, double vMax) {
		int euSize = uSize - 1;
		int evSize = vSize - 1;
		vertices = new Wvertex[uSize * vSize];
		edges = new Wedge[(euSize * evSize * 2 + euSize + evSize) * 2];
		faces = new Wface[euSize * evSize];
		for (int i = 0; i < uSize; ++i)
			for (int j = 0; j < vSize; ++j)
				vertices[i + uSize * j] = new Wvertex(new Vec3(i * uMax
						/ ((double) (uSize - 1)), j * vMax
						/ ((double) (vSize - 1)), 0), -1);
		int ec;
		int ec1;
		int k;
		int k1;
		int k1up;
		int f;
		int fd;
		int el = edges.length / 2;
		for (int i = 0; i < euSize; ++i)
			for (int j = 0; j < evSize; ++j) {
				ec = (i + euSize * j) * 2;
				f = i + euSize * j;
				fd = i + euSize * (j - 1);
				if (j == 0)
					fd = -1;
				ec1 = ec + 1;
				k = i + uSize * j;
				k1 = k + 1;
				k1up = i + 1 + uSize * (j + 1);

				edges[ec] = new Wedge(k1, ec + el, f, ec1);
				vertices[k].edge = ec;
				edges[ec1] = new Wedge(k1up, ec1 + el, f, ec + el + euSize * 2);
				if (j == evSize - 1)
					edges[ec1].next = euSize * evSize * 2 + i;
				edges[ec + el] = new Wedge(k, ec, fd, ec - euSize * 2 - 1 + el);
				if (i == 0 && j == 0)
					edges[ec + el].next = euSize * evSize * 2 + euSize + el;
				else if (i == 0)
					edges[ec + el].next = euSize * evSize * 2 + euSize + j - 1;
				else if (j == 0)
					edges[ec + el].next = ec - 2 + el;
				vertices[k1].edge = ec + el;
				edges[ec1 + el] = new Wedge(k1, ec1, f + 1, ec + 2);
				vertices[k1up].edge = ec1 + el;
				if (i == euSize - 1 && j == 0)
					edges[ec1 + el].next = ec + el;
				else if (i == euSize - 1)
					edges[ec1 + el].next = ec1 + el - euSize * 2;
				if (i == euSize - 1)
					edges[ec1 + el].face = -1;
				faces[f] = new Wface(ec);
			}
		vertices[uSize * (vSize - 1)].edge = (euSize * evSize) * 2 + euSize
				+ evSize - 1;
		for (int i = 0; i < euSize; i++) {
			k = i + uSize * (vSize - 1);
			ec = euSize * evSize * 2 + i;
			fd = i + euSize * (evSize - 1);
			edges[ec] = new Wedge(k, ec + el, fd, (i + euSize * (evSize - 1))
					* 2 - 1 + el);
			if (i == 0)
				edges[ec].next = euSize * evSize * 2 + euSize + evSize - 1;
			edges[ec + el] = new Wedge(k + 1, ec, -1, ec + 1 + el);
			if (i == euSize - 1)
				edges[ec + el].next = (i + euSize * (evSize - 1)) * 2 + 1 + el;
		}
		for (int j = 0; j < evSize; j++) {
			k = j * uSize;
			ec = euSize * evSize * 2 + euSize + j;
			f = euSize * j;
			edges[ec] = new Wedge(k, ec + el, f, euSize * j * 2);
			edges[ec + el] = new Wedge(k + uSize, ec, -1, ec + 1 + el);
			if (j == evSize - 1)
				edges[ec + el].next = euSize * evSize * 2 + el;
		}
	}

	/**
	 * The constructor takes two arguments. v[] is an array containing the
	 * vertices. faces[][] is an array of arrays containing the indices of the
	 * vertices which define each face. The vertices for each face must be
	 * listed in order, such that they go counter-clockwise when viewed from the
	 * outside of the mesh. All faces must have a consistent vertex order, such
	 * that the object has a well defined outer surface. This is true even if
	 * the mesh does not form a closed surface. It is an error to call the
	 * constructor with a faces[][] array which does not meet this condition,
	 * and the results are undefined.
	 * 
	 * @param v
	 *            Array of vertices positions
	 * @param f
	 *            Array of arrays describing vertex indices for each face
	 */
	public PolyMesh(Vec3[] v, int[][] f) {
		initialize();
		vertices = new Wvertex[v.length];
		faces = new Wface[f.length];
		int count = 0;
		for (int i = 0; i < f.length; i++)
			count += f[i].length;
		edges = new Wedge[count * 2];
		count = 0;
		int next, ed, prevEdge, zeroEdge;
		for (int i = 0; i < f.length; i++) {
			zeroEdge = prevEdge = -1;
			for (int j = 0; j < f[i].length; ++j) {
				next = j + 1;
				if (next == f[i].length)
					next = 0;
				ed = getEdge(f[i][next], f[i][j]);
				if (ed != -1) {
					edges[edges[ed].hedge].face = i;
					if (prevEdge != -1)
						edges[prevEdge].next = edges[ed].hedge;
					prevEdge = edges[ed].hedge;
				} else {
					edges[count] = new Wedge(f[i][next], count + edges.length
							/ 2, i, prevEdge);
					edges[count + edges.length / 2] = new Wedge(f[i][j], count,
							-1, -1);
					if (prevEdge != -1)
						edges[prevEdge].next = count;
					prevEdge = count++;
				}
				if (vertices[f[i][j]] == null)
					vertices[f[i][j]] = new Wvertex(v[f[i][j]], prevEdge);
				if (j == 0)
					zeroEdge = prevEdge;
				if (j == f[i].length - 1)
					edges[prevEdge].next = zeroEdge;
			}
			faces[i] = new Wface(prevEdge);
		}
		int n;
		for (int i = 0; i < edges.length; i++) {
			if (edges[i] != null && edges[i].face == -1 && edges[i].next == -1) {
				n = edges[i].vertex;
				for (int j = 0; j < edges.length; j++) {
					if (edges[j] != null && edges[edges[j].hedge].vertex == n
							&& edges[edges[j].hedge].face == -1
							&& edges[edges[j].hedge].next == -1) {
						edges[i].next = edges[j].hedge;
						break;
					}
				}
			}
		}

		// edges reduction
		int edgeCount = 0;
		int[] edgeTable = new int[edges.length];
		for (int i = 0; i < edges.length; ++i) {
			if (edges[i] != null)
				edgeTable[i] = edgeCount++;
			else
				edgeTable[i] = -1;

		}
		Wedge[] newEdges = new Wedge[edgeCount];
		for (int i = 0; i < edges.length; ++i) {
			if (edges[i] != null) {
				newEdges[edgeTable[i]] = new Wedge(edges[i]);
				newEdges[edgeTable[i]].hedge = edgeTable[edges[i].hedge];
				newEdges[edgeTable[i]].next = edgeTable[edges[i].next];
			}
		}
		for (int i = 0; i < vertices.length; ++i)
			vertices[i].edge = edgeTable[vertices[i].edge];
		for (int i = 0; i < faces.length; ++i)
			faces[i].edge = edgeTable[faces[i].edge];
		edges = newEdges;
		setSkeleton(new Skeleton());
	}

	/**
	 * Get the bounding box for the mesh. This is always the bounding box for
	 * the unsmoothed control mesh. If the smoothing method is set to
	 * approximating, the final surface may not actually touch the sides of this
	 * box. If the smoothing method is set to interpolating, the final surface
	 * may actually extend outside this box.
	 * 
	 * @return The bounds value
	 */

	public BoundingBox getBounds() {
		if (bounds == null)
			findBounds();
		return bounds;
	}

	/**
	 * Copy all the properties of another object, to make this one identical to
	 * it. If the two objects are of different classes, this will throw a
	 * ClassCastException.
	 * 
	 * @param obj
	 *            Object to copy
	 */

	public void copyObject(Object3D obj) {
		PolyMesh mesh = (PolyMesh) obj;

		texParam = null;
		vertices = new Wvertex[mesh.vertices.length];
		edges = new Wedge[mesh.edges.length];
		faces = new Wface[mesh.faces.length];
		for (int i = 0; i < mesh.vertices.length; i++)
			vertices[i] = new Wvertex(mesh.vertices[i]);
		for (int i = 0; i < mesh.edges.length; i++)
			edges[i] = new Wedge(mesh.edges[i]);
		for (int i = 0; i < mesh.faces.length; i++)
			faces[i] = new Wface(mesh.faces[i]);
		if (skeleton == null)
			skeleton = mesh.skeleton.duplicate();
		else
			skeleton.copy(mesh.skeleton);
		setSmoothingMethod(mesh.getSmoothingMethod());
		mirrorState = mesh.getMirrorState();
		closed = mesh.closed;
		copyTextureAndMaterial(obj);
		cachedMesh = null;
		cachedWire = null;
		cachedNormals = null;
		cachedEdgeNormals = null;
		cachedFaceNormals = null;
		bounds = null;
		controlledSmoothing = mesh.controlledSmoothing;
		minAngle = mesh.minAngle;
		maxAngle = mesh.maxAngle;
		minSmoothness = mesh.minSmoothness;
		maxSmoothness = mesh.maxSmoothness;
		interactiveSmoothLevel = mesh.interactiveSmoothLevel;
		projectedEdges = null;
		if (mesh.mappingData != null) {
			mappingData = mesh.mappingData.duplicate();
			mappingVerts = mesh.mappingVerts;
			mappingEdges = mesh.mappingEdges;
			mappingFaces = mesh.mappingFaces;
		} else {
			mappingData = null;
		}
		if (mesh.seams != null) {
			seams = new boolean[mesh.seams.length];
			for (int i = 0; i < seams.length; i++)
				seams[i] = mesh.seams[i];
		}
		useCustomColors = mesh.useCustomColors;
		vertColor = mesh.vertColor;
		selectedVertColor = mesh.selectedVertColor;
		edgeColor = mesh.edgeColor;
		selectedEdgeColor = mesh.selectedEdgeColor;
		seamColor = mesh.seamColor;
		selectedSeamColor = mesh.selectedSeamColor;
		meshColor = mesh.meshColor;
		selectedFaceColor = mesh.selectedFaceColor;
		meshRGBColor = mesh.meshRGBColor;
		selectedFaceRGBColor = mesh.selectedFaceRGBColor;
		handleSize = mesh.handleSize;
	}

	/**
	 * Gets the smoothing method applied to this PolyMesh object
	 * 
	 * @return The smoothingMethod value
	 */
	public int getSmoothingMethod() {
		return smoothingMethod;
	}

	/**
	 * Sets the smoothing method applied to this PolyMesh object
	 * 
	 * @param smoothingMethod
	 *            The new smoothingMethod value
	 */
	public void setSmoothingMethod(int smoothingMethod) {
		this.smoothingMethod = smoothingMethod;
		resetMesh();
	}

	/**
	 * Create a new object which is an exact duplicate of this one.
	 * 
	 * @return A duplicate of the winged mesh object
	 */

	public Object3D duplicate() {
		PolyMesh mesh = new PolyMesh();
		mesh.copyObject(this);
		return mesh;
	}

	/**
	 * Returns a wireframe mesh for the PolyMesh object
	 * 
	 * @return The wireframe mesh
	 */
	public WireframeMesh getWireframeMesh() {
		Vec3 point[];
		int from[];
		int to[];

		if (cachedWire != null)
			return cachedWire;
		if (mirrorState != NO_MIRROR) {
			if (mirroredMesh == null)
				getMirroredMesh();
			cachedWire = mirroredMesh.getWireframeMesh();
			return cachedWire;
		}
		point = new Vec3[vertices.length];
		from = new int[edges.length];
		to = new int[edges.length];
		for (int i = 0; i < vertices.length; ++i)
			point[i] = vertices[i].r;
		for (int i = 0; i < edges.length / 2; ++i) {
			from[i] = edges[i].vertex;
			to[i] = edges[edges[i].hedge].vertex;
		}
		return (cachedWire = new WireframeMesh(point, from, to));
	}

	/**
	 * Returns a rendering mesh for the PolyMesh object
	 * 
	 * @param tol
	 *            Tolerance
	 * @param interactive
	 *            Scene or rendering?
	 * @param info
	 *            The object info holding this mesh
	 * @return The rendering mesh
	 */
	public RenderingMesh getRenderingMesh(double tol, boolean interactive,
			ObjectInfo info) {
		if (interactive && cachedMesh != null)
			return cachedMesh;
		RenderingMesh rend = null;
		if (mirrorState != NO_MIRROR) {
			if (mirroredMesh == null)
				getMirroredMesh();
			rend = mirroredMesh.getRenderingMesh(tol, interactive, info);
			closed = mirroredMesh.isClosed();
			subdividedMesh = mirroredMesh.getSubdividedMesh();
			if (interactive)
				cachedMesh = rend;
			return rend;
		}
		closed = true;
		for (int i = 0; i < edges.length / 2; ++i) {
			if ((edges[i].face == -1) || (edges[edges[i].hedge].face == -1)) {
				closed = false;
				break;
			}
		}
		//long time = new Date().getTime();
		if (smoothingMethod == Mesh.APPROXIMATING) {
			// long t = System.currentTimeMillis();
			//PolyMesh mesh = (PolyMesh) duplicate();
			// System.out.println(System.currentTimeMillis() - t);
			// mesh.smoothingMethod = NO_SMOOTHING;
			if (interactive) {
				//mesh.smoothingMethod = NO_SMOOTHING;
//				for (int i = 0; i < interactiveSmoothLevel; ++i)
//					mesh.smoothWholeMesh(i, true, smoothingMethod, false);
//				cachedMesh = mesh.getRenderingMeshQuadCase();
				//long time = System.currentTimeMillis(); 
				QuadMesh qmesh = smoothWholeMesh(tol, true, interactiveSmoothLevel, false);
				cachedMesh = qmesh.getRenderingMesh();
				//time = System.currentTimeMillis() - time;
				//System.out.println( time/1000.0 );
				subdividedMesh = qmesh;
				return cachedMesh;
			} else {
				long time = System.currentTimeMillis(); 
//				mesh.printSize();
//				for (int i = 0; i < 6/*renderingSmoothLevel*/; ++i) {
//					mesh.smoothWholeMesh(i, false, smoothingMethod, false);
//					mesh.printSize();
//				}
//				mesh.finalSmoothing = true;
//				cachedMesh = mesh.getRenderingMesh(tol, interactive, info);
				QuadMesh qmesh = smoothWholeMesh(tol, false, Integer.MAX_VALUE, false);
				//System.out.println("vertices: " + qmesh.getVertices().length);
				cachedMesh = qmesh.getRenderingMesh();
				//time = System.currentTimeMillis() - time;
				//System.out.println( time/1000.0 );
				return cachedMesh;
			}
		}
		TextureMapping texMapping = getTextureMapping();
		vert = new Vector();
		v1 = new Vector();
		v2 = new Vector();
		v3 = new Vector();
		vertInfo = new Vector();
		faceInfo = new Vector();
		for (int i = 0; i < vertices.length; ++i) {
			vert.addElement(vertices[i].r);
			vertInfo.add(new VertexParamInfo(new int[] { i },
					new double[] { 1.0 }));
		}
		for (int i = 0; i < faces.length; ++i) {
			int[] vf = getFaceVertices(faces[i]);
			if (vf.length == 3) {
				v1.addElement(new Integer(vf[0]));
				v2.addElement(new Integer(vf[1]));
				v3.addElement(new Integer(vf[2]));
				faceInfo.add(new Integer(i));

			} else if (vf.length > 3) {
				triangulate(vf, i, false);
			}
		}
		Vec3[] vertArray = new Vec3[vert.size()];
		for (int i = 0; i < vertArray.length; ++i)
			vertArray[i] = (Vec3) vert.elementAt(i);

			RenderingTriangle[] tri = new RenderingTriangle[v1.size()];
			for (int i = 0; i < v1.size(); ++i)
				tri[i] = texMapping.mapTriangle(((Integer) v1.elementAt(i))
						.intValue(), ((Integer) v2.elementAt(i)).intValue(),
						((Integer) v3.elementAt(i)).intValue(), 0, 0, 0,
						vertArray);
			rend = new RenderingMesh(vertArray, new Vec3[] { null }, tri,
					texMapping, getMaterialMapping());
			ParameterValue oldParamVal[] = getParameterValues();
			if (oldParamVal != null) {
				ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
				for (int i = 0; i < oldParamVal.length; i++) {
					if (oldParamVal[i] instanceof VertexParameterValue) {
						double oldval[] = ((VertexParameterValue) oldParamVal[i])
								.getValue();
						double newval[] = new double[vert.size()];
						for (int j = 0; j < vert.size(); ++j) {
							int[] vf = ((VertexParamInfo) vertInfo.elementAt(j)).vert;
							double[] coef = ((VertexParamInfo) vertInfo
									.elementAt(j)).coef;
							for (int k = 0; k < vf.length; ++k)
								newval[j] += coef[k] * oldval[vf[k]];
						}
						newParamVal[i] = new VertexParameterValue(newval);

					} else if (oldParamVal[i] instanceof FaceParameterValue) {
						double oldval[] = ((FaceParameterValue) oldParamVal[i])
								.getValue();
						double newval[] = new double[faceInfo.size()];
						for (int j = 0; j < newval.length; ++j)
							newval[j] = oldval[((Integer) faceInfo.elementAt(j))
									.intValue()];
						newParamVal[i] = new FaceParameterValue(newval);
					} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
						FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
						double newval[][] = new double[v1.size()][3];
						for (int j = 0; j < v1.size(); ++j) {
							for (int k = 0; k < 3; k++) {
								int vertex = -1;
								switch (k) {
								case 0:
									vertex = ((Integer) v1.elementAt(j))
											.intValue();
									break;
								case 1:
									vertex = ((Integer) v2.elementAt(j))
											.intValue();
									break;
								case 2:
									vertex = ((Integer) v3.elementAt(j))
											.intValue();
									break;
								}
								int pmeFace = ((Integer) faceInfo.elementAt(j))
										.intValue();
								int[] fv = getFaceVertices(faces[pmeFace]);
								int[] vf = ((VertexParamInfo) vertInfo
										.elementAt(vertex)).vert;
								double[] coef = ((VertexParamInfo) vertInfo
										.elementAt(vertex)).coef;
								for (int l = 0; l < vf.length; ++l) {
									int vv = -1;
									for (int m = 0; m < fv.length; ++m) {
										if (fv[m] == vf[l]) {
											vv = m;
											break;
										}
									}
									if (vv == -1) {
										System.out
												.println("pb per face per vertex : point doesn't belong to face");
										vv = 0;
									}
									newval[j][k] += coef[l]
											* fvpv.getValue(pmeFace, vv);
								}

							}
						}
						newParamVal[i] = new FaceVertexParameterValue(newval);
					} else
						newParamVal[i] = oldParamVal[i].duplicate();
				}
				rend.setParameters(newParamVal);
			}
		
		if (interactive)
			cachedMesh = rend;
		return rend;
	}

	/**
	 * returns the subdivided polymesh after a call to interactive
	 * getRenderingMesh()
	 * 
	 * @return Subdivided polymesh
	 */
	public QuadMesh getSubdividedMesh() {
		if (smoothingMethod == Mesh.APPROXIMATING) {
			return subdividedMesh;
		} else {
			return null;
		}
	}

	protected int[] getInvMirroredVerts() {
		if (mirrorState != NO_MIRROR) {
			if (invMirroredVerts == null)
				getMirroredMesh();
		}
		return invMirroredVerts;
	}

	protected int[] getInvMirroredEdges() {
		if (mirrorState != NO_MIRROR) {
			if (invMirroredEdges == null)
				getMirroredMesh();
		}
		return invMirroredEdges;
	}

	protected int[] getInvMirroredFaces() {
		if (mirrorState != NO_MIRROR) {
			if (invMirroredFaces == null)
				getMirroredMesh();
		}
		return invMirroredFaces;
	}

	/**
	 * Returns a rendering mesh for the PolyMesh object smoothed as quads
	 * 
	 * @return The rendering mesh
	 */
	public RenderingMesh getRenderingMeshQuadCase() {
		RenderingMesh rend = null;
		TextureMapping texMapping = getTextureMapping();
		int[] tableInfoVec = new int[2 * faces.length];
		int[] v1vec = new int[2 * faces.length];
		int[] v2vec = new int[2 * faces.length];
		int[] v3vec = new int[2 * faces.length];
		int[][] pfpvTable = new int[2 * faces.length][3];
		int index = 0;
		closed = true;
		for (int i = 0; i < edges.length / 2; ++i) {
			if ((edges[i].face == -1) || (edges[edges[i].hedge].face == -1)) {
				closed = false;
				break;
			}
		}
		for (int i = 0; i < faces.length; ++i) {
			int[] vf = getFaceVertices(faces[i]);
			if (vf.length == 3) {
				v1vec[index] = vf[0];
				v2vec[index] = vf[1];
				v3vec[index] = vf[2];
				pfpvTable[index][0] = 0;
				pfpvTable[index][1] = 1;
				pfpvTable[index][2] = 2;
				tableInfoVec[index++] = i;
			} else if (vf.length == 4) {
				v1vec[index] = vf[0];
				v2vec[index] = vf[1];
				v3vec[index] = vf[2];
				pfpvTable[index][0] = 0;
				pfpvTable[index][1] = 1;
				pfpvTable[index][2] = 2;
				tableInfoVec[index++] = i;
				v1vec[index] = vf[2];
				v2vec[index] = vf[3];
				v3vec[index] = vf[0];
				pfpvTable[index][0] = 2;
				pfpvTable[index][1] = 3;
				pfpvTable[index][2] = 0;
				tableInfoVec[index++] = i;
			}
		}
		Vec3[] vertArray = new Vec3[vertices.length];
		for (int i = 0; i < vertArray.length; i++)
			vertArray[i] = vertices[i].r;
		RenderingTriangle[] tri = new RenderingTriangle[index];
		for (int i = 0; i < index; ++i)
			tri[i] = texMapping.mapTriangle(v1vec[i], v2vec[i], v3vec[i], 0, 0,
					0, vertArray);
		rend = new RenderingMesh(vertArray, new Vec3[] { null }, tri,
				texMapping, getMaterialMapping());
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[index];
					for (int j = 0; j < newval.length; ++j)
						newval[j] = oldval[tableInfoVec[j]];
					newParamVal[i] = new FaceParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
					double newval[][] = new double[index][3];
					for (int j = 0; j < index; ++j) {
						for (int k = 0; k < 3; k++) {
							newval[j][k] = fvpv.getValue(tableInfoVec[j],
									pfpvTable[j][k]);
						}
					}
					newParamVal[i] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[i] = oldParamVal[i].duplicate();
			}
			rend.setParameters(newParamVal);
		}
		return rend;
	}

	/**
	 * Finds the vertices around a given face
	 * 
	 * @param f
	 *            Face to find the vertices for
	 * @return Array of indices for vertices bordering the face
	 */
	public int[] getFaceVertices(Wface f) {
		Wedge e = edges[f.edge];
		int start = e.vertex;
		int count = 1;
		while (edges[e.next].vertex != start) {
			++count;
			if (count > edges.length) {
				System.out
						.println("Error in getFaceVertices : face is not closed");
				System.out.println(f.edge);
				return null;
			}
			e = edges[e.next];
		}
		int[] v = new int[count];
		e = edges[f.edge];
		v[0] = e.vertex;
		count = 0;
		while (edges[e.next].vertex != start) {
			e = edges[e.next];
			v[++count] = e.vertex;
		}
		return v;
	}
	
	/**
	 * Return the number vertices around a given face
	 * 
	 * @param f
	 *            Face to find the number of vertices for
	 * @return The number of vertices
	 */
	public int getFaceVertCount(Wface f) {
		Wedge e = edges[f.edge];
		int start = e.vertex;
		int count = 1;
		while (edges[e.next].vertex != start) {
			++count;
			if (count > edges.length) {
				System.out
						.println("Error in getFaceVertices : face is not closed");
				System.out.println(f.edge);
				return -1;
			}
			e = edges[e.next];
		}
		return count;
	}

	/**
	 * Finds the vertices around a given face, useful when computing a new mesh
	 * and new sets of mesh edges and faces are used
	 * 
	 * @param ne
	 *            set of edges
	 * @param nf
	 *            set of faces
	 * @param f
	 *            Face to find the vertices for
	 * @return Array of indices for vertices bordering the face
	 */
	public int[] getFaceVertices(int f, Wedge[] ne, Wface[] nf) {
		Wedge e = ne[nf[f].edge];
		int start = e.vertex;
		int count = 1;
		while (ne[e.next].vertex != start) {
			++count;
			if (count > ne.length) {
				System.out
						.println("Error in getFaceVertices : face is not closed");
				System.out.println(nf[f].edge);
				return null;
			}
			e = ne[e.next];
		}
		int[] v = new int[count];
		e = ne[nf[f].edge];
		v[0] = e.vertex;
		count = 0;
		while (ne[e.next].vertex != start) {
			e = ne[e.next];
			v[++count] = e.vertex;
		}
		return v;
	}

	/**
	 * Finds the edges around a given face
	 * 
	 * @param f
	 *            Face to find the edges for
	 * @return Array of indices for edges bordering the face
	 */
	public int[] getFaceEdges(Wface f) {
		Wedge e = edges[f.edge];
		int start = f.edge;
		int count = 1;
		while (e.next != start) {
			++count;
			if (count > edges.length) {
				System.out.println("Error getFaceEdges: face is not closed");
				System.out.println(f.edge);
				return null;
			}
			e = edges[e.next];
		}
		int[] fe = new int[count];
		e = edges[f.edge];
		fe[0] = f.edge;
		count = 0;
		while (e.next != start) {
			fe[++count] = e.next;
			e = edges[e.next];
		}
		return fe;
	}

	/**
	 * Finds the edges that border a given vertex
	 * 
	 * @param v
	 *            The vertex for which bordering edges are requested
	 * @return Array of indices for edges around the specified vertex
	 */
	public int[] getVertexEdges(Wvertex v) {
		Wedge e = edges[v.edge];
		int start = v.edge;
		int count = 1;
		while (edges[e.hedge].next != start) {
			++count;
			if (count > edges.length) {
				System.out.println("Error : too many edges around a vertex");
				System.out.println("ref " + v.edge);
				return null;
			}
			e = edges[edges[e.hedge].next];
		}
		int[] ed = new int[count];
		e = edges[v.edge];
		ed[0] = v.edge;
		count = 0;
		while (edges[e.hedge].next != start) {
			ed[++count] = edges[e.hedge].next;
			e = edges[edges[e.hedge].next];
		}
		return ed;
	}

	/**
	 * Finds the previous edge for a given edge
	 * 
	 * @param nEdges
	 *            The edges list (useful when a new edge list is being computed)
	 * @param start
	 *            Index of the edge to find the previous edge of
	 * @return Index of the previous edge
	 */
	public int getPreviousEdge(Wedge[] nEdges, int start) {
		Wedge e = nEdges[start];
		int count = 1;
		while (nEdges[e.hedge].next != start) {
			++count;
			if (count > nEdges.length) {
				System.out
						.println("Error : too many edges around a vertex (tmp edges)");
				System.out.println("edge : " + start);
				return -1;
			}
			e = nEdges[nEdges[e.hedge].next];
		}
		return e.hedge;
	}

	/**
	 * Finds the previous edge for a given edge
	 * 
	 * @param index
	 *            Index of the edge to find the previous edge of
	 * @return Index of the previous edge
	 */
	public int getPreviousEdge(int index) {
		Wedge e = edges[index];
		int count = 1;
		while (edges[e.hedge].next != index) {
			++count;
			if (count > edges.length) {
				System.out.println("Error : too many edges around a vertex");
				System.out.println("edge : " + index);
				return -1;
			}
			e = edges[edges[e.hedge].next];
		}
		return e.hedge;
	}

	/**
	 * Triangulates selected faces Ear clipping algorithm is used
	 * 
	 * @param selected
	 *            Faces selected for etriangulation
	 * @return The new face selection
	 */
	public boolean[] triangulateFaces(boolean[] selected) {
		int vert1;
		int vert2;
		int vert3;
		int e1;
		int e2;
		int e3;
		int e1p;
		int e2p;
		int e3p;
		int vertCount = vertices.length;
		int edgeCount;
		int faceCount;
		int face;
		int[] vf;
		Vector faceTable = new Vector();
		Vector vertTable = new Vector();
		HashMap facesTextureIndexMap = null;

		// dumpMesh();
		// first let's record any per face per vertex texture parameter
		// System.out.println(vertices.length);
		facesTextureIndexMap = recordFacesTexture(selected);
		vert = new Vector();
		for (int i = 0; i < vertices.length; ++i)
			vert.addElement(vertices[i].r);
		for (int i = 0; i < selected.length; i++) {
			if (selected[i]) {
				vf = getFaceVertices(faces[i]);
				if (vf.length == 3)
					continue;
				v1 = new Vector();
				v2 = new Vector();
				v3 = new Vector();
				vertInfo = new Vector();
				faceInfo = new Vector();
				/*
				 * s1 = null; s2 = null; s3 = null;
				 */
				triangulate(vf, i, false);
				Wvertex[] newVertices = new Wvertex[vert.size()];
				Wedge[] newEdges = new Wedge[v1.size() * 6 + edges.length];
				Wface[] newFaces = new Wface[faces.length - 1 + v1.size()];
				translateMesh(newVertices, newEdges, newFaces);
				edgeCount = edges.length / 2;
				faceCount = faces.length;
				for (int j = vertices.length; j < vert.size(); ++j) {
					newVertices[j] = new Wvertex((Vec3) vert.elementAt(j), -1);
					// newVertices[j].smoothness =
					// faces[i].centerSmoothness;
					vertTable.add(new Integer(edges[faces[i].edge].vertex));
				}
				// System.out.println( v1.size() );
				for (int j = 0; j < v1.size(); ++j) {
					vert1 = ((Integer) v1.elementAt(j)).intValue();
					vert2 = ((Integer) v2.elementAt(j)).intValue();
					vert3 = ((Integer) v3.elementAt(j)).intValue();
					// System.out.println( vert1 + " " + vert2 + " " + vert3
					// );
					face = -1;
					e1 = findNewEdge(vert1, vert2, newVertices, newEdges);
					if (e1 >= 0) {
						face = newEdges[e1].face;
						e1p = newEdges[e1].hedge;
						while (newEdges[e1p].next != e1)
							e1p = newEdges[newEdges[e1p].next].hedge;
					} else {
						e1 = edgeCount++;
						e1p = -1;
						newEdges[e1] = new Wedge(vert2, e1 + newEdges.length
								/ 2, faceCount, -1);
						newEdges[e1 + newEdges.length / 2] = new Wedge(vert1,
								e1, -1, -1);
						newEdges[e1].smoothness = 1.0f;
						newEdges[e1 + newEdges.length / 2].smoothness = 1.0f;
					}
					e2 = findNewEdge(vert2, vert3, newVertices, newEdges);
					if (e2 >= 0) {
						face = newEdges[e2].face;
						e2p = newEdges[e2].hedge;
						while (newEdges[e2p].next != e2)
							e2p = newEdges[newEdges[e2p].next].hedge;
					} else {
						e2 = edgeCount++;
						e2p = -1;
						newEdges[e2] = new Wedge(vert3, e2 + newEdges.length
								/ 2, faceCount, -1);
						newEdges[e2 + newEdges.length / 2] = new Wedge(vert2,
								e2, -1, -1);
						newEdges[e2].smoothness = 1.0f;
						newEdges[e2 + newEdges.length / 2].smoothness = 1.0f;
					}
					e3 = findNewEdge(vert3, vert1, newVertices, newEdges);
					if (e3 >= 0) {
						face = newEdges[e3].face;
						e3p = newEdges[e3].hedge;
						while (newEdges[e3p].next != e3)
							e3p = newEdges[newEdges[e3p].next].hedge;
					} else {
						e3 = edgeCount++;
						e3p = -1;
						newEdges[e3] = new Wedge(vert1, e3 + newEdges.length
								/ 2, faceCount, -1);
						newEdges[e3 + newEdges.length / 2] = new Wedge(vert3,
								e3, -1, -1);
						newEdges[e3].smoothness = 1.0f;
						newEdges[e3 + newEdges.length / 2].smoothness = 1.0f;
					}
					if (j == v1.size() - 1) {
						newEdges[e1].next = e2;
						newEdges[e2].next = e3;
						newEdges[e3].next = e1;
						newEdges[e1].face = i;
						newEdges[e2].face = i;
						newEdges[e3].face = i;
						newFaces[i].edge = e1;
					} else {
						if (e1p >= 0 && e2p < 0) {
							newEdges[e2 + newEdges.length / 2].next = newEdges[e1].next;
							newEdges[e2 + newEdges.length / 2].face = newEdges[e1].face;
						} else if (e2p < 0) {
							newEdges[e2 + newEdges.length / 2].next = newEdges[e1].hedge;
							newEdges[e2 + newEdges.length / 2].face = face;
						}
						newEdges[e1].next = e2;
						newEdges[e1].face = faceCount;
						if (e2p >= 0 && e3p < 0) {
							newEdges[e3 + newEdges.length / 2].next = newEdges[e2].next;
							newEdges[e3 + newEdges.length / 2].face = newEdges[e2].face;
						} else if (e3p < 0) {
							newEdges[e3 + newEdges.length / 2].next = newEdges[e2].hedge;
							newEdges[e3 + newEdges.length / 2].face = face;
						}
						newEdges[e2].next = e3;
						newEdges[e2].face = faceCount;
						if (e3p >= 0 && e1p < 0) {
							newEdges[e1 + newEdges.length / 2].next = newEdges[e3].next;
							newEdges[e1 + newEdges.length / 2].face = newEdges[e3].face;
						} else if (e1p < 0) {
							newEdges[e1 + newEdges.length / 2].next = newEdges[e3].hedge;
							newEdges[e1 + newEdges.length / 2].face = face;
						}
						newEdges[e3].next = e1;
						newEdges[e3].face = faceCount;
						if (e1p >= 0 && e1p != e3)
							newEdges[e1p].next = newEdges[e3].hedge;
						if (e2p >= 0 && e2p != e1)
							newEdges[e2p].next = newEdges[e1].hedge;
						if (e3p >= 0 && e3p != e2)
							newEdges[e3p].next = newEdges[e2].hedge;

						newFaces[faceCount] = new Wface(faces[i]);
						newFaces[faceCount].edge = e1;
						faceTable.addElement(new Integer(i));
						++faceCount;
					}
					newVertices[vert1].edge = e1;
					newVertices[vert2].edge = e2;
					newVertices[vert3].edge = e3;
				}
				// edges reduction
				edgeCount = 0;
				int[] edgeTable = new int[newEdges.length];
				for (int j = 0; j < newEdges.length; ++j) {
					if (newEdges[j] != null)
						edgeTable[j] = edgeCount++;
					else
						edgeTable[j] = -1;
				}
				if (edgeCount < newEdges.length) {
					edges = new Wedge[edgeCount];
					for (int j = 0; j < newEdges.length; ++j) {
						if (edgeTable[j] != -1) {
							edges[edgeTable[j]] = newEdges[j];
							edges[edgeTable[j]].hedge = edgeTable[newEdges[j].hedge];
							edges[edgeTable[j]].next = edgeTable[newEdges[j].next];
						}
					}
					for (int j = 0; j < newVertices.length; ++j)
						newVertices[j].edge = edgeTable[newVertices[j].edge];
					for (int j = 0; j < newFaces.length; ++j)
						newFaces[j].edge = edgeTable[newFaces[j].edge];
				} else
					edges = newEdges;
				vertices = newVertices;
				faces = newFaces;
			}
		}
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[faces.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = oldval[((Integer) faceTable.elementAt(j
								- oldval.length)).intValue()];
					newParamVal[k] = new FaceParameterValue(newval);
				} else if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[vertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = oldval[((Integer) vertTable.elementAt(j
								- oldval.length)).intValue()];
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[faces.length][3];
					int oldFaceCount = fvpv.getFaceCount();
					int faceRef;
					for (int j = 0; j < faces.length; ++j) {
						if (j < oldFaceCount && !selected[j]) {
							newval[j] = new double[fvpv.getFaceVertexCount(j)];
							for (int l = 0; l < newval[j].length; l++)
								newval[j][l] = fvpv.getValue(j, l);
						} else {
							if (j < oldFaceCount)
								faceRef = j;
							else
								faceRef = ((Integer) faceTable.elementAt(j
										- oldFaceCount)).intValue();
							int[] newIndices = getFaceVertices(faces[j]);
							int[] oldIndices = (int[]) facesTextureIndexMap
									.get(new Integer(faceRef));
							for (int m = 0; m < 2; m++) {
								if (newIndices[m] >= vertCount) {
									double meanVal = 0;
									for (int l = 0; l < oldIndices.length; l++)
										meanVal += fvpv.getValue(faceRef, l);
									meanVal /= oldIndices.length;
									newval[j][m] = meanVal;
								} else
									for (int l = 0; l < oldIndices.length; l++) {
										if (newIndices[m] == oldIndices[l]) {
											newval[j][m] = fvpv.getValue(
													faceRef, l);
										}
									}
							}
						}
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		boolean sel[] = new boolean[faces.length];
		for (int i = 0; i < selected.length; ++i)
			sel[i] = selected[i];
		for (int i = selected.length; i < sel.length; ++i)
			sel[i] = true;
		return sel;
	}

	/**
	 * Records the vertices for each selected face or every face is the
	 * selection is set to null
	 * 
	 * @return a HashMap containing for each face the list of vertices
	 */
	private HashMap recordFacesTexture(boolean[] selected) {
		HashMap facesTextureIndexMap = null;
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					facesTextureIndexMap = new HashMap();
					for (int i = 0; i < faces.length; i++) {
						if (selected == null || selected[i])
							facesTextureIndexMap.put(new Integer(i),
									getFaceVertices(faces[i]));
					}
					break;
				}
			}
		}
		return facesTextureIndexMap;
	}

	/**
	 * Given a face represented by an array of vertices indexes, this function
	 * yields the triangulated mesh for the face (ear cutting algorithm). This
	 * method is also used to compute face areas.
	 * 
	 * @param vf
	 *            Array of vertices indexes
	 * @param face
	 *            Face index
	 * @param areaOnly
	 *            Set this flag to true if you only want to compute face area
	 * @return Face area.
	 */
	private double triangulate(int[] vf, int face, boolean areaOnly) {
		int fe[] = null;
		if (!areaOnly)
			fe = getFaceEdges(faces[face]);
		boolean deleted[] = new boolean[vf.length];
		int start = -1;
		int prev;
		int pi, pnext, pprev, ppprev;
		int next;
		Vec3 norm = new Vec3();
		Vec3 v = new Vec3();
		double area = 0.0;
		Vec3 tmp;
		int i1;
		int i2;
		int i3;

		for (int i = 0; i < vf.length; ++i)
			v.add(vertices[vf[i]].r);
		v.scale(1.0 / (vf.length * 1.0));
		for (int i = 0; i < vf.length; ++i) {
			next = getNext(i, deleted);
			norm.add(vertices[vf[i]].r.minus(v).cross(
					vertices[vf[next]].r.minus(v)));
		}
		for (int i = 0; i < vf.length; ++i) {
			prev = getPrev(i, deleted);
			next = getNext(i, deleted);
			Vec3 crossVec = vertices[vf[i]].r.minus(vertices[vf[prev]].r)
					.cross(vertices[vf[next]].r.minus(vertices[vf[i]].r));
			double product = crossVec.dot(norm);
			if (product >= 0) {
				start = i;
				break;
			}
		}
		int i = start + 1;
		int count = 0;
		// as ear cutting occurs, deleted (cut) vertices are stored in deleted
		// array
		while (count < getDelLength(deleted) && getDelLength(deleted) > 3) {
			// first, find a proper starting point with negative dot product
			// of neighbor segments
			if (i >= vf.length)
				i -= vf.length;
			prev = getPrev(i, deleted);
			next = getNext(i, deleted);
			Vec3 crossVec = vertices[vf[next]].r.minus(vertices[vf[i]].r)
					.cross(vertices[vf[prev]].r.minus(vertices[vf[i]].r));
			double product = crossVec.dot(norm);
			if (product >= 0) {
				++count;
				++i;
			}
			// OK
			else {
				// ear cutting : we can start here
				count = 0;
				pi = prev;
				pnext = i;
				pprev = getPrev(pi, deleted);
				ppprev = getPrev(pprev, deleted);
				crossVec = vertices[vf[pnext]].r.minus(vertices[vf[pi]].r)
						.cross(vertices[vf[pprev]].r.minus(vertices[vf[pi]].r));
				product = crossVec.dot(norm);
				if (product >= 0) {
					if (ppprev != pprev && ppprev != pi && ppprev != pnext) {
						if (ptInTriangle(vertices[vf[pprev]].r,
								vertices[vf[pi]].r, vertices[vf[pnext]].r,
								vertices[vf[ppprev]].r)) {
							next = pnext;
							i = pi;
							prev = pprev;
							if (i == start)
								--start;
						}
					}
				} else {
					next = pnext;
					i = pi;
					prev = pprev;
					if (i == start)
						--start;
				}
				i1 = vf[getPrev(prev, deleted)];
				i2 = vf[prev];
				i3 = vf[i];
				if (!areaOnly)
					v1.add(new Integer(i1));
				if (!areaOnly)
					v2.add(new Integer(i2));
				if (!areaOnly)
					v3.add(new Integer(i3));
				if (faceInfo != null)
					faceInfo.add(new Integer(face));
				tmp = vertices[i1].r.cross(vertices[i2].r);
				tmp.add(vertices[i2].r.cross(vertices[i3].r));
				tmp.add(vertices[i3].r.cross(vertices[i1].r));
				area += tmp.length() / 2;
				deleted[prev] = true;
			}
		}
		count = 0;
		v = new Vec3();
		for (i = 0; i < vf.length; ++i) {
			if (!deleted[i]) {
				v.add(vertices[vf[i]].r);
				++count;
			}
		}
		// array v now contains the vertices for trully convex face
		if (count == 3) {
			// simple triangle
			for (i = 0; i < vf.length; ++i) {
				if (!deleted[i]) {
					i1 = vf[prev = getPrev(i, deleted)];
					i2 = vf[i];
					i3 = vf[next = getNext(i, deleted)];
					if (!areaOnly)
						v1.addElement(new Integer(i1));
					if (!areaOnly)
						v2.addElement(new Integer(i2));
					if (!areaOnly)
						v3.addElement(new Integer(i3));
					if (faceInfo != null)
						faceInfo.add(new Integer(face));
					tmp = vertices[i1].r.cross(vertices[i2].r);
					tmp.add(vertices[i2].r.cross(vertices[i3].r));
					tmp.add(vertices[i3].r.cross(vertices[i1].r));
					area += tmp.length() / 2;
					break;
				}
			}
		} else if (count == 4) {
			// quad
			// let's find out where to best place the diagonal
			for (i = 0; i < vf.length; ++i) {
				if (!deleted[i]) {
					prev = getPrev(i, deleted);
					next = getNext(i, deleted);
					int next2 = getNext(next, deleted);
					Vec3 mid1 = vertices[vf[prev]].r.plus(vertices[vf[next]].r);
					Vec3 mid2 = vertices[vf[next2]].r.plus(vertices[vf[i]].r);
					double product = norm.dot(mid1.minus(mid2));
					if (product >= 0) // convex face
					{
						i1 = vf[prev];
						i2 = vf[i];
						i3 = vf[next];
						if (!areaOnly)
							v1.addElement(new Integer(i1));
						if (!areaOnly)
							v2.addElement(new Integer(i2));
						if (!areaOnly)
							v3.addElement(new Integer(i3));
						if (faceInfo != null)
							faceInfo.add(new Integer(face));
						tmp = vertices[i1].r.cross(vertices[i2].r);
						tmp.add(vertices[i2].r.cross(vertices[i3].r));
						tmp.add(vertices[i3].r.cross(vertices[i1].r));
						area += tmp.length() / 2;
						i1 = vf[next];
						i2 = vf[next2];
						i3 = vf[prev];
						if (!areaOnly)
							v1.addElement(new Integer(i1));
						if (!areaOnly)
							v2.addElement(new Integer(i2));
						if (!areaOnly)
							v3.addElement(new Integer(i3));
						if (faceInfo != null)
							faceInfo.add(new Integer(face));
						tmp = vertices[i1].r.cross(vertices[i2].r);
						tmp.add(vertices[i2].r.cross(vertices[i3].r));
						tmp.add(vertices[i3].r.cross(vertices[i1].r));
						area += tmp.length() / 2;
					} else {
						i1 = vf[next2];
						i2 = vf[prev];
						i3 = vf[i];
						if (!areaOnly)
							v1.addElement(new Integer(i1));
						if (!areaOnly)
							v2.addElement(new Integer(i2));
						if (!areaOnly)
							v3.addElement(new Integer(i3));
						if (faceInfo != null)
							faceInfo.add(new Integer(face));
						tmp = vertices[i1].r.cross(vertices[i2].r);
						tmp.add(vertices[i2].r.cross(vertices[i3].r));
						tmp.add(vertices[i3].r.cross(vertices[i1].r));
						area += tmp.length() / 2;
						i1 = vf[i];
						i2 = vf[next];
						i3 = vf[next2];
						if (!areaOnly)
							v1.addElement(new Integer(i1));
						if (!areaOnly)
							v2.addElement(new Integer(i2));
						if (!areaOnly)
							v3.addElement(new Integer(i3));
						if (faceInfo != null)
							faceInfo.add(new Integer(face));
						tmp = vertices[i1].r.cross(vertices[i2].r);
						tmp.add(vertices[i2].r.cross(vertices[i3].r));
						tmp.add(vertices[i3].r.cross(vertices[i1].r));
						area += tmp.length() / 2;

					}
					break;
				}
			}
		} else if (count > 4) {
			// more than square
			v.scale(1.0 / (count * 1.0));
			if (!areaOnly) {
				vert.add(v);
				count = 0;
				for (i = 0; i < vf.length; ++i)
					if (!deleted[i])
						++count;
				int[] vv = new int[count];
				double[] coef = new double[count];
				coef[0] = 1.0 / ((double) count);
				count = 0;
				for (i = 0; i < vf.length; ++i)
					if (!deleted[i]) {
						vv[count] = vf[i];
						coef[count++] = coef[0];
					}
				if (vertInfo != null)
					vertInfo.add(new VertexParamInfo(vv, coef));
			}
			for (i = 0; i < vf.length; ++i)
				if (!deleted[i]) {
					{
						i1 = vf[getPrev(i, deleted)];
						i2 = vf[i];
						if (!areaOnly)
							v1.addElement(new Integer(i1));
						if (!areaOnly)
							v2.addElement(new Integer(i2));
						if (!areaOnly)
							v3.addElement(new Integer(vert.size() - 1));
						if (faceInfo != null)
							faceInfo.add(new Integer(face));
						tmp = vertices[i1].r.cross(vertices[i2].r);
						tmp.add(vertices[i2].r.cross(v));
						tmp.add(v.cross(vertices[i1].r));
						area += tmp.length() / 2;
					}
				}
		}
		return area;
	}

	/**
	 * Says if the projection of pt onto triangle v1, v2, v3 is in triangle or
	 * not.
	 * 
	 * @param v1
	 * @param v2
	 * @param v3
	 * @param pt
	 * @return true if the projection of p is inside the triangle
	 */
	private boolean ptInTriangle(Vec3 v1, Vec3 v2, Vec3 v3, Vec3 pt) {
		Vec3 vv1 = v2.minus(v1);
		vv1.normalize();
		Vec3 vv2 = v3.minus(v2);
		vv2.normalize();
		Vec3 norm = vv1.cross(vv2);
		norm.normalize();
		if (norm.length() < 1e-8)
			return false;
		double t, u, v, tmp;
		Vec3 p, s, q;
		vv1 = v2.minus(v1);
		vv2 = v3.minus(v1);
		p = norm.cross(vv2);
		tmp = p.dot(vv1);

		if (tmp < 1e-8 && tmp > -1e-8)
			return false;
		tmp = 1.0 / tmp;
		s = pt.minus(v1);
		u = tmp * s.dot(p);
		if (u < -0.00001 || u > 1.00001)
			return false;

		q = s.cross(vv1);
		v = tmp * norm.dot(q);
		if (v < -0.00001 || v > 1.00001)
			return false;

		return true;

	}

	/**
	 * Given an array, finds the edge that shares two vertices.
	 * 
	 * @param fe
	 *            Edges array
	 * @param v1
	 *            First vertex
	 * @param v2
	 *            Second vertex
	 * @return The edge value (-1 if no satisfying edge is found)
	 */
	private int getEdge(int[] fe, int v1, int v2) {
		for (int j = 0; j < fe.length; ++j)
			if ((v1 == edges[fe[j]].vertex && v2 == edges[edges[fe[j]].hedge].vertex)
					|| (v2 == edges[fe[j]].vertex && v1 == edges[edges[fe[j]].hedge].vertex))
				return fe[j];
		return -1;
	}

	/**
	 * Finds the edge that shares two vertices among the current edges array.
	 * Returns -1 if no array is found.
	 * 
	 * @param v1
	 *            First vertex
	 * @param v2
	 *            Second vertex
	 * @return The edge value (-1 if no satisfying edge is found)
	 */
	public int getEdge(int v1, int v2) {
		if (v1 >= vertices.length || v2 >= vertices.length)
			return -1;
		if (vertices[v1] == null)
			return -1;
		if (vertices[v1].edge == -1)
			return -1;
		for (int i = 0; i < edges.length; ++i) {
			if (edges[i] == null)
				continue;
			if ((v1 == edges[i].vertex && v2 == edges[edges[i].hedge].vertex)
					|| (v2 == edges[i].vertex && v1 == edges[edges[i].hedge].vertex))
				return i;
		}
		return -1;
	}

	/**
	 * Adds triangle smoothness to the smoothness vectors
	 * 
	 * @param fe
	 *            The face edges
	 * @param s1
	 *            The vector for first triangle edge smoothnesses
	 * @param s2
	 *            The vector for second triangle edge smoothnesses
	 * @param s3
	 *            The vector for third triangle edge smoothnesses
	 * @param i1
	 *            First vertex index
	 * @param i2
	 *            Second vertex index
	 * @param i3
	 *            Third vertex index
	 * @param s
	 *            The face edge smoothness
	 */
	private void addSmoothness(int[] fe, Vector s1, Vector s2, Vector s3,
			int i1, int i2, int i3, float s) {
		if (s1 != null) {
			int e = getEdge(fe, i1, i2);
			if (e != -1) {
				s1.add(new Float(edges[e].smoothness));
				if (edges[e].smoothness != edges[edges[e].hedge].smoothness)
					System.out.println("Pb smoothness");
			} else {
				s1.add(new Float(s));
			}
		}
		if (s2 != null) {
			int e = getEdge(fe, i2, i3);
			if (e != -1) {
				s2.add(new Float(edges[e].smoothness));
				if (edges[e].smoothness != edges[edges[e].hedge].smoothness)
					System.out.println("Pb smoothness");
			} else {
				s2.add(new Float(s));
			}
		}
		if (s3 != null) {
			int e = getEdge(fe, i3, i1);
			if (e != -1) {
				s3.add(new Float(edges[e].smoothness));
				if (edges[e].smoothness != edges[edges[e].hedge].smoothness)
					System.out.println("Pb smoothness");
			} else {
				s3.add(new Float(s));
			}
		}
	}

	/**
	 * Gets the previous vertex of polygon, given the fact that some vertices
	 * have been deleted
	 * 
	 * @param index
	 *            The index for which the previous vertex is looked for
	 * @param deleted
	 *            A 'deleted' flag array for the vertices
	 * @return The previous index
	 */
	private int getPrev(int index, boolean[] deleted) {
		int prev = index - 1;
		if (prev < 0)
			prev += deleted.length;
		while (deleted[prev]) {
			--prev;
			if (prev < 0)
				prev += deleted.length;
		}
		return prev;
	}

	/**
	 * Gets the next vertex of polygon, given the fact that some vertices have
	 * been deleted
	 * 
	 * @param index
	 *            The index for which the next vertex is looked for
	 * @param deleted
	 *            A 'deleted' flag array for the vertices
	 * @return The previous index
	 */
	private int getNext(int index, boolean[] deleted) {
		int next = index + 1;
		if (next >= deleted.length)
			next -= deleted.length;
		while (deleted[next]) {
			++next;
			if (next >= deleted.length)
				next -= deleted.length;
		}
		return next;
	}

	/**
	 * Gets number of vertices which haven't been deleted through ear cutting
	 * 
	 * @return The number of vertices not yet deleted
	 */
	private int getDelLength(boolean[] deleted) {
		int count = 0;
		for (int i = 0; i < deleted.length; i++)
			if (!deleted[i])
				++count;
		return count;
	}

	/**
	 * Get the list of vertices which define the mesh.
	 * 
	 * @return The vertices value
	 */

	public MeshVertex[] getVertices() {
		return vertices;
	}

	/**
	 * Get a list of the positions of all vertices which define the mesh.
	 * 
	 * @return The vertexPositions value
	 */

	public Vec3[] getVertexPositions() {
		Vec3 v[] = new Vec3[vertices.length];
		for (int i = 0; i < v.length; i++)
			v[i] = new Vec3(vertices[i].r);
		return v;
	}

	/**
	 * Set the positions for all the vertices of the mesh.
	 * 
	 * @param v
	 *            The new vertexPositions value
	 */

	public void setVertexPositions(Vec3 v[]) {
		for (int i = 0; i < v.length; i++)
			vertices[i].r = v[i];
		resetMesh();
	}

	/**
	 * Get an array of normal vectors, one for each vertex.
	 * 
	 * @return The normals value
	 */
	public Vec3[] getNormals() {
		if (cachedNormals != null)
			return cachedNormals;

		Vec3 norm[] = new Vec3[vertices.length];
		Vec3 faceNormals[] = getFaceNormals();
		int pred;
		Vec3 v1;
		Vec3 v2;
		Vec3 v3;
		Vec3 normal;
		boolean added;
		double area, angle;
		for (int i = 0; i < vertices.length; i++) {
			norm[i] = new Vec3();
			int[] ve = getVertexEdges(vertices[i]);
			if (ve.length > 1) {
				added = false;
				for (int j = 0; j < ve.length; ++j) {
					if (edges[ve[j]].face == -1)
						continue;
					pred = j - 1;
					if (pred < 0)
						pred = ve.length - 1;
					// if ( ignoreBoundaries && edges[pred].face == -1 )
					// continue;
					v1 = vertices[edges[ve[pred]].vertex].r
							.minus(vertices[i].r);
					v2 = vertices[edges[ve[j]].vertex].r.minus(vertices[i].r);
					// v3 = vertices[i].r.cross(
					// vertices[edges[ve[j]].vertex].r );
					// v3.add( vertices[edges[ve[j]].vertex].r.cross(
					// vertices[edges[ve[pred]].vertex].r ) );
					// v3.add( vertices[edges[ve[pred]].vertex].r.cross(
					// vertices[i].r ) );
					// area = v3.length();
					// System.out.println( "area: " + area);
					v1.normalize();
					v2.normalize();
					angle = Math.acos(v1.dot(v2));
					// if (edges[ve[j]].face > -1)
					// normal = faceNormals[edges[ve[j]].face];
					// else
					normal = v2.cross(v1);
					// System.out.println("i : " + normal.length());
					if (normal.length() > 0.001) {

						normal.normalize();
						if (v2.cross(v1).dot(normal) < 0)
							angle = 2 * Math.PI - angle;
						norm[i].add(normal.times(angle));
						added = true;
					}
				}
				if (!added) {
					for (int j = 0; j < ve.length; ++j) {
						if (edges[ve[j]].face == -1)
							continue;
						norm[i].add(faceNormals[edges[ve[j]].face]);
					}
				}
				norm[i].normalize();
			} else {
				norm[i] = (vertices[edges[ve[0]].vertex].r
						.minus(vertices[edges[edges[ve[0]].hedge].vertex].r));
				norm[i].normalize();
			}
			//System.out.println(norm[i]);
		}
		return cachedNormals = norm;
	}

	/**
	 * Get an array of normal vectors, one for each edges.
	 * 
	 * @return The normals value
	 */

	public Vec3[] getEdgeNormals() {
		if (cachedEdgeNormals != null)
			return cachedEdgeNormals;

		Vec3[] normals = getNormals();
		Vec3[] faceNormals = getFaceNormals();
		Vec3 norm[] = new Vec3[edges.length];
		Vec3 v;
		int vi;
		int hvi;
		int pvi;
		int phvi;
		int nvi;
		int nhvi;
		Vec3 vn;
		Vec3 hvn;
		for (int i = 0; i < edges.length / 2; i++) {
			vi = edges[i].vertex;
			hvi = edges[edges[i].hedge].vertex;
			pvi = edges[i].hedge;
			while (edges[pvi].next != i)
				pvi = edges[edges[pvi].next].hedge;
			pvi = edges[edges[pvi].hedge].vertex;
			phvi = i;
			while (edges[phvi].next != edges[i].hedge)
				phvi = edges[edges[phvi].next].hedge;
			phvi = edges[edges[phvi].hedge].vertex;
			nvi = edges[edges[i].next].vertex;
			nhvi = edges[edges[edges[i].hedge].next].vertex;
			if (edges[i].face != -1) {
				vn = vertices[vi].r.minus(vertices[hvi].r).cross(
						vertices[nvi].r.minus(vertices[hvi].r));
				if (vn.length() > 1e-6)
					vn.normalize();
				else
					vn = faceNormals[edges[i].face];
			} else
				vn = new Vec3();
			if (edges[edges[i].hedge].face != -1) {
				hvn = vertices[phvi].r.minus(vertices[hvi].r).cross(
						vertices[vi].r.minus(vertices[hvi].r));
				if (hvn.length() > 1e-6)
					hvn.normalize();
				else
					hvn = faceNormals[edges[edges[i].hedge].face];
			} else
				hvn = new Vec3();
			v = vn.plus(hvn);
			if (v.length() < 1e-6)
				v = normals[vi].plus(normals[hvi]);
			v.normalize();
			// if ( ( hvn.dot( v ) >= 0.9999 ) || ( vn.dot( v ) >= 0.9999 )
			// )
			// v.scale( 0 );
			// v.scale( Math.sin( Math.acos( vn.dot( hvn ) ) ) );
			norm[i] = v;

			if (edges[i].face != -1) {
				vn = vertices[pvi].r.minus(vertices[vi].r).cross(
						vertices[hvi].r.minus(vertices[vi].r));
				if (vn.length() > 1e-6)
					vn.normalize();
				else
					vn = faceNormals[edges[i].face];
			} else
				vn = new Vec3();
			if (edges[edges[i].hedge].face != -1) {
				hvn = vertices[hvi].r.minus(vertices[vi].r).cross(
						vertices[nhvi].r.minus(vertices[vi].r));
				if (hvn.length() > 1e-6)
					hvn.normalize();
				else
					hvn = faceNormals[edges[edges[i].hedge].face];
			} else
				hvn = new Vec3();
			v = vn.plus(hvn);
			if (v.length() < 1e-6)
				v = normals[vi].plus(normals[hvi]);
			v.normalize();
			// if ( ( hvn.dot( v ) >= 0.9999 ) || ( vn.dot( v ) >= 0.9999 )
			// )
			// v.scale( 0 );
			// v.scale( Math.sin( Math.acos( vn.dot( hvn ) ) ) );
			norm[edges[i].hedge] = v;
		}
		return cachedEdgeNormals = norm;
	}

	public Vec3 getEdgePosition(int edge) {
		Vec3 middle;
		middle = vertices[edges[edge].vertex].r
				.plus(vertices[edges[edges[edge].hedge].vertex].r);
		middle.scale(0.5);
		return middle;
	}

	public Vec3 getFacePosition(int face) {
		int[] fv = getFaceVertices(faces[face]);
		Vec3 middle = new Vec3();
		for (int j = 0; j < fv.length; j++)
			middle.add(vertices[fv[j]].r);
		middle.scale(1.0 / (double) fv.length);
		return middle;
	}

	/**
	 * Get an array of normal vectors, one for each face.
	 * 
	 * @return The normals value
	 */

	public Vec3[] getFaceNormals() {
		if (cachedFaceNormals != null)
			return cachedFaceNormals;

		Vec3 norm[] = new Vec3[faces.length];
		int pred;
		int next;
		Vec3 v1;
		Vec3 v2;
		for (int i = 0; i < faces.length; i++) {
			int[] vf = getFaceVertices(faces[i]);
			norm[i] = new Vec3();
			for (int j = 0; j < vf.length; ++j) {
				pred = j - 1;
				if (pred < 0)
					pred = vf.length - 1;
				next = j + 1;
				if (next >= vf.length)
					next = 0;
				v1 = vertices[vf[j]].r.minus(vertices[vf[pred]].r);
				v2 = vertices[vf[next]].r.minus(vertices[vf[j]].r);
				norm[i].add(v1.cross(v2));
			}
			norm[i].normalize();
		}
		return cachedFaceNormals = norm;
	}

	/**
	 * Returns the mesh edges
	 * 
	 * @return The edges value
	 */
	public Wedge[] getEdges() {
		return edges;
	}

	/**
	 * Returns the mesh faces
	 * 
	 * @return The faces value
	 */
	public Wface[] getFaces() {
		return faces;
	}

	/**
	 * Sets the mesh vertices, edges and faces array. Use this method after
	 * you've changed any mesh feature.
	 * 
	 * @param v
	 * @param e
	 * @param f
	 */
	public void setMeshTopology(Wvertex[] v, Wedge[] e, Wface[] f) {
		vertices = v;
		edges = e;
		faces = f;
		resetMesh();
	}

	/**
	 * Get the skeleton for the object. If it does not have one, this should
	 * return null.
	 * 
	 * @return The skeleton value
	 */

	public Skeleton getSkeleton() {
		return skeleton;
	}

	/**
	 * Set the skeleton for this object.
	 * 
	 * @param s
	 *            The new skeleton value
	 */

	public void setSkeleton(Skeleton s) {
		skeleton = s;
	}

	/**
	 * Calculate the (approximate) bounding box for the mesh.
	 */

	private void findBounds() {
		double minx;
		double miny;
		double minz;
		double maxx;
		double maxy;
		double maxz;
		Vec3 vert[];
		int i;

		if (cachedMesh != null)
			vert = cachedMesh.vert;
		else if (cachedWire != null)
			vert = cachedWire.vert;
		else {
			getWireframeMesh();
			vert = cachedWire.vert;
		}
		minx = maxx = vert[0].x;
		miny = maxy = vert[0].y;
		minz = maxz = vert[0].z;
		for (i = 1; i < vert.length; i++) {
			if (vert[i].x < minx)
				minx = vert[i].x;
			if (vert[i].x > maxx)
				maxx = vert[i].x;
			if (vert[i].y < miny)
				miny = vert[i].y;
			if (vert[i].y > maxy)
				maxy = vert[i].y;
			if (vert[i].z < minz)
				minz = vert[i].z;
			if (vert[i].z > maxz)
				maxz = vert[i].z;
		}
		bounds = new BoundingBox(minx, maxx, miny, maxy, minz, maxz);
	}

	/**
	 * Sets the size of the PolyMesh object
	 * 
	 * @param xsize
	 *            The new size value
	 * @param ysize
	 *            The new size value
	 * @param zsize
	 *            The new size value
	 */
	public void setSize(double xsize, double ysize, double zsize) {
		Vec3 size = getBounds().getSize();
		double xscale;
		double yscale;
		double zscale;

		if (size.x == 0.0)
			xscale = 1.0;
		else
			xscale = xsize / size.x;
		if (size.y == 0.0)
			yscale = 1.0;
		else
			yscale = ysize / size.y;
		if (size.z == 0.0)
			zscale = 1.0;
		else
			zscale = zsize / size.z;
		for (int i = 0; i < vertices.length; i++) {
			vertices[i].r.x *= xscale;
			vertices[i].r.y *= yscale;
			vertices[i].r.z *= zscale;
		}
		// if ( xscale * yscale * zscale < 0.0 )
		// reverseNormals();
		skeleton.scale(xscale, yscale, zscale);
		resetMesh();
	}

	/**
	 * A winged mesh is editable
	 * 
	 * @return The editable value
	 */

	public boolean isEditable() {
		return true;
	}

	/**
	 * The winged edge mesh can be exactly converted to a triangle mesh
	 * 
	 * @return Returns EXACTLY
	 */
	public int canConvertToTriangleMesh() {
		if (smoothingMethod == APPROXIMATING) {
			return APPROXIMATELY;
		}
		else {
			return EXACTLY;
		}
	}

	/**
	 * Convertion to a triangle mesh
	 * 
	 * @param tol
	 *            Tolerance - ignored
	 * @return The triangle mesh
	 */
	public TriangleMesh convertToTriangleMesh(double tol) {
		
		if (smoothingMethod == Mesh.APPROXIMATING) {
				QuadMesh qmesh = smoothWholeMesh(tol, true, Integer.MAX_VALUE, false);
				return qmesh.convertToTriangleMesh(tol);
		}
		
		TriangleMesh mesh;
		vert = new Vector();
		v1 = new Vector();
		v2 = new Vector();
		v3 = new Vector();
		vertInfo = new Vector();
		faceInfo = new Vector();
		polyedge = null;

		for (int i = 0; i < vertices.length; ++i) {
			vert.addElement(vertices[i].r);
			vertInfo.add(new VertexParamInfo(new int[] { i },
					new double[] { 1.0 }));
		}
		for (int i = 0; i < faces.length; ++i) {
			int[] vf = getFaceVertices(faces[i]);
			if (vf.length == 3) {
				v1.addElement(new Integer(vf[0]));
				v2.addElement(new Integer(vf[1]));
				v3.addElement(new Integer(vf[2]));
				if (faceInfo != null)
					faceInfo.add(new Integer(i));
			} else if (vf.length > 3) {
				triangulate(vf, i, false);
			}
		}
		int[][] tfaces = new int[v1.size()][3];
		Vec3[] v = new Vec3[vert.size()];
		for (int i = 0; i < v.length; ++i)
			v[i] = (Vec3) vert.elementAt(i);
		for (int i = 0; i < v1.size(); ++i) {
			tfaces[i][0] = ((Integer) v1.elementAt(i)).intValue();
			tfaces[i][1] = ((Integer) v2.elementAt(i)).intValue();
			tfaces[i][2] = ((Integer) v3.elementAt(i)).intValue();
		}
		mesh = new TriangleMesh(v, tfaces);
		mesh.setSmoothingMethod(smoothingMethod);
		if (smoothingMethod != Mesh.NO_SMOOTHING) {
			Vertex vertex[] = (Vertex[]) mesh.getVertices();
			Edge edge[] = mesh.getEdges();
			polyedge = new int[edge.length];
			Face face[] = mesh.getFaces();
			for (int i = 0; i < vertices.length; i++) {
				if (vertices[i].type == Wvertex.CORNER)
					vertex[i].smoothness = 0.0f;
				else
					vertex[i].smoothness = 1.0f;
			}
			Edge ted;
			int[] verticesEdges[] = new int[vertices.length][];
			for (int i = 0; i < edge.length; ++i)
				if (edge[i].v1 < vertices.length
						&& verticesEdges[edge[i].v1] == null)
					verticesEdges[edge[i].v1] = getVertexEdges(vertices[edge[i].v1]);
			for (int i = 0; i < edge.length; ++i) {
				polyedge[i] = -1;
				ted = edge[i];
				if (ted.v1 >= vertices.length || ted.v2 >= vertices.length) {
					ted.smoothness = 1.0f;
				} else {
					int[] ve = verticesEdges[ted.v1];
					for (int j = 0; j < ve.length; j++) {
						if (edges[ve[j]].vertex == ted.v2) {
							polyedge[i] = ve[j];
							break;
						}
					}
					if (polyedge[i] > -1)
						ted.smoothness = edges[polyedge[i]].smoothness;
					else
						ted.smoothness = 1.0f;
				}
				if (polyedge[i] >= edges.length / 2)
					polyedge[i] = edges[polyedge[i]].hedge;
			}

			if (controlledSmoothing) {
				Edge[] triedge = mesh.getEdges();
				Face[] triface = mesh.getFaces();
				MeshVertex[] trivert = mesh.getVertices();
				double mina = Math.cos(maxAngle * Math.PI / 180.0);
				double maxa = Math.cos(minAngle * Math.PI / 180.0);
				Edge ed;
				Face f1, f2;
				Vec3 norm1, norm2;
				double dot;
				for (int i = 0; i < edge.length; i++) {
					ed = triedge[i];
					if (ed.f2 == -1) {
						ed.smoothness = 0.0f;
						continue; // This is a boundary edge.
					}
					f1 = triface[ed.f1];
					f2 = triface[ed.f2];
					norm1 = trivert[f1.v1].r.minus(trivert[f1.v2].r).cross(
							trivert[f1.v1].r.minus(trivert[f1.v3].r));
					norm2 = trivert[f2.v1].r.minus(trivert[f2.v2].r).cross(
							trivert[f2.v1].r.minus(trivert[f2.v3].r));
					norm1.normalize();
					norm2.normalize();
					dot = norm1.dot(norm2);
					if (dot <= mina)
						ed.smoothness = maxSmoothness;
					else if (dot >= maxa)
						ed.smoothness = minSmoothness;
					else
						ed.smoothness = (float) ((dot - mina) / (maxa - mina))
								* (minSmoothness - maxSmoothness)
								+ maxSmoothness;
				}
			}
		}
		mesh.copyTextureAndMaterial(this);
		// Compute the trimesh texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[vert.size()];
					for (int j = 0; j < vert.size(); ++j) {
						int[] vf = ((VertexParamInfo) vertInfo.elementAt(j)).vert;
						double[] coef = ((VertexParamInfo) vertInfo
								.elementAt(j)).coef;
						for (int k = 0; k < vf.length; ++k)
							newval[j] += coef[k] * oldval[vf[k]];
					}
					newParamVal[i] = new VertexParameterValue(newval);

				} else if (oldParamVal[i] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[faceInfo.size()];
					for (int j = 0; j < newval.length; ++j)
						newval[j] = oldval[((Integer) faceInfo.elementAt(j))
								.intValue()];
					newParamVal[i] = new FaceParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
					double newval[][] = new double[v1.size()][3];
					for (int j = 0; j < v1.size(); ++j) {
						for (int k = 0; k < 3; k++) {
							int vertex = -1;
							switch (k) {
							case 0:
								vertex = ((Integer) v1.elementAt(j)).intValue();
								break;
							case 1:
								vertex = ((Integer) v1.elementAt(j)).intValue();
								break;
							case 2:
								vertex = ((Integer) v1.elementAt(j)).intValue();
								break;
							}
							int pmeFace = ((Integer) faceInfo.elementAt(j))
									.intValue();
							int[] fv = getFaceVertices(faces[pmeFace]);
							int[] vf = ((VertexParamInfo) vertInfo
									.elementAt(vertex)).vert;
							double[] coef = ((VertexParamInfo) vertInfo
									.elementAt(vertex)).coef;
							for (int l = 0; l < vf.length; ++l) {
								int vv = -1;
								for (int m = 0; m < fv.length; ++m) {
									if (fv[m] == vf[l]) {
										vv = m;
										break;
									}
								}
								if (vv == -1) {
									System.out
											.println("pb per face per vertex : point doesn't belong to face");
									vv = 0;
								}
								newval[j][k] += coef[l]
										* fvpv.getValue(pmeFace, vv);
							}

						}
					}
					newParamVal[i] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[i] = oldParamVal[i].duplicate();
			}
			mesh.setParameterValues(newParamVal);
		}
		return mesh;
	}

	/**
	 * Call this method after a call to convertToTriangleMesh in order to know
	 * how new faces relate to original polymesh faces.
	 * 
	 * @return An array describing which polymesh faces the triangle faces
	 *         relate to.
	 */
	public int[] getTriangleFaceIndex() {
		if (faceInfo == null || faceInfo.size() == 0)
			return null;
		int[] fi = new int[faceInfo.size()];
		for (int i = 0; i < fi.length; i++) {
			fi[i] = ((Integer) faceInfo.elementAt(i)).intValue();
		}
		return fi;
	}

	/**
	 * Call this method after a call to convertToTriangleMesh() to know how the
	 * new vertices are defined relative to the orignal polymesh vertices.
	 * 
	 * @return The vertex parameter information that defines each vertex
	 *         relative to original vertices. For each vertex, a value is
	 *         defined in terms of original vertices and coefficient to apply to
	 *         parameter values at the original vertices. The n first entries (n
	 *         being the number of vertices in the polymesh) are defined as
	 *         relative to themselves with a coefficient of 1.0.
	 */
	public VertexParamInfo[] getTriangleVertexParamInfo() {
		if (vertInfo == null || vertInfo.size() == 0)
			return null;
		VertexParamInfo[] vpi = new VertexParamInfo[vertInfo.size()];
		for (int i = 0; i < vpi.length; i++) {
			vpi[i] = (VertexParamInfo) vertInfo.get(i);
		}
		return vpi;
	}

	/**
	 * Call this method to get the underlying representation of the polymesh as
	 * a trimesh.
	 * 
	 * @return Indices array describing convertion between triangle mesh edges
	 *         and polymesh edges.
	 */
	public TriangleMesh getTriangleMesh() {
		return triangleMesh;
	}

	/**
	 * Call this method to access translation between edges of the original mesh
	 * and edges of the trimesh that is used to represent the polymesh. An index
	 * of -1 means that the edge of the trimesh is not an original edge of the
	 * polymesh.
	 * 
	 * @return Indices array describing convertion between triangle mesh edges
	 *         and polymesh edges.
	 */
	public int[] getPolyEdge() {
		return polyedge;
	}

	/**
	 * Display a window in which the user can edit this object.
	 * 
	 * @param parent
	 *            the window from which this command is being invoked
	 * @param info
	 *            the ObjectInfo corresponding to this object
	 * @param cb
	 *            a callback which will be executed when editing is complete. If
	 *            the user cancels the operation, it will not be called.
	 */

	public void edit(EditingWindow parent, ObjectInfo info, Runnable cb) {
		PolyMeshEditorWindow ed = new PolyMeshEditorWindow(parent, "PolyMesh '"
				+ info.name + "'", info, cb);
		((ObjectViewer) ed.getView()).setScene(parent.getScene(), info);
		ed.setVisible(true);
	}

	public void setInteractiveSmoothLevel(int smooth) {
		cachedMesh = null;
		cachedWire = null;
		interactiveSmoothLevel = smooth;
	}

	public int getInteractiveSmoothLevel() {
		return interactiveSmoothLevel;
	}

	/**
	 * Resets cached data
	 */
	public void resetMesh() {
		cachedMesh = null;
		cachedWire = null;
		cachedNormals = null;
		cachedEdgeNormals = null;
		cachedFaceNormals = null;
		mirroredMesh = null;
		if (controlledSmoothing) {
			double dot;
			float smoothness;
			double mina = Math.cos(maxAngle * Math.PI / 180.0);
			double maxa = Math.cos(minAngle * Math.PI / 180.0);
			Vec3[] normals = getFaceNormals();
			for (int i = 0; i < edges.length / 2; i++) {
				smoothness = 0f;
				if ((edges[i].face >= 0) && (edges[edges[i].hedge].face >= 0)) {
					dot = normals[edges[i].face]
							.dot(normals[edges[edges[i].hedge].face]);
					if (dot <= mina)
						smoothness = maxSmoothness;
					else if (dot >= maxa)
						smoothness = minSmoothness;
					else
						smoothness = (float) ((dot - mina) / (maxa - mina))
								* (minSmoothness - maxSmoothness)
								+ maxSmoothness;
				}
				edges[i].smoothness = edges[edges[i].hedge].smoothness = smoothness;
			}
		}
		bounds = null;
		if (seams != null)
			if (edges.length / 2 != seams.length)
				seams = null;
		if (mappingData != null) {
			if (vertices.length != mappingVerts || edges.length != mappingEdges
					|| faces.length != mappingFaces) {
				mappingData = null;
			}
		}
	}

	/**
	 * Deletes vertices from the mesh
	 * 
	 * @param v
	 *            Array of indices of vertices to delete
	 */
	public void deleteVertices(int[] v) {
		boolean[] deletedVertices = new boolean[vertices.length];
		boolean[] deletedEdges = new boolean[edges.length];
		boolean[] deletedFaces = new boolean[faces.length];

		for (int i = 0; i < v.length; ++i) {
			deletedVertices[v[i]] = true;
			int[] de = getVertexEdges(vertices[v[i]]);
			for (int j = 0; j < de.length; ++j) {
				deletedEdges[edges[de[j]].hedge] = true;
				deletedEdges[de[j]] = true;
				if (edges[edges[de[j]].hedge].face != -1)
					deletedFaces[edges[edges[de[j]].hedge].face] = true;
				if (edges[de[j]].face != -1)
					deletedFaces[edges[de[j]].face] = true;
			}
		}
		deletion(deletedVertices, deletedEdges, deletedFaces, false);
	}

	/**
	 * This method deletes points, edges and faces according to the boolean
	 * arrays describing which of them must be deleted. A first evaluation of
	 * these parameters must be carried out. Use deleteVertices, deleteEdges or
	 * deleteFaces instead.
	 * 
	 * @param deletedVertices
	 *            An array describing which points must be deleted
	 * @param deletedEdges
	 *            An array describing which edges must be deleted
	 * @param deletedFaces
	 *            An array describing which faces must be deleted
	 */
	private int[] deletion(boolean[] deletedVertices, boolean[] deletedEdges,
			boolean[] deletedFaces, boolean mirrorOp) {
		Wvertex[] newVertices;
		Wedge[] newEdges;
		Wface[] newFaces;
		Wedge te;

		int[] vertexTable = new int[vertices.length];
		int[] edgeTable = new int[edges.length];
		int[] faceTable = new int[faces.length];

		int count;
		boolean newDelete = true;
		while (newDelete) {
			newDelete = false;
			for (int i = 0; i < edges.length; ++i) {
				if ((!deletedEdges[i]) && deletedEdges[edges[i].next]) {
					int[] de = getVertexEdges(vertices[edges[i].vertex]);
					count = 0;
					for (int j = 0; j < de.length; ++j)
						if (!deletedEdges[de[j]])
							++count;
					if (count < 2) {
						deletedVertices[edges[i].vertex] = true;
						deletedEdges[i] = true;
						deletedEdges[edges[i].hedge] = true;
						newDelete = true;
					}
				}
			}
		}
		int index;
		for (int i = 0; i < vertices.length; ++i) {
			vertexTable[i] = -1;
			int[] ed = getVertexEdges(vertices[i]);
			count = 0;
			for (int j = 0; j < ed.length; ++j)
				if (!deletedEdges[ed[j]]) {
					++count;
					if (deletedEdges[vertices[i].edge])
						vertices[i].edge = ed[j];
				}
			if (count == 0)
				deletedVertices[i] = true;
			if (count == 1)
				System.out.println("Warning: a dangling vertex has been found");
		}
		count = 0;
		for (int i = 0; i < deletedVertices.length; ++i)
			if (deletedVertices[i])
				++count;
		if (vertices.length - count < 3) {
			new BStandardDialog(Translate.text("polymesh:errorTitle"), UIUtilities
					.breakString(Translate.text("illegalDelete")),
					BStandardDialog.ERROR).showMessageDialog(null);
			return null;
		}
		newVertices = new Wvertex[vertices.length - count];
		index = 0;
		for (int i = 0; i < vertices.length; ++i) {
			vertexTable[i] = -1;
			if (!deletedVertices[i]) {
				newVertices[index] = vertices[i];
				vertexTable[i] = index++;
			}
		}
		count = 0;
		for (int i = 0; i < deletedEdges.length / 2; ++i)
			if (deletedEdges[i])
				++count;
		if (edges.length / 2 - count < 3) {
			new BStandardDialog(Translate.text("polymesh:errorTitle"), UIUtilities
					.breakString(Translate.text("illegalDelete")),
					BStandardDialog.ERROR).showMessageDialog(null);
			return null;
		}

		newEdges = new Wedge[edges.length - 2 * count];
		index = 0;
		for (int i = 0; i < edges.length; ++i) {
			edgeTable[i] = -1;
			if (!deletedEdges[i]) {
				// check for deleted next edge.
				while (deletedEdges[edges[i].next]) {
					te = edges[edges[i].next];
					te = edges[te.hedge];
					edges[i].next = te.next;
				}
				newEdges[index] = edges[i];
				edgeTable[i] = index;
				++index;
			}
		}
		count = 0;
		for (int i = 0; i < deletedFaces.length; ++i)
			if (deletedFaces[i])
				++count;
		if (faces.length - count < 1) {
			new BStandardDialog(Translate.text("polymesh:errorTitle"), UIUtilities
					.breakString(Translate.text("illegalDelete")),
					BStandardDialog.ERROR).showMessageDialog(null);
			return null;
		}
		newFaces = new Wface[faces.length - count];
		index = 0;
		for (int i = 0; i < faces.length; ++i) {
			faceTable[i] = -1;
			if (!deletedFaces[i]) {
				newFaces[index] = faces[i];
				faceTable[i] = index++;
			}
		}
		for (int i = 0; i < newVertices.length; ++i)
			newVertices[i].edge = edgeTable[newVertices[i].edge];
		for (int i = 0; i < newEdges.length; ++i) {
			newEdges[i].vertex = vertexTable[newEdges[i].vertex];
			newEdges[i].hedge = edgeTable[newEdges[i].hedge];
			if (newEdges[i].face != -1)
				newEdges[i].face = faceTable[newEdges[i].face];
			newEdges[i].next = edgeTable[newEdges[i].next];
		}
		for (int i = 0; i < newFaces.length; ++i)
			newFaces[i].edge = edgeTable[newFaces[i].edge];
		if (mirrorOp) {
			if (mirroredVerts == null) {
				mirroredVerts = vertexTable;
				mirroredEdges = edgeTable;
				mirroredFaces = faceTable;
				invMirroredVerts = new int[newVertices.length];
				for (int i = 0; i < mirroredVerts.length; ++i) {
					if (mirroredVerts[i] != -1)
						invMirroredVerts[mirroredVerts[i]] = i;
				}
				invMirroredEdges = new int[newEdges.length / 2];
				for (int i = 0; i < mirroredEdges.length / 2; ++i) {
					if (mirroredEdges[i] != -1)
						invMirroredEdges[mirroredEdges[i]] = i;
				}
				invMirroredFaces = new int[newFaces.length];
				for (int i = 0; i < mirroredFaces.length; ++i) {
					if (mirroredFaces[i] != -1)
						invMirroredFaces[mirroredFaces[i]] = i;
				}
			} else {
				for (int i = 0; i < mirroredVerts.length; ++i) {
					if (mirroredVerts[i] != -1)
						mirroredVerts[i] = vertexTable[mirroredVerts[i]];
				}
				for (int i = 0; i < mirroredEdges.length; ++i) {
					if (mirroredEdges[i] != -1)
						mirroredEdges[i] = edgeTable[mirroredEdges[i]];
				}
				for (int i = 0; i < mirroredFaces.length; ++i) {
					if (mirroredFaces[i] != -1)
						mirroredFaces[i] = faceTable[mirroredFaces[i]];
				}
				count = 0;
				for (int i = 0; i < invMirroredVerts.length; ++i) {
					if (vertexTable[i] != -1)
						++count;
				}
				int[] newInvMirroredVerts = new int[count];
				count = 0;
				for (int i = 0; i < invMirroredVerts.length; ++i) {
					if (vertexTable[i] != -1)
						newInvMirroredVerts[vertexTable[i]] = invMirroredVerts[i];
				}
				count = 0;
				for (int i = 0; i < invMirroredEdges.length; ++i) {
					if (edgeTable[i] != -1)
						++count;
				}
				int[] newInvMirroredEdges = new int[count];
				for (int i = 0; i < invMirroredEdges.length; ++i) {
					if (edgeTable[i] != -1)
						newInvMirroredEdges[edgeTable[i]] = invMirroredEdges[i];
				}
				count = 0;
				for (int i = 0; i < invMirroredFaces.length; ++i) {
					if (faceTable[i] != -1)
						++count;
				}
				int[] newInvMirroredFaces = new int[count];
				for (int i = 0; i < invMirroredFaces.length; ++i) {
					if (faceTable[i] != -1)
						newInvMirroredFaces[faceTable[i]] = invMirroredFaces[i];
				}
				invMirroredVerts = newInvMirroredVerts;
				invMirroredEdges = newInvMirroredEdges;
				invMirroredFaces = newInvMirroredFaces;
			}
		}
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[newVertices.length];
					for (int j = 0, k = 0; j < oldval.length; j++)
						if (!deletedVertices[j])
							newval[k++] = oldval[j];
					newParamVal[i] = new VertexParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[newFaces.length];
					for (int j = 0, k = 0; j < oldval.length; j++)
						if (!deletedFaces[j])
							newval[k++] = oldval[j];

					newParamVal[i] = new FaceParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[i] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[i] = oldParamVal[i].duplicate();
			}
			setParameterValues(newParamVal);
		}
		faces = newFaces;
		edges = newEdges;
		vertices = newVertices;
		resetMesh();
		return vertexTable;
	}

	/**
	 * Deletes edges from the mesh
	 * 
	 * @param e
	 *            Array of indices of edges to delete
	 */
	public void deleteEdges(int[] e) {
		boolean[] deletedVertices = new boolean[vertices.length];
		boolean[] deletedEdges = new boolean[edges.length];
		boolean[] deletedFaces = new boolean[faces.length];
		for (int i = 0; i < e.length; ++i) {
			deletedEdges[edges[e[i]].hedge] = true;
			deletedEdges[e[i]] = true;
			if (edges[edges[e[i]].hedge].face != -1)
				deletedFaces[edges[edges[e[i]].hedge].face] = true;
			if (edges[e[i]].face != -1)
				deletedFaces[edges[e[i]].face] = true;
		}
		boolean del1;
		boolean del2;
		for (int i = 0; i < edges.length / 2; ++i) {
			del1 = (edges[i].face == -1);
			if (!del1)
				del1 = deletedFaces[edges[i].face];
			del2 = (edges[edges[i].hedge].face == -1);
			if (!del2)
				del2 = deletedFaces[edges[edges[i].hedge].face];
			if (del1 && del2) {
				deletedEdges[i] = true;
				deletedEdges[edges[i].hedge] = true;
			}
		}
		deletion(deletedVertices, deletedEdges, deletedFaces, false);
	}

	/**
	 * Deletes faces from the mesh
	 * 
	 * @param f
	 *            Array of indices of faces to delete
	 */
	public void deleteFaces(int[] f) {
		boolean[] deletedFaces = new boolean[faces.length];

		for (int i = 0; i < f.length; ++i)
			deletedFaces[f[i]] = true;
		deleteFaces(deletedFaces);
	}

	/**
	 * Deletes faces from the mesh
	 * 
	 * @param deletedFaces
	 *            Selection of faces to delete
	 */
	public void deleteFaces(boolean[] deletedFaces) {
		deleteFaces(deletedFaces, false);
	}

	/**
	 * Deletes faces from the mesh
	 * 
	 * @param deletedFaces
	 *            Selection of faces to delete
	 */
	public void deleteFaces(boolean[] deletedFaces, boolean mirrorOp) {
		boolean[] deletedVertices = new boolean[vertices.length];
		boolean[] deletedEdges = new boolean[edges.length];

		boolean del1;
		boolean del2;
		for (int i = 0; i < edges.length / 2; ++i) {
			del1 = (edges[i].face == -1);
			if (!del1)
				del1 = deletedFaces[edges[i].face];
			del2 = (edges[edges[i].hedge].face == -1);
			if (!del2)
				del2 = deletedFaces[edges[edges[i].hedge].face];
			if (del1 && del2) {
				deletedEdges[i] = true;
				deletedEdges[edges[i].hedge] = true;
			}
		}
		deletion(deletedVertices, deletedEdges, deletedFaces, mirrorOp);
	}

	/**
	 * Divides segments 2nd version no recursion
	 * 
	 * @param sel
	 *            Selected edges
	 * @param nseg
	 *            Number of segments to create
	 * @return Array of selected vertices for further selection
	 */
	public boolean[] divideEdges(boolean[] sel, int nseg) {
		Wvertex[] newVertices;
		Wedge[] newEdges;
		double fraction;
		int[] toVert;
		int[] fromVert;
		double[] fract;

		long t = System.currentTimeMillis();
		int size = vertices.length;
		int count = 0;
		for (int i = 0; i < sel.length; ++i)
			if (sel[i])
				count++;
		int add = nseg - 1;
		newVertices = new Wvertex[vertices.length + count * add];
		newEdges = new Wedge[edges.length + 2 * count * add];
		toVert = new int[count * add];
		fromVert = new int[count * add];
		fract = new double[count * add];
		translateMesh(newVertices, newEdges, faces);
		int index = 0;
		int vl = vertices.length;
		int el = edges.length / 2;
		for (int i = 0; i < sel.length; ++i) {
			if (sel[i]) {
				Vec3 delta;
				int to = newEdges[i].vertex;
				int from = newEdges[newEdges[i].hedge].vertex;
				int edgeTo = newEdges[i].next;
				int faceTo = newEdges[i].face;
				int faceFrom = newEdges[newEdges[i].hedge].face;
				delta = newVertices[to].r.minus(newVertices[from].r);
				delta.scale(1.0 / nseg);
				int ed = newVertices[newEdges[i].vertex].edge;
				while (newEdges[newEdges[ed].hedge].next != newEdges[i].hedge)
					ed = newEdges[newEdges[ed].hedge].next;
				newVertices[vl + index] = new Wvertex(vertices[from].r
						.plus(delta), el + index);
				newEdges[i].vertex = vl + index;
				newEdges[i].next = el + index;
				newEdges[el + index] = new Wedge(to, el + index
						+ newEdges.length / 2, faceTo, edgeTo);
				newEdges[el + index + newEdges.length / 2] = new Wedge(vl
						+ index, el + index, faceFrom, newEdges[i].hedge);
				newEdges[el + index].smoothness = edges[i].smoothness;
				newEdges[el + index + newEdges.length / 2].smoothness = edges[i].smoothness;
				newVertices[to].edge = el + index + newEdges.length / 2;
				fraction = 1 / ((double) (add + 1));
				// newVertices[vl + index].smoothness = (float) ( fraction *
				// vertices[to].smoothness + ( 1 - fraction ) * (
				// vertices[from].smoothness ) );
				toVert[index] = to;
				fromVert[index] = from;
				fract[index] = fraction;
				for (int j = 1; j < add; ++j) {
					++index;
					newVertices[vl + index] = new Wvertex(vertices[from].r
							.plus(delta.times(j + 1)), el + index);
					newEdges[el + index - 1].vertex = vl + index;
					newEdges[el + index - 1].next = el + index;
					newEdges[el + index] = new Wedge(to, el + index
							+ newEdges.length / 2, faceTo, edgeTo);
					newEdges[el + index + newEdges.length / 2] = new Wedge(vl
							+ index, el + index, faceFrom, newEdges[el + index
							- 1].hedge);
					newEdges[el + index].smoothness = edges[i].smoothness;
					newEdges[el + index + newEdges.length / 2].smoothness = edges[i].smoothness;
					newVertices[to].edge = el + index + newEdges.length / 2;
					fraction = (j + 1) / ((double) (add + 1));
					// newVertices[vl + index].smoothness = (float) (
					// fraction * vertices[to].smoothness + ( 1 - fraction )
					// * ( vertices[from].smoothness ) );
					toVert[index] = to;
					fromVert[index] = from;
					fract[index] = fraction;
				}
				newEdges[newEdges[ed].hedge].next = newEdges[el + index].hedge;
				++index;
			}
		}
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newVertices.length; ++j) {
						fraction = fract[j - oldval.length];
						newval[j] = fraction
								* oldval[toVert[j - oldval.length]]
								+ (1 - fraction)
								* (newval[fromVert[j - oldval.length]]);
					}
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[faces.length][];
					for (int j = 0; j < faces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, faces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);

		}
		boolean[] newSel = new boolean[newVertices.length];
		for (int i = vertices.length; i < newVertices.length; ++i)
			newSel[i] = true;
		edges = newEdges;
		vertices = newVertices;
		resetMesh();
		return newSel;
	}

	/**
	 * Divides all mesh segments by 2
	 * 
	 */
	public void divideAllEdgesByTwo() {
		Wvertex[] newVertices;
		Wedge[] newEdges;
		int[] toVert;
		int[] fromVert;

		//dumpFaceVertices();
		int count = edges.length / 2;
		newVertices = new Wvertex[vertices.length + count];
		newEdges = new Wedge[edges.length + 2 * count];
//		int[] newProjectEdges = null;
//		if (projectedEdges != null) {
//			newProjectEdges = new int[edges.length / 2 + count];
//		}
		toVert = new int[count];
		fromVert = new int[count];
		translateMesh(newVertices, newEdges, faces);
		int index = 0;
		int vl = vertices.length;
		int el = edges.length / 2;
		Vec3 delta;
		int to, from, edgeTo, faceTo, faceFrom, ed;
		for (int i = 0; i < count; ++i) {
			to = newEdges[i].vertex;
			from = newEdges[newEdges[i].hedge].vertex;
			edgeTo = newEdges[i].next;
			faceTo = newEdges[i].face;
			faceFrom = newEdges[newEdges[i].hedge].face;
			delta = newVertices[to].r.minus(newVertices[from].r);
			delta.scale(0.5);
			ed = newVertices[newEdges[i].vertex].edge;
			while (newEdges[newEdges[ed].hedge].next != newEdges[i].hedge) {
				ed = newEdges[newEdges[ed].hedge].next;
			}
			newVertices[vl + index] = new Wvertex(vertices[from].r.plus(delta),
					el + index);
			newEdges[i].vertex = vl + index;
			newEdges[i].next = el + index;
			newEdges[el + index] = new Wedge(to, el + index + newEdges.length
					/ 2, faceTo, edgeTo);
			newEdges[el + index + newEdges.length / 2] = new Wedge(vl + index,
					el + index, faceFrom, newEdges[i].hedge);
			newEdges[el + index].smoothness = edges[i].smoothness;
			newEdges[el + index + newEdges.length / 2].smoothness = edges[i].smoothness;
			newVertices[to].edge = el + index + newEdges.length / 2;
			toVert[index] = to;
			fromVert[index] = from;
			newEdges[newEdges[ed].hedge].next = newEdges[el + index].hedge;
//			if (projectedEdges != null) {
//				newProjectEdges[i] = projectedEdges[i];
//				newProjectEdges[newEdges[i].hedge] = projectedEdges[i];
//				newProjectEdges[el + index] = projectedEdges[i];
//				newProjectEdges[newEdges[el + index].hedge] = projectedEdges[i];
//			}
			++index;
		}
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newVertices.length; ++j)
						newval[j] = 0.5 * oldval[toVert[j - oldval.length]]
								+ 0.5 * oldval[fromVert[j - oldval.length]];
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[faces.length][];
					int prev, next;
					for (int j = 0; j < faces.length; ++j) {
						count = 2 * fvpv.getFaceVertexCount(j);
						double val[] = new double[count];
						if (newEdges[faces[j].edge].vertex < vertices.length) {
							//System.out.println(j + ": origine 0");
							for (int l = 0; l < count; l += 2) {
								val[l] = fvpv.getValue(j, l / 2);
							}
							for (int l = 1; l < count; l += 2) {
								next = l + 1;
								if (next >= count)
									next -= count;
								val[l] = 0.5 * val[l-1] + 0.5 * val[next];
							}
						} else {
							//System.out.println(j + ": origine 1");
							for (int l = 1; l < count; l += 2) {
								val[l] = fvpv.getValue(j, l / 2);
							}
							for (int l = 0; l < count; l += 2) {
								prev = l - 1;
								if (prev < 0)
									prev += count;
								val[l] = 0.5 * val[prev] + 0.5 * val[l + 1];
							}
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		edges = newEdges;
		vertices = newVertices;
		resetMesh();
		//projectedEdges = newProjectEdges;
		//dumpFaceVertices();
	}

	private void dumpFaceVertices() {
		for (int i = 0; i < faces.length; i++) {
			int[] fv = getFaceVertices(faces[i]);
			System.out.print(i + ": ");
			for (int j = 0; j < fv.length; j++)
				System.out.print(fv[j] + " ");
			System.out.println("");
		}
	}

	/**
	 * Merges two boundary edges.
	 * 
	 * @param e1
	 *            First edge to merge
	 * @param e2
	 *            Second edge to merge
	 * @return Selection corresponding to merged edge.
	 */

	public boolean[] mergeEdges(int e1, int e2, boolean center) {
		// dumpMesh();
		if (edges[e1].face != -1)
			e1 = edges[e1].hedge;
		if (edges[e2].face != -1)
			e2 = edges[e2].hedge;
		int pe1, ne1, pe2, ne2, he1, he2, phe2;
		int v1, v2;
		int re1 = -1;
		int re2 = -1;
		pe1 = getPreviousEdge(e1);
		ne1 = edges[e1].next;
		pe2 = getPreviousEdge(e2);
		ne2 = edges[e2].next;
		he1 = edges[e1].hedge;
		he2 = edges[e2].hedge;
		phe2 = getPreviousEdge(he2);
		if (ne1 == pe2)
			re1 = ne1;
		if (ne2 == pe1)
			re2 = ne2;
		v1 = edges[pe2].vertex;
		v2 = edges[phe2].vertex;
		if (ne1 == e2) {
			vertices[v1].edge = edges[e1].hedge;
			v1 = -1;
		} else {
			if (center) {
				vertices[edges[e1].vertex].r.add(vertices[v1].r);
				vertices[edges[e1].vertex].r.scale(0.5);
			}
			int[] ve = getVertexEdges(vertices[v1]);
			for (int i = 0; i < ve.length; ++i)
				edges[edges[ve[i]].hedge].vertex = edges[e1].vertex;
		}
		if (ne2 == e1) {
			vertices[v2].edge = e1;
			v2 = -1;
		} else {
			int[] ve = getVertexEdges(vertices[v2]);
			if (center) {
				vertices[edges[he1].vertex].r.add(vertices[v2].r);
				vertices[edges[he1].vertex].r.scale(0.5);
			}
			for (int i = 0; i < ve.length; ++i)
				edges[edges[ve[i]].hedge].vertex = edges[he1].vertex;
		}
		// System.out.println( "v1: " + pe1 );
		// System.out.println( "v2: " + pe2 );
		if (re1 == -1)
			edges[pe2].next = ne1;
		else {
			int p = edges[re1].hedge;
			edges[getPreviousEdge(p)].next = edges[p].next;
			vertices[edges[re1].vertex].edge = edges[e1].hedge;
			vertices[edges[edges[re1].hedge].vertex].edge = edges[e1].hedge;
		}
		if (re2 == -1)
			edges[pe1].next = ne2;
		else {
			int p = edges[re2].hedge;
			edges[getPreviousEdge(p)].next = edges[p].next;
			vertices[edges[re2].vertex].edge = e1;
			vertices[edges[edges[re2].hedge].vertex].edge = e1;
		}
		edges[phe2].next = e1;
		edges[e1].face = edges[he2].face;
		edges[e1].next = edges[he2].next;
		int[] vertexTable = new int[vertices.length];
		int[] edgeTable = new int[edges.length];
		int nv1, nv2;
		if (v1 == -1)
			nv1 = 1;
		else
			nv1 = 0;
		if (v2 == -1)
			nv2 = 1;
		else
			nv2 = 0;
		Wvertex[] newVert = new Wvertex[vertices.length - 2 + nv1 + nv2];
		// System.out.println( newVert.length + " /// " + vertices.length);
		Wedge[] newEdges;
		if (re1 != -1 && re2 != -1)
			newEdges = new Wedge[edges.length - 6];
		else if (re1 != -1 || re2 != -1)
			newEdges = new Wedge[edges.length - 4];
		else
			newEdges = new Wedge[edges.length - 2];
		int count = 0;
		for (int j = 0; j < vertices.length; ++j) {
			if (j != v1 && j != v2) {
				newVert[count] = new Wvertex(vertices[j]);
				vertexTable[j] = count++;
			}
		}
		if (v1 != -1)
			vertexTable[v1] = -vertexTable[edges[e1].vertex] - 1;
		if (v2 != -1)
			vertexTable[v2] = -vertexTable[edges[edges[e1].hedge].vertex] - 1;
		count = 0;
		int hre1 = -1;
		int hre2 = -1;
		if (re1 != -1)
			hre1 = edges[re1].hedge;
		if (re2 != -1)
			hre2 = edges[re2].hedge;
		for (int j = 0; j < edges.length; ++j) {
			if (j != e2 && j != edges[e2].hedge && j != re1 && j != re2
					&& j != hre1 && j != hre2) {
				edgeTable[j] = count;
				newEdges[count] = new Wedge(edges[j]);
				++count;
			} else
				edgeTable[j] = -1;
		}
		Wface[] newFaces = new Wface[faces.length];
		for (int j = 0; j < faces.length; ++j) {
			newFaces[j] = new Wface(faces[j]);
			int e = faces[j].edge;
			while (edgeTable[e] == -1)
				e = edges[e].next;
			newFaces[j].edge = edgeTable[e];
		}
		for (int j = 0; j < newEdges.length; ++j) {
			// if (edgeTable[newEdges[j].next] == -1)
			// System.out.println("warning : " + newEdges[j].next +
			// "rï¿½fï¿½rencï¿½");
			newEdges[j].next = edgeTable[newEdges[j].next];
			newEdges[j].hedge = edgeTable[newEdges[j].hedge];
			newEdges[j].vertex = vertexTable[newEdges[j].vertex];
		}
		for (int j = 0; j < newVert.length; ++j)
			newVert[j].edge = edgeTable[newVert[j].edge];
		// Update the texture parameters.
		TextureParameter param[] = getParameters();
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVert.length];
					for (int j = 0; j < oldval.length; j++)
						if (vertexTable[j] >= 0)
							newval[vertexTable[j]] = oldval[j];
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double[] newval[] = new double[faces.length][];
					for (int j = 0; j < faces.length; ++j) {
						int[] ofv = getFaceVertices(j, edges, faces);
						int[] nfv = getFaceVertices(j, newEdges, newFaces);
						for (int l = 0; l < nfv.length; l++)
							for (int m = 0; m < ofv.length; m++) {
								int vertex = vertexTable[ofv[m]];
								if (vertex < 0)
									vertex = -vertex - 1;
								if (nfv[l] == vertex)
									newval[j][l] = fvpv.getValue(j, m);
							}
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		vertices = newVert;
		edges = newEdges;
		faces = newFaces;
		// dumpMesh();
		removeTwoEdgeBoundaries();
		// dumpMesh();
		resetMesh();
		boolean[] newSel = new boolean[edges.length / 2];
		e1 = edgeTable[e1];
		if (e1 < edges.length / 2)
			newSel[e1] = true;
		else
			newSel[edges[e1].hedge] = true;
		return newSel;
	}

	/**
	 * Merges two boundary edges selections.
	 * 
	 * @param e1
	 *            First edge to merge whithin selection
	 * @param e2
	 *            Second edge to merge within selection
	 * @param sel
	 *            Boundary edges seelction (may be null)
	 * @return Selection corresponding to merged edges.
	 */

	public boolean[] mergeEdges(int e1, int e2, boolean[] sel, boolean center) {
		if (sel == null)
			return mergeEdges(e1, e2, center);

		int beforee1, aftere1, beforee2, aftere2, firste1, firste2;
		boolean closed1 = false;
		boolean closed2 = false;
		boolean[] fullSel = new boolean[edges.length];
		for (int i = 0; i < edges.length / 2; i++) {
			fullSel[i] = sel[i];
			fullSel[edges[i].hedge] = sel[i];
		}
		// dumpMesh();
		if (edges[e1].face != -1)
			e1 = edges[e1].hedge;
		if (edges[e2].face != -1)
			e2 = edges[e2].hedge;
		beforee1 = 0;
		firste1 = e1;
		int p = getPreviousEdge(e1);
		while (fullSel[p] && p != e1) {
			if (p == e2)
				return null;
			++beforee1;
			firste1 = p;
			p = getPreviousEdge(p);
		}
		if (p == e1)
			closed1 = true;
		beforee2 = 0;
		firste2 = e2;
		p = getPreviousEdge(e2);
		while (fullSel[p] && p != e2) {
			++beforee2;
			firste2 = p;
			p = getPreviousEdge(p);
		}
		if (p == e2)
			closed2 = true;
		aftere1 = aftere2 = 0;
		if (!closed1) {
			aftere1 = 0;
			p = edges[e1].next;
			while (fullSel[p]) {
				if (p == e2)
					return null;
				++aftere1;
				p = edges[p].next;
			}
		}
		if (!closed2) {
			aftere2 = 0;
			p = edges[e2].next;
			while (fullSel[p]) {
				++aftere2;
				p = edges[p].next;
			}
		}
		int[] vertexTable = new int[vertices.length];
		int[] edgeTable = new int[edges.length];
		Wvertex[] newVert = null;
		Wedge[] newEdges = null;
		int[] edges1 = null;
		int[] edges2 = null;
		// System.out.println("beforee1 / aftere1 " + beforee1 + " " + aftere1);
		// System.out.println("beforee2 / aftere2 " + beforee2 + " " + aftere2);
		// System.out.println("closed1: " + closed1);
		// System.out.println("closed2: " + closed2);
		// if both selection are boundaries, treat this special case
		if (closed1 && closed2) {
			if (beforee1 != beforee2)
				return null;
			int l = beforee1 + 1;
			edges1 = new int[l];
			edges2 = new int[l];
			edges1[0] = e1;
			int count = 1;
			while (count != edges1.length) {
				edges1[count] = edges[edges1[count - 1]].next;
				count++;
			}
			edges2[0] = edges[e2].next;
			count = 1;
			while (count != edges2.length) {
				edges2[count] = edges[edges2[count - 1]].next;
				count++;
			}
			int he2, phe2;
			int[] verts = new int[edges2.length];
			for (int i = 0; i < l; i++) {
				e2 = edges2[l - i - 1];
				e1 = edges1[i];
				he2 = edges[e2].hedge;
				phe2 = getPreviousEdge(he2);
				verts[i] = edges[e2].vertex;
				if (center) {
					vertices[edges[edges[e1].hedge].vertex].r
							.add(vertices[verts[i]].r);
					vertices[edges[edges[e1].hedge].vertex].r.scale(0.5);
				}
				int[] ve = getVertexEdges(vertices[verts[i]]);
				for (int j = 0; j < ve.length; ++j)
					edges[edges[ve[j]].hedge].vertex = edges[edges[e1].hedge].vertex;
				edges[phe2].next = e1;
				edges[e1].face = edges[he2].face;
				edges[e1].next = edges[he2].next;
			}
			newVert = new Wvertex[vertices.length - l];
			newEdges = new Wedge[edges.length - l * 2];
			count = 0;
			boolean deleted;
			for (int i = 0; i < vertices.length; ++i) {
				deleted = false;
				for (int j = 0; j < verts.length; j++)
					if (i == verts[j])
						deleted = true;
				if (!deleted) {
					newVert[count] = new Wvertex(vertices[i]);
					vertexTable[i] = count++;
				} else
					vertexTable[i] = -1;
			}
			count = 0;
			for (int i = 0; i < edges.length; i++) {
				deleted = false;
				for (int j = 0; j < edges2.length; j++)
					if (i == edges2[j] || i == edges[edges2[j]].hedge)
						deleted = true;
				if (!deleted) {
					edgeTable[i] = count;
					newEdges[count] = new Wedge(edges[i]);
					++count;
				} else
					edgeTable[i] = -1;
			}
			for (int j = 0; j < faces.length; ++j) {
				int e = faces[j].edge;
				while (edgeTable[e] == -1)
					e = edges[e].next;
				faces[j].edge = edgeTable[e];
			}
			for (int j = 0; j < newEdges.length; ++j) {
				newEdges[j].next = edgeTable[newEdges[j].next];
				newEdges[j].hedge = edgeTable[newEdges[j].hedge];
				newEdges[j].vertex = vertexTable[newEdges[j].vertex];
			}
			for (int j = 0; j < newVert.length; ++j)
				newVert[j].edge = edgeTable[newVert[j].edge];
		} else // two seperate selections
		{
			if (beforee1 != aftere2 || aftere1 != beforee2)
				return null;
			edges1 = new int[beforee1 + aftere1 + 1];
			edges2 = new int[beforee2 + aftere2 + 1];
			edges1[0] = firste1;
			int count = 1;
			while (count != edges1.length) {
				edges1[count] = edges[edges1[count - 1]].next;
				count++;
			}
			edges2[0] = firste2;
			count = 1;
			while (count != edges2.length) {
				edges2[count] = edges[edges2[count - 1]].next;
				count++;
			}
			int pe1, ne1, pe2, ne2, he2, phe2;
			int re1 = -1;
			int re2 = -1;

			pe1 = getPreviousEdge(edges1[0]);
			ne1 = edges[edges1[edges1.length - 1]].next;
			pe2 = getPreviousEdge(edges2[0]);
			ne2 = edges[edges2[edges2.length - 1]].next;
			if (ne1 == pe2)
				re1 = ne1;
			if (ne2 == pe1)
				re2 = ne2;
			int[] verts = new int[edges2.length + 1];
			int v;
			for (int i = 0; i < edges2.length; ++i) {
				v = verts[i] = edges[edges2[edges2.length - 1 - i]].vertex;
				if (center) {
					vertices[edges[edges[edges1[i]].hedge].vertex].r
							.add(vertices[v].r);
					vertices[edges[edges[edges1[i]].hedge].vertex].r.scale(0.5);
				}
				int[] ve = getVertexEdges(vertices[v]);
				for (int j = 0; j < ve.length; ++j)
					edges[edges[ve[j]].hedge].vertex = edges[edges[edges1[i]].hedge].vertex;
			}
			v = verts[edges1.length] = edges[edges[edges2[0]].hedge].vertex;
			if (center) {
				vertices[edges[edges1[edges1.length - 1]].vertex].r
						.add(vertices[v].r);
				vertices[edges[edges1[edges1.length - 1]].vertex].r.scale(0.5);
			}
			int[] ve = getVertexEdges(vertices[v]);
			for (int j = 0; j < ve.length; ++j)
				edges[edges[ve[j]].hedge].vertex = edges[edges1[edges1.length - 1]].vertex;
			if (re1 == -1)
				edges[pe2].next = ne1;
			else {
				p = edges[re1].hedge;
				edges[getPreviousEdge(p)].next = edges[p].next;
				vertices[edges[re1].vertex].edge = edges[edges1[edges1.length - 1]].hedge;
				vertices[edges[edges[re1].hedge].vertex].edge = edges[edges1[edges1.length - 1]].hedge;
			}
			if (re2 == -1)
				edges[pe1].next = ne2;
			else {
				p = edges[re2].hedge;
				edges[getPreviousEdge(p)].next = edges[p].next;
				vertices[edges[re2].vertex].edge = edges1[0];
				vertices[edges[edges[re2].hedge].vertex].edge = edges1[0];
			}
			for (int i = 0; i < edges1.length; ++i) {
				he2 = edges[edges2[edges2.length - 1 - i]].hedge;
				phe2 = getPreviousEdge(he2);
				edges[phe2].next = edges1[i];
				edges[edges1[i]].face = edges[he2].face;
				edges[edges1[i]].next = edges[he2].next;
			}
			newVert = new Wvertex[vertices.length - edges1.length - 1];
			if (re1 != -1 && re2 != -1)
				newEdges = new Wedge[edges.length - edges1.length * 2 - 4];
			else if (re1 != -1 || re2 != -1)
				newEdges = new Wedge[edges.length - edges1.length * 2 - 2];
			else
				newEdges = new Wedge[edges.length - edges1.length * 2];

			count = 0;
			boolean deleted;
			for (int i = 0; i < vertices.length; ++i) {
				deleted = false;
				for (int j = 0; j < verts.length; j++)
					if (i == verts[j])
						deleted = true;
				if (!deleted) {
					newVert[count] = new Wvertex(vertices[i]);
					vertexTable[i] = count++;
				} else
					vertexTable[i] = -1;
			}
			count = 0;
			int hre1 = -1;
			int hre2 = -1;
			if (re1 != -1)
				hre1 = edges[re1].hedge;
			if (re2 != -1)
				hre2 = edges[re2].hedge;
			for (int i = 0; i < edges.length; i++) {
				deleted = false;
				for (int j = 0; j < edges2.length; j++)
					if (i == edges2[j] || i == edges[edges2[j]].hedge
							|| i == re1 || i == re2 || i == hre1 || i == hre2)
						deleted = true;
				if (!deleted) {
					edgeTable[i] = count;
					newEdges[count] = new Wedge(edges[i]);
					++count;
				} else
					edgeTable[i] = -1;
			}
			for (int j = 0; j < faces.length; ++j) {
				int e = faces[j].edge;
				while (edgeTable[e] == -1)
					e = edges[e].next;
				faces[j].edge = edgeTable[e];
			}
			for (int j = 0; j < newEdges.length; ++j) {
				newEdges[j].next = edgeTable[newEdges[j].next];
				newEdges[j].hedge = edgeTable[newEdges[j].hedge];
				newEdges[j].vertex = vertexTable[newEdges[j].vertex];
			}
			for (int j = 0; j < newVert.length; ++j)
				newVert[j].edge = edgeTable[newVert[j].edge];
		}
		// Update the texture parameters.
		TextureParameter param[] = getParameters();
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVert.length];
					for (int j = 0; j < oldval.length; j++)
						if (vertexTable[j] != -1)
							newval[vertexTable[j]] = oldval[j];
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[faces.length][];
					for (int j = 0; j < faces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, faces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		vertices = newVert;
		edges = newEdges;
		removeTwoEdgeBoundaries();
		// dumpMesh();
		resetMesh();
		boolean[] newSel = new boolean[edges.length / 2];
		for (int i = 0; i < edges1.length; ++i) {
			edges1[i] = edgeTable[edges1[i]];
			if (edges1[i] < edges.length / 2)
				newSel[edges1[i]] = true;
			else
				newSel[edges[edges1[i]].hedge] = true;
		}
		return newSel;
	}

	/**
	 * This method scans the mesh for boundaries that are exactly two edges long
	 */
	public void removeTwoEdgeBoundaries() {
		int[] edgeTable = new int[edges.length];
		boolean[] deleted = new boolean[edges.length];
		Wedge[] newEdges = null;
		boolean hasDeleted = false;
		for (int i = 0; i < edges.length; ++i) {
			if (!deleted[i]) {
				if (edges[i].face == -1 && edges[edges[i].next].next == i
						&& edges[i].next != i && !deleted[edges[i].next]) {
					if (edges[edges[i].next].face != -1)
						System.out
								.println("Houston, We've got a problem in removeTwoEdgeBoundaries");
					int e2 = edges[i].next;
					int he2 = edges[e2].hedge;
					int phe2 = getPreviousEdge(he2);
					edges[phe2].next = i;
					edges[i].face = edges[he2].face;
					edges[i].next = edges[he2].next;
					vertices[edges[i].vertex].edge = edges[i].hedge;
					vertices[edges[edges[i].hedge].vertex].edge = i;
					deleted[e2] = true;
					deleted[edges[e2].hedge] = true;
					hasDeleted = true;
				}
			}
		}
		if (!hasDeleted)
			return;
		int count = 0;
		for (int i = 0; i < edges.length; ++i)
			if (!deleted[i]) {
				edgeTable[i] = count;
				++count;
			} else
				edgeTable[i] = -1;

		for (int j = 0; j < faces.length; ++j) {
			int e = faces[j].edge;
			while (edgeTable[e] == -1)
				e = edges[e].next;
			faces[j].edge = edgeTable[e];
		}
		newEdges = new Wedge[count];
		count = 0;
		for (int i = 0; i < edges.length; ++i)
			if (!deleted[i])
				newEdges[count++] = new Wedge(edges[i]);
		for (int j = 0; j < newEdges.length; ++j) {
			newEdges[j].next = edgeTable[newEdges[j].next];
			newEdges[j].hedge = edgeTable[newEdges[j].hedge];
		}
		for (int j = 0; j < vertices.length; ++j)
			vertices[j].edge = edgeTable[vertices[j].edge];
		edges = newEdges;
	}

	public ArrayList extractCurveFromSelection(boolean[] sel) {
		int beforee1, aftere1, firste1, e1;
		ArrayList curves = new ArrayList();
		ArrayList curve;
		ArrayList closed = new ArrayList();
		// deal with open curves
		for (int i = 0; i < edges.length / 2; i++)
			if (sel[i]) {
				int[] ve = getVertexEdges(vertices[edges[i].vertex]);
				int selected = 0;
				boolean regular = false;
				for (int j = 0; j < ve.length; j++)
					if (ve[j] != i && ve[j] != edges[i].hedge
							&& isEdgeSelected(ve[j], sel)) {
						selected++;
						regular = true;
					}
				int[] ve2 = getVertexEdges(vertices[edges[edges[i].hedge].vertex]);
				for (int j = 0; j < ve2.length; j++)
					if (ve2[j] != i && ve2[j] != edges[i].hedge
							&& isEdgeSelected(ve2[j], sel))
						selected++;
				if (selected >= 2)
					continue;
				int p = edges[i].hedge;
				if (regular)
					p = i;
				curve = new ArrayList();
				curve.add(new Vec3(vertices[edges[edges[p].hedge].vertex].r));
				while (isEdgeSelected(p, sel)) {
					curve.add(new Vec3(vertices[edges[p].vertex].r));
					if (p < edges.length / 2)
						sel[p] = false;
					else
						sel[edges[p].hedge] = false;
					ve = getVertexEdges(vertices[edges[p].vertex]);
					for (int j = 0; j < ve.length; j++)
						if (ve[j] != p && ve[j] != edges[p].hedge
								&& isEdgeSelected(ve[j], sel))
							p = ve[j];

				}
				curves.add(curve);
				closed.add(new Boolean(false));
			}
		// now deal with closed ones
		for (int i = 0; i < edges.length / 2; i++)
			if (sel[i]) {
				int p = i;
				curve = new ArrayList();
				int[] ve;
				while (isEdgeSelected(p, sel)) {
					System.out.println("adding " + p);
					curve.add(new Vec3(vertices[edges[p].vertex].r));
					if (p < edges.length / 2)
						sel[p] = false;
					else
						sel[edges[p].hedge] = false;
					ve = getVertexEdges(vertices[edges[p].vertex]);
					for (int j = 0; j < ve.length; j++)
						if (ve[j] != p && ve[j] != edges[p].hedge
								&& isEdgeSelected(ve[j], sel))
							p = ve[j];

				}
				curves.add(curve);
				closed.add(new Boolean(true));
			}
		curves.add(closed);
		return curves;
	}

	private boolean isEdgeSelected(int e, boolean[] sel) {
		if (e < edges.length / 2)
			return sel[e];
		else
			return sel[edges[e].hedge];
	}

	/**
	 * Divides segments according to a fraction array fractions with value
	 * between 0 and 1 indicates which segments are to be divided and how
	 * 
	 * @param fractions
	 *            Fraction value used to position the new vertices
	 * @return Array of selected vertices for further selection
	 */
	public boolean[] divideEdges(double[] fractions) {
		Wvertex[] newVertices;
		Wedge[] newEdges;
		double fraction;
		int[] toVert;
		int[] fromVert;
		double[] fract;

		int size = vertices.length;
		int count = 0;
		for (int i = 0; i < fractions.length; ++i)
			if (fractions[i] > 0 && fractions[i] < 1)
				count++;
		newVertices = new Wvertex[vertices.length + count];
		newEdges = new Wedge[edges.length + 2 * count];
		toVert = new int[count];
		fromVert = new int[count];
		fract = new double[count];
		translateMesh(newVertices, newEdges, faces);
		int index = 0;
		int vl = vertices.length;
		int el = edges.length / 2;
		for (int i = 0; i < fractions.length; ++i) {
			if (fractions[i] > 0 && fractions[i] < 1) {
				Vec3 delta;
				int to = newEdges[i].vertex;
				int from = newEdges[newEdges[i].hedge].vertex;
				int edgeTo = newEdges[i].next;
				int faceTo = newEdges[i].face;
				int faceFrom = newEdges[newEdges[i].hedge].face;
				delta = newVertices[to].r.minus(newVertices[from].r);
				delta.scale(fractions[i]);
				int ed = newVertices[newEdges[i].vertex].edge;
				while (newEdges[newEdges[ed].hedge].next != newEdges[i].hedge)
					ed = newEdges[newEdges[ed].hedge].next;
				newVertices[vl + index] = new Wvertex(vertices[from].r
						.plus(delta), el + index);
				newEdges[i].vertex = vl + index;
				newEdges[i].next = el + index;
				newEdges[el + index] = new Wedge(to, el + index
						+ newEdges.length / 2, faceTo, edgeTo);
				newEdges[el + index + newEdges.length / 2] = new Wedge(vl
						+ index, el + index, faceFrom, newEdges[i].hedge);
				newEdges[el + index].smoothness = edges[i].smoothness;
				newEdges[el + index + newEdges.length / 2].smoothness = edges[i].smoothness;
				newVertices[to].edge = el + index + newEdges.length / 2;
				fraction = fractions[i];
				// newVertices[vl + index].smoothness = (float) ( fraction *
				// vertices[to].smoothness + ( 1 - fraction ) * (
				// vertices[from].smoothness ) );
				toVert[index] = to;
				fromVert[index] = from;
				fract[index] = fraction;
				newEdges[newEdges[ed].hedge].next = newEdges[el + index].hedge;
				++index;
			}
		}
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newVertices.length; ++j) {
						fraction = fract[j - oldval.length];
						newval[j] = fraction
								* oldval[toVert[j - oldval.length]]
								+ (1 - fraction)
								* (newval[fromVert[j - oldval.length]]);
					}
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[faces.length][];
					for (int j = 0; j < faces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, faces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);

		}
		boolean[] newSel = new boolean[newVertices.length];
		for (int i = vertices.length; i < newVertices.length; ++i)
			newSel[i] = true;
		edges = newEdges;
		vertices = newVertices;

		resetMesh();
		return newSel;
	}

	/**
	 * Divides segments according to a single fraction value
	 * 
	 * @param sel
	 *            Edges selection
	 * @param fraction
	 *            Fraction value used to position the new vertices
	 * @return Array of selected vertices for further selection
	 */
	public boolean[] divideEdges(boolean[] sel, double fraction) {
		if (fraction <= 0 || fraction >= 1)
			return new boolean[vertices.length];
		Wvertex[] newVertices;
		Wedge[] newEdges;
		int[] toVert;
		int[] fromVert;
		Vec3 v;
		Vec3 maxv = null;
		double maxLength = 0;
		double val;

		int size = vertices.length;
		int count = 0;
		for (int i = 0; i < sel.length; ++i)
			if (sel[i]) {
				count++;
				v = vertices[edges[i].vertex].r
						.minus(vertices[edges[edges[i].hedge].vertex].r);
				if (v.length() > maxLength) {
					maxv = v;
					maxLength = v.length();
				}
			}
		if (count == 0)
			return new boolean[vertices.length];
		newVertices = new Wvertex[vertices.length + count];
		newEdges = new Wedge[edges.length + 2 * count];
		toVert = new int[count];
		fromVert = new int[count];
		translateMesh(newVertices, newEdges, faces);
		int index = 0;
		int vl = vertices.length;
		int el = edges.length / 2;
		for (int i = 0; i < sel.length; ++i) {
			if (sel[i]) {
				Vec3 delta;
				int to = newEdges[i].vertex;
				int from = newEdges[newEdges[i].hedge].vertex;
				int edgeTo = newEdges[i].next;
				int faceTo = newEdges[i].face;
				int faceFrom = newEdges[newEdges[i].hedge].face;
				delta = newVertices[to].r.minus(newVertices[from].r);
				if (delta.dot(maxv) < 0)
					val = 1 - fraction;
				else
					val = fraction;
				delta.scale(val);
				int ed = newVertices[newEdges[i].vertex].edge;
				while (newEdges[newEdges[ed].hedge].next != newEdges[i].hedge)
					ed = newEdges[newEdges[ed].hedge].next;
				newVertices[vl + index] = new Wvertex(vertices[from].r
						.plus(delta), el + index);
				newEdges[i].vertex = vl + index;
				newEdges[i].next = el + index;
				newEdges[el + index] = new Wedge(to, el + index
						+ newEdges.length / 2, faceTo, edgeTo);
				newEdges[el + index + newEdges.length / 2] = new Wedge(vl
						+ index, el + index, faceFrom, newEdges[i].hedge);
				newEdges[el + index].smoothness = edges[i].smoothness;
				newEdges[el + index + newEdges.length / 2].smoothness = edges[i].smoothness;
				newVertices[to].edge = el + index + newEdges.length / 2;
				// newVertices[vl + index].smoothness = (float) ( val *
				// vertices[to].smoothness + ( 1 - val ) * (
				// vertices[from].smoothness ) );
				if (val != fraction) {
					toVert[index] = from;
					fromVert[index] = to;
				} else {
					toVert[index] = to;
					fromVert[index] = from;
				}
				newEdges[newEdges[ed].hedge].next = newEdges[el + index].hedge;
				++index;
			}
		}
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newVertices.length; ++j) {
						newval[j] = fraction
								* oldval[toVert[j - oldval.length]]
								+ (1 - fraction)
								* (newval[fromVert[j - oldval.length]]);
					}
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[faces.length][];
					for (int j = 0; j < faces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, faces);
						double nval[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							nval[l] = fvpv.getAverageValue();
						}
						newval[j] = nval;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);

		}
		boolean[] newSel = new boolean[newVertices.length];
		for (int i = vertices.length; i < newVertices.length; ++i)
			newSel[i] = true;
		edges = newEdges;
		vertices = newVertices;

		resetMesh();
		return newSel;
	}

	/**
	 * Connects selected vertices, creating new faces in the process.
	 * 
	 * @param sel
	 *            Array of selected vertices
	 */
	public void connectVertices(boolean[] sel) {
		int count = 0;
		for (int i = 0; i < sel.length; ++i)
			if (sel[i])
				count++;
		if (count < 2)
			return;
		int[] connect = new int[count];
		count = 0;
		for (int i = 0; i < sel.length; ++i)
			if (sel[i])
				connect[count++] = i;
		connectVertices(connect);
	}

	/**
	 * Connects selected vertices, creating new faces in the process.
	 * 
	 * @param connect
	 *            Array of selected vertices indices
	 */
	public void connectVertices(int[] connect) {
		boolean add;
		boolean edgeShare;
		int i1;
		int i2;
		int i1min = -1;
		int i2min = -1;
		int start;
		int stop;
		int faceMin = 0;
		double penaltyMin = Double.MAX_VALUE;
		int face = 0;
		double area1;
		double area2;
		int[] e1;
		int[] e2;
		int kk;
		int ll;

		if (connect.length < 2)
			return;

		// s1 = null;
		// s2 = null;
		// s3 = null;
		for (int i = 1; i < connect.length; ++i) {
			for (int j = 0; j < i; ++j) {
				i1 = connect[i];
				i2 = connect[j];
				e1 = getVertexEdges(vertices[i1]);
				e2 = getVertexEdges(vertices[i2]);
				add = false;
				edgeShare = false;
				// first check: i1 and i2 should border the same face and not be
				// contiguous
				for (int k = 0; k < e1.length; ++k) {
					if (edges[e1[k]].vertex == i2) {
						edgeShare = true;
						break;
					}
				}
				if (edgeShare)
					continue;
				for (int k = 0; k < e1.length; ++k) {
					for (int l = 0; l < e2.length; ++l) {
						if (edges[e1[k]].face == edges[e2[l]].face)
							if (edges[e1[k]].face != -1) {
								face = edges[e1[k]].face;
								add = true;
							}
					}
				}
				if (!add) {
					// System.out.println( i1 + " " + i2 + " rejected face
					// share" );
					continue;
				}
				// System.out.println( i1 + " " + i2 + " candidates for face: "
				// + face );

				// area calculation : no null area face permitted
				int[] vf = getFaceVertices(faces[face]);
				if (vf.length == 4) {
					ll = 0;
					for (int k = 0; k < vf.length; ++k)
						for (int l = 0; l < connect.length; ++l)
							if (connect[l] == vf[k])
								++ll;
					if (ll > 2)
						continue;
				}
				start = 0;
				for (int k = 0; k < vf.length; ++k) {
					if (vf[k] == i1) {
						start = k;
						break;
					}
				}
				stop = 0;
				for (int k = 1; k < vf.length; ++k) {
					if (vf[k] == i2) {
						stop = k;
						break;
					}
				}
				if (start > stop) {
					ll = start;
					start = stop;
					stop = ll;
					ll = i1;
					i1 = i2;
					i2 = ll;
				}
				int[] vf1 = new int[vf.length - (stop - start) + 1];
				int[] vf2 = new int[stop - start + 1];
				kk = 0;
				while (kk <= start) {
					vf1[kk] = vf[kk];
					++kk;
				}
				kk = stop;
				while (kk < vf.length) {
					vf1[kk - (vf.length - vf1.length)] = vf[kk];
					++kk;
				}
				for (kk = start; kk <= stop; ++kk)
					vf2[kk - start] = vf[kk];
				if (vf1.length == 3) {
					ll = 0;
					for (int k = 0; k < vf1.length; ++k)
						for (int l = 0; l < connect.length; ++l)
							if (connect[l] == vf1[k])
								++ll;
					if (ll > 2)
						continue;
				}
				if (vf2.length == 3) {
					ll = 0;
					for (int k = 0; k < vf2.length; ++k)
						for (int l = 0; l < connect.length; ++l)
							if (connect[l] == vf2[k])
								++ll;
					if (ll > 2)
						continue;
				}
				area1 = triangulate(vf1, 0, true);
				area2 = triangulate(vf2, 0, true);
				// System.out.println( "area: " + area1 + " " + area2 );
				if ((area1 < 1e-10) || (area2 < 1e-10))
					continue;
				// two valid faces can be constructed.
				// decision depends on penalty factors
				double penalty = 0;
				penalty += calcPenalty(vf1);
				penalty += calcPenalty(vf2);
				// ll = vf1.length * vf1.length + vf2.length * vf2.length;
				if (penalty < penaltyMin) {
					i1min = i1;
					i2min = i2;
					faceMin = face;
					penaltyMin = penalty;
				}
			}
		}
		if (i1min < 0)
			return;
		int e1prev = 0;
		int e1next = 0;
		int e2next = 0;
		int e2prev = 0;
		e1 = getVertexEdges(vertices[i1min]);
		for (int i = 0; i < e1.length; ++i) {
			if (edges[e1[i]].face == faceMin)
				e1next = e1[i];
			if (edges[edges[e1[i]].hedge].face == faceMin)
				e1prev = edges[e1[i]].hedge;
		}
		e2 = getVertexEdges(vertices[i2min]);
		for (int i = 0; i < e2.length; ++i) {
			if (edges[e2[i]].face == faceMin)
				e2next = e2[i];
			if (edges[edges[e2[i]].hedge].face == faceMin)
				e2prev = edges[e2[i]].hedge;
		}
		Wedge[] newEdges = new Wedge[edges.length + 2];
		int newEdge = edges.length / 2;
		if (e1prev >= edges.length / 2)
			e1prev += newEdges.length / 2 - edges.length / 2;
		if (e2prev >= edges.length / 2)
			e2prev += newEdges.length / 2 - edges.length / 2;
		if (e1next >= edges.length / 2)
			e1next += newEdges.length / 2 - edges.length / 2;
		if (e2next >= edges.length / 2)
			e2next += newEdges.length / 2 - edges.length / 2;
		for (int i = 0; i < edges.length / 2; ++i) {
			newEdges[i] = new Wedge(edges[i]);
			newEdges[i].hedge = edges[i].hedge + newEdges.length / 2
					- edges.length / 2;
			if (newEdges[i].next >= edges.length / 2)
				newEdges[i].next += newEdges.length / 2 - edges.length / 2;
			newEdges[newEdges[i].hedge] = new Wedge(edges[edges[i].hedge]);
			if (newEdges[newEdges[i].hedge].next >= edges.length / 2)
				newEdges[newEdges[i].hedge].next += newEdges.length / 2
						- edges.length / 2;
		}
		newEdges[newEdge] = new Wedge(i1min, newEdges.length - 1, faceMin,
				e1next);
		newEdges[newEdges.length - 1] = new Wedge(i2min, newEdge, faceMin,
				e2next);
		newEdges[newEdge].smoothness = 1.0f;
		newEdges[newEdges.length - 1].smoothness = 1.0f;
		newEdges[e2prev].next = newEdge;
		newEdges[e1prev].next = newEdges.length - 1;
		int newFace = faces.length;
		Wface[] newFaces = new Wface[newFace + 1];
		for (int i = 0; i < faces.length; ++i) {
			newFaces[i] = faces[i];
			if (newFaces[i].edge >= edges.length / 2)
				newFaces[i].edge += newEdges.length / 2 - edges.length / 2;
		}
		changeFace(newEdges, newEdge, newFace);
		newFaces[newFace] = new Wface(newEdge);
		// newFaces[newFace].edgeSmoothness = faces[faceMin].edgeSmoothness;
		// newFaces[newFace].centerSmoothness = faces[faceMin].centerSmoothness;
		// newFaces[newFace].convex = faces[faceMin].convex;
		for (int i = 0; i < vertices.length; ++i)
			if (vertices[i].edge >= edges.length / 2)
				vertices[i].edge += newEdges.length / 2 - edges.length / 2;
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[newFaces.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					newval[faces.length] = newval[faceMin];
					newParamVal[i] = new FaceParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[i] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[i] = oldParamVal[i].duplicate();
			}
			setParameterValues(newParamVal);
		}
		faces = newFaces;
		edges = newEdges;
		// connect any other vertices left selected (recursion)
		connectVertices(connect);
		resetMesh();

	}

	/**
	 * Select an edge loop from a single edge.
	 * 
	 * @param startEdge
	 *            the index of the edge from which to find an edge loop
	 * @return a selection containing all the edges in the loop, or null if no
	 *         loop could be found
	 */

	private boolean[] findSingleEdgeLoop(int startEdge) {
		boolean newSel[] = new boolean[edges.length];
		int currentEdge = startEdge;
		int currentVert = edges[startEdge].vertex;
		Vector v = new Vector();
		Vec3 normDir = null;

		Vec3 vv = vertices[edges[startEdge].vertex].r
				.minus(vertices[edges[edges[startEdge].hedge].vertex].r);
		if (vv.length() < 1.0e-6)
			v.add(vv);
		while (true) {
			if (newSel[currentEdge]) {
				if (currentEdge == startEdge) {
					// System.out.println("good loop");
					return newSel; // This is a good edge loop.
				}
				return null; // The path looped back on itself without
				// hitting the original edge.
			}

			// Find the next edge which is most nearly parallel to this one.

			newSel[currentEdge] = true;
			Vec3 dir1 = vertices[edges[currentEdge].vertex].r
					.minus(vertices[edges[edges[currentEdge].hedge].vertex].r);
			dir1.normalize();
			int vertEdges[] = getVertexEdges(vertices[edges[currentEdge].vertex]);
			int bestEdge = -1;
			if (vertEdges.length == 4) {
				bestEdge = edges[edges[currentEdge].next].hedge;
				bestEdge = edges[bestEdge].next;
			} else {
				if (v.size() < 2) {
					double maxDot = -1.0;
					for (int i = 0; i < vertEdges.length; i++) {
						if (vertEdges[i] == edges[currentEdge].hedge)
							continue;
						Vec3 dir2 = vertices[edges[vertEdges[i]].vertex].r
								.minus(vertices[edges[edges[vertEdges[i]].hedge].vertex].r);
						dir2.normalize();
						double dot = dir1.dot(dir2);
						if (edges[currentEdge].vertex == edges[vertEdges[i]].vertex
								|| edges[currentEdge].vertex == edges[vertEdges[i]].vertex)
							dot = -dot;
						if (dot > maxDot) {
							maxDot = dot;
							bestEdge = vertEdges[i];
						}
					}
				} else {
					double minDot = 1.0;
					for (int i = 0; i < vertEdges.length; i++) {
						if (vertEdges[i] == edges[currentEdge].hedge)
							continue;
						Vec3 dir2 = vertices[edges[vertEdges[i]].vertex].r
								.minus(vertices[edges[edges[vertEdges[i]].hedge].vertex].r);
						dir2.normalize();
						double dot = Math.abs(normDir.dot(dir2));
						if (dot < minDot) {
							minDot = dot;
							bestEdge = vertEdges[i];
						}
					}
				}

			}
			vv = vertices[edges[bestEdge].vertex].r
					.minus(vertices[edges[edges[bestEdge].hedge].vertex].r);
			if (vv.length() < 1.0e-6)
				v.add(vv);
			if (v.size() > 1) {
				normDir = new Vec3();
				for (int i = 0; i < v.size() - 1; i++) {
					normDir.add(((Vec3) v.elementAt(i)).cross((Vec3) v
							.elementAt(i + 1)));
				}
				normDir.normalize();
			}
			currentEdge = bestEdge;
			currentVert = (edges[currentEdge].vertex == currentVert ? edges[edges[currentEdge].hedge].vertex
					: edges[currentEdge].vertex);
		}
	}

	/**
	 * Select an edge loop from each edge that is currently selected.
	 * 
	 * @param selection
	 *            the current selection (in edge mode)
	 * @return a selection containing all the edges in the loops, or null if no
	 *         loop could be found for one or more edges
	 */

	public boolean[] findEdgeLoops(boolean selection[]) {
		boolean newSel[] = new boolean[selection.length];
		if (mirrorState != NO_MIRROR) {
			getMirroredMesh();
			boolean[] mirrorSel = getMirroredSelection(mirroredMesh, selection);
			boolean[] newMirrorSel = mirroredMesh.findEdgeLoops(mirrorSel);
			newSel = getSelectionFromMirror(mirroredMesh, newMirrorSel);
		} else {
			for (int i = 0; i < selection.length; i++)

				if (selection[i]) {
					boolean loop[] = findSingleEdgeLoop(i);
					if (loop != null)
						for (int j = 0; j < loop.length / 2; j++) {
							newSel[j] |= loop[j];
							newSel[j] |= loop[edges[j].hedge];
						}
				}
		}
		return newSel;
	}

	/**
	 * Translates a selection onto a mirrored mesh
	 * 
	 * @param mirroredMesh
	 *            The mirrored mesh
	 * @param mirrorSel
	 *            Selection with regard to the mirrored mesh
	 * @return Original mesh selection
	 */
	private boolean[] getSelectionFromMirror(PolyMesh mirroredMesh,
			boolean[] mirrorSel) {
		MeshVertex[] mirrorVerts = mirroredMesh.getVertices();
		Wedge[] mirrorEdges = mirroredMesh.getEdges();
		boolean[] sel = new boolean[edges.length / 2];
		Vec3 v1, v2;
		for (int i = 0; i < edges.length / 2; ++i) {
			v1 = vertices[edges[i].vertex].r;
			v2 = vertices[edges[edges[i].hedge].vertex].r;
			if (v1.distance(mirrorVerts[mirrorEdges[i].vertex].r) < 1.0e-6
					&& v2
							.distance(mirrorVerts[mirrorEdges[mirrorEdges[i].hedge].vertex].r) < 1.0e-6) {
				sel[i] = mirrorSel[i];
			} else {
				for (int j = 0; j < mirrorEdges.length / 2; ++j) {
					if (v1.distance(mirrorVerts[mirrorEdges[j].vertex].r) < 1.0e-6
							&& v2
									.distance(mirrorVerts[mirrorEdges[mirrorEdges[j].hedge].vertex].r) < 1.0e-6) {
						sel[i] = mirrorSel[j];
						break;
					}
				}
			}
		}
		return sel;
	}

	/**
	 * Translates a selection onto a mirrored mesh
	 * 
	 * @param mirroredMesh
	 *            The mirrored mesh
	 * @param selection
	 *            Initial selection
	 * @return mirrored mesh selection
	 */
	private boolean[] getMirroredSelection(PolyMesh mirroredMesh,
			boolean[] selection) {
		MeshVertex[] mirrorVerts = mirroredMesh.getVertices();
		Wedge[] mirrorEdges = mirroredMesh.getEdges();
		boolean[] mirrorSel = new boolean[mirrorEdges.length / 2];
		Vec3 v1, v2;
		for (int i = 0; i < edges.length / 2; ++i) {
			v1 = mirrorVerts[mirrorEdges[i].vertex].r;
			v2 = mirrorVerts[mirrorEdges[mirrorEdges[i].hedge].vertex].r;
			if (v1.distance(vertices[edges[i].vertex].r) < 1.0e-6
					&& v2.distance(vertices[edges[edges[i].hedge].vertex].r) < 1.0e-6) {
				mirrorSel[i] = selection[i];
			} else {
				for (int j = 0; j < edges.length / 2; ++j) {
					if (v1.distance(vertices[edges[j].vertex].r) < 1.0e-6
							&& v2
									.distance(vertices[edges[edges[j].hedge].vertex].r) < 1.0e-6) {
						mirrorSel[i] = selection[j];
						break;
					}
				}
			}
		}
		return mirrorSel;
	}

	/**
	 * Select an edge strip from a single edge.
	 * 
	 * @param startEdge
	 *            the index of the edge from which to find an edge strip
	 * @return a selection containing all the edges in the strip, or null if no
	 *         strip could be found
	 */

	private boolean[] findSingleEdgeStrip(int startEdge, int every) {
		boolean[] sel = new boolean[edges.length];
		int next = edges[edges[edges[startEdge].next].next].hedge;
		sel[next] = true;
		int count = 1;
		while (next != startEdge && count < edges.length / 2) {
			next = edges[edges[edges[next].next].next].hedge;
			if (count % every == 0) {
				sel[next] = true;
			}
			++count;
		}
		if (count < edges.length / 2)
			return sel;
		else
			return null;
	}

	/**
	 * Select an edge strip from each edge that is currently selected.
	 * 
	 * @param selection
	 *            the current selection (in edge mode)
	 * @return a selection containing all the edges in the strips, or null if no
	 *         strip could be found for one or more edges
	 */

	public boolean[] findEdgeStrips(boolean selection[], int every) {
		boolean newSel[] = new boolean[selection.length];
		if (mirrorState != NO_MIRROR) {
			getMirroredMesh();
			boolean[] mirrorSel = getMirroredSelection(mirroredMesh, selection);
			boolean[] newMirrorSel = mirroredMesh.findEdgeStrips(mirrorSel, every);
			newSel = getSelectionFromMirror(mirroredMesh, newMirrorSel);
		} else {
			for (int i = 0; i < selection.length; i++)
				if (selection[i]) {
					boolean loop[] = findSingleEdgeStrip(i, every);
					if (loop != null)
						for (int j = 0; j < loop.length / 2; j++) {
							newSel[j] |= loop[j];
							newSel[j] |= loop[edges[j].hedge];
						}
				}
		}
		return newSel;
	}

	/**
	 * Computes a penalty function depending on the discrepancy between a
	 * perfect regular polygon and the specified polygon.
	 * 
	 * @param vf
	 *            Vertices of the polygon to compute the penalty factor for
	 * @return Penalty value
	 */
	private double calcPenalty(int[] vf) {
		int next;
		int next2;
		Vec3 v1;
		Vec3 v2;
		double r = 0.0;
		double t;
		double mean;
		double l1;
		double l2;

		mean = 2 * Math.PI / vf.length;
		for (int i = 0; i < vf.length; ++i) {
			next = i + 1;
			if (next == vf.length)
				next = 0;
			next2 = next + 1;
			if (next2 == vf.length)
				next2 = 0;
			v1 = vertices[vf[next2]].r.minus(vertices[vf[next]].r);
			v2 = vertices[vf[next]].r.minus(vertices[vf[i]].r);
			l1 = v1.length();
			if (l1 < 1e-10)
				continue;
			l2 = v2.length();
			if (l2 < 1e-10)
				continue;
			t = v1.dot(v2) / (l1 * l2);
			if (t > 1)
				t = 1;
			if (t < -1)
				t = -1;
			l1 = Math.acos(t);
			r += (l1 - mean) * (l1 - mean);
		}
		return r;
	}

	/**
	 * Changes a face reference to another one
	 * 
	 * @param ed
	 *            The edge array
	 * @param newFace
	 *            The new face reference
	 * @param eref
	 *            An edge bordering the face
	 */
	private void changeFace(Wedge[] ed, int eref, int newFace) {
		Wedge e = ed[eref];
		e.face = newFace;
		int start = e.vertex;
		int count = 1;
		while (ed[e.next].vertex != start) {
			++count;
			if (count > edges.length) {
				System.out.println("Error in changeFace: face is not closed");
				return;
			}
			e = ed[e.next];
			e.face = newFace;
		}
		return;
	}

	/**
	 * Returns true if the mesh is a closed volume
	 * 
	 * @return The closed value
	 */
	public boolean isClosed() {

		if (mirrorState != NO_MIRROR) {
			getMirroredMesh();
			return mirroredMesh.isClosed();
		}
		if (cachedMesh != null)
			return closed;
		else {
			for (int i = 0; i < edges.length; ++i)
				if (edges[i].face == -1)
					return false;
		}
		return true;
	}

	/**
	 * Constructor for PolyMesh object
	 * 
	 * @param in
	 *            The input stream
	 * @param theScene
	 *            The scene the polymesh belongs to
	 * @exception IOException
	 *                Exception that may occur when reading the file
	 * @exception InvalidObjectException
	 *                Signals that the PolyMesh structure is invalid
	 */
	public PolyMesh(DataInputStream in, Scene theScene) throws IOException,
			InvalidObjectException {
	    	super(in, theScene);
	    	initialize();
		readData(in, theScene);
		skeleton = new Skeleton(in);
	}
	
	public PolyMesh(DataInputStream in) throws IOException, InvalidObjectException {
	    super();
	    initialize();
	    readData(in, null);
	    skeleton = new Skeleton();
	}

	private void readData(DataInputStream in, Scene scene) throws IOException,
			InvalidObjectException {

		short version = in.readShort();
		if (version < 0 || version > 10)
			throw new InvalidObjectException("");
		if (version > 0)
			mirrorState = in.readShort();
		smoothingMethod = in.readInt();
		vertices = new Wvertex[in.readInt()];
		boolean hasNormal;
		for (int i = 0; i < vertices.length; i++) {
			vertices[i] = new Wvertex(new Vec3(in), in.readInt());
			if (version < 3)
				in.readFloat();
			vertices[i].ikJoint = in.readInt();
			vertices[i].ikWeight = in.readDouble();
			if (version > 4) {
				vertices[i].type = in.readShort();
				if (version < 9) {
					hasNormal = in.readBoolean();
					if (hasNormal)
						new Vec3(in);
//					if (hasNormal)
//					vertices[i].normal = new Vec3(in);
				}				
			}
		}
		edges = new Wedge[in.readInt()];
		for (int i = 0; i < edges.length; i++) {
			edges[i] = new Wedge(in.readInt(), in.readInt(), in.readInt(), in
					.readInt());
			edges[i].smoothness = in.readFloat();
		}
		faces = new Wface[in.readInt()];
		for (int i = 0; i < faces.length; i++) {
			faces[i] = new Wface(in.readInt());
			if (version < 4)
				in.readFloat();
			if (version < 3)
				in.readFloat();
			if (version < 4)
				in.readBoolean();
		}
		if (version > 1) {
			controlledSmoothing = in.readBoolean();
			minAngle = in.readDouble();
			maxAngle = in.readDouble();
			minSmoothness = in.readFloat();
			maxSmoothness = in.readFloat();
		}
		if (version > 2) {
			interactiveSmoothLevel = in.readInt();
			//former renderingSmoothLevel
			in.readInt();
		}
		if (version > 5) {
			if (in.readBoolean()) {
				seams = new boolean[edges.length / 2];
				for (int i = 0; i < seams.length; i++) {
					seams[i] = in.readBoolean();
				}
			}
		}
		if (version > 6) {
			if (in.readBoolean()) {
				mappingData = new UVMappingData(in, scene);
				mappingVerts = vertices.length;
				mappingEdges = edges.length;
				mappingFaces = faces.length;
			}
		}
		if (version > 7) {
			if (version > 9) {
				useCustomColors = in.readBoolean();
			}
			vertColor = new Color(in.readInt(), in.readInt(), in.readInt());
			selectedVertColor = new Color(in.readInt(), in.readInt(), in.readInt());
			edgeColor = new Color(in.readInt(), in.readInt(), in.readInt());
			selectedEdgeColor = new Color(in.readInt(), in.readInt(), in.readInt());
			seamColor = new Color(in.readInt(), in.readInt(), in.readInt());
			selectedSeamColor = new Color(in.readInt(), in.readInt(), in.readInt());
			meshColor = new Color(in.readInt(), in.readInt(), in.readInt());
			selectedFaceColor = new Color(in.readInt(), in.readInt(), in.readInt());
			meshRGBColor = ColorToRGB(meshColor);
			selectedFaceRGBColor = ColorToRGB(selectedFaceColor);
			handleSize = in.readInt();
		}
	}

	/**
	 * Smooths the whole mesh according to Catmull-Clark or Biermann-Levin-Zorin
	 * algorithm. Creasing according to Pixar/Blender algorithm. This method returns
	 * a quad mesh smoothed to the specified tolerance, unless the tolerance is set to -1
	 * in which case the polymesh is smoothed once.
	 * 
	 */
	public QuadMesh smoothWholeMesh(double tol, boolean calcProjectedEdges, int maxNs, boolean onePass) {
		if (mirrorState == NO_MIRROR && !onePass) {
			//first, check if this is a quad mesh
			boolean quad = true;
			for (int i = 0; i < faces.length; i++) {
				if (getFaceVertCount(faces[i]) != 4) {
					quad = false;
					break;
				}
			}
			quad = false;
			if (quad) {
				//System.out.println("quad mesh direct smoothing");
				QuadMesh qmesh = getQuadMesh();
				//qmesh.dumpMesh();
				//long time = new Date().getTime();
				qmesh.smoothMesh(tol, calcProjectedEdges, maxNs);
				projectedEdges = qmesh.getProjectedEdges();
				//System.out.println("only quad smoothing : " + String.valueOf((new Date().getTime() - time)*1.0/1000.0));
				return qmesh;
			}
		}
		if (!onePass) {
			//long time = new Date().getTime();
			PolyMesh smoothedMesh = (PolyMesh)this.duplicate();
			smoothedMesh.smoothWholeMesh(tol, calcProjectedEdges, maxNs, true);
			int[] pe = null;
			if (calcProjectedEdges) {
				int ne = smoothedMesh.getEdges().length/2;
				pe = new int[ne];
				for (int i = 0; i < edges.length / 2; i++) {
					pe[i] = i;
					pe[i + edges.length / 2] = i;
				}
				for (int i = edges.length; i < ne; i++) {
					pe[i] = -1;
				}
			}
			closed = smoothedMesh.isClosed();
			QuadMesh qmesh = smoothedMesh.getQuadMesh();
			int nfaces = smoothedMesh.getFaces().length;
			QuadFace[] qfaces = qmesh.getFaces();
			for (int i = 0; i < nfaces; i++) {
				if (smoothedMesh.subdivideFaces[i]) {
					qfaces[i].mark = QuadFace.SUBDIVIDE;
				} else {
					qfaces[i].mark = QuadFace.FINAL;
				}
			}
			//System.out.println("initial poly to quad smoothing : " + String.valueOf((new Date().getTime() - time)*1.0/1000.0));
			//time = new Date().getTime();
			if (maxNs > 1) {
				qmesh.smoothMesh(tol, calcProjectedEdges, 1, pe, maxNs);
			} else  {
				qmesh.setProjectedEdges(pe);
			}
			projectedEdges = qmesh.getProjectedEdges();
			//System.out.println("quad smoothing : " + String.valueOf((new Date().getTime() - time)*1.0/1000.0));
			return qmesh;
		}
		int ns = 0;
		int originalVert = vertices.length;
		Vec3[] normals = getNormals();
		int[] newProjectedEdges = null;
		if (calcProjectedEdges) {
			if (projectedEdges == null || projectedEdges.length != edges.length / 2) {
				projectedEdges = new int[edges.length];
				for (int i = 0; i < edges.length/2; i++)
					projectedEdges[i] = i;
			}
		} else
			projectedEdges = null;
		// edges subdivision for selected faces
		// first, edges selection
		int faceNum = faces.length;
		Wedge e;
		Vec3 pt;
		Vec3[] facePos = new Vec3[faces.length];
		for (int i = 0; i < faces.length; ++i) {
			int[] vf = getFaceVertices(faces[i]);
			pt = new Vec3();
			for (int j = 0; j < vf.length; ++j) {
				pt.add(vertices[vf[j]].r);
			}
			pt.scale(1.0 / vf.length);
			facePos[i] = pt;
		}
		// edge subdivision
		divideAllEdgesByTwo();
		// Set edge smoothness to 0 for edges lying on mirror
		float[] edgeSmoothness = null;
		if (mirrorState != NO_MIRROR) {
			edgeSmoothness = new float[edges.length];
			for (int i = 0; i < edges.length / 2; i++) {
				edgeSmoothness[i] = edges[i].smoothness;
				edgeSmoothness[edges[i].hedge] = edges[edges[i].hedge].smoothness;
				if ((mirrorState & MIRROR_ON_XY) != 0) {
					if (Math.abs(vertices[edges[i].vertex].r.z) < 1e-6
							&& Math
									.abs(vertices[edges[edges[i].hedge].vertex].r.z) < 1e-6) {
						edgeSmoothness[i] = 0.0f;
						edgeSmoothness[edges[i].hedge] = 0.0f;
					}
				}
				if ((mirrorState & MIRROR_ON_YZ) != 0) {
					if (Math.abs(vertices[edges[i].vertex].r.x) < 1e-6
							&& Math
									.abs(vertices[edges[edges[i].hedge].vertex].r.x) < 1e-6) {
						edgeSmoothness[i] = 0.0f;
						edgeSmoothness[edges[i].hedge] = 0.0f;
					}
				}
				if ((mirrorState & MIRROR_ON_XZ) != 0) {
					if (Math.abs(vertices[edges[i].vertex].r.y) < 1e-6
							&& Math
									.abs(vertices[edges[edges[i].hedge].vertex].r.y) < 1e-6) {
						edgeSmoothness[i] = 0.0f;
						edgeSmoothness[edges[i].hedge] = 0.0f;
					}
				}
			}
		}
		int addedVert = vertices.length;
		// build a table for selected faces and new vertices
		Wvertex[] newVert = new Wvertex[vertices.length + faceNum];
		// compute new vertices at the center of selected faces
		for (int i = 0; i < vertices.length; ++i)
			newVert[i] = new Wvertex(vertices[i]);
		VertexParamInfo[] vertParamInfo = new VertexParamInfo[newVert.length
				- vertices.length];
		int count = 0;
		int index = 0;
		for (int i = 0; i < faces.length; ++i) {
			int[] vf = getFaceVertices(faces[i]);
			double[] coef = new double[vf.length];
			for (int j = 0; j < vf.length; ++j)
				coef[j] = 1.0 / ((double) vf.length);
			count += vf.length;
			newVert[addedVert + index] = new Wvertex(facePos[i], -1);
			vertParamInfo[index] = new VertexParamInfo(vf, coef);
			index++;
		}
		count /= 2;
		// BTW, now we know how many more edges and faces
		Wedge[] newEdges = new Wedge[edges.length + count * 2];
		if (calcProjectedEdges) {
			newProjectedEdges = new int[edges.length/2 + count];
			for (int i = 0; i < edges.length / 2; i++) {
				newProjectedEdges[i] = projectedEdges[i];
			}
			for (int i = edges.length / 2; i < newEdges.length / 2; i++) {
				newProjectedEdges[i] = -1;
			}
			projectedEdges = newProjectedEdges;
		}

		Wface[] newFaces = new Wface[count + faces.length - faceNum];
	    boolean[] movedVert = null;
	    subdivideFaces = null;
		if (tol > 0 ) {
			subdivideFaces = new boolean[newFaces.length];
			movedVert = new boolean[newVert.length];
		}	    
		// Face table will help keep track which is which at texture parameter
		// computation time
		int[] paramFaceTable = new int[newFaces.length];

		for (int i = 0; i < edges.length / 2; ++i) {
			newEdges[i] = new Wedge(edges[i]);
			newEdges[i].hedge = edges[i].hedge + newEdges.length / 2
					- edges.length / 2;
			if (newEdges[i].next >= edges.length / 2)
				newEdges[i].next += newEdges.length / 2 - edges.length / 2;
			newEdges[newEdges[i].hedge] = new Wedge(edges[edges[i].hedge]);
			if (newEdges[newEdges[i].hedge].next >= edges.length / 2)
				newEdges[newEdges[i].hedge].next += newEdges.length / 2
						- edges.length / 2;
		}
		for (int i = 0; i < vertices.length; ++i)
			if (newVert[i].edge >= edges.length / 2)
				newVert[i].edge += newEdges.length / 2 - edges.length / 2;

		int face1;
		int face2;
		Vec3 pt1;
		Vec3 pt2;
		double smoothness, dist;
		// location of old vertices
		int n;
		Vec3 pos, oldPos;
		int v1;
		int v2;
		int sharp;
		double weight;
		double maxHard;
		int[] hardEdge = new int[2];
		int[] sharpEdge = new int[2];
		double[] smoothEdgeValue = new double[10];
		sharpEdge[1] = sharpEdge[0] = -1;
		smoothEdgeValue[1] = smoothEdgeValue[0] = 1;
		int hardnum;
		Vec3 sharpPt;
		// BLZ algorithm
		for (int i = 0; i < originalVert; ++i) {
			// adjacent polygons
			int ve[] = getVertexEdges(vertices[i]);
			if (ve.length > smoothEdgeValue.length) {
				smoothEdgeValue = new double[ve.length];
			}
			hardnum = 0;
			n = ve.length;
			sharp = 0;
			weight = 0;
			oldPos = newVert[i].r;
			pos = new Vec3();
			count = 0;
			maxHard = 0.0;
			for (int j = 0; j < ve.length; ++j) {
				smoothEdgeValue[j] = 0.0;
				face1 = edges[ve[j]].face;
				face2 = edges[edges[ve[j]].hedge].face;
				if (face1 != -1) {
					pos.add(facePos[face1]);
					pos
					.subtract(vertices[edges[edges[ve[j]].next].vertex].r
							.times(1.0 / 4.0));
					pos
					.subtract(vertices[edges[edges[edges[getPreviousEdge(ve[j])].hedge].next].vertex].r
							.times(1.0 / 4.0));
					pos.subtract(vertices[i].r.times(1.0 / 4.0));
					++count;
				}
				pos.add(vertices[edges[edges[ve[j]].next].vertex].r
						.times(3.0 / 2.0));
				if (mirrorState == NO_MIRROR)
					smoothness = edges[ve[j]].smoothness;
				else
					smoothness = edgeSmoothness[ve[j]];
				if (face1 != -1 && face2 != -1)
					smoothness = (1.0 - smoothness) * QuadMesh.MAX_SMOOTHNESS;
				else
					smoothness = QuadMesh.MAX_SMOOTHNESS; // boundary edges are treated as
				// hard edges.
				if (ns + 1 <= smoothness) {
					if (sharp < 2)
					{	sharpEdge[sharp] = ve[j];
					}
					++sharp;
				} else if (ns < smoothness) {
					weight += smoothness - ns;
					smoothEdgeValue[j] = smoothness - ns;
					if (smoothEdgeValue[j] > maxHard) {
						maxHard = smoothEdgeValue[j];
					}
					if (hardnum < 2)
						hardEdge[hardnum] = ve[j];
					++hardnum;
				}

			}
			pos.scale(1.0 / ((double) count * count));
			pos.add(vertices[i].r.times(1.0 - 3.0 / (2.0 * count) - 1.0
					/ (4.0 * count)));
			if (vertices[i].type != Wvertex.CORNER ) {
				switch (sharp) {
				case 0:
					if (hardnum <= 1) {
						newVert[i].r = pos;
					}
					else if (hardnum == 2) {
						weight /= 2;
						sharpPt = new Vec3(newVert[i].r.times(0.75));
						sharpPt
						.add(vertices[edges[edges[hardEdge[0]].next].vertex].r
								.times(0.125));
						sharpPt
						.add(vertices[edges[edges[hardEdge[1]].next].vertex].r
								.times(0.125));
						newVert[i].r = pos.times(1 - weight).plus(
								sharpPt.times(weight));
					} else {
						weight /= hardnum;
						newVert[i].r = pos.times(1 - weight).plus(
								newVert[i].r.times(weight));
					}
					break;
				case 1:
					if (hardnum == 0) {
						newVert[i].r = pos;
					}
					else if (hardnum == 1) {
						newVert[i].r = pos.times(1 - maxHard).plus(
								newVert[i].r.times(maxHard));
					} else {
						weight /= hardnum;
						newVert[i].r = pos.times(1 - weight).plus(
								newVert[i].r.times(weight));
					}
					break;
				case 2:
					sharpPt = new Vec3(newVert[i].r.times(0.75));
					sharpPt.add(vertices[edges[edges[sharpEdge[0]].next].vertex].r
							.times(0.125));
					sharpPt.add(vertices[edges[edges[sharpEdge[1]].next].vertex].r
							.times(0.125));
					//System.out.println(newVert[i].r + " " + edges[edges[sharpEdge[0]].next].vertex);
					//System.out.println(sharpPt + " " + edges[edges[sharpEdge[1]].next].vertex);
					if (hardnum == 0) {
						newVert[i].r = sharpPt;
					}
					else {
						weight /= hardnum;
						newVert[i].r = sharpPt.times(1 - weight).plus(
								newVert[i].r.times(weight));
					}
					break;
				default:
					//new vertex is marked as corner
					//newVert[i].type = Wvertex.CORNER;
					newVert[i].r = new Vec3(vertices[i].r);
					break;
				}
				if (tol > 0 ) {
					//dist = Math.abs(newVert[i].r.minus(oldPos).dot(normals[i]));
					dist = newVert[i].r.distance(oldPos);
					if (dist > tol) {
						movedVert[i] = true;
					}
				}				
			}
		}

		// location of the new midpoints
		int v3, v4, v5, v6;
		Vec3 v1r, v2r, v3r, v4r, v5r, v6r;
		int e1, e2, e1h, e2h;
		double gamma;
		for (int i = originalVert; i < addedVert; ++i) {
			oldPos = newVert[i].r;
			e1 = vertices[i].edge;
			e1h = edges[e1].hedge;
			e2 = edges[e1h].next;
			e2h = edges[e2].hedge;
			v1 = edges[e1].vertex;
			v2 = edges[e2].vertex;
			v3 = edges[edges[e1].next].vertex;
			v4 = edges[edges[getPreviousEdge(e1h)].hedge].vertex;
			v5 = edges[edges[getPreviousEdge(e2h)].hedge].vertex;
			v6 = edges[edges[e2].next].vertex;
			v1r = vertices[v1].r;
			v2r = vertices[v2].r;
			v3r = vertices[v3].r.minus(v1r).times(2).plus(v1r);
			v4r = vertices[v4].r.minus(v1r).times(2).plus(v1r);
			v5r = vertices[v5].r.minus(v2r).times(2).plus(v2r);
			v6r = vertices[v6].r.minus(v2r).times(2).plus(v2r);
			if (mirrorState == NO_MIRROR)
				smoothness = edges[e1].smoothness;
			else
				smoothness = edgeSmoothness[e1];
			smoothness = (1.0 - smoothness) * QuadMesh.MAX_SMOOTHNESS;
			face1 = edges[e1].face;
			face2 = edges[e2].face;
			// System.out.println( i + " " + face1 + " " + face2 );
			pt = null;
			if (face1 == -1 || face2 == -1)
				continue;
			gamma = 3.0 / 8.0;
			if (vertices[v1].type == Wvertex.CREASE) {
				int k = getVertexEdges(vertices[v1]).length;
				gamma = 3.0 / 8.0 - Math.cos(Math.PI / (double) k) / 4.0;
				// System.out.println( gamma + " :1");
			}
			if (vertices[v2].type == Wvertex.CREASE) {
				int k = getVertexEdges(vertices[v2]).length;
				gamma = 3.0 / 8.0 + Math.cos(Math.PI / (double) k) / 4.0;
				// System.out.println( gamma + " :2");
			}
			if (ns + 1 <= smoothness) {
				// hard edge
				//do nothing
			} else if (ns < smoothness) {
				// in between position
				pt = new Vec3(v1r);
				pt.scale(3.0 / 4.0 - gamma);
				pt.add(v2r.times(gamma));
				pt2 = new Vec3(v3r);
				pt2.add(v4r);
				pt2.add(v5r);
				pt2.add(v6r);
				pt2.scale(1.0 / 16.0);
				pt.add(pt2);
				pt2 = newVert[i].r;
				newVert[i].r = pt2.times(smoothness - ns).plus(
						pt.times(1 - (smoothness - ns)));
			} else {
				pt = new Vec3(v1r);
				pt.scale(3.0 / 4.0 - gamma);
				pt.add(v2r.times(gamma));
				pt2 = new Vec3(v3r);
				pt2.add(v4r);
				pt2.add(v5r);
				pt2.add(v6r);
				pt2.scale(1.0 / 16.0);
				pt.add(pt2);
				newVert[i].r = pt;
			}
			if (tol > 0 && !onePass) {
				v1r = normals[v1].plus(normals[v2]);
				v1r.normalize();
				dist = Math.abs(newVert[i].r.minus(oldPos).dot(v1r));
				//dist = newVert[i].r.distance(oldPos);
				if (dist > tol) {
					movedVert[i] = true;
				}
			}			
		}

		// new edges
		count = edges.length / 2;
		int faceCount = faces.length - faceNum;
		//Wedge e1w;
		//Wedge e2w;
		int ref1;
		int ref2;
		int next;
		for (int i = 0; i < faceNum; ++i) {
			int[] vf = getFaceVertices(faces[i]);
			n = vf.length / 2;
			newFaces[faceCount] = new Wface(count);
			paramFaceTable[faceCount] = i;
			ref1 = faces[i].edge;
			while (edges[ref1].vertex < originalVert)
				ref1 = edges[ref1].next;
			ref2 = getPreviousEdge(ref1);
			next = ref1;
			if (next >= edges.length / 2)
				next += newEdges.length / 2 - edges.length / 2;
			newEdges[next].next = count;
			newEdges[count] = new Wedge(addedVert + i, count + newEdges.length
					/ 2, faceCount, count + n - 1 + newEdges.length / 2);
			newEdges[count].smoothness = 1.0f;
			next = ref2;
			if (next >= edges.length / 2)
				next += newEdges.length / 2 - edges.length / 2;
			newEdges[count + n - 1 + newEdges.length / 2] = new Wedge(
					edges[edges[ref2].hedge].vertex, count + n - 1, faceCount,
					next);
			newEdges[count + n - 1 + newEdges.length / 2].smoothness = 1.0f;
			changeFace(newEdges, count, faceCount);
			++faceCount;
			for (int j = 1; j < n; ++j) {
				newFaces[faceCount] = new Wface(count + j);
				paramFaceTable[faceCount] = i;
				ref2 = edges[ref1].next;
				ref1 = edges[ref2].next;
				next = ref1;
				if (next >= edges.length / 2)
					next += newEdges.length / 2 - edges.length / 2;
				newEdges[next].next = count + j;
				newEdges[count + j] = new Wedge(addedVert + i, count + j
						+ newEdges.length / 2, faceCount, count + j - 1
						+ newEdges.length / 2);
				newEdges[count + j].smoothness = 1.0f;
				next = ref2;
				if (next >= edges.length / 2)
					next += newEdges.length / 2 - edges.length / 2;
				newEdges[count + j - 1 + newEdges.length / 2] = new Wedge(
						edges[edges[ref2].hedge].vertex, count + j - 1,
						faceCount, next);
				newEdges[count + j - 1 + newEdges.length / 2].smoothness = 1.0f;
				changeFace(newEdges, count + j, faceCount);
				++faceCount;
			}
			newVert[addedVert + i].edge = count + n - 1 + newEdges.length / 2;
			count += n;
//			if (movedVert != null) {
//				for (int j = 0; j < vf.length; j++) {
//					if (movedVert[vf[j]]) {
//						subdivideFaces[i] = true;
//						break;
//					}
//				}
//			}			
		}
		if (subdivideFaces != null) {
			for (int i = 0; i < newFaces.length; i++) {
				subdivideFaces[i] = true;
			}
		}
		// Update the texture parameters.
		// per face per vertex texture
		ParameterValue oldParamVal[] = getParameterValues();
		ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
		int[][] oldFaceVert = null;
		int[][] newFaceVert = null;
		int[][] newFaceVertFaceRef = null;
		int[][] newFaceVertIndexRef = null;
		int[] newToOldFaceIndex = null;
		int orFace;
		if (oldParamVal != null) {
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					newToOldFaceIndex = new int[newFaces.length];
					oldFaceVert = new int[faces.length][];
					newFaceVert = new int[newFaces.length][];
					newFaceVertFaceRef = new int[newFaces.length][];
					newFaceVertIndexRef = new int[newFaces.length][];
					int[] fv;
					orFace = 0;
					for (int j = 0; j < oldFaceVert.length; j++) {
						fv = getFaceVertices(faces[j]);
						for (int k = 0; k < fv.length / 2; k++) {
							newToOldFaceIndex[orFace++] = j;
						}
						oldFaceVert[j] = new int[fv.length];
						for (int k = 0; k < fv.length; k++) {
							oldFaceVert[j][k] = fv[k];
						}
					}
					for (int j = 0; j < newFaces.length; ++j) {
						fv = getFaceVertices(j, newEdges, newFaces);
						newFaceVert[j] = new int[fv.length];
						newFaceVertFaceRef[j] = new int[fv.length];
						newFaceVertIndexRef[j] = new int[fv.length];
						orFace = newToOldFaceIndex[j];
						for (int k = 0; k < fv.length; k++) {
							newFaceVert[j][k] = fv[k];
							if (fv[k] < vertices.length) {
								boolean found = false;
								for (int l = 0; l < oldFaceVert[orFace].length; l++) {
									if (oldFaceVert[orFace][l] == fv[k]) {
										newFaceVertFaceRef[j][k] = orFace;
										newFaceVertIndexRef[j][k] = l;
										found = true;
									}
								}
								if (!found) {
									System.out.println("Pas trouvŽ !!");
								}
							} else {
								newFaceVertFaceRef[j][k] = -1;
								newFaceVertIndexRef[j][k] = -1;
							}
						}
					}
					break;
				}
			}
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[newFaces.length];
					for (int j = 0; j < newval.length; j++)
						newval[j] = oldval[paramFaceTable[j]];
					newParamVal[i] = new FaceParameterValue(newval);
				} else if (oldParamVal[i] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[newVert.length];
					for (int j = 0; j < vertices.length; ++j)
						newval[j] = oldval[j];
					for (int j = vertices.length; j < newVert.length; ++j) {
						int[] vf = vertParamInfo[j - vertices.length].vert;
						double[] coef = vertParamInfo[j - vertices.length].coef;
						for (int k = 0; k < vf.length; ++k)
							newval[j] += coef[k] * oldval[vf[k]];
					}
					newParamVal[i] = new VertexParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					double val;
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						newval[j] = new double[newFaceVert[j].length];
						for (int k = 0; k < newval[j].length; k++) {
							if (newFaceVertFaceRef[j][k] != -1) {
								// old vertex
								newval[j][k] = fvpv.getValue(
										newFaceVertFaceRef[j][k],
										newFaceVertIndexRef[j][k]);
							} else {
								// new vertex
								orFace = newToOldFaceIndex[j];
								val = 0;
								int[] vf = vertParamInfo[newFaceVert[j][k]
										- vertices.length].vert;
								double[] coef = vertParamInfo[newFaceVert[j][k]
										- vertices.length].coef;
								for (int l = 0; l < vf.length; ++l) {
									boolean found = false;
									for (int m = 0; m < oldFaceVert[orFace].length; m++) {
										if (oldFaceVert[orFace][m] == vf[l]) {
											val += coef[l]
													* fvpv.getValue(orFace, m);
											found = true;
											break;
										}
									}
									if (!found) {
										System.out.println("Pas trouvŽ !!");
									}
								}
								newval[j][k] = val;
							}
						}
					}
					newParamVal[i] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[i] = oldParamVal[i].duplicate();
			}
			setParameterValues(newParamVal);
		}
		vertices = newVert;
		faces = newFaces;
		edges = newEdges;
		resetMesh();
		return null;
	}
	
	private QuadMesh getQuadMesh() {
		QuadVertex[] qverts = new QuadVertex[vertices.length];
		QuadEdge[] qedges = new QuadEdge[edges.length/2];
		QuadFace[] qfaces = new QuadFace[faces.length];
		for (int i = 0; i < qverts.length; i++) {
			qverts[i] = new QuadVertex(vertices[i]);
			qverts[i].firstEdge = (vertices[i].edge < edges.length/2 ? vertices[i].edge : edges[vertices[i].edge].hedge);
		}
		for (int i = 0; i < qedges.length; i++) {
			qedges[i] = new QuadEdge(edges[i], edges[edges[i].hedge]);
		}
		for (int i = 0; i < qfaces.length; i++) {
			qfaces[i] = new QuadFace(faces[i], edges);
		}
		QuadMesh qmesh = new QuadMesh(qverts, qedges, qfaces);
		qmesh.copyTextureAndMaterial(this);
		//Compute the quadmesh texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
		if (oldParamVal != null) {
			newParamVal = new ParameterValue[oldParamVal.length];
			for (int i = 0; i < oldParamVal.length; i++) {
				newParamVal[i] = oldParamVal[i].duplicate();
			}
			qmesh.setParameterValues(newParamVal);
		}
		return qmesh;
	}

	/**
	 * When a mesh has been subdivided using smoothWholeMesh, the array returned
	 * describes which edges belong to the original mesh
	 * 
	 * @return a boolean array which has the same length as the subdivided mesh
	 *         edges array
	 */
	int[] getProjectedEdges() {
		return projectedEdges;
	}

	/**
	 * Smoothes the mesh according to Catmull-Clark algorithm.
	 * 
	 * @param subdivideOnly
	 *            If true, then no smoothing occurs (subdivision only)
	 * @param selected
	 *            faces selected for subdivision
	 */
	public void smooth(boolean[] selected, boolean subdivideOnly) {
		// long t = System.currentTimeMillis();
		// dumpMesh();
		int originalVert = vertices.length;
		// edges subdivision for selected faces

		// first, edges selection
		boolean[] selection = new boolean[edges.length / 2];
		short[] selectType = new short[faces.length];
		int index;
		int faceNum = 0;
		int count;
		Wedge e;
		// faces adjacent to smoothed faces have a selection type of 1
		// smoothed faces have a selection type of 2
		for (int i = 0; i < faces.length; ++i) {
			if (selected[i]) {
				selectType[i] = 2;
				if (!subdivideOnly) {
					e = edges[faces[i].edge];
					int start = faces[i].edge;
					count = 0;
					if (edges[e.hedge].face >= 0)
						if (selectType[edges[e.hedge].face] == 0)
							selectType[edges[e.hedge].face] = 1;
					while (e.next != start) {
						++count;
						if (count > edges.length) {
							System.out
									.println("Error in smooth : face is not closed");
							System.out.println(faces[i].edge);
							return;
						}
						e = edges[e.next];
						if (edges[e.hedge].face >= 0)
							if (selectType[edges[e.hedge].face] == 0)
								selectType[edges[e.hedge].face] = 1;
					}
				}
			}
		}
		// edges selected for subdivision
		for (int i = 0; i < faces.length; ++i) {
			if (selectType[i] > 0) {
				++faceNum;
				int[] fe = getFaceEdges(faces[i]);
				for (int j = 0; j < fe.length; ++j) {
					index = fe[j];
					if (index >= edges.length / 2)
						index = edges[index].hedge;
					selection[index] = true;
				}
			}
		}
		// time3 += System.currentTimeMillis() - t;
		// actual edge subdivision
		divideEdges(selection, 2);
		// divideAllEdgesByTwo();
		int addedVert = vertices.length;
		// build a table for selected faces and new vertices
		int[] faceTable = new int[faceNum];
		count = 0;
		for (int i = 0; i < faces.length; ++i)
			if (selectType[i] > 0)
				faceTable[count++] = i;
		// dumpMesh();
		Wvertex[] newVert = new Wvertex[vertices.length + faceNum];
		// compute new vertices at the center of selected faces
		Vec3 pt;
		for (int i = 0; i < vertices.length; ++i)
			newVert[i] = new Wvertex(vertices[i]);
		VertexParamInfo[] vertParamInfo = new VertexParamInfo[newVert.length
				- vertices.length];
		count = 0;
		index = 0;
		Vec3[] facePos = new Vec3[faces.length];
		for (int i = 0; i < faces.length; ++i) {
			int[] vf = getFaceVertices(faces[i]);
			double[] coef = new double[vf.length];
			// System.out.println( "pair:" + vf.length );
			pt = new Vec3();
			for (int j = 0; j < vf.length; ++j) {
				pt.add(vertices[vf[j]].r);
				coef[j] = 1.0 / ((double) vf.length);
			}
			pt.scale(1.0 / vf.length);
			facePos[i] = pt;
			if (selectType[i] > 0) {
				count += vf.length / 2;
				newVert[addedVert + index] = new Wvertex(pt, -1);
				// newVert[addedVert + index].smoothness =
				// faces[i].centerSmoothness;
				vertParamInfo[index] = new VertexParamInfo(vf, coef);
				index++;
			}
		}
		// time4 += System.currentTimeMillis() - t;
		// BTW, now we know how many more edges and faces
		Wedge[] newEdges = new Wedge[edges.length + count * 2];
		Wface[] newFaces = new Wface[count + faces.length - faceNum];
		// Face table will help keep track which is which at texture parameter
		// computation time
		int[] paramFaceTable = new int[newFaces.length];

		for (int i = 0; i < edges.length / 2; ++i) {
			newEdges[i] = new Wedge(edges[i]);
			newEdges[i].hedge = edges[i].hedge + newEdges.length / 2
					- edges.length / 2;
			if (newEdges[i].next >= edges.length / 2)
				newEdges[i].next += newEdges.length / 2 - edges.length / 2;
			newEdges[newEdges[i].hedge] = new Wedge(edges[edges[i].hedge]);
			if (newEdges[newEdges[i].hedge].next >= edges.length / 2)
				newEdges[newEdges[i].hedge].next += newEdges.length / 2
						- edges.length / 2;
		}
		for (int i = 0; i < vertices.length; ++i)
			if (newVert[i].edge >= edges.length / 2)
				newVert[i].edge += newEdges.length / 2 - edges.length / 2;
		// copy intact, unselected faces and give them new index reference
		count = 0;
		for (int i = 0; i < faces.length; ++i)
			if (selectType[i] == 0) {
				newFaces[count] = new Wface(faces[i]);
				if (newFaces[count].edge >= edges.length / 2)
					newFaces[count].edge += newEdges.length / 2 - edges.length
							/ 2;
				paramFaceTable[count] = i;
				changeFace(newEdges, newFaces[count].edge, count);
				++count;
			}
		int face1;
		int face2;
		Vec3 pt1;
		Vec3 pt2;
		// time5 += System.currentTimeMillis() - t;
		// location of the new midpoints
		if (!subdivideOnly)
			for (int i = originalVert; i < addedVert; ++i) {
				e = edges[vertices[i].edge];
				face1 = e.face;
				face2 = edges[e.hedge].face;
				// System.out.println( i + " " + face1 + " " + face2 );
				pt = null;
				if (face1 == -1 || face2 == -1)
					continue;
				if (selectType[face1] <= 1 && selectType[face2] <= 1)
					continue;
				pt = facePos[face1].plus(facePos[face2]);
				pt2 = new Vec3(newVert[e.vertex].r);
				pt2.add(newVert[edges[edges[e.hedge].next].vertex].r);
				pt.add(pt2);
				pt.scale(0.25);
				pt2.scale(0.5);
				newVert[i].r = pt;
				//newVert[i].r = pt.times( e.smoothness ).plus( pt2.times(1 - e.smoothness ) );
			}
		// location of old vertices
		int n;
		Vec3 pos;
		int v1;
		int v2;
		boolean move;
		if (!subdivideOnly)
			for (int i = 0; i < originalVert; ++i) {

				int ve[] = getVertexEdges(vertices[i]);
				n = ve.length;
				move = false;
				// System.out.println( "ve.length " + n );
				pos = new Vec3();
				count = 0;
				move = false;
				for (int j = 0; j < ve.length; ++j) {
					face1 = edges[ve[j]].face;
					face2 = edges[edges[ve[j]].hedge].face;
					if (face1 != -1) {
						move |= (selectType[face1] == 2);
						pos.add(facePos[face1]);
						++count;
					}
					if (face2 != -1) {
						move |= (selectType[face2] == 2);
						pos.add(facePos[face2]);
						++count;
					}
				}
				if (!move)
					continue;
				pos.scale(1.0 / (count * n));
				pt = new Vec3();
				for (int j = 0; j < ve.length; ++j)
					pt.add(vertices[edges[ve[j]].vertex].r);
				pt.scale(2.0 / ((double) n * n));
				pos.add(pt);
				pos.add(newVert[i].r.times((n * 1.0 - 3.0) / ((double) n)));
				newVert[i].r = pos; // pos.times( vertices[i].smoothness
				// ).plus( vertices[i].r.times( 1-
				// vertices[i].smoothness) );

			}
		// time6 += System.currentTimeMillis() - t;
		// new edges
		count = edges.length / 2;
		int faceCount = faces.length - faceNum;
		Wedge e1;
		Wedge e2;
		int ref1;
		int ref2;
		int next;
		for (int i = 0; i < faceNum; ++i) {
			int[] vf = getFaceVertices(faces[faceTable[i]]);
			n = vf.length / 2;
			newFaces[faceCount] = new Wface(count);
			// newFaces[faceCount].edgeSmoothness =
			// faces[faceTable[i]].edgeSmoothness;
			// newFaces[faceCount].centerSmoothness =
			// faces[faceTable[i]].centerSmoothness;
			// newFaces[faceCount].convex = faces[faceTable[i]].convex;
			paramFaceTable[faceCount] = faceTable[i];
			ref1 = faces[faceTable[i]].edge;
			while (edges[ref1].vertex < originalVert)
				ref1 = edges[ref1].next;
			ref2 = getPreviousEdge(ref1);
			next = ref1;
			if (next >= edges.length / 2)
				next += newEdges.length / 2 - edges.length / 2;
			newEdges[next].next = count;
			newEdges[count] = new Wedge(addedVert + i, count + newEdges.length
					/ 2, faceCount, count + n - 1 + newEdges.length / 2);
			newEdges[count].smoothness = 1.0f;
			next = ref2;
			if (next >= edges.length / 2)
				next += newEdges.length / 2 - edges.length / 2;
			newEdges[count + n - 1 + newEdges.length / 2] = new Wedge(
					edges[edges[ref2].hedge].vertex, count + n - 1, faceCount,
					next);
			newEdges[count + n - 1 + newEdges.length / 2].smoothness = 1.0f;
			changeFace(newEdges, count, faceCount);
			++faceCount;
			for (int j = 1; j < n; ++j) {
				newFaces[faceCount] = new Wface(count + j);
				// newFaces[faceCount].edgeSmoothness =
				// faces[faceTable[i]].edgeSmoothness;
				// newFaces[faceCount].centerSmoothness =
				// faces[faceTable[i]].centerSmoothness;
				// newFaces[faceCount].convex = faces[faceTable[i]].convex;
				paramFaceTable[faceCount] = faceTable[i];
				ref2 = edges[ref1].next;
				ref1 = edges[ref2].next;
				next = ref1;
				if (next >= edges.length / 2)
					next += newEdges.length / 2 - edges.length / 2;
				newEdges[next].next = count + j;
				newEdges[count + j] = new Wedge(addedVert + i, count + j
						+ newEdges.length / 2, faceCount, count + j - 1
						+ newEdges.length / 2);
				newEdges[count + j].smoothness = 1.0f;
				next = ref2;
				if (next >= edges.length / 2)
					next += newEdges.length / 2 - edges.length / 2;
				newEdges[count + j - 1 + newEdges.length / 2] = new Wedge(
						edges[edges[ref2].hedge].vertex, count + j - 1,
						faceCount, next);
				newEdges[count + j - 1 + newEdges.length / 2].smoothness = 1.0f;
				changeFace(newEdges, count + j, faceCount);
				++faceCount;
			}
			newVert[addedVert + i].edge = count + n - 1 + newEdges.length / 2;
			count += n;
		}
		// time7 += System.currentTimeMillis() - t;
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[newFaces.length];
					for (int j = 0; j < newval.length; j++)
						newval[j] = oldval[paramFaceTable[j]];
					newParamVal[i] = new FaceParameterValue(newval);
				} else if (oldParamVal[i] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[newVert.length];
					for (int j = 0; j < vertices.length; ++j)
						newval[j] = oldval[j];
					for (int j = vertices.length; j < newVert.length; ++j) {
						int[] vf = vertParamInfo[j - vertices.length].vert;
						double[] coef = vertParamInfo[j - vertices.length].coef;
						for (int k = 0; k < vf.length; ++k)
							newval[j] += coef[k] * oldval[vf[k]];
					}
					newParamVal[i] = new VertexParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[i] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[i] = oldParamVal[i].duplicate();
			}
			setParameterValues(newParamVal);
		}
		vertices = newVert;
		faces = newFaces;
		edges = newEdges;
		resetMesh();
		// time1 += System.currentTimeMillis() - t;
		// dumpMesh();
	}

	/**
	 * Moves selected vertices along a given direction
	 * 
	 * @param direction
	 *            Which way to go?
	 * @param amount
	 *            Description of the Parameter
	 * @param selected
	 *            Description of the Parameter
	 */
	public void moveVertices(boolean[] selected, double amount, short direction) {
		switch (direction) {
		case NORMAL:
			Vec3[] norm = getNormals();
			for (int i = 0; i < vertices.length; i++) {
				if (selected[i])
					vertices[i].r.add(norm[i].times(amount));
			}
			break;
		case X:
			for (int i = 0; i < vertices.length; i++)
				if (selected[i])
					vertices[i].r.add(new Vec3(amount, 0, 0));
			break;
		case Y:
			for (int i = 0; i < vertices.length; i++)
				if (selected[i])
					vertices[i].r.add(new Vec3(0, amount, 0));
			break;
		case Z:
			for (int i = 0; i < vertices.length; i++)
				if (selected[i])
					vertices[i].r.add(new Vec3(0, 0, amount));
			break;
		}
		resetMesh();
	}

	/**
	 * Moves selected edges along a given direction
	 * 
	 * @param direction
	 *            Which way to go?
	 * @param amount
	 *            Description of the Parameter
	 * @param selected
	 *            Description of the Parameter
	 */
	public void moveEdges(boolean[] selected, double amount, short direction) {
		boolean[] moved = new boolean[vertices.length];
		Vec3 disp = null;
		switch (direction) {
		case X:
			disp = new Vec3(amount, 0, 0);
			break;
		case Y:
			disp = new Vec3(0, amount, 0);
			break;
		case Z:
			disp = new Vec3(0, 0, amount);
			break;
		}
		switch (direction) {
		case NORMAL:
			boolean[] facesSelected = new boolean[faces.length];
			int sel;
			for (int i = 0; i < faces.length; ++i) {
				int[] fe = getFaceEdges(faces[i]);
				for (int j = 0; j < fe.length; ++j) {
					sel = fe[j];
					if (sel >= edges.length / 2)
						sel = edges[fe[j]].hedge;
					if (selected[sel])
						facesSelected[i] = true;
				}
			}
			Vec3[] v = getVertexPositionsForFacesNormalDisplacement(
					facesSelected, amount);
			for (int i = 0; i < edges.length / 2; ++i) {
				if (selected[i]) {
					vertices[edges[i].vertex].r = v[edges[i].vertex];
					vertices[edges[edges[i].hedge].vertex].r = v[edges[edges[i].hedge].vertex];
				}
			}
			break;
		case X:
		case Y:
		case Z:
			for (int i = 0; i < edges.length / 2; i++) {
				if (selected[i]) {
					if (!moved[edges[i].vertex]) {
						vertices[edges[i].vertex].r.add(disp);
						moved[edges[i].vertex] = true;
					}
					if (!moved[edges[edges[i].hedge].vertex]) {
						vertices[edges[edges[i].hedge].vertex].r.add(disp);
						moved[edges[edges[i].hedge].vertex] = true;
					}
				}
			}
			break;
		}
		resetMesh();
	}

	/**
	 * Moves selected faces along a given direction
	 * 
	 * @param direction
	 *            Which way to go?
	 * @param amount
	 *            Description of the Parameter
	 * @param selected
	 *            Description of the Parameter
	 */
	public void moveFaces(boolean[] selected, double amount, short direction) {
		Vec3 disp = null;
		switch (direction) {
		case X:
			disp = new Vec3(amount, 0, 0);
			break;
		case Y:
			disp = new Vec3(0, amount, 0);
			break;
		case Z:
			disp = new Vec3(0, 0, amount);
			break;
		}
		boolean[] moved = new boolean[vertices.length];
		switch (direction) {
		case NORMAL:
			Vec3[] v = getVertexPositionsForFacesNormalDisplacement(selected,
					amount);
			for (int i = 0; i < vertices.length; ++i)
				vertices[i].r = v[i];
			break;
		case X:
		case Y:
		case Z:
			for (int i = 0; i < faces.length; i++) {
				if (selected[i]) {
					int[] fv = getFaceVertices(faces[i]);
					for (int j = 0; j < fv.length; ++j) {
						if (!moved[fv[j]]) {
							vertices[fv[j]].r.add(disp);
							moved[fv[j]] = true;
						}
					}
				}
			}
			break;
		}
		resetMesh();
	}

	/**
	 * Extrudes faces marked as selected.
	 * 
	 * @param selected
	 *            Faces selected for extrusion
	 * @param direction
	 *            Description of the Parameter
	 * @param value
	 *            Description of the Parameter
	 */
	public void extrudeFaces(boolean[] selected, double value, Vec3 direction) {
		extrudeFaces(selected, value, direction, 1.0, null, false, false);
	}

	/**
	 * Extrudes faces marked as selected.
	 * 
	 * @param selected
	 *            Faces selected for extrusion
	 * @param direction
	 *            Extrusion direction
	 * @param value
	 *            Extrusion amplitude
	 * @param scale
	 *            Extruded faces scaling coefficient
	 */
	public void extrudeFaces(boolean[] selected, double value, Vec3 direction,
			double scale, Vec3 camZ, boolean useNormals, boolean constrainAxis) {
		// dumpMesh();
		int count = 0;
		if ((Math.abs(value) < 1e-12) && Math.abs(1.0 - scale) < 1e-6)
			return;
		if (direction != null)
			if (direction.length() < 1e-12)
				return;
		Vec3[] normals = getNormals();
		Vec3[] faceNormals = getFaceNormals();
		for (int i = 0; i < faces.length; ++i) {
			if (selected[i]) {
				int[] fv = getFaceVertices(faces[i]);
				count += fv.length;
			}
		}
		Wvertex[] newVertices = new Wvertex[vertices.length + count];
		Wedge[] newEdges = new Wedge[edges.length + 4 * count];
		Wface[] newFaces = new Wface[faces.length + count];
		int[] paramFaceTable = new int[newFaces.length - faces.length];
		int[] paramVertexTable = new int[newVertices.length - vertices.length];
		translateMesh(newVertices, newEdges, newFaces);
		int newFaceCount = faces.length;
		int newVertCount = vertices.length;
		int newEdgeCount = edges.length / 2;
		Vec3 disp = null;
		if (direction != null)
			disp = direction.times(value);
		int next;
		int prev;
		for (int i = 0; i < faces.length; ++i) {
			if (selected[i]) {
				int[] fv = getFaceVertices(faces[i]);
				int[] fe = getFaceEdges(faces[i]);
				for (int j = 0; j < fv.length; ++j) {
					// System.out.println( fv[j] + " " + edges[fe[j]].vertex
					// );
					if (fe[j] >= edges.length / 2)
						fe[j] += newEdges.length / 2 - edges.length / 2;
				}
				if (direction == null)
					disp = faceNormals[i].times(value);
				for (int j = 0; j < fv.length; ++j) {
					next = j + 1;
					if (next == fv.length)
						next = 0;
					prev = j - 1;
					if (prev < 0)
						prev += fv.length;
					newVertices[newVertCount + j] = new Wvertex(
							vertices[fv[j]].r, newEdgeCount + 2 * j + 1);
					newVertices[newVertCount + j].r.add(disp);
					// newVertices[newVertCount + j].smoothness =
					// newVertices[fv[j]].smoothness;
					newVertices[fv[j]].edge = newEdgeCount + 2 * j;
					paramVertexTable[newVertCount + j - vertices.length] = fv[j];
					newEdges[newEdgeCount + 2 * j] = new Wedge(
							newVertCount + j, newEdgeCount + 2 * j
									+ newEdges.length / 2, newFaceCount + j,
							newEdgeCount + 2 * j + 1);
					newEdges[newEdgeCount + 2 * j + newEdges.length / 2] = new Wedge(
							fv[j], newEdgeCount + 2 * j, newFaceCount + next,
							fe[next]);
					newEdges[newEdgeCount + 2 * j].smoothness = 1.0f;
					newEdges[newEdgeCount + 2 * j + newEdges.length / 2].smoothness = 1.0f;
					newEdges[newEdgeCount + 2 * j + 1] = new Wedge(newVertCount
							+ prev, newEdgeCount + 2 * j + 1 + newEdges.length
							/ 2, newFaceCount + j, newEdgeCount + 2 * prev
							+ newEdges.length / 2);
					newEdges[newEdgeCount + 2 * j + 1 + newEdges.length / 2] = new Wedge(
							newVertCount + j, newEdgeCount + 2 * j + 1, i,
							newEdgeCount + 2 * next + 1 + newEdges.length / 2);
					newEdges[newEdgeCount + 2 * j + 1].smoothness = 1.0f;
					newEdges[newEdgeCount + 2 * j + 1 + newEdges.length / 2].smoothness = 1.0f;
					newEdges[fe[j]].next = newEdgeCount + 2 * j;
					newEdges[fe[j]].face = newFaceCount + j;
					newFaces[newFaceCount + j] = new Wface(newEdgeCount + 2 * j);
					// newFaces[newFaceCount + j].edgeSmoothness = 1.0f;
					// newFaces[newFaceCount + j].centerSmoothness =
					// faces[i].centerSmoothness;
					// newFaces[newFaceCount + j].convex = faces[i].convex;
					paramFaceTable[newFaceCount + j - faces.length] = i;
				}
				newFaces[i].edge = newEdgeCount + 1 + newEdges.length / 2;
				newVertCount += fv.length;
				newEdgeCount += 2 * fv.length;
				newFaceCount += fv.length;
			}
		}
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[newFaces.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = oldval[paramFaceTable[j - oldval.length]];
					newParamVal[i] = new FaceParameterValue(newval);
				} else if (oldParamVal[i] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[newVertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = oldval[paramVertexTable[j - oldval.length]];
					newParamVal[i] = new VertexParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[i] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[i] = oldParamVal[i].duplicate();
			}
			setParameterValues(newParamVal);
		}
		int fl = faces.length;
		faces = newFaces;
		vertices = newVertices;
		edges = newEdges;

		Vec3 diff;
		for (int i = 0; i < fl; ++i) {
			if (selected[i]) {
				diff = new Vec3();
				int[] fv = getFaceVertices(faces[i]);
				for (int j = 0; j < fv.length; ++j)
					diff.add(vertices[fv[j]].r);
				diff.scale(1.0 / fv.length);
				for (int j = 0; j < fv.length; ++j)
					vertices[fv[j]].r = diff.plus(vertices[fv[j]].r.minus(diff)
							.times(scale));
			}
		}

		if (Math.abs(scale) < 0.05)
			removeZeroLengthEdges();
		// dumpMesh();
		resetMesh();

	}

	/**
	 * Gets the edges bordering contiguous selected faces If the specified face
	 * is not seleted or is embedded in the selection (i.e. not bordered by an
	 * unselected face) null is returned.)
	 * 
	 * @param selected
	 *            Selected faces
	 * @param start
	 *            The index of the contour starting edge
	 * @return Array of edges describing the contour
	 */
	int[] getFaceContour(boolean[] selected, int start) {
		Vector ed = new Vector();
		ed.addElement(new Integer(start));
		int next = start;
		int count = 0;
		do {
			next = edges[next].next;
			while (isFaceSelected(selected, edges[edges[next].hedge].face)) {
				next = edges[edges[next].hedge].next;
				++count;
				if (count > edges.length) {
					System.out
							.println("Error in getFaceContour: wrong mesh structure");
					return null;
				}
			}
			ed.addElement(new Integer(next));
		} while (next != start);
		int[] fc = new int[ed.size() - 1];
		for (int i = 0; i < fc.length; ++i)
			fc[i] = ((Integer) ed.elementAt(i)).intValue();
		return fc;
	}

	/**
	 * Extrudes regions consisting in neighboring selected faces.
	 * 
	 * @param selected
	 *            Faces selected for extrusion
	 * @param direction
	 *            Direction in which to extrude. null for normal
	 * @param value
	 *            Extrude amplitude
	 */
	public void extrudeRegion(boolean[] selected, double value, Vec3 direction) {
		extrudeRegion(selected, value, direction, 1.0, null, false, false);
	}

	/**
	 * Extrudes regions consisting in neighboring selected faces.
	 * 
	 * @param selected
	 *            Faces selected for extrusion
	 * @param direction
	 *            Direction in which to extrude. null for normal
	 * @param value
	 *            Extrude amplitude
	 * @param scale
	 *            Scale value for extrude/bevel combo
	 */
	public void extrudeRegion(boolean[] selected, double value, Vec3 direction,
			double scale, Vec3 camZ, boolean useNormals, boolean constrainAxis) {
		// dumpMesh();
		Vec3[] normals = getNormals();
		int count;
		if ((Math.abs(value) < 1e-12) && Math.abs(1.0 - scale) < 1e-6)
			return;
		if (direction != null)
			if (direction.length() < 1e-12)
				return;
		Vec3 position;
		Vec3 v[];
		Vec3 disp;
		Vec3 sclFaceVert[] = new Vec3[faces.length];
		Vector sclFace[] = new Vector[faces.length];
		boolean[] done = new boolean[vertices.length];
		if (direction == null) {
			v = getVertexPositionsForFacesNormalDisplacement(selected, value);
			for (int i = 0; i < faces.length; ++i) {
				int[] vf = getFaceVertices(faces[i]);
				if (selected[i])
					for (int j = 0; j < vf.length; ++j)
						done[vf[j]] = true;
			}
		} else {
			disp = direction.times(value);
			v = new Vec3[vertices.length];
			for (int i = 0; i < v.length; ++i)
				v[i] = vertices[i].r;
			for (int i = 0; i < faces.length; ++i) {
				int[] vf = getFaceVertices(faces[i]);
				if (selected[i])
					for (int j = 0; j < vf.length; ++j) {
						if (!done[vf[j]]) {
							v[vf[j]] = vertices[vf[j]].r.plus(disp);
							done[vf[j]] = true;
						}
					}
			}
		}
		for (int i = 0; i < faces.length; ++i) {
			if (selected[i]) {
				int[] fv = getFaceVertices(faces[i]);
				sclFaceVert[i] = new Vec3();
				for (int j = 0; j < fv.length; ++j)
					sclFaceVert[i].add(v[fv[j]]);
				sclFaceVert[i].scale(1.0 / fv.length);
				Vector vv = new Vector();
				if (sclFace[i] == null) {
					sclFace[i] = vv;
					vv.add(new Integer(i));
				} else
					vv = sclFace[i];
				int[] fe = getFaceEdges(faces[i]);
				for (int j = 0; j < fe.length; ++j) {
					int k = edges[edges[fe[j]].hedge].face;
					if (k == -1)
						continue;
					if (selected[k]) {
						Integer ki = new Integer(k);
						if (sclFace[k] == null) {
							vv.add(ki);
							sclFace[k] = vv;
						} else {
							for (int l = 0; l < sclFace[k].size(); ++l) {
								if (!vv.contains((Integer) sclFace[k]
										.elementAt(l)))
									vv.add(sclFace[k].elementAt(l));
							}
							if (!vv.contains(ki))
								vv.add(ki);
							for (int l = 0; l < faces.length; ++l) {
								if ((i != l) && (sclFace[i] == sclFace[l]))
									sclFace[l] = vv;
							}
							sclFace[k] = vv;
						}
					}
				}
			}
		}
		int vl = vertices.length;
		for (int i = 0; i < vl; ++i) {
			Vec3 mean = null;

			if (done[i]) {
				int[] ve = getVertexEdges(vertices[i]);
				int start = -1;
				for (int j = 0; j < ve.length; ++j) {
					if (isFaceSelected(selected, edges[ve[j]].face)
							&& !isFaceSelected(selected,
									edges[edges[ve[j]].hedge].face))
						start = ve[j];
					if (isFaceSelected(selected, edges[ve[j]].face)
							&& (mean == null)) {
						Vector vv = sclFace[edges[ve[j]].face];
						mean = new Vec3();
						for (int k = 0; k < vv.size(); ++k)
							mean.add(sclFaceVert[((Integer) vv.elementAt(k))
									.intValue()]);
						mean.scale(1.0 / vv.size());
					}
				}
				if (start == -1 && mean != null) {
					// embedded vertex
					if (useNormals)
						vertices[i].r = v[i].plus(normals[i].times(scale - 1));
					else
						vertices[i].r = mean
								.plus(v[i].minus(mean).times(scale));
					if (constrainAxis) {
						position = vertices[i].r.minus(v[i]);
						position = position.minus(camZ
								.times(camZ.dot(position)));
						vertices[i].r = v[i].plus(position);
					}

					done[i] = false;
					continue;
				}
				int[] fc = getFaceContour(selected, start);
				int[] nfc = new int[fc.length];
				int[] nfv = new int[fc.length];
				int[] fv = new int[fc.length];
				Wvertex[] newVertices = new Wvertex[vertices.length + fc.length];
				Wedge[] newEdges = new Wedge[edges.length + 4 * fc.length];
				Wface[] newFaces = new Wface[faces.length + fc.length];
				int[] paramFaceTable = new int[newFaces.length - faces.length];
				int[] paramVertexTable = new int[newVertices.length
						- vertices.length];
				translateMesh(newVertices, newEdges, newFaces);
				for (int j = 0; j < nfc.length; ++j) {
					nfc[j] = fc[j];
					if (nfc[j] >= edges.length / 2)
						nfc[j] += newEdges.length / 2 - edges.length / 2;
					nfv[j] = newEdges[nfc[j]].vertex;
					fv[j] = edges[fc[j]].vertex;
				}
				int newFaceCount = faces.length;
				int newVertCount = vertices.length;
				int newEdgeCount = edges.length / 2;
				int next;
				int prev;
				int n;
				int newNext;
				int currentFace = edges[fc[0]].face;
				boolean changed;
				for (int j = 0; j < fc.length; ++j) {
					next = j + 1;
					if (next == nfv.length)
						next = 0;
					prev = j - 1;
					if (prev < 0)
						prev += nfv.length;
					if (useNormals)
						position = v[nfv[j]].plus(normals[fv[j]]
								.times(scale - 1));
					else
						position = mean
								.plus(v[nfv[j]].minus(mean).times(scale));
					if (constrainAxis) {
						position = position.minus(v[nfv[j]]);
						position = position.minus(camZ
								.times(camZ.dot(position)));
						position = v[nfv[j]].plus(position);
					}
					newVertices[newVertCount + j] = new Wvertex(position,
							newEdgeCount + 2 * j + 1);
					newVertices[nfv[j]].edge = newEdgeCount + 2 * j;
					paramVertexTable[newVertCount + j - vertices.length] = nfv[j];
					v[nfv[j]] = newVertices[nfv[j]].r;
					done[nfv[j]] = false;
					newEdges[newEdgeCount + 2 * j] = new Wedge(
							newVertCount + j, newEdgeCount + 2 * j
									+ newEdges.length / 2, newFaceCount + j,
							newEdgeCount + 2 * j + 1);
					newEdges[newEdgeCount + 2 * j + newEdges.length / 2] = new Wedge(
							nfv[j], newEdgeCount + 2 * j, newFaceCount + next,
							nfc[next]);
					newEdges[newEdgeCount + 2 * j].smoothness = 1.0f;
					newEdges[newEdgeCount + 2 * j + newEdges.length / 2].smoothness = 1.0f;
					newEdges[newEdgeCount + 2 * j + 1] = new Wedge(newVertCount
							+ prev, newEdgeCount + 2 * j + 1 + newEdges.length
							/ 2, newFaceCount + j, newEdgeCount + 2 * prev
							+ newEdges.length / 2);
					newEdges[newEdgeCount + 2 * j + 1 + newEdges.length / 2] = new Wedge(
							newVertCount + j, newEdgeCount + 2 * j + 1,
							currentFace, newEdgeCount + 2 * next + 1
									+ newEdges.length / 2);
					newEdges[newEdgeCount + 2 * j + 1].smoothness = 1.0f;
					newEdges[newEdgeCount + 2 * j + 1 + newEdges.length / 2].smoothness = 1.0f;
					// System.out.println( "changed next " + nfc[j] + " to "
					// + ( newEdgeCount + 2 * j ) );
					newEdges[nfc[j]].next = newEdgeCount + 2 * j;
					newEdges[nfc[j]].face = newFaceCount + j;
					newFaces[newFaceCount + j] = new Wface(newEdgeCount + 2 * j);
					// newFaces[newFaceCount + j].edgeSmoothness =
					// faces[currentFace].edgeSmoothness;
					// newFaces[newFaceCount + j].centerSmoothness =
					// faces[currentFace].centerSmoothness;
					// newFaces[newFaceCount + j].convex =
					// faces[currentFace].convex;
					newFaces[currentFace].edge = newEdgeCount + 2 * j + 1
							+ newEdges.length / 2;
					paramFaceTable[newFaceCount + j - faces.length] = currentFace;
					currentFace = edges[fc[next]].face;
					newNext = newEdgeCount + 2 * next + 1 + newEdges.length / 2;
					next = edges[fc[j]].next;
					changed = false;
					count = 0;
					while (isFaceSelected(selected,
							edges[edges[next].hedge].face)) {
						if (next >= edges.length / 2)
							n = next + newEdges.length / 2 - edges.length / 2;
						else
							n = next;
						if (!changed) {
							newEdges[newEdgeCount + 2 * j + 1 + newEdges.length
									/ 2].next = n;
							changed = true;
						}

						newEdges[newEdges[n].hedge].vertex = newVertCount + j;
						prev = next;
						if (prev >= edges.length / 2)
							prev += newEdges.length / 2 - edges.length / 2;
						next = edges[edges[next].hedge].next;
						++count;
						if (count > edges.length) {
							System.out
									.println("Error in extrudeRegion: wrong mesh structure");
							return;
						}
					}
					if (changed) {
						newEdges[newEdges[prev].hedge].next = newNext;
						newEdges[newEdges[prev].hedge].vertex = newVertCount
								+ j;
						// System.out.println( "changed next " +
						// newEdges[prev].hedge + " to " + newNext );
					}
				}

				// Update the texture parameters.
				ParameterValue oldParamVal[] = getParameterValues();
				if (oldParamVal != null) {
					ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
					for (int k = 0; k < oldParamVal.length; k++) {
						if (oldParamVal[k] instanceof FaceParameterValue) {
							double oldval[] = ((FaceParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newFaces.length];
							for (int j = 0; j < oldval.length; j++)
								newval[j] = oldval[j];
							for (int j = oldval.length; j < newval.length; j++)
								newval[j] = oldval[paramFaceTable[j
										- oldval.length]];
							newParamVal[k] = new FaceParameterValue(newval);
						} else if (oldParamVal[k] instanceof VertexParameterValue) {
							double oldval[] = ((VertexParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newVertices.length];
							for (int j = 0; j < oldval.length; j++)
								newval[j] = oldval[j];
							for (int j = oldval.length; j < newval.length; j++)
								newval[j] = oldval[paramVertexTable[j
										- oldval.length]];
							newParamVal[k] = new VertexParameterValue(newval);
						} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
							FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
							double newval[][] = new double[newFaces.length][];
							for (int j = 0; j < newFaces.length; ++j) {
								fv = getFaceVertices(j, newEdges, newFaces);
								double val[] = new double[fv.length];
								for (int l = 0; l < fv.length; l++) {
									val[l] = fvpv.getAverageValue();
								}
								newval[j] = val;
							}
							newParamVal[k] = new FaceVertexParameterValue(
									newval);
						} else
							newParamVal[k] = oldParamVal[k].duplicate();
					}
					setParameterValues(newParamVal);
				}
				faces = newFaces;
				vertices = newVertices;
				edges = newEdges;

			}
		}
		if (Math.abs(scale) < 0.05)
			removeZeroLengthEdges();
		resetMesh();
	}

	/**
	 * Extrudes edges, either as a whole or individually.
	 * 
	 * @param selected
	 *            Edges selected for extrusion
	 * @param direction
	 *            Direction in which to extrude. null for normal
	 * @param value
	 *            Extrude amplitude
	 */
	public void extrudeEdges(boolean[] selected, double value, Vec3 direction) {

		// dumpMesh();
		Vec3[] normals = getEdgeNormals();
		if (Math.abs(value) < 1e-12)
			return;
		if (direction != null)
			if (direction.length() < 1e-12)
				return;
		int count = 0;
		for (int i = 0; i < edges.length / 2; i++) {
			if (edges[i].face != -1 && edges[edges[i].hedge].face != -1)
				selected[i] = false; // only boundary edges can be extruded
			if (selected[i])
				++count;
		}
		if (count == 0)
			return;
		Wvertex[] newVertices = new Wvertex[vertices.length + 2 * count];
		Wedge[] newEdges = new Wedge[edges.length + 6 * count];
		Wface[] newFaces = new Wface[faces.length + count];
		int[] paramFaceTable = new int[count];
		int[] paramVertexTable = new int[2 * count];
		translateMesh(newVertices, newEdges, newFaces);
		int next;
		int previous;
		int current, newCurrent;
		int vindex = vertices.length;
		int eindex = edges.length / 2;
		int findex = faces.length;
		Vec3 disp = null;
		if (direction != null)
			disp = direction.times(value);
		for (int i = 0; i < edges.length / 2; i++) {
			if (!selected[i])
				continue;
			if (edges[i].face == -1)
				current = i;
			else
				current = edges[i].hedge;
			if (current < edges.length / 2)
				newCurrent = current;
			else
				newCurrent = current + 3 * count;
			previous = getPreviousEdge(newEdges, newCurrent);
			next = edges[current].next;
			newVertices[vindex] = new Wvertex(vertices[edges[current].vertex]);
			newVertices[vindex + 1] = new Wvertex(
					vertices[edges[edges[current].hedge].vertex]);
			paramVertexTable[vindex - vertices.length] = edges[current].vertex;
			paramVertexTable[vindex - vertices.length] = edges[edges[current].hedge].vertex;
			newVertices[vindex].edge = eindex + 2;
			newVertices[vindex + 1].edge = eindex + 1;
			if (direction == null) {
				newVertices[vindex].r.add(normals[i].times(value));
				newVertices[vindex + 1].r.add(normals[i].times(value));
			} else {
				newVertices[vindex].r.add(disp);
				newVertices[vindex + 1].r.add(disp);
			}
			newEdges[eindex] = new Wedge(vindex + 1, newEdges.length / 2
					+ eindex, -1, eindex + 1);
			newEdges[newEdges.length / 2 + eindex] = new Wedge(
					newEdges[previous].vertex, eindex, findex, newCurrent);
			newEdges[eindex].smoothness = newEdges[newEdges.length / 2 + eindex].smoothness = newEdges[newCurrent].smoothness;
			newEdges[previous].next = eindex;
			newEdges[eindex + 1] = new Wedge(vindex, newEdges.length / 2
					+ eindex + 1, -1, eindex + 2);
			newEdges[newEdges.length / 2 + eindex + 1] = new Wedge(vindex + 1,
					eindex + 1, findex, newEdges.length / 2 + eindex);
			newEdges[eindex + 1].smoothness = newEdges[newEdges.length / 2
					+ eindex + 1].smoothness = newEdges[newCurrent].smoothness;
			newEdges[eindex + 2] = new Wedge(newEdges[newCurrent].vertex,
					newEdges.length / 2 + eindex + 2, -1,
					newEdges[newCurrent].next);
			newEdges[newEdges.length / 2 + eindex + 2] = new Wedge(vindex,
					eindex + 2, findex, newEdges.length / 2 + eindex + 1);
			newEdges[eindex + 2].smoothness = newEdges[newEdges.length / 2
					+ eindex + 2].smoothness = newEdges[newCurrent].smoothness;
			newEdges[newCurrent].next = newEdges.length / 2 + eindex + 2;
			newFaces[findex] = new Wface(newCurrent);
			paramFaceTable[findex - faces.length] = newEdges[newEdges[newCurrent].hedge].face;
			newEdges[newCurrent].face = findex;
			swapEdge(newCurrent, eindex + 1, newVertices, newEdges, newFaces);
			++findex;
			eindex += 3;
			vindex += 2;
		}
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newFaces.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = oldval[paramFaceTable[j - oldval.length]];
					newParamVal[k] = new FaceParameterValue(newval);
				} else if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = oldval[paramVertexTable[j - oldval.length]];
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		faces = newFaces;
		vertices = newVertices;
		edges = newEdges;
		// dumpMesh();
		resetMesh();
	}

	/**
	 * Extrudes edge regions consisting in neighboring selected edges.
	 * 
	 * @param selected
	 *            Edges selected for extrusion
	 * @param direction
	 *            Direction in which to extrude. null for normal
	 * @param value
	 *            Extrude amplitude
	 */
	public void extrudeEdgeRegion(boolean[] selected, double value,
			Vec3 direction) {
		Vec3[] normals = getEdgeNormals();
		boolean[] tmpSel = new boolean[selected.length];
		if (Math.abs(value) < 1e-12)
			return;
		if (direction != null)
			if (direction.length() < 1e-12)
				return;
		int count = 0;
		for (int i = 0; i < edges.length / 2; i++) {
			if (edges[i].face != -1 && edges[edges[i].hedge].face != -1)
				selected[i] = false; // only boundary edges can be extruded
			if (selected[i]) {
				tmpSel[i] = selected[i];
				++count;
			}
		}
		if (count == 0)
			return;
		Wvertex[] newVertices = new Wvertex[vertices.length + 2 * count];
		Wedge[] newEdges = new Wedge[edges.length + 6 * count];
		Wface[] newFaces = new Wface[faces.length + count];
		int[] paramFaceTable = new int[count];
		int[] paramVertexTable = new int[2 * count];
		translateMesh(newVertices, newEdges, newFaces);
		int previous, newPrevious;
		int current, newCurrent, oldCurrent, prevVertex;
		int vindex = vertices.length;
		int eindex = edges.length / 2;
		int findex = faces.length;
		Vec3 disp = null;
		if (direction != null)
			disp = direction.times(value);
		int e, firste, laste, vstart, estart, newFirste;
		boolean closed = false;
		for (int i = 0; i < edges.length / 2; i++) {
			if (!tmpSel[i])
				continue;
			if (edges[i].face == -1)
				e = i;
			else
				e = edges[i].hedge;
			// first, let's get the edge region
			firste = laste = e;
			int p = getPreviousEdge(e);
			while (isEdgeSelected(p, tmpSel) && p != e) {
				firste = p;
				p = getPreviousEdge(p);
			}
			if (p == e) {
				closed = true;
				laste = e;
			}
			current = firste;
			if (current < edges.length / 2)
				newCurrent = current;
			else
				newCurrent = current + 3 * count;
			newFirste = newCurrent;

			// then three edges are created as per single edge extrusion
			previous = getPreviousEdge(current);
			newPrevious = getPreviousEdge(newEdges, newCurrent);
			newVertices[vindex] = new Wvertex(vertices[edges[current].vertex]);
			newVertices[vindex + 1] = new Wvertex(
					vertices[edges[edges[current].hedge].vertex]);
			paramVertexTable[vindex - vertices.length] = edges[current].vertex;
			paramVertexTable[vindex + 1 - vertices.length] = edges[edges[current].hedge].vertex;
			newVertices[vindex].edge = eindex + 2;
			newVertices[vindex + 1].edge = eindex + 1;
			if (direction == null) {
				if (isEdgeSelected(edges[current].next, selected)) {
					newVertices[vindex].r.add(normals[current]
							.times(value * 0.5));
					newVertices[vindex].r.add(normals[edges[current].next]
							.times(value * 0.5));
				} else {
					newVertices[vindex].r.add(normals[current].times(value));
				}
				if (isEdgeSelected(previous, selected)) {
					newVertices[vindex + 1].r.add(normals[current]
							.times(value * 0.5));
					newVertices[vindex + 1].r.add(normals[previous]
							.times(value * 0.5));
				} else {
					newVertices[vindex + 1].r
							.add(normals[current].times(value));
				}
			} else {
				newVertices[vindex].r.add(disp);
				newVertices[vindex + 1].r.add(disp);
			}
			newEdges[eindex] = new Wedge(vindex + 1, newEdges.length / 2
					+ eindex, -1, eindex + 1);
			newEdges[newEdges.length / 2 + eindex] = new Wedge(
					newEdges[newPrevious].vertex, eindex, findex, newCurrent);
			newEdges[eindex].smoothness = newEdges[newEdges.length / 2 + eindex].smoothness = newEdges[newCurrent].smoothness;
			newEdges[newPrevious].next = eindex;
			newEdges[eindex + 1] = new Wedge(vindex, newEdges.length / 2
					+ eindex + 1, -1, eindex + 2);
			newEdges[newEdges.length / 2 + eindex + 1] = new Wedge(vindex + 1,
					eindex + 1, findex, newEdges.length / 2 + eindex);
			newEdges[eindex + 1].smoothness = newEdges[newEdges.length / 2
					+ eindex + 1].smoothness = newEdges[newCurrent].smoothness;
			newEdges[eindex + 2] = new Wedge(newEdges[newCurrent].vertex,
					newEdges.length / 2 + eindex + 2, -1,
					newEdges[newCurrent].next);
			newEdges[newEdges.length / 2 + eindex + 2] = new Wedge(vindex,
					eindex + 2, findex, newEdges.length / 2 + eindex + 1);
			newEdges[eindex + 2].smoothness = newEdges[newEdges.length / 2
					+ eindex + 2].smoothness = newEdges[newCurrent].smoothness;
			newEdges[newCurrent].next = newEdges.length / 2 + eindex + 2;
			newFaces[findex] = new Wface(newCurrent);
			paramFaceTable[findex - faces.length] = newEdges[newEdges[newCurrent].hedge].face;
			newEdges[newCurrent].face = findex;
			// swap edges so selected edge is now the extruded edge
			swapEdge(newCurrent, eindex + 1, newVertices, newEdges, newFaces);
			oldCurrent = newCurrent;
			++findex;
			estart = eindex;
			eindex += 3;
			prevVertex = vindex;
			vstart = vindex;
			vindex += 2;
			e = firste;
			if (e < edges.length / 2)
				tmpSel[e] = false;
			else
				tmpSel[edges[e].hedge] = false;
			// then 2 edges are extruded for each edge along the selection
			while (isEdgeSelected(edges[e].next, tmpSel)) {
				e = edges[e].next;
				current = e;
				if (current < edges.length / 2)
					newCurrent = current;
				else
					newCurrent = current + 3 * count;
				if (!closed || e != laste) {
					newVertices[vindex] = new Wvertex(
							vertices[edges[current].vertex]);
					paramVertexTable[vindex - vertices.length] = edges[current].vertex;
					newVertices[vindex].edge = eindex + 1;
					if (direction == null) {
						if (isEdgeSelected(edges[e].next, selected)) {
							newVertices[vindex].r.add(normals[current]
									.times(value * 0.5));
							newVertices[vindex].r.add(normals[edges[e].next]
									.times(value * 0.5));
						} else
							newVertices[vindex].r.add(normals[current]
									.times(value));
					} else
						newVertices[vindex].r.add(disp);
					newEdges[eindex] = new Wedge(vindex, newEdges.length / 2
							+ eindex, -1, eindex + 1);
					newEdges[newEdges.length / 2 + eindex] = new Wedge(
							prevVertex, eindex, findex, eindex - 1);
					newEdges[eindex].smoothness = newEdges[newEdges.length / 2
							+ eindex].smoothness = newEdges[newCurrent].smoothness;
					newEdges[oldCurrent].next = eindex;
					newEdges[eindex + 1] = new Wedge(
							newEdges[newCurrent].vertex, newEdges.length / 2
									+ eindex + 1, -1, newEdges[newCurrent].next);
					newEdges[newEdges.length / 2 + eindex + 1] = new Wedge(
							vindex, eindex + 1, findex, newEdges.length / 2
									+ eindex);
					newEdges[eindex + 1].smoothness = newEdges[newEdges.length
							/ 2 + eindex + 1].smoothness = newEdges[newCurrent].smoothness;
					newEdges[newCurrent].next = newEdges.length / 2 + eindex
							+ 1;
					newFaces[findex] = new Wface(newCurrent);
					paramFaceTable[findex - faces.length] = newEdges[newEdges[newCurrent].hedge].face;
					newEdges[newCurrent].face = findex;
					newEdges[eindex - 1].face = findex;
					swapEdge(newCurrent, eindex, newVertices, newEdges,
							newFaces);
					oldCurrent = newCurrent;
					++findex;
					eindex += 2;
					prevVertex = vindex;
					++vindex;
				} else // special case, we have to close the extrusion
				{
					newEdges[eindex] = new Wedge(vstart + 1, newEdges.length
							/ 2 + eindex, -1, newFirste);
					newEdges[newEdges.length / 2 + eindex] = new Wedge(
							prevVertex, eindex, findex, eindex - 1);
					newEdges[eindex - 1].face = findex;
					newEdges[newCurrent].face = findex;
					newEdges[estart].next = newEdges.length / 2 + eindex;
					newEdges[estart].face = findex;
					newEdges[oldCurrent].next = eindex;
					newFaces[findex] = new Wface(newCurrent);
					paramFaceTable[findex - faces.length] = newEdges[newEdges[newCurrent].hedge].face;
					// dumpNewMesh( newVertices, newEdges, newFaces);
					swapEdge(newCurrent, eindex, newVertices, newEdges,
							newFaces);
					++findex;
					++eindex;
				}
				if (e < edges.length / 2)
					tmpSel[e] = false;
				else
					tmpSel[edges[e].hedge] = false;
				// dumpMesh();
			}
		}
		// clip mesh
		if (vindex != newVertices.length) {
			Wvertex[] nverts = new Wvertex[vindex];
			for (int i = 0; i < vindex; ++i)
				nverts[i] = newVertices[i];
			newVertices = nverts;
		}
		if (eindex != newEdges.length / 2) {
			Wedge[] nedges = new Wedge[2 * eindex];
			vertices = newVertices;
			edges = newEdges;
			faces = newFaces;
			translateMesh(newVertices, nedges, newFaces);
			edges = nedges;
		} else {
			vertices = newVertices;
			edges = newEdges;
			faces = newFaces;
		}
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newFaces.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = oldval[paramFaceTable[j - oldval.length]];
					newParamVal[k] = new FaceParameterValue(newval);
				} else if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = oldval[paramVertexTable[j - oldval.length]];
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		// dumpMesh();
		resetMesh();

	}

	/**
	 * Swaps two edges within the edge list
	 * 
	 * @param e1
	 *            First edge to swap
	 * @param e2
	 *            Second edge to swap
	 * @param v
	 *            Vertices array
	 * @param e
	 *            Edges array
	 * @param f
	 *            Faces array
	 */
	private void swapEdge(int e1, int e2, Wvertex[] v, Wedge e[], Wface f[]) {
		int he1 = e[e1].hedge;
		int he2 = e[e2].hedge;
		int p1 = getPreviousEdge(e, e1);
		int p2 = getPreviousEdge(e, e2);
		int ph1 = getPreviousEdge(e, he1);
		int ph2 = getPreviousEdge(e, he2);
		Wedge tmpEdge = e[e1];
		e[e1] = e[e2];
		e[e2] = tmpEdge;
		e[p1].next = e2;
		e[p2].next = e1;
		tmpEdge = e[he1];
		e[he1] = e[he2];
		e[he2] = tmpEdge;
		e[ph1].next = he2;
		e[ph2].next = he1;
		e[e1].hedge = he1;
		e[he1].hedge = e1;
		e[e2].hedge = he2;
		e[he2].hedge = e2;
		if (e[e1].face != -1)
			f[e[e1].face].edge = e1;
		if (e[he1].face != -1)
			f[e[he1].face].edge = he1;
		if (e[e2].face != -1)
			f[e[e2].face].edge = e2;
		if (e[he2].face != -1)
			f[e[he2].face].edge = he2;
		v[e[he1].vertex].edge = e1;
		v[e[e1].vertex].edge = he1;
		v[e[he2].vertex].edge = e2;
		v[e[e2].vertex].edge = he2;
	}

	/**
	 * Mesh thickening.
	 * 
	 * @param value
	 *            Thickening amplitude
	 */
	public void thickenMesh(double value, boolean faceDisplacement) {
		// dumpMesh();
		if (Math.abs(value) < 1e-12)
			return;
		Vec3 v[] = new Vec3[vertices.length];
		Vec3[] normals = getNormals();
		if (faceDisplacement)
			v = getVertexPositionsForFacesNormalDisplacement(null, value);
		else {
			for (int i = 0; i < vertices.length; ++i)
				v[i] = vertices[i].r.plus(normals[i].times(value));
		}
		// dumpMesh();
		// first step: delete faces that have all of their vertices on the
		// plane.
		int n;
		Wvertex[] newVertices = new Wvertex[vertices.length * 2];
		Wedge[] newEdges = new Wedge[edges.length * 2];
		Wface[] newFaces = new Wface[faces.length * 2];
		translateMesh(newVertices, newEdges, newFaces);
		Wedge[] te = edges;
		Wvertex[] tv = vertices;
		Wface[] tf = faces;
		edges = newEdges;
		vertices = newVertices;
		faces = newFaces;
		// dumpMesh();
		edges = te;
		vertices = tv;
		faces = tf;
		// System.out.println( "a2" );
		for (int i = 0; i < vertices.length; ++i) {
			// newVertices[i] = new Wvertex( vertices[i] );
			newVertices[i + vertices.length] = new Wvertex(newVertices[i]);
			if (value > 0)
				newVertices[i].r = v[i];
			else
				newVertices[i + vertices.length].r = v[i];
		}
		for (int i = 0; i < edges.length / 2; ++i) {
			newEdges[i + edges.length / 2] = new Wedge(newEdges[i]);
			newEdges[i + edges.length / 2].hedge += edges.length / 2;
			n = newEdges[i].hedge;
			while (newEdges[n].next != i)
				n = newEdges[newEdges[n].next].hedge;
			newEdges[i + edges.length / 2].next = n + edges.length / 2;
			newEdges[i + edges.length / 2].vertex = newEdges[newEdges[i].hedge].vertex
					+ vertices.length;
			newVertices[newEdges[i + edges.length / 2].vertex].edge = newEdges[i].hedge
					+ edges.length / 2;
			if (newEdges[i + edges.length / 2].face != -1)
				newEdges[i + edges.length / 2].face += faces.length;
		}
		for (int i = edges.length; i < edges.length + edges.length / 2; ++i) {
			newEdges[i + edges.length / 2] = new Wedge(newEdges[i]);
			newEdges[i + edges.length / 2].hedge += edges.length / 2;
			n = newEdges[i].hedge;
			while (newEdges[n].next != i)
				n = newEdges[newEdges[n].next].hedge;
			newEdges[i + edges.length / 2].next = n + edges.length / 2;
			newEdges[i + edges.length / 2].vertex = newEdges[newEdges[i].hedge].vertex
					+ vertices.length;
			newVertices[newEdges[i + edges.length / 2].vertex].edge = newEdges[i].hedge
					+ edges.length / 2;
			if (newEdges[i + edges.length / 2].face != -1)
				newEdges[i + edges.length / 2].face += faces.length;
		}
		for (int i = 0; i < faces.length; ++i) {
			newFaces[i] = new Wface(newFaces[i]);
			newFaces[i + faces.length] = new Wface(newFaces[i]);
			newFaces[i + faces.length].edge += edges.length / 2;
		}

		Vector boundary = new Vector();
		Vector boundaries = new Vector();
		boolean[] done = new boolean[edges.length];
		for (int i = 0; i < edges.length; ++i) {
			if ((edges[i].face == -1) && (!done[i])) {
				done[i] = true;
				boundary.clear();
				if (i >= edges.length / 2)
					boundary.add(new Integer(i + edges.length / 2));
				else
					boundary.add(new Integer(i));
				int current = edges[i].next;
				while (current != i) {
					if (current >= edges.length / 2)
						boundary.add(new Integer(current + edges.length / 2));
					else
						boundary.add(new Integer(current));
					done[current] = true;
					current = edges[current].next;
				}
				boundaries.addAll(boundary);
			}
		}
		int el = edges.length;
		vertices = newVertices;
		edges = newEdges;
		faces = newFaces;
		// dumpMesh();
		newEdges = new Wedge[edges.length + boundaries.size() * 2];
		newFaces = new Wface[faces.length + boundaries.size()];
		translateMesh(vertices, newEdges, newFaces);
		// System.out.println( "c" );
		int e;
		int en;
		int ve;
		int ee;
		int eep;
		int vve;
		int index = edges.length / 2;
		// System.out.println( boundaries.size() );
		for (int j = 0; j < boundaries.size(); ++j) {
			e = ((Integer) boundaries.elementAt(j)).intValue();
			ee = e + el / 2;
			if (e >= edges.length / 2)
				e += newEdges.length / 2 - edges.length / 2;
			if (ee >= edges.length / 2)
				ee += newEdges.length / 2 - edges.length / 2;
			ve = newEdges[e].vertex;
			en = newEdges[e].next;
			vve = ve + vertices.length / 2;
			eep = newEdges[ee].hedge;
			while (newEdges[eep].next != ee)
				eep = newEdges[newEdges[eep].next].hedge;
			// System.out.println( "e, en, ee, eep, ve, vve" );
			// System.out.println( e + " " + en + " " + ee + " " + eep + " "
			// + ve + " " + vve );
			newEdges[index] = new Wedge(vve, index + newEdges.length / 2, -2,
					ee);
			newEdges[index + newEdges.length / 2] = new Wedge(ve, index, -2, en);
			newEdges[eep].next = index + newEdges.length / 2;
			newEdges[e].next = index;
			++index;
		}
		index = faces.length;
		for (int j = 0; j < boundaries.size(); ++j) {
			e = ((Integer) boundaries.elementAt(j)).intValue();
			if (e >= edges.length / 2)
				e += newEdges.length / 2 - edges.length / 2;
			changeFace(newEdges, e, index);
			newFaces[index] = new Wface(e);
			++index;
		}
		edges = newEdges;
		faces = newFaces;
		// Update the texture parameters.
		TextureParameter param[] = getParameters();
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[faces.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < 2 * oldval.length; j++)
						newval[j] = oldval[j - oldval.length];
					for (int j = 2 * oldval.length; j < newval.length; j++)
						newval[j] = param[k].defaultVal;
					newParamVal[k] = new FaceParameterValue(newval);
				} else if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[vertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < 2 * oldval.length; j++)
						newval[j] = oldval[j - oldval.length];
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		// dumpMesh();
		// String s = checkMesh();
		// System.out.println( s );
		resetMesh();
		// System.out.println( "f" );
	}

	/**
	 * Translates mesh data structure into new, longer, arrays Helpful when
	 * increasing mesh vertices, edges, faces...
	 * 
	 * @param newVertices
	 *            New vertices array
	 * @param newEdges
	 *            New edges array
	 * @param newFaces
	 *            New faces array
	 */
	private void translateMesh(Wvertex[] newVertices, Wedge[] newEdges,
			Wface newFaces[]) {
		int max = Math.min(edges.length, newEdges.length);
		for (int i = 0; i < vertices.length; ++i) {
			newVertices[i] = new Wvertex(vertices[i]);
			if (newVertices[i].edge >= max / 2)
				newVertices[i].edge += newEdges.length / 2 - edges.length / 2;
		}
		for (int i = 0; i < max / 2; ++i) {
			newEdges[i] = new Wedge(edges[i]);
			newEdges[i].hedge = edges[i].hedge + newEdges.length / 2
					- edges.length / 2;
			if (newEdges[i].next >= max / 2)
				newEdges[i].next += newEdges.length / 2 - edges.length / 2;
			newEdges[newEdges[i].hedge] = new Wedge(edges[edges[i].hedge]);
			if (newEdges[newEdges[i].hedge].next >= max / 2)
				newEdges[newEdges[i].hedge].next += newEdges.length / 2
						- edges.length / 2;
		}
		for (int i = 0; i < faces.length; ++i) {
			newFaces[i] = new Wface(faces[i]);
			if (newFaces[i].edge >= max / 2)
				newFaces[i].edge += newEdges.length / 2 - edges.length / 2;
		}
	}

	/**
	 * Gets vertices positions for a normal displacement of selected edges.
	 * 
	 * @param selected
	 *            Edges selection
	 * @param amount
	 *            Movement amplitude in AoI unit
	 * @return The new vertices positions
	 */
	public Vec3[] getVertexPositionsForFacesNormalDisplacement(
			boolean[] selected, double amount) {
		Vec3 v[] = new Vec3[vertices.length];
		Vec3[] faceNormals = getFaceNormals();
		Vec3[] faceCenters = new Vec3[faces.length];
		Vec3 v1;
		Vec3 v2;
		int pred;
		for (int i = 0; i < faces.length; ++i) {
			faceCenters[i] = new Vec3();
			int[] vf = getFaceVertices(faces[i]);
			for (int j = 0; j < vf.length; ++j)
				faceCenters[i].add(vertices[vf[j]].r);
			faceCenters[i].scale(1.0 / ((double) vf.length));
		}
		for (int i = 0; i < vertices.length; ++i) {
			v[i] = new Vec3();
			int[] ve = getVertexEdges(vertices[i]);
			Vec3 disp = new Vec3();
			Vec3 orig;
			double t;
			double d;
			boolean sel;
			for (int j = 0; j < ve.length; ++j) {
				if (selected == null) {
					if (edges[ve[j]].face != -1)
						sel = true;
					else
						sel = false;
				} else if (isFaceSelected(selected, edges[ve[j]].face))
					sel = true;
				else
					sel = false;
				if (sel)
					disp.add(faceNormals[edges[ve[j]].face]);

			}
			if (disp.length() <= 1e-12) {
				v[i] = vertices[i].r;
				continue;
			}
			disp.normalize();
			int count = 0;
			for (int j = 0; j < ve.length; ++j) {
				if (selected == null) {
					if (edges[ve[j]].face != -1)
						sel = true;
					else
						sel = false;
				} else if (isFaceSelected(selected, edges[ve[j]].face))
					sel = true;
				else
					sel = false;
				if (sel) {
					if (Math.abs(disp.dot(faceNormals[edges[ve[j]].face])) < 1e-12)
						v[i].add(vertices[i].r
								.plus(faceNormals[edges[ve[j]].face]
										.times(amount)));
					else {
						orig = faceCenters[edges[ve[j]].face]
								.plus(faceNormals[edges[ve[j]].face]
										.times(amount));
						d = -orig.dot(faceNormals[edges[ve[j]].face]);
						t = -(faceNormals[edges[ve[j]].face].dot(vertices[i].r) + d)
								/ (disp.dot(faceNormals[edges[ve[j]].face]));
						v[i].add(vertices[i].r.plus(disp.times(t)));
					}
					++count;
				}

			}
			v[i].scale(1.0 / (double) count);
		}
		return v;
	}

	/**
	 * Gets boundary edges according to initial edge selection
	 * 
	 * @param selected
	 *            The edge(s) selected on boundaries
	 * @return The whole boundaries selection
	 */
	public boolean[] getBoundarySelection(boolean[] selected) {
		boolean[] workedOut = new boolean[selected.length];
		int start;
		int current;
		int sel;

		for (int i = 0; i < selected.length; ++i) {
			if (selected[i] && !workedOut[i]) {
				workedOut[i] = true;
				start = -1;
				if (edges[i].face == -1)
					start = i;
				else if (edges[edges[i].hedge].face == -1)
					start = edges[i].hedge;
				if (start < 0)
					// not a boundary edge
					continue;
				current = edges[start].next;
				while (current != start) {
					sel = current;
					if (sel >= edges.length / 2)
						sel = edges[current].hedge;
					workedOut[sel] = true;
					current = edges[current].next;
				}
			}
		}
		return workedOut;
	}

	/**
	 * Closes boundaries based on boundary edge selection. At least one edge
	 * must be selected on the boundary.
	 * 
	 * @param selected
	 *            Edge selection
	 * @return New face selection
	 */
	public boolean[] closeBoundary(boolean[] selected) {
		boolean[] workedOut = new boolean[selected.length];
		int start;
		int current;
		int sel;
		int oldFaceNum = faces.length;

		for (int i = 0; i < selected.length; ++i) {
			if (selected[i] && !workedOut[i]) {
				workedOut[i] = true;
				start = -1;
				if (edges[i].face == -1)
					start = i;
				else if (edges[edges[i].hedge].face == -1)
					start = edges[i].hedge;
				if (start < 0)
					// not a boundary edge
					continue;
				current = edges[start].next;
				edges[start].face = faces.length;
				while (current != start) {
					sel = current;
					if (sel >= edges.length / 2)
						sel = edges[current].hedge;
					workedOut[sel] = true;
					edges[current].face = faces.length;
					current = edges[current].next;
				}
				Wface[] newFaces = new Wface[faces.length + 1];
				for (int j = 0; j < faces.length; ++j)
					newFaces[j] = faces[j];
				newFaces[faces.length] = new Wface(start);
				// Update the texture parameters.
				TextureParameter param[] = getParameters();
				ParameterValue oldParamVal[] = getParameterValues();
				if (oldParamVal != null) {
					ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
					for (int k = 0; k < oldParamVal.length; k++) {
						if (oldParamVal[k] instanceof FaceParameterValue) {
							double oldval[] = ((FaceParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newFaces.length];
							for (int j = 0; j < oldval.length; j++)
								newval[j] = oldval[j];
							for (int j = oldval.length; j < newval.length; j++)
								newval[j] = param[k].defaultVal;
							newParamVal[k] = new FaceParameterValue(newval);
						} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
							FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
							double newval[][] = new double[newFaces.length][];
							for (int j = 0; j < newFaces.length; ++j) {
								int[] fv = getFaceVertices(j, edges, newFaces);
								double val[] = new double[fv.length];
								for (int l = 0; l < fv.length; l++) {
									val[l] = fvpv.getAverageValue();
								}
								newval[j] = val;
							}
							newParamVal[k] = new FaceVertexParameterValue(
									newval);
						} else
							newParamVal[k] = oldParamVal[k].duplicate();
					}
					setParameterValues(newParamVal);
				}
				faces = newFaces;
			}
		}
		resetMesh();
		boolean[] faceSel = new boolean[faces.length];
		for (int i = oldFaceNum; i < faces.length; ++i)
			faceSel[i] = true;
		return faceSel;
	}

	/**
	 * Collapses selected faces to single vertices
	 * 
	 * @param selected
	 *            Face selection for collapse
	 */
	public void collapseFaces(boolean[] selected) {
		selected = mergeFaces(selected);
		for (int i = 0; i < selected.length; ++i) {
			if (selected[i]) {
				int[] fe = getFaceEdges(faces[i]);
				int[] vf = getFaceVertices(faces[i]);
				Vec3 pt = new Vec3();
				for (int j = 0; j < vf.length; ++j)
					pt.add(vertices[vf[j]].r);
				pt.scale(1.0 / vf.length);
				vertices[vf[0]].r = pt;
				int v0edge = vertices[vf[0]].edge;
				for (int j = 0; j < edges.length; ++j) {
					boolean checkNext = true;
					while (checkNext) {
						checkNext = false;
						for (int k = 0; k < fe.length; ++k) {
							if (edges[j].next == edges[fe[k]].hedge) {
								edges[j].next = edges[edges[fe[k]].hedge].next;
								v0edge = edges[j].next;
								checkNext = true;
							}
						}
					}
					for (int k = 0; k < vf.length; ++k) {
						if (edges[j].vertex == vf[k])
							edges[j].vertex = vf[0];
					}
				}
				vertices[vf[0]].edge = v0edge;
				int[] vertexTable = new int[vertices.length];
				int[] edgeTable = new int[edges.length];
				Wvertex[] newVert = new Wvertex[vertices.length - vf.length + 1];
				Wedge[] newEdges = new Wedge[edges.length - 2 * fe.length];
				Wface[] newFaces = new Wface[faces.length - 1];
				int count = 0;
				boolean add;
				for (int j = 0; j < vertices.length; ++j) {
					add = true;
					for (int k = 1; k < vf.length; ++k)
						if (vf[k] == j)
							add = false;
					if (add) {
						newVert[count] = new Wvertex(vertices[j]);
						vertexTable[j] = count++;
					} else
						vertexTable[j] = -1;
				}
				count = 0;
				for (int j = 0; j < edges.length; ++j) {
					add = true;
					for (int k = 0; k < fe.length; ++k)
						if (fe[k] == j || edges[fe[k]].hedge == j)
							add = false;
					if (add) {
						edgeTable[j] = count;
						newEdges[count] = new Wedge(edges[j]);
						++count;
					} else
						edgeTable[j] = -1;
				}
				count = 0;
				for (int j = 0; j < faces.length; ++j) {
					if (j != i) {
						newFaces[count] = new Wface(faces[j]);
						int e = faces[j].edge;
						while (edgeTable[e] == -1)
							e = edges[e].next;
						newFaces[count].edge = edgeTable[e];
						++count;
					}
				}
				for (int j = 0; j < newEdges.length; ++j) {
					newEdges[j].next = edgeTable[newEdges[j].next];
					newEdges[j].hedge = edgeTable[newEdges[j].hedge];
					newEdges[j].vertex = vertexTable[newEdges[j].vertex];
					if (newEdges[j].face > i)
						--newEdges[j].face;
				}
				for (int j = 0; j < newVert.length; ++j)
					newVert[j].edge = edgeTable[newVert[j].edge];
				// Update the texture parameters.
				TextureParameter param[] = getParameters();
				ParameterValue oldParamVal[] = getParameterValues();
				if (oldParamVal != null) {
					ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
					for (int k = 0; k < oldParamVal.length; k++) {
						if (oldParamVal[k] instanceof FaceParameterValue) {
							double oldval[] = ((FaceParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newFaces.length];
							for (int j = 0; j < newval.length; j++) {
								if (j > i)
									newval[j] = oldval[j + 1];
								else if (j < i)
									newval[j] = oldval[j];
							}
							newParamVal[k] = new FaceParameterValue(newval);
						} else if (oldParamVal[k] instanceof VertexParameterValue) {
							double oldval[] = ((VertexParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newVert.length];
							for (int j = 0; j < oldval.length; j++)
								if (vertexTable[j] != -1)
									newval[vertexTable[j]] = oldval[j];
							newParamVal[k] = new VertexParameterValue(newval);
						} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
							FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
							double newval[][] = new double[newFaces.length][];
							for (int j = 0; j < newFaces.length; ++j) {
								int[] fv = getFaceVertices(j, newEdges,
										newFaces);
								double val[] = new double[fv.length];
								for (int l = 0; l < fv.length; l++) {
									val[l] = fvpv.getAverageValue();
								}
								newval[j] = val;
							}
							newParamVal[k] = new FaceVertexParameterValue(
									newval);
						} else
							newParamVal[k] = oldParamVal[k].duplicate();
					}
					setParameterValues(newParamVal);
				}
				faces = newFaces;
				vertices = newVert;
				edges = newEdges;
				resetMesh();
				boolean[] newSel = new boolean[faces.length];
				count = 0;
				for (int j = 0; j < selected.length; ++j)
					if (j != i)
						newSel[count++] = selected[j];
				selected = newSel;
				selected = removeTwoEdgedFaces(selected);
				i = -1;
			}
		}
	}

	/**
	 * Collapses selected edges to single vertices
	 * 
	 * @param selected
	 *            Edge selection for collapse
	 */
	public void collapseEdges(boolean[] selected) {
		for (int i = 0; i < selected.length; ++i) {
			if (selected[i]) {
				int vert = edges[i].vertex;
				int otherVert = edges[edges[i].hedge].vertex;
				Vec3 pt = new Vec3(vertices[vert].r);
				pt.add(vertices[otherVert].r);
				pt.scale(0.5);
				vertices[vert].r = pt;
				int v0edge = vertices[vert].edge;
				for (int j = 0; j < edges.length; ++j) {
					if (edges[j].next == i) {
						edges[j].next = edges[i].next;
						v0edge = edges[j].next;
					}
					if (edges[j].next == edges[i].hedge)
						edges[j].next = edges[edges[i].hedge].next;
					if (edges[j].vertex == otherVert)
						edges[j].vertex = vert;
				}
				vertices[vert].edge = v0edge;
				int[] vertexTable = new int[vertices.length];
				int[] edgeTable = new int[edges.length];
				Wvertex[] newVert = new Wvertex[vertices.length - 1];
				Wedge[] newEdges = new Wedge[edges.length - 2];
				int count = 0;
				for (int j = 0; j < vertices.length; ++j) {
					if (j != otherVert) {
						newVert[count] = new Wvertex(vertices[j]);
						vertexTable[j] = count++;
					} else
						vertexTable[j] = -1;
				}
				count = 0;
				for (int j = 0; j < edges.length; ++j) {
					if (j != i && j != edges[i].hedge) {
						edgeTable[j] = count;
						newEdges[count] = new Wedge(edges[j]);
						++count;
					} else
						edgeTable[j] = -1;
				}
				for (int j = 0; j < faces.length; ++j) {
					int e = faces[j].edge;
					while (edgeTable[e] == -1)
						e = edges[e].next;
					faces[j].edge = edgeTable[e];
				}
				for (int j = 0; j < newEdges.length; ++j) {
					newEdges[j].next = edgeTable[newEdges[j].next];
					newEdges[j].hedge = edgeTable[newEdges[j].hedge];
					newEdges[j].vertex = vertexTable[newEdges[j].vertex];
				}
				for (int j = 0; j < newVert.length; ++j)
					newVert[j].edge = edgeTable[newVert[j].edge];
				// Update the texture parameters.
				TextureParameter param[] = getParameters();
				ParameterValue oldParamVal[] = getParameterValues();
				if (oldParamVal != null) {
					ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
					for (int k = 0; k < oldParamVal.length; k++) {
						if (oldParamVal[k] instanceof VertexParameterValue) {
							double oldval[] = ((VertexParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newVert.length];
							for (int j = 0; j < oldval.length; j++)
								if (vertexTable[j] != -1)
									newval[vertexTable[j]] = oldval[j];
							newParamVal[k] = new VertexParameterValue(newval);
						} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
							FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
							double newval[][] = new double[faces.length][];
							for (int j = 0; j < faces.length; ++j) {
								int[] fv = getFaceVertices(j, newEdges, faces);
								double val[] = new double[fv.length];
								for (int l = 0; l < fv.length; l++) {
									val[l] = fvpv.getAverageValue();
								}
								newval[j] = val;
							}
							newParamVal[k] = new FaceVertexParameterValue(
									newval);
						} else
							newParamVal[k] = oldParamVal[k].duplicate();
					}
					setParameterValues(newParamVal);
				}
				vertices = newVert;
				edges = newEdges;
				resetMesh();
				boolean[] newSel = new boolean[edges.length];
				count = 0;
				for (int j = 0; j < selected.length; ++j)
					if (edgeTable[j] != -1)
						newSel[count++] = selected[j];
				selected = newSel;
				selected = removeTwoEdgedFaces(selected);
				i = -1;
			}
		}
	}

	/**
	 * Collapses selected vertices through merging selected vertices with
	 * neighbors
	 * 
	 * @param selected
	 *            Vertices selection for collapse
	 */
	public void collapseVertices(boolean[] selected) {
		for (int i = 0; i < selected.length; ++i) {
			if (selected[i]) {
				// System.out.println( "sï¿½ï¿½lectionnï¿½ï¿½: " + i );
				// dumpMesh();
				int[] ve = getVertexEdges(vertices[i]);
				int[] neighbors = new int[ve.length];
				for (int j = 0; j < neighbors.length; ++j)
					neighbors[j] = edges[ve[j]].vertex;
				Vec3 pt = new Vec3();
				for (int j = 0; j < neighbors.length; ++j)
					pt.add(vertices[neighbors[j]].r);
				pt.scale(1.0 / neighbors.length);
				vertices[i].r = pt;
				vertices[i].edge = edges[vertices[i].edge].next;
				for (int j = 0; j < edges.length; ++j) {
					boolean checkNext = true;
					while (checkNext) {
						checkNext = false;
						for (int k = 0; k < ve.length; ++k) {
							if (edges[j].next == edges[ve[k]].hedge) {
								edges[j].next = edges[edges[j].next].next;
								checkNext = true;
							} else if (edges[j].next == ve[k]) {
								edges[j].next = edges[ve[k]].next;
								checkNext = true;
							}
						}
					}
					for (int k = 0; k < neighbors.length; ++k) {
						if (edges[j].vertex == neighbors[k])
							edges[j].vertex = i;
					}
				}
				int[] vertexTable = new int[vertices.length];
				int[] edgeTable = new int[edges.length];
				Wvertex[] newVert = new Wvertex[vertices.length
						- neighbors.length];
				Wedge[] newEdges = new Wedge[edges.length - 2 * ve.length];
				int count = 0;
				boolean add;
				for (int j = 0; j < vertices.length; ++j) {
					add = true;
					for (int k = 0; k < neighbors.length; ++k)
						if (neighbors[k] == j)
							add = false;
					if (add) {
						newVert[count] = new Wvertex(vertices[j]);
						vertexTable[j] = count++;
					} else
						vertexTable[j] = -1;
				}
				count = 0;
				for (int j = 0; j < edges.length; ++j) {
					add = true;
					for (int k = 0; k < ve.length; ++k)
						if (ve[k] == j || edges[ve[k]].hedge == j)
							add = false;
					if (add) {
						edgeTable[j] = count;
						newEdges[count] = new Wedge(edges[j]);
						++count;
					} else
						edgeTable[j] = -1;
				}
				/*
				 * for ( int j = 0; j < vertices.length; ++j ) { int e =
				 * vertices[j].edge; while ( edgeTable[e] == -1 ) e =
				 * edges[edges[e].hedge].next; vertices[j].edge = edgeTable[e]; }
				 */
				for (int j = 0; j < faces.length; ++j) {
					int e = faces[j].edge;
					while (edgeTable[e] == -1)
						e = edges[e].next;
					faces[j].edge = edgeTable[e];
				}
				for (int j = 0; j < newEdges.length; ++j) {
					newEdges[j].next = edgeTable[newEdges[j].next];
					newEdges[j].hedge = edgeTable[newEdges[j].hedge];
					newEdges[j].vertex = vertexTable[newEdges[j].vertex];
				}
				for (int j = 0; j < newVert.length; ++j)
					newVert[j].edge = edgeTable[newVert[j].edge];
				// Update the texture parameters.
				TextureParameter param[] = getParameters();
				ParameterValue oldParamVal[] = getParameterValues();
				if (oldParamVal != null) {
					ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
					for (int k = 0; k < oldParamVal.length; k++) {
						if (oldParamVal[k] instanceof VertexParameterValue) {
							double oldval[] = ((VertexParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newVert.length];
							for (int j = 0; j < oldval.length; j++)
								if (vertexTable[j] != -1)
									newval[vertexTable[j]] = oldval[j];
							newParamVal[k] = new VertexParameterValue(newval);
						} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
							FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
							double newval[][] = new double[faces.length][];
							for (int j = 0; j < faces.length; ++j) {
								int[] fv = getFaceVertices(j, newEdges, faces);
								double val[] = new double[fv.length];
								for (int l = 0; l < fv.length; l++) {
									val[l] = fvpv.getAverageValue();
								}
								newval[j] = val;
							}
							newParamVal[k] = new FaceVertexParameterValue(
									newval);
						} else
							newParamVal[k] = oldParamVal[k].duplicate();
					}
					setParameterValues(newParamVal);
				}
				vertices = newVert;
				edges = newEdges;
				resetMesh();
				boolean[] newSel = new boolean[vertices.length];
				count = 0;
				selected[i] = false;
				for (int j = 0; j < selected.length; ++j) {
					add = true;
					for (int k = 0; k < neighbors.length; ++k) {
						if (neighbors[k] == j)
							add = false;
					}
					if (add)
						newSel[count++] = selected[j];
				}
				selected = newSel;
				// dumpMesh();
				selected = removeTwoEdgedFaces(selected);
				i = -1;
			}
		}
	}

	/**
	 * Transforms vertices into faces
	 * 
	 * @param selected
	 *            Vertices selection
	 */
	public void facetVertices(boolean[] selected) {
		for (int i = 0; i < selected.length; ++i) {
			if (selected[i]) {
				int[] ve = getVertexEdges(vertices[i]);
				for (int k = 0; k < ve.length; ++k) {
					if (edges[ve[k]].face == -1) {
						selected[i] = false;
						continue;
					}
				}
				// System.out.println( "sï¿½ï¿½lectionnï¿½ï¿½: " + i );
				// dumpMesh();
				Wvertex[] newVert = new Wvertex[vertices.length - 1];
				Wedge[] newEdges = new Wedge[edges.length];
				Wface[] newFaces = new Wface[faces.length + 1];
				int count = 0;
				for (int j = 0; j < vertices.length; ++j)
					if (j != i)
						newVert[count++] = new Wvertex(vertices[j]);
				for (int j = 0; j < edges.length; ++j)
					newEdges[j] = new Wedge(edges[j]);
				int prev;
				for (int k = 0; k < ve.length; ++k) {
					newEdges[newEdges[ve[k]].hedge].vertex = edges[edges[edges[ve[k]].hedge].next].vertex;
					newEdges[newEdges[ve[k]].hedge].next = edges[edges[edges[ve[k]].hedge].next].next;
					newEdges[ve[k]].face = faces.length;
					prev = k - 1;
					if (prev < 0)
						prev = ve.length - 1;
					newEdges[ve[k]].next = ve[prev];
				}
				for (int j = 0; j < faces.length; ++j) {
					newFaces[j] = new Wface(faces[j]);
					for (int k = 0; k < ve.length; ++k) {
						if (newFaces[j].edge == ve[k])
							newFaces[j].edge = edges[newFaces[j].edge].next;
						else if (newFaces[j].edge == edges[ve[k]].hedge)
							newFaces[j].edge = edges[edges[edges[ve[k]].hedge].next].next;
					}
				}
				newFaces[faces.length] = new Wface(ve[0]);
				// newFaces[faces.length].edgeSmoothness = 0;
				// newFaces[faces.length].centerSmoothness = 0;
				/*
				 * for ( int j = 0; j < ve.length; ++j ) {
				 * //newFaces[faces.length].edgeSmoothness +=
				 * faces[edges[ve[j]].face].edgeSmoothness;
				 * //newFaces[faces.length].centerSmoothness +=
				 * faces[edges[ve[j]].face].centerSmoothness; }
				 */
				// newFaces[faces.length].edgeSmoothness /= (double) ve.length;
				// newFaces[faces.length].centerSmoothness /= (double)
				// ve.length;
				for (int j = 0; j < newEdges.length; ++j)
					if (newEdges[j].vertex > i)
						--newEdges[j].vertex;
				// Update the texture parameters.
				TextureParameter param[] = getParameters();
				ParameterValue oldParamVal[] = getParameterValues();
				if (oldParamVal != null) {
					ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
					for (int k = 0; k < oldParamVal.length; k++) {
						if (oldParamVal[k] instanceof FaceParameterValue) {
							double oldval[] = ((FaceParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newFaces.length];
							for (int j = 0; j < oldval.length; j++)
								newval[j] = oldval[j];
							newval[newval.length - 1] = 0;
							for (int j = 0; j < ve.length; ++j)
								newval[newval.length - 1] += oldval[edges[ve[j]].face];
							newval[newval.length - 1] /= (double) ve.length;
							newParamVal[k] = new FaceParameterValue(newval);
						} else if (oldParamVal[k] instanceof VertexParameterValue) {
							double oldval[] = ((VertexParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newVert.length];
							count = 0;
							for (int j = 0; j < oldval.length; j++)
								if (i != j)
									newval[count++] = oldval[j];
							newParamVal[k] = new VertexParameterValue(newval);
						} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
							FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
							double newval[][] = new double[newFaces.length][];
							for (int j = 0; j < newFaces.length; ++j) {
								int[] fv = getFaceVertices(j, newEdges,
										newFaces);
								double val[] = new double[fv.length];
								for (int l = 0; l < fv.length; l++) {
									val[l] = fvpv.getAverageValue();
								}
								newval[j] = val;
							}
							newParamVal[k] = new FaceVertexParameterValue(
									newval);
						} else
							newParamVal[k] = oldParamVal[k].duplicate();
					}
					setParameterValues(newParamVal);
				}
				vertices = newVert;
				edges = newEdges;
				faces = newFaces;
				resetMesh();
				boolean[] newSel = new boolean[vertices.length];
				count = 0;
				for (int j = 0; j < selected.length; ++j)
					if (j != i)
						newSel[count++] = selected[j];
				selected = newSel;
				// dumpMesh();
				selected = removeTwoEdgedFaces(selected);
				i = -1;
			}
		}
	}

	/**
	 * It may occur during certain operations (e.g. collapse) that faces end up
	 * having only two edges. This method removes such faces. In doing so, it
	 * updates any face/edge/vertex selection if the array given is different
	 * from null.
	 * 
	 * @param selected
	 *            Current selection
	 * @return Updated face selection
	 */
	private boolean[] removeTwoEdgedFaces(boolean[] selected) {
		for (int i = 0; i < faces.length; ++i) {
			int[] fe = getFaceEdges(faces[i]);
			if (fe.length == 2) {
				int[] ve = getVertexEdges(vertices[edges[fe[0]].vertex]);
				if (ve.length == 2)
					if (edges[ve[0]].vertex == edges[ve[1]].vertex) {
						// lonely vertex
						selected = deleteDanglingVertex(edges[fe[0]].vertex,
								selected);
						--i;
						continue;
					}
				ve = getVertexEdges(vertices[edges[fe[1]].vertex]);
				if (ve.length == 2)
					if (edges[ve[0]].vertex == edges[ve[1]].vertex) {
						// lonely vertex
						selected = deleteDanglingVertex(edges[fe[1]].vertex,
								selected);
						--i;
						continue;
					}
				// remove the face
				// System.out.println( " face removed " );
				for (int j = 0; j < edges.length; ++j) {
					if (edges[j].next == edges[fe[1]].hedge)
						edges[j].next = fe[0];
					if (edges[j].next == fe[1])
						edges[j].next = edges[fe[0]].hedge;
				}
				edges[fe[0]].face = edges[edges[fe[1]].hedge].face;
				edges[fe[0]].next = edges[edges[fe[1]].hedge].next;
				for (int j = 0; j < vertices.length; ++j) {
					if (vertices[j].edge == edges[fe[1]].hedge)
						vertices[j].edge = fe[0];
					if (vertices[j].edge == fe[1])
						vertices[j].edge = edges[fe[0]].hedge;
				}
				for (int j = 0; j < faces.length; ++j) {
					if (faces[j].edge == edges[fe[1]].hedge)
						faces[j].edge = fe[0];
					if (faces[j].edge == fe[1])
						faces[j].edge = edges[fe[0]].hedge;
				}
				Wedge[] newEdges = new Wedge[edges.length - 2];
				Wface[] newFaces = new Wface[faces.length - 1];
				int count = 0;
				int[] edgeTable = new int[edges.length];
				for (int j = 0; j < edges.length; ++j) {
					if (j != edges[fe[1]].hedge && j != fe[1]) {
						edgeTable[j] = count;
						newEdges[count++] = new Wedge(edges[j]);
					} else
						edgeTable[j] = -1;
				}
				count = 0;
				for (int j = 0; j < faces.length; ++j) {
					if (j != i) {
						newFaces[count] = new Wface(faces[j]);
						newFaces[count].edge = edgeTable[faces[j].edge];
						++count;
					}
				}
				for (int j = 0; j < newEdges.length; ++j) {
					newEdges[j].next = edgeTable[newEdges[j].next];
					newEdges[j].hedge = edgeTable[newEdges[j].hedge];
					if (newEdges[j].face > i)
						--newEdges[j].face;
				}
				for (int j = 0; j < vertices.length; ++j)
					vertices[j].edge = edgeTable[vertices[j].edge];
				// Update the texture parameters.
				TextureParameter param[] = getParameters();
				ParameterValue oldParamVal[] = getParameterValues();
				if (oldParamVal != null) {
					ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
					for (int k = 0; k < oldParamVal.length; k++) {
						if (oldParamVal[k] instanceof FaceParameterValue) {
							double oldval[] = ((FaceParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newFaces.length];
							for (int j = 0; j < newval.length; j++) {
								if (j > i)
									newval[j] = oldval[j + 1];
								else if (j < i)
									newval[j] = oldval[j];
							}
							newParamVal[k] = new FaceParameterValue(newval);
						} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
							FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
							double newval[][] = new double[newFaces.length][];
							for (int j = 0; j < newFaces.length; ++j) {
								int[] fv = getFaceVertices(j, newEdges,
										newFaces);
								double val[] = new double[fv.length];
								for (int l = 0; l < fv.length; l++) {
									val[l] = fvpv.getAverageValue();
								}
								newval[j] = val;
							}
							newParamVal[k] = new FaceVertexParameterValue(
									newval);
						} else
							newParamVal[k] = oldParamVal[k].duplicate();
					}
					setParameterValues(newParamVal);
				}
				if (selected != null) {
					boolean[] newSel = null;
					count = 0;
					if (selected.length == faces.length) {
						newSel = new boolean[newFaces.length];
						for (int j = 0; j < selected.length; ++j)
							if (j != i)
								newSel[count++] = selected[j];
					} else if (selected.length == edges.length) {
						newSel = new boolean[newEdges.length];
						for (int j = 0; j < selected.length; ++j)
							if (edgeTable[j] != -1)
								newSel[count++] = selected[j];
					} else if (selected.length == vertices.length)
						newSel = selected;
					selected = newSel;
				}
				faces = newFaces;
				edges = newEdges;
				--i;
			}
		}
		return selected;
	}

	/**
	 * Deletes a single, dangling vertex linked to antoher vertex through two
	 * edges. This situation can occur when a two edge face is along a boundary.
	 * 
	 * @param vert
	 *            The dangling vertex to remove.
	 * @param selected
	 *            Current selection
	 * @return Updated selection
	 */
	private boolean[] deleteDanglingVertex(int vert, boolean[] selected) {
		Wvertex[] newVert = new Wvertex[vertices.length - 1];
		Wedge[] newEdges = new Wedge[edges.length - 4];
		Wface[] newFaces = new Wface[faces.length - 1];
		int[] edgeTable = new int[edges.length];
		int[] ve = getVertexEdges(vertices[vert]);
		int removedFace = edges[ve[0]].face;
		if (removedFace == -1)
			removedFace = edges[edges[ve[0]].hedge].face;
		// System.out.println( "Face: " + removedFace );
		int count = 0;
		for (int i = 0; i < edges.length; ++i)
			if (i != ve[0] && i != edges[ve[0]].hedge && i != ve[1]
					&& i != edges[ve[1]].hedge) {
				newEdges[count] = new Wedge(edges[i]);
				if (newEdges[count].next == edges[ve[0]].hedge
						|| newEdges[count].next == edges[ve[1]].hedge)
					newEdges[count].next = edges[edges[edges[i].next].next].next;
				edgeTable[i] = count++;
			} else
				edgeTable[i] = -1;
		count = 0;
		for (int i = 0; i < vertices.length; ++i)
			if (i != vert) {
				newVert[count] = new Wvertex(vertices[i]);
				newVert[count].edge = edgeTable[newVert[count].edge];
				++count;
			}
		count = 0;
		for (int i = 0; i < faces.length; ++i)
			if (i != removedFace) {
				newFaces[count] = new Wface(faces[i]);
				newFaces[count].edge = edgeTable[newFaces[count].edge];
				++count;
			}
		for (int i = 0; i < newEdges.length; ++i) {
			newEdges[i].next = edgeTable[newEdges[i].next];
			if (newEdges[i].face > removedFace)
				--newEdges[i].face;
			newEdges[i].hedge = edgeTable[newEdges[i].hedge];
			if (newEdges[i].vertex > vert)
				--newEdges[i].vertex;
		}
		// Update the texture parameters.
		TextureParameter param[] = getParameters();
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newFaces.length];
					count = 0;
					for (int j = 0; j < oldval.length; j++)
						if (j != removedFace)
							newval[count++] = oldval[j];
					newParamVal[k] = new FaceParameterValue(newval);
				} else if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVert.length];
					count = 0;
					for (int j = 0; j < oldval.length; j++)
						if (vert != j)
							newval[count++] = oldval[j];
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		if (selected != null) {
			boolean[] newSel = null;
			count = 0;
			if (selected.length == faces.length) {
				newSel = new boolean[newFaces.length];
				for (int j = 0; j < selected.length; ++j)
					if (j != removedFace)
						newSel[count++] = selected[j];
			} else if (selected.length == edges.length) {
				newSel = new boolean[newEdges.length];
				for (int j = 0; j < selected.length; ++j)
					if (edgeTable[j] != -1)
						newSel[count++] = selected[j];
			} else if (selected.length == vertices.length) {
				newSel = new boolean[newVert.length];
				for (int j = 0; j < selected.length; ++j)
					if (j != vert)
						newSel[count++] = selected[j];
			}
			selected = newSel;
		}
		faces = newFaces;
		vertices = newVert;
		edges = newEdges;
		resetMesh();
		return selected;
	}

	/**
	 * Merge selected edges, provided they share the same face and a vertex
	 * 
	 * @param selected
	 *            edges
	 * @return New selection
	 */
	public boolean[] mergeEdges(boolean[] selected) {
		// dumpMesh();
		// for ( int i = 0; i < selected.length; ++i )
		// System.out.println( i + " " + selected[i] );
		for (int i = 0; i < edges.length; ++i) {
			int esel = i;
			if (esel >= edges.length / 2)
				esel = edges[i].hedge;
			if (selected[esel]) {
				// System.out.println( "i:" + i );
				int face1 = edges[i].face;
				int face2 = edges[edges[i].hedge].face;
				int count = 0;
				int e = edges[i].next;
				esel = e;
				if (esel >= edges.length / 2)
					esel = edges[e].hedge;
				// System.out.println( i + " / " + e + " " + edges[e].face + " "
				// + selected[esel] );
				while (edges[e].face == face1
						&& edges[edges[e].hedge].face == face2
						&& selected[esel] && e != i) {
					// System.out.println( "e:" + e );
					++count;
					e = edges[e].next;
					esel = e;
					if (esel >= edges.length / 2)
						esel = edges[e].hedge;
				}
				// System.out.println( "count " + count );
				if (count == 0)
					continue;
				Wvertex[] newVert = new Wvertex[vertices.length - count];
				int[] vertexTable = new int[vertices.length];
				Wedge[] newEdges = new Wedge[edges.length - 2 * count];
				int[] edgeTable = new int[edges.length];
				int[] edgeLink = new int[count + 1];
				int[] vertexLink = new int[count + 1];
				edgeLink[0] = i;
				vertexLink[0] = edges[i].vertex;
				count = 1;
				e = edges[i].next;
				esel = e;
				if (esel >= edges.length / 2)
					esel = edges[e].hedge;
				while (edges[e].face == face1
						&& edges[edges[e].hedge].face == face2
						&& selected[esel] && e != i) {
					edgeLink[count] = e;
					vertexLink[count] = edges[e].vertex;
					++count;
					e = edges[e].next;
					esel = e;
					if (esel >= edges.length / 2)
						esel = edges[e].hedge;
				}
				count = 0;
				boolean add;
				for (int j = 0; j < vertices.length; ++j) {
					add = true;
					for (int k = 0; k < vertexLink.length - 1; ++k)
						if (j == vertexLink[k])
							add = false;
					if (j == edges[edgeLink[edgeLink.length - 1]].vertex)
						vertices[edges[edgeLink[edgeLink.length - 1]].vertex].edge = edges[edgeLink[0]].hedge;
					if (add) {
						newVert[count] = new Wvertex(vertices[j]);
						vertexTable[j] = count++;
					} else
						vertexTable[j] = -1;
				}
				/*
				 * for ( int k = 0; k < edgeLink.length; ++k ) {
				 * System.out.println( k + " edge link " + edgeLink[k] );
				 * System.out.println( k + " vertex link " + vertexLink[k] ); }
				 */
				count = 0;
				for (int j = 0; j < edges.length; ++j) {
					add = true;
					for (int k = 1; k < edgeLink.length; ++k)
						if (j == edgeLink[k] || j == edges[edgeLink[k]].hedge)
							add = false;
					if (add) {
						newEdges[count] = new Wedge(edges[j]);
						if (newEdges[count].next == edges[edgeLink[edgeLink.length - 1]].hedge)
							newEdges[count].next = edges[edgeLink[0]].hedge;
						edgeTable[j] = count++;
					} else
						edgeTable[j] = -1;
				}
				newEdges[edgeTable[edgeLink[0]]].vertex = edges[edgeLink[edgeLink.length - 1]].vertex;
				newEdges[edgeTable[edgeLink[0]]].next = edges[edgeLink[edgeLink.length - 1]].next;
				for (int j = 0; j < newVert.length; ++j)
					newVert[j].edge = edgeTable[newVert[j].edge];
				for (int j = 0; j < newEdges.length; ++j) {
					newEdges[j].next = edgeTable[newEdges[j].next];
					newEdges[j].hedge = edgeTable[newEdges[j].hedge];
					newEdges[j].vertex = vertexTable[newEdges[j].vertex];
				}
				for (int j = 0; j < faces.length; ++j) {
					e = faces[j].edge;
					while (edgeTable[e] == -1)
						e = edges[e].next;
					faces[j].edge = edgeTable[e];
				}
				boolean[] newSel = new boolean[newEdges.length / 2];
				count = 0;
				for (int j = 0; j < selected.length; ++j)
					if (edgeTable[j] != -1)
						newSel[count++] = selected[j];
				// Update the texture parameters.
				ParameterValue oldParamVal[] = getParameterValues();
				if (oldParamVal != null) {
					ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
					for (int k = 0; k < oldParamVal.length; k++) {
						if (oldParamVal[k] instanceof VertexParameterValue) {
							double oldval[] = ((VertexParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[newVert.length];
							for (int j = 0; j < oldval.length; j++)
								if (vertexTable[j] != -1)
									newval[vertexTable[j]] = oldval[j];
							newParamVal[k] = new VertexParameterValue(newval);
						} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
							FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
							double newval[][] = new double[faces.length][];
							for (int j = 0; j < faces.length; ++j) {
								int[] fv = getFaceVertices(j, newEdges, faces);
								double val[] = new double[fv.length];
								for (int l = 0; l < fv.length; l++) {
									val[l] = fvpv.getAverageValue();
								}
								newval[j] = val;
							}
							newParamVal[k] = new FaceVertexParameterValue(
									newval);
						} else
							newParamVal[k] = oldParamVal[k].duplicate();
					}
					setParameterValues(newParamVal);
				}
				selected = newSel;
				vertices = newVert;
				edges = newEdges;
				resetMesh();
				i = -1;
			}
		}
		System.out.println();
		return selected;
	}

	/**
	 * Merge selected faces, provided they share at least an edge
	 * 
	 * @param selected
	 *            faces
	 * @return New selection
	 */
	public boolean[] mergeFaces(boolean[] selected) {
		for (int i = 1; i < selected.length; ++i)
			for (int l = 0; l < i; ++l) {
				if (selected.length == 1) {
					boolean[] sel = new boolean[1];
					sel[0] = true;
					return sel;
				}
				if (selected[i] && selected[l]) {
					// first, edge merge to simplify boundary between face1
					// and face 2
					boolean[] edgeSel = new boolean[edges.length / 2];
					boolean merge = false;
					for (int j = 0; j < edges.length / 2; ++j) {
						if ((edges[j].face == l && edges[edges[j].hedge].face == i)
								|| (edges[j].face == i && edges[edges[j].hedge].face == l)) {
							merge = true;
							edgeSel[j] = true;
						}
					}
					if (!merge)
						continue;
					mergeEdges(edgeSel);
					int theEdge = -1;
					int count = 0;
					for (int j = 0; j < edges.length / 2; ++j) {
						if ((edges[j].face == l && edges[edges[j].hedge].face == i)
								|| (edges[j].face == i && edges[edges[j].hedge].face == l)) {
							++count;
							theEdge = j;
						}
					}
					if (count > 1) {
						// System.out.println( "pb with count" );
						new BStandardDialog(Translate.text("polymesh:errorTitle"),
								UIUtilities.breakString(Translate
										.text("polymesh:illegalMeshStructure")),
								BStandardDialog.ERROR).showMessageDialog(null);
						return selected;
					}
					Wedge[] newEdges = new Wedge[edges.length - 2];
					int[] edgeTable = new int[edges.length];
					Wface[] newFaces = new Wface[faces.length - 1];
					count = 0;
					for (int j = 0; j < edges.length; ++j) {
						if (j != theEdge && j != edges[theEdge].hedge) {
							newEdges[count] = new Wedge(edges[j]);
							if (newEdges[count].next == theEdge)
								newEdges[count].next = edges[edges[theEdge].hedge].next;
							if (newEdges[count].next == edges[theEdge].hedge)
								newEdges[count].next = edges[theEdge].next;
							edgeTable[j] = count++;
						} else
							edgeTable[j] = -1;
					}
					int e;
					for (int j = 0; j < vertices.length; ++j) {
						e = vertices[j].edge;
						while (edgeTable[e] == -1)
							e = edges[edges[e].hedge].next;
						vertices[j].edge = edgeTable[e];
					}
					for (int j = 0; j < newEdges.length; ++j) {
						newEdges[j].next = edgeTable[newEdges[j].next];
						newEdges[j].hedge = edgeTable[newEdges[j].hedge];
						if (newEdges[j].face == i)
							newEdges[j].face = l;
						if (newEdges[j].face > i)
							--newEdges[j].face;
					}
					count = 0;
					for (int j = 0; j < faces.length; ++j) {
						if (j != i) {
							newFaces[count] = new Wface(faces[j]);
							newFaces[count].edge = edgeTable[newFaces[count].edge];
							e = faces[j].edge;
							while (edgeTable[e] == -1)
								e = edges[e].next;
							newFaces[count++].edge = edgeTable[e];
						}
					}
					boolean[] newSel = new boolean[newFaces.length];
					count = 0;
					for (int j = 0; j < selected.length; ++j)
						if (j != i)
							newSel[count++] = selected[j];
					// Update the texture parameters.
					ParameterValue oldParamVal[] = getParameterValues();
					if (oldParamVal != null) {
						ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
						for (int k = 0; k < oldParamVal.length; k++) {
							if (oldParamVal[k] instanceof FaceParameterValue) {
								double oldval[] = ((FaceParameterValue) oldParamVal[k])
										.getValue();
								double newval[] = new double[newFaces.length];
								count = 0;
								for (int j = 0; j < oldval.length; j++)
									if (j != i)
										newval[count++] = oldval[j];
								newParamVal[k] = new FaceParameterValue(newval);
							} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
								FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
								double newval[][] = new double[newFaces.length][];
								for (int j = 0; j < newFaces.length; ++j) {
									int[] fv = getFaceVertices(j, newEdges,
											newFaces);
									double val[] = new double[fv.length];
									for (int m = 0; m < fv.length; m++) {
										val[m] = fvpv.getAverageValue();
									}
									newval[j] = val;
								}
								newParamVal[k] = new FaceVertexParameterValue(
										newval);
							} else
								newParamVal[k] = oldParamVal[k].duplicate();
						}
						setParameterValues(newParamVal);
					}
					selected = newSel;
					faces = newFaces;
					edges = newEdges;
					resetMesh();
					i = 1;
					l = -1;
				}
			}
		return selected;
	}

	/**
	 * Bevels selected edges
	 * 
	 * @param selected
	 *            Edges selected for bevel
	 * @param value
	 *            Bevel amplitude
	 * @return Description of the Return Value
	 */
	public boolean[] bevelEdges(boolean[] selected, double value) {
		if (value < 1e-6)
			return selected;

		int[] e;
		int ed;
		int ned;
		int ped;
		Vec3 p1;
		Vec3 p2;
		Vec3 p3;
		Vec3 t1;
		Vec3 t2;
		Vec3 t3;
		Vec3 n1;
		Vec3 n2;
		Vec3 n3;
		double t;
		double d;
		double a1;
		double a2;
		int vertCount = 0;
		int vertAdded;
		int edgeCount = 0;
		int faceCount = 0;
		double prod;

		// dumpMesh();
		boolean[] fullSel;
		fullSel = new boolean[edges.length];
		boolean[] newSel;
		Vec3[] edgeNormals = getEdgeNormals();
		Vec3[] faceNormals = getFaceNormals();
		Vec3[] normals = getNormals();
		Vec3[] cuts = new Vec3[edges.length * 2];
		short[] cutsQuality = new short[edges.length];
		int[] cutsTable = new int[edges.length * 2];
		for (int i = 0; i < edges.length / 2; ++i) {
			if (selected[i]) {
				fullSel[i] = fullSel[edges[i].hedge] = true;

				++edgeCount;
			}
			cutsTable[i] = cutsTable[edges[i].hedge] = -1;
			cutsTable[i + edges.length] = cutsTable[edges[i].hedge
					+ edges.length] = -1;
		}
		for (int i = 0; i < vertices.length; ++i) {
			e = getVertexEdges(vertices[i]);
			boolean cut = false;
			for (int j = 0; j < e.length; ++j) {
				if (fullSel[e[j]])
					cut = true;
			}
			if (!cut)
				continue;
			// System.out.println( "one vertex to cut: " + i );
			// vertices[i] counts against vertices to add.
			vertAdded = -1;
			// v = new Vec3[e.length];
			// for ( int j = 0; j < v.length; ++j )
			// {
			// v[j] = vertices[edges[e[j]].vertex].r.plus( vertices[i].r );
			// v[j].scale( 0.5 );
			// }
			for (int j = 0; j < e.length; ++j) {
				// System.out.println( e[j] + " :1/2: " + edges[e[j]].hedge );
				ed = edges[e[j]].hedge;
				ned = edges[ed].next;
				ped = j - 1;
				if (ped < 0)
					ped = e.length - 1;
				ped = edges[e[ped]].hedge;
				prod = vertices[edges[ned].vertex].r.minus(vertices[i].r)
						.cross(
								vertices[edges[e[j]].vertex].r
										.minus(vertices[i].r)).length();
				if (fullSel[ed] && fullSel[ned]) {

					// plane intersections
					if ((prod > 1e-4) && (e.length > 2)) {
						// System.out.println( "prod: " + prod );
						p1 = vertices[edges[ned].vertex].r.minus(vertices[i].r);
						p1.normalize();
						a1 = Math.acos(edgeNormals[ed].times(-1.0).dot(p1));
						a1 = Math.sin(a1);
						if (a1 < 0)
							System.out.println("a1 negative");
						// System.out.println( "a1: " + a1 );
						if (Math.abs(a1) > 1e-6)
							t = value / (2.0 * a1);
						else
							t = vertices[edges[ned].vertex].r.minus(
									vertices[i].r).length();
						a1 = vertices[edges[ned].vertex].r.minus(vertices[i].r)
								.length();
						if (t > a1)
							t = a1;
						p1 = vertices[i].r.plus(p1.times(t));

						p2 = vertices[edges[edges[ed].hedge].vertex].r
								.minus(vertices[i].r);
						p2.normalize();
						a2 = Math.acos(edgeNormals[edges[ned].hedge]
								.times(-1.0).dot(p2));
						a2 = Math.sin(a2);
						if (a2 < 0)
							System.out.println("a2 negative");
						// System.out.println( "a2: " + a2 );
						if (Math.abs(a2) > 1e-6)
							t = value / (2.0 * a2);
						else
							t = vertices[edges[edges[ed].hedge].vertex].r
									.minus(vertices[i].r).length();
						a2 = vertices[edges[edges[ed].hedge].vertex].r.minus(
								vertices[i].r).length();
						if (t > a2)
							t = a2;
						p2 = vertices[i].r.plus(p2.times(t));

						p3 = vertices[i].r;
						n1 = new Vec3(edgeNormals[ed]);
						n2 = new Vec3(edgeNormals[edges[ned].hedge]);
						n3 = vertices[edges[ned].vertex].r
								.minus(vertices[i].r)
								.cross(
										vertices[edges[edges[ed].hedge].vertex].r
												.minus(vertices[i].r));
						if (n3.length() > 1e-6)
							n3.normalize();
						else if (edges[ed].face != -1)
							n3 = faceNormals[edges[ed].face];
						else
							n3 = faceNormals[edges[e[j]].face];

						// System.out.println( "dot product: " + Math.abs(
						// n1.dot( n2.cross( n3 ) ) ) );

						if (Math.abs(n1.dot(n2.cross(n3))) < 1e-4) {
							if (edges[ed].face != -1)
								n1 = faceNormals[edges[ed].face];
							else
								n1 = faceNormals[edges[e[j]].face];
							p1 = vertices[edges[ned].vertex].r
									.minus(vertices[i].r);
							p1.normalize();
							p2 = vertices[i].r
									.minus(vertices[edges[e[j]].vertex].r);
							p2.normalize();
							p1.add(p2);
							p1.normalize();
							p1 = n1.cross(p1);
							p1.normalize();
							a1 = Math.acos(edgeNormals[ed].times(-1.0).dot(p1));
							a1 = Math.sin(a1);
							if (a1 < 0)
								System.out.println("a1 negative");
							// System.out.println( "a1: " + a1 );
							if (Math.abs(a1) > 1e-6)
								t = value / (2.0 * a1);
							else
								t = vertices[edges[ned].vertex].r.minus(
										vertices[i].r).length();
							a1 = vertices[edges[ned].vertex].r.minus(
									vertices[i].r).length();
							if (t > a1)
								t = a1;
							cuts[ed] = vertices[i].r.plus(p1.times(t));

						} else {
							t1 = n2.cross(n3).times(p1.dot(n1));
							t2 = n3.cross(n1).times(p2.dot(n2));
							t3 = n1.cross(n2).times(p3.dot(n3));
							cuts[ed] = t1.plus(t2).plus(t3).times(
									1.0 / n1.dot(n2.cross(n3)));
						}
					} else {
						// intersection of ed bevel with ned.
						if (edges[ed].face != -1)
							n1 = faceNormals[edges[ed].face];
						else
							n1 = faceNormals[edges[e[j]].face];
						p1 = vertices[edges[ned].vertex].r.minus(vertices[i].r);
						p1.normalize();
						p2 = vertices[i].r
								.minus(vertices[edges[e[j]].vertex].r);
						p2.normalize();
						p1.add(p2);
						p1.normalize();
						p1 = n1.cross(p1);
						p1.normalize();
						a1 = Math.acos(edgeNormals[ed].times(-1.0).dot(p1));
						a1 = Math.sin(a1);
						if (a1 < 0)
							System.out.println("a1 negative");
						// System.out.println( "a1: " + a1 );
						if (Math.abs(a1) > 1e-6)
							t = value / (2.0 * a1);
						else
							t = vertices[edges[ned].vertex].r.minus(
									vertices[i].r).length();
						a1 = vertices[edges[ned].vertex].r.minus(vertices[i].r)
								.length();
						if (t > a1)
							t = a1;
						cuts[ed] = vertices[i].r.plus(p1.times(t));

					}

					// p1.subtract( n1.times( a1 ) );
					// p2.subtract( n2.times( a2 ) );

					cutsQuality[ed] = TWO_BEVEL;
					if (vertAdded == -1)
						cutsTable[ed] = i;
					else
						cutsTable[ed] = vertAdded + vertCount + vertices.length;
					++vertAdded;
					// System.out.println( "vert count inc TWO_BEVEL" );

				} else if (fullSel[ed]) {

					if ((e.length == 2) || (prod < 1e-4)) {
						if (edges[ed].face != -1)
							n1 = faceNormals[edges[ed].face];
						else
							n1 = faceNormals[edges[e[j]].face];
						p1 = vertices[edges[ned].vertex].r.minus(vertices[i].r);
						p1.normalize();
						p2 = vertices[i].r
								.minus(vertices[edges[e[j]].vertex].r);
						p2.normalize();
						p1.add(p2);
						p1.normalize();
						p1 = n1.cross(p1);
						p1.normalize();
						a1 = Math.acos(edgeNormals[ed].times(-1.0).dot(p1));
						a1 = Math.sin(a1);
						if (a1 < 0)
							System.out.println("a1 negative");
						// System.out.println( "a1: " + a1 );
						if (Math.abs(a1) > 1e-6)
							t = value / (2.0 * a1);
						else
							t = vertices[edges[ned].vertex].r.minus(
									vertices[i].r).length();
						a1 = vertices[edges[ned].vertex].r.minus(vertices[i].r)
								.length();
						if (t > a1)
							t = a1;
						cuts[ed] = vertices[i].r.plus(p1.times(t));
						cutsQuality[ed] = ONE_BEVEL;
						if (vertAdded == -1)
							cutsTable[ed] = i;
						else
							cutsTable[ed] = vertAdded + vertCount
									+ vertices.length;
						++vertAdded;
						if (e.length == 2) {
							p1 = vertices[edges[ned].vertex].r
									.minus(vertices[i].r);
							if (p1.length() < 1e-4)
								cuts[ed + edges.length] = vertices[i].r;
							else {
								t = value / 2;
								if (t > p1.length())
									t = p1.length();
								cuts[ed + edges.length] = vertices[i].r.plus(p1
										.times(t));
							}
							cutsTable[ed + edges.length] = vertAdded
									+ vertCount + vertices.length;
							++vertAdded;
						}
					} else {
						// intersection of ed bevel with ned.
						p1 = vertices[edges[ned].vertex].r.minus(vertices[i].r);
						p1.normalize();
						a1 = Math.acos(edgeNormals[ed].times(-1.0).dot(p1));
						a1 = Math.sin(a1);
						if (a1 < 0)
							System.out.println("a1 negative");
						// System.out.println( "a1: " + a1 );
						if (Math.abs(a1) > 1e-6)
							t = value / (2.0 * a1);
						else
							t = vertices[edges[ned].vertex].r.minus(
									vertices[i].r).length();
						a1 = vertices[edges[ned].vertex].r.minus(vertices[i].r)
								.length();
						if (t > a1)
							t = a1;
						cuts[ed] = vertices[i].r.plus(p1.times(t));

						cutsQuality[ed] = ONE_BEVEL;
						if (vertAdded == -1)
							cutsTable[ed] = i;
						else
							cutsTable[ed] = vertAdded + vertCount
									+ vertices.length;
						++vertAdded;
						// System.out.println( "vert count inc ONE_BEVEL" );
						if (j == e.length - 1)
							if (cutsQuality[edges[ned].hedge] == ONE_BEVEL_NEXT
									&& e.length > 2) {
								cuts[ed] = cuts[ed]
										.plus(cuts[edges[ned].hedge])
										.times(0.5);
								cuts[edges[ned].hedge] = cuts[ed];
								cutsTable[ed] = cutsTable[edges[ned].hedge];
								--vertAdded;
								// System.out.println( "vertex MERGED ONE_BEVEL"
								// );
							}
					}
				} else if (fullSel[ned]) {

					if ((e.length == 2) || (prod < 1e-4)) {
						if (edges[ed].face != -1)
							n1 = faceNormals[edges[ed].face];
						else
							n1 = faceNormals[edges[e[j]].face];
						p1 = vertices[edges[ned].vertex].r.minus(vertices[i].r);
						p1.normalize();
						p2 = vertices[i].r
								.minus(vertices[edges[e[j]].vertex].r);
						p2.normalize();
						p1.add(p2);
						p1.normalize();
						p1 = n1.cross(p1);
						p1.normalize();
						a1 = Math.acos(edgeNormals[ed].times(-1.0).dot(p1));
						a1 = Math.sin(a1);
						if (a1 < 0)
							System.out.println("a1 negative");
						System.out.println("a1: " + a1);
						if (Math.abs(a1) > 1e-6)
							t = value / (2.0 * a1);
						else
							t = vertices[edges[ned].vertex].r.minus(
									vertices[i].r).length();
						a1 = vertices[edges[ned].vertex].r.minus(vertices[i].r)
								.length();
						if (t > a1)
							t = a1;
						cuts[ed] = vertices[i].r.plus(p1.times(t));
					} else {
						// intersection of ed bevel with ned.
						p2 = vertices[edges[edges[ed].hedge].vertex].r
								.minus(vertices[i].r);
						p2.normalize();
						a2 = Math.acos(edgeNormals[edges[ned].hedge]
								.times(-1.0).dot(p2));
						a2 = Math.sin(a2);
						if (a2 < 0)
							System.out.println("a2 negative");
						if (Math.abs(a2) > 1e-6)
							t = value / (2.0 * a2);
						else
							t = vertices[edges[edges[ed].hedge].vertex].r
									.minus(vertices[i].r).length();
						a2 = vertices[edges[edges[ed].hedge].vertex].r.minus(
								vertices[i].r).length();
						if (t > a2)
							t = a2;
						cuts[ed] = vertices[i].r.plus(p2.times(t));
					}
					cutsQuality[ed] = ONE_BEVEL_NEXT;
					if (vertAdded == -1)
						cutsTable[ed] = i;
					else
						cutsTable[ed] = vertAdded + vertCount + vertices.length;
					++vertAdded;
					// System.out.println( "vert count inc ONE_BEVEL_NEXT"
					// );
					if (j != 0) {
						if (cutsQuality[ped] == ONE_BEVEL && e.length > 2) {
							cuts[ed] = cuts[ed].plus(cuts[ped]).times(0.5);
							cuts[ped] = cuts[ed];
							cutsTable[ed] = cutsTable[ped];
							--vertAdded;
							// System.out.println( "vertex MERGED
							// ONE_BEVEL_NEXT" );
						}
					}
				} else if (!fullSel[ped]) {
					// intersection of vertex bevel with ed.
					cuts[ed] = null;
					cutsQuality[ed] = VERTEX_BEVEL;
					if (vertAdded == -1)
						cutsTable[ed] = i;
					else
						cutsTable[ed] = vertAdded + vertCount + vertices.length;
					++vertAdded;
					// System.out.println( "vert count inc VERTEX_BEVEL" );
				}
			}
			p3 = new Vec3();
			int k = 0;
			for (int j = 0; j < e.length; ++j) {
				// System.out.println( e[j] + " :1/2: " + edges[e[j]].hedge );
				ed = edges[e[j]].hedge;
				if ((cutsTable[ed] != -1) && (cuts[ed] != null)) {
					p3.add(cuts[ed]);
					++k;
				}
			}
			p3.scale(1.0 / k);
			for (int j = 0; j < e.length; ++j) {
				ed = edges[e[j]].hedge;
				if ((cutsTable[ed] != -1) && (cuts[ed] == null)) {
					if (p3.distance(vertices[i].r) < 1e-6) {
						System.out.println("#1");
						p1 = vertices[edges[e[j]].vertex].r
								.minus(vertices[i].r);
						if (p1.length() < 1e-4)
							cuts[ed] = vertices[i].r;
						else {
							p1.normalize();
							t = value / 2;
							if (t > p1.length())
								t = p1.length();
							p1.normalize();
							cuts[ed] = vertices[i].r.plus(p1.times(t));
						}
					} else {
						// intersection of vertex bevel with ed.
						// System.out.println( "intersection of vertex bevel
						// with ed" );
						n3 = normals[i];
						d = -p3.dot(n3);
						n1 = vertices[edges[edges[ed].hedge].vertex].r
								.minus(vertices[i].r);
						t = Math.abs(-(n3.dot(vertices[i].r) + d)
								/ (n3.dot(n1)));
						if (t * n1.length() <= value) {
							// t = -t;
							// p1 = vertices[edges[e[j]].vertex].r.minus(
							// vertices[i].r );
							if (n1.length() < 1e-4)
								cuts[ed] = vertices[i].r;
							else {
								// t = value / 2;
								if (t > 1)
									t = 1;
								// n1.normalize();
								cuts[ed] = vertices[i].r.plus(n1.times(t));
							}
						} else {
							if (t > 1)
								t = 1;
							cuts[ed] = vertices[i].r.plus(n1.times(t));
						}
					}
				}
			}
			if (vertAdded > 1)
				++faceCount;
			vertCount += vertAdded;
		}

		// System.out.println( "vertCount: " + vertCount );
		// System.out.println( "edgeCount: " + edgeCount );
		// System.out.println( "faceCount: " + faceCount );

		/*
		 * for ( int i = 0; i < edges.length; ++i ) { System.out.println( "cuts
		 * table " + i + " : " + cutsTable[i] + " / " + cutsTable[i +
		 * edges.length] ); System.out.println( "cuts " + i + " : " + cuts[i] ); }
		 */
		Wvertex newVertices[] = new Wvertex[vertices.length + vertCount];
		Wedge newEdges[] = new Wedge[edges.length + edgeCount * 6 + 4
				* vertCount];
		newSel = new boolean[newEdges.length];
		for (int i = 0; i < selected.length; ++i)
			newSel[i] = selected[i];
		Wface newFaces[] = new Wface[faces.length + faceCount + edgeCount];
		int[] vertParmTable = new int[newVertices.length];
		int[] faceParmTable = new int[newFaces.length];
		faceCount = faces.length;
		edgeCount = edges.length / 2;
		// System.out.println( edges.length + " " + newEdges.length + " " +
		// edgeCount );
		translateMesh(newVertices, newEdges, newFaces);
		Wedge[] te = edges;
		Wvertex[] tv = vertices;
		Wface[] tf = faces;
		int count;
		int next;
		int pred;
		int vl = vertices.length;
		int el = edges.length;
		int[] ee;
		int n;
		boolean cut;
		boolean first;
		for (int i = 0; i < vl; ++i) {
			vertParmTable[i] = i;
			e = getVertexEdges(vertices[i]);
			cut = false;
			for (int j = 0; j < e.length; ++j) {
				// System.out.println( e[j] + " :1/2: " + edges[e[j]].hedge );
				e[j] = edges[e[j]].hedge;
				if (fullSel[e[j]])
					cut = true;
			}
			if (!cut)
				continue;
			// System.out.println( "Face creation for: " + i + " " +
			// e.length );
			edges = newEdges;
			vertices = newVertices;
			faces = newFaces;
			ee = getVertexEdges(newVertices[i]);
			for (int j = 0; j < e.length; ++j)
				ee[j] = edges[ee[j]].hedge;
			count = 0;
			for (int j = 0; j < e.length; ++j) {
				next = j + 1;
				if (next == e.length)
					next = 0;
				if (cutsTable[e[j] + el] != -1)
					++count;
				if ((cutsTable[e[next]] != cutsTable[e[j]])
						&& (cutsTable[e[j]] != -1))
					++count;
			}
			if (count > 2 && e.length < 3) {
				if (fullSel[e[0]])
					n = 0;
				else
					n = 1;
				ed = e[n];
				ned = te[ed].next;
				// System.out.println( ed + " " + ned );
				if (cutsTable[ed] < vl)
					newVertices[cutsTable[ed]].r = cuts[ed];
				else if (newVertices[cutsTable[ed]] == null) {
					newVertices[cutsTable[ed]] = new Wvertex(cuts[ed], -1);
					vertParmTable[cutsTable[ed]] = i;
					// newVertices[cutsTable[ed]].smoothness =
					// vertices[i].smoothness;
				}
				newVertices[cutsTable[ed]].edge = edgeCount;
				newEdges[ee[n]].vertex = cutsTable[ed];
				newVertices[cutsTable[ed]].edge = newEdges[ee[n]].hedge;
				newVertices[cutsTable[ed]].edge = newEdges[ee[n]].hedge;
				newVertices[cutsTable[ed + el]] = new Wvertex(cuts[ed + el],
						edgeCount + 2);
				vertParmTable[cutsTable[ed + el]] = i;
				// newVertices[cutsTable[ed + el]].smoothness =
				// vertices[i].smoothness;
				newEdges[newEdges[newEdges[ee[n]].next].hedge].vertex = cutsTable[ed
						+ el];
				newEdges[newEdges[newEdges[ee[n]].next].hedge].next = edgeCount + 2;
				if (cutsTable[te[ned].hedge] < vl)
					newVertices[cutsTable[te[ned].hedge]].r = cuts[te[ned].hedge];
				else if (newVertices[cutsTable[te[ned].hedge]] == null) {
					newVertices[cutsTable[te[ned].hedge]] = new Wvertex(
							cuts[te[ned].hedge], -1);
					vertParmTable[cutsTable[te[ned].hedge]] = i;
					// newVertices[cutsTable[te[ned].hedge]].smoothness =
					// vertices[i].smoothness;
				}
				newVertices[cutsTable[te[ned].hedge]].edge = edgeCount + 1;
				newEdges[edgeCount] = new Wedge(cutsTable[ed + el], edgeCount
						+ newEdges.length / 2, newEdges[ee[n]].face,
						newEdges[ee[n]].next);
				newEdges[ee[n]].next = edgeCount;
				newEdges[edgeCount].smoothness = newEdges[ee[n]].smoothness;
				newEdges[edgeCount + newEdges.length / 2] = new Wedge(
						cutsTable[ed], edgeCount, faceCount, edgeCount + 1
								+ newEdges.length / 2);
				newEdges[edgeCount + newEdges.length / 2].smoothness = newEdges[ee[n]].smoothness;
				newEdges[edgeCount + 1] = new Wedge(cutsTable[ed], edgeCount
						+ 1 + newEdges.length / 2,
						newEdges[newEdges[ee[n]].hedge].face,
						newEdges[ee[n]].hedge);
				newEdges[edgeCount + 1].smoothness = newEdges[ee[n]].smoothness;
				newEdges[edgeCount + 1 + newEdges.length / 2] = new Wedge(
						cutsTable[te[ned].hedge], edgeCount + 1, faceCount,
						edgeCount + 2 + newEdges.length / 2);
				newEdges[edgeCount + 1 + newEdges.length / 2].smoothness = newEdges[ee[n]].smoothness;
				newEdges[edgeCount + 2] = new Wedge(cutsTable[te[ned].hedge],
						edgeCount + 2 + newEdges.length / 2,
						newEdges[newEdges[ee[n]].hedge].face, edgeCount + 1);
				newEdges[edgeCount + 2].smoothness = newEdges[ee[n]].smoothness;
				newEdges[edgeCount + 2 + newEdges.length / 2] = new Wedge(
						cutsTable[ed + el], edgeCount + 2, faceCount, edgeCount
								+ newEdges.length / 2);
				newEdges[edgeCount + 2 + newEdges.length / 2].smoothness = newEdges[ee[n]].smoothness;
				newFaces[faceCount] = new Wface(edgeCount + newEdges.length / 2);
				faceParmTable[faceCount] = te[e[n]].face;
				if (faceParmTable[faceCount] == -1)
					faceParmTable[faceCount] = te[te[e[n]].hedge].face;
				newSel[edgeCount] = true;
				newSel[edgeCount + 1] = true;
				newSel[edgeCount + 2] = true;
				edgeCount += 3;
				++faceCount;
			} else if (count > 2) {
				first = true;
				for (int j = 0; j < e.length; ++j) {
					next = j + 1;
					if (next == e.length)
						next = 0;
					if (cutsTable[e[next]] == -1) {
						if (cutsTable[e[j]] < vl)
							newVertices[cutsTable[e[j]]].r = cuts[e[j]];
						else if (newVertices[cutsTable[e[j]]] == null) {
							newVertices[cutsTable[e[j]]] = new Wvertex(
									cuts[e[j]], -1);
							vertParmTable[cutsTable[e[j]]] = i;
							// newVertices[cutsTable[e[j]]].smoothness =
							// vertices[i].smoothness;
						}
						newEdges[ee[j]].vertex = cutsTable[e[j]];
						newVertices[cutsTable[e[j]]].edge = newEdges[ee[j]].hedge;
						continue;
					}
					if (cutsTable[e[next]] == cutsTable[e[j]]) {
						newEdges[ee[j]].vertex = cutsTable[e[j]];
						continue;
					}
					if (cutsTable[e[j]] != -1) {
						if (cutsTable[e[j]] < vl)
							newVertices[cutsTable[e[j]]].r = cuts[e[j]];
						else if (newVertices[cutsTable[e[j]]] == null) {
							newVertices[cutsTable[e[j]]] = new Wvertex(
									cuts[e[j]], -1);
							vertParmTable[cutsTable[e[j]]] = i;
							// newVertices[cutsTable[e[j]]].smoothness =
							// vertices[i].smoothness;
						}
						newEdges[ee[j]].vertex = cutsTable[e[j]];
						newVertices[cutsTable[e[j]]].edge = newEdges[ee[j]].hedge;
					} else {
						pred = j - 1;
						if (pred < 0)
							pred = e.length - 1;
						newEdges[ee[j]].vertex = cutsTable[e[pred]];
					}
					newEdges[ee[j]].next = edgeCount;
					newEdges[edgeCount] = new Wedge(cutsTable[e[next]],
							edgeCount + newEdges.length / 2,
							newEdges[newEdges[ee[next]].hedge].face,
							newEdges[ee[next]].hedge);
					if (newEdges[newEdges[ee[next]].hedge].face != -1)
						newEdges[edgeCount].smoothness = 1.0f;
					newSel[edgeCount] = true;
					if (first) {
						newEdges[edgeCount + newEdges.length / 2] = new Wedge(
								newEdges[ee[j]].vertex, edgeCount, faceCount,
								edgeCount + count - 1 + newEdges.length / 2);
						// System.out.println( ( edgeCount + newEdges.length / 2
						// ) + " :next: " + ( edgeCount + count - 1 +
						// newEdges.length / 2 ) );
						first = false;
					} else
						newEdges[edgeCount + newEdges.length / 2] = new Wedge(
								newEdges[ee[j]].vertex, edgeCount, faceCount,
								edgeCount - 1 + newEdges.length / 2);
					newEdges[edgeCount + newEdges.length / 2].smoothness = newEdges[edgeCount].smoothness;
					++edgeCount;
					// System.out.println( j + " / " + e.length + " ec: " +
					// edgeCount );
				}
				newFaces[faceCount] = new Wface(edgeCount - 1 + newEdges.length
						/ 2);
				faceParmTable[faceCount] = te[e[0]].face;
				if (faceParmTable[faceCount] == -1)
					faceParmTable[faceCount] = te[te[e[0]].hedge].face;
				++faceCount;
			}
			edges = te;
			vertices = tv;
			faces = tf;
		}
		edges = newEdges;
		vertices = newVertices;
		faces = newFaces;
		// System.out.println( "Face terminÃ©e: " + edgeCount + " / " +
		// newEdges.length / 2 );
		// dumpMesh();
		edges = te;
		vertices = tv;
		faces = tf;
		int ed1;
		int ned1;
		int ed2;
		int ned2;
		int hed;
		int nhed;
		int ref1;
		int ref2;
		int nn1 = -1;
		int nn2;
		int ec;
		int ec1;
		int ec2;
		int hec;
		int hec1;
		int hec2;
		int ved[];
		for (int i = 0; i < edges.length / 2; ++i) {
			// System.out.println( "i :" + i );
			if (selected[i]) {

				// System.out.println( "i :" + i );
				hed = edges[i].hedge;
				nhed = newEdges[i].hedge;
				if (cutsTable[i] < vertices.length)
					newVertices[cutsTable[i]].r = cuts[i];
				else if (newVertices[cutsTable[i]] == null) {
					newVertices[cutsTable[i]] = new Wvertex(cuts[i], -1);
					vertParmTable[cutsTable[i]] = edges[i].vertex;
					// newVertices[cutsTable[i]].smoothness =
					// vertices[edges[i].vertex].smoothness;
				}
				if (cutsTable[hed] < vertices.length)
					newVertices[cutsTable[hed]].r = cuts[hed];
				else if (newVertices[cutsTable[hed]] == null) {
					newVertices[cutsTable[hed]] = new Wvertex(cuts[hed], -1);
					vertParmTable[cutsTable[hed]] = edges[edges[i].hedge].vertex;
					// newVertices[cutsTable[hed]].smoothness =
					// vertices[edges[edges[i].hedge].vertex].smoothness;
				}
				ed1 = edges[edges[i].next].hedge;
				while (edges[ed1].next != hed)
					ed1 = edges[edges[ed1].next].hedge;
				ned1 = newEdges[newEdges[i].next].hedge;
				while (newEdges[ned1].next != nhed)
					ned1 = newEdges[newEdges[ned1].next].hedge;
				if (cutsTable[ed1] < vertices.length)
					newVertices[cutsTable[ed1]].r = cuts[ed1];
				else if (newVertices[cutsTable[ed1]] == null) {
					newVertices[cutsTable[ed1]] = new Wvertex(cuts[ed1], -1);
					vertParmTable[cutsTable[ed1]] = edges[i].vertex;
					// newVertices[cutsTable[ed1]].smoothness =
					// vertices[edges[i].vertex].smoothness;
				}

				ed2 = edges[edges[hed].next].hedge;
				while (edges[ed2].next != i)
					ed2 = edges[edges[ed2].next].hedge;
				ned2 = newEdges[newEdges[nhed].next].hedge;
				while (newEdges[ned2].next != i)
					ned2 = newEdges[newEdges[ned2].next].hedge;
				if (cutsTable[ed2] < vertices.length)
					newVertices[cutsTable[ed2]].r = cuts[ed2];
				else if (newVertices[cutsTable[ed2]] == null) {
					newVertices[cutsTable[ed2]] = new Wvertex(cuts[ed2], -1);
					vertParmTable[cutsTable[ed2]] = edges[edges[i].hedge].vertex;
					// newVertices[cutsTable[ed2]].smoothness =
					// vertices[edges[edges[i].hedge].vertex].smoothness;
				}
				// System.out.println( i + " " + ed1 + " " + hed + " " + ed2 );
				edges = newEdges;
				vertices = newVertices;
				faces = newFaces;
				ref1 = findEdge(cutsTable[ed1], cutsTable[i]);
				ref2 = findEdge(cutsTable[ed2], cutsTable[hed]);
				if (ref1 != -1) {
					if (newEdges[i].next == ref1) {
						// swap
						// System.out.println( "*REF1 SWAP*" );
						// dumpMesh();
						nn1 = i;
						while (newEdges[nn1].next != newEdges[i].hedge)
							nn1 = newEdges[newEdges[nn1].next].hedge;
						// System.out.println( "nn1 done" );
						newVertices[newEdges[newEdges[ref1].hedge].vertex].edge = ref1;
						newEdges[i].vertex = cutsTable[i];
						newEdges[i].next = newEdges[ref1].next;
						newEdges[nn1].next = ref1;
						newFaces[newEdges[i].face].edge = i;
						newEdges[ref1].face = newEdges[newEdges[i].hedge].face;
						newEdges[ref1].next = newEdges[i].hedge;
						// System.out.println( "*REF1 SWAP DONE*" );
						// dumpMesh();
					}
				} else {
					ved = getVertexEdges(vertices[newEdges[i].vertex]);
					for (int j = 0; j < ved.length; ++j)
						newEdges[newEdges[ved[j]].hedge].vertex = cutsTable[i];
					nn1 = ved[ved.length - 1];
				}

				if (ref2 != -1) {
					if (newEdges[newEdges[i].hedge].next != ref2) {
						// swap
						// System.out.println( "*REF2 SWAP*" );
						newEdges[newEdges[i].hedge].vertex = cutsTable[ed2];
						nn2 = newEdges[ref2].hedge;
						while (newEdges[nn2].next != ref2)
							nn2 = newEdges[newEdges[nn2].next].hedge;
						// System.out.println( "nn2 done" );
						newEdges[nn2].next = i;
						newEdges[ref2].next = newEdges[newEdges[i].hedge].next;
						newEdges[newEdges[i].hedge].next = ref2;
						newEdges[ref2].face = newEdges[newEdges[i].hedge].face;
						newFaces[newEdges[i].face].edge = i;
						newVertices[newEdges[ref2].vertex].edge = newEdges[ref2].hedge;
					}
				} else {
					ved = getVertexEdges(vertices[newEdges[nhed].vertex]);
					for (int j = 0; j < ved.length; ++j)
						newEdges[newEdges[ved[j]].hedge].vertex = cutsTable[ed2];
					nn2 = ved[ved.length - 1];
					newVertices[cutsTable[ed2]].edge = nn2;
				}
				if (ref1 == -1) {
					newVertices[cutsTable[i]].edge = nn1;
					nn1 = i;
					// System.out.println( "nn1 2" );
					while (newEdges[nn1].next != newEdges[ned1].hedge)
						nn1 = newEdges[newEdges[nn1].next].hedge;
					// System.out.println( "nn1 2 done" );

				}
				edges = te;
				vertices = tv;
				faces = tf;

				ed = newEdges[nhed].next;
				if (ref2 != -1) {
					// System.out.println( "found ref2" );
					ec = ref2;
					hec = newEdges[ec].hedge;
					ed = newEdges[ec].next;
					--edgeCount;
				} else {
					ec = edgeCount;
					hec = edgeCount + newEdges.length / 2;
				}
				ec1 = edgeCount + 1;
				hec1 = edgeCount + 1 + newEdges.length / 2;
				if (ref1 != -1) {
					// System.out.println( "found ref1" );
					ec2 = ref1;
					hec2 = newEdges[ref1].hedge;
					--edgeCount;
				} else {
					ec2 = edgeCount + 2;
					hec2 = edgeCount + 2 + newEdges.length / 2;
				}
				if (ref2 == -1) {
					newEdges[ec] = new Wedge(cutsTable[hed], hec, faceCount,
							ec1);
					newEdges[ec].smoothness = newEdges[i].smoothness;
					newSel[ec] = true;
					newEdges[hec] = new Wedge(cutsTable[ed2], ec,
							newEdges[newEdges[ed].hedge].face,
							newEdges[newEdges[ed].hedge].next);
					newEdges[hec].smoothness = newEdges[i].smoothness;
					newEdges[nhed].next = ec;
					if (newEdges[nhed].face != -1)
						newFaces[newEdges[nhed].face].edge = hec1;
					newEdges[nhed].face = faceCount;
					newEdges[newEdges[ed].hedge].next = hec;
					newEdges[newEdges[ed].hedge].vertex = cutsTable[hed];

					newVertices[cutsTable[hed]].edge = ed;
					newVertices[cutsTable[ed2]].edge = i;

				} else {
					newEdges[ec].next = ec1;
					newEdges[ec].face = faceCount;
					if (newEdges[nhed].face != -1)
						newFaces[newEdges[nhed].face].edge = hec1;
					newEdges[nhed].face = faceCount;
				}
				if (ref1 == -1) {
					newEdges[ec2] = new Wedge(cutsTable[i], hec2, faceCount,
							nhed);
					newEdges[ec2].smoothness = newEdges[i].smoothness;
					newSel[ec2] = true;
					newEdges[hec2] = new Wedge(cutsTable[ed1], ec2,
							newEdges[newEdges[ned1].hedge].face,
							newEdges[ned1].hedge);
					newEdges[hec2].smoothness = newEdges[i].smoothness;
					newEdges[ned1].vertex = cutsTable[ed1];
					newEdges[ned1].next = hec1;
					newEdges[nn1].next = hec2;

					newVertices[cutsTable[ed1]].edge = newEdges[ned1].hedge;
					newVertices[cutsTable[i]].edge = newEdges[i].next;
				} else {
					nn1 = newEdges[ref1].hedge;
					while (newEdges[nn1].next != ref1)
						nn1 = newEdges[newEdges[nn1].next].hedge;
					newEdges[nn1].next = hec1;
					newEdges[ec2].face = faceCount;
				}
				newEdges[ec1] = new Wedge(cutsTable[ed1], hec1, faceCount, ec2);
				newEdges[ec1].smoothness = newEdges[i].smoothness;
				newEdges[hec1] = new Wedge(cutsTable[hed], ec1,
						newEdges[ed].face, ed);
				newEdges[hec1].smoothness = newEdges[i].smoothness;
				newSel[ec1] = true;
				newFaces[faceCount] = new Wface(nhed);
				faceParmTable[faceCount] = edges[i].face;
				edgeCount += 3;
				++faceCount;
				te = edges;
				tv = vertices;
				tf = faces;
				edges = newEdges;
				vertices = newVertices;
				faces = newFaces;
				// dumpMesh();
				edges = te;
				vertices = tv;
				faces = tf;
			}
		}
		// edges reduction
		edgeCount = 0;
		int[] edgeTable = new int[newEdges.length];
		for (int i = 0; i < newEdges.length; ++i) {
			if (newEdges[i] != null)
				edgeTable[i] = edgeCount++;
			else
				edgeTable[i] = -1;
		}
		boolean[] sel;
		if (edgeCount < newEdges.length) {
			edges = new Wedge[edgeCount];
			sel = new boolean[edgeCount];
			for (int i = 0; i < newEdges.length; ++i) {
				if (edgeTable[i] != -1) {
					edges[edgeTable[i]] = newEdges[i];
					sel[edgeTable[i]] = newSel[i];
					edges[edgeTable[i]].hedge = edgeTable[newEdges[i].hedge];
					edges[edgeTable[i]].next = edgeTable[newEdges[i].next];
				}
			}
			for (int i = 0; i < newVertices.length; ++i)
				newVertices[i].edge = edgeTable[newVertices[i].edge];
			for (int i = 0; i < newFaces.length; ++i)
				newFaces[i].edge = edgeTable[newFaces[i].edge];
			newSel = sel;
		} else
			edges = newEdges;
		vertices = newVertices;
		faces = newFaces;
		// Update the texture parameters.
		TextureParameter param[] = getParameters();
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[faces.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++) {
						if (faceParmTable[j] != -1)
							newval[j] = oldval[faceParmTable[j]];
						else
							newval[j] = param[k].defaultVal;
					}
					newParamVal[k] = new FaceParameterValue(newval);
				} else if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[vertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = oldval[vertParmTable[j]];
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		// System.out.println( checkMesh() );
		// dumpMesh();
		removeZeroLengthEdges();
		if (applyEdgeLengthLimit)
			removeSmallEdgeLengths();
		resetMesh();
		sel = new boolean[newSel.length / 2];
		for (int i = 0; i < sel.length; i++)
			sel[i] = newSel[i] | newSel[edges[i].hedge];
		return sel;
	}

	/**
	 * A utility method to find an edge between two vertices
	 * 
	 * @param fromVert
	 *            First edge vertex
	 * @param toVert
	 *            Second edge vertex
	 * @return The edge number if found, -1 otherwise.
	 */

	private int findEdge(int fromVert, int toVert) {
		// System.out.println( "find edge: " + fromVert + " " + toVert );
		int i = vertices[fromVert].edge;
		if (i == -1)
			return -1;
		while (true) {
			if (edges[i].vertex == toVert)
				return i;
			i = edges[edges[i].hedge].next;
			if (i == vertices[fromVert].edge)
				return -1;
		}
	}

	/**
	 * A utility method to find an edge between two vertices
	 * 
	 * @param fromVert
	 *            First edge vertex
	 * @param toVert
	 *            Second edge vertex
	 * @param newVertices
	 *            Description of the Parameter
	 * @param newEdges
	 *            Description of the Parameter
	 * @return The edge number if found, -1 otherwise.
	 */

	private int findNewEdge(int fromVert, int toVert, Wvertex[] newVertices,
			Wedge[] newEdges) {
		// System.out.println( "find edge: " + fromVert + " " + toVert );
		int i = newVertices[fromVert].edge;
		if (i == -1)
			return -1;
		while (true) {
			if (newEdges[i].vertex == toVert)
				return i;
			i = newEdges[newEdges[i].hedge].next;
			if (i == newVertices[fromVert].edge)
				return -1;
		}
	}

	/**
	 * Removes edges with a zero length (vertex reduction)
	 */
	public void removeZeroLengthEdges() {

		boolean reduction = false;
		for (int i = 0; i < edges.length / 2; ++i) {
			if (vertices[edges[i].vertex].r
					.distance(vertices[edges[edges[i].hedge].vertex].r) < 1e-6) {
				reduction = true;
				break;
			}
		}
		if (reduction) {
			// System.out.println( "Avant reduction: " + edges.length );
			boolean[] redSel = new boolean[edges.length / 2];
			for (int i = 0; i < edges.length / 2; ++i) {
				if (vertices[edges[i].vertex].r
						.distance(vertices[edges[edges[i].hedge].vertex].r) < 1e-6)
					redSel[i] = true;
			}
			collapseEdges(redSel);
			// System.out.println( "Apres reduction: " + edges.length );
		}
	}

	/**
	 * Removes small area faces if needed
	 */
	private void removeSmallEdgeLengths() {
		/*
		 * int[] vf; double area; boolean[] sel = new boolean[edges.length];
		 * boolean remove = false; double elimit = Math.sqrt( edgeLengthLimit );
		 * 
		 * for ( int i = 0; i < faces.length; ++i ) { vf = getFaceVertices(
		 * faces[i] ); area = triangulate( vf, 0, true ); System.out.println(
		 * area ); if ( area < edgeLengthLimit ) { int[] fe = getFaceEdges(
		 * faces[i] ); for (int j = 0; j < fe.length; j++) { Vec3 v =
		 * vertices[edges[fe[j]].vertex].r.minus(
		 * vertices[edges[edges[fe[j]].hedge].vertex].r ); if ( v.length() <
		 * elimit ) {
		 * 
		 * sel[fe[j]] = sel[edges[fe[j]].hedge] = true; remove = true; } } } }
		 */
		boolean[] sel = new boolean[edges.length];
		boolean remove = false;
		for (int i = 0; i < edges.length; ++i) {

			Vec3 v = vertices[edges[i].vertex].r
					.minus(vertices[edges[edges[i].hedge].vertex].r);
			if (v.length() < edgeLengthLimit) {

				sel[i] = sel[edges[i].hedge] = true;
				remove = true;
			}
		}
		if (remove)
			collapseEdges(sel);
	}

	/**
	 * Bevels selected vertices
	 * 
	 * @param selected
	 *            Vertices selected for bevel
	 * @param value
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 */
	public boolean[] bevelVertices(boolean[] selected, double value) {
		if (value < 1e-6)
			return selected;
		Vec3[] normals = getNormals();
		int[] e;
		Vec3[] v;
		Vec3 orig;
		Wvertex newVertices[];
		Wedge newEdges[];
		Wface newFaces[];
		boolean newSel[] = new boolean[selected.length];
		boolean tSel[];
		int vertTable[];
		int faceTable = 0;
		double d;
		double t;
		int el;
		int vl;
		int from;
		int to;
		int next;
		for (int i = 0; i < selected.length; ++i)
			newSel[i] = selected[i];
		for (int i = 0; i < selected.length; ++i)
			if (selected[i]) {
				e = getVertexEdges(vertices[i]);
				v = new Vec3[e.length];
				for (int j = 0; j < v.length; ++j)
					v[j] = vertices[edges[e[j]].vertex].r.minus(vertices[i].r);
				orig = new Vec3(vertices[i].r);
				orig.subtract(normals[i].times(value));
				d = -orig.dot(normals[i]);
				for (int j = 0; j < v.length; ++j) {
					if (Math.abs(normals[i].dot(v[j])) < 1e-12) {
						t = value / v[j].length();
						if (t > 1)
							t = 1;
					} else {
						t = -(normals[i].dot(vertices[i].r) + d)
								/ (normals[i].dot(v[j]));
						if (t < 0)
							t = -t;
						if (t > 1)
							t = 1;
					}
					v[j] = vertices[i].r.plus(v[j].times(t));
				}
				el = edges.length;
				vl = vertices.length;
				if (v.length == 2) {
					newVertices = new Wvertex[vertices.length + 1];
					tSel = newSel;
					newSel = new boolean[vertices.length + 1];
					for (int k = 0; k < tSel.length; k++)
						newSel[k] |= tSel[k];
					vertTable = new int[1];
					newEdges = new Wedge[edges.length + 2];
					newFaces = new Wface[faces.length];
					translateMesh(newVertices, newEdges, newFaces);
					vertices = newVertices;
					edges = newEdges;
					faces = newFaces;
					e = getVertexEdges(vertices[i]);
					vertices[i] = new Wvertex(v[0], e[0]);
					newSel[i] = true;
					vertices[vertices.length - 1] = new Wvertex(v[1], e[1]);
					// vertices[vertices.length - 1].smoothness =
					// vertices[i].smoothness;
					newSel[vertices.length - 1] = true;
					vertTable[0] = i;
					edges[el / 2] = new Wedge(i, edges.length - 1,
							edges[e[0]].face, e[0]);
					edges[edges[e[0]].hedge].next = edges.length - 1;
					edges[edges.length - 1] = new Wedge(vertices.length - 1,
							el / 2, edges[e[1]].face, e[1]);
					edges[edges[e[1]].hedge].next = el / 2;
					edges[edges[e[1]].hedge].vertex = vertices.length - 1;
				} else {
					newVertices = new Wvertex[vertices.length - 1 + v.length];
					tSel = newSel;
					newSel = new boolean[vertices.length - 1 + v.length];
					for (int k = 0; k < tSel.length; k++)
						newSel[k] |= tSel[k];
					vertTable = new int[-1 + v.length];
					newEdges = new Wedge[edges.length + 2 * v.length];
					newFaces = new Wface[faces.length + 1];
					translateMesh(newVertices, newEdges, newFaces);
					vertices = newVertices;
					edges = newEdges;
					faces = newFaces;
					e = getVertexEdges(vertices[i]);
					vertices[i].r = v[0];
					newSel[i] = true;
					vertices[i].edge = e[0];
					faceTable = edges[e[0]].face;
					edges[edges[e[0]].hedge].vertex = i;
					edges[edges[e[0]].hedge].next = el / 2 + 1;
					for (int j = 1; j < v.length; ++j) {
						edges[edges[e[j]].hedge].vertex = vl + j - 1;
						if (j < v.length - 1)
							edges[edges[e[j]].hedge].next = el / 2 + j + 1;
						else
							edges[edges[e[j]].hedge].next = el / 2;
						vertices[vl + j - 1] = new Wvertex(v[j], e[j]);
						// vertices[vl + j - 1].smoothness =
						// vertices[i].smoothness;
						newSel[vl + j - 1] = true;
						vertTable[j - 1] = i;
					}
					for (int j = 0; j < v.length; ++j) {
						if (j == 0)
							to = i;
						else
							to = vl + j - 1;
						edges[el / 2 + j] = new Wedge(to, el + v.length + j,
								edges[e[j]].face, e[j]);
						edges[el / 2 + j].smoothness = 1.0f;
						// faces[edges[e[j]].face].edge = el / 2 + j;
						from = j - 1;
						if (from == 0)
							from = i;
						else {
							if (from < 0)
								from = vertices.length - 1;
							else
								from = vl + from - 1;
						}
						if (j == 0)
							next = edges.length - 1;
						else
							next = el + v.length + j - 1;
						edges[el + v.length + j] = new Wedge(from, el / 2 + j,
								faces.length - 1, next);
						edges[el + v.length + j].smoothness = edges[el / 2 + j].smoothness;
					}
					faces[faces.length - 1] = new Wface(edges.length - 1);
				}
				// Update the texture parameters.
				TextureParameter param[] = getParameters();
				ParameterValue oldParamVal[] = getParameterValues();
				if (oldParamVal != null) {
					ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
					for (int k = 0; k < oldParamVal.length; k++) {
						if (oldParamVal[k] instanceof FaceParameterValue) {
							double oldval[] = ((FaceParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[faces.length];
							for (int j = 0; j < oldval.length; j++)
								newval[j] = oldval[j];
							for (int j = oldval.length; j < newval.length; j++)
								newval[j] = oldval[faceTable];
							newParamVal[k] = new FaceParameterValue(newval);
						} else if (oldParamVal[k] instanceof VertexParameterValue) {
							double oldval[] = ((VertexParameterValue) oldParamVal[k])
									.getValue();
							double newval[] = new double[vertices.length];
							for (int j = 0; j < oldval.length; j++)
								newval[j] = oldval[j];
							for (int j = oldval.length; j < newval.length; j++)
								newval[j] = oldval[vertTable[j - oldval.length]];
							newParamVal[k] = new VertexParameterValue(newval);
						} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
							FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
							double newval[][] = new double[newFaces.length][];
							for (int j = 0; j < newFaces.length; ++j) {
								int[] fv = getFaceVertices(j, newEdges,
										newFaces);
								double val[] = new double[fv.length];
								for (int l = 0; l < fv.length; l++) {
									val[l] = fvpv.getAverageValue();
								}
								newval[j] = val;
							}
							newParamVal[k] = new FaceVertexParameterValue(
									newval);
						} else
							newParamVal[k] = oldParamVal[k].duplicate();
					}
					setParameterValues(newParamVal);
				}
			}
		removeZeroLengthEdges();
		resetMesh();
		return newSel;
	}

	/**
	 * Returns a mirrored mesh as specified by mirror state attribute
	 * 
	 * @return The mirroredMesh value
	 */
	public PolyMesh getMirroredMesh() {
		if (mirroredMesh != null) {
			// System.out.println( "non null mirrored mesh : " +
			// invMirroredVerts );
			return mirroredMesh;
		}
		short state = mirrorState;
		mirrorState = NO_MIRROR;
		PolyMesh mesh = (PolyMesh) this.duplicate();
		//dumpMesh();
		mesh.setMirrorState(state);
		mesh.mirrorMesh();
		mirroredVerts = mesh.mirroredVerts;
		mirroredEdges = mesh.mirroredEdges;
		mirroredFaces = mesh.mirroredFaces;
		invMirroredVerts = mesh.invMirroredVerts;
		invMirroredEdges = mesh.invMirroredEdges;
		invMirroredFaces = mesh.invMirroredFaces;
		mirroredMesh = mesh;
		mirrorState = state;
		return mesh;
	}

	/**
	 * Mirrors the mesh as specified by the mirror state attribute
	 */
	public void mirrorMesh() {
		if (mirrorState == NO_MIRROR)
			return;
		// first step: delete faces that have all of their vertices on the
		// plane.
		boolean changed = false;
		boolean[] sel = new boolean[faces.length];
		int n;
		for (int i = 0; i < faces.length; ++i) {
			int[] fv = getFaceVertices(faces[i]);
			boolean del = true;
			for (int j = 0; j < fv.length; ++j) {
				if ((mirrorState & MIRROR_ON_XY) != 0) {
					if (Math.abs(vertices[fv[j]].r.z) > 1e-6)
						del = false;
				} else if ((mirrorState & MIRROR_ON_YZ) != 0) {
					if (Math.abs(vertices[fv[j]].r.x) > 1e-6)
						del = false;
				} else if ((mirrorState & MIRROR_ON_XZ) != 0) {
					if (Math.abs(vertices[fv[j]].r.y) > 1e-6)
						del = false;
				}
			}
			sel[i] = del;
			changed |= del;
		}
		if (!changed) {
			if ((mirrorState & MIRROR_ON_XY) != 0)
				mirrorState -= MIRROR_ON_XY;
			else if ((mirrorState & MIRROR_ON_YZ) != 0)
				mirrorState -= MIRROR_ON_YZ;
			else if ((mirrorState & MIRROR_ON_XZ) != 0)
				mirrorState -= MIRROR_ON_XZ;
			if (mirroredVerts == null) {
				mirroredVerts = new int[vertices.length];
				invMirroredVerts = new int[vertices.length];
				for (int i = 0; i < mirroredVerts.length; ++i) {
					mirroredVerts[i] = i;
					invMirroredVerts[i] = i;
				}
				mirroredEdges = new int[edges.length / 2];
				invMirroredEdges = new int[edges.length / 2];
				for (int i = 0; i < mirroredEdges.length; ++i) {
					mirroredEdges[i] = i;
					invMirroredEdges[i] = i;
				}
				mirroredFaces = new int[faces.length];
				invMirroredFaces = new int[faces.length];
				for (int i = 0; i < mirroredFaces.length; ++i) {
					mirroredFaces[i] = i;
					invMirroredFaces[i] = i;
				}
			}

		} else {
			deleteFaces(sel, true);
			Wvertex[] newVertices = new Wvertex[vertices.length * 2];
			Wedge[] newEdges = new Wedge[edges.length * 2];
			Wface[] newFaces = new Wface[faces.length * 2];
			int[] mirrorVertTable = new int[vertices.length * 2];
			int[] parmFaceTable = new int[faces.length];
			translateMesh(newVertices, newEdges, newFaces);
			for (int i = 0; i < vertices.length; ++i) {
				newVertices[i + vertices.length] = new Wvertex(newVertices[i]);
				if ((mirrorState & MIRROR_ON_XY) != 0)
					newVertices[i + vertices.length].r.z = -newVertices[i
							+ vertices.length].r.z;
				else if ((mirrorState & MIRROR_ON_YZ) != 0)
					newVertices[i + vertices.length].r.x = -newVertices[i
							+ vertices.length].r.x;
				else if ((mirrorState & MIRROR_ON_XZ) != 0)
					newVertices[i + vertices.length].r.y = -newVertices[i
							+ vertices.length].r.y;
				mirrorVertTable[i] = i;
				mirrorVertTable[i + vertices.length] = i;
			}
			for (int i = 0; i < edges.length / 2; ++i) {
				newEdges[i + edges.length / 2] = new Wedge(newEdges[i]);
				newEdges[i + edges.length / 2].hedge += edges.length / 2;
				n = newEdges[i].hedge;
				while (newEdges[n].next != i)
					n = newEdges[newEdges[n].next].hedge;
				newEdges[i + edges.length / 2].next = n + edges.length / 2;
				newEdges[i + edges.length / 2].vertex = newEdges[newEdges[i].hedge].vertex
						+ vertices.length;
				newVertices[newEdges[i + edges.length / 2].vertex].edge = newEdges[i].hedge
						+ edges.length / 2;
				if (newEdges[i + edges.length / 2].face != -1)
					newEdges[i + edges.length / 2].face += faces.length;
			}
			for (int i = edges.length; i < edges.length + edges.length / 2; ++i) {
				newEdges[i + edges.length / 2] = new Wedge(newEdges[i]);
				newEdges[i + edges.length / 2].hedge += edges.length / 2;
				n = newEdges[i].hedge;
				while (newEdges[n].next != i)
					n = newEdges[newEdges[n].next].hedge;
				newEdges[i + edges.length / 2].next = n + edges.length / 2;
				newEdges[i + edges.length / 2].vertex = newEdges[newEdges[i].hedge].vertex
						+ vertices.length;
				newVertices[newEdges[i + edges.length / 2].vertex].edge = newEdges[i].hedge
						+ edges.length / 2;
				if (newEdges[i + edges.length / 2].face != -1)
					newEdges[i + edges.length / 2].face += faces.length;
			}
			for (int i = 0; i < faces.length; ++i) {
				newFaces[i] = new Wface(newFaces[i]);
				newFaces[i + faces.length] = new Wface(newFaces[i]);
				newFaces[i + faces.length].edge += edges.length / 2;
				parmFaceTable[i] = i;
			}
			Vector boundary = new Vector();
			Vector boundaries = new Vector();
			boolean[] done = new boolean[edges.length];
			for (int i = 0; i < edges.length; ++i) {
				if ((edges[i].face == -1) && (!done[i])) {
					done[i] = true;
					boolean onMirrorPlane = true;
					boundary.clear();
					Vec3 r = vertices[edges[i].vertex].r;
					boundary.add(new Integer(i));
					if ((mirrorState & MIRROR_ON_XY) != 0) {
						if (Math.abs(r.z) > 1e-6)
							onMirrorPlane = false;
					} else if ((mirrorState & MIRROR_ON_YZ) != 0) {
						if (Math.abs(r.x) > 1e-6)
							onMirrorPlane = false;
					} else if ((mirrorState & MIRROR_ON_XZ) != 0) {
						if (Math.abs(r.y) > 1e-6)
							onMirrorPlane = false;
					}
					int current = edges[i].next;
					while (current != i) {
						r = vertices[edges[current].vertex].r;
						boundary.add(new Integer(current));
						done[current] = true;
						if ((mirrorState & MIRROR_ON_XY) != 0) {
							if (Math.abs(r.z) > 1e-6)
								onMirrorPlane = false;
						} else if ((mirrorState & MIRROR_ON_YZ) != 0) {
							if (Math.abs(r.x) > 1e-6)
								onMirrorPlane = false;
						} else if ((mirrorState & MIRROR_ON_XZ) != 0) {
							if (Math.abs(r.y) > 1e-6)
								onMirrorPlane = false;
						}
						current = edges[current].next;
					}
					if (!onMirrorPlane)
						continue;
					boundaries.addAll(boundary);
				}
			}
			for (int j = 0; j < boundaries.size(); ++j) {
				int e = ((Integer) boundaries.elementAt(j)).intValue();
				if (e >= edges.length / 2)
					e += edges.length / 2;
				int v = newEdges[e].vertex;
				newVertices[v + vertices.length].edge = -1;
				int newe = e + edges.length / 2;
				for (int k = 0; k < newEdges.length; ++k) {
					if (newEdges[k].vertex == v + vertices.length)
						newEdges[k].vertex = v;
					if (newEdges[k].next == newEdges[newe].hedge)
						newEdges[k].next = e;
				}
				newEdges[e].face = newEdges[newEdges[newe].hedge].face;
				newEdges[e].next = newEdges[newEdges[newe].hedge].next;
				newEdges[newe].vertex = -1;
				newEdges[newEdges[newe].hedge].vertex = -1;
				newFaces[newEdges[newEdges[newe].hedge].face].edge = e;
			}
			int index = 0;
			for (int i = 0; i < newVertices.length; ++i)
				if (newVertices[i].edge == -1)
					++index;
			Wedge[] ne = new Wedge[newEdges.length - 2 * boundaries.size()];
			Wvertex[] nv = new Wvertex[newVertices.length - index];
			int[] parmVertTable = new int[nv.length];
			int[] vertTable = new int[newVertices.length];
			int[] edgeTable = new int[newEdges.length];
			index = 0;
			for (int i = 0; i < newVertices.length; ++i) {
				vertTable[i] = -1;
				if (newVertices[i].edge != -1) {
					nv[index] = newVertices[i];
					parmVertTable[index] = mirrorVertTable[i];
					vertTable[i] = index++;
				}
			}
			index = 0;
			for (int i = 0; i < newEdges.length; ++i) {
				edgeTable[i] = -1;
				if (newEdges[i].vertex != -1) {
					ne[index] = newEdges[i];
					edgeTable[i] = index++;
				}
			}
			for (int i = 0; i < nv.length; ++i)
				nv[i].edge = edgeTable[nv[i].edge];
			for (int i = 0; i < ne.length; ++i) {
				ne[i].vertex = vertTable[ne[i].vertex];
				ne[i].hedge = edgeTable[ne[i].hedge];
				ne[i].next = edgeTable[ne[i].next];
			}
			for (int i = 0; i < newFaces.length; ++i)
				newFaces[i].edge = edgeTable[newFaces[i].edge];
			if ((mirrorState & MIRROR_ON_XY) != 0)
				mirrorState -= MIRROR_ON_XY;
			else if ((mirrorState & MIRROR_ON_YZ) != 0)
				mirrorState -= MIRROR_ON_YZ;
			else if ((mirrorState & MIRROR_ON_XZ) != 0)
				mirrorState -= MIRROR_ON_XZ;
			vertices = nv;
			edges = ne;
			faces = newFaces;
			// Update the texture parameters.
			TextureParameter param[] = getParameters();
			ParameterValue oldParamVal[] = getParameterValues();
			if (oldParamVal != null) {
				ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
				for (int k = 0; k < oldParamVal.length; k++) {
					if (oldParamVal[k] instanceof FaceParameterValue) {
						double oldval[] = ((FaceParameterValue) oldParamVal[k])
								.getValue();
						double newval[] = new double[faces.length];
						for (int j = 0; j < oldval.length; j++)
							newval[j] = oldval[j];
						for (int j = oldval.length; j < newval.length; j++)
							newval[j] = oldval[parmFaceTable[j - oldval.length]];
						newParamVal[k] = new FaceParameterValue(newval);
					} else if (oldParamVal[k] instanceof VertexParameterValue) {
						double oldval[] = ((VertexParameterValue) oldParamVal[k])
								.getValue();
						double newval[] = new double[vertices.length];
						for (int j = 0; j < oldval.length; j++)
							newval[j] = oldval[j];
						for (int j = oldval.length; j < newval.length; j++)
							newval[j] = oldval[parmVertTable[j]];
						newParamVal[k] = new VertexParameterValue(newval);
					} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
						FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
						double newval[][] = new double[newFaces.length][];
						for (int j = 0; j < newFaces.length; ++j) {
							int[] fv = getFaceVertices(j, newEdges, newFaces);
							double val[] = new double[fv.length];
							for (int l = 0; l < fv.length; l++) {
								val[l] = fvpv.getAverageValue();
							}
							newval[j] = val;
						}
						newParamVal[k] = new FaceVertexParameterValue(newval);
					} else
						newParamVal[k] = oldParamVal[k].duplicate();
				}
				setParameterValues(newParamVal);
			}
		}
		mirrorMesh();
		resetMesh();
	}

	/**
	 * Mirrors the mesh as specified by the mirror state attribute
	 */
	public void mirrorWholeMesh(short mirrorPlane) {
		switch (mirrorPlane) {
		case MIRROR_ON_XY:
			for (int i = 0; i < vertices.length; i++)
				vertices[i].r.z = -vertices[i].r.z;
			break;
		case MIRROR_ON_YZ:
			for (int i = 0; i < vertices.length; i++)
				vertices[i].r.x = -vertices[i].r.x;
			break;
		case MIRROR_ON_XZ:
			for (int i = 0; i < vertices.length; i++)
				vertices[i].r.y = -vertices[i].r.y;
			break;
		}
		invertNormals();
	}

	/**
	 * Inverts mesh normals.
	 */
	public void invertNormals() {
		Wedge[] newEdges = new Wedge[edges.length];

		int next;
		for (int i = 0; i < faces.length; i++) {
			int[] fe = getFaceEdges(faces[i]);
			for (int j = 0; j < fe.length; j++) {
				newEdges[fe[j]] = new Wedge(edges[fe[j]]);
				newEdges[fe[j]].vertex = edges[edges[fe[j]].hedge].vertex;
				next = j - 1;
				if (next < 0)
					next = fe.length - 1;
				newEdges[fe[j]].next = fe[next];
				if (edges[edges[fe[j]].hedge].face == -1) {
					newEdges[newEdges[fe[j]].hedge] = new Wedge(
							edges[edges[fe[j]].hedge]);
					newEdges[newEdges[fe[j]].hedge].vertex = edges[fe[j]].vertex;
					int p = fe[j];
					while (edges[p].next != edges[fe[j]].hedge)
						p = edges[edges[p].next].hedge;
					newEdges[newEdges[fe[j]].hedge].next = p;
				}
			}

		}
		for (int i = 0; i < vertices.length; i++)
			vertices[i].edge = edges[vertices[i].edge].hedge;
		edges = newEdges;
		resetMesh();
	}

	/**
	 * Joins two boundaries given a vertex on each boundary
	 * 
	 * @param one
	 *            Index of a vertex on the first boundary
	 * @param two
	 *            Index of a vertex on the second boundary
	 * @return True if boundaries have been joined successfully
	 */
	public boolean joinBoundaries(int one, int two) {
		int[] oe = getVertexEdges(vertices[one]);
		int ob = -1;
		int[] te = getVertexEdges(vertices[two]);
		int tb = -1;
		int op;
		int tp;

		// System.out.println( one + " " + two );
		// dumpMesh();
		for (int i = 0; i < oe.length; ++i) {
			if (edges[oe[i]].face == -1)
				ob = oe[i];
		}
		for (int i = 0; i < te.length; ++i) {
			if (edges[te[i]].face == -1)
				tb = te[i];
		}
		// System.out.println( ob + " " + tb );
		if (ob == -1 || tb == -1)
			return false;
		Wedge[] newEdges = new Wedge[edges.length + 2];
		Wface[] newFaces = new Wface[faces.length + 1];
		translateMesh(vertices, newEdges, newFaces);
		if (tb >= edges.length / 2)
			++tb;
		if (ob >= edges.length / 2)
			++ob;
		// System.out.println( ob + " " + tb );
		op = newEdges[ob].hedge;
		while (newEdges[op].next != ob)
			op = newEdges[newEdges[op].next].hedge;
		tp = newEdges[tb].hedge;
		while (newEdges[tp].next != tb)
			tp = newEdges[newEdges[tp].next].hedge;
		newEdges[op].face = faces.length;
		newEdges[tp].face = faces.length;
		op = ob;
		int count = 0;
		while (newEdges[op].next != ob) {
			++count;
			newEdges[op].face = faces.length;
			op = newEdges[op].next;
		}
		int[] ov = new int[count + 1];
		op = ob;
		count = 1;
		ov[0] = one;
		while (newEdges[op].next != ob) {
			ov[count++] = newEdges[op].vertex;
			op = newEdges[op].next;
		}
		count = 0;
		tp = tb;
		while (newEdges[tp].next != tb) {
			++count;
			newEdges[tp].face = faces.length;
			tp = newEdges[tp].next;
		}
		int[] tv = new int[count + 1];
		tp = tb;
		count = 1;
		tv[0] = two;
		while (newEdges[tp].next != tb) {
			tv[tv.length - count++] = newEdges[tp].vertex;
			tp = newEdges[tp].next;
		}

		newEdges[newEdges.length / 2 - 1] = new Wedge(one, newEdges.length - 1,
				faces.length, ob);
		newEdges[tp].next = newEdges.length / 2 - 1;
		newEdges[newEdges.length - 1] = new Wedge(two, newEdges.length / 2 - 1,
				faces.length, tb);
		newEdges[op].next = newEdges.length - 1;
		newFaces[faces.length] = new Wface(newEdges.length / 2 - 1);
		edges = newEdges;
		faces = newFaces;
		TextureParameter param[] = getParameters();
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[faces.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = param[k].defaultVal;
					newParamVal[k] = new FaceParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		int[] lv = ov;
		int[] sv = tv;
		if (tv.length > ov.length) {
			lv = tv;
			sv = ov;
		}
		int k;
		// dumpMesh();
		for (int i = 0; i < lv.length; ++i) {
			k = (int) Math.round(((double) i * sv.length)
					/ ((double) lv.length));
			if (k == sv.length)
				k = 0;
			blindConnectVertices(lv[i], sv[k]);
		}
		// dumpMesh();
		resetMesh();
		return true;
	}

	/**
	 * Connects two vertices
	 * 
	 * @param one
	 *            First vertex to connect
	 * @param two
	 *            Second vertex to connect
	 */
	private void blindConnectVertices(int one, int two) {
		int[] oe = getVertexEdges(vertices[one]);
		int ob = -1;
		int[] te = getVertexEdges(vertices[two]);
		int tb = -1;
		int op;
		int tp;

		for (int i = 0; i < oe.length; ++i) {
			for (int j = 0; j < te.length; ++j) {
				if (edges[oe[i]].face == edges[te[j]].face) {
					ob = oe[i];
					tb = te[j];
				}
			}
			if (edges[oe[i]].vertex == two)
				return;
		}
		for (int i = 0; i < te.length; ++i)
			if (ob == -1 || tb == -1)
				return;
		Wedge[] newEdges = new Wedge[edges.length + 2];
		Wface[] newFaces = new Wface[faces.length + 1];
		translateMesh(vertices, newEdges, newFaces);
		if (tb >= edges.length / 2)
			++tb;
		if (ob >= edges.length / 2)
			++ob;
		op = newEdges[ob].hedge;
		while (newEdges[op].next != ob)
			op = newEdges[newEdges[op].next].hedge;
		tp = newEdges[tb].hedge;
		while (newEdges[tp].next != tb)
			tp = newEdges[newEdges[tp].next].hedge;
		newEdges[newEdges.length / 2 - 1] = new Wedge(one, newEdges.length - 1,
				newEdges[ob].face, ob);
		newEdges[tp].next = newEdges.length / 2 - 1;
		newEdges[newEdges.length - 1] = new Wedge(two, newEdges.length / 2 - 1,
				faces.length, tb);
		newEdges[op].next = newEdges.length - 1;
		newFaces[faces.length] = new Wface(newEdges.length - 1);
		newFaces[newEdges[ob].face].edge = newEdges.length / 2 - 1;
		tp = tb;
		newEdges[tb].face = faces.length;
		while (newEdges[tp].next != tb) {
			newEdges[tp].face = faces.length;
			tp = newEdges[tp].next;
		}
		edges = newEdges;
		faces = newFaces;
		TextureParameter param[] = getParameters();
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[faces.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = param[k].defaultVal;
					newParamVal[k] = new FaceParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		resetMesh();
	}

	public boolean[] addMesh(PolyMesh addedMesh) {
		Wvertex[] addedVerts = (Wvertex[]) addedMesh.getVertices();
		Wedge[] addedEdges = addedMesh.getEdges();
		Wface[] addedFaces = addedMesh.getFaces();
		Wvertex[] newVerts = new Wvertex[vertices.length + addedVerts.length];
		Wedge[] newEdges = new Wedge[edges.length + addedEdges.length];
		Wface[] newFaces = new Wface[faces.length + addedFaces.length];
		translateMesh(newVerts, newEdges, newFaces);
		for (int i = 0; i < addedEdges.length / 2; i++) {
			newEdges[i + edges.length / 2] = new Wedge(addedEdges[i]);
			newEdges[i + edges.length / 2].vertex += vertices.length;
			if (newEdges[i + edges.length / 2].face != -1)
				newEdges[i + edges.length / 2].face += faces.length;
			if (addedEdges[i].next >= addedEdges.length / 2)
				newEdges[i + edges.length / 2].next += edges.length;
			else
				newEdges[i + edges.length / 2].next += edges.length / 2;
			if (addedEdges[i].hedge >= addedEdges.length / 2)
				newEdges[i + edges.length / 2].hedge += edges.length;
			else
				newEdges[i + edges.length / 2].hedge += edges.length / 2;
			newEdges[i + edges.length / 2 + newEdges.length / 2] = new Wedge(
					addedEdges[i + addedEdges.length / 2]);
			newEdges[i + edges.length / 2 + newEdges.length / 2].vertex += vertices.length;
			if (newEdges[i + edges.length / 2 + newEdges.length / 2].face != -1)
				newEdges[i + edges.length / 2 + newEdges.length / 2].face += faces.length;
			if (addedEdges[i + addedEdges.length / 2].next >= addedEdges.length / 2)
				newEdges[i + edges.length / 2 + newEdges.length / 2].next += edges.length;
			else
				newEdges[i + edges.length / 2 + newEdges.length / 2].next += edges.length / 2;
			if (addedEdges[i + addedEdges.length / 2].hedge >= addedEdges.length / 2)
				newEdges[i + edges.length / 2 + newEdges.length / 2].hedge += edges.length;
			else
				newEdges[i + edges.length / 2 + newEdges.length / 2].hedge += edges.length / 2;
		}
		Vec3 delta = new Vec3(0.1, 0.1, 0.1);
		for (int i = 0; i < addedVerts.length; i++) {
			newVerts[i + vertices.length] = new Wvertex(addedVerts[i]);
			newVerts[i + vertices.length].r.add(delta);
			if (addedVerts[i].edge >= addedEdges.length / 2)
				newVerts[i + vertices.length].edge += edges.length;
			else
				newVerts[i + vertices.length].edge += edges.length / 2;
		}
		for (int i = 0; i < addedFaces.length; i++) {
			newFaces[i + faces.length] = new Wface(addedFaces[i]);
			if (addedFaces[i].edge >= addedEdges.length / 2)
				newFaces[i + faces.length].edge += edges.length;
			else
				newFaces[i + faces.length].edge += edges.length / 2;
		}
		boolean[] sel = new boolean[newVerts.length];
		for (int i = vertices.length; i < newVerts.length; i++)
			sel[i] = true;
		vertices = newVerts;
		edges = newEdges;
		faces = newFaces;
		// Update the texture parameters.
		TextureParameter param[] = getParameters();
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[faces.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = param[k].defaultVal;
					newParamVal[k] = new FaceParameterValue(newval);
				} else if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[vertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = param[k].defaultVal;
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		checkMesh();
		resetMesh();
		return sel;
	}

	/**
	 * This method finds similar faces based on orientation or shape
	 * similarities. Criteria can be combined.
	 * 
	 * @param selected
	 *            The starting face selection
	 * @param normal
	 *            True if the criterion is based on normal orientation
	 * @param normalTol
	 *            tolerance on face orientation
	 * @param loose
	 *            True if the criterion is based on face shape, loose meaning
	 * @param looseTol
	 *            tolerance on face shape (loose)
	 * @param strict
	 *            True if the criterion is based on face shape, strict meaning
	 * @param strictTol
	 *            tolerance on face shape (strict)
	 * @return a similar faces selection
	 */
	public boolean[] findSimilarFaces(boolean[] selected, boolean normal,
			double normalTol, boolean loose, double looseTol, boolean strict,
			double strictTol) {
		if (!(normal || strict || loose))
			return selected;
		boolean newSel[] = new boolean[selected.length];
		Vec3[] faceNormals = getFaceNormals();
		double normalCrit = 1 - normalTol;
		int[] fie = null;
		double[] fia = null;
		double[] fil = null;
		int[] fe;
		double diff;
		Vec3 v1, v2;
		int next, pred, current;
		boolean sel;

		for (int i = 0; i < selected.length; i++) {
			if (loose || strict) {
				fie = getFaceVertices(faces[i]);
				fia = new double[fie.length];
				fil = new double[fie.length];
				for (int j = 0; j < fie.length; j++) {
					pred = j - 1;
					next = j + 1;
					if (pred < 0)
						pred = fie.length - 1;
					if (next == fie.length)
						next = 0;
					v1 = vertices[fie[pred]].r.minus(vertices[fie[j]].r);
					fil[j] = v1.length();
					v2 = vertices[fie[next]].r.minus(vertices[fie[j]].r);
					v1.normalize();
					v2.normalize();
					fia[j] = Math.acos(v1.dot(v2));
					if (Math.abs(fia[j]) < 0.001)
						fia[j] = 0.001;
				}
			}
			for (int j = 0; j < selected.length; j++)
				if (selected[i]) {
					newSel[j] = true;
					if (i == j)
						continue;
					newSel[j] = true;
					if (normal
							&& (faceNormals[i].dot(faceNormals[j]) < normalCrit))
						newSel[j] = false;
					if (loose) {
						fe = getFaceVertices(faces[j]);
						if (fe.length == fie.length) {
							sel = false;
							for (int offset = 0; offset < fe.length; offset++) {
								diff = 0;
								for (int k = 0; k < fe.length; k++) {
									current = offset + k;
									if (current < 0)
										current = fe.length - 1;
									if (current >= fe.length)
										current -= fe.length;
									pred = current - 1;
									next = current + 1;
									if (pred < 0)
										pred = fe.length - 1;
									if (next == fe.length)
										next = 0;
									v1 = vertices[fe[pred]].r
											.minus(vertices[fe[current]].r);
									v2 = vertices[fe[next]].r
											.minus(vertices[fe[current]].r);
									v1.normalize();
									v2.normalize();
									diff += Math.pow(
											(Math.acos(v1.dot(v2)) - fia[k])
													/ fia[k], 2);

								}
								diff = Math.sqrt(diff) / fe.length;
								if (diff < looseTol) {
									sel = true;
									break;
								}
							}
							if (!sel)
								newSel[j] = false;
						} else
							newSel[j] = false;
					}
					if (strict) {
						diff = 0;
						fe = getFaceVertices(faces[j]);
						if (fe.length == fie.length) {
							sel = false;
							for (int offset = 0; offset < fe.length; offset++) {
								diff = 0;
								for (int k = 0; k < fe.length; k++) {
									current = offset + k;
									if (current >= fe.length)
										current -= fe.length;
									pred = current - 1;
									next = current + 1;
									if (pred < 0)
										pred = fe.length - 1;
									if (next == fe.length)
										next = 0;
									v1 = vertices[fe[pred]].r
											.minus(vertices[fe[current]].r);
									diff += Math.pow((v1.length() - fil[k])
											/ fil[k], 2);
									// v2 =
									// vertices[fe[next]].r.minus(vertices[fe[current]].r);
									// v1.normalize();
									// v2.normalize();
									// diff +=
									// Math.pow((Math.acos(v1.dot(v2)) -
									// fia[k])/fia[k], 2);
								}
								diff = Math.sqrt(diff) / fe.length;
								if (diff < strictTol) {
									sel = true;
									break;
								}
							}
							if (!sel)
								newSel[j] = false;
						} else
							newSel[j] = false;
					}
					if (!normal && !strict && !loose)
						newSel[i] = false;
				}
		}

		return newSel;
	}

	/**
	 * This method finds edges with similar length.
	 * 
	 * @param selected
	 *            The starting edge selection
	 * @param tol
	 *            tolerance on edge length
	 * @return New edge selection
	 */
	public boolean[] findSimilarEdges(boolean[] selected, double tol) {
		boolean newSel[] = new boolean[selected.length];
		double length, diff;

		for (int i = 0; i < selected.length; i++) {
			if (selected[i]) {
				length = vertices[edges[i].vertex].r
						.distance(vertices[edges[edges[i].hedge].vertex].r);
				for (int j = 0; j < selected.length; j++) {
					diff = length
							- vertices[edges[j].vertex].r
									.distance(vertices[edges[edges[j].hedge].vertex].r);
					if (Math.abs(diff) <= tol)
						newSel[j] = true;
				}
				newSel[i] = true;
			}
		}
		return newSel;
	}

	/**
	 * Returns a boolean array describing if an edge is a seam
	 * 
	 * @return a boolean array of length edges.length/2 (may be null)
	 */
	public boolean[] getSeams() {
		return seams;
	}

	/**
	 * Marks edges as seams
	 * 
	 * @param newSeams
	 *            The edges that are to consider as seams (maybe null to clear
	 *            seams)
	 */
	public void setSeams(boolean[] newSeams) {
		if (newSeams == null)
			seams = null;
		else if (newSeams.length == edges.length / 2) {
			seams = newSeams;
			boolean empty = true;
			for (int i = 0; i < seams.length; i++)
				if (seams[i]) {
					empty = false;
					break;
				}
			if (empty)
				seams = null;
		}
	}

	/**
	 * Open edges marked as seams
	 */
	public int[] openSeams() {
		if (seams == null)
			return null;
		Wvertex[] newVertices;
		Wedge[] newEdges;
		Wface[] newFaces;
		boolean[] seamsCopy = new boolean[seams.length];
		ArrayList<Integer> vertTable = new ArrayList<Integer>();
		// System.out.println("Open seams " + seams);
		// dumpMesh();
		int count = 0;
		for (int i = 0; i < edges.length / 2; i++) {
			seamsCopy[i] = seams[i];
			if (seams[i])
				count++;
		}
		int orVertsLength = vertices.length;
		newVertices = new Wvertex[vertices.length + 2 * count];
		newEdges = new Wedge[edges.length + 2 * count];
		newFaces = new Wface[faces.length];
		translateMesh(newVertices, newEdges, newFaces);
		int vertCount = vertices.length;
		int edgeCount = edges.length / 2;
		int e, v1, v2, nv1, nv2, bv1, bv2, he, nhe, phe, nbv2, ref;
		int e1, e2, he1, he2, nhe1, nhe2, phe1, phe2, v3, ne1, ne2;
		int index, nextSeam;
		int toDo = count;
		int done = 0;
		do {
			for (int i = 0; i < edges.length / 2; i++) {
				if (seamsCopy[i]) {
					if (newEdges[i].face == -1
							|| newEdges[newEdges[i].hedge].face == -1) {
						seamsCopy[i] = false;
						continue;
					}
					bv1 = getBoundaryEdgeOnVertex(
							newEdges[newEdges[i].hedge].vertex, newVertices,
							newEdges);
					bv2 = getBoundaryEdgeOnVertex(newEdges[i].vertex,
							newVertices, newEdges);
					if (bv1 == -1 && bv2 == -1) {
						// each side of the edge is a boundary vertex
						// let's see if the next vertex is also selected so we
						// can open the mesh
						e1 = e2 = -1;
						nextSeam = -1;
						index = newEdges[i].next;
						while (index != newEdges[i].hedge && nextSeam == -1) {
							int boundary = getBoundaryEdgeOnVertex(
									newEdges[index].vertex, newVertices,
									newEdges);
							if (boundary == -1) {
								if (index < edges.length / 2
										&& seamsCopy[index]) {
									nextSeam = index;
									seamsCopy[index] = false;
									seamsCopy[i] = false;
									e1 = i;
									e2 = nextSeam;
									// System.out.println("ouverture #0a " +
									// i + ":" + index);
								} else if (index >= newEdges.length / 2) {
									if (newEdges[index].hedge < edges.length / 2
											&& seamsCopy[newEdges[index].hedge]) {
										nextSeam = index;
										seamsCopy[newEdges[index].hedge] = false;
										seamsCopy[i] = false;
										e1 = i;
										e2 = nextSeam;
										// System.out.println("ouverture #0b " +
										// i + ":" + newEdges[index].hedge);
									}
								}
							}
							index = newEdges[newEdges[index].hedge].next;
						}
						if (nextSeam == -1) {
							// maybe on the other side ?
							index = newEdges[newEdges[i].hedge].next;
							while (index != i && nextSeam == -1) {
								int boundary = getBoundaryEdgeOnVertex(
										newEdges[index].vertex, newVertices,
										newEdges);
								if (boundary == -1) {
									if (index < edges.length / 2
											&& seamsCopy[index]) {
										nextSeam = index;
										seamsCopy[i] = false;
										seamsCopy[index] = false;
										e1 = newEdges[i].hedge;
										e2 = nextSeam;
										// System.out.println("ouverture #0c " +
										// i + ":" + index);
									} else if (index >= newEdges.length / 2) {
										if (newEdges[index].hedge < edges.length / 2
												&& seamsCopy[newEdges[index].hedge]) {
											nextSeam = index;
											seamsCopy[i] = false;
											seamsCopy[newEdges[index].hedge] = false;
											e1 = newEdges[i].hedge;
											e2 = nextSeam;
											// System.out.println("ouverture
											// #0d " + i + ":" +
											// newEdges[index].hedge);
										}
									}
								}
								index = newEdges[newEdges[index].hedge].next;
							}
						}
						if (e1 != -1 && e2 != -1) {
							// we've found two contiguous seamsCopy
							v1 = newEdges[newEdges[e1].hedge].vertex;
							v2 = newEdges[e1].vertex;
							v3 = newEdges[e2].vertex;
							he1 = newEdges[e1].hedge;
							he2 = newEdges[e2].hedge;
							phe1 = getPreviousEdge(newEdges, he1);
							phe2 = getPreviousEdge(newEdges, he2);
							ne1 = edgeCount;
							ne2 = ne1 + 1;
							nhe1 = ne1 + newEdges.length / 2;
							nhe2 = ne2 + newEdges.length / 2;
							newVertices[vertCount] = new Wvertex(
									newVertices[v2].r, ne2);
							ref = v2;
							while (ref >= orVertsLength)
								ref = vertTable.get(ref - orVertsLength);
							vertTable.add(new Integer(ref));
							newEdges[ne1] = new Wedge(vertCount, nhe1, -1, ne2);
							newEdges[nhe1] = new Wedge(v1, ne1,
									newEdges[he1].face, newEdges[he1].next);
							newFaces[newEdges[he1].face].edge = nhe1;
							newEdges[he1].next = ne1;
							newEdges[he1].face = -1;
							newEdges[phe1].next = nhe1;
							newVertices[v1].edge = e1;
							newEdges[ne2] = new Wedge(v3, nhe2, -1, he2);
							newEdges[nhe2] = new Wedge(vertCount, ne2,
									newEdges[he2].face, newEdges[he2].next);
							newFaces[newEdges[he2].face].edge = nhe2;
							newEdges[he2].next = he1;
							newEdges[he2].face = -1;
							newEdges[phe2].next = nhe2;
							newVertices[v3].edge = he2;
							newVertices[v2].edge = he1;
							index = newEdges[newEdges[nhe2].next].hedge;
							while (index != ne1) {
								newEdges[index].vertex = vertCount;
								index = newEdges[newEdges[index].next].hedge;
							}
							++vertCount;
							edgeCount += 2;
							// System.out.println("ouverture #1 " + i);
						}
					} else if ((bv1 != -1 && bv2 == -1)
							|| (bv1 == -1 && bv2 != -1)) {
						// only one side of the edge is a boundary vertex
						if (bv1 != -1) {
							e = i;
						} else {
							e = newEdges[i].hedge;
							index = bv1;
							bv1 = bv2;
							bv2 = index;
						}
						v1 = newEdges[newEdges[e].hedge].vertex;
						v2 = newEdges[e].vertex;
						he = newEdges[e].hedge;
						nhe = edgeCount + newEdges.length / 2;
						phe = getPreviousEdge(newEdges, he);
						newEdges[edgeCount] = new Wedge(v2, nhe, -1, he);
						newEdges[nhe] = new Wedge(vertCount, edgeCount,
								newEdges[he].face, newEdges[he].next);
						newVertices[vertCount] = new Wvertex(new Vec3(
								newVertices[v1].r), edgeCount);
						ref = v1;
						while (ref >= orVertsLength)
							ref = vertTable.get(ref - orVertsLength);
						vertTable.add(new Integer(ref));
						newVertices[v1].edge = e;
						newVertices[v2].edge = he;
						newFaces[newEdges[nhe].face].edge = nhe;
						newEdges[he].face = -1;
						newEdges[he].next = newEdges[bv1].next;
						newEdges[bv1].next = edgeCount;
						newEdges[bv1].vertex = vertCount;
						newEdges[phe].next = nhe;
						// dumpNewMesh(newVertices, newEdges, newFaces);
						index = newEdges[newEdges[nhe].next].hedge;
						while (index != bv1) {
							newEdges[index].vertex = vertCount;
							index = newEdges[newEdges[index].next].hedge;
						}
						++edgeCount;
						++vertCount;
						seamsCopy[i] = false;
						// System.out.println("ouverture #2 " + i);
					} else if (bv1 != -1 && bv2 != -1) {
						// both sides of the edge are boundary vertices
						e = i;
						v1 = newEdges[newEdges[i].hedge].vertex;
						v2 = newEdges[i].vertex;
						nv1 = vertCount;
						nv2 = vertCount + 1;
						nbv2 = newEdges[bv2].next;
						he = newEdges[e].hedge;
						nhe = edgeCount + newEdges.length / 2;
						phe = getPreviousEdge(newEdges, he);
						newEdges[edgeCount] = new Wedge(nv2, nhe, -1,
								newEdges[bv2].next);
						newEdges[nhe] = new Wedge(nv1, edgeCount,
								newEdges[he].face, newEdges[he].next);
						newVertices[nv1] = new Wvertex(new Vec3(
								newVertices[v1].r), edgeCount);
						newVertices[nv2] = new Wvertex(new Vec3(
								newVertices[v2].r), nhe);
						ref = v1;
						while (ref >= orVertsLength)
							ref = vertTable.get(ref - orVertsLength);
						vertTable.add(new Integer(ref));
						ref = v2;
						while (ref >= orVertsLength)
							ref = vertTable.get(ref - orVertsLength);
						vertTable.add(new Integer(ref));
						newVertices[v1].edge = e;
						newVertices[v2].edge = he;
						newEdges[he].face = -1;
						newEdges[he].next = newEdges[bv1].next;
						newEdges[bv1].next = edgeCount;
						newEdges[bv1].vertex = nv1;
						newEdges[phe].next = nhe;
						newEdges[bv2].next = he;
						newFaces[newEdges[nhe].face].edge = nhe;
						index = newEdges[newEdges[nhe].next].hedge;
						while (index != bv1) {
							newEdges[index].vertex = nv1;
							index = newEdges[newEdges[index].next].hedge;
						}
						index = newEdges[nbv2].hedge;
						while (index != phe) {
							newEdges[index].vertex = nv2;
							index = newEdges[newEdges[index].next].hedge;
						}
						newEdges[phe].vertex = nv2;
						++edgeCount;
						vertCount += 2;
						seamsCopy[i] = false;
						// System.out.println("ouverture #3 " + i);
					}
				}
			}
			done = toDo;
			toDo = 0;
			for (int i = 0; i < edges.length / 2; i++)
				if (seamsCopy[i])
					toDo++;
		} while (toDo < done);
		// System.out.println("toDo left " + toDo);
		faces = newFaces;
		if (newVertices.length != vertCount) {
			vertices = new Wvertex[vertCount];
			for (int i = 0; i < vertCount; i++)
				vertices[i] = newVertices[i];
		} else
			vertices = newVertices;
		if (edgeCount < newEdges.length / 2) {
			// System.out.println("translating Mesh " + edgeCount*2 + " " +
			// newEdges.length);
			edges = newEdges;
			newEdges = new Wedge[edgeCount * 2];
			translateMesh(vertices, newEdges, faces);
			edges = newEdges;
		} else
			edges = newEdges;
		// Update the texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[vertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newval.length; j++)
						newval[j] = oldval[vertTable.get(j - oldval.length)];
					newParamVal[k] = new VertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);
		}
		if (vertTable.size() == 0) {
			return null;
		} else {
			int[] table = new int[vertTable.size() + orVertsLength];
			for (int i = 0; i < orVertsLength; i++) {
				table[i] = i;
			}
			for (int i = 0; i < vertTable.size(); i++) {
				table[i + orVertsLength] = vertTable.get(i);
			}
			return table;
		}
	}

	/**
	 * For internal use by openEdge.
	 * 
	 * @param index
	 *            The vertex to get boundary for
	 * @param newVertices
	 *            The current vertex array
	 * @param newEdges
	 *            The current edges array
	 * @return
	 */
	private int getBoundaryEdgeOnVertex(int index, Wvertex[] newVertices,
			Wedge[] newEdges) {
		int start = newEdges[newVertices[index].edge].hedge;
		if (newEdges[start].face == -1)
			return start;
		int e = start;
		do {
			e = newEdges[newEdges[e].next].hedge;
		} while (e != start && newEdges[e].face != -1);
		if (newEdges[e].face == -1)
			return e;
		return -1;
	}

	/**
	 * Returns an array to a continuous selection of edges. If the selection
	 * consists in several interconnected lines then an arbitray line is
	 * returned.
	 * 
	 * @param sel
	 *            The current edge selection
	 * @param start
	 *            An edge along the selected line
	 * @return the indew array describing the edge line
	 */
	private int[] findEdgeLine(boolean[] sel, int start) {
		ArrayList edgeLine = new ArrayList();
		int edge = start;
		int firstEdge = start;
		int newEdge;
		while (edge != -1) {
			newEdge = -1;
			int vertex = edges[edges[edge].hedge].vertex;
			int ve[] = getVertexEdges(vertices[vertex]);
			for (int i = 0; i < ve.length; i++) {
				if (ve[i] != edge && ve[i] != edges[edge].hedge
						&& isEdgeSelected(ve[i], seams))
					firstEdge = newEdge = ve[i];
			}
			edge = newEdge;
		}
		edge = firstEdge;
		while (edge != -1) {
			newEdge = -1;
			int vertex = edges[edge].vertex;
			int ve[] = getVertexEdges(vertices[vertex]);
			for (int i = 0; i < ve.length; i++) {
				if (ve[i] != edge && ve[i] != edges[edge].hedge
						&& isEdgeSelected(ve[i], seams))
					edgeLine.add(new Integer(newEdge = ve[i]));
			}
			edge = newEdge;
		}
		int[] indices = new int[edgeLine.size()];
		for (int i = 0; i < indices.length; i++)
			indices[i] = ((Integer) edgeLine.get(i)).intValue();
		return indices;
	}

	/**
	 * Finds appropriate seams in a mesh. Postponed to a later date.
	 */
	public void findSeams() {
		// seams = new boolean[edges.length/2];
		// TriangleMesh triMesh = convertToTriangleMesh(0.0);
		// boolean[] triSeams = TextureUtilities.findSeams(triMesh);
	}

	public void addStandaloneFace(Vec3[] newPoints) {
		Wvertex[] newVertices;
		Wedge[] newEdges;
		Wface[] newFaces;

		int count = newPoints.length;
		newVertices = new Wvertex[vertices.length + count];
		newEdges = new Wedge[edges.length + newPoints.length * 2];
		newFaces = new Wface[faces.length + 1];
		translateMesh(newVertices, newEdges, newFaces);
		int f, t, nextEdge, prevEdge;
		for (int i = 0; i < newPoints.length; i++) {
			f = i - 1;
			if (f < 0)
				f = newPoints.length - 1;
			f += vertices.length;
			t = i + vertices.length;
			if (i != newPoints.length - 1)
				nextEdge = edges.length / 2 + i + 1;
			else
				nextEdge = edges.length / 2;
			if (i != 0)
				prevEdge = edges.length / 2 + i - 1 + newEdges.length / 2;
			else
				prevEdge = edges.length / 2 + newPoints.length - 1
						+ newEdges.length / 2;
			newEdges[edges.length / 2 + i] = new Wedge(t, edges.length / 2 + i
					+ newEdges.length / 2, faces.length, nextEdge);
			newEdges[edges.length / 2 + i + newEdges.length / 2] = new Wedge(f,
					edges.length / 2 + i, -1, prevEdge);
		}
		for (int i = 0; i < newPoints.length; ++i) {
			f = edges.length / 2 + i + 1;
			if (i == newPoints.length - 1)
				f = edges.length / 2;
			newVertices[vertices.length + i] = new Wvertex(newPoints[i], f);
		}
		newFaces[faces.length] = new Wface(edges.length / 2);
		vertices = newVertices;
		edges = newEdges;
		faces = newFaces;
		// Update the texture parameters.
		TextureParameter param[] = getParameters();
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newVertices.length; ++j)
						newval[j] = param[k].defaultVal;
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[faces.length];
					for (int j = 0; j < faces.length - 1; ++j)
						newval[j] = oldval[((Integer) faceInfo.elementAt(j))
								.intValue()];
					newval[faces.length - 1] = param[k].defaultVal;
					newParamVal[k] = new FaceParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);

		}
		resetMesh();
	}

	public boolean addFaceFromPoints(int from, int to, Vec3[] newPoints) {
		Wvertex[] newVertices;
		Wedge[] newEdges;
		Wface[] newFaces;

		int count = newPoints.length;
		newVertices = new Wvertex[vertices.length + count];
		newEdges = new Wedge[edges.length + (newPoints.length + 1) * 2];
		newFaces = new Wface[faces.length + 1];
		translateMesh(newVertices, newEdges, newFaces);
		ArrayList v;
		ArrayList v1 = new ArrayList();
		ArrayList v2 = new ArrayList();
		boolean found;
		int fromPoint = from;
		while (fromPoint != to) {
			found = false;
			int[] ve = getVertexEdges(vertices[fromPoint]);
			for (int i = 0; i < ve.length; i++) {
				if (edges[ve[i]].face < 0) {
					found = true;
					if (ve[i] < edges.length / 2)
						v1.add(new Integer(ve[i]));
					else
						v1.add(new Integer(ve[i] + newEdges.length / 2
								- edges.length / 2));
					fromPoint = edges[ve[i]].vertex;
					break;
				}
			}
			if (fromPoint == from) {
				return false;
			}
			if (!found) {
				v1 = null;
				fromPoint = to;
			}

		}
		fromPoint = to;
		while (fromPoint != from) {
			found = false;
			int[] ve = getVertexEdges(vertices[fromPoint]);
			for (int i = 0; i < ve.length; i++) {
				if (edges[ve[i]].face < 0) {
					found = true;
					if (ve[i] < edges.length / 2)
						v2.add(new Integer(ve[i]));
					else
						v2.add(new Integer(ve[i] + newEdges.length / 2
								- edges.length / 2));
					fromPoint = edges[ve[i]].vertex;
					break;
				}
			}
			if (fromPoint == to)
				return false;
			if (!found) {
				v2 = null;
				break;
			}
		}
		v = v1;
		if (v1 == null && v2 == null)
			return false;
		boolean invert = false;
		if (v1 == null || (v2 != null && v1.size() > v2.size())) {
			v = v2;
			invert = true;

		}
		for (int i = 0; i < v.size(); i++) {
			int k = ((Integer) v.get(i)).intValue();
			newEdges[k].face = faces.length;
		}
		int f, t;
		for (int i = 0; i < newPoints.length + 1; i++) {
			f = i - 1;
			if (f < 0) {
				if (invert)
					f = from;
				else
					f = to;
			} else
				f += vertices.length;
			t = i;
			if (t > newPoints.length - 1) {
				if (invert)
					t = to;
				else
					t = from;
			} else
				t += vertices.length;
			newEdges[edges.length / 2 + i] = new Wedge(t, edges.length / 2 + i
					+ newEdges.length / 2, faces.length, edges.length / 2 + i
					+ 1);
			newEdges[edges.length / 2 + i + newEdges.length / 2] = new Wedge(f,
					edges.length / 2 + i, -1, edges.length / 2 + i - 1
							+ newEdges.length / 2);
		}
		int index = ((Integer) v.get(0)).intValue();
		newEdges[edges.length / 2 + newPoints.length].next = index;
		if (index >= newEdges.length / 2)
			index -= newEdges.length / 2 - edges.length / 2;
		int[] ve;
		if (invert)
			ve = getVertexEdges(vertices[to]);
		else
			ve = getVertexEdges(vertices[from]);
		for (int i = 0; i < ve.length; i++) {
			if (edges[edges[ve[i]].hedge].next == index) {
				index = edges[ve[i]].hedge;
				if (index >= edges.length / 2)
					index += newEdges.length / 2 - edges.length / 2;
				newEdges[index].next = edges.length / 2 + newPoints.length
						+ newEdges.length / 2;
				break;
			}
		}
		index = ((Integer) v.get(v.size() - 1)).intValue();
		newEdges[edges.length / 2 + newEdges.length / 2].next = newEdges[index].next;
		newEdges[index].next = edges.length / 2;
		for (int i = 0; i < newPoints.length; ++i) {
			if (!invert)
				newVertices[vertices.length + i] = new Wvertex(
						newPoints[newPoints.length - 1 - i], edges.length / 2
								+ i + 1);
			else
				newVertices[vertices.length + i] = new Wvertex(newPoints[i],
						edges.length / 2 + i + 1);
		}
		newFaces[faces.length] = new Wface(edges.length / 2);
		vertices = newVertices;
		edges = newEdges;
		faces = newFaces;
		// Update the texture parameters.
		TextureParameter param[] = getParameters();
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int k = 0; k < oldParamVal.length; k++) {
				if (oldParamVal[k] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[newVertices.length];
					for (int j = 0; j < oldval.length; j++)
						newval[j] = oldval[j];
					for (int j = oldval.length; j < newVertices.length; ++j)
						newval[j] = param[k].defaultVal;
					newParamVal[k] = new VertexParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[k])
							.getValue();
					double newval[] = new double[faces.length];
					for (int j = 0; j < faces.length - 1; ++j)
						newval[j] = oldval[j];
					newval[faces.length - 1] = param[k].defaultVal;
					newParamVal[k] = new FaceParameterValue(newval);
				} else if (oldParamVal[k] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[k];
					double newval[][] = new double[newFaces.length][];
					for (int j = 0; j < newFaces.length; ++j) {
						int[] fv = getFaceVertices(j, newEdges, newFaces);
						double val[] = new double[fv.length];
						for (int l = 0; l < fv.length; l++) {
							val[l] = fvpv.getAverageValue();
						}
						newval[j] = val;
					}
					newParamVal[k] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[k] = oldParamVal[k].duplicate();
			}
			setParameterValues(newParamVal);

		}
		resetMesh();
		return true;
	}

	/**
	 * Checks a mesh for validity
	 * 
	 * @return Mesh diagnostic
	 */
	public String checkMesh() {

		boolean repairTwoEdgeFaces = false;
		String s = "";

		s += "Mesh consisting of:\n";
		s += vertices.length + " vertices, " + edges.length / 2 + " edges, "
				+ faces.length + " faces.\n\n";
		s += "Checking Vertices...\n";
		for (int i = 0; i < vertices.length; ++i) {
			if (vertices[i].edge >= edges.length) {
				vertices[i].edge = 0;
				s += "vertex " + i + " : edge reference too high\n";
			} else if (vertices[i].edge < 0) {
				vertices[i].edge = 0;
				s += "vertex " + i + " : negative edge reference\n";
			}
		}
		for (int i = 0; i < vertices.length; ++i) {
			if (edges[vertices[i].edge].hedge >= 0
					&& edges[vertices[i].edge].hedge < edges.length
					&& edges[edges[vertices[i].edge].hedge].vertex != i) {
				s += "Wrong edge reference for vertex " + i + ".\n";
				boolean corrected = false;
				for (int j = 0; j < edges.length; ++j) {
					if (edges[edges[j].hedge].vertex == i) {
						vertices[i].edge = j;
						corrected = true;
						break;
					}
				}
				if (corrected)
					s += "Corrected.\n";
				else
					s += "No edge leaving this vertex. No correction possible.\n";
			}
		}
		int counter;
		int ed;
		for (int i = 0; i < vertices.length; ++i) {
			ed = vertices[i].edge;
			counter = 0;
			while (edges[edges[ed].hedge].next != vertices[i].edge
					&& counter < edges.length) {
				ed = edges[edges[ed].hedge].next;
				counter++;
			}
			if (counter == edges.length)
				s += "Problem infinite edge references on vertex: " + i + ".\n";
		}
		s += "Done checking vertices.\n\n";
		s += "Checking Edges...\n";
		boolean validHedge = true;
		boolean validVertex = true;
		for (int i = 0; i < edges.length; ++i) {
			if (edges[i].hedge >= edges.length)
				validHedge = false;
			if (edges[i].vertex >= vertices.length)
				validVertex = false;
			if (edges[i].face >= faces.length)
				edges[i].face = -2;
			if (edges[i].vertex == edges[edges[i].hedge].vertex) {
				s += "Null length edge found\n";
			}
			if (edges[i].next == i) {
				s += "Single edge loop found\n";
				edges[i].next = 0;
			} else if (edges[edges[i].next].next == i) {
				repairTwoEdgeFaces = true;
				System.out.println("pb 2 edge: " + edges[i].face + " "
						+ edges[edges[i].next].face);
				System.out.println(faces[edges[i].face].edge + " / " + i
						+ " / " + edges[i].next);
			}
		}
		if (!validHedge)
			s += "Half edge reference(s) out of range. No correction possible.\n";
		if (!validVertex)
			s += "Vertex reference(s) out of range. No correction possible.\n";
		if (repairTwoEdgeFaces)
			s += "Two edge faces/boundaries found.\n";
		if (validHedge & validVertex) {
			for (int i = 0; i < edges.length; ++i) {
				if (edges[i].hedge < edges.length / 2 && i < edges.length / 2)
					s += "Wrong position in array for edges " + i + " and "
							+ edges[i].hedge + ".\n";
				if (edges[i].hedge >= edges.length / 2 && i >= edges.length / 2)
					s += "Wrong position in array for edges " + i + " and "
							+ edges[i].hedge + ".\n";
				if (edges[i].face == -1 && edges[edges[i].hedge].face == -1)
					s += "Edges " + i + " and other half-edge" + edges[i].hedge
							+ " both boundary edges.\n";
				if (edges[i].next >= edges.length) {
					edges[i].next = 0;
					s += "edge " + i + " : next edge reference too high\n";
				} else if (edges[i].next < 0) {
					edges[i].next = 0;
					s += "edge " + i + " : negative next edge reference\n";
				}
				if (edges[i].face >= faces.length) {
					edges[i].face = -1;
					s += "edge " + i + " : face reference too high\n";
				}
			}
		}
		s += "Done checking edges.\n\n";
		s += "Checking Faces...\n";
		for (int i = 0; i < faces.length; ++i)
			if (faces[i].edge >= edges.length) {
				faces[i].edge = 0;
				s += "face " + i + " : edge reference too high\n";
			} else if (faces[i].edge < 0) {
				faces[i].edge = 0;
				s += "face " + i + " : negative edge reference\n";
			}
		for (int i = 0; i < faces.length; ++i) {
			if (edges[faces[i].edge].face != i) {
				s += "Wrong edge reference for face " + i + ".\n";
				boolean corrected = false;
				ed = faces[i].edge;
				int index = 0;
				while (edges[ed].next != faces[i].edge && index < edges.length) {
					if (edges[ed].face == i)
						corrected = true;
					ed = edges[ed].next;
					++index;
				}
				if (index >= edges.length)
					s += "Unclosed face. No correction possible.\n";
				else if (corrected) {
					changeFace(edges, faces[i].edge, i);
					s += "Corrected.\n";
				} else {
					for (int j = 0; j < edges.length; ++j) {
						if (edges[j].face == i) {
							faces[i].edge = j;
							changeFace(edges, j, i);
							corrected = true;
							break;
						}
					}
					if (corrected)
						s += "Corrected.\n";
					else
						s += "No edge sharing this face. No correction possible.\n";
				}
			} else {
				ed = faces[i].edge;
				int index = 0;
				while (edges[ed].next != faces[i].edge && index < edges.length) {
					if (edges[ed].face != i) {
						s += "Wrong edge face reference for edge " + ed
								+ " (face " + i + "). Corrected.\n";
						edges[ed].face = i;
					}
					ed = edges[ed].next;
					++index;
				}
				if (index >= edges.length)
					s += "Unclosed face. No correction possible.\n";
			}

		}
		s += "Done checking faces.\n\n";
		if (repairTwoEdgeFaces) {
			s += "Repairing two edge faces/boundaries...\n";
			removeTwoEdgeBoundaries();
			removeTwoEdgedFaces(null);
		}
		return s;
	}

	/**
	 * Get a MeshViewer which can be used for viewing this mesh.
	 * 
	 * @param controller
	 *            Description of the Parameter
	 * @param options
	 *            Description of the Parameter
	 * @return Description of the Return Value
	 */

	public MeshViewer createMeshViewer(MeshEditController controller,
			RowContainer options) {
		return new PolyMeshViewer(controller, options);
	}

	/**
	 * When setting the texture, we need to clear the caches.
	 * 
	 * @param tex
	 *            The new texture value
	 * @param mapping
	 *            The new texture value
	 */

	public void setTexture(Texture tex, TextureMapping mapping) {
		super.setTexture(tex, mapping);
		cachedMesh = null;
		mirroredMesh = null;
	}

	/**
	 * When setting texture parameters, we need to clear the caches.
	 * 
	 * @param val
	 *            The new parameterValues value
	 */

	public void setParameterValues(ParameterValue val[]) {
		super.setParameterValues(val);
		cachedMesh = null;
		mirroredMesh = null;
	}

	/**
	 * When setting texture parameters, we need to clear the caches.
	 * 
	 * @param param
	 *            The new parameterValue value
	 * @param val
	 *            The new parameterValue value
	 */

	public void setParameterValue(TextureParameter param, ParameterValue val) {
		super.setParameterValue(param, val);
		cachedMesh = null;
		mirroredMesh = null;
	}

	/**
	 * dumps the mesh to console (debugging purposes)
	 */
	protected void dumpMesh() {
		dumpNewMesh(vertices, edges, faces);
	}

	/**
	 * dumps the mesh to console (debugging purposes)
	 */
	protected void dumpNewMesh(Wvertex[] vertices, Wedge[] edges, Wface[] faces) {
		for (int i = 0; i < vertices.length; ++i) {
			if (vertices[i] != null)
				System.out.println("vertex " + i + " " + vertices[i].edge + " "
						+ vertices[i].r);
		}
		for (int i = 0; i < faces.length; ++i) {

			if (faces[i] != null)
				System.out.println("face " + i + " " + faces[i].edge);
		}
		for (int i = 0; i < edges.length; ++i) {
			if (edges[i] != null)
				System.out.println("edge " + i + " " + edges[i]);
		}

	}

	/**
	 * dumps currently built mesh to console (debugging purposes)
	 */
	protected void dumpMesh(Wvertex[] nv, Wedge[] ne, Wface[] nf) {
		Wvertex[] v = vertices;
		vertices = nv;
		Wedge[] e = edges;
		edges = ne;
		Wface[] f = faces;
		faces = nf;
		dumpMesh();
		vertices = v;
		edges = e;
		faces = f;
	}

	/**
	 * Gets the mirror state attribute of the PolyMesh object
	 * 
	 * @return The mirrorState value
	 */
	public short getMirrorState() {
		return mirrorState;
	}

	/**
	 * Sets the mirror state attribute of the PolyMesh object
	 * 
	 * @param state
	 *            The new mirrorState value
	 */
	public void setMirrorState(short state) {
		mirrorState = state;
		resetMesh();
	}

	public boolean isControlledSmoothing() {
		return controlledSmoothing;
	}

	public void setControlledSmoothing(boolean controlledSmoothing) {
		this.controlledSmoothing = controlledSmoothing;
		resetMesh();
	}

	public double getMinAngle() {
		return minAngle;
	}

	public void setMinAngle(double minAngle) {
		this.minAngle = minAngle;
		resetMesh();
	}

	public double getMaxAngle() {
		return maxAngle;
	}

	public void setMaxAngle(double maxAngle) {
		this.maxAngle = maxAngle;
		resetMesh();
	}

	public float getMinSmoothness() {
		return minSmoothness;
	}

	public void setMinSmoothness(float minSmoothness) {
		this.minSmoothness = minSmoothness;
		resetMesh();
	}

	public float getMaxSmoothness() {
		return maxSmoothness;
	}

	public void setMaxSmoothness(float maxSmoothness) {
		this.maxSmoothness = maxSmoothness;
		resetMesh();
	}

	public Property[] getProperties() {
		return (Property[]) PROPERTIES.clone();
	}

	public Object getPropertyValue(int index) {
		switch (index) {
		case 0:
			return new Integer(interactiveSmoothLevel);
		default:
			return null;
		}
	}

	public void setPropertyValue(int index, Object value) {
		int val = ((Integer) value).intValue();
		if (index == 0)
			setInteractiveSmoothLevel(val);
	}

	/**
	 * Writes the poly mesh to an output stream (file, presumably)
	 * 
	 * @param out
	 *            The output stream
	 * @param theScene
	 *            Scene which contains the mesh
	 * @exception IOException
	 *                I/O exception
	 */
	public void writeToFile(DataOutputStream out, Scene theScene)
			throws IOException {
		if (theScene != null)
			super.writeToFile(out, theScene);

		out.writeShort(10);
		out.writeShort(mirrorState);
		out.writeInt(smoothingMethod);
		out.writeInt(vertices.length);
		for (int i = 0; i < vertices.length; i++) {
			vertices[i].r.writeToFile(out);
			out.writeInt(vertices[i].edge);
			// out.writeFloat( vertices[i].smoothness );
			out.writeInt(vertices[i].ikJoint);
			out.writeDouble(vertices[i].ikWeight);
			out.writeShort(vertices[i].type);
//			if (vertices[i].normal != null) {
//				out.writeBoolean(true);
//				vertices[i].normal.writeToFile(out);
//			} else
//				out.writeBoolean(false);
		}
		out.writeInt(edges.length);
		for (int i = 0; i < edges.length; i++) {
			out.writeInt(edges[i].vertex);
			out.writeInt(edges[i].hedge);
			out.writeInt(edges[i].face);
			out.writeInt(edges[i].next);
			out.writeFloat(edges[i].smoothness);
		}
		out.writeInt(faces.length);
		for (int i = 0; i < faces.length; i++) {
			out.writeInt(faces[i].edge);
			// out.writeFloat( 1.0f );
			// out.writeFloat( faces[i].centerSmoothness );
			// out.writeBoolean( faces[i].convex );
		}
		out.writeBoolean(controlledSmoothing);
		out.writeDouble(minAngle);
		out.writeDouble(maxAngle);
		out.writeFloat(minSmoothness);
		out.writeFloat(maxSmoothness);
		out.writeInt(interactiveSmoothLevel);
		out.writeInt(0); //former renderingSmoothLevel
		if (seams == null) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			for (int i = 0; i < seams.length; i++) {
				out.writeBoolean(seams[i]);
			}
		}
		if (mappingData != null) {
			out.writeBoolean(true);
			mappingData.writeToFile(out, theScene);
		} else {
			out.writeBoolean(false);
		}
		out.writeBoolean(useCustomColors);
		out.writeInt(vertColor.getRed());
		out.writeInt(vertColor.getGreen());
		out.writeInt(vertColor.getBlue());
		out.writeInt(selectedVertColor.getRed());
		out.writeInt(selectedVertColor.getGreen());
		out.writeInt(selectedVertColor.getBlue());
		out.writeInt(edgeColor.getRed());
		out.writeInt(edgeColor.getGreen());
		out.writeInt(edgeColor.getBlue());
		out.writeInt(selectedEdgeColor.getRed());
		out.writeInt(selectedEdgeColor.getGreen());
		out.writeInt(selectedEdgeColor.getBlue());
		out.writeInt(seamColor.getRed());
		out.writeInt(seamColor.getGreen());
		out.writeInt(seamColor.getBlue());
		out.writeInt(selectedSeamColor.getRed());
		out.writeInt(selectedSeamColor.getGreen());
		out.writeInt(selectedSeamColor.getBlue());
		out.writeInt(meshColor.getRed());
		out.writeInt(meshColor.getGreen());
		out.writeInt(meshColor.getBlue());
		out.writeInt(selectedFaceColor.getRed());
		out.writeInt(selectedFaceColor.getGreen());
		out.writeInt(selectedFaceColor.getBlue());
		out.writeInt(handleSize);
		if (theScene != null)
			skeleton.writeToStream(out);
	}
	
	public void printSize() {
		System.out.println(vertices.length + " verts (" + 
				vertices.length * 54 + "), " + edges.length + " edges (" +
				edges.length * 28 + "), " + faces.length + " faces (" +
				faces.length * 8 + "), for a total of: " + (vertices.length * 54 + edges.length * 28 + faces.length * 8 )  + " bytes");
	}

	/**
	 * Return a Keyframe which describes the current pose of this object.
	 * 
	 * @return The poseKeyframe value
	 */

	public Keyframe getPoseKeyframe() {
		return new PolyMeshKeyframe(this);
	}

	/**
	 * Modify this object based on a pose keyframe.
	 * 
	 * @param k
	 *            Description of the Parameter
	 */

	public void applyPoseKeyframe(Keyframe k) {
		PolyMeshKeyframe key = (PolyMeshKeyframe) k;

		for (int i = 0; i < vertices.length; i++) {
			Wvertex v = vertices[i];
			v.r.set(key.vertPos[i]);
			// v.smoothness = (float) key.vertSmoothness[i];
		}
		if (texParam != null && texParam.length > 0)
			for (int i = 0; i < texParam.length; i++)
				paramValue[i] = key.paramValue[i].duplicate();
		for (int i = 0; i < edges.length; i++)
			edges[i].smoothness = (float) key.edgeSmoothness[i];
		/*
		 * for ( int i = 0; i < faces.length; i++ ) { //1.0f = (float)
		 * key.faceEdgeSmoothness[i]; //faces[i].centerSmoothness = (float)
		 * key.facePointSmoothness[i]; //faces[i].convex = (boolean)
		 * key.faceConvex[i]; }
		 */
		skeleton.copy(key.skeleton);
		resetMesh();
	}

	/**
	 * Allow PolyMeshes to be converted to Actors.
	 * 
	 * @return Description of the Return Value
	 */

	public boolean canConvertToActor() {
		return true;
	}

	/**
	 * PolyMeshes cannot be keyframed directly, since any change to mesh
	 * topology would cause all keyframes to become invalid. Return an actor for
	 * this mesh.
	 * 
	 * @return The posableObject value
	 */

	public Object3D getPosableObject() {
		PolyMesh m = (PolyMesh) duplicate();
		return new Actor(m);
	}

	/**
	 * This class represents a pose of a PolyMesh.
	 * 
	 * @author Francois Guillet
	 * @created February, 4 2005
	 */

	public static class PolyMeshKeyframe extends MeshGesture {
		Vec3 vertPos[];

		float edgeSmoothness[];

		ParameterValue paramValue[];

		Skeleton skeleton;

		PolyMesh mesh;

		/**
		 * Constructor for the PolyMeshKeyframe object
		 * 
		 * @param mesh
		 *            The polymesh
		 */
		public PolyMeshKeyframe(PolyMesh mesh) {
			this.mesh = mesh;
			skeleton = mesh.getSkeleton().duplicate();
			vertPos = new Vec3[mesh.vertices.length];
			// vertSmoothness = new float[mesh.vertices.length];
			edgeSmoothness = new float[mesh.edges.length];
			for (int i = 0; i < vertPos.length; i++) {
				Wvertex v = mesh.vertices[i];
				vertPos[i] = new Vec3(v.r);
				// vertSmoothness[i] = v.smoothness;
			}
			for (int i = 0; i < edgeSmoothness.length; i++)
				edgeSmoothness[i] = mesh.edges[i].smoothness;
			paramValue = new ParameterValue[mesh.texParam.length];
			for (int i = 0; i < paramValue.length; i++)
				paramValue[i] = mesh.paramValue[i].duplicate();
		}

		/**
		 * Constructor for the PolyMeshKeyframe object
		 */
		private PolyMeshKeyframe() {
		}

		/**
		 * Get the Mesh this Gesture belongs to.
		 * 
		 * @return The mesh value
		 */

		protected Mesh getMesh() {
			return mesh;
		}

		/**
		 * Get the positions of all vertices in this Gesture.
		 * 
		 * @return The vertexPositions value
		 */

		protected Vec3[] getVertexPositions() {
			return vertPos;
		}

		/**
		 * Set the positions of all vertices in this Gesture.
		 * 
		 * @param pos
		 *            The new vertexPositions value
		 */

		protected void setVertexPositions(Vec3 pos[]) {
			vertPos = pos;
		}

		/**
		 * Get the skeleton for this pose (or null if it doesn't have one).
		 * 
		 * @return The skeleton value
		 */

		public Skeleton getSkeleton() {
			return skeleton;
		}

		/**
		 * Set the skeleton for this pose.
		 * 
		 * @param s
		 *            The new skeleton value
		 */

		public void setSkeleton(Skeleton s) {
			skeleton = s;
		}

		/**
		 * Create a duplicate of this keyframe.
		 * 
		 * @return Description of the Return Value
		 */

		public Keyframe duplicate() {
			return duplicate(mesh);
		}

		/**
		 * Description of the Method
		 * 
		 * @param owner
		 *            Description of the Parameter
		 * @return Description of the Return Value
		 */
		public Keyframe duplicate(Object owner) {
			PolyMeshKeyframe k = new PolyMeshKeyframe();
			k.mesh = (PolyMesh) owner;
			k.skeleton = skeleton.duplicate();
			k.vertPos = new Vec3[vertPos.length];
			// k.vertSmoothness = new float[vertSmoothness.length];
			k.edgeSmoothness = new float[edgeSmoothness.length];
			for (int i = 0; i < vertPos.length; i++) {
				k.vertPos[i] = new Vec3(vertPos[i]);
				// k.vertSmoothness[i] = vertSmoothness[i];
			}
			for (int i = 0; i < edgeSmoothness.length; i++)
				k.edgeSmoothness[i] = edgeSmoothness[i];
			k.paramValue = new ParameterValue[paramValue.length];
			for (int i = 0; i < paramValue.length; i++)
				k.paramValue[i] = paramValue[i].duplicate();
			return k;
		}

		/**
		 * Get the list of graphable values for this keyframe.
		 * 
		 * @return The graphValues value
		 */

		public double[] getGraphValues() {
			return new double[0];
		}

		/**
		 * Set the list of graphable values for this keyframe.
		 * 
		 * @param values
		 *            The new graphValues value
		 */

		public void setGraphValues(double values[]) {
		}

		/**
		 * These methods return a new Keyframe which is a weighted average of
		 * this one and one, two, or three others. These methods should never be
		 * called, since PolyMeshes can only be keyframed by converting them to
		 * Actors.
		 * 
		 * @param o2
		 *            Description of the Parameter
		 * @param weight1
		 *            Description of the Parameter
		 * @param weight2
		 *            Description of the Parameter
		 * @return Description of the Return Value
		 */

		public Keyframe blend(Keyframe o2, double weight1, double weight2) {
			return null;
		}

		/**
		 * Description of the Method
		 * 
		 * @param o2
		 *            Description of the Parameter
		 * @param o3
		 *            Description of the Parameter
		 * @param weight1
		 *            Description of the Parameter
		 * @param weight2
		 *            Description of the Parameter
		 * @param weight3
		 *            Description of the Parameter
		 * @return Description of the Return Value
		 */
		public Keyframe blend(Keyframe o2, Keyframe o3, double weight1,
				double weight2, double weight3) {
			return null;
		}

		/**
		 * Description of the Method
		 * 
		 * @param o2
		 *            Description of the Parameter
		 * @param o3
		 *            Description of the Parameter
		 * @param o4
		 *            Description of the Parameter
		 * @param weight1
		 *            Description of the Parameter
		 * @param weight2
		 *            Description of the Parameter
		 * @param weight3
		 *            Description of the Parameter
		 * @param weight4
		 *            Description of the Parameter
		 * @return Description of the Return Value
		 */
		public Keyframe blend(Keyframe o2, Keyframe o3, Keyframe o4,
				double weight1, double weight2, double weight3, double weight4) {
			return null;
		}

		/**
		 * Modify the mesh surface of a Gesture to be a weighted average of an
		 * arbitrary list of Gestures, averaged about this pose. This method
		 * only modifies the vertex positions and texture parameters, not the
		 * skeleton, and all vertex positions are based on the offsets from the
		 * joints they are bound to.
		 * 
		 * @param average
		 *            the Gesture to modify to be an average of other Gestures
		 * @param p
		 *            the list of Gestures to average
		 * @param weight
		 *            the weights for the different Gestures
		 */

		public void blendSurface(MeshGesture average, MeshGesture p[],
				double weight[]) {
			super.blendSurface(average, p, weight);
			PolyMeshKeyframe avg = (PolyMeshKeyframe) average;
			for (int i = 0; i < weight.length; i++) {
				PolyMeshKeyframe key = (PolyMeshKeyframe) p[i];
				// for ( int j = 0; j < vertSmoothness.length; j++ )
				// avg.vertSmoothness[j] += (float) ( weight[i] * (
				// key.vertSmoothness[j] - vertSmoothness[j] ) );
				for (int j = 0; j < edgeSmoothness.length; j++)
					avg.edgeSmoothness[j] += weight[i]
							* (key.edgeSmoothness[j] - edgeSmoothness[j]);
			}

			// Make sure all smoothness values are within legal bounds.

			/*
			 * for ( int i = 0; i < vertSmoothness.length; i++ ) { if (
			 * avg.vertSmoothness[i] < 0.0 ) avg.vertSmoothness[i] = 0.0f; if (
			 * avg.vertSmoothness[i] > 1.0 ) avg.vertSmoothness[i] = 1.0f; }
			 */
			for (int i = 0; i < edgeSmoothness.length; i++) {
				if (avg.edgeSmoothness[i] < 0.0)
					avg.edgeSmoothness[i] = 0.0f;
				if (avg.edgeSmoothness[i] > 1.0)
					avg.edgeSmoothness[i] = 1.0f;
			}
		}

		/**
		 * Determine whether this keyframe is identical to another one.
		 * 
		 * @param k
		 *            Description of the Parameter
		 * @return Description of the Return Value
		 */

		public boolean equals(Keyframe k) {
			if (!(k instanceof PolyMeshKeyframe))
				return false;
			PolyMeshKeyframe key = (PolyMeshKeyframe) k;
			for (int i = 0; i < vertPos.length; i++) {
				if (!vertPos[i].equals(key.vertPos[i]))
					return false;
				// if ( vertSmoothness[i] != key.vertSmoothness[i] )
				// return false;
			}
			for (int i = 0; i < paramValue.length; i++)
				if (!paramValue[i].equals(key.paramValue[i]))
					return false;
			for (int i = 0; i < edgeSmoothness.length; i++)
				if (edgeSmoothness[i] != key.edgeSmoothness[i])
					return false;
			if (!skeleton.equals(key.skeleton))
				return false;
			return true;
		}

		/**
		 * Update the texture parameter values when the texture is changed.
		 * 
		 * @param oldParams
		 *            Description of the Parameter
		 * @param newParams
		 *            Description of the Parameter
		 */

		public void textureChanged(TextureParameter oldParams[],
				TextureParameter newParams[]) {
			ParameterValue newval[] = new ParameterValue[newParams.length];

			for (int i = 0; i < newParams.length; i++) {
				int j;
				for (j = 0; j < oldParams.length
						&& !oldParams[j].equals(newParams[i]); j++)
					;
				if (j == oldParams.length) {
					// This is a new parameter, so copy the value from the
					// mesh.

					for (int k = 0; k < mesh.texParam.length; k++)
						if (mesh.texParam[k].equals(newParams[i])) {
							newval[i] = mesh.paramValue[k].duplicate();
							break;
						}
				} else {
					// This is an old parameter, so copy the values over.

					newval[i] = paramValue[j];
				}
			}
			paramValue = newval;
		}

		/**
		 * Get the value of a per-vertex texture parameter.
		 * 
		 * @param p
		 *            Description of the Parameter
		 * @return The textureParameter value
		 */

		public ParameterValue getTextureParameter(TextureParameter p) {
			// Determine which parameter to get.

			for (int i = 0; i < mesh.texParam.length; i++)
				if (mesh.texParam[i].equals(p))
					return paramValue[i];
			return null;
		}

		/**
		 * Set the value of a per-vertex texture parameter.
		 * 
		 * @param p
		 *            The new textureParameter value
		 * @param value
		 *            The new textureParameter value
		 */

		public void setTextureParameter(TextureParameter p, ParameterValue value) {
			// Determine which parameter to set.

			int which;
			for (which = 0; which < mesh.texParam.length
					&& !mesh.texParam[which].equals(p); which++)
				;
			if (which == mesh.texParam.length)
				return;
			paramValue[which] = value;
		}

		/**
		 * Write out a representation of this keyframe to a stream.
		 * 
		 * @param out
		 *            Description of the Parameter
		 * @exception IOException
		 *                Description of the Exception
		 */

		public void writeToStream(DataOutputStream out) throws IOException {
			out.writeShort(2);
			// version
			out.writeInt(vertPos.length);
			for (int i = 0; i < vertPos.length; i++) {
				vertPos[i].writeToFile(out);
				// out.writeFloat( vertSmoothness[i] );
			}
			for (int i = 0; i < paramValue.length; i++) {
				out.writeUTF(paramValue[i].getClass().getName());
				paramValue[i].writeToStream(out);
			}
			out.writeInt(edgeSmoothness.length);
			for (int i = 0; i < edgeSmoothness.length; i++)
				out.writeFloat(edgeSmoothness[i]);
			Joint joint[] = skeleton.getJoints();
			for (int i = 0; i < joint.length; i++) {
				joint[i].coords.writeToFile(out);
				out.writeDouble(joint[i].angle1.pos);
				out.writeDouble(joint[i].angle2.pos);
				out.writeDouble(joint[i].twist.pos);
				out.writeDouble(joint[i].length.pos);
			}
		}

		/**
		 * Reconstructs the keyframe from its serialized representation.
		 * 
		 * @param in
		 *            Description of the Parameter
		 * @param parent
		 *            Description of the Parameter
		 * @exception IOException
		 *                Description of the Exception
		 * @exception InvalidObjectException
		 *                Description of the Exception
		 */

		public PolyMeshKeyframe(DataInputStream in, Object parent)
				throws IOException, InvalidObjectException {
			this();
			short version = in.readShort();
			if (version < 0 || version > 2)
				throw new InvalidObjectException("");
			mesh = (PolyMesh) parent;
			int numVert = in.readInt();
			vertPos = new Vec3[numVert];
			// vertSmoothness = new float[numVert];
			float smoothness;
			paramValue = new ParameterValue[mesh.texParam.length];
			for (int i = 0; i < numVert; i++) {
				vertPos[i] = new Vec3(in);
				if (version < 1)
					smoothness = in.readFloat();
			}
			for (int i = 0; i < paramValue.length; i++)
				paramValue[i] = readParameterValue(in);
			edgeSmoothness = new float[in.readInt()];
			for (int i = 0; i < edgeSmoothness.length; i++)
				edgeSmoothness[i] = in.readFloat();
			if (version < 2) {
				int l = in.readInt();
				for (int i = 0; i < l; i++) {
					in.readFloat();
					in.readFloat();
					in.readBoolean();
				}
			}

			skeleton = mesh.getSkeleton().duplicate();
			Joint joint[] = skeleton.getJoints();
			for (int i = 0; i < joint.length; i++) {
				joint[i].coords = new CoordinateSystem(in);
				joint[i].angle1.pos = in.readDouble();
				joint[i].angle2.pos = in.readDouble();
				joint[i].twist.pos = in.readDouble();
				joint[i].length.pos = in.readDouble();
			}
		}
	}

	/**
	 * Winged mesh vertex structure
	 * 
	 * @author Francois Guillet
	 * @created december 19, 2004
	 */
	public class Wvertex extends MeshVertex {
		/**
		 * Edges next to this vertex
		 */
		public int edge;

		public short type;

		//public Vec3 normal;

		public final static short NONE = 0;

		public final static short CREASE = 1;

		public final static short CORNER = 2;


		/**
		 * Constructor for the Wvertex object
		 * 
		 * @param p
		 *            Vertex position
		 * @param edge
		 *            half edge that starts at this vertex
		 */
		public Wvertex(Vec3 p, int edge) {
			super(new Vec3(p));
			this.edge = edge;
			// smoothness = 1.0f;
		}

		/**
		 * Constructor for the Wvertex object
		 * 
		 * @param vertex
		 *            Vertex to duplicate
		 */
		public Wvertex(Wvertex vertex) {
			super(vertex);
			this.edge = vertex.edge;
			type = vertex.type;
//			if (vertex.normal != null)
//				normal = new Vec3(vertex.normal);
			// this.smoothness = vertex.smoothness;
		}
	}

	/**
	 * Checks if a face is selected. Voids are not selected by nature
	 * 
	 * @param selected
	 *            Selection array
	 * @param f
	 *            Index of face to test selection for
	 * @return The faceSelected value
	 */
	private boolean isFaceSelected(boolean[] selected, int f) {
		if (f == -1)
			return false;
		// a void is never selected!
		else
			return selected[f];
	}

	/**
	 * Half edge structure
	 * 
	 * @author Francois Guillet
	 * @created december 19, 2004
	 */
	public class Wedge {
		/**
		 * The vertex at the end of the edge
		 */
		public int vertex;

		/**
		 * Other half edge
		 */
		public int hedge;

		/**
		 * the face bordered by the half edge
		 */
		public int face;

		/**
		 * Next half edge around the face
		 */
		public int next;

		/**
		 * Edge smoothness
		 */
		public float smoothness;

		/**
		 * Constructor for the Wedge object
		 * 
		 * @param vertex
		 *            The vertex the edge points to
		 * @param hedge
		 *            The other half edge
		 * @param face
		 *            The face this half edge borders
		 * @param next
		 *            Next half edge around the face
		 */
		public Wedge(int vertex, int hedge, int face, int next) {
			this.vertex = vertex;
			this.hedge = hedge;
			this.face = face;
			this.next = next;
			smoothness = 1.0f;
		}

		/**
		 * Constructor for the Wedge object
		 * 
		 * @param edge
		 *            An edge to be duplicated
		 */
		public Wedge(Wedge edge) {
			vertex = edge.vertex;
			hedge = edge.hedge;
			face = edge.face;
			next = edge.next;
			smoothness = edge.smoothness;
		}

		/**
		 * Description of the Method
		 * 
		 * @return Description of the Return Value
		 */
		public String toString() {
			return ("vertex:" + vertex + " hedge:" + hedge + " face:" + face
					+ " next:" + next + " smoothness:" + smoothness);
		}
	}

	/**
	 * Winged mesh face structure
	 * 
	 * @author Francois Guillet
	 * @created december 19, 2004
	 */
	public class Wface {
		/**
		 * A half-edge that borders the face
		 */
		public int edge;

		/**
		 * Constructor for the Edge object
		 * 
		 * @param edge
		 *            An edge of this face
		 */
		public Wface(int edge) {
			this.edge = edge;
		}

		/**
		 * Constructor for the Wface object
		 * 
		 * @param face
		 *            Face to duplicate
		 */
		public Wface(Wface face) {
			edge = face.edge;
		}
	}

	/**
	 * New vertex computation : vertices involved and coefficients
	 * 
	 * @author Francois Guillet
	 * @created Feburary, 12, 2005
	 */
	public class VertexParamInfo {
		/**
		 * Vertices involved
		 */
		public int[] vert;

		/**
		 * And coefficients
		 */
		public double[] coef;

		/**
		 * Constructor for the VertexParamInfo object
		 * 
		 * @param vert
		 *            Vertices indices
		 * @param coef
		 *            Vertices weights
		 */
		public VertexParamInfo(int[] vert, double[] coef) {
			this.vert = vert;
			this.coef = coef;
		}
	}

	/**
	 * @return the mappingData
	 */
	public UVMappingData getMappingData() {
		return mappingData;
	}

	/**
	 * @param mappingData
	 *            the mappingData to set
	 */
	public void setMappingData(UVMappingData mappingData) {
		this.mappingData = mappingData;
		mappingVerts = vertices.length;
		mappingEdges = edges.length;
		mappingFaces = faces.length;
	}

	public Color getEdgeColor() {
		if (useCustomColors) {
			return edgeColor;
		} else {
			return ViewerCanvas.lineColor;
		}
	}

	public void setEdgeColor(Color edgeColor) {
		this.edgeColor = edgeColor;
	}

	public Color getMeshColor() {
		if (useCustomColors) {
			return meshColor;
		} else {
			return ViewerCanvas.surfaceColor;
		}
	}

	public void setMeshColor(Color meshColor) {
		this.meshColor = meshColor;
		meshRGBColor = ColorToRGB(meshColor);
	}

	public Color getSelectedFaceColor() {
		if (useCustomColors) {
			return selectedFaceColor;
		} else {
			return new Color(255, 102, 255);
		}
	}

	public void setSelectedFaceColor(Color selectedFaceColor) {
		this.selectedFaceColor = selectedFaceColor;
		selectedFaceRGBColor = ColorToRGB(selectedFaceColor);
	}

	public int getHandleSize() {
		if (useCustomColors) {
			return handleSize;
		} else {
			return 3;
		}
	}

	public void setHandleSize(int handleSize) {
		this.handleSize = handleSize;
	}

	public Color getSelectedEdgeColor() {
		if (useCustomColors) {
			return selectedEdgeColor;
		} else {
			return ViewerCanvas.highlightColor;
		}
	}

	public void setSelectedEdgeColor(Color selectedEdgeColor) {
		this.selectedEdgeColor = selectedEdgeColor;
	}

	public Color getSelectedSeamColor() {
		if (useCustomColors) {
			return selectedSeamColor;
		} else {
			return new Color(0, 162, 255);
		}
	}

	public void setSelectedSeamColor(Color selectedSeamColor) {
		this.selectedSeamColor = selectedSeamColor;
	}

	public Color getSelectedVertColor() {
		if (useCustomColors) {
			return selectedVertColor;
		} else {
			return ViewerCanvas.highlightColor;
		}
	}

	public void setSelectedVertColor(Color selectedVertColor) {
		this.selectedVertColor = selectedVertColor;
	}

	public Color getVertColor() {
		if (useCustomColors) {
			return vertColor;
		} else {
			return ViewerCanvas.lineColor;
		}
	}

	public void setVertColor(Color vertColor) {
		this.vertColor = vertColor;
	}

	public Color getSeamColor() {
		if (useCustomColors) {
			return vertColor;
		} else {
			return new Color(0,0,255);
		}
	}

	public void setSeamColor(Color seamColor) {
		this.seamColor = seamColor;
	}

	public RGBColor getMeshRGBColor() {
		if (useCustomColors) {
			return meshRGBColor;
		} else {
			return ViewerCanvas.surfaceRGBColor;
		}
	}

	public RGBColor getSelectedFaceRGBColor() {
		return selectedFaceRGBColor;
	}
	
	
	
	public boolean useCustomColors() {
		return useCustomColors;
	}

	public void setUseCustomColors(boolean useCustomColors) {
		this.useCustomColors = useCustomColors;
	}

	public RGBColor ColorToRGB(Color color) {
		return new RGBColor(((float) color.getRed()) / 255.0f, ((float) color
				.getGreen()) / 255.0f, ((float) color.getBlue()) / 255.0f);

	}
}
