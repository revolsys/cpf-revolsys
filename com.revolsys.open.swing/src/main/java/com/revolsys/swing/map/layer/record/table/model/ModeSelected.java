package com.revolsys.swing.map.layer.record.table.model;

import java.util.List;

import javax.swing.Icon;
import javax.swing.ListSelectionModel;

import com.revolsys.swing.Icons;
import com.revolsys.swing.action.enablecheck.EnableCheck;
import com.revolsys.swing.action.enablecheck.ObjectPropertyEnableCheck;
import com.revolsys.swing.map.layer.record.AbstractRecordLayer;
import com.revolsys.swing.map.layer.record.LayerRecord;
import com.revolsys.util.Property;

public class ModeSelected extends ModeAbstractCached {
  private final EnableCheck enableCheck;

  public ModeSelected(final RecordLayerTableModel model) {
    super(RecordLayerTableModel.MODE_RECORDS_SELECTED, model);
    final AbstractRecordLayer layer = getLayer();
    this.enableCheck = new ObjectPropertyEnableCheck(layer, "hasSelectedRecords");
  }

  @Override
  public void activate() {
    final AbstractRecordLayer layer = getLayer();
    addListeners( //
      Property.addListenerRunnable(layer, "selectionCount", this::refresh), //
      Property.addListenerRunnable(layer, AbstractRecordLayer.RECORD_UPDATED,
        this::fireTableDataChanged) //
    );
    super.activate();
  }

  @Override
  protected boolean canAddCachedRecord(final LayerRecord record) {
    final AbstractRecordLayer layer = getLayer();
    if (layer.isSelected(record)) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public EnableCheck getEnableCheck() {
    return this.enableCheck;
  }

  @Override
  public Icon getIcon() {
    return Icons.getIcon("filter_selected");
  }

  @Override
  protected List<LayerRecord> getRecordsForCache() {
    final AbstractRecordLayer layer = getLayer();
    return layer.getSelectedRecords();
  }

  @Override
  public ListSelectionModel getSelectionModel() {
    final RecordLayerTableModel tableModel = getTableModel();
    return new RecordLayerHighlightedListSelectionModel(tableModel);
  }

  @Override
  public String getTitle() {
    return "Show Only Selected Records";
  }
}