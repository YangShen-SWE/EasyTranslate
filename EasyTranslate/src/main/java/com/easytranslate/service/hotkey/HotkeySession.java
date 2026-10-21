package com.easytranslate.service.hotkey;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** One native service per activation; closed-generation notifications are ignored. */
public final class HotkeySession implements AutoCloseable {
  private final Supplier<GlobalHotkeyService> factory;
  private GlobalHotkeyService service;
  private AtomicBoolean active;
  public HotkeySession(Supplier<GlobalHotkeyService> factory) {
    this.factory = Objects.requireNonNull(factory);
  }
  public synchronized void start(Runnable trigger) {
    Objects.requireNonNull(trigger);
    if (service != null) return;
    GlobalHotkeyService next = Objects.requireNonNull(factory.get());
    AtomicBoolean token = new AtomicBoolean(true);
    service = next; active = token;
    try {
      next.start(() -> { if (token.get()) trigger.run(); });
    } catch (RuntimeException | LinkageError failure) {
      token.set(false); service = null; active = null;
      try { next.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
      throw failure;
    }
  }
  @Override public synchronized void close() {
    if (service == null) return;
    active.set(false);
    GlobalHotkeyService old = service;
    service = null; active = null;
    old.close();
  }
}
