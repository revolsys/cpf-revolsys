package com.revolsys.gis.wms.capabilities;

public class WmsBoundingBox {
  private com.revolsys.geometry.model.BoundingBox envelope;

  private double resX;

  private double resY;

  private String srs;

  public com.revolsys.geometry.model.BoundingBox getEnvelope() {
    return this.envelope;
  }

  public double getResX() {
    return this.resX;
  }

  public double getResY() {
    return this.resY;
  }

  public String getSrs() {
    return this.srs;
  }

  public void setEnvelope(final com.revolsys.geometry.model.BoundingBox envelope) {
    this.envelope = envelope;
  }

  public void setResX(final double resX) {
    this.resX = resX;
  }

  public void setResY(final double resY) {
    this.resY = resY;
  }

  public void setSrs(final String srs) {
    this.srs = srs;
  }

}