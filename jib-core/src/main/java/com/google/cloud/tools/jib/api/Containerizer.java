/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.builder.steps.BuildResult;
import com.google.cloud.tools.jib.builder.steps.StepsRunner;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.filesystem.XdgDirectories;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Configures how to containerize. */
public class Containerizer {

  /**
   * The default directory for caching the base image layers, in {@code [user cache
   * home]/google-cloud-tools-java/jib}.
   */
  public static final Path DEFAULT_BASE_CACHE_DIRECTORY = XdgDirectories.getCacheHome();

  public static final String DEFAULT_APPLICATION_CACHE_DIRECTORY_NAME =
      "jib-core-application-layers-cache";

  private static final String DEFAULT_TOOL_NAME = "jib-core";
  private static final String DEFAULT_TOOL_VERSION =
      Containerizer.class.getPackage().getImplementationVersion();

  private static final String DESCRIPTION_FOR_DOCKER_REGISTRY = "Building and pushing image";
  private static final String DESCRIPTION_FOR_DOCKER_DAEMON = "Building image to Docker daemon";
  private static final String DESCRIPTION_FOR_TARBALL = "Building image tarball";

  /**
   * Gets a new {@link Containerizer} that containerizes to a container registry.
   *
   * @param registryImage the {@link RegistryImage} that defines target container registry and
   *     credentials
   * @return a new {@link Containerizer}
   */
  public static Containerizer to(RegistryImage registryImage) {
    ImageConfiguration imageConfiguration =
        ImageConfiguration.builder(registryImage.getImageReference())
            .setCredentialRetrievers(registryImage.getCredentialRetrievers())
            .build();

    Function<BuildContext, StepsRunner> stepsRunnerFactory =
        buildContext -> StepsRunner.begin(buildContext).registryPushSteps();

    return new Containerizer(
        DESCRIPTION_FOR_DOCKER_REGISTRY, imageConfiguration, stepsRunnerFactory, true);
  }

  /**
   * Gets a new {@link Containerizer} that containerizes to a Docker daemon.
   *
   * @param dockerDaemonImage the {@link DockerDaemonImage} that defines target Docker daemon
   * @return a new {@link Containerizer}
   */
  public static Containerizer to(DockerDaemonImage dockerDaemonImage) {
    ImageConfiguration imageConfiguration =
        ImageConfiguration.builder(dockerDaemonImage.getImageReference()).build();

    DockerClient dockerClient =
        new DockerClient(
            dockerDaemonImage.getDockerExecutable(), dockerDaemonImage.getDockerEnvironment());
    Function<BuildContext, StepsRunner> stepsRunnerFactory =
        buildContext -> StepsRunner.begin(buildContext).dockerLoadSteps(dockerClient);

    return new Containerizer(
        DESCRIPTION_FOR_DOCKER_DAEMON, imageConfiguration, stepsRunnerFactory, false);
  }

  /**
   * Gets a new {@link Containerizer} that containerizes to a tarball archive.
   *
   * @param tarImage the {@link TarImage} that defines target output file
   * @return a new {@link Containerizer}
   */
  public static Containerizer to(TarImage tarImage) {
    Optional<ImageReference> imageReference = tarImage.getImageReference();
    if (!imageReference.isPresent()) {
      throw new IllegalArgumentException(
          "Image name must be set when building a TarImage; use TarImage#named(...) to set the name"
              + " of the target image");
    }

    ImageConfiguration imageConfiguration =
        ImageConfiguration.builder(imageReference.get()).build();

    Function<BuildContext, StepsRunner> stepsRunnerFactory =
        buildContext -> StepsRunner.begin(buildContext).tarBuildSteps(tarImage.getPath());

    return new Containerizer(
        DESCRIPTION_FOR_TARBALL, imageConfiguration, stepsRunnerFactory, false);
  }

  private final String description;
  private final ImageConfiguration imageConfiguration;
  private final Function<BuildContext, StepsRunner> stepsRunnerFactory;
  private final boolean mustBeOnline;
  private final Set<String> additionalTags = new HashSet<>();
  private final EventHandlers.Builder eventHandlersBuilder = EventHandlers.builder();

  @Nullable private ExecutorService executorService;
  private Path baseImageLayersCacheDirectory = DEFAULT_BASE_CACHE_DIRECTORY;
  @Nullable private Path applicationLayersCacheDirectory;
  private boolean allowInsecureRegistries = false;
  private boolean offline = false;
  private String toolName = DEFAULT_TOOL_NAME;
  @Nullable private String toolVersion = DEFAULT_TOOL_VERSION;
  private boolean alwaysCacheBaseImage = false;
  private ListMultimap<String, String> registryMirrors = ArrayListMultimap.create();
  private boolean enablePlatformTags = false;

  /** Instantiate with {@link #to}. */
  private Containerizer(
      String description,
      ImageConfiguration imageConfiguration,
      Function<BuildContext, StepsRunner> stepsRunnerFactory,
      boolean mustBeOnline) {
    this.description = description;
    this.imageConfiguration = imageConfiguration;
    this.stepsRunnerFactory = stepsRunnerFactory;
    this.mustBeOnline = mustBeOnline;
  }

  /**
   * Adds an additional tag to tag the target image with. For example, the following would
   * containerize to both {@code gcr.io/my-project/my-image:tag} and {@code
   * gcr.io/my-project/my-image:tag2}:
   *
   * <pre>{@code
   * Containerizer.to(RegistryImage.named("gcr.io/my-project/my-image:tag")).withAdditionalTag("tag2");
   * }</pre>
   *
   * @param tag the additional tag to push to
   * @return this
   */
  public Containerizer withAdditionalTag(String tag) {
    Preconditions.checkArgument(ImageReference.isValidTag(tag), "invalid tag '%s'", tag);
    additionalTags.add(tag);
    return this;
  }

  /**
   * Sets the {@link ExecutorService} Jib executes on. Jib, by default, uses {@link
   * Executors#newCachedThreadPool}.
   *
   * @param executorService the {@link ExecutorService}
   * @return this
   */
  public Containerizer setExecutorService(@Nullable ExecutorService executorService) {
    this.executorService = executorService;
    return this;
  }

  /**
   * Sets the directory to use for caching base image layers. This cache can (and should) be shared
   * between multiple images. The default base image layers cache directory is {@code [user cache
   * home]/google-cloud-tools-java/jib} ({@link #DEFAULT_BASE_CACHE_DIRECTORY}. This directory can
   * be the same directory used for {@link #setApplicationLayersCache}.
   *
   * @param cacheDirectory the cache directory
   * @return this
   */
  public Containerizer setBaseImageLayersCache(Path cacheDirectory) {
    baseImageLayersCacheDirectory = cacheDirectory;
    return this;
  }

  /**
   * Sets the directory to use for caching application layers. This cache can be shared between
   * multiple images. If not set, a temporary directory will be used as the application layers
   * cache. This directory can be the same directory used for {@link #setBaseImageLayersCache}.
   *
   * @param cacheDirectory the cache directory
   * @return this
   */
  public Containerizer setApplicationLayersCache(Path cacheDirectory) {
    applicationLayersCacheDirectory = cacheDirectory;
    return this;
  }

  /**
   * Adds the {@code eventConsumer} to handle the {@link JibEvent} with class {@code eventType}. The
   * order in which handlers are added is the order in which they are called when the event is
   * dispatched.
   *
   * <p><b>Note: Implementations of {@code eventConsumer} must be thread-safe.</b>
   *
   * @param eventType the event type that {@code eventConsumer} should handle
   * @param eventConsumer the event handler
   * @param <E> the type of {@code eventType}
   * @return this
   */
  public <E extends JibEvent> Containerizer addEventHandler(
      Class<E> eventType, Consumer<? super E> eventConsumer) {
    eventHandlersBuilder.add(eventType, eventConsumer);
    return this;
  }

  /**
   * Adds the {@code eventConsumer} to handle all {@link JibEvent} types. See {@link
   * #addEventHandler(Class, Consumer)} for more details.
   *
   * @param eventConsumer the event handler
   * @return this
   */
  public Containerizer addEventHandler(Consumer<JibEvent> eventConsumer) {
    eventHandlersBuilder.add(JibEvent.class, eventConsumer);
    return this;
  }

  /**
   * Sets whether or not to allow communication over HTTP/insecure HTTPS.
   *
   * @param allowInsecureRegistries if {@code true}, insecure connections will be allowed
   * @return this
   */
  public Containerizer setAllowInsecureRegistries(boolean allowInsecureRegistries) {
    this.allowInsecureRegistries = allowInsecureRegistries;
    return this;
  }

  /**
   * Sets whether or not to run the build in offline mode. In offline mode, the base image is
   * retrieved from the cache instead of pulled from a registry, and the build will fail if the base
   * image is not in the cache or if the target is an image registry.
   *
   * @param offline if {@code true}, the build will run in offline mode
   * @return this
   */
  public Containerizer setOfflineMode(boolean offline) {
    if (mustBeOnline && offline) {
      throw new IllegalStateException("Cannot build to a container registry in offline mode");
    }
    this.offline = offline;
    return this;
  }

  /**
   * Sets the name of the tool that is using Jib Core. The tool name is sent as part of the {@code
   * User-Agent} in registry requests and set as the {@code created_by} in the container layer
   * history. Defaults to {@code jib-core}.
   *
   * @param toolName the name of the tool using this library
   * @return this
   */
  public Containerizer setToolName(String toolName) {
    this.toolName = toolName;
    return this;
  }

  /**
   * Sets the version of the tool that is using Jib Core. The tool version is sent as part of the
   * {@code User-Agent} in registry requests and set as the {@code created_by} in the container
   * layer history. Defaults to the current version of jib-core.
   *
   * @param toolVersion the name of the tool using this library
   * @return this
   */
  public Containerizer setToolVersion(@Nullable String toolVersion) {
    this.toolVersion = toolVersion;
    return this;
  }

  /**
   * Controls the optimization which skips downloading base image layers that exist in a target
   * registry. If the user does not set this property, then read as false.
   *
   * @param alwaysCacheBaseImage if {@code true}, base image layers are always pulled and cached. If
   *     {@code false}, base image layers will not be pulled/cached if they already exist on the
   *     target registry.
   * @return this
   */
  public Containerizer setAlwaysCacheBaseImage(boolean alwaysCacheBaseImage) {
    this.alwaysCacheBaseImage = alwaysCacheBaseImage;
    return this;
  }

  /**
   * Adds mirrors for a base image registry. Jib will try its mirrors in the given order before
   * finally trying the registry.
   *
   * @param registry base image registry for which mirrors are configured
   * @param mirrors a list of mirrors, where each element is in the form of {@code host[:port]}
   * @return this
   */
  public Containerizer addRegistryMirrors(String registry, List<String> mirrors) {
    registryMirrors.putAll(registry, mirrors);
    return this;
  }

  /**
   * Enables adding platform tags to images.
   *
   * @param enablePlatformTags if {@code true}, adds platform tags to images. If {@code false}
   *     images are not tagged with platform tags.
   * @return this
   */
  public Containerizer setEnablePlatformTags(boolean enablePlatformTags) {
    this.enablePlatformTags = enablePlatformTags;
    return this;
  }

  Set<String> getAdditionalTags() {
    return ImmutableSet.copyOf(additionalTags);
  }

  ListMultimap<String, String> getRegistryMirrors() {
    return ImmutableListMultimap.copyOf(registryMirrors);
  }

  Optional<ExecutorService> getExecutorService() {
    return Optional.ofNullable(executorService);
  }

  Path getBaseImageLayersCacheDirectory() {
    return baseImageLayersCacheDirectory;
  }

  Path getApplicationLayersCacheDirectory() throws CacheDirectoryCreationException {
    if (applicationLayersCacheDirectory == null) {
      // Create a directory in temp if application layers cache directory is not set.
      try {
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir"));
        applicationLayersCacheDirectory = tmp.resolve(DEFAULT_APPLICATION_CACHE_DIRECTORY_NAME);
        Files.createDirectories(applicationLayersCacheDirectory);
      } catch (IOException ex) {
        throw new CacheDirectoryCreationException(ex);
      }
    }
    return applicationLayersCacheDirectory;
  }

  EventHandlers buildEventHandlers() {
    return eventHandlersBuilder.build();
  }

  boolean getAllowInsecureRegistries() {
    return allowInsecureRegistries;
  }

  boolean isOfflineMode() {
    return offline;
  }

  String getToolName() {
    return toolName;
  }

  @Nullable
  String getToolVersion() {
    return toolVersion;
  }

  boolean getAlwaysCacheBaseImage() {
    return alwaysCacheBaseImage;
  }

  boolean getEnablePlatformTags() {
    return enablePlatformTags;
  }

  String getDescription() {
    return description;
  }

  ImageConfiguration getImageConfiguration() {
    return imageConfiguration;
  }

  BuildResult run(BuildContext buildContext) throws ExecutionException, InterruptedException {
    return stepsRunnerFactory.apply(buildContext).run();
  }
}
