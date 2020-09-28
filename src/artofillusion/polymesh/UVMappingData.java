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
import java.awt.Point;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.ArrayList;

import artofillusion.Scene;
import artofillusion.math.Vec2;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedEdge;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedFace;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedVertex;
import artofillusion.texture.Texture;
import artofillusion.texture.TextureMapping;
import artofillusion.ui.Translate;

/**
 * This class holds unfolded meshes and UV mapping editor information
 * for saving into AoI files and future reuse.
 * 
 * @author Francois Guillet
 *
 */
public class UVMappingData {

	/**
	 * This class holds a mapping description.
	 * @author pims
	 *
	 */
	public class UVMeshMapping {
		public Vec2[][] v; //mesh pieces vertices positions

		public String name; //mapping name

		public ArrayList<Integer> textures; //textures associated

		//to this mapping (ids)

		public Color edgeColor;

		private UVMeshMapping() {
		}

		public UVMeshMapping(Vec2[][] v, String name) {
			this.v = v;
			this.name = name;
			textures = new ArrayList<Integer>();
			edgeColor = new Color(0, 0, 0);
		}

		public UVMeshMapping duplicate() {
			UVMeshMapping newMapping = new UVMeshMapping();
			newMapping.v = new Vec2[v.length][];
			for (int i = 0; i < v.length; i++) {
				newMapping.v[i] = new Vec2[v[i].length];
				for (int j = 0; j < v[i].length; j++) {
					newMapping.v[i][j] = new Vec2(v[i][j]);
				}
			}
			newMapping.name = new String(name);
			newMapping.textures = new ArrayList<Integer>();
			for (int i = 0; i < textures.size(); i++) {
				newMapping.textures.add(textures.get(i));
			}
			newMapping.edgeColor = new Color(edgeColor.getRed(), edgeColor
					.getGreen(), edgeColor.getBlue());
			return newMapping;
		}

		public void writeToFile(DataOutputStream out, Scene scene)
				throws IOException {
			out.writeShort(1);
			out.writeUTF(name);
			Texture tex;
			int numTex = scene.getNumTextures();
			out.writeInt(textures.size());
			for (int i = 0; i < textures.size(); i++) {
				boolean found = false;
				for (int j = 0; j < numTex; j++) {
					tex = scene.getTexture(j);
					if (tex.getID() == textures.get(i).intValue()) {
						out.writeInt(j);
						found = true;
						break;
					}
				}
				if (!found) {
					out.writeInt(-1);
				}
			}
			out.writeInt(v.length);
			for (int i = 0; i < v.length; i++) {
				out.writeInt(v[i].length);
				for (int j = 0; j < v[i].length; j++) {
					v[i][j].writeToFile(out);
				}
			}
			out.writeInt(edgeColor.getRed());
			out.writeInt(edgeColor.getGreen());
			out.writeInt(edgeColor.getBlue());
		}

		public UVMeshMapping(DataInputStream in, Scene scene)
				throws IOException, InvalidObjectException {
			short version = in.readShort();
			if (version < 0 || version > 1)
				throw new InvalidObjectException("");
			name = in.readUTF();
			int count = in.readInt();
			textures = new ArrayList<Integer>();
			Texture tex;
			int index;
			int numTex = scene.getNumTextures();
			for (int i = 0; i < count; i++) {
				index = in.readInt();
				if (index == -1) {
					//texture unknown at save time
					continue;
				}
				if (index < numTex) {
					tex = scene.getTexture(index);
					System.out.println("texture loaded ok");
					textures.add(tex.getID());
				} else {
					System.out.println("texture loading workaround");
					textures.add(index);
				}
			}
			count = in.readInt();
			v = new Vec2[count][];
			for (int i = 0; i < count; i++) {
				v[i] = new Vec2[in.readInt()];
				for (int j = 0; j < v[i].length; j++) {
					v[i][j] = new Vec2(in);
				}
			}
			if (version >= 1) {
				int r, g, b;
				r = in.readInt();
				g = in.readInt();
				b = in.readInt();
				edgeColor = new Color(r, g, b);
			} else {
				edgeColor = new Color(0, 0, 0);
			}
		}
	}

	protected UnfoldedMesh[] meshes; //unfolded mesh pieces
	
	public int[] displayed; //table to number of displayed vertices;

	public int[][] verticesTable;

	public int[][] invVerticesTable;


	protected ArrayList<UVMeshMapping> mappings;

	protected int sampling; //texture display resolution is persistent

	private UVMappingData() {
	}

	public UVMappingData(UnfoldedMesh[] meshes) {
		super();
		this.meshes = meshes;
		mappings = new ArrayList<UVMeshMapping>();
		addNewMapping(Translate.text("polymesh:mapping") + " #1", null);
		sampling = 1;
		setTables();
	}

	public UVMappingData duplicate() {
		UVMappingData newData = new UVMappingData();
		newData.meshes = meshes;
		newData.mappings = new ArrayList<UVMeshMapping>();
		for (int i = 0; i < mappings.size(); i++) {
			newData.mappings.add(mappings.get(i).duplicate());
		}
		newData.sampling = sampling;
		newData.setTables();
		return newData;
	}

	public void writeToFile(DataOutputStream out, Scene scene)
			throws IOException {
		out.writeShort(0);
		out.writeInt(meshes.length);
		for (int i = 0; i < meshes.length; i++) {
			meshes[i].writeToFile(out);
		}
		out.writeInt(mappings.size());
		for (int i = 0; i < mappings.size(); i++) {
			mappings.get(i).writeToFile(out, scene);
		}
		out.writeInt(sampling);
	}

	public UVMappingData(DataInputStream in, Scene scene) throws IOException,
			InvalidObjectException {
		short version = in.readShort();
		if (version < 0 || version > 0)
			throw new InvalidObjectException("");
		int count = in.readInt();
		meshes = new UnfoldedMesh[count];
		for (int i = 0; i < count; i++) {
			meshes[i] = new UnfoldedMesh(in);
		}
		count = in.readInt();
		mappings = new ArrayList<UVMeshMapping>();
		for (int i = 0; i < count; i++) {
			mappings.add(new UVMeshMapping(in, scene));
		}
		sampling = in.readInt();
		setTables();
	}

	/**
	 * Creates a mapping from default vertices positions or from
	 * an existing mapping vertices positions
	 * @param name The name of the new mapping
	 * @param dupMapping Mapping to duplicate vertices positions from.
	 * May be null in which case default positions are used.
	 * @return The new mapping
	 */
	public UVMeshMapping addNewMapping(String name, UVMeshMapping dupMapping) {
		UVMeshMapping mapping;
		Vec2[][] map = new Vec2[meshes.length][];
		for (int i = 0; i < meshes.length; i++) {
			if (dupMapping != null) {
				Vec2[][] v = dupMapping.v;
				map[i] = new Vec2[v[i].length];
				for (int j = 0; j < v[i].length; j++) {
					map[i][j] = new Vec2(v[i][j]);
				}
			} else {
				UnfoldedVertex[] v = meshes[i].getVertices();
				map[i] = new Vec2[v.length];
				for (int j = 0; j < v.length; j++) {
					map[i][j] = new Vec2(v[j].r);
				}
			}
		}
		mappings.add(mapping = new UVMeshMapping(map, name));
		return mapping;
	}
	
	public void setTables() {
		displayed = new int[meshes.length];
		verticesTable = new int[meshes.length][];
		invVerticesTable = new int[meshes.length][];
		for (int i = 0; i < meshes.length; i++) {
			int count = 0;
			UnfoldedVertex[] vert = meshes[i].vertices;
			for (int j = 0; j < vert.length; j++) {
				if (vert[j].id != -1)
					count++;
			}
			displayed[i] = count;
			verticesTable[i] = new int[count];
			invVerticesTable[i] = new int[vert.length];
			count = 0;
			for (int j = 0; j < vert.length; j++) {
				if (vert[j].id == -1) {
					invVerticesTable[i][j] = -1;
					continue;
				}
				verticesTable[i][count] = j;
				invVerticesTable[i][j] = count;
				count++;
			}
		}		
	}

	/**
	 * @return the sampling set by user for texture image
	 */
	public int getSampling() {
		return sampling;
	}

	/**
	 * @param sampling The sampling to set for creating texture image
	 */
	public void setSampling(int sampling) {
		this.sampling = sampling;
	}

	/**
	 * @return The original unfolded meshes
	 */
	public UnfoldedMesh[] getMeshes() {
		return meshes;
	}

	/**
	 * @return The list of mappings
	 */
	public ArrayList<UVMeshMapping> getMappings() {
		return mappings;
	}

	/**
	 * @return The mapping at a given index
	 */
	public UVMeshMapping getMapping(int index) {
		return mappings.get(index);
	}
}
