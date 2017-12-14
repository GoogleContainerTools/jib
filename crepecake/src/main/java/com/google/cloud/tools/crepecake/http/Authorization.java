package com.google.cloud.tools.crepecake.http;

import java.util.Arrays;
import java.util.List;

/**
 * Holds the credentials for an HTTP {@code Authorization} header.
 *
 * <p>The HTTP {@code Authorization} header is in the format:
 *
 * <pre>{@code Authorization: <scheme> <token>}</pre>
 */
public class Authorization {

  private final String scheme;
  private final String token;

  Authorization(String scheme, String token) {
    this.scheme = scheme;
    this.token = token;
  }

  /** @return the {@code Authorization} header as a two-element list: [scheme, token] */
  public List<String> asList() {
    return Arrays.asList(scheme, token);
  }
}
