package com.google.cloud.tools.jib.plugins.common;

import javax.annotation.Nullable;

/** Holds a username and password property. */
public interface AuthProperty {

  @Nullable
  String getUsername();

  @Nullable
  String getPassword();
}
