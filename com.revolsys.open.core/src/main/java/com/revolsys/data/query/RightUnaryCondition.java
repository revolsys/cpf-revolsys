package com.revolsys.data.query;

import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.List;

import com.revolsys.data.equals.EqualsRegistry;
import com.revolsys.data.record.schema.RecordStore;

public class RightUnaryCondition extends Condition {

  private final QueryValue value;

  private final String operator;

  public RightUnaryCondition(final QueryValue value, final String operator) {
    this.operator = operator;
    this.value = value;
  }

  @Override
  public void appendDefaultSql(Query query,
    final RecordStore recordStore, final StringBuffer buffer) {
    value.appendSql(query, recordStore, buffer);
    buffer.append(" ");
    buffer.append(operator);
  }

  @Override
  public int appendParameters(final int index, final PreparedStatement statement) {
    return value.appendParameters(index, statement);
  }

  @Override
  public RightUnaryCondition clone() {
    return new RightUnaryCondition(value.clone(), operator);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof RightUnaryCondition) {
      final RightUnaryCondition condition = (RightUnaryCondition)obj;
      if (EqualsRegistry.equal(condition.getValue(), this.getValue())) {
        if (EqualsRegistry.equal(condition.getOperator(), this.getOperator())) {
          return true;
        }
      }
    }
    return false;
  }

  public String getOperator() {
    return operator;
  }

  @Override
  public List<QueryValue> getQueryValues() {
    return Collections.singletonList(value);
  }

  public QueryValue getValue() {
    return value;
  }

  @Override
  public String toString() {
    return getValue() + " " + getOperator();
  }
}