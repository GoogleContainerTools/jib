/*
 * Copyright 2018 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.maven;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.BuildImageSteps;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.frontend.HelpfulMessageBuilder;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.settings.Server;

/** Builds a container image. */
@Mojo(name = "build", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildImageMojo extends JibPluginConfiguration {

  /** Enumeration of {@link BuildableManifestTemplate}s. */
  public enum ImageFormat {
    Docker(V22ManifestTemplate.class),
    OCI(OCIManifestTemplate.class);

    private final Class<? extends BuildableManifestTemplate> manifestTemplateClass;

    ImageFormat(Class<? extends BuildableManifestTemplate> manifestTemplateClass) {
      this.manifestTemplateClass = manifestTemplateClass;
    }

    private Class<? extends BuildableManifestTemplate> getManifestTemplateClass() {
      return manifestTemplateClass;
    }
  }

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-maven-plugin";

  private static final HelpfulMessageBuilder helpfulMessageBuilder =
      new HelpfulMessageBuilder("Build image failed");

  @Nullable
  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    // These @Nullable parameters should never be null.
    Preconditions.checkNotNull(project);
    Preconditions.checkNotNull(session);
    Preconditions.checkNotNull(repository);
    Preconditions.checkNotNull(imageFormat);

    validateParameters();

    ProjectProperties projectProperties = new ProjectProperties(project, getLog());
    String inferredMainClass = projectProperties.getMainClass(mainClass);

    SourceFilesConfiguration sourceFilesConfiguration =
        projectProperties.getSourceFilesConfiguration();

    // Parses 'from' into image reference.
    ImageReference baseImage = getBaseImageReference();

    // Checks Maven settings for registry credentials.
    session.getSettings().getServer(baseImage.getRegistry());
    Map<String, Authorization> registryCredentials = new HashMap<>(2);
    // Retrieves credentials for the base image registry.
    Authorization baseImageRegistryCredentials =
        getRegistryCredentialsFromSettings(baseImage.getRegistry());
    if (baseImageRegistryCredentials != null) {
      registryCredentials.put(baseImage.getRegistry(), baseImageRegistryCredentials);
    }
    // Retrieves credentials for the target registry.
    Authorization targetRegistryCredentials = getRegistryCredentialsFromSettings(registry);
    if (targetRegistryCredentials != null) {
      registryCredentials.put(registry, targetRegistryCredentials);
    }
    RegistryCredentials mavenSettingsCredentials =
        RegistryCredentials.from("Maven settings", registryCredentials);

    ImageReference targetImageReference = ImageReference.of(registry, repository, tag);
    ImageFormat imageFormatToEnum = ImageFormat.valueOf(imageFormat);
    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(new MavenBuildLogger(getLog()))
            .setBaseImage(baseImage)
            .setTargetImage(targetImageReference)
            .setCredentialHelperNames(credHelpers)
            .setKnownRegistryCredentials(mavenSettingsCredentials)
            .setMainClass(inferredMainClass)
            .setJvmFlags(jvmFlags)
            .setEnvironment(environment)
            .setTargetFormat(imageFormatToEnum.getManifestTemplateClass())
            .build();

    // Uses a directory in the Maven build cache as the Jib cache.
    Path cacheDirectory = Paths.get(project.getBuild().getDirectory(), CACHE_DIRECTORY_NAME);
    if (!Files.exists(cacheDirectory)) {
      try {
        Files.createDirectory(cacheDirectory);

      } catch (IOException ex) {
        throw new MojoExecutionException("Could not create cache directory: " + cacheDirectory, ex);
      }
    }
    Caches.Initializer cachesInitializer = Caches.newInitializer(cacheDirectory);
    if (useOnlyProjectCache) {
      cachesInitializer.setBaseCacheDirectory(cacheDirectory);
    }

    getLog().info("");
    getLog().info("Pushing image as " + targetImageReference);
    getLog().info("");

    // TODO: Instead of disabling logging, have authentication credentials be provided
    // Disables annoying Apache HTTP client logging.
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    buildImage(
        new BuildImageSteps(buildConfiguration, sourceFilesConfiguration, cachesInitializer));

    getLog().info("");
    getLog().info("Built and pushed image as " + targetImageReference);
    getLog().info("");
  }

  @VisibleForTesting
  void buildImage(BuildImageSteps buildImageSteps) throws MojoExecutionException {
    try {
      buildImageSteps.run();

    } catch (CacheMetadataCorruptedException cacheMetadataCorruptedException) {
      throw new MojoExecutionException(
          helpfulMessageBuilder.withSuggestion("run 'mvn clean' to clear the cache"),
          cacheMetadataCorruptedException);

    } catch (ExecutionException executionException) {
      BuildConfiguration buildConfiguration = buildImageSteps.getBuildConfiguration();

      if (executionException.getCause() instanceof HttpHostConnectException) {
        // Failed to connect to registry.
        throw new MojoExecutionException(
            helpfulMessageBuilder.withSuggestion(
                "make sure your Internet is up and that the registry you are pushing to exists"),
            executionException.getCause());

      } else if (executionException.getCause() instanceof RegistryUnauthorizedException) {
        handleRegistryUnauthorizedException(
            (RegistryUnauthorizedException) executionException.getCause(), buildConfiguration);

      } else if (executionException.getCause() instanceof RegistryAuthenticationFailedException
          && executionException.getCause().getCause() instanceof HttpResponseException) {
        handleRegistryUnauthorizedException(
            new RegistryUnauthorizedException(
                buildConfiguration.getTargetRegistry(),
                buildConfiguration.getTargetRepository(),
                (HttpResponseException) executionException.getCause().getCause()),
            buildConfiguration);

      } else if (executionException.getCause() instanceof UnknownHostException) {
        throw new MojoExecutionException(
            helpfulMessageBuilder.withSuggestion(
                "make sure that the registry you configured exists/is spelled properly"),
            executionException.getCause());

      } else {
        throw new MojoExecutionException(
            helpfulMessageBuilder.withNoHelp(), executionException.getCause());
      }

    } catch (InterruptedException | IOException ex) {
      getLog().error(ex);
      // TODO: Add more suggestions for various build failures.
      throw new MojoExecutionException(helpfulMessageBuilder.withNoHelp(), ex);

    } catch (CacheDirectoryNotOwnedException ex) {
      throw new MojoExecutionException(
          helpfulMessageBuilder.withSuggestion(
              "check that '"
                  + ex.getCacheDirectory()
                  + "' is not used by another application or set the `useOnlyProjectCache` "
                  + "configuration"),
          ex);
    }
  }

  /** Attempts to retrieve credentials for {@code registry} from Maven settings. */
  @Nullable
  private Authorization getRegistryCredentialsFromSettings(@Nullable String registry) {
    if (registry == null) {
      return null;
    }
    Preconditions.checkNotNull(session);
    Server registryServerSettings = session.getSettings().getServer(registry);
    if (registryServerSettings == null) {
      return null;
    }
    return Authorizations.withBasicCredentials(
        registryServerSettings.getUsername(), registryServerSettings.getPassword());
  }

  /** Checks validity of plugin parameters. */
  private void validateParameters() throws MojoFailureException {
    // These @Nullable parameters should never be null.
    Preconditions.checkNotNull(repository);
    Preconditions.checkNotNull(imageFormat);

    // Validates 'registry'.
    if (!Strings.isNullOrEmpty(registry) && !ImageReference.isValidRegistry(registry)) {
      getLog().error("Invalid format for 'registry'");
    }
    // Validates 'repository'.
    if (!ImageReference.isValidRepository(repository)) {
      getLog().error("Invalid format for 'repository'");
    }
    // Validates 'tag'.
    if (!Strings.isNullOrEmpty(tag)) {
      if (!ImageReference.isValidTag(tag)) {
        getLog().error("Invalid format for 'tag'");
      }

      // 'tag' must not contain forward slashes.
      if (tag.indexOf('/') >= 0) {
        getLog().error("'tag' cannot contain '/'");
        throw new MojoFailureException("Invalid configuration parameters");
      }
    }
    // Validates 'imageFormat'.
    boolean validFormat = false;
    for (ImageFormat format : ImageFormat.values()) {
      if (imageFormat.equals(format.name())) {
        validFormat = true;
        break;
      }
    }
    if (!validFormat) {
      throw new MojoFailureException(
          "<imageFormat> parameter is configured with value '"
              + imageFormat
              + "', but the only valid configuration options are '"
              + ImageFormat.Docker
              + "' and '"
              + ImageFormat.OCI
              + "'.");
    }
  }

  /** @return the {@link ImageReference} parsed from {@link #from}. */
  private ImageReference getBaseImageReference() throws MojoFailureException {
    Preconditions.checkNotNull(from);

    try {
      return ImageReference.parse(from);

    } catch (InvalidImageReferenceException ex) {
      throw new MojoFailureException("Parameter 'from' is invalid", ex);
    }
  }

  private void handleRegistryUnauthorizedException(
      RegistryUnauthorizedException registryUnauthorizedException,
      BuildConfiguration buildConfiguration)
      throws MojoExecutionException {
    if (registryUnauthorizedException.getHttpResponseException().getStatusCode()
        == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
      // No permissions for registry/repository.
      throw new MojoExecutionException(
          helpfulMessageBuilder.withSuggestion(
              "make sure you have permissions for "
                  + registryUnauthorizedException.getImageReference()),
          registryUnauthorizedException);

    } else if ((buildConfiguration.getCredentialHelperNames() == null
            || buildConfiguration.getCredentialHelperNames().isEmpty())
        && (buildConfiguration.getKnownRegistryCredentials() == null
            || !buildConfiguration
                .getKnownRegistryCredentials()
                .has(registryUnauthorizedException.getRegistry()))) {
      // No credential helpers defined.
      throw new MojoExecutionException(
          helpfulMessageBuilder.withSuggestion(
              "set a credential helper name with the configuration 'credHelpers' or "
                  + "set credentials for '"
                  + registryUnauthorizedException.getRegistry()
                  + "' in your Maven settings"),
          registryUnauthorizedException);

    } else {
      // Credential helper probably was not configured correctly or did not have the necessary
      // credentials.
      throw new MojoExecutionException(
          helpfulMessageBuilder.withSuggestion(
              "make sure your credentials for '"
                  + registryUnauthorizedException.getRegistry()
                  + "' are set up correctly"),
          registryUnauthorizedException);
    }
  }
}
