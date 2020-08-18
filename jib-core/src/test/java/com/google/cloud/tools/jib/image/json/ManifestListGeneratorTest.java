package com.google.cloud.tools.jib.image.json;

import com.google.cloud.tools.jib.image.Image;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link ManifestListGenerator}. */
public class ManifestListGeneratorTest {

  private Image image1;
  private Image image2;
  private ManifestListGenerator manifestListGenerator;

  private void setUp(Class<? extends BuildableManifestTemplate> imageFormat) {
    image1 = Image.builder(imageFormat).setArchitecture("amd64").setOs("linux").build();
    image2 = Image.builder(imageFormat).setArchitecture("arm64").setOs("ubuntu").build();
    manifestListGenerator = new ManifestListGenerator(Arrays.asList(image1, image2));
  }

  @Test
  public void testGetManifest_v22() {
    setUp(V22ManifestTemplate.class);
    testGetManifestListTemplate(V22ManifestTemplate.class);
  }

  /** Tests translation of image to {@link BuildableManifestTemplate}. */
  private <T extends BuildableManifestTemplate> void testGetManifestListTemplate(
      Class<T> manifestTemplateClass) {

    /**
     * Expected manifest list JSON:
     *
     * <pre>{@code
     * 	  {
     * "schemaVersion":2,
     * "mediaType":"application/vnd.docker.distribution.manifest.list.v2+json",
     * "manifests":[
     * {
     * "mediaType":"application/vnd.docker.distribution.manifest.v2+json",
     * "digest":"sha256:1f25787aab4669d252bdae09a72b9c345d2a7b8c64c8dbfba4c82af4834dbccc",
     * "size":264,
     * "platform":{
     * "architecture":"amd64",
     * "os":"linux"
     * }
     * },
     * {
     * "mediaType":"application/vnd.docker.distribution.manifest.v2+json",
     * "digest":"sha256:3806f91f28cb58fe70e7a10a55fefad1b7b45bcee0e22fa2408d519b2adb9631",
     * "size":264,
     * "platform":{
     * "architecture":"arm64",
     * "os":"ubuntu"
     * }
     * }
     * ]
     * }
     *
     * }</pre>
     */
    ManifestTemplate manifestTemplate =
        manifestListGenerator.getManifestListTemplate(manifestTemplateClass);
    Assert.assertTrue(manifestTemplate instanceof V22ManifestListTemplate);
    V22ManifestListTemplate manifestList = (V22ManifestListTemplate) manifestTemplate;
    Assert.assertEquals(2, manifestList.getSchemaVersion());
    Assert.assertEquals(
        Arrays.asList("sha256:1f25787aab4669d252bdae09a72b9c345d2a7b8c64c8dbfba4c82af4834dbccc"),
        manifestList.getDigestsForPlatform("amd64", "linux"));
    Assert.assertEquals(
        Arrays.asList("sha256:3806f91f28cb58fe70e7a10a55fefad1b7b45bcee0e22fa2408d519b2adb9631"),
        manifestList.getDigestsForPlatform("arm64", "ubuntu"));
  }
}
