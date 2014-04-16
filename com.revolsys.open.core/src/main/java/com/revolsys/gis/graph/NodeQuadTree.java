package com.revolsys.gis.graph;

import java.util.Collection;
import java.util.List;

import com.revolsys.gis.algorithm.index.AbstractIdObjectPointQuadTree;
import com.revolsys.jts.geom.BoundingBox;
import com.revolsys.jts.geom.Coordinates;
import com.revolsys.jts.geom.Envelope;
import com.revolsys.visitor.CreateListVisitor;

public class NodeQuadTree<T> extends AbstractIdObjectPointQuadTree<Node<T>> {
  private final Graph<T> graph;

  public NodeQuadTree(final Graph<T> graph) {
    this.graph = graph;
    final Collection<Integer> ids = graph.getNodeIds();
    add(ids);
  }

  @Override
  public Coordinates getCoordinates(final Node<T> node) {
    return node;
  }

  @Override
  public BoundingBox getEnvelope(final Node<T> node) {
    if (node == null) {
      return new Envelope();
    } else {
      final double x = node.getX();
      final double y = node.getY();
      final BoundingBox envelope = new Envelope(x, y, x, y);
      return envelope;
    }
  }

  @Override
  public int getId(final Node<T> object) {
    return object.getId();
  }

  @Override
  public Node<T> getObject(final Integer id) {
    return this.graph.getNode(id);
  }

  @Override
  public List<Node<T>> getObjects(final List<Integer> ids) {
    return graph.getNodes(ids);
  }

  @Override
  public List<Node<T>> query(final BoundingBox envelope) {
    final CreateListVisitor<Node<T>> visitor = new CreateListVisitor<Node<T>>();
    visit(envelope, visitor);
    return visitor.getList();
  }
}
