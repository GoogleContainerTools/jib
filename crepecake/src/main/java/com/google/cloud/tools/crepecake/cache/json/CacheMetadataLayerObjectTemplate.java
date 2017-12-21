/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.cache.json;

import com.google.cloud.tools.crepecake.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.crepecake.cache.CachedLayerType;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.Layer;
import com.google.cloud.tools.crepecake.json.JsonTemplate;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Inner JSON template for storing metadata about a layer in the cache as part of {@link
 * CacheMetadataTemplate}.
 *
 * @see CacheMetadataTemplate for example
 */
public class CacheMetadataLayerObjectTemplate extends JsonTemplate {

  /** The type of layer. */
  private CachedLayerType type;

  /** The reference to the layer. */
  private final ReferenceObject reference = new ReferenceObject();

  /**
   * A list of image tags where the layer should exist at (i.e. the layer was pushed to the
   * repository for that image).
   */
  private List<String> existsOn = new ArrayList<>();

  /** Additional properties for the layer. */
  @Nullable private PropertiesObject properties;

  /**
   * The reference for a layer consists of its size (in bytes), digest, and diff ID.
   *
   * @see Layer for details
   */
  private static class ReferenceObject extends JsonTemplate {

    private long size;
    private DescriptorDigest digest;
    private DescriptorDigest diffId;
  }

  /** Extra properties for a layer. Only valid for application layers. */
  private static class PropertiesObject extends JsonTemplate {

    /** The paths to the source directories that the layer was constructed from. */
    private List<String> sourceDirectories = new ArrayList<>();

    /** The last time the layer was constructed. */
    private long lastModifiedTime;
  }

  public CachedLayerType getType() {
    return type;
  }

  public long getSize() {
    return reference.size;
  }

  public DescriptorDigest getDigest() {
    return reference.digest;
  }

  public DescriptorDigest getDiffId() {
    return reference.diffId;
  }

  public List<String> getExistsOn() {
    return existsOn;
  }

  public List<String> getSourceDirectories() throws CacheMetadataCorruptedException {
    return getPropertiesNonnull().sourceDirectories;
  }

  public long getLastModifiedTime() throws CacheMetadataCorruptedException {
    return getPropertiesNonnull().lastModifiedTime;
  }

  public CacheMetadataLayerObjectTemplate setType(CachedLayerType type) {
    this.type = type;
    return this;
  }

  public CacheMetadataLayerObjectTemplate setSize(long size) {
    reference.size = size;
    return this;
  }

  public CacheMetadataLayerObjectTemplate setDigest(DescriptorDigest digest) {
    reference.digest = digest;
    return this;
  }

  public CacheMetadataLayerObjectTemplate setDiffId(DescriptorDigest diffId) {
    reference.diffId = diffId;
    return this;
  }

  public CacheMetadataLayerObjectTemplate setExistsOn(List<String> existsOn) {
    this.existsOn = existsOn;
    return this;
  }

  public CacheMetadataLayerObjectTemplate setSourceDirectories(List<String> sourceDirectories) {
    checkTypeValidForProperties();
    createPropertiesIfNull();
    assert properties != null;
    properties.sourceDirectories = sourceDirectories;
    return this;
  }

  public CacheMetadataLayerObjectTemplate setLastModifiedTime(long lastModifiedTime) {
    checkTypeValidForProperties();
    createPropertiesIfNull();
    assert properties != null;
    properties.lastModifiedTime = lastModifiedTime;
    return this;
  }

  /** Returns properties if non-null; otherwise throws the appropriate exception. */
  private PropertiesObject getPropertiesNonNull() throws CacheMetadataCorruptedException {
    checkTypeValidForProperties();
    if (properties == null) {
      throw new CacheMetadataCorruptedException("Properties not found for application layer type");
    }
    return properties;
  }

  /** @throws IllegalStateException if is non-application layer type */
  private void checkTypeValidForProperties() {
    if (type != CachedLayerType.DEPENDENCIES
        && type != CachedLayerType.RESOURCES
        && type != CachedLayerType.CLASSES) {
      throw new IllegalStateException(
          "Properties is not a valid field for non-application layer type");
    }
  }

  /** Instantiates {@link #properties} if it's {@code null}. */
  private void createPropertiesIfNull() {
    if (properties == null) {
      properties = new PropertiesObject();
    }
  }
}
