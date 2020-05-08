package com.google.cloud.tools.jib.image;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;

public class ManifestDescriptor<T extends BuildableManifestTemplate> {

  private final T manifestTemplate;
  private final DescriptorDigest imageDigest;
//  private final DescriptorDigest imageId;

  public ManifestDescriptor(T manifestTemplate, DescriptorDigest imageDigest/*, DescriptorDigest imageId*/) {
    this.manifestTemplate = manifestTemplate;
    this.imageDigest = imageDigest;
//    this.imageId = imageId;
  }

  /**
   * Gets the manifest.
   *
   * @return the manifest
   */
  public T getManifestTemplate() {
    return manifestTemplate;
  }

  /**
   * Gets the image digest.
   *
   * @return the digest
   */
  public DescriptorDigest getImageDigest() {
    return imageDigest;
  }

  /**
   * Gets the image ID.
   *
   * @return the digest
   */
  /*public DescriptorDigest getImageId() {
    return imageId;
  }*/
}