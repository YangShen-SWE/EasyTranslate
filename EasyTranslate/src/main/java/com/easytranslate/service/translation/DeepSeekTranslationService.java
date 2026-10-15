package com.easytranslate.service.translation;

import com.easytranslate.config.ApiKeyStore;
import com.easytranslate.model.Translation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static com.easytranslate.service.translation.TranslationFailure.Kind.*;

public class DeepSeekTranslationService implements TranslationService {
  private static final URI ENDPOINT = URI.create("https://api.deepseek.com/chat/completions");
  private final ObjectMapper mapper = new ObjectMapper()
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private final Supplier<Optional<String>> keyProvider;
  private final AsyncTransport transport;
  private final com.easytranslate.config.OnlineTranslationPolicy onlinePolicy;

  @FunctionalInterface
  interface AsyncTransport {
    java.util.concurrent.CompletableFuture<HttpResponse<String>> send(HttpRequest request);
  }

  @FunctionalInterface
  interface Transport {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
  }

  public DeepSeekTranslationService(ApiKeyStore apiKeyStore) {
    this(apiKeyStore, new com.easytranslate.config.OnlineTranslationPolicy());
  }

  public DeepSeekTranslationService(ApiKeyStore apiKeyStore,
      com.easytranslate.config.OnlineTranslationPolicy onlinePolicy) {
    this(Objects.requireNonNull(apiKeyStore)::load, defaultTransport(),
        Objects.requireNonNull(onlinePolicy, "onlinePolicy"));
  }

  // Legacy synchronous fake boundary; never used by the production constructors.
  DeepSeekTranslationService(Supplier<Optional<String>> keyProvider, Transport transport) {
    this(keyProvider, request -> {
      try { return java.util.concurrent.CompletableFuture.completedFuture(transport.send(request)); }
      catch (IOException | InterruptedException error) {
        return java.util.concurrent.CompletableFuture.failedFuture(error);
      }
    }, null);
  }

  DeepSeekTranslationService(Supplier<Optional<String>> keyProvider, AsyncTransport transport,
      com.easytranslate.config.OnlineTranslationPolicy onlinePolicy) {
    this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
    this.transport = Objects.requireNonNull(transport, "transport");
    this.onlinePolicy = onlinePolicy;
  }

  private static AsyncTransport defaultTransport() {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    return request -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  @Override
  public Translation translate(String sourceText) {
    return translate(sourceText, onlinePolicy == null ? null : onlinePolicy.permit());
  }

  @Override
  public Translation translate(String sourceText,
      com.easytranslate.config.OnlineTranslationPolicy.Permit permit) {
    if (permit == null && onlinePolicy != null) permit = onlinePolicy.permit();
    if (permit != null) permit.requireAllowed();
    if (sourceText == null || sourceText.isBlank()) {
      throw new IllegalArgumentException("待翻译文本不能为空");
    }
    String apiKey;
    try {
      apiKey = keyProvider.get().orElse("").strip();
    } catch (RuntimeException error) {
      throw new TranslationFailure(KEY_UNREADABLE, error);
    }
    if (apiKey.isBlank()) throw new TranslationFailure(MISSING_KEY);

    try {
      ObjectNode body = mapper.createObjectNode();
      body.put("model", "deepseek-flash");
      body.putObject("thinking").put("type", "disabled");
      body.put("stream", false);
      var messages = body.putArray("messages");
      messages.addObject().put("role", "system")
          .put("content", "将用户给出的英文翻译成简洁、准确的中文。只返回译文。");
      messages.addObject().put("role", "user").put("content", sourceText);

      HttpRequest request;
      try {
        request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();
      } catch (IllegalArgumentException error) {
        throw new TranslationFailure(KEY_UNREADABLE, error);
      }
      // Exactly one send per call: recovery is manual, never an unbounded automatic retry.
      var pending = permit == null ? transport.send(request) : permit.start(() -> transport.send(request));
      HttpResponse<String> response = awaitResponse(pending);
      if (response == null) throw new TranslationFailure(INVALID_RESPONSE);
      if (response.statusCode() != 200) throw statusFailure(response.statusCode());
      return new Translation(sourceText, translatedContent(response.body()));
    } catch (HttpTimeoutException error) {
      throw new TranslationFailure(TIMEOUT, error);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new TranslationFailure(INTERRUPTED, error);
    } catch (JsonProcessingException error) {
      // Malformed JSON is not a network failure. No remote content appears in our message.
      throw new TranslationFailure(INVALID_RESPONSE, error);
    } catch (IOException error) {
      throw new TranslationFailure(NETWORK, error);
    }
  }

  private static HttpResponse<String> awaitResponse(
      java.util.concurrent.CompletableFuture<HttpResponse<String>> pending)
      throws IOException, InterruptedException {
    try { return pending.get(); }
    catch (java.util.concurrent.ExecutionException error) {
      Throwable cause = error.getCause();
      if (cause instanceof IOException io) throw io;
      if (cause instanceof InterruptedException interrupted) throw interrupted;
      if (cause instanceof RuntimeException runtime) throw runtime;
      throw new TranslationFailure(UNKNOWN, error);
    }
  }

  private static TranslationFailure statusFailure(int status) {
    if (status == 401 || status == 403) return new TranslationFailure(AUTHENTICATION);
    if (status == 429) return new TranslationFailure(RATE_LIMIT);
    if (status >= 500 && status <= 599) return new TranslationFailure(SERVICE_UNAVAILABLE);
    return new TranslationFailure(REQUEST_REJECTED);
  }

  private String translatedContent(String body) throws JsonProcessingException {
    if (body == null || body.isBlank()) throw new TranslationFailure(EMPTY_RESPONSE);
    JsonNode json = mapper.readTree(body);
    if (json == null || !json.isObject()) throw new TranslationFailure(INVALID_RESPONSE);
    JsonNode choices = json.get("choices");
    if (choices == null || !choices.isArray()) throw new TranslationFailure(INVALID_RESPONSE);
    if (choices.isEmpty()) throw new TranslationFailure(EMPTY_RESPONSE);
    JsonNode message = choices.get(0).get("message");
    if (message == null || !message.isObject() || !message.has("content")) {
      throw new TranslationFailure(INVALID_RESPONSE);
    }
    JsonNode content = message.get("content");
    if (content.isNull()) throw new TranslationFailure(EMPTY_RESPONSE);
    if (!content.isTextual()) throw new TranslationFailure(INVALID_RESPONSE);
    String text = content.textValue().strip();
    if (text.isBlank()) throw new TranslationFailure(EMPTY_RESPONSE);
    return text;
  }
}
