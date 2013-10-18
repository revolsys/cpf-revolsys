package com.revolsys.gis.oracle.esri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.revolsys.gis.cs.GeometryFactory;
import com.revolsys.gis.data.io.DataObjectStoreSchema;
import com.revolsys.gis.data.model.Attribute;
import com.revolsys.gis.data.model.AttributeProperties;
import com.revolsys.gis.data.model.DataObjectMetaDataImpl;
import com.revolsys.gis.data.model.types.DataType;
import com.revolsys.jdbc.attribute.JdbcAttributeAdder;
import com.revolsys.jdbc.io.AbstractJdbcDataObjectStore;
import com.revolsys.jdbc.io.JdbcConstants;
import com.revolsys.jdbc.io.SqlFunction;

public class ArcSdeStGeometryAttributeAdder extends JdbcAttributeAdder {
  private static final Logger LOG = LoggerFactory.getLogger(ArcSdeStGeometryAttributeAdder.class);

  private final AbstractJdbcDataObjectStore dataStore;

  public ArcSdeStGeometryAttributeAdder(
    final AbstractJdbcDataObjectStore dataStore) {
    this.dataStore = dataStore;

  }

  @Override
  public Attribute addAttribute(final DataObjectMetaDataImpl metaData,
    final String name, final String dataTypeName, final int sqlType,
    final int length, final int scale, final boolean required) {
    final DataObjectStoreSchema schema = metaData.getSchema();
    final String typePath = metaData.getPath();
    final String owner = dataStore.getDatabaseSchemaName(schema);
    final String tableName = dataStore.getDatabaseTableName(typePath);
    final String columnName = name.toUpperCase();
    final int esriSrid = ArcSdeConstants.getIntegerColumnProperty(schema,
      typePath, columnName, ArcSdeConstants.ESRI_SRID_PROPERTY);
    if (esriSrid == -1) {
      LOG.error("Column not registered in SDE.ST_GEOMETRY table " + owner + "."
        + tableName + "." + name);
    }
    final int numAxis = ArcSdeConstants.getIntegerColumnProperty(schema,
      typePath, columnName, ArcSdeConstants.NUM_AXIS);
    if (numAxis == -1) {
      LOG.error("Column not found in SDE.GEOMETRY_COLUMNS table " + owner + "."
        + tableName + "." + name);
    }
    final DataType dataType = ArcSdeConstants.getColumnProperty(schema,
      typePath, columnName, ArcSdeConstants.DATA_TYPE);
    if (dataType == null) {
      LOG.error("Column not found in SDE.GEOMETRY_COLUMNS table " + owner + "."
        + tableName + "." + name);
    }

    final ArcSdeSpatialReference spatialReference = ArcSdeConstants.getColumnProperty(
      schema, typePath, columnName,
      ArcSdeConstants.SPATIAL_REFERENCE);

    final Attribute attribute = new ArcSdeStGeometryAttribute(name,
      dataType, required, null, spatialReference, numAxis);

    metaData.addAttribute(attribute);
    attribute.setProperty(JdbcConstants.FUNCTION_INTERSECTS, new SqlFunction(
      "SDE.ST_ENVINTERSECTS(", ") = 1"));
    attribute.setProperty(JdbcConstants.FUNCTION_BUFFER, new SqlFunction(
      "SDE.ST_BUFFER(", ")"));
    if (spatialReference != null) {
      final int srid = spatialReference.getSrid();
      final Double scaleXy = spatialReference.getXyScale();
      final Double scaleZ = spatialReference.getZScale();
      final GeometryFactory geometryFactory = GeometryFactory.getFactory(srid,
        scaleXy, scaleZ);
      attribute.setProperty(AttributeProperties.GEOMETRY_FACTORY,
        geometryFactory);
    }
    return attribute;
  }

}
