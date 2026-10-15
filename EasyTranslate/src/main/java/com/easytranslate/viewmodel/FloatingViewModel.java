package com.easytranslate.viewmodel;

import com.easytranslate.model.Translation;
import com.easytranslate.usecase.TranslationOperation;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.util.Objects;

public class FloatingViewModel {
  private final ReadOnlyStringWrapper sourceText = new ReadOnlyStringWrapper("");
  private final ReadOnlyStringWrapper translatedText = new ReadOnlyStringWrapper("");
  private final ReadOnlyObjectWrapper<TranslationOperation> operation =
      new ReadOnlyObjectWrapper<>(TranslationOperation.IDLE);
  private final ReadOnlyStringWrapper statusText = new ReadOnlyStringWrapper("等待划词");
  private String failureMessage = "翻译失败，请检查网络或 API Key";

  public void showTranslation(Translation translation) {
    Objects.requireNonNull(translation, "translation");
    sourceText.set(translation.sourceText());
    translatedText.set(translation.translatedText());
    showOperation(TranslationOperation.SUCCEEDED);
  }

  public void showOperation(TranslationOperation state) {
    Objects.requireNonNull(state, "state");
    failureMessage = "翻译失败，请检查网络或 API Key";
    operation.set(state);
    refreshStatus();
  }

  public void showFailure(String message) {
    failureMessage = Objects.requireNonNull(message, "message");
    operation.set(TranslationOperation.FAILED);
    refreshStatus();
  }

  private void refreshStatus() {
    String message = switch (operation.get()) {
      case ONLINE_DISABLED -> "在线翻译已关闭，请在设置中阅读说明并同意后开启";
      case IDLE -> "等待划词";
      case READING -> "正在读取选区";
      case TRANSLATING -> "正在翻译";
      case SUCCEEDED -> "翻译成功";
      case NO_SELECTION -> "未选中文字，请选中后再按 Tab";
      case UNREADABLE -> "无法读取选区，请尝试其他应用";
      case FAILED -> failureMessage;
    };
    if (!translatedText.get().isEmpty() && operation.get() != TranslationOperation.SUCCEEDED) {
      message += " · 上次结果";
    }
    statusText.set(message);
  }

  public ReadOnlyStringProperty sourceTextProperty() {
    return sourceText.getReadOnlyProperty();
  }
  public ReadOnlyStringProperty translatedTextProperty() {
    return translatedText.getReadOnlyProperty();
  }
  public ReadOnlyObjectProperty<TranslationOperation> operationProperty() {
    return operation.getReadOnlyProperty();
  }
  public ReadOnlyStringProperty statusTextProperty() {
    return statusText.getReadOnlyProperty();
  }
}
