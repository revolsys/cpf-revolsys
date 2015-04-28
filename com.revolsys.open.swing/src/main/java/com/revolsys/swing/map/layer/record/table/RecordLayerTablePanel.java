package com.revolsys.swing.map.layer.record.table;

import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;

import com.revolsys.collection.map.Maps;
import com.revolsys.data.query.Condition;
import com.revolsys.data.record.Record;
import com.revolsys.data.record.property.DirectionalAttributes;
import com.revolsys.data.record.schema.RecordDefinition;
import com.revolsys.data.types.DataType;
import com.revolsys.data.types.DataTypes;
import com.revolsys.io.map.MapSerializer;
import com.revolsys.io.map.MapSerializerUtil;
import com.revolsys.jts.geom.BoundingBox;
import com.revolsys.jts.geom.Geometry;
import com.revolsys.jts.geom.GeometryFactory;
import com.revolsys.swing.action.InvokeMethodAction;
import com.revolsys.swing.action.enablecheck.AndEnableCheck;
import com.revolsys.swing.action.enablecheck.EnableCheck;
import com.revolsys.swing.action.enablecheck.InvokeMethodEnableCheck;
import com.revolsys.swing.action.enablecheck.ObjectPropertyEnableCheck;
import com.revolsys.swing.action.enablecheck.OrEnableCheck;
import com.revolsys.swing.map.form.RecordLayerForm;
import com.revolsys.swing.map.layer.Project;
import com.revolsys.swing.map.layer.record.AbstractRecordLayer;
import com.revolsys.swing.map.layer.record.BatchUpdate;
import com.revolsys.swing.map.layer.record.LayerRecord;
import com.revolsys.swing.map.layer.record.SqlLayerFilter;
import com.revolsys.swing.map.layer.record.component.FieldFilterPanel;
import com.revolsys.swing.map.layer.record.table.model.RecordLayerTableModel;
import com.revolsys.swing.menu.MenuFactory;
import com.revolsys.swing.menu.WrappedMenuFactory;
import com.revolsys.swing.table.TablePanel;
import com.revolsys.swing.table.TableRowCount;
import com.revolsys.swing.table.record.editor.RecordTableCellEditor;
import com.revolsys.swing.table.record.model.RecordRowTableModel;
import com.revolsys.swing.table.record.row.RecordRowPropertyEnableCheck;
import com.revolsys.swing.table.record.row.RecordRowRunnable;
import com.revolsys.swing.toolbar.ToolBar;
import com.revolsys.util.Property;

public class RecordLayerTablePanel extends TablePanel implements PropertyChangeListener,
  MapSerializer {
  private static final String PLUGIN_NAME = "tableView";

  private static final long serialVersionUID = 1L;

  public static final String FILTER_GEOMETRY = "filter_geometry";

  public static final String FILTER_ATTRIBUTE = "filter_attribute";

  private AbstractRecordLayer layer;

  private RecordLayerTableModel tableModel;

  private final JToggleButton selectedButton;

  private final JButton fieldSetsButton;

  private FieldFilterPanel fieldFilterPanel;

  public RecordLayerTablePanel(final AbstractRecordLayer layer, final RecordLayerTable table) {
    super(table);
    this.layer = layer;
    this.tableModel = getTableModel();
    final Map<String, Object> config = layer.getPluginConfig(PLUGIN_NAME);
    setPluginConfig(config);
    layer.setPluginConfig(PLUGIN_NAME, this);

    // Right click Menu
    final MenuFactory menu = this.tableModel.getMenu();

    final RecordTableCellEditor tableCellEditor = table.getTableCellEditor();
    tableCellEditor.setPopupMenu(menu);

    table.getTableCellEditor().addMouseListener(this);
    table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    final RecordDefinition recordDefinition = layer.getRecordDefinition();
    final boolean hasGeometry = recordDefinition.getGeometryFieldIndex() != -1;
    final EnableCheck deletableEnableCheck = new RecordRowPropertyEnableCheck("deletable");

    final EnableCheck modifiedEnableCheck = new RecordRowPropertyEnableCheck("modified");
    final EnableCheck notDeletedEnableCheck = new ObjectPropertyEnableCheck(this, "recordDeleted",
      false);
    final OrEnableCheck modifiedOrDeleted = new OrEnableCheck(modifiedEnableCheck,
      new RecordRowPropertyEnableCheck("deleted"));

    final EnableCheck editableEnableCheck = new ObjectPropertyEnableCheck(layer, "editable");

    menu.addGroup(0, "default");
    menu.addGroup(1, "record");
    menu.addGroup(2, "dnd");

    final MenuFactory layerMenuFactory = MenuFactory.findMenu(layer);
    if (layerMenuFactory != null) {
      menu.addComponentFactory("default", 0, new WrappedMenuFactory("Layer", layerMenuFactory));
    }

    menu.addMenuItemTitleIcon("record", "View/Edit Record", "table_edit", notDeletedEnableCheck,
      this, "editRecord");

    if (hasGeometry) {
      menu.addMenuItemTitleIcon("record", "Zoom to Record", "magnifier_zoom_selected",
        notDeletedEnableCheck, this, "zoomToRecord");
    }
    menu.addMenuItemTitleIcon("record", "Delete Record", "table_row_delete", deletableEnableCheck,
      this, "deleteRecord");

    menu.addMenuItem("record", RecordRowRunnable.createAction("Revert Record", "arrow_revert",
      modifiedOrDeleted, "revertChanges"));

    menu.addMenuItem("record", RecordRowRunnable.createAction("Revert Empty Fields",
      "field_empty_revert", modifiedEnableCheck, "revertEmptyFields"));

    menu.addMenuItemTitleIcon("dnd", "Copy Record", "page_copy", this, "copyRecord");

    if (hasGeometry) {
      menu.addMenuItemTitleIcon("dnd", "Paste Geometry", "geometry_paste", new AndEnableCheck(
        editableEnableCheck, new InvokeMethodEnableCheck(this, "canPasteRecordGeometry")), this,
        "pasteGeometry");

      final MenuFactory editMenu = new MenuFactory("Edit Record Operations");
      editMenu.setEnableCheck(notDeletedEnableCheck);
      final DataType geometryDataType = recordDefinition.getGeometryField().getType();
      if (geometryDataType == DataTypes.LINE_STRING
        || geometryDataType == DataTypes.MULTI_LINE_STRING) {
        if (DirectionalAttributes.getProperty(recordDefinition).hasDirectionalAttributes()) {
          editMenu.addMenuItemTitleIcon("geometry", RecordLayerForm.FLIP_RECORD_NAME,
            RecordLayerForm.FLIP_RECORD_ICON, editableEnableCheck, this, "flipRecordOrientation");
          editMenu.addMenuItemTitleIcon("geometry", RecordLayerForm.FLIP_LINE_ORIENTATION_NAME,
            RecordLayerForm.FLIP_LINE_ORIENTATION_ICON, editableEnableCheck, this,
            "flipLineOrientation");
          editMenu.addMenuItemTitleIcon("geometry", RecordLayerForm.FLIP_FIELDS_NAME,
            RecordLayerForm.FLIP_FIELDS_ICON, editableEnableCheck, this, "flipFields");
        } else {
          editMenu.addMenuItemTitleIcon("geometry", "Flip Line Orientation", "flip_line",
            editableEnableCheck, this, "flipLineOrientation");
        }
      }
      menu.addComponentFactory("record", 2, editMenu);
    }

    // Toolbar
    final ToolBar toolBar = getToolBar();

    if (layerMenuFactory != null) {
      toolBar.addButtonTitleIcon("menu", "Layer Menu", "menu", layerMenuFactory, "show", layer,
        this, 10, 10);
    }
    if (hasGeometry) {
      final EnableCheck hasSelectedRecords = new ObjectPropertyEnableCheck(layer,
        "hasSelectedRecords");
      toolBar.addButton("layer", "Zoom to Selected", "magnifier_zoom_selected", hasSelectedRecords,
        layer, "zoomToSelected");
    }
    toolBar.addComponent("count", new TableRowCount(this.tableModel));

    toolBar.addButtonTitleIcon("table", "Refresh", "table_refresh", this, "refresh");

    this.fieldSetsButton = toolBar.addButtonTitleIcon("table", "Field Sets", "fields_filter", this,
      "actionShowFieldSetsMenu");

    this.fieldFilterPanel = new FieldFilterPanel(this, this.tableModel, config);
    toolBar.addComponent("search", this.fieldFilterPanel);

    toolBar.addButtonTitleIcon("search", "Advanced Search", "filter_edits", this.fieldFilterPanel,
      "showAdvancedFilter");

    final EnableCheck hasFilter = new ObjectPropertyEnableCheck(this.tableModel, "hasFilter");

    toolBar.addButton("search", "Clear Search", "filter_delete", hasFilter, this.fieldFilterPanel,
      "clear");

    // Filter buttons

    final JToggleButton clearFilter = toolBar.addToggleButtonTitleIcon(FILTER_ATTRIBUTE, -1,
      "Show All Records", "table_filter", this.tableModel, "setFieldFilterMode",
      RecordLayerTableModel.MODE_ALL);
    clearFilter.doClick();

    toolBar.addToggleButton(FILTER_ATTRIBUTE, -1, "Show Only Changed Records",
      "change_table_filter", editableEnableCheck, this.tableModel, "setFieldFilterMode",
      RecordLayerTableModel.MODE_EDITS);

    this.selectedButton = toolBar.addToggleButton(FILTER_ATTRIBUTE, -1,
      "Show Only Selected Records", "filter_selected", null, this.tableModel, "setFieldFilterMode",
      RecordLayerTableModel.MODE_SELECTED);

    if (hasGeometry) {
      final JToggleButton showAllGeometries = toolBar.addToggleButtonTitleIcon(FILTER_GEOMETRY, -1,
        "Show All Records ", "world_filter", this.tableModel, "setFilterByBoundingBox", false);
      showAllGeometries.doClick();

      toolBar.addToggleButtonTitleIcon(FILTER_GEOMETRY, -1, "Show Records on Map", "map_filter",
        this.tableModel, "setFilterByBoundingBox", true);
    }
    Property.addListener(layer, this);
  }

  public void actionShowFieldSetsMenu() {
    final JPopupMenu menu = new JPopupMenu();

    final JMenuItem editMenuItem = InvokeMethodAction.createMenuItem("Edit Field Sets",
      "fields_filter_edit", this.layer, "showProperties", "Field Sets");
    menu.add(editMenuItem);

    menu.addSeparator();

    final AbstractRecordLayer layer = getLayer();
    final String fieldSetName = layer.getFieldNamesSetName();
    for (final String fieldSetName2 : layer.getFieldNamesSetNames()) {
      final JCheckBoxMenuItem menuItem = InvokeMethodAction.createCheckBoxMenuItem(fieldSetName2,
        layer, "setFieldNamesSetName", fieldSetName2);
      if (fieldSetName2.equalsIgnoreCase(fieldSetName)) {
        menuItem.setSelected(true);
      }
      menu.add(menuItem);
    }
    MenuFactory.showMenu(menu, this.fieldSetsButton, 10, 10);
  }

  public boolean canPasteRecordGeometry() {
    final LayerRecord record = getEventRowObject();
    return this.layer.canPasteRecordGeometry(record);
  }

  public void copyRecord() {
    final LayerRecord record = getEventRowObject();
    this.layer.copyRecordsToClipboard(Collections.singletonList(record));
  }

  public void deleteRecord() {
    final LayerRecord record = getEventRowObject();
    this.layer.deleteRecords(record);
  }

  public void editRecord() {
    final LayerRecord record = getEventRowObject();
    this.layer.showForm(record);
  }

  public void flipFields() {
    final LayerRecord record = getEventRowObject();
    try (
      BatchUpdate batchUpdate = new BatchUpdate(record)) {
      final DirectionalAttributes property = DirectionalAttributes.getProperty(record);
      property.reverseAttributes(record);
    }
  }

  public void flipLineOrientation() {
    final LayerRecord record = getEventRowObject();
    try (
      BatchUpdate batchUpdate = new BatchUpdate(record)) {
      final DirectionalAttributes property = DirectionalAttributes.getProperty(record);
      property.reverseGeometry(record);
    }
  }

  public void flipRecordOrientation() {
    final LayerRecord record = getEventRowObject();
    try (
      BatchUpdate batchUpdate = new BatchUpdate(record)) {
      DirectionalAttributes.reverse(record);
    }
  }

  public Collection<? extends String> getColumnNames() {
    return this.layer.getFieldNamesSet();
  }

  protected LayerRecord getEventRowObject() {
    final RecordRowTableModel model = getTableModel();
    final int row = getEventRow();
    if (row > -1) {
      final LayerRecord record = model.getRecord(row);
      return record;
    } else {
      return null;
    }
  }

  public AbstractRecordLayer getLayer() {
    return this.layer;
  }

  @Override
  protected Object getMenuSource() {
    return this.layer;
  }

  @SuppressWarnings("unchecked")
  @Override
  public RecordLayerTable getTable() {
    return (RecordLayerTable)super.getTable();
  }

  @SuppressWarnings("unchecked")
  @Override
  public RecordLayerTableModel getTableModel() {
    final JTable table = getTable();
    return (RecordLayerTableModel)table.getModel();
  }

  @Override
  public boolean isCurrentCellEditable() {
    return super.isCurrentCellEditable() && this.layer.isCanEditRecords();
  }

  public boolean isRecordDeleted() {
    final int eventRow = TablePanel.getEventRow();
    if (eventRow != -1) {
      final LayerRecord record = this.tableModel.getRecord(eventRow);
      return record.isDeleted();
    }
    return false;
  }

  @Override
  public void mouseClicked(final MouseEvent e) {
    super.mouseClicked(e);
    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
      if (isEditing()) {
        final JTable table = getTable();
        final TableCellEditor cellEditor = table.getCellEditor();
        cellEditor.stopCellEditing();
      }
      editRecord();
    }
  }

  public void pasteGeometry() {
    final LayerRecord record = getEventRowObject();
    this.layer.pasteRecordGeometry(record);
  }

  @Override
  public void propertyChange(final PropertyChangeEvent event) {
    final Object source = event.getSource();
    if (source instanceof LayerRecord) {
      repaint();
    } else if (source == this.layer) {
      final String propertyName = event.getPropertyName();
      if ("recordsChanged".equals(propertyName)) {
        this.tableModel.refresh();
      }
      repaint();
    }
  }

  public void refresh() {
    this.tableModel.refresh();
  }

  @Override
  public void removeNotify() {
    final RecordLayerTable table = getTable();
    if (table != null) {
      final RecordTableCellEditor tableCellEditor = table.getTableCellEditor();
      tableCellEditor.close();
      table.dispose();
    }
    if (this.layer != null) {
      Property.removeListener(this.layer, this);
      this.layer.setPluginConfig(PLUGIN_NAME, toMap());
    }
    this.tableModel = null;
    this.layer = null;
    if (this.fieldFilterPanel != null) {
      this.fieldFilterPanel.close();
      this.fieldFilterPanel = null;
    }
    super.removeNotify();
  }

  public void setFieldFilterMode(final String mode) {
    if (RecordLayerTableModel.MODE_SELECTED.equals(mode)) {
      this.selectedButton.setSelected(true);
      this.selectedButton.doClick();
    }
  }

  protected void setPluginConfig(final Map<String, Object> config) {
    final Object orderBy = config.get("orderBy");
    if (orderBy instanceof Map) {
      @SuppressWarnings("unchecked")
      final Map<String, Boolean> order = (Map<String, Boolean>)orderBy;
      this.tableModel.setOrderBy(order);
    }
    final String fieldFilterMode = Maps.getString(config, "fieldFilterMode",
      RecordLayerTableModel.MODE_ALL);
    this.tableModel.setFieldFilterMode(fieldFilterMode);
  }

  @Override
  public Map<String, Object> toMap() {
    final Map<String, Object> map = new LinkedHashMap<>();
    MapSerializerUtil.add(map, "fieldFilterMode", this.tableModel.getFieldFilterMode());
    final Condition condition = this.tableModel.getFilter();
    if (this.fieldFilterPanel != null) {
      MapSerializerUtil.add(map, "searchField", this.fieldFilterPanel.getSearchFieldName());
    }
    if (condition != null) {
      final String sql = condition.toFormattedString();
      final SqlLayerFilter filter = new SqlLayerFilter(this.layer, sql);
      MapSerializerUtil.add(map, "filter", filter);
    }
    MapSerializerUtil.add(map, "orderBy", this.tableModel.getOrderBy());
    return map;
  }

  public void zoomToRecord() {
    final Record object = getEventRowObject();
    final Project project = this.layer.getProject();
    final Geometry geometry = object.getGeometryValue();
    if (geometry != null) {
      final GeometryFactory geometryFactory = project.getGeometryFactory();
      final BoundingBox boundingBox = geometry.getBoundingBox()
        .convert(geometryFactory)
        .expandPercent(0.1);
      project.setViewBoundingBox(boundingBox);
    }
  }
}
