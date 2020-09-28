/*
 *  Copyright (C) 2005 by Francois Guillet
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package artofillusion.polymesh;

import java.util.Vector;

import artofillusion.math.Vec3;
import artofillusion.object.TriangleMesh;

/**
 * This class is responsible for utilities concerning texture like findings seams and mesh unfolding
 */
public class TextureUtilities 
{
    /**
     * Automatic seam computing according to:
     * "Seamster: Inconspicuous Low-Distorsion Texture Seam Layout"
     * by Alla Scheffer and John C. Hart
     * @param mesh
     * @param r
     * @return
     */ 
    public static boolean[] findSeams(TriangleMesh mesh, int r)
    {
        TriangleMesh.Vertex[] v = (TriangleMesh.Vertex[])mesh.getVertices();
        TriangleMesh.Edge[] e = mesh.getEdges();
        TriangleMesh.Face[] f = mesh.getFaces();
        Vec3 v1, v2, v3;
        int next, maxDist;
        double dist, distance;
        int f1, f2, pred;
        Vector vert;
        
        boolean[] seams = new boolean[e.length];
        double[][] D = new double[v.length][r];
        int[] ve;
        vert = new Vector();
        //distorsion measurement
        for (int i = 0; i < r; i++)
        {
            for (int j = 0; j < v.length; j++)
            {
                v1 = v[j].r;
                ve = v[j].getEdges();
                if ( r != 0 )
                {
                    /*distance = 0;
                    maxDist = -1;
                    for (int k = 0; k < ve.length; ++k)
                    {
                        if ( e[ve[k]].v1 != j )
                            v2 = v[e[ve[k]].v1].r;
                        else
                            v2 = v[e[ve[k]].v2].r;
                        dist = v2.distance(v1);
                        if (dist > distance)
                        {
                            distance = dist;
                            maxDist = k;
                        }
                    }
                    boolean done = false;
                    int l = maxDist;
                    if (i > 1)
                    {
                        //recalculer l;
                    }
                    pred = -1;
                    vert.clear();
                    while ( !done )
                    {
                        l = getNextContour( v, l, maxDist, v1 );
                    }*/
                }
                else
                    for (int k = 0; k < ve.length; ++k)
                {
                    if ( e[ve[k]].v1 != j )
                        v2 = v[e[ve[k]].v1].r;
                    else
                        v2 = v[e[ve[k]].v2].r;
                    next = k + 1;
                    if ( next == ve.length )
                        next = 0;
                    if ( e[ve[next]].v1 != j )
                        v3 = v[e[ve[next]].v1].r;
                    else
                        v3 = v[e[ve[next]].v2].r;
                    v2.subtract(v1);
                    v3.subtract(v1);
                    v2.normalize();
                    v3.normalize();
                    D[j][0] += Math.acos(v2.dot(v3));
                }
                D[j][0] = ( 2 * Math.PI - D[j][0] ) / ( 2 * Math.PI );
            }
        }
        return seams;
    }
    
    private static int getNextContour( TriangleMesh.Vertex[] v, TriangleMesh.Edge[] e, TriangleMesh.Face[] f,  int l, double maxDist, Vec3 v1)
    {
        int f1, f2;

        int[] le = v[l].getEdges();
                        for (int k = 0; k < le.length; ++k)
                        {
                            f1 = e[le[k]].f1;
                            if ( f1 != -1 )
                            {
                                if ( (  v[f[f1].v1].r.distance(v1) <= maxDist &&
                                        v[f[f1].v2].r.distance(v1) <= maxDist &&
                                        v[f[f1].v3].r.distance(v1) > maxDist ) ||
                                      ( v[f[f1].v2].r.distance(v1) <= maxDist &&
                                        v[f[f1].v3].r.distance(v1) <= maxDist &&
                                        v[f[f1].v1].r.distance(v1) > maxDist ) || 
                                      ( v[f[f1].v3].r.distance(v1) <= maxDist &&
                                        v[f[f1].v1].r.distance(v1) <= maxDist &&
                                        v[f[f1].v2].r.distance(v1) > maxDist ) )
                                {
                                    
                                }
                            }
                        }
        return 0;
    }
}
