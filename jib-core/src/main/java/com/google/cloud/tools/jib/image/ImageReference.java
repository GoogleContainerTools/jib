/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.image;

import com.google.common.base.Strings;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Represents an image reference.
 *
 * @see <a
 *     href="https://github.com/docker/distribution/blob/master/reference/reference.go">https://github.com/docker/distribution/blob/master/reference/reference.go</a>
 * @see <a
 *     href="https://docs.docker.com/engine/reference/commandline/tag/#extended-description">https://docs.docker.com/engine/reference/commandline/tag/#extended-description</a>
 */
public class ImageReference {

  private static final String DOCKER_HUB_REGISTRY = "registry.hub.docker.com";
  private static final String DEFAULT_TAG = "latest";
  private static final String LIBRARY_REPOSITORY_PREFIX = "library/";

  /**
   * Matches all sequences of alphanumeric characters possibly separated by any number of dashes in
   * the middle.
   */
  private static final String REGISTRY_COMPONENT_REGEX =
      "(?:[a-zA-Z\\d]|(?:[a-zA-Z\\d][a-zA-Z\\d-]*[a-zA-Z\\d]))";

  /**
   * Matches sequences of {@code REGISTRY_COMPONENT_REGEX} separated by a dot, with an optional
   * {@code :port} at the end.
   */
  private static final String REGISTRY_REGEX =
      String.format("%s(?:\\.%s)*(?::\\d+)?", REGISTRY_COMPONENT_REGEX, REGISTRY_COMPONENT_REGEX);

  /**
   * Matches all sequences of alphanumeric characters separated by a separator.
   *
   * <p>A separator is either an underscore, a dot, two underscores, or any number of dashes.
   */
  private static final String REPOSITORY_COMPONENT_REGEX =
      "[a-z\\d]+(?:(?:[_.]|__|[-]*)[a-z\\d]+)*";

  /** Matches all repetitions of {@code REPOSITORY_COMPONENT_REGEX} separated by a backslash. */
  private static final String REPOSITORY_REGEX =
      String.format("(?:%s/)*%s", REPOSITORY_COMPONENT_REGEX, REPOSITORY_COMPONENT_REGEX);

  /** Matches a tag of max length 128. */
  private static final String TAG_REGEX = "[\\w][\\w.-]{0,127}";

  /**
   * Matches a full image reference, which is the registry, repository, and tag/digest separated by
   * backslashes. The repository is required, but the registry and tag/digest are optional.
   */
  private static final String REFERENCE_REGEX =
      String.format(
          "^(?:(%s)/)?(%s)(?:(?::(%s))|(?:@(%s)))?$",
          REGISTRY_REGEX, REPOSITORY_REGEX, TAG_REGEX, DescriptorDigest.DIGEST_REGEX);

  private static final Pattern REFERENCE_PATTERN = Pattern.compile(REFERENCE_REGEX);

  /** Parses an image reference. */
  public static ImageReference parse(String reference) throws InvalidImageReferenceException {
    Matcher matcher = REFERENCE_PATTERN.matcher(reference);

    if (!matcher.find() || matcher.groupCount() < 4) {
      throw new InvalidImageReferenceException(reference);
    }

    String registry = matcher.group(1);
    String repository = matcher.group(2);
    String tag = matcher.group(3);
    String digest = matcher.group(4);

    // If no registry was matched, use Docker Hub by default.
    if (Strings.isNullOrEmpty(registry)) {
      registry = DOCKER_HUB_REGISTRY;
    }

    if (Strings.isNullOrEmpty(repository)) {
      throw new InvalidImageReferenceException(reference);
    }
    /*
     * If a registry was matched but it does not contain any dots or colons, it should actually be
     * part of the repository unless it is "localhost".
     *
     * See https://github.com/docker/distribution/blob/245ca4659e09e9745f3cc1217bf56e946509220c/reference/normalize.go#L62
     */
    if (!registry.contains(".") && !registry.contains(":") && !"localhost".equals(registry)) {
      repository = registry + "/" + repository;
      registry = DOCKER_HUB_REGISTRY;
    }
    /*
     * For Docker Hub, if the repository is only one component, then it should be prefixed with
     * 'library/'.
     *
     * See https://docs.docker.com/engine/reference/commandline/pull/#pull-an-image-from-docker-hub
     */
    if (DOCKER_HUB_REGISTRY.equals(registry) && repository.indexOf('/') < 0) {
      repository = LIBRARY_REPOSITORY_PREFIX + repository;
    }

    if (!Strings.isNullOrEmpty(tag)) {
      if (!Strings.isNullOrEmpty(digest)) {
        // Cannot have matched both tag and digest.
        throw new InvalidImageReferenceException(reference);
      }
    } else if (!Strings.isNullOrEmpty(digest)) {
      tag = digest;
    } else {
      tag = DEFAULT_TAG;
    }

    return new ImageReference(registry, repository, tag);
  }

  /** Builds an image reference from a registry, repository, and tag. */
  public static ImageReference of(
      @Nullable String registry, String repository, @Nullable String tag) {
    if (Strings.isNullOrEmpty(registry)) {
      registry = DOCKER_HUB_REGISTRY;
    }
    if (Strings.isNullOrEmpty(tag)) {
      tag = DEFAULT_TAG;
    }
    return new ImageReference(registry, repository, tag);
  }

  /** @return {@code true} if is a valid registry; {@code false} otherwise */
  public static boolean isValidRegistry(String registry) {
    return registry.matches(REGISTRY_REGEX);
  }

  /** @return {@code true} if is a valid repository; {@code false} otherwise */
  public static boolean isValidRepository(String repository) {
    return repository.matches(REPOSITORY_REGEX);
  }

  /** @return {@code true} if is a valid tag; {@code false} otherwise */
  public static boolean isValidTag(String tag) {
    return tag.matches(TAG_REGEX);
  }

  private final String registry;
  private final String repository;
  private final String tag;

  /** Use {@link #parse} to construct. */
  private ImageReference(String registry, String repository, String tag) {
    this.registry = registry;
    this.repository = repository;
    this.tag = tag;
  }

  public String getRegistry() {
    return registry;
  }

  public String getRepository() {
    return repository;
  }

  public String getTag() {
    return tag;
  }

  /** @return the image reference in Docker-readable format (inverse of {@link #parse}) */
  @Override
  public String toString() {
    StringBuilder referenceString = new StringBuilder();

    if (!DOCKER_HUB_REGISTRY.equals(registry)) {
      // Use registry and repository if not Docker Hub.
      referenceString.append(registry).append('/').append(repository);

    } else if (repository.startsWith(LIBRARY_REPOSITORY_PREFIX)) {
      // If Docker Hub and repository has 'library/' prefix, remove the 'library/' prefix.
      referenceString.append(repository.substring(LIBRARY_REPOSITORY_PREFIX.length()));

    } else {
      // Use just repository if Docker Hub.
      referenceString.append(repository);
    }

    // Use tag if not the default tag.
    if (!DEFAULT_TAG.equals(tag)) {
      referenceString.append(':').append(tag);
    }

    return referenceString.toString();
  }
}
