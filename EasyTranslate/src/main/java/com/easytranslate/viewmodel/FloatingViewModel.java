package com.easytranslate.viewmodel;

import com.easytranslate.model.Translation;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

public class FloatingViewModel
{
  private final ReadOnlyStringWrapper sourceText = new ReadOnlyStringWrapper("");
  private final ReadOnlyStringWrapper translatedText = new ReadOnlyStringWrapper("");
  public void showTranslation(Translation translation){
    sourceText.set(translation.sourceText());
    translatedText.set(translation.translatedText());
  }
  public ReadOnlyStringProperty sourceTextProperty(){
    return sourceText.getReadOnlyProperty();
  }
  public ReadOnlyStringProperty translatedTextProperty(){
    return translatedText.getReadOnlyProperty();
  }
}
