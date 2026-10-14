package com.easytranslate.service.translation;

import com.easytranslate.model.Translation;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.easytranslate.service.translation.TranslationFailure.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** In-memory HTTP boundary. No HTTP client, preferences, real key or network is used. */
class DeepSeekTranslationServiceTest {
  private static final String GOOD = "{\"choices\":[{\"message\":{\"content\":\"  测试译文  \"}}]}";

  @Test void invalidSourceNeverReadsKeyOrSends() {
    AtomicInteger loads = new AtomicInteger();
    Fixture f = new Fixture();
    var service = new DeepSeekTranslationService(() -> { loads.incrementAndGet(); return Optional.empty(); }, f::send);
    for (String source : new String[]{null, "", " \n\t"}) {
      assertThrows(IllegalArgumentException.class, () -> service.translate(source));
    }
    assertEquals(0, loads.get());
    assertEquals(0, f.sends);
  }

  @Test void missingAndBlankKeyNeverSend() {
    for (Optional<String> key : List.of(Optional.<String>empty(), Optional.of(" \t"))) {
      Fixture f = new Fixture(); f.key.set(key);
      expect(f, MISSING_KEY);
      assertEquals(0, f.sends);
    }
  }

  @Test void keyReadFailureIsDistinctAndDoesNotExposeCause() {
    Fixture f = new Fixture();
    var cause = new IllegalStateException("synthetic-private-key-detail");
    var service = new DeepSeekTranslationService(() -> { throw cause; }, f::send);
    var error = assertThrows(TranslationFailure.class, () -> service.translate("sample"));
    assertEquals(KEY_UNREADABLE, error.kind());
    assertSame(cause, error.getCause());
    assertFalse(error.getMessage().contains("synthetic-private"));
    assertEquals(0, f.sends);
  }

  @Test void invalidHeaderKeyDoesNotSendOrLeak() {
    Fixture f = new Fixture(); f.key.set(Optional.of("test\r\nInjected: invalid"));
    expect(f, KEY_UNREADABLE);
    assertEquals(0, f.sends);
  }

  @Test void successPreservesRequestContractAndStripsOnlyResultEdges() throws Exception {
    Fixture f = new Fixture();
    assertEquals(new Translation("Example sentence.", "测试译文"), f.service().translate("Example sentence."));
    assertEquals(1, f.sends);
    assertEquals(URI.create("https://api.deepseek.com/chat/completions"), f.request.uri());
    assertEquals("POST", f.request.method());
    assertEquals(Duration.ofSeconds(20), f.request.timeout().orElseThrow());
    assertEquals("Bearer test-only-placeholder", f.request.headers().firstValue("Authorization").orElseThrow());
    assertEquals("application/json", f.request.headers().firstValue("Content-Type").orElseThrow());
    var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(requestBody(f.request));
    assertEquals("deepseek-flash", json.path("model").asText());
    assertEquals("disabled", json.path("thinking").path("type").asText());
    assertFalse(json.path("stream").asBoolean());
    assertEquals("Example sentence.", json.path("messages").path(1).path("content").asText());
  }

  @Test void unauthorizedAndForbiddenAreAuthenticationWithoutRetries() {
    for (int status : new int[]{401, 403}) expectStatus(status, AUTHENTICATION);
  }

  @Test void rateLimitIsManualRecoveryWithoutRetries() { expectStatus(429, RATE_LIMIT); }

  @Test void serverErrorsAreServiceUnavailableWithoutRetries() {
    for (int status : new int[]{500, 502, 503, 599}) expectStatus(status, SERVICE_UNAVAILABLE);
  }

  @Test void otherStatusIsRejectedRatherThanNetworkOrJsonFailure() {
    for (int status : new int[]{302, 400, 402, 404}) expectStatus(status, REQUEST_REJECTED);
  }

  @Test void networkIOExceptionIsNotTimeout() {
    for (IOException cause : List.of(new ConnectException("synthetic socket detail"), new IOException("synthetic network detail"))) {
      Fixture f = new Fixture(); f.io = cause;
      assertSame(cause, expect(f, NETWORK).getCause());
      assertEquals(1, f.sends);
    }
  }

  @Test void timeoutIsDistinctFromNetwork() {
    Fixture f = new Fixture(); f.io = new HttpTimeoutException("synthetic timeout detail");
    expect(f, TIMEOUT);
    assertEquals(1, f.sends);
  }

  @Test void interruptionRestoresInterruptFlagWithoutRetries() {
    assertFalse(Thread.currentThread().isInterrupted());
    Fixture f = new Fixture(); f.interrupted = true;
    try {
      expect(f, INTERRUPTED);
      assertTrue(Thread.currentThread().isInterrupted());
      assertEquals(1, f.sends);
    } finally { Thread.interrupted(); }
  }

  @Test void emptyBodyChoicesAndContentAreEmptyResponse() {
    for (String body : new String[]{null, "", " \n", "{\"choices\":[]}",
        "{\"choices\":[{\"message\":{\"content\":null}}]}",
        "{\"choices\":[{\"message\":{\"content\":\" \\n \"}}]}"}) {
      Fixture f = new Fixture(); f.body = body;
      expect(f, EMPTY_RESPONSE);
      assertEquals(1, f.sends);
    }
  }

  @Test void malformedAndTrailingJsonAreInvalidRatherThanNetwork() {
    for (String body : new String[]{"not-json", "{", GOOD + " {}", GOOD + " trailing"}) {
      Fixture f = new Fixture(); f.body = body;
      expect(f, INVALID_RESPONSE);
      assertEquals(1, f.sends);
    }
  }

  @Test void missingOrWrongSchemaIsInvalidResponse() {
    for (String body : new String[]{"null", "[]", "{}", "{\"choices\":{}}", "{\"choices\":[null]}",
        "{\"choices\":[{}]}", "{\"choices\":[{\"message\":null}]}", "{\"choices\":[{\"message\":{}}]}"}) {
      Fixture f = new Fixture(); f.body = body;
      expect(f, INVALID_RESPONSE);
      assertEquals(1, f.sends);
    }
  }

  @Test void contentMustBeTextRatherThanCoercedScalar() {
    for (String content : new String[]{"123", "true", "{}", "[]"}) {
      Fixture f = new Fixture(); f.body = "{\"choices\":[{\"message\":{\"content\":" + content + "}}]}";
      expect(f, INVALID_RESPONSE);
      assertEquals(1, f.sends);
    }
  }

  @Test void nextManualTriggerReloadsKeyAndCanRecoverWithoutAutomaticRetry() {
    Fixture f = new Fixture(); var service = f.service();
    f.key.set(Optional.empty());
    assertEquals(MISSING_KEY, assertThrows(TranslationFailure.class, () -> service.translate("sample")).kind());
    assertEquals(0, f.sends);
    f.key.set(Optional.of("test-only-placeholder")); f.status = 429;
    assertEquals(RATE_LIMIT, assertThrows(TranslationFailure.class, () -> service.translate("sample")).kind());
    assertEquals(1, f.sends);
    f.status = 200;
    assertEquals("测试译文", service.translate("sample").translatedText());
    assertEquals(2, f.sends);
  }

  private static TranslationFailure expect(Fixture f, TranslationFailure.Kind kind) {
    var error = assertThrows(TranslationFailure.class, () -> f.service().translate("sample"));
    assertEquals(kind, error.kind());
    assertEquals(kind.userMessage(), error.getMessage());
    return error;
  }

  private static void expectStatus(int status, TranslationFailure.Kind kind) {
    Fixture f = new Fixture(); f.status = status; f.body = "synthetic-private-remote-body";
    var error = expect(f, kind);
    assertFalse(error.getMessage().contains(f.body));
    assertEquals(1, f.sends);
  }

  private static String requestBody(HttpRequest request) throws Exception {
    var result = new CompletableFuture<String>();
    var text = new java.io.ByteArrayOutputStream();
    request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
      public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
      public void onNext(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); text.writeBytes(bytes);
      }
      public void onError(Throwable error) { result.completeExceptionally(error); }
      public void onComplete() { result.complete(text.toString(StandardCharsets.UTF_8)); }
    });
    return result.get(2, TimeUnit.SECONDS);
  }

  private static final class Fixture {
    final AtomicReference<Optional<String>> key = new AtomicReference<>(Optional.of("test-only-placeholder"));
    int sends, status = 200;
    String body = GOOD;
    HttpRequest request;
    IOException io;
    boolean interrupted;
    DeepSeekTranslationService service() { return new DeepSeekTranslationService(key::get, this::send); }
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
      sends++; this.request = request;
      if (io != null) throw io;
      if (interrupted) throw new InterruptedException("synthetic interruption");
      return new FakeResponse(status, body, request);
    }
  }

  private record FakeResponse(int statusCode, String body, HttpRequest request) implements HttpResponse<String> {
    public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
    public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
    public Optional<SSLSession> sslSession() { return Optional.empty(); }
    public URI uri() { return request.uri(); }
    public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
}
