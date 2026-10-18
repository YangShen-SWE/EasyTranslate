package com.easytranslate.service.hotkey;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static com.easytranslate.service.hotkey.TabTriggerFilter.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic events only: no Windows DLL, hook installation, keyboard injection or IME. */
class TabTriggerFilterTest {
  private final Set<Integer> modifiers = new HashSet<>();
  private final TabTriggerFilter filter = new TabTriggerFilter(false, modifiers::contains);

  private boolean event(int event, int flags) { return filter.onKeyEvent(0, event, VK_TAB, flags); }

  @Test void physicalPressTriggersOnceUntilRelease() {
    assertTrue(event(KEY_DOWN, 0));
    for (int i = 0; i < 100; i++) assertFalse(event(KEY_DOWN, 0));
    assertFalse(event(KEY_UP, 0));
    assertTrue(event(KEY_DOWN, 0));
  }

  @Test void eachModifierBlocksBothInitialPressAndRepeatsAfterModifierRelease() {
    for (int modifier : new int[]{0x10, 0x11, 0x12, 0x5B, 0x5C}) {
      modifiers.add(modifier);
      assertFalse(event(KEY_DOWN, 0), "modifier " + modifier);
      modifiers.clear();
      assertFalse(event(KEY_DOWN, 0));
      event(KEY_UP, 0);
      assertTrue(event(KEY_DOWN, 0));
      event(KEY_UP, 0);
    }
  }

  @Test void altFlagBlocksEvenWhenModifierSnapshotIsClear() {
    assertFalse(event(KEY_DOWN, 0x20));
    assertFalse(event(KEY_DOWN, 0));
    event(KEY_UP, 0);
    assertTrue(event(KEY_DOWN, 0));
  }

  @Test void systemKeyDownDoesNotTriggerAndSystemKeyUpRearms() {
    assertFalse(event(SYS_KEY_DOWN, 0));
    assertFalse(event(KEY_DOWN, 0));
    event(SYS_KEY_UP, 0);
    assertTrue(event(KEY_DOWN, 0));
  }

  @Test void heldAtInstallationWaitsForPhysicalRelease() {
    var held = new TabTriggerFilter(true, key -> false);
    assertFalse(held.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    assertFalse(held.onKeyEvent(0, KEY_UP, VK_TAB, 0));
    assertTrue(held.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
  }

  @Test void injectedPressesNeverTriggerOrLatchPhysicalTab() {
    for (int flags : new int[]{0x10, 0x02, 0x12}) {
      assertFalse(event(KEY_DOWN, flags));
      assertTrue(event(KEY_DOWN, 0));
      event(KEY_UP, 0);
    }
  }

  @Test void injectedReleaseCannotRearmHeldPhysicalTab() {
    assertTrue(event(KEY_DOWN, 0));
    assertFalse(event(KEY_UP, 0x10));
    assertFalse(event(KEY_UP, 0x02));
    assertFalse(event(KEY_DOWN, 0));
    event(KEY_UP, 0);
    assertTrue(event(KEY_DOWN, 0));
  }

  @Test void negativeHookCodeDoesNotTriggerOrMutateLatch() {
    assertFalse(filter.onKeyEvent(-1, KEY_DOWN, VK_TAB, 0));
    assertTrue(event(KEY_DOWN, 0));
    assertFalse(filter.onKeyEvent(-1, KEY_UP, VK_TAB, 0));
    assertFalse(event(KEY_DOWN, 0));
  }

  @Test void unrelatedKeysAndUnknownMessagesNeverQueryModifiers() {
    var reads = new AtomicInteger();
    var f = new TabTriggerFilter(false, key -> { reads.incrementAndGet(); return false; });
    assertFalse(f.onKeyEvent(0, KEY_DOWN, 0x41, 0));
    assertFalse(f.onKeyEvent(0, 0x999, VK_TAB, 0));
    assertFalse(f.onKeyEvent(0, KEY_UP, VK_TAB, 0));
    assertEquals(0, reads.get());
    assertTrue(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    assertEquals(5, reads.get());
  }

  @Test void otherKeyReleaseDoesNotRearmTab() {
    assertTrue(event(KEY_DOWN, 0));
    assertFalse(filter.onKeyEvent(0, KEY_UP, 0x41, 0));
    assertFalse(event(KEY_DOWN, 0));
  }

  @Test void modifierPressedDuringHoldDoesNotCreateAnotherIntent() {
    assertTrue(event(KEY_DOWN, 0));
    modifiers.add(0x11);
    assertFalse(event(KEY_DOWN, 0));
    modifiers.clear();
    assertFalse(event(KEY_DOWN, 0));
    event(KEY_UP, 0);
    assertTrue(event(KEY_DOWN, 0));
  }

  @Test void altGrStyleControlAndAltSnapshotBlocksWithoutClaimingImeDetection() {
    modifiers.addAll(Set.of(0x11, 0x12));
    assertFalse(event(KEY_DOWN, 0));
    modifiers.clear();
    assertFalse(event(KEY_DOWN, 0));
    event(KEY_UP, 0);
    assertTrue(event(KEY_DOWN, 0));
  }

  @Test void failingModifierReadDoesNotEnableRepeatedTrigger() {
    var reads = new AtomicInteger();
    var f = new TabTriggerFilter(false, key -> {
      if (reads.getAndIncrement() == 0) throw new IllegalStateException("fake snapshot failure");
      return false;
    });
    assertThrows(IllegalStateException.class, () -> f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    assertFalse(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    f.onKeyEvent(0, KEY_UP, VK_TAB, 0);
    assertTrue(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
  }

  @Test void ownWindowSuppressionLatchesUntilReleaseEvenIfFocusMovesAway() {
    var ownWindow = new java.util.concurrent.atomic.AtomicBoolean(true);
    var f = new TabTriggerFilter(false, key -> false, ownWindow::get);
    assertFalse(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    ownWindow.set(false);
    assertFalse(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    f.onKeyEvent(0, KEY_UP, VK_TAB, 0);
    assertTrue(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
  }

  @Test void eachFreshPressSamplesForegroundGateOnce() {
    var samples = new AtomicInteger();
    var f = new TabTriggerFilter(false, key -> false, () -> samples.getAndIncrement() == 1);
    assertTrue(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    assertFalse(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    assertEquals(1, samples.get());
    f.onKeyEvent(0, KEY_UP, VK_TAB, 0);
    assertFalse(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    assertEquals(2, samples.get());
  }

  @Test void failedForegroundQueryCannotTurnHeldTabIntoARepeatedIntent() {
    var f = new TabTriggerFilter(false, key -> false, () -> { throw new IllegalStateException("fake foreground failure"); });
    assertThrows(IllegalStateException.class, () -> f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    assertFalse(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
  }

  @Test void rejectedChordDoesNotQueryForegroundAndNullGateIsRejected() {
    var f = new TabTriggerFilter(false, key -> key == 0x10, () -> fail("unneeded foreground query"));
    assertFalse(f.onKeyEvent(0, KEY_DOWN, VK_TAB, 0));
    assertThrows(NullPointerException.class, () -> new TabTriggerFilter(false, key -> false, null));
  }

  @Test void modifierReaderIsRequired() {
    assertThrows(NullPointerException.class, () -> new TabTriggerFilter(false, null));
  }
}
