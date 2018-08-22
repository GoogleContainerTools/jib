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

/** A test helper to resolve artifacts from a local repository in test/resources */
public class TestRepository extends ExternalResource {

  private static final String TEST_M2 = "testM2";

  private MojoRule testHarness;
  private ArtifactRepositoryFactory artifactRepositoryFactory;
  private ArtifactHandlerManager artifactHandlerManager;
  private ArtifactRepository testLocalRepo;
  private ArtifactResolver artifactResolver;
  private ArtifactHandler jarHandler;

  @Override
  protected void before()
      throws ComponentLookupException, URISyntaxException, MalformedURLException {
    testHarness = new MojoRule();
    artifactRepositoryFactory = testHarness.lookup(ArtifactRepositoryFactory.class);
    artifactHandlerManager = testHarness.lookup(ArtifactHandlerManager.class);
    artifactResolver = testHarness.lookup(ArtifactResolver.class);
    jarHandler = artifactHandlerManager.getArtifactHandler("jar");

    testLocalRepo =
        artifactRepositoryFactory.createArtifactRepository(
            "test",
            Resources.getResource(TEST_M2).toURI().toURL().toString(),
            new DefaultRepositoryLayout(),
            null,
            null);
  }

  public Artifact findArtifact(String group, String artifact, String version) {
    ArtifactResolutionRequest artifactResolutionRequest = new ArtifactResolutionRequest();
    artifactResolutionRequest.setLocalRepository(testLocalRepo);
    Artifact artifactToFind =
        new DefaultArtifact(group, artifact, version, null, "jar", null, jarHandler);

    artifactResolutionRequest.setArtifact(artifactToFind);

    ArtifactResolutionResult ars = artifactResolver.resolve(artifactResolutionRequest);

    Assert.assertEquals(1, ars.getArtifacts().size());
    return ars.getArtifacts().iterator().next();
  }

  public Path artifactPathOnDisk(String group, String artifact, String version) {
    return findArtifact(group, artifact, version).getFile().toPath();
  }
}
