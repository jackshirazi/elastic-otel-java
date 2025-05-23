/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.example.javaagent.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DynamicConfigSmokeTest extends TestAppSmokeTest {

  @BeforeAll
  public static void start() {
    startTestApp(
        (container) -> {
          container.addEnv(
              "OTEL_INSTRUMENTATION_METHODS_INCLUDE",
              "co.elastic.otel.test.DynamicConfigController[flipSending]");
          container.addEnv(
              "ELASTIC_OTEL_JAVA_EXPERIMENTAL_DISABLE_INSTRUMENTATIONS_CHECKER", "true");
          container.addEnv(
              "ELASTIC_OTEL_JAVA_EXPERIMENTAL_DISABLE_INSTRUMENTATIONS_CHECKER_INTERVAL_MS", "300");
          container.addEnv("OTEL_JAVAAGENT_DEBUG", "true");
        });
  }

  @AfterAll
  public static void end() {
    stopApp();
  }

  @AfterEach
  public void endTest() {
    doRequest(getUrl("/dynamicconfig/reset"), okResponseBody("reset"));
  }

  @Test
  public void flipSending() throws IOException {
    doRequest(getUrl("/health"), okResponseBody("Alive!"));
    doRequest(getUrl("/dynamicconfig/flipSending"), okResponseBody("stopped"));

    // the first flip-sending request should not be part of the exported traces anymore
    List<ExportTraceServiceRequest> traces = waitForTraces();
    List<Span> spans = getSpans(traces).toList();
    assertThat(spans).hasSize(1).extracting("name").containsOnly("GET /health");

    clearBackend();

    doRequest(getUrl("/health"), okResponseBody("Alive!"));
    doRequest(getUrl("/dynamicconfig/flipSending"), okResponseBody("restarted"));
    // During /health, the sending should still have been disabled, /flipSending should be recorded
    traces = waitForTraces();
    spans = getSpans(traces).toList();
    assertThat(spans)
        .hasSize(2)
        .extracting("name")
        .containsOnly("GET /dynamicconfig/flipSending", "DynamicConfigController.flipSending");
  }
}
