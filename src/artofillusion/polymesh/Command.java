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

/**
 * The command interface is implemented by all editing operations in 
 * the UV mapper for undo/redo.
 * @author pims
 *
 */
public interface Command {

    /**
     * execute the command
     *
     */
    public void execute();
    
    /**
     * Undo the command
     *
     */
    public void undo();
    
    /**
     * Redo the command
     *
     */
    public void redo();
}
