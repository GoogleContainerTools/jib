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
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.InsecureRegistryException;
import com.google.cloud.tools.jib.registry.RegistryCredentialsNotSentException;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import java.io.IOException;
import java.net.UnknownHostException;
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
          ImageReference.of("someregistry", "somerepository", null),
          false,
          "baseImageCredHelperConfiguration",
          registry -> "baseImageAuthConfiguration " + registry,
          ImageReference.of("toRegistry", "torepository", null),
          false,
          "targetImageCredHelperConfiguration",
          registry -> "targetImageAuthConfiguration " + registry,
          "toConfig",
          "toFlag",
          "buildFile");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private JibContainerBuilder mockJibContainerBuilder;
  @Mock private Containerizer mockContainerizer;
  @Mock private EventDispatcher mockEventDispatcher;
  @Mock private RegistryUnauthorizedException mockRegistryUnauthorizedException;
  @Mock private RegistryCredentialsNotSentException mockRegistryCredentialsNotSentException;
  @Mock private HttpResponseException mockHttpResponseException;

  private BuildStepsRunner testBuildImageStepsRunner;

  @Before
  public void setUpMocks() {
    testBuildImageStepsRunner = new BuildStepsRunner("ignored", "ignored");
  }

  @Test
  public void testBuildImage_pass()
      throws BuildStepsExecutionException, IOException, CacheDirectoryCreationException {
    testBuildImageStepsRunner.build(
        mockJibContainerBuilder,
        mockContainerizer,
        mockEventDispatcher,
        Collections.emptyList(),
        TEST_HELPFUL_SUGGESTIONS);
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
      testBuildImageStepsRunner.build(
          mockJibContainerBuilder,
          mockContainerizer,
          mockEventDispatcher,
          Collections.emptyList(),
          TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

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
      testBuildImageStepsRunner.build(
          mockJibContainerBuilder,
          mockContainerizer,
          mockEventDispatcher,
          Collections.emptyList(),
          TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

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
      testBuildImageStepsRunner.build(
          mockJibContainerBuilder,
          mockContainerizer,
          mockEventDispatcher,
          Collections.emptyList(),
          TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

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
      testBuildImageStepsRunner.build(
          mockJibContainerBuilder,
          mockContainerizer,
          mockEventDispatcher,
          Collections.emptyList(),
          TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

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
    Mockito.when(mockRegistryUnauthorizedException.getRegistry()).thenReturn("someregistry");
    Mockito.when(mockRegistryUnauthorizedException.getRepository()).thenReturn("somerepository");
    Mockito.when(mockHttpResponseException.getStatusCode()).thenReturn(-1); // Unknown

    Mockito.doThrow(mockRegistryUnauthorizedException)
        .when(mockJibContainerBuilder)
        .containerize(mockContainerizer);

    try {
      testBuildImageStepsRunner.build(
          mockJibContainerBuilder,
          mockContainerizer,
          mockEventDispatcher,
          Collections.emptyList(),
          TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(
          TEST_HELPFUL_SUGGESTIONS.forNoCredentialsDefined("someregistry", "somerepository"),
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
      testBuildImageStepsRunner.build(
          mockJibContainerBuilder,
          mockContainerizer,
          mockEventDispatcher,
          Collections.emptyList(),
          TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.forCredentialsNotSent(), ex.getMessage());
    }
  }

  @Test
  public void testBuildImage_other()
      throws InterruptedException, IOException, CacheDirectoryCreationException, RegistryException,
          ExecutionException {
    RegistryException registryException = new RegistryException("messagePrefix");
    Mockito.doThrow(registryException)
        .when(mockJibContainerBuilder)
        .containerize(mockContainerizer);

    try {
      testBuildImageStepsRunner.build(
          mockJibContainerBuilder,
          mockContainerizer,
          mockEventDispatcher,
          Collections.emptyList(),
          TEST_HELPFUL_SUGGESTIONS);
      Assert.fail("buildImage should have thrown an exception");

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.none(), ex.getMessage());
    }
  }
}
