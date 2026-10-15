package com.easytranslate.service.translation;
import com.easytranslate.model.Translation;
public interface TranslationService
{
  Translation translate(String sourceText);

  /** Online implementations additionally gate the actual asynchronous send admission. */
  default Translation translate(String sourceText, com.easytranslate.config.OnlineTranslationPolicy.Permit permit) {
    if (permit != null) permit.requireAllowed();
    return translate(sourceText);
  }
}
