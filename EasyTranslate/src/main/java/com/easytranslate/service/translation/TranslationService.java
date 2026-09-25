package com.easytranslate.service.translation;
import com.easytranslate.model.Translation;
public interface TranslationService
{
  Translation translate(String sourceText);
}
