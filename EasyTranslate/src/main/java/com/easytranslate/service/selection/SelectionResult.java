package com.easytranslate.service.selection;

import java.util.Objects;

public record SelectionResult(Status status, String text) {
  /** Inclusive raw UTF-16 code-unit limit, before whitespace normalization. */
  public static final int MAX_TEXT_LENGTH = 1000;

  public enum Status {
    Found,    // 读取到了完整、长度允许的文字
    None,     // 没有选中文字
    Unknown,  // 无法判断目标软件的选区
    TooLong   // 超过长度上限；不保存或翻译截断后的前缀
  }

  public SelectionResult {
    Objects.requireNonNull(status, "status");
    if (status == Status.Found) {
      if (text == null || text.isBlank()) {
        throw new IllegalArgumentException("Found 必须包含文字");
      }
      if (text.length() > MAX_TEXT_LENGTH) {
        throw new IllegalArgumentException("Found 不能包含超长文字");
      }
    } else if (text != null) {
      throw new IllegalArgumentException("非Found 状态不能包含文字");
    }
  }

  public static SelectionResult found(String text) {
    Objects.requireNonNull(text, "text");
    // Check the original capture, including whitespace, not a shorter stripped prefix.
    if (text.length() > MAX_TEXT_LENGTH) return tooLong();
    if (text.isBlank()) throw new IllegalArgumentException("Found 必须包含文字");
    return new SelectionResult(Status.Found, text.strip());
  }

  public static SelectionResult none() {
    return new SelectionResult(Status.None, null);
  }

  public static SelectionResult unknown() {
    return new SelectionResult(Status.Unknown, null);
  }

  public static SelectionResult tooLong() {
    return new SelectionResult(Status.TooLong, null);
  }
}
