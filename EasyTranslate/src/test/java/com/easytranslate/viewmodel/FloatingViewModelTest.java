package com.easytranslate.viewmodel;

import com.easytranslate.model.Translation;
import com.easytranslate.usecase.TranslationOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** JavaFX properties without toolkit startup, private preferences or network. */
class FloatingViewModelTest {
  @Test
  void startsIdleWithoutPretendingThereIsAPreviousResult() {
    var model = new FloatingViewModel();
    assertEquals(TranslationOperation.IDLE, model.operationProperty().get());
    assertEquals("等待划词", model.statusTextProperty().get());
    assertEquals("", model.sourceTextProperty().get());
    assertEquals("", model.translatedTextProperty().get());
  }

  @Test
  void allNonSuccessStatesWithoutTextHaveDistinctMessagesAndNoPreviousMarker() {
    var model = new FloatingViewModel();
    var states = new TranslationOperation[]{TranslationOperation.READING, TranslationOperation.TRANSLATING,
        TranslationOperation.NO_SELECTION, TranslationOperation.UNREADABLE, TranslationOperation.FAILED};
    var messages = new String[]{"正在读取选区", "正在翻译", "未选中文字，请选中后再按 Tab",
        "无法读取选区，请尝试其他应用", "翻译失败，请检查网络或 API Key"};
    for (int i = 0; i < states.length; i++) {
      model.showOperation(states[i]);
      assertEquals(states[i], model.operationProperty().get());
      assertEquals(messages[i], model.statusTextProperty().get());
      assertEquals("", model.translatedTextProperty().get());
    }
  }

  @Test
  void everyNonSuccessStateRetainsBothTextsAndMarksPreviousResult() {
    var model = new FloatingViewModel();
    model.showTranslation(new Translation("old source", "旧译文"));
    for (var state : TranslationOperation.values()) {
      if (state == TranslationOperation.SUCCEEDED) continue;
      model.showOperation(state);
      assertEquals(state, model.operationProperty().get());
      assertTrue(model.statusTextProperty().get().endsWith(" · 上次结果"), state.toString());
      assertEquals("old source", model.sourceTextProperty().get());
      assertEquals("旧译文", model.translatedTextProperty().get());
    }
  }

  @Test
  void newSuccessReplacesOldTextsAndRemovesPreviousMarker() {
    var model = new FloatingViewModel();
    model.showTranslation(new Translation("old", "旧译文"));
    model.showOperation(TranslationOperation.NO_SELECTION);
    model.showTranslation(new Translation("new", "新译文"));
    assertEquals(TranslationOperation.SUCCEEDED, model.operationProperty().get());
    assertEquals("翻译成功", model.statusTextProperty().get());
    assertEquals("new", model.sourceTextProperty().get());
    assertEquals("新译文", model.translatedTextProperty().get());
  }

  @Test
  void repeatedIdenticalSuccessStillClearsFailureAndPreviousMarker() {
    var model = new FloatingViewModel();
    var result = new Translation("word", "词");
    model.showTranslation(result);
    model.showFailure("请先在设置中保存 API Key");
    model.showTranslation(result);
    assertEquals("翻译成功", model.statusTextProperty().get());
    assertEquals(TranslationOperation.SUCCEEDED, model.operationProperty().get());
  }

  @Test
  void customFailureIsPreservedButResetOnNextOperation() {
    var model = new FloatingViewModel();
    model.showTranslation(new Translation("old", "旧译文"));
    model.showFailure("请先在设置中保存 API Key");
    assertEquals("请先在设置中保存 API Key · 上次结果", model.statusTextProperty().get());
    model.showOperation(TranslationOperation.READING);
    assertEquals("正在读取选区 · 上次结果", model.statusTextProperty().get());
    model.showOperation(TranslationOperation.FAILED);
    assertEquals("翻译失败，请检查网络或 API Key · 上次结果", model.statusTextProperty().get());
  }

  @Test
  void tooLongExplainsLimitAndNoUploadWhileRetainingOldTranslation() {
    var model = new FloatingViewModel();
    model.showOperation(TranslationOperation.SELECTION_TOO_LONG);
    String message = model.statusTextProperty().get();
    assertTrue(message.contains("未发送翻译"));
    assertTrue(message.contains("1000 个 UTF-16 单位"));
    assertFalse(message.contains("上次结果"));
    model.showTranslation(new Translation("old", "旧译文"));
    model.showOperation(TranslationOperation.SELECTION_TOO_LONG);
    assertEquals(message + " · 上次结果", model.statusTextProperty().get());
    assertEquals("old", model.sourceTextProperty().get());
    assertEquals("旧译文", model.translatedTextProperty().get());
    model.showTranslation(new Translation("new", "新译文"));
    assertEquals("翻译成功", model.statusTextProperty().get());
  }

  @Test
  void invalidInputsDoNotMutateExistingPresentation() {
    var model = new FloatingViewModel();
    model.showTranslation(new Translation("word", "词"));
    assertThrows(NullPointerException.class, () -> model.showOperation(null));
    assertThrows(NullPointerException.class, () -> model.showFailure(null));
    assertThrows(NullPointerException.class, () -> model.showTranslation(null));
    assertEquals("翻译成功", model.statusTextProperty().get());
    assertEquals("词", model.translatedTextProperty().get());
  }
}
