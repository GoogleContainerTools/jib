/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.crepecake.blob.Blobs;
import java.io.IOException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/** Says "Hi" to the user. */
@Mojo(name = "sayhi")
public class GreetingMojo extends AbstractMojo {

  @Override
  public void execute() throws MojoExecutionException {
    getLog().info("Hello, world.");

    try {
      Blobs.from("Hihi").writeTo(System.out);
    } catch (IOException ex) {
      throw new MojoExecutionException("", ex);
    }
  }
}
