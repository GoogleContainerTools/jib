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
import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.builder.BuildSteps;
import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.InsecureRegistryException;
import com.google.cloud.tools.jib.registry.RegistryCredentialsNotSentException;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

/** Tests for {@link BuildStepsRunner}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildStepsRunnerTest {

  private static final HelpfulSuggestions TEST_HELPFUL_SUGGESTIONS =
      new HelpfulSuggestions(
          "messagePrefix",
          "clearCacheCommand",
          ImageReference.of("someregistry", "somerepository", null),
          false,
          "baseImageCredHelperConfiguration",
          registry -> "baseImageAuthConfiguration " + registry,
          ImageReference.of("toRegistry", "toRepository", null),
          false,
          "targetImageCredHelperConfiguration",
          registry -> "targetImageAuthConfiguration " + registry,
          "toConfig",
          "toFlag",
          "buildFile");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private BuildSteps mockBuildSteps;
  @Mock private JibLogger mockBuildLogger;
  @Mock private RegistryUnauthorizedException mockRegistryUnauthorizedException;
  @Mock private RegistryCredentialsNotSentException mockRegistryCredentialsNotSentException;
  @Mock private HttpResponseException mockHttpResponseException;
  @Mock private ExecutionException mockExecutionException;
  @Mock private BuildConfiguration mockBuildConfiguration;

  private BuildStepsRunner testBuildImageStepsRunner;

  @Before
  public void setUpMocks() {
    testBuildImageStepsRunner = new BuildStepsRunner(mockBuildSteps, "ignored", "ignored");

    Mockito.when(mockBuildSteps.getBuildConfiguration()).thenReturn(mockBuildConfiguration);
    Mockito.when(mockBuildConfiguration.getBuildLogger()).thenReturn(mockBuildLogger);
    Mockito.when(mockBuildConfiguration.getLayerConfigurations())
        .thenReturn(
            ImmutableList.of(
                LayerConfiguration.builder()
                    .addEntry(Paths.get("ignored"), Paths.get("ignored"))
                    .build(),
                LayerConfiguration.builder()
                    .addEntry(Paths.get("ignored"), Paths.get("ignored"))
                    .build(),
                LayerConfiguration.builder()
                    .addEntry(Paths.get("ignored"), Paths.get("ignored"))
                    .build()));
  }

  @Test
  public void testBuildImage_pass() throws BuildStepsExecutionException {
    testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
  }

  @Test
  public void testBuildImage_cacheMetadataCorruptedException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    CacheMetadataCorruptedException mockCacheMetadataCorruptedException =
        Mockito.mock(CacheMetadataCorruptedException.class);
    Mockito.doThrow(mockCacheMetadataCorruptedException).when(mockBuildSteps).run();

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forCacheNeedsClean(), ex.getMessage());
      Assert.assertEquals(mockCacheMetadataCorruptedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_httpHostConnectException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    HttpHostConnectException mockHttpHostConnectException =
        Mockito.mock(HttpHostConnectException.class);
    Mockito.when(mockExecutionException.getCause()).thenReturn(mockHttpHostConnectException);
    Mockito.doThrow(mockExecutionException).when(mockBuildSteps).run();

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forHttpHostConnect(), ex.getMessage());
      Assert.assertEquals(mockHttpHostConnectException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_unknownHostException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    UnknownHostException mockUnknownHostException = Mockito.mock(UnknownHostException.class);
    Mockito.when(mockExecutionException.getCause()).thenReturn(mockUnknownHostException);
    Mockito.doThrow(mockExecutionException).when(mockBuildSteps).run();

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forUnknownHost(), ex.getMessage());
      Assert.assertEquals(mockUnknownHostException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_insecureRegistryException()
      throws InterruptedException, ExecutionException, CacheDirectoryNotOwnedException,
          CacheMetadataCorruptedException, IOException, CacheDirectoryCreationException {
    InsecureRegistryException mockInsecureRegistryException =
        Mockito.mock(InsecureRegistryException.class);
    Mockito.when(mockExecutionException.getCause()).thenReturn(mockInsecureRegistryException);
    Mockito.doThrow(mockExecutionException).when(mockBuildSteps).run();

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forInsecureRegistry(), ex.getMessage());
      Assert.assertEquals(mockInsecureRegistryException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_registryUnauthorizedException_statusCodeForbidden()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getImageReference())
        .thenReturn("someregistry/somerepository");
    Mockito.when(mockHttpResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_FORBIDDEN);

    Mockito.when(mockExecutionException.getCause()).thenReturn(mockRegistryUnauthorizedException);
    Mockito.doThrow(mockExecutionException).when(mockBuildSteps).run();

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(
          TEST_HELPFUL_SUGGESTIONS.forHttpStatusCodeForbidden("someregistry/somerepository"),
          ex.getMessage());
      Assert.assertEquals(mockRegistryUnauthorizedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_registryUnauthorizedException_noCredentials()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getRegistry()).thenReturn("someregistry");
    Mockito.when(mockRegistryUnauthorizedException.getRepository()).thenReturn("somerepository");
    Mockito.when(mockHttpResponseException.getStatusCode()).thenReturn(-1); // Unknown

    Mockito.when(mockExecutionException.getCause()).thenReturn(mockRegistryUnauthorizedException);
    Mockito.doThrow(mockExecutionException).when(mockBuildSteps).run();

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(
          TEST_HELPFUL_SUGGESTIONS.forNoCredentialsDefined("someregistry", "somerepository"),
          ex.getMessage());
      Assert.assertEquals(mockRegistryUnauthorizedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_registryCredentialsNotSentException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    Mockito.when(mockExecutionException.getCause())
        .thenReturn(mockRegistryCredentialsNotSentException);
    Mockito.doThrow(mockExecutionException).when(mockBuildSteps).run();

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forCredentialsNotSent(), ex.getMessage());
      Assert.assertEquals(mockRegistryCredentialsNotSentException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_other()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    Throwable throwable = new Throwable();
    Mockito.when(mockExecutionException.getCause()).thenReturn(throwable);
    Mockito.doThrow(mockExecutionException).when(mockBuildSteps).run();

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.none(), ex.getMessage());
      Assert.assertEquals(throwable, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_otherException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException, CacheDirectoryCreationException {
    IOException ioException = new IOException();
    Mockito.doThrow(ioException).when(mockBuildSteps).run();

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.none(), ex.getMessage());
      Assert.assertEquals(ioException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_cacheDirectoryNotOwnedException_needsClean()
      throws InterruptedException, ExecutionException, CacheDirectoryNotOwnedException,
          CacheMetadataCorruptedException, IOException, CacheDirectoryCreationException {
    Path expectedCacheDirectory = Paths.get("some/path");

    CacheDirectoryNotOwnedException mockCacheDirectoryNotOwnedException =
        Mockito.mock(CacheDirectoryNotOwnedException.class);
    Mockito.when(mockCacheDirectoryNotOwnedException.getCacheDirectory())
        .thenReturn(expectedCacheDirectory);
    Mockito.doThrow(mockCacheDirectoryNotOwnedException).when(mockBuildSteps).run();

    Mockito.when(mockBuildConfiguration.getApplicationLayersCacheConfiguration())
        .thenReturn(CacheConfiguration.forPath(expectedCacheDirectory));

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forCacheNeedsClean(), ex.getMessage());
      Assert.assertEquals(mockCacheDirectoryNotOwnedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_cacheDirectoryNotOwnedException()
      throws InterruptedException, ExecutionException, CacheDirectoryNotOwnedException,
          CacheMetadataCorruptedException, IOException, CacheDirectoryCreationException {
    Path expectedCacheDirectory = Paths.get("some/path");

    CacheDirectoryNotOwnedException mockCacheDirectoryNotOwnedException =
        Mockito.mock(CacheDirectoryNotOwnedException.class);
    Mockito.when(mockCacheDirectoryNotOwnedException.getCacheDirectory())
        .thenReturn(expectedCacheDirectory);
    Mockito.doThrow(mockCacheDirectoryNotOwnedException).when(mockBuildSteps).run();

    Mockito.when(mockBuildConfiguration.getApplicationLayersCacheConfiguration())
        .thenReturn(CacheConfiguration.forPath(Paths.get("another/path")));

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(
          TEST_HELPFUL_SUGGESTIONS.forCacheDirectoryNotOwned(expectedCacheDirectory),
          ex.getMessage());
      Assert.assertEquals(mockCacheDirectoryNotOwnedException, ex.getCause());
    }
  }
}
