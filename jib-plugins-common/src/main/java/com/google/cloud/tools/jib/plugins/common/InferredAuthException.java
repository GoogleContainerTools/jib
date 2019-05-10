package com.google.cloud.tools.jib.plugins.common;

/**
 * Indicates that the {@link InferredAuthProvider} failed encountered a failure while trying to
 * determine auth credentials (not thrown for missing).
 */
public class InferredAuthException extends Exception {
  public InferredAuthException(String message) {
    super(message);
  }
}
