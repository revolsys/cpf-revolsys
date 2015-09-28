package com.revolsys.record.io.format.shp;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel.MapMode;
import java.util.NoSuchElementException;

import com.revolsys.collection.iterator.AbstractIterator;
import com.revolsys.datatype.DataType;
import com.revolsys.datatype.DataTypes;
import com.revolsys.geometry.cs.esri.EsriCoordinateSystems;
import com.revolsys.geometry.model.Geometry;
import com.revolsys.geometry.model.GeometryFactory;
import com.revolsys.io.EndianInput;
import com.revolsys.io.FileUtil;
import com.revolsys.io.IoConstants;
import com.revolsys.io.PathName;
import com.revolsys.io.endian.EndianInputStream;
import com.revolsys.io.endian.EndianMappedByteBuffer;
import com.revolsys.io.endian.LittleEndianRandomAccessFile;
import com.revolsys.record.Record;
import com.revolsys.record.RecordFactory;
import com.revolsys.record.Records;
import com.revolsys.record.io.RecordReader;
import com.revolsys.record.io.format.xbase.XbaseIterator;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.record.schema.RecordDefinitionImpl;
import com.revolsys.spring.resource.Resource;
import com.revolsys.util.Property;

public class ShapefileIterator extends AbstractIterator<Record>implements RecordReader {

  private boolean closeFile = true;

  private GeometryFactory geometryFactory;

  private EndianInput in;

  private EndianMappedByteBuffer indexIn;

  private boolean mappedFile;

  private final String name;

  private int position;

  private RecordDefinition recordDefinition;

  private RecordFactory recordFactory;

  private Resource resource;

  private RecordDefinition returnRecordDefinition;

  private int shapeType;

  private PathName typeName;

  private XbaseIterator xbaseIterator;

  public ShapefileIterator(final Resource resource, final RecordFactory factory)
    throws IOException {
    this.recordFactory = factory;
    final String baseName = resource.getBaseName();
    this.name = baseName;
    this.typeName = PathName.newPathName("/" + this.name);
    this.resource = resource;
  }

  @Override
  protected void doClose() {
    if (this.closeFile) {
      forceClose();
    }
  }

  @Override
  protected synchronized void doInit() {
    if (this.in == null) {
      try {
        final Boolean memoryMapped = getProperty("memoryMapped");
        try {
          final File file = this.resource.getFile();
          final File indexFile = new File(file.getParentFile(), this.name + ".shx");
          if (Boolean.TRUE == memoryMapped) {
            this.in = new EndianMappedByteBuffer(file, MapMode.READ_ONLY);
            this.indexIn = new EndianMappedByteBuffer(indexFile, MapMode.READ_ONLY);
            this.mappedFile = true;
          } else {
            this.in = new LittleEndianRandomAccessFile(file, "r");
          }
        } catch (final IllegalArgumentException e) {
          this.in = new EndianInputStream(this.resource.getInputStream());
        } catch (final FileNotFoundException e) {
          this.in = new EndianInputStream(this.resource.getInputStream());
        }

        final Resource xbaseResource = this.resource.newResourceChangeExtension("dbf");
        if (xbaseResource.exists()) {
          this.xbaseIterator = new XbaseIterator(xbaseResource, this.recordFactory,
            () -> updateRecordDefinition());
          this.xbaseIterator.setTypeName(this.typeName);
          this.xbaseIterator.setProperty("memoryMapped", memoryMapped);
          this.xbaseIterator.setCloseFile(this.closeFile);
        }
        loadHeader();
        int axisCount;
        switch (this.shapeType) {
          case ShapefileConstants.POINT_SHAPE: // 1
          case ShapefileConstants.POLYLINE_SHAPE: // 3
          case ShapefileConstants.POLYGON_SHAPE: // 5
          case ShapefileConstants.MULTI_POINT_SHAPE: // 8
            axisCount = 2;
          break;
          case ShapefileConstants.POINT_Z_SHAPE: // 9
          case ShapefileConstants.POLYLINE_Z_SHAPE: // 10
          case ShapefileConstants.POLYGON_Z_SHAPE: // 19
          case ShapefileConstants.MULTI_POINT_Z_SHAPE: // 20
            axisCount = 3;
          break;
          case ShapefileConstants.POINT_ZM_SHAPE: // 11
          case ShapefileConstants.POLYLINE_ZM_SHAPE: // 13
          case ShapefileConstants.POLYGON_ZM_SHAPE: // 15
          case ShapefileConstants.MULTI_POINT_ZM_SHAPE: // 18
          case ShapefileConstants.POINT_M_SHAPE: // 21
          case ShapefileConstants.POLYLINE_M_SHAPE: // 23
          case ShapefileConstants.POLYGON_M_SHAPE: // 25
          case ShapefileConstants.MULTI_POINT_M_SHAPE: // 28
            axisCount = 4;
          break;
          default:
            throw new RuntimeException("Unknown shape type:" + this.shapeType);
        }
        this.geometryFactory = getProperty(IoConstants.GEOMETRY_FACTORY);
        if (this.geometryFactory == null) {
          this.geometryFactory = EsriCoordinateSystems.getGeometryFactory(this.resource);
          if (this.geometryFactory != null) {
            this.geometryFactory = this.geometryFactory.convertAxisCount(axisCount);
          }
        }
        if (this.geometryFactory == null) {
          this.geometryFactory = GeometryFactory.floating(0, axisCount);
        }
        setProperty(IoConstants.GEOMETRY_FACTORY, this.geometryFactory);

        if (this.xbaseIterator != null) {
          this.xbaseIterator.hasNext();
        }
        if (this.recordDefinition == null) {
          this.recordDefinition = Records.newGeometryRecordDefinition();
        }
        this.recordDefinition.setGeometryFactory(this.geometryFactory);
      } catch (final IOException e) {
        throw new RuntimeException("Error initializing mappedFile " + this.resource, e);
      }
    }
  }

  public void forceClose() {
    FileUtil.closeSilent(this.in, this.indexIn);
    if (this.xbaseIterator != null) {
      this.xbaseIterator.forceClose();
    }
    this.recordFactory = null;
    this.geometryFactory = null;
    this.in = null;
    this.indexIn = null;
    this.recordDefinition = null;
    this.resource = null;
    this.xbaseIterator = null;
  }

  @Override
  protected Record getNext() {
    Record record;
    try {
      if (this.xbaseIterator != null) {
        if (this.xbaseIterator.hasNext()) {
          record = this.xbaseIterator.next();
          for (int i = 0; i < this.xbaseIterator.getDeletedCount(); i++) {
            this.position++;
            readGeometry();
          }
        } else {
          throw new NoSuchElementException();
        }
      } else {
        record = this.recordFactory.newRecord(this.recordDefinition);
      }

      final Geometry geometry = readGeometry();
      record.setGeometryValue(geometry);
    } catch (final EOFException e) {
      throw new NoSuchElementException();
    } catch (final IOException e) {
      throw new RuntimeException("Error reading geometry " + this.resource, e);
    }
    if (this.returnRecordDefinition == null) {
      return record;
    } else {
      final Record copy = this.recordFactory.newRecord(this.returnRecordDefinition);
      copy.setValues(record);
      return copy;
    }
  }

  public int getPosition() {
    return this.position;
  }

  @Override
  public RecordDefinition getRecordDefinition() {
    open();
    return this.recordDefinition;
  }

  public RecordFactory getRecordFactory() {
    return this.recordFactory;
  }

  public PathName getTypeName() {
    return this.typeName;
  }

  public boolean isCloseFile() {
    return this.closeFile;
  }

  /**
   * Load the header record from the shape mappedFile.
   *
   * @throws IOException If an I/O error occurs.
   */
  @SuppressWarnings("unused")
  private void loadHeader() throws IOException {
    this.in.readInt();
    this.in.skipBytes(20);
    final int fileLength = this.in.readInt();
    final int version = this.in.readLEInt();
    this.shapeType = this.in.readLEInt();
    final double minX = this.in.readLEDouble();
    final double minY = this.in.readLEDouble();
    final double maxX = this.in.readLEDouble();
    final double maxY = this.in.readLEDouble();
    final double minZ = this.in.readLEDouble();
    final double maxZ = this.in.readLEDouble();
    final double minM = this.in.readLEDouble();
    final double maxM = this.in.readLEDouble();
  }

  @SuppressWarnings("unused")
  private Geometry readGeometry() throws IOException {
    final int recordNumber = this.in.readInt();
    final int recordLength = this.in.readInt();
    final int shapeType = this.in.readLEInt();
    final ShapefileGeometryUtil util = ShapefileGeometryUtil.SHP_INSTANCE;
    switch (shapeType) {
      case ShapefileConstants.NULL_SHAPE:
        switch (this.shapeType) {
          case ShapefileConstants.POINT_SHAPE:
          case ShapefileConstants.POINT_M_SHAPE:
          case ShapefileConstants.POINT_Z_SHAPE:
          case ShapefileConstants.POINT_ZM_SHAPE:
            return this.geometryFactory.point();

          case ShapefileConstants.MULTI_POINT_SHAPE:
          case ShapefileConstants.MULTI_POINT_M_SHAPE:
          case ShapefileConstants.MULTI_POINT_Z_SHAPE:
          case ShapefileConstants.MULTI_POINT_ZM_SHAPE:
            return this.geometryFactory.multiPoint();

          case ShapefileConstants.POLYLINE_SHAPE:
          case ShapefileConstants.POLYLINE_M_SHAPE:
          case ShapefileConstants.POLYLINE_Z_SHAPE:
          case ShapefileConstants.POLYLINE_ZM_SHAPE:
            return this.geometryFactory.multiLineString();

          case ShapefileConstants.POLYGON_SHAPE:
          case ShapefileConstants.POLYGON_M_SHAPE:
          case ShapefileConstants.POLYGON_Z_SHAPE:
          case ShapefileConstants.POLYGON_ZM_SHAPE:
            return this.geometryFactory.multiPolygon();
          default:
            throw new IllegalArgumentException("Shapefile shape type not supported: " + shapeType);
        }
      case ShapefileConstants.POINT_SHAPE:
        return util.readPoint(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.POINT_M_SHAPE:
        return util.readPointM(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.POINT_Z_SHAPE:
        return util.readPointZ(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.POINT_ZM_SHAPE:
        return util.readPointZM(this.geometryFactory, this.in, recordLength);

      case ShapefileConstants.MULTI_POINT_SHAPE:
        return util.readMultipoint(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.MULTI_POINT_M_SHAPE:
        return util.readMultipointM(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.MULTI_POINT_Z_SHAPE:
        return util.readMultipointZ(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.MULTI_POINT_ZM_SHAPE:
        return util.readMultipointZM(this.geometryFactory, this.in, recordLength);

      case ShapefileConstants.POLYLINE_SHAPE:
        return util.readPolyline(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.POLYLINE_M_SHAPE:
        return util.readPolylineM(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.POLYLINE_Z_SHAPE:
        return util.readPolylineZ(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.POLYLINE_ZM_SHAPE:
        return util.readPolylineZM(this.geometryFactory, this.in, recordLength);

      case ShapefileConstants.POLYGON_SHAPE:
        return util.readPolygon(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.POLYGON_M_SHAPE:
        return util.readPolygonM(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.POLYGON_Z_SHAPE:
        return util.readPolygonZ(this.geometryFactory, this.in, recordLength);
      case ShapefileConstants.POLYGON_ZM_SHAPE:
        return util.readPolygonZM(this.geometryFactory, this.in, recordLength);
      default:
        throw new IllegalArgumentException("Shapefile shape type not supported: " + shapeType);
    }
  }

  public void setCloseFile(final boolean closeFile) {
    this.closeFile = closeFile;
    if (this.xbaseIterator != null) {
      this.xbaseIterator.setCloseFile(closeFile);
    }
  }

  public void setPosition(final int position) {
    if (this.mappedFile) {
      final EndianMappedByteBuffer file = (EndianMappedByteBuffer)this.in;
      this.position = position;
      try {
        this.indexIn.seek(100 + 8 * position);
        final int offset = this.indexIn.readInt();
        file.seek(offset * 2);
        setLoadNext(true);
      } catch (final IOException e) {
        throw new RuntimeException("Unable to find record " + position, e);
      }
      if (this.xbaseIterator != null) {
        this.xbaseIterator.setPosition(position);
      }
    } else {
      throw new UnsupportedOperationException("The position can only be set on files");
    }
  }

  public void setRecordDefinition(final RecordDefinition recordDefinition) {
    this.returnRecordDefinition = recordDefinition;
  }

  public void setTypeName(final PathName typeName) {
    if (Property.hasValue(typeName)) {
      this.typeName = typeName;
    }
  }

  @Override
  public String toString() {
    return ShapefileConstants.DESCRIPTION + " " + this.resource;
  }

  private void updateRecordDefinition() {
    assert this.recordDefinition == null : "Cannot override recordDefinition when set";
    if (this.xbaseIterator != null) {
      final RecordDefinitionImpl recordDefinition = this.xbaseIterator.getRecordDefinition();
      this.recordDefinition = recordDefinition;
      if (recordDefinition.getGeometryFieldIndex() == -1) {
        DataType geometryType = DataTypes.GEOMETRY;
        switch (this.shapeType) {
          case ShapefileConstants.POINT_SHAPE:
          case ShapefileConstants.POINT_Z_SHAPE:
          case ShapefileConstants.POINT_M_SHAPE:
          case ShapefileConstants.POINT_ZM_SHAPE:
            geometryType = DataTypes.POINT;
          break;

          case ShapefileConstants.POLYLINE_SHAPE:
          case ShapefileConstants.POLYLINE_Z_SHAPE:
          case ShapefileConstants.POLYLINE_M_SHAPE:
          case ShapefileConstants.POLYLINE_ZM_SHAPE:
            geometryType = DataTypes.MULTI_LINE_STRING;
          break;

          case ShapefileConstants.POLYGON_SHAPE:
          case ShapefileConstants.POLYGON_Z_SHAPE:
          case ShapefileConstants.POLYGON_M_SHAPE:
          case ShapefileConstants.POLYGON_ZM_SHAPE:
            geometryType = DataTypes.MULTI_POLYGON;
          break;

          case ShapefileConstants.MULTI_POINT_SHAPE:
          case ShapefileConstants.MULTI_POINT_Z_SHAPE:
          case ShapefileConstants.MULTI_POINT_M_SHAPE:
          case ShapefileConstants.MULTI_POINT_ZM_SHAPE:
            geometryType = DataTypes.MULTI_POINT;
          break;

          default:
          break;
        }
        recordDefinition.addField("geometry", geometryType, true);
      }
    }
  }

}