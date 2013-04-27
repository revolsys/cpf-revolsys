package com.revolsys.gis.algorithm.index.quadtree;

import java.util.List;

import com.revolsys.collection.Visitor;
import com.revolsys.filter.Filter;
import com.revolsys.filter.InvokeMethodFilter;
import com.revolsys.gis.cs.BoundingBox;
import com.revolsys.gis.cs.GeometryFactory;
import com.revolsys.gis.data.model.DataObject;
import com.revolsys.visitor.CreateListVisitor;
import com.revolsys.visitor.SingleObjectVisitor;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

public class QuadTree<T> {
  private GeometryFactory geometryFactory;

  public static Envelope ensureExtent(final Envelope envelope,
    final double minExtent) {
    double minX = envelope.getMinX();
    double maxX = envelope.getMaxX();
    double minY = envelope.getMinY();
    double maxY = envelope.getMaxY();
    if (minX != maxX && minY != maxY) {
      return envelope;
    }

    if (minX == maxX) {
      minX = minX - minExtent / 2.0;
      maxX = minX + minExtent / 2.0;
    }
    if (minY == maxY) {
      minY = minY - minExtent / 2.0;
      maxY = minY + minExtent / 2.0;
    }
    return new Envelope(minX, maxX, minY, maxY);
  }

  private final Root<T> root = new Root<T>();;

  private double minExtent = 1.0;

  private int size = 0;

  public QuadTree() {
  }

  public QuadTree(GeometryFactory geometryFactory) {
    this.geometryFactory = geometryFactory;
  }

  private void collectStats(final Envelope envelope) {
    final double delX = envelope.getWidth();
    if (delX < minExtent && delX > 0.0) {
      minExtent = delX;
    }

    final double delY = envelope.getHeight();
    if (delY < minExtent && delY > 0.0) {
      minExtent = delY;
    }
  }

  public int depth() {
    if (root != null) {
      return root.depth();
    }
    return 0;
  }

  public int getSize() {
    return size;
  }

  public void insert(final Envelope envelope, final T item) {
    if (envelope == null) {
      throw new IllegalArgumentException("Item envelope must not be null");
    } else {
      size++;
      collectStats(envelope);
      final Envelope insertEnv = ensureExtent(envelope, minExtent);
      root.insert(insertEnv, item);
    }
  }

  public List<T> query(BoundingBox boundingBox) {
    final CreateListVisitor<T> visitor = new CreateListVisitor<T>();
    query(boundingBox, visitor);
    return visitor.getList();
  }

  public List<T> query(Envelope envelope) {
    return query(new BoundingBox(envelope));
  }

  public void query(Envelope envelope, Visitor<T> visitor) {
    query(new BoundingBox(envelope), visitor);
  }

  public List<T> query(BoundingBox boundingBox, Filter<T> filter) {
    final CreateListVisitor<T> visitor = new CreateListVisitor<T>(filter);
    query(boundingBox, visitor);
    return visitor.getList();
  }

  public List<T> query(BoundingBox boundingBox, String methodName,
    Object... parameters) {
    InvokeMethodFilter<T> filter = new InvokeMethodFilter<T>(methodName,
      parameters);
    return query(boundingBox, filter);
  }

  private BoundingBox convertBoundingBox(BoundingBox boundingBox) {
    if (geometryFactory != null) {
      boundingBox = boundingBox.convert(geometryFactory);
    }
    return boundingBox;
  }

  public List<T> queryEnvelope(final BoundingBox boundingBox) {
    return query(boundingBox);
  }

  public List<T> queryEnvelope(final Geometry geometry) {
    final BoundingBox boundingBox = BoundingBox.getBoundingBox(geometry);
    return query(boundingBox);
  }

  public void query(BoundingBox boundingBox, final Visitor<T> visitor) {
    boundingBox = convertBoundingBox(boundingBox);
    root.visit(boundingBox, visitor);
  }

  public T queryFirst(final Geometry geometry, final Filter<T> filter) {
    BoundingBox boundingBox = BoundingBox.getBoundingBox(geometry);
    return queryFirst(boundingBox, filter);
  }

  public T queryFirst(final BoundingBox boundingBox, final Filter<T> filter) {
    final SingleObjectVisitor<T> visitor = new SingleObjectVisitor<T>(filter);
    query(boundingBox, visitor);
    return visitor.getObject();
  }

  public List<T> queryAll() {
    final CreateListVisitor<T> visitor = new CreateListVisitor<T>();
    root.visit(visitor);
    return visitor.getList();
  }

  public boolean remove(final Envelope envelope, T item) {
    final Envelope posEnv = ensureExtent(envelope, minExtent);
    boolean removed = root.remove(posEnv, item);
    if (removed) {
      size--;
    }
    return removed;
  }

  public int size() {
    if (root != null) {
      return root.size();
    }
    return 0;
  }

}