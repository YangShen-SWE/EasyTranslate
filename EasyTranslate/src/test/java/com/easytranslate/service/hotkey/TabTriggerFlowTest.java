package com.easytranslate.service.hotkey;

import com.easytranslate.model.Translation;
import com.easytranslate.service.selection.SelectionResult;
import com.easytranslate.usecase.LatestTranslationCoordinator;
import com.easytranslate.usecase.TranslateSelectionUseCase;
import com.easytranslate.usecase.TranslationOperation;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import static com.easytranslate.service.hotkey.TabTriggerFilter.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real filter/use case/coordinator; manual notification/worker/UI queues, fake selection/translation.
 * Does not install or exercise Windows hooks; IME candidate behaviour remains a manual test. */
class TabTriggerFlowTest {
  @Test void heldTabCreatesOnlyOneNotificationReadAndTranslation() {
    var f = new Fixture(SelectionResult.found("sample"));
    f.key(KEY_DOWN, VK_TAB, 0);
    for (int i = 0; i < 100; i++) f.key(KEY_DOWN, VK_TAB, 0);
    assertEquals(1, f.notifications.tasks.size());
    assertEquals(0, f.reads);
    f.drain();
    assertEquals(1, f.reads);
    assertEquals(1, f.calls);
    assertEquals(List.of(new Translation("sample", "translated")), f.results);
    f.key(KEY_UP, VK_TAB, 0);
    f.key(KEY_DOWN, VK_TAB, 0); f.drain();
    assertEquals(2, f.calls);
  }

  @Test void blockedChordsInjectedAndSystemEventsDoNotReadSelection() {
    var f = new Fixture(SelectionResult.found("sample"));
    for (int modifier : new int[]{0x10, 0x11, 0x12, 0x5B, 0x5C}) {
      f.modifiers.add(modifier); f.key(KEY_DOWN, VK_TAB, 0);
      f.modifiers.clear(); f.key(KEY_DOWN, VK_TAB, 0); f.key(KEY_UP, VK_TAB, 0);
    }
    f.key(KEY_DOWN, VK_TAB, 0x10); f.key(KEY_UP, VK_TAB, 0x10);
    f.key(SYS_KEY_DOWN, VK_TAB, 0x20); f.key(SYS_KEY_UP, VK_TAB, 0x20);
    f.drain();
    assertEquals(0, f.reads); assertEquals(0, f.calls);
    assertTrue(f.operations.isEmpty());
    f.key(KEY_DOWN, VK_TAB, 0); f.drain();
    assertEquals(1, f.calls);
  }

  @Test void noneUnknownAndTooLongHaveTerminalStateAndNeverTranslate() {
    for (SelectionResult selection : List.of(SelectionResult.none(), SelectionResult.unknown(), SelectionResult.tooLong())) {
      var f = new Fixture(selection);
      f.key(KEY_DOWN, VK_TAB, 0); f.drain();
      assertEquals(1, f.reads); assertEquals(0, f.calls);
      assertTrue(f.results.isEmpty());
      assertEquals(selection.status() == SelectionResult.Status.None ? TranslationOperation.NO_SELECTION
          : selection.status() == SelectionResult.Status.Unknown ? TranslationOperation.UNREADABLE
          : TranslationOperation.SELECTION_TOO_LONG, f.operations.getLast());
    }
  }

  @Test void newEmptyPressInvalidatesSuccessAlreadyQueuedForUi() {
    var f = new Fixture(SelectionResult.found("old"));
    f.key(KEY_DOWN, VK_TAB, 0); f.notifications.runAll(); f.worker.runAll();
    f.selection = SelectionResult.none();
    f.key(KEY_UP, VK_TAB, 0); f.key(KEY_DOWN, VK_TAB, 0); f.notifications.runAll();
    f.worker.runAll(); f.ui.runAll();
    assertTrue(f.results.isEmpty());
    assertEquals(1, f.calls);
    assertEquals(TranslationOperation.NO_SELECTION, f.operations.getLast());
  }

  @Test void rejectedChordDoesNotSupersedeAnAlreadyAcceptedIntent() {
    var f = new Fixture(SelectionResult.found("sample"));
    f.key(KEY_DOWN, VK_TAB, 0); f.notifications.runAll(); f.worker.runAll();
    f.key(KEY_UP, VK_TAB, 0); f.modifiers.add(0x11); f.key(KEY_DOWN, VK_TAB, 0);
    f.drain();
    assertEquals(1, f.results.size());
    assertEquals(TranslationOperation.SUCCEEDED, f.operations.getLast());
  }

  @Test void effectsMailboxIsIndependentOfTranslationAndRejectedChordsMayAnimate() {
    var f = new Fixture(SelectionResult.found("sample"));
    f.key(KEY_DOWN, 0x41, 0); f.effects.mouse(true);
    f.modifiers.add(0x11); f.key(KEY_DOWN, VK_TAB, 0); f.drain();
    assertEquals(InputActivityBuffer.LEFT | InputActivityBuffer.MOUSE, f.effects.drain());
    assertEquals(0, f.reads); assertEquals(0, f.calls);
    assertEquals(0, f.effects.drain());
    f.key(KEY_UP, VK_TAB, 0); f.modifiers.clear();
    f.key(KEY_DOWN, VK_TAB, 0);
    // Draining the animation mailbox cannot trigger or cancel the pending translation.
    assertEquals(InputActivityBuffer.LEFT, f.effects.drain());
    f.drain(); assertEquals(1, f.calls);
  }

  @Test void coordinatorCloseStillRejectsNotificationAlreadyWaiting() {
    var f = new Fixture(SelectionResult.found("sample"));
    f.key(KEY_DOWN, VK_TAB, 0); f.coordinator.close(); f.drain();
    assertEquals(0, f.reads); assertEquals(0, f.calls);
    assertTrue(f.results.isEmpty()); assertTrue(f.operations.isEmpty());
  }

  @Test void tabInAnyOwnWindowDoesNotQueueReadSendOrOperationAndExternalPressRecovers() {
    var f = new Fixture(SelectionResult.found("synthetic input"));
    f.ownWindow = true;
    f.key(KEY_DOWN, VK_TAB, 0); f.drain();
    assertEquals(0, f.reads); assertEquals(0, f.calls);
    assertTrue(f.operations.isEmpty());
    assertEquals(InputActivityBuffer.LEFT, f.effects.drain());
    f.ownWindow = false;
    f.key(KEY_DOWN, VK_TAB, 0); f.drain();
    assertEquals(0, f.reads);
    f.key(KEY_UP, VK_TAB, 0); f.key(KEY_DOWN, VK_TAB, 0); f.drain();
    assertEquals(1, f.reads); assertEquals(1, f.calls);
  }

  @Test void ownWindowTraversalDoesNotInvalidateAnExternalTranslationAlreadyQueued() {
    var f = new Fixture(SelectionResult.found("synthetic input"));
    f.key(KEY_DOWN, VK_TAB, 0); f.notifications.runAll(); f.worker.runAll();
    f.key(KEY_UP, VK_TAB, 0); f.ownWindow = true; f.key(KEY_DOWN, VK_TAB, 0);
    f.drain();
    assertEquals(1, f.reads); assertEquals(1, f.calls); assertEquals(1, f.results.size());
    assertEquals(TranslationOperation.SUCCEEDED, f.operations.getLast());
  }

  private static final class ManualQueue implements Executor {
    final Queue<Runnable> tasks = new ArrayDeque<>();
    public void execute(Runnable action) { tasks.add(action); }
    void runAll() { while (!tasks.isEmpty()) tasks.remove().run(); }
  }

  private static final class Fixture {
    final Set<Integer> modifiers = new HashSet<>();
    boolean ownWindow;
    final TabTriggerFilter filter = new TabTriggerFilter(false, modifiers::contains, () -> ownWindow);
    final InputActivityBuffer effects = new InputActivityBuffer();
    final ManualQueue notifications = new ManualQueue(), worker = new ManualQueue(), ui = new ManualQueue();
    final List<Translation> results = new ArrayList<>();
    final List<TranslationOperation> operations = new ArrayList<>();
    final LatestTranslationCoordinator coordinator;
    SelectionResult selection;
    int reads, calls;
    Fixture(SelectionResult selection) {
      this.selection = selection;
      coordinator = new LatestTranslationCoordinator(new TranslateSelectionUseCase(
          () -> { reads++; return this.selection; },
          text -> { calls++; return new Translation(text, "translated"); }, worker),
          ui, results::add, error -> fail(error), operations::add);
    }
    void key(int event, int key, int flags) {
      effects.key(key, event == KEY_DOWN || event == SYS_KEY_DOWN);
      if (filter.onKeyEvent(0, event, key, flags)) notifications.execute(coordinator::trigger);
    }
    void drain() { notifications.runAll(); worker.runAll(); ui.runAll(); }
  }
}
