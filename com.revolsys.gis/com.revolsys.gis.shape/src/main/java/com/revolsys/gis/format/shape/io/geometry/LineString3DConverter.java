package com.revolsys.gis.format.shape.io.geometry;

import java.io.IOException;

import com.revolsys.gis.format.shape.io.ShapeConstants;
import com.revolsys.gis.io.EndianInput;
import com.revolsys.gis.io.EndianOutput;
import com.revolsys.util.MathUtil;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.PrecisionModel;

public class LineString3DConverter implements ShapefileGeometryConverter {
  private GeometryFactory geometryFactory;

  public LineString3DConverter() {
    this(null);
  }

  public LineString3DConverter(
    final GeometryFactory geometryFactory) {
    if (geometryFactory != null) {
      this.geometryFactory = geometryFactory;
    } else {
      this.geometryFactory = new GeometryFactory(new PrecisionModel());
    }
  }

  public int getShapeType() {
    return ShapeConstants.POLYLINE_Z_SHAPE;
  }

  /*
   * (non-Javadoc)
   * @see
   * com.revolsys.gis.format.shape.io.geometry.ShapefileGeometryConverter#read
   * (int, com.revolsys.gis.format.core.io.LittleEndianRandomAccessFile)
   */
  public Geometry read(
    final EndianInput in,
    final long recordLength)
    throws IOException {
    // skip bounding box;
    in.skipBytes(4 * MathUtil.BYTES_IN_DOUBLE);
    final int numParts = in.readLEInt();
    final int numPoints = in.readLEInt();
    final int[] partIndex = new int[numParts];
    final Coordinate[] coordinates = new Coordinate[numPoints];
    for (int i = 0; i < partIndex.length; i++) {
      partIndex[i] = in.readLEInt();
    }
    for (int i = 0; i < coordinates.length; i++) {
      final double x = in.readLEDouble();
      final double y = in.readLEDouble();
      coordinates[i] = new Coordinate(x, y);
    }
    // skip min/max Z
    in.skipBytes(2 * MathUtil.BYTES_IN_DOUBLE);
    for (int i = 0; i < coordinates.length; i++) {
      final double z = in.readLEDouble();
      final Coordinate coordinate = coordinates[i];
      coordinate.z = z;
    }
    // if (startIndex + (recordLength * 2) < endIndex) {
    // // skip min/max M + m values
    // in.skipBytes((2 + numPoints) * MathUtil.BYTES_IN_DOUBLE);
    // }
    if (numParts == 1) {
      return geometryFactory.createLineString(coordinates);
    } else {
      final LineString[] lines = new LineString[numParts];
      for (int i = 0; i < partIndex.length - 1; i++) {
        final int partStart = partIndex[i];
        final int partEnd = partIndex[i + 1];
        final int numCoords = partEnd - partStart;

        final Coordinate[] partCoords = new Coordinate[numCoords];
        System.arraycopy(coordinates, partStart, partCoords, 0, numCoords);
        lines[i] = geometryFactory.createLineString(partCoords);
      }
      return geometryFactory.createMultiLineString(lines);
    }
  }

  /*
   * (non-Javadoc)
   * @see
   * com.revolsys.gis.format.shape.io.geometry.ShapefileGeometryConverter#write
   * (com.revolsys.gis.format.core.io.LittleEndianRandomAccessFile,
   * com.vividsolutions.jts.geom.Geometry)
   */
  public void write(
    final EndianOutput out,
    final Geometry geometry)
    throws IOException {
    if (geometry instanceof LineString) {
      final LineString line = (LineString)geometry;
      writePolyLineZHeader(out, geometry);
      out.writeLEInt(0);
      ShapefileGeometryUtil.write2DCoordinates(out, line);
      ShapefileGeometryUtil.writeCoordinateZValues(out, line);
    } else if (geometry instanceof MultiLineString) {
      final MultiLineString multiLine = (MultiLineString)geometry;
      writePolyLineZHeader(out, multiLine);
      ShapefileGeometryUtil.writePartIndexes(out, multiLine);
      ShapefileGeometryUtil.write2DCoordinates(out, multiLine);
      ShapefileGeometryUtil.writeCoordinateZValues(out, multiLine);
    } else {
      throw new IllegalArgumentException("Expecting " + LineString.class
        + " geometry got " + geometry.getClass());
    }
  }

  private void writePolyLineZHeader(
    final EndianOutput out,
    final Geometry geometry)
    throws IOException {
    final int numCoordinates = geometry.getNumPoints();
    final int numGeometries = geometry.getNumGeometries();
    final int recordLength = ((3 + numGeometries) * MathUtil.BYTES_IN_INT + (6 + 3 * numCoordinates)
      * MathUtil.BYTES_IN_DOUBLE) / 2;
    out.writeInt(recordLength);
    out.writeLEInt(ShapeConstants.POLYLINE_Z_SHAPE);
    final Envelope envelope = geometry.getEnvelopeInternal();
    ShapefileGeometryUtil.writeEnvelope(out, envelope);
    out.writeLEInt(numGeometries);
    out.writeLEInt(numCoordinates);
  }
}
