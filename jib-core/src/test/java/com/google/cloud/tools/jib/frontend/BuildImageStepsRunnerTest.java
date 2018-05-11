/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.frontend;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.BuildImageSteps;
import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
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

/** Tests for {@link BuildImageStepsRunner}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildImageStepsRunnerTest {

  private static final HelpfulSuggestions TEST_HELPFUL_SUGGESTIONS =
      new HelpfulSuggestions(
          "messagePrefix",
          "clearCacheCommand",
          "baseImageCredHelperConfiguration",
          registry -> "baseImageAuthConfiguration " + registry,
          "targetImageCredHelperConfiguration",
          registry -> "targetImageAuthConfiguration " + registry);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private BuildImageSteps mockBuildImageSteps;
  @Mock private SourceFilesConfiguration mockSourceFilesConfiguration;
  @Mock private BuildLogger mockBuildLogger;
  @Mock private RegistryUnauthorizedException mockRegistryUnauthorizedException;
  @Mock private HttpResponseException mockHttpResponseException;
  @Mock private ExecutionException mockExecutionException;
  @Mock private BuildConfiguration mockBuildConfiguration;

  private BuildImageStepsRunner testBuildImageStepsRunner;

  @Before
  public void setUpMocks() {
    testBuildImageStepsRunner = new BuildImageStepsRunner(() -> mockBuildImageSteps);

    Mockito.when(mockBuildImageSteps.getBuildConfiguration()).thenReturn(mockBuildConfiguration);
    Mockito.when(mockBuildConfiguration.getBuildLogger()).thenReturn(mockBuildLogger);
    Mockito.when(mockBuildConfiguration.getTargetImageReference())
        .thenReturn(ImageReference.of("someregistry", "somerepository", "sometag"));
    Mockito.when(mockBuildImageSteps.getSourceFilesConfiguration())
        .thenReturn(mockSourceFilesConfiguration);
    Mockito.when(mockSourceFilesConfiguration.getClassesFiles())
        .thenReturn(Collections.emptyList());
    Mockito.when(mockSourceFilesConfiguration.getResourcesFiles())
        .thenReturn(Collections.emptyList());
    Mockito.when(mockSourceFilesConfiguration.getDependenciesFiles())
        .thenReturn(Collections.emptyList());
  }

  @Test
  public void testBuildImage_pass() throws BuildImageStepsExecutionException {
    testBuildImageStepsRunner.buildImage(TEST_HELPFUL_SUGGESTIONS);
  }

  @Test
  public void testBuildImage_cacheMetadataCorruptedException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    CacheMetadataCorruptedException mockCacheMetadataCorruptedException =
        Mockito.mock(CacheMetadataCorruptedException.class);
    Mockito.doThrow(mockCacheMetadataCorruptedException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forCacheMetadataCorrupted(), ex.getMessage());
      Assert.assertEquals(mockCacheMetadataCorruptedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_httpHostConnectException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    HttpHostConnectException mockHttpHostConnectException =
        Mockito.mock(HttpHostConnectException.class);
    Mockito.when(mockExecutionException.getCause()).thenReturn(mockHttpHostConnectException);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forHttpHostConnect(), ex.getMessage());
      Assert.assertEquals(mockHttpHostConnectException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_unknownHostException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    UnknownHostException mockUnknownHostException = Mockito.mock(UnknownHostException.class);
    Mockito.when(mockExecutionException.getCause()).thenReturn(mockUnknownHostException);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forUnknownHost(), ex.getMessage());
      Assert.assertEquals(mockUnknownHostException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_registryUnauthorizedException_statusCodeForbidden()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getImageReference())
        .thenReturn("someregistry/somerepository");
    Mockito.when(mockHttpResponseException.getStatusCode())
        .thenReturn(HttpStatusCodes.STATUS_CODE_FORBIDDEN);

    Mockito.when(mockExecutionException.getCause()).thenReturn(mockRegistryUnauthorizedException);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          TEST_HELPFUL_SUGGESTIONS.forHttpStatusCodeForbidden("someregistry/somerepository"),
          ex.getMessage());
      Assert.assertEquals(mockRegistryUnauthorizedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_registryUnauthorizedException_noCredentials()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getRegistry()).thenReturn("someregistry");
    Mockito.when(mockHttpResponseException.getStatusCode()).thenReturn(-1); // Unknown

    Mockito.when(mockExecutionException.getCause()).thenReturn(mockRegistryUnauthorizedException);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    Mockito.when(mockBuildConfiguration.getBaseImageRegistry()).thenReturn("someregistry");

    try {
      testBuildImageStepsRunner.buildImage(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          TEST_HELPFUL_SUGGESTIONS.forNoCredentialHelpersDefinedForBaseImage("someregistry"),
          ex.getMessage());
      Assert.assertEquals(mockRegistryUnauthorizedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_registryUnauthorizedException_other()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getRegistry()).thenReturn("someregistry");
    Mockito.when(mockHttpResponseException.getStatusCode()).thenReturn(-1); // Unknown

    Mockito.when(mockExecutionException.getCause()).thenReturn(mockRegistryUnauthorizedException);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    Mockito.when(mockBuildConfiguration.getBaseImageRegistry()).thenReturn("someregistry");
    Mockito.when(mockBuildConfiguration.getBaseImageCredentialHelperName())
        .thenReturn("some-credential-helper");

    try {
      testBuildImageStepsRunner.buildImage(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          TEST_HELPFUL_SUGGESTIONS.forCredentialsNotCorrect("someregistry"), ex.getMessage());
      Assert.assertEquals(mockRegistryUnauthorizedException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_executionException_other()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    Throwable throwable = new Throwable();
    Mockito.when(mockExecutionException.getCause()).thenReturn(throwable);
    Mockito.doThrow(mockExecutionException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.none(), ex.getMessage());
      Assert.assertEquals(throwable, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_otherException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    IOException ioException = new IOException();
    Mockito.doThrow(ioException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.none(), ex.getMessage());
      Assert.assertEquals(ioException, ex.getCause());
    }
  }

  @Test
  public void testBuildImage_cacheDirectoryNotOwnedException()
      throws InterruptedException, ExecutionException, CacheDirectoryNotOwnedException,
          CacheMetadataCorruptedException, IOException {
    Path expectedCacheDirectory = Paths.get("some/path");

    CacheDirectoryNotOwnedException mockCacheDirectoryNotOwnedException =
        Mockito.mock(CacheDirectoryNotOwnedException.class);
    Mockito.when(mockCacheDirectoryNotOwnedException.getCacheDirectory())
        .thenReturn(expectedCacheDirectory);
    Mockito.doThrow(mockCacheDirectoryNotOwnedException).when(mockBuildImageSteps).run();

    try {
      testBuildImageStepsRunner.buildImage(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildImageStepsExecutionException ex) {
      Assert.assertEquals(
          TEST_HELPFUL_SUGGESTIONS.forCacheDirectoryNotOwned(expectedCacheDirectory),
          ex.getMessage());
      Assert.assertEquals(mockCacheDirectoryNotOwnedException, ex.getCause());
    }
  }
}
