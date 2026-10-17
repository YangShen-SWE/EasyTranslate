package com.easytranslate.service.hotkey;

import java.util.Objects;
import java.util.function.IntPredicate;

/** Hook-thread-only decision; never consumes keys, captures text or starts translation itself. */
final class TabTriggerFilter {
  static final int VK_TAB = 0x09;
  static final int KEY_DOWN = 0x0100, KEY_UP = 0x0101;
  static final int SYS_KEY_DOWN = 0x0104, SYS_KEY_UP = 0x0105;
  private static final int ALT_DOWN = 0x20;
  private static final int INJECTED = 0x10 | 0x02;
  private final IntPredicate keyDown;
  private boolean tabDown;

  /** A Tab already held at installation must be released before it can trigger. */
  TabTriggerFilter(boolean tabInitiallyDown, IntPredicate keyDown) {
    this.tabDown = tabInitiallyDown;
    this.keyDown = Objects.requireNonNull(keyDown, "keyDown");
  }

  boolean onKeyEvent(int hookCode, int event, int virtualKey, int flags) {
    if (hookCode < 0 || virtualKey != VK_TAB || (flags & INJECTED) != 0) return false;
    if (event == KEY_UP || event == SYS_KEY_UP) {
      tabDown = false;
      return false;
    }
    if (event != KEY_DOWN && event != SYS_KEY_DOWN) return false;
    if (tabDown) return false;
    // Latch even rejected chords: releasing a modifier during held Tab must not trigger a repeat.
    tabDown = true;
    return event == KEY_DOWN && (flags & ALT_DOWN) == 0
        && !keyDown.test(0x10)  // Shift (either side)
        && !keyDown.test(0x11)  // Control (either side, including AltGr's Control)
        && !keyDown.test(0x12)  // Alt (either side)
        && !keyDown.test(0x5B)  // left Windows
        && !keyDown.test(0x5C); // right Windows
  }
}
