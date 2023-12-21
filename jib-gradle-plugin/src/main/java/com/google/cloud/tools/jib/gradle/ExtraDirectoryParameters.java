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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtraDirectoriesConfiguration;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;

/** Configuration of an extra directory. */
public class ExtraDirectoryParameters implements ExtraDirectoriesConfiguration {

  ConfigurableFileCollection from;
  private Property<String> into;
  private ListProperty<String> includes;
  private ListProperty<String> excludes;

  @Inject
  public ExtraDirectoryParameters(ObjectFactory objects) {
    this.from = objects.fileCollection();
    this.into = objects.property(String.class).value("/");
    this.includes = objects.listProperty(String.class).empty();
    this.excludes = objects.listProperty(String.class).empty();
  }

  ExtraDirectoryParameters(ObjectFactory objects, Path from, String into) {
    this(objects);
    this.from = objects.fileCollection().from(from);
    System.out.println("VALUE OF FROM");
    System.out.println(this.from);
    this.into = objects.property(String.class).value(into);
  }

  @Override
  @InputFiles
  public Path getFrom() {
    System.out.println("VALUE OF GET FROM");
    System.out.println(from.getSingleFile().toPath());
    return from.getSingleFile().toPath();
  }

  public void setFrom(Object from) {
    this.from.from(from);
  }

  public void setFrom(Provider<Object> from) {
    this.from.setFrom(from);
  }

  @Override
  @Input
  public String getInto() {
    return into.get();
  }

  public void setInto(String into) {
    this.into.set(into);
  }

  public void setInto(Provider<String> into) {
    this.into.set(into);
  }

  @Input
  public ListProperty<String> getIncludes() {
    return includes;
  }

  @Input
  public ListProperty<String> getExcludes() {
    return excludes;
  }

  @Override
  @Internal
  public List<String> getIncludesList() {
    return includes.get();
  }

  @Override
  @Internal
  public List<String> getExcludesList() {
    return excludes.get();
  }
}
