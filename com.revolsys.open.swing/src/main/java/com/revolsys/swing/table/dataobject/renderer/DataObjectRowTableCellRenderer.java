package com.revolsys.swing.table.dataobject.renderer;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;

import com.revolsys.converter.string.BigDecimalStringConverter;
import com.revolsys.swing.table.dataobject.model.DataObjectRowTableModel;

public class DataObjectRowTableCellRenderer extends DefaultTableCellRenderer {
  private static final long serialVersionUID = 1L;

  public DataObjectRowTableCellRenderer() {
    setBorder(new EmptyBorder(1, 2, 1, 2));
    setOpaque(true);
  }

  @Override
  public Component getTableCellRendererComponent(final JTable table,
    final Object value, final boolean isSelected, final boolean hasFocus,
    final int rowIndex, final int columnIndex) {
    final DataObjectRowTableModel model = (DataObjectRowTableModel)table.getModel();

    final boolean selected = model.isSelected(isSelected, rowIndex, columnIndex);
    final Object displayValue;
    final int attributesOffset = model.getAttributesOffset();
    if (columnIndex < attributesOffset) {
      displayValue = value;
    } else {
      displayValue = model.toDisplayValue(columnIndex, value);
    }
    super.getTableCellRendererComponent(table, displayValue, selected,
      hasFocus, rowIndex, columnIndex);
    if (BigDecimalStringConverter.isNumber(displayValue)) {
      setHorizontalAlignment(SwingConstants.RIGHT);
      setHorizontalTextPosition(SwingConstants.RIGHT);
    } else {
      setHorizontalAlignment(SwingConstants.LEFT);
      setHorizontalTextPosition(SwingConstants.LEFT);
    }
    return this;
  }
}
