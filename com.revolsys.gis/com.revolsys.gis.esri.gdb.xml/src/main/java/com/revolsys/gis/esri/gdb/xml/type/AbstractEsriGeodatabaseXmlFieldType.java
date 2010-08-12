package com.revolsys.gis.esri.gdb.xml.type;

import com.revolsys.gis.data.model.types.DataType;
import com.revolsys.gis.esri.gdb.xml.EsriGeodatabaseXmlConstants;
import com.revolsys.xml.XsiConstants;
import com.revolsys.xml.io.XmlWriter;

public abstract class AbstractEsriGeodatabaseXmlFieldType implements
  EsriGeodatabaseXmlFieldType, EsriGeodatabaseXmlConstants {

  private String esriFieldTypeName;

  private String xmlSchemaTypeName;

  private DataType dataType;

  public boolean isUsePrecision() {
    return false;
  }

  public int getFixedLength() {
    return -1;
  }

  public AbstractEsriGeodatabaseXmlFieldType(
    DataType dataType,
    String xmlSchemaTypeName,
    String esriFieldTypeName) {
    this.dataType = dataType;
    this.xmlSchemaTypeName = xmlSchemaTypeName;
    this.esriFieldTypeName = esriFieldTypeName;
  }

  public void writeValue(
    XmlWriter out,
    Object value) {
    out.startTag(EsriGeodatabaseXmlConstants.VALUE);
    if (value == null) {
      out.attribute(XsiConstants.NIL, true);
    } else {
      out.attribute(XsiConstants.TYPE, getType(value));
      writeValueText(out, value);
    }
    out.endTag(EsriGeodatabaseXmlConstants.VALUE);
  }

  protected String getType(
    Object value) {
    return xmlSchemaTypeName;
  }

  protected abstract void writeValueText(
    XmlWriter out,
    Object value);

  public String getEsriFieldTypeName() {
    return esriFieldTypeName;
  }

  public String getXmlSchemaTypeName() {
    return xmlSchemaTypeName;
  }

  public DataType getDataType() {
    return dataType;
  }

}
