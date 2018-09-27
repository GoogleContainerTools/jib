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

import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibContainerBuilder}. */
@RunWith(MockitoJUnitRunner.class)
public class JibContainerBuilderTest {

  @Mock private LayerConfiguration mockLayerConfiguration1;
  @Mock private LayerConfiguration mockLayerConfiguration2;
  @Mock private CredentialRetriever mockCredentialRetriever;

  @Test
  public void testToContainerConfiguration_set() throws InvalidImageReferenceException {
    JibContainerBuilder jibContainerBuilder =
        Jib.from("base/image/reference")
            .setEntrypoint(Arrays.asList("entry", "point"))
            .setEnvironment(ImmutableMap.of("name", "value"))
            .setExposedPorts(Arrays.asList(Port.tcp(1234), Port.udp(5678)))
            .setLabels(ImmutableMap.of("key", "value"))
            .setProgramArguments(Arrays.asList("program", "arguments"));

    ContainerConfiguration containerConfiguration = jibContainerBuilder.toContainerConfiguration();
    Assert.assertEquals(Arrays.asList("entry", "point"), containerConfiguration.getEntrypoint());
    Assert.assertEquals(
        ImmutableMap.of("name", "value"), containerConfiguration.getEnvironmentMap());
    Assert.assertEquals(
        Arrays.asList(Port.tcp(1234), Port.udp(5678)), containerConfiguration.getExposedPorts());
    Assert.assertEquals(ImmutableMap.of("key", "value"), containerConfiguration.getLabels());
    Assert.assertEquals(
        Arrays.asList("program", "arguments"), containerConfiguration.getProgramArguments());
  }

  @Test
  public void testToContainerConfiguration_add() {
    JibContainerBuilder jibContainerBuilder =
        Jib.from(ImageReference.of("base", "image", "reference"))
            .setEntrypoint("entry", "point")
            .setEnvironment(ImmutableMap.of("name", "value"))
            .addEnvironmentVariable("environment", "variable")
            .setExposedPorts(Port.tcp(1234), Port.udp(5678))
            .addExposedPort(Port.tcp(1337))
            .setLabels(ImmutableMap.of("key", "value"))
            .addLabel("added", "label")
            .setProgramArguments("program", "arguments");

    ContainerConfiguration containerConfiguration = jibContainerBuilder.toContainerConfiguration();
    Assert.assertEquals(Arrays.asList("entry", "point"), containerConfiguration.getEntrypoint());
    Assert.assertEquals(
        ImmutableMap.of("name", "value", "environment", "variable"),
        containerConfiguration.getEnvironmentMap());
    Assert.assertEquals(
        Arrays.asList(Port.tcp(1234), Port.udp(5678), Port.tcp(1337)),
        containerConfiguration.getExposedPorts());
    Assert.assertEquals(
        ImmutableMap.of("key", "value", "added", "label"), containerConfiguration.getLabels());
    Assert.assertEquals(
        Arrays.asList("program", "arguments"), containerConfiguration.getProgramArguments());
  }

  @Test
  public void testToBuildConfiguration()
      throws InvalidImageReferenceException, CredentialRetrievalException, IOException,
          CacheDirectoryCreationException {
    RegistryImage baseImage =
        RegistryImage.named("base/image").addCredentialRetriever(mockCredentialRetriever);
    RegistryImage targetImage =
        RegistryImage.named(ImageReference.of("gcr.io", "my-project/my-app", null))
            .addCredential("username", "password");

    JibContainerBuilder jibContainerBuilder =
        Jib.from(baseImage)
            .setLayers(Arrays.asList(mockLayerConfiguration1, mockLayerConfiguration2));
    BuildConfiguration buildConfiguration = jibContainerBuilder.toBuildConfiguration(targetImage);

    Assert.assertEquals(
        jibContainerBuilder.toContainerConfiguration(),
        buildConfiguration.getContainerConfiguration());

    Assert.assertEquals(
        baseImage.toImageConfiguration().getImage().toString(),
        buildConfiguration.getBaseImageConfiguration().getImage().toString());
    Assert.assertEquals(
        1, buildConfiguration.getBaseImageConfiguration().getCredentialRetrievers().size());
    Assert.assertSame(
        mockCredentialRetriever,
        buildConfiguration.getBaseImageConfiguration().getCredentialRetrievers().get(0));

    Assert.assertEquals(
        targetImage.toImageConfiguration().getImage().toString(),
        buildConfiguration.getTargetImageConfiguration().getImage().toString());
    Assert.assertEquals(
        1, buildConfiguration.getTargetImageConfiguration().getCredentialRetrievers().size());
    Assert.assertEquals(
        Credential.basic("username", "password"),
        buildConfiguration
            .getTargetImageConfiguration()
            .getCredentialRetrievers()
            .get(0)
            .retrieve()
            .orElseThrow(AssertionError::new));

    Assert.assertEquals(
        Arrays.asList(mockLayerConfiguration1, mockLayerConfiguration2),
        buildConfiguration.getLayerConfigurations());

    Assert.assertEquals("jib-core", buildConfiguration.getToolName());
  }
}
