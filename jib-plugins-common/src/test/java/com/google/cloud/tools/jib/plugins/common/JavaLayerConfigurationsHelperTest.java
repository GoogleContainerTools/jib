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

import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JavaLayerConfigurationsHelper}. */
@RunWith(MockitoJUnitRunner.class)
public class JavaLayerConfigurationsHelperTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private JavaLayerConfigurationsHelper.FileToLayerAdder fileToLayerAdder;

  @Test
  public void testIsEmptyDirectory() throws IOException {
    Assert.assertTrue(
        JavaLayerConfigurationsHelper.isEmptyDirectory(temporaryFolder.getRoot().toPath()));
  }

  @Test
  public void testIsEmptyDirectory_file() throws IOException {
    Assert.assertFalse(
        JavaLayerConfigurationsHelper.isEmptyDirectory(temporaryFolder.newFile().toPath()));
  }

  @Test
  public void testIsEmptyDirectory_nonExistent() throws IOException {
    Assert.assertFalse(JavaLayerConfigurationsHelper.isEmptyDirectory(Paths.get("non/existent")));
  }

  @Test
  public void testAddFilesToLayer_file() throws IOException {
    temporaryFolder.newFile("file");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    JavaLayerConfigurationsHelper.addFilesToLayer(
        sourceRoot, path -> true, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder).add(sourceRoot.resolve("file"), basePath.resolve("file"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesToLayer_emptyDirectory() throws IOException {
    temporaryFolder.newFolder("leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");

    JavaLayerConfigurationsHelper.addFilesToLayer(
        sourceRoot, path -> true, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder).add(sourceRoot.resolve("leaf"), basePath.resolve("leaf"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesToLayer_nonEmptyDirectoryIgnored() throws IOException {
    temporaryFolder.newFolder("non-empty", "leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    JavaLayerConfigurationsHelper.addFilesToLayer(
        sourceRoot, path -> true, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("non-empty/leaf"), basePath.resolve("non-empty/leaf"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesToLayer_filter() throws IOException {
    temporaryFolder.newFile("non-target");
    temporaryFolder.newFolder("sub");
    temporaryFolder.newFile("sub/target");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");

    Predicate<Path> nameIsTarget = path -> "target".equals(path.getFileName().toString());
    JavaLayerConfigurationsHelper.addFilesToLayer(
        sourceRoot, nameIsTarget, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("sub/target"), basePath.resolve("sub/target"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesToLayer_emptyDirectoryForced() throws IOException {
    temporaryFolder.newFolder("sub", "leaf");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/path/in/container");

    JavaLayerConfigurationsHelper.addFilesToLayer(
        sourceRoot, path -> false, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("sub/leaf"), basePath.resolve("sub/leaf"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }

  @Test
  public void testAddFilesToLayer_fileAsSource() throws IOException {
    Path sourceFile = temporaryFolder.newFile("foo").toPath();

    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/");
    try {
      JavaLayerConfigurationsHelper.addFilesToLayer(
          sourceFile, path -> true, basePath, fileToLayerAdder);
      Assert.fail();
    } catch (NotDirectoryException ex) {
      Assert.assertThat(ex.getMessage(), CoreMatchers.containsString("foo is not a directory"));
    }
  }

  @Test
  public void testAddFilesToLayer_complex() throws IOException {
    temporaryFolder.newFile("A.class");
    temporaryFolder.newFile("B.java");
    temporaryFolder.newFolder("example", "dir");
    temporaryFolder.newFile("example/dir/C.class");
    temporaryFolder.newFile("example/C.class");
    temporaryFolder.newFolder("test", "resources", "leaf");
    temporaryFolder.newFile("test/resources/D.java");
    temporaryFolder.newFile("test/D.class");

    Path sourceRoot = temporaryFolder.getRoot().toPath();
    AbsoluteUnixPath basePath = AbsoluteUnixPath.get("/base");

    Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");

    JavaLayerConfigurationsHelper.addFilesToLayer(
        sourceRoot, isClassFile, basePath, fileToLayerAdder);
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("A.class"), basePath.resolve("A.class"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("example/dir/C.class"), basePath.resolve("example/dir/C.class"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("example/C.class"), basePath.resolve("example/C.class"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("test/resources/leaf"), basePath.resolve("test/resources/leaf"));
    Mockito.verify(fileToLayerAdder)
        .add(sourceRoot.resolve("test/D.class"), basePath.resolve("test/D.class"));
    Mockito.verifyNoMoreInteractions(fileToLayerAdder);
  }
}
