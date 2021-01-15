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

import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
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
    Assert.assertNull(testJibExtension.getFrom().getImage());
    Assert.assertNull(testJibExtension.getFrom().getCredHelper());

    List<PlatformParameters> defaultPlatforms = testJibExtension.getFrom().getPlatforms().get();
    Assert.assertEquals(1, defaultPlatforms.size());
    Assert.assertEquals("amd64", defaultPlatforms.get(0).getArchitecture());
    Assert.assertEquals("linux", defaultPlatforms.get(0).getOs());

    testJibExtension.from(
        from -> {
          from.setImage("some image");
          from.setCredHelper("some cred helper");
          from.auth(auth -> auth.setUsername("some username"));
          from.auth(auth -> auth.setPassword("some password"));
          from.platforms(
              platformSpec -> {
                platformSpec.platform(
                    platform -> {
                      platform.setArchitecture("arm");
                      platform.setOs("windows");
                    });
              });
        });
    Assert.assertEquals("some image", testJibExtension.getFrom().getImage());
    Assert.assertEquals("some cred helper", testJibExtension.getFrom().getCredHelper());
    Assert.assertEquals("some username", testJibExtension.getFrom().getAuth().getUsername());
    Assert.assertEquals("some password", testJibExtension.getFrom().getAuth().getPassword());

    List<PlatformParameters> platforms = testJibExtension.getFrom().getPlatforms().get();
    Assert.assertEquals(1, platforms.size());
    Assert.assertEquals("arm", platforms.get(0).getArchitecture());
    Assert.assertEquals("windows", platforms.get(0).getOs());
  }

  @Test
  public void testTo() {
    Assert.assertNull(testJibExtension.getTo().getImage());
    Assert.assertNull(testJibExtension.getTo().getCredHelper());

    testJibExtension.to(
        to -> {
          to.setImage("some image");
          to.setCredHelper("some cred helper");
          to.auth(auth -> auth.setUsername("some username"));
          to.auth(auth -> auth.setPassword("some password"));
        });
    Assert.assertEquals("some image", testJibExtension.getTo().getImage());
    Assert.assertEquals("some cred helper", testJibExtension.getTo().getCredHelper());
    Assert.assertEquals("some username", testJibExtension.getTo().getAuth().getUsername());
    Assert.assertEquals("some password", testJibExtension.getTo().getAuth().getPassword());
  }

  @Test
  public void testToTags_noTagsPropertySet() {
    Assert.assertEquals(Collections.emptySet(), testJibExtension.getTo().getTags());
  }

  @Test
  public void testToTags_containsNullTag() {
    TargetImageParameters testToParameters = generateTargetImageParametersWithTags(null, "tag1");
    try {
      testToParameters.getTags();
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("jib.to.tags contains null tag", ex.getMessage());
    }
  }

  @Test
  public void testToTags_containsEmptyTag() {
    TargetImageParameters testToParameters = generateTargetImageParametersWithTags("", "tag1");
    try {
      testToParameters.getTags();
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("jib.to.tags contains empty tag", ex.getMessage());
    }
  }

  @Test
  public void testContainer() {
    Assert.assertEquals(Collections.emptyList(), testJibExtension.getContainer().getJvmFlags());
    Assert.assertEquals(Collections.emptyMap(), testJibExtension.getContainer().getEnvironment());
    Assert.assertEquals(
        Collections.emptyList(), testJibExtension.getContainer().getExtraClasspath());
    Assert.assertFalse(testJibExtension.getContainer().getExpandClasspathDependencies());
    Assert.assertNull(testJibExtension.getContainer().getMainClass());
    Assert.assertNull(testJibExtension.getContainer().getArgs());
    Assert.assertSame(ImageFormat.Docker, testJibExtension.getContainer().getFormat());
    Assert.assertEquals(Collections.emptyList(), testJibExtension.getContainer().getPorts());
    Assert.assertEquals(Collections.emptyMap(), testJibExtension.getContainer().getLabels());
    Assert.assertEquals("", testJibExtension.getContainer().getAppRoot());
    Assert.assertEquals(
        "EPOCH_PLUS_SECOND", testJibExtension.getContainer().getFilesModificationTime());
    Assert.assertEquals("EPOCH", testJibExtension.getContainer().getCreationTime());

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
          container.setLabels(ImmutableMap.of("label1", "value1", "label2", "value2"));
          container.setFormat(ImageFormat.OCI);
          container.setAppRoot("some invalid appRoot value");
          container.setFilesModificationTime("some invalid time value");
        });
    ContainerParameters container = testJibExtension.getContainer();
    Assert.assertEquals(Arrays.asList("foo", "bar", "baz"), container.getEntrypoint());
    Assert.assertEquals(Arrays.asList("jvmFlag1", "jvmFlag2"), container.getJvmFlags());
    Assert.assertEquals(
        ImmutableMap.of("var1", "value1", "var2", "value2"), container.getEnvironment());
    Assert.assertEquals(ImmutableList.of("/d1", "/d2", "/d3"), container.getExtraClasspath());
    Assert.assertTrue(testJibExtension.getContainer().getExpandClasspathDependencies());
    Assert.assertEquals("mainClass", testJibExtension.getContainer().getMainClass());
    Assert.assertEquals(Arrays.asList("arg1", "arg2", "arg3"), container.getArgs());
    Assert.assertEquals(Arrays.asList("1000", "2000-2010", "3000"), container.getPorts());
    Assert.assertEquals(
        ImmutableMap.of("label1", "value1", "label2", "value2"), container.getLabels());
    Assert.assertSame(ImageFormat.OCI, container.getFormat());
    Assert.assertEquals("some invalid appRoot value", container.getAppRoot());
    Assert.assertEquals("some invalid time value", container.getFilesModificationTime());
  }

  @Test
  public void testContainerizingMode() {
    Assert.assertEquals("exploded", testJibExtension.getContainerizingMode());
  }

  @Test
  public void testExtraDirectories_default() {
    Assert.assertEquals(1, testJibExtension.getExtraDirectories().getPaths().size());
    Assert.assertEquals(
        Paths.get(fakeProject.getProjectDir().getPath(), "src", "main", "jib"),
        testJibExtension.getExtraDirectories().getPaths().get(0).getFrom());
    Assert.assertEquals(
        Collections.emptyMap(), testJibExtension.getExtraDirectories().getPermissions());
  }

  @Test
  public void testExtraDirectories() {
    testJibExtension.extraDirectories(
        extraDirectories -> {
          extraDirectories.setPaths("test/path");
          extraDirectories.setPermissions(ImmutableMap.of("file1", "123", "file2", "456"));
        });

    Assert.assertEquals(1, testJibExtension.getExtraDirectories().getPaths().size());
    Assert.assertEquals(
        Paths.get(fakeProject.getProjectDir().getPath(), "test", "path"),
        testJibExtension.getExtraDirectories().getPaths().get(0).getFrom());
    Assert.assertEquals(
        ImmutableMap.of("file1", "123", "file2", "456"),
        testJibExtension.getExtraDirectories().getPermissions());
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

    Assert.assertEquals(2, testJibExtension.getExtraDirectories().getPaths().size());
    Assert.assertEquals(
        Paths.get(fakeProject.getProjectDir().getPath(), "test", "path"),
        testJibExtension.getExtraDirectories().getPaths().get(0).getFrom());
    Assert.assertEquals("/", testJibExtension.getExtraDirectories().getPaths().get(0).getInto());
    Assert.assertEquals(
        Paths.get(fakeProject.getProjectDir().getPath(), "another", "path"),
        testJibExtension.getExtraDirectories().getPaths().get(1).getFrom());
    Assert.assertEquals(
        "/non/default/target", testJibExtension.getExtraDirectories().getPaths().get(1).getInto());
  }

  @Test
  public void testExtraDirectories_fileForPaths() {
    testJibExtension.extraDirectories(
        extraDirectories -> extraDirectories.setPaths(Paths.get("test", "path").toFile()));
    Assert.assertEquals(1, testJibExtension.getExtraDirectories().getPaths().size());
    Assert.assertEquals(
        Paths.get(fakeProject.getProjectDir().getPath(), "test", "path"),
        testJibExtension.getExtraDirectories().getPaths().get(0).getFrom());
  }

  @Test
  public void testExtraDirectories_stringListForPaths() {
    testJibExtension.extraDirectories(
        extraDirectories -> extraDirectories.setPaths(Arrays.asList("test/path", "another/path")));

    Assert.assertEquals(2, testJibExtension.getExtraDirectories().getPaths().size());
    Assert.assertEquals(
        Paths.get(fakeProject.getProjectDir().getPath(), "test", "path"),
        testJibExtension.getExtraDirectories().getPaths().get(0).getFrom());
    Assert.assertEquals(
        Paths.get(fakeProject.getProjectDir().getPath(), "another", "path"),
        testJibExtension.getExtraDirectories().getPaths().get(1).getFrom());
  }

  @Test
  public void testExtraDirectories_fileListForPaths() {
    testJibExtension.extraDirectories(
        extraDirectories -> {
          extraDirectories.setPaths(
              Arrays.asList(
                  Paths.get("test", "path").toFile(), Paths.get("another", "path").toFile()));
        });

    Assert.assertEquals(2, testJibExtension.getExtraDirectories().getPaths().size());
    Assert.assertEquals(
        Paths.get(fakeProject.getProjectDir().getPath(), "test", "path"),
        testJibExtension.getExtraDirectories().getPaths().get(0).getFrom());
    Assert.assertEquals(
        Paths.get(fakeProject.getProjectDir().getPath(), "another", "path"),
        testJibExtension.getExtraDirectories().getPaths().get(1).getFrom());
  }

  @Test
  public void testDockerClient() {
    testJibExtension.dockerClient(
        dockerClient -> {
          dockerClient.setExecutable("test-executable");
          dockerClient.setEnvironment(ImmutableMap.of("key1", "val1", "key2", "val2"));
        });

    Assert.assertEquals(
        Paths.get("test-executable"), testJibExtension.getDockerClient().getExecutablePath());
    Assert.assertEquals(
        ImmutableMap.of("key1", "val1", "key2", "val2"),
        testJibExtension.getDockerClient().getEnvironment());
  }

  @Test
  public void testOutputFiles() {
    testJibExtension.outputPaths(
        outputFiles -> {
          outputFiles.setDigest("/path/to/digest");
          outputFiles.setImageId("/path/to/id");
          outputFiles.setTar("path/to/tar");
        });

    Assert.assertEquals(
        Paths.get("/path/to/digest").toAbsolutePath(),
        testJibExtension.getOutputPaths().getDigestPath());
    Assert.assertEquals(
        Paths.get("/path/to/id").toAbsolutePath(),
        testJibExtension.getOutputPaths().getImageIdPath());
    Assert.assertEquals(
        fakeProject.getProjectDir().toPath().resolve(Paths.get("path/to/tar")),
        testJibExtension.getOutputPaths().getTarPath());
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
    Assert.assertEquals(
        ImmutableSet.of(
            root.resolve("sync1").toAbsolutePath(), root.resolve("sync2").toAbsolutePath()),
        testJibExtension.getSkaffold().getSync().getExcludes());
    Assert.assertEquals(
        ImmutableSet.of(
            root.resolve("watch1").toAbsolutePath(), root.resolve("watch2").toAbsolutePath()),
        testJibExtension.getSkaffold().getWatch().getBuildIncludes());
    Assert.assertEquals(
        ImmutableSet.of(root.resolve("watch3").toAbsolutePath()),
        testJibExtension.getSkaffold().getWatch().getIncludes());
    Assert.assertEquals(
        ImmutableSet.of(root.resolve("watch4").toAbsolutePath()),
        testJibExtension.getSkaffold().getWatch().getExcludes());
  }

  @Test
  public void testProperties() {
    System.setProperties(new Properties());

    System.setProperty("jib.from.image", "fromImage");
    Assert.assertEquals("fromImage", testJibExtension.getFrom().getImage());
    System.setProperty("jib.from.credHelper", "credHelper");
    Assert.assertEquals("credHelper", testJibExtension.getFrom().getCredHelper());

    System.setProperty("jib.to.image", "toImage");
    Assert.assertEquals("toImage", testJibExtension.getTo().getImage());
    System.setProperty("jib.to.tags", "tag1,tag2,tag3");
    Assert.assertEquals(
        ImmutableSet.of("tag1", "tag2", "tag3"), testJibExtension.getTo().getTags());
    System.setProperty("jib.to.credHelper", "credHelper");
    Assert.assertEquals("credHelper", testJibExtension.getTo().getCredHelper());

    System.setProperty("jib.container.appRoot", "appRoot");
    Assert.assertEquals("appRoot", testJibExtension.getContainer().getAppRoot());
    System.setProperty("jib.container.args", "arg1,arg2,arg3");
    Assert.assertEquals(
        ImmutableList.of("arg1", "arg2", "arg3"), testJibExtension.getContainer().getArgs());
    System.setProperty("jib.container.entrypoint", "entry1,entry2,entry3");
    Assert.assertEquals(
        ImmutableList.of("entry1", "entry2", "entry3"),
        testJibExtension.getContainer().getEntrypoint());
    System.setProperty("jib.container.environment", "env1=val1,env2=val2");
    Assert.assertEquals(
        ImmutableMap.of("env1", "val1", "env2", "val2"),
        testJibExtension.getContainer().getEnvironment());
    System.setProperty("jib.container.extraClasspath", "/d1,/d2,/d3");
    Assert.assertEquals(
        ImmutableList.of("/d1", "/d2", "/d3"), testJibExtension.getContainer().getExtraClasspath());
    System.setProperty("jib.container.expandClasspathDependencies", "true");
    Assert.assertTrue(testJibExtension.getContainer().getExpandClasspathDependencies());
    System.setProperty("jib.container.format", "OCI");
    Assert.assertSame(ImageFormat.OCI, testJibExtension.getContainer().getFormat());
    System.setProperty("jib.container.jvmFlags", "flag1,flag2,flag3");
    Assert.assertEquals(
        ImmutableList.of("flag1", "flag2", "flag3"), testJibExtension.getContainer().getJvmFlags());
    System.setProperty("jib.container.labels", "label1=val1,label2=val2");
    Assert.assertEquals(
        ImmutableMap.of("label1", "val1", "label2", "val2"),
        testJibExtension.getContainer().getLabels());
    System.setProperty("jib.container.mainClass", "main");
    Assert.assertEquals("main", testJibExtension.getContainer().getMainClass());
    System.setProperty("jib.container.ports", "port1,port2,port3");
    Assert.assertEquals(
        ImmutableList.of("port1", "port2", "port3"), testJibExtension.getContainer().getPorts());
    System.setProperty("jib.container.user", "myUser");
    Assert.assertEquals("myUser", testJibExtension.getContainer().getUser());
    System.setProperty("jib.container.filesModificationTime", "2011-12-03T22:42:05Z");
    Assert.assertEquals(
        "2011-12-03T22:42:05Z", testJibExtension.getContainer().getFilesModificationTime());
    System.setProperty("jib.containerizingMode", "packaged");
    Assert.assertEquals("packaged", testJibExtension.getContainerizingMode());

    System.setProperty("jib.extraDirectories.paths", "/foo,/bar/baz");
    Assert.assertEquals(2, testJibExtension.getExtraDirectories().getPaths().size());
    Assert.assertEquals(
        Paths.get("/foo"), testJibExtension.getExtraDirectories().getPaths().get(0).getFrom());
    Assert.assertEquals(
        Paths.get("/bar/baz"), testJibExtension.getExtraDirectories().getPaths().get(1).getFrom());
    System.setProperty("jib.extraDirectories.permissions", "/foo/bar=707,/baz=456");
    Assert.assertEquals(
        ImmutableMap.of("/foo/bar", "707", "/baz", "456"),
        testJibExtension.getExtraDirectories().getPermissions());

    System.setProperty("jib.dockerClient.executable", "test-exec");
    Assert.assertEquals(
        Paths.get("test-exec"), testJibExtension.getDockerClient().getExecutablePath());
    System.setProperty("jib.dockerClient.environment", "env1=val1,env2=val2");
    Assert.assertEquals(
        ImmutableMap.of("env1", "val1", "env2", "val2"),
        testJibExtension.getDockerClient().getEnvironment());

    // Absolute paths
    System.setProperty("jib.outputPaths.digest", "/digest/path");
    Assert.assertEquals(
        Paths.get("/digest/path").toAbsolutePath(),
        testJibExtension.getOutputPaths().getDigestPath());
    System.setProperty("jib.outputPaths.imageId", "/id/path");
    Assert.assertEquals(
        Paths.get("/id/path").toAbsolutePath(), testJibExtension.getOutputPaths().getImageIdPath());
    System.setProperty("jib.outputPaths.tar", "/tar/path");
    Assert.assertEquals(
        Paths.get("/tar/path").toAbsolutePath(), testJibExtension.getOutputPaths().getTarPath());
    // Relative paths
    System.setProperty("jib.outputPaths.digest", "digest/path");
    Assert.assertEquals(
        fakeProject.getProjectDir().toPath().resolve(Paths.get("digest/path")),
        testJibExtension.getOutputPaths().getDigestPath());
    System.setProperty("jib.outputPaths.imageId", "id/path");
    Assert.assertEquals(
        fakeProject.getProjectDir().toPath().resolve(Paths.get("id/path")),
        testJibExtension.getOutputPaths().getImageIdPath());
    System.setProperty("jib.outputPaths.tar", "tar/path");
    Assert.assertEquals(
        fakeProject.getProjectDir().toPath().resolve(Paths.get("tar/path")),
        testJibExtension.getOutputPaths().getTarPath());
  }

  public TargetImageParameters generateTargetImageParametersWithTags(String... tags) {
    HashSet<String> set = new HashSet<>(Arrays.asList(tags));
    testJibExtension.to(
        to -> {
          to.setTags(set);
        });
    return testJibExtension.getTo();
  }
}
