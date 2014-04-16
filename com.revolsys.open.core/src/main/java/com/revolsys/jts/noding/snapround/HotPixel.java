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

package com.revolsys.jts.noding.snapround;

import com.revolsys.jts.algorithm.LineIntersector;
import com.revolsys.jts.geom.BoundingBox;
import com.revolsys.jts.geom.Coordinate;
import com.revolsys.jts.geom.Coordinates;
import com.revolsys.jts.geom.Envelope;
import com.revolsys.jts.noding.NodedSegmentString;
import com.revolsys.jts.util.Assert;

/**
 * Implements a "hot pixel" as used in the Snap Rounding algorithm.
 * A hot pixel contains the interior of the tolerance square and
 * the boundary
 * <b>minus</b> the top and right segments.
 * <p>
 * The hot pixel operations are all computed in the integer domain
 * to avoid rounding problems.
 *
 * @version 1.7
 */
public class HotPixel {
  // testing only
  // public static int nTests = 0;

  private final LineIntersector li;

  private Coordinates pt;

  private final Coordinates originalPt;

  private Coordinates ptScaled;

  private Coordinates p0Scaled;

  private Coordinates p1Scaled;

  private final double scaleFactor;

  private double minx;

  private double maxx;

  private double miny;

  private double maxy;

  /**
   * The corners of the hot pixel, in the order:
   *  10
   *  23
   */
  private final Coordinates[] corner = new Coordinates[4];

  private BoundingBox safeEnv = null;

  private static final double SAFE_ENV_EXPANSION_FACTOR = 0.75;

  /**
   * Creates a new hot pixel, using a given scale factor.
   * The scale factor must be strictly positive (non-zero).
   * 
   * @param pt the coordinate at the centre of the pixel
   * @param scaleFactor the scaleFactor determining the pixel size.  Must be > 0
   * @param li the intersector to use for testing intersection with line segments
   * 
   */
  public HotPixel(final Coordinates pt, final double scaleFactor,
    final LineIntersector li) {
    originalPt = pt;
    this.pt = pt;
    this.scaleFactor = scaleFactor;
    this.li = li;
    // tolerance = 0.5;
    if (scaleFactor <= 0) {
      throw new IllegalArgumentException("Scale factor must be non-zero");
    }
    if (scaleFactor != 1.0) {
      this.pt = new Coordinate(scale(pt.getX()), scale(pt.getY()),
        Coordinates.NULL_ORDINATE);
      p0Scaled = new Coordinate();
      p1Scaled = new Coordinate();
    }
    initCorners(this.pt);
  }

  /**
   * Adds a new node (equal to the snap pt) to the specified segment
   * if the segment passes through the hot pixel
   *
   * @param segStr
   * @param segIndex
   * @return true if a node was added to the segment
   */
  public boolean addSnappedNode(final NodedSegmentString segStr,
    final int segIndex) {
    final Coordinates p0 = segStr.getCoordinate(segIndex);
    final Coordinates p1 = segStr.getCoordinate(segIndex + 1);

    if (intersects(p0, p1)) {
      // System.out.println("snapped: " + snapPt);
      // System.out.println("POINT (" + snapPt.x + " " + snapPt.y + ")");
      segStr.addIntersection(getCoordinate(), segIndex);

      return true;
    }
    return false;
  }

  private void copyScaled(final Coordinates p, final Coordinates pScaled) {
    pScaled.setX(scale(p.getX()));
    pScaled.setY(scale(p.getY()));
  }

  /**
   * Gets the coordinate this hot pixel is based at.
   * 
   * @return the coordinate of the pixel
   */
  public Coordinates getCoordinate() {
    return originalPt;
  }

  /**
   * Returns a "safe" envelope that is guaranteed to contain the hot pixel.
   * The envelope returned will be larger than the exact envelope of the 
   * pixel.
   * 
   * @return an envelope which contains the hot pixel
   */
  public BoundingBox getSafeEnvelope() {
    if (safeEnv == null) {
      final double safeTolerance = SAFE_ENV_EXPANSION_FACTOR / scaleFactor;
      safeEnv = new Envelope(originalPt.getX() - safeTolerance,
        originalPt.getY() - safeTolerance, originalPt.getX() + safeTolerance,
        originalPt.getY() + safeTolerance);
    }
    return safeEnv;
  }

  private void initCorners(final Coordinates pt) {
    final double tolerance = 0.5;
    minx = pt.getX() - tolerance;
    maxx = pt.getX() + tolerance;
    miny = pt.getY() - tolerance;
    maxy = pt.getY() + tolerance;

    corner[0] = new Coordinate(maxx, maxy, Coordinates.NULL_ORDINATE);
    corner[1] = new Coordinate(minx, maxy, Coordinates.NULL_ORDINATE);
    corner[2] = new Coordinate(minx, miny, Coordinates.NULL_ORDINATE);
    corner[3] = new Coordinate(maxx, miny, Coordinates.NULL_ORDINATE);
  }

  /**
   * Tests whether the line segment (p0-p1) 
   * intersects this hot pixel.
   * 
   * @param p0 the first coordinate of the line segment to test
   * @param p1 the second coordinate of the line segment to test
   * @return true if the line segment intersects this hot pixel
   */
  public boolean intersects(final Coordinates p0, final Coordinates p1) {
    if (scaleFactor == 1.0) {
      return intersectsScaled(p0, p1);
    }

    copyScaled(p0, p0Scaled);
    copyScaled(p1, p1Scaled);
    return intersectsScaled(p0Scaled, p1Scaled);
  }

  /**
   * Test whether the given segment intersects
   * the closure of this hot pixel.
   * This is NOT the test used in the standard snap-rounding
   * algorithm, which uses the partially closed tolerance square
   * instead.
   * This routine is provided for testing purposes only.
   *
   * @param p0 the start point of a line segment
   * @param p1 the end point of a line segment
   * @return <code>true</code> if the segment intersects the closure of the pixel's tolerance square
   */
  private boolean intersectsPixelClosure(final Coordinates p0,
    final Coordinates p1) {
    li.computeIntersection(p0, p1, corner[0], corner[1]);
    if (li.hasIntersection()) {
      return true;
    }
    li.computeIntersection(p0, p1, corner[1], corner[2]);
    if (li.hasIntersection()) {
      return true;
    }
    li.computeIntersection(p0, p1, corner[2], corner[3]);
    if (li.hasIntersection()) {
      return true;
    }
    li.computeIntersection(p0, p1, corner[3], corner[0]);
    if (li.hasIntersection()) {
      return true;
    }

    return false;
  }

  private boolean intersectsScaled(final Coordinates p0, final Coordinates p1) {
    final double segMinx = Math.min(p0.getX(), p1.getX());
    final double segMaxx = Math.max(p0.getX(), p1.getX());
    final double segMiny = Math.min(p0.getY(), p1.getY());
    final double segMaxy = Math.max(p0.getY(), p1.getY());

    final boolean isOutsidePixelEnv = maxx < segMinx || minx > segMaxx
      || maxy < segMiny || miny > segMaxy;
    if (isOutsidePixelEnv) {
      return false;
    }
    final boolean intersects = intersectsToleranceSquare(p0, p1);
    // boolean intersectsPixelClosure = intersectsPixelClosure(p0, p1);

    // if (intersectsPixel != intersects) {
    // Debug.println("Found hot pixel intersection mismatch at " + pt);
    // Debug.println("Test segment: " + p0 + " " + p1);
    // }

    /*
     * if (scaleFactor != 1.0) { boolean intersectsScaled =
     * intersectsScaledTest(p0, p1); if (intersectsScaled != intersects) {
     * intersectsScaledTest(p0, p1); //
     * Debug.println("Found hot pixel scaled intersection mismatch at " + pt);
     * // Debug.println("Test segment: " + p0 + " " + p1); } return
     * intersectsScaled; }
     */

    Assert.isTrue(!(isOutsidePixelEnv && intersects), "Found bad envelope test");
    // if (isOutsideEnv && intersects) {
    // Debug.println("Found bad envelope test");
    // }

    return intersects;
    // return intersectsPixelClosure;
  }

  /**
   * Tests whether the segment p0-p1 intersects the hot pixel tolerance square.
   * Because the tolerance square point set is partially open (along the
   * top and right) the test needs to be more sophisticated than
   * simply checking for any intersection.  
   * However, it can take advantage of the fact that the hot pixel edges
   * do not lie on the coordinate grid.  
   * It is sufficient to check if any of the following occur:
   * <ul>
   * <li>a proper intersection between the segment and any hot pixel edge
   * <li>an intersection between the segment and <b>both</b> the left and bottom hot pixel edges
   * (which detects the case where the segment intersects the bottom left hot pixel corner)
   * <li>an intersection between a segment endpoint and the hot pixel coordinate
   * </ul>
   *
   * @param p0
   * @param p1
   * @return
   */
  private boolean intersectsToleranceSquare(final Coordinates p0,
    final Coordinates p1) {
    boolean intersectsLeft = false;
    boolean intersectsBottom = false;

    li.computeIntersection(p0, p1, corner[0], corner[1]);
    if (li.isProper()) {
      return true;
    }

    li.computeIntersection(p0, p1, corner[1], corner[2]);
    if (li.isProper()) {
      return true;
    }
    if (li.hasIntersection()) {
      intersectsLeft = true;
    }

    li.computeIntersection(p0, p1, corner[2], corner[3]);
    if (li.isProper()) {
      return true;
    }
    if (li.hasIntersection()) {
      intersectsBottom = true;
    }

    li.computeIntersection(p0, p1, corner[3], corner[0]);
    if (li.isProper()) {
      return true;
    }

    if (intersectsLeft && intersectsBottom) {
      return true;
    }

    if (p0.equals(pt)) {
      return true;
    }
    if (p1.equals(pt)) {
      return true;
    }

    return false;
  }

  private double scale(final double val) {
    return Math.round(val * scaleFactor);
  }

}
