package com.easytranslate.service.translation;

import com.easytranslate.config.OnlineTranslationPolicy;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

/** Real request/parse path with an asynchronous fake transport. No client or private key. */
class OnlineDeepSeekTranslationTest {
  private Preferences prefs;
  private OnlineTranslationPolicy policy;
  private final AtomicInteger loads = new AtomicInteger(), sends = new AtomicInteger();
  @BeforeEach void setup() {
    prefs = Preferences.userRoot().node("com/easytranslate/test-http-consent-" + UUID.randomUUID());
    policy = new OnlineTranslationPolicy(prefs);
  }
  @AfterEach void cleanup() throws Exception { prefs.removeNode(); prefs.flush(); }
  private DeepSeekTranslationService service() {
    return new DeepSeekTranslationService(() -> { loads.incrementAndGet(); return Optional.of("synthetic-placeholder"); },
        request -> { sends.incrementAndGet(); return CompletableFuture.completedFuture(new Reply(request)); }, policy);
  }
  private void disabled(Runnable work) {
    assertEquals(TranslationFailure.Kind.ONLINE_DISABLED,
        assertThrows(TranslationFailure.class, work::run).kind());
  }
  @Test void defaultRefusesBeforeKeyAndSend() {
    disabled(() -> service().translate("sample"));
    assertEquals(0, loads.get()); assertEquals(0, sends.get());
  }
  @Test void refusalDoesNotLoadKeyOrSend() {
    policy.choose(false); disabled(() -> service().translate("sample"));
    assertEquals(0, loads.get()); assertEquals(0, sends.get());
  }
  @Test void consentAllowsExactlyOneSend() {
    policy.choose(true);
    assertEquals("译文", service().translate("sample").translatedText());
    assertEquals(1, loads.get()); assertEquals(1, sends.get());
  }
  @Test void revocationDuringRequestPreparationPreventsTransportAdmission() {
    policy.choose(true);
    var service = new DeepSeekTranslationService(() -> {
      policy.choose(false); return Optional.of("synthetic-placeholder");
    }, request -> { sends.incrementAndGet(); return CompletableFuture.completedFuture(new Reply(request)); }, policy);
    disabled(() -> service.translate("sample"));
    assertEquals(0, sends.get());
  }
  @Test void oldPermitCannotSendAfterReenable() {
    policy.choose(true); var old = policy.permit();
    policy.choose(false); policy.choose(true);
    disabled(() -> service().translate("sample", old));
    assertEquals(0, loads.get()); assertEquals(0, sends.get());
    service().translate("new sample"); assertEquals(1, sends.get());
  }
  @Test void disablingDoesNotWaitForAnAlreadyAdmittedResponse() throws Exception {
    policy.choose(true);
    var admitted = new CompletableFuture<Void>();
    var response = new CompletableFuture<HttpResponse<String>>();
    var service = new DeepSeekTranslationService(() -> Optional.of("synthetic-placeholder"), request -> {
      admitted.complete(null); return response;
    }, policy);
    var result = CompletableFuture.supplyAsync(() -> service.translate("sample"));
    try {
      admitted.get(3, TimeUnit.SECONDS);
      CompletableFuture.runAsync(() -> policy.choose(false)).get(3, TimeUnit.SECONDS);
      assertFalse(result.isDone());
      disabled(() -> service.translate("next sample"));
    } finally { response.complete(new Reply(HttpRequest.newBuilder(URI.create("https://example.invalid")).build())); }
    assertEquals("译文", result.get(3, TimeUnit.SECONDS).translatedText());
    // Service can finish; the coordinator must drop this old response.
  }
  @Test void asynchronousTimeoutRetainsD04Classification() {
    policy.choose(true);
    var service = new DeepSeekTranslationService(() -> Optional.of("synthetic-placeholder"),
        request -> CompletableFuture.failedFuture(new HttpTimeoutException("synthetic")), policy);
    assertEquals(TranslationFailure.Kind.TIMEOUT,
        assertThrows(TranslationFailure.class, () -> service.translate("sample")).kind());
  }
  @Test void admittedHttpResultCannotReachUiAfterRevokeAndReenable() throws Exception {
    policy.choose(true);
    var admitted = new CompletableFuture<Void>();
    var response = new CompletableFuture<HttpResponse<String>>();
    var service = new DeepSeekTranslationService(() -> Optional.of("synthetic-placeholder"), request -> {
      sends.incrementAndGet(); admitted.complete(null); return response;
    }, policy);
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var results = new ArrayList<com.easytranslate.model.Translation>();
    var failures = new ArrayList<Throwable>();
    var states = new ArrayList<com.easytranslate.usecase.TranslationOperation>();
    try (var worker = Executors.newSingleThreadExecutor();
        var coordinator = new com.easytranslate.usecase.LatestTranslationCoordinator(
            new com.easytranslate.usecase.TranslateSelectionUseCase(() ->
                com.easytranslate.service.selection.SelectionResult.found("sample"), service, worker),
            ui::add, results::add, failures::add, states::add, policy)) {
      coordinator.trigger();
      try {
        admitted.get(3, TimeUnit.SECONDS);
        policy.choose(false); coordinator.refreshOnlineAccess();
        policy.choose(true); coordinator.refreshOnlineAccess();
      } finally {
        response.complete(new Reply(HttpRequest.newBuilder(URI.create("https://example.invalid")).build()));
      }
      worker.submit(() -> {}).get(3, TimeUnit.SECONDS);
      Runnable task; while ((task = ui.poll()) != null) task.run();
      assertTrue(results.isEmpty()); assertTrue(failures.isEmpty());
      assertEquals(List.of(com.easytranslate.usecase.TranslationOperation.IDLE), states);
      coordinator.trigger(); worker.submit(() -> {}).get(3, TimeUnit.SECONDS);
      while ((task = ui.poll()) != null) task.run();
      assertEquals(1, results.size()); assertEquals(2, sends.get());
      assertEquals("译文", results.getFirst().translatedText());
    }
  }

  private record Reply(HttpRequest request) implements HttpResponse<String> {
    public int statusCode() { return 200; }
    public String body() { return "{\"choices\":[{\"message\":{\"content\":\"译文\"}}]}"; }
    public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
    public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (n, v) -> true); }
    public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
    public URI uri() { return request.uri(); }
    public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
  }
}
