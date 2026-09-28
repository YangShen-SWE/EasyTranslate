package com.easytranslate.service.hotkey;

import java.util.concurrent.atomic.AtomicInteger;

/** A bounded animation mailbox: only paw bits survive, never text or input history. */
public final class InputActivityBuffer {
  public static final int LEFT = 1, RIGHT = 2, MOUSE = 4;
  private final boolean[] held = new boolean[256];
  private final AtomicInteger pending = new AtomicInteger();

  public void key(int code, boolean down) {
    if (code < 0 || code >= held.length) return;
    boolean wasDown = held[code];
    held[code] = down;
    if (!down || wasDown) return;
    int side = sideForKey(code);
    pending.getAndUpdate(bits -> bits | side);
  }

  public static int sideForKey(int code) {
    if (code == 0x20) return LEFT | RIGHT;
    if ("12345QWERTASDFGZXCVB".indexOf(code) >= 0 || code == 0x09 || code == 0xA0 || code == 0xA2) return LEFT;
    if ((code >= 0x30 && code <= 0x5A) || (code >= 0x60 && code <= 0x6F)
        || (code >= 0xBA && code <= 0xDE) || code == 0x0D || code == 0x08 || code == 0xA1 || code == 0xA3) return RIGHT;
    return 0;
  }

  public void mouse(boolean left) {
    pending.getAndUpdate(bits -> bits | MOUSE | (left ? LEFT : RIGHT));
  }

  public int drain() { return pending.getAndSet(0); }
}
