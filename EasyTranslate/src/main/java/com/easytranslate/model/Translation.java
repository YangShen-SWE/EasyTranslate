package com.easytranslate.model;

public record Translation(String sourceText,String translatedText)
{
  //只保存一条翻译结果，不负责界面，也不负责翻译


}
