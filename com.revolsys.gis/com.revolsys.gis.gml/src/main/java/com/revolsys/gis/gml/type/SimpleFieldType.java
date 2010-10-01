package com.revolsys.gis.gml.type;

import com.revolsys.gis.data.model.types.DataType;
import com.revolsys.xml.io.XmlWriter;

public class SimpleFieldType extends AbstractGmlFieldType {

  public SimpleFieldType(
    DataType dataType) {
    super(dataType, "xs:" + dataType.getName().getLocalPart());
  }

  protected void writeValueText(
    XmlWriter out,
    Object value) {
    out.text(value);
  }
}
