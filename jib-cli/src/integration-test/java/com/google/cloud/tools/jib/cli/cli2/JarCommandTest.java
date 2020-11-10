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

package com.google.cloud.tools.jib.cli.cli2;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.Command;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import picocli.CommandLine;

public class JarCommandTest {

  @Test
  public void testErrorLogging_fileDoesNotExist() {
    CommandLine jibCli = new CommandLine(new JibCli());
    StringWriter stringWriter = new StringWriter();
    jibCli.setErr(new PrintWriter(stringWriter));

    Integer exitCode = jibCli.execute("jar", "--target", "docker://jib-cli-image", "unknown.jar");

    assertThat(exitCode).isEqualTo(1);
    assertThat(stringWriter.toString())
        .isEqualTo("[ERROR] The file path provided does not exist: unknown.jar\n");
  }

  @Test
  public void testErrorLogging_directoryGiven() throws URISyntaxException {
    CommandLine jibCli = new CommandLine(new JibCli());
    StringWriter stringWriter = new StringWriter();
    jibCli.setErr(new PrintWriter(stringWriter));

    Path jarFile = Paths.get("/");
    Integer exitCode =
        jibCli.execute("jar", "--target", "docker://jib-cli-image", jarFile.toString());

    assertThat(exitCode).isEqualTo(1);
    assertThat(stringWriter.toString())
        .isEqualTo(
            "[ERROR] The file path provided is for a directory. Please provide a path to a jar file: "
                + jarFile.toString()
                + "\n");
  }

  @Test
  public void testJar_toDocker() throws IOException, InterruptedException, URISyntaxException {
    Path jarFile = Paths.get(Resources.getResource("simpleJar.jar").toURI());
    Integer exitCode =
        new CommandLine(new JibCli())
            .execute("jar", "--target", "docker://jib-cli-image", jarFile.toString());
    String output = new Command("docker", "run", "--rm", "jib-cli-image").run();

    assertThat(exitCode).isEqualTo(0);
    assertThat(output).isEqualTo("Hello World");
  }
}
