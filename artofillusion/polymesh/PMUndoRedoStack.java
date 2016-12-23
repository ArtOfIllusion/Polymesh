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
 * This class implements a stack of fixed size accepting Commands which will be
 * executed for undo/redo operations
 * 
 * @author Francois Guillet
 * 
 */
public class PMUndoRedoStack {
    
    private Command[] commands;
    private int pointer;
    
    public PMUndoRedoStack(int size) {
	commands = new Command[size+1];
	pointer = 0;
    }
    
    /**
     * Adds a new command to the undo stack
     * @param cmd The command to add
     */
    public void addCommand(Command cmd) {
	if (commands[pointer] != null) {
	    pointer = (pointer + 1) % commands.length;
	}
	if (commands[(pointer + 1) % commands.length] != null) {
	    //stack is full, clearing bottom command
	    commands[(pointer + 1) % commands.length] = null;
	}
	commands[pointer] = cmd;
	clearRedoStack();
    }
    
    private void clearRedoStack() {
	int index = (pointer + 1) % commands.length;
	while (commands[index] != null) {
	    commands[index] = null;
	    index = (pointer + 1) % commands.length;
	}
    }

    /**
     * Returns true if undo operations are available
     * @return
     */
    public boolean canUndo() {
	return (commands[pointer] != null);
    }
    
    /**
     * Returns true if redo operations are available
     * @return
     */
    public boolean canRedo() {
	return (commands[(pointer + 1) % commands.length] != null);
    }
    
    public void undo() {
	if (commands[pointer] == null) {
	    return;
	}
	commands[pointer].undo();
	--pointer;
	if (pointer < 0) {
	    pointer += commands.length;
	}
    }
    
    public void redo() {
	if (commands[(pointer + 1) % commands.length] == null) {
	    return;
	}
	pointer = (pointer + 1) % commands.length;
	commands[pointer].redo();
    }
    
    /**
     * Sets the size of the undo and redo stacks
     * @param newSize The new size for undo/redo stacks
     */
    public void setSize(int newSize) {
	commands = new Command[newSize+1];
	pointer = 0;
    }
}
