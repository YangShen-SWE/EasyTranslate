package com.easytranslate.service.selection;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Offline wire-decoder and generated-script contract checks, NOT a Windows UIA execution. */
class WindowsSelectedTextServiceTest {
  @Test
  void acceptsComplete999And1000UnitCapturesWithoutTruncation() {
    for (int length : new int[]{999, 1000}) {
      String text = "a".repeat(length - 1) + "Z";
      assertEquals(SelectionResult.found(text), decode(text));
      assertEquals(text, decode(text).text());
    }
  }

  @Test
  void rejects1001AndLargerCapturesWithoutKeepingTheirPrefixes() {
    for (String text : new String[]{"a".repeat(1001), "a".repeat(2000),
        " ".repeat(1000) + "Z", " ".repeat(1001)}) {
      assertEquals(SelectionResult.tooLong(), decode(text));
      assertNull(decode(text).text());
    }
  }

  @Test
  void explicitTooLongSentinelContainsNoText() {
    assertEquals(SelectionResult.tooLong(), WindowsSelectedTextService.parseResponse("TOO_LONG\r\n"));
  }

  @Test
  void emptyAndWhitespaceCapturesAreNone() {
    for (String text : new String[]{"", " ", "\t\n", "\u2003", " ".repeat(1000)}) {
      assertEquals(SelectionResult.none(), decode(text));
    }
  }

  @Test
  void distinguishesUnknownFromConfirmedNoSelection() {
    assertEquals(SelectionResult.none(), WindowsSelectedTextService.parseResponse("NONE"));
    for (String response : new String[]{null, "", "UNKNOWN", "other", "TOO_LONG:prefix", "NONE\nUNKNOWN"}) {
      assertEquals(SelectionResult.unknown(), WindowsSelectedTextService.parseResponse(response));
    }
  }

  @Test
  void preservesUnicodeInternalSpacesAndMultilineText() {
    String text = "  \u2003丹麦  学习\nnext step 😀\u2003 ";
    assertEquals("丹麦  学习\nnext step 😀", decode(text).text());
  }

  @Test
  void supplementaryCharactersCountAsTwoUtf16Units() {
    assertEquals(SelectionResult.Status.Found, decode("😀".repeat(500)).status());
    assertEquals(1000, decode("😀".repeat(500)).text().length());
    assertEquals(SelectionResult.tooLong(), decode("😀".repeat(500) + "x"));
  }

  @Test
  void corruptBase64OrUtf8IsUnknownInsteadOfReplacementText() {
    for (String response : new String[]{"FOUND:!bad", "FOUND:YQ==\nYg==", "FOUND:/w=="}) {
      assertEquals(SelectionResult.unknown(), WindowsSelectedTextService.parseResponse(response));
    }
  }

  @Test
  void scriptProbesOneExtraUnitAndRejectsBeforeBase64Encoding() {
    String script = WindowsSelectedTextService.SCRIPT;
    assertTrue(script.contains(".GetText(" + (SelectionResult.MAX_TEXT_LENGTH + 1) + ")"));
    assertFalse(script.contains(".GetText(1000)"));
    assertFalse(script.contains(".GetText(-1)"));
    assertTrue(script.contains("$text.Length -gt " + SelectionResult.MAX_TEXT_LENGTH));
    assertTrue(script.contains("$foundText.Length -gt " + SelectionResult.MAX_TEXT_LENGTH));
    assertTrue(script.indexOf("Write('TOO_LONG')") < script.indexOf("::UTF8.GetBytes($foundText)"));
    assertFalse(script.contains("%d"), "all Java script placeholders must be rendered");
  }

  @Test
  void scriptBoundsAncestorsAndRechecksForegroundBeforePublishing() {
    String script = WindowsSelectedTextService.SCRIPT;
    assertTrue(script.contains("if (-not (Test-Foreground $node)) { break }"));
    String recheck = "if ([ForegroundNative]::GetForegroundWindow().ToInt64() -ne $foreground)";
    assertTrue(script.indexOf(recheck) > script.indexOf(".GetText("));
    assertTrue(script.indexOf(recheck) < script.indexOf("Write('TOO_LONG')"));
  }

  @Test
  void scriptRejectsDisjointRangesRatherThanSendingTheFirst() {
    String script = WindowsSelectedTextService.SCRIPT;
    assertTrue(script.contains("$ranges.Length -gt 1"));
    assertTrue(script.contains("$ranges.Length -eq 1"));
    assertTrue(script.contains("$ranges.Length -eq 0"));
    assertTrue(script.indexOf("$ranges.Length -gt 1") < script.indexOf("$ranges[0].GetText("));
  }

  private static SelectionResult decode(String text) {
    String payload = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    return WindowsSelectedTextService.parseResponse("FOUND:" + payload);
  }
}
