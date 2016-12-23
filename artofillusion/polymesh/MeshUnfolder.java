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

import java.util.ArrayList;
import java.util.Date;
import java.util.Stack;
import java.util.Vector;

import buoy.widget.BTextArea;
import artofillusion.math.Vec2;
import artofillusion.math.Vec3;
import artofillusion.object.FacetedMesh;
import artofillusion.object.TriangleMesh;
import artofillusion.object.TriangleMesh.Face;
import artofillusion.object.TriangleMesh.Vertex;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedEdge;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedFace;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedVertex;
import artofillusion.ui.Translate;
import no.uib.cipr.matrix.*;
import no.uib.cipr.matrix.sparse.*;

public class MeshUnfolder {
	protected FacetedMesh mesh; // the mesh to unfold

	protected TriangleMesh trimesh; // the trimesh version of the mesh to

	// unfold

	private double[] angles; // mesh angles, 3 angles for each triangle

	private double[] weights;

	private double[] var; // variables, i.e. angles plus Lagrange

	// multipliers

	private double gradient[];

	private CompRowMatrix j2matrix; // J2 matrix as defined in ABF++

	FlexCompRowMatrix jstar; // J* matrix as defined in ABF++

	FlexCompRowMatrix jstarstar; // J** matrix as defined in ABF++

	FlexCompRowMatrix matrix; // matrix of linear system to solve

	DenseVector bb2vec; // right hand of linear system to solve

	double[] lambdaStar; // diagonal of lambda matrix

	double[] lambdaStarInv; // diagonal of invers lambda matrix

	private int[][] j2nonzero; // per row array of array to non zero column

	// elements

	private int[][] angleTable; // angle references for each interior vertex

	private int[][] anglePTable; // angle references for each interior

	// vertex, plus one (see ABF++)

	private int[][] angleMTable; // angle references for each interior

	// vertex, minus one (see ABF++)

	private int[] interiorVertices; // interior vertices array

	private double[] constraints; // constraints values

	private DenseVector weightsVec; // angle weights

	private int nint; // number of interior vertices

	private int ntri; // number of triangles

	private int nangles; // number of angles

	private double[] bstar; // ABF++ vector

	private double[] solution; // solution to full linear system

	private int[] invInteriorTable; // inverse table : given an interior

	// vertex, yields the index to vertex in
	// the mesh
	private UnfoldedMesh[] unfoldedMeshes; // the unfolded meshes resulting

	// from unfolding process

	private int[] vertexTable; // vertex table correspondance between

	// original mesh vertices and opened mesh vertices

	private int[] faceTable; // same for faces

	private FlexCompRowMatrix matTmat;

	/**
	 * Creates a new unfolder instance. This class unfolds triangle meshes.
	 * 
	 * @param mesh
	 *                The mesh to unfold (any kind of facetted mesh)
	 * @param trimesh
	 *                The triangle mesh version of original mesh (maybe
	 *                equal to mesh if mesh is a triangle mesh).
	 * @param vertexTable
	 *                Table to vertex indices prior to opening seams.
	 *                Correct vertex index will be placed in UnfoldedVertex
	 *                data. If a the vertexTable length is smaller than the
	 *                number of vertices, then these vertices are supposed
	 *                to be created for triangulation purposes and will be
	 *                assigned a vertex index of -1. They won't be displayed
	 *                by the UVMappingCanvas widget. This array may be null
	 *                if the mesh is a triangle mesh has no seams.
	 * @param faceTable
	 *                Table to face indices. If this array is not null, then
	 *                unfoldedFaces will be given an id corresponding to
	 *                this array. This allows to identify which facetted
	 *                face yielded a given trimesh face. This array may be
	 *                null if the mesh to unfold is already a triangle mesh.
	 *                Correct vertex index will be placed in UnfoldedVertex
	 *                data. If a the vertexTable length is smaller than the
	 *                number of vertices, then these vertices are supposed
	 *                to be created for triangulation purposes and will be
	 *                assigned a vertex index of -1. They won't be displayed
	 *                by the UVMappingCanvas widget.
	 */
	public MeshUnfolder(FacetedMesh mesh, TriangleMesh trimesh,
			int[] vertexTable, int[] faceTable) {
		this.mesh = mesh;
		this.trimesh = trimesh;
		this.vertexTable = vertexTable;
		this.faceTable = faceTable;
	}
	
	public boolean unfold(BTextArea textArea, double res) {
		//return unfoldAbf(textArea, res);
		return unfoldLinearAbf(textArea);
	}

	/**
	 * Unfolds the mesh using the ABF++ algorithm
	 * 
	 * @param res
	 *                The residual to reach
	 * @param textArea
	 *                The text area to print results to
	 * 
	 */
	public boolean unfoldAbf(BTextArea textArea, double res) {
		// dump mesh;
		TriangleMesh.Edge[] edges = trimesh.getEdges();
		int nedges = edges.length;
		long totaltime;
		QMR qmr = null;
		int ind[];
		int jnd[];
		double[] idata;
		double[] jdata;
		SparseVector JstarVecI;
		SparseVector JstarVecJ;
		SparseVector JstarstarVec;
		double value;
		int iter = 0;

		initialize();
		computeConstraints();
		computeJ2();
		computeGradient();
		double residual = computeResidual();
		textArea.append("Initial residual: " + residual + "\n");
		double mpi = Math.PI * 0.9999;
		totaltime = new Date().getTime();
		double epsilon = res * res;
		textArea.append("Target residual: " + res + "\n");
		while (residual > epsilon) {
			// bstar computation
			for (int i = 0; i < ntri; i++) {
				bstar[i] = constraints[i];
				for (int j = 0; j < 3; j++) {
					bstar[i] += (weights[3 * i + j] / 2) * -gradient[3 * i + j];
				}
			}
			for (int i = 0; i < nint; i++) {
				bstar[ntri + i] = constraints[ntri + i];
				bstar[ntri + nint + i] = constraints[ntri + nint + i];
				for (int j = 0; j < angleTable[i].length; j++) {
					bstar[ntri + i] += j2matrix.get(i, angleTable[i][j])
							* (weights[angleTable[i][j]] / 2)
							* -gradient[angleTable[i][j]];
					bstar[ntri + nint + i] += j2matrix.get(nint + i,
							anglePTable[i][j])
							* (weights[anglePTable[i][j]] / 2)
							* -gradient[anglePTable[i][j]];
					bstar[ntri + nint + i] += j2matrix.get(nint + i,
							angleMTable[i][j])
							* (weights[angleMTable[i][j]] / 2)
							* -gradient[angleMTable[i][j]];
				}
			}
			// jstar computation
			if (iter != 0) {
				jstar.zero();
			}
			int index;
			for (int i = 0; i < 2 * nint; i++) {
				for (int j = 0; j < j2nonzero[i].length; j++) {
					index = j2nonzero[i][j] / 3;
					jstar.add(i, index, j2matrix.get(i, j2nonzero[i][j])
							* (weights[j2nonzero[i][j]] / 2));
				}
			}
			if (iter != 0) {
				jstar.compact();
			}
			// jstarstar computation
			if (iter != 0) {
				jstarstar.zero();
			}
			for (int e = 0; e < nedges; e++) {
				int i = invInteriorTable[edges[e].v1];
				int j = invInteriorTable[edges[e].v2];
				if (i == -1 || j == -1)
					continue;
				value = jstarstarCompute(i, j);
				jstarstar.set(i, j, value);
				jstarstar.set(j, i, value);
				i += nint;
				value = jstarstarCompute(i, j);
				jstarstar.set(i, j, value);
				jstarstar.set(j, i, value);
				i -= nint;
				j += nint;
				value = jstarstarCompute(i, j);
				jstarstar.set(i, j, value);
				jstarstar.set(j, i, value);
				i += nint;
				value = jstarstarCompute(i, j);
				jstarstar.set(i, j, value);
				jstarstar.set(j, i, value);
			}
			for (int e = 0; e < nint; e++) {
				value = jstarstarCompute(e, e);
				jstarstar.set(e, e, value);
				value = jstarstarCompute(e + nint, e);
				jstarstar.set(e + nint, e, value);
				value = jstarstarCompute(e, e + nint);
				jstarstar.set(e, e + nint, value);
				value = jstarstarCompute(e + nint, e + nint);
				jstarstar.set(e + nint, e + nint, value);
			}
			if (iter == 0) {
				jstarstar.compact();
			}
			if (iter != 0) {
				matrix.zero();
				bb2vec.zero();
				matrix.zero();
			}
			for (int e = 0; e < nedges; e++) {
				int i = invInteriorTable[edges[e].v1];
				int j = invInteriorTable[edges[e].v2];
				if (i == -1 || j == -1)
					continue;
				addToMat(matrix, jstar, lambdaStarInv, i, j);
				addToMat(matrix, jstar, lambdaStarInv, i + nint, j);
				addToMat(matrix, jstar, lambdaStarInv, i, j + nint);
				addToMat(matrix, jstar, lambdaStarInv, i + nint, j + nint);
			}
			for (int i = 0; i < nint; i++) {
				addToMat(matrix, jstar, lambdaStarInv, i);
				addToMat(matrix, jstar, lambdaStarInv, i + nint, i);
				addToMat(matrix, jstar, lambdaStarInv, i + nint);
				JstarstarVec = jstarstar.getRow(i);
				jnd = JstarstarVec.getIndex();
				jdata = JstarstarVec.getData();
				for (int j = 0; j < jnd.length; j++) {
					matrix.add(i, jnd[j], -jdata[j]);
				}
				JstarstarVec = jstarstar.getRow(i + nint);
				jnd = JstarstarVec.getIndex();
				jdata = JstarstarVec.getData();
				for (int j = 0; j < jnd.length; j++) {
					matrix.add(i + nint, jnd[j], -jdata[j]);
				}
			}
			for (int i = 0; i < 2 * nint; i++) {
				JstarVecI = jstar.getRow(i);
				ind = JstarVecI.getIndex();
				idata = JstarVecI.getData();
				bb2vec.set(i, -bstar[i + ntri]);
				for (int k = 0; k < ind.length; k++) {
					bb2vec.add(i, idata[k] * lambdaStarInv[ind[k]]
							* bstar[ind[k]]);
				}
			}
			if (iter == 0) {
				matrix.compact();
			}
			DenseVector bb2sol = new DenseVector(2 * nint);
			if (qmr == null)
				qmr = new QMR(bb2sol);
			try {
				qmr.solve(matrix, bb2vec, bb2sol);
			} catch (IterativeSolverNotConvergedException e) {
				textArea.append("Failure : unfolding did not converge");
				e.printStackTrace();
				return false;
			}
			double[] newbb2sol = bb2sol.getData();
			for (int i = 0; i < 2 * nint; i++) {
				solution[4 * ntri + i] = newbb2sol[i];
			}
			for (int i = 0; i < ntri; i++) {
				solution[nangles + i] = lambdaStarInv[i] * bstar[i];
			}
			for (int i = 0; i < nangles; i++) {
				solution[i] = 0;
			}
			for (int j = 0; j < 2 * nint; j++) {
				JstarVecJ = jstar.getRow(j);
				jnd = JstarVecJ.getIndex();
				jdata = JstarVecJ.getData();
				for (int i = 0; i < jnd.length; i++) {
					solution[nangles + jnd[i]] -= lambdaStarInv[jnd[i]]
							* jdata[i] * newbb2sol[j];
				}
			}
			for (int i = 0; i < ntri; i++) {
				for (int k = 0; k < 3; k++) {
					solution[3 * i + k] += solution[i + nangles];
				}
			}
			for (int j = ntri; j < ntri + 2 * nint; j++) {
				for (int i = 0; i < j2nonzero[j - ntri].length; i++) {
					solution[j2nonzero[j - ntri][i]] += j2matrix.get(j - ntri,
							j2nonzero[j - ntri][i])
							* solution[j + nangles];
				}
			}
			for (int i = 0; i < nangles; i++) {
				solution[i] = (weights[i] / 2) * (-gradient[i] - solution[i]);
			}
			for (int i = 0; i < 3 * ntri; i++) {
				var[i] += solution[i];
				if (var[i] < 1e-3) {
					var[i] = 1e-3;
				} else if (var[i] > mpi) {
					var[i] = mpi;
				}
			}
			for (int i = 3 * ntri; i < var.length; i++) {
				var[i] += solution[i];
			}
			computeConstraints();
			computeJ2();
			computeGradient();
			residual = computeResidual();
			textArea.append("Iteration #" + (iter + 1) + ":\n");
			textArea
					.append((long) ((new Date().getTime() - totaltime) / 1000.0)
							+ "s elapsed\n");
			textArea.append("Residual: " + Math.sqrt(residual) + " against "
					+ res + "\n");
			++iter;
		}
		totaltime = new Date().getTime() - totaltime;
		textArea.append("Total unfolding time: "
				+ Math.round(totaltime / 1000.0) + "s\n\n");
		// now let's build the unfolded meshes
		textArea.append("Rebuilding 2D mesh.\n");
		Face[] faces = trimesh.getFaces();
		Vertex[] verts = (Vertex[]) trimesh.getVertices();
		ArrayList<UnfoldedMesh> unfoldedMeshesList = new ArrayList<UnfoldedMesh>();
		UnfoldedEdge[] uedges = new UnfoldedEdge[nedges];
		UnfoldedFace[] ufaces = new UnfoldedFace[trimesh.getFaces().length];
		UnfoldedVertex[] uverts = new UnfoldedVertex[trimesh.getVertices().length];
		// first let's put proper vertex ids
		// vertex with an id of -1 don't belong to original
		// facetted edge and are due to triangulation
		for (int i = 0; i < uverts.length; i++) {
			uverts[i] = new UnfoldedVertex(verts[i]);
			if (vertexTable != null) {
				if (i < vertexTable.length) {
					uverts[i].id = vertexTable[i];
				} else {
					uverts[i].id = -1;
				}
			} else {
				uverts[i].id = i;
			}

		}
		// now let's find if wich edges are visible
		// and which come from triangulation
		int[][] faceVertIndices = new int[ufaces.length][];
		int count;
		int meshFaceCount = mesh.getFaceCount();
		for (int i = 0; i < meshFaceCount; i++) {
			count = mesh.getFaceVertexCount(i);
			faceVertIndices[i] = new int[count];
			for (int j = 0; j < count; j++) {
				faceVertIndices[i][j] = mesh.getFaceVertexIndex(i, j);
			}
		}
		int next, f1, f2;
		for (int i = 0; i < uedges.length; i++) {
			uedges[i] = new UnfoldedEdge(edges[i]);
			uedges[i].hidden = true;
			f1 = uedges[i].f1;
			if (f1 != -1) {
				if (faceTable != null) {
					f1 = faceTable[f1];
				}
				for (int j = 0; j < faceVertIndices[f1].length; j++) {
					next = j + 1;
					if (next >= faceVertIndices[f1].length) {
						next = 0;
					}
					if ((uedges[i].v1 == faceVertIndices[f1][j] && uedges[i].v2 == faceVertIndices[f1][next])
							|| (uedges[i].v2 == faceVertIndices[f1][j] && uedges[i].v1 == faceVertIndices[f1][next])) {
						uedges[i].hidden = false;
					}
				}
			}
			f2 = uedges[i].f2;
			if (f2 != -1) {
				if (faceTable != null) {
					f2 = faceTable[f2];
				}
				for (int j = 0; j < faceVertIndices[f2].length; j++) {
					next = j + 1;
					if (next >= faceVertIndices[f2].length) {
						next = 0;
					}
					if ((uedges[i].v1 == faceVertIndices[f2][j] && uedges[i].v2 == faceVertIndices[f2][next])
							|| (uedges[i].v2 == faceVertIndices[f2][j] && uedges[i].v1 == faceVertIndices[f2][next])) {
						uedges[i].hidden = false;
					}
				}
			}
		}
		// if there is a non null faceTable,
		// use it to keep track of orignal, non triangulated
		// faces
		for (int i = 0; i < ufaces.length; i++) {
			ufaces[i] = new UnfoldedFace(faces[i]);
			if (faceTable != null) {
				ufaces[i].id = faceTable[i];
			} else {
				ufaces[i].id = i;
			}
		}
		// 2D mesh reconstruction per se
		// first follow edges to isolate separate mesh pieces
		// then find the vertices positions using computeUnfoldedMesh
		boolean[] unfoldedFace = new boolean[ufaces.length];
		boolean[] unfoldedVerts = new boolean[uverts.length];
		boolean done = false;
		Stack<Integer> edgeStack = new Stack<Integer>();
		ArrayList<Integer> vertList = new ArrayList<Integer>();
		ArrayList<Integer> edgeList = new ArrayList<Integer>();
		ArrayList<Integer> faceList = new ArrayList<Integer>();
		int index;
		int pieceCount = 0;
		while (!done) {
			edgeStack.empty();
			vertList.clear();
			edgeList.clear();
			faceList.clear();
			index = -1;
			for (int i = unfoldedFace.length - 1; i >= 0; i--) {
				if (!unfoldedFace[i]) {
					index = i;
					break;
				}
			}
			if (index == -1) {
				done = true;
			} else {
				textArea.append("Building piece #" + pieceCount + "...\n");
				totaltime = new Date().getTime();
				edgeStack.push(ufaces[index].e1);
				double dist = verts[ufaces[index].v1].r
						.distance(verts[ufaces[index].v2].r);
				uverts[ufaces[index].v1].r = new Vec2(0, 0);
				uverts[ufaces[index].v2].r = new Vec2(dist, 0);
				unfoldedVerts[ufaces[index].v1] = true;
				unfoldedVerts[ufaces[index].v2] = true;
				edgeList.add(ufaces[index].e1);
				vertList.add(ufaces[index].v1);
				vertList.add(ufaces[index].v2);
				int ed;
				while (!edgeStack.empty()) {
					ed = edgeStack.pop();
					f1 = uedges[ed].f1;
					f2 = uedges[ed].f2;
					if (f1 != -1 && !unfoldedFace[f1]) {
						computeFace(ed, f1, uverts, uedges, ufaces,
								unfoldedFace, unfoldedVerts, vertList,
								edgeList, faceList);
						if (ufaces[f1].e1 != ed) {
							edgeList.add(ufaces[f1].e1);
							edgeStack.push(ufaces[f1].e1);
						}
						if (ufaces[f1].e2 != ed) {
							edgeList.add(ufaces[f1].e2);
							edgeStack.push(ufaces[f1].e2);
						}
						if (ufaces[f1].e3 != ed) {
							edgeList.add(ufaces[f1].e3);
							edgeStack.push(ufaces[f1].e3);
						}
					}
					if (f2 != -1 && !unfoldedFace[f2]) {
						computeFace(ed, f2, uverts, uedges, ufaces,
								unfoldedFace, unfoldedVerts, vertList,
								edgeList, faceList);
						if (ufaces[f2].e1 != ed) {
							edgeList.add(ufaces[f2].e1);
							edgeStack.push(ufaces[f2].e1);
						}
						if (ufaces[f2].e2 != ed) {
							edgeList.add(ufaces[f2].e2);
							edgeStack.push(ufaces[f2].e2);
						}
						if (ufaces[f2].e3 != ed) {
							edgeList.add(ufaces[f2].e3);
							edgeStack.push(ufaces[f2].e3);
						}
					}
				}
				totaltime = new Date().getTime() - totaltime;
				textArea.append("separating piece: " + Math.round(totaltime / 1000) + "s\n");
				totaltime = new Date().getTime();
				try {
					unfoldedMeshesList.add(computeUnfoldedMesh(vertList,
							edgeList, faceList, uverts, uedges, ufaces));
				} catch (IterativeSolverNotConvergedException e) {
					textArea.append("Failure : unfolding did not converge");
					e.printStackTrace();
					return false;
				}
				totaltime = new Date().getTime() - totaltime;
				textArea.append("reconstruction done in "
						+ Math.round(totaltime / 1000) + "s\n");
				++pieceCount;
			}
		}
		unfoldedMeshes = new UnfoldedMesh[unfoldedMeshesList.size()];
		for (int i = 0; i < unfoldedMeshes.length; i++) {
			unfoldedMeshes[i] = unfoldedMeshesList.get(i);
			unfoldedMeshes[i].setName(Translate.text("polymesh:piece" + (i + 1)));
		}
		return true;
	}
	
	public boolean unfoldLinearAbf(BTextArea textArea) {
		textArea.append("Unfolding mesh...\n");
		// dump mesh;
		TriangleMesh.Edge[] edges = trimesh.getEdges();
		int nedges = edges.length;
		long totaltime;
		QMR qmr = null;
		
		totaltime = new Date().getTime();
		TriangleMesh.Vertex[] vertices = (Vertex[]) trimesh.getVertices();
		TriangleMesh.Face[] faces = trimesh.getFaces();
		ntri = faces.length;
		nangles = 3 * ntri;
		angles = new double[nangles];
		Vec3 v1r, v2r, v3r;
		for (int i = 0; i < ntri; i++) {
			v1r = vertices[faces[i].v3].r.minus(vertices[faces[i].v1].r);
			v2r = vertices[faces[i].v2].r.minus(vertices[faces[i].v1].r);
			v3r = vertices[faces[i].v3].r.minus(vertices[faces[i].v2].r);
			v1r.normalize();
			v2r.normalize();
			v3r.normalize();
			angles[i * 3] = Math.acos(v1r.dot(v2r));
			angles[i * 3 + 1] = Math.acos(-v2r.dot(v3r));
			angles[i * 3 + 2] = Math.acos(v1r.dot(v3r));
		}
		// setup interior vertices table
		invInteriorTable = new int[vertices.length];
		Vector interiorVerticesTable = new Vector();
		int count = 0;
		for (int i = 0; i < vertices.length; i++) {
			if (edges[vertices[i].firstEdge].f1 != -1
					&& edges[vertices[i].firstEdge].f2 != -1) {
				// not a boundary edge
				interiorVerticesTable.add(new Integer(i));
				invInteriorTable[i] = count++;
			} else
				invInteriorTable[i] = -1;
		}
		nint = interiorVerticesTable.size();
		interiorVertices = new int[nint];
		for (int i = 0; i < nint; i++) {
			interiorVertices[i] = ((Integer) interiorVerticesTable.get(i))
					.intValue();
		}
		interiorVerticesTable = null;
		angleTable = new int[nint][];
		for (int i = 0; i < nint; i++) {
			int v = interiorVertices[i];
			int[] ed = vertices[v].getEdges();
			angleTable[i] = new int[ed.length];
			// edges do not sequentially point to faces they delimit
			// so we need to find the faces looking at both sides
			// of an edge
			int[] tris = new int[ed.length];
			boolean add;
			int index = 0;
			int tri;
			for (int j = 0; ((j < ed.length) && (index < ed.length)); j++) {
				add = true;
				tri = edges[ed[j]].f1;
				for (int k = 0; k < ed.length; k++) {
					if (tris[k] == tri) {
						add = false;
						break;
					}
				}
				if (add)
					tris[index++] = tri;
				add = true;
				tri = edges[ed[j]].f2;
				for (int k = 0; k < ed.length; k++) {
					if (tris[k] == tri) {
						add = false;
						break;
					}
				}
				if (add)
					tris[index++] = tri;
			}
			// now setup angles at interior vertices
			// and plus and minus angles as defined in ABF++ paper
			for (int j = 0; j < ed.length; j++) {
				tri = tris[j];
				if (v == faces[tri].v1) {
					angleTable[i][j] = 3 * tri + 0;
				} else if (v == faces[tri].v2) {
					angleTable[i][j] = 3 * tri + 1;
				} else if (v == faces[tri].v3) {
					angleTable[i][j] = 3 * tri + 2;
				} else {
					System.out
							.println("pb setting angle tables: vertex not in face");
				}
			}
		}
		var = new double[nangles];
		for (int i = 0; i < nangles; i++) {
			var[i] = angles[i]; // start from current angle values
		}
		double anglesum;
		for (int i = 0; i < nint; i++) {
			anglesum = 0;
			for (int j = 0; j < angleTable[i].length; j++) {
				anglesum += angles[angleTable[i][j]];
			}
			if (anglesum > 0)
				for (int j = 0; j < angleTable[i].length; j++) {
					var[angleTable[i][j]] *= 2 * Math.PI / anglesum;
				}
		}
		constraints = new double[ntri + 2 * nint];
		//set up matrix and constraints
		//System.out.println(ntri+2*nint);
		FlexCompRowMatrix newMat = new FlexCompRowMatrix(ntri + 2*nint, 3*ntri);
		FlexCompRowMatrix newMatTMat = new FlexCompRowMatrix( 2*nint + ntri, 2*nint + ntri);
		double[] newConstraints = new double[ntri + 2 * nint];
		for (int i = 0; i < ntri; i++) {
			newConstraints[i]  = Math.PI;
		}
		for (int i = ntri; i < ntri + nint; i++) {
			newConstraints[i]  = 2*Math.PI;
		}
		for (int i = 0; i < faces.length; i++) {
			addToConstraints(newConstraints, i, faces[i].v1, faces[i].v2, faces[i].v3, 3*i,ntri, nint);
			addToConstraints(newConstraints, i, faces[i].v2, faces[i].v3, faces[i].v1, 3*i+1, ntri, nint);
			addToConstraints(newConstraints, i, faces[i].v3, faces[i].v1, faces[i].v2, 3*i+2, ntri, nint);
			addToMatTMat(newMat, newMatTMat, i, faces[i].v1, faces[i].v2, faces[i].v3, 3*i,ntri, nint);
			addToMatTMat(newMat, newMatTMat, i, faces[i].v2, faces[i].v3, faces[i].v1, 3*i+1, ntri, nint);
			addToMatTMat(newMat, newMatTMat, i, faces[i].v3, faces[i].v1, faces[i].v2, 3*i+2, ntri, nint);
		}
		/*for (int i = 0; i < ntri + 2*nint; i++) {
			System.out.println("c " + i + " : " + newConstraints[i]);
		}
		for (int i = 0; i < ntri + 2*nint; i++) {
			for (int j = 0; j < ntri + 2*nint; j++) {
				System.out.print(newMatTMat.get(i, j)+" ");
			}
			System.out.println(" ");
		}*/
		DenseVector sol = new DenseVector(ntri + 2*nint);
		CG cg = new CG(sol);
		DenseVector newcons = new DenseVector(newConstraints);
		try {
			cg.solve(newMatTMat, newcons, sol);
		} catch (IterativeSolverNotConvergedException e) {
			textArea.append("Failure : unfolding did not converge");
			e.printStackTrace();
			return false;
		}
		double[] soldata = sol.getData();
		/*for (int i = 0; i < ntri + 2*nint; i++) {
			System.out.println("sol " + i + " : " + soldata[i]);
		}*/
		for (int i = 0; i < var.length; i++) {
			var[i] = 0;
		}
		SparseVector matVecI;
		int[] ind;
		double[] idata;
		for (int i = 0; i < ntri + 2 * nint; i++) {
			matVecI = newMat.getRow(i);
			ind = matVecI.getIndex();
			idata = matVecI.getData();
			//System.out.println("Row : " + i);
			for (int j = 0; j < ind.length; j++) {
				//System.out.println("colonne " + ind[j] + " : " + idata[j]);
				var[ind[j]] += soldata[i]*idata[j];
			}
		}
		//System.out.println("result");	
		for (int i = 0; i < var.length; i++) {
			var[i] = (var[i]+1)*angles[i];
			//System.out.print(var[i]+" ");
		}
		//System.out.println(" ");	
		totaltime = new Date().getTime() - totaltime;
		textArea.append("Mesh unfolded : "
				+ Math.round(totaltime / 1000.0) + "s\n");
		totaltime = new Date().getTime();
		// now let's build the unfolded meshes
		textArea.append("Rebuilding 2D mesh.\n");
		Vertex[] verts = (Vertex[]) trimesh.getVertices();
		ArrayList<UnfoldedMesh> unfoldedMeshesList = new ArrayList<UnfoldedMesh>();
		UnfoldedEdge[] uedges = new UnfoldedEdge[nedges];
		UnfoldedFace[] ufaces = new UnfoldedFace[trimesh.getFaces().length];
		UnfoldedVertex[] uverts = new UnfoldedVertex[trimesh.getVertices().length];
		// first let's put proper vertex ids
		// vertex with an id of -1 don't belong to original
		// facetted edge and are due to triangulation
		for (int i = 0; i < uverts.length; i++) {
			uverts[i] = new UnfoldedVertex(verts[i]);
			if (vertexTable != null) {
				if (i < vertexTable.length) {
					uverts[i].id = vertexTable[i];
				} else {
					uverts[i].id = -1;
				}
			} else {
				uverts[i].id = i;
			}

		}
		// now let's find if wich edges are visible
		// and which come from triangulation
		int[][] faceVertIndices = new int[ufaces.length][];
		int meshFaceCount = mesh.getFaceCount();
		//int count;
		for (int i = 0; i < meshFaceCount; i++) {
			count = mesh.getFaceVertexCount(i);
			faceVertIndices[i] = new int[count];
			for (int j = 0; j < count; j++) {
				faceVertIndices[i][j] = mesh.getFaceVertexIndex(i, j);
			}
		}
		int next, f1, f2;
		for (int i = 0; i < uedges.length; i++) {
			uedges[i] = new UnfoldedEdge(edges[i]);
			uedges[i].hidden = true;
			f1 = uedges[i].f1;
			if (f1 != -1) {
				if (faceTable != null) {
					f1 = faceTable[f1];
				}
				for (int j = 0; j < faceVertIndices[f1].length; j++) {
					next = j + 1;
					if (next >= faceVertIndices[f1].length) {
						next = 0;
					}
					if ((uedges[i].v1 == faceVertIndices[f1][j] && uedges[i].v2 == faceVertIndices[f1][next])
							|| (uedges[i].v2 == faceVertIndices[f1][j] && uedges[i].v1 == faceVertIndices[f1][next])) {
						uedges[i].hidden = false;
					}
				}
			}
			f2 = uedges[i].f2;
			if (f2 != -1) {
				if (faceTable != null) {
					f2 = faceTable[f2];
				}
				for (int j = 0; j < faceVertIndices[f2].length; j++) {
					next = j + 1;
					if (next >= faceVertIndices[f2].length) {
						next = 0;
					}
					if ((uedges[i].v1 == faceVertIndices[f2][j] && uedges[i].v2 == faceVertIndices[f2][next])
							|| (uedges[i].v2 == faceVertIndices[f2][j] && uedges[i].v1 == faceVertIndices[f2][next])) {
						uedges[i].hidden = false;
					}
				}
			}
		}
		// if there is a non null faceTable,
		// use it to keep track of orignal, non triangulated
		// faces
		for (int i = 0; i < ufaces.length; i++) {
			ufaces[i] = new UnfoldedFace(faces[i]);
			if (faceTable != null) {
				ufaces[i].id = faceTable[i];
			} else {
				ufaces[i].id = i;
			}
		}
		// 2D mesh reconstruction per se
		// first follow edges to isolate separate mesh pieces
		// then find the vertices positions using computeUnfoldedMesh
		boolean[] unfoldedFace = new boolean[ufaces.length];
		boolean[] unfoldedVerts = new boolean[uverts.length];
		boolean done = false;
		Stack<Integer> edgeStack = new Stack<Integer>();
		ArrayList<Integer> vertList = new ArrayList<Integer>();
		ArrayList<Integer> edgeList = new ArrayList<Integer>();
		ArrayList<Integer> faceList = new ArrayList<Integer>();
		int index;
		int pieceCount = 0;
		while (!done) {
			edgeStack.empty();
			vertList.clear();
			edgeList.clear();
			faceList.clear();
			index = -1;
			for (int i = unfoldedFace.length - 1; i >= 0; i--) {
				if (!unfoldedFace[i]) {
					index = i;
					break;
				}
			}
			if (index == -1) {
				done = true;
			} else {
				textArea.append("Building piece #" + pieceCount + "...\n");
				totaltime = new Date().getTime();
				edgeStack.push(ufaces[index].e1);
				double dist = verts[ufaces[index].v1].r
						.distance(verts[ufaces[index].v2].r);
				uverts[ufaces[index].v1].r = new Vec2(0, 0);
				uverts[ufaces[index].v2].r = new Vec2(dist, 0);
				unfoldedVerts[ufaces[index].v1] = true;
				unfoldedVerts[ufaces[index].v2] = true;
				edgeList.add(ufaces[index].e1);
				vertList.add(ufaces[index].v1);
				vertList.add(ufaces[index].v2);
				int ed;
				while (!edgeStack.empty()) {
					ed = edgeStack.pop();
					f1 = uedges[ed].f1;
					f2 = uedges[ed].f2;
					if (f1 != -1 && !unfoldedFace[f1]) {
						computeFace(ed, f1, uverts, uedges, ufaces,
								unfoldedFace, unfoldedVerts, vertList,
								edgeList, faceList);
						if (ufaces[f1].e1 != ed) {
							edgeList.add(ufaces[f1].e1);
							edgeStack.push(ufaces[f1].e1);
						}
						if (ufaces[f1].e2 != ed) {
							edgeList.add(ufaces[f1].e2);
							edgeStack.push(ufaces[f1].e2);
						}
						if (ufaces[f1].e3 != ed) {
							edgeList.add(ufaces[f1].e3);
							edgeStack.push(ufaces[f1].e3);
						}
					}
					if (f2 != -1 && !unfoldedFace[f2]) {
						computeFace(ed, f2, uverts, uedges, ufaces,
								unfoldedFace, unfoldedVerts, vertList,
								edgeList, faceList);
						if (ufaces[f2].e1 != ed) {
							edgeList.add(ufaces[f2].e1);
							edgeStack.push(ufaces[f2].e1);
						}
						if (ufaces[f2].e2 != ed) {
							edgeList.add(ufaces[f2].e2);
							edgeStack.push(ufaces[f2].e2);
						}
						if (ufaces[f2].e3 != ed) {
							edgeList.add(ufaces[f2].e3);
							edgeStack.push(ufaces[f2].e3);
						}
					}
				}
				try {
					unfoldedMeshesList.add(computeUnfoldedMesh(vertList,
							edgeList, faceList, uverts, uedges, ufaces));
				} catch (IterativeSolverNotConvergedException e) {
					textArea.append("Failure : unfolding did not converge");
					e.printStackTrace();
					return false;
				}
				totaltime = new Date().getTime() - totaltime;
				textArea.append("reconstruction done in "
						+ Math.round(totaltime / 1000) + "s\n");
				++pieceCount;
			}
		}
		unfoldedMeshes = new UnfoldedMesh[unfoldedMeshesList.size()];
		for (int i = 0; i < unfoldedMeshes.length; i++) {
			unfoldedMeshes[i] = unfoldedMeshesList.get(i);
			unfoldedMeshes[i].setName(Translate.text("polymesh:piece" + (i + 1)));
		}
		return true;
	}

	private void addToConstraints(double[] constraints, int f, int v1, int v2, int v3,
			int a1, int ntri, int nint) {
		double alpha1 = angles[a1];
		double lsana1 = Math.log(Math.sin(alpha1));
		int interiorVertV1 = invInteriorTable[v1];
		int interiorVertV2 = invInteriorTable[v2];
		int interiorVertV3 = invInteriorTable[v3];
		constraints[f] -= alpha1;
		if (interiorVertV1 != -1) {
			constraints[ntri+interiorVertV1] -= alpha1;
		}
		if (interiorVertV2 != -1) {
			constraints[ntri+nint+interiorVertV2] += lsana1;
		}
		if (interiorVertV3 != -1) {
			constraints[ntri+nint+interiorVertV3] -= lsana1;
		}
	}
	
	private void addToMatTMat(FlexCompRowMatrix mat, FlexCompRowMatrix matTmat, int f, int v1, int v2, int v3,
			int a1, int ntri, int nint) {
		double alpha1 = angles[a1];
		double tana1 = alpha1/Math.tan(alpha1);
		int interiorVertV1 = invInteriorTable[v1];
		int interiorVertV2 = invInteriorTable[v2];
		int interiorVertV3 = invInteriorTable[v3];
		mat.add(f, a1, alpha1);
		matTmat.add(f, f, alpha1*alpha1 );
		if (interiorVertV1 != -1) {
			mat.add(ntri+interiorVertV1, a1, alpha1);
			matTmat.add(ntri+interiorVertV1, ntri+interiorVertV1, alpha1*alpha1);
			matTmat.add(ntri+interiorVertV1, f, alpha1*alpha1);
			matTmat.add(f, ntri+interiorVertV1, alpha1*alpha1);
		}
		if (interiorVertV2 != -1) {
			mat.add(ntri+nint+interiorVertV2, a1, -tana1);
			matTmat.add(ntri+nint+interiorVertV2, ntri+nint+interiorVertV2, tana1*tana1);
			matTmat.add(ntri+nint+interiorVertV2, f, -alpha1*tana1);
			matTmat.add(f, ntri+nint+interiorVertV2, -alpha1*tana1);
		}
		if (interiorVertV3 != -1) {
			mat.add(ntri+nint+interiorVertV3, a1, tana1);
			matTmat.add(ntri+nint+interiorVertV3, ntri+nint+interiorVertV3, tana1*tana1);
			matTmat.add(ntri+nint+interiorVertV3, f, alpha1*tana1);
			matTmat.add(f, ntri+nint+interiorVertV3, alpha1*tana1);
		}
		if (interiorVertV1 != -1 && interiorVertV2 != -1) {
			matTmat.add(ntri+interiorVertV1, ntri+nint+interiorVertV2, -alpha1*tana1);
			matTmat.add(ntri+nint+interiorVertV2, ntri+interiorVertV1, -alpha1*tana1);
		}
		if (interiorVertV1 != -1 && interiorVertV3 != -1) {
			matTmat.add(ntri+interiorVertV1, ntri+nint+interiorVertV3, alpha1*tana1);
			matTmat.add(ntri+nint+interiorVertV3, ntri+interiorVertV1, alpha1*tana1);
		}
		if (interiorVertV2 != -1 && interiorVertV3 != -1) {
			matTmat.add(ntri+nint+interiorVertV2, ntri+nint+interiorVertV3, -tana1*tana1);
			matTmat.add(ntri+nint+interiorVertV3, ntri+nint+interiorVertV2, -tana1*tana1);
		}
	}


	/**
	 * Given a piece of the unfolded mesh, this method computes a standalone
	 * unfolded mesh. Ids are kept, they will make it possible to find back
	 * which face/vertex is concerned during UVMapping edition.
	 */
	private UnfoldedMesh computeUnfoldedMesh(ArrayList<Integer> vertList,
			ArrayList<Integer> edgeList, ArrayList<Integer> faceList,
			UnfoldedVertex[] uverts, UnfoldedEdge[] uedges,
			UnfoldedFace[] ufaces) throws IterativeSolverNotConvergedException {
		UnfoldedVertex[] vertices = new UnfoldedVertex[vertList.size()];
		UnfoldedEdge[] edges = new UnfoldedEdge[edgeList.size()];
		UnfoldedFace[] faces = new UnfoldedFace[faceList.size()];
		int[] vertTrans = new int[uverts.length];
		int[] edgeTrans = new int[uedges.length];
		int[] faceTrans = new int[ufaces.length];
		// first, translate the indexes
		for (int i = 0; i < vertTrans.length; i++) {
			vertTrans[i] = -1;
		}
		for (int i = 0; i < vertices.length; i++) {
			vertTrans[vertList.get(i)] = i;
		}
		for (int i = 0; i < edgeTrans.length; i++) {
			edgeTrans[i] = -1;
		}
		for (int i = 0; i < edges.length; i++) {
			edgeTrans[edgeList.get(i)] = i;
		}
		for (int i = 0; i < faceTrans.length; i++) {
			faceTrans[i] = -1;
		}
		for (int i = 0; i < faces.length; i++) {
			faceTrans[faceList.get(i)] = i;
		}
		for (int i = 0; i < vertices.length; i++) {
			vertices[i] = uverts[vertList.get(i)].duplicate();
			if (edgeTrans[vertices[i].edge] == -1)
				System.out.println("Pb edge translation");
			vertices[i].edge = edgeTrans[vertices[i].edge];
		}
		for (int i = 0; i < edges.length; i++) {
			edges[i] = uedges[edgeList.get(i)].duplicate();
			if (vertTrans[edges[i].v1] == -1)
				System.out.println("Pb edge vert v1");
			if (vertTrans[edges[i].v2] == -1)
				System.out.println("Pb edge vert v2");
			edges[i].v1 = vertTrans[edges[i].v1];
			edges[i].v2 = vertTrans[edges[i].v2];
			if (edges[i].f1 != -1) {
				if (faceTrans[edges[i].f1] == -1)
					System.out.println("Pb edge face f1");
				edges[i].f1 = faceTrans[edges[i].f1];
			}
			if (edges[i].f2 != -1) {
				if (faceTrans[edges[i].f2] == -1)
					System.out.println("Pb edge face f2");
				edges[i].f2 = faceTrans[edges[i].f2];
			}
		}
		for (int i = 0; i < faces.length; i++) {
			faces[i] = ufaces[faceList.get(i)].duplicate();
			if (vertTrans[faces[i].v1] == -1)
				System.out.println("Pb face vert v1");
			if (vertTrans[faces[i].v2] == -1)
				System.out.println("Pb face vert v2");
			if (vertTrans[faces[i].v3] == -1)
				System.out.println("Pb face vert v3");
			faces[i].v1 = vertTrans[faces[i].v1];
			faces[i].v2 = vertTrans[faces[i].v2];
			faces[i].v3 = vertTrans[faces[i].v3];
			if (edgeTrans[faces[i].e1] == -1)
				System.out.println("Pb face edge e1");
			if (edgeTrans[faces[i].e2] == -1)
				System.out.println("Pb face edge e2");
			if (edgeTrans[faces[i].e3] == -1)
				System.out.println("Pb face edge e3");
			faces[i].e1 = edgeTrans[faces[i].e1];
			faces[i].e2 = edgeTrans[faces[i].e2];
			faces[i].e3 = edgeTrans[faces[i].e3];
		}
		// then rebuild 2D mesh positions according
		// to ABF++ procedure
		int nvars = 2 * (vertices.length - 2); // number of variables
		//System.out.println("nvar: " + nvars);
		SparseVector b = new SparseVector(6 * faces.length);
		FlexCompColMatrix mat = new FlexCompColMatrix(6 * faces.length, nvars);
		FlexCompRowMatrix matTmat = new FlexCompRowMatrix(nvars, nvars);
		int fi;
		double m1xx, m1xy, m2xx, m2xy, m3xx, m3xy;
		double m1yx, m1yy, m2yx, m2yy, m3yx, m3yy;
		m3xx = -1;
		m3xy = 0;
		m3yx = 0;
		m3yy = -1;
		double s, cs1, sn1;
		double m00, m01, m10, m11;
		int v1, v2, v3;
		double a1, a2, a3;
		double tmp;
		for (int i = 0; i < faces.length; i++) for (int j = 0; j < 3; j++) { 
			fi = faceList.get(i);
			switch (j) {
			default:
			case 0:
				v1 = faces[i].v1;
				v2 = faces[i].v2;
				v3 = faces[i].v3;
				a1 = var[3 * fi];
				a2 = var[3 * fi + 1];
				a3 = var[3 * fi + 2];
				break;
			case 1:
				v3 = faces[i].v1;
				v1 = faces[i].v2;
				v2 = faces[i].v3;
				a3 = var[3 * fi];
				a1 = var[3 * fi + 1];
				a2 = var[3 * fi + 2];
				break;
			case 2:
				v2 = faces[i].v1;
				v3 = faces[i].v2;
				v1 = faces[i].v3;
				a2 = var[3 * fi];
				a3 = var[3 * fi + 1];
				a1 = var[3 * fi + 2];
				break;
				
			}
//			v1 = faces[i].v1;
//			v2 = faces[i].v2;
//			v3 = faces[i].v3;
			//System.out.println(v1 + " " + v2 + " " +v3 + " " + a1 + " " + a2 + " " + a3);
			s = Math.sin(a2); // / Math.sin(a3);
			cs1 = s * Math.cos(a1);
			sn1 = s * Math.sin(a1);
			m00 = cs1;
			m01 = sn1;
			m10 = -sn1;
			m11 = cs1;
			m1xx = Math.sin(a3) - m00;
			m1xy = -m01;
			m2xx = m00;
			m2xy = m01;
			m1yx = -m10;
			m1yy = Math.sin(a3) - m11;
			m2yx = m10;
			m2yy = m11;
			m3xx = -Math.sin(a3);
			m3xy = 0;
			m3yx = 0;
			m3yy = -Math.sin(a3);
			if (v1 == 0 || v1 == 1) {
				b.add(6 * i + j, -m1xx * vertices[v1].r.x - m1xy
								* vertices[v1].r.y);
				b.add(6 * i + j + 3, -m1yx * vertices[v1].r.x - m1yy
						* vertices[v1].r.y);
			} else {
				mat.set(i * 6 + j, 2 * (v1 - 2), m1xx);
				mat.set(i * 6 + j, 2 * (v1 - 2) + 1, m1xy);
				mat.set(i * 6 + j + 3, 2 * (v1 - 2), m1yx);
				mat.set(i * 6 + j + 3, 2 * (v1 - 2) + 1, m1yy);
				matTmat.add(2 * (v1 - 2), 2 * (v1 - 2), m1xx * m1xx + m1yx
						* m1yx);
				matTmat.add(2 * (v1 - 2), 2 * (v1 - 2) + 1, m1xx * m1xy + m1yx
						* m1yy);
				matTmat.add(2 * (v1 - 2) + 1, 2 * (v1 - 2), m1xx * m1xy + m1yx
						* m1yy);
				matTmat.add(2 * (v1 - 2) + 1, 2 * (v1 - 2) + 1, m1xy * m1xy
						+ m1yy * m1yy);
				if (v2 != 0 && v2 != 1) {
					tmp = m1xx * m2xx + m1yx * m2yx;
					matTmat.add(2 * (v1 - 2), 2 * (v2 - 2), tmp);
					matTmat.add(2 * (v2 - 2), 2 * (v1 - 2), tmp);
					tmp = m1xx * m2xy + m1yx * m2yy;
					matTmat.add(2 * (v1 - 2), 2 * (v2 - 2) + 1, tmp);
					matTmat.add(2 * (v2 - 2) + 1, 2 * (v1 - 2), tmp);
					tmp = m1xy * m2xx + m1yy * m2yx;
					matTmat.add(2 * (v1 - 2) + 1, 2 * (v2 - 2), tmp);
					matTmat.add(2 * (v2 - 2), 2 * (v1 - 2) + 1, tmp);
					tmp = m1xy * m2xy + m1yy * m2yy;
					matTmat.add(2 * (v1 - 2) + 1, 2 * (v2 - 2) + 1, tmp);
					matTmat.add(2 * (v2 - 2) + 1, 2 * (v1 - 2) + 1, tmp);
				}
				if (v3 != 0 && v3 != 1) {
					tmp = m1xx * m3xx + m1yx * m3yx;
					matTmat.add(2 * (v1 - 2), 2 * (v3 - 2), tmp);
					matTmat.add(2 * (v3 - 2), 2 * (v1 - 2), tmp);
					tmp = m1xx * m3xy + m1yx * m3yy;
					matTmat.add(2 * (v1 - 2), 2 * (v3 - 2) + 1, tmp);
					matTmat.add(2 * (v3 - 2) + 1, 2 * (v1 - 2), tmp);
					tmp = m1xy * m3xx + m1yy * m3yx;
					matTmat.add(2 * (v1 - 2) + 1, 2 * (v3 - 2), tmp);
					matTmat.add(2 * (v3 - 2), 2 * (v1 - 2) + 1, tmp);
					tmp = m1xy * m3xy + m1yy * m3yy;
					matTmat.add(2 * (v1 - 2) + 1, 2 * (v3 - 2) + 1, tmp);
					matTmat.add(2 * (v3 - 2) + 1, 2 * (v1 - 2) + 1, tmp);
				}
			}
			if (v2 == 0 || v2 == 1) {
				b.add(6 * i + j, -m2xx * vertices[v2].r.x - m2xy
								* vertices[v2].r.y);
				b.add(6 * i + j + 3, -m2yx * vertices[v2].r.x - m2yy
						* vertices[v2].r.y);
			} else {
				mat.set(i * 6 + j, 2 * (v2 - 2), m2xx);
				mat.set(i * 6 + j, 2 * (v2 - 2) + 1, m2xy);
				mat.set(i * 6 + j + 3, 2 * (v2 - 2), m2yx);
				mat.set(i * 6 + j + 3, 2 * (v2 - 2) + 1, m2yy);
				matTmat.add(2 * (v2 - 2), 2 * (v2 - 2), m2xx * m2xx + m2yx
						* m2yx);
				matTmat.add(2 * (v2 - 2), 2 * (v2 - 2) + 1, m2xx * m2xy + m2yx
						* m2yy);
				matTmat.add(2 * (v2 - 2) + 1, 2 * (v2 - 2), m2xx * m2xy + m2yx
						* m2yy);
				matTmat.add(2 * (v2 - 2) + 1, 2 * (v2 - 2) + 1, m2xy * m2xy
						+ m2yy * m2yy);
				if (v3 != 0 && v3 != 1) {
					tmp = m2xx * m3xx + m2yx * m3yx;
					matTmat.add(2 * (v2 - 2), 2 * (v3 - 2), tmp);
					matTmat.add(2 * (v3 - 2), 2 * (v2 - 2), tmp);
					tmp = m2xx * m3xy + m2yx * m3yy;
					matTmat.add(2 * (v2 - 2), 2 * (v3 - 2) + 1, tmp);
					matTmat.add(2 * (v3 - 2) + 1, 2 * (v2 - 2), tmp);
					tmp = m2xy * m3xx + m2yy * m3yx;
					matTmat.add(2 * (v2 - 2) + 1, 2 * (v3 - 2), tmp);
					matTmat.add(2 * (v3 - 2), 2 * (v2 - 2) + 1, tmp);
					tmp = m2xy * m3xy + m2yy * m3yy;
					matTmat.add(2 * (v2 - 2) + 1, 2 * (v3 - 2) + 1, tmp);
					matTmat.add(2 * (v3 - 2) + 1, 2 * (v2 - 2) + 1, tmp);
				}
			}
			if (v3 == 0 || v3 == 1) {
				b
						.add(6 * i + j, -m3xx * vertices[v3].r.x - m3xy
								* vertices[v3].r.y);
				b.add(6 * i + j + 3, -m3yx * vertices[v3].r.x - m3yy
						* vertices[v3].r.y);
			} else {
				mat.set(i * 6 + j, 2 * (v3 - 2), m3xx);
				mat.set(i * 6 + j, 2 * (v3 - 2) + 1, m3xy);
				mat.set(i * 6 + j + 3, 2 * (v3 - 2), m3yx);
				mat.set(i * 6 + j + 3, 2 * (v3 - 2) + 1, m3yy);
				matTmat.add(2 * (v3 - 2), 2 * (v3 - 2), m3xx * m3xx + m3yx
						* m3yx);
				matTmat.add(2 * (v3 - 2), 2 * (v3 - 2) + 1, m3xx * m3xy + m3yx
						* m3yy);
				matTmat.add(2 * (v3 - 2) + 1, 2 * (v3 - 2), m3xx * m3xy + m3yx
						* m3yy);
				matTmat.add(2 * (v3 - 2) + 1, 2 * (v3 - 2) + 1, m3xy * m3xy
						+ m3yy * m3yy);
			}
		}
		DenseVector vsol = new DenseVector(nvars);
		SparseVector mtb = new SparseVector(nvars);
		//for (int i = 0; i < 6*faces.length; i++)
		//	System.out.println("b("+i+") : "+b.get(i));
		mtb = (SparseVector) mat.transMult(b, mtb);
		//for (int i = 0; i < nvars; i++)
		//	System.out.println("righthand("+i+") : "+mtb.get(i));
		CG qmr = new CG(mtb);
		qmr.solve(matTmat, mtb, vsol);
		//for (int i = 0; i < nvars; i++)
		//	System.out.println("sol("+i+") : "+vsol.get(i));
		Vec2 center = new Vec2();
		for (int i = 2; i < vertices.length; i++) {
			vertices[i].r.x = vsol.get((i - 2) * 2);
			vertices[i].r.y = vsol.get((i - 2) * 2 + 1);
			center.add(vertices[i].r);
		}
		center.add(vertices[0].r);
		center.add(vertices[1].r);
		//check if the mesh is right handed
		boolean leftHanded = false;
		Vec2 v1r = vertices[faces[0].v3].r.minus(vertices[faces[0].v1].r);
		Vec2 v2r = vertices[faces[0].v2].r.minus(vertices[faces[0].v1].r);
		if (v1r.x * v2r.y - v1r.y * v2r.x < 0) {
			leftHanded = true;
		}
		// center the mesh
		center.scale(1.0 / ((double) vertices.length));
		if (!leftHanded) {
			center.y *= -1;
		}
		for (int i = 0; i < vertices.length; i++) {
			if (!leftHanded) {
				vertices[i].r.y *= -1;
			}
			vertices[i].r.subtract(center);
		}
		return new UnfoldedMesh(vertices, edges, faces);
	}

	private UnfoldedMesh computeUnfoldedMesh1(ArrayList<Integer> vertList,
			ArrayList<Integer> edgeList, ArrayList<Integer> faceList,
			UnfoldedVertex[] uverts, UnfoldedEdge[] uedges,
			UnfoldedFace[] ufaces) throws IterativeSolverNotConvergedException {
		UnfoldedVertex[] vertices = new UnfoldedVertex[vertList.size()];
		UnfoldedEdge[] edges = new UnfoldedEdge[edgeList.size()];
		UnfoldedFace[] faces = new UnfoldedFace[faceList.size()];
		int[] vertTrans = new int[uverts.length];
		int[] edgeTrans = new int[uedges.length];
		int[] faceTrans = new int[ufaces.length];
		// first, translate the indexes
		for (int i = 0; i < vertTrans.length; i++) {
			vertTrans[i] = -1;
		}
		for (int i = 0; i < vertices.length; i++) {
			vertTrans[vertList.get(i)] = i;
		}
		for (int i = 0; i < edgeTrans.length; i++) {
			edgeTrans[i] = -1;
		}
		for (int i = 0; i < edges.length; i++) {
			edgeTrans[edgeList.get(i)] = i;
		}
		for (int i = 0; i < faceTrans.length; i++) {
			faceTrans[i] = -1;
		}
		for (int i = 0; i < faces.length; i++) {
			faceTrans[faceList.get(i)] = i;
		}
		for (int i = 0; i < vertices.length; i++) {
			vertices[i] = uverts[vertList.get(i)].duplicate();
			if (edgeTrans[vertices[i].edge] == -1)
				System.out.println("Pb edge translation");
			vertices[i].edge = edgeTrans[vertices[i].edge];
		}
		for (int i = 0; i < edges.length; i++) {
			edges[i] = uedges[edgeList.get(i)].duplicate();
			if (vertTrans[edges[i].v1] == -1)
				System.out.println("Pb edge vert v1");
			if (vertTrans[edges[i].v2] == -1)
				System.out.println("Pb edge vert v2");
			edges[i].v1 = vertTrans[edges[i].v1];
			edges[i].v2 = vertTrans[edges[i].v2];
			if (edges[i].f1 != -1) {
				if (faceTrans[edges[i].f1] == -1)
					System.out.println("Pb edge face f1");
				edges[i].f1 = faceTrans[edges[i].f1];
			}
			if (edges[i].f2 != -1) {
				if (faceTrans[edges[i].f2] == -1)
					System.out.println("Pb edge face f2");
				edges[i].f2 = faceTrans[edges[i].f2];
			}
		}
		for (int i = 0; i < faces.length; i++) {
			faces[i] = ufaces[faceList.get(i)].duplicate();
			if (vertTrans[faces[i].v1] == -1)
				System.out.println("Pb face vert v1");
			if (vertTrans[faces[i].v2] == -1)
				System.out.println("Pb face vert v2");
			if (vertTrans[faces[i].v3] == -1)
				System.out.println("Pb face vert v3");
			faces[i].v1 = vertTrans[faces[i].v1];
			faces[i].v2 = vertTrans[faces[i].v2];
			faces[i].v3 = vertTrans[faces[i].v3];
			if (edgeTrans[faces[i].e1] == -1)
				System.out.println("Pb face edge e1");
			if (edgeTrans[faces[i].e2] == -1)
				System.out.println("Pb face edge e2");
			if (edgeTrans[faces[i].e3] == -1)
				System.out.println("Pb face edge e3");
			faces[i].e1 = edgeTrans[faces[i].e1];
			faces[i].e2 = edgeTrans[faces[i].e2];
			faces[i].e3 = edgeTrans[faces[i].e3];
		}
		// then rebuild 2D mesh positions according
		// to ABF++ procedure
		int nvars = 2 * (vertices.length - 2); // number of variables
		SparseVector b = new SparseVector(2 * faces.length);
		FlexCompColMatrix mat = new FlexCompColMatrix(2 * faces.length, nvars);
		FlexCompRowMatrix matTmat = new FlexCompRowMatrix(nvars, nvars);
		int fi;
		double m1xx, m1xy, m2xx, m2xy, m3xx, m3xy;
		double m1yx, m1yy, m2yx, m2yy, m3yx, m3yy;
		m3xx = -1;
		m3xy = 0;
		m3yx = 0;
		m3yy = -1;
		double s, cs1, sn1;
		double m00, m01, m10, m11;
		int v1, v2, v3;
		double tmp;
		for (int i = 0; i < faces.length; i++) {
			fi = faceList.get(i);
			v1 = faces[i].v1;
			v2 = faces[i].v2;
			v3 = faces[i].v3;
			s = Math.sin(var[3 * fi + 1]) / Math.sin(var[3 * fi + 2]);
			cs1 = s * Math.cos(var[3 * fi]);
			sn1 = s * Math.sin(var[3 * fi]);
			m00 = cs1;
			m01 = sn1;
			m10 = -sn1;
			m11 = cs1;
			m1xx = 1 - m00;
			m1xy = -m01;
			m2xx = m00;
			m2xy = m01;
			m1yx = -m10;
			m1yy = 1 - m11;
			m2yx = m10;
			m2yy = m11;
			if (v1 == 0 || v1 == 1) {
				b.add(2 * i, -m1xx * vertices[v1].r.x - m1xy
								* vertices[v1].r.y);
				b.add(2 * i + 1, -m1yx * vertices[v1].r.x - m1yy
						* vertices[v1].r.y);
			} else {
				mat.set(i * 2, 2 * (v1 - 2), m1xx);
				mat.set(i * 2, 2 * (v1 - 2) + 1, m1xy);
				mat.set(i * 2 + 1, 2 * (v1 - 2), m1yx);
				mat.set(i * 2 + 1, 2 * (v1 - 2) + 1, m1yy);
				matTmat.add(2 * (v1 - 2), 2 * (v1 - 2), m1xx * m1xx + m1yx
						* m1yx);
				matTmat.add(2 * (v1 - 2), 2 * (v1 - 2) + 1, m1xx * m1xy + m1yx
						* m1yy);
				matTmat.add(2 * (v1 - 2) + 1, 2 * (v1 - 2), m1xx * m1xy + m1yx
						* m1yy);
				matTmat.add(2 * (v1 - 2) + 1, 2 * (v1 - 2) + 1, m1xy * m1xy
						+ m1yy * m1yy);
				if (v2 != 0 && v2 != 1) {
					tmp = m1xx * m2xx + m1yx * m2yx;
					matTmat.add(2 * (v1 - 2), 2 * (v2 - 2), tmp);
					matTmat.add(2 * (v2 - 2), 2 * (v1 - 2), tmp);
					tmp = m1xx * m2xy + m1yx * m2yy;
					matTmat.add(2 * (v1 - 2), 2 * (v2 - 2) + 1, tmp);
					matTmat.add(2 * (v2 - 2) + 1, 2 * (v1 - 2), tmp);
					tmp = m1xy * m2xx + m1yy * m2yx;
					matTmat.add(2 * (v1 - 2) + 1, 2 * (v2 - 2), tmp);
					matTmat.add(2 * (v2 - 2), 2 * (v1 - 2) + 1, tmp);
					tmp = m1xy * m2xy + m1yy * m2yy;
					matTmat.add(2 * (v1 - 2) + 1, 2 * (v2 - 2) + 1, tmp);
					matTmat.add(2 * (v2 - 2) + 1, 2 * (v1 - 2) + 1, tmp);
				}
				if (v3 != 0 && v3 != 1) {
					tmp = m1xx * m3xx + m1yx * m3yx;
					matTmat.add(2 * (v1 - 2), 2 * (v3 - 2), tmp);
					matTmat.add(2 * (v3 - 2), 2 * (v1 - 2), tmp);
					tmp = m1xx * m3xy + m1yx * m3yy;
					matTmat.add(2 * (v1 - 2), 2 * (v3 - 2) + 1, tmp);
					matTmat.add(2 * (v3 - 2) + 1, 2 * (v1 - 2), tmp);
					tmp = m1xy * m3xx + m1yy * m3yx;
					matTmat.add(2 * (v1 - 2) + 1, 2 * (v3 - 2), tmp);
					matTmat.add(2 * (v3 - 2), 2 * (v1 - 2) + 1, tmp);
					tmp = m1xy * m3xy + m1yy * m3yy;
					matTmat.add(2 * (v1 - 2) + 1, 2 * (v3 - 2) + 1, tmp);
					matTmat.add(2 * (v3 - 2) + 1, 2 * (v1 - 2) + 1, tmp);
				}
			}
			if (v2 == 0 || v2 == 1) {
				b.add(2 * i, -m2xx * vertices[v2].r.x - m2xy
								* vertices[v2].r.y);
				b.add(2 * i + 1, -m2yx * vertices[v2].r.x - m2yy
						* vertices[v2].r.y);
			} else {
				mat.set(i * 2, 2 * (v2 - 2), m2xx);
				mat.set(i * 2, 2 * (v2 - 2) + 1, m2xy);
				mat.set(i * 2 + 1, 2 * (v2 - 2), m2yx);
				mat.set(i * 2 + 1, 2 * (v2 - 2) + 1, m2yy);
				matTmat.add(2 * (v2 - 2), 2 * (v2 - 2), m2xx * m2xx + m2yx
						* m2yx);
				matTmat.add(2 * (v2 - 2), 2 * (v2 - 2) + 1, m2xx * m2xy + m2yx
						* m2yy);
				matTmat.add(2 * (v2 - 2) + 1, 2 * (v2 - 2), m2xx * m2xy + m2yx
						* m2yy);
				matTmat.add(2 * (v2 - 2) + 1, 2 * (v2 - 2) + 1, m2xy * m2xy
						+ m2yy * m2yy);
				if (v3 != 0 && v3 != 1) {
					tmp = m2xx * m3xx + m2yx * m3yx;
					matTmat.add(2 * (v2 - 2), 2 * (v3 - 2), tmp);
					matTmat.add(2 * (v3 - 2), 2 * (v2 - 2), tmp);
					tmp = m2xx * m3xy + m2yx * m3yy;
					matTmat.add(2 * (v2 - 2), 2 * (v3 - 2) + 1, tmp);
					matTmat.add(2 * (v3 - 2) + 1, 2 * (v2 - 2), tmp);
					tmp = m2xy * m3xx + m2yy * m3yx;
					matTmat.add(2 * (v2 - 2) + 1, 2 * (v3 - 2), tmp);
					matTmat.add(2 * (v3 - 2), 2 * (v2 - 2) + 1, tmp);
					tmp = m2xy * m3xy + m2yy * m3yy;
					matTmat.add(2 * (v2 - 2) + 1, 2 * (v3 - 2) + 1, tmp);
					matTmat.add(2 * (v3 - 2) + 1, 2 * (v2 - 2) + 1, tmp);
				}
			}
			if (v3 == 0 || v3 == 1) {
				b
						.add(2 * i, -m3xx * vertices[v3].r.x - m3xy
								* vertices[v3].r.y);
				b.add(2 * i + 1, -m3yx * vertices[v3].r.x - m3yy
						* vertices[v3].r.y);
			} else {
				mat.set(i * 2, 2 * (v3 - 2), m3xx);
				mat.set(i * 2, 2 * (v3 - 2) + 1, m3xy);
				mat.set(i * 2 + 1, 2 * (v3 - 2), m3yx);
				mat.set(i * 2 + 1, 2 * (v3 - 2) + 1, m3yy);
				matTmat.add(2 * (v3 - 2), 2 * (v3 - 2), m3xx * m3xx + m3yx
						* m3yx);
				matTmat.add(2 * (v3 - 2), 2 * (v3 - 2) + 1, m3xx * m3xy + m3yx
						* m3yy);
				matTmat.add(2 * (v3 - 2) + 1, 2 * (v3 - 2), m3xx * m3xy + m3yx
						* m3yy);
				matTmat.add(2 * (v3 - 2) + 1, 2 * (v3 - 2) + 1, m3xy * m3xy
						+ m3yy * m3yy);
			}
		}
		DenseVector vsol = new DenseVector(nvars);
		SparseVector mtb = new SparseVector(nvars);
		long time = new Date().getTime();
		//System.out.println("transMult en cours ");
		System.out.flush();
		mtb = (SparseVector) mat.transMult(b, mtb);
		
		/******/
//		double tmpdbl = 0;
//		int nonzero = 0;
//		SparseVector matVecI, matVecJ;
//		boolean zero;
//		int[] ind, jnd;
//		double[] idata, jdata;
//		int[] bindx;
//		double[] bdata;
//		SparseVector nmtb = new SparseVector(nvars);
////		for (int i = 0; i < nvars; i++) {
////			matVecI = mat.getColumn(i);
////			
////		}
//		FlexCompRowMatrix newmat = new FlexCompRowMatrix(nvars, nvars);
//		for (int i = 0; i < nvars; i++) {
//			for (int j = 0; j < nvars; j++) {
//				tmpdbl = 0;
//				zero = true;
//				matVecI = mat.getColumn(i);
//				matVecJ = mat.getColumn(j);
//				tmpdbl = matVecI.dot(matVecJ);
//				if (Math.abs(tmpdbl) > 0.0001) {
//					zero = false;
//				}
////				
////				ind = matVecI.getIndex();
////				idata = matVecI.getData();
////				jnd = matVecJ.getIndex();
////				jdata = matVecJ.getData();
////				for (int k = 0; k < ind.length; k++) {
////					for (int l = 0; l < jnd.length; l++) {
////						if (ind[k] == jnd[l]) {
////							tmpdbl += idata[k]*jdata[l];
////							zero = false;
////						}
////					}
////				}
//				if (!zero) {
//					newmat.add(i, j, tmpdbl);
//				}
//				if (Math.abs(tmpdbl - matTmat.get(i,j)) > 0.001 ) {
//						System.out.println("matTmat pb : " + i + " " + j + " | " + tmpdbl + " | " + matTmat.get(i,j));
//				}
//			}
//		}
		//mat.transAmult(mat, newmat);
		time = new Date().getTime() - time;
		//System.out.println("transMult: " + Math.round(time / 1000) + "s\n");
		//System.out.flush();
		time = new Date().getTime();
		CG qmr = new CG(mtb);
		qmr.solve(matTmat, mtb, vsol);
		//qmr.solve(newmat, mtb, vsol);
		time = new Date().getTime() - time;
		//System.out.println("solved: " + Math.round(time / 1000) + "s\n");
		//System.out.flush();
		Vec2 center = new Vec2();
		for (int i = 2; i < vertices.length; i++) {
			vertices[i].r.x = vsol.get((i - 2) * 2);
			vertices[i].r.y = vsol.get((i - 2) * 2 + 1);
			center.add(vertices[i].r);
		}
		center.add(vertices[0].r);
		center.add(vertices[1].r);
		//check if the mesh is right handed
		boolean leftHanded = false;
		Vec2 v1r = vertices[faces[0].v3].r.minus(vertices[faces[0].v1].r);
		Vec2 v2r = vertices[faces[0].v2].r.minus(vertices[faces[0].v1].r);
		if (v1r.x * v2r.y - v1r.y * v2r.x < 0) {
			leftHanded = true;
		}
		// center the mesh
		center.scale(1.0 / ((double) vertices.length));
		if (!leftHanded) {
			center.y *= -1;
		}
		for (int i = 0; i < vertices.length; i++) {
			if (!leftHanded) {
				vertices[i].r.y *= -1;
			}
			vertices[i].r.subtract(center);
		}
		return new UnfoldedMesh(vertices, edges, faces);
	}
	
	private UnfoldedMesh computeUnfoldedMeshNew(ArrayList<Integer> vertList,
			ArrayList<Integer> edgeList, ArrayList<Integer> faceList,
			UnfoldedVertex[] uverts, UnfoldedEdge[] uedges,
			UnfoldedFace[] ufaces) throws IterativeSolverNotConvergedException {
		UnfoldedVertex[] vertices = new UnfoldedVertex[vertList.size()];
		UnfoldedEdge[] edges = new UnfoldedEdge[edgeList.size()];
		UnfoldedFace[] faces = new UnfoldedFace[faceList.size()];
		int[] vertTrans = new int[uverts.length];
		int[] edgeTrans = new int[uedges.length];
		int[] faceTrans = new int[ufaces.length];
		// first, translate the indexes
		for (int i = 0; i < vertTrans.length; i++) {
			vertTrans[i] = -1;
		}
		for (int i = 0; i < vertices.length; i++) {
			vertTrans[vertList.get(i)] = i;
		}
		for (int i = 0; i < edgeTrans.length; i++) {
			edgeTrans[i] = -1;
		}
		for (int i = 0; i < edges.length; i++) {
			edgeTrans[edgeList.get(i)] = i;
		}
		for (int i = 0; i < faceTrans.length; i++) {
			faceTrans[i] = -1;
		}
		for (int i = 0; i < faces.length; i++) {
			faceTrans[faceList.get(i)] = i;
		}
		for (int i = 0; i < vertices.length; i++) {
			vertices[i] = uverts[vertList.get(i)].duplicate();
			if (edgeTrans[vertices[i].edge] == -1)
				System.out.println("Pb edge translation");
			vertices[i].edge = edgeTrans[vertices[i].edge];
		}
		for (int i = 0; i < edges.length; i++) {
			edges[i] = uedges[edgeList.get(i)].duplicate();
			if (vertTrans[edges[i].v1] == -1)
				System.out.println("Pb edge vert v1");
			if (vertTrans[edges[i].v2] == -1)
				System.out.println("Pb edge vert v2");
			edges[i].v1 = vertTrans[edges[i].v1];
			edges[i].v2 = vertTrans[edges[i].v2];
			if (edges[i].f1 != -1) {
				if (faceTrans[edges[i].f1] == -1)
					System.out.println("Pb edge face f1");
				edges[i].f1 = faceTrans[edges[i].f1];
			}
			if (edges[i].f2 != -1) {
				if (faceTrans[edges[i].f2] == -1)
					System.out.println("Pb edge face f2");
				edges[i].f2 = faceTrans[edges[i].f2];
			}
		}
		for (int i = 0; i < faces.length; i++) {
			faces[i] = ufaces[faceList.get(i)].duplicate();
			if (vertTrans[faces[i].v1] == -1)
				System.out.println("Pb face vert v1");
			if (vertTrans[faces[i].v2] == -1)
				System.out.println("Pb face vert v2");
			if (vertTrans[faces[i].v3] == -1)
				System.out.println("Pb face vert v3");
			faces[i].v1 = vertTrans[faces[i].v1];
			faces[i].v2 = vertTrans[faces[i].v2];
			faces[i].v3 = vertTrans[faces[i].v3];
			if (edgeTrans[faces[i].e1] == -1)
				System.out.println("Pb face edge e1");
			if (edgeTrans[faces[i].e2] == -1)
				System.out.println("Pb face edge e2");
			if (edgeTrans[faces[i].e3] == -1)
				System.out.println("Pb face edge e3");
			faces[i].e1 = edgeTrans[faces[i].e1];
			faces[i].e2 = edgeTrans[faces[i].e2];
			faces[i].e3 = edgeTrans[faces[i].e3];
		}
		// then rebuild 2D mesh positions according
		// to ABF++ procedure
		int nvars = 2 * (vertices.length); // - 2); // number of variables
		SparseVector b = new SparseVector(6 * faces.length);
		FlexCompColMatrix mat = new FlexCompColMatrix(6 * ntri + 4, nvars);
		FlexCompRowMatrix matTmat = new FlexCompRowMatrix(nvars, nvars);
		SparseVector rh = new SparseVector(6*ntri+4);
		for (int i = 0; i < faces.length; i++) {
//			addToConstraints(newConstraints, i, faces[i].v1, faces[i].v2, faces[i].v3, 3*i,ntri, nint);
//			addToConstraints(newConstraints, i, faces[i].v2, faces[i].v3, faces[i].v1, 3*i+1, ntri, nint);
//			addToConstraints(newConstraints, i, faces[i].v3, faces[i].v1, faces[i].v2, 3*i+2, ntri, nint);
			addToRebuildMatTMat(mat, matTmat, 6*i, faces[i].v1, faces[i].v2, faces[i].v3, 3*i, 3*i+1, 3*i+2);
			addToRebuildMatTMat(mat, matTmat, 6*i+2, faces[i].v2, faces[i].v3, faces[i].v1, 3*i+1, 3*i+2, 3*i);
			addToRebuildMatTMat(mat, matTmat, 6*i+4, faces[i].v3, faces[i].v1, faces[i].v2, 3*i+2, 3*i, 3*i+1);
		}
		rh.add(2*ntri, vertices[0].r.x);
		rh.add(2*ntri+1, vertices[0].r.y);
		rh.add(2*ntri+2, vertices[1].r.x);
		rh.add(2*ntri+3, vertices[1].r.y);
		mat.add(2*ntri+0, 0, 1);
		mat.add(2*ntri+1, 1, 1);
		mat.add(2*ntri+2, 2, 1);
		mat.add(2*ntri+3, 3, 1);
		DenseVector vsol = new DenseVector(nvars);
		vsol.set(0, vertices[0].r.x);
		vsol.set(1, vertices[0].r.y);
		vsol.set(2, vertices[1].r.x);
		vsol.set(3, vertices[1].r.y);
		
		SparseVector mtb = new SparseVector(nvars);
		long time = new Date().getTime();
		//System.out.println("transMult en cours ");
		//System.out.flush();
		//mtb = (SparseVector) mat.transMult(rh, mtb);
		
		/******/
		double tmpdbl = 0;
		int nonzero = 0;
		SparseVector matVecI, matVecJ;
		boolean zero;
		int[] ind, jnd;
		double[] idata, jdata;
		int[] bindx;
		double[] bdata;
//		SparseVector nmtb = new SparseVector(nvars);
////		for (int i = 0; i < nvars; i++) {
////			matVecI = mat.getColumn(i);
////			
////		}
//		FlexCompRowMatrix newmat = new FlexCompRowMatrix(nvars, nvars);
		for (int i = 0; i < nvars; i++) {
			matVecI = mat.getColumn(i);
			tmpdbl = matVecI.dot(rh);
			if (Math.abs(tmpdbl) > 0.0001) {
				mtb.add(i, tmpdbl);
			}
			for (int j = 0; j < nvars; j++) {
				tmpdbl = 0;
				zero = true;
				matVecJ = mat.getColumn(j);
				tmpdbl = matVecI.dot(matVecJ);
				if (Math.abs(tmpdbl) > 0.0001) {
					zero = false;
				}
				if (!zero) {
					matTmat.add(i, j, tmpdbl);
				}
//				if (Math.abs(tmpdbl - matTmat.get(i,j)) > 0.001 ) {
//						System.out.println("matTmat pb : " + i + " " + j + " | " + tmpdbl + " | " + matTmat.get(i,j));
//				}
			}
		}
		//mat.transAmult(mat, newmat);
		time = new Date().getTime() - time;
		//System.out.println("transMult: " + Math.round(time / 1000) + "s\n");
		//System.out.flush();
		time = new Date().getTime();
		QMR qmr = new QMR(mtb);
		qmr.solve(matTmat, mtb, vsol);
		//qmr.solve(newmat, mtb, vsol);
		time = new Date().getTime() - time;
		//System.out.println("solved: " + Math.round(time / 1000) + "s\n");
		//System.out.flush();
		Vec2 center = new Vec2();
		for (int i = 0; i < vertices.length; i++) {
			vertices[i].r.x = vsol.get(i * 2);
			vertices[i].r.y = vsol.get(i * 2 + 1);
			center.add(vertices[i].r);
		}
		//check if the mesh is right handed
		boolean leftHanded = false;
		Vec2 v1r = vertices[faces[0].v3].r.minus(vertices[faces[0].v1].r);
		Vec2 v2r = vertices[faces[0].v2].r.minus(vertices[faces[0].v1].r);
		if (v1r.x * v2r.y - v1r.y * v2r.x < 0) {
			leftHanded = true;
		}
		// center the mesh
		center.scale(1.0 / ((double) vertices.length));
		if (!leftHanded) {
			center.y *= -1;
		}
		for (int i = 0; i < vertices.length; i++) {
			if (!leftHanded) {
				vertices[i].r.y *= -1;
			}
			vertices[i].r.subtract(center);
		}
		return new UnfoldedMesh(vertices, edges, faces);
	}
	
	private void addToRebuildMatTMat(FlexCompColMatrix mat,
			FlexCompRowMatrix matTmat, int f, int v1, int v2, int v3, int a1, int a2, int a3) {
		double a = Math.sin(var[a2])/Math.sin(var[a3]);
		double b = a * Math.sin(var[a1]);
		a *= Math.cos(var[a1]);
		mat.add(f, 2*v1, 1-a);
		mat.add(f, 2*v1+1, b);
		mat.add(f+1, 2*v1, -b);
		mat.add(f+1, 2*v1+1, 1-a);
		mat.add(f, 2*v2, a);
		mat.add(f, 2*v2+1, -b);
		mat.add(f+1, 2*v2, b);
		mat.add(f+1, 2*v2+1, a);
		mat.add(f, 2*v3, -1);
		mat.add(f+1, 2*v3+1, -1);
	}

	/**
	 * Given an edge and a face, this method checks the 3rd vertex as being
	 * unfolded if's not already checked
	 */
	private void computeFace(int e, int f, UnfoldedVertex[] uverts,
			UnfoldedEdge[] uedges, UnfoldedFace[] ufaces,
			boolean[] unfoldedFace, boolean[] unfoldedVerts,
			ArrayList<Integer> vertList, ArrayList<Integer> edgeList,
			ArrayList<Integer> faceList) {
		int v1, v2, v3;
		v1 = ufaces[f].v1;
		v2 = ufaces[f].v2;
		v3 = ufaces[f].v3;
		if ((uedges[e].v1 == v1 && uedges[e].v2 == v2)
				|| (uedges[e].v1 == v2 && uedges[e].v2 == v1)) {
			// add v3
			if (!unfoldedVerts[ufaces[f].v3]) {
				unfoldedVerts[ufaces[f].v3] = true;
				vertList.add(v3);
			}
		} else if ((uedges[e].v1 == v2 && uedges[e].v2 == v3)
				|| (uedges[e].v1 == v3 && uedges[e].v2 == v2)) {
			if (!unfoldedVerts[ufaces[f].v1]) {
				// add v1
				unfoldedVerts[ufaces[f].v1] = true;
				vertList.add(v1);
			}
		} else {
			if (!unfoldedVerts[ufaces[f].v2]) {
				// add v2
				unfoldedVerts[ufaces[f].v2] = true;
				vertList.add(v2);
			}
		}
		unfoldedFace[f] = true;
		faceList.add(f);
	}

	/**
	 * ABF++ core unfolding process helper method
	 */
	private void addToMat(FlexCompRowMatrix mat, FlexCompRowMatrix sparseJstar,
			double[] lambdaStarInv, int v1, int v2) {

		int ind[];
		int jnd[];
		double[] idata;
		double[] jdata;
		SparseVector JstarVecI;
		SparseVector JstarVecJ;
		JstarVecI = sparseJstar.getRow(v1);
		ind = JstarVecI.getIndex();
		idata = JstarVecI.getData();
		JstarVecJ = sparseJstar.getRow(v2);
		jnd = JstarVecJ.getIndex();
		jdata = JstarVecJ.getData();
		for (int k = 0; k < ind.length; k++) {
			for (int l = 0; l < jnd.length; l++) {
				if (ind[k] == jnd[l]) {
					mat
							.add(v1, v2, idata[k] * lambdaStarInv[ind[k]]
									* jdata[l]);
					mat
							.add(v2, v1, idata[k] * lambdaStarInv[ind[k]]
									* jdata[l]);
				}
			}
		}
	}

	/**
	 * ABF++ core unfolding process helper method
	 */
	private void addToMat(FlexCompRowMatrix mat, FlexCompRowMatrix sparseJstar,
			double[] lambdaStarInv, int v1) {

		int ind[];
		double[] idata;
		SparseVector JstarVecI;
		JstarVecI = sparseJstar.getRow(v1);
		ind = JstarVecI.getIndex();
		idata = JstarVecI.getData();
		for (int k = 0; k < ind.length; k++) {
			mat.add(v1, v1, idata[k] * lambdaStarInv[ind[k]] * idata[k]);
		}
	}

	/**
	 * ABF++ core unfolding process helper method
	 */
	private double jstarstarCompute(int i, int j) {
		double value = 0;
		for (int k = 0; k < j2nonzero[i].length; k++) {
			for (int l = 0; l < j2nonzero[j].length; l++) {
				if (j2nonzero[i][k] == j2nonzero[j][l])
					value += j2matrix.get(i, j2nonzero[i][k])
							* (weights[j2nonzero[i][k]] / 2)
							* j2matrix.get(j, j2nonzero[i][k]);
			}
		}
		return value;
	}

	/**
	 * Computes current ABF++ residual
	 * 
	 * @return The residual
	 */
	private double computeResidual() {
		double value = 0;
		for (int i = 0; i < nangles; i++) {
			value += gradient[i] * gradient[i];
		}
		for (int i = 0; i < ntri + 2 * nint; i++) {
			value += constraints[i] * constraints[i];
		}
		return value;
	}

	/**
	 * @return the unfolded meshes in an array of UnfoldedMesh
	 */
	public UnfoldedMesh[] getUnfoldedMeshes() {
		return unfoldedMeshes;
	}
	
	/**
	 * Initilization job :
	 * computes mesh angle,
	 * builds interior vertices table
	 * and more generally allocates memory and instantite objects needed.
	 */
	@SuppressWarnings("unchecked")
	private void initialize() {
		TriangleMesh.Vertex[] vertices = (Vertex[]) trimesh.getVertices();
		TriangleMesh.Edge[] edges = trimesh.getEdges();
		TriangleMesh.Face[] faces = trimesh.getFaces();

		// compute the mesh angles
		ntri = faces.length;
		nangles = 3 * ntri;
		angles = new double[nangles];
		Vec3 v1, v2, v3;
		for (int i = 0; i < ntri; i++) {
			v1 = vertices[faces[i].v3].r.minus(vertices[faces[i].v1].r);
			v2 = vertices[faces[i].v2].r.minus(vertices[faces[i].v1].r);
			v3 = vertices[faces[i].v3].r.minus(vertices[faces[i].v2].r);
			v1.normalize();
			v2.normalize();
			v3.normalize();
			angles[i * 3] = Math.acos(v1.dot(v2));
			angles[i * 3 + 1] = Math.acos(-v2.dot(v3));
			angles[i * 3 + 2] = Math.acos(v1.dot(v3));
		}
		weights = new double[angles.length];
		for (int i = 0; i < angles.length; i++) {
			weights[i] = /* 1.0 / */(angles[i] * angles[i]);
		}
		// setup interior vertices table
		invInteriorTable = new int[vertices.length];
		Vector interiorVerticesTable = new Vector();
		int count = 0;
		for (int i = 0; i < vertices.length; i++) {
			if (edges[vertices[i].firstEdge].f1 != -1
					&& edges[vertices[i].firstEdge].f2 != -1) {
				// not a boundary edge
				interiorVerticesTable.add(new Integer(i));
				invInteriorTable[i] = count++;
			} else
				invInteriorTable[i] = -1;
		}
		nint = interiorVerticesTable.size();
		interiorVertices = new int[nint];
		for (int i = 0; i < nint; i++) {
			interiorVertices[i] = ((Integer) interiorVerticesTable.get(i))
					.intValue();
		}
		interiorVerticesTable = null;
		angleTable = new int[nint][];
		angleMTable = new int[nint][];
		anglePTable = new int[nint][];
		for (int i = 0; i < nint; i++) {
			int v = interiorVertices[i];
			// System.out.println("interior vertex: " +
			// interiorVertices[i]);
			int[] ed = vertices[v].getEdges();
			angleTable[i] = new int[ed.length];
			angleMTable[i] = new int[ed.length];
			anglePTable[i] = new int[ed.length];
			// edges do not sequentially point to faces they delimit
			// so we need to find the faces looking at both sides
			// of an edge
			int[] tris = new int[ed.length];
			boolean add;
			int index = 0;
			int tri;
			for (int j = 0; ((j < ed.length) && (index < ed.length)); j++) {
				add = true;
				tri = edges[ed[j]].f1;
				for (int k = 0; k < ed.length; k++) {
					if (tris[k] == tri) {
						add = false;
						break;
					}
				}
				if (add)
					tris[index++] = tri;
				add = true;
				tri = edges[ed[j]].f2;
				for (int k = 0; k < ed.length; k++) {
					if (tris[k] == tri) {
						add = false;
						break;
					}
				}
				if (add)
					tris[index++] = tri;
			}
			// now setup angles at interior vertices
			// and plus and minus angles as defined in ABF++ paper
			for (int j = 0; j < ed.length; j++) {
				tri = tris[j];
				if (v == faces[tri].v1) {
					angleTable[i][j] = 3 * tri + 0;
					angleMTable[i][j] = 3 * tri + 1;
					anglePTable[i][j] = 3 * tri + 2;
				} else if (v == faces[tri].v2) {
					angleTable[i][j] = 3 * tri + 1;
					angleMTable[i][j] = 3 * tri + 2;
					anglePTable[i][j] = 3 * tri + 0;
				} else if (v == faces[tri].v3) {
					angleTable[i][j] = 3 * tri + 2;
					angleMTable[i][j] = 3 * tri + 0;
					anglePTable[i][j] = 3 * tri + 1;
				} else {
					System.out
							.println("pb setting angle tables: vertex not in face");
				}
			}
		}
		var = new double[4 * ntri + 2 * nint];
		for (int i = 0; i < nangles; i++) {
			var[i] = angles[i]; // start from current angle values
		}
		double anglesum;
		for (int i = 0; i < nint; i++) {
			anglesum = 0;
			for (int j = 0; j < angleTable[i].length; j++) {
				anglesum += angles[angleTable[i][j]];
			}
			if (anglesum > 0)
				for (int j = 0; j < angleTable[i].length; j++) {
					var[angleTable[i][j]] *= 2 * Math.PI / anglesum;
					angles[angleTable[i][j]] = var[angleTable[i][j]];
				}
		}
		for (int i = nangles; i < 4 * ntri + nint; i++) {
			var[i] = 0; // Lagrange multipliers are set to zero
		}
		for (int i = 4 * ntri + nint; i < var.length; i++) {
			var[i] = 1; // Length Lagrange multipliers are set to one
		}
		constraints = new double[ntri + 2 * nint];
		gradient = new double[nangles];
		bstar = new double[ntri + 2 * nint];
		solution = new double[4 * ntri + 2 * nint];
		jstar = new FlexCompRowMatrix(2 * nint, ntri);
		jstarstar = new FlexCompRowMatrix(2 * nint, 2 * nint);
		matrix = new FlexCompRowMatrix(2 * nint, 2 * nint);
		bb2vec = new DenseVector(2 * nint);
		// lambdaStar is computed once and for all
		lambdaStar = new double[ntri];
		lambdaStarInv = new double[ntri];
		for (int i = 0; i < ntri; i++) {
			lambdaStar[i] = 0;
			for (int j = 0; j < 3; j++) {
				lambdaStar[i] += weights[3 * i + j] / 2;
			}
			lambdaStarInv[i] = 1 / lambdaStar[i];
		}
		weightsVec = new DenseVector(weights);
		weightsVec.scale(0.5);
	}

	/**
	 * Computes constraints for the ABF++ algorithm
	 *
	 */
	private void computeConstraints() {
		// triangle validity constraint
		for (int i = 0; i < ntri; i++) {
			constraints[i] = -Math.PI;
			for (int j = 0; j < 3; j++) {
				constraints[i] += var[3 * i + j];
			}
		}
		// planarity constraint
		for (int i = 0; i < nint; i++) {
			constraints[ntri + i] = -2 * Math.PI;
			for (int j = 0; j < angleTable[i].length; j++) {
				constraints[ntri + i] += var[angleTable[i][j]];
			}
		}
		// length constraint
		double mproduct, pproduct;
		for (int i = 0; i < nint; i++) {
			pproduct = mproduct = 1;
			for (int j = 0; j < angleTable[i].length; j++) {
				mproduct *= Math.sin(var[angleMTable[i][j]]);
				pproduct *= Math.sin(var[anglePTable[i][j]]);
			}
			constraints[ntri + nint + i] = pproduct - mproduct;
		}
	}
	
	/**
	 * Computes constraints for the ABF++ algorithm
	 *
	 */
	private void computeLinearAbfConstraints() {
		// triangle validity constraint
		for (int i = 0; i < ntri; i++) {
			constraints[i] = Math.PI;
			for (int j = 0; j < 3; j++) {
				constraints[i] -= angles[3 * i + j];
			}
		}
		// planarity constraint
		for (int i = 0; i < nint; i++) {
			constraints[ntri + i] = 2 * Math.PI;
			for (int j = 0; j < angleTable[i].length; j++) {
				constraints[ntri + i] -= angles[angleTable[i][j]];
			}
		}
		// length constraint
		double sum;
		for (int i = 0; i < nint; i++) {
			sum = 0.0;
			for (int j = 0; j < angleTable[i].length; j++) {
				sum -= Math.log(Math.sin(var[angleMTable[i][j]]));
				sum += Math.log(Math.sin(var[anglePTable[i][j]]));
			}
			constraints[ntri + nint + i] = sum;
		}
	}

	/**
	 * Computes J2 matrix
	 * 
	 */
	private void computeJ2() {
		if (j2matrix == null) {
			// first iteration
			j2nonzero = new int[2 * nint][];
			for (int i = 0; i < nint; i++) {
				j2nonzero[i] = new int[angleTable[i].length];
				j2nonzero[nint + i] = new int[anglePTable[i].length
						+ angleMTable[i].length];
				for (int j = 0; j < angleTable[i].length; j++) {
					j2nonzero[i][j] = angleTable[i][j];
					j2nonzero[nint + i][j] = anglePTable[i][j];
					j2nonzero[nint + i][angleTable[i].length + j] = angleMTable[i][j];
				}
			}
			j2matrix = new CompRowMatrix(2 * nint, nangles, j2nonzero);
		}
		// zero out entries
		j2matrix.zero();
		// planarity constraint
		for (int i = 0; i < nint; i++) {
			for (int j = 0; j < angleTable[i].length; j++) {
				j2matrix.set(i, angleTable[i][j], 1.0);
			}
		}
		// length constraint
		double pproduct;
		double mproduct;
		for (int i = 0; i < nint; i++) {
			for (int j = 0; j < angleTable[i].length; j++) {
				pproduct = mproduct = 1;
				for (int k = 0; k < angleTable[i].length; k++) {
					if (k != j) {
						pproduct *= Math.sin(var[anglePTable[i][k]]);
						mproduct *= Math.sin(var[angleMTable[i][k]]);
					} else {
						pproduct *= Math.cos(var[anglePTable[i][k]]);
						mproduct *= -Math.cos(var[angleMTable[i][k]]);
					}
				}
				j2matrix.add(nint + i, anglePTable[i][j], pproduct);
				j2matrix.add(nint + i, angleMTable[i][j], mproduct);
			}
		}
	}

	private void computeGradient() {
		for (int i = 0; i < ntri; i++) {
			for (int j = 0; j < 3; j++) {
				gradient[3 * i + j] = (2 / weights[3 * i + j])
						* (var[3 * i + j] - angles[3 * i + j]);
				gradient[3 * i + j] += var[nangles + i];
			}
		}
		for (int i = 0; i < nint; i++) {
			for (int j = 0; j < angleTable[i].length; j++) {
				gradient[angleTable[i][j]] += j2matrix.get(i, angleTable[i][j])
						* var[4 * ntri + i];
				gradient[anglePTable[i][j]] += j2matrix.get(nint + i,
						anglePTable[i][j])
						* var[4 * ntri + nint + i];
				gradient[angleMTable[i][j]] += j2matrix.get(nint + i,
						angleMTable[i][j])
						* var[4 * ntri + nint + i];
			}
		}
	}

	/**
	 * Computes the funtion to minimize (ABF++)
	 * 
	 * @return The function value
	 */
	private double computeFunction() {
		double f = 0;
		for (int i = 0; i < nangles; i++) {
			f += (1 / weights[i]) * (var[i] - angles[i]) * (var[i] - angles[i]);
		}
		for (int i = 0; i < ntri + 2 * nint; i++) {
			f += var[nangles + i] * constraints[i];
		}
		return f;
	}

	/**
	 * Helper method to verify if the gradient is properly computed.
	 *
	 */
	@SuppressWarnings("unused")
	private void checkGradient() {
		double value, f1, f2;

		System.out.println(nangles + " " + 4 * ntri + " " + (4 * ntri + nint));
		for (int i = 0; i < nangles; i++) {
			value = var[i];
			var[i] = value - 0.0001;
			computeConstraints();
			f1 = computeFunction();
			var[i] = value + 0.0001;
			computeConstraints();
			f2 = computeFunction();
			var[i] = value;
			value = (f2 - f1) / 0.0002;
			System.out.println(i + " " + value + " " + gradient[i]);
		}
		System.out.println("/***** contrainsts *****/");
		for (int i = nangles; i < var.length; i++) {
			value = var[i];
			var[i] = value - 0.0001;
			computeConstraints();
			f1 = computeFunction();
			var[i] = value + 0.0001;
			computeConstraints();
			f2 = computeFunction();
			var[i] = value;
			value = (f2 - f1) / 0.0002;
			System.out
					.println(i + " " + value + " " + constraints[i - nangles]);
		}
		System.out.println("/***** end gradient *****/");

	}
	
	private UnfoldedMesh computeUnfoldedMeshSimple(ArrayList<Integer> vertList,
			ArrayList<Integer> edgeList, ArrayList<Integer> faceList,
			UnfoldedVertex[] uverts, UnfoldedEdge[] uedges,
			UnfoldedFace[] ufaces) throws IterativeSolverNotConvergedException {
		System.out.println("Compute unfolded mesh curde reconstruction");
		UnfoldedVertex[] vertices = new UnfoldedVertex[vertList.size()];
		UnfoldedEdge[] edges = new UnfoldedEdge[edgeList.size()];
		UnfoldedFace[] faces = new UnfoldedFace[faceList.size()];
		int[] vertTrans = new int[uverts.length];
		int[] edgeTrans = new int[uedges.length];
		int[] faceTrans = new int[ufaces.length];
		// first, translate the indexes
		for (int i = 0; i < vertTrans.length; i++) {
			vertTrans[i] = -1;
		}
		for (int i = 0; i < vertices.length; i++) {
			vertTrans[vertList.get(i)] = i;
		}
		for (int i = 0; i < edgeTrans.length; i++) {
			edgeTrans[i] = -1;
		}
		for (int i = 0; i < edges.length; i++) {
			edgeTrans[edgeList.get(i)] = i;
		}
		for (int i = 0; i < faceTrans.length; i++) {
			faceTrans[i] = -1;
		}
		for (int i = 0; i < faces.length; i++) {
			faceTrans[faceList.get(i)] = i;
		}
		for (int i = 0; i < vertices.length; i++) {
			vertices[i] = uverts[vertList.get(i)].duplicate();
			if (edgeTrans[vertices[i].edge] == -1)
				System.out.println("Pb edge translation");
			vertices[i].edge = edgeTrans[vertices[i].edge];
		}
		for (int i = 0; i < edges.length; i++) {
			edges[i] = uedges[edgeList.get(i)].duplicate();
			if (vertTrans[edges[i].v1] == -1)
				System.out.println("Pb edge vert v1");
			if (vertTrans[edges[i].v2] == -1)
				System.out.println("Pb edge vert v2");
			edges[i].v1 = vertTrans[edges[i].v1];
			edges[i].v2 = vertTrans[edges[i].v2];
			if (edges[i].f1 != -1) {
				if (faceTrans[edges[i].f1] == -1)
					System.out.println("Pb edge face f1");
				edges[i].f1 = faceTrans[edges[i].f1];
			}
			if (edges[i].f2 != -1) {
				if (faceTrans[edges[i].f2] == -1)
					System.out.println("Pb edge face f2");
				edges[i].f2 = faceTrans[edges[i].f2];
			}
		}
		for (int i = 0; i < faces.length; i++) {
			faces[i] = ufaces[faceList.get(i)].duplicate();
			if (vertTrans[faces[i].v1] == -1)
				System.out.println("Pb face vert v1");
			if (vertTrans[faces[i].v2] == -1)
				System.out.println("Pb face vert v2");
			if (vertTrans[faces[i].v3] == -1)
				System.out.println("Pb face vert v3");
			faces[i].v1 = vertTrans[faces[i].v1];
			faces[i].v2 = vertTrans[faces[i].v2];
			faces[i].v3 = vertTrans[faces[i].v3];
			if (edgeTrans[faces[i].e1] == -1)
				System.out.println("Pb face edge e1");
			if (edgeTrans[faces[i].e2] == -1)
				System.out.println("Pb face edge e2");
			if (edgeTrans[faces[i].e3] == -1)
				System.out.println("Pb face edge e3");
			faces[i].e1 = edgeTrans[faces[i].e1];
			faces[i].e2 = edgeTrans[faces[i].e2];
			faces[i].e3 = edgeTrans[faces[i].e3];
		}
		Vec2 center = new Vec2();
		for (int i = 0; i < vertices.length; i++) {
			center.add(vertices[i].r);
		}
		//check if the mesh is right handed
		boolean leftHanded = false;
		Vec2 v1r = vertices[faces[0].v3].r.minus(vertices[faces[0].v1].r);
		Vec2 v2r = vertices[faces[0].v2].r.minus(vertices[faces[0].v1].r);
		if (v1r.x * v2r.y - v1r.y * v2r.x < 0) {
			leftHanded = true;
		}
		// center the mesh
		center.scale(1.0 / ((double) vertices.length));
		if (!leftHanded) {
			center.y *= -1;
		}
		for (int i = 0; i < vertices.length; i++) {
			if (!leftHanded) {
				vertices[i].r.y *= -1;
			}
			vertices[i].r.subtract(center);
		}
		return new UnfoldedMesh(vertices, edges, faces);
	}
	
	/**
	 * Given an edge and a face, this method checks the 3rd vertex as being
	 * unfolded if it's not already checked
	 */
	private void computeFaceNew(int e, int f, UnfoldedVertex[] uverts,
			UnfoldedEdge[] uedges, UnfoldedFace[] ufaces,
			boolean[] unfoldedFace, boolean[] unfoldedVerts,
			ArrayList<Integer> vertList, ArrayList<Integer> edgeList,
			ArrayList<Integer> faceList, double[] angles) {
		int v1, v2, v3;
		v1 = ufaces[f].v1;
		v2 = ufaces[f].v2;
		v3 = ufaces[f].v3;
		Vec2 v;
		//System.out.println("Face: " + f);
		double sin2, sin3, sin1, cos1;
		if ((uedges[e].v1 == v1 && uedges[e].v2 == v2)
				|| (uedges[e].v1 == v2 && uedges[e].v2 == v1)) {
			// add v3
			//System.out.println("add v3");
			if (!unfoldedVerts[ufaces[f].v3]) {
				unfoldedVerts[ufaces[f].v3] = true;
				vertList.add(v3);
				sin1 = Math.sin(angles[3*f]);
				sin2 = Math.sin(angles[3*f+1]);
				sin3 = Math.sin(angles[3*f+2]);
				cos1 = Math.cos(angles[3*f]);
				v = uverts[v2].r.minus(uverts[v1].r);
				uverts[v3].r = new Vec2( (sin2/sin3)*(cos1*v.x - sin1*v.y) + uverts[v1].r.x,
						(sin2/sin3)*(sin1*v.x + cos1*v.y) + uverts[v1].r.y);
			}
		} else if ((uedges[e].v1 == v2 && uedges[e].v2 == v3)
				|| (uedges[e].v1 == v3 && uedges[e].v2 == v2)) {
			if (!unfoldedVerts[ufaces[f].v1]) {
				// add v1
				//System.out.println("add v1");
				unfoldedVerts[ufaces[f].v1] = true;
				vertList.add(v1);
				sin1 = Math.sin(angles[3*f+1]);
				sin2 = Math.sin(angles[3*f+2]);
				sin3 = Math.sin(angles[3*f]);
				cos1 = Math.cos(angles[3*f+1]);
				v = uverts[v3].r.minus(uverts[v2].r);
				uverts[v1].r = new Vec2( (sin2/sin3)*(cos1*v.x - sin1*v.y) + uverts[v2].r.x,
						(sin2/sin3)*(sin1*v.x + cos1*v.y) + uverts[v2].r.y);
			}
		} else {
			if (!unfoldedVerts[ufaces[f].v2]) {
				// add v2
				//System.out.println("add v2");
				unfoldedVerts[ufaces[f].v2] = true;
				vertList.add(v2);
				sin1 = Math.sin(angles[3*f+2]);
				sin2 = Math.sin(angles[3*f]);
				sin3 = Math.sin(angles[3*f+1]);
				cos1 = Math.cos(angles[3*f+2]);
				v = uverts[v1].r.minus(uverts[v3].r);
				uverts[v2].r = new Vec2( (sin2/sin3)*(cos1*v.x - sin1*v.y) + uverts[v3].r.x,
						(sin2/sin3)*(sin1*v.x + cos1*v.y) + uverts[v3].r.y);
			}
		}
//		System.out.println("Face: " + f);
//		System.out.println("v1: " + uverts[v1].r);
//		System.out.println("v2: " + uverts[v2].r);
//		System.out.println("v3: " + uverts[v3].r);
//		cos1 = Math.acos((uverts[v2].r.minus(uverts[v1].r).dot(uverts[v3].r.minus(uverts[v1].r))/
//					((uverts[v2].r.minus(uverts[v1].r).length())*(uverts[v3].r.minus(uverts[v1].r)).length())));
//		System.out.println(angles[3*f] + " : " + cos1 );
//		cos1 = Math.acos((uverts[v1].r.minus(uverts[v2].r).dot(uverts[v3].r.minus(uverts[v2].r))/
//				((uverts[v1].r.minus(uverts[v2].r).length())*(uverts[v3].r.minus(uverts[v2].r)).length())));
//		System.out.println(angles[3*f+1] + " : " + cos1 );
//		cos1 = Math.acos((uverts[v2].r.minus(uverts[v3].r).dot(uverts[v1].r.minus(uverts[v3].r))/
//				((uverts[v2].r.minus(uverts[v3].r).length())*(uverts[v1].r.minus(uverts[v3].r)).length())));
//		System.out.println(angles[3*f+2] + " : " + cos1 );
		unfoldedFace[f] = true;
		faceList.add(f);
	}

}
