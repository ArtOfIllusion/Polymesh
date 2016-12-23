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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;

import artofillusion.image.BMPEncoder;
import artofillusion.math.Vec2;
import artofillusion.object.FacetedMesh;
import artofillusion.object.ObjectInfo;
import artofillusion.polymesh.UVMappingCanvas.MappingPositionsCommand;
import artofillusion.polymesh.UVMappingCanvas.Range;
import artofillusion.polymesh.UVMappingData.UVMeshMapping;
import artofillusion.texture.LayeredMapping;
import artofillusion.texture.LayeredTexture;
import artofillusion.texture.Texture;
import artofillusion.texture.TextureMapping;
import artofillusion.texture.UVMapping;
import artofillusion.ui.ActionProcessor;
import artofillusion.ui.ComponentsDialog;
import artofillusion.ui.Translate;
import artofillusion.ui.UIUtilities;
import artofillusion.ui.ValueField;
import buoy.event.CommandEvent;
import buoy.event.MouseDraggedEvent;
import buoy.event.MouseMovedEvent;
import buoy.event.MousePressedEvent;
import buoy.event.MouseReleasedEvent;
import buoy.event.MouseScrolledEvent;
import buoy.event.SelectionChangedEvent;
import buoy.event.ValueChangedEvent;
import buoy.event.WidgetMouseEvent;
import buoy.event.WindowClosingEvent;
import buoy.widget.BButton;
import buoy.widget.BCheckBox;
import buoy.widget.BCheckBoxMenuItem;
import buoy.widget.BColorChooser;
import buoy.widget.BComboBox;
import buoy.widget.BDialog;
import buoy.widget.BFileChooser;
import buoy.widget.BFrame;
import buoy.widget.BLabel;
import buoy.widget.BList;
import buoy.widget.BMenu;
import buoy.widget.BMenuBar;
import buoy.widget.BMenuItem;
import buoy.widget.BScrollPane;
import buoy.widget.BSeparator;
import buoy.widget.BSpinner;
import buoy.widget.BSplitPane;
import buoy.widget.BStandardDialog;
import buoy.widget.BTextField;
import buoy.widget.BorderContainer;
import buoy.widget.LayoutInfo;
import buoy.widget.RowContainer;
import buoy.widget.Widget;
import buoy.xml.WidgetDecoder;

/**
 * This window allows the user to edit UV mapping using unfolded pieces of mesh
 * displayed over the texture image.
 * 
 * @author Francois Guillet
 * 
 */
public class UVMappingEditorDialog extends BDialog {

	/**
	 * Undo/Redo command for sending texture to mapping
	 * 
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
	 * 
	 */
	public class SendTextureToMappingCommand implements Command {

		int texture, oldMapping, newMapping;

		public SendTextureToMappingCommand(int texture, int oldMapping,
				int newMapping) {
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
			for (int j = 0; j < fromMapping.textures.size(); j++) {
				if (texture == fromMapping.textures.get(j).intValue()) {
					fromMapping.textures.remove(j);
					break;
				}
			}
			mappingMenuItems[from].setState(false);
			toMapping.textures.add(new Integer(texture));
			mappingMenuItems[from].setState(false);
			mappingMenuItems[to].setState(true);
			changeMapping(to);
			mappingCB.setSelectedIndex(to);
			updateState();
		}
	}

	/**
	 * Undo/Redo command for changing selected mapping
	 * 
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
	 * 
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

		public void redo() {
			ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
			mappingCB.remove(index);
			mappings.remove(index);
			UVMeshMapping firstMapping = mappings.get(0);
			for (int j = 0; j < currentMapping.textures.size(); j++) {
				firstMapping.textures.add(currentMapping.textures.get(j));
			}
			if (firstMapping.textures.size() > 0) {
				currentTexture = getTextureFromID(firstMapping.textures.get(0));
				mappingCanvas.setTexture(texList.get(currentTexture),
						mappingList.get(currentTexture));
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
			for (int j = 0; j < mapping.textures.size(); j++) {
				firstMapping.textures.remove(mapping.textures.get(j));
			}
			currentMapping = newMapping;
			if (currentMapping.textures.size() > 0) {
				currentTexture = getTextureFromID(currentMapping.textures
						.get(0));
				mappingCanvas.setTexture(texList.get(currentTexture),
						mappingList.get(currentTexture));
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
	 * 
	 */
	public class AddMappingCommand implements Command {

		UVMeshMapping mapping;

		int selected;

		public AddMappingCommand(UVMeshMapping mapping, int selected) {
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
	 * 
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
	 * 
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
	 * Implementation of ExportImage dialog as a subclass of UVMappingEditor
	 * Dialog.
	 * 
	 * @author Francois Guillet
	 * 
	 */
	private class ExportImageDialog extends BDialog {

		private BorderContainer borderContainer1;

		private BSpinner widthSpinner;

		private BSpinner heightSpinner;

		private BTextField fileTextField;

		private BButton fileButton;

		private BButton okButton;

		private BButton cancelButton;

		public int width;

		public int height;

		public boolean clickedOk;

		public File file;

		public ExportImageDialog(int width, int height) {
			super(UVMappingEditorDialog.this, true);
			this.width = width;
			this.height = height;
			setTitle(Translate.text("polymesh:exportImageFile"));
			InputStream inputStream = null;
			try {
				WidgetDecoder decoder = new WidgetDecoder(
						inputStream = getClass().getResource(
								"interfaces/exportImage.xml").openStream(),
						PolyMeshPlugin.resources);
				borderContainer1 = (BorderContainer) decoder.getRootObject();
				widthSpinner = ((BSpinner) decoder.getObject("widthSpinner"));
				widthSpinner.setValue(new Integer(width));
				heightSpinner = ((BSpinner) decoder.getObject("heightSpinner"));
				heightSpinner.setValue(new Integer(height));
				fileTextField = ((BTextField) decoder
						.getObject("fileTextField"));
				fileButton = ((BButton) decoder.getObject("fileButton"));
				okButton = ((BButton) decoder.getObject("okButton"));
				cancelButton = ((BButton) decoder.getObject("cancelButton"));
				okButton.addEventLink(CommandEvent.class, this, "doOK");
				cancelButton.addEventLink(CommandEvent.class, this, "doCancel");
				fileButton.addEventLink(CommandEvent.class, this,
						"doChooseFile");
				fileTextField.addEventLink(ValueChangedEvent.class, this,
						"doFilePathChanged");
				this.addEventLink(WindowClosingEvent.class, this, "doCancel");
				setContent(borderContainer1);
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				try {
					if (inputStream != null)
						inputStream.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
			file = null;
			pack();
			setVisible(true);
		}

		@SuppressWarnings("unused")
		private void doOK() {
			clickedOk = true;
			width = ((Integer) widthSpinner.getValue()).intValue();
			height = ((Integer) heightSpinner.getValue()).intValue();
			dispose();
		}

		@SuppressWarnings("unused")
		private void doCancel() {
			clickedOk = false;
			dispose();
		}

		@SuppressWarnings("unused")
		private void doChooseFile() {
			BFileChooser chooser = new BFileChooser(BFileChooser.SAVE_FILE,
					Translate.text("polymesh:chooseExportImageFile"));
			if (file != null) {
				chooser.setDirectory(file.getParentFile());
			}
			if (chooser.showDialog(UVMappingEditorDialog.this)) {
				file = chooser.getSelectedFile();
				fileTextField.setText(file.getAbsolutePath());
			}
		}

		@SuppressWarnings("unused")
		private void doFilePathChanged() {
			file = new File(fileTextField.getText());
		}
	}

	private UVMappingCanvas mappingCanvas; // the mapping canvas displayed

	// at window center

	private BList pieceList; // the list of mesh pieces

	private UVMappingData mappingData; // mapping data associated to the

	// unfolded mesh

	private MeshPreviewer preview; // 3D textutring preview

	protected ActionProcessor mouseProcessor;

	protected UVMappingManipulator manipulator;

	private ObjectInfo objInfo;

	private UVMeshMapping currentMapping; // the mapping currently edited

	private int currentTexture; // the texture currently edited

	private ArrayList<Texture> texList; // the texture list of edited mesh

	private ArrayList<UVMapping> mappingList; // the corresponding mapping

	// list

	private ArrayList<Vec2[][]> oldCoordList; // the old texture coordinates

	// for undoing changes

	private UVMappingData oldMappingData; // the original mapping data

	// for undoing changes

	private PMUndoRedoStack undoRedoStack; // the Undo/Redo stack

	private boolean clickedOk; // true if the user clicked the ok button

	private boolean tension;

	private int tensionValue;

	protected static final double[] tensionArray = { 5.0, 3.0, 2.0, 1.0, 0.5 };

	private int tensionDistance;

	private int undoLevels;

	/* Interface variables */
	private BorderContainer borderContainer1;

	private BLabel componentLabel;

	private BComboBox componentCB;

	private BLabel uMinValue;

	private BLabel uMaxValue;

	private BLabel vMinValue;

	private BLabel vMaxValue;

	private BButton autoButton;

	private BLabel resLabel;

	private BSpinner resSpinner;

	private BComboBox mappingCB;

	private BLabel textureLabel;

	private BComboBox textureCB;

	private BCheckBox meshTensionCB;

	private BSpinner distanceSpinner;

	private BComboBox tensionCB;

	private BMenuBar menuBar;

	private BMenuItem undoMenuItem;

	private BMenuItem redoMenuItem;

	private BMenu sendTexToMappingMenu;

	private BMenuItem removeMappingMenuItem;

	private BCheckBoxMenuItem[] mappingMenuItems;

	public UVMappingEditorDialog(String title, ObjectInfo objInfo,
			boolean initialize, BFrame parent) {
		super(parent, title, true);
		this.objInfo = objInfo;
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mappingData = mesh.getMappingData();
		oldMappingData = mappingData.duplicate();
		undoLevels = 20;
		undoRedoStack = new PMUndoRedoStack(undoLevels);
		tension = false;
		tensionValue = 2;
		tensionDistance = 3;
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
						oldCoordList
								.add(((UVMapping) mapping)
										.findFaceTextureCoordinates((FacetedMesh) objInfo.object));
					}
				}
			}
		} else {
			if (mapping instanceof UVMapping) {
				if (((UVMapping) mapping).isPerFaceVertex(mesh)) {
					texList.add(tex);
					mappingList.add((UVMapping) mapping);
					oldCoordList
							.add(((UVMapping) mapping)
									.findFaceTextureCoordinates((FacetedMesh) objInfo.object));
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
					for (int k = 0; k < textures.size(); k++) {
						if (getTextureFromID(textures.get(k)) == i) {
							hasTexture = true;
						}
					}
				}
				if (!hasTexture) {
					currentMapping.textures.add(texList.get(i).getID());
				}
			}
		}
		if (currentMapping.textures.size() > 0) {
			currentTexture = getTextureFromID(currentMapping.textures.get(0));
		}
		// create interface
		BorderContainer content = new BorderContainer();
		setContent(content);
		RowContainer buttons = new RowContainer();
		buttons.setDefaultLayout(new LayoutInfo(LayoutInfo.CENTER,
				LayoutInfo.NONE, new Insets(2, 2, 2, 2), new Dimension(0, 0)));
		buttons.add(Translate.button("ok", this, "doOk"));
		buttons.add(Translate.button("cancel", this, "doCancel"));
		content.add(buttons, BorderContainer.SOUTH, new LayoutInfo(
				LayoutInfo.CENTER, LayoutInfo.NONE, new Insets(2, 2, 2, 2),
				new Dimension(0, 0)));
		InputStream inputStream = null;
		try {
			WidgetDecoder decoder = new WidgetDecoder(inputStream = getClass()
					.getResource("interfaces/unfoldEditor.xml").openStream(),
					PolyMeshPlugin.resources);
			borderContainer1 = (BorderContainer) decoder.getRootObject();
			uMinValue = ((BLabel) decoder.getObject("uMinValue"));
			uMaxValue = ((BLabel) decoder.getObject("uMaxValue"));
			vMinValue = ((BLabel) decoder.getObject("vMinValue"));
			vMaxValue = ((BLabel) decoder.getObject("vMaxValue"));
			autoButton = ((BButton) decoder.getObject("autoButton"));
			autoButton.addEventLink(CommandEvent.class, this, "doAutoScale");
			resLabel = ((BLabel) decoder.getObject("resLabel"));
			mappingCB = ((BComboBox) decoder.getObject("mappingCB"));
			mappingCB.addEventLink(ValueChangedEvent.class, this,
					"doMappingChanged");
			textureLabel = ((BLabel) decoder.getObject("textureLabel"));
			textureCB = ((BComboBox) decoder.getObject("textureCB"));
			textureCB.addEventLink(ValueChangedEvent.class, this,
					"doTextureChanged");
			componentLabel = ((BLabel) decoder.getObject("componentLabel"));
			componentCB = ((BComboBox) decoder.getObject("componentCB"));
			content.add(borderContainer1, BorderContainer.WEST, new LayoutInfo(
					LayoutInfo.CENTER, LayoutInfo.BOTH, new Insets(2, 2, 2, 2),
					new Dimension(0, 0)));
			ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
			for (int i = 0; i < mappings.size(); i++) {
				mappingCB.add(mappings.get(i).name);
			}
			setTexturesForMapping(currentMapping);
			componentCB.setContents(new String[] { Translate.text("Diffuse"),
					Translate.text("Specular"), Translate.text("Transparent"),
					Translate.text("Hilight"), Translate.text("Emissive") });
			componentCB.addEventLink(ValueChangedEvent.class, this,
					"doChangeComponent");
			resSpinner = ((BSpinner) decoder.getObject("resSpinner"));
			resSpinner.setValue(new Integer(mappingData.sampling));
			resSpinner.addEventLink(ValueChangedEvent.class, this,
					"doSamplingChanged");
			meshTensionCB = ((BCheckBox) decoder.getObject("meshTensionCB"));
			meshTensionCB.addEventLink(ValueChangedEvent.class, this,
					"doTensionChanged");
			distanceSpinner = ((BSpinner) decoder.getObject("distanceSpinner"));
			distanceSpinner.setValue(tensionDistance);
			distanceSpinner.addEventLink(ValueChangedEvent.class, this,
					"doMaxDistanceValueChanged");
			tensionCB = ((BComboBox) decoder.getObject("tensionCB"));
			tensionCB.addEventLink(ValueChangedEvent.class, this,
					"doTensionValueChanged");
			tensionCB.setContents(new String[] { Translate.text("VeryLow"),
					Translate.text("Low"), Translate.text("Medium"),
					Translate.text("High"), Translate.text("VeryHigh") });
			tensionCB.setSelectedIndex(tensionValue);
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			try {
				if (inputStream != null)
					inputStream.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		BSplitPane meshViewPanel = new BSplitPane(BSplitPane.VERTICAL,
				pieceList = new BList(), preview = new MeshPreviewer(objInfo,
						150, 150));
		tex = null;
		mapping = null;
		if (currentTexture >= 0) {
			tex = texList.get(currentTexture);
			mapping = mappingList.get(currentTexture);
		}
		mappingCanvas = new UVMappingCanvas(this, mappingData, preview, tex,
				(UVMapping) mapping);
		BScrollPane sp = new BScrollPane(mappingCanvas);
		meshViewPanel.setResizeWeight(0.7);
		meshViewPanel.setContinuousLayout(true);
		BSplitPane div = new BSplitPane(BSplitPane.HORIZONTAL, sp,
				meshViewPanel);
		div.setResizeWeight(0.5);
		div.setContinuousLayout(true);
		content.add(div, BorderContainer.CENTER, new LayoutInfo(
				LayoutInfo.CENTER, LayoutInfo.BOTH, new Insets(2, 2, 2, 2),
				new Dimension(0, 0)));
		UnfoldedMesh[] meshes = mappingData.getMeshes();
		for (int i = 0; i < meshes.length; i++)
			pieceList.add(meshes[i].getName());
		pieceList.setMultipleSelectionEnabled(false);
		pieceList.setSelected(0, true);
		addEventLink(WindowClosingEvent.class, this, "doCancel");
		UIUtilities.centerWindow(this);
		mappingCanvas.addEventLink(MousePressedEvent.class, this,
				"processMousePressed");
		mappingCanvas.addEventLink(MouseReleasedEvent.class, this,
				"processMouseReleased");
		mappingCanvas.addEventLink(MouseDraggedEvent.class, this,
				"processMouseDragged");
		mappingCanvas.addEventLink(MouseMovedEvent.class, this,
				"processMouseMoved");
		mappingCanvas.addEventLink(MouseScrolledEvent.class, this,
				"processMouseScrolled");
		manipulator = new UVMappingManipulator(mappingCanvas, this);
		menuBar = new BMenuBar();
		BMenu menu = Translate.menu("polymesh:edit");
		menu.add(undoMenuItem = Translate.menuItem("undo", this, "doUndo"));
		menu.add(redoMenuItem = Translate.menuItem("redo", this, "doRedo"));
		menu.add(Translate.menuItem("polymesh:undoLevels", this, "doSetUndoLevels"));
		menu.addSeparator();
		menu.add(Translate.menuItem("polymesh:selectAll", this, "doSelectAll"));
		menu.add(Translate.menuItem("polymesh:pinSelection", this, "doPinSelection"));
		menu.add(Translate.menuItem("polymesh:unpinSelection", this,
				"doUnpinSelection"));
		menu.add(Translate.menuItem("polymesh:renameSelectedPiece", this,
				"doRenameSelectedPiece"));
		menu.add(new BSeparator());
		menu.add(Translate.menuItem("polymesh:exportImage", this, "doExportImage"));
		menuBar.add(menu);
		menu = Translate.menu("polymesh:mapping");
		menu.add(Translate.menuItem("polymesh:fitMappingToImage", this,
				"doFitMappingToImage"));
		menu.add(Translate.menuItem("polymesh:addMapping", this, "doAddMapping"));
		menu.add(Translate.menuItem("polymesh:duplicateMapping", this,
				"doDuplicateMapping"));
		removeMappingMenuItem = Translate.menuItem("polymesh:removeMapping", this,
				"doRemoveMapping");
		menu.add(removeMappingMenuItem);
		menu.add(Translate.menuItem("polymesh:editMappingColor", this,
				"doEditMappingColor"));
		menu.add(new BSeparator());
		sendTexToMappingMenu = Translate.menu("polymesh:sendTexToMapping");
		menu.add(sendTexToMappingMenu);
		updateMappingMenu();
		menuBar.add(menu);
		menu = Translate.menu("polymesh:preferences");
		BCheckBoxMenuItem cbitem = Translate.checkboxMenuItem(
				"polymesh:showSelectionOnPreview", this, "doShowSelection", true);
		menu.add(cbitem);
		cbitem = Translate.checkboxMenuItem("polymesh:liveUpdate", this,
				"doLiveUpdate", true);
		menu.add(cbitem);
		cbitem = Translate.checkboxMenuItem("polymesh:boldEdges", this, "doBoldEdges",
				true);
		menu.add(cbitem);
		menuBar.add(menu);
		setMenuBar(menuBar);
		setTexturesForMapping(currentMapping);
		if (currentTexture != -1) {
			textureCB.setSelectedIndex(0);
		}
		updateState();
		
		pack();
		pieceList.addEventLink(SelectionChangedEvent.class, this,
				"doPieceListSelection");
		setVisible(true);
	}

	@SuppressWarnings("unused")
	private void doSetUndoLevels() {
		ValueField undoLevelsVF = new ValueField((double) undoLevels,
				ValueField.POSITIVE + ValueField.INTEGER);
		ComponentsDialog dlg = new ComponentsDialog(this, Translate
				.text("polymesh:setUndoLevelsTitle"), new Widget[] { undoLevelsVF },
				new String[] { Translate.text("polymesh:numberOfUndoLevels") });
		if (!dlg.clickedOk())
			return;
		if ((int) undoLevelsVF.getValue() == undoLevels) {
			return;
		}
		undoLevels = (int) undoLevelsVF.getValue();
		undoRedoStack.setSize(undoLevels);
		updateUndoRedoMenus();
	}

	private void setTexturesForMapping(UVMeshMapping currentMapping) {
		textureCB.removeAll();
		ArrayList<Integer> textures = currentMapping.textures;
		for (int i = 0; i < textures.size(); i++) {
			textureCB.add(texList.get(getTextureFromID(textures.get(i)))
					.getName());
		}
	}

	@SuppressWarnings("unused")
	private void doUndo() {
		if (undoRedoStack.canUndo()) {
			undoRedoStack.undo();
		}
		updateUndoRedoMenus();
	}

	@SuppressWarnings("unused")
	private void doRedo() {
		if (undoRedoStack.canRedo()) {
			undoRedoStack.redo();
		}
		updateUndoRedoMenus();
	}

	private void updateUndoRedoMenus() {
		if (undoRedoStack.canUndo()) {
			undoMenuItem.setEnabled(true);
		} else {
			undoMenuItem.setEnabled(false);
		}
		if (undoRedoStack.canRedo()) {
			redoMenuItem.setEnabled(true);
		} else {
			redoMenuItem.setEnabled(false);
		}
	}

	/**
	 * Adds a command to the undo stack
	 * 
	 * @param cmd
	 *                The command to add to the stack
	 */
	public void addUndoCommand(Command cmd) {
		undoRedoStack.addCommand(cmd);
		updateUndoRedoMenus();
	}

	@SuppressWarnings("unused")
	private void doEditMappingColor() {
		BColorChooser chooser = new BColorChooser(currentMapping.edgeColor,
				"chooseEdgeColor");
		if (chooser.showDialog(this)) {
			currentMapping.edgeColor = chooser.getColor();
			mappingCanvas.repaint();
		}
	}

	@SuppressWarnings("unused")
	private void doPinSelection() {
		mappingCanvas.pinSelection(true);
	}

	@SuppressWarnings("unused")
	private void doUnpinSelection() {
		mappingCanvas.pinSelection(false);
	}

	@SuppressWarnings("unused")
	private void doBoldEdges(CommandEvent evt) {
		BCheckBoxMenuItem item = (BCheckBoxMenuItem) evt.getWidget();
		mappingCanvas.setBoldEdges(item.getState());
	}

	@SuppressWarnings("unused")
	private void doLiveUpdate(CommandEvent evt) {
		BCheckBoxMenuItem item = (BCheckBoxMenuItem) evt.getWidget();
		preview.setShowSelection(item.getState());
		manipulator.setLiveUpdate(item.getState());
	}

	@SuppressWarnings("unused")
	private void doRenameSelectedPiece() {
		int index = pieceList.getSelectedIndex();
		if (index < 0) {
			pieceList.setSelected(0, true);
			index = 0;
		}
		String oldName = mappingData.meshes[index].getName();
		BStandardDialog dlg = new BStandardDialog(
				Translate.text("polymesh:pieceName"), Translate
						.text("polymesh:enterPieceName"), BStandardDialog.QUESTION);
		String res = dlg.showInputDialog(this, null, oldName);
		if (res != null) {
			addUndoCommand(new RenamePieceCommand(index, oldName, res));
			setPieceName(index, res);
		}
	}

	/**
	 * Chnages the name of a given mesh piece
	 * 
	 * @param piece
	 *                The index of the piece to change the name of
	 * @param newName
	 *                The piece new name
	 */
	public void setPieceName(int piece, String newName) {
		mappingData.meshes[piece].setName(newName);
		pieceList.replace(piece, newName);
	}

	/**
	 * Enables/disables menu items depending on available texture
	 * 
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
	 * 
	 */
	private void updateMappingMenu() {
		sendTexToMappingMenu.removeAll();
		mappingMenuItems = new BCheckBoxMenuItem[mappingData.mappings.size()];
		for (int i = 0; i < mappingData.mappings.size(); i++) {
			sendTexToMappingMenu
					.add(mappingMenuItems[i] = new BCheckBoxMenuItem(
							mappingData.mappings.get(i).name, false));
			mappingMenuItems[i].addEventLink(CommandEvent.class, this,
					"doSendToMapping");
			if (mappingData.mappings.get(i) == currentMapping) {
				mappingMenuItems[i].setState(true);
			}
		}
	}

	/**
	 * Assigns a texture to a mapping
	 * 
	 * @param ev
	 *                The command event that identifies to which mapping the
	 *                texture must be assigned to
	 */
	@SuppressWarnings("unused")
	private void doSendToMapping(CommandEvent ev) {
		BCheckBoxMenuItem item = (BCheckBoxMenuItem) ev.getWidget();
		UVMeshMapping newMapping = null;
		int from = -1;
		int to = -1;
		for (int i = 0; i < mappingMenuItems.length; i++) {
			if (item == mappingMenuItems[i]) {
				to = i;
			} else if (mappingData.mappings.get(i) == currentMapping) {
				from = i;
			}
		}
		SendTextureToMappingCommand cmd = new SendTextureToMappingCommand(
				texList.get(currentTexture).getID(), from, to);
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
					if (t < 0) {
						mapping.textures.remove(j);
					}
				}
			}
		}
	}

	private int getTextureFromID(Integer id) {
		if (texList == null || texList.size() == 0) {
			return -1;
		}
		for (int i = 0; i < texList.size(); i++) {
			if (texList.get(i).getID() == id) {
				return i;
			}
		}
		return -1;
	}

	@SuppressWarnings("unused")
	private void doMappingChanged() {
		int index = mappingCB.getSelectedIndex();
		if (mappingData.mappings.get(index) != currentMapping) {
			int oldMapping = -1;
			for (int i = 0; i < mappingData.mappings.size(); i++) {
				if (mappingData.mappings.get(i) == currentMapping) {
					oldMapping = i;
				}
			}
			ChangeMappingCommand cmd = new ChangeMappingCommand(oldMapping,
					index);
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
		} else {
			currentTexture = -1;
		}
		if (currentTexture >= 0) {
			mappingCanvas.setTexture(texList.get(currentTexture), mappingList
					.get(currentTexture));
		} else {
			mappingCanvas.setTexture(null, null);
		}
		mappingCanvas.setMapping(currentMapping);
		updateState();
	}

	@SuppressWarnings("unused")
	private void doTextureChanged() {
		if (currentTexture == textureCB.getSelectedIndex()) {
			return;
		}
		int oldTexture = currentTexture;
		currentTexture = textureCB.getSelectedIndex();
		mappingCanvas.setTexture(texList.get(currentTexture), mappingList
				.get(currentTexture));
		ChangeTextureCommand cmd = new ChangeTextureCommand(oldTexture,
				currentTexture);
		addUndoCommand(cmd);
		updateState();
	}

	@SuppressWarnings("unused")
	private void doRemoveMapping() {
		if (mappingData.mappings.size() == 1) {
			return;
		}
		ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
		for (int i = 0; i < mappings.size(); i++) {
			if (mappings.get(i) == currentMapping) {
				RemoveMappingCommand cmd = new RemoveMappingCommand(
						currentMapping, i);
				addUndoCommand(cmd);
				cmd.execute();
				break;
			}
		}
	}

	@SuppressWarnings("unused")
	private void doFitMappingToImage() {
		MappingPositionsCommand cmd = mappingCanvas.new MappingPositionsCommand();
		cmd.setOldPos(currentMapping.v);
		Range range = mappingCanvas.getRange();
		cmd.setOldRange(range.umin, range.umax, range.vmin, range.vmax);
		double xmin = Double.MAX_VALUE;
		double xmax = -Double.MAX_VALUE;
		double ymin = Double.MAX_VALUE;
		double ymax = -Double.MAX_VALUE;
		for (int i = 0; i < currentMapping.v.length; i++) {
			for (int j = 0; j < currentMapping.v[i].length; j++) {
				if (mappingData.meshes[i].vertices[j].id == -1)
					continue;
				if (currentMapping.v[i][j].x < xmin) {
					xmin = currentMapping.v[i][j].x;
				}
				if (currentMapping.v[i][j].x > xmax) {
					xmax = currentMapping.v[i][j].x;
				}
				if (currentMapping.v[i][j].y < ymin) {
					ymin = currentMapping.v[i][j].y;
				}
				if (currentMapping.v[i][j].y > ymax) {
					ymax = currentMapping.v[i][j].y;
				}
			}
		}
		if (xmin == xmax || ymin == ymax) {
			return;
		}
		double uscale = (0.9) / (xmax - xmin);
		double vscale = (0.9) / (ymax - ymin);
		if (uscale < vscale) {
			vscale = uscale;
		} else {
			uscale = vscale;
		}
		for (int i = 0; i < currentMapping.v.length; i++) {
			for (int j = 0; j < currentMapping.v[i].length; j++) {
				currentMapping.v[i][j].x = (currentMapping.v[i][j].x - xmin)
						* uscale + 0.05;
				currentMapping.v[i][j].y = (currentMapping.v[i][j].y - ymin)
						* vscale + 0.05;
			}
		}
		cmd.setNewPos(currentMapping.v);
		cmd.setNewRange(0, 1, 0, 1);
		addUndoCommand(cmd);
		mappingCanvas.setRange(0, 1, 0, 1);
		mappingCanvas.repaint();
	}

	@SuppressWarnings("unused")
	private void doAddMapping() {
		int sel = mappingCB.getSelectedIndex();
		addMapping(false);
		addUndoCommand(new AddMappingCommand(currentMapping, sel));
	}

	@SuppressWarnings("unused")
	private void doDuplicateMapping() {
		int sel = mappingCB.getSelectedIndex();
		addMapping(true);
		addUndoCommand(new AddMappingCommand(currentMapping, sel));
	}

	/**
	 * Adds another mapping to the available mappings.
	 * 
	 * @param duplicate
	 *                Use default vertices positions or duplicate current
	 *                mapping
	 */
	private void addMapping(boolean duplicate) {
		BStandardDialog dlg = new BStandardDialog(Translate
				.text("polymesh:addMapping"), Translate.text("polymesh:enterMappingName"),
				BStandardDialog.QUESTION);
		String res = dlg.showInputDialog(this, null, Translate
				.text("polymesh:mapping")
				+ " #" + (mappingData.mappings.size() + 1));
		if (res != null) {
			UVMeshMapping mapping = null;
			if (duplicate) {
				mapping = mappingData.addNewMapping(res, currentMapping);
			} else {
				mapping = mappingData.addNewMapping(res, null);
			}
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

	@SuppressWarnings("unused")
	private void doShowSelection(CommandEvent evt) {
		BCheckBoxMenuItem item = (BCheckBoxMenuItem) evt.getWidget();
		preview.setShowSelection(item.getState());
		mappingCanvas.setSelection(mappingCanvas.getSelection());
	}

	@SuppressWarnings("unused")
	private void doSelectAll() {
		mappingCanvas.selectAll();
	}

	@SuppressWarnings("unused")
	private void doSamplingChanged() {
		mappingCanvas.setSampling(((Integer) resSpinner.getValue()).intValue());
	}

	@SuppressWarnings("unused")
	private void doExportImage() {
		ExportImageDialog dlg = new ExportImageDialog(500, 500);
		if (!dlg.clickedOk || dlg.file == null) {
			return;
		}
		File f = dlg.file;
		BufferedImage offscreen = new BufferedImage(dlg.width, dlg.height,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D offscreenGraphics = (Graphics2D) offscreen.getGraphics();
		mappingCanvas.drawOnto(offscreenGraphics, dlg.width, dlg.height);
		try {
			BufferedOutputStream bos = new BufferedOutputStream(
					new FileOutputStream(f));
			DataOutputStream dos = new DataOutputStream(bos);
			BMPEncoder bmp = new BMPEncoder(offscreen);
			bmp.writeImage(dos);
			dos.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unused")
	private void processMousePressed(WidgetMouseEvent ev) {
		if (mouseProcessor != null)
			mouseProcessor.stopProcessing();
		doMousePressed(ev);
		mouseProcessor = new ActionProcessor();
	}

	@SuppressWarnings("unused")
	private void processMouseDragged(final WidgetMouseEvent ev) {
		if (mouseProcessor != null)
			mouseProcessor.addEvent(new Runnable() {
				public void run() {
					doMouseDragged(ev);
				}
			});
	}

	@SuppressWarnings("unused")
	private void processMouseMoved(final WidgetMouseEvent ev) {
		if (mouseProcessor != null)
			mouseProcessor.addEvent(new Runnable() {
				public void run() {
					doMouseMoved(ev);
				}
			});
	}

	@SuppressWarnings("unused")
	private void processMouseReleased(WidgetMouseEvent ev) {
		if (mouseProcessor != null) {
			mouseProcessor.stopProcessing();
			mouseProcessor = null;
			doMouseReleased(ev);
		}
	}

	@SuppressWarnings("unused")
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

	@SuppressWarnings("unused")
	private void doOk() {
		clickedOk = true;
		dispose();
	}

	@SuppressWarnings("unused")
	private void doCancel() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		if (texList != null) {
			for (int i = 0; i < texList.size(); i++) {
				mappingList.get(i).setFaceTextureCoordinates(mesh,
						oldCoordList.get(i));
			}
		}
		mesh.setMappingData(oldMappingData);
		dispose();
	}

	@SuppressWarnings("unused")
	private void doPieceListSelection() {
		if (mappingCanvas.getSelectedPiece() == pieceList.getSelectedIndex()) {
			return;
		}
		addUndoCommand(new SelectPieceCommand(mappingCanvas.getSelectedPiece(),
				pieceList.getSelectedIndex()));
		mappingCanvas.setSelectedPiece(pieceList.getSelectedIndex());
	}

	/**
	 * Works out a default layout when the unfolded meshes are displayed for
	 * the first time. Call is forwarded to
	 * UVMappingCanvas.initializeMeshLayout().
	 * 
	 */
	public void initializeMeshLayout() {
		mappingCanvas.resetMeshLayout();
	}

	public void displayUVMinMax(double umin, double umax, double vmin,
			double vmax) {
		DecimalFormat format = new DecimalFormat();
		format.setMaximumFractionDigits(2);
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

	@SuppressWarnings("unused")
	//for debugging purposes
	private void dumpTextureIDs() {
		ArrayList<UVMeshMapping> mappings = mappingData.getMappings();
		UVMeshMapping mapping;
		for (int i = 0; i < mappings.size(); i++) {
			mapping = mappings.get(i);
			System.out.print(i + ": ");
			for (int j = 0; j < mapping.textures.size(); j++) {
				System.out.print(mapping.textures.get(j) + " ");
			}
			System.out.println("");
		}
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

	@SuppressWarnings("unused")
	private void doTensionChanged() {
		tension = meshTensionCB.getState();
		if (tension) {
			mappingCanvas.findSelectionDistance();
		}
	}

	@SuppressWarnings("unused")
	private void doTensionValueChanged() {
		tensionValue = tensionCB.getSelectedIndex();
	}

	@SuppressWarnings("unused")
	private void doMaxDistanceValueChanged() {
		tensionDistance = ((Integer) distanceSpinner.getValue()).intValue();
		mappingCanvas.findSelectionDistance();
	}

	@SuppressWarnings("unused")
	private void doAutoScale() {
		mappingCanvas.resetMeshLayout();
		mappingCanvas.repaint();
	}
}
