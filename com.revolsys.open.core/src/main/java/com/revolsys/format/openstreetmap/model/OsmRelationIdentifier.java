package com.revolsys.format.openstreetmap.model;

import com.revolsys.identifier.SingleIdentifier;

public class OsmRelationIdentifier extends SingleIdentifier {

  public OsmRelationIdentifier(final long id) {
    super(id);
  }

  @Override
  public boolean equals(final Object other) {
    if (other instanceof OsmRelationIdentifier) {
      return super.equals(other);
    } else {
      return false;
    }
  }
}
