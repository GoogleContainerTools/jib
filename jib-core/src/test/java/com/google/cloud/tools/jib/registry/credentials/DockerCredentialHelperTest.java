/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DockerCredentialHelperTest {

  private static final String CREDENTIAL_JSON =
      "{\"Username\":\"myusername\",\"Secret\":\"mysecret\"}";

  @Mock private Process process;
  @Mock private Function<List<String>, ProcessBuilder> processBuilderFactory;
  @Mock private ProcessBuilder processBuilder;
  @Mock private ProcessBuilder errorProcessBuilder;

  private final Properties systemProperties = new Properties();

  @Before
  public void setUp() throws IOException {
    systemProperties.put("os.name", "unknown");

    Mockito.when(process.getInputStream())
        .thenReturn(new ByteArrayInputStream(CREDENTIAL_JSON.getBytes(StandardCharsets.UTF_8)));
    Mockito.when(process.getOutputStream()).thenReturn(ByteStreams.nullOutputStream());
    Mockito.when(processBuilder.start()).thenReturn(process);
    Mockito.when(errorProcessBuilder.start())
        .thenThrow(new IOException("No such file or directory"));
  }

  @Test
  public void testDockerCredentialsTemplate_read() throws IOException {
    DockerCredentialHelper.DockerCredentialsTemplate template =
        JsonTemplateMapper.readJson(
            CREDENTIAL_JSON, DockerCredentialHelper.DockerCredentialsTemplate.class);
    Assert.assertEquals("myusername", template.username);
    Assert.assertEquals("mysecret", template.secret);
  }

  @Test
  public void testDockerCredentialsTemplate_canReadNull() throws IOException {
    String input = "{}";
    DockerCredentialHelper.DockerCredentialsTemplate template =
        JsonTemplateMapper.readJson(input, DockerCredentialHelper.DockerCredentialsTemplate.class);
    Assert.assertNull(template.username);
    Assert.assertNull(template.secret);
  }

  @Test
  public void testRetrieve()
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    List<String> command = Arrays.asList(Paths.get("/foo/bar").toString(), "get");
    Mockito.when(processBuilderFactory.apply(command)).thenReturn(processBuilder);

    DockerCredentialHelper credentialHelper =
        dockerCredentialHelper(
            "serverUrl", Paths.get("/foo/bar"), systemProperties, processBuilderFactory);
    Credential credential = credentialHelper.retrieve();
    Assert.assertEquals("myusername", credential.getUsername());
    Assert.assertEquals("mysecret", credential.getPassword());

    Mockito.verify(processBuilderFactory).apply(command);
  }

  @Test
  public void testRetrieveWithEnvironment()
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    List<String> command = Arrays.asList(Paths.get("/foo/bar").toString(), "get");
    Mockito.when(processBuilderFactory.apply(command)).thenReturn(processBuilder);
    Map<String, String> processBuilderEnvironment = Mockito.spy(new HashMap<>());
    Mockito.when(processBuilder.environment()).thenReturn(processBuilderEnvironment);

    Map<String, String> credHelperEnvironment = ImmutableMap.of("ENV_VARIABLE", "Value");
    DockerCredentialHelper credentialHelper =
        dockerCredentialHelper(
            "serverUrl",
            Paths.get("/foo/bar"),
            systemProperties,
            processBuilderFactory,
            credHelperEnvironment);
    Credential credential = credentialHelper.retrieve();
    Assert.assertEquals("myusername", credential.getUsername());
    Assert.assertEquals("mysecret", credential.getPassword());

    Mockito.verify(processBuilderFactory).apply(command);
    Mockito.verify(processBuilderEnvironment).putAll(credHelperEnvironment);
    Assert.assertEquals(1, processBuilderEnvironment.size());
  }

  @Test
  public void testRetrieve_cmdSuffixAddedOnWindows()
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    systemProperties.setProperty("os.name", "WINdows");
    List<String> command = Arrays.asList(Paths.get("/foo/bar.cmd").toString(), "get");
    Mockito.when(processBuilderFactory.apply(command)).thenReturn(processBuilder);

    DockerCredentialHelper credentialHelper =
        dockerCredentialHelper(
            "serverUrl", Paths.get("/foo/bar"), systemProperties, processBuilderFactory);
    Credential credential = credentialHelper.retrieve();
    Assert.assertEquals("myusername", credential.getUsername());
    Assert.assertEquals("mysecret", credential.getPassword());

    Mockito.verify(processBuilderFactory).apply(command);
  }

  @Test
  public void testRetrieve_cmdSuffixAlreadyGivenOnWindows()
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    systemProperties.setProperty("os.name", "WINdows");
    List<String> command = Arrays.asList(Paths.get("/foo/bar.CmD").toString(), "get");
    Mockito.when(processBuilderFactory.apply(command)).thenReturn(processBuilder);

    DockerCredentialHelper credentialHelper =
        dockerCredentialHelper(
            "serverUrl", Paths.get("/foo/bar.CmD"), systemProperties, processBuilderFactory);
    Credential credential = credentialHelper.retrieve();
    Assert.assertEquals("myusername", credential.getUsername());
    Assert.assertEquals("mysecret", credential.getPassword());

    Mockito.verify(processBuilderFactory).apply(command);
  }

  @Test
  public void testRetrieve_exeSuffixAlreadyGivenOnWindows()
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    systemProperties.setProperty("os.name", "WINdows");
    List<String> command = Arrays.asList(Paths.get("/foo/bar.eXE").toString(), "get");
    Mockito.when(processBuilderFactory.apply(command)).thenReturn(processBuilder);

    DockerCredentialHelper credentialHelper =
        dockerCredentialHelper(
            "serverUrl", Paths.get("/foo/bar.eXE"), systemProperties, processBuilderFactory);
    Credential credential = credentialHelper.retrieve();
    Assert.assertEquals("myusername", credential.getUsername());
    Assert.assertEquals("mysecret", credential.getPassword());

    Mockito.verify(processBuilderFactory).apply(command);
  }

  @Test
  public void testRetrieve_cmdSuffixNotFoundOnWindows()
      throws CredentialHelperUnhandledServerUrlException, CredentialHelperNotFoundException,
          IOException {
    systemProperties.setProperty("os.name", "WINdows");
    List<String> errorCmdCommand = Arrays.asList(Paths.get("/foo/bar.cmd").toString(), "get");
    List<String> errorExeCommand = Arrays.asList(Paths.get("/foo/bar.exe").toString(), "get");
    List<String> command = Arrays.asList(Paths.get("/foo/bar").toString(), "get");
    Mockito.when(processBuilderFactory.apply(errorCmdCommand)).thenReturn(errorProcessBuilder);
    Mockito.when(processBuilderFactory.apply(errorExeCommand)).thenReturn(errorProcessBuilder);
    Mockito.when(processBuilderFactory.apply(command)).thenReturn(processBuilder);

    DockerCredentialHelper credentialHelper =
        dockerCredentialHelper(
            "serverUrl", Paths.get("/foo/bar"), systemProperties, processBuilderFactory);
    Credential credential = credentialHelper.retrieve();
    Assert.assertEquals("myusername", credential.getUsername());
    Assert.assertEquals("mysecret", credential.getPassword());

    Mockito.verify(processBuilderFactory).apply(errorCmdCommand);
    Mockito.verify(processBuilderFactory).apply(errorExeCommand);
    Mockito.verify(processBuilderFactory).apply(command);
  }

  @Test
  public void testRetrieve_fileNotFoundExceptionMessage()
      throws CredentialHelperUnhandledServerUrlException, IOException {
    Mockito.when(processBuilderFactory.apply(Mockito.any())).thenReturn(processBuilder);
    Mockito.when(processBuilder.start())
        .thenThrow(
            new IOException(
                "CreateProcess error=2, Das System kann die angegebene Datei nicht finden"));

    DockerCredentialHelper credentialHelper =
        dockerCredentialHelper(
            "serverUrl", Paths.get("/ignored"), systemProperties, processBuilderFactory);
    try {
      credentialHelper.retrieve();
      Assert.fail();
    } catch (CredentialHelperNotFoundException ex) {
      Assert.assertNotNull(ex.getMessage());
    }
  }

  private DockerCredentialHelper dockerCredentialHelper(
      String serverUrl,
      Path credentialHelper,
      Properties properties,
      Function<List<String>, ProcessBuilder> processBuilderFactory) {
    return dockerCredentialHelper(
        serverUrl, credentialHelper, properties, processBuilderFactory, Collections.emptyMap());
  }

  private DockerCredentialHelper dockerCredentialHelper(
      String serverUrl,
      Path credentialHelper,
      Properties properties,
      Function<List<String>, ProcessBuilder> processBuilderFactory,
      Map<String, String> environment) {
    return new DockerCredentialHelper(
        serverUrl, credentialHelper, properties, processBuilderFactory, environment);
  }
}
