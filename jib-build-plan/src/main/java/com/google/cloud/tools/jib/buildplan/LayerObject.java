package com.google.cloud.tools.jib.buildplan;

import javax.annotation.concurrent.Immutable;

/** Serves as a base class for the "layers" property in the build plan specification. */
@Immutable
public class LayerObject {

  public static enum TYPE {
    FILE_ENTRIES,
  }

  private final TYPE type;

  public LayerObject(TYPE type) {
    this.type = type;
  }

  public TYPE getType() {
    return type;
  }
}
