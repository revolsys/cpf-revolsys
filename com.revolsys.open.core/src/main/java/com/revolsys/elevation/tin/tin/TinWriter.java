package com.revolsys.elevation.tin.tin;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.revolsys.elevation.tin.TriangulatedIrregularNetwork;
import com.revolsys.elevation.tin.TriangulatedIrregularNetworkWriter;
import com.revolsys.geometry.model.Point;
import com.revolsys.geometry.model.Triangle;
import com.revolsys.properties.BaseObjectWithProperties;
import com.revolsys.spring.resource.Resource;

public class TinWriter extends BaseObjectWithProperties
  implements TriangulatedIrregularNetworkWriter {

  private final PrintWriter out;

  private int tinIndex = 0;

  public TinWriter(final Resource resource) {
    this.out = resource.newPrintWriter();
    this.out.println("TIN");
  }

  @Override
  public void close() {
    this.out.close();
  }

  @Override
  public void flush() {
    this.out.flush();
  }

  @Override
  public void open() {
  }

  @Override
  public void write(final TriangulatedIrregularNetwork tin) {
    this.out.println("BEGT");

    this.out.print("TNAM tin-");
    this.out.println(++this.tinIndex);

    this.out.println("TCOL 255 255 255");

    int nodeIndex = 0;
    final Map<Point, Integer> nodeMap = new HashMap<>();
    final Set<Point> nodes = tin.getNodes();
    this.out.print("VERT ");
    this.out.println(nodes.size());
    for (final Point point : nodes) {
      nodeMap.put(point, ++nodeIndex);
      this.out.print(point.getX());
      this.out.print(' ');
      this.out.print(point.getY());
      this.out.print(' ');
      this.out.println(point.getZ());
    }

    final List<Triangle> triangles = tin.getTriangles();
    this.out.print("TRI ");
    this.out.println(triangles.size());
    for (final Triangle triangle : triangles) {
      for (int i = 0; i < 3; i++) {
        if (i > 0) {
          this.out.print(' ');
        }
        final Point point = triangle.getPoint(i);
        final Integer index = nodeMap.get(point);
        if (index == null) {
          throw new NullPointerException();
        }
        this.out.print(index);
      }
      this.out.println();
    }

    this.out.println("ENDT");
  }
}