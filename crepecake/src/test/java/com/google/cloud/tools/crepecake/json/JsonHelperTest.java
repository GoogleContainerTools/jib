/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.json;

import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.common.io.CharStreams;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.security.DigestException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JsonHelper}. */
public class JsonHelperTest {

  private static class TestJson extends JsonTemplate {
    private int number;
    private String text;
    private DescriptorDigest digest;
    private InnerObject innerObject;
    private List<InnerObject> list;

    private static class InnerObject extends JsonTemplate {
      // This field has the same name as a field in the outer class, but either NOT interfere with the other.
      private int number;
      private List<String> texts;
      private List<DescriptorDigest> digests;
    }
  }

  @Test
  public void testWriteJson() throws DigestException, IOException, URISyntaxException {
    File jsonFile = new File(getClass().getClassLoader().getResource("json/basic.json").toURI());
    final String expectedJson =
        CharStreams.toString(new InputStreamReader(new FileInputStream(jsonFile)));

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

    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream();
    JsonHelper.writeJson(jsonStream, testJson);

    Assert.assertEquals(expectedJson, jsonStream.toString());
  }
}
