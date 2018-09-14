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

package com.google.cloud.tools.jib.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.rules.ExternalResource;

/** Sets up the plugin for testing. */
public class TestPlugin extends ExternalResource {

  private String pluginVersion;

  String getVersion() {
    return pluginVersion;
  }

  @Override
  protected void before() throws IOException, XmlPullParserException, VerificationException {
    // Installs the plugin for use in tests.
    Verifier verifier = new Verifier(".", true);
    verifier.setAutoclean(false);
    verifier.addCliOption("-DskipTests");
    verifier.addCliOption("-Dfmt.skip");
    verifier.addCliOption("-Dcheckstyle.skip");
    verifier.executeGoal("install");

    // Reads the project version.
    MavenXpp3Reader reader = new MavenXpp3Reader();
    Model model =
        reader.read(Files.newBufferedReader(Paths.get("pom.xml"), StandardCharsets.UTF_8));
    pluginVersion = model.getVersion();
  }
}
