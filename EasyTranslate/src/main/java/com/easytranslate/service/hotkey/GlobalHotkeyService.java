package com.easytranslate.service.hotkey;

public interface GlobalHotkeyService extends AutoCloseable
{
  void start(Runnable onTriggered);
  //开始监听，收到指定按键时执行传入操作

  @Override
  void close();
  //释放快捷键
}
