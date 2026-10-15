package com.easytranslate.service.translation;

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Safe, local messages: never display remote bodies, keys or low-level exception text. */
public final class TranslationFailure extends IllegalStateException {
  public enum Kind {
    ONLINE_DISABLED("在线翻译已关闭，请在设置中阅读说明并同意后开启"),
    MISSING_KEY("请先在设置中保存 DeepSeek API Key"),
    KEY_UNREADABLE("无法读取密钥，请在设置中重新保存 API Key"),
    AUTHENTICATION("密钥无效或无权限，请检查并重新保存 API Key"),
    RATE_LIMIT("请求过于频繁，请稍后手动重试"),
    NETWORK("网络连接失败，请检查网络后重试"),
    TIMEOUT("翻译请求超时，请稍后手动重试"),
    EMPTY_RESPONSE("服务未返回译文，请稍后手动重试"),
    INVALID_RESPONSE("服务响应格式异常，请稍后重试；仍失败请检查服务状态"),
    SERVICE_UNAVAILABLE("翻译服务暂不可用，请稍后手动重试"),
    REQUEST_REJECTED("服务拒绝请求，请检查账户额度与服务设置"),
    INTERRUPTED("翻译请求已中断，请重新触发"),
    UNKNOWN("翻译失败，请稍后重试；仍失败请检查服务设置");

    private final String message;
    Kind(String message) { this.message = message; }
    public String userMessage() { return message; }
  }

  private final Kind kind;

  public TranslationFailure(Kind kind) {
    this(kind, null);
  }

  public TranslationFailure(Kind kind, Throwable cause) {
    super(Objects.requireNonNull(kind, "kind").userMessage(), cause);
    this.kind = kind;
  }

  public Kind kind() { return kind; }

  /** Unwrap asynchronous containers, not the typed failure's diagnostic cause. */
  public static String userMessage(Throwable error) {
    Throwable current = error;
    // Bound traversal even if a caller supplies cyclic or very deep wrapper chains.
    for (int i = 0; i < 16 && current != null; i++) {
      if (current instanceof TranslationFailure failure) return failure.kind.userMessage();
      if (!(current instanceof CompletionException || current instanceof ExecutionException)) break;
      current = current.getCause();
    }
    return Kind.UNKNOWN.userMessage();
  }
}
