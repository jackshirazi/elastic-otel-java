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

import static co.elastic.otel.dynamicconfig.sampler.ConsistentSamplingUtil.getInvalidRandomValue;
import static co.elastic.otel.dynamicconfig.sampler.ConsistentSamplingUtil.isValidThreshold;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;

/** Abstract base class for consistent samplers. */
@SuppressWarnings("InconsistentOverloads")
public abstract class ConsistentSampler implements Sampler, Composable {

  /**
   * Returns a {@link ConsistentSampler} that samples each span with a fixed probability.
   *
   * @param samplingProbability the sampling probability
   * @return a sampler
   */
  public static ConsistentSampler probabilityBased(double samplingProbability) {
    return new ConsistentThresholdSampler(samplingProbability);
  }

  /**
   * Returns a new {@link ConsistentSampler} that respects the sampling decision of the parent span
   * or falls-back to the given sampler if it is a root span.
   *
   * @param rootSampler the root sampler
   */
  public static ConsistentSampler parentBased(Composable rootSampler) {
    return new ConsistentParentBasedSampler(rootSampler);
  }

  @Override
  public final SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {
    Span parentSpan = Span.fromContext(parentContext);
    SpanContext parentSpanContext = parentSpan.getSpanContext();

    TraceState parentTraceState = parentSpanContext.getTraceState();
    String otelTraceStateString = parentTraceState.get(OtelTraceState.TRACE_STATE_KEY);
    OtelTraceState otelTraceState = OtelTraceState.parse(otelTraceStateString);

    SamplingIntent intent =
        getSamplingIntent(parentContext, name, spanKind, attributes, parentLinks);
    long threshold = intent.getThreshold();

    // determine sampling decision
    boolean isSampled;
    boolean isAdjustedCountCorrect;
    if (isValidThreshold(threshold)) {
      long randomness = getRandomness(otelTraceState, traceId);
      isSampled = threshold <= randomness;
      isAdjustedCountCorrect = intent.isAdjustedCountReliable();
    } else { // DROP
      isSampled = false;
      isAdjustedCountCorrect = false;
    }

    SamplingDecision samplingDecision =
        isSampled ? SamplingDecision.RECORD_AND_SAMPLE : SamplingDecision.DROP;

    // determine tracestate changes
    if (isSampled && isAdjustedCountCorrect) {
      otelTraceState.setThreshold(threshold);
    } else {
      otelTraceState.invalidateThreshold();
    }

    String newOtTraceState = otelTraceState.serialize();

    return new SamplingResult() {

      @Override
      public SamplingDecision getDecision() {
        return samplingDecision;
      }

      @Override
      public Attributes getAttributes() {
        return intent.getAttributes();
      }

      @Override
      public TraceState getUpdatedTraceState(TraceState parentTraceState) {
        return intent.updateTraceState(parentTraceState).toBuilder()
            .put(OtelTraceState.TRACE_STATE_KEY, newOtTraceState)
            .build();
      }
    };
  }

  private static long getRandomness(OtelTraceState otelTraceState, String traceId) {
    if (otelTraceState.hasValidRandomValue()) {
      return otelTraceState.getRandomValue();
    } else {
      return OtelTraceState.parseHex(traceId, 18, 14, getInvalidRandomValue());
    }
  }
}
