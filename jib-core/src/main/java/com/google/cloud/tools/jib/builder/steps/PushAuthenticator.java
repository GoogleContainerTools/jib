package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.registry.RegistryAuthenticator;
import com.google.cloud.tools.jib.registry.RegistryCredentialsNotSentException;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;

public class PushAuthenticator {

  public static PushAuthenticator create(BuildContext buildContext)
      throws CredentialRetrievalException, IOException, RegistryException {
    Credential credential =
        RegistryCredentialRetriever.getTargetImageCredential(buildContext).orElse(null);

    Optional<RegistryAuthenticator> authenticator =
        buildContext
            .newTargetImageRegistryClientFactory()
            .newRegistryClient()
            .getRegistryAuthenticator();

    if (authenticator.isPresent()) {
      Authorization tokenAuthorization = authenticator.get().authenticatePush(credential);
      return new PushAuthenticator(buildContext, credential, tokenAuthorization);
    }

    if (credential != null && !credential.isOAuth2RefreshToken()) {
      Authorization basicAuthorization =
          Authorization.fromBasicCredentials(credential.getUsername(), credential.getPassword());
      return new PushAuthenticator(buildContext, credential, basicAuthorization);
    }

    return new PushAuthenticator(buildContext, credential, null);
  }

  private final BuildContext buildContext;
  @Nullable private final Credential credential;

  @Nullable private Authorization authorization;

  private PushAuthenticator(
      BuildContext buildContext,
      @Nullable Credential credential,
      @Nullable Authorization authorization) {
    this.buildContext = buildContext;
    this.credential = credential;
    this.authorization = authorization;
  }

  public synchronized Optional<Authorization> getAuthorization() {
    return Optional.ofNullable(authorization);
  }

  public synchronized void refreshBearerToken(@Nullable String wwwAuthenticate)
      throws RegistryAuthenticationFailedException, RegistryCredentialsNotSentException {
    Optional<RegistryAuthenticator> authenticator =
        buildContext
            .newTargetImageRegistryClientFactory()
            .newRegistryClient()
            .getRegistryAuthenticator(wwwAuthenticate);
    if (!authenticator.isPresent()) {
      String registry = buildContext.getTargetImageConfiguration().getImageRegistry();
      String repository = buildContext.getTargetImageConfiguration().getImageRepository();
      throw new RegistryAuthenticationFailedException(
          registry, repository, "server returned 'WWW-Authenticate: Basic' HTTP header");
    }
    authorization = authenticator.get().authenticatePush(credential);
  }
}
