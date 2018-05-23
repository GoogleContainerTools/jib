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
import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.cloud.tools.jib.builder.BuildSteps;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.CacheDirectoryNotOwnedException;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
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

/** Tests for {@link BuildStepsRunner}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildStepsRunnerTest {

  private static final HelpfulSuggestions TEST_HELPFUL_SUGGESTIONS =
      new HelpfulSuggestions(
          "messagePrefix",
          "clearCacheCommand",
          "baseImageCredHelperConfiguration",
          registry -> "baseImageAuthConfiguration " + registry,
          "targetImageCredHelperConfiguration",
          registry -> "targetImageAuthConfiguration " + registry);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private BuildSteps mockBuildSteps;
  @Mock private SourceFilesConfiguration mockSourceFilesConfiguration;
  @Mock private BuildLogger mockBuildLogger;
  @Mock private RegistryUnauthorizedException mockRegistryUnauthorizedException;
  @Mock private HttpResponseException mockHttpResponseException;
  @Mock private ExecutionException mockExecutionException;
  @Mock private BuildConfiguration mockBuildConfiguration;

  private BuildStepsRunner testBuildImageStepsRunner;

  @Before
  public void setUpMocks() {
    testBuildImageStepsRunner = new BuildStepsRunner(mockBuildSteps);

    Mockito.when(mockBuildSteps.getBuildConfiguration()).thenReturn(mockBuildConfiguration);
    Mockito.when(mockBuildConfiguration.getBuildLogger()).thenReturn(mockBuildLogger);
    Mockito.when(mockBuildSteps.getSourceFilesConfiguration())
        .thenReturn(mockSourceFilesConfiguration);
    Mockito.when(mockSourceFilesConfiguration.getClassesFiles())
        .thenReturn(Collections.emptyList());
    Mockito.when(mockSourceFilesConfiguration.getResourcesFiles())
        .thenReturn(Collections.emptyList());
    Mockito.when(mockSourceFilesConfiguration.getDependenciesFiles())
        .thenReturn(Collections.emptyList());
  }

  @Test
  public void testBuildImage_pass() throws BuildStepsExecutionException {
    testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
  }

  @Test
  public void testBuildImage_cacheMetadataCorruptedException()
      throws InterruptedException, ExecutionException, CacheMetadataCorruptedException, IOException,
          CacheDirectoryNotOwnedException {
    CacheMetadataCorruptedException mockCacheMetadataCorruptedException =
        Mockito.mock(CacheMetadataCorruptedException.class);
    Mockito.doThrow(mockCacheMetadataCorruptedException).when(mockBuildSteps).run();

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
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
          CacheDirectoryNotOwnedException {
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
          CacheDirectoryNotOwnedException {
    Mockito.when(mockRegistryUnauthorizedException.getHttpResponseException())
        .thenReturn(mockHttpResponseException);
    Mockito.when(mockRegistryUnauthorizedException.getRegistry()).thenReturn("someregistry");
    Mockito.when(mockHttpResponseException.getStatusCode()).thenReturn(-1); // Unknown

    Mockito.when(mockExecutionException.getCause()).thenReturn(mockRegistryUnauthorizedException);
    Mockito.doThrow(mockExecutionException).when(mockBuildSteps).run();

    Mockito.when(mockBuildConfiguration.getBaseImageRegistry()).thenReturn("someregistry");

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
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
    Mockito.doThrow(mockExecutionException).when(mockBuildSteps).run();

    Mockito.when(mockBuildConfiguration.getBaseImageRegistry()).thenReturn("someregistry");
    Mockito.when(mockBuildConfiguration.getBaseImageCredentialHelperName())
        .thenReturn("some-credential-helper");

    try {
      testBuildImageStepsRunner.build(TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
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
          CacheDirectoryNotOwnedException {
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
  public void testBuildImage_cacheDirectoryNotOwnedException()
      throws InterruptedException, ExecutionException, CacheDirectoryNotOwnedException,
          CacheMetadataCorruptedException, IOException {
    Path expectedCacheDirectory = Paths.get("some/path");

    CacheDirectoryNotOwnedException mockCacheDirectoryNotOwnedException =
        Mockito.mock(CacheDirectoryNotOwnedException.class);
    Mockito.when(mockCacheDirectoryNotOwnedException.getCacheDirectory())
        .thenReturn(expectedCacheDirectory);
    Mockito.doThrow(mockCacheDirectoryNotOwnedException).when(mockBuildSteps).run();

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
