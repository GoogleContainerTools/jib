package com.google.cloud.tools.jib.plugins.common;

import java.util.Optional;

public interface InferredAuthProvider {
  Optional<AuthProperty> inferredAuth(String registry) throws InferredAuthException;
}
