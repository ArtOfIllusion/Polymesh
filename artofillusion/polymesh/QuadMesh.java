/* Copyright (C) 2007 by François Guillet

 This program is free software; you can redistribute it and/or modify it under the
 terms of the GNU General Public License as published by the Free Software
 Foundation; either version 2 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY 
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.polymesh;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Date;
import java.util.Stack;
import java.util.Vector;

import buoy.widget.RowContainer;
import artofillusion.MeshViewer;
import artofillusion.RenderingMesh;
import artofillusion.RenderingTriangle;
import artofillusion.Scene;
import artofillusion.WireframeMesh;
import artofillusion.animation.Keyframe;
import artofillusion.animation.Skeleton;
import artofillusion.math.BoundingBox;
import artofillusion.math.Vec3;
import artofillusion.object.FacetedMesh;
import artofillusion.object.MeshVertex;
import artofillusion.object.Object3D;
import artofillusion.object.ObjectInfo;
import artofillusion.object.TriangleMesh;
import artofillusion.polymesh.PolyMesh.VertexParamInfo;
import artofillusion.polymesh.PolyMesh.Wedge;
import artofillusion.polymesh.PolyMesh.Wface;
import artofillusion.polymesh.PolyMesh.Wvertex;
import artofillusion.texture.FaceParameterValue;
import artofillusion.texture.FaceVertexParameterValue;
import artofillusion.texture.ParameterValue;
import artofillusion.texture.TextureMapping;
import artofillusion.texture.VertexParameterValue;
import artofillusion.ui.MeshEditController;

/**
 * A QuadMesh is a mesh exclusively made up of quads. This mesh is not meant to be edited by users but
 * it backs up PolyMeshes when doing Catmull-Calrk smoothing. It may however be extended in the future
 * to provide a standalone, new kind of mesh for AoI. Its structure is heavily dervived from AoI trimesh.
 * 
 * Smoothness and smoothing algorithm is identical to PolyMesh smoothing algorithm since a smoothed
 * PolyMesh is a quad mesh.
 * 
 * @author Francois Guillet
 *
 */
public class QuadMesh extends Object3D implements FacetedMesh {

	/** A vertex specifies a position vector, the number of edges which share the vertex, and
	 * the "first" edge. Vertices also have a smoothness parameter associated to it : a vertex can be a
	 * smooth vertex, a crease or a corner.
	 * A vertex also has a "smoothness" parameter associated with it.
	 */

	public static class QuadVertex extends MeshVertex {
		public int firstEdge;

		public short type;

		public final static short NONE = 0;

		public final static short CREASE = 1;

		public final static short CORNER = 2;
		
		//private boolean marked;

		public QuadVertex(Vec3 p) {
			super(p);
			firstEdge = -1;
			type = NONE;
		}

		public QuadVertex(QuadVertex v) {
			super(v);
			firstEdge = v.firstEdge;
			type = v.type;
		}
		
		public QuadVertex(Wvertex v) {
			super(v);
			type = v.type;
		}

		/** Make this vertex identical to another one. */

		public void copy(QuadVertex v) {
			r.set(v.r);
			firstEdge = v.firstEdge;
			type = v.type;
			ikJoint = v.ikJoint;
			ikWeight = v.ikWeight;
		}
		
		public String toString() {
			return r.toString() + " first: " + firstEdge + " type: " + type;
		}
	}

	/** An edge is defined by the two vertices which it connects, and the two faces it is
	 adjacent to.  For a boundary edge, f2 must equal -1.  An edge also has a "smoothness"
	 parameter. */

	public static class QuadEdge {
		public int v1, v2, f1, f2;

		public float smoothness;

		public boolean mark;

		public QuadEdge(int vertex1, int vertex2, int face1) {
			v1 = vertex1;
			v2 = vertex2;
			f1 = face1;
			f2 = -1;
			smoothness = 1.0f;
		}
		
		public QuadEdge(int vertex1, int vertex2, int face1, int face2) {
			this(vertex1, vertex2, face1);
			f2 = face2;
		}

		public QuadEdge(QuadEdge e) {
			v1 = e.v1;
			v2 = e.v2;
			f1 = e.f1;
			f2 = e.f2;
			smoothness = e.smoothness;
		}
		
		public QuadEdge(Wedge e, Wedge he) {
			if (e.face != -1) {
				v2 = e.vertex;
				v1 = he.vertex;
				f1 = e.face;
				f2 = he.face;
			} else {
				v1 = e.vertex;
				v2 = he.vertex;
				f2 = e.face;
				f1 = he.face;
			}
			smoothness = e.smoothness;			
		}
		
		public String toString() {
			return "verts: " + v1 + " " + v2 + " faces: " + f1 + " " + f2 + " smooth: " + smoothness;
		}
	}

	/** A face is defined by its four vertices and four edges.  The vertices must be arranged
	 in counter-clockwise order, when viewed from the outside.  Edges 1, 2, 3 and 4 connect
	 vertices 1 and 2, 2 and 3, 3 and 4, and 4 and 1 respectively. */

	public static class QuadFace {
		
		public int v1, v2, v3, v4;
		public int e1, e2, e3, e4;
		public int mark;
		//subdivision states for a face
		public static final int FINAL = 0; //no further subdivision required
		public static final int SUBDIVIDE = 1; //full subdivision required
		public static final int YV2 = 2; //a Y around vertex 2 is required
		public static final int YV4 = 3; //a Y around vertex 4 is required

		public QuadFace(int vertex1, int vertex2, int vertex3, int vertex4,
				int edge1, int edge2, int edge3, int edge4) {
			v1 = vertex1;
			v2 = vertex2;
			v3 = vertex3;
			v4 = vertex4;
			e1 = edge1;
			e2 = edge2;
			e3 = edge3;
			e4 = edge4;
			mark = SUBDIVIDE;
		}

		public QuadFace(QuadFace f) {
			v1 = f.v1;
			v2 = f.v2;
			v3 = f.v3;
			v4 = f.v4;
			e1 = f.e1;
			e2 = f.e2;
			e3 = f.e3;
			e4 = f.e4;
			mark = f.mark;
		}
		
		public QuadFace(Wface f, Wedge[] wedges) {
			int e = f.edge;
			v4 = wedges[wedges[e].hedge].vertex;
			if (e < wedges.length/2) {
				e4 = e;
			} else {
				e4 = wedges[e].hedge;
			}
			v1 = wedges[e].vertex;
			e = wedges[e].next;
			if (e < wedges.length/2) {
				e1 = e;
			} else {
				e1 = wedges[e].hedge;
			}
			v2 = wedges[e].vertex;
			e = wedges[e].next;
			if (e < wedges.length/2) {
				e2 = e;
			} else {
				e2 = wedges[e].hedge;
			}
			v3 = wedges[e].vertex;
			e = wedges[e].next;
			if (e < wedges.length/2) {
				e3 = e;
			} else {
				e3 = wedges[e].hedge;
			}
			mark = SUBDIVIDE;
		}

		/** Given another face, return the index of the edge it shares with this one, or -1 if
		 they do not share an edge. */

		public int getSharedFace(QuadFace f) {
			if (f.e1 == e1 || f.e2 == e1 || f.e3 == e1 || f.e4 == e1)
				return e1;
			if (f.e1 == e2 || f.e2 == e2 || f.e3 == e2 || f.e4 == e2)
				return e2;
			if (f.e1 == e3 || f.e2 == e3 || f.e3 == e3 || f.e4 == e3)
				return e3;
			if (f.e1 == e4 || f.e2 == e4 || f.e3 == e4 || f.e4 == e4)
				return e4;
			return -1;
		}
		
		public String toString() {
			String markString="";
			if (mark == SUBDIVIDE) {
				markString = "SUBDIVIDE";
			} else if (mark == YV2) {
				markString = "YV2";
			} else if (mark == YV4) {
				markString = "YV4";
			} else if (mark == FINAL) {
				markString = "FINAL";
			}
			return "verts: " + v1 + " " + v2 + " " +v3 + " " + v4 + " edges: " + e1 + " " + e2 + " " + e3 + " " + e4 + " " + markString;
			//return "verts: " + v1 + " edges: " + e1 + " " + e2 + " " + e3 + " " + e4 + " " + markString;
		}
	}

	private QuadVertex[] vertices;

	private QuadEdge[] edges;

	private QuadFace[] faces;

	private BoundingBox bounds;

	private RenderingMesh cachedMesh;

	private WireframeMesh cachedWire;
	
	private int[] projectedEdges;
	
	public final static int MAX_SMOOTHNESS = 11;
	
	long t1, t2, t3, t4, t5, t6, t7;
	
//	static {
//		//debug stuff
//		QuadVertex[] v = new QuadVertex[4];
//		v[0] = new QuadVertex(new Vec3(0,0,1));
//		v[0].firstEdge = 0;
//		v[1] = new QuadVertex(new Vec3(1,0,1));
//		v[1].firstEdge = 1;
//		v[2] = new QuadVertex(new Vec3(1,1,1));
//		v[2].firstEdge = 2;
//		v[3] = new QuadVertex(new Vec3(0,1,1));
//		v[3].firstEdge = 3;
//		QuadEdge[] e = new QuadEdge[4];
//		e[0] = new QuadEdge(1, 0, -1);
//		e[0].f2 = 0;
//		e[1] = new QuadEdge(2, 1, -1);
//		e[1].f2 = 0;
//		e[2] = new QuadEdge(2, 3, 0);
//		e[3] = new QuadEdge(3, 0, 0);
//		QuadFace[] f = new QuadFace[1];
//		f[0] = new QuadFace(0, 1, 2, 3, 0, 1, 2, 3);
//		QuadMesh m = new QuadMesh(v, e, f);
//		m.smoothMesh(1.0, false);
//		System.out.println("********");
//	}

	private QuadMesh() {
	}

	public QuadMesh(QuadVertex[] verts, QuadEdge[] edges, QuadFace[] faces) {
		super();
		this.vertices = verts;
		this.edges = edges;
		this.faces = faces;
	}

	public QuadMesh(DataInputStream in, Scene scene) throws IOException,
			InvalidObjectException {
		super(in, scene);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void applyPoseKeyframe(Keyframe arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void copyObject(Object3D object) {
		QuadMesh mesh = (QuadMesh) object;
		vertices = new QuadVertex[mesh.vertices.length];
		for (int i = 0; i < vertices.length; i++) {
			vertices[i] = new QuadVertex(mesh.vertices[i]);
		}
		edges = new QuadEdge[mesh.edges.length];
		for (int i = 0; i < edges.length; i++) {
			edges[i] = new QuadEdge(mesh.edges[i]);
		}
		faces = new QuadFace[mesh.faces.length];
		for (int i = 0; i < faces.length; i++) {
			faces[i] = mesh.faces[i];
		}
	}

	@Override
	public Object3D duplicate() {
		QuadMesh mesh = new QuadMesh();
		mesh.copyObject(this);
		return mesh;
	}

	@Override
	public BoundingBox getBounds() {
		if (bounds == null)
			findBounds();
		return bounds;
	}
	
	@Override
	public RenderingMesh getRenderingMesh(double tol, boolean interactive, ObjectInfo info) {
		if (interactive && cachedMesh != null)
			return cachedMesh;
		return null;
	}

	/** Calculate the (approximate) bounding box for the mesh. */
	private void findBounds() {
		double minx, miny, minz, maxx, maxy, maxz;
		Vec3 vert[];
		int i;

//		if (cachedMesh != null)
//			vert = cachedMesh.vert;
//		else if (cachedWire != null)
//			vert = cachedWire.vert;
//		else {
//			getWireframeMesh();
//			vert = cachedWire.vert;
//		}
		minx = maxx = vertices[0].r.x;
		miny = maxy = vertices[0].r.y;
		minz = maxz = vertices[0].r.z;
		for (i = 1; i < vertices.length; i++) {
			if (vertices[i].r.x < minx)
				minx = vertices[i].r.x;
			if (vertices[i].r.x > maxx)
				maxx = vertices[i].r.x;
			if (vertices[i].r.y < miny)
				miny = vertices[i].r.y;
			if (vertices[i].r.y > maxy)
				maxy = vertices[i].r.y;
			if (vertices[i].r.z < minz)
				minz = vertices[i].r.z;
			if (vertices[i].r.z > maxz)
				maxz = vertices[i].r.z;
		}
		bounds = new BoundingBox(minx, maxx, miny, maxy, minz, maxz);
	}

	@Override
	public Keyframe getPoseKeyframe() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public WireframeMesh getWireframeMesh() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
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
		//skeleton.scale(xscale, yscale, zscale);
		resetMesh();

	}

	private void resetMesh() {
		bounds = null;
		cachedMesh = null;
		cachedWire = null;
	}

	public int getFaceCount() {
		return faces.length;
	}

	public int getFaceVertexCount(int face) {
		return 4;
	}

	public int getFaceVertexIndex(int face, int vertex) {
		QuadFace f = faces[face];
		if (vertex == 0) {
			return f.v1;
		}
		if (vertex == 1) {
			return f.v2;
		}
		if (vertex == 3) {
			return f.v3;
		}
		return f.v4;
	}

	public MeshViewer createMeshViewer(MeshEditController controller,
			RowContainer rowContainer) {
		// TODO Auto-generated method stub
		return null;
	}

	/** Get an array of normal vectors.  This calculates a single normal for each vertex,
	 ignoring smoothness values. */

	public Vec3[] getNormals() {
		Vec3 faceNorm, norm[] = new Vec3[vertices.length];
		double length, dot;
		// Calculate a normal for each face, and average the face normals for each vertex.

		for (int i = 0; i < norm.length; i++)
			norm[i] = new Vec3();
		for (int i = 0; i < faces.length; i++) {
			Vec3 edge1 = vertices[faces[i].v2].r.minus(vertices[faces[i].v1].r);
			Vec3 edge2 = vertices[faces[i].v3].r.minus(vertices[faces[i].v2].r);
			Vec3 edge3 = vertices[faces[i].v4].r.minus(vertices[faces[i].v3].r);
			Vec3 edge4 = vertices[faces[i].v1].r.minus(vertices[faces[i].v4].r);
			edge1.normalize();
			edge2.normalize();
			edge3.normalize();
			edge4.normalize();
			faceNorm = edge1.cross(edge4);
			length = faceNorm.length();
			if (length != 0.0) {
				faceNorm.scale(-1.0 / length);
				dot = -edge1.dot(edge4);
				if (dot < -1.0) {
					dot = -1.0;
				} else if (dot > 1.0) {
					dot = 1.0;
				}
				norm[faces[i].v1].add(faceNorm.times(Math.acos(dot)));
			}
			faceNorm = edge2.cross(edge1);
			length = faceNorm.length();
			if (length != 0.0) {
				faceNorm.scale(-1.0 / length);
				dot = -edge2.dot(edge1);
				if (dot < -1.0) {
					dot = -1.0;
				} else if (dot > 1.0) {
					dot = 1.0;
				}
				norm[faces[i].v2].add(faceNorm.times(Math.acos(dot)));
			}
			faceNorm = edge3.cross(edge2);
			length = faceNorm.length();
			if (length != 0.0) {
				faceNorm.scale(-1.0 / length);
				dot = -edge3.dot(edge2);
				if (dot < -1.0) {
					dot = -1.0;
				} else if (dot > 1.0) {
					dot = 1.0;
				}
				norm[faces[i].v3].add(faceNorm.times(Math.acos(dot)));
			}
			faceNorm = edge4.cross(edge3);
			length = faceNorm.length();
			if (length != 0.0) {
				faceNorm.scale(-1.0 / length);
				dot = -edge4.dot(edge3);
				if (dot < -1.0) {
					dot = -1.0;
				} else if (dot > 1.0) {
					dot = 1.0;
				}
				norm[faces[i].v4].add(faceNorm.times(Math.acos(dot)));
			}
		}
		for (int i = 0; i < norm.length; i++)
			norm[i].normalize();
		return norm;
	}

	public Vec3[] getVertexPositions() {
		Vec3 v[] = new Vec3[vertices.length];
		for (int i = 0; i < v.length; i++)
			v[i] = new Vec3(vertices[i].r);
		return v;
	}

	public void setVertexPositions(Vec3 v[]) {
		for (int i = 0; i < v.length; i++)
			vertices[i].r = v[i];
		resetMesh();
	}

	public MeshVertex[] getVertices() {
		return vertices;
	}
	
	public QuadEdge[] getEdges() {
		return edges;
	}
	
	public QuadFace[] getFaces() {
		return faces;
	}
	
	public TriangleMesh convertToTriangleMesh(double tol) {
		Vec3[] vertArray = new Vec3[vertices.length];
		for (int i = 0; i < vertArray.length; ++i)
			vertArray[i] = (Vec3) vertices[i].r;
		int[][] facesArray = new int[2*faces.length][3];
		for (int i = 0; i < faces.length; i++) {
			facesArray[2*i][0] = faces[i].v1;
			facesArray[2*i][1] = faces[i].v2;
			facesArray[2*i][2] = faces[i].v3;
			facesArray[2*i+1][0] = faces[i].v1;
			facesArray[2*i+1][1] = faces[i].v3;
			facesArray[2*i+1][2] = faces[i].v4;
		}
		TriangleMesh triMesh =new TriangleMesh(vertArray, facesArray);
		triMesh.copyTextureAndMaterial(this);
		triMesh.setSmoothingMethod(TriangleMesh.APPROXIMATING);
//		 Compute the trimesh texture parameters.
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof VertexParameterValue) {
					newParamVal[i] = oldParamVal[i].duplicate();

				} else if (oldParamVal[i] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[i])
							.getValue();
					double newval[] = new double[facesArray.length];
					for (int j = 0; j < oldval.length; ++j) {
						newval[2*j] = oldval[j];
						newval[2*j+1] = oldval[j];
					}
					newParamVal[i] = new FaceParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
					double newval[][] = new double[facesArray.length][3];
					for (int j = 0; j < faces.length; ++j) {
						newval[2*j][0] = fvpv.getValue(j, 0);
						newval[2*j][1] = fvpv.getValue(j, 1);
						newval[2*j][2] = fvpv.getValue(j, 2);
						newval[2*j+1][0] = fvpv.getValue(j, 0);
						newval[2*j+1][1] = fvpv.getValue(j, 2);
						newval[2*j+1][2] = fvpv.getValue(j, 3);
					}	
					newParamVal[i] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[i] = oldParamVal[i].duplicate();
			}
			triMesh.setParameterValues(newParamVal);
		}
		return triMesh;		
	}
	
	public void smoothMesh(double tol, boolean calcProjectedEdges, int maxNs) {
		//printSize();
		t1 = t2 = t3 = t4 = t5 = t6 = t7 = 0;
		smoothMesh(tol, calcProjectedEdges, 0, null, maxNs);
	}
	
	public void smoothMesh(double tol, boolean calcProjectedEdges, int ns, int[] pe, int maxNs) {
		projectedEdges = pe;
		if (projectedEdges == null) {
			ns = 0;
		}
		t1 = t2 = t3 = t4 = t5 = t6 = t7 = 0;
		smoothMesh(tol, calcProjectedEdges, ns, maxNs);
	}
	
	private void smoothMesh(double tol, boolean calcProjectedEdges, int ns, int maxNs) {
		//debug
		//dumpMesh();
//		System.out.println("smoothing: " + ns);
//		long time1 = System.currentTimeMillis();
		Vec3[] normals = getNormals();
		//first, let's find which faces are subdivided, which are not and which
		//bear Ys in between subdivivided and still faces.
//		for (int i = 0; i < faces.length; i++) {
//			if (! (faces[i].mark == QuadFace.SUBDIVIDE)) {
//				System.out.println("face: " + i + " non sub");
//			} else {
//				System.out.println("face: " + i + " sub");
//			}
//				
//		}
		for (int i = 0; i < edges.length; i++) {
			edges[i].mark = false;
		}
		Stack<QuadEdge> stack = new Stack<QuadEdge>();
		//check for initial critical edges
		for (int i = 0; i < faces.length; i++) {
			checkEdge(i, faces[i].e1, stack);
			checkEdge(i, faces[i].e2, stack);
			checkEdge(i, faces[i].e3, stack);
			checkEdge(i, faces[i].e4, stack);
		}
		while (!stack.isEmpty()) {
			QuadEdge ed = (QuadEdge)stack.pop();
			if (ed.mark) {
				solveCriticalEdge(ed, stack);
			}
		}
		if (!stack.empty()) {
			System.out.println("stack not empty after solving crits");
		}
		//final check
		if (stack.empty()) {
			//check for initial critical edges
			for (int i = 0; i < faces.length; i++) {
				checkEdge(i, faces[i].e1, stack);
				checkEdge(i, faces[i].e2, stack);
				checkEdge(i, faces[i].e3, stack);
				checkEdge(i, faces[i].e4, stack);
			}
			if (!stack.empty()) {
				System.out.println("stack not empty after check");
			}
		}
//		long time2 = System.currentTimeMillis();
//		t1 += time2 - time1;
//		time1 = System.currentTimeMillis();
		//subdivided edges are marked and counted
		for (int i = 0; i < edges.length; i++) {
			edges[i].mark = false;
		}
		int edgeCount = 0;
		int face3count = 0;
		int face4count = 0;
		QuadFace face;
		for (int i = 0; i < faces.length; i++) {
			if (faces[i].mark != QuadFace.FINAL) {
				face = faces[i];
				switch (face.mark) {
				case QuadFace.SUBDIVIDE:
					if (!edges[face.e1].mark) {
						edges[face.e1].mark = true;
						edgeCount++;
					}
					if (!edges[face.e2].mark) {
						edges[face.e2].mark = true;
						edgeCount++;
					}
					if (!edges[face.e3].mark) {
						edges[face.e3].mark = true;
						edgeCount++;
					}
					if (!edges[face.e4].mark) {
						edges[face.e4].mark = true;
						edgeCount++;
					}
					face4count++;
					break;
				case QuadFace.YV2:
					if (!edges[face.e1].mark) {
						edges[face.e1].mark = true;
						edgeCount++;
					}
					if (!edges[face.e2].mark) {
						edges[face.e2].mark = true;
						edgeCount++;
					}
					face3count++;
					break;
				case QuadFace.YV4:
					if (!edges[face.e3].mark) {
						edges[face.e3].mark = true;
						edgeCount++;
					}
					if (!edges[face.e4].mark) {
						edges[face.e4].mark = true;
						edgeCount++;
					}
					face3count++;
					break;
				}
			}
		}
		//catmull clark like subdivision (BLZ algorithm)
		QuadVertex[] nverts = new QuadVertex[vertices.length + edgeCount + face3count + face4count];
		boolean[] moveVerts = new boolean[nverts.length];
		QuadEdge[] nedges = new QuadEdge[edges.length + edgeCount + 4*face4count + 3*face3count];
		QuadFace[] nfaces = new QuadFace[faces.length - (face3count + face4count) + 4*face4count + 3*face3count];
		//System.out.println("edgesCount: " + edgeCount + " face3count: " + face3count + " face4count: " + face4count);
		//System.out.println("ntable: " + nverts.length + " " + nedges.length + " " + nfaces.length);
		//builds a table for edge subdivision
		int[] edgeTable = new int[edges.length];
		int index = 0;
		for (int i = 0; i < edgeTable.length; i++) {
			if (edges[i].mark) {
				edgeTable[i] = index;
				index++;
			} else {
				edgeTable[i] = -1;
			}
		}
		index = 0;
		for (int i = 0; i < faces.length; i++) {
			if (faces[i].mark == QuadFace.SUBDIVIDE) {
				moveVerts[faces[i].v1] = true;
				moveVerts[faces[i].v2] = true;
				moveVerts[faces[i].v3] = true;
				moveVerts[faces[i].v4] = true;
				moveVerts[vertices.length + edgeTable[faces[i].e1]] = true;
				moveVerts[vertices.length + edgeTable[faces[i].e2]] = true;
				moveVerts[vertices.length + edgeTable[faces[i].e3]] = true;
				moveVerts[vertices.length + edgeTable[faces[i].e4]] = true;
			}
		}
		int[] npe = null;
		if (calcProjectedEdges) {
			npe = new int[nedges.length];
		}
//		time2 = System.currentTimeMillis();
//		t2 += time2 - time1;
//		time1 = System.currentTimeMillis();
		//compute new vertices
		//original vertices
		for (int i = 0; i < vertices.length; i++) {
			nverts[i] = new QuadVertex(vertices[i]);
		}
		//edge middles
		Vec3 r;
		index = 0;
		for (int i = 0; i < edges.length; i++) {
			if (edges[i].mark) {
				r = vertices[edges[i].v1].r.plus(vertices[edges[i].v2].r);
				r.scale(0.5);
				nverts[index+vertices.length] = new QuadVertex(r);
				nverts[index+vertices.length].firstEdge = edges.length + index;
				index++;
			}
		}
		//face centers
		index = 0;
		int fc = edges.length + edgeCount;
		for (int i = 0; i < faces.length; i++) {
			if (faces[i].mark != QuadFace.FINAL) {
				r = vertices[faces[i].v1].r.plus(vertices[faces[i].v2].r);
				r.add(vertices[faces[i].v3].r);
				r.add(vertices[faces[i].v4].r);
				r.scale(0.25);
				nverts[index + edgeCount + vertices.length] = new QuadVertex(r);
				nverts[index + edgeCount + vertices.length].firstEdge = fc;
				index++;
				if (faces[i].mark == QuadFace.SUBDIVIDE) {
					fc += 4;
				} else {
					fc += 3;
				}
			}			
		}
//		time2 = System.currentTimeMillis();
//		t3 += time2 - time1;
//		time1 = System.currentTimeMillis();
		//compute edge split at edge middles
		index = 0;
		for (int i = 0; i < edges.length; i++) {
			nedges[i] = new QuadEdge(edges[i]);
			if (edges[i].mark) {
				nedges[index + edges.length] = new QuadEdge(edges[i]);
				nedges[i].v2 = vertices.length + index;
				nedges[index + edges.length].v1 = vertices.length + index;
				nverts[nedges[i].v1].firstEdge = i;
				nverts[nedges[i].v2].firstEdge = i;
				nverts[nedges[index+edges.length].v2].firstEdge = index+edges.length;
				if (calcProjectedEdges) {
					if (projectedEdges == null) {
						npe[i] = i;
						npe[index + edges.length] = i;
					} else {
						npe[i] = projectedEdges[i];
						npe[index + edges.length] = projectedEdges[i];
					}
				}
				index++;
			} else if (calcProjectedEdges) {
				if (projectedEdges == null) {
					npe[i] = i;
				} else {
					npe[i] = projectedEdges[i];
				}
			}
		}
		//additional face edges
		index = 0;
		fc = 0;
		int faceStart = faces.length - (face3count + face4count);
		for (int i = 0; i < faces.length; i++) {
			if (faces[i].mark == QuadFace.SUBDIVIDE) {
				nedges[edges.length + edgeCount + fc] = new QuadEdge(
						edgeTable[faces[i].e1] + vertices.length, edgeCount
						+ vertices.length + index, faceStart + fc, faceStart + fc + 1);
				nedges[edges.length + edgeCount +  fc + 1] = new QuadEdge(
						edgeTable[faces[i].e2] + vertices.length, edgeCount
						+ vertices.length + index, faceStart + fc + 1, faceStart + fc + 2);
				nedges[edges.length + edgeCount +  + fc + 2] = new QuadEdge(
						edgeTable[faces[i].e3] + vertices.length, edgeCount
						+ vertices.length + index, faceStart + fc + 2, faceStart + fc + 3);
				nedges[edges.length + edgeCount + fc + 3] = new QuadEdge(
						edgeTable[faces[i].e4] + vertices.length, edgeCount
						+ vertices.length + index, faceStart + fc + 3, faceStart + fc);
				if (calcProjectedEdges) {
					npe[edges.length + edgeCount + fc] = -1;
					npe[edges.length + edgeCount + fc + 1] = -1;
					npe[edges.length + edgeCount + fc + 2] = -1;
					npe[edges.length + edgeCount + fc + 3] = -1;
				}
				fc += 4;
				index++;
			} else if (faces[i].mark == QuadFace.YV2) {
				nedges[edges.length + edgeCount + fc] = new QuadEdge(
						edgeTable[faces[i].e1] + vertices.length, edgeCount
						+ vertices.length + index, faceStart + fc, faceStart + fc + 1);
				nedges[edges.length + edgeCount +  fc + 1] = new QuadEdge(
						edgeTable[faces[i].e2] + vertices.length, edgeCount
						+ vertices.length + index, faceStart + fc + 1, faceStart + fc + 2);
				nedges[edges.length + edgeCount +  fc + 2] = new QuadEdge(
						faces[i].v4, edgeCount
						+ vertices.length + index, faceStart + fc + 2, faceStart + fc);
				if (calcProjectedEdges) {
					npe[edges.length + edgeCount + fc] = -1;
					npe[edges.length + edgeCount + fc + 1] = -1;
					npe[edges.length + edgeCount + fc + 2] = -1;
				}
				fc += 3;
				index++;
			}  else if (faces[i].mark == QuadFace.YV4) {
				nedges[edges.length + edgeCount + fc] = new QuadEdge(
						faces[i].v2, edgeCount
						+ vertices.length + index, faceStart + fc, faceStart + fc + 1);
				nedges[edges.length + edgeCount +  fc + 1] = new QuadEdge(
						edgeTable[faces[i].e3] + vertices.length, edgeCount
						+ vertices.length + index, faceStart + fc + 1, faceStart + fc + 2);
				nedges[edges.length + edgeCount +  fc + 2] = new QuadEdge(
						edgeTable[faces[i].e4] + vertices.length, edgeCount
						+ vertices.length + index, faceStart + fc + 2, faceStart + fc);
				if (calcProjectedEdges) {
					npe[edges.length + edgeCount + fc] = -1;
					npe[edges.length + edgeCount + fc + 1] = -1;
					npe[edges.length + edgeCount + fc + 2] = -1;
				}
				fc += 3;
				index++;
			}						
		}
		//compute new faces
		int fe1, fe2, fe3, fe4, fe5, fe6, fe7, fe8;
		index = 0;
		for (int i = 0; i < faces.length; i++) {
			if (faces[i].mark == QuadFace.FINAL) {
				face = nfaces[index] = new QuadFace(faces[i]);
				face.mark = QuadFace.FINAL;
				if (edges[faces[i].e1].f1 == i) {
					nedges[face.e1].f1 = index;
				} else if (edges[faces[i].e1].f2 == i) {
					nedges[face.e1].f2 = index;
				}
				if (edges[faces[i].e2].f1 == i) {
					nedges[face.e2].f1 = index;
				} else if (edges[faces[i].e2].f2 == i) {
					nedges[face.e2].f2 = index;
				}
				if (edges[faces[i].e3].f1 == i) {
					nedges[face.e3].f1 = index;
				} else if (edges[faces[i].e3].f2 == i) {
					nedges[face.e3].f2 = index;
				}
				if (edges[faces[i].e4].f1 == i) {
					nedges[face.e4].f1 = index;
				} else if (edges[faces[i].e4].f2 == i) {
					nedges[face.e4].f2 = index;
				}
				index++;
			}
		}
		index = fc = 0;
		for (int i = 0; i < faces.length; i++) {
			if (faces[i].mark == QuadFace.SUBDIVIDE) {
				if (edges[faces[i].e1].v1 == faces[i].v1) {
					fe1 = faces[i].e1;
					nedges[fe1].f1 = faceStart + fc;
					fe2 = edgeTable[faces[i].e1] + edges.length;
					nedges[fe2].f1 = faceStart + fc + 1;
				} else {
					fe1 = edgeTable[faces[i].e1] + edges.length;
					nedges[fe1].f2 = faceStart + fc;
					fe2 = faces[i].e1;
					nedges[fe2].f2 = faceStart + fc + 1;
				}
				if (edges[faces[i].e2].v1 == faces[i].v2) {
					fe3 = faces[i].e2;
					nedges[fe3].f1 = faceStart + fc + 1;
					fe4 = edgeTable[faces[i].e2] + edges.length;
					nedges[fe4].f1 = faceStart + fc + 2;
				} else {
					fe3 = edgeTable[faces[i].e2] + edges.length;
					nedges[fe3].f2 = faceStart + fc + 1;
					fe4 = faces[i].e2;
					nedges[fe4].f2 = faceStart + fc + 2;
				}
				if (edges[faces[i].e3].v1 == faces[i].v3) {
					fe5 = faces[i].e3;
					nedges[fe5].f1 = faceStart + fc + 2;
					fe6 = edgeTable[faces[i].e3] + edges.length;
					nedges[fe6].f1 = faceStart + fc + 3;
				} else {
					fe5 = edgeTable[faces[i].e3] + edges.length;
					nedges[fe5].f2 = faceStart + fc + 2;
					fe6 = faces[i].e3;
					nedges[fe6].f2 = faceStart + fc + 3;
				}
				if (edges[faces[i].e4].v1 == faces[i].v4) {
					fe7 = faces[i].e4;
					nedges[fe7].f1 = faceStart + fc + 3;
					fe8 = edgeTable[faces[i].e4] + edges.length;
					nedges[fe8].f1 = faceStart + fc;
				} else {
					fe7 = edgeTable[faces[i].e4] + edges.length;
					nedges[fe7].f2 = faceStart + fc + 3;
					fe8 = faces[i].e4;
					nedges[fe8].f2 = faceStart + fc;
				}
				nverts[faces[i].v1].firstEdge = fe1;
				nverts[faces[i].v2].firstEdge = fe3;
				nverts[faces[i].v3].firstEdge = fe5;
				nverts[faces[i].v4].firstEdge = fe7;
				nfaces[faceStart + fc] = new QuadFace(faces[i].v1, edgeTable[faces[i].e1]
						+ vertices.length, edgeCount + vertices.length + index,
						edgeTable[faces[i].e4] + vertices.length, fe1, edges.length + edgeCount + fc,
						edges.length + edgeCount + fc + 3, fe8);
				nfaces[faceStart + fc + 1] = new QuadFace(faces[i].v2, edgeTable[faces[i].e2]
						+ vertices.length, edgeCount + vertices.length + index,
						edgeTable[faces[i].e1] + vertices.length, fe3, edges.length + edgeCount + fc + 1,
						edges.length + edgeCount + fc, fe2);
				nfaces[faceStart + fc + 2] = new QuadFace(faces[i].v3, edgeTable[faces[i].e3]
						+ vertices.length, edgeCount + vertices.length + index,
						edgeTable[faces[i].e2] + vertices.length, fe5, edges.length + edgeCount + fc + 2,
						edges.length + edgeCount + fc + 1, fe4);
				nfaces[faceStart + fc + 3] = new QuadFace(faces[i].v4, edgeTable[faces[i].e4]
						+ vertices.length, edgeCount + vertices.length + index,
						edgeTable[faces[i].e3] + vertices.length, fe7, edges.length + edgeCount + fc + 3,
						edges.length + edgeCount + fc + 2, fe6);
				fc += 4;
				index++;
			} else if (faces[i].mark == QuadFace.YV4) {
				fe1 = faces[i].e1;
				if (edges[fe1].v1 == faces[i].v1) {
					nedges[fe1].f1 = faceStart + fc;
				} else {
					nedges[fe1].f2 = faceStart + fc;
				}
				fe2 = faces[i].e2;
				if (edges[fe2].v1 == faces[i].v2) {
					nedges[fe2].f1 = faceStart + fc + 1;
				} else {
					nedges[fe2].f2 = faceStart + fc + 1;
				}
				if (edges[faces[i].e3].v1 == faces[i].v3) {
					fe3 = faces[i].e3;
					nedges[fe3].f1 = faceStart + fc + 1;
					fe4 = edgeTable[faces[i].e3] + edges.length;
					nedges[fe4].f1 = faceStart + fc + 2;
				} else {
					fe3 = edgeTable[faces[i].e3] + edges.length;
					nedges[fe3].f2 = faceStart + fc + 1;
					fe4 = faces[i].e3;
					nedges[fe4].f2 = faceStart + fc + 2;
				}
				if (edges[faces[i].e4].v1 == faces[i].v4) {
					fe5 = faces[i].e4;
					nedges[fe5].f1 = faceStart + fc + 2;
					fe6 = edgeTable[faces[i].e4] + edges.length;
					nedges[fe6].f1 = faceStart + fc;
				} else {
					fe5 = edgeTable[faces[i].e4] + edges.length;
					nedges[fe5].f2 = faceStart + fc + 2;
					fe6 = faces[i].e4;
					nedges[fe6].f2 = faceStart + fc;
				}
				nverts[faces[i].v1].firstEdge = fe1;
				nverts[faces[i].v2].firstEdge = fe2;
				nverts[faces[i].v3].firstEdge = fe3;
				nverts[faces[i].v4].firstEdge = fe5;
				nfaces[faceStart + fc] = new QuadFace(faces[i].v1, faces[i].v2,
				    edgeCount + vertices.length + index, edgeTable[faces[i].e4] + vertices.length,
				    fe1, edges.length + edgeCount + fc, edges.length + edgeCount + fc + 2, fe6);
				nfaces[faceStart + fc + 1] = new QuadFace(faces[i].v2, faces[i].v3, edgeTable[faces[i].e3]
				    + vertices.length, edgeCount + vertices.length + index,
				    fe2, fe3, edges.length + edgeCount + fc + 1,
				    edges.length + edgeCount + fc);
				nfaces[faceStart + fc + 2] = new QuadFace(faces[i].v4,  edgeTable[faces[i].e4] + vertices.length,
				     edgeCount + vertices.length + index, edgeTable[faces[i].e3] + vertices.length,
				     fe5, edges.length + edgeCount + fc + 2, edges.length + edgeCount + fc + 1, fe4);
				nfaces[faceStart + fc].mark = QuadFace.FINAL;
				nfaces[faceStart + fc + 1].mark = QuadFace.FINAL;
				nfaces[faceStart + fc + 2].mark = QuadFace.FINAL;
				fc += 3;
				index++;
			} else if (faces[i].mark == QuadFace.YV2) {
				if (edges[faces[i].e1].v1 == faces[i].v1) {
					fe1 = faces[i].e1;
					nedges[fe1].f1 = faceStart + fc;
					fe2 = edgeTable[faces[i].e1] + edges.length;
					nedges[fe2].f1 = faceStart + fc + 1;
				} else {
					fe1 = edgeTable[faces[i].e1] + edges.length;
					nedges[fe1].f2 = faceStart + fc;
					fe2 = faces[i].e1;
					nedges[fe2].f2 = faceStart + fc + 1;
				}
				if (edges[faces[i].e2].v1 == faces[i].v2) {
					fe3 = faces[i].e2;
					nedges[fe3].f1 = faceStart + fc + 1;
					fe4 = edgeTable[faces[i].e2] + edges.length;
					nedges[fe4].f1 = faceStart + fc + 2;
				} else {
					fe3 = edgeTable[faces[i].e2] + edges.length;
					nedges[fe3].f2 = faceStart + fc + 1;
					fe4 = faces[i].e2;
					nedges[fe4].f2 = faceStart + fc + 2;
				}
				fe5 = faces[i].e3;
				if (edges[fe5].v1 == faces[i].v3) {
					nedges[fe5].f1 = faceStart + fc + 2;
				} else {
					nedges[fe5].f2 = faceStart + fc + 2;
				}
				fe6 = faces[i].e4;
				if (edges[fe6].v1 == faces[i].v4) {
					nedges[fe6].f1 = faceStart + fc;
				} else {
					nedges[fe6].f2 = faceStart + fc;
				}
				nverts[faces[i].v1].firstEdge = fe1;
				nverts[faces[i].v2].firstEdge = fe3;
				nverts[faces[i].v3].firstEdge = fe5;
				nverts[faces[i].v4].firstEdge = fe6;
				nfaces[faceStart + fc] = new QuadFace(faces[i].v1, edgeTable[faces[i].e1]
					+ vertices.length, edgeCount + vertices.length + index,
					faces[i].v4, fe1, edges.length + edgeCount + fc,
				    edges.length + edgeCount + fc + 2, fe6);
				nfaces[faceStart + fc + 1] = new QuadFace(faces[i].v2, edgeTable[faces[i].e2]
				    + vertices.length, edgeCount + vertices.length + index,
				    edgeTable[faces[i].e1] + vertices.length, fe3, edges.length + edgeCount + fc + 1,
				    edges.length + edgeCount + fc, fe2);
				nfaces[faceStart + fc + 2] = new QuadFace(faces[i].v3,  faces[i].v4,
				     edgeCount + vertices.length + index, edgeTable[faces[i].e2] + vertices.length,
				     fe5, edges.length + edgeCount + fc + 2, edges.length + edgeCount + fc + 1, fe4);
				nfaces[faceStart + fc].mark = QuadFace.FINAL;
				nfaces[faceStart + fc + 1].mark = QuadFace.FINAL;
				nfaces[faceStart + fc + 2].mark = QuadFace.FINAL;
				fc += 3;
				index++;
			}
		}
//		time2 = System.currentTimeMillis();
//		t4 += time2 - time1;
//		time1 = System.currentTimeMillis();
		double dist = 0;
		// BLZ algorithm
		//variables that describe how many hard edges there
		//are around a vertex. This will decide how old vertices
		//are moved
		int sharp;
		double weight;
		int numEdges;
		int[] hardEdge = new int[2];
		int[] hardEdgeToVert = new int[2];
		int[] sharpEdge = new int[2];
		int[] sharpEdgeToVert = new int[2];
		double[] smoothEdgeValue = new double[10];
		Vec3 pos;
		int count;
		int face1, face2, nextVert;
		double maxHard;
		sharpEdge[1] = sharpEdge[0] = -1;
		smoothEdgeValue[1] = smoothEdgeValue[0] = 1;
		int hardnum;
		Vec3 sharpPt;
		double smoothness;
		double maxDist = 0.0;
		for (int i = 0; i < vertices.length; ++i) {
			if (!moveVerts[i]) {
				//fixed vertex
				continue;
			}
			// adjacent polygons
			moveVerts[i] = false;
			int ve[] = getVertexEdges(vertices[i]);
			numEdges = ve.length;
			if (numEdges > smoothEdgeValue.length) {
				smoothEdgeValue = new double[ve.length];
			}
			hardnum = 0;
			sharp = 0;
			weight = 0;
			pos = new Vec3();
			count = 0;
			maxHard = 0.0;
			for (int j = 0; j < numEdges; ++j) {
				smoothEdgeValue[j] = 0.0;
				if (edges[ve[j]].v1 == i) {
					face1 = edges[ve[j]].f1;
					face2 = edges[ve[j]].f2;
					nextVert = edges[ve[j]].v2;
				} else {
					face1 = edges[ve[j]].f2;
					face2 = edges[ve[j]].f1;
					nextVert = edges[ve[j]].v1;
				}
				if (face1 != -1) {
					r = vertices[faces[face1].v1].r.plus(vertices[faces[face1].v2].r);
					r.add(vertices[faces[face1].v3].r);
					r.add(vertices[faces[face1].v4].r);
					r.scale(0.25);
					pos.add(r);
					pos.subtract(vertices[nextVert].r.times(1.0 / 4.0));
					pos.subtract(vertices[getPreviousVertex(i, face1)].r.times(1.0 / 4.0));
					pos.subtract(vertices[i].r.times(1.0 / 4.0));
					++count;
				}
				pos.add(vertices[nextVert].r.times(3.0 / 2.0));
				smoothness = edges[ve[j]].smoothness;
				if (face1 != -1 && face2 != -1)
					smoothness = (1.0 - smoothness) * MAX_SMOOTHNESS;
				else
					smoothness = MAX_SMOOTHNESS; // boundary edges are treated as hard edges.
				if (ns + 1 <= smoothness) {
					if (sharp < 2) {
						sharpEdge[sharp] = ve[j];
						sharpEdgeToVert[sharp] = nextVert;
					}
					++sharp;
				} else if (ns < smoothness) {
					weight += smoothness - ns;
					smoothEdgeValue[j] = smoothness - ns;
					if (smoothEdgeValue[j] > maxHard) {
						maxHard = smoothEdgeValue[j];
					}
					if (hardnum < 2)
					{
						hardEdge[hardnum] = ve[j];
						hardEdgeToVert[hardnum] = nextVert;
					}
					++hardnum;
				}
			}
			pos.scale(1.0 / ((double) count * count));
			pos.add(vertices[i].r.times(1.0 - 3.0 / (2.0 * count) - 1.0 / (4.0 * count)));
			if (vertices[i].type != Wvertex.CORNER) {
				switch (sharp) {
				case 0:
					if (hardnum == 2) {
						weight /= 2;
						sharpPt = new Vec3(vertices[i].r.times(0.75));
						sharpPt.add(vertices[hardEdgeToVert[0]].r.times(0.125));
						sharpPt.add(vertices[hardEdgeToVert[1]].r.times(0.125));
						pos = pos.times(1 - weight).plus(sharpPt.times(weight));
					} else if (hardnum > 2) {
						weight /= hardnum;
						pos = pos.times(1 - weight).plus(nverts[i].r.times(weight));
					}
					break;
				case 1:
					if (hardnum == 1) {
						pos = pos.times(1 - maxHard).plus(vertices[i].r.times(maxHard));
					} else if (hardnum > 1) {
						weight /= hardnum;
						pos = pos.times(1 - weight).plus(vertices[i].r.times(weight));
					}
					break;
				case 2:
					sharpPt = new Vec3(vertices[i].r.times(0.75));
					sharpPt.add(vertices[sharpEdgeToVert[0]].r.times(0.125));
					sharpPt.add(vertices[sharpEdgeToVert[1]].r.times(0.125));
					if (hardnum == 0) {
						pos = sharpPt;
					}
					else {
						weight /= hardnum;
						pos = sharpPt.times(1 - weight).plus(vertices[i].r.times(weight));
					}
					break;
				default:
					//new vertex is marked as corner
					nverts[i].type = Wvertex.CORNER;
					pos = new Vec3(vertices[i].r);
					break;
				}
				if (nverts[i].type != Wvertex.CORNER) {
					dist = Math.abs(pos.minus(nverts[i].r).dot(normals[i]));
					if (dist > tol) {
						moveVerts[i] = true;
					}
					if (dist > maxDist) {
						maxDist = dist;
					}
					nverts[i].r = pos;
				}
			}
		}
//		time2 = System.currentTimeMillis();
//		t5 += time2 - time1;
//		time1 = System.currentTimeMillis();
		//compute mid edge vertices location
		int v1, v2, v3, v4, v5, v6;
		Vec3 v1r, v2r, v3r, v4r, v5r, v6r, pt2;
		double gamma;
		for (int i = 0; i < edges.length; ++i) {
			index = edgeTable[i];
			if (index < 0) {
				//unsplit edge
				continue;
			}
			if (!moveVerts[vertices.length + index]) {
				//unmoved vert
				continue;
			}
			moveVerts[vertices.length + index] = false;
			v1 = edges[i].v1;
			v2 = edges[i].v2;
			face1 = edges[i].f1;
			face2 = edges[i].f2;
			if (face1 == -1 || face2 == -1) {
				continue;
			}
			v5 = getNextVertex(v2, face1);
			v3 = getPreviousVertex(v1, face1);
			v4 = getNextVertex(v1, face2);
			v6 = getPreviousVertex(v2, face2);
			v1r = vertices[v1].r;
			v2r = vertices[v2].r;
			v3r = vertices[v3].r;
			v4r = vertices[v4].r;
			v5r = vertices[v5].r;
			v6r = vertices[v6].r;
			smoothness = edges[i].smoothness;
			smoothness = (1.0 - smoothness) * MAX_SMOOTHNESS;
			pos = null;
			gamma = 3.0 / 8.0;
			if (vertices[v1].type == Wvertex.CREASE) {
				int k = getVertexEdges(vertices[v1]).length;
				gamma = 3.0 / 8.0 - Math.cos(Math.PI / (double) k) / 4.0;
			}
			if (vertices[v2].type == Wvertex.CREASE) {
				int k = getVertexEdges(vertices[v2]).length;
				gamma = 3.0 / 8.0 + Math.cos(Math.PI / (double) k) / 4.0;
			}
			if (ns + 1 <= smoothness) {
				continue;
			} else if (ns < smoothness) {
				pos = v1r.times(0.75 - gamma);
				pos.add(v2r.times(gamma));
				pt2 = new Vec3(v3r);
				pt2.add(v4r);
				pt2.add(v5r);
				pt2.add(v6r);
				pt2.scale(0.0625);
				pos.add(pt2);
				pos = nverts[index + vertices.length].r.times(smoothness - ns).plus(pos.times(1 - (smoothness - ns)));
			} else {
				pos = v1r.times(0.75 - gamma);
				pos.add(v2r.times(gamma));
				pt2 = new Vec3(v3r);
				pt2.add(v4r);
				pt2.add(v5r);
				pt2.add(v6r);
				pt2.scale(0.0625);
				pos.add(pt2);
			}
			//dist = pos.distance(nverts[index+vertices.length].r);
			v1r = normals[v1].plus(normals[v2]);
			v1r.normalize();
			dist = Math.abs(pos.minus(nverts[index+vertices.length].r).dot(v1r));
			if ( dist > tol ) {
				moveVerts[index+vertices.length] = true;
			}
			if (dist > maxDist) {
				maxDist = dist;
			}
			nverts[index+vertices.length].r = pos;
		}
		boolean refine = false;
		for (int i = 0; i < nfaces.length; i++) {
			if (nfaces[i].mark == QuadFace.FINAL) {
				continue;
			}
			if (moveVerts[nfaces[i].v1] || moveVerts[nfaces[i].v2] || moveVerts[nfaces[i].v3]
			                    || moveVerts[nfaces[i].v4]  ) {
				refine = true;
				nfaces[i].mark = QuadFace.SUBDIVIDE;
			} else {
				nfaces[i].mark = QuadFace.FINAL;
			}
		}
//		time2 = System.currentTimeMillis();
//		t6 += time2 - time1;
//		time1 = System.currentTimeMillis();
		//compute new texture parameters
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[i]).getValue();
					double newval[] = new double[nfaces.length];
					index = 0;
					fc = 0;
					for (int j = 0; j < faces.length; j++) {
						if (faces[j].mark == QuadFace.FINAL) {
							newval[index] = oldval[j];
							index++;
						} else if (faces[j].mark == QuadFace.SUBDIVIDE) {
							newval[faceStart + fc] = oldval[j];
							newval[faceStart + fc + 1] = oldval[j];
							newval[faceStart + fc + 2] = oldval[j];
							newval[faceStart + fc + 3] = oldval[j];
							fc += 4;
						} else if (faces[j].mark == QuadFace.YV2 ||
								faces[j].mark == QuadFace.YV4) {
							newval[faceStart + fc] = oldval[j];
							newval[faceStart + fc + 1] = oldval[j];
							newval[faceStart + fc + 2] = oldval[j];
							fc += 3;
						}
					}
					newParamVal[i] = new FaceParameterValue(newval);
				} else if (oldParamVal[i] instanceof VertexParameterValue) {
					double oldval[] = ((VertexParameterValue) oldParamVal[i]).getValue();
					double newval[] = new double[nverts.length];
					for (int j = 0; j < vertices.length; ++j) {
						newval[j] = oldval[j];
					}
					for (int j = 0; j < edges.length; ++j) {
						index = edgeTable[j];
						if (index < 0) {
							continue;
						}
						newval[index+vertices.length] = (oldval[edges[j].v1] + oldval[edges[j].v2])/2;
					}
					index = 0;
					for (int j = 0; j < faces.length; ++j) {
						if (faces[j].mark != QuadFace.FINAL) {
							newval[index+edgeCount+vertices.length] = (oldval[faces[j].v1] + oldval[faces[j].v2] + 
								oldval[faces[j].v3] + oldval[faces[j].v4])/4;
							index++;
						}
					}
					newParamVal[i] = new VertexParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					double center, val1, val2, val3, val4, nv1, nv2, nv3, nv4;
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
					double newval[][] = new double[nfaces.length][];
					fc = 0;
					index = 0;
					for (int j = 0; j < faces.length; ++j) {
						if (faces[j].mark == QuadFace.FINAL) {
							newval[index] = new double[4];
							for(int k = 0; k < 4; k++) {
								newval[index][k] = fvpv.getValue(j, k);
							}
							index++;
							continue;
						}
						center = val1 = fvpv.getValue(j, 0);
						center += val2 = fvpv.getValue(j, 1);
						center += val3 = fvpv.getValue(j, 2);
						center += val4 = fvpv.getValue(j, 3);
						center /= 4;
						nv1 = (val1 + val2)*0.5;
						nv2 = (val2 + val3)*0.5;
						nv3 = (val3 + val4)*0.5;
						nv4 = (val4 + val1)*0.5;
						if (faces[j].mark == QuadFace.SUBDIVIDE) {
							newval[faceStart + fc] = new double[] { val1, nv1, center, nv4 };
							newval[faceStart + fc + 1] = new double[] { val2, nv2, center, nv1 };
							newval[faceStart + fc + 2] = new double[] { val3, nv3, center, nv2 };
							newval[faceStart + fc + 3] = new double[] { val4, nv4, center, nv3 };
							fc += 4;
						} else if (faces[j].mark == QuadFace.YV2) {
							newval[faceStart + fc] = new double[] { val1, nv1, center, val4 };
							newval[faceStart + fc + 1] = new double[] { val2, nv2, center, nv1 };
							newval[faceStart + fc + 2] = new double[] { val3, val4, center, nv2 };
							fc += 3;
						}  else if (faces[j].mark == QuadFace.YV4) {
							newval[faceStart + fc] = new double[] { val1, val2, center, nv4 };
							newval[faceStart + fc + 1] = new double[] { val2, val3, nv3, center};
							newval[faceStart + fc + 2] = new double[] { val4, nv4, center, nv3 };
							fc += 3;
						}
					}
					newParamVal[i] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[i] = oldParamVal[i].duplicate();
			}
			setParameterValues(newParamVal);
		}
//		time2 = System.currentTimeMillis();
//		t7 += time2 - time1;
//		time1 = System.currentTimeMillis();
		vertices = nverts;
		edges = nedges;
		faces = nfaces;
		projectedEdges = npe ;
		resetMesh();
//		System.out.println("subdivs: " + (ns + 1) + " " + maxDist);
//		System.out.println("vertices : " + vertices.length);
		//maxNs = 0;
		if (refine && ns < MAX_SMOOTHNESS - 1 && ns < maxNs) {
			smoothMesh(tol, calcProjectedEdges, ns+1, maxNs);
		}
//		if (ns == 0 || ns == 1) {
//			System.out.println("vertices : " + vertices.length);
//			System.out.println( "t1: " + t1);
//			System.out.println( "t2: " + t2);
//			System.out.println( "t3: " + t3);
//			System.out.println( "t4: " + t4);
//			System.out.println( "t5: " + t5);
//			System.out.println( "t6: " + t6);
//			System.out.println( "t7: " + t7);
//		}
	}
	
	private void solveCriticalEdge(QuadEdge ed, Stack<QuadEdge> stack) {
		QuadFace f1 = faces[ed.f1];
		QuadFace f2 = faces[ed.f2];
		//first, find out wich face edge is edge "ed"
		int k1;
		if (edges[f1.e1] == ed) {
			k1 = 1;
		} else if (edges[f1.e2] == ed) {
			k1 = 2;
		} else if (edges[f1.e3] == ed) {
			k1 = 3;
		} else {
			k1 = 4;
		}
		int k2;
		if (edges[f2.e1] == ed) {
			k2 = 1;
		} else if (edges[f2.e2] == ed) {
			k2 = 2;
		} else if (edges[f2.e3] == ed) {
			k2 = 3;
		} else {
			k2 = 4;
		}
		ed.mark = false;
		//we've got to get through all 16 cases
		if (f1.mark == QuadFace.SUBDIVIDE && f2.mark == QuadFace.SUBDIVIDE) {
			//nothing to do
			return;
		}
		if (f1.mark == QuadFace.SUBDIVIDE && f2.mark == QuadFace.YV2) {
			//check compatibility, put f2 to SUBDIVIDE if needed
			if (k2 > 2) {
				//Subdivision required, -> subdivision on edge 3 and 4
				f2.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f2, f2.e3, stack);
				checkEdge(ed.f2, f2.e4, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.SUBDIVIDE && f2.mark == QuadFace.YV4) {
			//check compatibility, put f2 to SUBDIVIDE if needed
			if (k2 < 3) {
				//YV2 required, -> subdivision on edge 1 and 2
				f2.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f2, f2.e1, stack);
				checkEdge(ed.f2, f2.e2, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.SUBDIVIDE && f2.mark == QuadFace.FINAL) {
			//Put f2 to YV2 or YV4
			if (k2 < 3) {
				//YV2 required, -> subdivision on edge 1 and 2
				f2.mark = QuadFace.YV2;
				checkEdge(ed.f2, f2.e1, stack);
				checkEdge(ed.f2, f2.e2, stack);
			} else {
				//YV4 required, -> subdivision on edge 3 and 4
				f2.mark = QuadFace.YV4;
				checkEdge(ed.f2, f2.e3, stack);
				checkEdge(ed.f2, f2.e4, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.YV2 && f2.mark == QuadFace.SUBDIVIDE) {
			//check compatibility, put f1 to SUBDIVIDE if needed
			if (k1 > 2) {
				//YV4 required, -> subdivision on edge 3 and 4
				f1.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f1, f1.e3, stack);
				checkEdge(ed.f1, f1.e4, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.YV2 && f2.mark == QuadFace.YV2) {
			//check compatibility, put f1 or f2 to SUBDIVIDE if needed
			if (k1 > 2 && k2 < 3) {
				//Subdivided first face
				f1.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f1, f1.e3, stack);
				checkEdge(ed.f1, f1.e4, stack);
			} else if (k1 < 3 && k2 > 2) {
				//Subdivided second face
				f2.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f2, f2.e3, stack);
				checkEdge(ed.f2, f2.e4, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.YV2 && f2.mark == QuadFace.YV4) {
			//check compatibility, put f1 or f2 to SUBDIVIDE if needed
			if (k1 > 2 && k2 > 2) {
				//Subdivided first face
				f1.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f1, f1.e3, stack);
				checkEdge(ed.f1, f1.e4, stack);
			} else if (k1 < 3 && k2 < 3) {
				//Subdivided second face
				f2.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f2, f2.e1, stack);
				checkEdge(ed.f2, f2.e2, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.YV2 && f2.mark == QuadFace.FINAL) {
			//Put f2 to YV2 or YV4
			if (k1 < 3 && k2 < 3) {
				//put f2 to YV2
				f2.mark = QuadFace.YV2;
				checkEdge(ed.f2, f2.e1, stack);
				checkEdge(ed.f2, f2.e2, stack);
			}
			if (k1 < 3 && k2 > 2) {
				//put f2 to YV4
				f2.mark = QuadFace.YV4;
				checkEdge(ed.f2, f2.e3, stack);
				checkEdge(ed.f2, f2.e4, stack);
			}			
			return;
		}
		if (f1.mark == QuadFace.YV4 && f2.mark == QuadFace.SUBDIVIDE) {
			//check compatibility, put f1 to SUBDIVIDE if needed
			if (k1 < 3) {
				f1.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f1, f1.e1, stack);
				checkEdge(ed.f1, f1.e2, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.YV4 && f2.mark == QuadFace.YV2) {
			//check compatibility, put f1 or f2 to SUBDIVIDE if needed
			if (k1 < 3 && k2 < 3) {
				//Subdivided first face
				f1.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f1, f1.e1, stack);
				checkEdge(ed.f1, f1.e2, stack);
			} else if (k1 > 2 && k2 > 2) {
				//Subdivided second face
				f2.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f2, f2.e3, stack);
				checkEdge(ed.f2, f2.e4, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.YV4 && f2.mark == QuadFace.YV4) {
			//check compatibility, put f1 or f2 to SUBDIVIDE if needed
			if (k1 < 3 && k2 > 2) {
				//Subdivided first face
				f1.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f1, f1.e1, stack);
				checkEdge(ed.f1, f1.e2, stack);
			} else if (k1 > 2 && k2 < 3) {
				//Subdivided second face
				f2.mark = QuadFace.SUBDIVIDE;
				checkEdge(ed.f2, f2.e1, stack);
				checkEdge(ed.f2, f2.e2, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.YV4 && f2.mark == QuadFace.FINAL) {
			//Put f2 to YV2 or YV4
			if (k1 > 2 && k2 < 3) {
				//Put second face to YV2
				f2.mark = QuadFace.YV2;
				checkEdge(ed.f2, f2.e1, stack);
				checkEdge(ed.f2, f2.e2, stack);
			} else if (k1 > 2 && k2 > 2) {
				//Put second face to YV4
				f2.mark = QuadFace.YV4;
				checkEdge(ed.f2, f2.e3, stack);
				checkEdge(ed.f2, f2.e4, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.FINAL && f2.mark == QuadFace.SUBDIVIDE) {
			//check compatibility, put f1 to YV2 or YV4 if needed
			if (k1 < 3) {
				//YV2 required, -> subdivision on edge 1 and 2
				f1.mark = QuadFace.YV2;
				checkEdge(ed.f1, f1.e1, stack);
				checkEdge(ed.f1, f1.e2, stack);
			} else {
				//YV4 required, -> subdivision on edge 3 and 4
				f1.mark = QuadFace.YV4;
				checkEdge(ed.f1, f1.e3, stack);
				checkEdge(ed.f1, f1.e4, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.FINAL && f2.mark == QuadFace.YV2) {
			//check compatibility, put f1 to YV2 or YV4 if needed
			if (k1 < 3 && k2 < 3) {
				//YV2 required, -> subdivision on edge 1 and 2
				f1.mark = QuadFace.YV2;
				checkEdge(ed.f1, f1.e1, stack);
				checkEdge(ed.f1, f1.e2, stack);
			} else if (k1 > 2 && k2 < 3) {
				//YV4 required, -> subdivision on edge 3 and 4
				f1.mark = QuadFace.YV4;
				checkEdge(ed.f1, f1.e3, stack);
				checkEdge(ed.f1, f1.e4, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.FINAL && f2.mark == QuadFace.YV4) {
			//check compatibility, put f1 to YV2 or YV4 if needed
			if (k1 < 3 && k2 > 2) {
				//YV2 required, -> subdivision on edge 1 and 2
				f1.mark = QuadFace.YV2;
				checkEdge(ed.f1, f1.e1, stack);
				checkEdge(ed.f1, f1.e2, stack);
			} else if (k1 > 2 && k2 > 2) {
				//YV4 required, -> subdivision on edge 3 and 4
				f1.mark = QuadFace.YV4;
				checkEdge(ed.f1, f1.e3, stack);
				checkEdge(ed.f1, f1.e4, stack);
			}
			return;
		}
		if (f1.mark == QuadFace.FINAL && f2.mark == QuadFace.FINAL) {
			System.out.println("solve edge for final and final, now there's a problem.");
			return;
		}
	}

	private void checkEdge(int f, int e, Stack<QuadEdge> stack) {
		if (f == -1) {
			System.out.println("check edge terror f == -1");
			return;
		}
		int fp;
		if (edges[e].f1 == f) {
			fp = edges[e].f2;
		} else if (edges[e].f2 == f) {
			fp = edges[e].f1;
		} else {
			System.out.println("check edge terror egde doesn't share face");
			return;
		}
		if (fp == -1) {
			//boundaries aren't critical edges
			return;
		}
		//check if face 1 is subdivided, either full subdiv or Y subdiv
		boolean subdiv1 = (faces[f].mark == QuadFace.SUBDIVIDE);
		subdiv1 |= (faces[f].mark == QuadFace.YV2 && (e == faces[f].e1 || e == faces[f].e2));
		subdiv1 |= (faces[f].mark == QuadFace.YV4 && (e == faces[f].e3 || e == faces[f].e4));
		//same for face 2
		boolean subdiv2 = (faces[fp].mark == QuadFace.SUBDIVIDE);
		subdiv2 |= (faces[fp].mark == QuadFace.YV2 && (e == faces[fp].e1 || e == faces[fp].e2));
		subdiv2 |= (faces[fp].mark == QuadFace.YV4 && (e == faces[fp].e3 || e == faces[fp].e4));
		//if face 1 and 2 subdiv differ and one of them is true,
		//then the edge is critical and placed onto stack
		//(if not already)
		if (subdiv1 != subdiv2 && (subdiv1 || subdiv2) ) {
			if (!edges[e].mark) {
				edges[e].mark =true;
				stack.add(edges[e]);
			}
		} else {
			edges[e].mark = false;
		}
	}

	public int getPreviousVertex(int vertex, int face) {
		QuadFace f = faces[face];
		if (vertex == f.v1) {
			return f.v4;
		} else if (vertex == f.v2) {
			return f.v1;
		} else if (vertex == f.v3) {
			return f.v2;
		} else if (vertex == f.v4) {
			return f.v3;
		}
		return -1;
	}
	
	public int getNextVertex(int vertex, int face) {
		QuadFace f = faces[face];
		if (vertex == f.v1) {
			return f.v2;
		} else if (vertex == f.v2) {
			return f.v3;
		} else if (vertex == f.v3) {
			return f.v4;
		} else if (vertex == f.v4) {
			return f.v1;
		}
		return -1;
	}

	public RenderingMesh getRenderingMesh() {
		
		Vec3[] vertArray = new Vec3[vertices.length];
		for (int i = 0; i < vertArray.length; ++i)
			vertArray[i] = (Vec3) vertices[i].r;

		// The mesh needs to be smooth shaded, so we need to calculate the normal vectors.
		// There may be more than one normal associated with a vertex, if that vertex is
		// on a crease.  Begin by finding a "true" normal for each face.
		// see TriangleMesh.getRenderingMesh() for original code
		Vec3 trueNorm[] = new Vec3 [faces.length];
		for (int i = 0; i < faces.length; i++) {
			trueNorm[i] = vertices[faces[i].v3].r.minus(vertices[faces[i].v1].r).cross(
					vertices[faces[i].v4].r.minus(vertices[faces[i].v2].r));
			double length = trueNorm[i].length();
			if (length > 0.0) {
				trueNorm[i].scale(1.0/length);
			}
		}

	    Vector<Vec3> norm = new Vector<Vec3>();
	    int[] facenorm = new int [faces.length*4];
	    int normals = 0;
	    
        // Now loop over each vertex.
		int[] ed;
		int f;
		QuadFace tmpFace;
		QuadEdge tmpEdge;
		int loop, index, first;
        int faceIndex, otherFace;
        int m, last;
        for (int i = 0; i < vertices.length; i++) {
            ed = getVertexOrderedEdges(vertices[i]);
            
            // If this vertex is a corner or a crease, we can just set its normal to null.            
            if (vertices[i].type  == QuadVertex.CORNER || vertices[i].type  == QuadVertex.CREASE)  {
            	norm.addElement(null);
            	for (int j = 0; j < ed.length; j++) {
            		f = edges[ed[j]].f1;
            		tmpFace = faces[f];
            		if (tmpFace.v1 == i) {
            			facenorm[4*f] = normals;
            		} else if (tmpFace.v2 == i) {
            			facenorm[4*f+1] = normals;
            		} else if (tmpFace.v3 == i) {
            			facenorm[4*f+2] = normals;
            		} else {
            			facenorm[4*f+3] = normals;
            		}
            		f = edges[ed[j]].f2;
            		if (f != -1) {
            			if (tmpFace.v1 == i) {
            				facenorm[4*f] = normals;
            			} else if (tmpFace.v2 == i) {
            				facenorm[4*f+1] = normals;
            			} else if (tmpFace.v3 == i) {
            				facenorm[4*f+2] = normals;
            			} else {
            				facenorm[4*f+3] = normals;
            			}
            		}
            	}
            	normals++;
            	continue;
            }
            
            // If any of the edges intersecting this vertex are creases, we need to start at
            // one of them.
            for ( loop = 0, index = -1; loop < ed.length; loop++) {
            	tmpEdge = edges[ed[loop]];
            	if (tmpEdge.f2 == -1 || tmpEdge.smoothness < 1.0f){
            		if (index != -1)
            			break;
            		index = loop;
            	}
            }

            if (loop == ed.length) {
            	// There are 0 or 1 crease edges intersecting this vertex, so we will use
            	// the same normal for every face.  Find it by averaging the normals of all
            	// the faces sharing this point.

            	Vec3 temp = new Vec3();
            	faceIndex = -1;
            	for (int j = 0; j < ed.length; j++) {
            		tmpEdge = edges[ed[j]];
            		faceIndex = (tmpEdge.f1 == faceIndex ? tmpEdge.f2 : tmpEdge.f1);
            		otherFace = (tmpEdge.f1 == faceIndex ? tmpEdge.f2 : tmpEdge.f1);
            		tmpFace = faces[faceIndex];
            		// TODO 
            		Vec3 edge1 = vertices[tmpFace.v2].r.minus(vertices[tmpFace.v1].r);
            		Vec3 edge2 = vertices[tmpFace.v3].r.minus(vertices[tmpFace.v2].r);
            		Vec3 edge3 = vertices[tmpFace.v4].r.minus(vertices[tmpFace.v3].r);
            		Vec3 edge4 = vertices[tmpFace.v1].r.minus(vertices[tmpFace.v4].r);
            		edge1.normalize();
            		edge2.normalize();
            		edge3.normalize();
            		edge4.normalize();
            		double dot;
            		if (tmpFace.v1 == i) {
            			facenorm[4*faceIndex] = normals;
            			dot = -edge4.dot(edge1);
            		} else if (tmpFace.v2 == i) {
            			facenorm[4*faceIndex+1] = normals;
            			dot = -edge1.dot(edge2);
            		} else if (tmpFace.v3 == i) {
            			facenorm[4*faceIndex+2] = normals;
            			dot = -edge2.dot(edge3);
            		} else {
            			facenorm[4*faceIndex+3] = normals;
            			dot = -edge3.dot(edge4);
            		}
            		if (dot < -1.0)
            			dot = -1.0; // This can occassionally happen due to roundoff error
            		if (dot > 1.0)
            			dot = 1.0;
            		temp.add(trueNorm[faceIndex].times(Math.acos(dot)));
            		if (otherFace != -1) {
            			tmpFace = faces[otherFace];
            			if (tmpFace.v1 == i) {
            				facenorm[4*otherFace] = normals;
            				dot = -edge4.dot(edge1);
            			} else if (tmpFace.v2 == i) {
            				facenorm[4*otherFace+1] = normals;
            				dot = -edge1.dot(edge2);
            			} else if (tmpFace.v3 == i) {
            				facenorm[4*otherFace+2] = normals;
            				dot = -edge2.dot(edge3);
            			} else {
            				facenorm[4*otherFace+3] = normals;
            				dot = -edge3.dot(edge4);
            			}
            		}
            	}
            	temp.normalize();
            	norm.addElement(temp);
            	normals++;
            	continue;
            }

            // This vertex is intersected by at least two crease edges, so we need to
            // calculate a normal vector for each group of faces between two creases.
            
            first = loop = index;
            tmpEdge = edges[ed[loop]];
groups:     do {
                Vec3 temp = new Vec3();
                do {
                    // For each group of faces, find the first and last edges.  Average
                    // the normals of the faces in between, and record that these faces 
                    // will use this normal.

                    loop = (loop+1) % ed.length;
                    m = tmpEdge.f1;
                    tmpFace = faces[m];
                    if (tmpFace.e1 != ed[loop] && tmpFace.e2 != ed[loop] && tmpFace.e3 != ed[loop] && tmpFace.e4 != ed[loop]) {
                        m = tmpEdge.f2;
                        if (m == -1)
                          break groups;
                        tmpFace = faces[m];
                    }
                    Vec3 edge1 = vertices[tmpFace.v2].r.minus(vertices[tmpFace.v1].r);
            		Vec3 edge2 = vertices[tmpFace.v3].r.minus(vertices[tmpFace.v2].r);
            		Vec3 edge3 = vertices[tmpFace.v4].r.minus(vertices[tmpFace.v3].r);
            		Vec3 edge4 = vertices[tmpFace.v1].r.minus(vertices[tmpFace.v4].r);
            		edge1.normalize();
            		edge2.normalize();
            		edge3.normalize();
            		edge4.normalize();
            		double dot;
            		if (tmpFace.v1 == i) {
            			facenorm[4*m] = normals;
            			dot = -edge4.dot(edge1);
            		} else if (tmpFace.v2 == i) {
            			facenorm[4*m+1] = normals;
            			dot = -edge1.dot(edge2);
            		} else if (tmpFace.v3 == i) {
            			facenorm[4*m+2] = normals;
            			dot = -edge2.dot(edge3);
            		} else {
            			facenorm[4*m+3] = normals;
            			dot = -edge3.dot(edge4);
            		}
                    if (dot < -1.0)
                      dot = -1.0; // This can occassionally happen due to roundoff error
                    if (dot > 1.0)
                      dot = 1.0;
                    temp.add(trueNorm[m].times(Math.acos(dot)));
                    tmpEdge = edges[ed[loop]];
                  } while (tmpEdge.f2 != -1 && tmpEdge.smoothness == 1.0f);
                last = loop;
                temp.normalize();
                norm.addElement(temp);
                normals++;
                loop = first = last;
                tmpEdge = edges[ed[first]];
              } while (last != index);
          }
            
        // Finally, assemble all the normals into an array and create the triangles.
            
        Vec3[] normalArray = new Vec3 [norm.size()];
        for (int i = 0; i < normalArray.length; i++) {
        	normalArray[i] = norm.elementAt(i);
        }
        
		TextureMapping texMapping = getTextureMapping();
		RenderingMesh renderingMesh = null;
		RenderingTriangle[] tri = new RenderingTriangle[2*faces.length];
		for (int i = 0; i < faces.length; ++i) {
			tri[2*i] = texMapping.mapTriangle(faces[i].v1, faces[i].v2,
					faces[i].v3, facenorm[4*i], facenorm[4*i+1], facenorm[4*i+2],
					vertArray);
			tri[2*i+1] = texMapping.mapTriangle(faces[i].v1, faces[i].v3,
					faces[i].v4, facenorm[4*i], facenorm[4*i+2], facenorm[4*i+3],
					vertArray);
		}
		renderingMesh = new RenderingMesh(vertArray, normalArray, tri,
				texMapping, getMaterialMapping());
		ParameterValue oldParamVal[] = getParameterValues();
		if (oldParamVal != null) {
			ParameterValue newParamVal[] = new ParameterValue[oldParamVal.length];
			for (int i = 0; i < oldParamVal.length; i++) {
				if (oldParamVal[i] instanceof FaceParameterValue) {
					double oldval[] = ((FaceParameterValue) oldParamVal[i]).getValue();
					double newval[] = new double[2*faces.length];
					for (int j = 0; j < oldval.length; ++j) {
						newval[2*j] = oldval[j];
						newval[2*j+1] = oldval[j];
					}
					newParamVal[i] = new FaceParameterValue(newval);
				} else if (oldParamVal[i] instanceof FaceVertexParameterValue) {
					FaceVertexParameterValue fvpv = (FaceVertexParameterValue) oldParamVal[i];
					double newval[][] = new double[2*faces.length][3];
					for (int j = 0; j < faces.length; ++j) {
						newval[2*j][0] = fvpv.getValue(j, 0);
						newval[2*j][1] = fvpv.getValue(j, 1);
						newval[2*j][2] = fvpv.getValue(j, 2);
						newval[2*j+1][0] = fvpv.getValue(j, 0);
						newval[2*j+1][1] = fvpv.getValue(j, 2);
						newval[2*j+1][2] = fvpv.getValue(j, 3);
						
					}
					newParamVal[i] = new FaceVertexParameterValue(newval);
				} else
					newParamVal[i] = oldParamVal[i].duplicate();
			}
			renderingMesh.setParameters(newParamVal);
		}
		return renderingMesh;
	}

	public void setSkeleton(Skeleton skeleton) {
		// TODO Auto-generated method stub
	}
	
	/**
	 * Returns the edges aroun a given vertex, unordered
	 * @param v The vertex to find edges for
	 * @return The edges array
	 */
	public int[] getVertexEdges(QuadVertex v) {
		return getVertexEdges(v, -1, false);
	}
	
	/**
	 * Returns the edges aroun a given vertex, ordered
	 * Ordering can be clockwise or anti-clockwise
	 * @param v The vertex to find edges for
	 * @return The edges array
	 */
	public int[] getVertexOrderedEdges(QuadVertex v) {
		return getVertexEdges(v, -1, true);
	}
	
	private int[] getVertexEdges(QuadVertex v, int numEdges, boolean order) {
		int count = 0;
		int[] vertEdges = null;
		if (numEdges > 0) {
			vertEdges = new int[numEdges];
		}
		int e = v.firstEdge;
		int estart = e;
		count = 1;
		if (numEdges > 0) {
			vertEdges[0] = e;
		}
		QuadFace f;
		boolean notDone = true;
		boolean finished = false;
		int index = 0;
		while(notDone && index <= edges.length + 1) {
			f = null;
			if (vertices[edges[e].v1] == v) {
				if (edges[e].f1 != -1) {
					f = faces[edges[e].f1];
				}
			} else {
				if (edges[e].f2 != -1) {
					f = faces[edges[e].f2];
				}
			}
			if (f != null) {
				if (f.e1 == e) {
					e = f.e4;
				} else if (f.e2 == e) {
					e = f.e1;
				} else if (f.e3 == e) {
					e = f.e2;
				} else if (f.e4 == e) {
					e = f.e3;
				}
				if (e != estart) {
					if (numEdges > 0) {
						vertEdges[count] = e;
					}
					++count;
				} else {
					//we're done
					finished = true;
					notDone = false;
				}
			} else {
				//we're done for this side of the starting edge
				notDone = false;
			}
			index++;
		}
		if (!finished) {
			//boundary edge
			//we have to check the other side of the starting edge
			//firt let's reverse the first set if we're storing edges
			if (numEdges > 0 && order) {
				int[] newVertEdges = new int[numEdges];
				for (int i = 0; i < count; i++) {
					newVertEdges[i] = vertEdges[count - 1 - i];
				}
				vertEdges = newVertEdges;
			}
			//here we go again
			e = estart;
			index = 0;
			notDone = true;
			while(notDone  && index <= edges.length + 1) {
				f = null;
				//start with other face
				if (vertices[edges[e].v1] == v) {
					if (edges[e].f2 != -1) {
						f = faces[edges[e].f2];
					}
				} else {
					if (edges[e].f1 != -1) {
						f = faces[edges[e].f1];
					}
				}
				if (f != null) {
					if (f.e1 == e) {
						e = f.e2;
					} else if (f.e2 == e) {
						e = f.e3;
					} else if (f.e3 == e) {
						e = f.e4;
					} else if (f.e4 == e) {
						e = f.e1;
					}
					if (e != estart) {
						if (numEdges > 0) {
							vertEdges[count] = e;
						}
						++count;
					} else {
						//we're done, except we should encounter a boundary
						int vert = -1;
						for (int i = 0; i < vertices.length; i++) {
							if (vertices[i] == v) {
								vert = i;
							}
						}
						System.out.println("problem with find edges around a vertex: boundary not met. " + vert);
						notDone = false;
					}
				} else {
					//we're done for this side of the starting edge
					notDone = false;
				}
				index++;
			}
		}
		if (numEdges < 1) {
			return getVertexEdges(v, count, order);
		} else {
			return vertEdges;
		}
	}
	
	public int[] getProjectedEdges() {
		return projectedEdges;
	}
	
	/**
	 * Prints out the mesh to the console
	 *
	 */
	public void dumpMesh() {
		System.out.println("vertices:");
		for (int i = 0; i < vertices.length; i++) {
			System.out.println(i + ": " + vertices[i]);
		}
		System.out.println("edges:");
		for (int i = 0; i < edges.length; i++) {
			System.out.println(i + ": " + edges[i]);
		}
		System.out.println("faces:");
		for (int i = 0; i < faces.length; i++) {
			System.out.println(i + ": " + faces[i]);
		}
	}
	
	public void printSize() {
		System.out.println(vertices.length + " verts (" + 
				vertices.length * 50 + "), " + edges.length + " edges (" +
				edges.length * 28 + "), " + faces.length + " faces (" +
				faces.length * 24 + "), for a total of: " + (vertices.length * 54 + edges.length * 28 + faces.length * 24 )  + " bytes");
	}

	public void setProjectedEdges(int[] projectedEdges) {
		this.projectedEdges = projectedEdges;	
	}
}
