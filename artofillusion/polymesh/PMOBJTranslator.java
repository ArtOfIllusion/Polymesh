/*
 *  Copyright (C) 2002-2004 by Peter Eastman, modifications for Polymesh plugin (C) 2005 Francois Guillet
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import artofillusion.Scene;
import artofillusion.Translator;
import artofillusion.ui.Translate;
import buoy.widget.BFrame;

/**
 *  PMOBJTranslator is a Translator which imports and exports OBJ files to/from
 *  Polymeshes.
 *
 *@author     Francois Guillet
 *@created    13 juin 2005
 */

public class PMOBJTranslator implements Translator
{
    /**
     *  Gets the name attribute of the PMOBJTranslator object
     *
     *@return    The name value
     */
    public String getName()
    {
        return Translate.text("polymesh:importWavefront");
    }


    public boolean canImport()
    {
        return true;
    }


    public boolean canExport()
    {
        return true;
    }


     public void importFile( BFrame parent )
    {
        PMOBJImporter.importFile( parent );
    }


     public void exportFile( BFrame parent, Scene theScene )
    {
    	 PMOBJExporter.exportFile( parent, theScene );
    }
}
