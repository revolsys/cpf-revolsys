package com.revolsys.swing.undo;

import com.revolsys.equals.Equals;
import com.revolsys.geometry.model.Geometry;
import com.revolsys.record.property.DirectionalFields;
import com.revolsys.swing.map.layer.record.LayerRecord;

public class ReverseRecordGeometryUndo extends AbstractUndoableEdit {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  private final Geometry oldValue;

  private final LayerRecord record;

  public ReverseRecordGeometryUndo(final LayerRecord record) {
    this.record = record;
    this.oldValue = record.getGeometry();
  }

  @Override
  public boolean canRedo() {
    if (super.canRedo()) {
      final Geometry value = this.record.getGeometry();
      if (Equals.equal(value, this.oldValue)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean canUndo() {
    if (super.canUndo()) {
      final Geometry value = this.record.getGeometry();
      if (Equals.equal(value.reverse(), this.oldValue)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doRedo() {
    final DirectionalFields property = DirectionalFields.getProperty(this.record);
    property.reverseGeometry(this.record);
  }

  @Override
  protected void doUndo() {
    final DirectionalFields property = DirectionalFields.getProperty(this.record);
    property.reverseGeometry(this.record);
  }

  @Override
  public String toString() {
    return "Reverse geometry";
  }
}
