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

package com.google.cloud.tools.jib.api.buildplan;

import com.google.cloud.tools.jib.buildplan.UnixPathParser;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.Immutable;

/**
 * Represents a Unix-style path in relative form (does not start at the file system root {@code /}).
 *
 * <p>This class is immutable and thread-safe.
 */
@Immutable
public class RelativeUnixPath {

  /**
   * Gets a new {@link RelativeUnixPath} from a Unix-style path in relative form. The {@code path}
   * must be relative (does not begin with a leading slash {@code /}).
   *
   * @param relativePath the relative path
   * @return a new {@link RelativeUnixPath}
   */
  public static RelativeUnixPath get(String relativePath) {
    if (relativePath.startsWith("/")) {
      throw new IllegalArgumentException("Path starts with forward slash (/): " + relativePath);
    }

    return new RelativeUnixPath(UnixPathParser.parse(relativePath));
  }

  private final List<String> pathComponents;

  /** Instantiate with {@link #get}. */
  private RelativeUnixPath(List<String> pathComponents) {
    this.pathComponents = pathComponents;
  }

  /**
   * Gets the relative Unix path this represents, in a list of components.
   *
   * @return the relative path this represents, in a list of components
   */
  List<String> getRelativePathComponents() {
    return new ArrayList<>(pathComponents);
  }
}
