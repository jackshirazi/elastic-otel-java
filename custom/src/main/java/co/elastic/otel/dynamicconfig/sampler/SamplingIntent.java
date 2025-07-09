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
package co.elastic.otel.dynamicconfig.sampler;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.TraceState;

/** Interface for declaring sampling intent by Composable Samplers. */
public interface SamplingIntent {

  /**
   * Returns the suggested rejection threshold value. The returned value must be either from the
   * interval [0, 2^56) or be equal to ConsistentSamplingUtil.getInvalidThreshold().
   *
   * @return a threshold value
   */
  long getThreshold();

  /*
   * Return true if the adjusted count (calculated as reciprocal of the sampling probability) can be faithfully used to estimate span metrics.
   */
  default boolean isAdjustedCountReliable() {
    return true;
  }

  /**
   * Returns a set of Attributes to be added to the Span in case of positive sampling decision.
   *
   * @return Attributes
   */
  default Attributes getAttributes() {
    return Attributes.empty();
  }

  /**
   * Given an input Tracestate and sampling Decision provide a Tracestate to be associated with the
   * Span.
   *
   * @param parentState the TraceState of the parent Span
   * @return a TraceState
   */
  default TraceState updateTraceState(TraceState parentState) {
    return parentState;
  }
}
