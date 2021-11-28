/*
 *  Copyright (C) 2006-2007 by Francois Guillet
 *  Changes copyright (C) 2022 by Maksim Khramov
 *
 *  This program is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 2 of the License, or (at your option) any later version.
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */
package artofillusion.polymesh;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JSpinner.NumberEditor;

import artofillusion.ArtOfIllusion;
import artofillusion.Camera;
import artofillusion.LayoutWindow;
import artofillusion.MeshEditorWindow;
import artofillusion.MeshViewer;
import artofillusion.MoveViewTool;
import artofillusion.RenderingMesh;
import artofillusion.RotateViewTool;

import artofillusion.SkewMeshTool;
import artofillusion.TaperMeshTool;
import artofillusion.TextureParameter;
import artofillusion.ThickenMeshTool;
import artofillusion.UndoRecord;
import artofillusion.ViewerCanvas;
import artofillusion.animation.Joint;
import artofillusion.animation.Skeleton;
import artofillusion.animation.SkeletonTool;
import artofillusion.keystroke.KeystrokeManager;
import artofillusion.keystroke.KeystrokePreferencesPanel;
import artofillusion.keystroke.KeystrokeRecord;
import artofillusion.math.CoordinateSystem;
import artofillusion.math.Vec3;
import artofillusion.object.Curve;
import artofillusion.object.Mesh;
import artofillusion.object.MeshVertex;
import artofillusion.object.Object3D;
import artofillusion.object.ObjectInfo;
import artofillusion.object.TriangleMesh;
import artofillusion.polymesh.PolyMesh.Wedge;
import artofillusion.polymesh.PolyMesh.Wface;
import artofillusion.polymesh.PolyMesh.Wvertex;
import artofillusion.polymesh.PolyMeshValueWidget.ValueWidgetOwner;
import artofillusion.polymesh.ui.ColorButton;
import artofillusion.texture.FaceParameterValue;
import artofillusion.texture.VertexParameterValue;

import artofillusion.texture.ParameterValue;
import artofillusion.ui.ActionProcessor;
import artofillusion.ui.ComponentsDialog;
import artofillusion.ui.EditingTool;
import artofillusion.ui.EditingWindow;
import artofillusion.ui.GenericTool;
import artofillusion.ui.PanelDialog;
import artofillusion.ui.PopupMenuManager;
import artofillusion.ui.ToolPalette;
import artofillusion.ui.Translate;
import artofillusion.ui.UIUtilities;
import artofillusion.ui.ValueField;
import artofillusion.ui.ValueSlider;
import buoy.event.CommandEvent;
import buoy.event.EventSource;
import buoy.event.KeyPressedEvent;
import buoy.event.ValueChangedEvent;
import buoy.event.WidgetEvent;
import buoy.event.WidgetMouseEvent;
import buoy.event.WindowClosingEvent;
import buoy.widget.BButton;
import buoy.widget.BCheckBox;
import buoy.widget.BCheckBoxMenuItem;
import buoy.widget.BDialog;
import buoy.widget.BFileChooser;
import buoy.widget.BFrame;
import buoy.widget.BLabel;
import buoy.widget.BMenu;
import buoy.widget.BMenuItem;
import buoy.widget.BPopupMenu;
import buoy.widget.BProgressBar;
import buoy.widget.BSlider;
import buoy.widget.BSpinner;
import buoy.widget.BStandardDialog;
import buoy.widget.BTextArea;
import buoy.widget.BTextField;
import buoy.widget.BorderContainer;
import buoy.widget.ColumnContainer;
import buoy.widget.FormContainer;
import buoy.widget.GridContainer;
import buoy.widget.LayoutInfo;
import buoy.widget.MenuWidget;
import buoy.widget.OverlayContainer;
import buoy.widget.RowContainer;
import buoy.widget.Shortcut;
import buoy.widget.Widget;
import buoy.xml.WidgetDecoder;

/**
 * The PolyMeshEditorWindow class represents the window for editing PolyMesh
 * objects.
 * 
 * @author Francois Guillet
 */

public class PolyMeshEditorWindow extends MeshEditorWindow implements
		EditingWindow, PopupMenuManager, ValueWidgetOwner {
	private Scene scene;

	private ToolPalette modes;

	private Runnable onClose;

	private BMenu editMenu;

	private BMenuItem[] editMenuItem;

	private BMenuItem[] meshMenuItem;

	private BCheckBoxMenuItem[] smoothItem;

	private BMenuItem[] mirrorItem;

	private BMenu vertexMenu;

	private BPopupMenu vertexPopupMenu;

	private MenuWidget[] vertexMenuItem;

	private MenuWidget[] vertexPopupMenuItem;

	private BMenu edgeMenu;

	private BPopupMenu edgePopupMenu;

	private MenuWidget[] edgeMenuItem;

	private MenuWidget[] edgePopupMenuItem;

	private BMenuItem[] divideMenuItem, popupDivideMenuItem;

	private BMenu faceMenu;

	private BPopupMenu facePopupMenu;

	private MenuWidget[] faceMenuItem;

	private MenuWidget[] facePopupMenuItem;

	private BMenu skeletonMenu;

	private MenuWidget[] skeletonMenuItem;

	private BMenuItem pasteItem;

	private RowContainer levelContainer;

	private RowContainer vertexContainer;

	private RowContainer edgeContainer;

	private RowContainer faceContainer;

	private OverlayContainer overlayVertexEdgeFace;

	private BSpinner tensionSpin, ispin;

	private ValueSlider edgeSlider;

	private BCheckBox cornerCB;

	private EditingTool reshapeMeshTool;

	public final static int RESHAPE_TOOL = 0;

	private EditingTool altTool;

	private EditingTool skewMeshTool;

	public final static int SKEW_TOOL = 1;

	private EditingTool taperMeshTool;

	public final static int TAPER_TOOL = 2;

	private EditingTool bevelTool;

	public final static int BEVEL_TOOL = 3;

	private EditingTool thickenMeshTool;

	public final static int THICKEN_TOOL = 4;

	private EditingTool extrudeTool;

	public final static int EXTRUDE_TOOL = 5;

	private EditingTool knifeTool;

	public final static int KNIFE_TOOL = 6;

	private EditingTool createFaceTool;

	public final static int CREATE_FACE_TOOL = 7;

	private EditingTool extrudeCurveTool;

	public final static int EXTRUDE_CURVE_TOOL = 8;

	private EditingTool sewTool;

	public final static int SEW_TOOL = 9;

	private EditingTool skeletonTool;

	public final static int SKELETON_TOOL = 10;

	private boolean realView;

	private boolean realMirror;

	private int smoothingMethod;

	private PolyMesh priorValueMesh;

	private boolean[] valueSelection;

	private short moveDirection;


	private PolyMeshValueWidget valueWidget;
	
	private BDialog valueWidgetDialog;

	private BMenuItem[] extrudeItem, extrudeEdgeItem;

	private BMenuItem[] extrudeRegionItem, extrudeEdgeRegionItem;

	private BMenuItem[] popupExtrudeItem, popupExtrudeEdgeItem;

	private BMenuItem[] popupExtrudeRegionItem, popupExtrudeEdgeRegionItem;

	private Vec3 direction;

	private int selectionDistance[], maxDistance, selectMode;

	private int projectedEdge[];

	private boolean selected[];

	private Vec3 vertDisplacements[];

	private static EventSource eventSource;

	private static PolyMesh clipboardMesh;

	protected boolean tolerant;
	

	private TextureParameter faceIndexParam, jointWeightParam;

	protected boolean hideFace[];

	protected boolean hideVert[];
	
	protected boolean hideEdge[];

	protected boolean[] selPoints;

	protected Vec3 selCenter;

	protected double meanSelDistance;

	private boolean projectOntoSurface;

	private static double normalTol = 0.01;

	private static double looseShapeTol = 0.01;

	private static double strictShapeTol = 0.01;

	private static double edgeTol = 0.01;

	protected static boolean lastFreehand, lastProjectOntoSurface,
			lastTolerant;

	private RenderingMesh lastPreview;

	private GenericTool pointTool, edgeTool, faceTool;

	private short mirror;

	private boolean thickenFaces;

	private Shortcut singleNormalShortcut, groupNormalShortcut;

	private static boolean selectVisible = false;

	private static boolean looseSelect = false;

	private static int looseSelectValue = 20;

	private BCheckBox frontSelectCB;

	private BCheckBox looseSelectCB;

	private BSpinner looseSelectSpinner;
	
	private FormContainer propertiesPanel;

	private boolean unseenValueWidgetDialog;

	private BMenuItem[] ringMenuItem;

	private BMenuItem[] popupRingMenuItem;

	/**
	 * Constructor for the PolyMeshEditorWindow object
	 * 
	 * @param parent
	 *                the window from which this command is being invoked
	 * @param title
	 *                Window title
	 * @param obj
	 *                the ObjectInfo corresponding to this object
	 * @param onClose
	 *                a callback which will be executed when editing is over
	 */
	public PolyMeshEditorWindow(EditingWindow parent, String title, ObjectInfo obj, Runnable onClose) {
		super(parent, title, obj);
		
		PolyMesh mesh = (PolyMesh) objInfo.object;
		if (eventSource == null) eventSource = new EventSource();
		eventSource.addEventLink(CopyEvent.class, this, "doCopyEvent");
		hideVert = new boolean[mesh.getVertices().length];
		this.onClose = onClose;
		NumberFormat format = NumberFormat.getInstance();
		format.setMaximumFractionDigits(3);
		FormContainer content = new FormContainer(new double[] { 0, 1, 0 },
				new double[] { 1, 0, 0, 0 });
		setContent(content);
		valueWidget = new PolyMeshValueWidget(this);
		valueWidgetDialog = new BDialog(this,  "choose value", true);
		valueWidgetDialog.setContent(valueWidget);
		valueWidgetDialog.pack();
		unseenValueWidgetDialog = true;
		//content.add(valueWidget = new PolymeshValueWidget(), 2, 0, 1, 1);
		content.setDefaultLayout(new LayoutInfo(LayoutInfo.CENTER,
				LayoutInfo.BOTH, null, null));
		BorderContainer widgets = new BorderContainer();
		RowContainer meshContainer = new RowContainer();
		levelContainer = new RowContainer();
		vertexContainer = new RowContainer();
		edgeContainer = new RowContainer();
		faceContainer = new RowContainer();
		meshContainer.add(looseSelectCB = new BCheckBox(Translate
				.text("polymesh:looseSelect"), looseSelect));
		looseSelectCB.addEventLink(ValueChangedEvent.class, this,
				"doLooseSelectionChanged");
		meshContainer.add(looseSelectSpinner = new BSpinner(looseSelectValue,
				1, 100, 1));
		looseSelectSpinner.addEventLink(ValueChangedEvent.class, this,
				"doLooseSelectionValueChanged");
		meshContainer.add(frontSelectCB = new BCheckBox(Translate
				.text("polymesh:frontSelect"), selectVisible));
		frontSelectCB.addEventLink(ValueChangedEvent.class, this,
				"doFrontSelectionChanged");
		meshContainer.add(new BLabel(Translate.text("polymesh:meshTension") + ": "));
		tensionSpin = new BSpinner(tensionDistance, 0, 999, 1);
		setSpinnerColumns(tensionSpin, 3);
		tensionSpin.addEventLink(ValueChangedEvent.class, this,
				"doTensionChanged");
		meshContainer.add(tensionSpin);
//		levelContainer.add(new BLabel(Translate.text("polymesh:subdivisionLevels")
//				+ ": "));
		levelContainer.add(new BLabel(Translate.text("polymesh:interactiveSubdiv")));
		ispin = new BSpinner(1, 1, 6, 1);
		levelContainer.add(ispin);
		ispin.setValue(new Integer(mesh.getInteractiveSmoothLevel()));
		ispin.addEventLink(ValueChangedEvent.class, this, "doInteractiveLevel");
		//levelContainer.add(new BLabel(Translate.text("polymesh:render")));
		//rspin = new BSpinner(1, 1, 6, 1);
		//rspin.setValue(new Integer(mesh.getRenderingSmoothLevel()));
		//levelContainer.add(rspin);
		//rspin.addEventLink(ValueChangedEvent.class, this, "doRenderingLevel");
		meshContainer.add(levelContainer);
		cornerCB = new BCheckBox(Translate.text("polymesh:corner"), false);
		cornerCB.addEventLink(ValueChangedEvent.class, this, "doCornerChanged");
		vertexContainer.add(cornerCB);
		edgeSlider = new ValueSlider(0.0, 1.0, 1000, 0.0);
		edgeSlider.addEventLink(ValueChangedEvent.class, this,
				"doEdgeSliderChanged");
		edgeContainer.add(new BLabel(Translate.text("polymesh:smoothness")));
		edgeContainer.add(edgeSlider);
		overlayVertexEdgeFace = new OverlayContainer();
		overlayVertexEdgeFace.add(faceContainer);
		overlayVertexEdgeFace.add(edgeContainer);
		overlayVertexEdgeFace.add(vertexContainer);
		widgets.add(overlayVertexEdgeFace, BorderContainer.WEST);
		widgets.add(meshContainer, BorderContainer.EAST);
		content.add(widgets, 0, 1, 3, 1);
		content.add(helpText = new BLabel(), 0, 2, 3, 1);
		content.add(viewsContainer, 1, 0);
		RowContainer buttons = new RowContainer();
		buttons.add(Translate.button("ok", this, "doOk"));
		buttons.add(Translate.button("cancel", this, "doCancel"));
		content.add(buttons, 0, 3, 2, 1, new LayoutInfo());
		FormContainer toolsContainer = new FormContainer(new double[] { 1 },
				new double[] { 1, 0 });
		toolsContainer.setDefaultLayout(new LayoutInfo(LayoutInfo.NORTH,
				LayoutInfo.BOTH));
		content.add(toolsContainer, 0, 0);
		toolsContainer.add(tools = new ToolPalette(1, 12), 0, 0);
		tools.addTool(defaultTool = reshapeMeshTool = new MeshStandardTool(
				this, this));
		tools.addTool(skewMeshTool = new SkewMeshTool(this, this));
		tools.addTool(taperMeshTool = new TaperMeshTool(this, this));
		tools.addTool(bevelTool = new AdvancedBevelExtrudeTool(this, this));
		tools.addTool(extrudeTool = new AdvancedExtrudeTool(this, this));
		tools.addTool(thickenMeshTool = new ThickenMeshTool(this, this));
		tools.addTool(knifeTool = new PMKnifeTool(this, this));
		tools.addTool(createFaceTool = new PMCreateFaceTool(this, this));
		tools.addTool(extrudeCurveTool = new PMExtrudeCurveTool(this, this));
		tools.addTool(sewTool = new PMSewTool(this, this));
		tools.addTool(skeletonTool = new SkeletonTool(this, true));
		EditingTool metaTool;
		tools.addTool(metaTool = new MoveViewTool(this));
		tools.addTool(altTool = new RotateViewTool(this));
		tools.selectTool(defaultTool);
		loadPreferences();
		for (int i = 0; i < theView.length; i++) {
			MeshViewer view = (MeshViewer) theView[i];
			view.setMetaTool(metaTool);
			view.setAltTool(altTool);
			view.setScene(parent.getScene(), obj);
			view.setFreehandSelection(lastFreehand);
			view.setPopupMenuManager(this);
		}
		tolerant = lastTolerant;
		projectOntoSurface = lastProjectOntoSurface;
		toolsContainer.add(modes = new ToolPalette(1, 3), 0, 1);
		modes.addTool(pointTool = new GenericTool(this, "polymesh:pmpoint",
			    Translate.text("pointSelectionModeTool.tipText")));
		modes.addTool(edgeTool = new GenericTool(this, "polymesh:pmedge",
				Translate.text("edgeSelectionModeTool.tipText")));
		modes.addTool(faceTool = new GenericTool(this, "polymesh:pmface",
				Translate.text("faceSelectionModeTool.tipText")));
		setSelectionMode(modes.getSelection());
		UIUtilities.applyDefaultFont(content);
		UIUtilities.applyDefaultBackground(content);
		createPrefsMenu();
		createEditMenu();
		createMeshMenu((PolyMesh) objInfo.object);
		createVertexMenu();
		createEdgeMenu();
		createFaceMenu();
		createSkeletonMenu((PolyMesh) objInfo.object);
		createTextureMenu();
		createViewMenu();
		recursivelyAddListeners(this);
		Dimension d1 = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension d2;
		d2 = new Dimension((d1.width * 3) / 4, (d1.height * 3) / 4);
		setBounds(new Rectangle((d1.width - d2.width) / 2,
				(d1.height - d2.height) / 2, d2.width, d2.height));
		tools.requestFocus();
		updateMenus();
		realView = false;
		realMirror = false;
		addExtraParameters();
		doLevelContainerEnable();
		selected = new boolean[((Mesh) objInfo.object).getVertices().length];
		// addEventLink( WindowClosingEvent.class, this, "doCancel" );

		overlayVertexEdgeFace.setVisibleChild(vertexContainer);
	}

	/**
	 * Builds the edit menu
	 * 
	 */
	private void createEditMenu() {
		editMenu = Translate.menu("edit");
		menubar.add(editMenu);
		editMenuItem = new BMenuItem[12];
		editMenu.add(undoItem = Translate.menuItem("undo", this, "undoCommand"));
		editMenu.add(redoItem = Translate.menuItem("redo", this, "redoCommand"));
		editMenu.addSeparator();
		editMenu.add(Translate.menuItem("polymesh:copy", this, "doCopy"));
		editMenu.add(pasteItem = Translate.menuItem("polymesh:paste", this, "doPaste"));
		if (clipboardMesh == null)
			pasteItem.setEnabled(false);
		editMenu.addSeparator();
                editMenu.add(editMenuItem[0] = Translate.menuItem("clear", this, "deleteCommand"));
                editMenu.add(editMenuItem[1] = Translate.menuItem("selectAll", this, "selectAllCommand"));
                editMenu.add(editMenuItem[2] = Translate.menuItem("polymesh:showNormal", this, "bringNormal"));
                editMenu.add(editMenuItem[3] = Translate.menuItem("extendSelection", this, "extendSelectionCommand"));
                editMenu.add(Translate.menuItem("invertSelection", this, "invertSelectionCommand"));
                editMenu.add(editMenuItem[4] = Translate.menuItem("polymesh:scaleSelection", this, "scaleSelectionCommand"));
                editMenu.add(editMenuItem[5] = Translate.menuItem("polymesh:scaleNormal", this, "scaleNormalSelectionCommand"));
                editMenu.add(editMenuItem[6] = Translate.checkboxMenuItem("tolerantSelection", this, "tolerantModeChanged", lastTolerant));
                editMenu.add(editMenuItem[7] = Translate.checkboxMenuItem("freehandSelection", this, "freehandModeChanged", lastFreehand));
                editMenu.add(editMenuItem[8] = Translate.checkboxMenuItem("projectOntoSurface", this, "projectModeChanged", lastProjectOntoSurface));
		editMenu.addSeparator();
		editMenu.add(editMenuItem[9] = Translate.menuItem("hideSelection", this, "doHideSelection"));
		editMenu.add(editMenuItem[10] = Translate.menuItem("showAll", this, "doShowAll"));
		editMenu.addSeparator();
		editMenu.add(Translate.menuItem("polymesh:editDisplayProperties", this, "doEditProperties"));
	}

	/**
	 * Builds the mesh menu
	 * 
	 * @param obj
	 *                The winged mesh being edited
	 */
	void createMeshMenu(PolyMesh obj) {
		PolyMesh mesh = (PolyMesh) objInfo.object;

		BMenu meshMenu = Translate.menu("mesh");
		menubar.add(meshMenu);
		meshMenuItem = new BMenuItem[5];
		BMenu smoothMenu;
		meshMenu.add(Translate.menuItem("polymesh:centerMesh", this, "doCenterMesh"));
		meshMenu.add(smoothMenu = Translate.menu("smoothingMethod"));
		smoothItem = new BCheckBoxMenuItem[2];
                smoothMenu.add(smoothItem[0] = Translate.checkboxMenuItem("none", this, "smoothingChanged", obj.getSmoothingMethod() == Mesh.NO_SMOOTHING));
                smoothMenu.add(smoothItem[1] = Translate.checkboxMenuItem("approximating", this, "smoothingChanged", obj.getSmoothingMethod() == Mesh.APPROXIMATING));

                meshMenu.add(meshMenuItem[0] = Translate.menuItem("polymesh:controlledSmoothing", this, "doControlledSmoothing"));
                meshMenu.add(meshMenuItem[1] = Translate.menuItem("polymesh:smoothMesh", this, "doSmoothMesh"));
                meshMenu.add(meshMenuItem[2] = Translate.menuItem("polymesh:subdivideMesh", this, "doSubdivideMesh"));
                meshMenu.add(meshMenuItem[3] = Translate.menuItem("polymesh:thickenMeshFaceNormal", this, "doThickenMesh"));
                meshMenu.add(meshMenuItem[4] = Translate.menuItem("polymesh:thickenMeshVertexNormal", this, "doThickenMesh"));
		BMenu mirrorMenu;
		meshMenu.add(mirrorMenu = Translate.menu("polymesh:mirrorMesh"));
		mirrorItem = new BMenuItem[4];
		mirrorMenu.add(mirrorItem[0] = Translate.menuItem("polymesh:mirrorOff", this, "doMirrorOff"));
		mirrorMenu.add(mirrorItem[1] = Translate.checkboxMenuItem( "polymesh:mirrorOnXY", this, "doMirrorOn", false));
		mirrorMenu.add(mirrorItem[2] = Translate.checkboxMenuItem("polymesh:mirrorOnXZ", this, "doMirrorOn", false));
		mirrorMenu.add(mirrorItem[3] = Translate.checkboxMenuItem("polymesh:mirrorOnYZ", this, "doMirrorOn", false));
		if ((mesh.getMirrorState() & PolyMesh.MIRROR_ON_XY) != 0)
			((BCheckBoxMenuItem) mirrorItem[1]).setState(true);
		if ((mesh.getMirrorState() & PolyMesh.MIRROR_ON_XZ) != 0)
			((BCheckBoxMenuItem) mirrorItem[2]).setState(true);
		if ((mesh.getMirrorState() & PolyMesh.MIRROR_ON_YZ) != 0)
			((BCheckBoxMenuItem) mirrorItem[3]).setState(true);
		BMenu mirrorWholeMesh;
		meshMenu.add(mirrorWholeMesh = Translate.menu("polymesh:mirrorWholeMesh"));
		mirrorWholeMesh.add(Translate.menuItem("polymesh:mirrorOnXY", this, "doMirrorWholeXY"));
		mirrorWholeMesh.add(Translate.menuItem("polymesh:mirrorOnYZ", this, "doMirrorWholeYZ"));
		mirrorWholeMesh.add(Translate.menuItem("polymesh:mirrorOnXZ", this, "doMirrorWholeXZ"));
		meshMenu.add(Translate.menuItem("invertNormals", this, "doInvertNormals"));
		meshMenu.add(Translate.menuItem("meshTension", this, "setTensionCommand"));
		meshMenu.add(Translate.menuItem("polymesh:checkMesh", this, "doCheckMesh"));
		meshMenu.addSeparator();
                meshMenu.add(Translate.menuItem("polymesh:saveAsTemplate", this, "doSaveAsTemplate"));
	}

	/**
	 * Builds the vertex menu
	 */
	void createVertexMenu() {
                vertexMenu = Translate.menu("polymesh:vertex");
                menubar.add(vertexMenu);
                vertexMenuItem = new MenuWidget[16];
                vertexMenu.add(vertexMenuItem[0] = Translate.menuItem("polymesh:connect", this, "doConnectVertices"));
                vertexMenu.add(vertexMenuItem[1] = Translate.menu("polymesh:moveAlong"));
                ((BMenu) vertexMenuItem[1]).add(Translate.menuItem("polymesh:normal", this, "doMoveVerticesNormal"));
                ((BMenu) vertexMenuItem[1]).add(Translate.menuItem("polymesh:x", this, "doMoveVerticesX"));
                ((BMenu) vertexMenuItem[1]).add(Translate.menuItem("polymesh:y", this, "doMoveVerticesY"));
                ((BMenu) vertexMenuItem[1]).add(Translate.menuItem("polymesh:z", this, "doMoveVerticesZ"));
                vertexMenu.add(vertexMenuItem[2] = Translate.menuItem("polymesh:collapse", this, "doCollapseVertices"));
                vertexMenu.add(vertexMenuItem[3] = Translate.menuItem("polymesh:facet", this, "doFacetVertices"));
                vertexMenu.add(vertexMenuItem[4] = Translate.menuItem("polymesh:bevel", this, "doBevelVertices"));
                vertexMenu.addSeparator();
                vertexMenu.add(vertexMenuItem[5] = Translate.menuItem("polymesh:meanSphere", this, "doMeanSphere"));
                vertexMenu.add(vertexMenuItem[6] = Translate.menuItem("polymesh:closestSphere", this, "doClosestSphere"));
                vertexMenu.add(vertexMenuItem[7] = Translate.menuItem("polymesh:plane", this, "doPlane"));
                vertexMenu.addSeparator();
                vertexMenu.add(vertexMenuItem[8] = Translate.menuItem("polymesh:selectBoundary", this, "doSelectBoundary"));
                vertexMenu.add(vertexMenuItem[9] = Translate.menuItem("polymesh:closeBoundary", this, "doCloseBoundary"));
                vertexMenu.add(vertexMenuItem[10] = Translate.menuItem("polymesh:joinBoundaries", this, "doJoinBoundaries"));
                vertexMenu.addSeparator();
                vertexMenu.add(vertexMenuItem[11] = Translate.menuItem("editPoints", this, "setPointsCommand"));
                vertexMenu.add(vertexMenuItem[12] = Translate.menuItem("transformPoints", this, "transformPointsCommand"));
                vertexMenu.add(vertexMenuItem[13] = Translate.menuItem("randomize", this, "randomizeCommand"));
                vertexMenu.addSeparator();
                vertexMenu.add(vertexMenuItem[14] = Translate.menuItem("parameters", this, "setParametersCommand"));
                vertexMenu.addSeparator();
                vertexMenu.add(vertexMenuItem[15] = Translate.menuItem("polymesh:selectCorners", this, "doSelectCorners"));
                vertexPopupMenu = new BPopupMenu();
                vertexPopupMenuItem = new MenuWidget[16];
                vertexPopupMenu.add(vertexPopupMenuItem[0] = Translate.menuItem("polymesh:connect", this, "doConnectVertices"));
                vertexPopupMenu.add(vertexPopupMenuItem[1] = Translate.menu("polymesh:moveAlong"));
                ((BMenu) vertexPopupMenuItem[1]).add(Translate.menuItem("polymesh:normal", this, "doMoveVerticesNormal"));
                ((BMenu) vertexPopupMenuItem[1]).add(Translate.menuItem("polymesh:x", this, "doMoveVerticesX"));
                ((BMenu) vertexPopupMenuItem[1]).add(Translate.menuItem("polymesh:y", this, "doMoveVerticesY"));
                ((BMenu) vertexPopupMenuItem[1]).add(Translate.menuItem("polymesh:z", this, "doMoveVerticesZ"));
                vertexPopupMenu.add(vertexPopupMenuItem[2] = Translate.menuItem("polymesh:collapse", this, "doCollapseVertices"));
                vertexPopupMenu.add(vertexPopupMenuItem[3] = Translate.menuItem("polymesh:facet", this, "doFacetVertices"));
                vertexPopupMenu.add(vertexPopupMenuItem[4] = Translate.menuItem("polymesh:bevel", this, "doBevelVertices"));
                vertexPopupMenu.addSeparator();
                vertexPopupMenu.add(vertexPopupMenuItem[5] = Translate.menuItem("polymesh:meanSphere", this, "doMeanSphere"));
                vertexPopupMenu.add(vertexPopupMenuItem[6] = Translate.menuItem("polymesh:closestSphere", this, "doClosestSphere"));
                vertexPopupMenu.add(vertexPopupMenuItem[7] = Translate.menuItem("polymesh:plane", this, "doPlane"));
                vertexPopupMenu.addSeparator();
                vertexPopupMenu.add(vertexPopupMenuItem[8] = Translate.menuItem("polymesh:selectBoundary", this, "doSelectBoundary"));
                vertexPopupMenu.add(vertexPopupMenuItem[9] = Translate.menuItem("polymesh:closeBoundary", this, "doCloseBoundary"));
                vertexPopupMenu.add(vertexPopupMenuItem[10] = Translate.menuItem("polymesh:joinBoundaries", this, "doJoinBoundaries"));
                vertexPopupMenu.addSeparator();
                vertexPopupMenu.add(vertexPopupMenuItem[11] = Translate.menuItem("editPoints", this, "setPointsCommand"));
                vertexPopupMenu.add(vertexPopupMenuItem[12] = Translate.menuItem("transformPoints", this, "transformPointsCommand"));
                vertexPopupMenu.add(vertexPopupMenuItem[13] = Translate.menuItem("randomize", this, "randomizeCommand"));
                vertexPopupMenu.addSeparator();
                vertexPopupMenu.add(vertexPopupMenuItem[14] = Translate.menuItem("parameters", this, "setParametersCommand"));
                vertexPopupMenu.addSeparator();
                vertexPopupMenu.add(vertexPopupMenuItem[15] = Translate.menuItem("polymesh:selectCorners", this, "doSelectCorners"));

	}

	/**
	 * Builds the edge menu
	 * 
	 */
	void createEdgeMenu() {
		edgeMenu = Translate.menu("polymesh:edge");
		menubar.add(edgeMenu);
		edgeMenuItem = new MenuWidget[22];
		edgeMenu.add(edgeMenuItem[0] = Translate.menu("polymesh:divide"));
		divideMenuItem = new BMenuItem[5];
		((BMenu) edgeMenuItem[0]).add(divideMenuItem[0] = Translate.menuItem("polymesh:two", this, "doDivideEdges"));
		((BMenu) edgeMenuItem[0]).add(divideMenuItem[1] = Translate.menuItem("polymesh:three", this, "doDivideEdges"));
		((BMenu) edgeMenuItem[0]).add(divideMenuItem[2] = Translate.menuItem("polymesh:four", this, "doDivideEdges"));
		((BMenu) edgeMenuItem[0]).add(divideMenuItem[3] = Translate.menuItem("polymesh:five", this, "doDivideEdges"));
		((BMenu) edgeMenuItem[0]).add(divideMenuItem[4] = Translate.menuItem("polymesh:specify", this, "doDivideEdges"));
		edgeMenu.add(edgeMenuItem[1] = Translate.menu("polymesh:moveAlong"));
		((BMenu) edgeMenuItem[1]).add(Translate.menuItem("polymesh:normal", this,"doMoveEdgesNormal"));
		((BMenu) edgeMenuItem[1]).add(Translate.menuItem("polymesh:x", this,"doMoveEdgesX"));
		((BMenu) edgeMenuItem[1]).add(Translate.menuItem("polymesh:y", this,"doMoveEdgesY"));
		((BMenu) edgeMenuItem[1]).add(Translate.menuItem("polymesh:z", this,"doMoveEdgesZ"));
		edgeMenu.addSeparator();
		edgeMenu.add(edgeMenuItem[2] = Translate.menu("polymesh:extrude"));
		extrudeEdgeItem = new BMenuItem[4];
		extrudeEdgeRegionItem = new BMenuItem[4];
		((BMenu) edgeMenuItem[2]).add(extrudeEdgeItem[0] = Translate.menuItem("polymesh:extrudeNormal", this, "doExtrudeEdge"));
		((BMenu) edgeMenuItem[2]).add(extrudeEdgeItem[1] = Translate.menuItem("polymesh:xExtrude", this, "doExtrudeEdge"));
		((BMenu) edgeMenuItem[2]).add(extrudeEdgeItem[2] = Translate.menuItem("polymesh:yExtrude", this, "doExtrudeEdge"));
		((BMenu) edgeMenuItem[2]).add(extrudeEdgeItem[3] = Translate.menuItem("polymesh:zExtrude", this, "doExtrudeEdge"));
		singleNormalShortcut = extrudeEdgeItem[0].getShortcut();
		edgeMenu.add(edgeMenuItem[3] = Translate.menu("polymesh:extrudeRegion"));
		((BMenu) edgeMenuItem[3]).add(extrudeEdgeRegionItem[0] = Translate.menuItem("polymesh:extrudeRegionNormal", this, "doExtrudeEdgeRegion"));
		((BMenu) edgeMenuItem[3]).add(extrudeEdgeRegionItem[1] = Translate.menuItem("polymesh:xExtrude", this, "doExtrudeEdgeRegion"));
		((BMenu) edgeMenuItem[3]).add(extrudeEdgeRegionItem[2] = Translate.menuItem("polymesh:yExtrude", this, "doExtrudeEdgeRegion"));
		((BMenu) edgeMenuItem[3]).add(extrudeEdgeRegionItem[3] = Translate.menuItem("polymesh:zExtrude", this, "doExtrudeEdgeRegion"));
		groupNormalShortcut = extrudeEdgeRegionItem[0].getShortcut();
		edgeMenu.addSeparator();
		edgeMenu.add(edgeMenuItem[4] = Translate.menuItem("polymesh:collapse", this,"doCollapseEdges"));
		edgeMenu.add(edgeMenuItem[5] = Translate.menuItem("polymesh:merge", this,"doMergeEdges"));
		edgeMenu.add(edgeMenuItem[6] = Translate.menuItem("polymesh:bevel", this,"doBevelEdges"));
		edgeMenu.addSeparator();
		edgeMenu.add(edgeMenuItem[7] = Translate.menuItem("polymesh:selectLoop", this,"doSelectLoop"));
		edgeMenu.add(edgeMenuItem[8] = Translate.menu("polymesh:selectRing"));
		ringMenuItem = new BMenuItem[6];
		((BMenu) edgeMenuItem[8]).add(ringMenuItem[0] = Translate.menuItem("polymesh:all", this, "doSelectRing"));
		((BMenu) edgeMenuItem[8]).add(ringMenuItem[1] = Translate.menuItem("polymesh:two", this, "doSelectRing"));
		((BMenu) edgeMenuItem[8]).add(ringMenuItem[2] = Translate.menuItem("polymesh:three", this, "doSelectRing"));
		((BMenu) edgeMenuItem[8]).add(ringMenuItem[3] = Translate.menuItem("polymesh:four", this, "doSelectRing"));
		((BMenu) edgeMenuItem[8]).add(ringMenuItem[4] = Translate.menuItem("polymesh:five", this, "doSelectRing"));
		((BMenu) edgeMenuItem[8]).add(ringMenuItem[5] = Translate.menuItem("polymesh:specify", this, "doSelectRing"));
		edgeMenu.add(edgeMenuItem[9] = Translate.menuItem("polymesh:insertLoops",this, "doInsertLoops"));
		edgeMenu.add(edgeMenuItem[10] = Translate.menuItem("polymesh:selectBoundary",this, "doSelectBoundary"));
		edgeMenu.add(edgeMenuItem[11] = Translate.menuItem("polymesh:closeBoundary",this, "doCloseBoundary"));
		edgeMenu.add(edgeMenuItem[12] = Translate.menuItem("polymesh:findSimilar",this, "doFindSimilarEdges"));
		edgeMenu.add(edgeMenuItem[13] = Translate.menuItem("polymesh:extractToCurve",this, "doExtractToCurve"));
		edgeMenu.addSeparator();
		edgeMenu.add(edgeMenuItem[14] = Translate.menuItem("polymesh:markSelAsSeams",this, "doMarkSelAsSeams"));
		edgeMenu.add(edgeMenuItem[15] = Translate.menuItem("polymesh:seamsToSel",this, "doSeamsToSel"));
		edgeMenu.add(edgeMenuItem[16] = Translate.menuItem("polymesh:addSelToSeams",this, "doAddSelToSeams"));
		edgeMenu.add(edgeMenuItem[17] = Translate.menuItem("polymesh:removeSelFromSeams", this, "doRemoveSelFromSeams"));
		edgeMenu.add(edgeMenuItem[18] = Translate.menuItem("polymesh:openSeams", this,"doOpenSeams"));
		edgeMenu.add(edgeMenuItem[19] = Translate.menuItem("polymesh:clearSeams",this, "doClearSeams"));
		edgeMenu.addSeparator();
		edgeMenu.add(edgeMenuItem[20] = Translate.menuItem("polymesh:selectSmoothnessRange", this, "doSelectEdgeSmoothnessRange"));
		edgeMenu.addSeparator();
		edgeMenu.add(edgeMenuItem[21] = Translate.menuItem("polymesh:bevelProperties",this, "doBevelProperties"));

		edgePopupMenu = new BPopupMenu();
		edgePopupMenuItem = new MenuWidget[22];
		edgePopupMenu.add(edgePopupMenuItem[0] = Translate.menu("polymesh:divide"));
		popupDivideMenuItem = new BMenuItem[5];
		((BMenu) edgePopupMenuItem[0]).add(popupDivideMenuItem[0] = Translate.menuItem("polymesh:two", this, "doDivideEdges"));
		((BMenu) edgePopupMenuItem[0]).add(popupDivideMenuItem[1] = Translate.menuItem("polymesh:three", this, "doDivideEdges"));
		((BMenu) edgePopupMenuItem[0]).add(popupDivideMenuItem[2] = Translate.menuItem("polymesh:four", this, "doDivideEdges"));
		((BMenu) edgePopupMenuItem[0]).add(popupDivideMenuItem[3] = Translate.menuItem("polymesh:five", this, "doDivideEdges"));
		((BMenu) edgePopupMenuItem[0]).add(popupDivideMenuItem[4] = Translate.menuItem("polymesh:specify", this, "doDivideEdges"));
		edgePopupMenu.add(edgePopupMenuItem[1] = Translate.menu("polymesh:moveAlong"));
		((BMenu) edgePopupMenuItem[1]).add(Translate.menuItem("polymesh:normal", this,"doMoveEdgesNormal"));
		((BMenu) edgePopupMenuItem[1]).add(Translate.menuItem("polymesh:x", this,"doMoveEdgesX"));
		((BMenu) edgePopupMenuItem[1]).add(Translate.menuItem("polymesh:y", this,"doMoveEdgesY"));
		((BMenu) edgePopupMenuItem[1]).add(Translate.menuItem("polymesh:z", this,"doMoveEdgesZ"));
		edgePopupMenu.addSeparator();
		edgePopupMenu.add(edgePopupMenuItem[2] = Translate.menu("polymesh:extrude"));
		popupExtrudeEdgeItem = new BMenuItem[4];
		popupExtrudeEdgeRegionItem = new BMenuItem[4];
		((BMenu) edgePopupMenuItem[2]).add(popupExtrudeEdgeItem[0] = Translate.menuItem("polymesh:extrudeNormal", this, "doExtrudeEdge"));
		((BMenu) edgePopupMenuItem[2]).add(popupExtrudeEdgeItem[1] = Translate.menuItem("polymesh:xExtrude", this, "doExtrudeEdge"));
		((BMenu) edgePopupMenuItem[2]).add(popupExtrudeEdgeItem[2] = Translate.menuItem("polymesh:yExtrude", this, "doExtrudeEdge"));
		((BMenu) edgePopupMenuItem[2]).add(popupExtrudeEdgeItem[3] = Translate.menuItem("polymesh:zExtrude", this, "doExtrudeEdge"));
		edgePopupMenu.add(edgePopupMenuItem[3] = Translate.menu("polymesh:extrudeRegion"));
		((BMenu) edgePopupMenuItem[3]).add(popupExtrudeEdgeRegionItem[0] = Translate.menuItem("polymesh:extrudeRegionNormal", this, "doExtrudeEdgeRegion"));
		((BMenu) edgePopupMenuItem[3]).add(popupExtrudeEdgeRegionItem[1] = Translate.menuItem("polymesh:xExtrude", this, "doExtrudeEdgeRegion"));
		((BMenu) edgePopupMenuItem[3]).add(popupExtrudeEdgeRegionItem[2] = Translate.menuItem("polymesh:yExtrude", this, "doExtrudeEdgeRegion"));
		((BMenu) edgePopupMenuItem[3]).add(popupExtrudeEdgeRegionItem[3] = Translate.menuItem("polymesh:zExtrude", this, "doExtrudeEdgeRegion"));
		edgePopupMenu.addSeparator();
		edgePopupMenu.add(edgePopupMenuItem[4] = Translate.menuItem("polymesh:collapse", this, "doCollapseEdges"));
		edgePopupMenu.add(edgePopupMenuItem[5] = Translate.menuItem("polymesh:merge",this, "doMergeEdges"));
		edgePopupMenu.add(edgePopupMenuItem[6] = Translate.menuItem("polymesh:bevel",this, "doBevelEdges"));
		
		edgePopupMenu.addSeparator();
		edgePopupMenu.add(edgePopupMenuItem[7] = Translate.menuItem("polymesh:selectLoop", this, "doSelectLoop"));
		edgePopupMenu.add(edgePopupMenuItem[8] = Translate.menu("polymesh:selectRing"));
		popupRingMenuItem = new BMenuItem[6];
		((BMenu) edgePopupMenuItem[8]).add(popupRingMenuItem[0] = Translate.menuItem("polymesh:all", this, "doSelectRing"));
		((BMenu) edgePopupMenuItem[8]).add(popupRingMenuItem[1] = Translate.menuItem("polymesh:two", this, "doSelectRing"));
		((BMenu) edgePopupMenuItem[8]).add(popupRingMenuItem[2] = Translate.menuItem("polymesh:three", this, "doSelectRing"));
		((BMenu) edgePopupMenuItem[8]).add(popupRingMenuItem[3] = Translate.menuItem("polymesh:four", this, "doSelectRing"));
		((BMenu) edgePopupMenuItem[8]).add(popupRingMenuItem[4] = Translate.menuItem("polymesh:five", this, "doSelectRing"));
		((BMenu) edgePopupMenuItem[8]).add(popupRingMenuItem[5] = Translate.menuItem("polymesh:specify", this, "doSelectRing"));
		edgePopupMenu.add(edgePopupMenuItem[9] = Translate.menuItem("polymesh:insertLoops", this, "doInsertLoops"));
		edgePopupMenu.add(edgePopupMenuItem[10] = Translate.menuItem("polymesh:selectBoundary", this, "doSelectBoundary"));
		edgePopupMenu.add(edgePopupMenuItem[11] = Translate.menuItem("polymesh:closeBoundary", this, "doCloseBoundary"));
		edgePopupMenu.add(edgePopupMenuItem[12] = Translate.menuItem("polymesh:findSimilar", this, "doFindSimilarEdges"));
		edgePopupMenu.add(edgePopupMenuItem[13] = Translate.menuItem("polymesh:extractToCurve", this, "doExtractToCurve"));
		edgePopupMenu.addSeparator();
		edgePopupMenu.add(edgePopupMenuItem[14] = Translate.menuItem("polymesh:markSelAsSeams", this, "doMarkSelAsSeams"));
		edgePopupMenu.add(edgePopupMenuItem[15] = Translate.menuItem("polymesh:seamsToSel", this, "doSeamsToSel"));
		edgePopupMenu.add(edgePopupMenuItem[16] = Translate.menuItem("polymesh:addSelToSeams", this, "doAddSelToSeams"));
		edgePopupMenu.add(edgePopupMenuItem[17] = Translate.menuItem("polymesh:removeSelFromSeams", this, "doRemoveSelFromSeams"));
		edgePopupMenu.add(edgePopupMenuItem[18] = Translate.menuItem("polymesh:openSeams", this, "doOpenSeams"));
		edgePopupMenu.add(edgePopupMenuItem[19] = Translate.menuItem("polymesh:clearSeams", this, "doClearSeams"));
		edgePopupMenu.addSeparator();
		edgePopupMenu.add(edgePopupMenuItem[20] = Translate.menuItem("polymesh:selectSmoothnessRange", this, "doSelectEdgeSmoothnessRange"));
		edgePopupMenu.addSeparator();
		edgePopupMenu.add(edgePopupMenuItem[21] = Translate.menuItem("polymesh:bevelProperties", this, "doBevelProperties"));
	}

	/**
	 * Builds the face menu
	 * 
	 */
	void createFaceMenu() {
		faceMenu = Translate.menu("polymesh:face");
		faceMenuItem = new MenuWidget[11];
		faceMenu.add(faceMenuItem[0] = Translate.menu("polymesh:moveAlong"));
                ((BMenu) faceMenuItem[0]).add(Translate.menuItem("polymesh:normal", this, "doMoveFacesNormal"));
                ((BMenu) faceMenuItem[0]).add(Translate.menuItem("polymesh:x", this, "doMoveFacesX"));
                ((BMenu) faceMenuItem[0]).add(Translate.menuItem("polymesh:y", this, "doMoveFacesY"));
                ((BMenu) faceMenuItem[0]).add(Translate.menuItem("polymesh:z", this, "doMoveFacesZ"));
                faceMenu.add(faceMenuItem[1] = Translate.menu("polymesh:extrude"));
                extrudeItem = new BMenuItem[4];
                extrudeRegionItem = new BMenuItem[4];
                ((BMenu) faceMenuItem[1]).add(extrudeItem[0] = Translate.menuItem("polymesh:extrudeNormal", this, "doExtrude"));
                ((BMenu) faceMenuItem[1]).add(extrudeItem[1] = Translate.menuItem("polymesh:xExtrude", this, "doExtrude"));
                ((BMenu) faceMenuItem[1]).add(extrudeItem[2] = Translate.menuItem("polymesh:yExtrude", this, "doExtrude"));
                ((BMenu) faceMenuItem[1]).add(extrudeItem[3] = Translate.menuItem("polymesh:zExtrude", this, "doExtrude"));
                faceMenu.add(faceMenuItem[2] = Translate.menu("polymesh:extrudeRegion"));
                ((BMenu) faceMenuItem[2]).add(extrudeRegionItem[0] = Translate.menuItem("polymesh:extrudeRegionNormal", this, "doExtrudeRegion"));
                ((BMenu) faceMenuItem[2]).add(extrudeRegionItem[1] = Translate.menuItem("polymesh:xExtrude", this, "doExtrudeRegion"));
                ((BMenu) faceMenuItem[2]).add(extrudeRegionItem[2] = Translate.menuItem("polymesh:yExtrude", this, "doExtrudeRegion"));
                ((BMenu) faceMenuItem[2]).add(extrudeRegionItem[3] = Translate.menuItem("polymesh:zExtrude", this, "doExtrudeRegion"));
                faceMenu.addSeparator();
                faceMenu.add(faceMenuItem[3] = Translate.menuItem("polymesh:smoothFaces", this, "doSmoothFaces"));
                faceMenu.add(faceMenuItem[4] = Translate.menuItem("polymesh:subdivideFaces", this, "doSubdivideFaces"));
                faceMenu.add(faceMenuItem[5] = Translate.menuItem("polymesh:collapse", this, "doCollapseFaces"));
                faceMenu.add(faceMenuItem[6] = Translate.menuItem("polymesh:merge", this, "doMergeFaces"));
                faceMenu.add(faceMenuItem[7] = Translate.menuItem("polymesh:triangulate", this, "doTriangulateFaces"));
                faceMenu.add(faceMenuItem[8] = Translate.menuItem("polymesh:outlineFaces", this, "doOutlineFaces"));
                faceMenu.addSeparator();
                faceMenu.add(faceMenuItem[9] = Translate.menuItem("parameters", this, "setParametersCommand"));
                faceMenu.add(faceMenuItem[10] = Translate.menuItem("polymesh:findSimilar", this, "doFindSimilarFaces"));
                menubar.add(faceMenu);
                facePopupMenu = new BPopupMenu();
                facePopupMenuItem = new MenuWidget[11];
                facePopupMenu.add(facePopupMenuItem[0] = Translate.menu("polymesh:moveAlong"));
                ((BMenu) facePopupMenuItem[0]).add(Translate.menuItem("polymesh:normal", this, "doMoveFacesNormal"));
                ((BMenu) facePopupMenuItem[0]).add(Translate.menuItem("polymesh:x", this, "doMoveFacesX"));
                ((BMenu) facePopupMenuItem[0]).add(Translate.menuItem("polymesh:y", this, "doMoveFacesY"));
                ((BMenu) facePopupMenuItem[0]).add(Translate.menuItem("polymesh:z", this, "doMoveFacesZ"));
                facePopupMenu.add(facePopupMenuItem[1] = Translate.menu("polymesh:extrude"));
                popupExtrudeItem = new BMenuItem[4];
                popupExtrudeRegionItem = new BMenuItem[4];
                ((BMenu) facePopupMenuItem[1]).add(popupExtrudeItem[0] = Translate.menuItem("polymesh:extrudeNormal", this, "doExtrude"));
                ((BMenu) facePopupMenuItem[1]).add(popupExtrudeItem[1] = Translate.menuItem("polymesh:xExtrude", this, "doExtrude"));
                ((BMenu) facePopupMenuItem[1]).add(popupExtrudeItem[2] = Translate.menuItem("polymesh:yExtrude", this, "doExtrude"));
                ((BMenu) facePopupMenuItem[1]).add(popupExtrudeItem[3] = Translate.menuItem("polymesh:zExtrude", this, "doExtrude"));
                facePopupMenu.add(facePopupMenuItem[2] = Translate.menu("polymesh:extrudeRegion"));
                ((BMenu) facePopupMenuItem[2]).add(popupExtrudeRegionItem[0] = Translate.menuItem("polymesh:extrudeRegionNormal", this, "doExtrudeRegion"));
                ((BMenu) facePopupMenuItem[2]).add(popupExtrudeRegionItem[1] = Translate.menuItem("polymesh:xExtrude", this, "doExtrudeRegion"));
                ((BMenu) facePopupMenuItem[2]).add(popupExtrudeRegionItem[2] = Translate.menuItem("polymesh:yExtrude", this, "doExtrudeRegion"));
                ((BMenu) facePopupMenuItem[2]).add(popupExtrudeRegionItem[3] = Translate.menuItem("polymesh:zExtrude", this, "doExtrudeRegion"));
                facePopupMenu.addSeparator();
                facePopupMenu.add(facePopupMenuItem[3] = Translate.menuItem("polymesh:smoothFaces", this, "doSmoothFaces"));
                facePopupMenu.add(facePopupMenuItem[4] = Translate.menuItem("polymesh:subdivideFaces", this, "doSubdivideFaces"));
                facePopupMenu.add(facePopupMenuItem[5] = Translate.menuItem("polymesh:collapse", this, "doCollapseFaces"));
                facePopupMenu.add(facePopupMenuItem[6] = Translate.menuItem("polymesh:merge", this, "doMergeFaces"));
                facePopupMenu.add(facePopupMenuItem[7] = Translate.menuItem("polymesh:triangulate", this, "doTriangulateFaces"));
                facePopupMenu.add(facePopupMenuItem[8] = Translate.menuItem("polymesh:outlineFaces", this, "doOutlineFaces"));
                facePopupMenu.addSeparator();
                facePopupMenu.add(facePopupMenuItem[9] = Translate.menuItem("polymesh:parameters", this, "setParametersCommand"));
                facePopupMenu.add(facePopupMenuItem[10] = Translate.menuItem("polymesh:findSimilar", this, "doFindSimilarFaces"));
	}

	/**
	 * Builds the skeleton menu
	 * 
	 * @param obj
	 *                The poly mesh being edited
	 */
	void createSkeletonMenu(PolyMesh obj) {

                skeletonMenu = Translate.menu("skeleton");
                menubar.add(skeletonMenu);
                skeletonMenuItem = new BMenuItem[6];
                skeletonMenu.add(skeletonMenuItem[0] = Translate.menuItem("editBone", this, "editJointCommand"));
                skeletonMenu.add(skeletonMenuItem[1] = Translate.menuItem("deleteBone", this, "deleteJointCommand"));
                skeletonMenu.add(skeletonMenuItem[2] = Translate.menuItem("setParentBone", this, "setJointParentCommand"));
                skeletonMenu.add(skeletonMenuItem[3] = Translate.menuItem("importSkeleton", this, "importSkeletonCommand"));
                skeletonMenu.addSeparator();
                skeletonMenu.add(skeletonMenuItem[4] = Translate.menuItem("bindSkeleton", this, "bindSkeletonCommand"));
                skeletonMenu.add(skeletonMenuItem[5] = Translate.checkboxMenuItem("detachSkeleton", this, "skeletonDetachedChanged", false));
	}

        private final BMenuItem unfoldMeshAction = Translate.menuItem("polymesh:unfoldMesh", this, "doUnfoldMesh");
        private final BMenuItem editMappingAction = Translate.menuItem("polymesh:editMapping", this, "doEditMapping");
	
        /**
	 * Builds the texture menu
	 */
	void createTextureMenu() {
          BMenu textureMenu = Translate.menu("polymesh:texture");
          textureMenu.add(unfoldMeshAction);
          textureMenu.add(editMappingAction);
          menubar.add(textureMenu);
	}

	private void createPrefsMenu() {
                BMenu prefsMenu = Translate.menu("polymesh:prefs");
                menubar.add(prefsMenu);
                prefsMenu.add(Translate.menuItem("polymesh:reloadKeystrokes", this, "reloadKeystrokes"));
                // prefsMenu.addSeparator();
                prefsMenu.add(Translate.menuItem("polymesh:editKeystrokes", this, "editKeystrokes"));
                prefsMenu.addSeparator();
                prefsMenu.add(Translate.menuItem("polymesh:loadDefaults", this, "doLoadDefaultProperties"));
                prefsMenu.add(Translate.menuItem("polymesh:storeDefaults", this, "doStoreDefaultProperties"));
                prefsMenu.add(Translate.menuItem("polymesh:resetDefaults", this, "doResetDefaultProperties"));
	}
	
	@SuppressWarnings("unused")
	private void doSelectCorners() {
		if (selectMode != POINT_MODE) {
			return;
		}
		PolyMesh mesh = (PolyMesh) objInfo.object;
		Wvertex[] vertices = (Wvertex[]) mesh.getVertices();
		for (int i = 0; i < selected.length; i++) {
			selected[i] |= (vertices[i].type == Wvertex.CORNER);
		}
		setSelection(selected);
	}
	
	@SuppressWarnings("unused")
	private void doSelectEdgeSmoothnessRange() {
		EdgeSmoothnessRangeDialog dlg = new EdgeSmoothnessRangeDialog();
	}

	@SuppressWarnings("unused")
	private void doLoadDefaultProperties() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.loadFromDisplayPropertiesPreferences();
		updateImage();
	}

	@SuppressWarnings("unused")
	private void doStoreDefaultProperties() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.storeDisplayPropertiesAsReferences();
	}

	@SuppressWarnings("unused")
	private void doResetDefaultProperties() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.resetDisplayPropertiesPreferences();
		updateImage();
	}

	/**
	 * Edits the mesh display properties like colors and handle size
	 *
	 */
	public void doEditProperties() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		ColorButton vertColorButton = new ColorButton(mesh.getVertColor());
		ColorButton selectedVertColorButton = new ColorButton(mesh
				.getSelectedVertColor());
		ColorButton edgeColorButton = new ColorButton(mesh.getEdgeColor());
		ColorButton selectedEdgeColorButton = new ColorButton(mesh
				.getSelectedEdgeColor());
		ColorButton seamColorButton = new ColorButton(mesh.getSeamColor());
		ColorButton selectedSeamColorButton = new ColorButton(mesh
				.getSelectedSeamColor());
		ColorButton meshColorButton = new ColorButton(mesh.getMeshColor());
		ColorButton selectedFaceColorButton = new ColorButton(mesh
				.getSelectedFaceColor());
		BSpinner handleSpinner = new BSpinner(mesh.getHandleSize(), 2, 100, 1);
		BCheckBox useCustomColors = new BCheckBox(Translate.text("polymesh:useCustomColors"), mesh.useCustomColors());
		propertiesPanel = new FormContainer(2, 12);
	    propertiesPanel.setColumnWeight(1, 1.0);
	    LayoutInfo labelLayout = new LayoutInfo(LayoutInfo.EAST, LayoutInfo.NONE, new Insets(2, 0, 2, 5), null);
	    LayoutInfo widgetLayout = new LayoutInfo(LayoutInfo.WEST, LayoutInfo.BOTH, new Insets(2, 0, 2, 0), null);
	    LayoutInfo centerLayout = new LayoutInfo(LayoutInfo.CENTER, LayoutInfo.NONE, new Insets(2, 0, 2, 0), null);
	    propertiesPanel.add(useCustomColors, 0, 0, 2, 1, centerLayout);
	    propertiesPanel.add(Translate.label("polymesh:vertColor"), 0, 1, labelLayout);
	    propertiesPanel.add(Translate.label("polymesh:selectedVertColor"), 0, 2, labelLayout);
	    propertiesPanel.add(Translate.label("polymesh:edgeColor"), 0, 3, labelLayout);
	    propertiesPanel.add(Translate.label("polymesh:selectedEdgeColor"), 0, 4, labelLayout);
	    propertiesPanel.add(Translate.label("polymesh:seamColor"), 0, 5, labelLayout);
	    propertiesPanel.add(Translate.label("polymesh:selectedSeamColor"), 0, 6, labelLayout);
	    propertiesPanel.add(Translate.label("polymesh:meshColor"), 0, 7, labelLayout);
	    propertiesPanel.add(Translate.label("polymesh:selectedFaceColor"), 0, 8, labelLayout);
	    propertiesPanel.add(Translate.label("polymesh:handleSize"), 0, 9, labelLayout);
	    propertiesPanel.add(vertColorButton, 1, 1, labelLayout);
	    propertiesPanel.add(selectedVertColorButton, 1, 2, widgetLayout);
	    propertiesPanel.add(edgeColorButton, 1, 3, widgetLayout);
	    propertiesPanel.add(selectedEdgeColorButton, 1, 4, widgetLayout);
	    propertiesPanel.add(seamColorButton, 1, 5, widgetLayout);
	    propertiesPanel.add(selectedSeamColorButton, 1, 6, widgetLayout);
	    propertiesPanel.add(meshColorButton, 1, 7, widgetLayout);
	    propertiesPanel.add(selectedFaceColorButton, 1, 8, widgetLayout);
	    propertiesPanel.add(handleSpinner, 1, 9, widgetLayout);
	    int n = propertiesPanel.getChildCount();
	    for (int i = 1; i < n; i++) {
	    	propertiesPanel.getChild(i).setEnabled(useCustomColors.getState());
	    }
	    useCustomColors.addEventLink(ValueChangedEvent.class, new Object() {
	        void processEvent(ValueChangedEvent ev)
	        {
	        	Widget w  = ev.getWidget();
	        	int n = propertiesPanel.getChildCount();
	    	    for (int i = 1; i < n; i++) {
	    	    	propertiesPanel.getChild(i).setEnabled(((BCheckBox)w).getState());
	    	    }
	        }
	      });
	    PanelDialog dlg = new PanelDialog(this, Translate
				.text("polymesh:setMeshProperties"), propertiesPanel);
		if (dlg.clickedOk()) {
			mesh.setUseCustomColors(useCustomColors.getState());
			if (mesh.useCustomColors()) {
				mesh.setVertColor(vertColorButton.getColor());
				mesh.setSelectedVertColor(selectedVertColorButton.getColor());
				mesh.setEdgeColor(edgeColorButton.getColor());
				mesh.setSelectedEdgeColor(selectedEdgeColorButton.getColor());
				mesh.setSeamColor(seamColorButton.getColor());
				mesh.setSelectedSeamColor(selectedSeamColorButton.getColor());
				mesh.setMeshColor(meshColorButton.getColor());
				mesh.setSelectedFaceColor(selectedFaceColorButton.getColor());
				mesh.setHandleSize(((Integer) handleSpinner.getValue()));
			}
			updateImage();
		}
	}
	public void doCenterMesh() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		MeshVertex[] v = mesh.getVertices();
		Vec3 center = new Vec3();
		for (int i = 0; i < v.length; i++) {
			center.add(v[i].r);
		}
		center.scale(1.0/v.length);
		for (int i = 0; i < v.length; i++) {
			v[i].r.subtract(center);
		}
		mesh.resetMesh();
		objectChanged();
		updateImage();
	}

	/**
	 * Delete the selected points, edges, or faces from the mesh.
	 */
	public void deleteCommand() {
		PolyMesh mesh = (PolyMesh) objInfo.object;

		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		if (selectMode == POINT_MODE) {
			int count = 0;
			for (int i = 0; i < selected.length; ++i)
				if (selected[i])
					++count;
			int[] indices = new int[count];
			count = 0;
			for (int i = 0; i < selected.length; ++i)
				if (selected[i])
					indices[count++] = i;
			if (mesh.getVertices().length - indices.length < 3) {
				new BStandardDialog(Translate.text("polymesh:errorTitle"), UIUtilities
						.breakString(Translate.text("illegalDelete")),
						BStandardDialog.ERROR).showMessageDialog(null);
				return;
			}
			mesh.deleteVertices(indices);
		} else if (selectMode == EDGE_MODE) {
			int count = 0;
			for (int i = 0; i < selected.length; ++i)
				if (selected[i])
					++count;
			int[] indices = new int[count];
			count = 0;
			for (int i = 0; i < selected.length; ++i)
				if (selected[i])
					indices[count++] = i;
			if (mesh.getEdges().length - indices.length < 3) {
				new BStandardDialog(Translate.text("polymesh:errorTitle"), UIUtilities
						.breakString(Translate.text("illegalDelete")),
						BStandardDialog.ERROR).showMessageDialog(null);
				return;
			}
			mesh.deleteEdges(indices);
		} else {
			int count = 0;
			for (int i = 0; i < selected.length; ++i)
				if (selected[i])
					++count;
			int[] indices = new int[count];
			count = 0;
			for (int i = 0; i < selected.length; ++i)
				if (selected[i])
					indices[count++] = i;
			if (mesh.getFaces().length - indices.length < 1) {
				new BStandardDialog(Translate.text("polymesh:errorTitle"), UIUtilities
						.breakString(Translate.text("illegalDelete")),
						BStandardDialog.ERROR).showMessageDialog(null);
				return;
			}
			mesh.deleteFaces(indices);
		}
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		updateImage();

	}

	/**
	 * Select the entire mesh.
	 */
	void selectAllCommand() {
		setUndoRecord(new UndoRecord(this, false,
				UndoRecord.SET_MESH_SELECTION, new Object[] { this,
						new Integer(selectMode), selected.clone() }));
		for (int i = 0; i < selected.length; i++)
			selected[i] = true;
		setSelection(selected);
	}

	/**
	 * Select the edges which form the boundary of the mesh.
	 */

	public void selectBoundaryCommand() {
		PolyMesh theMesh = (PolyMesh) objInfo.object;
		if (selectMode != EDGE_MODE) {
			new BStandardDialog(Translate.text("polymesh:errorTitle"), UIUtilities
					.breakString(Translate.text("polymesh:edgeModeForBoundary")),
					BStandardDialog.ERROR).showMessageDialog(this);
			return;
		}
	}

	/**
	 * Extend the selection outward by one edge.
	 */

	public void extendSelectionCommand() {
		PolyMesh theMesh = (PolyMesh) objInfo.object;
		int dist[] = getSelectionDistance();
		boolean selectedVert[] = new boolean[dist.length];
		Wedge edges[] = theMesh.getEdges();

		setUndoRecord(new UndoRecord(this, false,
				UndoRecord.SET_MESH_SELECTION, new Object[] { this,
						new Integer(selectMode), selected.clone() }));
		for (int i = 0; i < edges.length; i++)
			if ((dist[edges[i].vertex] == 0 || dist[edges[edges[i].hedge].vertex] == 0))
				selectedVert[edges[i].vertex] = selectedVert[edges[edges[i].hedge].vertex] = true;
		if (selectMode == PolyMeshEditorWindow.POINT_MODE)
			setSelection(selectedVert);
		else if (selectMode == PolyMeshEditorWindow.EDGE_MODE) {
			for (int i = 0; i < edges.length / 2; i++)
				selected[i] = (selectedVert[edges[i].vertex] && selectedVert[edges[edges[i].hedge].vertex]);
			setSelection(selected);
		} else {
			Wface faces[] = theMesh.getFaces();
			for (int i = 0; i < faces.length; i++) {
				selected[i] = true;
				int[] fv = theMesh.getFaceVertices(faces[i]);
				for (int k = 0; k < fv.length; ++k)
					selected[i] &= selectedVert[fv[k]];
			}
			setSelection(selected);
		}
	}

	/**
	 * Selects edge loops from current selection
	 */
	public void doSelectLoop() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		boolean[] loop = mesh.findEdgeLoops(selected);
		if (loop != null)
			setSelection(loop);
	}

	/**
	 * Selects edge rings from current selection
	 */
	public void doSelectRing(CommandEvent ev) {
		
		PolyMesh mesh = (PolyMesh) objInfo.object;
		boolean[] ring = null;
		if (ev.getWidget() == ringMenuItem[0]
				|| ev.getWidget() == popupRingMenuItem[0]) {
			ring = mesh.findEdgeStrips(selected, 1);
		} else if (ev.getWidget() == ringMenuItem[1]
				|| ev.getWidget() == popupRingMenuItem[1]) {
			ring = mesh.findEdgeStrips(selected, 2);
		} else if (ev.getWidget() == ringMenuItem[2]
				|| ev.getWidget() == popupRingMenuItem[2]) {
			ring = mesh.findEdgeStrips(selected, 3);
		} else if (ev.getWidget() == ringMenuItem[3]
				|| ev.getWidget() == popupRingMenuItem[3]) {
			ring = mesh.findEdgeStrips(selected, 4);
		} else if (ev.getWidget() == ringMenuItem[4]
				|| ev.getWidget() == popupRingMenuItem[4]) {
			ring = mesh.findEdgeStrips(selected, 5);
		} else if (ev.getWidget() == ringMenuItem[5]
				|| ev.getWidget() == popupRingMenuItem[5]) {
			DivideDialog dlg = new DivideDialog();
			int num = dlg.getNumber();
			if (num > 0)
				ring = mesh.findEdgeStrips(selected, num);
		}
		if (ring != null)
			setSelection(ring);
	}

	/**
	 * Description of the Method
	 */
	private void freehandModeChanged() {
		lastFreehand = ((BCheckBoxMenuItem) editMenuItem[7]).getState();
		for (int i = 0; i < theView.length; i++)
			((PolyMeshViewer) theView[i])
					.setFreehandSelection(((BCheckBoxMenuItem) editMenuItem[7])
							.getState());
		savePreferences();
	}

	private void projectModeChanged() {
		setProjectOntoSurface(((BCheckBoxMenuItem) editMenuItem[8]).getState());
		updateImage();
	}

	/** Get whether the control mesh is displayed projected onto the surface. */

	public boolean getProjectOntoSurface() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		if (!projectOntoSurface
				|| (mesh.getSmoothingMethod() != Mesh.APPROXIMATING && mesh
						.getSmoothingMethod() != Mesh.INTERPOLATING))
			return false;
		else
			return true;
	}

	/** Set whether the control mesh is displayed projected onto the surface. */
	public void setProjectOntoSurface(boolean project) {
		lastProjectOntoSurface = projectOntoSurface = project;
		savePreferences();
	}

	/**
	 * Determine which edge of the control mesh corresponds to each edge of
	 * the subdivided mesh. If the control mesh is not being projected onto
	 * the surface, this returns null.
	 */

	int[] findProjectedEdges() {
		// See if we actually want to project the control mesh.
		if (!getProjectOntoSurface()) {
			lastPreview = null;
			return null;
		}

		// See whether we need to rebuild to list of projected edges.
		PolyMesh mesh = (PolyMesh) objInfo.object;
		RenderingMesh preview = getObject().getPreviewMesh();
		if (preview == lastPreview)
			return projectedEdge; // The mesh hasn't changed.
		lastPreview = preview;

		mesh = (PolyMesh) objInfo.object;
		QuadMesh subdividedMesh = mesh.getSubdividedMesh();
		projectedEdge = subdividedMesh.getProjectedEdges();
		if (projectedEdge == null)
			System.out.println("null projected Edge");
		return projectedEdge;
	}

	/** Add an extra texture parameter to a triangle mesh. */

	private void addTriangleMeshExtraParameter(TriangleMesh mesh) {
		TextureParameter hideFaceParam = new TextureParameter(this,
				"Hide Face", 0.0, 1.0, 0.0);
		TextureParameter params[] = mesh.getParameters();
		TextureParameter newparams[] = new TextureParameter[params.length + 1];
		ParameterValue values[] = mesh.getParameterValues();
		ParameterValue newvalues[] = new ParameterValue[values.length + 1];
		for (int i = 0; i < params.length; i++) {
			newparams[i] = params[i];
			newvalues[i] = values[i];
		}
		newparams[params.length] = hideFaceParam;
		newvalues[values.length] = new FaceParameterValue(mesh, hideFaceParam);
		double index[] = new double[mesh.getFaces().length];
		for (int i = 0; i < index.length; i++)
			index[i] = i;
		((FaceParameterValue) newvalues[values.length]).setValue(index);
		mesh.setParameters(newparams);
		mesh.setParameterValues(newvalues);
	}

	/**
	 * Get the subdivided mesh which represents the surface. If the
	 * mesh is not subdivided, this returns null.
	 */

	QuadMesh getSubdividedPolyMesh() {
		return ((PolyMesh) objInfo.object).getSubdividedMesh();
	}

	/**
	 * Cancel button selected
	 */
	protected void doCancel() {
		oldMesh = null;
		eventSource.removeEventLink(CopyEvent.class, this);
		dispose();
	}

	/**
	 * OK button selection
	 */
	protected void doOk() {
		PolyMesh theMesh = (PolyMesh) objInfo.object;
		if (realView)
			theMesh.setSmoothingMethod(smoothingMethod);
		if (realMirror)
			theMesh.setMirrorState(mirror);
		if (((PolyMesh) oldMesh).getMaterial() != null) {
			if (!theMesh.isClosed()) {
				String options[] = new String[] { Translate.text("button.ok"),
						Translate.text("button.cancel") };
				BStandardDialog dlg = new BStandardDialog(Translate
						.text("polymesh:errorTitle"), UIUtilities.breakString(Translate
						.text("surfaceNoLongerClosed")),
						BStandardDialog.WARNING);
				int choice = dlg.showOptionDialog(this, options, options[0]);
				if (choice == 1)
					return;
				theMesh.setMaterial(null, null);
			} else
				theMesh.setMaterial(((PolyMesh) oldMesh).getMaterial(),
						((PolyMesh) oldMesh).getMaterialMapping());
		}
		removeExtraParameters();
		if (oldMesh != theMesh)
			oldMesh.copyObject(theMesh);
		oldMesh = null;
		eventSource.removeEventLink(CopyEvent.class, this);
		dispose();
		onClose.run();
		parentWindow.updateImage();
		parentWindow.updateMenus();
	}

	/*
	 * EditingWindow methods.
	 */
	/**
	 * Sets the currently selected tool
	 * 
	 * @param tool
	 *                The new tool
	 */
	public void setTool(EditingTool tool) {
		if (tool instanceof GenericTool) {
			if (selectMode == modes.getSelection())
				return;
			if (undoItem != null)
				setUndoRecord(new UndoRecord(this, false,
						UndoRecord.SET_MESH_SELECTION, new Object[] { this,
								new Integer(selectMode), selected }));
			setSelectionMode(modes.getSelection());
			theView[currentView].getCurrentTool().activate();
		} else {
			for (int i = 0; i < theView.length; i++)
				theView[i].setTool(tool);
			currentTool = tool;

		}
	}

	/**
	 * Given a list of deltas which will be added to the selected vertices,
	 * calculate the corresponding deltas for the unselected vertices
	 * according to the mesh tension.
	 * 
	 * @param delta
	 *                Description of the Parameter
	 */

	public void adjustDeltas(Vec3 delta[]) {
		int dist[] = getSelectionDistance();
		int count[] = new int[delta.length];
		PolyMesh theMesh = (PolyMesh) objInfo.object;
		PolyMesh.Wedge edge[] = theMesh.getEdges();
		int maxDistance = getTensionDistance();
		double tension = getMeshTension();
		double scale[] = new double[maxDistance + 1];

		for (int i = 0; i < delta.length; i++)
			if (dist[i] != 0)
				delta[i].set(0.0, 0.0, 0.0);
		for (int i = 0; i < maxDistance; i++) {
			for (int j = 0; j < count.length; j++)
				count[j] = 0;
			for (int j = 0; j < edge.length; j++) {
				if (dist[edge[j].vertex] == i
						&& dist[edge[edge[j].hedge].vertex] == i + 1) {
					count[edge[edge[j].hedge].vertex]++;
					delta[edge[edge[j].hedge].vertex]
							.add(delta[edge[j].vertex]);
				} else if (dist[edge[edge[j].hedge].vertex] == i
						&& dist[edge[j].vertex] == i + 1) {
					count[edge[j].vertex]++;
					delta[edge[j].vertex]
							.add(delta[edge[edge[j].hedge].vertex]);
				}
			}
			for (int j = 0; j < count.length; j++)
				if (count[j] > 1)
					delta[j].scale(1.0 / count[j]);
		}
		for (int i = 0; i < scale.length; i++)
			scale[i] = Math.pow((maxDistance - i + 1.0) / (maxDistance + 1.0),
					tension);
		for (int i = 0; i < delta.length; i++)
			if (dist[i] > 0)
				delta[i].scale(scale[dist[i]]);
	}

	/**
	 * Updates window menus
	 */
	public void updateMenus() {
		super.updateMenus();
		switch (selectMode) {
		default:
		case POINT_MODE:
			vertexMenu.setEnabled(true);
			edgeMenu.setEnabled(false);
			faceMenu.setEnabled(false);
			break;
		case EDGE_MODE:
			vertexMenu.setEnabled(false);
			edgeMenu.setEnabled(true);
			extrudeEdgeItem[0].setShortcut(singleNormalShortcut);
			extrudeEdgeRegionItem[0].setShortcut(groupNormalShortcut);
			extrudeItem[0].setShortcut(null);
			extrudeRegionItem[0].setShortcut(null);
			faceMenu.setEnabled(false);
			break;
		case FACE_MODE:
			vertexMenu.setEnabled(false);
			edgeMenu.setEnabled(false);
			faceMenu.setEnabled(true);
			extrudeItem[0].setShortcut(singleNormalShortcut);
			extrudeRegionItem[0].setShortcut(groupNormalShortcut);
			extrudeEdgeItem[0].setShortcut(null);
			extrudeEdgeRegionItem[0].setShortcut(null);
			break;
		}
		PolyMesh mesh = (PolyMesh) objInfo.object;
		MeshViewer view = (MeshViewer) theView[currentView];
		boolean any = false;
		int i;
		int selCount = 0;

		if (selected != null)
			for (i = 0; i < selected.length; i++)
				if (selected[i])
					++selCount;
		if (selCount > 0) {
			//((RotateViewTool) altTool).setUseSelectionCenter(true);
			any = true;
			for (int j = 0; j < vertexMenuItem.length; ++j) {
				((Widget) vertexMenuItem[j]).setEnabled(true);
				((Widget) vertexPopupMenuItem[j]).setEnabled(true);
			}
			for (int j = 0; j < edgeMenuItem.length; ++j) {
				((Widget) edgeMenuItem[j]).setEnabled(true);
				((Widget) edgePopupMenuItem[j]).setEnabled(true);
			}
			for (int j = 0; j < faceMenuItem.length; ++j) {
				((Widget) faceMenuItem[j]).setEnabled(true);
				((Widget) facePopupMenuItem[j]).setEnabled(true);
			}
			editMenuItem[0].setEnabled(true);
			editMenuItem[2].setEnabled(true);
			editMenuItem[3].setEnabled(true);
			editMenuItem[4].setEnabled(true);
			editMenuItem[5].setEnabled(true);
			if (selCount < 4) {
				((Widget) vertexMenuItem[6]).setEnabled(false);
				((Widget) vertexMenuItem[7]).setEnabled(false);
				((Widget) vertexMenuItem[8]).setEnabled(false);
				((Widget) vertexPopupMenuItem[6]).setEnabled(false);
				((Widget) vertexPopupMenuItem[7]).setEnabled(false);
				((Widget) vertexPopupMenuItem[8]).setEnabled(false);
			}
			if (selCount != 2) {
				((Widget) vertexMenuItem[10]).setEnabled(false);
				((Widget) vertexPopupMenuItem[10]).setEnabled(false);
			}
			switch (selectMode) {
			default:
			case POINT_MODE:
				if (mesh.getSmoothingMethod() == Mesh.APPROXIMATING) {
					cornerCB.setEnabled(true);
					boolean corner = true;
					Wvertex[] vertices = (Wvertex[]) mesh.getVertices();
					if (selected != null && selected.length == vertices.length)
						for (i = 0; i < selected.length; i++)
							if (selected[i])
								corner &= (vertices[i].type == Wvertex.CORNER);
					cornerCB.setState(corner);
				} else {
					cornerCB.setState(false);
					cornerCB.setEnabled(false);
				}
				break;
			case EDGE_MODE:
				if (mesh.getSmoothingMethod() == Mesh.APPROXIMATING) {
					edgeSlider.setEnabled(true);
					float s = 1.0f;
					Wedge[] ed = mesh.getEdges();
					if (selected != null)
						for (i = 0; i < selected.length; i++)
							if (selected[i] && ed[i].smoothness < s)
								s = ed[i].smoothness;
					edgeSlider.setValue(s);
				} else {
					edgeSlider.setEnabled(false);
				}
				break;
			case FACE_MODE:
				break;
			}
		} else {
			//((RotateViewTool) altTool).setUseSelectionCenter(false);
			for (int j = 0; j < vertexMenuItem.length; ++j) {
				((Widget) vertexMenuItem[j]).setEnabled(false);
				((Widget) vertexPopupMenuItem[j]).setEnabled(false);
			}
			for (int j = 0; j < edgeMenuItem.length; ++j) {
				((Widget) edgeMenuItem[j]).setEnabled(false);
				((Widget) edgePopupMenuItem[j]).setEnabled(false);
			}
			for (int j = 0; j < faceMenuItem.length; ++j) {
				((Widget) faceMenuItem[j]).setEnabled(false);
				((Widget) facePopupMenuItem[j]).setEnabled(false);
			}
			editMenuItem[0].setEnabled(false);
			editMenuItem[2].setEnabled(false);
			editMenuItem[3].setEnabled(false);
			editMenuItem[4].setEnabled(false);
			editMenuItem[5].setEnabled(false);
			switch (selectMode) {
			default:
			case POINT_MODE:
				cornerCB.setState(false);
				cornerCB.setEnabled(false);
				((Widget) vertexMenuItem[15]).setEnabled(true);
				((Widget) vertexPopupMenuItem[15]).setEnabled(true);
				break;
			case EDGE_MODE:
				((Widget) edgeMenuItem[20]).setEnabled(true);
				((Widget) edgeMenuItem[21]).setEnabled(true);
				((Widget) edgePopupMenuItem[20]).setEnabled(true);
				((Widget) edgePopupMenuItem[21]).setEnabled(true);
				edgeSlider.setEnabled(false);
				break;
			case FACE_MODE:
				break;
			}
		}
		if (selected != null)
			if (selCount == selected.length) {
				editMenuItem[1].setEnabled(false);
				editMenuItem[2].setEnabled(false);
				((BMenuItem) faceMenuItem[10]).setEnabled(false);
				((BMenuItem) edgeMenuItem[12]).setEnabled(false);
				((BMenuItem) facePopupMenuItem[10]).setEnabled(false);
				((BMenuItem) edgePopupMenuItem[12]).setEnabled(false);
			} else {
				editMenuItem[1].setEnabled(true);
			}
		
                editMappingAction.setEnabled(mesh.getMappingData() != null);                
                
		if (mesh.isClosed()) {
			((BMenuItem) vertexMenuItem[8]).setEnabled(false);
			((BMenuItem) vertexMenuItem[9]).setEnabled(false);
			((BMenuItem) vertexPopupMenuItem[8]).setEnabled(false);
			((BMenuItem) vertexPopupMenuItem[9]).setEnabled(false);
			((BMenuItem) edgeMenuItem[10]).setEnabled(false);
			((BMenuItem) edgeMenuItem[11]).setEnabled(false);
			((BMenuItem) edgePopupMenuItem[10]).setEnabled(false);
			((BMenuItem) edgePopupMenuItem[11]).setEnabled(false);
			((BMenuItem) meshMenuItem[3]).setEnabled(false);
			((BMenuItem) meshMenuItem[4]).setEnabled(false);
			
                        unfoldMeshAction.setEnabled(mesh.getSeams() != null);
                        
		} else {
			((BMenuItem) vertexMenuItem[8]).setEnabled(true);
			((BMenuItem) vertexPopupMenuItem[8]).setEnabled(true);
			((BMenuItem) edgeMenuItem[10]).setEnabled(true);
			((BMenuItem) edgePopupMenuItem[10]).setEnabled(true);
			((BMenuItem) vertexMenuItem[9]).setEnabled(true);
			((BMenuItem) vertexPopupMenuItem[9]).setEnabled(true);
			((BMenuItem) edgeMenuItem[11]).setEnabled(true);
			((BMenuItem) edgePopupMenuItem[11]).setEnabled(true);
			((BMenuItem) meshMenuItem[3]).setEnabled(true);
			((BMenuItem) meshMenuItem[4]).setEnabled(true);
			unfoldMeshAction.setEnabled(true);
		}
		if (mesh.getSeams() != null) {
			for (int j = 15; j <= 19; j++) {
				((BMenuItem) edgeMenuItem[j]).setEnabled(true);
				((BMenuItem) edgePopupMenuItem[j]).setEnabled(true);
			}

		} else {
			for (int j = 15; j <= 19; j++) {
				((BMenuItem) edgeMenuItem[j]).setEnabled(false);
				((BMenuItem) edgePopupMenuItem[j]).setEnabled(false);
			}
		}
		// ( (BMenuItem) edgeMenuItem[4] ).setEnabled( false );
		templateItem
				.setEnabled(theView[currentView].getTemplateImage() != null);
		Skeleton s = mesh.getSkeleton();
		Joint selJoint = s.getJoint(view.getSelectedJoint());
		((BMenuItem) skeletonMenuItem[0]).setEnabled(selJoint != null);
		((BMenuItem) skeletonMenuItem[1]).setEnabled(selJoint != null
				&& selJoint.children.length == 0);
		((BMenuItem) skeletonMenuItem[2]).setEnabled(selJoint != null);
		((BMenuItem) skeletonMenuItem[4]).setEnabled(any);
		((BMenuItem) edgeMenuItem[14]).setEnabled(true);
		((BMenuItem) edgePopupMenuItem[14]).setEnabled(true);
	}

	/**
	 * Gets the action direction currently selected
	 * 
	 * @return The actionDirection value
	 */
	public int getActionDirection() {
		return PolyMesh.NORMAL;
	}

	/**
	 * Changes the interactive smooth level, if appropriate
	 * 
	 * @param amount
	 *                The quantity by which the level should be changed
	 */
	public void changeInteractiveSmoothLevel(int amount) {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		int level;
		if (mesh.getSmoothingMethod() != Mesh.APPROXIMATING)
			return;
		mesh = (PolyMesh) objInfo.object;
		level = mesh.getInteractiveSmoothLevel();
		if (level + amount > 0) {
			mesh.setInteractiveSmoothLevel(level + amount);
			ispin.setValue(new Integer(level + amount));
			objectChanged();
			updateImage();
		}
	}

	/**
	 * Toggles live smoothing on/off
	 */
	public void toggleSmoothing() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		if (realView) {
			mesh.setSmoothingMethod(smoothingMethod);
			realView = false;
		} else {
			smoothingMethod = mesh.getSmoothingMethod();
			mesh.setSmoothingMethod(Mesh.NO_SMOOTHING);
			realView = true;
		}
		objectChanged();
		updateImage();
	}

	/**
	 * Toggles live mirror on/off
	 */
	public void toggleMirror() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		if (realMirror) {
			mesh.setMirrorState(mirror);
			mesh.getMirroredMesh();
			realMirror = false;
		} else {
			mirror = mesh.getMirrorState();
			mesh.setMirrorState(PolyMesh.NO_MIRROR);
			realMirror = true;
		}
		objectChanged();
		updateImage();

	}

	public void selectTool(int tool) {
		switch (tool) {
		case RESHAPE_TOOL:
			tools.selectTool(reshapeMeshTool);
			break;
		case SKEW_TOOL:
			tools.selectTool(skewMeshTool);
			break;
		case TAPER_TOOL:
			tools.selectTool(taperMeshTool);
			break;
		case BEVEL_TOOL:
			tools.selectTool(bevelTool);
			break;
		case EXTRUDE_TOOL:
			tools.selectTool(extrudeTool);
			break;
		case EXTRUDE_CURVE_TOOL:
			tools.selectTool(extrudeCurveTool);
			break;
		case THICKEN_TOOL:
			tools.selectTool(thickenMeshTool);
			break;
		case CREATE_FACE_TOOL:
			tools.selectTool(createFaceTool);
			break;
		case KNIFE_TOOL:
			tools.selectTool(knifeTool);
			break;
		case SEW_TOOL:
			tools.selectTool(sewTool);
			break;
		case SKELETON_TOOL:
			tools.selectTool(skeletonTool);
			break;
		}
	}

	/**
	 * Toggles manipulators betwwen 2D and 3D (to be removed presumably
	 */
	public void toggleManipulator() {
		if (currentTool instanceof AdvancedEditingTool) {
			PolyMeshViewer view = (PolyMeshViewer) getView();
			((AdvancedEditingTool) currentTool).toggleManipulator(view);
			view.repaint();
		}
	}

	/**
	 * Toggles manipulator view mode (i.e. X,Y,Z U,V and N, P, Q)
	 */
	public void toggleManipulatorViewMode() {
		PolyMeshViewer view = (PolyMeshViewer) getView();
		ArrayList manipulators = view.getManipulators();
		Iterator iter = manipulators.iterator();
		Manipulator manipulator;
		while (iter.hasNext()) {
			manipulator = (Manipulator) iter.next();
			manipulator.toggleViewMode();
		}
		view.repaint();
	}

	/**
	 * Edits AoI keystrokes
	 */
	public void editKeystrokes() {
		BorderContainer bc = new BorderContainer();
		KeystrokePreferencesPanel keystrokePanel = new KeystrokePreferencesPanel();
		bc.add(keystrokePanel, BorderContainer.CENTER);
		PanelDialog dlg = new PanelDialog(this, Translate.text("keystrokes"),
				bc);
		if (!dlg.clickedOk())
			return;
		keystrokePanel.saveChanges();
	}

	/**
	 * Deletes any keystroke script associated to the PolyMesh plugin
	 */
	public void cleanKeystrokes() {
		KeystrokeRecord[] keys = KeystrokeManager.getAllRecords();
		for (int i = 0; i < keys.length; i++) {
			if (keys[i].getName().endsWith("(PolyMesh)"))
				KeystrokeManager.removeRecord(keys[i]);
		}
		try {
			KeystrokeManager.saveRecords();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Reloads keystroke scripts shipped with the PolyMesh plugin
	 */
	public void reloadKeystrokes() {
		cleanKeystrokes();
		try {
			InputStream in = getClass()
					.getResourceAsStream("/PMkeystrokes.xml");
			KeystrokeManager.addRecordsFromXML(in);
			in.close();
			KeystrokeManager.saveRecords();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Toggles help mode on/off
	 */
	public void toggleHelpMode() {
		Manipulator.toggleHelpMode();
	}

	/**
	 * Called when a key has been pressed
	 * 
	 * @param e
	 *                The KeyPressedEvent
	 */
	protected void keyPressed(KeyPressedEvent e) {
		((PolyMeshViewer) getView()).keyPressed(e);
		valueWidget.keyPressed(e);
		KeystrokeManager.executeKeystrokes(e, this);
	}

	private void deactivateTools() {
		pointTool.deactivate();
		edgeTool.deactivate();
		faceTool.deactivate();
	}

	private void activateTools() {
		pointTool.activate();
		edgeTool.activate();
		faceTool.activate();
	}

	/**
	 * Connects selected vertices
	 */
	private void doConnectVertices() {
		PolyMesh theMesh = (PolyMesh) objInfo.object;

		PolyMesh prevMesh = (PolyMesh) theMesh.duplicate();
		if (selectMode == POINT_MODE) {
			int[] indices = getIndicesFromSelection(selected);
			theMesh.connectVertices(indices);
			setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
					new Object[] { theMesh, prevMesh }));
			objectChanged();
			updateImage();

		}
	}

	/**
	 * Gets the selected points, edges or faces as an integer array given
	 * the boolean selection array
	 * 
	 * @param selected
	 *                The boolean
	 * @return The indicesFromSelection value
	 */
	private int[] getIndicesFromSelection(boolean[] selected) {
		int count = 0;
		for (int i = 0; i < selected.length; ++i)
			if (selected[i])
				++count;
		int[] indices = new int[count];
		count = 0;
		for (int i = 0; i < selected.length; ++i)
			if (selected[i])
				indices[count++] = i;
		return indices;
	}

	/**
	 * Divides selected edges into n segements
	 * 
	 * @param ev
	 *                The command event
	 */
	private void doDivideEdges(CommandEvent ev) {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		boolean[] sel = null;

		if (ev.getWidget() == divideMenuItem[0]
				|| ev.getWidget() == popupDivideMenuItem[0]) {
			sel = mesh.divideEdges(selected, 2);
		} else if (ev.getWidget() == divideMenuItem[1]
				|| ev.getWidget() == popupDivideMenuItem[1]) {
			sel = mesh.divideEdges(selected, 3);
		} else if (ev.getWidget() == divideMenuItem[2]
				|| ev.getWidget() == popupDivideMenuItem[2]) {
			sel = mesh.divideEdges(selected, 4);
		} else if (ev.getWidget() == divideMenuItem[3]
				|| ev.getWidget() == popupDivideMenuItem[3]) {
			sel = mesh.divideEdges(selected, 5);
		} else if (ev.getWidget() == divideMenuItem[4]
				|| ev.getWidget() == popupDivideMenuItem[4]) {
			DivideDialog dlg = new DivideDialog();
			int num = dlg.getNumber();
			if (num > 0)
				sel = mesh.divideEdges(selected, num);
		}
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		if (sel != null) {
			modes.selectTool(pointTool);
			setSelectionMode(POINT_MODE);
			setSelection(sel);
			updateMenus();
		}
		updateImage();

	}

	/**
	 * Called when a smoothing method command is selected
	 * 
	 * @param ev
	 *                The command event
	 */
	private void smoothingChanged(CommandEvent ev) {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, mesh.duplicate() }));
		Object source = ev.getWidget();
		for (int i = 0; i < smoothItem.length; i++)
			smoothItem[i].setState(false);
		/*
		 * for ( int i = 0; i < smoothItem.length; i++ ) if ( source ==
		 * smoothItem[i] ) { mesh.setSmoothingMethod( i );
		 * smoothItem[i].setState( true ); }
		 */
		if (source == smoothItem[1]) {
			mesh.setSmoothingMethod(Mesh.APPROXIMATING);
			smoothItem[1].setState(true);
//		} else if (source == smoothItem[1]) {
//			mesh.setSmoothingMethod(Mesh.SMOOTH_SHADING);
//			smoothItem[1].setState(true);
		} else {
			mesh.setSmoothingMethod(Mesh.NO_SMOOTHING);
			smoothItem[0].setState(true);
		}
		realView = false;
		doLevelContainerEnable();
		objectChanged();
		updateImage();

	}

	private void doLevelContainerEnable() {
		boolean enable = false;
		PolyMesh mesh = (PolyMesh) objInfo.object;
		if (mesh.getSmoothingMethod() == Mesh.APPROXIMATING)
			enable = true;
		Iterator iter = levelContainer.getChildren().iterator();
		Widget w;
		while (iter.hasNext()) {
			w = (Widget) iter.next();
			w.setEnabled(enable);
		}
	}

	/**
	 * Smoothes the mesh
	 */
	private void doSmoothMesh() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		mesh.smoothWholeMesh(-1, false, 1, true);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		updateImage();

	}

	/**
	 * Subdivides the mesh
	 */
	private void doSubdivideMesh() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		boolean[] selected = new boolean[mesh.getFaces().length];
		for (int i = 0; i < selected.length; ++i)
			selected[i] = true;
		mesh.smooth(selected, true);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		updateImage();

	}

	/**
	 * Smoothes the mesh according to face selection
	 */
	private void doSmoothFaces() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		mesh.smooth(selected, false);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		updateImage();

	}

	/**
	 * Subdivides the mesh according to face selection
	 */
	private void doSubdivideFaces() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		mesh.smooth(selected, true);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		updateImage();

	}
	
	/**
	 * Selects edges surrounding face selection
	 */
	private void doOutlineFaces() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		Wedge[] edges = mesh.getEdges();
		Wface[] faces = mesh.getFaces();
		boolean[] edgeSel = new boolean[edges.length/2]; 
		for (int i = 0; i < faces.length; i++) {
			if (selected[i]) {
				int[] fe = mesh.getFaceEdges(faces[i]);
				for (int j = 0; j < fe.length; j++) {
					if (edges[fe[j]].face == -1 || edges[edges[fe[j]].hedge].face == -1) {
						continue;
					}
					if (selected[edges[fe[j]].face] && ! selected[ edges[edges[fe[j]].hedge].face] ) {
						if (fe[j] < edges.length/2) {
							edgeSel[fe[j]] = true;
						} else {
							edgeSel[edges[fe[j]].hedge] = true;
						}
					} else if (!selected[edges[fe[j]].face] && selected[ edges[edges[fe[j]].hedge].face] ) {
						if (fe[j] < edges.length/2) {
							edgeSel[fe[j]] = true;
						} else {
							edgeSel[edges[fe[j]].hedge] = true;
						}
					}
				}
			}
		}
		setSelectionMode(EDGE_MODE);
		setSelection(edgeSel);
		updateImage();
	}

	/**
	 * Move vertices menu command (normal)
	 */
	private void doMoveVerticesNormal() {
		move(selectMode, PolyMesh.NORMAL);
	}

	/**
	 * Move vertices menu command (x)
	 */
	private void doMoveVerticesX() {
		move(selectMode, PolyMesh.X);
	}

	/**
	 * Move vertices menu command (y)
	 */
	private void doMoveVerticesY() {
		move(selectMode, PolyMesh.Y);
	}

	/**
	 * Move vertices menu command (z)
	 */
	private void doMoveVerticesZ() {
		move(selectMode, PolyMesh.Z);
	}

	/**
	 * Move edges menu command (normal)
	 */
	private void doMoveEdgesNormal() {
		move(selectMode, PolyMesh.NORMAL);
	}

	/**
	 * Move edges menu command (x)
	 */
	private void doMoveEdgesX() {
		move(selectMode, PolyMesh.X);
	}

	/**
	 * Move edges menu command (y)
	 */
	private void doMoveEdgesY() {
		move(selectMode, PolyMesh.Y);
	}

	/**
	 * Move edges menu command (z)
	 */
	private void doMoveEdgesZ() {
		move(selectMode, PolyMesh.Z);
	}

	/**
	 * Move faces menu command (normal)
	 */
	private void doMoveFacesNormal() {
		move(selectMode, PolyMesh.NORMAL);
	}

	/**
	 * Move faces menu command (x)
	 */
	private void doMoveFacesX() {
		move(selectMode, PolyMesh.X);
	}

	/**
	 * Move faces menu command (y)
	 */
	private void doMoveFacesY() {
		move(selectMode, PolyMesh.Y);
	}

	/**
	 * Move faces menu command (z)
	 */
	private void doMoveFacesZ() {
		move(selectMode, PolyMesh.Z);
	}

	/**
	 * Generic move command
	 * 
	 * @param kind
	 *                Description of the Parameter
	 * @param direction
	 *                Description of the Parameter
	 */
	private void move(int kind, short direction) {
		if (valueWidget.isActivated())
			return;
		moveDirection = direction;
		Runnable callback = new Runnable() {

			public void run() {
				doMoveCallback();
			}
		};
		valueWidget.activate(callback);
	}

	/**
	 * Callback called when the value has changed in the value dialog (move)
	 */
	private void doMoveCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		switch (selectMode) {
		default:
		case POINT_MODE:
			mesh.moveVertices(valueSelection, valueWidget.getValue(),
					moveDirection);
			break;
		case EDGE_MODE:
			mesh.moveEdges(valueSelection, valueWidget.getValue(),
					moveDirection);
			break;
		case FACE_MODE:
			mesh.moveFaces(valueSelection, valueWidget.getValue(),
					moveDirection);
			break;
		}
		objectChanged();
		setSelection(valueSelection);
	}

	/**
	 * Bevel edges command
	 */
	private void doBevelEdges() {
		if (valueWidget.isActivated())
			return;
		Runnable callback = new Runnable() {
			public void run() {
				doBevelEdgesCallback();
			}
		};
		valueWidget.activate(callback);
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog
	 */
	private void doBevelEdgesCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		boolean[] sel = mesh.bevelEdges(valueSelection, valueWidget
				.getValue());
		objectChanged();
		setSelection(sel);
	}

	/**
	 * Bevel edges command
	 */
	private void doBevelVertices() {
		if (valueWidget.isActivated())
			return;
		Runnable callback = new Runnable() {

			public void run() {
				doBevelVerticesCallback();
			}
		};
		valueWidget.activate(callback);
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog
	 */
	private void doBevelVerticesCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		boolean[] sel = mesh.bevelVertices(valueSelection, valueWidget
				.getValue());
		objectChanged();
		setSelection(sel);
	}

	/**
	 * Validate button selected
	 */
	public void doValueWidgetValidate() {
		valueWidgetDialog.setVisible(false);
		PolyMesh mesh = (PolyMesh) objInfo.object;
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, priorValueMesh }));
	}

	/**
	 * Cancel button selected
	 */
	public void doValueWidgetAbort() {
		valueWidgetDialog.setVisible(false);
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		objectChanged();
		PolyMesh valueMesh = null;
		priorValueMesh = null;
		setSelection(valueSelection);
		updateImage();
	}

	public void prepareToShowValueWidget() {
		priorValueMesh = (PolyMesh)((PolyMesh) objInfo.object).duplicate();
		valueSelection = selected;
	}
	
	public void showValueWidget() {
		if (unseenValueWidgetDialog) {
			Window main = (Window) this.getComponent();
			Window dlg = (Window) valueWidgetDialog.getComponent();
			Point mp = main.getLocation();
			Rectangle mb = main.getBounds();
			Point dp = new Point();
			dp.x = mp.x + mb.width + 5;
			dp.y = mp.y;
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			if (dp.x + valueWidgetDialog.getBounds().width > screenSize.width) {
				dp.x = screenSize.width - valueWidget.getBounds().width;
			}
			dlg.setLocation(dp);
			unseenValueWidgetDialog = false;
		}
		valueWidgetDialog.setVisible(true);
	}
	
	public void hideValueWidget() {
		valueWidgetDialog.setVisible(false);
	}

	/**
	 * Face extrusion
	 * 
	 * @param ev
	 *                The command event
	 */
	private void doExtrude(CommandEvent ev) {

		if (valueWidget.isActivated())
			return;
		Widget w = ev.getWidget();
		if (w == extrudeItem[0] || w == popupExtrudeItem[0])
			direction = null;
		else if (w == extrudeItem[1] || w == popupExtrudeItem[1])
			direction = Vec3.vx();
		else if (w == extrudeItem[2] || w == popupExtrudeItem[2])
			direction = Vec3.vy();
		else if (w == extrudeItem[3] || w == popupExtrudeItem[3])
			direction = Vec3.vz();
		Runnable callback = new Runnable() {

			public void run() {
				doExtrudeCallback();
			}
		};
		valueWidget.activate(callback);
	}

	/**
	 * Edge extrusion
	 * 
	 * @param ev
	 *                The command event
	 */
	private void doExtrudeEdge(CommandEvent ev) {

		if (valueWidget.isActivated())
			return;
		Widget w = ev.getWidget();
		if (w == extrudeEdgeItem[0] || w == popupExtrudeEdgeItem[0])
			direction = null;
		else if (w == extrudeEdgeItem[1] || w == popupExtrudeEdgeItem[1])
			direction = Vec3.vx();
		else if (w == extrudeEdgeItem[2] || w == popupExtrudeEdgeItem[2])
			direction = Vec3.vy();
		else if (w == extrudeEdgeItem[3] || w == popupExtrudeEdgeItem[3])
			direction = Vec3.vz();
		Runnable callback = new Runnable() {

			public void run() {
				doExtrudeEdgeCallback();
			}
		};
		valueWidget.activate(callback);
	}

	/**
	 * Region extrusion
	 * 
	 * @param ev
	 *                Command event
	 */
	private void doExtrudeRegion(CommandEvent ev) {

		if (valueWidget.isActivated())
			return;
		Widget w = ev.getWidget();
		if (w == extrudeRegionItem[0] || w == popupExtrudeRegionItem[0])
			direction = null;
		else if (w == extrudeRegionItem[1] || w == popupExtrudeRegionItem[1])
			direction = Vec3.vx();
		else if (w == extrudeRegionItem[2] || w == popupExtrudeRegionItem[2])
			direction = Vec3.vy();
		else if (w == extrudeRegionItem[3] || w == popupExtrudeRegionItem[3])
			direction = Vec3.vz();
		Runnable callback = new Runnable() {

			public void run() {
				doExtrudeRegionCallback();
			}
		};
		valueWidget.activate(callback);
	}

	/**
	 * Edge Region extrusion
	 * 
	 * @param ev
	 *                Command event
	 */
	private void doExtrudeEdgeRegion(CommandEvent ev) {

		if (valueWidget.isActivated())
			return;
		Widget w = ev.getWidget();
		if (w == extrudeEdgeRegionItem[0] || w == popupExtrudeEdgeRegionItem[0])
			direction = null;
		else if (w == extrudeEdgeRegionItem[1]
				|| w == popupExtrudeEdgeRegionItem[1])
			direction = Vec3.vx();
		else if (w == extrudeEdgeRegionItem[2]
				|| w == popupExtrudeEdgeRegionItem[2])
			direction = Vec3.vy();
		else if (w == extrudeEdgeRegionItem[3]
				|| w == popupExtrudeEdgeRegionItem[3])
			direction = Vec3.vz();
		Runnable callback = new Runnable() {

			public void run() {
				doExtrudeEdgeRegionCallback();
			}
		};
		valueWidget.activate(callback);
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog
	 */
	private void doExtrudeCallback() {
		PolyMesh valueMesh = (PolyMesh) objInfo.object;
		valueMesh.copyObject(priorValueMesh);
		valueMesh.extrudeFaces(valueSelection, valueWidget.getValue(),
				direction);
		boolean[] sel = new boolean[valueMesh.getFaces().length];
		for (int i = 0; i < valueSelection.length; ++i)
			sel[i] = valueSelection[i];
		objectChanged();
		setSelection(sel);
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog
	 */
	private void doExtrudeEdgeCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		mesh.extrudeEdges(valueSelection, valueWidget.getValue(),
				direction);
		boolean[] sel = new boolean[mesh.getEdges().length / 2];
		for (int i = 0; i < valueSelection.length; ++i)
			sel[i] = valueSelection[i];
		objectChanged();
		setSelection(sel);
	}

	/**
	 * Mesh thickening
	 */
	private void doThickenMesh(CommandEvent ev) {

		if (valueWidget.isActivated())
			return;
		if (ev.getWidget() == meshMenuItem[3])
			thickenFaces = true;
		else
			thickenFaces = false;
		Runnable callback = new Runnable() {

			public void run() {
				doThickenMeshCallback();
			}
		};
		valueWidget.activate(callback);
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog
	 */
	private void doThickenMeshCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		mesh.thickenMesh(valueWidget.getValue(), thickenFaces);
		boolean[] sel = new boolean[mesh.getFaces().length];
		objectChanged();
		updateImage();
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog
	 */
	private void doExtrudeRegionCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		mesh.copyObject(priorValueMesh);
		mesh.extrudeRegion(valueSelection, valueWidget.getValue(),
				direction);
		boolean[] sel = new boolean[mesh.getFaces().length];
		for (int i = 0; i < valueSelection.length; ++i)
			sel[i] = valueSelection[i];
		objectChanged();
		setSelection(sel);
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog
	 */
	private void doExtrudeEdgeRegionCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		mesh.extrudeEdgeRegion(valueSelection, valueWidget.getValue(),
				direction);
		boolean[] sel = new boolean[mesh.getEdges().length / 2];
		for (int i = 0; i < valueSelection.length; ++i)
			sel[i] = valueSelection[i];
		objectChanged();
		setSelection(sel);
	}

	/**
	 * Loops insertion
	 */
	private void doInsertLoops() {

		if (valueWidget.isActivated())
			return;
		Runnable callback = new Runnable() {

			public void run() {
				doInsertLoopsCallback();
			}
		};
		valueWidget.setTempValueRange(0, 1.0);
		valueWidget.activate(0.5, callback);
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog
	 */
	private void doInsertLoopsCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		boolean sel[] = mesh.divideEdges(valueSelection, valueWidget
				.getValue());
		mesh.connectVertices(sel);
		objectChanged();
		setSelectionMode(POINT_MODE);
		setSelection(sel);
		updateImage();
	}

	/**
	 * Brings selected vertices to the mean sphere calculated from these
	 * vertices
	 */
	private void doMeanSphere() {

		if (valueWidget.isActivated())
			return;
		Vec3 origin;
		double radius;
		PolyMesh mesh = (PolyMesh) objInfo.object;
		priorValueMesh = (PolyMesh)mesh.duplicate();
		MeshVertex[] vert = priorValueMesh.getVertices();
		Vec3[] normals = priorValueMesh.getNormals();
		int count = 0;
		origin = new Vec3();
		radius = 0;
		for (int i = 0; i < vert.length; ++i)
			if (selected[i]) {
				++count;
				origin.add(vert[i].r);
			}
		vertDisplacements = new Vec3[count];
		origin.scale(1.0 / count);
		for (int i = 0; i < vert.length; ++i)
			if (selected[i])
				radius += vert[i].r.minus(origin).length();
		radius /= count;
		count = 0;
		for (int i = 0; i < vert.length; ++i)
			if (selected[i]) {
				vertDisplacements[count] = vert[i].r.minus(origin);
				if (vertDisplacements[count].length() < 1e-6)
					vertDisplacements[count] = new Vec3(normals[i]);
				vertDisplacements[count].scale(radius
						/ vertDisplacements[count].length());
				vertDisplacements[count].add(origin);
				vertDisplacements[count].subtract(vert[i].r);
				++count;
			}
		if (checkForNullMovement(vertDisplacements))
			return;
		Runnable callback = new Runnable() {

			public void run() {
				doBringCallback();
			}
		};
		valueWidget.activate(callback);
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog
	 */
	private void doBringCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		MeshVertex[] vert = mesh.getVertices();
		int count = 0;
		for (int i = 0; i < vert.length; ++i)
			if (selected[i]) {
				vert[i].r.add(vertDisplacements[count].times(valueWidget
						.getValue()));
				++count;
			}
		mesh.resetMesh();
		objectChanged();
		setSelection(selected);
	}

	/**
	 * Brings vertices onto the closest sphere portion
	 */
	private void doClosestSphere() {

		double a;
		double b;
		double c;
		double l;
		double la;
		double lb;
		double lc;
		double t;
		Vec3 origin;
		double radius;

		if (valueWidget.isActivated())
			return;
		PolyMesh mesh = (PolyMesh) objInfo.object;
		priorValueMesh = (PolyMesh)mesh.duplicate();
		MeshVertex[] vert = priorValueMesh.getVertices();
		Vec3[] normals = priorValueMesh.getNormals();
		int count = 0;
		origin = new Vec3();
		radius = 0;
		l = 0;
		for (int i = 0; i < vert.length; ++i)
			if (selected[i]) {
				++count;
				origin.add(vert[i].r);
			}
		origin.scale(1.0 / count);
		a = origin.x;
		b = origin.y;
		c = origin.z;
		radius = 1;
		double delta = 1;
		double newa;
		double newb;
		double newc;
		int dummy = 0;
		while (delta > 0.01 && dummy < 10000) {
			l = 0;
			for (int i = 0; i < vert.length; ++i)
				if (selected[i])
					l += Math.sqrt((vert[i].r.x - a) * (vert[i].r.x - a)
							+ (vert[i].r.y - b) * (vert[i].r.y - b)
							+ (vert[i].r.z - c) * (vert[i].r.z - c));
			l /= count;
			la = lb = lc = 0;
			for (int i = 0; i < vert.length; ++i)
				if (selected[i]) {

					t = Math.sqrt((vert[i].r.x - a) * (vert[i].r.x - a)
							+ (vert[i].r.y - b) * (vert[i].r.y - b)
							+ (vert[i].r.z - c) * (vert[i].r.z - c));
					if (t < 1e-6)
						continue;
					la += (a - vert[i].r.x) / t;
					lb += (b - vert[i].r.y) / t;
					lc += (c - vert[i].r.z) / t;
				}
			la /= count;
			lb /= count;
			lc /= count;
			newa = origin.x + l * la;
			newb = origin.y + l * lb;
			newc = origin.z + l * lc;
			delta = 0;
			if (Math.max(Math.abs(newa), Math.abs(a)) > 1e-6)
				delta += Math.abs(newa - a)
						/ Math.max(Math.abs(newa), Math.abs(a));
			if (Math.max(Math.abs(newb), Math.abs(b)) > 1e-6)
				delta += Math.abs(newb - b)
						/ Math.max(Math.abs(newb), Math.abs(b));
			if (Math.max(Math.abs(newc), Math.abs(c)) > 1e-6)
				delta += Math.abs(newc - c)
						/ Math.max(Math.abs(newc), Math.abs(c));
			if (Math.max(Math.abs(radius), Math.abs(l)) > 1e-6)
				delta += Math.abs(l - radius)
						/ Math.max(Math.abs(radius), Math.abs(l));
			a = newa;
			b = newb;
			c = newc;
			radius = l;
			// System.out.println( delta + " : " + newa + " " + newb + " " +
			// newc + " " + l );
			++dummy;
		}
		origin = new Vec3(a, b, c);
		// System.out.println( dummy );
		if (dummy >= 10000)
			System.out.println("Warning: Too many iterations");
		vertDisplacements = new Vec3[count];
		count = 0;
		// System.out.println( a + " " + b + " " + c + " " + radius );
		for (int i = 0; i < vert.length; ++i)
			if (selected[i]) {
				vertDisplacements[count] = vert[i].r.minus(origin);
				if (vertDisplacements[count].length() < 1e-6)
					vertDisplacements[count] = new Vec3(normals[i]);
				vertDisplacements[count].scale(radius
						/ vertDisplacements[count].length());
				vertDisplacements[count].add(origin);
				vertDisplacements[count].subtract(vert[i].r);
				++count;
			}
		if (checkForNullMovement(vertDisplacements))
			return;
		Runnable callback = new Runnable() {

			public void run() {
				doBringCallback();
			}
		};
		valueWidget.activate(callback);
	}

	/**
	 * Checks if vertices movements will actually result in a displacement
	 * 
	 * @param movement
	 *                Vertices movements
	 * @return True if a displacement will occur
	 */
	private boolean checkForNullMovement(Vec3[] movement) {
		double sum = 0;
		for (int i = 0; i < movement.length; ++i)
			sum += movement[i].length();
		if (sum / movement.length < 1e-6) {
			new BStandardDialog(Translate.text("polymesh:errorTitle"), UIUtilities
					.breakString(Translate.text("polymesh:nullMovement")),
					BStandardDialog.ERROR).showMessageDialog(null);
			return true;
		}
		return false;
	}

	/**
	 * Brings selected vertices to a plane calculated from these vertices
	 */
	private void doPlane() {

		if (valueWidget.isActivated())
			return;
		Vec3 origin;
		double radius;
		PolyMesh mesh = (PolyMesh) objInfo.object;
		priorValueMesh = (PolyMesh)mesh.duplicate();
		MeshVertex[] vert = priorValueMesh.getVertices();
		Vec3[] normals = priorValueMesh.getNormals();
		Vec3 norm = new Vec3();
		int count = 0;
		origin = new Vec3();
		radius = 0;
		for (int i = 0; i < vert.length; ++i)
			if (selected[i]) {
				++count;
				origin.add(vert[i].r);
				norm.add(normals[i]);
			}
		vertDisplacements = new Vec3[count];
		origin.scale(1.0 / count);
		/*
		 * if ( norm.length() < 1e-6 ) { new BStandardDialog( "",
		 * UIUtilities.breakString( Translate.text( "cantFlatten" ) ),
		 * BStandardDialog.ERROR ).showMessageDialog( null ); return; }
		 */
		norm.normalize();
		for (int i = 0; i < vert.length; ++i)
			if (selected[i])
				radius += vert[i].r.minus(origin).length();
		radius /= count;
		count = 0;
		for (int i = 0; i < vert.length; ++i)
			if (selected[i])
				vertDisplacements[count++] = norm.times(-norm.dot(vert[i].r
						.minus(origin)));
		if (checkForNullMovement(vertDisplacements))
			return;
		Runnable callback = new Runnable() {

			public void run() {
				doBringCallback();
			}
		};
		valueWidget.activate(callback);
	}

//	/**
//	 * Description of the Method
//	 */
//	private void enableNormalFunction() {
////		editMenu.setEnabled(true);
////		vertexMenu.setEnabled(true);
////		vertexPopupMenu.setEnabled(true);
////		edgeMenu.setEnabled(true);
////		faceMenu.setEnabled(true);
////		skeletonMenu.setEnabled(true);
////		okButton.setEnabled(true);
////		tools.selectTool(defaultTool);
////		activateTools();
//	}
//
//	/**
//	 * Description of the Method
//	 */
//	private void disableNormalFunction() {
////		editMenu.setEnabled(false);
////		vertexMenu.setEnabled(false);
////		vertexPopupMenu.setEnabled(false);
////		edgeMenu.setEnabled(false);
////		faceMenu.setEnabled(false);
////		skeletonMenu.setEnabled(false);
////		okButton.setEnabled(false);
////		tools.selectTool(altTool);
////		deactivateTools();
//	}

	/**
	 * Brings normal to current selection
	 */
	private void bringNormal() {
		Camera theCamera = theView[currentView].getCamera();
		Vec3 orig = new Vec3(0, 0, 0);
		Vec3 zdir;
		Vec3 updir;
		PolyMesh mesh = (PolyMesh) objInfo.object;
		Vec3[] norm = mesh.getNormals();
		Wedge[] ed = mesh.getEdges();
		Wface[] f = mesh.getFaces();
		if (selectMode == POINT_MODE) {
			for (int i = 0; i < selected.length; ++i) {
				if (selected[i])
					orig.add(norm[i]);
			}
		} else if (selectMode == EDGE_MODE) {
			for (int i = 0; i < selected.length; ++i) {
				if (selected[i]) {
					orig.add(norm[ed[i].vertex]);
					orig.add(norm[ed[ed[i].hedge].vertex]);
				}
			}
		} else {
			for (int i = 0; i < selected.length; ++i) {
				if (selected[i]) {
					int[] fv = mesh.getFaceVertices(f[i]);
					Vec3 v = new Vec3();
					for (int j = 0; j < fv.length; ++j)
						v.add(norm[fv[j]]);
					v.normalize();
					orig.add(v);
				}
			}
		}
		if (orig.length() < 1e-6)
			return;
		orig.normalize();
		orig.scale(theCamera.getCameraCoordinates().getOrigin().length());
		zdir = orig.times(-1.0);
		updir = new Vec3(zdir.y, -zdir.x, 0.0);
		if (updir.length() < 1e-6)
			updir = new Vec3(0.0, zdir.z, -zdir.y);
		theCamera.setCameraCoordinates(new CoordinateSystem(orig, zdir, updir));
		theView[currentView].setOrientation(ViewerCanvas.VIEW_OTHER);
		updateImage();

	}

	public void triggerPopupEvent(WidgetMouseEvent e) {
		if (selectMode == POINT_MODE)
			vertexPopupMenu.show(e);
		else if (selectMode == EDGE_MODE)
			edgePopupMenu.show(e);
		else
			facePopupMenu.show(e);
	}

	public void showPopupMenu(Widget w, int x, int y) {
		if (selectMode == POINT_MODE)
			vertexPopupMenu.show(w, x, y);
		else if (selectMode == EDGE_MODE)
			edgePopupMenu.show(w, x, y);
		else
			facePopupMenu.show(w, x, y);
	}

	/**
	 * Sets the number of columns displayed by a spinner
	 * 
	 * @param spinner
	 *                The concerned BSpinner
	 * @param numCol
	 *                The new number of columns to show
	 */
	public static void setSpinnerColumns(BSpinner spinner, int numCol) {
		NumberEditor ed = (NumberEditor) ((JSpinner) spinner.getComponent())
				.getEditor();
		JFormattedTextField field = ed.getTextField();
		field.setColumns(numCol);
		((JSpinner) spinner.getComponent()).setEditor(ed);
	}


	/**
	 * Sets the smoothness of selected vertices or edges
	 */
	void setSmoothnessCommand() {
		final PolyMesh theMesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) theMesh.duplicate();
		final Wvertex vt[] = (Wvertex[]) theMesh.getVertices();
		final Wedge ed[] = theMesh.getEdges();
		final boolean pointmode = (selectMode == POINT_MODE);
		final ActionProcessor processor = new ActionProcessor();
		float value;
		final ValueSlider smoothness;
		int i;

		for (i = 0; i < selected.length && !selected[i]; i++)
			;
		if (i == selected.length)
			return;
		/*
		 * if ( pointmode ) valueWidget.getValue() = vt[i].smoothness; else
		 */
		value = ed[i].smoothness;
		value = 0.001f * (Math.round(valueWidget.getValue() * 1000.0f));
		smoothness = new ValueSlider(0.0, 1.0, 1000, (double) valueWidget
				.getValue());
		smoothness.addEventLink(ValueChangedEvent.class, new Object() {
			void processEvent() {
				processor.addEvent(new Runnable() {
					public void run() {
						float s = (float) smoothness.getValue();
						if (s < 0)
							s = 0;
						if (s > 1)
							s = 1;
						for (int i = 0; i < selected.length; i++)
							if (selected[i]) {
								/*
								 * if ( pointmode ) vt[i].smoothness = s; else {
								 */
								ed[i].smoothness = s;
								ed[ed[i].hedge].smoothness = s;
								// }
							}
						theMesh
								.setSmoothingMethod(theMesh
										.getSmoothingMethod());
						objectChanged();
						updateImage();

					}
				});
			}
		});
		ComponentsDialog dlg = new ComponentsDialog(this, Translate
				.text(pointmode ? "setPointSmoothness" : "setEdgeSmoothness"),
				new Widget[] { smoothness }, new String[] { Translate
						.text("Smoothness") });
		processor.stopProcessing();
		if (dlg.clickedOk())
			setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
					new Object[] { theMesh, prevMesh }));
		else {
			theMesh.copyObject(prevMesh);
			objectChanged();
			updateImage();

		}
	}

	public void doEdgeSliderChanged()

	{
		PolyMesh theMesh = (PolyMesh) objInfo.object;
		final Wedge ed[] = theMesh.getEdges();
		float s = (float) edgeSlider.getValue();
		if (s < 0)
			s = 0;
		if (s > 1)
			s = 1;
		for (int i = 0; i < selected.length; i++)
			if (selected[i]) {
				ed[i].smoothness = s;
				ed[ed[i].hedge].smoothness = s;
			}
		objectChanged();
		updateImage();
	}

	/**
	 * Bevel properties settings
	 */
	private void doBevelProperties() {
		BevelPropertiesDialog dlg = new BevelPropertiesDialog();
	}

	/**
	 * Gets the selectionDistance attribute of the PolyMeshEditorWindow
	 * object
	 * 
	 * @return The selectionDistance valueWidget.getValue()
	 */
	public int[] getSelectionDistance() {
		if (maxDistance != getTensionDistance() || selectionDistance == null)
			findSelectionDistance();
		return selectionDistance;
	}

	/**
	 * Calculate the distance (in edges) between each vertex and the nearest
	 * selected vertex.
	 */

	void findSelectionDistance() {
		int i;
		int j;
		int dist[] = new int[((PolyMesh) objInfo.object).getVertices().length];
		Wedge e[] = ((PolyMesh) objInfo.object).getEdges();
		Wface[] f = ((PolyMesh) objInfo.object).getFaces();

		maxDistance = getTensionDistance();

		// First, set each distance to 0 or -1, depending on whether that vertex
		// is part of the
		// current selection.

		if (selectMode == POINT_MODE)
			for (i = 0; i < dist.length; i++)
				dist[i] = selected[i] ? 0 : -1;
		else if (selectMode == EDGE_MODE) {
			for (i = 0; i < dist.length; i++)
				dist[i] = -1;
			for (i = 0; i < selected.length; i++)
				if (selected[i])
					dist[e[i].vertex] = dist[e[e[i].hedge].vertex] = 0;
		} else {
			for (i = 0; i < dist.length; i++)
				dist[i] = -1;
			for (i = 0; i < selected.length; i++)
				if (selected[i]) {
					int[] vf = ((PolyMesh) objInfo.object)
							.getFaceVertices(f[i]);
					for (j = 0; j < vf.length; ++j)
						dist[vf[j]] = 0;
				}
		}

		// Now extend this outward up to maxDistance.

		for (i = 0; i < maxDistance; i++)
			for (j = 0; j < e.length / 2; j++) {
				if (dist[e[j].vertex] == -1 && dist[e[e[j].hedge].vertex] == i)
					dist[e[j].vertex] = i + 1;
				else if (dist[e[e[j].hedge].vertex] == -1
						&& dist[e[j].vertex] == i)
					dist[e[e[j].hedge].vertex] = i + 1;
			}
		selectionDistance = dist;
	}

	/**
	 * Determine whether we are in tolerant selection mode.
	 * 
	 * @return The tolerant valueWidget.getValue()
	 */

	public boolean isTolerant() {
		return tolerant;
	}

	/**
	 * Set whether to use tolerant selection mode.
	 * 
	 * @param tol
	 *                The new tolerant valueWidget.getValue()
	 */

	public void setTolerant(boolean tol) {
		tolerant = tol;
	}

	/**
	 * Get the extra texture parameter which was added the mesh to keep
	 * track of which faces are hidden.
	 * 
	 * @return The extraParameter valueWidget.getValue()
	 */

	//public TextureParameter getExtraParameter() {
	//	return hideFaceParam;
	//}

	/**
	 * Get which faces are hidden. This may be null, which means that all
	 * faces are visible.
	 * 
	 * @return The hiddenFaces valueWidget.getValue()
	 */

	public boolean[] getHiddenFaces() {
		return hideFace;
	}

	/**
	 * Gets the selectionMode attribute of the PolyMeshViewer object
	 * 
	 * @return The selectionMode valueWidget.getValue()
	 */
	public int getSelectionMode() {
		return selectMode;
	}

	/**
	 * Get an array of flags telling which parts of the mesh are currently
	 * selected. Depending on the current selection mode, these flags may
	 * correspond to vertices, edges or faces.
	 * 
	 * @return The selection valueWidget.getValue()
	 */

	public boolean[] getSelection() {
		return selected;
	}

	public AdvancedEditingTool.SelectionProperties getSelectionProperties() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		MeshVertex v[] = (MeshVertex[]) mesh.getVertices();
		Wedge e[] = mesh.getEdges();
		Wface f[] = mesh.getFaces();
		Vec3[] normals = null;
		Vec3[] features = null;
		switch (selectMode) {
		case POINT_MODE:
			normals = mesh.getNormals();
			features = new Vec3[v.length + 2];
			break;
		case EDGE_MODE:
			normals = mesh.getEdgeNormals();
			features = new Vec3[e.length / 2 + 2];
			break;
		case FACE_MODE:
			normals = mesh.getFaceNormals();
			features = new Vec3[f.length + 2];
			break;
		}
		Vec3 normal = new Vec3();
		/*
		 * int count = 0; for (int i =0; i < selected.length; i++) if
		 * (selected[i]) ++count; Vec3[] features = new Vec3[count+1];
		 * features[0] = new Vec3(); count = 1; Vec3 middle; for (int i =0; i <
		 * selected.length; i++) { if (selected[i]) { switch(selectMode) { case
		 * POINT_MODE : features[0].add(v[i].r); features[count] = new
		 * Vec3(v[i].r); break; case EDGE_MODE : middle =
		 * v[e[i].vertex].r.plus(v[e[e[i].hedge].vertex].r); middle.scale(0.5);
		 * features[0].add(middle); features[count] = middle; break; case
		 * FACE_MODE : int[] fv = mesh.getFaceVertices(f[i]); middle = new
		 * Vec3(); for (int j = 0; j < fv.length; j++) middle.add(v[fv[j]].r);
		 * middle.scale(1.0/(double)fv.length); features[0].add(middle);
		 * features[count] = middle; } normal.add(normals[i]); count++; } }
		 */
		int count = 0;
		features[0] = new Vec3();
		features[1] = new Vec3();
		count = 0;
		Vec3 middle;
		for (int i = 0; i < selected.length; i++) {
			switch (selectMode) {
			case POINT_MODE:
				if (selected[i])
					features[0].add(v[i].r);
				features[i + 2] = v[i].r;
				break;
			case EDGE_MODE:
				middle = v[e[i].vertex].r.plus(v[e[e[i].hedge].vertex].r);
				middle.scale(0.5);
				if (selected[i])
					features[0].add(middle);
				features[i + 2] = middle;
				break;
			case FACE_MODE:
				int[] fv = mesh.getFaceVertices(f[i]);
				middle = new Vec3();
				for (int j = 0; j < fv.length; j++)
					middle.add(v[fv[j]].r);
				middle.scale(1.0 / (double) fv.length);
				if (selected[i])
					features[0].add(middle);
				features[i + 2] = middle;
			}
			if (selected[i]) {
				normal.add(normals[i]);
				count++;
			}
		}
		double coef = 1.0 / (double) (count);
		features[0].scale(coef);
		CoordinateSystem coords = null;
		if (normal.length() > 0) {
			normal.normalize();
			Vec3 updir = Vec3.vx();
			if (updir.dot(normal) < 0.9) {
				updir = normal.cross(updir);
				updir.normalize();
			} else {
				updir = normal.cross(Vec3.vy());
				updir.normalize();
			}
			coords = new CoordinateSystem(new Vec3(0, 0, 0), normal, updir);
		}
		AdvancedEditingTool.SelectionProperties props = new AdvancedEditingTool.SelectionProperties();
		props.featurePoints = features;
		props.specificCoordinateSystem = coords;
		return props;
	}

	/**
	 * When the selection mode changes, do our best to convert the old
	 * selection to the new mode.
	 * 
	 * @param mode
	 *                The new selectionMode valueWidget.getValue()
	 */

	public void setSelectionMode(int mode) {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		MeshVertex v[] = (MeshVertex[]) mesh.getVertices();
		Wedge e[] = mesh.getEdges();
		Wface f[] = mesh.getFaces();
		boolean newSel[];
		int i;

		if (mode == selectMode)
			return;
		if (mode == POINT_MODE) {
			overlayVertexEdgeFace.setVisibleChild(vertexContainer);
			newSel = new boolean[v.length];
			if (selectMode == FACE_MODE) {
				for (i = 0; i < f.length; i++)
					if (selected[i]) {
						int[] vf = ((PolyMesh) objInfo.object)
								.getFaceVertices(f[i]);
						for (int j = 0; j < vf.length; ++j)
							newSel[vf[j]] = true;
					}
			} else {
				for (i = 0; i < e.length / 2; i++)
					if (selected[i])
						newSel[e[i].vertex] = newSel[e[e[i].hedge].vertex] = true;
			}
		} else if (mode == EDGE_MODE) {
			overlayVertexEdgeFace.setVisibleChild(edgeContainer);
			newSel = new boolean[e.length / 2];
			if (selectMode == POINT_MODE) {
				if (tolerant) {
					for (i = 0; i < e.length / 2; i++)
						newSel[i] = selected[e[i].vertex]
								| selected[e[e[i].hedge].vertex];
				} else {
					for (i = 0; i < e.length / 2; i++)
						newSel[i] = selected[e[i].vertex]
								& selected[e[e[i].hedge].vertex];
				}
			} else {
				for (i = 0; i < f.length; i++) {
					if (selected[i]) {
						int[] fe = ((PolyMesh) objInfo.object)
								.getFaceEdges(f[i]);
						for (int j = 0; j < fe.length; ++j) {
							if (fe[j] >= e.length / 2)
								newSel[e[fe[j]].hedge] = true;
							else
								newSel[fe[j]] = true;
						}
					}
				}
			}
		} else {
			overlayVertexEdgeFace.setVisibleChild(faceContainer);
			newSel = new boolean[f.length];
			if (selectMode == POINT_MODE) {
				if (tolerant) {
					for (i = 0; i < f.length; i++) {
						int[] vf = ((PolyMesh) objInfo.object)
								.getFaceVertices(f[i]);
						for (int j = 0; j < vf.length; ++j)
							newSel[i] |= selected[vf[j]];
					}
				} else {
					for (i = 0; i < f.length; i++) {
						newSel[i] = true;
						int[] vf = ((PolyMesh) objInfo.object)
								.getFaceVertices(f[i]);
						for (int j = 0; j < vf.length; ++j)
							newSel[i] &= selected[vf[j]];
					}
				}
			} else {
				int k;
				for (i = 0; i < f.length; i++) {
					if (!tolerant)
						newSel[i] = true;
					int[] fe = ((PolyMesh) objInfo.object).getFaceEdges(f[i]);
					for (int j = 0; j < fe.length; j++) {
						if (fe[j] >= e.length / 2)
							k = e[fe[j]].hedge;
						else
							k = fe[j];
						if (tolerant)
							newSel[i] |= selected[k];
						else
							newSel[i] &= selected[k];
					}
				}
			}
		}
		selectMode = mode;
		setSelection(newSel);
		if (modes.getSelection() != mode)
			modes.selectTool(modes.getTool(mode));
		layoutChildren();
		if (currentTool instanceof AdvancedEditingTool) {
			((AdvancedEditingTool) currentTool).selectionModeChanged(mode);
		}
		repaint();
	}

	/**
	 * Set which faces are hidden.  Pass null to show all faces.
	 * 
	 * @param hidden
	 *                The new hiddenFaces valueWidget.getValue()
	 */
	public void setHiddenFaces(boolean hidden[])
	{
		PolyMesh mesh = (PolyMesh) objInfo.object;
		hideFace = hidden;
		hideVert = new boolean [mesh.getVertices().length];
		if (hideFace != null)
		{
			for (int i = 0; i < hideVert.length; i++)
				hideVert[i] = true;
			Wface face[] = mesh.getFaces();
			for (int i = 0; i < face.length; i++)

				if (!hideFace[i]) {
					int[] vf = mesh.getFaceVertices(face[i]);
					for (int j = 0; j < vf.length; ++j)
						hideVert[vf[j]] = false;
				}
		}
		else
		{
			for (int i = 0; i < hideVert.length; i++)
				hideVert[i] = false;
		}
		FaceParameterValue val = (FaceParameterValue) objInfo.object.getParameterValue(faceIndexParam);
		double param[] = val.getValue();
		for (int i = 0; i < param.length; i++)
			param[i] = i;
		val.setValue(param);
		objInfo.object.setParameterValue(faceIndexParam, val);
		objInfo.clearCachedMeshes();
		updateImage();
	}
	
	public boolean selectOnlyVisible() {
		return frontSelectCB.getState();
	}

	/**
	 * Sets point or edge selection
	 * 
	 * @param sel
	 *                The new selection array
	 */
	public void setSelection(boolean sel[]) {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		Wvertex[] verts = (Wvertex[]) mesh.getVertices();
		Wedge[] edges = mesh.getEdges();
		if (selectMode == POINT_MODE && sel.length == verts.length) {
			selected = sel;
		} else if (selectMode == EDGE_MODE
				&& sel.length == mesh.getEdges().length / 2) {
			selected = sel;
		} else if (selectMode == FACE_MODE
				&& sel.length == mesh.getFaces().length) {
			selected = sel;
		}
		findSelectionDistance();
		currentTool.getWindow().updateMenus();
		for (ViewerCanvas view : theView)
		      view.repaint();
		repaint();
	}

	/**
	 * Set the object being edited in this window.
	 * 
	 * @param obj
	 *                The new object valueWidget.getValue()
	 */

	public void setObject(Object3D obj) {
		objInfo.object = obj;
		objInfo.clearCachedMeshes();
	}

	/**
	 * Sets a new mesh
	 * 
	 * @param mesh
	 *                The new mesh valueWidget.getValue()
	 */
	public void setMesh(Mesh mesh) {
		PolyMesh obj = (PolyMesh) mesh;
		setObject(obj);
		hideVert = new boolean[mesh.getVertices().length];
		for (int i = 0; i < theView.length; i++) {
			if (getSelectionMode() == PolyMeshEditorWindow.POINT_MODE
					&& selected.length != obj.getVertices().length)
				((PolyMeshViewer) theView[i]).visible = new boolean[obj
						.getVertices().length];
			if (getSelectionMode() == PolyMeshEditorWindow.EDGE_MODE
					&& selected.length != obj.getEdges().length / 2)
				((PolyMeshViewer) theView[i]).visible = new boolean[obj
						.getEdges().length];
			if (getSelectionMode() == PolyMeshEditorWindow.FACE_MODE
					&& selected.length != obj.getFaces().length)
				((PolyMeshViewer) theView[i]).visible = new boolean[obj
						.getFaces().length];
		}
		if (getSelectionMode() == PolyMeshEditorWindow.POINT_MODE
				&& selected.length != obj.getVertices().length)
			selected = new boolean[obj.getVertices().length];
		if (getSelectionMode() == PolyMeshEditorWindow.EDGE_MODE
				&& selected.length != obj.getEdges().length / 2)
			selected = new boolean[obj.getEdges().length / 2];
		if (getSelectionMode() == PolyMeshEditorWindow.FACE_MODE
				&& selected.length != obj.getFaces().length)
			selected = new boolean[obj.getFaces().length];
		if (hideFace != null)
	    {
	      boolean oldHideFace[] = hideFace;
	      FaceParameterValue val = (FaceParameterValue) getObject().object.getParameterValue(faceIndexParam);
	      double param[] = val.getValue();
	      hideFace = new boolean [obj.getFaces().length];
	      for (int i = 0; i < param.length; i++)
	      {
	        int index = (int) param[i];
	        if (index < oldHideFace.length)
	          hideFace[i] = oldHideFace[index];
	      }
	    }
		
	    setHiddenFaces(hideFace);
	    updateJointWeightParam();
		findSelectionDistance();
		currentTool.getWindow().updateMenus();
		updateImage();
	}

	/**
	 * When the object changes, we need to rebuild the display.
	 */

	public void objectChanged() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		((PolyMesh) mesh).resetMesh();
		setMesh((PolyMesh) mesh);
		super.objectChanged();
	}

	/** Update the parameter which records weights for the currently selected joint. */

	private void updateJointWeightParam()
	{
		PolyMesh mesh = (PolyMesh) objInfo.object;
		MeshVertex vert[] = mesh.getVertices();
		double jointWeight[] = new double [vert.length];
		int selJointId = ((MeshViewer) theView[currentView]).getSelectedJoint();
		Joint selJoint = getObject().getSkeleton().getJoint(selJointId);
		for (int i = 0; i < jointWeight.length; i++)
		{
			Joint vertJoint = getObject().getSkeleton().getJoint(vert[i].ikJoint);
			if (selJoint == null)
				jointWeight[i] = 0.0;
			else if (vert[i].ikJoint == selJointId)
				jointWeight[i] = (selJoint.parent == null ? 1.0 : vert[i].ikWeight);
			else if (vertJoint != null && vertJoint.parent == selJoint)
				jointWeight[i] = 1.0-vert[i].ikWeight;
			else
				jointWeight[i] = 0.0;
		}
		VertexParameterValue value = (VertexParameterValue) getObject().object.getParameterValue(jointWeightParam);
		value.setValue(jointWeight);
		getObject().object.setParameterValues(getObject().object.getParameterValues());
		
		objInfo.clearCachedMeshes();    
	}
	
	/** Get the extra texture parameter which was added to the mesh to keep track of
    face indices in the editor. */

	public TextureParameter getFaceIndexParameter()
	{
		return faceIndexParam;
	}

	/** Get the extra texture parameter which was added to the mesh to keep track of
    joint weighting. */

	public TextureParameter getJointWeightParam()
	{
		return jointWeightParam;
	}

	/** Add extra texture parameters to the mesh which will be used for keeping track of face
    and vertex indices. */

	private void addExtraParameters()
	{
		if (faceIndexParam != null)
			return;
		faceIndexParam = new TextureParameter(this, "Face Index", 0.0, Double.MAX_VALUE, 0.0);
		jointWeightParam = new TextureParameter(this, "Joint Weight", 0.0, 1.0, 0.0);
		PolyMesh mesh = (PolyMesh) getObject().object;
		TextureParameter params[] = mesh.getParameters();
		TextureParameter newparams[] = new TextureParameter [params.length+2];
		ParameterValue values[] = mesh.getParameterValues();
		ParameterValue newvalues[] = new ParameterValue [values.length+2];
		for (int i = 0; i < params.length; i++)
		{
			newparams[i] = params[i];
			newvalues[i] = values[i];
		}
		newparams[params.length] = faceIndexParam;
		newvalues[values.length] = new FaceParameterValue(mesh, faceIndexParam);
		double faceIndex[] = new double [mesh.getFaces().length];
		for (int i = 0; i < faceIndex.length; i++)
			faceIndex[i] = i;
		((FaceParameterValue) newvalues[values.length]).setValue(faceIndex);
		newparams[params.length+1] = jointWeightParam;
		newvalues[values.length+1] = new VertexParameterValue(mesh, jointWeightParam);
		mesh.setParameters(newparams);
		mesh.setParameterValues(newvalues);
		getObject().clearCachedMeshes();
		updateJointWeightParam();
	}

	/** Remove the extra texture parameters from the mesh which were used for keeping track of
    face and vertex indices. */

	public void removeExtraParameters()
	{
		if (faceIndexParam == null)
			return;
		faceIndexParam = null;
		jointWeightParam = null;
		PolyMesh mesh = (PolyMesh) getObject().object;
		TextureParameter params[] = mesh.getParameters();
		TextureParameter newparams[] = new TextureParameter [params.length-2];
		ParameterValue values[] = mesh.getParameterValues();
		ParameterValue newvalues[] = new ParameterValue [values.length-2];
		for (int i = 0; i < newparams.length; i++)
		{
			newparams[i] = params[i];
			newvalues[i] = values[i];
		}
		mesh.setParameters(newparams);
		mesh.setParameterValues(newvalues);
		getObject().clearCachedMeshes();
	}


	/**
	 * Selects complete boundaries based on vertex or edge selection. If
	 * selection is empty, then all boundaries are selected.
	 */
	private void doSelectBoundary() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		Wedge[] edges = mesh.getEdges();
		Wvertex[] vertices = (Wvertex[]) mesh.getVertices();

		boolean emptySel = true;
		for (int i = 0; i < selected.length; i++)
			if (selected[i])
				emptySel = false;

		if (selectMode == POINT_MODE) {
			boolean[] edgeSel = new boolean[edges.length / 2];
			for (int i = 0; i < selected.length; ++i) {
				if (emptySel || selected[i]) {
					int[] ve = mesh.getVertexEdges(vertices[i]);
					for (int j = 0; j < ve.length; ++j) {
						if (edges[ve[j]].face == -1) {
							int sel = ve[j];
							if (sel >= edges.length / 2)
								sel = edges[sel].hedge;
							edgeSel[sel] = true;
						}
					}
				}
			}
			edgeSel = mesh.getBoundarySelection(edgeSel);
			for (int i = 0; i < selected.length; ++i)
				selected[i] = false;
			for (int i = 0; i < edgeSel.length; ++i) {
				if (edgeSel[i]) {
					selected[edges[i].vertex] = true;
					selected[edges[edges[i].hedge].vertex] = true;
				}
			}
		} else {
			if (emptySel) {
				for (int i = 0; i < edges.length; ++i) {
					if (edges[i].face == -1) {
						if (i >= edges.length / 2)
							selected[edges[i].hedge] = true;
						else
							selected[i] = true;
					}
				}
			} else
				selected = mesh.getBoundarySelection(selected);
		}
		setSelection(selected);
	}

	/**
	 * Closes selected boundaries
	 */
	private void doCloseBoundary() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		Wedge[] edges = mesh.getEdges();
		Wvertex[] vertices = (Wvertex[]) mesh.getVertices();
		boolean[] newFaceSel;
		if (selectMode == POINT_MODE) {
			boolean[] edgeSel = new boolean[edges.length / 2];
			for (int i = 0; i < selected.length; ++i) {
				if (selected[i]) {
					int[] ve = mesh.getVertexEdges(vertices[i]);
					for (int j = 0; j < ve.length; ++j) {
						if (edges[ve[j]].face == -1) {
							int sel = ve[j];
							if (sel >= edges.length / 2)
								sel = edges[sel].hedge;
							edgeSel[sel] = true;
						}
					}
				}
			}
			newFaceSel = mesh.closeBoundary(edgeSel);

		} else
			newFaceSel = mesh.closeBoundary(selected);
		objectChanged();
		setSelectionMode(FACE_MODE);
		updateMenus();
		setSelection(newFaceSel);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
	}

	/**
	 * Joins boundaries, each one of the two being identified by a selected
	 * vertex
	 */
	private void doJoinBoundaries() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		boolean[] newFaceSel;
		int one = -1;
		int two = -1;
		for (int i = 0; i < selected.length; ++i)
			if (selected[i]) {
				if (one == -1)
					one = i;
				else
					two = i;
			}
		if (!mesh.joinBoundaries(one, two))
			return;
		objectChanged();
		setSelectionMode(FACE_MODE);
		updateMenus();
		newFaceSel = new boolean[mesh.getFaces().length];
		for (int i = prevMesh.getFaces().length; i < newFaceSel.length; ++i)
			newFaceSel[i] = true;
		setSelection(newFaceSel);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
	}

	/**
	 * Hide the selected part of the mesh.
	 */

	private void doHideSelection() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		boolean hide[] = new boolean[mesh.getFaces().length];
		if (selectMode == FACE_MODE)
			System.arraycopy(selected, 0, hide, 0, selected.length);
		else if (selectMode == EDGE_MODE) {
			Wedge edges[] = mesh.getEdges();
			for (int i = 0; i < selected.length; i++) {
				if (selected[i]) {
					if (edges[i].face >= 0) {
						hide[edges[i].face] = true;
					}
					if (edges[edges[i].hedge].face >= 0) {
						hide[edges[edges[i].hedge].face] = true;
					}
				}
			}
		} else {
			Wface faces[] = mesh.getFaces();
			for (int i = 0; i < faces.length; i++) {
				hide[i] = false;
				int[] vf = mesh.getFaceVertices(faces[i]);
				for (int j = 0; j < vf.length; ++j)
					hide[i] = (hide[i] || selected[vf[j]]);
			}
		}
		boolean wasHidden[] = hideFace;
		if (wasHidden != null)
			for (int i = 0; i < wasHidden.length; i++)
				if (wasHidden[i])
					hide[i] = true;
		setHiddenFaces(hide);
		for (int i = 0; i < selected.length; i++)
			selected[i] = false;
		setSelection(selected);
	}

	/**
	 * Show all faces of the mesh.
	 */

	private void doShowAll() {
		setHiddenFaces(null);
	}

	/**
	 * Collapse faces command
	 */
	private void doCollapseFaces() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		if (mesh.getFaces().length == 1) {
			new BStandardDialog(Translate.text("polymesh:errorTitle"), UIUtilities
					.breakString(Translate.text("illegalDelete")),
					BStandardDialog.ERROR).showMessageDialog(null);
			return;
		}
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		mesh.collapseFaces(selected);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		updateImage();
	}

	/**
	 * Collapse edges command
	 */
	private void doCollapseEdges() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		if (mesh.getFaces().length == 1) {
			new BStandardDialog(Translate.text("polymesh:errorTitle"), UIUtilities
					.breakString(Translate.text("illegalDelete")),
					BStandardDialog.ERROR).showMessageDialog(null);
			return;
		}
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		mesh.collapseEdges(selected);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		updateImage();
	}

	/**
	 * Collapse vertices command
	 */
	private void doCollapseVertices() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		Wvertex[] verts = (Wvertex[]) mesh.getVertices();
		for (int i = 0; i < selected.length; ++i) {
			if (selected[i]) {
				int[] fv = mesh.getVertexEdges(verts[i]);
				if (fv.length == selected.length) {
					new BStandardDialog(Translate.text("polymesh:errorTitle"),
							UIUtilities.breakString(Translate
									.text("illegalDelete")),
							BStandardDialog.ERROR).showMessageDialog(null);
					return;
				}
			}
		}
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		mesh.collapseVertices(selected);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		updateImage();
	}

	/**
	 * Facet vertices command
	 */
	private void doFacetVertices() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		Wvertex[] verts = (Wvertex[]) mesh.getVertices();
		for (int i = 0; i < selected.length; ++i) {
			if (selected[i]) {
				int[] fv = mesh.getVertexEdges(verts[i]);
				if (fv.length == selected.length) {
					new BStandardDialog(Translate.text("polymesh:errorTitle"),
							UIUtilities.breakString(Translate
									.text("illegalDelete")),
							BStandardDialog.ERROR).showMessageDialog(null);
					return;
				}
			}
		}
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		mesh.facetVertices(selected);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		updateImage();
	}

	/**
	 * Merge edges command
	 */
	private void doMergeEdges() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		boolean[] sel = mesh.mergeEdges(selected);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		setSelection(sel);
		updateImage();
	}

	/**
	 * Merge faces command
	 */
	private void doMergeFaces() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		boolean[] sel = mesh.mergeFaces(selected);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		setSelection(sel);
		updateImage();
	}

	/**
	 * Triangulate faces command
	 */
	private void doTriangulateFaces() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		boolean[] sel = mesh.triangulateFaces(selected);
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
		objectChanged();
		setSelection(sel);
		updateImage();
	}

	/**
	 * Invert the current selection.
	 */

	public void invertSelectionCommand() {
		boolean newSel[] = new boolean[selected.length];
		for (int i = 0; i < newSel.length; i++)
			newSel[i] = !selected[i];
		setUndoRecord(new UndoRecord(this, false,
				UndoRecord.SET_MESH_SELECTION, new Object[] { this,
						new Integer(selectMode), selected }));
		setSelection(newSel);
	}

	/**
	 * Scales current selection using the slider
	 */
	void scaleSelectionCommand() {
		if (valueWidget.isActivated())
			return;
		initSelPoints();
		Runnable callback = new Runnable() {

			public void run() {
				doScaleSelectionCallback();
			}
		};
		valueWidget.activate(1.0, callback);
	}

	/**
	 * Push/pulls current selection using the slider
	 */
	void scaleNormalSelectionCommand() {
		if (valueWidget.isActivated())
			return;
		initSelPoints();
		Runnable callback = new Runnable() {

			public void run() {
				doScaleNormalSelectionCallback();
			}
		};
		valueWidget.activate(callback);
	}

	private void initSelPoints() {
		prepareToShowValueWidget();
		MeshVertex[] orVerts = priorValueMesh.getVertices();

		if (selectMode == POINT_MODE) {
			selPoints = selected;
		} else if (selectMode == EDGE_MODE) {
			selPoints = new boolean[orVerts.length];
			Wedge[] edges = priorValueMesh.getEdges();
			for (int i = 0; i < valueSelection.length; ++i)
				if (valueSelection[i]) {
					selPoints[edges[i].vertex] = true;
					selPoints[edges[edges[i].hedge].vertex] = true;
				}
		} else {
			selPoints = new boolean[orVerts.length];
			Wface[] faces = priorValueMesh.getFaces();
			for (int i = 0; i < valueSelection.length; ++i)
				if (valueSelection[i]) {
					int[] fv = priorValueMesh.getFaceVertices(faces[i]);
					for (int j = 0; j < fv.length; j++)
						selPoints[fv[j]] = true;
				}
		}
		int count = 0;
		selCenter = new Vec3();
		for (int i = 0; i < selPoints.length; i++) {
			if (selPoints[i]) {
				selCenter.add(orVerts[i].r);
				++count;
			}
		}
		if (count > 0) {
			selCenter.scale(1.0 / count);
			for (int i = 0; i < selPoints.length; i++) {
				if (selPoints[i])
					meanSelDistance += orVerts[i].r.distance(selCenter);
			}
			meanSelDistance /= count;
		}
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog (scaelSelection)
	 */
	private void doScaleSelectionCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		MeshVertex[] vertices = mesh.getVertices();
		MeshVertex[] orVerts = priorValueMesh.getVertices();

		for (int i = 0; i < selPoints.length; i++) {
			if (selPoints[i])
				vertices[i].r = selCenter.plus(orVerts[i].r.minus(selCenter)
						.times(valueWidget.getValue()));
		}
		objectChanged();
		setSelection(valueSelection);
	}

	/**
	 * Callback called when the valueWidget.getValue() has changed in the
	 * valueWidget.getValue() dialog (push/pull selection)
	 */
	private void doScaleNormalSelectionCallback() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		mesh.copyObject(priorValueMesh);
		Vec3[] normals = priorValueMesh.getNormals();
		MeshVertex[] vertices = mesh.getVertices();
		MeshVertex[] orVerts = priorValueMesh.getVertices();
		for (int i = 0; i < selPoints.length; i++) {
			if (selPoints[i]) {
				vertices[i].r = orVerts[i].r.plus(normals[i].times(valueWidget
						.getValue()));
			}
		}
		objectChanged();
		setSelection(valueSelection);
	}

	/**
	 * mirrors the mesh about XY plane
	 */
	private void doMirrorWholeXY() {
		doMirrorWhole(PolyMesh.MIRROR_ON_XY);
	}

	/**
	 * mirrors the mesh about YZ plane
	 */
	private void doMirrorWholeYZ() {
		doMirrorWhole(PolyMesh.MIRROR_ON_YZ);
	}

	/**
	 * mirrors the mesh about XZ plane
	 */
	private void doMirrorWholeXZ() {
		doMirrorWhole(PolyMesh.MIRROR_ON_XZ);
	}

	/**
	 * mirrors the mesh about a plane
	 */
	private void doMirrorWhole(short mirrorOrientation) {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		mesh.mirrorWholeMesh(mirrorOrientation);
		objectChanged();
		updateMenus();
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
	}

	/**
	 * inverts normals
	 */
	private void doInvertNormals() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		PolyMesh prevMesh = (PolyMesh) mesh.duplicate();
		mesh.invertNormals();
		objectChanged();
		updateMenus();
		updateImage();
		setUndoRecord(new UndoRecord(this, false, UndoRecord.COPY_OBJECT,
				new Object[] { mesh, prevMesh }));
	}

	/**
	 * Sets off a previously set mirror
	 */
	private void doMirrorOff() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		if (mesh.getMirrorState() == PolyMesh.NO_MIRROR)
			return;
		BStandardDialog dlg = new BStandardDialog(Translate
				.text("polymesh:removeMeshMirror"),
				Translate.text("polymesh:keepMirroredMesh"), BStandardDialog.QUESTION);
		int r = dlg.showOptionDialog(this, new String[] {
				Translate.text("polymesh:keep"), Translate.text("polymesh:discard"),
				Translate.text("polymesh:cancel") }, "cancel");
		if (r == 0) {
			PolyMesh newMesh = mesh.getMirroredMesh(); 
			mesh.copyObject(newMesh);
			((BCheckBoxMenuItem) mirrorItem[1]).setState(false);
			((BCheckBoxMenuItem) mirrorItem[2]).setState(false);
			((BCheckBoxMenuItem) mirrorItem[3]).setState(false);
			objectChanged();
			updateImage();
		} else if (r == 1) {
			((BCheckBoxMenuItem) mirrorItem[1]).setState(false);
			((BCheckBoxMenuItem) mirrorItem[2]).setState(false);
			((BCheckBoxMenuItem) mirrorItem[3]).setState(false);
			mesh.setMirrorState(PolyMesh.NO_MIRROR);
			objectChanged();
			setSelection(selected);
		}
	}

	/**
	 * Sets a mirror on XY, YZ or XZ plane
	 * 
	 * @param ev
	 *                CommandEvent
	 */
	private void doMirrorOn(CommandEvent ev) {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		short mirrorState = 0;
		if (((BCheckBoxMenuItem) mirrorItem[1]).getState())
			mirrorState |= PolyMesh.MIRROR_ON_XY;
		if (((BCheckBoxMenuItem) mirrorItem[2]).getState())
			mirrorState |= PolyMesh.MIRROR_ON_XZ;
		if (((BCheckBoxMenuItem) mirrorItem[3]).getState())
			mirrorState |= PolyMesh.MIRROR_ON_YZ;
		mesh.setMirrorState(mirrorState);
		realMirror = false;
		objectChanged();
		setSelection(selected);
	}

	private void doCopyEvent() {
		pasteItem.setEnabled(true);
	}

	private void doCopy() {
		clipboardMesh = (PolyMesh) ((PolyMesh) objInfo.object).duplicate();
		int selCount = 0;

		if (selected != null)
			for (int i = 0; i < selected.length; i++)
				if (!selected[i])
					++selCount;
		if (selCount > 0) {
			if (selectMode == POINT_MODE) {
				int[] indices = new int[selCount];
				int count = 0;
				for (int i = 0; i < selected.length; ++i)
					if (!selected[i])
						indices[count++] = i;
				if (clipboardMesh.getVertices().length - indices.length < 3) {
					//back to original mesh
					clipboardMesh = (PolyMesh) ((PolyMesh) objInfo.object).duplicate();
				} else {
					clipboardMesh.deleteVertices(indices);				
				}
			} else if (selectMode == EDGE_MODE) {
				int count = 0;
				int[] indices = new int[selCount];
				for (int i = 0; i < selected.length; ++i)
					if (!selected[i])
						indices[count++] = i;
				if (clipboardMesh.getEdges().length - indices.length < 3) {
					//back to original mesh
					clipboardMesh = (PolyMesh) ((PolyMesh) objInfo.object).duplicate();
				} else {
					clipboardMesh.deleteEdges(indices);
				}
			} else {
				int count = 0;
				int[] indices = new int[selCount];
				for (int i = 0; i < selected.length; ++i)
					if (!selected[i])
						indices[count++] = i;
				if (clipboardMesh.getFaces().length - indices.length < 1) {
					//back to original mesh
					clipboardMesh = (PolyMesh) ((PolyMesh) objInfo.object).duplicate();
				} else {
					clipboardMesh.deleteFaces(indices);
				}
			}
		}
		eventSource.dispatchEvent(new CopyEvent(this));
	}

	private void doPaste() {
		if (clipboardMesh == null)
			return;
		setSelectionMode(POINT_MODE);
		PolyMesh mesh = (PolyMesh) objInfo.object;
		boolean[] sel = mesh.addMesh(clipboardMesh);
		objectChanged();
		setSelection(sel);
	}

	private void doSaveAsTemplate() {
		BFileChooser chooser;

		File templateDir = new File(ArtOfIllusion.PLUGIN_DIRECTORY
				+ File.separator + "PolyMeshTemplates");
		if (!templateDir.exists()) {
			if (!templateDir.mkdir()) {
				new BStandardDialog(Translate.text("polymesh:errorTemplateDir"),
						UIUtilities
								.breakString(Translate.text("illegalDelete")),
						BStandardDialog.ERROR).showMessageDialog(null);
				return;
			}
		}
		chooser = new BFileChooser(BFileChooser.SAVE_FILE, Translate
				.text("polymesh:saveTemplate"), templateDir);
		if (chooser.showDialog(null)) {
			try {
				File file = chooser.getSelectedFile();
				DataOutputStream dos = new DataOutputStream(
						new FileOutputStream(file));
				((PolyMesh) objInfo.object).writeToFile(dos, null);
				dos.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

	}

	/**
	 * Get the object being edited in this window.
	 * 
	 * @return The object valueWidget.getValue()
	 */

	public ObjectInfo getObject() {
		return objInfo;
	}

	private void skeletonDetachedChanged() {
		for (int i = 0; i < theView.length; i++)
			((PolyMeshViewer) theView[i])
					.setSkeletonDetached(((BCheckBoxMenuItem) skeletonMenuItem[5])
							.getState());
	}

	private void doLooseSelectionChanged() {
		looseSelect = looseSelectCB.getState();
	}

	private void doLooseSelectionValueChanged() {
		looseSelectValue = ((Integer) looseSelectSpinner.getValue());
	}

	@SuppressWarnings("unused")
	private void doFrontSelectionChanged() {
		if (selectVisible = frontSelectCB.getState()) {
			setSelection(selected);
		}
	}

	public boolean isFrontSelectionOn() {
		return frontSelectCB.getState();
	}

	public int getLooseSelectionRange() {
		if (!looseSelectCB.getState()) {
			return 0;
		} else {
			return ((Integer) looseSelectSpinner.getValue());
		}
	}


	/**
	 * Checks mesh for validity
	 */
	private void doCheckMesh() {
		// dumps selection
		PolyMesh mesh = ((PolyMesh) objInfo.object);
		String name = "";
		switch (selectMode) {
		case POINT_MODE:
			System.out.println("Selected Vertices:");
			name = "vertex ";
			break;
		case EDGE_MODE:
			System.out.println("Selected Edges:");
			name = "edge ";
			break;
		case FACE_MODE:
			System.out.println("Selected Faces:");
			name = "face ";
			break;
		}
		for (int i = 0; i < selected.length; i++) {
			if (selected[i]) {
				System.out.println(name + i + " selected.");
			}
		}
		new CheckMeshDialog();
	}

	private void tolerantModeChanged() {
		tolerant = lastTolerant = ((BCheckBoxMenuItem) editMenuItem[6])
				.getState();
		setTolerant(((BCheckBoxMenuItem) editMenuItem[6]).getState());
		savePreferences();
	}

	private void doControlledSmoothing() {
		new ControlledSmoothingDialog(this);
	}

	/**
	 * Finds appropriate seams in the mesh
	 * 
	 */
	private void doFindSeams() {
		((PolyMesh) objInfo.object).findSeams();
		objectChanged();
		updateImage();
	}

	@SuppressWarnings("unused")
	private void doMarkSelAsSeams() {
		if (selectMode == EDGE_MODE) {
			boolean[] seams = new boolean[selected.length];
			for (int i = 0; i < selected.length; i++)
				seams[i] = selected[i];
			((PolyMesh) objInfo.object).setSeams(seams);
			objectChanged();
			updateImage();
			updateMenus();
		}
	}

	@SuppressWarnings("unused")
	private void doOpenSeams() {
		((PolyMesh) objInfo.object).openSeams();
		objectChanged();
		updateImage();
		updateMenus();
	}

	@SuppressWarnings("unused")
	private void doClearSeams() {
		((PolyMesh) objInfo.object).setSeams(null);
		objectChanged();
		updateImage();
		updateMenus();
	}

	private void doUnfoldMesh() {
		UnfoldStatusDialog dlg = new UnfoldStatusDialog();
		if (!dlg.cancelled) {
			doEditMapping();
		}
	}

	@SuppressWarnings("unused")
	private void doUnfold(UnfoldStatusDialog dlg) {
		PolyMesh theMesh = (PolyMesh) objInfo.object;
		PolyMesh mesh = (PolyMesh) ((PolyMesh) objInfo.object).duplicate();
		ObjectInfo info = objInfo.duplicate();
		info.coords = new CoordinateSystem();
		int[] vertTable;
		vertTable = mesh.openSeams();
		int vertCount = mesh.getVertices().length;
		try {
			mesh.setSmoothingMethod(Mesh.NO_SMOOTHING);
			TriangleMesh triMesh = mesh.convertToTriangleMesh(0);
			int newVertCount = triMesh.getVertices().length;
			if (vertTable == null && newVertCount != vertCount) {
				vertTable = new int[newVertCount];
				for (int i = 0; i < vertCount; i++) {
					vertTable[i] = i;
				}
				for (int i = vertCount; i < newVertCount; i++) {
					vertTable[i] = -1;
				}
			} else if (vertTable != null && newVertCount != vertCount) {
				int[] nVertTable = new int[newVertCount];
				for (int i = 0; i < vertCount; i++) {
					nVertTable[i] = vertTable[i];
				}
				for (int i = vertCount; i < newVertCount; i++) {
					nVertTable[i] = -1;
				}
				vertTable = nVertTable;
			}
			int[] faceTable = mesh.getTriangleFaceIndex();
			MeshUnfolder unfolder = new MeshUnfolder(mesh, triMesh, vertTable,
					faceTable);
			if (unfolder.unfold(dlg.textArea, dlg.residual)) {
				UVMappingData data = new UVMappingData(unfolder
						.getUnfoldedMeshes());
				theMesh.setMappingData(data);
				dlg.unfoldFinished(true);
			} else {
				dlg.unfoldFinished(false);
			}
		} catch (Exception e) {
			dlg.unfoldFinished(false);
			e.printStackTrace();
		}
		updateMenus();
	}

	@SuppressWarnings({"unused", "ResultOfObjectAllocationIgnored"})
	private void doEditMapping() {
		PolyMesh theMesh = (PolyMesh) objInfo.object;
		ObjectInfo info = objInfo.duplicate();
		info.coords = new CoordinateSystem();
		UVMappingData data = theMesh.getMappingData();
		if (data == null) {
			return;
		}
		new UVMappingEditorDialog(info, true, this);
	}

	@SuppressWarnings("unused")
	private void doFindSimilarFaces() {
		new FindSimilarFacesDialog(selected);
	}

	@SuppressWarnings("unused")
	private void doFindSimilarEdges() {
		new FindSimilarEdgesDialog(selected);
	}

	@SuppressWarnings("unused")
	private void setSubdivionLevels() {
		new SubdivisionDialog(this);
	}

//	@SuppressWarnings("unused")
//	private void doRenderingLevel(ValueChangedEvent ev) {
//		((PolyMesh) objInfo.object).setRenderingSmoothLevel(((Integer) rspin
//				.getValue()).intValue());
//	}

	private void doInteractiveLevel(ValueChangedEvent ev) {
		((PolyMesh) objInfo.object).setInteractiveSmoothLevel(((Integer) ispin
                        .getValue()));
		objectChanged();
		updateImage();
	}

	private void doCornerChanged() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		Wvertex[] vertices = (Wvertex[]) mesh.getVertices();
		short type = Wvertex.NONE;
		if (cornerCB.getState())
			type = Wvertex.CORNER;
		for (int i = 0; i < selected.length; ++i)
			if (selected[i])
				vertices[i].type = type;
		objectChanged();
		updateImage();
	}

//	private void doAddVertexNormal() {
//		PolyMesh mesh = (PolyMesh) objInfo.object;
//		Wvertex[] vertices = (Wvertex[]) mesh.getVertices();
//		Vec3[] normals = mesh.getNormals();
//		for (int i = 0; i < selected.length; ++i)
//			if (selected[i])
//				vertices[i].normal = new Vec3(normals[i]);
//		objectChanged();
//		updateImage();
//	}
//
//	private void doRemoveVertexNormal() {
//		PolyMesh mesh = (PolyMesh) objInfo.object;
//		Wvertex[] vertices = (Wvertex[]) mesh.getVertices();
////		for (int i = 0; i < selected.length; ++i)
////			if (selected[i])
////				vertices[i].normal = null;
//		objectChanged();
//		updateImage();
//	}

	public PolyMeshValueWidget getValueWidget() {
		return valueWidget;
	}

	public boolean showNormals() {
		if (selectMode != POINT_MODE)
			return false;
		if (((PolyMesh) objInfo.object).getSmoothingMethod() != Mesh.APPROXIMATING)
			return false;
		return true;
	}

	/** Load all the preferences into memory. */

	protected void loadPreferences() {
		super.loadPreferences();
		lastFreehand = preferences
				.getBoolean("freehandSelection", lastFreehand);
		lastTolerant = preferences
				.getBoolean("tolerantSelection", lastTolerant);
		lastProjectOntoSurface = preferences.getBoolean("projectOntoSurface",
				lastProjectOntoSurface);
	}

	/** Save user settings that should be persistent between sessions. */

	protected void savePreferences() {
		super.savePreferences();
		preferences.putBoolean("freehandSelection", lastFreehand);
		preferences.putBoolean("tolerantSelection", lastTolerant);
		preferences.putBoolean("projectOntoSurface", lastProjectOntoSurface);
	}

	public void setTensionCommand() {
		super.setTensionCommand();
		tensionSpin.setValue(new Integer(tensionDistance));
	}

	public void doTensionChanged() {
		lastTensionDistance = tensionDistance = ((Integer) tensionSpin
                        .getValue());
		savePreferences();
	}

	/**
	 * This methods extracts the current selection to AoI curves
	 */
	private void doExtractToCurve() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		ArrayList curves = mesh.extractCurveFromSelection(selected);
		ArrayList closed = (ArrayList) curves.get(curves.size() - 1);
		for (int i = 0; i < curves.size() - 1; i++) {
			ArrayList curve = (ArrayList) curves.get(i);
			Vec3[] v = new Vec3[curve.size()];
			float[] s = new float[v.length];
			for (int j = 0; j < v.length; j++) {
				v[j] = (Vec3) curve.get(j);
				s[j] = 1.0f;
			}
			boolean b = ((Boolean) closed.get(i));
			Curve c = new Curve(v, s, mesh.getSmoothingMethod(), b);
			((LayoutWindow) parentWindow).addObject(c, objInfo.coords,
					("PMCurve " + i), null);
		}
		((LayoutWindow) parentWindow).repaint();
	}

	/**
	 * This methods adds the mesh seams to edge selection
	 */
	public void doSeamsToSel() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		boolean[] seams = mesh.getSeams();
		if (seams == null)
			return;
		for (int i = 0; i < seams.length; i++)
			selected[i] |= seams[i];
		objectChanged();
		updateImage();
	}

	/**
	 * This methods removes the selection off the mesh
	 */
	public void doRemoveSelFromSeams() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		boolean[] seams = mesh.getSeams();
		if (seams == null)
			return;
		for (int i = 0; i < seams.length; i++)
			seams[i] &= !selected[i];
		mesh.setSeams(seams);
		objectChanged();
		updateImage();
	}

	/**
	 * This methods removes the selection off the mesh
	 */
	public void doAddSelToSeams() {
		PolyMesh mesh = (PolyMesh) objInfo.object;
		boolean[] seams = mesh.getSeams();
		if (seams == null)
			return;
		for (int i = 0; i < seams.length; i++)
			seams[i] |= selected[i];
		mesh.setSeams(seams);
		objectChanged();
		updateImage();
	}

	/**
	 * A dialog presenting options to find similar faces
	 */
	private class FindSimilarFacesDialog extends BDialog {
		private boolean[] orSelection;

		private BorderContainer borderContainer1;

		private BCheckBox normalCB;

		private BCheckBox looseShapeCB;

		private BCheckBox strictShapeCB;

		private BLabel tolerance1;

		private BLabel tolerance2;

		private BLabel tolerance3;

		private BButton okButton;

		private BButton cancelButton;

		private PMValueField normalCBVF;

		private PMValueField looseShapeCBVF;

		private PMValueField strictShapeCBVF;

		private boolean ok;

		public FindSimilarFacesDialog(boolean selected[]) {
			super(PolyMeshEditorWindow.this, Translate
					.text("polymesh:similarFacesTitle"), true);
			this.orSelection = selected;
			InputStream is = null;
			try {
				is = getClass().getResource("interfaces/similar.xml")
						.openStream();
				WidgetDecoder decoder = new WidgetDecoder(is);
				borderContainer1 = (BorderContainer) decoder.getRootObject();
				BLabel titleTextLabel = ((BLabel) decoder
						.getObject("titleTextLabel"));
				titleTextLabel.setText(Translate.text("polymesh:"+titleTextLabel
						.getText()));
				normalCB = ((BCheckBox) decoder.getObject("normalCB"));
				normalCB.setText(Translate.text("polymesh:"+normalCB.getText()));
				looseShapeCB = ((BCheckBox) decoder.getObject("looseShapeCB"));
				looseShapeCB.setText(Translate.text("polymesh:"+looseShapeCB.getText()));
				strictShapeCB = ((BCheckBox) decoder.getObject("strictShapeCB"));
				strictShapeCB
						.setText(Translate.text("polymesh:"+strictShapeCB.getText()));
				tolerance1 = ((BLabel) decoder.getObject("tolerance1"));
				tolerance2 = ((BLabel) decoder.getObject("tolerance2"));
				tolerance3 = ((BLabel) decoder.getObject("tolerance3"));
				tolerance1.setText(Translate.text("polymesh:"+tolerance1.getText()));
				tolerance2.setText(Translate.text("polymesh:"+tolerance2.getText()));
				tolerance3.setText(Translate.text("polymesh:"+tolerance3.getText()));
				BTextField normalCBTF = ((BTextField) decoder
						.getObject("normalCBTF"));
				BTextField looseShapeCBTF = ((BTextField) decoder
						.getObject("looseShapeCBTF"));
				BTextField strictShapeCBTF = ((BTextField) decoder
						.getObject("strictShapeCBTF"));
				normalCBVF = new PMValueField(normalTol, ValueField.NONE);
				normalCBVF.setTextField((BTextField) decoder
						.getObject("normalCBTF"));
				looseShapeCBVF = new PMValueField(looseShapeTol,
						ValueField.NONE);
				looseShapeCBVF.setTextField((BTextField) decoder
						.getObject("looseShapeCBTF"));
				strictShapeCBVF = new PMValueField(strictShapeTol,
						ValueField.NONE);
				strictShapeCBVF.setTextField((BTextField) decoder
						.getObject("strictShapeCBTF"));
				GridContainer okCancelGrid = ((GridContainer) decoder
						.getObject("OkCancelGrid"));
				okButton = ((BButton) decoder.getObject("okButton"));
				cancelButton = ((BButton) decoder.getObject("cancelButton"));
				okButton.setText(Translate.text("polymesh:ok"));
				cancelButton.setText(Translate.text("polymesh:cancel"));
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				if (is != null)
					try {
						is.close();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
			}
			setContent(borderContainer1);
			normalCBVF.addEventLink(ValueChangedEvent.class, this,
					"doTolValueChanged");
			strictShapeCBVF.addEventLink(ValueChangedEvent.class, this,
					"doTolValueChanged");
			looseShapeCBVF.addEventLink(ValueChangedEvent.class, this,
					"doTolValueChanged");
			normalCB.addEventLink(ValueChangedEvent.class, this,
					"doCBValueChanged");
			strictShapeCB.addEventLink(ValueChangedEvent.class, this,
					"doCBValueChanged");
			looseShapeCB.addEventLink(ValueChangedEvent.class, this,
					"doCBValueChanged");
			okButton.addEventLink(CommandEvent.class, this, "doOK");
			cancelButton.addEventLink(CommandEvent.class, this, "doCancel");
			addEventLink(WindowClosingEvent.class, this, "doCancel");
			okButton.setEnabled(false);
			pack();
			UIUtilities.centerWindow(this);
			ok = false;
			setVisible(true);
		}

		private void doTolValueChanged() {
			fetchTolValues();
			selected = ((PolyMesh) objInfo.object).findSimilarFaces(
					orSelection, isNormal(), normalTol, isLoose(),
					looseShapeTol, isStrict(), strictShapeTol);
			objectChanged();
			updateImage();
		}

		private void doCBValueChanged() {
			tolerance1.setEnabled(normalCB.getState());
			normalCBVF.setEnabled(normalCB.getState());
			tolerance2.setEnabled(looseShapeCB.getState());
			looseShapeCBVF.setEnabled(looseShapeCB.getState());
			tolerance3.setEnabled(strictShapeCB.getState());
			strictShapeCBVF.setEnabled(strictShapeCB.getState());
			doTolValueChanged();
			if (!(normalCB.getState() || looseShapeCB.getState() || strictShapeCB
					.getState()))
				okButton.setEnabled(false);
			else
				okButton.setEnabled(true);
		}

		private void doCancel() {
			selected = orSelection;
			objectChanged();
			updateImage();
			dispose();
		}

		private void doOK() {
			fetchTolValues();
			dispose();
		}

		private void fetchTolValues() {
			if (normalCB.getState())
				normalTol = normalCBVF.getValue();
			if (looseShapeCB.getState())
				looseShapeTol = looseShapeCBVF.getValue();
			if (strictShapeCB.getState())
				strictShapeTol = strictShapeCBVF.getValue();
		}

		public boolean isNormal() {
			return normalCB.getState();
		}

		public boolean isLoose() {
			return looseShapeCB.getState();
		}

		public boolean isStrict() {
			return strictShapeCB.getState();
		}

		public boolean isOK() {
			return ok;
		}
	}

	/**
	 * A dialog presenting options to find similar edges
	 */
	private class FindSimilarEdgesDialog extends BDialog {
		private boolean[] orSelection;

		private BorderContainer borderContainer1;

		private BButton okButton;

		private BButton cancelButton;

		private PMValueField toleranceVF;

		public FindSimilarEdgesDialog(boolean selected[]) {
			super(PolyMeshEditorWindow.this, Translate
					.text("polymesh:similarEdgesTitle"), true);
			this.orSelection = selected;
			InputStream inputStream = null;
			try {
				inputStream = getClass().getResource(
						"interfaces/similaredges.xml").openStream();
				WidgetDecoder decoder = new WidgetDecoder(inputStream);
				borderContainer1 = (BorderContainer) decoder.getRootObject();
				BLabel tolerance1 = ((BLabel) decoder.getObject("tolerance1"));
				tolerance1.setText(Translate.text("polymesh:"+tolerance1.getText()));
				okButton = ((BButton) decoder.getObject("okButton"));
				cancelButton = ((BButton) decoder.getObject("cancelButton"));
				BTextField toleranceTF = ((BTextField) decoder
						.getObject("toleranceTF"));
				toleranceVF = new PMValueField(edgeTol, ValueField.NONE);
				toleranceVF.setTextField((BTextField) decoder
						.getObject("toleranceTF"));
				okButton = ((BButton) decoder.getObject("okButton"));
				cancelButton = ((BButton) decoder.getObject("cancelButton"));
				okButton.setText(Translate.text("polymesh:ok"));
				cancelButton.setText(Translate.text("polymesh:cancel"));
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
			setContent(borderContainer1);
			toleranceVF.addEventLink(ValueChangedEvent.class, this,
					"doTolValueChanged");
			okButton.addEventLink(CommandEvent.class, this, "doOK");
			cancelButton.addEventLink(CommandEvent.class, this, "doCancel");
			addEventLink(WindowClosingEvent.class, this, "doCancel");
			pack();
			UIUtilities.centerWindow(this);
			doTolValueChanged();
			setVisible(true);
		}

		private void doTolValueChanged() {
			fetchTolValues();
			selected = ((PolyMesh) objInfo.object).findSimilarEdges(
					orSelection, edgeTol);
			objectChanged();
			updateImage();
		}

		private void doCancel() {
			selected = orSelection;
			objectChanged();
			updateImage();
			dispose();
		}

		private void doOK() {
			fetchTolValues();
			dispose();
		}

		private void fetchTolValues() {
			edgeTol = toleranceVF.getValue();
		}
	}

	/**
	 * A dialog to enter the number of segments when subdividing edges
	 * 
	 * @author Francois Guillet
	 */
	public class DivideDialog extends BDialog {
		private BSpinner divideSpinner;

		private BButton okButton;

		private BButton cancelButton;

		private int num = -1;

		/**
		 * Constructor for the DivideDialog object
		 */
		public DivideDialog() {
			super(PolyMeshEditorWindow.this, Translate
					.text("polymesh:subdivideEdgesTitle"), true);
			InputStream is = null;
			try {
				WidgetDecoder decoder = new WidgetDecoder(is = getClass()
						.getResource("interfaces/divide.xml").openStream());
				setContent((BorderContainer) decoder.getRootObject());
				divideSpinner = ((BSpinner) decoder.getObject("divideSpinner"));
				BLabel divideLabel = ((BLabel) decoder.getObject("divideLabel"));
				divideLabel.setText(Translate.text("polymesh:"+divideLabel.getText()));
				okButton = ((BButton) decoder.getObject("okButton"));
				okButton.setText(Translate.text("polymesh:ok"));
				cancelButton = ((BButton) decoder.getObject("cancelButton"));
				cancelButton.setText(Translate.text("polymesh:cancel"));
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				if (is != null)
					try {
						is.close();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
			}
			okButton.addEventLink(CommandEvent.class, this, "doOK");
			cancelButton.addEventLink(CommandEvent.class, this, "doCancel");
			pack();
			UIUtilities.centerWindow(this);
			setVisible(true);
		}

		/**
		 * OK button selected
		 */
		private void doOK() {
			num = ((Integer) divideSpinner.getValue());
			dispose();
		}

		/**
		 * Cancel button selected
		 */
		private void doCancel() {
			dispose();
			num = -1;
		}

		/**
		 * Returns spinner valueWidget.getValue() if the user clicked on the OK
		 * button else return -1.
		 * 
		 * @return number of segments
		 */
		public int getNumber() {
			return num;
		}
	}

	/**
	 * A dialog for bevel properties selection
	 * 
	 * @author Francois Guillet
	 */
	private class BevelPropertiesDialog extends BDialog {
		private BorderContainer borderContainer1;

		private FormContainer formContainer1;

		private PMValueField areaLimitFieldVF;

		private BCheckBox applyCB;

		private GridContainer gridContainer1;

		private BButton okButton;

		private BButton cancelButton;

		/**
		 * Constructor for the Bevel Properties dialog
		 */
		public BevelPropertiesDialog() {
			super(PolyMeshEditorWindow.this, Translate.text("polymesh:bevelPropertiesTitle"), true);
			InputStream is = null;
			try {
				WidgetDecoder decoder = new WidgetDecoder(getClass()
						.getResource("interfaces/bevelArea.xml").openStream());
				borderContainer1 = (BorderContainer) decoder.getRootObject();
				BLabel areaLimit = ((BLabel) decoder.getObject("areaLimit"));
				areaLimit.setText(Translate.text("polymesh:"+areaLimit.getText()));
				areaLimitFieldVF = new PMValueField(PolyMesh.edgeLengthLimit,
						ValueField.NONE);
				areaLimitFieldVF.setTextField((BTextField) decoder
						.getObject("areaLimitField"));
				applyCB = ((BCheckBox) decoder.getObject("applyCB"));
				applyCB.setText(Translate.text("polymesh:"+applyCB.getText()));
				applyCB.setState(PolyMesh.applyEdgeLengthLimit);
				BLabel bevelAreaLabel = ((BLabel) decoder
						.getObject("bevelAreaLabel"));
				bevelAreaLabel.setText(Translate.text("polymesh:"+bevelAreaLabel
						.getText()));
				okButton = ((BButton) decoder.getObject("okButton"));
				okButton.setText(Translate.text("polymesh:ok"));
				cancelButton = ((BButton) decoder.getObject("cancelButton"));
				cancelButton.setText(Translate.text("polymesh:cancel"));
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				if (is != null)
					try {
						is.close();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
			}
			setContent(borderContainer1);
			okButton.addEventLink(CommandEvent.class, this, "doOK");
			cancelButton.addEventLink(CommandEvent.class, this, "doCancel");
			addEventLink(WindowClosingEvent.class, this, "doCancel");
			pack();
			UIUtilities.centerWindow(this);
			setVisible(true);
		}

		/**
		 * OK button selected
		 */
		private void doOK() {
			PolyMesh.applyEdgeLengthLimit = applyCB.getState();
			if (PolyMesh.applyEdgeLengthLimit)
				PolyMesh.edgeLengthLimit = areaLimitFieldVF.getValue();
			dispose();
		}

		/**
		 * Cancel button selected
		 */
		private void doCancel() {
			dispose();
		}
	}

	/**
	 * A dialog which show the result of check/repair operation
	 * 
	 * @author Francois Guillet
	 */
	public class CheckMeshDialog extends BDialog {
		private BButton dismiss;

		private BTextArea textArea;

		/**
		 * Constructor for the CheckMeshDialog object
		 */
		public CheckMeshDialog() {
			super(PolyMeshEditorWindow.this, Translate.text("polymesh:checkRepair"),
					true);

			BorderContainer borderContainer1 = null;
			InputStream is = null;
			try {
				WidgetDecoder decoder = new WidgetDecoder(is = getClass()
						.getResource("interfaces/check.xml").openStream());
				borderContainer1 = (BorderContainer) decoder.getRootObject();
				textArea = ((BTextArea) decoder.getObject("TextArea"));
				dismiss = ((BButton) decoder.getObject("dismiss"));
				dismiss.setText(Translate.text("polymesh:dismiss"));
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				if (is != null)
					try {
						is.close();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
			}
			setContent(borderContainer1);
			dismiss.addEventLink(CommandEvent.class, this, "doDismiss");
			pack();
			UIUtilities.centerWindow(this);
			PolyMesh mesh = (PolyMesh) objInfo.object;
			textArea.append(mesh.checkMesh());
			setVisible(true);
		}

		/**
		 * Description of the Method
		 */
		private void doDismiss() {
			dispose();
		}
	}

	private class CopyEvent implements WidgetEvent {
		Widget widget;

		public CopyEvent(Widget w) {
			widget = w;
		}

		public Widget getWidget() {
			return widget;
		}
	}

	private class ControlledSmoothingDialog extends BDialog {
		private BCheckBox applyCB;

		private BLabel maxAngle;

		private BLabel minAngle;

		private BLabel angleRange;

		private BLabel smoothnessRange;

		private BLabel minSmoothness;

		private BLabel maxSmoothness;

		private PMValueField minAngleVF, maxAngleVF, minSmoothnessVF,
				maxSmoothnessVF;

		private boolean backApply;

		private double backMaxAngle, backMinAngle, backMinSmoothness,
				backMaxSmoothness;

		private PolyMesh mesh, prevMesh;

		private BSlider minAngleSlider;

		private BSlider maxAngleSlider;

		private BSlider minSmoothnessSlider;

		private BSlider maxSmoothnessSlider;

		public ControlledSmoothingDialog(BFrame parent) {
			super(parent, Translate.text("polymesh:controlledSmoothness"), true);
			setTitle(Translate.text("polymesh:controlledSmoothnessDialogTitle"));
			mesh = (PolyMesh) objInfo.object;
			prevMesh = (PolyMesh) mesh.duplicate();
			backApply = mesh.isControlledSmoothing();
			backMinAngle = mesh.getMinAngle();
			backMaxAngle = mesh.getMaxAngle();
			backMinSmoothness = mesh.getMinSmoothness();
			backMaxSmoothness = mesh.getMaxSmoothness();
			InputStream inputStream = null;
			try {
				WidgetDecoder decoder = new WidgetDecoder(getClass()
						.getResource("interfaces/controlledSmoothing.xml")
						.openStream());
				ColumnContainer columnContainer1 = (ColumnContainer) decoder
						.getRootObject();
				BLabel controlledSmoothing = ((BLabel) decoder
						.getObject("controlledSmoothing"));
				controlledSmoothing.setText(Translate
						.text("polymesh:"+controlledSmoothing.getText()));
				applyCB = ((BCheckBox) decoder.getObject("applyCB"));
				applyCB.setText(Translate.text("polymesh:"+applyCB.getText()));
				applyCB
						.addEventLink(ValueChangedEvent.class, this,
								"doApplyCB");
				maxAngle = ((BLabel) decoder.getObject("maxAngle"));
				maxAngle.setText(Translate.text("polymesh:"+maxAngle.getText()));
				BTextField maxAngleValue = ((BTextField) decoder
						.getObject("maxAngleValue"));
				BTextField minAngleValue = ((BTextField) decoder
						.getObject("minAngleValue"));
				minAngle = ((BLabel) decoder.getObject("minAngle"));
				minAngle.setText(Translate.text("polymesh:"+ minAngle.getText()));
				angleRange = ((BLabel) decoder.getObject("angleRange"));
				angleRange.setText(Translate.text("polymesh:"+angleRange.getText()));
				smoothnessRange = ((BLabel) decoder
						.getObject("smoothnessRange"));
				smoothnessRange.setText(Translate.text("polymesh:"+smoothnessRange
						.getText()));
				minSmoothness = ((BLabel) decoder.getObject("minSmoothness"));
				minSmoothness
						.setText(Translate.text("polymesh:"+minSmoothness.getText()));
				BTextField minSmoothnessValue = ((BTextField) decoder
						.getObject("minSmoothnessValue"));
				maxSmoothness = ((BLabel) decoder.getObject("maxSmoothness"));
				maxSmoothness
						.setText(Translate.text("polymesh:"+maxSmoothness.getText()));
				BTextField maxSmoothnessValue = ((BTextField) decoder
						.getObject("maxSmoothnessValue"));
				BButton okButton = ((BButton) decoder.getObject("okButton"));
				okButton.addEventLink(CommandEvent.class, this, "doOK");
				okButton.setText(Translate.text("polymesh:ok"));
				// applyButton = ((BButton) decoder.getObject("applyButton"));
				// applyButton.addEventLink( CommandEvent.class, this, "doApply"
				// );
				BButton cancelButton = ((BButton) decoder
						.getObject("cancelButton"));
				cancelButton.addEventLink(CommandEvent.class, this, "doCancel");
				cancelButton.setText(Translate.text("polymesh:cancel"));
				minAngleVF = new PMValueField(0.0, ValueField.NONNEGATIVE);
				minAngleVF.setTextField(minAngleValue);
				maxAngleVF = new PMValueField(180.0, ValueField.NONNEGATIVE);
				maxAngleVF.setTextField(maxAngleValue);
				minSmoothnessVF = new PMValueField(1.0, ValueField.NONNEGATIVE);
				minSmoothnessVF.setTextField(minSmoothnessValue);
				maxSmoothnessVF = new PMValueField(0.0, ValueField.NONNEGATIVE);
				maxSmoothnessVF.setTextField(maxSmoothnessValue);
				applyCB.setState(mesh.isControlledSmoothing());
				minAngleVF.setValue(mesh.getMinAngle());
				maxAngleVF.setValue(mesh.getMaxAngle());
				minSmoothnessVF.setValue(mesh.getMinSmoothness());
				maxSmoothnessVF.setValue(mesh.getMaxSmoothness());
				minAngleVF.addEventLink(ValueChangedEvent.class, this,
						"doApplyVF");
				maxAngleVF.addEventLink(ValueChangedEvent.class, this,
						"doApplyVF");
				minSmoothnessVF.addEventLink(ValueChangedEvent.class, this,
						"doApplyVF");
				maxSmoothnessVF.addEventLink(ValueChangedEvent.class, this,
						"doApplyVF");
				minAngleSlider = ((BSlider) decoder.getObject("minAngleSlider"));
				maxAngleSlider = ((BSlider) decoder.getObject("maxAngleSlider"));
				minSmoothnessSlider = ((BSlider) decoder
						.getObject("minSmoothnessSlider"));
				maxSmoothnessSlider = ((BSlider) decoder
						.getObject("maxSmoothnessSlider"));
				minAngleSlider.addEventLink(ValueChangedEvent.class, this,
						"doApplySL");
				maxAngleSlider.addEventLink(ValueChangedEvent.class, this,
						"doApplySL");
				minSmoothnessSlider.addEventLink(ValueChangedEvent.class, this,
						"doApplySL");
				maxSmoothnessSlider.addEventLink(ValueChangedEvent.class, this,
						"doApplySL");
				doApplyCB();
				setContent(columnContainer1);
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
			pack();
			addEventLink(WindowClosingEvent.class, this, "doCancel");
			UIUtilities.centerWindow(PolyMeshEditorWindow.this);
			setVisible(true);

		}

		private void doCancel() {
			mesh.setControlledSmoothing(backApply);
			mesh.setMinAngle(backMinAngle);
			mesh.setMaxAngle(backMaxAngle);
			mesh.setMinSmoothness((float) backMinSmoothness);
			mesh.setMaxSmoothness((float) backMaxSmoothness);
			objectChanged();
			updateImage();
			dispose();
		}

		private void doApplyCB() {
			boolean state = applyCB.getState();
			angleRange.setEnabled(state);
			smoothnessRange.setEnabled(state);
			minAngle.setEnabled(state);
			maxAngle.setEnabled(state);
			minAngleVF.setEnabled(state);
			maxAngleVF.setEnabled(state);
			minSmoothness.setEnabled(state);
			maxSmoothness.setEnabled(state);
			minSmoothnessVF.setEnabled(state);
			maxSmoothnessVF.setEnabled(state);
			minAngleSlider.setEnabled(state);
			maxAngleSlider.setEnabled(state);
			minSmoothnessSlider.setEnabled(state);
			maxSmoothnessSlider.setEnabled(state);
			doApplyVF();
		}

		private void doApplyVF() {
			boolean state = applyCB.getState();
			if (minAngleVF.getValue() > 180.0)
				minAngleVF.setValue(180);
			if (maxAngleVF.getValue() > 180.0)
				maxAngleVF.setValue(180);
			if (minSmoothnessVF.getValue() > 1.0)
				minSmoothnessVF.setValue(1.0);
			if (maxSmoothnessVF.getValue() > 1.0)
				maxSmoothnessVF.setValue(1.0);
			int val = (int) Math.round(minAngleVF.getValue() / 1.80);
			minAngleSlider.setValue(val);
			val = (int) Math.round(maxAngleVF.getValue() / 1.80);
			maxAngleSlider.setValue(val);
			val = (int) Math.round(minSmoothnessVF.getValue() * 100);
			minSmoothnessSlider.setValue(val);
			val = (int) Math.round(maxSmoothnessVF.getValue() * 100);
			maxSmoothnessSlider.setValue(val);
			mesh.setControlledSmoothing(state);
			mesh.setMinAngle(minAngleVF.getValue());
			mesh.setMaxAngle(maxAngleVF.getValue());
			mesh.setMinSmoothness((float) minSmoothnessVF.getValue());
			mesh.setMaxSmoothness((float) maxSmoothnessVF.getValue());
			objectChanged();
			updateImage();
		}

		private void doApplySL() {
			boolean state = applyCB.getState();
			mesh.setControlledSmoothing(state);
			double val = minAngleSlider.getValue() * 1.8;
			mesh.setMinAngle(val);
			minAngleVF.setValue(val);
			val = maxAngleSlider.getValue() * 1.8;
			mesh.setMaxAngle(val);
			maxAngleVF.setValue(val);
			val = ((float) minSmoothnessSlider.getValue()) / 100.0;
			mesh.setMinSmoothness((float) val);
			minSmoothnessVF.setValue(val);
			val = ((float) maxSmoothnessSlider.getValue()) / 100.0;
			mesh.setMaxSmoothness((float) val);
			maxSmoothnessVF.setValue(val);
			objectChanged();
			updateImage();
		}

		private void doOK() {
			doApplyVF();
			setUndoRecord(new UndoRecord(PolyMeshEditorWindow.this, false,
					UndoRecord.COPY_OBJECT, new Object[] { mesh, prevMesh }));
			dispose();
		}
	}

	private class SubdivisionDialog extends BDialog {
		private FormContainer formContainer1;

		private BSpinner interactiveSpinner;

		private int backInteractiveSmoothness;

		private PolyMesh prevMesh;

		public SubdivisionDialog(BFrame parent) {
			super(parent, Translate.text("polymesh:subdivisionLevelDialogTitle"),
					false);
			PolyMesh mesh = (PolyMesh) objInfo.object;
			backInteractiveSmoothness = mesh.getInteractiveSmoothLevel();
			prevMesh = (PolyMesh) mesh.duplicate();
			InputStream inputStream = null;
			try {
				WidgetDecoder decoder = new WidgetDecoder(getClass()
						.getResource("interfaces/subdivision.xml").openStream());
				BorderContainer borderContainer1 = (BorderContainer) decoder
						.getRootObject();
				BLabel interactiveLabel = ((BLabel) decoder
						.getObject("interactiveLabel"));
				interactiveLabel.setText(Translate.text("polymesh:"+interactiveLabel
						.getText()));
				BLabel renderingLabel = ((BLabel) decoder
						.getObject("renderingLabel"));
				renderingLabel.setText(Translate.text("polymesh:"+renderingLabel
						.getText()));
				interactiveSpinner = ((BSpinner) decoder
						.getObject("interactiveSpinner"));
				interactiveSpinner.setValue(new Integer(
						backInteractiveSmoothness));
				interactiveSpinner.addEventLink(ValueChangedEvent.class, this,
						"doInteractiveSpinnerChanged");
				SpinnerNumberModel model = (SpinnerNumberModel) interactiveSpinner
						.getModel();
				model.setMaximum(new Integer(6));
				BLabel chooseLevelsLabel = ((BLabel) decoder
						.getObject("chooseLevelsLabel"));
				chooseLevelsLabel.setText(Translate.text("polymesh:"+chooseLevelsLabel
						.getText()));
				GridContainer okCancelGrid = ((GridContainer) decoder
						.getObject("OkCancelGrid"));
				BButton okButton = ((BButton) decoder.getObject("okButton"));
				okButton.addEventLink(CommandEvent.class, this, "doOK");
				okButton.setText(Translate.text("polymesh:ok"));
				BButton cancelButton = ((BButton) decoder
						.getObject("cancelButton"));
				cancelButton.addEventLink(CommandEvent.class, this, "doCancel");
				cancelButton.setText(Translate.text("polymesh:cancel"));
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
			pack();
			addEventLink(WindowClosingEvent.class, this, "doCancel");
			UIUtilities.centerDialog(this,PolyMeshEditorWindow.this);
			setVisible(true);

		}

		private void doCancel() {
			PolyMesh mesh = (PolyMesh) objInfo.object;
			mesh.setInteractiveSmoothLevel(backInteractiveSmoothness);
			objectChanged();
			updateImage();
			dispose();
		}

		private void doInteractiveSpinnerChanged() {
			PolyMesh mesh = (PolyMesh) objInfo.object;
			mesh.setInteractiveSmoothLevel(((Integer) interactiveSpinner
					.getValue()).intValue());
			objectChanged();
			updateImage();
		}

		private void doOK() {
			PolyMesh mesh = (PolyMesh) objInfo.object;
			setUndoRecord(new UndoRecord(PolyMeshEditorWindow.this, false,
					UndoRecord.COPY_OBJECT, new Object[] { mesh, prevMesh }));
			dispose();
		}
	}

	private class UnfoldStatusDialog extends BDialog {
		private BorderContainer borderContainer1;

		private BProgressBar progressBar;

		protected BTextArea textArea;

		private BButton proceedButton;

		private BButton advancedButton;

		private BLabel residualLabel;

		private RowContainer rowContainer1;

		private BTextField residualTF;

		private int status;

		protected boolean cancelled;

		protected double residual;

		private PMValueField residualVF;

		private Thread unfoldThread;

		public UnfoldStatusDialog() {
			super(PolyMeshEditorWindow.this, Translate.text("polymesh:meshUnfolding"),
					true);
			int nverts = ((PolyMesh) objInfo.object).getVertices().length;
			if (nverts < 1000) {
				residual = 0.001;
			} else {
				residual = 1;
			}
			InputStream inputStream = null;
			try {
				WidgetDecoder decoder = new WidgetDecoder(
						inputStream = getClass().getResource(
								"interfaces/unfoldStatus.xml").openStream(),
						PolyMeshPlugin.resources);
				borderContainer1 = (BorderContainer) decoder.getRootObject();
				progressBar = ((BProgressBar) decoder.getObject("progressBar"));
				textArea = ((BTextArea) decoder.getObject("TextArea"));
				rowContainer1 = ((RowContainer) decoder
						.getObject("RowContainer1"));
				proceedButton = ((BButton) decoder.getObject("proceedButton"));
				advancedButton = ((BButton) decoder.getObject("advancedButton"));
				advancedButton.addEventLink(CommandEvent.class, this,
						"doAdvancedButton");
				residualLabel = ((BLabel) decoder.getObject("residualLabel"));
				residualTF = ((BTextField) decoder.getObject("residualTF"));
				setContent(borderContainer1);
				proceedButton.addEventLink(CommandEvent.class, this,
						"doProceedButton");
				residualVF = new PMValueField(residual, ValueField.POSITIVE);
				residualVF.setTextField(residualTF);
				residualVF.setValue(residual);
				residualVF.addEventLink(ValueChangedEvent.class, this,
						"doResidualChanged");
				residualLabel.setVisible(false);
				residualVF.setVisible(false);
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
			status = 0;
			cancelled = false;
			pack();
			addEventLink(WindowClosingEvent.class, this, "doCancel");
			UIUtilities.centerDialog(this,PolyMeshEditorWindow.this);
			advancedButton.setVisible(false);
			progressBar.setProgressText("");
			progressBar.setEnabled(false);
			progressBar.setVisible(false);
			setVisible(true);
		}

		private void doAdvancedButton() {
			boolean showing = residualLabel.isVisible();
			residualLabel.setVisible(!showing);
			residualVF.setVisible(!showing);
			if (showing) {
				advancedButton.setText(Translate.text("polymesh:advanced"));
			} else {
				advancedButton.setText(Translate.text("polymesh:basic"));
			}
			rowContainer1.layoutChildren();
		}

		private void doProceedButton() {
			switch (status) {
			case 0:
				proceedButton.setText(Translate.text("polymesh:abort"));
				progressBar.setProgressText(Translate.text("polymesh:unfolding"));
				progressBar.setEnabled(true);
				progressBar.setVisible(true);
				pack();
				unfoldThread = (new Thread() {
					public void run() {
						doUnfold(UnfoldStatusDialog.this);
					}
				});
				unfoldThread.start();
				status = 1;
				break;
			case 1:
				unfoldThread.stop();
				doCancel();
				break;
			case 2:
				dispose();
				break;

			}
		}

		private void doCancel() {
			cancelled = true;
			switch (status) {
			case 1:
				//cancel thread
			case 0:
			case 2:
				dispose();
				break;
			}
		}

		private void unfoldFinished(boolean ok) {
			if (ok) {
				proceedButton.setText(Translate.text("polymesh:continue"));
				progressBar.setProgressText("");
				progressBar.setEnabled(false);
				progressBar.setVisible(false);
				pack();
				status = 2;
			} else {
				cancelled = true;
			}
		}

		private void doResidualChanged() {
			residual = residualVF.getValue();
		}
	}

	private class EdgeSmoothnessRangeDialog extends BDialog {
		private BorderContainer borderContainer1;

		private BTextField minSmoothnessTF;
		
		private PMValueField minSmoothnessVF;

		private BSlider minSmoothnessSlider;

		private BTextField maxSmoothnessTF;
		
		private PMValueField maxSmoothnessVF;

		private BSlider maxSmoothnessSlider;

		private BButton addButton;

		private BButton setButton;

		private BButton cancelButton;
		
		private boolean[] orSel, newSel;

		public EdgeSmoothnessRangeDialog() {
			super(PolyMeshEditorWindow.this, Translate
					.text("polymesh:smoothnessRange"), true);
			orSel = new boolean[selected.length];
			newSel = new boolean[selected.length];
			for (int i = 0; i < selected.length; i++) {
				orSel[i] = selected[i];
			}
			setSelection(newSel);
			InputStream inputStream = null;
			try {
				WidgetDecoder decoder = new WidgetDecoder(
						inputStream = getClass().getResource(
								"interfaces/smoothnessRange.xml").openStream(),
								PolyMeshPlugin.resources);
				borderContainer1 = (BorderContainer) decoder.getRootObject();
				minSmoothnessTF = ((BTextField) decoder
						.getObject("minSmoothnessTF"));
				minSmoothnessSlider = ((BSlider) decoder
						.getObject("minSmoothnessSlider"));
				maxSmoothnessTF = ((BTextField) decoder
						.getObject("maxSmoothnessTF"));
				maxSmoothnessSlider = ((BSlider) decoder
						.getObject("maxSmoothnessSlider"));
				minSmoothnessSlider.addEventLink(ValueChangedEvent.class, this, "doMinSliderChanged");
				maxSmoothnessSlider.addEventLink(ValueChangedEvent.class, this, "doMaxSliderChanged");
				addButton = ((BButton) decoder.getObject("addButton"));
				addButton.addEventLink(CommandEvent.class, this, "doAdd");
				setButton = ((BButton) decoder.getObject("setButton"));
				setButton.addEventLink(CommandEvent.class, this, "doSet");
				cancelButton = ((BButton) decoder.getObject("cancelButton"));
				cancelButton.addEventLink(CommandEvent.class, this, "doCancel");
				minSmoothnessVF = new PMValueField(0.0, ValueField.NONNEGATIVE);
				minSmoothnessVF.setTextField(minSmoothnessTF);
				minSmoothnessVF.setValue(0.0);
				maxSmoothnessVF = new PMValueField(1.0, ValueField.POSITIVE);
				maxSmoothnessVF.setTextField(maxSmoothnessTF);
				maxSmoothnessVF.setValue(1.0);
				maxSmoothnessSlider.setValue(100);
				minSmoothnessVF.addEventLink(ValueChangedEvent.class, this, "doValuesChanged");
				maxSmoothnessVF.addEventLink(ValueChangedEvent.class, this, "doValuesChanged");
				setContent(borderContainer1);
				addEventLink(WindowClosingEvent.class, this, "doCancel");
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
			updateSelection();
			pack();
			setVisible(true);
		}
		
		private void doMinSliderChanged() {
			minSmoothnessVF.setValue( ((Integer)minSmoothnessSlider.getValue()) /100.0);
			updateSelection();
		}
		
		private void doMaxSliderChanged() {
			maxSmoothnessVF.setValue( ((Integer)maxSmoothnessSlider.getValue()) /100.0);
			updateSelection();
		}
		
		private void doValuesChanged() {
			updateSelection();
		}
		
		private void updateSelection() {
			PolyMesh mesh = (PolyMesh) objInfo.object;
			Wedge[] edges = mesh.getEdges();
			double min = minSmoothnessVF.getValue();
			double max = maxSmoothnessVF.getValue();
			for (int i = 0; i < newSel.length; i++) {
				newSel[i] = (edges[i].smoothness >= min) & (edges[i].smoothness <= max );
			}
			setSelection(newSel);
		}

		private void doAdd() {
			for (int i = 0; i < orSel.length; i++) {
				orSel[i] |= newSel[i];
			}
			setSelection(orSel);
			dispose();
		}
		
		private void doSet() {
			setSelection(newSel);
			dispose();
		}
		
		private void doCancel() {
			setSelection(orSel);
			dispose();
		}
	}
}
