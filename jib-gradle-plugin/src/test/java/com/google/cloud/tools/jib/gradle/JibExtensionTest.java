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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;

/** Tests for {@link JibExtension}. */
public class JibExtensionTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();

  private JibExtension testJibExtension;
  private Project fakeProject;

  @Before
  public void setUp() {
    fakeProject = ProjectBuilder.builder().build();
    testJibExtension =
        fakeProject
            .getExtensions()
            .create(JibPlugin.JIB_EXTENSION_NAME, JibExtension.class, fakeProject);
  }

  @Test
  public void testFrom() {
    assertThat(testJibExtension.getFrom().getImage()).isNull();
    assertThat(testJibExtension.getFrom().getCredHelper().getHelper()).isNull();

    List<PlatformParameters> defaultPlatforms = testJibExtension.getFrom().getPlatforms().get();
    assertThat(defaultPlatforms).hasSize(1);
    assertThat(defaultPlatforms.get(0).getArchitecture()).isEqualTo("amd64");
    assertThat(defaultPlatforms.get(0).getOs()).isEqualTo("linux");

    testJibExtension.from(
        from -> {
          from.setImage("some image");
          from.setCredHelper("some cred helper");
          from.auth(auth -> auth.setUsername("some username"));
          from.auth(auth -> auth.setPassword("some password"));
          from.platforms(
              platformSpec ->
                  platformSpec.platform(
                      platform -> {
                        platform.setArchitecture("arm");
                        platform.setOs("windows");
                      }));
        });
    assertThat(testJibExtension.getFrom().getImage()).isEqualTo("some image");
    assertThat(testJibExtension.getFrom().getCredHelper().getHelper())
        .isEqualTo("some cred helper");
    assertThat(testJibExtension.getFrom().getAuth().getUsername()).isEqualTo("some username");
    assertThat(testJibExtension.getFrom().getAuth().getPassword()).isEqualTo("some password");

    List<PlatformParameters> platforms = testJibExtension.getFrom().getPlatforms().get();
    assertThat(platforms).hasSize(1);
    assertThat(platforms.get(0).getArchitecture()).isEqualTo("arm");
    assertThat(platforms.get(0).getOs()).isEqualTo("windows");
  }

  @Test
  public void testFromCredHelperClosure() {
    assertThat(testJibExtension.getFrom().getImage()).isNull();
    assertThat(testJibExtension.getFrom().getCredHelper().getHelper()).isNull();

    testJibExtension.from(
        from -> {
          from.setImage("some image");
          from.credHelper(
              credHelper -> {
                credHelper.setHelper("some cred helper");
                credHelper.setEnvironment(Collections.singletonMap("ENV_VARIABLE", "Value"));
              });
        });
    assertThat(testJibExtension.getFrom().getCredHelper().getHelper())
        .isEqualTo("some cred helper");
    assertThat(testJibExtension.getFrom().getCredHelper().getEnvironment())
        .isEqualTo(Collections.singletonMap("ENV_VARIABLE", "Value"));
  }

  @Test
  public void testTo() {
    assertThat(testJibExtension.getTo().getImage()).isNull();
    assertThat(testJibExtension.getTo().getCredHelper().getHelper()).isNull();

    testJibExtension.to(
        to -> {
          to.setImage("some image");
          to.setCredHelper("some cred helper");
          to.auth(auth -> auth.setUsername("some username"));
          to.auth(auth -> auth.setPassword("some password"));
        });
    assertThat(testJibExtension.getTo().getImage()).isEqualTo("some image");
    assertThat(testJibExtension.getTo().getCredHelper().getHelper()).isEqualTo("some cred helper");
    assertThat(testJibExtension.getTo().getAuth().getUsername()).isEqualTo("some username");
    assertThat(testJibExtension.getTo().getAuth().getPassword()).isEqualTo("some password");
  }

  @Test
  public void testToCredHelperClosure() {
    assertThat(testJibExtension.getTo().getImage()).isNull();
    assertThat(testJibExtension.getTo().getCredHelper().getHelper()).isNull();

    testJibExtension.to(
        to -> {
          to.setImage("some image");
          to.credHelper(
              credHelper -> {
                credHelper.setHelper("some cred helper");
                credHelper.setEnvironment(Collections.singletonMap("ENV_VARIABLE", "Value"));
              });
        });
    assertThat(testJibExtension.getTo().getCredHelper().getHelper()).isEqualTo("some cred helper");
    assertThat(testJibExtension.getTo().getCredHelper().getEnvironment())
        .isEqualTo(Collections.singletonMap("ENV_VARIABLE", "Value"));
  }

  @Test
  public void testToTags_noTagsPropertySet() {
    assertThat(testJibExtension.getTo().getTags()).isEmpty();
  }

  @Test
  public void testToTags_containsNullTag() {
    TargetImageParameters testToParameters = generateTargetImageParametersWithTags(null, "tag1");
    Exception exception =
        assertThrows(IllegalArgumentException.class, () -> testToParameters.getTags());
    assertThat(exception).hasMessageThat().isEqualTo("jib.to.tags contains null tag");
  }

  @Test
  public void testToTags_containsEmptyTag() {
    TargetImageParameters testToParameters = generateTargetImageParametersWithTags("", "tag1");
    Exception exception =
        assertThrows(IllegalArgumentException.class, () -> testToParameters.getTags());
    assertThat(exception).hasMessageThat().isEqualTo("jib.to.tags contains empty tag");
  }

  @Test
  public void testContainer() {
    assertThat(testJibExtension.getContainer().getJvmFlags()).isEmpty();
    assertThat(testJibExtension.getContainer().getEnvironment()).isEmpty();
    assertThat(testJibExtension.getContainer().getExtraClasspath()).isEmpty();
    assertThat(testJibExtension.getContainer().getExpandClasspathDependencies()).isFalse();
    assertThat(testJibExtension.getContainer().getMainClass()).isNull();
    assertThat(testJibExtension.getContainer().getArgs()).isNull();
    assertThat(testJibExtension.getContainer().getFormat()).isSameInstanceAs(ImageFormat.Docker);
    assertThat(testJibExtension.getContainer().getPorts()).isEmpty();
    assertThat(testJibExtension.getContainer().getLabels().get()).isEmpty();
    assertThat(testJibExtension.getContainer().getAppRoot()).isEmpty();
    assertThat(testJibExtension.getContainer().getFilesModificationTime().get())
        .isEqualTo("EPOCH_PLUS_SECOND");
    assertThat(testJibExtension.getContainer().getCreationTime().get()).isEqualTo("EPOCH");

    testJibExtension.container(
        container -> {
          container.setJvmFlags(Arrays.asList("jvmFlag1", "jvmFlag2"));
          container.setEnvironment(ImmutableMap.of("var1", "value1", "var2", "value2"));
          container.setEntrypoint(Arrays.asList("foo", "bar", "baz"));
          container.setExtraClasspath(Arrays.asList("/d1", "/d2", "/d3"));
          container.setExpandClasspathDependencies(true);
          container.setMainClass("mainClass");
          container.setArgs(Arrays.asList("arg1", "arg2", "arg3"));
          container.setPorts(Arrays.asList("1000", "2000-2010", "3000"));
          container.setFormat(ImageFormat.OCI);
          container.setAppRoot("some invalid appRoot value");
          container.getFilesModificationTime().set("some invalid time value");
          container.getCreationTime().set("some other invalid time value");
        });
    ContainerParameters container = testJibExtension.getContainer();
    assertThat(container.getEntrypoint()).containsExactly("foo", "bar", "baz").inOrder();
    assertThat(container.getJvmFlags()).containsExactly("jvmFlag1", "jvmFlag2").inOrder();
    assertThat(container.getEnvironment())
        .containsExactly("var1", "value1", "var2", "value2")
        .inOrder();
    assertThat(container.getExtraClasspath()).containsExactly("/d1", "/d2", "/d3").inOrder();
    assertThat(testJibExtension.getContainer().getExpandClasspathDependencies()).isTrue();
    assertThat(testJibExtension.getContainer().getMainClass()).isEqualTo("mainClass");
    assertThat(container.getArgs()).containsExactly("arg1", "arg2", "arg3").inOrder();
    assertThat(container.getPorts()).containsExactly("1000", "2000-2010", "3000").inOrder();
    assertThat(container.getFormat()).isSameInstanceAs(ImageFormat.OCI);
    assertThat(container.getAppRoot()).isEqualTo("some invalid appRoot value");
    assertThat(container.getFilesModificationTime().get()).isEqualTo("some invalid time value");
    assertThat(container.getCreationTime().get()).isEqualTo("some other invalid time value");
    testJibExtension.container(
        extensionContainer -> {
          extensionContainer.getFilesModificationTime().set((String) null);
          extensionContainer.getCreationTime().set((String) null);
        });
    container = testJibExtension.getContainer();
    assertThat(container.getFilesModificationTime().get()).isEqualTo("EPOCH_PLUS_SECOND");
    assertThat(container.getCreationTime().get()).isEqualTo("EPOCH");
  }

  @Test
  public void testSetFormat() {
    testJibExtension.container(
        container -> {
          container.setFormat("OCI");
        });
    ContainerParameters container = testJibExtension.getContainer();
    assertThat(container.getFormat()).isSameInstanceAs(ImageFormat.OCI);
  }

  @Test
  public void testContainerizingMode() {
    assertThat(testJibExtension.getContainerizingMode()).isEqualTo("exploded");
  }

  @Test
  public void testConfigurationName() {
    assertThat(testJibExtension.getConfigurationName().get()).isEqualTo("runtimeClasspath");
  }

  @Test
  public void testExtraDirectories_default() {
    assertThat(testJibExtension.getExtraDirectories().getPaths()).hasSize(1);
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("src/main/jib"));
    assertThat(testJibExtension.getExtraDirectories().getPermissions().get()).isEmpty();
  }

  @Test
  public void testExtraDirectories() {
    testJibExtension.extraDirectories(
        extraDirectories -> {
          extraDirectories.setPaths("test/path");
        });

    assertThat(testJibExtension.getExtraDirectories().getPaths()).hasSize(1);
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("test/path"));
  }

  @Test
  public void testExtraDirectories_lazyEvaluation_setFromInto() {
    testJibExtension.extraDirectories(
        extraDirectories ->
            extraDirectories.paths(
                paths -> {
                  ProviderFactory providerFactory = fakeProject.getProviders();
                  Provider<Object> from = providerFactory.provider(() -> "test/path");
                  Provider<String> into = providerFactory.provider(() -> "/target");
                  paths.path(
                      path -> {
                        path.setFrom(from);
                        path.setInto(into);
                      });
                }));

    assertThat(testJibExtension.getExtraDirectories().getPaths()).hasSize(1);
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("test/path"));
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getInto())
        .isEqualTo("/target");
  }

  @Test
  public void testExtraDirectories_withTarget() {
    testJibExtension.extraDirectories(
        extraDirectories ->
            extraDirectories.paths(
                paths -> {
                  paths.path(
                      path -> {
                        path.setFrom("test/path");
                        path.setInto("/");
                      });
                  paths.path(
                      path -> {
                        path.setFrom("another/path");
                        path.setInto("/non/default/target");
                      });
                }));

    assertThat(testJibExtension.getExtraDirectories().getPaths()).hasSize(2);
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("test/path"));
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getInto()).isEqualTo("/");
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(1).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("another/path"));
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(1).getInto())
        .isEqualTo("/non/default/target");
  }

  @Test
  public void testExtraDirectories_fileForPaths() {
    testJibExtension.extraDirectories(
        extraDirectories -> extraDirectories.setPaths(Paths.get("test/path").toFile()));
    assertThat(testJibExtension.getExtraDirectories().getPaths()).hasSize(1);
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("test/path"));
  }

  @Test
  public void testExtraDirectories_stringListForPaths() {
    testJibExtension.extraDirectories(
        extraDirectories -> extraDirectories.setPaths(Arrays.asList("test/path", "another/path")));

    assertThat(testJibExtension.getExtraDirectories().getPaths()).hasSize(2);
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("test/path"));
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(1).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("another/path"));
  }

  @Test
  public void testExtraDirectories_lazyEvaluation_StringListForPaths() {
    testJibExtension.extraDirectories(
        extraDirectories -> {
          ProviderFactory providerFactory = fakeProject.getProviders();
          Provider<Object> paths =
              providerFactory.provider(() -> Arrays.asList("test/path", "another/path"));
          extraDirectories.setPaths(paths);
        });

    assertThat(testJibExtension.getExtraDirectories().getPaths()).hasSize(2);
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("test/path"));
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(1).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("another/path"));
  }

  @Test
  public void testExtraDirectories_fileListForPaths() {
    testJibExtension.extraDirectories(
        extraDirectories ->
            extraDirectories.setPaths(
                Arrays.asList(
                    Paths.get("test", "path").toFile(), Paths.get("another", "path").toFile())));

    assertThat(testJibExtension.getExtraDirectories().getPaths()).hasSize(2);
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("test/path"));
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(1).getFrom())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("another/path"));
  }

  @Test
  public void testDockerClient() {
    testJibExtension.dockerClient(
        dockerClient -> {
          dockerClient.setExecutable("test-executable");
          dockerClient.setEnvironment(ImmutableMap.of("key1", "val1", "key2", "val2"));
        });

    assertThat(testJibExtension.getDockerClient().getExecutablePath())
        .isEqualTo(Paths.get("test-executable"));
    assertThat(testJibExtension.getDockerClient().getEnvironment())
        .containsExactly("key1", "val1", "key2", "val2")
        .inOrder();
  }

  @Test
  public void testOutputFiles() {
    testJibExtension.outputPaths(
        outputFiles -> {
          outputFiles.setDigest("/path/to/digest");
          outputFiles.setImageId("/path/to/id");
          outputFiles.setTar("path/to/tar");
        });

    assertThat(testJibExtension.getOutputPaths().getDigestPath())
        .isEqualTo(Paths.get("/path/to/digest").toAbsolutePath());
    assertThat(testJibExtension.getOutputPaths().getImageIdPath())
        .isEqualTo(Paths.get("/path/to/id").toAbsolutePath());
    assertThat(testJibExtension.getOutputPaths().getTarPath())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("path/to/tar"));
  }

  @Test
  public void testSkaffold() {
    testJibExtension.skaffold(
        skaffold -> {
          skaffold.sync(sync -> sync.setExcludes(fakeProject.files("sync1", "sync2")));
          skaffold.watch(
              watch -> {
                watch.setBuildIncludes(ImmutableList.of("watch1", "watch2"));
                watch.setIncludes("watch3");
                watch.setExcludes(ImmutableList.of(new File("watch4")));
              });
        });
    Path root = fakeProject.getRootDir().toPath();
    assertThat(testJibExtension.getSkaffold().getSync().getExcludes())
        .containsExactly(
            root.resolve("sync1").toAbsolutePath(), root.resolve("sync2").toAbsolutePath());
    assertThat(testJibExtension.getSkaffold().getWatch().getBuildIncludes())
        .containsExactly(
            root.resolve("watch1").toAbsolutePath(), root.resolve("watch2").toAbsolutePath());
    assertThat(testJibExtension.getSkaffold().getWatch().getIncludes())
        .containsExactly(root.resolve("watch3").toAbsolutePath());
    assertThat(testJibExtension.getSkaffold().getWatch().getExcludes())
        .containsExactly(root.resolve("watch4").toAbsolutePath());
  }

  @Test
  public void testProperties() {
    System.setProperties(new Properties());

    System.setProperty("jib.from.image", "fromImage");
    assertThat(testJibExtension.getFrom().getImage()).isEqualTo("fromImage");
    System.setProperty("jib.from.credHelper", "credHelper");
    assertThat(testJibExtension.getFrom().getCredHelper().getHelper()).isEqualTo("credHelper");

    System.setProperty("jib.from.platforms", "linux/amd64,darwin/arm64");
    List<PlatformParameters> platforms = testJibExtension.getFrom().getPlatforms().get();
    assertThat(platforms).hasSize(2);
    assertThat(platforms.get(0).getOs()).isEqualTo("linux");
    assertThat(platforms.get(0).getArchitecture()).isEqualTo("amd64");
    assertThat(platforms.get(1).getOs()).isEqualTo("darwin");
    assertThat(platforms.get(1).getArchitecture()).isEqualTo("arm64");

    System.setProperty("jib.to.image", "toImage");
    assertThat(testJibExtension.getTo().getImage()).isEqualTo("toImage");
    System.setProperty("jib.to.tags", "tag1,tag2,tag3");
    assertThat(testJibExtension.getTo().getTags()).containsExactly("tag1", "tag2", "tag3");
    System.setProperty("jib.to.credHelper", "credHelper");
    assertThat(testJibExtension.getTo().getCredHelper().getHelper()).isEqualTo("credHelper");

    System.setProperty("jib.container.appRoot", "appRoot");
    assertThat(testJibExtension.getContainer().getAppRoot()).isEqualTo("appRoot");
    System.setProperty("jib.container.args", "arg1,arg2,arg3");
    assertThat(testJibExtension.getContainer().getArgs())
        .containsExactly("arg1", "arg2", "arg3")
        .inOrder();
    System.setProperty("jib.container.entrypoint", "entry1,entry2,entry3");
    assertThat(testJibExtension.getContainer().getEntrypoint())
        .containsExactly("entry1", "entry2", "entry3")
        .inOrder();
    System.setProperty("jib.container.environment", "env1=val1,env2=val2");
    assertThat(testJibExtension.getContainer().getEnvironment())
        .containsExactly("env1", "val1", "env2", "val2")
        .inOrder();
    System.setProperty("jib.container.extraClasspath", "/d1,/d2,/d3");
    assertThat(testJibExtension.getContainer().getExtraClasspath())
        .containsExactly("/d1", "/d2", "/d3")
        .inOrder();
    System.setProperty("jib.container.expandClasspathDependencies", "true");
    assertTrue(testJibExtension.getContainer().getExpandClasspathDependencies());
    System.setProperty("jib.container.format", "OCI");
    assertThat(testJibExtension.getContainer().getFormat()).isSameInstanceAs(ImageFormat.OCI);
    System.setProperty("jib.container.jvmFlags", "flag1,flag2,flag3");
    assertThat(testJibExtension.getContainer().getJvmFlags())
        .containsExactly("flag1", "flag2", "flag3")
        .inOrder();
    System.setProperty("jib.container.labels", "label1=val1,label2=val2");
    assertThat(testJibExtension.getContainer().getLabels().get())
        .containsExactly("label1", "val1", "label2", "val2")
        .inOrder();
    System.setProperty("jib.container.mainClass", "main");
    assertThat(testJibExtension.getContainer().getMainClass()).isEqualTo("main");
    System.setProperty("jib.container.ports", "port1,port2,port3");
    assertThat(testJibExtension.getContainer().getPorts())
        .containsExactly("port1", "port2", "port3")
        .inOrder();
    System.setProperty("jib.container.user", "myUser");
    assertThat(testJibExtension.getContainer().getUser()).isEqualTo("myUser");
    System.setProperty("jib.container.filesModificationTime", "2011-12-03T22:42:05Z");
    testJibExtension
        .getContainer()
        .getFilesModificationTime()
        .set("property should override value");
    assertThat(testJibExtension.getContainer().getFilesModificationTime().get())
        .isEqualTo("2011-12-03T22:42:05Z");
    System.setProperty("jib.container.creationTime", "2011-12-03T11:42:05Z");
    testJibExtension.getContainer().getCreationTime().set("property should override value");
    assertThat(testJibExtension.getContainer().getCreationTime().get())
        .isEqualTo("2011-12-03T11:42:05Z");
    System.setProperty("jib.containerizingMode", "packaged");
    assertThat(testJibExtension.getContainerizingMode()).isEqualTo("packaged");

    System.setProperty("jib.extraDirectories.paths", "/foo,/bar/baz");
    assertThat(testJibExtension.getExtraDirectories().getPaths()).hasSize(2);
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(0).getFrom())
        .isEqualTo(Paths.get("/foo"));
    assertThat(testJibExtension.getExtraDirectories().getPaths().get(1).getFrom())
        .isEqualTo(Paths.get("/bar/baz"));
    System.setProperty("jib.extraDirectories.permissions", "/foo/bar=707,/baz=456");
    assertThat(testJibExtension.getExtraDirectories().getPermissions().get())
        .containsExactly("/foo/bar", "707", "/baz", "456")
        .inOrder();

    System.setProperty("jib.dockerClient.executable", "test-exec");
    assertThat(testJibExtension.getDockerClient().getExecutablePath())
        .isEqualTo(Paths.get("test-exec"));
    System.setProperty("jib.dockerClient.environment", "env1=val1,env2=val2");
    assertThat(testJibExtension.getDockerClient().getEnvironment())
        .containsExactly("env1", "val1", "env2", "val2")
        .inOrder();
  }

  @Test
  public void testLazyPropertiesFinalization() {
    Property<String> filesModificationTime =
        testJibExtension.getContainer().getFilesModificationTime();
    filesModificationTime.set((String) null);
    filesModificationTime.finalizeValue();
    System.setProperty("jib.container.filesModificationTime", "EPOCH_PLUS_SECOND");
    assertThat(testJibExtension.getContainer().getFilesModificationTime().get())
        .isEqualTo("EPOCH_PLUS_SECOND");
    Property<String> creationTime = testJibExtension.getContainer().getCreationTime();
    creationTime.set((String) null);
    creationTime.finalizeValue();
    System.setProperty("jib.container.creationTime", "EPOCH");
    assertThat(testJibExtension.getContainer().getCreationTime().get()).isEqualTo("EPOCH");
  }

  @Test
  public void testSystemPropertiesWithInvalidPlatform() {
    System.setProperty("jib.from.platforms", "linux /amd64");
    assertThrows(IllegalArgumentException.class, testJibExtension.getFrom()::getPlatforms);
  }

  @Test
  public void testPropertiesOutputPaths() {
    System.setProperties(new Properties());
    // Absolute paths
    System.setProperty("jib.outputPaths.digest", "/digest/path");
    assertThat(testJibExtension.getOutputPaths().getDigestPath())
        .isEqualTo(Paths.get("/digest/path").toAbsolutePath());
    System.setProperty("jib.outputPaths.imageId", "/id/path");
    assertThat(testJibExtension.getOutputPaths().getImageIdPath())
        .isEqualTo(Paths.get("/id/path").toAbsolutePath());
    System.setProperty("jib.outputPaths.tar", "/tar/path");
    assertThat(testJibExtension.getOutputPaths().getTarPath())
        .isEqualTo(Paths.get("/tar/path").toAbsolutePath());
    // Relative paths
    System.setProperty("jib.outputPaths.digest", "digest/path");
    assertThat(testJibExtension.getOutputPaths().getDigestPath())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("digest/path"));
    System.setProperty("jib.outputPaths.imageId", "id/path");
    assertThat(testJibExtension.getOutputPaths().getImageIdPath())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("id/path"));
    System.setProperty("jib.outputPaths.tar", "tar/path");
    assertThat(testJibExtension.getOutputPaths().getTarPath())
        .isEqualTo(fakeProject.getProjectDir().toPath().resolve("tar/path"));
    System.setProperty("jib.configurationName", "myConfiguration");
    assertThat(testJibExtension.getConfigurationName().get()).isEqualTo("myConfiguration");
  }

  private TargetImageParameters generateTargetImageParametersWithTags(String... tags) {
    HashSet<String> set = new HashSet<>(Arrays.asList(tags));
    testJibExtension.to(to -> to.setTags(set));
    return testJibExtension.getTo();
  }
}
