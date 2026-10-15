package com.easytranslate.config;

import com.easytranslate.service.translation.TranslationFailure;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Versioned opt-in. No key or selected text is stored here. */
public final class OnlineTranslationPolicy {
  public enum Decision { UNDECIDED, DENIED, ENABLED }
  private static final String KEY = "onlineTranslationConsent";
  private final Preferences preferences;
  private Decision decision;
  private long revision;

  public OnlineTranslationPolicy() {
    this(Preferences.userNodeForPackage(OnlineTranslationPolicy.class).node("privacy"));
  }

  public OnlineTranslationPolicy(Preferences preferences) {
    this.preferences = Objects.requireNonNull(preferences, "preferences");
    try {
      decision = switch (preferences.get(KEY, "")) {
        case "v1:enabled" -> Decision.ENABLED;
        case "v1:denied" -> Decision.DENIED;
        default -> Decision.UNDECIDED;
      };
    } catch (RuntimeException error) { decision = Decision.DENIED; }
  }

  public synchronized Decision decision() { return decision; }

  /** Invalidates all previous permits, including when enabling again. Fail closed on save errors. */
  public synchronized void choose(boolean enable) {
    revision++;
    decision = Decision.DENIED;
    try {
      preferences.put(KEY, enable ? "v1:enabled" : "v1:denied");
      preferences.flush();
      decision = enable ? Decision.ENABLED : Decision.DENIED;
    } catch (RuntimeException | BackingStoreException error) {
      try { preferences.put(KEY, "v1:denied"); preferences.flush(); }
      catch (RuntimeException | BackingStoreException ignored) { /* current process remains off */ }
      throw new IllegalStateException("无法保存在线设置，当前已关闭在线翻译", error);
    }
  }

  public synchronized Permit permit() { return new Permit(revision); }

  public final class Permit {
    private final long issuedRevision;
    private Permit(long revision) { this.issuedRevision = revision; }
    public boolean isAllowed() {
      synchronized (OnlineTranslationPolicy.this) {
        return decision == Decision.ENABLED && revision == issuedRevision;
      }
    }
    public void requireAllowed() {
      if (!isAllowed()) throw new TranslationFailure(TranslationFailure.Kind.ONLINE_DISABLED);
    }
    /** Admission and revoke share a lock. Start must enqueue asynchronously, never wait for HTTP. */
    public <T> CompletableFuture<T> start(Supplier<CompletableFuture<T>> sendAsync) {
      synchronized (OnlineTranslationPolicy.this) {
        requireAllowed();
        return Objects.requireNonNull(sendAsync.get(), "request future");
      }
    }
  }
}
