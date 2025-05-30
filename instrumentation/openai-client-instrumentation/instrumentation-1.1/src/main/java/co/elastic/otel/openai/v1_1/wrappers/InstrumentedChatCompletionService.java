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
package co.elastic.otel.openai.v1_1.wrappers;

import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.ERROR_TYPE;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_OPENAI_REQUEST_RESPONSE_FORMAT;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_OPENAI_REQUEST_SEED;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_OPERATION_NAME;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_REQUEST_FREQUENCY_PENALTY;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_REQUEST_MAX_TOKENS;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_REQUEST_MODEL;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_REQUEST_PRESENCE_PENALTY;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_REQUEST_STOP_SEQUENCES;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_REQUEST_TEMPERATURE;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_REQUEST_TOP_P;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_RESPONSE_ID;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_RESPONSE_MODEL;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_SYSTEM;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS;
import static co.elastic.otel.openai.v1_1.wrappers.GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS;

import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.blocking.chat.ChatCompletionService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class InstrumentedChatCompletionService
    extends DelegatingInvocationHandler<ChatCompletionService, InstrumentedChatCompletionService> {

  public static class RequestHolder {
    final ChatCompletionCreateParams request;
    final InstrumentationSettings settings;

    public RequestHolder(ChatCompletionCreateParams request, InstrumentationSettings settings) {
      this.request = request;
      this.settings = settings;
    }
  }

  public static class ChatCompletionResult {
    private final String responseModel;
    private final String responseId;
    private final List<String> finishReasons;

    private final Long inputTokens;
    private final Long completionTokens;

    public ChatCompletionResult(
        String responseModel,
        String responseId,
        List<String> finishReasons,
        Long inputTokens,
        Long completionTokens) {
      this.responseModel = responseModel;
      this.responseId = responseId;
      this.finishReasons = finishReasons;
      this.inputTokens = inputTokens;
      this.completionTokens = completionTokens;
    }
  }

  static final Instrumenter<RequestHolder, ChatCompletionResult> INSTRUMENTER =
      Instrumenter.<RequestHolder, ChatCompletionResult>builder(
              GlobalOpenTelemetry.get(),
              Constants.INSTRUMENTATION_NAME,
              holder -> "chat " + holder.request.model())
          .addAttributesExtractor(
              new AttributesExtractor<RequestHolder, ChatCompletionResult>() {
                @Override
                public void onStart(
                    AttributesBuilder attributes,
                    Context parentContext,
                    RequestHolder requestHolder) {
                  ChatCompletionCreateParams request = requestHolder.request;

                  requestHolder.settings.putServerInfoIntoAttributes(attributes);
                  attributes.put(GEN_AI_SYSTEM, "openai");
                  attributes.put(GEN_AI_OPERATION_NAME, "chat");
                  attributes.put(GEN_AI_REQUEST_MODEL, request.model().toString());
                  request
                      .frequencyPenalty()
                      .ifPresent(val -> attributes.put(GEN_AI_REQUEST_FREQUENCY_PENALTY, val));
                  request
                      .maxTokens()
                      .ifPresent(val -> attributes.put(GEN_AI_REQUEST_MAX_TOKENS, val));
                  request
                      .presencePenalty()
                      .ifPresent(val -> attributes.put(GEN_AI_REQUEST_PRESENCE_PENALTY, val));
                  request
                      .temperature()
                      .ifPresent(val -> attributes.put(GEN_AI_REQUEST_TEMPERATURE, val));
                  request.topP().ifPresent(val -> attributes.put(GEN_AI_REQUEST_TOP_P, val));
                  request.seed().ifPresent(val -> attributes.put(GEN_AI_OPENAI_REQUEST_SEED, val));
                  request
                      .stop()
                      .ifPresent(
                          stop -> {
                            if (stop.isString()) {
                              attributes.put(
                                  GEN_AI_REQUEST_STOP_SEQUENCES,
                                  Collections.singletonList(stop.asString()));
                            } else if (stop.isStrings()) {
                              attributes.put(GEN_AI_REQUEST_STOP_SEQUENCES, stop.asStrings());
                            }
                          });
                  request
                      .responseFormat()
                      .ifPresent(
                          val -> {
                            String typeString = extractType(val);
                            if (typeString != null) {
                              attributes.put(GEN_AI_OPENAI_REQUEST_RESPONSE_FORMAT, typeString);
                            }
                          });
                }

                @Override
                public void onEnd(
                    AttributesBuilder attributes,
                    Context context,
                    RequestHolder request,
                    ChatCompletionResult result,
                    Throwable error) {
                  if (error != null) {
                    attributes.put(ERROR_TYPE, error.getClass().getCanonicalName());
                  } else {
                    attributes.put(GEN_AI_RESPONSE_MODEL, result.responseModel);
                    attributes.put(GEN_AI_RESPONSE_ID, result.responseId);

                    List<String> finishReasons = result.finishReasons;
                    if (finishReasons != null && !finishReasons.isEmpty()) {
                      attributes.put(GEN_AI_RESPONSE_FINISH_REASONS, finishReasons);
                    }

                    if (result.inputTokens != null) {
                      attributes.put(GEN_AI_USAGE_INPUT_TOKENS, result.inputTokens);
                    }
                    if (result.completionTokens != null) {
                      attributes.put(GEN_AI_USAGE_OUTPUT_TOKENS, result.completionTokens);
                    }
                  }
                }
              })
          .addOperationMetrics(GenAiClientMetrics::new)
          .buildInstrumenter();

  private static String extractType(ChatCompletionCreateParams.ResponseFormat val) {
    if (val.isText()) {
      return val.asText()._type().toString();
    }
    if (val.isJsonObject()) {
      return val.asJsonObject()._type().toString();
    }
    if (val.isJsonSchema()) {
      return val.asJsonSchema()._type().toString();
    }
    return null;
  }

  private final InstrumentationSettings settings;

  InstrumentedChatCompletionService(
      ChatCompletionService delegate, InstrumentationSettings settings) {
    super(delegate);
    this.settings = settings;
  }

  @Override
  protected Class<ChatCompletionService> getProxyType() {
    return ChatCompletionService.class;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    Class<?>[] parameterTypes = method.getParameterTypes();

    if (methodName.equals("create")
        && parameterTypes.length >= 1
        && parameterTypes[0] == ChatCompletionCreateParams.class) {
      if (parameterTypes.length == 1) {
        return create((ChatCompletionCreateParams) args[0], RequestOptions.none());
      } else if (parameterTypes.length == 2 && parameterTypes[1] == RequestOptions.class) {
        return create((ChatCompletionCreateParams) args[0], (RequestOptions) args[1]);
      }
    }

    if (methodName.equals("createStreaming")
        && parameterTypes.length >= 1
        && parameterTypes[0] == ChatCompletionCreateParams.class) {
      if (parameterTypes.length == 1) {
        return createStreaming((ChatCompletionCreateParams) args[0], RequestOptions.none());
      } else if (parameterTypes.length == 2 && parameterTypes[1] == RequestOptions.class) {
        return createStreaming((ChatCompletionCreateParams) args[0], (RequestOptions) args[1]);
      }
    }
    return super.invoke(proxy, method, args);
  }

  public ChatCompletion create(
      ChatCompletionCreateParams chatCompletionCreateParams, RequestOptions requestOptions) {

    RequestHolder requestHolder = new RequestHolder(chatCompletionCreateParams, settings);

    Context parentCtx = Context.current();
    if (!INSTRUMENTER.shouldStart(parentCtx, requestHolder)) {
      return createWithLogs(chatCompletionCreateParams, requestOptions);
    }

    Context ctx = INSTRUMENTER.start(parentCtx, requestHolder);
    ChatCompletion completion;
    try (Scope scope = ctx.makeCurrent()) {
      completion = createWithLogs(chatCompletionCreateParams, requestOptions);
    } catch (Throwable t) {
      INSTRUMENTER.end(ctx, requestHolder, null, t);
      throw t;
    }

    List<String> finishReasons =
        completion.choices().stream()
            .map(ChatCompletion.Choice::finishReason)
            .map(ChatCompletion.Choice.FinishReason::toString)
            .collect(Collectors.toList());
    Long inputTokens = null;
    Long completionTokens = null;
    Optional<CompletionUsage> usage = completion.usage();
    if (usage.isPresent()) {
      inputTokens = usage.get().promptTokens();
      completionTokens = usage.get().completionTokens();
    }
    ChatCompletionResult result =
        new ChatCompletionResult(
            completion.model(), completion.id(), finishReasons, inputTokens, completionTokens);
    INSTRUMENTER.end(ctx, requestHolder, result, null);
    return completion;
  }

  private ChatCompletion createWithLogs(
      ChatCompletionCreateParams chatCompletionCreateParams, RequestOptions requestOptions) {
    ChatCompletionEventsHelper.emitPromptLogEvents(chatCompletionCreateParams, settings);
    ChatCompletion result = delegate.create(chatCompletionCreateParams, requestOptions);
    ChatCompletionEventsHelper.emitCompletionLogEvents(result, settings);
    return result;
  }

  public StreamResponse<ChatCompletionChunk> createStreaming(
      ChatCompletionCreateParams chatCompletionCreateParams, RequestOptions requestOptions) {
    RequestHolder requestHolder = new RequestHolder(chatCompletionCreateParams, settings);

    Context parentCtx = Context.current();
    if (!INSTRUMENTER.shouldStart(parentCtx, requestHolder)) {
      return createStreamingWithLogs(chatCompletionCreateParams, requestOptions);
    }

    Context ctx = INSTRUMENTER.start(parentCtx, requestHolder);
    StreamResponse<ChatCompletionChunk> response;
    try (Scope scope = ctx.makeCurrent()) {
      response = createStreamingWithLogs(chatCompletionCreateParams, requestOptions);
    } catch (Throwable t) {
      INSTRUMENTER.end(ctx, requestHolder, null, t);
      throw t;
    }

    return new TracingStreamedResponse(response, ctx, requestHolder);
  }

  private StreamResponse<ChatCompletionChunk> createStreamingWithLogs(
      ChatCompletionCreateParams chatCompletionCreateParams, RequestOptions requestOptions) {
    ChatCompletionEventsHelper.emitPromptLogEvents(chatCompletionCreateParams, settings);
    StreamResponse<ChatCompletionChunk> result =
        delegate.createStreaming(chatCompletionCreateParams, requestOptions);
    if (settings.emitEvents) {
      result = new EventLoggingStreamedResponse(result, settings);
    }
    return result;
  }
}
