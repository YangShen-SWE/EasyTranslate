package com.easytranslate.service.selection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure selection-result contract checks; no Windows UI Automation is started. */
class SelectionResultTest {
  @Test
  void foundStripsSurroundingUnicodeWhitespace() {
    SelectionResult result = SelectionResult.found("\u2003 steady progress \u2003");

    assertEquals(SelectionResult.Status.Found, result.status());
    assertEquals("steady progress", result.text());
  }

  @Test
  void foundPreservesInternalWhitespaceAndLineBreaks() {
    assertEquals("steady  progress\nnext step",
        SelectionResult.found("  steady  progress\nnext step  ").text());
  }

  @Test
  void foundRejectsEmptyAndBlankText() {
    for (String text : new String[]{"", " ", "\t\n", "\u2003"}) {
      assertThrows(IllegalArgumentException.class, () -> SelectionResult.found(text));
    }
  }

  @Test
  void foundStatusRejectsNullText() {
    assertThrows(IllegalArgumentException.class,
        () -> new SelectionResult(SelectionResult.Status.Found, null));
  }

  @Test
  void statusMustNotBeNull() {
    assertThrows(NullPointerException.class, () -> new SelectionResult(null, null));
  }

  @Test
  void noneHasNoText() {
    SelectionResult result = SelectionResult.none();
    assertEquals(SelectionResult.Status.None, result.status());
    assertNull(result.text());
  }

  @Test
  void unknownHasNoText() {
    SelectionResult result = SelectionResult.unknown();
    assertEquals(SelectionResult.Status.Unknown, result.status());
    assertNull(result.text());
  }

  @Test
  void complete999And1000UnitSelectionsRemainFound() {
    for (int length : new int[]{999, 1000}) {
      String text = "a".repeat(length);
      assertEquals(text, SelectionResult.found(text).text());
    }
  }

  @Test
  void overLimitFactoryResultDoesNotExposeAnyCapturedPrefix() {
    for (String text : new String[]{"a".repeat(1001), " ".repeat(1000) + "Z", " ".repeat(1001)}) {
      assertEquals(SelectionResult.tooLong(), SelectionResult.found(text));
      assertNull(SelectionResult.found(text).text());
    }
  }

  @Test
  void directFoundConstructorCannotBypassLengthLimit() {
    assertThrows(IllegalArgumentException.class,
        () -> new SelectionResult(SelectionResult.Status.Found, "a".repeat(1001)));
  }

  @Test
  void lengthIsMeasuredBeforeStrippingAndCountsSurrogatePairs() {
    assertEquals(SelectionResult.Status.Found, SelectionResult.found("😀".repeat(500)).status());
    assertEquals(SelectionResult.tooLong(), SelectionResult.found("😀".repeat(500) + "x"));
    assertEquals("a".repeat(998), SelectionResult.found(" " + "a".repeat(998) + " ").text());
    assertEquals(SelectionResult.tooLong(), SelectionResult.found(" " + "a".repeat(999) + " "));
  }

  @Test
  void tooLongHasNoText() {
    assertEquals(SelectionResult.Status.TooLong, SelectionResult.tooLong().status());
    assertNull(SelectionResult.tooLong().text());
  }

  @Test
  void nonFoundStatusesRejectTextIncludingEmptyText() {
    for (SelectionResult.Status status : new SelectionResult.Status[]{
        SelectionResult.Status.None, SelectionResult.Status.Unknown, SelectionResult.Status.TooLong}) {
      assertThrows(IllegalArgumentException.class, () -> new SelectionResult(status, "word"));
      assertThrows(IllegalArgumentException.class, () -> new SelectionResult(status, ""));
    }
  }
}
