/* Changes copyright (C) 2023 by Maksim Khramov

 This program is free software; you can redistribute it and/or modify it under the
 terms of the GNU General Public License as published by the Free Software
 Foundation; either version 2 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY 
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 PARTICULAR PURPOSE.  See the GNU General Public License for more details. */

package artofillusion.polymesh;

import java.awt.event.InputEvent;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import artofillusion.ui.Translate;
import artofillusion.ui.ValueField;
import buoy.event.CommandEvent;
import buoy.event.KeyPressedEvent;
import buoy.event.ValueChangedEvent;
import buoy.widget.BButton;
import buoy.widget.BCheckBox;
import buoy.widget.BLabel;
import buoy.widget.BSlider;
import buoy.widget.BSpinner;
import buoy.widget.BTextField;
import buoy.widget.BorderContainer;
import buoy.widget.Widget;
import buoy.xml.WidgetDecoder;

/**
 * This class displays a composite widget that allows to enter a value either using a slider or
 * a text field.
 */
public class PolyMeshValueWidget extends BorderContainer
{
    public interface ValueWidgetOwner {
    	public void showValueWidget();
    	public void doValueWidgetValidate();
    	public void doValueWidgetAbort();
		public void prepareToShowValueWidget();
    }
	
	private BButton validateButton, abortButton;
    private BSlider valueSlider;
    private BSpinner minSpinner;
    private BSpinner maxSpinner;
    private PMValueField valueField;
    private BCheckBox retainValueCB;
    private double valueMin, valueMax, tempValueMin, tempValueMax;
    private double value;
    private NumberFormat format;
    private Runnable runCallback;
    private boolean temporaryRange;
    private ValueWidgetOwner owner;

    public PolyMeshValueWidget(ValueWidgetOwner owner)
    {
        this.owner = owner;
    	format = NumberFormat.getInstance();
        format.setMaximumFractionDigits( 3 );
        InputStream is = null;
        try
        {
            WidgetDecoder decoder = new WidgetDecoder( is = getClass().getResource( "interfaces/value.xml" ).openStream() );
            this.add( (Widget) decoder.getRootObject(), BorderContainer.CENTER );
            valueSlider = ( (BSlider) decoder.getObject( "valueSlider" ) );
            BLabel maxLabel = ( (BLabel) decoder.getObject( "maxLabel" ) );
            maxLabel.setText( Translate.text( "polymesh:" + maxLabel.getText() ) );
            maxSpinner = ( (BSpinner) decoder.getObject( "maxSpinner" ) );
            validateButton = ( (BButton) decoder.getObject( "validateButton" ) );
            validateButton.setText( Translate.text( "polymesh:" + validateButton.getText() ) );
            abortButton = ( (BButton) decoder.getObject( "abortButton" ) );
            abortButton.setText( Translate.text( "polymesh:" + abortButton.getText() ) );
            BLabel minLabel = ( (BLabel) decoder.getObject( "minLabel" ) );
            minLabel.setText( Translate.text( "polymesh:" + minLabel.getText() ) );
            retainValueCB = ( (BCheckBox) decoder.getObject( "retainValueCB" ) );
            retainValueCB.setText( Translate.text( "polymesh:" + retainValueCB.getText() ) );
            minSpinner = ( (BSpinner) decoder.getObject( "minSpinner" ) );
            valueField = new PMValueField( 0.0, ValueField.NONE );
            valueField.setTextField( (BTextField) decoder.getObject( "valueField" ) );
            setSpinnerColumns( minSpinner, 2 );
            setSpinnerColumns( maxSpinner, 2 );
            setSpinnerFractionDigits( minSpinner, 2 );
            setSpinnerFractionDigits( maxSpinner, 2 );
            minSpinner.setModel( new SpinnerNumberModel( -2, -1000, 1000, 0.01 ) );
            maxSpinner.setModel( new SpinnerNumberModel( 2, -1000, 1000, 0.01 ) );
            valueSlider.setValue( 500 );
            valueSlider.addEventLink( ValueChangedEvent.class, this, "doValueChanged" );
            validateButton.addEventLink( CommandEvent.class, this, "doValidate" );
            abortButton.addEventLink( CommandEvent.class, this, "doAbort" );
            valueField.addEventLink( ValueChangedEvent.class, this, "doValueFieldChanged" );
            valueField.addEventLink( KeyPressedEvent.class, this, "doValidateValue" );
            minSpinner.addEventLink( ValueChangedEvent.class, this, "doValueFieldChanged" );
            maxSpinner.addEventLink( ValueChangedEvent.class, this, "doValueFieldChanged" );
        }
        catch ( IOException ex )
        {
            ex.printStackTrace();
        }
        finally
        {
            if (is != null)
                try
                {
                    is.close();
                }
                catch ( IOException ex )
                {
                    ex.printStackTrace();
                }
        }
        temporaryRange = false;
        deactivate();
    }

    /**
     *  Called when a key has been pressed
     *
     *@param  e  The KeyPressedEvent
     */
    protected void keyPressed( KeyPressedEvent e )
    {
        if (! valueSlider.isEnabled())
            return;
        int code = e.getKeyCode();
        int modifiers = e.getModifiers();
        if ( ( modifiers & InputEvent.CTRL_MASK ) != 0 )
            return;
        switch ( code )
        {
            case KeyPressedEvent.VK_PLUS:
            case KeyPressedEvent.VK_ADD:
                if ( valueSlider.isEnabled() )
                {
                    int slider = valueSlider.getValue();
                    if ( slider < 100 )
                    {
                        valueSlider.setValue( slider + 1 );
                        doValueChanged();
                    }
                    else
                    {
                        SpinnerNumberModel model = (SpinnerNumberModel) maxSpinner.getModel();
                        maxSpinner.setValue( model.getNextValue() );
                        doValueChanged();
                    }
                }
                break;
            case KeyPressedEvent.VK_MINUS:
            case KeyPressedEvent.VK_SUBTRACT:
                if ( valueSlider.isEnabled() )
                {
                    int slider = valueSlider.getValue();
                    if ( slider > 0 )
                    {
                        valueSlider.setValue( slider - 1 );
                        doValueChanged();
                    }
                    else
                    {
                        SpinnerNumberModel model = (SpinnerNumberModel) minSpinner.getModel();
                        minSpinner.setValue( model.getPreviousValue() );
                        doValueChanged();
                    }
                }
                break;
            case KeyPressedEvent.VK_ENTER:
                if ( runCallback != null )
                    doValidate();
                break;
            case KeyPressedEvent.VK_END:
                if ( runCallback != null )
                    doAbort();
                break;
            default:
                break;
        }
    }

    /**
     * Call this methode to activate the value widget with a default value of 0.
     *
     * @param runCallback  Callback to call when value has changed
     * @param validateCallback Callback to call when value is validated by user
     * @param abortCallback  Callback to call when process is cancelled by user
     */
   public void activate(Runnable runCallback)
    {
        activate( 0.0, runCallback);
    }

    /**
     *
     * @param val Value widget initial value
     * @param runCallback  Callback to call when value has changed
     */
   public void activate( double val, Runnable runCallback)
   {
       this.runCallback = runCallback;
       if ( !retainValueCB.getState() )
       {
           value = val;
           valueField.setValue( val );
           double min = ( (Double) minSpinner.getValue() ).doubleValue();
           if ( min > 0.0 )
               minSpinner.setValue( new Double( 0.0 ) );
           double max = ( (Double) maxSpinner.getValue() ).doubleValue();
           if ( max < 0.0 )
               maxSpinner.setValue( new Double( 0.0 ) );
           valueSlider.setValue( (int) Math.round( ( val - min ) * 1000 / ( max - min ) ) );
       }
       retainValueCB.setEnabled( true );
       valueField.setEnabled( true );
       minSpinner.setEnabled( true );
       maxSpinner.setEnabled( true );
       validateButton.setEnabled( true );
       abortButton.setEnabled( true );
       valueSlider.setEnabled( true );
       owner.prepareToShowValueWidget();
       if (runCallback != null)
           runCallback.run();
       owner.showValueWidget();
   }

    /**
     *  Disable value entering mode
     */
    public void deactivate()
    {
        retainValueCB.setEnabled( false );
        valueField.setEnabled( false );
        minSpinner.setEnabled( false );
        maxSpinner.setEnabled( false );
        validateButton.setEnabled( false );
        abortButton.setEnabled( false );
        valueSlider.setEnabled( false );
        runCallback = null;
        if (temporaryRange)
        {
            double tmp = value;
            setRangeValues(valueMin, valueMax);
            temporaryRange = false;
            value = tmp;
        }
    }


    /**
     *  Called when the slider value has changed
     */
    private void doValueChanged()
    {
        double min = ( (Double) minSpinner.getValue() ).doubleValue();
        double max = ( (Double) maxSpinner.getValue() ).doubleValue();
        value = ( (double) valueSlider.getValue() ) * ( max - min ) / 1000.0 + min;
        try
        {
            valueField.setValue( format.parse( format.format( value ) ).doubleValue() );

        }
        catch ( Exception e )
        {

        }

        if ( runCallback != null )
            runCallback.run();
    }

    /**
      *  Called when a key has been pressed in the value field
      *
      *@param  e  The KeyPressedEvent
      */
     private void doValidateValue( KeyPressedEvent e )
     {
         int code = e.getKeyCode();
         int modifiers = e.getModifiers();
         if ( ( modifiers & InputEvent.CTRL_MASK ) != 0 )
             return;
         switch ( code )
         {
             case KeyPressedEvent.VK_ENTER:
                 if ( runCallback != null )
                     doValidate();
                 break;
         }
     }

    /**
     *  Validate button selected
     */
    private void doValidate()
    {
        owner.doValueWidgetValidate();
        deactivate();
    }


    /**
     *  Cancel button selected
     */
    private void doAbort()
    {
        owner.doValueWidgetAbort();
        deactivate();
    }

    /**
     *  value filed has been edited
     */
    private void doValueFieldChanged()
    {
        value = valueField.getValue();
        double min = ( (Double) minSpinner.getValue() ).doubleValue();
        double max = ( (Double) maxSpinner.getValue() ).doubleValue();
        if ( value < min )
        {
            minSpinner.setValue( new Double( value ) );
            valueSlider.setValue( 0 );
        }
        if ( value > max )
        {
            maxSpinner.setValue( new Double( value ) );
            valueSlider.setValue( 1000 );
        }
        else
            valueSlider.setValue( (int) Math.round( ( value - min ) * 1000 / ( max - min ) ) );
        if ( runCallback != null )
            runCallback.run();
    }


    /**
     *  Sets the number of columns displayed by a spinner
     *
     *@param  spinner  The concerned BSpinner
     *@param  numCol   The new number of columns to show
     */
    public static void setSpinnerColumns( BSpinner spinner, int numCol )
    {
        JSpinner.NumberEditor ed = (JSpinner.NumberEditor)spinner.getComponent().getEditor();
        JFormattedTextField field = ed.getTextField();
        field.setColumns( numCol );
        spinner.getComponent().setEditor( ed );
    }


    /**
     *  Sets the number of minimum fraction digits for a 'double' spinner
     *
     *@param  spinner    The concerned BSpinner
     *@param  numDigits  The new minimum number of fraction digits
     */
    public static void setSpinnerFractionDigits( BSpinner spinner, int numDigits )
    {
        JSpinner.NumberEditor ed = (JSpinner.NumberEditor) spinner.getComponent().getEditor();
        DecimalFormat format = ed.getFormat();
        format.setMinimumFractionDigits( 1 );
        spinner.getComponent().setEditor( ed );
    }

    public double getValueMin()
    {
        return  (Double) minSpinner.getValue();
    }

    public void setValueMin(double valueMin)
    {
        this.valueMin = valueMin;
        minSpinner.setValue(valueMin);
    }

    public double getValueMax()
    {
        return ( (Double) maxSpinner.getValue() );
    }

    public void setValueMax(double valueMax)
    {
        this.valueMax = valueMax;
        maxSpinner.setValue(valueMax);
    }

    /**
     * Sets the temporary min and max values of the widget. These values are reset to min and max
     * values when the value selection is validated or aborted.
     * @param tmpValueMin
     * @param tmpValueMax
     */
    public void setTempValueRange(double tmpValueMin, double tmpValueMax)
    {
       if ( !temporaryRange )
       {
           temporaryRange = true;
           valueMin = ( (Double) minSpinner.getValue() ).doubleValue();
           valueMax = ( (Double) maxSpinner.getValue() ).doubleValue();
           minSpinner.setValue( new Double( tmpValueMin ) );
           maxSpinner.setValue( new Double( tmpValueMax ) );
           if (value < tmpValueMin)
               value = tmpValueMin;
           if (value > tmpValueMax)
               value = tmpValueMax;
           valueSlider.setValue( (int) Math.round( ( value - tmpValueMin ) * 1000 / ( tmpValueMax - tmpValueMin ) ) );
           try
           {
               valueField.setValue( format.parse( format.format( value ) ).doubleValue() );

           }
           catch ( Exception e )
           {

           }
       }
    }

    /**
     * Sets the min and max values of the widget
     * @param valueMin  min value
     * @param valueMax  max value
     */
    public void setRangeValues(double valueMin, double valueMax)
    {
        this.valueMin = valueMin;
        this.valueMax = valueMax;
        minSpinner.setValue( new Double( valueMin ) );
        maxSpinner.setValue( new Double( valueMax ) );
        if (value < valueMin)
            value = valueMin;
        if (value > valueMax)
            value = valueMax;
        valueSlider.setValue( (int) Math.round( ( value - valueMin ) * 1000 / ( valueMax - valueMin ) ) );
        try
        {
            valueField.setValue( format.parse( format.format( value ) ).doubleValue() );

        }
        catch ( Exception e )
        {

        }
    }

    public double getValue()
    {
        return value;
    }

    public void setValue(double value)
    {
        this.value = value;
    }

    public boolean isActivated()
    {
        return valueSlider.isEnabled();
    }
}
