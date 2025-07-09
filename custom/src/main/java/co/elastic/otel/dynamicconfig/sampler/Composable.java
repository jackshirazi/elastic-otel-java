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
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.util.List;

/** An interface for components to be used by composite consistent probability samplers. */
public interface Composable {

  /**
   * Returns the SamplingIntent that is used for the sampling decision. The SamplingIntent includes
   * the threshold value which will be used for the sampling decision.
   *
   * <p>NOTE: Keep in mind, that in any case the returned threshold value must not depend directly
   * or indirectly on the random value. In particular this means that the parent sampled flag must
   * not be used for the calculation of the threshold as the sampled flag depends itself on the
   * random value.
   */
  SamplingIntent getSamplingIntent(
      Context parentContext,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks);

  /** Return the string providing a description of the implementation. */
  String getDescription();
}
