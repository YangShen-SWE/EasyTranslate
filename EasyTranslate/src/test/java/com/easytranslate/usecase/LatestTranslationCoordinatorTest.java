package com.easytranslate.usecase;

import com.easytranslate.model.Translation;
import com.easytranslate.service.selection.SelectedTextService;
import com.easytranslate.service.selection.SelectionResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Real coordinator + real use case, with controlled worker/UI queues and no native/API calls. */
class LatestTranslationCoordinatorTest {
  private static final long WAIT_SECONDS = 5;

  @Test
  void latestSuccessIsDeliveredOnlyOnUiExecutor() {
    Fixture f = new Fixture(SelectionResult.found("B"));
    f.coordinator.trigger();
    f.worker.runNext();
    assertTrue(f.translations.isEmpty());
    f.ui.runAll();
    assertEquals(List.of(translated("B")), f.translations);
    assertTrue(f.failures.isEmpty());
  }

  @Test
  void latestFailureIsDeliveredWithoutPretendingSuccess() {
    Fixture f = new Fixture(SelectionResult.found("B"));
    IllegalStateException failure = new IllegalStateException("fake latest failure");
    f.reply.set(source -> { throw failure; });
    f.coordinator.trigger();
    f.worker.runNext();
    assertTrue(f.failures.isEmpty());
    f.ui.runAll();
    assertTrue(f.translations.isEmpty());
    assertSame(failure, f.failures.getFirst().getCause());
  }

  @Test
  void newerSuccessWinsWhenOldSuccessFinishesLast() throws Exception {
    checkInFlightOldResponse(false, SelectionResult.found("B"));
  }

  @Test
  void oldFailureFinishingLastDoesNotOverwriteNewSuccess() throws Exception {
    checkInFlightOldResponse(true, SelectionResult.found("B"));
  }

  @Test
  void noneTriggerInvalidatesInFlightSuccessWithoutCallingTranslator() throws Exception {
    checkInFlightOldResponse(false, SelectionResult.none());
  }

  @Test
  void unknownTriggerInvalidatesInFlightSuccessWithoutCallingTranslator() throws Exception {
    checkInFlightOldResponse(false, SelectionResult.unknown());
  }

  @Test
  void staleSuccessAlreadyQueuedForUiIsRecheckedAfterNewTrigger() {
    Fixture f = new Fixture(SelectionResult.found("A"), SelectionResult.found("B"));
    f.coordinator.trigger();
    f.worker.runNext(); // A callback is queued but has not changed the view.
    f.coordinator.trigger();
    f.ui.runAll(); // B is still reading: A must already be obsolete.
    assertTrue(f.translations.isEmpty());
    f.worker.runNext();
    f.ui.runAll();
    assertEquals(List.of(translated("B")), f.translations);
  }

  @Test
  void staleFailureAlreadyQueuedForUiIsRecheckedAfterNewTrigger() {
    Fixture f = new Fixture(SelectionResult.found("A"), SelectionResult.found("B"));
    f.reply.set(source -> {
      if (source.equals("A")) throw new IllegalStateException("fake old failure");
      return translated(source);
    });
    f.coordinator.trigger();
    f.worker.runNext();
    f.coordinator.trigger();
    f.ui.runAll();
    assertTrue(f.failures.isEmpty());
    f.worker.runNext();
    f.ui.runAll();
    assertEquals(List.of(translated("B")), f.translations);
    assertTrue(f.failures.isEmpty());
  }

  @Test
  void identityIsAssignedBeforeDelayedSelectionBegins() {
    Fixture f = new Fixture(SelectionResult.found("B"));
    f.coordinator.trigger(); // A has not begun selection.
    f.coordinator.trigger(); // B supersedes it before either worker runs.
    f.worker.runNext();
    assertEquals(0, f.selectionCalls.get());
    assertEquals(0, f.translationCalls.get());
    f.worker.runNext();
    f.ui.runAll();
    assertEquals(1, f.selectionCalls.get());
    assertEquals(1, f.translationCalls.get());
    assertEquals(List.of(translated("B")), f.translations);
  }

  @Test
  void supersededDuringSelectionDoesNotStartOldTranslation() {
    AtomicReference<LatestTranslationCoordinator> coordinator = new AtomicReference<>();
    AtomicInteger reads = new AtomicInteger();
    Fixture f = new Fixture(() -> {
      if (reads.incrementAndGet() == 1) {
        coordinator.get().trigger(); // B arrives while A is being read.
        return SelectionResult.found("A");
      }
      return SelectionResult.found("B");
    });
    coordinator.set(f.coordinator);
    f.coordinator.trigger();
    f.worker.runNext();
    assertEquals(0, f.translationCalls.get());
    f.worker.runNext();
    f.ui.runAll();
    assertEquals(1, f.translationCalls.get());
    assertEquals(List.of(translated("B")), f.translations);
  }

  @Test
  void closeDropsBothSuccessAndFailureAlreadyQueuedForUi() {
    for (boolean fail : new boolean[]{false, true}) {
      Fixture f = new Fixture(SelectionResult.found("A"));
      if (fail) f.reply.set(source -> { throw new IllegalStateException("fake close failure"); });
      f.coordinator.trigger();
      f.worker.runNext();
      f.coordinator.close();
      f.ui.runAll();
      assertTrue(f.translations.isEmpty());
      assertTrue(f.failures.isEmpty());
    }
  }

  @Test
  void closeSkipsPendingSelectionAndRejectsSubsequentTriggers() {
    Fixture f = new Fixture(SelectionResult.found("A"));
    f.coordinator.trigger();
    f.coordinator.close();
    f.coordinator.close(); // Idempotent lifecycle cleanup.
    f.coordinator.trigger();
    f.worker.runAll();
    f.ui.runAll();
    assertEquals(0, f.selectionCalls.get());
    assertEquals(0, f.translationCalls.get());
    assertTrue(f.translations.isEmpty());
    assertTrue(f.failures.isEmpty());
  }

  @Test
  void closeWhileTranslationIsInFlightDiscardsSuccessAndFailure() throws Exception {
    for (boolean fail : new boolean[]{false, true}) {
      Fixture f = new Fixture(SelectionResult.found("A"));
      Gate gate = new Gate(fail);
      f.reply.set(gate::translate);
      f.coordinator.trigger();
      CompletableFuture<Void> oldWorker = CompletableFuture.runAsync(f.worker::runNext);
      try {
        assertTrue(gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS));
        f.coordinator.close();
      } finally {
        gate.release.countDown();
        oldWorker.get(WAIT_SECONDS, TimeUnit.SECONDS);
      }
      f.ui.runAll();
      assertEquals(1, f.translationCalls.get()); // Closing is not HTTP cancellation.
      assertTrue(f.translations.isEmpty());
      assertTrue(f.failures.isEmpty());
    }
  }

  private static void checkInFlightOldResponse(boolean failOld, SelectionResult newer) throws Exception {
    Fixture f = new Fixture(SelectionResult.found("A"), newer);
    Gate gate = new Gate(failOld);
    f.reply.set(gate::translate);
    f.coordinator.trigger();
    CompletableFuture<Void> oldWorker = CompletableFuture.runAsync(f.worker::runNext);
    try {
      assertTrue(gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS), "A service must be in flight");
      f.coordinator.trigger();
      f.worker.runNext(); // B captures/translates/completes before A is released.
      f.ui.runAll();
      assertEquals(newer.status() == SelectionResult.Status.Found
          ? List.of(translated("B")) : List.of(), f.translations);
    } finally {
      gate.release.countDown();
      oldWorker.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }
    f.ui.runAll();
    assertEquals(newer.status() == SelectionResult.Status.Found
        ? List.of(translated("B")) : List.of(), f.translations);
    assertEquals(newer.status() == SelectionResult.Status.Found ? 2 : 1, f.translationCalls.get());
    assertTrue(f.failures.isEmpty());
  }

  private static Translation translated(String source) {
    return new Translation(source, "translated-" + source);
  }

  private static final class Gate {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final boolean fail;

    Gate(boolean fail) { this.fail = fail; }

    Translation translate(String source) {
      if (source.equals("A")) {
        entered.countDown();
        try {
          if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS))
            throw new IllegalStateException("fake translation gate timed out");
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("fake translation gate interrupted", error);
        }
        if (fail) throw new IllegalStateException("fake old service failure");
      }
      return translated(source);
    }
  }

  private static final class Fixture {
    final QueuedExecutor worker = new QueuedExecutor();
    final QueuedExecutor ui = new QueuedExecutor();
    final AtomicInteger selectionCalls = new AtomicInteger();
    final AtomicInteger translationCalls = new AtomicInteger();
    final AtomicReference<Function<String, Translation>> reply = new AtomicReference<>(
        LatestTranslationCoordinatorTest::translated);
    final List<Translation> translations = new ArrayList<>();
    final List<Throwable> failures = new ArrayList<>();
    final LatestTranslationCoordinator coordinator;

    Fixture(SelectionResult... selections) {
      this(new ArrayDeque<>(List.of(selections))::remove);
    }

    Fixture(SelectedTextService selection) {
      TranslateSelectionUseCase useCase = new TranslateSelectionUseCase(() -> {
        selectionCalls.incrementAndGet();
        return selection.getSelectedText();
      }, source -> {
        translationCalls.incrementAndGet();
        return reply.get().apply(source);
      }, worker);
      coordinator = new LatestTranslationCoordinator(useCase, ui, translations::add, failures::add);
    }
  }

  private static final class QueuedExecutor implements Executor {
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    @Override public synchronized void execute(Runnable task) { tasks.add(task); }

    void runNext() {
      Runnable task;
      synchronized (this) { task = tasks.remove(); }
      task.run();
    }

    void runAll() {
      while (true) {
        Runnable task;
        synchronized (this) { task = tasks.poll(); }
        if (task == null) return;
        task.run();
      }
    }
  }
}
