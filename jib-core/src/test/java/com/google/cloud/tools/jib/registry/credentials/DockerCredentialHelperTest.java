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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

public class DockerCredentialHelperTest {

  @Test
  public void testDockerCredentialsTemplate_read() throws IOException {
    String input = "{\"Username\":\"myusername\",\"Secret\":\"mysecret\"}";
    DockerCredentialHelper.DockerCredentialsTemplate template =
        JsonTemplateMapper.readJson(input, DockerCredentialHelper.DockerCredentialsTemplate.class);
    Assert.assertEquals("myusername", template.username);
    Assert.assertEquals("mysecret", template.secret);
  }

  @Test
  public void testDockerCredentialsTemplate_canReadNull() throws IOException {
    String input = "{}";
    DockerCredentialHelper.DockerCredentialsTemplate template =
        JsonTemplateMapper.readJson(input, DockerCredentialHelper.DockerCredentialsTemplate.class);
    Assert.assertNull(template.username);
    Assert.assertNull(template.secret);
  }
}
