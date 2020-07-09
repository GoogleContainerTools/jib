/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.json;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JsonTemplateMapper}. */
public class JsonTemplateMapperTest {

  private static class TestJson implements JsonTemplate {
    private int number;
    private String text;
    private DescriptorDigest digest;
    private InnerObject innerObject;
    private List<InnerObject> list;

    private static class InnerObject implements JsonTemplate {
      // This field has the same name as a field in the outer class, but either NOT interfere with
      // the other.
      private int number;
      private List<String> texts;
      private List<DescriptorDigest> digests;
    }
  }

  @Test
  public void testWriteJson() throws DigestException, IOException, URISyntaxException {
    Path jsonFile = Paths.get(Resources.getResource("core/json/basic.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    TestJson testJson = new TestJson();
    testJson.number = 54;
    testJson.text = "crepecake";
    testJson.digest =
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad");
    testJson.innerObject = new TestJson.InnerObject();
    testJson.innerObject.number = 23;
    testJson.innerObject.texts = Arrays.asList("first text", "second text");
    testJson.innerObject.digests =
        Arrays.asList(
            DescriptorDigest.fromDigest(
                "sha256:91e0cae00b86c289b33fee303a807ae72dd9f0315c16b74e6ab0cdbe9d996c10"),
            DescriptorDigest.fromHash(
                "4945ba5011739b0b98c4a41afe224e417f47c7c99b2ce76830999c9a0861b236"));

    TestJson.InnerObject innerObject1 = new TestJson.InnerObject();
    innerObject1.number = 42;
    innerObject1.texts = Collections.emptyList();
    TestJson.InnerObject innerObject2 = new TestJson.InnerObject();
    innerObject2.number = 99;
    innerObject2.texts = Collections.singletonList("some text");
    innerObject2.digests =
        Collections.singletonList(
            DescriptorDigest.fromDigest(
                "sha256:d38f571aa1c11e3d516e0ef7e513e7308ccbeb869770cb8c4319d63b10a0075e"));
    testJson.list = Arrays.asList(innerObject1, innerObject2);

    Assert.assertEquals(expectedJson, JsonTemplateMapper.toUtf8String(testJson));
  }

  @Test
  public void testReadJsonWithLock() throws IOException, URISyntaxException, DigestException {
    Path jsonFile = Paths.get(Resources.getResource("core/json/basic.json").toURI());

    // Deserializes into a metadata JSON object.
    TestJson testJson = JsonTemplateMapper.readJsonFromFileWithLock(jsonFile, TestJson.class);

    MatcherAssert.assertThat(testJson.number, CoreMatchers.is(54));
    MatcherAssert.assertThat(testJson.text, CoreMatchers.is("crepecake"));
    MatcherAssert.assertThat(
        testJson.digest,
        CoreMatchers.is(
            DescriptorDigest.fromDigest(
                "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad")));
    MatcherAssert.assertThat(
        testJson.innerObject, CoreMatchers.instanceOf(TestJson.InnerObject.class));
    MatcherAssert.assertThat(testJson.innerObject.number, CoreMatchers.is(23));
    MatcherAssert.assertThat(
        testJson.innerObject.texts, CoreMatchers.is(Arrays.asList("first text", "second text")));
    MatcherAssert.assertThat(
        testJson.innerObject.digests,
        CoreMatchers.is(
            Arrays.asList(
                DescriptorDigest.fromDigest(
                    "sha256:91e0cae00b86c289b33fee303a807ae72dd9f0315c16b74e6ab0cdbe9d996c10"),
                DescriptorDigest.fromHash(
                    "4945ba5011739b0b98c4a41afe224e417f47c7c99b2ce76830999c9a0861b236"))));
    // ignore testJson.list
  }

  @Test
  public void testReadListOfJson() throws IOException, URISyntaxException, DigestException {
    Path jsonFile = Paths.get(Resources.getResource("core/json/basic_list.json").toURI());

    String jsonString = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
    List<TestJson> listofJsons = JsonTemplateMapper.readListOfJson(jsonString, TestJson.class);
    TestJson json1 = listofJsons.get(0);
    TestJson json2 = listofJsons.get(1);

    DescriptorDigest digest1 =
        DescriptorDigest.fromDigest(
            "sha256:91e0cae00b86c289b33fee303a807ae72dd9f0315c16b74e6ab0cdbe9d996c10");
    DescriptorDigest digest2 =
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad");

    Assert.assertEquals(1, json1.number);
    Assert.assertEquals(2, json2.number);
    Assert.assertEquals("text1", json1.text);
    Assert.assertEquals("text2", json2.text);
    Assert.assertEquals(digest1, json1.digest);
    Assert.assertEquals(digest2, json2.digest);
    Assert.assertEquals(10, json1.innerObject.number);
    Assert.assertEquals(20, json2.innerObject.number);
    Assert.assertEquals(2, json1.list.size());
    Assert.assertTrue(json2.list.isEmpty());
  }

  @Test
  public void testToBlob_listOfJson() throws IOException, URISyntaxException {
    Path jsonFile = Paths.get(Resources.getResource("core/json/basic_list.json").toURI());

    String jsonString = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
    List<TestJson> listOfJson = JsonTemplateMapper.readListOfJson(jsonString, TestJson.class);

    Assert.assertEquals(jsonString, JsonTemplateMapper.toUtf8String(listOfJson));
  }

  @Test
  public void testReadJson_inputStream() throws IOException {
    String testJson = "{\"number\":3, \"text\":\"cool\"}";
    ByteArrayInputStream in = new ByteArrayInputStream(testJson.getBytes(StandardCharsets.UTF_8));
    TestJson json = JsonTemplateMapper.readJson(in, TestJson.class);
    Assert.assertEquals(3, json.number);
    Assert.assertEquals("cool", json.text);
  }
}
