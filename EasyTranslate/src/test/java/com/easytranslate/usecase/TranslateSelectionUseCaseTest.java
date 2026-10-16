package com.easytranslate.usecase;

import com.easytranslate.model.Translation;
import com.easytranslate.service.selection.SelectedTextService;
import com.easytranslate.service.selection.SelectionResult;
import com.easytranslate.service.translation.TranslationService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Offline checks of the real use case, using only injected in-memory services. */
class TranslateSelectionUseCaseTest {
  private static final long WAIT_SECONDS = 5;

  @Test
  void foundSelectionIsPassedToTranslationExactlyOnce() throws Exception {
    AtomicInteger selectionCalls = new AtomicInteger();
    RecordingTranslationService translator = new RecordingTranslationService();
    TranslateSelectionUseCase useCase = new TranslateSelectionUseCase(() -> {
      selectionCalls.incrementAndGet();
      return SelectionResult.found("  steady progress  ");
    }, translator);

    Optional<Translation> result = await(useCase.execute());

    assertEquals(Optional.of(new Translation("steady progress", "稳定进步")), result);
    assertEquals(1, selectionCalls.get());
    assertEquals(1, translator.calls.get());
    assertEquals("steady progress", translator.lastSource.get());
  }

  @Test
  void noSelectionReturnsEmptyWithoutCallingTranslation() throws Exception {
    RecordingTranslationService translator = new RecordingTranslationService();
    TranslateSelectionUseCase useCase = new TranslateSelectionUseCase(
        SelectionResult::none, translator);

    assertTrue(await(useCase.execute()).isEmpty());
    assertEquals(0, translator.calls.get());
  }

  @Test
  void unknownSelectionReturnsEmptyWithoutCallingTranslation() throws Exception {
    RecordingTranslationService translator = new RecordingTranslationService();
    TranslateSelectionUseCase useCase = new TranslateSelectionUseCase(
        SelectionResult::unknown, translator);

    assertTrue(await(useCase.execute()).isEmpty());
    assertEquals(0, translator.calls.get());
  }

  @Test
  void selectionFailureCompletesExceptionallyWithoutCallingTranslation() {
    RecordingTranslationService translator = new RecordingTranslationService();
    IllegalStateException failure = new IllegalStateException("fake selection failure");
    TranslateSelectionUseCase useCase = new TranslateSelectionUseCase(() -> {
      throw failure;
    }, translator);

    ExecutionException error = assertThrows(ExecutionException.class,
        () -> await(useCase.execute()));

    assertSame(failure, error.getCause());
    assertEquals(0, translator.calls.get());
  }

  @Test
  void translationFailureIsNotReportedAsAnEmptySuccessfulResult() {
    IllegalStateException failure = new IllegalStateException("fake translation failure");
    AtomicInteger calls = new AtomicInteger();
    TranslationService translator = source -> {
      calls.incrementAndGet();
      throw failure;
    };
    TranslateSelectionUseCase useCase = new TranslateSelectionUseCase(
        () -> SelectionResult.found("steady progress"), translator);

    ExecutionException error = assertThrows(ExecutionException.class,
        () -> await(useCase.execute()));

    assertSame(failure, error.getCause());
    assertEquals(1, calls.get());
  }

  @Test
  void selectionAndTranslationRunOffTheCallingThread() throws Exception {
    Thread caller = Thread.currentThread();
    AtomicReference<Thread> selectionThread = new AtomicReference<>();
    AtomicReference<Thread> translationThread = new AtomicReference<>();
    SelectedTextService selection = () -> {
      selectionThread.set(Thread.currentThread());
      return SelectionResult.found("steady progress");
    };
    TranslationService translator = source -> {
      translationThread.set(Thread.currentThread());
      return new Translation(source, "稳定进步");
    };

    await(new TranslateSelectionUseCase(selection, translator).execute());

    assertNotNull(selectionThread.get());
    assertNotNull(translationThread.get());
    assertNotSame(caller, selectionThread.get());
    assertNotSame(caller, translationThread.get());
  }

  @Test
  void executeReturnsWhileSelectionIsStillPending() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    RecordingTranslationService translator = new RecordingTranslationService();
    SelectedTextService selection = () -> {
      entered.countDown();
      try {
        if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
          throw new IllegalStateException("fake selection gate timed out");
        }
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("fake selection gate interrupted", error);
      }
      return SelectionResult.found("steady progress");
    };
    CompletableFuture<Optional<Translation>> future =
        new TranslateSelectionUseCase(selection, translator).execute();

    try {
      assertTrue(entered.await(WAIT_SECONDS, TimeUnit.SECONDS), "selection should start");
      assertFalse(future.isDone(), "future should remain pending until selection is released");
      assertEquals(0, translator.calls.get());
    } finally {
      release.countDown();
      // Always settle the worker, even if an assertion fails; no sleeps or background leaks.
      await(future);
    }
    assertEquals(1, translator.calls.get());
  }

  @Test
  void tooLongSelectionEndsWithoutCallingTranslationOrPublishingTranslating() throws Exception {
    var translator = new RecordingTranslationService();
    var operations = new java.util.ArrayList<TranslationOperation>();
    var useCase = new TranslateSelectionUseCase(
        () -> SelectionResult.found("a".repeat(1001)), translator);
    assertTrue(await(useCase.execute(() -> true, operations::add)).isEmpty());
    assertEquals(java.util.List.of(TranslationOperation.SELECTION_TOO_LONG), operations);
    assertEquals(0, translator.calls.get());
    assertNull(translator.lastSource.get());
  }

  @Test
  void boundarySelectionsReachTranslationOnceAndInFull() throws Exception {
    for (int length : new int[]{999, 1000}) {
      var translator = new RecordingTranslationService();
      String source = "a".repeat(length - 1) + "Z";
      var useCase = new TranslateSelectionUseCase(() -> SelectionResult.found(source), translator);
      assertEquals(source, await(useCase.execute()).orElseThrow().sourceText());
      assertEquals(source, translator.lastSource.get());
      assertEquals(1, translator.calls.get());
    }
  }

  @Test
  void confirmedEmptySelectionPublishesNoSelectionRatherThanSuccess() throws Exception {
    var translator = new RecordingTranslationService();
    var useCase = new TranslateSelectionUseCase(SelectionResult::none, translator);
    var operations = new java.util.ArrayList<TranslationOperation>();
    assertTrue(await(useCase.execute(() -> true, operations::add)).isEmpty());
    assertEquals(java.util.List.of(TranslationOperation.NO_SELECTION), operations);
    assertEquals(0, translator.calls.get());
  }

  private static Optional<Translation> await(
      CompletableFuture<Optional<Translation>> future) throws Exception {
    return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
  }

  private static final class RecordingTranslationService implements TranslationService {
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> lastSource = new AtomicReference<>();

    @Override
    public Translation translate(String sourceText) {
      calls.incrementAndGet();
      lastSource.set(sourceText);
      return new Translation(sourceText, "稳定进步");
    }
  }
}
