package com.easytranslate.service.selection;

public class SelectionDebug {

  public static void main(String[] args) throws InterruptedException {
    System.out.println("5 秒内切到目标软件，选中文字或取消选择...");
    Thread.sleep(5000);

    SelectedTextService service = new WindowsSelectedTextService();
    SelectionResult result = service.getSelectedText();

    switch (result.status()) {
      case Found -> System.out.println("读到：" + result.text());
      case None -> System.out.println("确认：当前没有选中文字");
      case Unknown -> System.out.println("无法判断当前软件的选区");
    }
  }
}