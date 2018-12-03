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

import de.thomaskrille.dropwizard_template_config.TemplateConfigBundle;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import example.health.TemplateHealthCheck;
import example.resources.HelloWorldResource;

/**
 * <a href="https://www.dropwizard.io/1.3.5/docs/manual/index.html">
 * Refer Dropwizard User Manual</a>
 */
public class JibExampleApplication extends Application<JibExampleConfiguration> {

  public static void main(final String[] args) throws Exception {
    new JibExampleApplication().run(args);
  }

  @Override
  public String getName() {
    return "Dropwizard Jib Example";
  }

  @Override
  public void initialize(final Bootstrap<JibExampleConfiguration> bootstrap) {
    // Enable FreeMarker config templates
    bootstrap.addBundle(new TemplateConfigBundle());
  }

  @Override
  public void run(final JibExampleConfiguration configuration, final Environment environment) {
    final TemplateHealthCheck healthCheck =
        new TemplateHealthCheck(configuration.getHelloConfiguration().getTemplate());
    environment.healthChecks().register("template", healthCheck);

    environment.jersey().register(HelloWorldResource.from(configuration));
  }
}
