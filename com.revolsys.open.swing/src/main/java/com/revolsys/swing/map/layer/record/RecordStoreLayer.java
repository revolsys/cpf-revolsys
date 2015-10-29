package com.revolsys.swing.map.layer.record;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.JComponent;
import javax.swing.SwingWorker;

import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import com.revolsys.collection.iterator.Iterators;
import com.revolsys.collection.map.Maps;
import com.revolsys.geometry.algorithm.index.RecordQuadTree;
import com.revolsys.geometry.cs.CoordinateSystem;
import com.revolsys.geometry.model.BoundingBox;
import com.revolsys.geometry.model.Geometry;
import com.revolsys.geometry.model.GeometryFactory;
import com.revolsys.gis.io.Statistics;
import com.revolsys.identifier.Identifier;
import com.revolsys.io.PathName;
import com.revolsys.io.Writer;
import com.revolsys.io.map.MapSerializerUtil;
import com.revolsys.predicate.Predicates;
import com.revolsys.record.Record;
import com.revolsys.record.RecordFactory;
import com.revolsys.record.RecordState;
import com.revolsys.record.Records;
import com.revolsys.record.code.CodeTable;
import com.revolsys.record.io.RecordReader;
import com.revolsys.record.io.RecordStoreConnectionManager;
import com.revolsys.record.query.Condition;
import com.revolsys.record.query.Q;
import com.revolsys.record.query.Query;
import com.revolsys.record.query.functions.F;
import com.revolsys.record.query.functions.WithinDistance;
import com.revolsys.record.schema.FieldDefinition;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.record.schema.RecordStore;
import com.revolsys.swing.SwingUtil;
import com.revolsys.swing.component.BasePanel;
import com.revolsys.swing.component.ValueField;
import com.revolsys.swing.layout.GroupLayouts;
import com.revolsys.swing.map.layer.AbstractLayer;
import com.revolsys.swing.map.layer.record.table.model.RecordSaveErrorTableModel;
import com.revolsys.swing.parallel.Invoke;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.Transaction;
import com.revolsys.util.Label;
import com.revolsys.util.Property;
import com.revolsys.util.enableable.Enabled;

public class RecordStoreLayer extends AbstractRecordLayer {

  public static AbstractLayer create(final Map<String, Object> properties) {
    return new RecordStoreLayer(properties);
  }

  private final Label cacheIdForm = new Label("form");

  /**
   * Caches of sets of {@link Record#getIdentifier()} for different purposes (e.g. selected records, deleted records).
   * Each cache has a separate cacheId. The cache id is recommended to be a private variable to prevent modification
   * of that cache.
   */
  private Map<Label, Set<Identifier>> cacheIdToRecordIdMap = new WeakHashMap<>();

  private BoundingBox loadedBoundingBox = BoundingBox.EMPTY;

  private BoundingBox loadingBoundingBox = BoundingBox.EMPTY;

  private SwingWorker<List<LayerRecord>, Void> loadingWorker;

  /** Cache of records from {@link Record#getIdentifier()} to {@link Record}. */
  private Map<Identifier, LayerRecord> recordIdToRecordMap = new WeakHashMap<>();

  private RecordStore recordStore;

  private PathName typePath;

  public RecordStoreLayer(final Map<String, ? extends Object> properties) {
    super(properties);
    setType("recordStoreLayer");
  }

  public RecordStoreLayer(final RecordStore recordStore, final PathName typePath,
    final boolean exists) {
    this.recordStore = recordStore;
    setExists(exists);
    setType("recordStoreLayer");

    final RecordDefinition recordDefinition = recordStore.getRecordDefinition(typePath);
    setTypePath(typePath);
    setRecordDefinition(recordDefinition);
  }

  @Override
  protected LayerRecord addRecordToCache(final Label cacheId, final LayerRecord record) {
    if (isLayerRecord(record)) {
      if (record.getState() == RecordState.DELETED && !isDeleted(record)) {
        return record;
      } else {
        final Identifier identifier = record.getIdentifier();
        if (identifier == null) {
          return super.addRecordToCache(cacheId, record);
        } else {
          synchronized (getSync()) {
            LayerRecord proxyRecord;
            if (record instanceof AbstractProxyLayerRecord) {
              proxyRecord = record;
            } else {
              getCachedRecord(identifier, record);
              proxyRecord = createProxyRecord(identifier);
            }
            Maps.addToSet(this.cacheIdToRecordIdMap, cacheId, identifier);
            return proxyRecord;
          }
        }
      }
    }
    return record;
  }

  /**
   * Remove any cached records that are currently not used.
   */
  @Override
  protected void cleanCachedRecords() {
    synchronized (getSync()) {
      super.cleanCachedRecords();
      final Set<Identifier> identifiers = new HashSet<>();
      for (final Set<Identifier> recordIds : this.cacheIdToRecordIdMap.values()) {
        if (recordIds != null) {
          identifiers.addAll(recordIds);
        }
      }
      for (final AbstractProxyLayerRecord record : getProxyRecords()) {
        final Identifier identifier = record.getIdentifier();
        if (identifier != null) {
          identifiers.add(identifier);
        }
      }
      synchronized (this.recordIdToRecordMap) {
        this.recordIdToRecordMap.keySet().retainAll(identifiers);
      }
    }
  }

  @Override
  public void clearCachedRecords(final Label cacheId) {
    synchronized (getSync()) {
      super.clearCachedRecords(cacheId);
      this.cacheIdToRecordIdMap.remove(cacheId);
    }
  }

  protected void clearLoading(final BoundingBox loadedBoundingBox) {
    synchronized (getSync()) {
      if (loadedBoundingBox == this.loadingBoundingBox) {
        firePropertyChange("loaded", false, true);
        this.loadedBoundingBox = this.loadingBoundingBox;
        this.loadingBoundingBox = BoundingBox.EMPTY;
        this.loadingWorker = null;
      }

    }
  }

  @Override
  public RecordStoreLayer clone() {
    final RecordStoreLayer clone = (RecordStoreLayer)super.clone();
    clone.cacheIdToRecordIdMap = new WeakHashMap<>();
    clone.loadedBoundingBox = BoundingBox.EMPTY;
    clone.loadingBoundingBox = BoundingBox.EMPTY;
    clone.loadingWorker = null;
    clone.recordIdToRecordMap = new WeakHashMap<>();
    return clone;
  }

  protected LoadingWorker createLoadingWorker(final BoundingBox boundingBox) {
    return new LoadingWorker(this, boundingBox);
  }

  @Override
  protected ValueField createPropertiesTabGeneralPanelSource(final BasePanel parent) {
    final ValueField panel = super.createPropertiesTabGeneralPanelSource(parent);
    final Map<String, String> connectionProperties = getProperty("connection");
    String connectionName = null;
    String url = null;
    String username = null;
    if (isExists()) {
      final RecordStore recordStore = getRecordStore();
      url = recordStore.getUrl();
      username = recordStore.getUsername();
    }
    if (connectionProperties != null) {
      connectionName = connectionProperties.get("name");
      if (!isExists()) {
        url = connectionProperties.get("url");
        username = connectionProperties.get("username");

      }
    }
    if (connectionName != null) {
      SwingUtil.addLabelledReadOnlyTextField(panel, "Record Store Name", connectionName);
    }
    if (url != null) {
      SwingUtil.addLabelledReadOnlyTextField(panel, "Record Store URL", url);
    }
    if (username != null) {
      SwingUtil.addLabelledReadOnlyTextField(panel, "Record Store Username", username);
    }
    SwingUtil.addLabelledReadOnlyTextField(panel, "Type Path", this.typePath);

    GroupLayouts.makeColumns(panel, 2, true);
    return panel;
  }

  @SuppressWarnings("unchecked")
  protected <V extends LayerRecord> V createProxyRecord(final Identifier identifier) {
    return (V)new IdentifierProxyLayerRecord(this, identifier);
  }

  @Override
  public void delete() {
    super.delete();
    if (this.recordStore != null) {
      final Map<String, String> connectionProperties = getProperty("connection");
      if (connectionProperties != null) {
        final Map<String, Object> config = new HashMap<>();
        config.put("connection", connectionProperties);
        RecordStoreConnectionManager.releaseRecordStore(config);
      }
      this.recordStore = null;
    }
    final SwingWorker<List<LayerRecord>, Void> loadingWorker = this.loadingWorker;
    this.cacheIdToRecordIdMap = Collections.emptyMap();
    this.loadedBoundingBox = BoundingBox.EMPTY;
    this.loadingBoundingBox = BoundingBox.EMPTY;
    this.loadingWorker = null;
    this.recordIdToRecordMap = Collections.emptyMap();
    this.typePath = null;
    if (loadingWorker != null) {
      loadingWorker.cancel(true);
    }
  }

  @Override
  protected boolean doInitialize() {
    RecordStore recordStore = this.recordStore;
    if (recordStore == null) {
      final Map<String, String> connectionProperties = getProperty("connection");
      if (connectionProperties == null) {
        LoggerFactory.getLogger(getClass()).error(
          "A record store layer requires a connectionProperties entry with a name or url, username, and password: "
            + getPath());
        return false;
      } else {
        final Map<String, Object> config = new HashMap<>();
        config.put("connection", connectionProperties);
        recordStore = RecordStoreConnectionManager.getRecordStore(config);

        if (recordStore == null) {
          LoggerFactory.getLogger(getClass())
            .error("Unable to create record store for layer: " + getPath());
          return false;
        } else {
          try {
            recordStore.initialize();
          } catch (final Throwable e) {
            throw new RuntimeException("Unable to iniaitlize record store for layer " + getPath(),
              e);
          }

          setRecordStore(recordStore);
        }
      }
    }
    final PathName typePath = getTypePath();
    RecordDefinition recordDefinition = getRecordDefinition();
    if (recordDefinition == null) {
      recordDefinition = recordStore.getRecordDefinition(typePath);
      if (recordDefinition == null) {
        recordDefinition = recordStore.getRecordDefinition(typePath);
        LoggerFactory.getLogger(getClass())
          .error("Cannot find table " + typePath + " for layer " + getPath());
        return false;
      } else {
        setRecordDefinition(recordDefinition);
        return true;
      }
    } else {
      return true;
    }
  }

  @SuppressWarnings({
    "unchecked", "rawtypes"
  })
  @Override
  protected List<LayerRecord> doQuery(final BoundingBox boundingBox) {
    try (
      final Enabled enabled = eventsDisabled()) {
      final GeometryFactory geometryFactory = getGeometryFactory();
      final BoundingBox queryBoundingBox = boundingBox.convert(geometryFactory);
      if (this.loadedBoundingBox.covers(queryBoundingBox)) {
        return (List)getIndex().queryIntersects(queryBoundingBox);
      } else {
        final List<LayerRecord> records = getRecordsFromRecordStore(queryBoundingBox);
        return records;
      }
    }
  }

  @Override
  public List<LayerRecord> doQuery(final Geometry geometry, final double distance) {
    if (geometry == null) {
      return Collections.emptyList();
    } else {
      final RecordDefinition recordDefinition = getRecordDefinition();
      final FieldDefinition geometryField = getGeometryField();
      final WithinDistance where = F.dWithin(geometryField, geometry, distance);
      final Query query = new Query(recordDefinition, where);
      return query(query);
    }
  }

  @SuppressWarnings({
    "rawtypes", "unchecked"
  })
  @Override
  protected List<LayerRecord> doQueryBackground(final BoundingBox boundingBox) {
    if (boundingBox == null || boundingBox.isEmpty()) {
      return Collections.emptyList();
    } else {
      synchronized (getSync()) {
        final BoundingBox loadBoundingBox = boundingBox.expandPercent(0.2);
        if (!this.loadedBoundingBox.covers(boundingBox)
          && !this.loadingBoundingBox.covers(boundingBox)) {
          if (this.loadingWorker != null) {
            this.loadingWorker.cancel(true);
          }
          this.loadingBoundingBox = loadBoundingBox;
          this.loadingWorker = createLoadingWorker(loadBoundingBox);
          Invoke.worker(this.loadingWorker);
        }
      }
      final RecordQuadTree index = getIndex();

      final List<LayerRecord> records = (List)index.queryIntersects(boundingBox);
      return records;
    }
  }

  @Override
  protected void doRefresh() {
    synchronized (getSync()) {
      if (this.loadingWorker != null) {
        this.loadingWorker.cancel(true);
      }
      this.loadedBoundingBox = BoundingBox.EMPTY;
      this.loadingBoundingBox = this.loadedBoundingBox;
      setIndexRecords(null);
      cleanCachedRecords();
    }
    final RecordStore recordStore = getRecordStore();
    final PathName typePath = getTypePath();
    final CodeTable codeTable = recordStore.getCodeTable(typePath);
    if (codeTable != null) {
      codeTable.refresh();
    }
    super.doRefresh();
  }

  @Override
  protected boolean doSaveChanges(final RecordSaveErrorTableModel errors,
    final LayerRecord record) {
    final boolean deleted = super.isDeleted(record);

    if (isExists()) {
      if (this.recordStore != null) {
        final RecordStore recordStore = getRecordStore();
        final PlatformTransactionManager transactionManager = recordStore.getTransactionManager();
        try (
          Transaction transaction = new Transaction(transactionManager, Propagation.REQUIRES_NEW)) {
          try {
            try (
              final Writer<Record> writer = recordStore.newRecordWriter()) {
              if (isRecordCached(this.getCacheIdDeleted(), record) || super.isDeleted(record)) {
                preDeleteRecord(record);
                record.setState(RecordState.DELETED);
                writeDelete(writer, record);
              } else {
                final RecordDefinition recordDefinition = getRecordDefinition();
                final int fieldCount = recordDefinition.getFieldCount();
                for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                  record.validateField(fieldIndex);
                }
                if (super.isModified(record)) {
                  writeUpdate(writer, record);
                } else if (super.isNew(record)) {
                  Identifier identifier = record.getIdentifier();
                  final List<String> idFieldNames = recordDefinition.getIdFieldNames();
                  if (identifier == null && !idFieldNames.isEmpty()) {
                    identifier = recordStore.newPrimaryIdentifier(this.typePath);
                    if (identifier != null) {
                      identifier.setIdentifier(record, idFieldNames);
                    }
                  }
                  writer.write(record);
                }
              }
            }
            if (!deleted) {
              record.setState(RecordState.PERSISTED);
            }
            return true;
          } catch (final Throwable e) {
            throw transaction.setRollbackOnly(e);
          }
        }
      }
    }
    return false;
  }

  @Override
  public void forEach(final Query query, final Consumer<LayerRecord> consumer) {
    if (isExists()) {
      final RecordStore recordStore = getRecordStore();
      if (recordStore != null) {
        final Predicate<Record> filter = query.getWhereCondition();
        final Map<String, Boolean> orderBy = query.getOrderBy();

        final List<LayerRecord> changedRecords = new ArrayList<>();
        changedRecords.addAll(getNewRecords());
        changedRecords.addAll(getModifiedRecords());
        Records.filterAndSort(changedRecords, filter, orderBy);
        final Iterator<LayerRecord> changedIterator = changedRecords.iterator();
        LayerRecord currentChangedRecord = Iterators.next(changedIterator);

        final Comparator<Record> comparator = Records.newComparatorOrderBy(orderBy);
        try (
          final Enabled enabled = eventsDisabled();
          final RecordReader reader = newRecordStoreRecordReader(query);) {
          for (LayerRecord record : reader.<LayerRecord> i()) {
            boolean write = true;
            final Identifier identifier = getId(record);
            if (identifier != null) {
              final LayerRecord cachedRecord = this.recordIdToRecordMap.get(identifier);
              if (cachedRecord != null) {
                record = cachedRecord;
                if (record.isChanged()) {
                  write = false;
                }
              }
            }
            if (write) {
              while (currentChangedRecord != null
                && comparator.compare(currentChangedRecord, record) < 0) {
                consumer.accept(currentChangedRecord);
                currentChangedRecord = Iterators.next(changedIterator);
              }
              consumer.accept(record);
            }
          }
          while (currentChangedRecord != null) {
            consumer.accept(currentChangedRecord);
            currentChangedRecord = Iterators.next(changedIterator);
          }
        }
      }
    }
  }

  @Override
  public BoundingBox getBoundingBox() {
    if (hasGeometryField()) {
      final CoordinateSystem coordinateSystem = getCoordinateSystem();
      if (coordinateSystem != null) {
        return coordinateSystem.getAreaBoundingBox();
      }
    }
    return BoundingBox.EMPTY;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <V extends LayerRecord> V getCachedRecord(final Identifier identifier) {
    final RecordDefinition recordDefinition = getRecordDefinition();
    final List<String> idFieldNames = recordDefinition.getIdFieldNames();
    if (idFieldNames.isEmpty()) {
      LoggerFactory.getLogger(getClass()).error(this.typePath + " does not have a primary key");
      return null;
    } else {
      synchronized (this.recordIdToRecordMap) {
        LayerRecord record = this.recordIdToRecordMap.get(identifier);
        if (record == null) {
          final Condition where = getCachedRecordQuery(idFieldNames, identifier);
          final Query query = new Query(recordDefinition, where);
          try (
            RecordReader reader = newRecordStoreRecordReader(query)) {
            record = reader.getFirst();
            if (record != null) {
              this.recordIdToRecordMap.put(identifier, record);
            }
          }
        }
        return (V)record;
      }
    }
  }

  /**
   * Get the record from the cache if it exists, otherwise add this record to the cache
   *
   * @param identifier
   * @param record
   */
  protected LayerRecord getCachedRecord(final Identifier identifier, final LayerRecord record) {
    assert!(record instanceof AbstractProxyLayerRecord);
    synchronized (this.recordIdToRecordMap) {
      final LayerRecord cachedRecord = this.recordIdToRecordMap.get(identifier);
      if (cachedRecord == null) {
        this.recordIdToRecordMap.put(identifier, record);
        return record;
      } else {
        // TODO see if it has been updated and refresh values if appropriate
        return cachedRecord;
      }
    }
  }

  @Override
  public int getCachedRecordCount(final Label cacheId) {
    int count = super.getCachedRecordCount(cacheId);
    final Set<Identifier> identifiers = this.cacheIdToRecordIdMap.get(cacheId);
    if (identifiers != null) {
      count += identifiers.size();
    }
    return count;
  }

  protected Condition getCachedRecordQuery(final List<String> idFieldNames,
    final Identifier identifier) {
    return Q.equalId(idFieldNames, identifier);
  }

  @Override
  public List<LayerRecord> getCachedRecords(final Label cacheId) {
    synchronized (getSync()) {
      final List<LayerRecord> records = super.getCachedRecords(cacheId);
      final Set<Identifier> recordIds = this.cacheIdToRecordIdMap.get(cacheId);
      if (recordIds != null) {
        for (final Identifier recordId : recordIds) {
          final LayerRecord record = getRecordById(recordId);
          if (record != null) {
            records.add(record);
          }
        }
      }
      return records;
    }
  }

  public FieldDefinition getGeometryField() {
    final RecordDefinition recordDefinition = getRecordDefinition();
    if (recordDefinition == null) {
      return null;
    } else {
      return recordDefinition.getGeometryField();
    }
  }

  protected Identifier getId(final LayerRecord record) {
    if (isLayerRecord(record)) {
      return record.getIdentifier();
    } else {
      return null;
    }
  }

  public BoundingBox getLoadingBoundingBox() {
    return this.loadingBoundingBox;
  }

  @Override
  public int getPersistedRecordCount() {
    if (isExists()) {
      final RecordDefinition recordDefinition = getRecordDefinition();
      final Query query = new Query(recordDefinition);
      return super.getRecordCountPersisted(query);
    }
    return 0;
  }

  @Override
  public List<LayerRecord> getPersistedRecords(final Query query) {
    final List<LayerRecord> records = new ArrayList<>();
    if (isExists()) {
      final RecordStore recordStore = getRecordStore();
      if (recordStore != null) {
        try (
          final Enabled enabled = eventsDisabled();
          final RecordReader reader = newRecordStoreRecordReader(query);) {
          final Statistics statistics = query.getProperty("statistics");
          for (final LayerRecord record : reader.<LayerRecord> i()) {
            final Identifier identifier = getId(record);
            if (identifier == null) {
              records.add(record);
            } else {
              synchronized (getSync()) {
                final LayerRecord cachedRecord = getCachedRecord(identifier, record);
                if (!cachedRecord.isDeleted()) {
                  final LayerRecord proxyRecord = createProxyRecord(identifier);
                  records.add(proxyRecord);
                }
              }
            }
            if (statistics != null) {
              statistics.add(record);
            }
          }
        }
      }
    }
    return records;
  }

  @Override
  public LayerRecord getRecordById(final Identifier identifier) {
    final LayerRecord record = getCachedRecord(identifier);
    if (record == null) {
      return record;
    } else {
      return createProxyRecord(identifier);
    }
  }

  @Override
  public int getRecordCount(final Query query) {
    int count = 0;
    count += Predicates.count(getNewRecords(), query.getWhereCondition());
    count += Predicates.count(getModifiedRecords(), query.getWhereCondition());
    count += getRecordCountPersisted(query);
    count -= Predicates.count(getDeletedRecords(), query.getWhereCondition());
    return count;
  }

  @Override
  public int getRecordCountPersisted(final Query query) {
    if (isExists()) {
      final RecordStore recordStore = getRecordStore();
      if (recordStore != null) {
        return recordStore.getRecordCount(query);
      }
    }
    return 0;
  }

  protected List<LayerRecord> getRecordsFromRecordStore(final BoundingBox boundingBox) {
    final RecordDefinition recordDefinition = getRecordDefinition();
    final Query query = Query.intersects(recordDefinition, boundingBox);
    return query(query);
  }

  @Override
  public RecordStore getRecordStore() {
    return this.recordStore;
  }

  @Override
  public PathName getTypePath() {
    return this.typePath;
  }

  @Override
  public boolean isLayerRecord(final Record record) {
    if (record instanceof LoadingRecord) {
      return false;
    } else if (record instanceof LayerRecord) {
      final LayerRecord layerRecord = (LayerRecord)record;
      if (layerRecord.getLayer() == this) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isRecordCached(final Label cacheId, final LayerRecord record) {
    if (isLayerRecord(record)) {
      synchronized (getSync()) {
        final Identifier identifier = record.getIdentifier();
        if (identifier != null) {
          if (Maps.collectionContains(this.cacheIdToRecordIdMap, cacheId, identifier)) {
            return true;
          }
        }
        return super.isRecordCached(cacheId, record);
      }
    }
    return false;
  }

  @Override
  public LayerRecord newLayerRecord(final Map<String, Object> values) {
    if (!isReadOnly() && isEditable() && isCanAddRecords()) {
      final LayerRecord record = new NewProxyLayerRecord(this, values);
      addRecordToCache(getCacheIdNew(), record);
      if (isEventsEnabled()) {
        cleanCachedRecords();
      }
      fireRecordInserted(record);
      return record;
    } else {
      return null;
    }
  }

  @Override
  protected RecordStoreLayerRecord newLayerRecord(final RecordDefinition recordDefinition) {
    if (recordDefinition.equals(getRecordDefinition())) {
      return new RecordStoreLayerRecord(this);
    } else {
      throw new IllegalArgumentException("Cannot create records for " + recordDefinition);
    }
  }

  protected RecordReader newRecordStoreRecordReader(final Query query) {
    final RecordStore recordStore = getRecordStore();
    if (recordStore == null) {
      return RecordReader.empty();
    } else {
      final RecordFactory<LayerRecord> recordFactory = getRecordFactory();
      query.setRecordFactory(recordFactory);
      return recordStore.getRecords(query);
    }
  }

  @Override
  protected boolean postSaveDeletedRecord(final LayerRecord record) {
    final boolean deleted = super.postSaveDeletedRecord(record);
    if (deleted) {
      removeRecordFromCache(this.getCacheIdDeleted(), record);
    }
    return deleted;
  }

  protected void preDeleteRecord(final LayerRecord record) {
  }

  @Override
  protected void removeForm(final LayerRecord record) {
    synchronized (getSync()) {
      final Identifier id = getId(record);
      if (id != null) {
        removeRecordFromCache(this.cacheIdForm, record);
      }
      super.removeForm(record);
    }
  }

  @Override
  protected boolean removeRecordFromCache(final Label cacheId, final LayerRecord record) {
    boolean removed = false;
    if (isLayerRecord(record)) {
      final Identifier identifier = record.getIdentifier();
      if (identifier != null) {
        synchronized (getSync()) {
          removed = Maps.removeFromSet(this.cacheIdToRecordIdMap, cacheId, identifier);
        }
      }
    }
    removed |= super.removeRecordFromCache(cacheId, record);
    return removed;
  }

  @Override
  protected boolean removeRecordFromCache(final LayerRecord record) {
    synchronized (getSync()) {
      boolean removed = false;
      if (isLayerRecord(record)) {
        for (final Label cacheId : new ArrayList<>(this.cacheIdToRecordIdMap.keySet())) {
          removed |= removeRecordFromCache(cacheId, record);
        }
      }
      removed |= super.removeRecordFromCache(record);
      return removed;
    }
  }

  @Override
  public void revertChanges(final LayerRecord record) {
    removeRecordFromCache(this.getCacheIdDeleted(), record);
    super.revertChanges(record);
  }

  protected void setIndexRecords(final BoundingBox loadedBoundingBox,
    final List<LayerRecord> records) {
    synchronized (getSync()) {
      if (loadedBoundingBox == this.loadingBoundingBox) {
        setIndexRecords(records);
        clearLoading(loadedBoundingBox);
      }
    }
    firePropertyChange("refresh", false, true);
  }

  @Override
  public void setProperty(final String name, final Object value) {
    if ("typePath".equals(name)) {
      super.setProperty(name, PathName.newPathName(value));
    } else {
      super.setProperty(name, value);
    }
  }

  public <V extends LayerRecord> List<V> setRecordsToCache(final Label cacheId,
    final Collection<? extends LayerRecord> records) {
    synchronized (getSync()) {
      this.cacheIdToRecordIdMap.put(cacheId, new HashSet<>());
      return addRecordsToCache(cacheId, records);
    }
  }

  protected void setRecordStore(final RecordStore recordStore) {
    this.recordStore = recordStore;
  }

  public void setTypePath(final PathName typePath) {
    this.typePath = typePath;
    if (this.typePath != null) {
      if (!Property.hasValue(getName())) {
        setName(this.typePath.getName());
      }
      if (isExists()) {
        final RecordStore recordStore = getRecordStore();
        if (recordStore != null) {
          final RecordDefinition recordDefinition = recordStore.getRecordDefinition(this.typePath);
          if (recordDefinition != null) {

            setRecordDefinition(recordDefinition);
            return;
          }
        }
      }
    }
    setRecordDefinition(null);
  }

  @Override
  public <V extends JComponent> V showForm(LayerRecord record) {
    synchronized (getSync()) {
      final Identifier identifier = getId(record);
      if (identifier != null) {
        record = addRecordToCache(this.cacheIdForm, record);
      }
      return super.showForm(record);
    }
  }

  @Override
  public Map<String, Object> toMap() {
    final Map<String, Object> map = super.toMap();
    MapSerializerUtil.add(map, "typePath", this.typePath);
    return map;
  }

  protected void writeDelete(final Writer<Record> writer, final LayerRecord record) {
    writer.write(record);
  }

  protected void writeUpdate(final Writer<Record> writer, final LayerRecord record) {
    writer.write(record);
  }

}
