package com.revolsys.gis.graph.filter;

import com.revolsys.filter.Filter;
import com.revolsys.gis.graph.Edge;
import com.revolsys.gis.graph.Node;
import com.revolsys.gis.jts.LineStringUtil;
import com.revolsys.jts.geom.BoundingBox;
import com.revolsys.jts.geom.Envelope;
import com.revolsys.jts.geom.LineString;

public class IsPointOnLineEdgeFilter<T> implements Filter<Node<T>> {

  private final Edge<T> edge;

  private BoundingBox envelope;

  private final double maxDistance;

  public IsPointOnLineEdgeFilter(final Edge<T> edge, final double maxDistance) {
    this.edge = edge;
    this.maxDistance = maxDistance;
    this.envelope = edge.getBoundingBox();
    envelope = envelope.expand(maxDistance);
  }

  @Override
  public boolean accept(final Node<T> node) {
    final LineString line = edge.getLine();
    if (!edge.hasNode(node)) {
      if (envelope.intersects(new Envelope(node))) {
        if (LineStringUtil.isPointOnLine(line, node, maxDistance)) {
          return true;
        }
      }
    }
    return false;
  }

  public com.revolsys.jts.geom.BoundingBox getEnvelope() {
    return envelope;
  }

}
