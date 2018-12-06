package com.google.cloud.tools.jib.plugins.common;

import java.util.Optional;

/** Provides inferred auth information. */
public interface InferredAuthProvider {

  Optional<AuthProperty> getAuth(String registry) throws InferredAuthRetrievalException;
}
