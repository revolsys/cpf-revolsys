package com.revolsys.gis.oracle.esri;

import java.util.Map;

import com.revolsys.gis.cs.GeometryFactory;
import com.revolsys.gis.data.model.AttributeProperties;
import com.revolsys.gis.data.model.types.DataType;
import com.revolsys.jdbc.attribute.JdbcAttribute;

public class ArcSdeSdeGeometryAttribute extends JdbcAttribute {

  private final ArcSdeSpatialReference spatialReference;

  private final GeometryFactory geometryFactory;

  private final int numAxis;

  public ArcSdeSdeGeometryAttribute(final String name, final DataType type,
    final boolean required, final Map<String, Object> properties,
    final ArcSdeSpatialReference spatialReference, final int numAxis) {
    super(name, type, -1, 0, 0, required, properties);
    this.spatialReference = spatialReference;
    final GeometryFactory factory = spatialReference.getGeometryFactory();
    this.geometryFactory = GeometryFactory.getFactory(factory.getSRID(),
      numAxis, factory.getScaleXY(), factory.getScaleZ());
    this.numAxis = numAxis;
    setProperty(AttributeProperties.GEOMETRY_FACTORY, geometryFactory);
  }

  public GeometryFactory getGeometryFactory() {
    return geometryFactory;
  }

  public int getNumAxis() {
    return numAxis;
  }

  public ArcSdeSpatialReference getSpatialReference() {
    return spatialReference;
  }
}