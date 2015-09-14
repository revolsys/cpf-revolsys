package com.revolsys.geometry.graph.visitor;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.revolsys.equals.EqualsInstance;
import com.revolsys.geometry.graph.Edge;
import com.revolsys.geometry.graph.Node;
import com.revolsys.geometry.graph.RecordGraph;
import com.revolsys.geometry.model.End;
import com.revolsys.record.Record;
import com.revolsys.record.schema.RecordDefinition;

public class PolygonNodeRemovalVisitor implements Consumer<Node<Record>> {

  private final Collection<String> excludedAttributes = new HashSet<String>();

  private final RecordGraph graph;

  public PolygonNodeRemovalVisitor(final RecordDefinition recordDefinition, final RecordGraph graph,
    final Collection<String> excludedAttributes) {
    super();
    this.graph = graph;
    if (excludedAttributes != null) {
      this.excludedAttributes.addAll(excludedAttributes);
    }
  }

  @Override
  public void accept(final Node<Record> node) {
    final Set<Edge<Record>> edges = new LinkedHashSet<Edge<Record>>(node.getEdges());
    while (edges.size() > 1) {
      final Edge<Record> edge = edges.iterator().next();
      final Record object = edge.getObject();
      final Set<Edge<Record>> matchedEdges = new HashSet<Edge<Record>>();
      final End end = edge.getEnd(node);
      for (final Edge<Record> matchEdge : edges) {
        final Record matchObject = matchEdge.getObject();
        if (edge != matchEdge) {
          final End matchEnd = matchEdge.getEnd(node);
          if (end != matchEnd) {
            if (EqualsInstance.INSTANCE.equals(object, matchObject, this.excludedAttributes)) {
              matchedEdges.add(matchEdge);
            }
          }
        }
      }
      if (matchedEdges.size() == 1) {
        final Edge<Record> matchedEdge = matchedEdges.iterator().next();
        if (end.isFrom()) {
          this.graph.merge(node, matchedEdge, edge);
        } else {
          this.graph.merge(node, edge, matchedEdge);
        }
      }
      edges.removeAll(matchedEdges);
      edges.remove(edge);
    }
  }

}
