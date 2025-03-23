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

package com.google.cloud.tools.jib.plugins.common;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InsecureRegistryException;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.registry.RegistryCredentialsNotSentException;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.http.conn.HttpHostConnectException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibBuildRunner}. */
@RunWith(MockitoJUnitRunner.class)
public class JibBuildRunnerTest {

  private static final HelpfulSuggestions TEST_HELPFUL_SUGGESTIONS =
      new HelpfulSuggestions(
          "messagePrefix", "clearCacheCommand", "toConfig", "toFlag", "buildFile");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private JibContainerBuilder mockJibContainerBuilder;
  @Mock private JibContainer mockJibContainer;
  @Mock private Containerizer mockContainerizer;
  @Mock private RegistryUnauthorizedException mockRegistryUnauthorizedException;
  @Mock private RegistryCredentialsNotSentException mockRegistryCredentialsNotSentException;
  @Mock private HttpResponseException mockHttpResponseException;

  private JibBuildRunner testJibBuildRunner;

  @Before
  public void setUpMocks() {
    testJibBuildRunner =
        new JibBuildRunner(
            mockJibContainerBuilder,
            mockContainerizer,
            ignored -> {},
            TEST_HELPFUL_SUGGESTIONS,
            "ignored",
            "ignored");
  }

  @Test
  public void testBuildImage_pass()
      throws BuildStepsExecutionException, IOException, CacheDirectoryCreationException {
    JibContainer buildResult = testJibBuildRunner.runBuild();
    Assert.assertNull(buildResult);
  }

  @Test
  public void testBuildImage_httpHostConnectException()
      throws InterruptedException, IOException, CacheDirectoryCreationException, RegistryException,
          ExecutionException {
    HttpHostConnectException mockHttpHostConnectException =
        Mockito.mock(HttpHostConnectException.class);
    Mockito.doThrow(mockHttpHostConnectException)
        .when(mockJibContainerBuilder)
        .containerize(mockContainerizer);

    try {
      testJibBuildRunner.runBuild();
      Assert.fail();

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forHttpHostConnect(), ex.getMessage());
    }
  }

  @Test
  public void testBuildImage_unknownHostException()
      throws InterruptedException, IOException, CacheDirectoryCreationException, RegistryException,
          ExecutionException {
    UnknownHostException mockUnknownHostException = Mockito.mock(UnknownHostException.class);
    Mockito.doThrow(mockUnknownHostException)
        .when(mockJibContainerBuilder)
        .containerize(mockContainerizer);

    try {
      testJibBuildRunner.runBuild();
      Assert.fail();

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forUnknownHost(), ex.getMessage());
    }
  }

  @Test
  public void testBuildImage_insecureRegistryException()
      throws InterruptedException, IOException, CacheDirectoryCreationException, RegistryException,
          ExecutionException {
    InsecureRegistryException mockInsecureRegistryException =
        Mockito.mock(InsecureRegistryException.class);
    Mockito.doThrow(mockInsecureRegistryException)
        .when(mockJibContainerBuilder)
        .containerize(mockContainerizer);

    try {
      testJibBuildRunner.runBuild();
      Assert.fail();

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forInsecureRegistry(), ex.getMessage());
    }
  }

  @Test
  public void testBuildImage_registryUnauthorizedException_statusCodeForbidden()
      throws InterruptedException, IOException, CacheDirectoryCreationException, RegistryException,
          ExecutionException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getImageReference())
        .thenReturn("someregistry/somerepository");
    Mockito.when(mockHttpResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_FORBIDDEN);

    Mockito.doThrow(mockRegistryUnauthorizedException)
        .when(mockJibContainerBuilder)
        .containerize(mockContainerizer);

    try {
      testJibBuildRunner.runBuild();
      Assert.fail();

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(
          TEST_HELPFUL_SUGGESTIONS.forHttpStatusCodeForbidden("someregistry/somerepository"),
          ex.getMessage());
    }
  }

  @Test
  public void testBuildImage_registryUnauthorizedException_noCredentials()
      throws InterruptedException, IOException, CacheDirectoryCreationException, RegistryException,
          ExecutionException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getImageReference())
        .thenReturn("someregistry/somerepository");
    Mockito.when(mockHttpResponseException.getStatusCode()).thenReturn(-1); // Unknown

    Mockito.doThrow(mockRegistryUnauthorizedException)
        .when(mockJibContainerBuilder)
        .containerize(mockContainerizer);

    try {
      testJibBuildRunner.runBuild();
      Assert.fail();

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(
          TEST_HELPFUL_SUGGESTIONS.forNoCredentialsDefined("someregistry/somerepository"),
          ex.getMessage());
    }
  }

  @Test
  public void testBuildImage_registryCredentialsNotSentException()
      throws InterruptedException, IOException, CacheDirectoryCreationException, RegistryException,
          ExecutionException {
    Mockito.doThrow(mockRegistryCredentialsNotSentException)
        .when(mockJibContainerBuilder)
        .containerize(mockContainerizer);

    try {
      testJibBuildRunner.runBuild();
      Assert.fail();

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forCredentialsNotSent(), ex.getMessage());
    }
  }

  @Test
  public void testBuildImage_other()
      throws InterruptedException, IOException, CacheDirectoryCreationException, RegistryException,
          ExecutionException {
    Mockito.doThrow(new RegistryException("messagePrefix"))
        .when(mockJibContainerBuilder)
        .containerize(mockContainerizer);

    try {
      testJibBuildRunner.runBuild();
      Assert.fail();

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.none(), ex.getMessage());
    }
  }

  @Test
  public void testBuildImage_writesImageJson() throws Exception {
    final ImageReference targetImageReference = ImageReference.parse("gcr.io/distroless/java:11");
    final String imageId =
        "sha256:61bb3ec31a47cb730eb58a38bbfa813761a51dca69d10e39c24c3d00a7b2c7a9";
    final String digest = "sha256:3f1be7e19129edb202c071a659a4db35280ab2bb1a16f223bfd5d1948657b6fc";
    final Set<String> tags = ImmutableSet.of("latest", "0.1.41-69d10e-20200116T101403");

    final Path outputPath = temporaryFolder.newFile("jib-image.json").toPath();

    Mockito.when(mockJibContainer.getTargetImage()).thenReturn(targetImageReference);
    Mockito.when(mockJibContainer.getImageId()).thenReturn(DescriptorDigest.fromDigest(imageId));
    Mockito.when(mockJibContainer.getDigest()).thenReturn(DescriptorDigest.fromDigest(digest));
    Mockito.when(mockJibContainer.getTags()).thenReturn(tags);
    Mockito.when(mockJibContainerBuilder.containerize(mockContainerizer))
        .thenReturn(mockJibContainer);
    Mockito.when(mockJibContainer.isImagePushed()).thenReturn(true);
    testJibBuildRunner.writeImageJson(outputPath).runBuild();

    final String outputJson = new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8);
    final ImageMetadataOutput metadataOutput = ImageMetadataOutput.fromJson(outputJson);
    Assert.assertEquals(targetImageReference.toString(), metadataOutput.getImage());
    Assert.assertEquals(imageId, metadataOutput.getImageId());
    Assert.assertEquals(digest, metadataOutput.getImageDigest());
    Assert.assertEquals(tags, ImmutableSet.copyOf(metadataOutput.getTags()));
    Assert.assertTrue(metadataOutput.isImagePushed());
  }

  @Test
  public void testBuildImage_writeImageTagDigest() throws Exception {
    Set<String> imageTagsDigest = ImmutableSet.of(
            "gcr.io/project/image:latest@sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            "gcr.io/project/image:custom-tag@sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");

    String expectedImageTagsDigestOutput = "gcr.io/project/image:latest@sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789\n" +
            "gcr.io/project/image:custom-tag@sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789\n";

    final Path outputPath = temporaryFolder.newFile("jib-image-tag.digest").toPath();

    Mockito.when(mockJibContainer.getImageTagsWithDigest()).thenReturn(imageTagsDigest);
    Mockito.when(mockJibContainerBuilder.containerize(mockContainerizer))
            .thenReturn(mockJibContainer);
    Mockito.when(mockJibContainer.isImagePushed()).thenReturn(true);

    testJibBuildRunner.writeImageTagDigest(outputPath).runBuild();

    final String imageTagDigestOutput = new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8);

    Assert.assertEquals(expectedImageTagsDigestOutput, imageTagDigestOutput);
  }
}
