package com.easytranslate.service.hotkey;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.LowLevelKeyboardProc;
import com.sun.jna.platform.win32.WinUser.MSG;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WindowsTabObserverService implements GlobalHotkeyService {
  private static final int VK_TAB = 0x09;

  private final AtomicBoolean tabDown = new AtomicBoolean(false);
  private final CountDownLatch ready = new CountDownLatch(1);

  private final ExecutorService notifications =
      Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "tab-notification");
        thread.setDaemon(true);
        return thread;
      });

  // 必须保存引用，否则 Java 的回调对象可能被回收。
  private volatile LowLevelKeyboardProc callback;
  private volatile HHOOK hook;
  private volatile int hookThreadId;
  private volatile RuntimeException startFailure;
  private Thread hookThread;
  private boolean started;

  @Override
  public synchronized void start(Runnable onTriggered) {
    Objects.requireNonNull(onTriggered, "onTriggered");

    if (started) {
      throw new IllegalStateException("Tab 监听已经启动");
    }
    started = true;

    hookThread = new Thread(() -> runMessageLoop(onTriggered), "tab-hook");
    hookThread.setDaemon(true);
    hookThread.start();

    try {
      if (!ready.await(5, TimeUnit.SECONDS)) {
        close();
        throw new IllegalStateException("等待 Tab 监听启动超时");
      }
    } catch (InterruptedException e) {
      close();
      Thread.currentThread().interrupt();
      throw new IllegalStateException("等待 Tab 监听启动时被中断", e);
    }

    if (startFailure != null) {
      close();
      throw startFailure;
    }
  }

  private void runMessageLoop(Runnable onTriggered) {
    try {
      MSG message = new MSG();

      // 先创建线程的 Windows 消息队列，关闭时才能向它发送退出消息。
      User32.INSTANCE.PeekMessage(message, null, 0, 0, 0);

      callback = (nCode, wParam, keyInfo) -> {
        try {
          if (nCode >= 0 && keyInfo != null && keyInfo.vkCode == VK_TAB) {
            int eventType = wParam.intValue();

            if (eventType == WinUser.WM_KEYDOWN
                || eventType == WinUser.WM_SYSKEYDOWN) {
              // 长按 Tab 会产生重复 key-down；一次按下只通知一次。
              //alt+tab组合键排除
              if (tabDown.compareAndSet(false, true)) {
                boolean altDown = (keyInfo.flags & 0x20) != 0;
                if(!altDown && eventType ==WinUser.WM_KEYDOWN){
                  try {
                    notifications.execute(onTriggered);
                  } catch (RejectedExecutionException ignored) {
                    // 程序正在关闭，不再提交新通知。
                  }
                }

              }
            } else if (eventType == WinUser.WM_KEYUP
                || eventType == WinUser.WM_SYSKEYUP) {
              tabDown.set(false);
            }
          }
        } catch (RuntimeException ignored) {
          // 观察代码出错也不能影响原程序接收按键。
        }

        // 始终把原始按键交给下一个钩子及目标程序。
        long address = keyInfo == null
            ? 0
            : Pointer.nativeValue(keyInfo.getPointer());

        return User32.INSTANCE.CallNextHookEx(
            null, nCode, wParam, new LPARAM(address)
        );
      };

      HHOOK installed = User32.INSTANCE.SetWindowsHookEx(
          WinUser.WH_KEYBOARD_LL,
          callback,
          Kernel32.INSTANCE.GetModuleHandle(null),
          0
      );

      if (installed == null) {
        startFailure = new IllegalStateException(
            "安装键盘监听失败，Windows 错误码：" + Native.getLastError()
        );
        return;
      }

      hook = installed;
      hookThreadId = Kernel32.INSTANCE.GetCurrentThreadId();
      ready.countDown();

      while (true) {
        int result = User32.INSTANCE.GetMessage(message, null, 0, 0);

        if (result == 0) { // 收到退出消息
          break;
        }
        if (result == -1) {
          System.err.println("键盘消息循环出错：" + Native.getLastError());
          break;
        }

        User32.INSTANCE.TranslateMessage(message);
        User32.INSTANCE.DispatchMessage(message);
      }
    } catch (Throwable error) {
      RuntimeException failure = error instanceof RuntimeException runtime
          ? runtime
          : new IllegalStateException("Tab 监听失败", error);

      if (ready.getCount() > 0) {
        startFailure = failure;
      } else {
        System.err.println(failure.getMessage());
      }
    } finally {
      ready.countDown();

      if (hook != null) {
        User32.INSTANCE.UnhookWindowsHookEx(hook);
        hook = null;
      }

      hookThreadId = 0;
      callback = null;
      notifications.shutdownNow();
    }
  }

  @Override
  public synchronized void close() {
    if (hookThreadId != 0) {
      User32.INSTANCE.PostThreadMessage(
          hookThreadId,
          WinUser.WM_QUIT,
          new WPARAM(0),
          new LPARAM(0)
      );
    }

    if (hookThread != null && hookThread != Thread.currentThread()) {
      try {
        hookThread.join(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    notifications.shutdownNow();
  }
}