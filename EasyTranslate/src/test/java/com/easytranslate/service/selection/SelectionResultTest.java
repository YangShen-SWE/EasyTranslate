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
  void nonFoundStatusesRejectTextIncludingEmptyText() {
    for (SelectionResult.Status status : new SelectionResult.Status[]{
        SelectionResult.Status.None, SelectionResult.Status.Unknown}) {
      assertThrows(IllegalArgumentException.class, () -> new SelectionResult(status, "word"));
      assertThrows(IllegalArgumentException.class, () -> new SelectionResult(status, ""));
    }
  }
}
