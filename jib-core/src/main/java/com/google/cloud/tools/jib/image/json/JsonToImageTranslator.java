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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.configuration.DockerHealthCheck;
import com.google.cloud.tools.jib.image.DigestOnlyLayer;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.ReferenceLayer;
import com.google.cloud.tools.jib.image.ReferenceNoDiffIdLayer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Translates {@link V21ManifestTemplate} and {@link V22ManifestTemplate} into {@link Image}. */
public class JsonToImageTranslator {

  /**
   * Pattern used for parsing information out of exposed port configurations. Only accepts single
   * ports with protocol.
   *
   * <p>Example matches: 100, 1000/tcp, 2000/udp
   */
  private static final Pattern PORT_PATTERN =
      Pattern.compile("(?<portNum>\\d+)(?:/(?<protocol>tcp|udp))?");

  /**
   * Pattern used for parsing environment variables in the format {@code NAME=VALUE}. {@code NAME}
   * should not contain an '='.
   *
   * <p>Example matches: NAME=VALUE, A12345=$$$$$
   */
  @VisibleForTesting
  static final Pattern ENVIRONMENT_PATTERN = Pattern.compile("(?<name>[^=]+)=(?<value>.*)");

  /**
   * Translates {@link V21ManifestTemplate} to {@link Image}.
   *
   * @param manifestTemplate the template containing the image layers.
   * @return the translated {@link Image}.
   * @throws LayerPropertyNotFoundException if adding image layers fails.
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   */
  public static Image toImage(V21ManifestTemplate manifestTemplate)
      throws LayerPropertyNotFoundException, BadContainerConfigurationFormatException {
    Image.Builder imageBuilder = Image.builder(V21ManifestTemplate.class);

    // V21 layers are in reverse order of V22. (The first layer is the latest one.)
    for (DescriptorDigest digest : Lists.reverse(manifestTemplate.getLayerDigests())) {
      imageBuilder.addLayer(new DigestOnlyLayer(digest));
    }

    Optional<ContainerConfigurationTemplate> configuration =
        manifestTemplate.getContainerConfiguration();
    if (configuration.isPresent()) {
      configureBuilderWithContainerConfiguration(imageBuilder, configuration.get());
    }
    return imageBuilder.build();
  }

  /**
   * Translates {@link BuildableManifestTemplate} to {@link Image}. Uses the corresponding {@link
   * ContainerConfigurationTemplate} to get the layer diff IDs.
   *
   * @param manifestTemplate the template containing the image layers.
   * @param containerConfigurationTemplate the template containing the diff IDs and container
   *     configuration properties.
   * @return the translated {@link Image}.
   * @throws LayerCountMismatchException if the manifest and configuration contain conflicting layer
   *     information.
   * @throws LayerPropertyNotFoundException if adding image layers fails.
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   */
  public static Image toImage(
      BuildableManifestTemplate manifestTemplate,
      ContainerConfigurationTemplate containerConfigurationTemplate)
      throws LayerCountMismatchException, LayerPropertyNotFoundException,
          BadContainerConfigurationFormatException {
    List<ReferenceNoDiffIdLayer> layers = new ArrayList<>();
    for (BuildableManifestTemplate.ContentDescriptorTemplate layerObjectTemplate :
        manifestTemplate.getLayers()) {
      if (layerObjectTemplate.getDigest() == null) {
        throw new IllegalArgumentException(
            "All layers in the manifest template must have digest set");
      }

      layers.add(
          new ReferenceNoDiffIdLayer(
              new BlobDescriptor(layerObjectTemplate.getSize(), layerObjectTemplate.getDigest())));
    }

    List<DescriptorDigest> diffIds = containerConfigurationTemplate.getDiffIds();
    if (layers.size() != diffIds.size()) {
      throw new LayerCountMismatchException(
          "Mismatch between image manifest and container configuration");
    }

    Image.Builder imageBuilder = Image.builder(manifestTemplate.getClass());

    for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
      ReferenceNoDiffIdLayer noDiffIdLayer = layers.get(layerIndex);
      DescriptorDigest diffId = diffIds.get(layerIndex);

      imageBuilder.addLayer(new ReferenceLayer(noDiffIdLayer.getBlobDescriptor(), diffId));
    }

    configureBuilderWithContainerConfiguration(imageBuilder, containerConfigurationTemplate);
    return imageBuilder.build();
  }

  private static void configureBuilderWithContainerConfiguration(
      Image.Builder imageBuilder, ContainerConfigurationTemplate containerConfigurationTemplate)
      throws BadContainerConfigurationFormatException {

    containerConfigurationTemplate.getHistory().forEach(imageBuilder::addHistory);

    if (containerConfigurationTemplate.getCreated() != null) {
      try {
        imageBuilder.setCreated(Instant.parse(containerConfigurationTemplate.getCreated()));
      } catch (DateTimeParseException ex) {
        try {
          // TODO: remove when using Java >= 12.
          // See https://github.com/GoogleContainerTools/jib/issues/2428
          imageBuilder.setCreated(
              DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(
                  containerConfigurationTemplate.getCreated(), Instant::from));
        } catch (DateTimeParseException ignored) {
          throw new BadContainerConfigurationFormatException(
              "Invalid image creation time: " + containerConfigurationTemplate.getCreated(), ex);
        }
      }
    }

    if (containerConfigurationTemplate.getArchitecture() != null) {
      imageBuilder.setArchitecture(containerConfigurationTemplate.getArchitecture());
    }
    if (containerConfigurationTemplate.getOs() != null) {
      imageBuilder.setOs(containerConfigurationTemplate.getOs());
    }

    imageBuilder.setEntrypoint(containerConfigurationTemplate.getContainerEntrypoint());
    imageBuilder.setProgramArguments(containerConfigurationTemplate.getContainerCmd());

    List<String> baseHealthCheckCommand = containerConfigurationTemplate.getContainerHealthTest();
    if (baseHealthCheckCommand != null) {
      DockerHealthCheck.Builder builder = DockerHealthCheck.fromCommand(baseHealthCheckCommand);
      if (containerConfigurationTemplate.getContainerHealthInterval() != null) {
        builder.setInterval(
            Duration.ofNanos(containerConfigurationTemplate.getContainerHealthInterval()));
      }
      if (containerConfigurationTemplate.getContainerHealthTimeout() != null) {
        builder.setTimeout(
            Duration.ofNanos(containerConfigurationTemplate.getContainerHealthTimeout()));
      }
      if (containerConfigurationTemplate.getContainerHealthStartPeriod() != null) {
        builder.setStartPeriod(
            Duration.ofNanos(containerConfigurationTemplate.getContainerHealthStartPeriod()));
      }
      if (containerConfigurationTemplate.getContainerHealthRetries() != null) {
        builder.setRetries(containerConfigurationTemplate.getContainerHealthRetries());
      }
      imageBuilder.setHealthCheck(builder.build());
    }

    if (containerConfigurationTemplate.getContainerExposedPorts() != null) {
      imageBuilder.addExposedPorts(
          portMapToSet(containerConfigurationTemplate.getContainerExposedPorts()));
    }

    if (containerConfigurationTemplate.getContainerVolumes() != null) {
      imageBuilder.addVolumes(volumeMapToSet(containerConfigurationTemplate.getContainerVolumes()));
    }

    if (containerConfigurationTemplate.getContainerEnvironment() != null) {
      for (String environmentVariable : containerConfigurationTemplate.getContainerEnvironment()) {
        Matcher matcher = ENVIRONMENT_PATTERN.matcher(environmentVariable);
        if (!matcher.matches()) {
          throw new BadContainerConfigurationFormatException(
              "Invalid environment variable definition: " + environmentVariable);
        }
        imageBuilder.addEnvironmentVariable(matcher.group("name"), matcher.group("value"));
      }
    }

    imageBuilder.addLabels(containerConfigurationTemplate.getContainerLabels());
    imageBuilder.setWorkingDirectory(containerConfigurationTemplate.getContainerWorkingDir());
    imageBuilder.setUser(containerConfigurationTemplate.getContainerUser());
  }

  /**
   * Converts a map of exposed ports as strings to a set of {@link Port}s (e.g. {@code
   * {"1000/tcp":{}}} -> {@code Port(1000, Protocol.TCP)}).
   *
   * @param portMap the map to convert
   * @return a set of {@link Port}s
   */
  @VisibleForTesting
  static ImmutableSet<Port> portMapToSet(@Nullable Map<String, Map<?, ?>> portMap)
      throws BadContainerConfigurationFormatException {
    if (portMap == null) {
      return ImmutableSet.of();
    }
    ImmutableSet.Builder<Port> ports = new ImmutableSet.Builder<>();
    for (Map.Entry<String, Map<?, ?>> entry : portMap.entrySet()) {
      String port = entry.getKey();
      Matcher matcher = PORT_PATTERN.matcher(port);
      if (!matcher.matches()) {
        throw new BadContainerConfigurationFormatException(
            "Invalid port configuration: '" + port + "'.");
      }

      int portNumber = Integer.parseInt(matcher.group("portNum"));
      String protocol = matcher.group("protocol");
      ports.add(Port.parseProtocol(portNumber, protocol));
    }
    return ports.build();
  }

  /**
   * Converts a map of volumes strings to a set of {@link AbsoluteUnixPath}s (e.g. {@code {@code
   * {"/var/log/my-app-logs":{}}} -> AbsoluteUnixPath().get("/var/log/my-app-logs")}).
   *
   * @param volumeMap the map to convert
   * @return a set of {@link AbsoluteUnixPath}s
   */
  @VisibleForTesting
  static ImmutableSet<AbsoluteUnixPath> volumeMapToSet(@Nullable Map<String, Map<?, ?>> volumeMap)
      throws BadContainerConfigurationFormatException {
    if (volumeMap == null) {
      return ImmutableSet.of();
    }

    ImmutableSet.Builder<AbsoluteUnixPath> volumeList = ImmutableSet.builder();
    for (String volume : volumeMap.keySet()) {
      try {
        volumeList.add(AbsoluteUnixPath.get(volume));
      } catch (IllegalArgumentException exception) {
        throw new BadContainerConfigurationFormatException("Invalid volume path: " + volume);
      }
    }

    return volumeList.build();
  }

  private JsonToImageTranslator() {}
}
