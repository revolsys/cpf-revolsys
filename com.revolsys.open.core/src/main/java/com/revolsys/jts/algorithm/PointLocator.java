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
package com.revolsys.jts.algorithm;

import java.util.Iterator;

import com.revolsys.jts.geom.Geometry;
import com.revolsys.jts.geom.GeometryCollection;
import com.revolsys.jts.geom.GeometryCollectionIterator;
import com.revolsys.jts.geom.LineString;
import com.revolsys.jts.geom.LinearRing;
import com.revolsys.jts.geom.Location;
import com.revolsys.jts.geom.MultiLineString;
import com.revolsys.jts.geom.MultiPolygon;
import com.revolsys.jts.geom.Point;
import com.revolsys.jts.geom.Polygon;

/**
 * Computes the topological ({@link Location})
 * of a single point to a {@link Geometry}.
 * A {@link BoundaryNodeRule} may be specified 
 * to control the evaluation of whether the point lies on the boundary or not
 * The default rule is to use the the <i>SFS Boundary Determination Rule</i>
 * <p>
 * Notes:
 * <ul>
 * <li>{@link LinearRing}s do not enclose any area - points inside the ring are still in the EXTERIOR of the ring.
 * </ul>
 * Instances of this class are not reentrant.
 *
 * @version 1.7
 */
public class PointLocator {
  // default is to use OGC SFS rule
  private BoundaryNodeRule boundaryRule =
  // BoundaryNodeRule.ENDPOINT_BOUNDARY_RULE;
  BoundaryNodeRule.OGC_SFS_BOUNDARY_RULE;

  private boolean isIn; // true if the point lies in or on any Geometry element

  private int numBoundaries; // the number of sub-elements whose boundaries the
                             // point lies in

  public PointLocator() {
  }

  public PointLocator(final BoundaryNodeRule boundaryRule) {
    if (boundaryRule == null) {
      throw new IllegalArgumentException("Rule must be non-null");
    }
    this.boundaryRule = boundaryRule;
  }

  private void computeLocation(final Point p, final Geometry geom) {
    if (geom instanceof Point) {
      updateLocationInfo(locate(p, (Point)geom));
    }
    if (geom instanceof LineString) {
      updateLocationInfo(locate(p, (LineString)geom));
    } else if (geom instanceof Polygon) {
      updateLocationInfo(locate(p, (Polygon)geom));
    } else if (geom instanceof MultiLineString) {
      final MultiLineString ml = (MultiLineString)geom;
      for (int i = 0; i < ml.getGeometryCount(); i++) {
        final LineString l = (LineString)ml.getGeometry(i);
        updateLocationInfo(locate(p, l));
      }
    } else if (geom instanceof MultiPolygon) {
      final MultiPolygon mpoly = (MultiPolygon)geom;
      for (int i = 0; i < mpoly.getGeometryCount(); i++) {
        final Polygon poly = (Polygon)mpoly.getGeometry(i);
        updateLocationInfo(locate(p, poly));
      }
    } else if (geom instanceof GeometryCollection) {
      final Iterator<Geometry> geomi = new GeometryCollectionIterator(geom);
      while (geomi.hasNext()) {
        final Geometry g2 = geomi.next();
        if (g2 != geom) {
          computeLocation(p, g2);
        }
      }
    }
  }

  /**
   * Convenience method to test a point for intersection with
   * a Geometry
   * @param p the coordinate to test
   * @param geom the Geometry to test
   * @return <code>true</code> if the point is in the interior or boundary of the Geometry
   */
  public boolean intersects(final Point p, final Geometry geom) {
    return locate(p, geom) != Location.EXTERIOR;
  }

  /**
   * Computes the topological relationship ({@link Location}) of a single point
   * to a Geometry.
   * It handles both single-element
   * and multi-element Geometries.
   * The algorithm for multi-part Geometries
   * takes into account the SFS Boundary Determination Rule.
   *
   * @return the {@link Location} of the point relative to the input Geometry
   */
  public Location locate(final Point p, final Geometry geom) {
    if (geom.isEmpty()) {
      return Location.EXTERIOR;
    }

    if (geom instanceof LineString) {
      return locate(p, (LineString)geom);
    } else if (geom instanceof Polygon) {
      return locate(p, (Polygon)geom);
    }

    isIn = false;
    numBoundaries = 0;
    computeLocation(p, geom);
    if (boundaryRule.isInBoundary(numBoundaries)) {
      return Location.BOUNDARY;
    }
    if (numBoundaries > 0 || isIn) {
      return Location.INTERIOR;
    }

    return Location.EXTERIOR;
  }

  private Location locate(final Point point, final LineString line) {
    // bounding-box check
    if (!point.intersects(line.getBoundingBox())) {
      return Location.EXTERIOR;
    }

    if (!line.isClosed()) {
      if (point.equals(line.getVertex(0)) || point.equals(line.getVertex(-1))) {
        return Location.BOUNDARY;
      }
    }
    if (CGAlgorithms.isOnLine(point, line)) {
      return Location.INTERIOR;
    }
    return Location.EXTERIOR;
  }

  private Location locate(final Point p, final Point pt) {
    // no point in doing envelope test, since equality test is just as fast

    final Point ptCoord = pt.getPoint();
    if (ptCoord.equals(2, p)) {
      return Location.INTERIOR;
    }
    return Location.EXTERIOR;
  }

  private Location locate(final Point p, final Polygon poly) {
    if (poly.isEmpty()) {
      return Location.EXTERIOR;
    }

    final LinearRing shell = poly.getExteriorRing();

    final Location shellLoc = locateInPolygonRing(p, shell);
    if (shellLoc == Location.EXTERIOR) {
      return Location.EXTERIOR;
    }
    if (shellLoc == Location.BOUNDARY) {
      return Location.BOUNDARY;
    }
    // now test if the point lies in or on the holes
    for (int i = 0; i < poly.getNumInteriorRing(); i++) {
      final LinearRing hole = poly.getInteriorRing(i);
      final Location holeLoc = locateInPolygonRing(p, hole);
      if (holeLoc == Location.INTERIOR) {
        return Location.EXTERIOR;
      }
      if (holeLoc == Location.BOUNDARY) {
        return Location.BOUNDARY;
      }
    }
    return Location.INTERIOR;
  }

  private Location locateInPolygonRing(final Point p, final LinearRing ring) {
    // bounding-box check
    if (!p.intersects(ring.getBoundingBox())) {
      return Location.EXTERIOR;
    }

    return RayCrossingCounter.locatePointInRing(p, ring);
  }

  private void updateLocationInfo(final Location loc) {
    if (loc == Location.INTERIOR) {
      isIn = true;
    }
    if (loc == Location.BOUNDARY) {
      numBoundaries++;
    }
  }

}