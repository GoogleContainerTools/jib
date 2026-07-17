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

package com.google.cloud.tools.jib.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.cloud.tools.jib.image.json.DescriptorDigestDeserializer;
import com.google.cloud.tools.jib.image.json.DescriptorDigestSerializer;
import java.security.DigestException;
import java.util.Locale;

/**
 * Represents a SHA-256 content descriptor digest as defined by the Registry HTTP API v2 reference.
 *
 * @see <a
 *     href="https://docs.docker.com/registry/spec/api/#content-digests">https://docs.docker.com/registry/spec/api/#content-digests</a>
 * @see <a href="https://github.com/opencontainers/image-spec/blob/master/descriptor.md#digests">OCI
 *     Content Descriptor Digest</a>
 */
@JsonSerialize(using = DescriptorDigestSerializer.class)
@JsonDeserialize(using = DescriptorDigestDeserializer.class)
public class DescriptorDigest {

  public static final int HASH_LENGTH = 64;

  /** Pattern matches a SHA-256 hash - 32 bytes in lowercase hexadecimal. */
  private static final String HASH_REGEX = String.format(Locale.US, "[a-f0-9]{%d}", HASH_LENGTH);

  /** The algorithm prefix for the digest string. */
  private static final String DIGEST_PREFIX = "sha256:";

  /** Pattern matches a SHA-256 digest - a SHA-256 hash prefixed with "sha256:". */
  static final String DIGEST_REGEX = DIGEST_PREFIX + HASH_REGEX;

  private final String hash;

  /**
   * Creates a new instance from a valid hash string.
   *
   * @param hash the hash to generate the {@link DescriptorDigest} from
   * @return a new {@link DescriptorDigest} created from the hash
   * @throws DigestException if the hash is invalid
   */
  public static DescriptorDigest fromHash(String hash) throws DigestException {
    if (!hash.matches(HASH_REGEX)) {
      throw new DigestException("Invalid hash: " + hash);
    }

    return new DescriptorDigest(hash);
  }

  /**
   * Creates a new instance from a valid digest string.
   *
   * @param digest the digest to generate the {@link DescriptorDigest} from
   * @return a new {@link DescriptorDigest} created from the digest
   * @throws DigestException if the digest is invalid
   */
  public static DescriptorDigest fromDigest(String digest) throws DigestException {
    if (!digest.matches(DIGEST_REGEX)) {
      throw new DigestException("Invalid digest: " + digest);
    }

    // Extracts the hash portion of the digest.
    String hash = digest.substring(DIGEST_PREFIX.length());
    return new DescriptorDigest(hash);
  }

  private DescriptorDigest(String hash) {
    this.hash = hash;
  }

  public String getHash() {
    return hash;
  }

  @Override
  public String toString() {
    return DIGEST_PREFIX + hash;
  }

  /** Pass-through hash code of the digest string. */
  @Override
  public int hashCode() {
    return hash.hashCode();
  }

  /** Two digest objects are equal if their digest strings are equal. */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof DescriptorDigest) {
      return hash.equals(((DescriptorDigest) obj).hash);
    }

    return false;
  }
}
