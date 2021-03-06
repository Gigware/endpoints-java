/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.server.spi.discovery;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.server.spi.IoUtil;
import com.google.api.server.spi.ServiceContext;
import com.google.api.server.spi.TypeLoader;
import com.google.api.server.spi.config.ApiConfigLoader;
import com.google.api.server.spi.config.annotationreader.ApiConfigAnnotationReader;
import com.google.api.server.spi.config.model.ApiConfig;
import com.google.api.server.spi.discovery.DiscoveryGenerator.DiscoveryContext;
import com.google.api.server.spi.testing.ArrayEndpoint;
import com.google.api.server.spi.testing.EnumEndpoint;
import com.google.api.server.spi.testing.FooEndpoint;
import com.google.api.server.spi.testing.MultipleParameterEndpoint;
import com.google.api.server.spi.testing.NamespaceEndpoint;
import com.google.api.services.discovery.model.DirectoryList;
import com.google.api.services.discovery.model.RestDescription;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import io.swagger.util.Json;

/**
 * Tests for {@link DiscoveryGenerator}.
 */
@RunWith(JUnit4.class)
public class DiscoveryGeneratorTest {
  private final DiscoveryContext context = new DiscoveryContext()
      .setApiRoot("https://discovery-test.appspot.com/api");
  private final ObjectMapper mapper = Json.mapper();
  private DiscoveryGenerator generator;
  private ApiConfigLoader configLoader;

  @Before
  public void setUp() throws Exception {
    TypeLoader typeLoader = new TypeLoader(getClass().getClassLoader());
    ApiConfigAnnotationReader annotationReader =
        new ApiConfigAnnotationReader(typeLoader.getAnnotationTypes());
    this.configLoader = new ApiConfigLoader(new ApiConfig.Factory(), typeLoader,
        annotationReader);
    this.generator = new DiscoveryGenerator(typeLoader);
  }

  @Test
  public void testWriteDiscovery_FooEndpoint() throws Exception {
    RestDescription doc = getDiscovery(FooEndpoint.class, context);
    RestDescription expected = readExpectedAsDiscovery("foo_endpoint.json");
    compareDiscovery(expected, doc);
  }

  @Test
  public void testWriteDiscovery_FooEndpointDefaultContext() throws Exception {
    RestDescription doc = getDiscovery(FooEndpoint.class, new DiscoveryContext());
    RestDescription expected = readExpectedAsDiscovery("foo_endpoint_default_context.json");
    compareDiscovery(expected, doc);
  }

  @Test
  public void testWriteDiscovery_FooEndpointLocalhost() throws Exception {
    RestDescription doc = getDiscovery(
        FooEndpoint.class, new DiscoveryContext().setApiRoot("http://localhost:8080/api"));
    RestDescription expected = readExpectedAsDiscovery("foo_endpoint_localhost.json");
    compareDiscovery(expected, doc);
  }

  @Test
  public void testWriteDiscovery_EnumEndpoint() throws Exception {
    RestDescription doc = getDiscovery(EnumEndpoint.class, new DiscoveryContext());
    RestDescription expected = readExpectedAsDiscovery("enum_endpoint.json");
    compareDiscovery(expected, doc);
  }

  @Test
  public void testWriteDiscovery_ArrayEndpoint() throws Exception {
    RestDescription doc = getDiscovery(ArrayEndpoint.class, new DiscoveryContext());
    RestDescription expected = readExpectedAsDiscovery("array_endpoint.json");
    compareDiscovery(expected, doc);
  }

  @Test
  public void testWriteDiscovery_namespace() throws Exception {
    RestDescription doc = getDiscovery(NamespaceEndpoint.class, new DiscoveryContext());
    RestDescription expected = readExpectedAsDiscovery("namespace_endpoint.json");
    compareDiscovery(expected, doc);
  }

  @Test
  public void testWriteDiscovery_parameterOrder() throws Exception {
    RestDescription doc = getDiscovery(MultipleParameterEndpoint.class, new DiscoveryContext());
    RestDescription expected = readExpectedAsDiscovery("multiple_parameter_endpoint.json");
    compareDiscovery(expected, doc);
  }

  @Test
  public void testWriteDiscovery_newHostname() throws Exception {
    RestDescription doc = getDiscovery(
        FooEndpoint.class,
        new DiscoveryContext().setApiRoot("http://notlocalhost/api").setHostname("localhost:8080"));
    RestDescription expected = readExpectedAsDiscovery("foo_endpoint_localhost.json");
    compareDiscovery(expected, doc);
  }

  @Test
  public void testWriteDiscovery_directory() throws Exception {
    DiscoveryGenerator.Result result =
        generator.writeDiscovery(
            ImmutableList.of(
                configLoader.loadConfiguration(ServiceContext.create(), ArrayEndpoint.class),
                configLoader.loadConfiguration(ServiceContext.create(), EnumEndpoint.class)),
            new DiscoveryContext());
    assertThat(result.directory()).isEqualTo(readExpectedAsDirectory("directory.json"));
  }

  @Test
  public void testDirectoryIsCloneable() throws Exception {
    ApiConfig config = configLoader.loadConfiguration(ServiceContext.create(), FooEndpoint.class);
    DiscoveryGenerator.Result result = generator.writeDiscovery(ImmutableList.of(config), context);
    result.directory().clone();
  }

  private RestDescription getDiscovery(Class<?> serviceClass, DiscoveryContext context)
      throws Exception {
    ApiConfig config = configLoader.loadConfiguration(ServiceContext.create(), serviceClass);
    // If the clone call fails, the generated discovery is invalid.
    return Iterables.getFirst(
        generator.writeDiscovery(ImmutableList.of(config), context).discoveryDocs().values(),
        null).clone();
  }

  private RestDescription readExpectedAsDiscovery(String file) throws Exception {
    String expectedString = IoUtil.readResourceFile(DiscoveryGeneratorTest.class, file);
    return JacksonFactory.getDefaultInstance().createJsonParser(expectedString)
        .parse(RestDescription.class);
  }

  private DirectoryList readExpectedAsDirectory(String file) throws Exception {
    String expectedString = IoUtil.readResourceFile(DiscoveryGeneratorTest.class, file);
    return JacksonFactory.getDefaultInstance().createJsonParser(expectedString)
        .parse(DirectoryList.class);
  }

  private void compareDiscovery(RestDescription expected, RestDescription actual) throws Exception {
    System.out.println("Actual: " + mapper.writeValueAsString(actual));
    System.out.println("Expected: " + mapper.writeValueAsString(expected));
    assertThat(actual).isEqualTo(expected);
  }
}
