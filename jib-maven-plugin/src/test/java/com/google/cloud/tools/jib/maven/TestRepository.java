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

import com.google.common.io.Resources;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.testing.MojoRule;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.junit.Assert;
import org.junit.rules.ExternalResource;

/** A test helper to resolve artifacts from a local repository in test/resources. */
public class TestRepository extends ExternalResource {

  private static final String TEST_M2 = "maven/testM2";

  private ArtifactRepository testLocalRepo;
  private ArtifactResolver artifactResolver;
  private ArtifactHandler jarHandler;

  @Override
  protected void before()
      throws ComponentLookupException, URISyntaxException, MalformedURLException {
    MojoRule testHarness = new MojoRule();
    ArtifactRepositoryFactory artifactRepositoryFactory =
        testHarness.lookup(ArtifactRepositoryFactory.class);
    artifactResolver = testHarness.lookup(ArtifactResolver.class);
    jarHandler = testHarness.lookup(ArtifactHandlerManager.class).getArtifactHandler("jar");
    testLocalRepo =
        artifactRepositoryFactory.createArtifactRepository(
            "test",
            Resources.getResource(TEST_M2).toURI().toURL().toString(),
            new DefaultRepositoryLayout(),
            null,
            null);
  }

  Artifact findArtifact(String group, String artifact, String version) {
    ArtifactResolutionRequest artifactResolutionRequest = new ArtifactResolutionRequest();
    artifactResolutionRequest.setLocalRepository(testLocalRepo);
    Artifact artifactToFind =
        new DefaultArtifact(group, artifact, version, null, "jar", null, jarHandler);

    artifactResolutionRequest.setArtifact(artifactToFind);

    ArtifactResolutionResult ars = artifactResolver.resolve(artifactResolutionRequest);

    Assert.assertEquals(1, ars.getArtifacts().size());
    return ars.getArtifacts().iterator().next();
  }

  Path artifactPathOnDisk(String group, String artifact, String version) {
    return findArtifact(group, artifact, version).getFile().toPath();
  }
}
