package com.revolsys.swing.map.layer.dataobject;

import java.beans.PropertyChangeEvent;
import java.util.HashMap;
import java.util.Map;

import org.springframework.util.StringUtils;

import com.revolsys.gis.data.model.ArrayDataObject;
import com.revolsys.gis.data.model.Attribute;
import com.revolsys.gis.data.model.DataObjectMetaData;
import com.revolsys.gis.data.model.DataObjectState;
import com.revolsys.gis.model.data.equals.EqualsRegistry;
import com.revolsys.util.Property;

public class LayerDataObject extends ArrayDataObject {
  private static final long serialVersionUID = 1L;

  private final AbstractDataObjectLayer layer;

  private Map<String, Object> originalValues;

  public LayerDataObject(final AbstractDataObjectLayer layer) {
    super(layer.getMetaData());
    this.layer = layer;
  }

  protected void clearOriginalValues() {
    if (this.originalValues != null) {
      super.setValues(this.originalValues);
    }
    this.originalValues = null;
  }

  public void firePropertyChange(final String attributeName,
    final Object oldValue, final Object newValue) {
    final AbstractDataObjectLayer layer = getLayer();
    if (layer.isEventsEnabled()) {
      final PropertyChangeEvent event = new PropertyChangeEvent(this,
        attributeName, oldValue, newValue);
      layer.propertyChange(event);
    }
  }

  public AbstractDataObjectLayer getLayer() {
    return this.layer;
  }

  @SuppressWarnings("unchecked")
  public <T> T getOriginalValue(final String name) {
    if (this.originalValues != null) {
      if (this.originalValues.containsKey(name)) {
        return (T)this.originalValues.get(name);
      }
    }
    return (T)getValue(name);
  }

  public boolean isDeletable() {
    if (this.layer.isCanDeleteRecords()) {
      return !isDeleted();
    }
    return false;
  }

  public boolean isDeleted() {
    return getState() == DataObjectState.Deleted;
  }

  public boolean isGeometryEditable() {
    return true;
  }

  @Override
  public boolean isModified() {
    return this.originalValues != null;
  }

  public boolean isModified(final int index) {
    if (this.originalValues == null) {
      return false;
    } else {
      final String attributeName = getMetaData().getAttributeName(index);
      return isModified(attributeName);
    }
  }

  public boolean isModified(final String name) {
    if (this.originalValues == null) {
      return false;
    } else {
      return this.originalValues.containsKey(name);
    }
  }

  @Override
  public boolean isValid(final int index) {
    final DataObjectMetaData metaData = getMetaData();
    final String name = metaData.getAttributeName(index);
    return isValid(name);

  }

  public boolean isValid(final String name) {
    final Attribute attribute = getMetaData().getAttribute(name);
    if (attribute.isRequired()) {
      final Object value = getValue(name);
      if (value == null || value instanceof String
        && !StringUtils.hasText((String)value)) {
        return false;
      }
    }
    return true;
  }

  public LayerDataObject revertChanges() {
    if (this.originalValues != null || getState() == DataObjectState.Deleted) {
      setState(DataObjectState.Persisted);
      clearOriginalValues();
      setState(DataObjectState.Persisted);
      final AbstractDataObjectLayer layer = getLayer();
      layer.revertChanges(this);
      firePropertyChange("state", DataObjectState.Modified,
        DataObjectState.Persisted);
    }
    return this;
  }

  public void revertEmptyFields() {
    for (final String fieldName : getMetaData().getAttributeNames()) {
      final Object value = getValue(fieldName);
      if (Property.isEmpty(value)) {
        if (!layer.isFieldUserReadOnly(fieldName)) {
          final Object originalValue = getOriginalValue(fieldName);
          if (!Property.isEmpty(originalValue)) {
            setValue(fieldName, originalValue);
          }
        }
      }
    }
  }

  @Override
  public void setValue(final int index, final Object value) {
    final DataObjectMetaData metaData = getMetaData();
    final String attributeName = metaData.getAttributeName(index);

    final Object oldValue = getValue(index);
    if (!EqualsRegistry.INSTANCE.equals(oldValue, value)) {
      final AbstractDataObjectLayer layer = getLayer();
      final DataObjectState state = getState();
      if (DataObjectState.Initalizing.equals(state)) {
        // Allow modification on initialization
      } else if (DataObjectState.New.equals(state)) {
        if (!layer.isCanAddRecords()) {
          throw new IllegalStateException(
            "Adding new objects is not supported for layer " + layer);
        }
      } else if (DataObjectState.Deleted.equals(state)) {
        throw new IllegalStateException(
          "Cannot edit a deleted object for layer " + layer);
      } else {
        if (layer.isCanEditRecords()) {
          final Object originalValue = getOriginalValue(attributeName);
          if (EqualsRegistry.equal(value, originalValue)) {
            if (this.originalValues != null) {
              this.originalValues.remove(attributeName);
              if (this.originalValues.isEmpty()) {
                this.originalValues = null;
                setState(DataObjectState.Persisted);
              }
            }
          } else {
            if (this.originalValues == null) {
              this.originalValues = new HashMap<String, Object>();
            }
            this.originalValues.put(attributeName, originalValue);
          }
        } else {
          throw new IllegalStateException(
            "Editing objects is not supported for layer " + layer);
        }
      }
      super.setValue(index, value);
      if (!DataObjectState.Initalizing.equals(state)) {
        firePropertyChange(attributeName, oldValue, value);
      }
    }
  }
}
