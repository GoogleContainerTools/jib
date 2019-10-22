package com.google.cloud.tools.jib.http;

/** Helper to expose package-private methods. */
public class RequestWrapper {

  private final Request request;

  public RequestWrapper(Request request) {
    this.request = request;
  }

  public int getHttpTimeout() {
    return request.getHttpTimeout();
  }
}
