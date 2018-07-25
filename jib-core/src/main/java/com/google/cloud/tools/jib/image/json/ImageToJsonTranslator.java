/*
 * Copyright 2017 Google LLC. All rights reserved.
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
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSortedMap;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
   * Converts a list of {@link Port}s to the corresponding container config format for exposed ports
   * (e.g. {@code Port(1000, Protocol.TCP)} -> {@code {"1000/tcp":{}}}).
   *
   * @param exposedPorts the list of {@link Port}s to translate, or {@code null}
   * @return a sorted map with the string representation of the ports as keys and empty maps as
   *     values, or {@code null} if {@code exposedPorts} is {@code null}
   */
  @VisibleForTesting
  @Nullable
  static Map<String, Map<?, ?>> portListToMap(@Nullable List<Port> exposedPorts) {
    if (exposedPorts == null) {
      return null;
    }
    ImmutableSortedMap.Builder<String, Map<?, ?>> result =
        new ImmutableSortedMap.Builder<>(String::compareTo);
    for (Port port : exposedPorts) {
      result.put(port.getPort() + "/" + port.getProtocol(), Collections.emptyMap());
    }
    return result.build();
  }

  private final Image<CachedLayer> image;

  /**
   * Instantiate with an {@link Image}.
   *
   * @param image the image to translate.
   */
  public ImageToJsonTranslator(Image<CachedLayer> image) {
    this.image = image;
  }

  /**
   * Gets the container configuration as a {@link Blob}.
   *
   * @return the container configuration {@link Blob}.
   */
  public Blob getContainerConfigurationBlob() {
    // Set up the JSON template.
    ContainerConfigurationTemplate template = new ContainerConfigurationTemplate();

    // Adds the layer diff IDs.
    for (CachedLayer layer : image.getLayers()) {
      template.addLayerDiffId(layer.getDiffId());
    }

    // Sets the creation time. Instant#toString() returns an ISO-8601 formatted string.
    template.setCreated(image.getCreated() == null ? null : image.getCreated().toString());

    // Adds the environment variables.
    template.setContainerEnvironment(image.getEnvironment());

    // Sets the entrypoint.
    template.setContainerEntrypoint(image.getEntrypoint());

    // Sets the main method arguments.
    template.setContainerCmd(image.getJavaArguments());

    // Sets the exposed ports.
    template.setContainerExposedPorts(portListToMap(image.getExposedPorts()));

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
      for (CachedLayer layer : image.getLayers()) {
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
