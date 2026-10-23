package com.easytranslate.usecase;

import com.easytranslate.model.Translation;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Explicit-save contract only: no default repository, disk writes, logging or automatic collection.
 * Calls are serialized. A future UI adapter must dispatch slow storage off the FX thread.
 */
public final class VocabularySaveBoundary implements AutoCloseable {
  public enum Result { SAVED, DUPLICATE, INELIGIBLE, STALE, STORAGE_FAILED }
  public enum InsertResult { INSERTED, ALREADY_EXISTS }

  /** Immutable snapshot; intent is session-local, not a durable database identifier. */
  public record Candidate(long intent, String key, String source, String translation) {}

  @FunctionalInterface
  public interface Repository {
    /** Atomically insert by normalized key; never overwrite an existing entry.
     * Implementations own durable uniqueness. Throw on failure; a retry must be idempotent.
     */
    InsertResult insertIfAbsent(Candidate candidate) throws Exception;
  }

  private final Repository repository;
  private Candidate current;
  private String lastSavedKey;
  private boolean closed;

  public VocabularySaveBoundary(Repository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  // Package-private: only the latest coordinator publication may grant eligibility.
  synchronized void displayed(long intent, Translation value) {
    current = null;
    if (closed || value == null || value.sourceText() == null || value.translatedText() == null) return;
    String source = value.sourceText().strip().replaceAll("\\s+", " ");
    String translated = value.translatedText().strip();
    // Conservative English word/short phrase boundary, not sentence/language detection.
    if (source.length() > 80 || !source.matches("[A-Za-z]+(?:['’-][A-Za-z]+)*(?: [A-Za-z]+(?:['’-][A-Za-z]+)*){0,4}")
        || translated.isBlank() || translated.length() > 4000
        || translated.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) return;
    current = new Candidate(intent, source.toLowerCase(Locale.ROOT).replace('’', '\''), source, translated);
  }

  synchronized void invalidate() { current = null; }

  public synchronized Optional<Candidate> candidate() { return Optional.ofNullable(current); }

  /** Caller must retain the intent from the actual displayed snapshot, never a mutable source field. */
  public synchronized Result save(long displayedIntent) {
    if (closed || current == null) return Result.INELIGIBLE;
    if (current.intent() != displayedIntent) return Result.STALE;
    if (current.key().equals(lastSavedKey)) return Result.DUPLICATE;
    try {
      InsertResult result = Objects.requireNonNull(repository.insertIfAbsent(current));
      lastSavedKey = current.key();
      return result == InsertResult.INSERTED ? Result.SAVED : Result.DUPLICATE;
    } catch (Exception failure) {
      // Do not expose database messages (which may contain credentials or original text).
      // Keep eligibility for an explicit retry; translation state is unaffected.
      return Result.STORAGE_FAILED;
    }
  }

  @Override public synchronized void close() { closed = true; current = null; lastSavedKey = null; }
}
