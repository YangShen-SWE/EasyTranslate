package com.easytranslate.service.translation;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class TranslationFailureTest {
  @Test void everyKindHasDistinctRecoveryMessage() {
    var messages = new HashSet<String>();
    for (var kind : TranslationFailure.Kind.values()) {
      assertFalse(kind.userMessage().isBlank());
      assertTrue(messages.add(kind.userMessage()), kind.name());
      assertEquals(kind.userMessage(), TranslationFailure.userMessage(new TranslationFailure(kind)));
    }
  }

  @Test void asyncWrappersPreserveTypedFailureRatherThanDeepestCause() {
    for (var kind : TranslationFailure.Kind.values()) {
      var error = new TranslationFailure(kind, new IOException("synthetic-private-diagnostic"));
      var wrapped = new CompletionException(new ExecutionException(new CompletionException(error)));
      assertEquals(kind.userMessage(), TranslationFailure.userMessage(wrapped));
      assertSame(error.getCause(), new CompletionException(error).getCause().getCause());
    }
  }

  @Test void unknownExceptionNeverShowsExternalMessageOrDiagnostic() {
    var error = new CompletionException(new IOException("synthetic-key-and-selected-text"));
    assertEquals(TranslationFailure.Kind.UNKNOWN.userMessage(), TranslationFailure.userMessage(error));
    assertFalse(TranslationFailure.userMessage(error).contains("synthetic"));
  }

  @Test void rawMessagePrefixIsNotTrustedAsLocalAdvice() {
    var error = new IllegalStateException("请先在设置中保存 synthetic-untrusted-content");
    assertEquals(TranslationFailure.Kind.UNKNOWN.userMessage(), TranslationFailure.userMessage(error));
  }

  @Test void nullFailureUsesSafeFallback() {
    assertEquals(TranslationFailure.Kind.UNKNOWN.userMessage(), TranslationFailure.userMessage(null));
  }

  @Test void excessivelyDeepWrappersHaveBoundedFallback() {
    Throwable error = new TranslationFailure(TranslationFailure.Kind.TIMEOUT);
    for (int i = 0; i < 20; i++) error = new CompletionException(error);
    assertEquals(TranslationFailure.Kind.UNKNOWN.userMessage(), TranslationFailure.userMessage(error));
  }

  @Test void nullKindIsRejected() {
    assertThrows(NullPointerException.class, () -> new TranslationFailure(null));
  }
}
