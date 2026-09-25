package com.easytranslate.service.selection;

import java.util.Optional;

public interface SelectedTextService
{
//  Optional<String> getSelectedText();
  //用optional是因为其他程序不一定有选区或者不允许读取 optional.empty()可以表示没有取得文字就不翻译
  SelectionResult getSelectedText();
}
