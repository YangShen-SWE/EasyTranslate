package com.easytranslate.usecase;

import com.easytranslate.model.Translation;
import com.easytranslate.service.selection.SelectionResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class VocabularySaveBoundaryTest {
  private Translation text(String source) { return new Translation(source, "测试释义"); }

  @Test void explicitSaveOnlyAfterDisplay() {
    List<VocabularySaveBoundary.Candidate> writes = new ArrayList<>();
    var b = new VocabularySaveBoundary(c -> { writes.add(c); return VocabularySaveBoundary.InsertResult.INSERTED; });
    assertEquals(VocabularySaveBoundary.Result.INELIGIBLE, b.save(1));
    b.displayed(1, text(" Hello   World "));
    assertTrue(writes.isEmpty());
    var c = b.candidate().orElseThrow();
    assertEquals("hello world", c.key()); assertEquals("Hello World", c.source());
    assertEquals(VocabularySaveBoundary.Result.SAVED, b.save(1));
    assertEquals(List.of(c), writes);
  }

  @Test void repeatedClicksAndCaseWhitespaceVariantsDoNotRepeatInsert() {
    AtomicInteger calls = new AtomicInteger();
    var b = new VocabularySaveBoundary(c -> { calls.incrementAndGet(); return VocabularySaveBoundary.InsertResult.INSERTED; });
    b.displayed(1, text("Hello World")); b.save(1);
    assertEquals(VocabularySaveBoundary.Result.DUPLICATE, b.save(1));
    b.displayed(2, text(" hello  WORLD "));
    assertEquals(VocabularySaveBoundary.Result.DUPLICATE, b.save(2));
    assertEquals(1, calls.get());
  }

  @Test void durableUniquenessBelongsToAtomicRepositoryAcrossEntriesAndSessions() {
    Set<String> stored = new HashSet<>();
    VocabularySaveBoundary.Repository repo = c -> stored.add(c.key())
        ? VocabularySaveBoundary.InsertResult.INSERTED : VocabularySaveBoundary.InsertResult.ALREADY_EXISTS;
    var b = new VocabularySaveBoundary(repo);
    b.displayed(1, text("alpha")); assertEquals(VocabularySaveBoundary.Result.SAVED, b.save(1));
    b.displayed(2, text("beta")); b.save(2);
    b.displayed(3, text("ALPHA")); assertEquals(VocabularySaveBoundary.Result.DUPLICATE, b.save(3));
    b.close(); var next = new VocabularySaveBoundary(repo); next.displayed(1, text("alpha"));
    assertEquals(VocabularySaveBoundary.Result.DUPLICATE, next.save(1)); assertEquals(2, stored.size());
  }

  @Test void invalidSourcesAndTranslationsNeverReachStorage() {
    var b = new VocabularySaveBoundary(c -> { fail("invalid write"); return null; });
    for (String source : List.of("", " ", "one two three four five six", "hello.", "123", "中文", "x".repeat(81), "hello\u0000")) {
      b.displayed(1, text(source)); assertTrue(b.candidate().isEmpty(), source);
      assertEquals(VocabularySaveBoundary.Result.INELIGIBLE, b.save(1));
    }
    for (Translation t : Arrays.asList(null, new Translation(null,"x"),new Translation("word",null),
        new Translation("word"," "),new Translation("word","x".repeat(4001)),new Translation("word","bad\u0000"))) {
      b.displayed(1,t); assertTrue(b.candidate().isEmpty());
    }
  }

  @Test void conservativeWordPhraseAndApostropheKeys() {
    var b = new VocabularySaveBoundary(c -> VocabularySaveBoundary.InsertResult.INSERTED);
    for (String source : List.of("well-known", "don't", "Don’t", "one two three four five", "a".repeat(80))) {
      b.displayed(1,text(source)); assertTrue(b.candidate().isPresent(),source);
    }
    b.displayed(2,text("Don’t")); assertEquals("don't",b.candidate().orElseThrow().key());
  }

  @Test void storageFailureCanRetryWithoutMarkingSaved() {
    AtomicInteger calls = new AtomicInteger();
    var b = new VocabularySaveBoundary(c -> { if(calls.incrementAndGet()==1) throw new Exception("fake sensitive db detail"); return VocabularySaveBoundary.InsertResult.INSERTED; });
    b.displayed(1,text("alpha"));
    assertEquals(VocabularySaveBoundary.Result.STORAGE_FAILED,b.save(1)); assertTrue(b.candidate().isPresent());
    assertEquals(VocabularySaveBoundary.Result.SAVED,b.save(1)); assertEquals(2,calls.get());
  }

  @Test void nullStorageResponseIsFailureNotSuccess() {
    var b = new VocabularySaveBoundary(c -> null); b.displayed(1,text("alpha"));
    assertEquals(VocabularySaveBoundary.Result.STORAGE_FAILED,b.save(1));
  }

  @Test void staleSnapshotAndCloseAreRejected() {
    var b = new VocabularySaveBoundary(c -> { fail("stale write"); return null; });
    b.displayed(2,text("beta")); assertEquals(VocabularySaveBoundary.Result.STALE,b.save(1));
    b.invalidate(); assertEquals(VocabularySaveBoundary.Result.INELIGIBLE,b.save(2));
    b.close(); b.displayed(3,text("gamma")); assertTrue(b.candidate().isEmpty());
  }

  private static class QueueExecutor implements Executor {
    final Queue<Runnable> tasks = new ArrayDeque<>();
    public void execute(Runnable r) { tasks.add(r); }
    void drain() { while(!tasks.isEmpty()) tasks.remove().run(); }
  }
  private final class Flow {
    final QueueExecutor worker = new QueueExecutor(), ui = new QueueExecutor();
    final Queue<SelectionResult> selections = new ArrayDeque<>();
    final List<Translation> displayed = new ArrayList<>();
    final List<TranslationOperation> operations = new ArrayList<>();
    final AtomicInteger writes = new AtomicInteger();
    final VocabularySaveBoundary boundary = new VocabularySaveBoundary(c -> { writes.incrementAndGet(); throw new Exception("fake DB unavailable"); });
    final LatestTranslationCoordinator coordinator;
    Flow() {
      var useCase = new TranslateSelectionUseCase(selections::remove, VocabularySaveBoundaryTest.this::text, worker);
      coordinator = new LatestTranslationCoordinator(useCase, ui, displayed::add, e -> fail(e), operations::add, null, boundary);
    }
    void trigger(SelectionResult selection) { selections.add(selection); coordinator.trigger(); worker.drain(); ui.drain(); }
  }

  @Test void queuedOldSuccessNeverGrantsEligibilityAndNewTriggerRevokesImmediately() {
    Flow f = new Flow(); f.selections.add(SelectionResult.found("alpha")); f.coordinator.trigger(); f.worker.drain();
    assertTrue(f.boundary.candidate().isEmpty());
    f.coordinator.trigger(); f.ui.drain(); assertTrue(f.boundary.candidate().isEmpty());
    f.selections.add(SelectionResult.found("beta")); f.worker.drain(); f.ui.drain();
    assertEquals("beta",f.boundary.candidate().orElseThrow().key());
    assertEquals(List.of(text("beta")),f.displayed);
    f.coordinator.trigger(); assertTrue(f.boundary.candidate().isEmpty()); f.coordinator.close();
  }

  @Test void retainedOldTranslationCannotBeSavedAfterEmptyUnreadableOrTooLong() {
    for (SelectionResult invalid : List.of(SelectionResult.none(),SelectionResult.unknown(),SelectionResult.tooLong())) {
      Flow f = new Flow(); f.trigger(SelectionResult.found("alpha")); assertTrue(f.boundary.candidate().isPresent());
      f.trigger(invalid); assertTrue(f.boundary.candidate().isEmpty()); assertEquals(1,f.displayed.size());
      assertEquals(0,f.writes.get());
    }
  }

  @Test void dbFailureDoesNotChangeSuccessfulTranslationOrPreventNextOne() {
    Flow f = new Flow(); f.trigger(SelectionResult.found("alpha"));
    long intent = f.boundary.candidate().orElseThrow().intent();
    assertEquals(VocabularySaveBoundary.Result.STORAGE_FAILED,f.boundary.save(intent));
    assertEquals(TranslationOperation.SUCCEEDED,f.operations.getLast());
    f.trigger(SelectionResult.found("beta")); assertEquals(2,f.displayed.size());
    f.coordinator.close(); assertTrue(f.boundary.candidate().isEmpty());
  }

  @Test void failedNewTranslationRevokesOldCandidateWithoutDatabaseCalls() {
    var worker = new QueueExecutor(); var ui = new QueueExecutor();
    var b = new VocabularySaveBoundary(c -> { fail("failed translation write"); return null; });
    AtomicInteger calls = new AtomicInteger(); List<Throwable> errors = new ArrayList<>();
    var useCase = new TranslateSelectionUseCase(() -> SelectionResult.found("alpha"), s -> {
      if(calls.incrementAndGet() > 1) throw new IllegalStateException("fake service failure");
      return text(s);
    },worker);
    var coordinator = new LatestTranslationCoordinator(useCase,ui,t -> {},errors::add,o -> {},null,b);
    coordinator.trigger(); worker.drain(); ui.drain(); assertTrue(b.candidate().isPresent());
    coordinator.trigger(); worker.drain(); ui.drain(); assertTrue(b.candidate().isEmpty());
    assertEquals(1,errors.size());
  }

  @Test void consentRefreshRevokesCandidateAndQueuedResponse() {
    var values = new HashMap<String,String>();
    var preferences = new java.util.prefs.AbstractPreferences(null, "") {
      protected String getSpi(String k) { return values.get(k); }
      protected void putSpi(String k,String v) { values.put(k,v); }
      protected void removeSpi(String k) { values.remove(k); }
      protected void removeNodeSpi() { values.clear(); }
      protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
      protected String[] childrenNamesSpi() { return new String[0]; }
      protected java.util.prefs.AbstractPreferences childSpi(String n) { throw new UnsupportedOperationException(); }
      protected void syncSpi() {} protected void flushSpi() {}
    };
    var policy = new com.easytranslate.config.OnlineTranslationPolicy(preferences); policy.choose(true);
    var worker = new QueueExecutor(); var ui = new QueueExecutor();
    var b = new VocabularySaveBoundary(c -> { fail("disabled write"); return null; });
    var useCase = new TranslateSelectionUseCase(() -> SelectionResult.found("alpha"),this::text,worker);
    var coordinator = new LatestTranslationCoordinator(useCase,ui,t -> {},e -> fail(e),o -> {},policy,b);
    coordinator.trigger(); worker.drain(); ui.drain(); assertTrue(b.candidate().isPresent());
    coordinator.trigger(); worker.drain(); policy.choose(false); coordinator.refreshOnlineAccess();
    assertTrue(b.candidate().isEmpty()); ui.drain(); assertTrue(b.candidate().isEmpty());
    coordinator.trigger(); worker.drain(); ui.drain(); assertTrue(b.candidate().isEmpty());
  }

  @Test void rejectedDisplayCannotGrantEligibility() {
    var worker = new QueueExecutor(); var ui = new QueueExecutor();
    var b = new VocabularySaveBoundary(c -> { fail("not displayed"); return null; });
    var useCase = new TranslateSelectionUseCase(() -> SelectionResult.found("alpha"),this::text,worker);
    var coordinator = new LatestTranslationCoordinator(useCase,ui,t -> { throw new IllegalStateException("fake view failure"); },e -> {},o -> {},null,b);
    coordinator.trigger(); worker.drain(); assertThrows(IllegalStateException.class,ui::drain);
    assertTrue(b.candidate().isEmpty());
  }

  @Test void reentrantCloseDuringDisplayCannotGrantEligibility() {
    var boundary = new VocabularySaveBoundary(c -> { fail("write after close"); return null; });
    var holder = new LatestTranslationCoordinator[1];
    var useCase = new TranslateSelectionUseCase(() -> SelectionResult.found("alpha"),this::text,Runnable::run);
    holder[0] = new LatestTranslationCoordinator(useCase,Runnable::run,t -> holder[0].close(),e -> fail(e),o -> {},null,boundary);
    holder[0].trigger(); assertTrue(boundary.candidate().isEmpty());
  }
}
