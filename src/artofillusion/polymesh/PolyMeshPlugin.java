
/*
 *  Copyright 2004-2007 Francois Guillet
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import java.io.InputStream;
import java.util.Locale;
import java.util.ResourceBundle;

import artofillusion.ArtOfIllusion;
import artofillusion.LayoutWindow;
import artofillusion.Plugin;
import artofillusion.Scene;
import artofillusion.keystroke.KeystrokeManager;
import artofillusion.keystroke.KeystrokeRecord;
import artofillusion.math.CoordinateSystem;
import artofillusion.object.ObjectInfo;
import artofillusion.object.SplineMesh;
import artofillusion.object.TriangleMesh;
import artofillusion.ui.ToolPalette;
import artofillusion.ui.Translate;
import buoy.widget.BMenu;
import buoy.widget.BMenuBar;
import buoy.widget.BMenuItem;
import buoy.widget.BStandardDialog;
import buoy.widget.MenuWidget;


/**
 *  This is the plugin class that plugs PolyMesh structure and editing features into AoI
 *
 *@author     Francois Guillet
 */
public class PolyMeshPlugin implements Plugin
{
    public static ResourceBundle resources;
	
	/**
     *  Process messages sent to plugin by AoI (see AoI API description)
     *
     *@param  message  The message
     *@param  args     Arguments depending on the message
     */
    public void processMessage( int message, Object args[] )
    {
        if ( message == Plugin.APPLICATION_STARTING )
        {
        	resources = ResourceBundle.getBundle( "polymesh", ArtOfIllusion.getPreferences().getLocale() );
        	KeystrokeRecord[] keys = KeystrokeManager.getAllRecords();
            boolean keysImplemented = false;
            for (int i = 0; i  < keys.length; i++)
            {
                if (keys[i].getName().endsWith("(PolyMesh)"))
                {
                    keysImplemented = true;
                    break;
                }
            }
            if ( ! keysImplemented )
            {
                try
                {
                    InputStream in = getClass().getResourceAsStream("/PMkeystrokes.xml");
                    KeystrokeManager.addRecordsFromXML(in);
                    in.close();
                    KeystrokeManager.saveRecords();
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
        else if ( message == Plugin.SCENE_WINDOW_CREATED )
        {
            LayoutWindow layout = (LayoutWindow) args[0];
            ToolPalette palette = layout.getToolPalette();
            palette.addTool( 8, new CreatePolyMeshTool( layout ));
            palette.toggleDefaultTool();
            palette.toggleDefaultTool();
            BMenuItem menuItem = Translate.menuItem( "polymesh:convertToPolyMesh", new ConvertObject( layout ), "doConvert" );
            BMenu toolsMenu = layout.getObjectMenu();
            int count = toolsMenu.getChildCount();
            MenuWidget[] mw = new MenuWidget[count];
            for (int i = count - 1; i >= 0; i-- )
                mw[i] = toolsMenu.getChild(i);
            toolsMenu.removeAll();
            for (int i = 0; i <= 7; i++ )
                toolsMenu.add(mw[i]);
            toolsMenu.add( menuItem );
            for (int i = 8; i < count; i++ )
                toolsMenu.add(mw[i]);
            layout.layoutChildren();
        }
    }
    
    
    private class ConvertObject
    {
        LayoutWindow window;
        
        public ConvertObject( LayoutWindow w )
        {
            window = w;
        }
        
        private void doConvert()
        {
            PolyMesh mesh = null;
            Scene scene = window.getScene();
            int[] sel = scene.getSelection();
            for (int i = 0; i < sel.length; i++)
            {
                Object obj = scene.getObject( sel[i] ).object;
                if ( obj instanceof SplineMesh )
                {
                    mesh = new PolyMesh( (SplineMesh) obj );
                    if ( mesh != null )
                    {                                                      
                        ObjectInfo info = scene.getObject( sel[i] );
                        CoordinateSystem coords = new CoordinateSystem();
                        coords.copyCoords(info.coords);
                        window.addObject( mesh, coords, info.name, null );
                    }
                }
                else if (obj instanceof TriangleMesh)
                {
                    BStandardDialog dlg = new BStandardDialog(Translate.text("polymesh:triangleToPolymesh"), Translate.text("polymesh:convertToQuads"), BStandardDialog.QUESTION); 
                    int r = dlg.showOptionDialog( window, new String[] { Translate.text("polymesh:findQuadsDistance"), Translate.text("polymesh:findQuadsAngular"), Translate.text("polymesh:keepTriangles") }, Translate.text("polymesh:convertToQuads") );
                    mesh = new PolyMesh( (TriangleMesh) obj, r == 0 || r == 1, r == 1  );
                    if ( mesh != null )
                    {
                        ObjectInfo info = scene.getObject( sel[i] );
                        CoordinateSystem coords = new CoordinateSystem();
                        coords.copyCoords(info.coords);
                        window.addObject( mesh, coords, scene.getObject( sel[i] ).name, null );
                    }
                }
            }
            window.updateImage();
        }
    }
}

