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
package co.elastic.opamp.client.connectivity.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link co.elastic.opamp.client.request.service.RequestService} implementation that uses {@link
 * okhttp3.OkHttpClient} to send the request.
 */
public class OkHttpSender implements HttpSender {
  private final OkHttpClient client;
  private final String url;

  public static OkHttpSender create(String url) {
    return create(new OkHttpClient(), url);
  }

  public static OkHttpSender create(OkHttpClient client, String url) {
    return new OkHttpSender(client, url);
  }

  private OkHttpSender(OkHttpClient client, String url) {
    this.client = client;
    this.url = url;
  }

  @Override
  public CompletableFuture<Response> send(Consumer<OutputStream> writer, int contentLength) {
    CompletableFuture<Response> future = new CompletableFuture<>();
    okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(url);
    String contentType = "application/x-protobuf";
    builder.addHeader("Content-Type", contentType);

    RequestBody body = new RawRequestBody(writer, contentLength, MediaType.parse(contentType));
    builder.post(body);

    try {
      okhttp3.Response response = client.newCall(builder.build()).execute();
      if (response.isSuccessful()) {
        if (response.body() != null) {
          future.complete(new OkHttpResponse(response));
        }
      } else {
        future.completeExceptionally(new HttpErrorException(response.code(), response.message()));
      }
    } catch (IOException e) {
      future.completeExceptionally(e);
    }

    future.completeExceptionally(new IllegalStateException());

    return future;
  }

  private static class OkHttpResponse implements Response {
    private final okhttp3.Response response;

    private OkHttpResponse(okhttp3.Response response) {
      this.response = response;
    }

    @Override
    public int statusCode() {
      return response.code();
    }

    @Override
    public String statusMessage() {
      return response.message();
    }

    @Override
    public InputStream bodyInputStream() {
      if (response.body() != null) {
        return response.body().byteStream();
      }
      return null;
    }

    @Override
    public String getHeader(String name) {
      return response.headers().get(name);
    }

    @Override
    public void close() {
      response.close();
    }
  }

  private static class RawRequestBody extends RequestBody {
    private final Consumer<OutputStream> writer;
    private final int contentLength;
    private final MediaType contentType;

    private RawRequestBody(
        Consumer<OutputStream> writer, int contentLength, MediaType contentType) {
      this.writer = writer;
      this.contentLength = contentLength;
      this.contentType = contentType;
    }

    @Nullable
    @Override
    public MediaType contentType() {
      return contentType;
    }

    @Override
    public long contentLength() {
      return contentLength;
    }

    @Override
    public void writeTo(@NotNull BufferedSink bufferedSink) {
      writer.accept(bufferedSink.outputStream());
    }
  }
}
