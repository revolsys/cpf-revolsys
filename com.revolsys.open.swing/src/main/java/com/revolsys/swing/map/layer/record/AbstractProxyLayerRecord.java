package com.revolsys.swing.map.layer.record;

import java.util.Collection;
import java.util.Map;

import com.revolsys.identifier.Identifier;
import com.revolsys.record.Record;
import com.revolsys.record.RecordState;
import com.revolsys.record.schema.FieldDefinition;

public abstract class AbstractProxyLayerRecord extends AbstractLayerRecord {
  public AbstractProxyLayerRecord(final AbstractRecordLayer layer) {
    super(layer);
  }

  protected <R extends LayerRecord> R addProxiedRecord(final R record) {
    final AbstractRecordLayer layer = getLayer();
    layer.addProxiedRecord(record);
    return record;
  }

  protected Identifier addProxiedRecordIdentifier(final Identifier identifier) {
    final AbstractRecordLayer layer = getLayer();
    layer.addProxiedRecordIdentifier(identifier);
    return identifier;
  }

  @Override
  public void cancelChanges() {
    final LayerRecord layerRecord = getProxiedRecord();
    if (layerRecord != null) {
      synchronized (layerRecord) {
        layerRecord.cancelChanges();
      }
    }
  }

  @Override
  public void clearChanges() {
    final LayerRecord layerRecord = getProxiedRecord();
    if (layerRecord != null) {
      synchronized (layerRecord) {
        layerRecord.clearChanges();
      }
    }
  }

  @Override
  public void firePropertyChange(final String propertyName, final Object oldValue,
    final Object newValue) {
    final LayerRecord layerRecord = getProxiedRecord();
    if (layerRecord != null) {
      layerRecord.firePropertyChange(propertyName, oldValue, newValue);
    }
  }

  @Override
  public <T> T getOriginalValue(final String name) {
    final LayerRecord layerRecord = getProxiedRecord();
    return layerRecord.getOriginalValue(name);
  }

  protected LayerRecord getProxiedRecord() {
    final AbstractRecordLayer layer = getLayer();
    final Identifier identifier = getIdentifier();
    final LayerRecord layerRecord = layer.getCachedRecord(identifier);
    return layerRecord;
  }

  protected Record getRecord() {
    return getProxiedRecord();
  }

  @Override
  public RecordState getState() {
    final Record record = getRecord();
    if (record == null) {
      return RecordState.DELETED;
    } else {
      return record.getState();
    }
  }

  @Override
  public <T> T getValue(final int index) {
    final Record record = getRecord();
    if (record == null) {
      return null;
    } else {
      return record.getValue(index);
    }
  }

  @Override
  public boolean isDeleted() {
    final LayerRecord record = getProxiedRecord();
    if (record == null) {
      return true;
    } else {
      return record.isDeleted();
    }
  }

  @Override
  public boolean isGeometryEditable() {
    final LayerRecord record = getProxiedRecord();
    return record.isGeometryEditable();
  }

  @Override
  public boolean isModified() {
    final LayerRecord record = getProxiedRecord();
    return record.isModified();
  }

  @Override
  public boolean isModified(final String name) {
    final LayerRecord record = getProxiedRecord();
    if (record == null) {
      return false;
    } else {
      return record.isModified(name);
    }
  }

  @Override
  public boolean isProxy() {
    return true;
  }

  @Override
  public boolean isSame(Record record) {
    if (record == this) {
      return true;
    } else {
      if (record instanceof AbstractProxyLayerRecord) {
        final AbstractProxyLayerRecord proxyRecord = (AbstractProxyLayerRecord)record;
        record = proxyRecord.getProxiedRecord();
      }
      final LayerRecord layerRecord = getProxiedRecord();
      return layerRecord.isSame(record);
    }
  }

  protected <R extends LayerRecord> R removeProxiedRecord(final R record) {
    final AbstractRecordLayer layer = getLayer();
    layer.removeProxiedRecord(record);
    return null;
  }

  protected Identifier removeProxiedRecordIdentifier(final Identifier identifier) {
    final AbstractRecordLayer layer = getLayer();
    layer.removeProxiedRecordIdentifier(identifier);
    return null;
  }

  @Override
  public LayerRecord revertChanges() {
    final LayerRecord layerRecord = getProxiedRecord();
    return layerRecord.revertChanges();
  }

  @Override
  public void setState(final RecordState state) {
    final Record record = getRecord();
    if (record != null) {
      record.setState(state);
    }
  }

  @Override
  protected boolean setValue(final FieldDefinition fieldDefinition, final Object value) {
    final int index = fieldDefinition.getIndex();
    final Record record = getRecord();
    return record.setValue(index, value);
  }

  @Override
  public void setValues(final Map<? extends String, ? extends Object> values,
    final Collection<String> fieldNames) {
    final Record record = getRecord();
    record.setValues(values, fieldNames);
  }
}
