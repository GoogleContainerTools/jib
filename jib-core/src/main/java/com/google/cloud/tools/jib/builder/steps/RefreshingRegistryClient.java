package com.google.cloud.tools.jib.builder.steps;

import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryAuthenticator;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryCredentialsNotSentException;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;

public class RefreshingRegistryClient {

  private static interface RegistryAction<T> {
    T call(RegistryClient registryClient) throws IOException, RegistryException;
  }

  public static RefreshingRegistryClient create(BuildContext buildContext)
      throws CredentialRetrievalException, IOException, RegistryException {
    Credential credential =
        RegistryCredentialRetriever.getTargetImageCredential(buildContext).orElse(null);

    Optional<RegistryAuthenticator> authenticator =
        buildContext
            .newTargetImageRegistryClientFactory()
            .newRegistryClient()
            .getRegistryAuthenticator();

    Authorization authorization = null;
    if (authenticator.isPresent()) {
      authorization = authenticator.get().authenticatePush(credential);

    } else if (credential != null && !credential.isOAuth2RefreshToken()) {
      authorization =
          Authorization.fromBasicCredentials(credential.getUsername(), credential.getPassword());
    }

    return new RefreshingRegistryClient(buildContext, credential, authorization);
  }

  private final BuildContext buildContext;
  private final Credential credential;

  private AtomicReference<RegistryClient> registryClient;

  RefreshingRegistryClient(
      BuildContext buildContext, Credential credential, Authorization authorization) {
    this.buildContext = buildContext;
    this.credential = credential;
    registryClient.set(
        buildContext
            .newTargetImageRegistryClientFactory()
            .setAuthorization(authorization)
            .newRegistryClient());
  }

  Optional<BlobDescriptor> checkBlob(DescriptorDigest blobDigest)
      throws IOException, RegistryException {
    return run(registryClient -> registryClient.checkBlob(blobDigest));
  }

  DescriptorDigest pushManifest(BuildableManifestTemplate manifestTemplate, String imageTag)
      throws IOException, RegistryException {
    return run(registryClient -> registryClient.pushManifest(manifestTemplate, imageTag));
  }

  boolean pushBlob(
      DescriptorDigest blobDigest,
      Blob blob,
      String sourceRepository,
      Consumer<Long> writtenByteCountListener)
      throws IOException, RegistryException {
    return run(
        registryClient ->
            registryClient.pushBlob(blobDigest, blob, sourceRepository, writtenByteCountListener));
  }

  private <T> T run(RegistryAction<T> action) throws IOException, RegistryException {
    int refreshCount = 0;
    while (true) {
      try {
        return action.call(registryClient.get());

      } catch (RegistryUnauthorizedException ex) {
        int code = ex.getHttpResponseException().getStatusCode();
        if (code != HttpStatusCodes.STATUS_CODE_UNAUTHORIZED || refreshCount++ > 3) {
          throw ex;
        }

        // Because we successfully authenticated with the registry initially, getting 401 here
        // probably means the token was expired.
        String wwwAuthenticate = ex.getHttpResponseException().getHeaders().getAuthenticate();
        refreshBearerToken(wwwAuthenticate);
      }
    }
  }

  private void refreshBearerToken(@Nullable String wwwAuthenticate)
      throws RegistryAuthenticationFailedException, RegistryCredentialsNotSentException {
    if (wwwAuthenticate != null) {
      Optional<RegistryAuthenticator> authenticator =
          buildContext
              .newTargetImageRegistryClientFactory()
              .newRegistryClient()
              .getRegistryAuthenticator(wwwAuthenticate);
      if (authenticator.isPresent()) {
        Authorization authorization = authenticator.get().authenticatePush(credential);
        registryClient.set(
            buildContext
                .newTargetImageRegistryClientFactory()
                .setAuthorization(authorization)
                .newRegistryClient());
      }
    }

    throw new RegistryAuthenticationFailedException(
        buildContext.getTargetImageConfiguration().getImageRegistry(),
        buildContext.getTargetImageConfiguration().getImageRepository(),
        "server did not return 'WWW-Authenticate: Bearer' header; actual: " + wwwAuthenticate);
  }
}
