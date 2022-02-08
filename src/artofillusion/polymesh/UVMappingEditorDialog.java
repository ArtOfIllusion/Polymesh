/*
 *  Copyright (C) 2007 by François Guillet
 *  Modifications Copyright (C) 2019 by Petri Ihalainen
 *  Changes copyright (C) 2022 by Maksim Khramov

 *  This program is free software; you can redistribute it and/or modify it under the 
 *  terms of the GNU General Public License as published by the Free Software 
 *  Foundation; either version 2 of the License, or (at your option) any later version. 
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY 
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package artofillusion.polymesh;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.BasicStroke;
import java.awt.Rectangle;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import javax.imageio.ImageIO;

import artofillusion.math.Vec2;
import artofillusion.object.FacetedMesh;
import artofillusion.object.ObjectInfo;
import artofillusion.polymesh.UVMappingCanvas.MappingPositionsCommand;
import artofillusion.polymesh.UVMappingCanvas.Range;
import artofillusion.polymesh.UVMappingData.UVMeshMapping;
import artofillusion.polymesh.UnfoldedMesh.UnfoldedEdge;
import artofillusion.texture.*;
import artofillusion.TextureParameter;
import artofillusion.ui.*;

import buoy.event.*;
import buoy.widget.*;
import buoy.xml.WidgetDecoder;


/**
 * This window allows the user to edit UV mapping using unfolded pieces of mesh
 * displayed over the texture image.
 * 
 * @author François Guillet
 */
public class UVMappingEditorDialog extends BDialog {

    private final UVMappingCanvas mappingCanvas; // the mapping canvas displayed at window center
    private final BList pieceList; // the list of mesh pieces
    private UVMappingData mappingData; // mapping data associated to the unfolded mesh
    private final MeshPreviewer preview; // 3D texturing preview
    protected ActionProcessor mouseProcessor;
    protected UVMappingManipulator manipulator;
    private ObjectInfo objInfo;
    private UVMeshMapping currentMapping; // the mapping currently edited
    private int currentTexture; // the texture currently edited
    private ArrayList<Texture> texList; // the texture list of edited mesh
    private ArrayList<UVMapping> mappingList; // the corresponding mapping  list
    private ArrayList<Vec2[][]> oldCoordList; // the old texture coordinates for undoing changes
    private UVMappingData oldMappingData; // the original mapping data for undoing changes
    
    private boolean clickedOk; // true if the user clicked the ok button

    private boolean tension;
    private int tensionValue = 2;
    private int tensionDistance = 3;

     // This is not pretty. 3.0 --> 3.5 or maybe a geometric sequence

    protected static final double[] tensionArray = { 5.0, 3.0, 2.0, 1.0, 0.5 };
    
    
    private int undoLevels = 20;
    private PMUndoRedoStack undoRedoStack = new PMUndoRedoStack(undoLevels); // the Undo/Redo stack

    /* Interface variables */

   
    private BLabel componentLabel;
    private BComboBox componentCB;
    private BLabel uMinValue;
    private BLabel uMaxValue;
    private BLabel vMinValue;
    private BLabel vMaxValue;
    private BLabel resLabel;
    private BSpinner resSpinner;
    private BComboBox mappingCB;
    private BLabel textureLabel;
    private BComboBox textureCB;
    private BCheckBox meshTensionCB;
    private BSpinner distanceSpinner;
    private BComboBox tensionCB;
    
    private BMenuItem undoMenuItem;
    private BMenuItem redoMenuItem;
    private BMenu sendTexToMappingMenu;
    private BMenuItem removeMappingMenuItem;
    private BCheckBoxMenuItem[] mappingMenuItems;
    private BCheckBoxMenuItem gridMenuItem;

    public static final int TRANSPARENT = 0, WHITE = 1, TEXTURED = 2;

    /** 
     *  Construct a new UVMappingEditorDialog
     */

    public UVMappingEditorDialog(ObjectInfo objInfo, boolean initialize, BFrame parent) {

        super(parent, Translate.text("uvCoordsTitle"), true);
        this.objInfo = objInfo;
        PolyMesh mesh = (PolyMesh) objInfo.object;
        mappingData = mesh.getMappingData();
        oldMappingData = mappingData.duplicate();

        // find out the UVMapped texture on parFacePerVertex basis
        // record current coordinates in order to undo if the user cancels

        texList = new ArrayList<Texture>();
        mappingList = new ArrayList<UVMapping>();
        oldCoordList = new ArrayList<Vec2[][]>();
        Texture tex = objInfo.object.getTexture();
        TextureMapping mapping = objInfo.object.getTextureMapping();
        if (tex instanceof LayeredTexture) {
            LayeredMapping layeredMapping = (LayeredMapping) mapping;
            Texture[] textures = layeredMapping.getLayers();
            for (int i = 0; i < textures.length; i++) {
                mapping = layeredMapping.getLayerMapping(i);
                if (mapping instanceof UVMapping) {
                    if (((UVMapping) mapping).isPerFaceVertex(mesh)) {
                        texList.add(textures[i]);
                        mappingList.add((UVMapping) mapping);
                        oldCoordList.add(((UVMapping) mapping).findFaceTextureCoordinates((FacetedMesh) objInfo.object));
                    }
                }
            }
        } else {
            if (mapping instanceof UVMapping) {
                if (((UVMapping) mapping).isPerFaceVertex(mesh)) {
                    texList.add(tex);
                    mappingList.add((UVMapping) mapping);
                    oldCoordList.add(((UVMapping) mapping).findFaceTextureCoordinates((FacetedMesh) objInfo.object));
                }
            }
        }
        if (texList.size() == 0) {
            texList = null;
            mappingList = null;
            oldCoordList = null;
        }
        currentTexture = -1;
        initializeMappingsTextures();
        currentMapping = mappingData.getMappings().get(0);
        if (texList != null) {
            for (int i = 0; i < texList.size(); i++) {
                boolean hasTexture = false;
                for (int j = 0; j < mappingData.mappings.size(); j++) {
                    ArrayList<Integer> textures = mappingData.mappings.get(j).textures;
                    for (int k = 0; k < textures.size(); k++)
                        if (getTextureFromID(textures.get(k)) == i)
                            hasTexture = true;
                }
                if (!hasTexture)
                    currentMapping.textures.add(texList.get(i).getID());
            }
        }
        if (currentMapping.textures.size() > 0)
            currentTexture = getTextureFromID(currentMapping.textures.get(0));

        // create interface

        BorderContainer content = new BorderContainer();
        setContent(content);
        RowContainer buttons = new RowContainer();
        buttons.setDefaultLayout(new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.NONE, new Insets(2, 2, 2, 2), new Dimension(0, 0)));
        buttons.add(Translate.button("ok", this, "doOk"));
        buttons.add(Translate.button("cancel", this, "doCancel"));
        content.add(buttons, 
                    BorderContainer.SOUTH, 
                    new LayoutInfo(LayoutInfo.CENTER, 
                                   LayoutInfo.NONE, 
                                   new Insets(2, 2, 2, 2), 
                                   new Dimension(0, 0)));
        
        try(InputStream is = getClass().getResource("interfaces/unfoldEditor.xml").openStream()) {
            WidgetDecoder decoder = new WidgetDecoder(is, PolyMeshPlugin.resources);
            BorderContainer borderContainer = (BorderContainer) decoder.getRootObject();
            uMinValue = ((BLabel) decoder.getObject("uMinValue"));
            uMaxValue = ((BLabel) decoder.getObject("uMaxValue"));
            vMinValue = ((BLabel) decoder.getObject("vMinValue"));
            vMaxValue = ((BLabel) decoder.getObject("vMaxValue"));
            //autoButton = ((BButton) decoder.getObject("autoButton"));
            //autoButton.addEventLink(CommandEvent.class, this, "doAutoScale");
            resLabel = ((BLabel) decoder.getObject("resLabel"));
            mappingCB = ((BComboBox) decoder.getObject("mappingCB"));
            mappingCB.addEventLink(ValueChangedEvent.class, this, "doMappingChanged");
            textureLabel = ((BLabel) decoder.getObject("textureLabel"));
            textureCB = ((BComboBox) decoder.getObject("textureCB"));
            textureCB.addEventLink(ValueChangedEvent.class, this, "doTextureChanged");
            componentLabel = ((BLabel) decoder.getObject("componentLabel"));
            componentCB = ((BComboBox) decoder.getObject("componentCB"));
            content.add(borderContainer, 
                        BorderContainer.WEST, 
                        new LayoutInfo(LayoutInfo.CENTER, 
                                       LayoutInfo.BOTH, 
                                       new Insets(2, 2, 2, 2),
                                       new Dimension(0, 0)));
            ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
            
            for (int i = 0; i < mappings.size(); i++) 
                mappingCB.add(mappings.get(i).name);

            setTexturesForMapping(currentMapping);
            componentCB.setContents(new String[] {Translate.text("Diffuse"),
                                                  Translate.text("Specular"),
                                                  Translate.text("Transparent"),
                                                  Translate.text("Hilight"),
                                                  Translate.text("Emissive") });
            componentCB.addEventLink(ValueChangedEvent.class, this, "doChangeComponent");
            resSpinner = ((BSpinner) decoder.getObject("resSpinner"));
            resSpinner.setValue(mappingData.sampling);
            resSpinner.addEventLink(ValueChangedEvent.class, this, "doSamplingChanged");
            meshTensionCB = ((BCheckBox) decoder.getObject("meshTensionCB"));
            meshTensionCB.addEventLink(ValueChangedEvent.class, this, "doTensionChanged");
            distanceSpinner = ((BSpinner) decoder.getObject("distanceSpinner"));
            distanceSpinner.setValue(tensionDistance);
            distanceSpinner.addEventLink(ValueChangedEvent.class, this, "doMaxDistanceValueChanged");
            tensionCB = ((BComboBox) decoder.getObject("tensionCB"));
            tensionCB.addEventLink(ValueChangedEvent.class, this, "doTensionValueChanged");
            tensionCB.setContents(new String[] {Translate.text("VeryLow"),
                                                Translate.text("Low"), 
                                                Translate.text("Medium"),
                                                Translate.text("High"), 
                                                Translate.text("VeryHigh")});
            tensionCB.setSelectedIndex(tensionValue);
        } catch (IOException ex) {
            ex.printStackTrace();
        } 

        BSplitPane meshViewPanel = new BSplitPane(BSplitPane.VERTICAL, 
                                                  new BScrollPane(pieceList = new BList()), 
                                                  preview = new MeshPreviewer(objInfo, 200, 200));
        tex = null;
        mapping = null;
        if (currentTexture >= 0) {
            tex = texList.get(currentTexture);
            mapping = mappingList.get(currentTexture);
        }
        mappingCanvas = new UVMappingCanvas(this, mappingData, preview, tex, (UVMapping)mapping);
        BScrollPane sp = new BScrollPane(mappingCanvas, BScrollPane.SCROLLBAR_NEVER,  BScrollPane.SCROLLBAR_NEVER);
        meshViewPanel.setResizeWeight(1.0);
        meshViewPanel.setContinuousLayout(true);
        BSplitPane div = new BSplitPane(BSplitPane.HORIZONTAL, sp, meshViewPanel);
        div.setResizeWeight(1.0);
        div.setContinuousLayout(true);
        content.add(div, 
                    BorderContainer.CENTER, 
                    new LayoutInfo(LayoutInfo.CENTER, 
                                   LayoutInfo.BOTH, 
                                   new Insets(2, 2, 2, 2), 
                                   new Dimension(0, 0)));
        UnfoldedMesh[] meshes = mappingData.getMeshes();
        for (int i = 0; i < meshes.length; i++)
            pieceList.add(meshes[i].getName());
        pieceList.setMultipleSelectionEnabled(false);
        pieceList.setSelected(0, true);
        pieceList.addEventLink(SelectionChangedEvent.class, this, "doPieceListSelection");

        mappingCanvas.addEventLink(MousePressedEvent.class, this, "processMousePressed");
        mappingCanvas.addEventLink(MouseReleasedEvent.class, this, "processMouseReleased");
        mappingCanvas.addEventLink(MouseDraggedEvent.class, this, "processMouseDragged");
        mappingCanvas.addEventLink(MouseMovedEvent.class, this, "processMouseMoved");
        mappingCanvas.addEventLink(MouseScrolledEvent.class, this, "processMouseScrolled");
        manipulator = new UVMappingManipulator(mappingCanvas, this);

        BMenuBar menuBar = new BMenuBar();

        BMenu menu = Translate.menu("polymesh:edit");
        menu.add(undoMenuItem = Translate.menuItem("undo", this, "doUndo"));
        menu.add(redoMenuItem = Translate.menuItem("redo", this, "doRedo"));
        menu.add(Translate.menuItem("polymesh:undoLevels", this, "doSetUndoLevels"));
        menu.addSeparator();
        menu.add(Translate.menuItem("polymesh:selectAll", this, "doSelectAll"));
        menu.add(Translate.menuItem("polymesh:pinSelection", this, "doPinSelection"));
        menu.add(Translate.menuItem("polymesh:unpinSelection", this, "doUnpinSelection"));
        menu.add(Translate.menuItem("polymesh:renameSelectedPiece", this, "doRenameSelectedPiece"));
        menu.add(new BSeparator());
        menu.add(Translate.menuItem("polymesh:exportImage", this, "openImageExportDialog"));
        menuBar.add(menu);

        menu = Translate.menu("polymesh:mapping");
        menu.add(Translate.menuItem("polymesh:fitMappingToImage", this, "doFitMappingToImage"));
        menu.add(Translate.menuItem("polymesh:addMapping", this, "doAddMapping"));
        menu.add(Translate.menuItem("polymesh:duplicateMapping", this, "doDuplicateMapping"));
        removeMappingMenuItem = Translate.menuItem("polymesh:removeMapping", this, "doRemoveMapping");
        menu.add(removeMappingMenuItem);
        menu.add(Translate.menuItem("polymesh:editMappingColor", this, "doEditMappingColor"));
        menu.add(new BSeparator());
        sendTexToMappingMenu = Translate.menu("polymesh:sendTexToMapping");
        menu.add(sendTexToMappingMenu);
        updateMappingMenu();
        menuBar.add(menu);

        menu = Translate.menu("polymesh:preferences");
        menu.add(Translate.checkboxMenuItem("polymesh:showSelectionOnPreview", this, "doShowSelection", true));
        menu.add(Translate.checkboxMenuItem("polymesh:liveUpdate", this, "doLiveUpdate", true));
        menu.add(Translate.checkboxMenuItem("polymesh:boldEdges", this, "doBoldEdges", true));
        menuBar.add(menu);

        // Would prefer to use transpations of AoI but unfortunately those come with keyboard shortcuts
        // that are't implemented.
        menu = Translate.menu("view");
        menu.add(Translate.menuItem("polymesh:fitToSelection", mappingCanvas, "fitToSelection"));
        menu.add(Translate.menuItem("polymesh:fitToAll", mappingCanvas, "fitToAll"));
        menu.add(gridMenuItem = Translate.checkboxMenuItem("polymesh:showGrid", mappingCanvas, "repaint", true));
        menuBar.add(menu);

        setMenuBar(menuBar);
        setTexturesForMapping(currentMapping);

        if (currentTexture != -1)
            textureCB.setSelectedIndex(0);

        updateState();

        // This prevents the "0...." in U/V min/max labels.
        Dimension d = new BLabel("+XXX.XXX").getPreferredSize();
        uMinValue.getComponent().setPreferredSize(d);
        uMaxValue.getComponent().setPreferredSize(d);
        vMinValue.getComponent().setPreferredSize(d);
        vMaxValue.getComponent().setPreferredSize(d);

        pack();

        // This was entirely unexpected: The next four lines seem to bring the crazy pane split  
        // sizes back to their senses. The size increase needs to be at least 2x2 pixels. 
        // Without this the window split is badly off, zoom center may be outside the canvas etc...
        // - Q: Is this platform dependent? Via the look and feel and hence the frame sizes?
        // - To consider: WindowResizedEvent to launch recalculation of the sizes.

        Rectangle b = getBounds();
        b.width  += 2;
        b.height += 2;
        setBounds(b);

        UIUtilities.centerWindow(this); // Has to be after 'pack()'
        addEventLink(WindowClosingEvent.class, this, "doCancel");

        setVisible(true);
    }

    private void doSetUndoLevels() {
        ValueField undoLevelsVF = new ValueField((double) undoLevels, ValueField.POSITIVE + ValueField.INTEGER);
        ComponentsDialog dlg = new ComponentsDialog(this, 
                                                    Translate.text("polymesh:setUndoLevelsTitle"), 
                                                    new Widget[] {undoLevelsVF}, 
                                                    new String[] {Translate.text("polymesh:numberOfUndoLevels")});
        if (!dlg.clickedOk())
            return;
        if ((int) undoLevelsVF.getValue() == undoLevels)
            return;
        undoLevels = (int) undoLevelsVF.getValue();
        undoRedoStack.setSize(undoLevels);
        updateUndoRedoMenus();
    }

    private void setTexturesForMapping(UVMeshMapping currentMapping) {
        textureCB.removeAll();
        ArrayList<Integer> textures = currentMapping.textures;
        for (int i = 0; i < textures.size(); i++)
            textureCB.add(texList.get(getTextureFromID(textures.get(i))).getName());
    }

    private void doUndo() {
        if (undoRedoStack.canUndo())
            undoRedoStack.undo();
        updateUndoRedoMenus();
    }

    private void doRedo() {
        if (undoRedoStack.canRedo())
            undoRedoStack.redo();
        updateUndoRedoMenus();
    }

    private void updateUndoRedoMenus() {
        if (undoRedoStack.canUndo())
            undoMenuItem.setEnabled(true);
        else
            undoMenuItem.setEnabled(false);
        if (undoRedoStack.canRedo())
            redoMenuItem.setEnabled(true);
        else
            redoMenuItem.setEnabled(false);
    }

    /**
     * Adds a command to the undo stack
     * 
     * @param cmd The command to add to the stack
     */
    public void addUndoCommand(Command cmd) {
        undoRedoStack.addCommand(cmd);
        updateUndoRedoMenus();
    }

    private void doEditMappingColor() {
        BColorChooser chooser = new BColorChooser(currentMapping.edgeColor, "chooseEdgeColor");
        if (chooser.showDialog(this)) {
            currentMapping.edgeColor = chooser.getColor();
            mappingCanvas.repaint();
        }
    }

    private void doPinSelection() {
        mappingCanvas.pinSelection(true);
    }

    private void doUnpinSelection() {
        mappingCanvas.pinSelection(false);
    }

    private void doBoldEdges(CommandEvent evt) {
        BCheckBoxMenuItem item = (BCheckBoxMenuItem) evt.getWidget();
        mappingCanvas.setBoldEdges(item.getState());
    }

    private void doLiveUpdate(CommandEvent evt) {
        BCheckBoxMenuItem item = (BCheckBoxMenuItem) evt.getWidget();
        preview.setShowSelection(item.getState());
        manipulator.setLiveUpdate(item.getState());
    }

    private void doRenameSelectedPiece() {
        int index = pieceList.getSelectedIndex();
        if (index < 0) {
            pieceList.setSelected(0, true);
            index = 0;
        }
        String oldName = mappingData.meshes[index].getName();
        BStandardDialog dlg = new BStandardDialog(Translate.text("polymesh:pieceName"), 
                                                  Translate.text("polymesh:enterPieceName"), 
                                                  BStandardDialog.QUESTION);
        String res = dlg.showInputDialog(this, null, oldName);
        if (res != null)
        {
            addUndoCommand(new RenamePieceCommand(index, oldName, res));
            setPieceName(index, res);
        }
    }

    /**
     * Chnages the name of a given mesh piece
     * 
     * @param piece   The index of the piece to change the name of
     * @param newName The piece new name
     */
    public void setPieceName(int piece, String newName){ 
        mappingData.meshes[piece].setName(newName);
        pieceList.replace(piece, newName);
    }

    /**
     * Enables/disables menu items depending on available texture
     */
    private void updateState() {
        if (currentTexture > -1) {
            textureLabel.setEnabled(true);
            textureCB.setEnabled(true);
            componentLabel.setEnabled(true);
            componentCB.setEnabled(true);
            resLabel.setEnabled(true);
            resSpinner.setEnabled(true);
            sendTexToMappingMenu.setEnabled(true);
        } else {
            textureLabel.setEnabled(false);
            textureCB.setEnabled(false);
            componentLabel.setEnabled(false);
            componentCB.setEnabled(false);
            resLabel.setEnabled(false);
            resSpinner.setEnabled(false);
            sendTexToMappingMenu.setEnabled(false);
        }
        for (int i = 0; i < mappingData.mappings.size(); i++) {
            if (mappingData.mappings.get(i) == currentMapping) {
                mappingCB.setSelectedIndex(i);
                mappingMenuItems[i].setState(true);
            } else {
                mappingMenuItems[i].setState(false);
            }
        }
    }

    /**
     * updates the mapping menu when a mapping has been added or discarded
     */
    private void updateMappingMenu() {
        sendTexToMappingMenu.removeAll();
        mappingMenuItems = new BCheckBoxMenuItem[mappingData.mappings.size()];
        for (int i = 0; i < mappingData.mappings.size(); i++) {
            sendTexToMappingMenu.add(mappingMenuItems[i] = new BCheckBoxMenuItem(mappingData.mappings.get(i).name, false));
            mappingMenuItems[i].addEventLink(CommandEvent.class, this, "doSendToMapping");
            if (mappingData.mappings.get(i) == currentMapping)
                mappingMenuItems[i].setState(true);
        }
    }

    /**
     * Assigns a texture to a mapping
     * 
     * @param ev The command event that identifies to which mapping the texture must be assigned to
     */
    private void doSendToMapping(CommandEvent ev) {
        BCheckBoxMenuItem item = (BCheckBoxMenuItem) ev.getWidget();
        UVMeshMapping newMapping = null;
        int from = -1;
        int to = -1;
        for (int i = 0; i < mappingMenuItems.length; i++) {
            if (item == mappingMenuItems[i])
                to = i;
            else if (mappingData.mappings.get(i) == currentMapping)
                from = i;
        }
        SendTextureToMappingCommand cmd = new SendTextureToMappingCommand(texList.get(currentTexture).getID(), from, to);
        addUndoCommand(cmd);
        cmd.execute();
    }

    private void initializeMappingsTextures() {
        ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
        for (int i = 0; i < mappings.size(); i++) {
            UVMeshMapping mapping = mappings.get(i);
            int t;
            if (mapping.textures.size() > 0) {
                for (int j = mapping.textures.size() - 1; j >= 0; j--) {
                    t = getTextureFromID(mapping.textures.get(j));
                    if (t < 0)
                        mapping.textures.remove(j);
                }
            }
        }
    }

    private int getTextureFromID(Integer id) {
        if (texList == null || texList.size() == 0)
            return -1;
        for (int i = 0; i < texList.size(); i++)
            if (texList.get(i).getID() == id)
                return i;
        return -1;
    }

    private void doMappingChanged() {
        int index = mappingCB.getSelectedIndex();
        if (mappingData.mappings.get(index) != currentMapping) {
            int oldMapping = -1;
            for (int i = 0; i < mappingData.mappings.size(); i++)
                if (mappingData.mappings.get(i) == currentMapping)
                    oldMapping = i;
            ChangeMappingCommand cmd = new ChangeMappingCommand(oldMapping, index);
            addUndoCommand(cmd);
            changeMapping(index);
        }
    }

    private void changeMapping(int index) {
        currentMapping = mappingData.mappings.get(index);
        setTexturesForMapping(currentMapping);
        if (currentMapping.textures.size() > 0) {
            currentTexture = getTextureFromID(currentMapping.textures.get(0));
            textureCB.setSelectedIndex(0);
        }
        else
            currentTexture = -1;
        if (currentTexture >= 0)
            mappingCanvas.setTexture(texList.get(currentTexture), mappingList.get(currentTexture));
        else
            mappingCanvas.setTexture(null, null);
        mappingCanvas.setMapping(currentMapping);
        updateState();
    }

    private void doTextureChanged() {
        if (currentTexture == textureCB.getSelectedIndex())
            return;
        int oldTexture = currentTexture;
        currentTexture = textureCB.getSelectedIndex();
        mappingCanvas.setTexture(texList.get(currentTexture), mappingList.get(currentTexture));
        ChangeTextureCommand cmd = new ChangeTextureCommand(oldTexture, currentTexture);
        addUndoCommand(cmd);
        updateState();
    }

    private void doRemoveMapping() {
        if (mappingData.mappings.size() == 1)
            return;

        ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
        for (int i = 0; i < mappings.size(); i++) {
            if (mappings.get(i) == currentMapping) {
                RemoveMappingCommand cmd = new RemoveMappingCommand(currentMapping, i);
                addUndoCommand(cmd);
                cmd.execute();
                break;
            }
        }
    }

    private void doFitMappingToImage() {
        MappingPositionsCommand cmd = mappingCanvas.new MappingPositionsCommand();
        cmd.setOldPos(currentMapping.v);
        Range range = mappingCanvas.getRange();
        cmd.setOldRange(range.umin, range.umax, range.vmin, range.vmax);
        double xmin =  Double.MAX_VALUE;
        double xmax = -Double.MAX_VALUE;
        double ymin =  Double.MAX_VALUE;
        double ymax = -Double.MAX_VALUE;
        for (int i = 0; i < currentMapping.v.length; i++) {
            for (int j = 0; j < currentMapping.v[i].length; j++) {
                if (mappingData.meshes[i].vertices[j].id == -1)
                    continue;
                xmin = Math.min(xmin, currentMapping.v[i][j].x);
                xmax = Math.max(xmax, currentMapping.v[i][j].x);
                ymin = Math.min(ymin, currentMapping.v[i][j].y);
                ymax = Math.max(ymax, currentMapping.v[i][j].y);
            }
        }
        if (xmin == xmax || ymin == ymax)
            return;
        double scale = 0.9/Math.max(xmax - xmin, ymax - ymin);
        double xMargin = (1.0-(xmax - xmin)*scale)*0.5;
        double yMargin = (1.0-(ymax - ymin)*scale)*0.5;

        for (int i = 0; i < currentMapping.v.length; i++)
            for (int j = 0; j < currentMapping.v[i].length; j++) {
                currentMapping.v[i][j].x = (currentMapping.v[i][j].x - xmin) * scale + xMargin;
                currentMapping.v[i][j].y = (currentMapping.v[i][j].y - ymin) * scale + yMargin;
            }

        cmd.setNewPos(currentMapping.v);
        cmd.setNewRange(-0.02, 1.02, -0.02, 1.02);
        addUndoCommand(cmd);
        mappingCanvas.setRange(-0.02, 1.02, -0.02, 1.02);
        mappingCanvas.repaint();
    }

    private void doAddMapping() {
        int sel = mappingCB.getSelectedIndex();
        addMapping(false);
        addUndoCommand(new AddMappingCommand(currentMapping, sel));
    }

    private void doDuplicateMapping() {
        int sel = mappingCB.getSelectedIndex();
        addMapping(true);
        addUndoCommand(new AddMappingCommand(currentMapping, sel));
    }

    /**
     * Adds another mapping to the available mappings.
     * 
     * @param duplicate Use default vertices positions or duplicate current mapping
     */
    private void addMapping(boolean duplicate) {
        BStandardDialog dlg = new BStandardDialog(Translate.text("polymesh:addMapping"), 
                                                  Translate.text("polymesh:enterMappingName"), 
                                                  BStandardDialog.QUESTION);
        String res = dlg.showInputDialog(this, null, Translate.text("polymesh:mappingDefaultName") + " #" + (mappingCB.getItemCount()+1));
        if (res != null) {
            UVMeshMapping mapping = null;
            if (duplicate)
                mapping = mappingData.addNewMapping(res, currentMapping);
            else
                mapping = mappingData.addNewMapping(res, null);
            mappingCB.add(mapping.name);
            mappingCB.setSelectedValue(mapping.name);
            currentTexture = -1;
            currentMapping = mapping;
            mappingCanvas.setTexture(null, null);
            mappingCanvas.setMapping(mapping);
            updateMappingMenu();
            updateState();
        }
    }

    private void doShowSelection(CommandEvent evt) {
        BCheckBoxMenuItem item = (BCheckBoxMenuItem) evt.getWidget();
        preview.setShowSelection(item.getState());
        mappingCanvas.setSelection(mappingCanvas.getSelection());
    }

    private void doSelectAll() {
        mappingCanvas.selectAll();
    }

    private void doSamplingChanged() {
        mappingCanvas.setSampling((Integer) resSpinner.getValue());
    }

    private void processMousePressed(WidgetMouseEvent ev) {
        if (mouseProcessor != null)
            mouseProcessor.stopProcessing();
        doMousePressed(ev);
        mouseProcessor = new ActionProcessor();
    }

    private void processMouseDragged(final WidgetMouseEvent ev) {
        if (mouseProcessor != null)
            mouseProcessor.addEvent(() -> doMouseDragged(ev));
    }

    private void processMouseMoved(final WidgetMouseEvent ev) {
        if (mouseProcessor != null)
            mouseProcessor.addEvent(() -> doMouseMoved(ev));
    }

    private void processMouseReleased(WidgetMouseEvent ev) {
        if (mouseProcessor != null) {
            mouseProcessor.stopProcessing();
            mouseProcessor = null;
            doMouseReleased(ev);
        }
    }

    private void processMouseScrolled(MouseScrolledEvent ev) {
        doMouseScrolled(ev);
    }

    protected void doMousePressed(WidgetMouseEvent ev) {
        manipulator.mousePressed(ev);
    }

    protected void doMouseDragged(WidgetMouseEvent ev) {
        manipulator.mouseDragged(ev);
    }

    protected void doMouseMoved(WidgetMouseEvent ev) {
        manipulator.mouseMoved(ev);
    }

    protected void doMouseReleased(WidgetMouseEvent ev) {
        manipulator.mouseReleased(ev);
    }

    protected void doMouseScrolled(MouseScrolledEvent ev) {
        manipulator.mouseScrolled(ev);
    }

    protected void doChangeComponent() {
        mappingCanvas.setComponent(componentCB.getSelectedIndex());
    }

    private void doOk() {
        clickedOk = true;
        dispose();
    }

    private void doCancel() {
        PolyMesh mesh = (PolyMesh) objInfo.object;
        if (texList != null)
            for (int i = 0; i < texList.size(); i++)
                mappingList.get(i).setFaceTextureCoordinates(mesh, oldCoordList.get(i));
        mesh.setMappingData(oldMappingData);
        dispose();
    }

    private void doPieceListSelection() {
        if (mappingCanvas.getSelectedPiece() == pieceList.getSelectedIndex())
            return;
        addUndoCommand(new SelectPieceCommand(mappingCanvas.getSelectedPiece(), pieceList.getSelectedIndex()));
        mappingCanvas.setSelectedPiece(pieceList.getSelectedIndex());
    }

    /**
     * Works out a default layout when the unfolded meshes are displayed for
     * the first time. Call is forwarded to UVMappingCanvas.initializeMeshLayout().
     */
    public void initializeMeshLayout() {
        mappingCanvas.fitRangeToAll();
    }

    public void displayUVMinMax(double umin, double umax, double vmin, double vmax) {
        DecimalFormat format = new DecimalFormat();
        format.setMaximumFractionDigits(3);
        format.setPositivePrefix(" ");
        uMinValue.setText(format.format(umin));
        vMinValue.setText(format.format(vmin));
        uMaxValue.setText(format.format(umax));
        vMaxValue.setText(format.format(vmax));
    }

    /**
     * @return True if the user has clicked on the Ok Button
     */
    public boolean isClickedOk() {
        return clickedOk;
    }

    /**
     * @return the mappingData
     */
    public UVMappingData getMappingData() {
        return mappingData;
    }

    //for debugging purposes
    private void dumpTextureIDs() {
        ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
        UVMeshMapping mapping;
        for (int i = 0; i < mappings.size(); i++) {
            mapping = mappings.get(i);
            System.out.print(i + ": ");
            for (int j = 0; j < mapping.textures.size(); j++)
                System.out.print(mapping.textures.get(j) + " ");
            System.out.println("");
        }
    }

    /**
     * @return True if mesh tension is on
     */
    public boolean drawGrid() {
        return gridMenuItem.getState();
    }

    /**
     * @return True if mesh tension is on
     */
    public boolean tensionOn() {
        return tension;
    }

    /**
     * @return the tensionCutoff
     */
    public int getMaxTensionDistance() {
        return tensionDistance;
    }

    /**
     * @return the tensionValue
     */
    public double getTensionValue() {
        return tensionArray[tensionValue];
    }

    private void doTensionChanged() {
        tension = meshTensionCB.getState();
        if (tension)
            mappingCanvas.findSelectionDistance();
    }

    private void doTensionValueChanged() {
        tensionValue = tensionCB.getSelectedIndex();
    }

    private void doMaxDistanceValueChanged() {
        tensionDistance = ((Integer) distanceSpinner.getValue());
        mappingCanvas.findSelectionDistance();
    }

    private void openImageExportDialog()
    {
        // First check if the mapping coordinates fit the texture image area.
        // UV-Coordinates must be within [0.0, 1.0], if they aren't, warn the user.

        double xmin =  Double.MAX_VALUE;
        double xmax = -Double.MAX_VALUE;
        double ymin =  Double.MAX_VALUE;
        double ymax = -Double.MAX_VALUE;
        for (int i = 0; i < currentMapping.v.length; i++) {
            for (int j = 0; j < currentMapping.v[i].length; j++) {
                if (mappingData.meshes[i].vertices[j].id == -1)
                    continue;
                xmin = Math.min(xmin, currentMapping.v[i][j].x);
                xmax = Math.max(xmax, currentMapping.v[i][j].x);
                ymin = Math.min(ymin, currentMapping.v[i][j].y);
                ymax = Math.max(ymax, currentMapping.v[i][j].y);
            }
        }
        if(xmin < 0.0 || ymin < 0.0 || xmax > 1.0 || ymax > 1.0)
        {
            BStandardDialog sizeWarning = new BStandardDialog();
            sizeWarning.setStyle(BStandardDialog.WARNING);
            sizeWarning.setMessage(Translate.text("polymesh:mappingSizeWarning"));
            int choice = sizeWarning.showOptionDialog(this, 
                                                      new String[] {Translate.text("polymesh:continue"),
                                                                    Translate.text("polymesh:revert")}, 
                                                                    Translate.text("polymesh:revert"));
            if (choice == 1)
                return;
        }

        // Good to go

        new ExportImageDialog();
    }

    private void createAndExportMapImage(ExportImageDialog exportDialog, File outputFile)
    {
        BufferedImage mappingImage = mappingImage(exportDialog.getResolution(), 
                                                  exportDialog.getSelectedBackground(),
                                                  exportDialog.useAntialias(),
                                                  exportDialog.useMappingColor(),
                                                  exportDialog.textureOnly());

        // Let's make sure it is .png. This could be more sophisticated, 
        // but at least it eliminates mistakes

        String fullPath = outputFile.getAbsolutePath();
        boolean extensionChanged = false;
        if (! fullPath.endsWith(".png"))
        {
            fullPath = fullPath + ".png";
            outputFile = new File(fullPath);
            extensionChanged = true;
        }
        try 
        {
            ImageIO.write(mappingImage, "png", outputFile);
            if (extensionChanged)
            {
                BStandardDialog extWarning = new BStandardDialog();
                extWarning.setStyle(BStandardDialog.INFORMATION);
                extWarning.setMessage(Translate.text("polymesh:fileExtensionChanged") + " " + outputFile.getName());
                extWarning.showMessageDialog(this);
            }
        }
        catch (FileNotFoundException e) 
        {
            e.printStackTrace();
            new BStandardDialog("Save failed", e.getMessage(), BStandardDialog.ERROR).showMessageDialog(this);
        }
        catch (IOException e) 
        {
            e.printStackTrace();
            new BStandardDialog("Save failed", e.getMessage(), BStandardDialog.ERROR).showMessageDialog(this);
        }
    }

    private BufferedImage mappingImage(int resolution, int background, boolean antialiased, boolean mappingColor, boolean textureOnly) 
    {
        UnfoldedMesh[] meshes = mappingData.getMeshes();
        if (meshes == null)
            return null;

        BufferedImage mappingImage = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mappingImage.createGraphics();
        if (antialiased)
        {
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

        // Paint the background

        switch (background)
        {
            case TRANSPARENT:
                break;
            case WHITE:
                g.setColor(Color.white);
                g.fillRect(0, 0, resolution, resolution);
                break;
            case TEXTURED:
                TextureParameter param[] = mappingList.get(currentTexture).getParameters();
                double paramVal[] = null;
                if (param != null) 
                {
                    paramVal = new double[param.length];
                    for (int i = 0; i < param.length; i++)
                        paramVal[i] = param[i].defaultVal;
                }
                Image textureImage = ((Texture2D)texList.
                                       get(currentTexture)).
                                       createComponentImage(0, 1, 0, 1, 
                                                            resolution, resolution, 
                                                            componentCB.getSelectedIndex(),
                                                            0.0, paramVal);
                if (textureImage != null)
                     g.drawImage(textureImage, 0, 0, null);
                break; 
            default:
                break;
        }

        // Draw the lines

        if (!textureOnly)
        {
            AffineTransform at = new AffineTransform();
            at.scale(resolution, -resolution);
            at.translate(0.0, -1.0);
            g.setStroke(new BasicStroke((float)(1.0/resolution)));
            g.setTransform(at);
            if (mappingColor)
                g.setColor(currentMapping.edgeColor);
            else
                g.setColor(Color.black);
            for (int i = 0; i < meshes.length; i++) 
            {
                UnfoldedMesh mesh = meshes[i];
                Vec2[] v = currentMapping.v[i];
                UnfoldedEdge[] e = mesh.getEdges();
                for (int j = 0; j < e.length; j++)
                {
                    if (e[j].hidden) // What is this? Need another user choice?
                        continue;
                    g.draw(new Line2D.Double(v[e[j].v1].x, v[e[j].v1].y, v[e[j].v2].x, v[e[j].v2].y));
                }
            }
        }
        g.dispose();

        // We're good

        return mappingImage;
    }

    /**
     * Undo/Redo command for sending texture to mapping
     */
    public class ChangeTextureCommand implements Command {

        int oldTexture, newTexture;

        public ChangeTextureCommand(int oldTexture, int newTexture) {
            super();
            this.oldTexture = oldTexture;
            this.newTexture = newTexture;
        }

        public void execute() {
            redo();
        }

        public void redo() {
            textureCB.setSelectedIndex(newTexture);
            doTextureChanged();
        }

        public void undo() {
            textureCB.setSelectedIndex(oldTexture);
            doTextureChanged();
        }
    }

    /**
     * Undo/Redo command for sending texture to mapping
     */
    public class SendTextureToMappingCommand implements Command {

        int texture, oldMapping, newMapping;

        public SendTextureToMappingCommand(int texture, int oldMapping, int newMapping)
        {
            this.texture = texture;
            this.oldMapping = oldMapping;
            this.newMapping = newMapping;
        }

        public void execute() {
            redo();
        }

        public void redo() {
            sendToMapping(oldMapping, newMapping);
        }

        public void undo() {
            sendToMapping(newMapping, oldMapping);
        }

        public void sendToMapping(int from, int to) {
            UVMeshMapping fromMapping = mappingData.mappings.get(from);
            UVMeshMapping toMapping = mappingData.mappings.get(to);
            for (int j = 0; j < fromMapping.textures.size(); j++)
                if (texture == fromMapping.textures.get(j)) {
                    fromMapping.textures.remove(j);
                    break;
                }

            mappingMenuItems[from].setState(false);
            toMapping.textures.add(texture);
            mappingMenuItems[from].setState(false);
            mappingMenuItems[to].setState(true);
            changeMapping(to);
            mappingCB.setSelectedIndex(to);
            updateState();
        }
    }

    /**
     * Undo/Redo command for changing selected mapping
     */
    public class ChangeMappingCommand implements Command {

        int oldMapping, newMapping;

        public ChangeMappingCommand(int oldMapping, int newMapping) {
            this.oldMapping = oldMapping;
            this.newMapping = newMapping;
        }

        public void execute() {
            redo();
        }

        public void redo() {
            changeMapping(newMapping);
        }

        public void undo() {
            changeMapping(oldMapping);
        }
    }

    /**
     * Undo/Redo command for adding a mapping
     */
    public class RemoveMappingCommand implements Command {

        UVMeshMapping mapping;
        int index;

        public RemoveMappingCommand(UVMeshMapping mapping, int index) {
            super();
            this.mapping = mapping.duplicate();
            this.index = index;
        }

        public void execute() {
            redo();
        }

        public void redo() 
        {
            ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
            mappingCB.remove(index);
            mappings.remove(index);
            UVMeshMapping firstMapping = mappings.get(0);
            for (int j = 0; j < currentMapping.textures.size(); j++)
                firstMapping.textures.add(currentMapping.textures.get(j));
            if (firstMapping.textures.size() > 0) {
                currentTexture = getTextureFromID(firstMapping.textures.get(0));
                mappingCanvas.setTexture(texList.get(currentTexture), mappingList.get(currentTexture));
            } else {
                currentTexture = -1;
                mappingCanvas.setTexture(null, null);
            }
            mappingCanvas.setMapping(firstMapping);
            currentMapping = firstMapping;
            updateMappingMenu();
            setTexturesForMapping(currentMapping);
            if (currentTexture != -1)
                textureCB.setSelectedIndex(currentTexture);
            updateState();
            mappingCanvas.repaint();
        }

        public void undo() {
            ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
            UVMeshMapping newMapping = mapping.duplicate();
            mappingCB.add(index, newMapping.name);
            mappings.add(index, newMapping);
            UVMeshMapping firstMapping = mappings.get(0);
            for (int j = 0; j < mapping.textures.size(); j++)
                firstMapping.textures.remove(mapping.textures.get(j));
            currentMapping = newMapping;
            if (currentMapping.textures.size() > 0) {
                currentTexture = getTextureFromID(currentMapping.textures.get(0));
                mappingCanvas.setTexture(texList.get(currentTexture), mappingList.get(currentTexture));
            } else {
                currentTexture = -1;
                mappingCanvas.setTexture(null, null);
            }
            mappingCanvas.setMapping(currentMapping);
            updateMappingMenu();
            setTexturesForMapping(currentMapping);
            if (currentTexture != -1)
                textureCB.setSelectedIndex(currentTexture);
            updateState();
            mappingCanvas.repaint();
        }
    }

    /**
     * Undo/Redo command for adding a mapping
     */
    public class AddMappingCommand implements Command {

        UVMeshMapping mapping;
        int selected;

        public AddMappingCommand(UVMeshMapping mapping, int selected){
            super();
            this.mapping = mapping.duplicate();
            this.selected = selected;
        }

        public void execute() {
            redo();
        }

        public void redo() {
            UVMeshMapping newMapping = mapping.duplicate();
            mappingData.mappings.add(newMapping);
            mappingCB.add(newMapping.name);
            mappingCB.setSelectedValue(newMapping.name);
            currentTexture = -1;
            currentMapping = newMapping;
            mappingCanvas.setTexture(null, null);
            mappingCanvas.setMapping(newMapping);
            updateMappingMenu();
            updateState();
        }

        public void undo() {
            ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
            int index = mappings.size() - 1;
            mappingCB.remove(index);
            mappings.remove(index);
            UVMeshMapping newMapping = mappings.get(selected);
            mappingCanvas.setMapping(newMapping);
            currentMapping = newMapping;
            updateMappingMenu();
            updateState();
        }
    }

    /**
     * Undo/Redo command for selecting a piece
     */
    public class SelectPieceCommand implements Command {

        private int oldPiece;
        private int newPiece;

        public SelectPieceCommand(int oldPiece, int newPiece) {
            super();
            this.oldPiece = oldPiece;
            this.newPiece = newPiece;
        }

        public void execute() {
            redo();
        }

        public void redo() {
            mappingCanvas.setSelectedPiece(newPiece);
            pieceList.setSelected(newPiece, true);
            repaint();
        }

        public void undo() {
            mappingCanvas.setSelectedPiece(oldPiece);
            pieceList.setSelected(oldPiece, true);
            repaint();
        }
    }

    /**
     * Undo/Redo command for renaming a piece
     */
    public class RenamePieceCommand implements Command {

        private int piece;
        private String oldName;
        private String newName;

        public RenamePieceCommand(int piece, String oldName, String newName) {
            super();
            this.piece = piece;
            this.oldName = oldName;
            this.newName = newName;
        }

        public void execute() {
            redo();
        }

        public void redo() {
            setPieceName(piece, newName);
            repaint();
        }

        public void undo() {
            setPieceName(piece, oldName);
        }
    }

    /**
     * A dialog to to define the exported mapping image.
     */
    class ExportImageDialog extends BDialog 
    {
        BSpinner resolutionSpinner;
        BButton exportButton;
        BButton closeButton;
        BButton cancelButton;
        RadioButtonGroup bgButtons, colorButtons;
        BRadioButton transparentButton, whiteButton, texturedButton, useMappingButton, blackButton;
        BCheckBox antialiasBox, textureOnlyBox;
        ColumnContainer content, leftBox, rightBox;
        RowContainer resoContainer, optsContainer, actionContainer;

        // Things to consider:
        // - Selection for line width? More choices for antialiased image?
        // - Option to export the texture image only

        ExportImageDialog()
        {
            super(UVMappingEditorDialog.this, true);
            addEventLink(WindowClosingEvent.class, new Object()
                        {void processEvent(WindowClosingEvent e) {dispose();}});
            
            LayoutInfo labelLayout  = new LayoutInfo(LayoutInfo.WEST,      LayoutInfo.NONE,       new Insets(10, 10, 0,  2), null);
            LayoutInfo valueLayout  = new LayoutInfo(LayoutInfo.WEST,      LayoutInfo.HORIZONTAL, new Insets(10,  0, 2, 10), null);
            LayoutInfo headerLayout = new LayoutInfo(LayoutInfo.NORTHWEST, LayoutInfo.NONE,       new Insets(10, 10, 5, 10), null);
            LayoutInfo radioLayout  = new LayoutInfo(LayoutInfo.NORTHWEST, LayoutInfo.NONE,       new Insets( 0, 25, 0, 10), null);
            LayoutInfo actionLayout = new LayoutInfo(LayoutInfo.SOUTHEAST, LayoutInfo.NONE,       new Insets(10,  0, 0, 10), null);
            LayoutInfo boxLayout    = new LayoutInfo(LayoutInfo.NORTHWEST, LayoutInfo.NONE,       new Insets( 0,  0, 0,  0), null);
            LayoutInfo actboxLayout = new LayoutInfo(LayoutInfo.SOUTHEAST, LayoutInfo.NONE,       new Insets( 15, 0, 0, 10), null);

            content = new ColumnContainer();
            content.add(resoContainer   = new RowContainer(), boxLayout);
            content.add(optsContainer   = new RowContainer(), boxLayout);
            content.add(actionContainer = new RowContainer(), actboxLayout);
            optsContainer.add(leftBox   = new ColumnContainer(), boxLayout);
            optsContainer.add(rightBox  = new ColumnContainer(), boxLayout);

            resoContainer.add(new BLabel(Translate.text("polymesh:imageResolution")), labelLayout);
            resoContainer.add(resolutionSpinner = new BSpinner(), valueLayout);

            bgButtons = new RadioButtonGroup();
            leftBox.add(new BLabel(Translate.text("polymesh:backgroundType")), headerLayout);
            leftBox.add(transparentButton = new BRadioButton(Translate.text("polymesh:transparent"), true,  bgButtons), radioLayout);
            leftBox.add(whiteButton       = new BRadioButton(Translate.text("polymesh:white"),       false, bgButtons), radioLayout);
            leftBox.add(texturedButton    = new BRadioButton(Translate.text("polymesh:textured"),    false, bgButtons), radioLayout);
            leftBox.add(textureOnlyBox    = new BCheckBox   (Translate.text("polymesh:textureOnly"), false), radioLayout);
            textureOnlyBox.setEnabled(false);

            colorButtons = new RadioButtonGroup();
            rightBox.add(new BLabel(Translate.text("polymesh:lineProperties")), headerLayout);
            rightBox.add(antialiasBox     = new BCheckBox   (Translate.text("polymesh:softLines"),       true), radioLayout);
            rightBox.add(useMappingButton = new BRadioButton(Translate.text("polymesh:useMappingColor"), true,  colorButtons), radioLayout);
            rightBox.add(blackButton      = new BRadioButton(Translate.text("polymesh:useBlack"),        false, colorButtons), radioLayout);

            actionContainer.add(exportButton = new BButton(Translate.text("polymesh:exportImage")));
            actionContainer.add(cancelButton = new BButton(Translate.text("polymesh:cancel")));

            transparentButton.addEventLink(ValueChangedEvent.class, this, "updateDialogState");
            whiteButton.addEventLink      (ValueChangedEvent.class, this, "updateDialogState");
            texturedButton.addEventLink   (ValueChangedEvent.class, this, "updateDialogState");
            textureOnlyBox.addEventLink   (ValueChangedEvent.class, this, "updateDialogState");

            cancelButton.addEventLink(CommandEvent.class, this, "close");
            exportButton.addEventLink(CommandEvent.class, new Object()
            {
                void processEvent()
                {
                    openExportChooser(ExportImageDialog.this); // Could move the method inline here?
                }
            });

            if (currentTexture == -1)
                texturedButton.setEnabled(false);
            
            resolutionSpinner.setValue(123456); 
            Dimension d = resolutionSpinner.getPreferredSize();
            resolutionSpinner.getComponent().setPreferredSize(d);
            resolutionSpinner.setValue(640); 

            setContent(content);
            pack();
            setResizable(false);
            setVisible(true);
        }

        private void updateDialogState()
        {
            textureOnlyBox.setEnabled(texturedButton.getState());
            antialiasBox.setEnabled(!textureOnlyBox.isEnabled() || !textureOnlyBox.getState());
            useMappingButton.setEnabled(!textureOnlyBox.isEnabled() || !textureOnlyBox.getState());
            blackButton.setEnabled(!textureOnlyBox.isEnabled() || !textureOnlyBox.getState());
        }
        
        int getSelectedBackground()
        {
            if (transparentButton.getState()) return TRANSPARENT;
            if (whiteButton.getState())       return WHITE;
            if (texturedButton.getState())    return TEXTURED;
            return -1;
        }

        int getResolution()
        {
            return (Integer) resolutionSpinner.getValue();
        }
        
        boolean useAntialias()
        {
            return antialiasBox.getState();
        }

        boolean useMappingColor()
        {
            return useMappingButton.getState();
        }

        boolean textureOnly()
        {
            return textureOnlyBox.getState();
        }

        void close()
        {
            dispose();
        }

        private void openExportChooser(ExportImageDialog exportDialog)
        {
            BFileChooser exportChooser = new BFileChooser(BFileChooser.SAVE_FILE, Translate.text("polymesh:chooseExportImageFile"));
            exportChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG file", "png"));
            exportChooser.setSelectedFile(new File(objInfo.getName() + ", " + currentMapping.name + ".png"));
            if (exportChooser.showDialog(this))
            {
                try
                {
                    createAndExportMapImage(exportDialog, exportChooser.getSelectedFile());
                    exportDialog.close();
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}
