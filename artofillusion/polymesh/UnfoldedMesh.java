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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;

import artofillusion.math.Vec2;
import artofillusion.object.TriangleMesh;

/**
 * An unfolded mesh is a 2D triangle mesh that represents a piece of a facetted
 * mesh which has been unfolded. Heavily derived from triangle mesh class.
 * 
 * @author Francois Guillet
 * 
 */
public class UnfoldedMesh {

    public static class UnfoldedVertex {
	public Vec2 r; // vertex position

	public int id; // vertex id to establish a correspondance with original

	// mesh

	public boolean pinned; // true if the vertex is pinned to the image;

	public int edge; // an edge sharing this vertex

	public UnfoldedVertex(Vec2 r) {
		this.r = r;
	}

	public UnfoldedVertex(TriangleMesh.Vertex v) {
	    edge = v.firstEdge;
	    this.r = new Vec2(v.r.x, v.r.y);
	}

	public UnfoldedVertex duplicate() {
		UnfoldedVertex v = null;
		if (r != null) {
			v = new UnfoldedVertex(new Vec2(r));
		}
		else {
			v = new UnfoldedVertex((Vec2)null);
		}
	    v.edge = edge;
	    v.id = id;
	    v.pinned = pinned;
	    return v;
	}
	
	public void writeToFile(DataOutputStream out)
	    throws IOException {
	    out.writeShort(0);
	    out.writeInt(edge);
	    out.writeInt(id);
	    out.writeBoolean(pinned);
	    r.writeToFile(out);
	}
	
	public UnfoldedVertex(DataInputStream in) throws IOException,
	    InvalidObjectException {
	    short version = in.readShort();
	    if (version < 0 || version > 0)
		    throw new InvalidObjectException("");
	    edge = in.readInt();
	    id =in.readInt();
	    pinned = in.readBoolean();
	    r = new Vec2(in);
	}
    }

    public static class UnfoldedEdge {
	public int v1; // edge first vertex

	public int v2; // edge second vertex

	public int f1; // face that shares edge

	public int f2; // second face that shares edge

	public boolean hidden; // true if the edge does not exist in the

	// original

	// folded mesh structure

	public UnfoldedEdge(int vertex1, int vertex2, int face1) {
	    v1 = vertex1;
	    v2 = vertex2;
	    f1 = face1;
	    f2 = -1;
	    hidden = false;
	}

	public UnfoldedEdge(int vertex1, int vertex2, int face1, int face2) {
	    v1 = vertex1;
	    v2 = vertex2;
	    f1 = face1;
	    f2 = face2;
	    hidden = false;
	}

	public UnfoldedEdge(TriangleMesh.Edge edge) {
	    v1 = edge.v1;
	    v2 = edge.v2;
	    f1 = edge.f1;
	    f2 = edge.f2;
	    hidden = false;
	}

	public UnfoldedEdge duplicate() {
	    UnfoldedEdge e = new UnfoldedEdge(v1, v2, f1, f2);
	    e.hidden = hidden;
	    return e;
	}
	
	public void writeToFile(DataOutputStream out)
	    throws IOException {
	    out.writeShort(0);
	    out.writeInt(v1);
	    out.writeInt(v2);
	    out.writeInt(f1);
	    out.writeInt(f2);
	    out.writeBoolean(hidden);
	}
	
	public UnfoldedEdge(DataInputStream in) throws IOException,
	    InvalidObjectException {
	    short version = in.readShort();
	    if (version < 0 || version > 0)
		    throw new InvalidObjectException("");
	    v1 = in.readInt();
	    v2 = in.readInt();
	    f1 = in.readInt();
	    f2 = in.readInt();
	    hidden = in.readBoolean();
	}
    }

    public static class UnfoldedFace {
	public int v1, v2, v3; // vertices

	public int e1, e2, e3; // edges

	public int id; // face id to establish correspondance with original

	// mesh

	public UnfoldedFace(int v1, int v2, int v3, int e1, int e2, int e3) {
	    this.v1 = v1;
	    this.v2 = v2;
	    this.v3 = v3;
	    this.e1 = e1;
	    this.e2 = e2;
	    this.e3 = e3;
	}

	public UnfoldedFace(TriangleMesh.Face face) {
	    this.v1 = face.v1;
	    this.v2 = face.v2;
	    this.v3 = face.v3;
	    this.e1 = face.e1;
	    this.e2 = face.e2;
	    this.e3 = face.e3;
	}

	public UnfoldedFace duplicate() {
	    UnfoldedFace f = new UnfoldedFace(v1, v2, v3, e1, e2, e3);
	    f.id = id;
	    return f;
	}
	
	public void writeToFile(DataOutputStream out)
	    throws IOException {
	    out.writeShort(0);
	    out.writeInt(v1);
	    out.writeInt(v2);
	    out.writeInt(v3);
	    out.writeInt(e1);
	    out.writeInt(e2);
	    out.writeInt(e3);
	    out.writeInt(id);
	}
	
	public UnfoldedFace(DataInputStream in) throws IOException,
	    InvalidObjectException {
	    short version = in.readShort();
	    if (version < 0 || version > 0)
		    throw new InvalidObjectException("");
	    v1 = in.readInt();
	    v2 = in.readInt();
	    v3 = in.readInt();
	    e1 = in.readInt();
	    e2 = in.readInt();
	    e3 = in.readInt();
	    id = in.readInt();
	}
    }

    UnfoldedVertex vertices[]; // mesh vertices

    UnfoldedEdge edges[]; // mesh edges

    UnfoldedFace faces[]; // mesh faces
    
    private String name;

    public UnfoldedMesh(UnfoldedVertex[] vertices, UnfoldedEdge[] edges,
	    UnfoldedFace[] faces) {
	super();
	this.vertices = vertices;
	this.edges = edges;
	this.faces = faces;
	name = "Unfolded Mesh";
    }

    public UnfoldedMesh duplicate() {
	UnfoldedVertex[] v = new UnfoldedVertex[vertices.length];
	for (int i = 0; i < v.length; i++) {
	    v[i] = vertices[i].duplicate();
	}
	UnfoldedEdge[] e = new UnfoldedEdge[edges.length];
	for (int i = 0; i < e.length; i++) {
	    e[i] = edges[i].duplicate();
	}
	UnfoldedFace[] f = new UnfoldedFace[faces.length];
	for (int i = 0; i < f.length; i++) {
	    f[i] = faces[i].duplicate();
	}
	UnfoldedMesh mesh = new UnfoldedMesh(v, e, f);
	mesh.name = new String(name);
	return mesh;
    }
    
    public void writeToFile(DataOutputStream out)
    throws IOException {
	out.writeShort(0);
	out.writeUTF(name);
	out.writeInt(vertices.length);
	for (int i = 0; i < vertices.length; i++) {
	    vertices[i].writeToFile(out);
	}
	out.writeInt(edges.length);
	for (int i = 0; i < edges.length; i++) {
	    edges[i].writeToFile(out);
	}
	out.writeInt(faces.length);
	for (int i = 0; i < faces.length; i++) {
	    faces[i].writeToFile(out);
	}
    }

    public UnfoldedMesh(DataInputStream in) throws IOException,
    InvalidObjectException {
	short version = in.readShort();
	if (version < 0 || version > 0)
	    throw new InvalidObjectException("");
	name = in.readUTF();
	int count = in.readInt();
	vertices = new UnfoldedVertex[count];
	for (int i = 0; i < vertices.length; i++) {
	    vertices[i] = new UnfoldedVertex(in);
	}
	count = in.readInt();
	edges = new UnfoldedEdge[count];
	for (int i = 0; i < edges.length; i++) {
	    edges[i] = new UnfoldedEdge(in);
	}
	count = in.readInt();
	faces = new UnfoldedFace[count];
	for (int i = 0; i < faces.length; i++) {
	    faces[i] = new UnfoldedFace(in);
	}
    }

    /**
         * @return the edges
         */
    public UnfoldedEdge[] getEdges() {
	return edges;
    }

    /**
         * @param edges
         *                The edges to set
         */
    public void setEdges(UnfoldedEdge[] edges) {
	this.edges = edges;
    }

    /**
         * @return The faces
         */
    public UnfoldedFace[] getFaces() {
	return faces;
    }

    /**
         * @param faces
         *                the faces to set
         */
    public void setFaces(UnfoldedFace[] faces) {
	this.faces = faces;
    }

    /**
         * @return the vertices
         */
    public UnfoldedVertex[] getVertices() {
	return vertices;
    }

    /**
         * @param vertices
         *                the vertices to set
         */
    public void setVertices(UnfoldedVertex[] vertices) {
	this.vertices = vertices;
    }
    

    /**
     * @return Mesh piece name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name The piece name to set
     */
    public void setName(String name) {
        this.name = name;
    }
}
