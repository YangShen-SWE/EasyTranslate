package com.easytranslate.service.translation;
import com.easytranslate.model.Translation;
public class MockTranslationService implements TranslationService
{
  @Override
  public Translation translate(String sourceText){
    return new Translation(sourceText,"模拟译文： " + sourceText);
  }
}
