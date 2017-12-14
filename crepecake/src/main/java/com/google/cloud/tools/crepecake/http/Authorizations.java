package com.google.cloud.tools.crepecake.http;

/** Static initializers for {@link Authorization}. */
public abstract class Authorizations {

  public static Authorization withBearerToken(String token) {
    return new Authorization("Bearer", token);
  }

  public static Authorization withBasicToken(String token) {
    return new Authorization("Basic", token);
  }
}
