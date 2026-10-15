package com.easytranslate.usecase;

import com.easytranslate.config.OnlineTranslationPolicy;
import com.easytranslate.model.Translation;
import com.easytranslate.service.selection.SelectionResult;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

class OnlineTranslationCoordinatorTest {
  private Preferences prefs;
  private OnlineTranslationPolicy policy;
  private final Queue<Runnable> worker = new ArrayDeque<>(), ui = new ConcurrentLinkedQueue<>();
  private final List<Translation> translations = new ArrayList<>();
  private final List<Throwable> errors = new ArrayList<>();
  private final List<TranslationOperation> states = new ArrayList<>();
  private final AtomicInteger captures = new AtomicInteger(), calls = new AtomicInteger();
  private LatestTranslationCoordinator coordinator;
  @BeforeEach void setup() {
    prefs = Preferences.userRoot().node("com/easytranslate/test-coordinator-consent-" + UUID.randomUUID());
    policy = new OnlineTranslationPolicy(prefs);
    coordinator = make(worker::add, source -> { calls.incrementAndGet(); return new Translation(source, "译文"); });
  }
  @AfterEach void cleanup() throws Exception { coordinator.close(); prefs.removeNode(); prefs.flush(); }
  private LatestTranslationCoordinator make(Executor executor,
      com.easytranslate.service.translation.TranslationService service) {
    return new LatestTranslationCoordinator(new TranslateSelectionUseCase(() -> {
      captures.incrementAndGet(); return SelectionResult.found("sample");
    }, service, executor), ui::add, translations::add, errors::add, states::add, policy);
  }
  private void drain(Queue<Runnable> queue) { while (!queue.isEmpty()) queue.remove().run(); }
  private void choice(boolean value) { policy.choose(value); coordinator.refreshOnlineAccess(); }

  @Test void defaultTriggerDoesNotCaptureOrScheduleWorker() {
    coordinator.trigger(); drain(ui);
    assertTrue(worker.isEmpty()); assertEquals(0, captures.get()); assertEquals(0, calls.get());
    assertEquals(List.of(TranslationOperation.ONLINE_DISABLED), states);
  }
  @Test void persistedRefusalBlocksSubsequentTriggers() {
    choice(false); coordinator.trigger(); drain(worker); drain(ui);
    assertEquals(0, captures.get()); assertEquals(0, calls.get()); assertTrue(translations.isEmpty());
  }
  @Test void consentDoesNotAutomaticallyCaptureOrSend() {
    choice(true); drain(ui);
    assertEquals(List.of(TranslationOperation.IDLE), states);
    assertTrue(worker.isEmpty()); assertEquals(0, calls.get());
    coordinator.trigger(); drain(worker); drain(ui);
    assertEquals(1, calls.get()); assertEquals(1, translations.size());
    assertEquals(TranslationOperation.SUCCEEDED, states.getLast());
  }
  @Test void disableBeforeSelectionSkipsWorkAndOldProgress() {
    choice(true); coordinator.trigger(); choice(false);
    drain(worker); drain(ui);
    assertEquals(0, captures.get()); assertEquals(0, calls.get());
    assertEquals(List.of(TranslationOperation.ONLINE_DISABLED), states);
  }
  @Test void queuedSuccessCannotPublishAfterDisableAndReenable() {
    choice(true); coordinator.trigger(); drain(worker);
    choice(false); choice(true); drain(ui);
    assertTrue(translations.isEmpty()); assertTrue(errors.isEmpty());
    assertEquals(List.of(TranslationOperation.IDLE), states);
    coordinator.trigger(); drain(worker); drain(ui); assertEquals(1, translations.size());
  }
  @Test void queuedFailureCannotPublishAfterDisable() {
    coordinator = make(worker::add, source -> { throw new IllegalStateException("synthetic"); });
    choice(true); coordinator.trigger(); drain(worker); choice(false); drain(ui);
    assertTrue(errors.isEmpty()); assertEquals(List.of(TranslationOperation.ONLINE_DISABLED), states);
  }
  @Test void policyRevokesQueuedCallbacksEvenBeforeRefreshNotification() {
    choice(true); coordinator.trigger(); drain(worker);
    policy.choose(false); drain(ui);
    assertTrue(translations.isEmpty()); assertTrue(states.isEmpty());
  }
  @Test void disablingDuringCaptureSkipsService() {
    coordinator = new LatestTranslationCoordinator(new TranslateSelectionUseCase(() -> {
      policy.choose(false); return SelectionResult.found("sample");
    }, source -> { calls.incrementAndGet(); return new Translation(source, "译文"); }, worker::add),
        ui::add, translations::add, errors::add, states::add, policy);
    choice(true); coordinator.trigger(); drain(worker); coordinator.refreshOnlineAccess(); drain(ui);
    assertEquals(0, calls.get()); assertTrue(translations.isEmpty());
    assertEquals(List.of(TranslationOperation.ONLINE_DISABLED), states);
  }
  @Test void inflightSuccessAndFailureAreDroppedAfterReenable() throws Exception {
    for (boolean fail : new boolean[]{false, true}) {
      states.clear(); translations.clear(); errors.clear();
      var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
      try (var executor = Executors.newSingleThreadExecutor()) {
        coordinator = make(executor, source -> {
          entered.countDown();
          try { if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("fake service timeout"); }
          catch (InterruptedException error) { throw new AssertionError(error); }
          if (fail) throw new IllegalStateException("synthetic");
          return new Translation(source, "旧译文");
        });
        choice(true); coordinator.trigger();
        try {
          assertTrue(entered.await(3, TimeUnit.SECONDS));
          choice(false); choice(true);
        } finally { release.countDown(); }
        executor.submit(() -> {}).get(3, TimeUnit.SECONDS);
        drain(ui);
        assertTrue(translations.isEmpty()); assertTrue(errors.isEmpty());
        assertEquals(List.of(TranslationOperation.IDLE), states);
        coordinator.close();
      }
    }
  }
}
