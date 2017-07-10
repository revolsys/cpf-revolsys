package com.revolsys.oracle.recordstore.field;

import com.revolsys.jdbc.field.JdbcFieldAdder;
import com.revolsys.jdbc.io.AbstractJdbcRecordStore;
import com.revolsys.jdbc.io.JdbcRecordDefinition;
import com.revolsys.record.schema.FieldDefinition;

public class OracleBlobFieldAdder extends JdbcFieldAdder {

  public OracleBlobFieldAdder() {
  }

  @Override
  public FieldDefinition addField(final AbstractJdbcRecordStore recordStore,
    final JdbcRecordDefinition recordDefinition, final String dbName, final String name,
    final String dataTypeName, final int sqlType, final int length, final int scale,
    final boolean required, final String description) {
    final OracleJdbcBlobFieldDefinition attribute = new OracleJdbcBlobFieldDefinition(dbName, name,
      sqlType, length, required, description);
    recordDefinition.addField(attribute);
    return attribute;
  }

}
