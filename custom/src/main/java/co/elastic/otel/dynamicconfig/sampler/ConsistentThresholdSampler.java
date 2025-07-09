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

import static co.elastic.otel.dynamicconfig.sampler.ConsistentSamplingUtil.calculateSamplingProbability;
import static co.elastic.otel.dynamicconfig.sampler.ConsistentSamplingUtil.calculateThreshold;
import static co.elastic.otel.dynamicconfig.sampler.ConsistentSamplingUtil.checkThreshold;
import static co.elastic.otel.dynamicconfig.sampler.ConsistentSamplingUtil.getInvalidThreshold;
import static co.elastic.otel.dynamicconfig.sampler.ConsistentSamplingUtil.getMaxThreshold;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.util.List;

public class ConsistentThresholdSampler extends ConsistentSampler {

  private volatile long threshold;
  private volatile String description;

  protected ConsistentThresholdSampler(double samplingProbability) {
    setSamplingProbability(samplingProbability);
  }

  public void setSamplingProbability(double samplingProbability) {
    long threshold = calculateThreshold(samplingProbability);
    checkThreshold(threshold);
    this.threshold = threshold;

    String thresholdString;
    if (threshold == getMaxThreshold()) {
      thresholdString = "max";
    } else {
      thresholdString =
          ConsistentSamplingUtil.appendLast56BitHexEncodedWithoutTrailingZeros(
                  new StringBuilder(), threshold)
              .toString();
    }

    // tiny eventual consistency where the description would be out of date with the threshold, but
    // this doesn't really matter
    this.description =
        "ConsistentFixedThresholdSampler{threshold="
            + thresholdString
            + ", sampling probability="
            + calculateSamplingProbability(threshold)
            + "}";
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public SamplingIntent getSamplingIntent(
      Context parentContext,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {

    return () -> {
      if (threshold == getMaxThreshold()) {
        return getInvalidThreshold();
      }
      return threshold;
    };
  }
}
