/*
 * The JTS Topology Suite is a collection of Java classes that
 * implement the fundamental operations required to validate a given
 * geo-spatial data set to a known topological specification.
 *
 * Copyright (C) 2001 Vivid Solutions
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * For more information, contact:
 *
 *     Vivid Solutions
 *     Suite #1A
 *     2328 Government Street
 *     Victoria BC  V8T 5G5
 *     Canada
 *
 *     (250)385-6040
 *     www.vividsolutions.com
 */
package com.revolsys.jts.geom.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.revolsys.gis.data.model.types.DataType;
import com.revolsys.gis.data.model.types.DataTypes;
import com.revolsys.jts.geom.BoundingBox;
import com.revolsys.jts.geom.Dimension;
import com.revolsys.jts.geom.Envelope;
import com.revolsys.jts.geom.Geometry;
import com.revolsys.jts.geom.GeometryCollection;
import com.revolsys.jts.geom.GeometryFactory;
import com.revolsys.jts.geom.Point;

/**
 * Models a collection of {@link Geometry}s of
 * arbitrary type and dimension.
 * 
 *
 *@version 1.7
 */
public abstract class AbstractGeometryCollection extends BaseGeometry implements
  GeometryCollection {
  private static final long serialVersionUID = -8159852648192400768L;

  /**
   * Creates and returns a full copy of this {@link GeometryCollection} object.
   * (including all coordinates contained by it).
   *
   * @return a clone of this instance
   */
  @Override
  public AbstractGeometryCollection clone() {
    final AbstractGeometryCollection gc = (AbstractGeometryCollection)super.clone();
    return gc;
  }

  @Override
  public int compareToSameClass(final Geometry geometry) {
    final Set<Geometry> theseElements = new TreeSet<>(getGeometries());
    final Set<Geometry> otherElements = new TreeSet<>(geometry.getGeometries());
    return compare(theseElements, otherElements);
  }

  @Override
  protected BoundingBox computeBoundingBox() {
    BoundingBox envelope = new Envelope(getGeometryFactory());
    for (final Geometry geometry : geometries()) {
      envelope = envelope.expandToInclude(geometry);
    }
    return envelope;
  }

  @Override
  protected boolean doEquals(final int axisCount, final Geometry geometry) {
    final int geometryCount1 = getGeometryCount();
    final int geometryCount2 = geometry.getGeometryCount();
    if (geometryCount1 == geometryCount2) {
      for (int i = 0; i < geometryCount1; i++) {
        final Geometry part1 = getGeometry(i);
        final Geometry part2 = geometry.getGeometry(i);
        if (!part1.equals(axisCount, part2)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean equalsExact(final Geometry other, final double tolerance) {
    if (!isEquivalentClass(other)) {
      return false;
    }
    final GeometryCollection otherCollection = (GeometryCollection)other;
    if (getGeometryCount() != otherCollection.getGeometryCount()) {
      return false;
    }
    int i = 0;
    for (final Geometry geometry : geometries()) {
      if (!geometry.equalsExact(otherCollection.getGeometry(i), tolerance)) {
        return false;
      }
      i++;
    }
    return true;
  }

  @Override
  public Iterable<Geometry> geometries() {
    return getGeometries();
  }

  /**
   *  Returns the area of this <code>GeometryCollection</code>
   *
   * @return the area of the polygon
   */
  @Override
  public double getArea() {
    double area = 0.0;
    for (final Geometry geometry : geometries()) {
      area += geometry.getArea();
    }
    return area;
  }

  @Override
  public Geometry getBoundary() {
    throw new IllegalArgumentException(
      "This method does not support GeometryCollection arguments");
  }

  @Override
  public int getBoundaryDimension() {
    int dimension = Dimension.FALSE;
    for (final Geometry geometry : geometries()) {
      dimension = Math.max(dimension, geometry.getBoundaryDimension());
    }
    return dimension;
  }

  @Override
  public DataType getDataType() {
    return DataTypes.GEOMETRY_COLLECTION;
  }

  @Override
  public int getDimension() {
    int dimension = Dimension.FALSE;
    for (final Geometry geometry : geometries()) {
      dimension = Math.max(dimension, geometry.getDimension());
    }
    return dimension;
  }

  @Override
  public <V extends Geometry> List<V> getGeometries(final Class<V> geometryClass) {
    final List<V> geometries = super.getGeometries(geometryClass);
    for (final Geometry geometry : geometries()) {
      if (geometry != null) {
        final List<V> partGeometries = geometry.getGeometries(geometryClass);
        geometries.addAll(partGeometries);
      }
    }
    return geometries;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <V extends Geometry> V getGeometry(final int partIndex) {
    final List<Geometry> geometries = getGeometries();
    return (V)geometries.get(partIndex);
  }

  @Override
  public <V extends Geometry> List<V> getGeometryComponents(
    final Class<V> geometryClass) {
    final List<V> geometries = super.getGeometryComponents(geometryClass);
    for (final Geometry geometry : geometries()) {
      if (geometry != null) {
        final List<V> partGeometries = geometry.getGeometryComponents(geometryClass);
        geometries.addAll(partGeometries);
      }
    }
    return geometries;
  }

  @Override
  public int getGeometryCount() {
    return getGeometries().size();
  }

  @Override
  public double getLength() {
    double sum = 0.0;
    for (final Geometry geometry : geometries()) {
      sum += geometry.getLength();
    }
    return sum;
  }

  @Override
  public Point getPoint() {
    if (isEmpty()) {
      return null;
    } else {
      return getGeometry(0).getPoint();
    }
  }

  @Override
  public int getVertexCount() {
    int numPoints = 0;
    for (final Geometry geometry : geometries()) {
      numPoints += geometry.getVertexCount();
    }
    return numPoints;
  }

  @Override
  public boolean intersects(final BoundingBox boundingBox) {
    if (isEmpty() || boundingBox.isEmpty()) {
      return false;
    } else {
      for (final Geometry geometry : geometries()) {
        if (geometry.intersects(boundingBox)) {
          return true;
        }
      }
      return false;
    }
  }

  @Override
  public boolean isEmpty() {
    if (getGeometryCount() == 0) {
      return true;
    } else {
      for (final Geometry geometry : geometries()) {
        if (!geometry.isEmpty()) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public Geometry move(final double... deltas) {
    if (deltas == null || isEmpty()) {
      return this;
    } else {
      final List<Geometry> parts = new ArrayList<>();
      for (final Geometry part : geometries()) {
        final Geometry movedPart = part.move(deltas);
        parts.add(movedPart);
      }
      final GeometryFactory geometryFactory = getGeometryFactory();
      return geometryFactory.geometryCollection(parts);
    }
  }

  @Override
  public GeometryCollection normalize() {
    final List<Geometry> geometries = new ArrayList<>();
    for (final Geometry part : geometries()) {
      final Geometry normalizedPart = part.normalize();
      geometries.add(normalizedPart);
    }
    Collections.sort(geometries);
    final GeometryFactory geometryFactory = getGeometryFactory();
    final GeometryCollection normalizedGeometry = geometryFactory.geometryCollection(geometries);
    return normalizedGeometry;
  }

  /**
   * Creates a {@link GeometryCollection} with
   * every component reversed.
   * The order of the components in the collection are not reversed.
   *
   * @return a {@link GeometryCollection} in the reverse order
   */
  @Override
  public GeometryCollection reverse() {
    final List<Geometry> revGeoms = new ArrayList<>();
    for (final Geometry geometry : geometries()) {
      if (!geometry.isEmpty()) {
        final Geometry reverse = geometry.reverse();
        revGeoms.add(reverse);
      }
    }
    final GeometryFactory geometryFactory = getGeometryFactory();
    return geometryFactory.geometryCollection(revGeoms);
  }
}