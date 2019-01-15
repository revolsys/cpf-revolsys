package com.revolsys.jdbc.io;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.revolsys.collection.ResultPager;
import com.revolsys.collection.iterator.AbstractIterator;
import com.revolsys.collection.map.Maps;
import com.revolsys.datatype.DataTypes;
import com.revolsys.identifier.Identifier;
import com.revolsys.io.PathName;
import com.revolsys.io.PathUtil;
import com.revolsys.jdbc.JdbcConnection;
import com.revolsys.jdbc.JdbcUtils;
import com.revolsys.jdbc.field.JdbcFieldAdder;
import com.revolsys.jdbc.field.JdbcFieldDefinition;
import com.revolsys.logging.Logs;
import com.revolsys.record.ArrayRecord;
import com.revolsys.record.Record;
import com.revolsys.record.RecordFactory;
import com.revolsys.record.RecordState;
import com.revolsys.record.code.AbstractCodeTable;
import com.revolsys.record.io.RecordStoreExtension;
import com.revolsys.record.io.RecordStoreQueryReader;
import com.revolsys.record.io.RecordWriter;
import com.revolsys.record.property.GlobalIdProperty;
import com.revolsys.record.query.Query;
import com.revolsys.record.schema.AbstractRecordStore;
import com.revolsys.record.schema.FieldDefinition;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.record.schema.RecordDefinitionImpl;
import com.revolsys.record.schema.RecordStore;
import com.revolsys.record.schema.RecordStoreSchema;
import com.revolsys.record.schema.RecordStoreSchemaElement;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.Transaction;
import com.revolsys.util.Booleans;
import com.revolsys.util.Exceptions;
import com.revolsys.util.Property;

public abstract class AbstractJdbcRecordStore extends AbstractRecordStore
  implements JdbcRecordStore, RecordStoreExtension {
  public static final List<String> DEFAULT_PERMISSIONS = Arrays.asList("SELECT");

  public static final AbstractIterator<Record> newJdbcIterator(final RecordStore recordStore,
    final Query query, final Map<String, Object> properties) {
    return new JdbcQueryIterator((AbstractJdbcRecordStore)recordStore, query, properties);
  }

  private final Set<String> allSchemaNames = new TreeSet<>();

  private int batchSize;

  private JdbcDatabaseFactory databaseFactory;

  private DataSource dataSource;

  private final Object exceptionWriterKey = new Object();

  private Set<String> excludeTablePaths = new HashSet<>();

  private List<String> excludeTablePatterns = new ArrayList<>();

  private final Map<String, JdbcFieldAdder> fieldDefinitionAdders = new HashMap<>();

  private boolean flushBetweenTypes;

  private String primaryKeySql;

  private String primaryKeyTableCondition;

  private String schemaPermissionsSql;

  private String schemaTablePermissionsSql;

  private final Map<String, String> sequenceTypeSqlMap = new HashMap<>();

  private String sqlPrefix;

  private String sqlSuffix;

  private DataSourceTransactionManager transactionManager;

  private final Object writerKey = new Object();

  public AbstractJdbcRecordStore() {
    this(ArrayRecord.FACTORY);
  }

  public AbstractJdbcRecordStore(final DataSource dataSource) {
    this();
    setDataSource(dataSource);
  }

  public AbstractJdbcRecordStore(final JdbcDatabaseFactory databaseFactory,
    final Map<String, ? extends Object> connectionProperties) {
    this(databaseFactory, ArrayRecord.FACTORY);
    setConnectionProperties(connectionProperties);
    final DataSource dataSource = databaseFactory.newDataSource(connectionProperties);
    setDataSource(dataSource);
    try (
      JdbcConnection jdbcConnection = getJdbcConnection()) {
    }
  }

  public AbstractJdbcRecordStore(final JdbcDatabaseFactory databaseFactory,
    final RecordFactory<? extends Record> recordFactory) {
    this(recordFactory);
    this.databaseFactory = databaseFactory;
  }

  public AbstractJdbcRecordStore(final RecordFactory<? extends Record> recordFactory) {
    super(recordFactory);
    setIteratorFactory(new RecordStoreIteratorFactory(AbstractJdbcRecordStore::newJdbcIterator));
    addRecordStoreExtension(this);
  }

  protected void addAllSchemaNames(final String schemaName) {
    this.allSchemaNames.add(schemaName.toUpperCase());
  }

  public void addExcludeTablePaths(final String tableName) {
    addExcludeTablePaths(tableName);
  }

  protected JdbcFieldDefinition addField(final JdbcRecordDefinition recordDefinition,
    final String dbColumnName, final String name, final String dataType, final int sqlType,
    final int length, final int scale, final boolean required, final String description) {
    JdbcFieldAdder attributeAdder = this.fieldDefinitionAdders.get(dataType);
    if (attributeAdder == null) {
      attributeAdder = new JdbcFieldAdder(DataTypes.OBJECT);
    }
    return (JdbcFieldDefinition)attributeAdder.addField(this, recordDefinition, dbColumnName, name,
      dataType, sqlType, length, scale, required, description);
  }

  protected void addField(final ResultSetMetaData resultSetMetaData,
    final JdbcRecordDefinition recordDefinition, final String name, final int i,
    final String description) throws SQLException {
    final String dataType = resultSetMetaData.getColumnTypeName(i);
    final int sqlType = resultSetMetaData.getColumnType(i);
    final int length = resultSetMetaData.getPrecision(i);
    final int scale = resultSetMetaData.getScale(i);
    final boolean required = false;
    addField(recordDefinition, name, name.toUpperCase(), dataType, sqlType, length, scale, required,
      description);
  }

  public void addFieldAdder(final String sqlTypeName, final JdbcFieldAdder adder) {
    this.fieldDefinitionAdders.put(sqlTypeName, adder);
  }

  /**
   * Add a new field definition for record definitions that don't have a primary key.
   *
   * @param recordDefinition
   */
  protected void addRowIdFieldDefinition(final RecordDefinitionImpl recordDefinition) {
    final Object tableType = recordDefinition.getProperty("tableType");
    if ("TABLE".equals(tableType)) {
      final JdbcFieldDefinition idFieldDefinition = newRowIdFieldDefinition();
      if (idFieldDefinition != null) {
        recordDefinition.addField(idFieldDefinition);
        final String idFieldName = idFieldDefinition.getName();
        recordDefinition.setIdFieldName(idFieldName);
      }
    }
  }

  @Override
  @PreDestroy
  public synchronized void close() {
    try {
      super.close();
      if (this.databaseFactory != null && this.dataSource != null) {
        JdbcDatabaseFactory.closeDataSource(this.dataSource);
      }
    } finally {
      this.allSchemaNames.clear();
      this.fieldDefinitionAdders.clear();
      this.transactionManager = null;
      this.databaseFactory = null;
      this.dataSource = null;
      this.excludeTablePatterns.clear();
      this.sequenceTypeSqlMap.clear();
      this.sqlPrefix = null;
      this.sqlSuffix = null;
    }
  }

  @Override
  public boolean deleteRecord(final Record record) {
    final RecordState state = RecordState.DELETED;
    write(record, state);
    return true;
  }

  @Override
  public int deleteRecords(final Iterable<? extends Record> records) {
    return writeAll(records, RecordState.DELETED);
  }

  @Override
  public int deleteRecords(final Query query) {
    final String typeName = query.getTypeName();
    RecordDefinition recordDefinition = query.getRecordDefinition();
    if (recordDefinition == null) {
      if (typeName != null) {
        recordDefinition = getRecordDefinition(typeName);
        query.setRecordDefinition(recordDefinition);
      }
    }
    final String sql = JdbcUtils.getDeleteSql(query);
    try (
      Transaction transaction = newTransaction(com.revolsys.transaction.Propagation.REQUIRED)) {
      // It's important to have this in an inner try. Otherwise the exceptions
      // won't get caught on closing the writer and the transaction won't get
      // rolled back.
      try (
        JdbcConnection connection = getJdbcConnection(isAutoCommit());
        final PreparedStatement statement = connection.prepareStatement(sql)) {

        JdbcUtils.setPreparedStatementParameters(statement, query);
        return statement.executeUpdate();
      } catch (final SQLException e) {
        transaction.setRollbackOnly();
        throw new RuntimeException("Unable to delete : " + sql, e);
      } catch (final RuntimeException e) {
        transaction.setRollbackOnly();
        throw e;
      } catch (final Error e) {
        transaction.setRollbackOnly();
        throw e;
      }
    }
  }

  public Set<String> getAllSchemaNames() {
    return this.allSchemaNames;
  }

  public int getBatchSize() {
    return this.batchSize;
  }

  public List<String> getColumnNames(final String typePath) {
    final RecordDefinition recordDefinition = getRecordDefinition(typePath);
    return recordDefinition.getFieldNames();
  }

  protected Set<String> getDatabaseSchemaNames() {
    final Set<String> schemaNames = new TreeSet<>();
    try {
      try (
        final Connection connection = getJdbcConnection();
        final PreparedStatement statement = connection.prepareStatement(this.schemaPermissionsSql);
        final ResultSet resultSet = statement.executeQuery();) {
        while (resultSet.next()) {
          final String schemaName = resultSet.getString("SCHEMA_NAME");
          addAllSchemaNames(schemaName);
          if (!isSchemaExcluded(schemaName)) {
            schemaNames.add(schemaName);
          }
        }
      }
    } catch (final Throwable e) {
      Logs.error(this, "Unable to get schema and table permissions", e);
    }
    return schemaNames;
  }

  protected DataSource getDataSource() {
    return this.dataSource;
  }

  public Set<String> getExcludeTablePaths() {
    return this.excludeTablePaths;
  }

  public JdbcFieldDefinition getField(final String schemaName, final String tableName,
    final String columnName) {
    final String typePath = PathUtil.toPath(schemaName, tableName);
    final RecordDefinition recordDefinition = getRecordDefinition(typePath);
    if (recordDefinition == null) {
      return null;
    } else {
      final FieldDefinition attribute = recordDefinition.getField(columnName);
      return (JdbcFieldDefinition)attribute;
    }
  }

  @Override
  public String getGeneratePrimaryKeySql(final JdbcRecordDefinition recordDefinition) {
    throw new UnsupportedOperationException(
      "Cannot create SQL to generate Primary Key for " + recordDefinition);
  }

  public String getIdFieldName(final String typePath) {
    final RecordDefinition recordDefinition = getRecordDefinition(typePath);
    if (recordDefinition == null) {
      return null;
    } else {
      return recordDefinition.getIdFieldName();
    }
  }

  @Override
  public JdbcConnection getJdbcConnection() {
    return new JdbcConnection(this.dataSource);
  }

  @Override
  public JdbcConnection getJdbcConnection(final boolean autoCommit) {
    return new JdbcConnection(this.dataSource, autoCommit);
  }

  protected Identifier getNextPrimaryKey(final String typePath) {
    return null;
  }

  @Override
  public int getRecordCount(Query query) {
    if (query == null) {
      return 0;
    } else {
      query = query.clone();
      query.setSql(null);
      query.setFieldNames("count(*)");
      query.clearOrderBy();
      final String sql = JdbcUtils.getSelectSql(query);
      try (
        JdbcConnection connection = getJdbcConnection()) {
        try (
          final PreparedStatement statement = connection.prepareStatement(sql)) {
          JdbcUtils.setPreparedStatementParameters(statement, query);
          try (
            final ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
              final int rowCount = resultSet.getInt(1);
              return rowCount;
            } else {
              return 0;
            }
          }
        } catch (final SQLException e) {
          throw connection.getException("getRecordCount", sql, e);
        } catch (final IllegalArgumentException e) {
          Logs.error(this, "Cannot get row count: " + query, e);
          return 0;
        }
      }
    }
  }

  @Override
  public JdbcRecordDefinition getRecordDefinition(String typePath,
    final ResultSetMetaData resultSetMetaData, final String dbTableName) {
    if (Property.isEmpty(typePath)) {
      typePath = "Record";
    }

    try {
      final PathName pathName = PathName.newPathName(typePath);
      final PathName schemaName = pathName.getParent();
      final JdbcRecordStoreSchema schema = getSchema(schemaName);
      final JdbcRecordDefinition resultRecordDefinition = newRecordDefinition(schema, pathName,
        dbTableName);

      final RecordDefinition recordDefinition = getRecordDefinition(typePath);
      for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
        final String fieldName = resultSetMetaData.getColumnName(i).toUpperCase();
        if (recordDefinition != null && recordDefinition.isIdField(fieldName)) {
          resultRecordDefinition.setIdFieldIndex(i - 1);
        }
        addField(resultSetMetaData, resultRecordDefinition, fieldName, i, null);
      }

      addRecordDefinitionProperties(resultRecordDefinition);

      return resultRecordDefinition;
    } catch (final SQLException e) {
      throw new IllegalArgumentException("Unable to load metadata for " + typePath);
    }
  }

  public String getSchemaTablePermissionsSql() {
    return this.schemaTablePermissionsSql;
  }

  protected String getSequenceName(final JdbcRecordDefinition recordDefinition) {
    return null;
  }

  public String getSqlPrefix() {
    return this.sqlPrefix;
  }

  public String getSqlSuffix() {
    return this.sqlSuffix;
  }

  @Override
  public PlatformTransactionManager getTransactionManager() {
    return this.transactionManager;
  }

  @Override
  public void initialize(final RecordStore recordStore,
    final Map<String, Object> connectionProperties) {
  }

  @Override
  protected void initializeDo() {
    super.initializeDo();
    if (this.dataSource != null) {
      this.transactionManager = new DataSourceTransactionManager(this.dataSource);
    }
  }

  @Override
  public void insertRecord(final Record record) {
    write(record, RecordState.NEW);
  }

  @Override
  public void insertRecords(final Iterable<? extends Record> records) {
    writeAll(records, RecordState.NEW);
  }

  public boolean isAutoCommit() {
    boolean autoCommit = false;
    if (Booleans.getBoolean(getProperties().get("autoCommit"))) {
      autoCommit = true;
    }
    return autoCommit;
  }

  @Override
  public boolean isEditable(final PathName typePath) {
    final RecordDefinition recordDefinition = getRecordDefinition(typePath);
    return recordDefinition.getIdFieldIndex() != -1;
  }

  @Override
  public boolean isEnabled(final RecordStore recordStore) {
    return true;
  }

  protected boolean isExcluded(final String dbSchemaName, final String tableName) {
    final String path = ("/" + dbSchemaName + "/" + tableName).toUpperCase().replaceAll("/+", "/");
    if (this.excludeTablePaths.contains(path)) {
      return true;
    } else {
      for (final String pattern : this.excludeTablePatterns) {
        if (path.matches(pattern) || tableName.matches(pattern)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isFlushBetweenTypes() {
    return this.flushBetweenTypes;
  }

  @Override
  public boolean isIdFieldRowid(final RecordDefinition recordDefinition) {
    return false;
  }

  public abstract boolean isSchemaExcluded(String schemaName);

  protected synchronized Map<String, List<String>> loadIdFieldNames(final String dbSchemaName) {
    final String schemaName = "/" + dbSchemaName.toUpperCase();
    final Map<String, List<String>> idFieldNames = new HashMap<>();
    try {
      try (
        final Connection connection = getJdbcConnection();
        final PreparedStatement statement = connection.prepareStatement(this.primaryKeySql);) {
        statement.setString(1, dbSchemaName);
        try (
          final ResultSet rs = statement.executeQuery()) {
          while (rs.next()) {
            final String tableName = rs.getString("TABLE_NAME").toUpperCase();
            final String idFieldName = rs.getString("COLUMN_NAME");
            Maps.addToList(idFieldNames, schemaName + "/" + tableName, idFieldName);
          }
        }
      }
    } catch (final Throwable e) {
      throw new IllegalArgumentException("Unable to primary keys for schema " + dbSchemaName, e);
    }
    return idFieldNames;
  }

  protected synchronized List<String> loadIdFieldNames(final String dbSchemaName,
    final String dbTableName) {
    final List<String> idFieldNames = new ArrayList<>();
    try {
      try (
        final Connection connection = getJdbcConnection();
        final PreparedStatement statement = connection
          .prepareStatement(this.primaryKeySql + this.primaryKeyTableCondition);) {
        statement.setString(1, dbSchemaName);
        statement.setString(2, dbTableName);
        try (
          final ResultSet rs = statement.executeQuery()) {
          while (rs.next()) {
            final String idFieldName = rs.getString("COLUMN_NAME");
            idFieldNames.add(idFieldName);
          }
        }
      }
    } catch (final Throwable e) {
      throw new IllegalArgumentException(
        "Unable to primary keys for table " + dbSchemaName + "." + dbTableName, e);
    }
    return idFieldNames;
  }

  protected Map<PathName, JdbcRecordDefinition> loadRecordDefinitionsPermissions(
    final JdbcRecordStoreSchema schema) {
    final PathName schemaPath = schema.getPathName();
    final String dbSchemaName = schema.getDbName();
    try (
      final Connection connection = getJdbcConnection();
      final PreparedStatement statement = connection
        .prepareStatement(this.schemaTablePermissionsSql)) {
      statement.setString(1, dbSchemaName);
      try (
        final ResultSet resultSet = statement.executeQuery()) {
        final Map<PathName, JdbcRecordDefinition> recordDefinitionMap = new TreeMap<>();
        while (resultSet.next()) {
          final String dbTableName = resultSet.getString("TABLE_NAME");
          if (!isExcluded(dbSchemaName, dbTableName)) {
            final String tableName = dbTableName.toUpperCase();
            final PathName pathName = schemaPath.newChild(tableName);

            JdbcRecordDefinition recordDefinition = recordDefinitionMap.get(pathName);
            List<String> tablePermissions;
            if (recordDefinition == null) {
              recordDefinition = newRecordDefinition(schema, pathName, dbTableName);
              recordDefinitionMap.put(pathName, recordDefinition);

              tablePermissions = new ArrayList<>();
              recordDefinition.setProperty("permissions", tablePermissions);

              final String description = resultSet.getString("REMARKS");
              recordDefinition.setDescription(description);

              final String tableType = resultSet.getString("TABLE_TYPE");
              recordDefinition.setProperty("tableType", tableType);
            } else {
              tablePermissions = recordDefinition.getProperty("permissions");
            }
            final String privilege = resultSet.getString("PRIVILEGE");
            if ("ALL".equals(privilege)) {
              tablePermissions.add("SELECT");
              tablePermissions.add("INSERT");
              tablePermissions.add("UPDATE");
              tablePermissions.add("DELETE");
            } else {
              tablePermissions.add(privilege);
            }

          }
        }
        return recordDefinitionMap;
      }
    } catch (final Throwable e) {
      throw Exceptions.wrap("Unable to get schema and table permissions: " + dbSchemaName, e);
    }
  }

  protected Identifier newPrimaryIdentifier(final JdbcRecordDefinition recordDefinition) {
    final GlobalIdProperty globalIdProperty = GlobalIdProperty.getProperty(recordDefinition);
    if (globalIdProperty == null) {
      if (isIdFieldRowid(recordDefinition)) {
        return null;
      } else if (recordDefinition.hasIdField()) {
        final String sequenceName = getSequenceName(recordDefinition);
        return getNextPrimaryKey(sequenceName);
      } else {
        return null;
      }
    } else {
      return Identifier.newIdentifier(UUID.randomUUID().toString());
    }
  }

  @Override
  public Identifier newPrimaryIdentifier(final PathName typePath) {
    final JdbcRecordDefinition recordDefinition = getRecordDefinition(typePath);
    return newPrimaryIdentifier(recordDefinition);
  }

  protected JdbcRecordDefinition newRecordDefinition(final JdbcRecordStoreSchema schema,
    final PathName pathName, final String dbTableName) {
    return new JdbcRecordDefinition(schema, pathName, dbTableName);
  }

  protected RecordStoreQueryReader newRecordReader(final Query query) {
    final RecordStoreQueryReader reader = newRecordReader();
    reader.addQuery(query);
    return reader;
  }

  @Override
  public RecordWriter newRecordWriter() {
    return newRecordWriter(false);
  }

  protected RecordWriter newRecordWriter(final boolean throwExceptions) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      Object writerKey;
      if (throwExceptions) {
        writerKey = this.exceptionWriterKey;
      } else {
        writerKey = this.writerKey;
      }
      JdbcWriterResourceHolder resourceHolder = (JdbcWriterResourceHolder)TransactionSynchronizationManager
        .getResource(writerKey);
      if (resourceHolder == null) {
        resourceHolder = new JdbcWriterResourceHolder();
        TransactionSynchronizationManager.bindResource(writerKey, resourceHolder);
      }
      final JdbcWriterWrapper writerWrapper = resourceHolder.getWriterWrapper(this, throwExceptions,
        this.batchSize);

      if (!resourceHolder.isSynchronizedWithTransaction()) {
        final JdbcWriterSynchronization synchronization = new JdbcWriterSynchronization(this,
          resourceHolder, writerKey);
        TransactionSynchronizationManager.registerSynchronization(synchronization);
        resourceHolder.setSynchronizedWithTransaction(true);
      }

      return writerWrapper;

    } else {
      return newRecordWriter(this.batchSize);
    }
  }

  protected JdbcWriterImpl newRecordWriter(final int batchSize) {
    final JdbcWriterImpl writer = new JdbcWriterImpl(this);
    writer.setSqlPrefix(this.sqlPrefix);
    writer.setSqlSuffix(this.sqlSuffix);
    writer.setBatchSize(batchSize);
    writer.setLabel(getLabel());
    writer.setFlushBetweenTypes(this.flushBetweenTypes);
    writer.setQuoteColumnNames(false);
    return writer;
  }

  @Override
  protected RecordStoreSchema newRootSchema() {
    return new JdbcRecordStoreSchema(this);
  }

  /**
   * Create the field definition for the row identifier column for tables that don't have a primary key.
   * @return
   */
  protected JdbcFieldDefinition newRowIdFieldDefinition() {
    return null;
  }

  @Override
  public ResultPager<Record> page(final Query query) {
    return new JdbcQueryResultPager(this, getProperties(), query);
  }

  protected void postProcess(final JdbcRecordStoreSchema schema) {
  }

  @Override
  public final void postProcess(final RecordStoreSchema schema) {
    postProcess((JdbcRecordStoreSchema)schema);
  }

  protected void preProcess(final JdbcRecordStoreSchema schema) {
    for (final JdbcFieldAdder fieldDefinitionAdder : this.fieldDefinitionAdders.values()) {
      fieldDefinitionAdder.initialize(schema);
    }
  }

  @Override
  public final void preProcess(final RecordStoreSchema schema) {
    preProcess((JdbcRecordStoreSchema)schema);
  }

  @Override
  protected Map<PathName, ? extends RecordStoreSchemaElement> refreshSchemaElements(
    final RecordStoreSchema schema) {
    final JdbcRecordStoreSchema jdbcSchema = (JdbcRecordStoreSchema)schema;
    final JdbcRecordStoreSchema rootSchema = getRootSchema();
    final PathName schemaPath = jdbcSchema.getPathName();
    if (jdbcSchema == rootSchema) {
      final Map<PathName, RecordStoreSchemaElement> schemas = new TreeMap<>();
      final Set<String> databaseSchemaNames = getDatabaseSchemaNames();
      for (final String dbSchemaName : databaseSchemaNames) {
        final PathName childSchemaPath = schemaPath.newChild(dbSchemaName.toUpperCase());
        JdbcRecordStoreSchema childSchema = jdbcSchema.getSchema(childSchemaPath);
        if (childSchema == null) {
          childSchema = new JdbcRecordStoreSchema(rootSchema, childSchemaPath, dbSchemaName);
        } else {
          if (childSchema.isInitialized()) {
            childSchema.refresh();
          }
        }
        schemas.put(childSchemaPath, childSchema);
      }
      return schemas;
    } else {
      final String dbSchemaName = jdbcSchema.getDbName();
      final Map<PathName, JdbcRecordDefinition> recordDefinitionMap = loadRecordDefinitionsPermissions(
        jdbcSchema);
      final Map<PathName, RecordStoreSchemaElement> elementsByPath = new TreeMap<>();
      try {
        try (
          final Connection connection = getJdbcConnection()) {
          final DatabaseMetaData databaseMetaData = connection.getMetaData();
          final List<PathName> removedPaths = jdbcSchema.getTypePaths();
          final Map<String, List<String>> idFieldNameMap = loadIdFieldNames(dbSchemaName);
          for (final JdbcRecordDefinition recordDefinition : recordDefinitionMap.values()) {
            final PathName typePath = recordDefinition.getPathName();
            removedPaths.remove(typePath);
            final List<String> idFieldNames = idFieldNameMap.get(typePath);
            if (Property.isEmpty(idFieldNames)) {
              addRowIdFieldDefinition(recordDefinition);
            }
            elementsByPath.put(typePath, recordDefinition);
          }
          try (
            final ResultSet columnsRs = databaseMetaData.getColumns(null, dbSchemaName, "%", "%")) {
            while (columnsRs.next()) {
              final String tableName = columnsRs.getString("TABLE_NAME").toUpperCase();
              final PathName typePath = schemaPath.newChild(tableName);
              final JdbcRecordDefinition recordDefinition = recordDefinitionMap.get(typePath);
              if (recordDefinition != null) {
                final String dbColumnName = columnsRs.getString("COLUMN_NAME");
                final String name = dbColumnName.toUpperCase();
                final int sqlType = columnsRs.getInt("DATA_TYPE");
                final String dataType = columnsRs.getString("TYPE_NAME");
                final int length = columnsRs.getInt("COLUMN_SIZE");
                int scale = columnsRs.getInt("DECIMAL_DIGITS");
                if (columnsRs.wasNull()) {
                  scale = -1;
                }
                final boolean required = !columnsRs.getString("IS_NULLABLE").equals("YES");
                final String description = columnsRs.getString("REMARKS");
                addField(recordDefinition, dbColumnName, name, dataType, sqlType, length, scale,
                  required, description);
              }
            }

            for (final RecordDefinitionImpl recordDefinition : recordDefinitionMap.values()) {
              final String typePath = recordDefinition.getPath();
              final List<String> idFieldNames = idFieldNameMap.get(typePath);
              if (!Property.isEmpty(idFieldNames)) {
                recordDefinition.setIdFieldNames(idFieldNames);
              }
            }

          }
        }
      } catch (final Throwable e) {
        throw new IllegalArgumentException("Unable to load metadata for schema " + dbSchemaName, e);
      }
      return elementsByPath;
    }
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public void setCodeTables(final List<AbstractCodeTable> codeTables) {
    for (final AbstractCodeTable codeTable : codeTables) {
      addCodeTable(codeTable);
    }
  }

  public void setDataSource(final DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void setExcludeTablePaths(final Collection<String> excludeTablePaths) {
    this.excludeTablePaths = new HashSet<>(excludeTablePaths);
  }

  public void setExcludeTablePaths(final String... excludeTablePaths) {
    setExcludeTablePaths(Arrays.asList(excludeTablePaths));
  }

  public void setExcludeTablePatterns(final String... excludeTablePatterns) {
    this.excludeTablePatterns = new ArrayList<>(Arrays.asList(excludeTablePatterns));
  }

  public void setFlushBetweenTypes(final boolean flushBetweenTypes) {
    this.flushBetweenTypes = flushBetweenTypes;
  }

  public void setPrimaryKeySql(final String primaryKeySql) {
    this.primaryKeySql = primaryKeySql;
  }

  public void setPrimaryKeyTableCondition(final String primaryKeyTableCondition) {
    this.primaryKeyTableCondition = primaryKeyTableCondition;
  }

  protected void setSchemaPermissionsSql(final String scehmaPermissionsSql) {
    this.schemaPermissionsSql = scehmaPermissionsSql;
  }

  public void setSchemaTablePermissionsSql(final String tablePermissionsSql) {
    this.schemaTablePermissionsSql = tablePermissionsSql;
  }

  public void setSqlPrefix(final String sqlPrefix) {
    this.sqlPrefix = sqlPrefix;
  }

  public void setSqlSuffix(final String sqlSuffix) {
    this.sqlSuffix = sqlSuffix;
  }

  @Override
  public void updateRecord(final Record record) {
    write(record, null);
  }

  @Override
  public void updateRecords(final Iterable<? extends Record> records) {
    writeAll(records, null);
  }

  protected void write(final Record record, final RecordState state) {
    try (
      Transaction transaction = newTransaction(com.revolsys.transaction.Propagation.REQUIRED)) {
      // It's important to have this in an inner try. Otherwise the exceptions
      // won't get caught on closing the writer and the transaction won't get
      // rolled back.
      try (
        RecordWriter writer = newRecordWriter(true)) {
        write(writer, record, state);
      } catch (final RuntimeException e) {
        transaction.setRollbackOnly();
        throw e;
      } catch (final Error e) {
        transaction.setRollbackOnly();
        throw e;
      }
    }
  }

  protected Record write(final RecordWriter writer, Record record, final RecordState state) {
    if (state == RecordState.NEW) {
      if (record.getState() != state) {
        record = newRecord(record);
      }
    } else if (state != null) {
      record.setState(state);
    }
    writer.write(record);
    return record;
  }

  protected int writeAll(final Iterable<? extends Record> records, final RecordState state) {
    int count = 0;
    try (
      Transaction transaction = newTransaction(Propagation.REQUIRED)) {
      // It's important to have this in an inner try. Otherwise the exceptions
      // won't get caught on closing the writer and the transaction won't get
      // rolled back.
      try (
        final RecordWriter writer = newRecordWriter(true)) {
        for (final Record record : records) {
          write(writer, record, state);
          count++;
        }
      } catch (final RuntimeException e) {
        transaction.setRollbackOnly();
        throw e;
      } catch (final Error e) {
        transaction.setRollbackOnly();
        throw e;
      }
    }
    return count;
  }
}
