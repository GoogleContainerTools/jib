/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.image.json;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.configuration.DockerHealthCheck;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Translates an {@link Image} into a manifest or container configuration JSON BLOB.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * ImageToJsonTranslator translator = new ImageToJsonTranslator(image);
 * Blob containerConfigurationBlob = translator.getContainerConfigurationBlob();
 * BlobDescriptor containerConfigurationBlobDescriptor = blob.writeTo(outputStream);
 * Blob manifestBlob = translator.getManifestBlob(containerConfigurationBlobDescriptor);
 * }</pre>
 */
public class ImageToJsonTranslator {

  /**
   * Converts a set of {@link Port}s to the corresponding container config format for exposed ports
   * (e.g. {@code Port(1000, Protocol.TCP)} -> {@code {"1000/tcp":{}}}).
   *
   * @param exposedPorts the set of {@link Port}s to translate, or {@code null}
   * @return a sorted map with the string representation of the ports as keys and empty maps as
   *     values, or {@code null} if {@code exposedPorts} is {@code null}
   */
  @VisibleForTesting
  @Nullable
  static Map<String, Map<?, ?>> portSetToMap(@Nullable Set<Port> exposedPorts) {
    return setToMap(exposedPorts, port -> port.getPort() + "/" + port.getProtocol());
  }

  /**
   * Converts a set of {@link AbsoluteUnixPath}s to the corresponding container config format for
   * volumes (e.g. {@code AbsoluteUnixPath().get("/var/log/my-app-logs")} -> {@code
   * {"/var/log/my-app-logs":{}}}).
   *
   * @param volumes the set of {@link AbsoluteUnixPath}s to translate, or {@code null}
   * @return a sorted map with the string representation of the ports as keys and empty maps as
   *     values, or {@code null} if {@code exposedPorts} is {@code null}
   */
  @VisibleForTesting
  @Nullable
  static Map<String, Map<?, ?>> volumesSetToMap(@Nullable Set<AbsoluteUnixPath> volumes) {
    return setToMap(volumes, AbsoluteUnixPath::toString);
  }

  /**
   * Converts the map of environment variables to a list with items in the format "NAME=VALUE".
   *
   * @return the list
   */
  @VisibleForTesting
  @Nullable
  static ImmutableList<String> environmentMapToList(@Nullable Map<String, String> environment) {
    if (environment == null) {
      return null;
    }
    Preconditions.checkArgument(
        environment.keySet().stream().noneMatch(key -> key.contains("=")),
        "Illegal environment variable: name cannot contain '='");
    return environment
        .entrySet()
        .stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(ImmutableList.toImmutableList());
  }

  /**
   * Turns a set into a sorted map where each element of the set is mapped to an entry composed by
   * the key generated with {@code Function<E, String> elementMapper} and an empty map as value.
   *
   * <p>This method is needed because the volume object is a direct JSON serialization of the Go
   * type map[string]struct{} and is represented in JSON as an object mapping its keys to an empty
   * object.
   *
   * <p>Further read at the <a
   * href="https://github.com/opencontainers/image-spec/blob/master/config.md">image specs.</a>
   *
   * @param set the set of elements to be transformed
   * @param keyMapper the mapper function to generate keys to the map
   * @param <E> the type of the elements from the set
   * @return an map
   */
  @Nullable
  private static <E> Map<String, Map<?, ?>> setToMap(
      @Nullable Set<E> set, Function<E, String> keyMapper) {
    if (set == null) {
      return null;
    }

    return set.stream()
        .collect(
            ImmutableSortedMap.toImmutableSortedMap(
                String::compareTo, keyMapper, ignored -> Collections.emptyMap()));
  }

  private final Image<Layer> image;

  /**
   * Instantiate with an {@link Image}.
   *
   * @param image the image to translate
   */
  public ImageToJsonTranslator(Image<Layer> image) {
    this.image = image;
  }

  /**
   * Gets the container configuration as a {@link Blob}.
   *
   * @return the container configuration {@link Blob}
   */
  public Blob getContainerConfigurationBlob() {
    // Set up the JSON template.
    ContainerConfigurationTemplate template = new ContainerConfigurationTemplate();

    // Adds the layer diff IDs.
    for (Layer layer : image.getLayers()) {
      template.addLayerDiffId(layer.getDiffId());
    }

    // Adds the history.
    for (HistoryEntry historyObject : image.getHistory()) {
      template.addHistoryEntry(historyObject);
    }

    template.setCreated(image.getCreated() == null ? null : image.getCreated().toString());
    template.setContainerEnvironment(environmentMapToList(image.getEnvironment()));
    template.setContainerEntrypoint(image.getEntrypoint());
    template.setContainerCmd(image.getProgramArguments());
    template.setContainerExposedPorts(portSetToMap(image.getExposedPorts()));
    template.setContainerVolumes(volumesSetToMap(image.getVolumes()));
    template.setContainerLabels(image.getLabels());
    template.setContainerWorkingDir(image.getWorkingDirectory());
    template.setContainerUser(image.getUser());

    // Ignore healthcheck if not Docker/command is empty
    DockerHealthCheck healthCheck = image.getHealthCheck();
    if (image.getImageFormat() == V22ManifestTemplate.class && healthCheck != null) {
      template.setContainerHealthCheckTest(healthCheck.getCommand());
      healthCheck
          .getInterval()
          .ifPresent(interval -> template.setContainerHealthCheckInterval(interval.toNanos()));
      healthCheck
          .getTimeout()
          .ifPresent(timeout -> template.setContainerHealthCheckTimeout(timeout.toNanos()));
      healthCheck
          .getStartPeriod()
          .ifPresent(
              startPeriod -> template.setContainerHealthCheckStartPeriod(startPeriod.toNanos()));
      template.setContainerHealthCheckRetries(healthCheck.getRetries().orElse(null));
    }

    // Serializes into JSON.
    return JsonTemplateMapper.toBlob(template);
  }

  /**
   * Gets the manifest as a JSON template. The {@code containerConfigurationBlobDescriptor} must be
   * the [@link BlobDescriptor} obtained by writing out the container configuration {@link Blob}
   * returned from {@link #getContainerConfigurationBlob()}.
   *
   * @param <T> child type of {@link BuildableManifestTemplate}.
   * @param manifestTemplateClass the JSON template to translate the image to.
   * @param containerConfigurationBlobDescriptor the container configuration descriptor.
   * @return the image contents serialized as JSON.
   */
  public <T extends BuildableManifestTemplate> T getManifestTemplate(
      Class<T> manifestTemplateClass, BlobDescriptor containerConfigurationBlobDescriptor) {
    try {
      // Set up the JSON template.
      T template = manifestTemplateClass.getDeclaredConstructor().newInstance();

      // Adds the container configuration reference.
      DescriptorDigest containerConfigurationDigest =
          containerConfigurationBlobDescriptor.getDigest();
      long containerConfigurationSize = containerConfigurationBlobDescriptor.getSize();
      template.setContainerConfiguration(containerConfigurationSize, containerConfigurationDigest);

      // Adds the layers.
      for (Layer layer : image.getLayers()) {
        template.addLayer(
            layer.getBlobDescriptor().getSize(), layer.getBlobDescriptor().getDigest());
      }

      // Serializes into JSON.
      return template;

    } catch (InstantiationException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException ex) {
      throw new IllegalArgumentException(manifestTemplateClass + " cannot be instantiated", ex);
    }
  }
}
