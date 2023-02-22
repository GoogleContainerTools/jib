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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Test for {@link GradleRawConfiguration}. */
@RunWith(MockitoJUnitRunner.class)
public class GradleRawConfigurationTest {

  @Mock private MapProperty<String, String> labels;
  @Mock private Property<String> mainClass;

  @Test
  public void testGetters() {
    JibExtension jibExtension = Mockito.mock(JibExtension.class);

    AuthParameters authParameters = Mockito.mock(AuthParameters.class);
    BaseImageParameters baseImageParameters = Mockito.mock(BaseImageParameters.class);
    TargetImageParameters targetImageParameters = Mockito.mock(TargetImageParameters.class);
    ContainerParameters containerParameters = Mockito.mock(ContainerParameters.class);
    DockerClientParameters dockerClientParameters = Mockito.mock(DockerClientParameters.class);
    OutputPathsParameters outputPathsParameters = Mockito.mock(OutputPathsParameters.class);
    CredHelperParameters fromCredHelperParameters = Mockito.mock(CredHelperParameters.class);
    CredHelperParameters toCredHelperParameters = Mockito.mock(CredHelperParameters.class);
    Property<String> filesModificationTime = Mockito.mock(Property.class);
    Property<String> creationTime = Mockito.mock(Property.class);

    Mockito.when(authParameters.getUsername()).thenReturn("user");
    Mockito.when(authParameters.getPassword()).thenReturn("password");
    Mockito.when(authParameters.getAuthDescriptor()).thenReturn("from.auth");
    Mockito.when(authParameters.getUsernameDescriptor()).thenReturn("from.auth.username");
    Mockito.when(authParameters.getPasswordDescriptor()).thenReturn("from.auth.password");

    Mockito.when(jibExtension.getFrom()).thenReturn(baseImageParameters);
    Mockito.when(jibExtension.getTo()).thenReturn(targetImageParameters);
    Mockito.when(jibExtension.getContainer()).thenReturn(containerParameters);
    Mockito.when(jibExtension.getDockerClient()).thenReturn(dockerClientParameters);
    Mockito.when(jibExtension.getOutputPaths()).thenReturn(outputPathsParameters);
    Mockito.when(jibExtension.getAllowInsecureRegistries()).thenReturn(true);

    Mockito.when(fromCredHelperParameters.getHelperName()).thenReturn(Optional.of("gcr"));
    Mockito.when(fromCredHelperParameters.getEnvironment())
        .thenReturn(Collections.singletonMap("ENV_VARIABLE", "Value1"));
    Mockito.when(baseImageParameters.getCredHelper()).thenReturn(fromCredHelperParameters);
    Mockito.when(baseImageParameters.getImage()).thenReturn("openjdk:15");
    Mockito.when(baseImageParameters.getAuth()).thenReturn(authParameters);

    Mockito.when(targetImageParameters.getTags())
        .thenReturn(new HashSet<>(Arrays.asList("additional", "tags")));
    Mockito.when(toCredHelperParameters.getHelperName()).thenReturn(Optional.of("ecr-login"));
    Mockito.when(toCredHelperParameters.getEnvironment())
        .thenReturn(Collections.singletonMap("ENV_VARIABLE", "Value2"));
    Mockito.when(targetImageParameters.getCredHelper()).thenReturn(toCredHelperParameters);

    Mockito.when(containerParameters.getAppRoot()).thenReturn("/app/root");
    Mockito.when(containerParameters.getArgs()).thenReturn(Arrays.asList("--log", "info"));
    Mockito.when(containerParameters.getEntrypoint()).thenReturn(Arrays.asList("java", "Main"));
    Mockito.when(containerParameters.getEnvironment())
        .thenReturn(new HashMap<>(ImmutableMap.of("currency", "dollar")));
    Mockito.when(containerParameters.getJvmFlags()).thenReturn(Arrays.asList("-cp", "."));
    Mockito.when(labels.get()).thenReturn(Collections.singletonMap("unit", "cm"));
    Mockito.when(containerParameters.getLabels()).thenReturn(labels);
    Mockito.when(mainClass.getOrNull()).thenReturn("com.example.Main");
    Mockito.when(containerParameters.getMainClass()).thenReturn(mainClass);
    Mockito.when(containerParameters.getPorts()).thenReturn(Arrays.asList("80/tcp", "0"));
    Mockito.when(containerParameters.getUser()).thenReturn("admin:wheel");
    Mockito.when(containerParameters.getFilesModificationTime()).thenReturn(filesModificationTime);
    Mockito.when(filesModificationTime.get()).thenReturn("2011-12-03T22:42:05Z");
    Mockito.when(containerParameters.getCreationTime()).thenReturn(creationTime);
    Mockito.when(creationTime.get()).thenReturn("2011-12-03T11:42:05Z");

    Mockito.when(dockerClientParameters.getExecutablePath()).thenReturn(Paths.get("test"));
    Mockito.when(dockerClientParameters.getEnvironment())
        .thenReturn(new HashMap<>(ImmutableMap.of("docker", "client")));

    Mockito.when(outputPathsParameters.getDigestPath()).thenReturn(Paths.get("digest/path"));
    Mockito.when(outputPathsParameters.getImageIdPath()).thenReturn(Paths.get("id/path"));
    Mockito.when(outputPathsParameters.getImageJsonPath()).thenReturn(Paths.get("json/path"));
    Mockito.when(outputPathsParameters.getTarPath()).thenReturn(Paths.get("tar/path"));

    GradleRawConfiguration rawConfiguration = new GradleRawConfiguration(jibExtension);

    AuthProperty fromAuth = rawConfiguration.getFromAuth();
    Assert.assertEquals("user", fromAuth.getUsername());
    Assert.assertEquals("password", fromAuth.getPassword());
    Assert.assertEquals("from.auth", fromAuth.getAuthDescriptor());
    Assert.assertEquals("from.auth.username", fromAuth.getUsernameDescriptor());
    Assert.assertEquals("from.auth.password", fromAuth.getPasswordDescriptor());

    Assert.assertTrue(rawConfiguration.getAllowInsecureRegistries());
    Assert.assertEquals("/app/root", rawConfiguration.getAppRoot());
    Assert.assertEquals(Arrays.asList("java", "Main"), rawConfiguration.getEntrypoint().get());
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of("currency", "dollar")), rawConfiguration.getEnvironment());
    Assert.assertEquals("gcr", rawConfiguration.getFromCredHelper().getHelperName().get());
    Assert.assertEquals(
        Collections.singletonMap("ENV_VARIABLE", "Value1"),
        rawConfiguration.getFromCredHelper().getEnvironment());
    Assert.assertEquals("openjdk:15", rawConfiguration.getFromImage().get());
    Assert.assertEquals(Arrays.asList("-cp", "."), rawConfiguration.getJvmFlags());
    Assert.assertEquals(new HashMap<>(ImmutableMap.of("unit", "cm")), rawConfiguration.getLabels());
    Assert.assertEquals("com.example.Main", rawConfiguration.getMainClass().get());
    Assert.assertEquals(Arrays.asList("80/tcp", "0"), rawConfiguration.getPorts());
    Assert.assertEquals(
        Arrays.asList("--log", "info"), rawConfiguration.getProgramArguments().get());
    Assert.assertEquals(
        new HashSet<>(Arrays.asList("additional", "tags")),
        Sets.newHashSet(rawConfiguration.getToTags()));
    Assert.assertEquals("ecr-login", rawConfiguration.getToCredHelper().getHelperName().get());
    Assert.assertEquals(
        Collections.singletonMap("ENV_VARIABLE", "Value2"),
        rawConfiguration.getToCredHelper().getEnvironment());
    Assert.assertEquals("admin:wheel", rawConfiguration.getUser().get());
    Assert.assertEquals("2011-12-03T22:42:05Z", rawConfiguration.getFilesModificationTime());
    Assert.assertEquals("2011-12-03T11:42:05Z", rawConfiguration.getCreationTime());
    Assert.assertEquals(Paths.get("test"), rawConfiguration.getDockerExecutable().get());
    Assert.assertEquals(
        new HashMap<>(ImmutableMap.of("docker", "client")),
        rawConfiguration.getDockerEnvironment());
    Assert.assertEquals(Paths.get("digest/path"), rawConfiguration.getDigestOutputPath());
    Assert.assertEquals(Paths.get("id/path"), rawConfiguration.getImageIdOutputPath());
    Assert.assertEquals(Paths.get("json/path"), rawConfiguration.getImageJsonOutputPath());
    Assert.assertEquals(Paths.get("tar/path"), rawConfiguration.getTarOutputPath());
  }
}
