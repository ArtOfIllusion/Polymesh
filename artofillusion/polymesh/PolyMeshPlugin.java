/*
 *  Copyright 2004-2007 Francois Guillet
    Changes copyright (C) 2017 by Maksim Khramov

 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import java.io.InputStream;
import java.util.ResourceBundle;
import artofillusion.ArtOfIllusion;
import artofillusion.LayoutWindow;
import artofillusion.Plugin;
import artofillusion.UndoRecord;
import artofillusion.keystroke.KeystrokeManager;
import artofillusion.keystroke.KeystrokeRecord;
import artofillusion.math.CoordinateSystem;
import artofillusion.object.ObjectInfo;
import artofillusion.object.SplineMesh;
import artofillusion.object.TriangleMesh;
import artofillusion.ui.ToolPalette;
import artofillusion.ui.Translate;
import buoy.widget.BMenu;
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
    @Override
    public void processMessage( int message, Object args[] )
    {
        if ( message == Plugin.APPLICATION_STARTING )
        {
            resources = ResourceBundle.getBundle( "polymesh", ArtOfIllusion.getPreferences().getLocale() );
            boolean keysImplemented = false;
            for (KeystrokeRecord key : KeystrokeManager.getAllRecords()) {
                if (key.getName().endsWith("(PolyMesh)")) {
                    keysImplemented = true;
                    break;
                }
            }
            if (keysImplemented) return;

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
        private final LayoutWindow window;
        
        public ConvertObject( LayoutWindow window )
        {
            this.window = window;
        }
        
        private void doConvert()
        {
            PolyMesh mesh;
            BStandardDialog dlg = new BStandardDialog(Translate.text("polymesh:triangleToPolyTitle"), Translate.text("polymesh:convertToQuads"), BStandardDialog.QUESTION);
            String[] options = new String[] { Translate.text("polymesh:findQuadsDistance"), Translate.text("polymesh:findQuadsAngular"), Translate.text("polymesh:keepTriangles") };
            
            //NB!!! optionDefault is not match to any option button defined above... 
            String optionDefault = Translate.text("polymesh:convertToQuads");
            
            for(ObjectInfo item: window.getSelectedObjects()) 
            {
                if(item.getObject() instanceof SplineMesh)
                {
                    mesh = new PolyMesh( (SplineMesh) item.getObject() );
                    CoordinateSystem coords = new CoordinateSystem();
                    coords.copyCoords(item.getCoords());
                    String name = "Polymesh" + item.getName();
                    window.addObject( mesh, coords, name, (UndoRecord)null );
                }
                else if(item.getObject() instanceof TriangleMesh)
                {                    
                    int r = dlg.showOptionDialog( window, options, optionDefault );
                    mesh = new PolyMesh( (TriangleMesh) item.getObject(), r == 0 || r == 1, r == 1  );
                    CoordinateSystem coords = new CoordinateSystem();
                    coords.copyCoords(item.getCoords());
                    String name = "Polymesh" + item.getName();
                    window.addObject( mesh, coords, name, (UndoRecord)null );
                }
            }

            window.updateImage();
        }
    }
}

