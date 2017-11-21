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

package com.google.cloud.tools.crepecake.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JsonParser}. */
public class JsonParserTest {
  private static class TestJson {
    private int number;
    private String text;
    private transient String transientString;
    private InnerObject innerObject;
    private List<InnerObject> list;

    private static class InnerObject {
      private int number;
      private List<String> texts;
    }
  }

  @Test
  public void testFromJson_basic() {
    StringBuffer jsonBuffer = new StringBuffer();
    jsonBuffer.append("{");
    jsonBuffer.append("number: 54,");
    jsonBuffer.append("text: \"crepecake\",");
    jsonBuffer.append("transientString: \"notpartofjson\",");
    jsonBuffer.append("innerObject: {");
    jsonBuffer.append("number: 23,");
    jsonBuffer.append("texts: [\"first text\", \"second text\"]");
    jsonBuffer.append("},");
    jsonBuffer.append("list: [");
    jsonBuffer.append("{");
    jsonBuffer.append("number: 42,");
    jsonBuffer.append("texts: []");
    jsonBuffer.append("},");
    jsonBuffer.append("{");
    jsonBuffer.append("number: 99,");
    jsonBuffer.append("texts: [\"some text\"]");
    jsonBuffer.append("}");
    jsonBuffer.append("]");
    jsonBuffer.append("}");

    final String json = jsonBuffer.toString();

    TestJson testJson = JsonParser.fromJson(json, TestJson.class);

    Assert.assertEquals(54, testJson.number);
    Assert.assertEquals("crepecake", testJson.text);
    Assert.assertNull(testJson.transientString);
    Assert.assertEquals(23, testJson.innerObject.number);
    Assert.assertEquals(2, testJson.innerObject.texts.size());
    Assert.assertEquals("first text", testJson.innerObject.texts.get(0));
    Assert.assertEquals("second text", testJson.innerObject.texts.get(1));
    Assert.assertEquals(42, testJson.list.get(0).number);
    Assert.assertEquals(0, testJson.list.get(0).texts.size());
    Assert.assertEquals(99, testJson.list.get(1).number);
    Assert.assertEquals(1, testJson.list.get(1).texts.size());
    Assert.assertEquals("some text", testJson.list.get(1).texts.get(0));
  }

  @Test
  public void testToJson_basic() {
    final String expectedJson =
        "{\"number\":54,\"text\":\"crepecake\",\"innerObject\":{\"number\":23,\"texts\":"
            + "[\"first text\",\"second text\"]},\"list\":[{\"number\":99,\"texts\":[\"some text\"]},{\"number\":0}]}";

    TestJson testJson = new TestJson();
    testJson.number = 54;
    testJson.text = "crepecake";
    testJson.transientString = "notpartofjson";
    testJson.innerObject = new TestJson.InnerObject();
    testJson.innerObject.number = 23;
    testJson.innerObject.texts = Arrays.asList("first text", "second text");

    TestJson.InnerObject innerObject1 = new TestJson.InnerObject();
    innerObject1.number = 42;
    innerObject1.texts = Collections.emptyList();
    TestJson.InnerObject innerObject2 = new TestJson.InnerObject();
    innerObject1.number = 99;
    innerObject1.texts = Collections.singletonList("some text");
    testJson.list = Arrays.asList(innerObject1, innerObject2);

    String json = JsonParser.toJson(testJson);
    Assert.assertEquals(expectedJson, json);
  }
}
