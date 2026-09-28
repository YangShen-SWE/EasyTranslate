package com.easytranslate.service.hotkey;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Passive, optional effects hooks. Every event is passed through unchanged. */
public final class WindowsInputActivityService implements AutoCloseable {
  private static final int WM_LBUTTONDOWN = 0x0201, WM_RBUTTONDOWN = 0x0204;
  private final InputActivityBuffer activity = new InputActivityBuffer();
  private final AtomicBoolean closed = new AtomicBoolean();
  private volatile int threadId;
  private volatile String failure;
  private volatile boolean running;
  private LowLevelKeyboardProc keyboardCallback;
  private LowLevelMouseProc mouseCallback;

  public WindowsInputActivityService() {
    Thread thread = new Thread(this::run, "companion-input-effects");
    thread.setDaemon(true);
    thread.start();
  }

  public int drain() { return activity.drain(); }
  public boolean isRunning() { return running; }
  public String failure() { return failure; }

  private void run() {
    HHOOK keyboard = null, mouse = null;
    try {
      MSG message = new MSG();
      User32.INSTANCE.PeekMessage(message, null, 0, 0, 0);
      threadId = Kernel32.INSTANCE.GetCurrentThreadId();
      if (closed.get()) return;
      keyboardCallback = (code, kind, info) -> {
        try {
          if (code >= 0 && info != null && !closed.get()) {
            int event = kind.intValue();
            if (event == WinUser.WM_KEYDOWN || event == WinUser.WM_SYSKEYDOWN) activity.key(info.vkCode, true);
            else if (event == WinUser.WM_KEYUP || event == WinUser.WM_SYSKEYUP) activity.key(info.vkCode, false);
          }
        } catch (RuntimeException ignored) { }
        return User32.INSTANCE.CallNextHookEx(null, code, kind,
            new LPARAM(info == null ? 0 : Pointer.nativeValue(info.getPointer())));
      };
      mouseCallback = (code, kind, info) -> {
        try {
          if (code >= 0 && !closed.get()) {
            if (kind.intValue() == WM_LBUTTONDOWN) activity.mouse(true);
            else if (kind.intValue() == WM_RBUTTONDOWN) activity.mouse(false);
          }
        } catch (RuntimeException ignored) { }
        return User32.INSTANCE.CallNextHookEx(null, code, kind,
            new LPARAM(info == null ? 0 : Pointer.nativeValue(info.getPointer())));
      };
      keyboard = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, keyboardCallback, Kernel32.INSTANCE.GetModuleHandle(null), 0);
      mouse = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_MOUSE_LL, mouseCallback, Kernel32.INSTANCE.GetModuleHandle(null), 0);
      if (keyboard == null || mouse == null) throw new IllegalStateException("Windows error " + Native.getLastError());
      running = true;
      while (!closed.get()) {
        int result = User32.INSTANCE.GetMessage(message, null, 0, 0);
        if (result == 0) break;
        if (result == -1) throw new IllegalStateException("Input message loop failed");
        User32.INSTANCE.TranslateMessage(message);
        User32.INSTANCE.DispatchMessage(message);
      }
    } catch (Throwable error) {
      failure = "键鼠动效监听不可用";
    } finally {
      running = false;
      if (keyboard != null) User32.INSTANCE.UnhookWindowsHookEx(keyboard);
      if (mouse != null) User32.INSTANCE.UnhookWindowsHookEx(mouse);
      threadId = 0;
      keyboardCallback = null;
      mouseCallback = null;
    }
  }

  @Override public void close() {
    closed.set(true);
    int id = threadId;
    if (id != 0) User32.INSTANCE.PostThreadMessage(id, WinUser.WM_QUIT, new WPARAM(0), new LPARAM(0));
    activity.drain();
  }
}
