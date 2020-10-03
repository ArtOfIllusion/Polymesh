package artofillusion.polymesh;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTextArea;
import javax.swing.JToolTip;
import javax.swing.UIManager;
import javax.swing.border.Border;

import artofillusion.ui.Translate;
import buoy.widget.BToolTip;
import buoy.widget.Widget;

/**
 * A custom BToolTip able to display any component
 */
public class PMToolTip extends BToolTip
{
    private Widget w;


    /**
     *  Constructor for the TapBToolTip object
     *
     *@param  c  Description of the Parameter
     */
    public PMToolTip( Component c )
    {
        component = c;
    }


    /**
     *  Create a new PMToolTip.
     *
     *@param  text  the text to display in the tool tip
     */

    public PMToolTip( String text )
    {
        component = new JToolTip();
        ( (JToolTip) component ).setTipText( text );
    }


    /**
     *  Get the text to display on the tool tip.
     *
     *@return    The text value
     */

    public String getText()
    {

        if ( component instanceof JToolTip )
            return ( (JToolTip) component ).getTipText();
        else
            return "";
    }


    /**
     *  Set the text to display on the tool tip.
     *
     *@param  text  The new text value
     */

    public void setText( String text )
    {
        if ( component instanceof JToolTip )
            ( (JToolTip) component ).setTipText( text );
        invalidateSize();
    }

    public static PMToolTip areaToolTip( String property, int columns )
    {
        JTextArea area = new JTextArea( Translate.text( "polymesh:" + property ) );
        area.setColumns( columns );
        area.setLineWrap( true );
        area.setWrapStyleWord( true );
        area.setBackground( (Color) UIManager.get( "ToolTip.background" ) );
        area.setBorder( (Border) UIManager.get( "ToolTip.border" ) );
        area.setSize( area.getPreferredSize() );
        return new PMToolTip( area );
    }
}
