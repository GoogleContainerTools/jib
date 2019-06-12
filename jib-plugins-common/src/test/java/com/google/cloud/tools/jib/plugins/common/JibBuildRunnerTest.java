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
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InsecureRegistryException;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.registry.RegistryCredentialsNotSentException;
import java.io.IOException;
import java.net.UnknownHostException;
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
          "messagePrefix",
          "clearCacheCommand",
          ImageReference.of("someregistry", "somerepository", null),
          false,
          ImageReference.of("toRegistry", "torepository", null),
          false,
          "toConfig",
          "toFlag",
          "buildFile");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private JibContainerBuilder mockJibContainerBuilder;
  @Mock private Containerizer mockContainerizer;
  @Mock private RegistryUnauthorizedException mockRegistryUnauthorizedException;
  @Mock private RegistryCredentialsNotSentException mockRegistryCredentialsNotSentException;
  @Mock private HttpResponseException mockHttpResponseException;

  private JibBuildRunner testJibBuildRunner;

  @Before
  public void setUpMocks() {
    testJibBuildRunner = new JibBuildRunner("ignored", "ignored");
  }

  @Test
  public void testBuildImage_pass()
      throws BuildStepsExecutionException, IOException, CacheDirectoryCreationException {
    testJibBuildRunner.build(
        mockJibContainerBuilder, mockContainerizer, logEvent -> {}, TEST_HELPFUL_SUGGESTIONS);
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
      testJibBuildRunner.build(
          mockJibContainerBuilder, mockContainerizer, logEvent -> {}, TEST_HELPFUL_SUGGESTIONS);
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
      testJibBuildRunner.build(
          mockJibContainerBuilder, mockContainerizer, logEvent -> {}, TEST_HELPFUL_SUGGESTIONS);
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
      testJibBuildRunner.build(
          mockJibContainerBuilder, mockContainerizer, logEvent -> {}, TEST_HELPFUL_SUGGESTIONS);
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
      testJibBuildRunner.build(
          mockJibContainerBuilder, mockContainerizer, logEvent -> {}, TEST_HELPFUL_SUGGESTIONS);
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
    Mockito.when(mockRegistryUnauthorizedException.getRegistry()).thenReturn("someregistry");
    Mockito.when(mockRegistryUnauthorizedException.getRepository()).thenReturn("somerepository");
    Mockito.when(mockHttpResponseException.getStatusCode()).thenReturn(-1); // Unknown

    Mockito.doThrow(mockRegistryUnauthorizedException)
        .when(mockJibContainerBuilder)
        .containerize(mockContainerizer);

    try {
      testJibBuildRunner.build(
          mockJibContainerBuilder, mockContainerizer, logEvent -> {}, TEST_HELPFUL_SUGGESTIONS);
      Assert.fail();

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
      testJibBuildRunner.build(
          mockJibContainerBuilder, mockContainerizer, logEvent -> {}, TEST_HELPFUL_SUGGESTIONS);
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
      testJibBuildRunner.build(
          mockJibContainerBuilder, mockContainerizer, logEvent -> {}, TEST_HELPFUL_SUGGESTIONS);
      Assert.fail();

    } catch (BuildStepsExecutionException ex) {
      Assert.assertEquals(TEST_HELPFUL_SUGGESTIONS.none(), ex.getMessage());
    }
  }
}
