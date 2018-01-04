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

package com.google.cloud.tools.crepecake.registry;

import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.http.Request;
import com.google.cloud.tools.crepecake.http.Response;
import com.google.cloud.tools.crepecake.image.json.V22ManifestTemplate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ManifestPusher}. */
@RunWith(MockitoJUnitRunner.class)
public class ManifestPusherTest {

  @Mock private V22ManifestTemplate mockManifestTemplate;

  private ManifestPusher testManifestPusher;

  @Before
  public void setUp() {
    testManifestPusher = new ManifestPusher(mockManifestTemplate, "test-image-tag");
  }

  @Test
  public void testBuildRequest() {
    Request.Builder mockRequestBuilder = Mockito.mock(Request.Builder.class);
    testManifestPusher.buildRequest(mockRequestBuilder);

    Mockito.verify(mockRequestBuilder).setContentType(V22ManifestTemplate.MEDIA_TYPE);
    Mockito.verify(mockRequestBuilder).setBody(Mockito.any(Blob.class));
  }

  @Test
  public void testHandleResponse() {
    Assert.assertNull(testManifestPusher.handleResponse(Mockito.mock(Response.class)));
  }

  @Test
  public void testApiRouteSuffix() {
    Assert.assertEquals("/manifests/test-image-tag", testManifestPusher.getApiRouteSuffix());
  }

  @Test
  public void testGetHttpMethod() {
    Assert.assertEquals("PUT", testManifestPusher.getHttpMethod());
  }

  @Test
  public void testGetActionDescription() {
    Assert.assertEquals(
        "push image manifest for someServerUrl/someImageName:test-image-tag",
        testManifestPusher.getActionDescription("someServerUrl", "someImageName"));
  }
}
