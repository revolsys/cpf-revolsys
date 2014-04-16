
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

import com.revolsys.jts.geom.BoundingBox;
import com.revolsys.jts.geom.Coordinate;
import com.revolsys.jts.geom.Coordinates;
import com.revolsys.jts.geom.CoordinatesList;
import com.revolsys.jts.geom.Geometry;
import com.revolsys.jts.geom.GeometryCollection;
import com.revolsys.jts.geom.GeometryFactory;
import com.revolsys.jts.geom.LineString;
import com.revolsys.jts.geom.Polygon;

/**
 * Computes a point in the interior of an areal geometry.
 *
 * <h2>Algorithm</h2>
 * <ul>
 *   <li>Find a Y value which is close to the centre of 
 *       the geometry's vertical extent but is different
 *       to any of it's Y ordinates.
 *   <li>Create a horizontal bisector line using the Y value
 *       and the geometry's horizontal extent
 *   <li>Find the intersection between the geometry
 *       and the horizontal bisector line.
 *       The intersection is a collection of lines and points.
 *   <li>Pick the midpoint of the largest intersection geometry  
 * </ul>
 *
 * <h3>KNOWN BUGS</h3>
 * <ul>
 * <li>If a fixed precision model is used,
 * in some cases this method may return a point
 * which does not lie in the interior.
 * </ul>
 *
 * @version 1.7
 */
public class InteriorPointArea {

  private static double avg(double a, double b)
  {
    return (a + b) / 2.0;
  }

  private GeometryFactory factory;
  private Coordinates interiorPoint = null;
  private double maxWidth = 0.0;

  /**
   * Creates a new interior point finder
   * for an areal geometry.
   * 
   * @param g an areal geometry
   */
  public InteriorPointArea(Geometry g)
  {
    factory = g.getGeometryFactory();
    add(g);
  }
  
  /**
   * Gets the computed interior point.
   * 
   * @return the coordinate of an interior point
   */
  public Coordinates getInteriorPoint()
  {
    return interiorPoint;
  }

  /**
   * Tests the interior vertices (if any)
   * defined by an areal Geometry for the best inside point.
   * If a component Geometry is not of dimension 2 it is not tested.
   * 
   * @param geom the geometry to add
   */
  private void add(Geometry geom)
  {
    if (geom instanceof Polygon) {
      addPolygon(geom);
    }
    else if (geom instanceof GeometryCollection) {
      GeometryCollection gc = (GeometryCollection) geom;
      for (int i = 0; i < gc.getNumGeometries(); i++) {
        add(gc.getGeometry(i));
      }
    }
  }

  /**
   * Finds an interior point of a Polygon.
   * @param geometry the geometry to analyze
   */
  private void addPolygon(Geometry geometry) {
    if (geometry.isEmpty())
      return;
    
    Coordinates intPt;
    double width = 0;
    
    LineString bisector = horizontalBisector(geometry);
    if (bisector.getLength() == 0.0) {
      width = 0;
      intPt = bisector.getCoordinate();
    }
    else {
      Geometry intersections = bisector.intersection(geometry);
      Geometry widestIntersection = widestGeometry(intersections);
      width = widestIntersection.getBoundingBox().getWidth();
      intPt = centre(widestIntersection.getBoundingBox());
    }
    if (interiorPoint == null || width > maxWidth) {
      interiorPoint = intPt;
      maxWidth = width;
    }
  }

  //@return if geometry is a collection, the widest sub-geometry; otherwise,
  //the geometry itself
  private Geometry widestGeometry(Geometry geometry) {
    if (!(geometry instanceof GeometryCollection)) {
        return geometry;
    }
    return widestGeometry((GeometryCollection) geometry);
  }

  private Geometry widestGeometry(GeometryCollection gc) {
    if (gc.isEmpty()) {
        return gc;
    }

    Geometry widestGeometry = gc.getGeometry(0);
    // scan remaining geom components to see if any are wider
    for (int i = 1; i < gc.getNumGeometries(); i++) { 
        if (gc.getGeometry(i).getBoundingBox().getWidth() >
            widestGeometry.getBoundingBox().getWidth()) {
            widestGeometry = gc.getGeometry(i);
        }
    }
    return widestGeometry;
  }

  protected LineString horizontalBisector(Geometry geometry) {
    BoundingBox envelope = geometry.getBoundingBox();

    /**
     * Original algorithm.  Fails when geometry contains a horizontal
     * segment at the Y midpoint.
     */
    // Assert: for areas, minx <> maxx
    //double avgY = avg(envelope.getMinY(), envelope.getMaxY());
    
    double bisectY = SafeBisectorFinder.getBisectorY((Polygon) geometry);
    return factory.lineString(new Coordinates[] {
            new Coordinate((double)envelope.getMinX(), bisectY, Coordinates.NULL_ORDINATE),
            new Coordinate((double)envelope.getMaxX(), bisectY, Coordinates.NULL_ORDINATE)
        });
  }

  /**
   * Returns the centre point of the envelope.
   * @param envelope the envelope to analyze
   * @return the centre of the envelope
   */
  public static Coordinates centre(BoundingBox envelope) {
      return new Coordinate((double)avg(envelope.getMinX(),
              envelope.getMaxX()),
          avg(envelope.getMinY(), envelope.getMaxY()), Coordinates.NULL_ORDINATE);
  }

  /**
   * Finds a safe bisector Y ordinate
   * by projecting to the Y axis
   * and finding the Y-ordinate interval
   * which contains the centre of the Y extent.
   * The centre of this interval is returned as the bisector Y-ordinate.
   * 
   * @author mdavis
   *
   */
  private static class SafeBisectorFinder 
  {
	  public static double getBisectorY(Polygon poly)
	  {
		  SafeBisectorFinder finder = new SafeBisectorFinder(poly);
		  return finder.getBisectorY();
	  }
	  
	  private Polygon poly;
	  
	  private double centreY;
	  private double hiY = Double.MAX_VALUE;
	  private double loY = -Double.MAX_VALUE;
	  
	  public SafeBisectorFinder(Polygon poly) {
		  this.poly = poly;
		  
		  // initialize using extremal values
		  hiY = poly.getBoundingBox().getMaxY();
		  loY = poly.getBoundingBox().getMinY();
		  centreY = avg(loY, hiY);
	  }
	  
	  public double getBisectorY()
	  {
		  process(poly.getExteriorRing());
		  for (int i = 0; i < poly.getNumInteriorRing(); i++) {
			  process(poly.getInteriorRingN(i));
		  }
		  double bisectY = avg(hiY, loY);
		  return bisectY;
	  }

	private void process(LineString line) {
		CoordinatesList seq = line.getCoordinatesList();
		for (int i = 0; i < seq.size(); i++) {
			double y = seq.getY(i);
			updateInterval(y);
		}
	}

	private void updateInterval(double y) {
		if (y <= centreY) {
			if (y > loY)
				loY = y;
		}
		else if (y > centreY) {
			if (y < hiY) {
				hiY = y;
			}
		}
	}
  }
}
