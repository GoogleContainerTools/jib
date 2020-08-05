/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import java.io.File;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link FixedPermissionsProvider}. */
public class FixedPermissionsProviderTest {
  @Rule public final TemporaryFolder temporaryDirectory = new TemporaryFolder();

  private FilePermissions filesPermission;
  private FilePermissions directoriesPermission;
  private File file;
  private File directory;

  @Before
  public void setUp() throws IOException {
    filesPermission = FilePermissions.fromOctalString("444");
    directoriesPermission = FilePermissions.fromOctalString("555");
    file = temporaryDirectory.newFile("file");
    directory = temporaryDirectory.newFolder("directory");
  }

  @Test
  public void testApply_file() {
    FixedPermissionsProvider provider =
        new FixedPermissionsProvider(filesPermission, directoriesPermission);
    Assert.assertEquals(filesPermission, provider.get(file.toPath(), AbsoluteUnixPath.get("/")));
  }

  @Test
  public void testApply_directory() {
    FixedPermissionsProvider provider =
        new FixedPermissionsProvider(filesPermission, directoriesPermission);
    Assert.assertEquals(
        directoriesPermission, provider.get(directory.toPath(), AbsoluteUnixPath.get("/")));
  }
}
