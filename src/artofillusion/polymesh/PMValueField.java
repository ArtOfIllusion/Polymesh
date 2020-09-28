/*
 *  Copyright (C) 2005 by Francois Guillet.
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import javax.swing.JTextField;

import artofillusion.ui.ValueField;
import buoy.widget.BTextField;

/**
 *  A PMValueField is a ValueField for which the BTextField can be set
 *
 *@author     Francois Guillet
 *@created    January, 18 2005
 */

public class PMValueField extends ValueField
{
    /**
     *  Constructor for the ValueField object
     *
     *@param  value        Description of the Parameter
     *@param  constraints  Description of the Parameter
     */
    public PMValueField( double value, int constraints )
    {
        super( value, constraints, 5 );
    }


    /**
     *  Constructor for the ValueField object
     *
     *@param  value        Description of the Parameter
     *@param  constraints  Description of the Parameter
     */
    public PMValueField( float value, int constraints )
    {
        super( (double) value, constraints, 5 );
    }


    /**
     *  Constructor for the ValueField object
     *
     *@param  value        Description of the Parameter
     *@param  constraints  Description of the Parameter
     *@param  columns      Description of the Parameter
     */
    public PMValueField( float value, int constraints, int columns )
    {
        super( (double) value, constraints, columns );
    }


    /**
     *  Constructor for the ValueField object
     *
     *@param  value        Description of the Parameter
     *@param  constraints  Description of the Parameter
     *@param  columns      Description of the Parameter
     */
    public PMValueField( double value, int constraints, int columns )
    {
        super( value, constraints, columns );
    }



    /**
     *  Sets the textField attribute of the ValueField object
     *
     *@param  tf  The new textField value
     */
    public void setTextField( BTextField tf )
    {
        String text = getText();
        int c = getColumns();
        component = tf.getComponent();
        JTextField jtf = (JTextField) component;
        jtf.setText( text );
        jtf.setColumns( c );
        jtf.addCaretListener( caretListener );
        jtf.getDocument().addDocumentListener( documentListener );
/*        addEventLink( MousePressedEvent.class,
            new Object()
            {
                void processEvent()
                {
                    try
                    {
                        Field field = ValueField.class.getDeclaredField( "clicked" );
                        field.setAccessible( true );
                        field.set( this, new Boolean( true ) );
                    }
                    catch ( Exception ex )
                    {
                        ex.printStackTrace();
                    }
                }
            } );  */
    }
}

