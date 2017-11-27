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

package com.google.cloud.tools.crepecake.image;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a SHA-256 digest as defined by the Registry HTTP API v2 reference.
 *
 * @see <a
 *     href="https://docs.docker.com/registry/spec/api/#content-digests">https://docs.docker.com/registry/spec/api/#content-digests</a>
 */
public class Digest {

  /** Pattern matches a SHA-256 hash - 32 bytes in lowercase hexadecimal. */
  private static final String HASH_REGEX = "[a-f0-9]{64}";

  /** Pattern matches a SHA-256 digest - a SHA-256 hash prefixed with "sha256:". */
  private static final String DIGEST_REGEX = "sha256:" + HASH_REGEX;

  private static final Pattern HASH_PATTERN = Pattern.compile(HASH_REGEX);

  private final String digest;
  private final String hash;

  /** Creates a new instance from a valid hash string. */
  public static Digest fromHash(String hash) throws DigestException {
    if (!hash.matches(HASH_REGEX)) {
      throw new DigestException("Invalid hash: " + hash);
    }

    String digest = "sha256:" + hash;
    return new Digest(digest, hash);
  }

  /** Creates a new instance from a valid digest string. */
  public static Digest fromDigest(String digest) throws DigestException {
    if (!digest.matches(DIGEST_REGEX)) {
      throw new DigestException("Invalid digest: " + digest);
    }

    Matcher matcher = HASH_PATTERN.matcher(digest);
    if (!matcher.find()) {
      throw new DigestException("Invalid digest: " + digest);
    }

    String hash = matcher.group(0);
    return new Digest(digest, hash);
  }

  public String getHash() {
    return hash;
  }

  public String toString() {
    return digest;
  }

  /** Pass-through hash code of the digest string. */
  @Override
  public int hashCode() {
    return digest.hashCode();
  }

  /** Two digest objects are equal if their digest strings are equal. */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Digest) {
      return digest.equals(((Digest) obj).digest);
    }

    return false;
  }

  private Digest(String digest, String hash) {
    this.digest = digest;
    this.hash = hash;
  }
}
