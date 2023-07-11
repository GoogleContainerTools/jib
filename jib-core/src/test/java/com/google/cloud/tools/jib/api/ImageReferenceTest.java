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

import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link ImageReference}. */
class ImageReferenceTest {

  private static final List<String> goodRegistries =
      Arrays.asList("some.domain---name.123.com:8080", "gcr.io", "localhost", null, "");
  private static final List<String> goodRepositories =
      Arrays.asList("some123_abc/repository__123-456/name---here", "distroless/java", "repository");
  private static final List<String> goodTags = Arrays.asList("some-.-.Tag", "", "latest", null);
  private static final List<String> goodDigests =
      Arrays.asList(
          "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null);

  private static final List<String> badImageReferences =
      Arrays.asList(
          "",
          ":justsometag",
          "@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "repository@sha256:a",
          "repository@notadigest",
          "Repositorywithuppercase",
          "registry:8080/Repositorywithuppercase/repository:sometag",
          "domain.name:nonnumberport/repository",
          "domain.name:nonnumberport//:no-repository");

  @Test
  void testParse_pass() throws InvalidImageReferenceException {
    for (String goodRegistry : goodRegistries) {
      for (String goodRepository : goodRepositories) {
        for (String goodTag : goodTags) {
          for (String goodDigest : goodDigests) {
            verifyParse(goodRegistry, goodRepository, goodTag, goodDigest);
          }
        }
      }
    }
  }

  @Test
  void testParse_dockerHub_official() throws InvalidImageReferenceException {
    String imageReferenceString = "busybox";
    ImageReference imageReference = ImageReference.parse(imageReferenceString);

    Assert.assertEquals("registry-1.docker.io", imageReference.getRegistry());
    Assert.assertEquals("library/busybox", imageReference.getRepository());
    Assert.assertEquals("latest", imageReference.getTag().orElse(null));
  }

  @Test
  void testParse_dockerHub_user() throws InvalidImageReferenceException {
    String imageReferenceString = "someuser/someimage";
    ImageReference imageReference = ImageReference.parse(imageReferenceString);

    Assert.assertEquals("registry-1.docker.io", imageReference.getRegistry());
    Assert.assertEquals("someuser/someimage", imageReference.getRepository());
    Assert.assertEquals("latest", imageReference.getTag().orElse(null));
  }

  @Test
  void testParse_invalid() {
    for (String badImageReference : badImageReferences) {
      try {
        ImageReference.parse(badImageReference);
        Assert.fail(badImageReference + " should not be a valid image reference");

      } catch (InvalidImageReferenceException ex) {
        MatcherAssert.assertThat(ex.getMessage(), CoreMatchers.containsString(badImageReference));
      }
    }
  }

  @Test
  void testOf_smoke() {
    String expectedRegistry = "someregistry";
    String expectedRepository = "somerepository";
    String expectedTag = "sometag";

    Assert.assertEquals(
        expectedRegistry,
        ImageReference.of(expectedRegistry, expectedRepository, expectedTag).getRegistry());
    Assert.assertEquals(
        expectedRepository,
        ImageReference.of(expectedRegistry, expectedRepository, expectedTag).getRepository());
    Assert.assertEquals(
        expectedTag,
        ImageReference.of(expectedRegistry, expectedRepository, expectedTag).getTag().orElse(null));
    Assert.assertEquals(
        "registry-1.docker.io",
        ImageReference.of(null, expectedRepository, expectedTag).getRegistry());
    Assert.assertEquals(
        "registry-1.docker.io", ImageReference.of(null, expectedRepository, null).getRegistry());
    Assert.assertEquals(
        "latest",
        ImageReference.of(expectedRegistry, expectedRepository, null).getTag().orElse(null));
    Assert.assertEquals(
        "latest", ImageReference.of(null, expectedRepository, null).getTag().orElse(null));
    Assert.assertEquals(
        expectedRepository, ImageReference.of(null, expectedRepository, null).getRepository());
  }

  @Test
  void testToString() throws InvalidImageReferenceException {
    Assert.assertEquals("someimage", ImageReference.of(null, "someimage", null).toString());
    Assert.assertEquals("someimage", ImageReference.of("", "someimage", "").toString());
    Assert.assertEquals(
        "someotherimage", ImageReference.of(null, "library/someotherimage", null).toString());
    Assert.assertEquals(
        "someregistry/someotherimage",
        ImageReference.of("someregistry", "someotherimage", null).toString());
    Assert.assertEquals(
        "anotherregistry/anotherimage:sometag",
        ImageReference.of("anotherregistry", "anotherimage", "sometag").toString());

    Assert.assertEquals(
        "someimage@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        ImageReference.of(
                null,
                "someimage",
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            .toString());
    Assert.assertEquals(
        "gcr.io/distroless/java@sha256:b430543bea1d8326e767058bdab3a2482ea45f59d7af5c5c61334cd29ede88a1",
        ImageReference.parse(
                "gcr.io/distroless/java@sha256:b430543bea1d8326e767058bdab3a2482ea45f59d7af5c5c61334cd29ede88a1")
            .toString());
  }

  @Test
  void testToStringWithQualifier() {
    Assert.assertEquals(
        "someimage:latest", ImageReference.of(null, "someimage", null).toStringWithQualifier());
    Assert.assertEquals(
        "someimage:latest", ImageReference.of("", "someimage", "").toStringWithQualifier());
    Assert.assertEquals(
        "someotherimage:latest",
        ImageReference.of(null, "library/someotherimage", null).toStringWithQualifier());
    Assert.assertEquals(
        "someregistry/someotherimage:latest",
        ImageReference.of("someregistry", "someotherimage", null).toStringWithQualifier());
    Assert.assertEquals(
        "anotherregistry/anotherimage:sometag",
        ImageReference.of("anotherregistry", "anotherimage", "sometag").toStringWithQualifier());
    Assert.assertEquals(
        "anotherregistry/anotherimage@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        ImageReference.of(
                "anotherregistry",
                "anotherimage",
                null,
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            .toStringWithQualifier());
    Assert.assertEquals(
        "anotherregistry/anotherimage@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        ImageReference.of(
                "anotherregistry",
                "anotherimage",
                "sometag",
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            .toStringWithQualifier());
  }

  @Test
  void testIsScratch() throws InvalidImageReferenceException {
    Assert.assertTrue(ImageReference.parse("scratch").isScratch());
    Assert.assertTrue(ImageReference.scratch().isScratch());
    Assert.assertFalse(ImageReference.of("", "scratch", "").isScratch());
    Assert.assertFalse(ImageReference.of(null, "scratch", null).isScratch());
  }

  @Test
  void testToString_scratch() {
    Assert.assertEquals("scratch", ImageReference.scratch().toString());
  }

  @Test
  void testGetRegistry() {
    Assert.assertEquals(
        "registry-1.docker.io", ImageReference.of(null, "someimage", null).getRegistry());
    Assert.assertEquals(
        "registry-1.docker.io", ImageReference.of("docker.io", "someimage", null).getRegistry());
    Assert.assertEquals(
        "index.docker.io", ImageReference.of("index.docker.io", "someimage", null).getRegistry());
    Assert.assertEquals(
        "registry.hub.docker.com",
        ImageReference.of("registry.hub.docker.com", "someimage", null).getRegistry());
    Assert.assertEquals("gcr.io", ImageReference.of("gcr.io", "someimage", null).getRegistry());
  }

  @Test
  void testEquality() throws InvalidImageReferenceException {
    ImageReference image1 = ImageReference.parse("gcr.io/project/image:tag");
    ImageReference image2 = ImageReference.parse("gcr.io/project/image:tag");

    Assert.assertEquals(image1, image2);
    Assert.assertEquals(image1.hashCode(), image2.hashCode());
  }

  @Test
  void testEquality_differentRegistry() throws InvalidImageReferenceException {
    ImageReference image1 = ImageReference.parse("gcr.io/project/image:tag");
    ImageReference image2 = ImageReference.parse("registry-1.docker.io/project/image:tag");

    Assert.assertNotEquals(image1, image2);
    Assert.assertNotEquals(image1.hashCode(), image2.hashCode());
  }

  @Test
  void testEquality_differentRepository() throws InvalidImageReferenceException {
    ImageReference image1 = ImageReference.parse("gcr.io/project/image:tag");
    ImageReference image2 = ImageReference.parse("gcr.io/project2/image:tag");

    Assert.assertNotEquals(image1, image2);
    Assert.assertNotEquals(image1.hashCode(), image2.hashCode());
  }

  @Test
  void testEquality_differentTag() throws InvalidImageReferenceException {
    ImageReference image1 = ImageReference.parse("gcr.io/project/image:tag1");
    ImageReference image2 = ImageReference.parse("gcr.io/project/image:tag2");

    Assert.assertNotEquals(image1, image2);
    Assert.assertNotEquals(image1.hashCode(), image2.hashCode());
  }

  private void verifyParse(String registry, String repository, String tag, String digest)
      throws InvalidImageReferenceException {
    // Gets the expected parsed components.
    String expectedRegistry = registry;
    if (Strings.isNullOrEmpty(expectedRegistry)) {
      expectedRegistry = "registry-1.docker.io";
    }
    String expectedRepository = repository;
    if ("registry-1.docker.io".equals(expectedRegistry) && repository.indexOf('/') < 0) {
      expectedRepository = "library/" + expectedRepository;
    }
    String expectedTag = tag;
    if (Strings.isNullOrEmpty(expectedTag) && Strings.isNullOrEmpty(digest)) {
      expectedTag = "latest";
    }
    if (Strings.isNullOrEmpty(expectedTag)) {
      expectedTag = null;
    }

    String expectedDigest = digest;
    if (Strings.isNullOrEmpty(digest)) {
      expectedDigest = null;
    }

    // Builds the image reference to parse.
    StringBuilder imageReferenceBuilder = new StringBuilder();
    if (!Strings.isNullOrEmpty(registry)) {
      imageReferenceBuilder.append(registry).append('/');
    }
    imageReferenceBuilder.append(repository);
    if (!Strings.isNullOrEmpty(tag)) {
      imageReferenceBuilder.append(':').append(tag);
    }
    if (!Strings.isNullOrEmpty(digest)) {
      imageReferenceBuilder.append('@').append(digest);
    }

    ImageReference imageReference = ImageReference.parse(imageReferenceBuilder.toString());

    Assert.assertEquals(expectedRegistry, imageReference.getRegistry());
    Assert.assertEquals(expectedRepository, imageReference.getRepository());
    Assert.assertEquals(expectedTag, imageReference.getTag().orElse(null));
    Assert.assertEquals(expectedDigest, imageReference.getDigest().orElse(null));
  }
}
