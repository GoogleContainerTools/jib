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

package example;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

import example.config.HelloWorldConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@SuppressWarnings("unused")
public class JibExampleConfiguration extends Configuration {
  @Valid @NotNull @JsonProperty
  private HelloWorldConfiguration hello;

  @JsonProperty("hello")
  public HelloWorldConfiguration getHelloConfiguration() {
    return hello;
  }
}
