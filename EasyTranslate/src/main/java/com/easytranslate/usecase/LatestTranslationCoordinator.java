package com.easytranslate.usecase;

import com.easytranslate.model.Translation;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Latest trigger wins, including callbacks already queued for the UI thread. */
public final class LatestTranslationCoordinator implements AutoCloseable {
  private final TranslateSelectionUseCase useCase;
  private final Executor uiExecutor;
  private final Consumer<Translation> onTranslation;
  private final Consumer<Throwable> onFailure;
  private final Consumer<TranslationOperation> onOperation;
  private final com.easytranslate.config.OnlineTranslationPolicy onlinePolicy;
  private com.easytranslate.config.OnlineTranslationPolicy.Permit activePermit;
  private final VocabularySaveBoundary vocabulary;
  private long latestIntent;
  private boolean closed;

  public LatestTranslationCoordinator(TranslateSelectionUseCase useCase, Executor uiExecutor,
      Consumer<Translation> onTranslation, Consumer<Throwable> onFailure) {
    this(useCase, uiExecutor, onTranslation, onFailure, operation -> {});
  }

  public LatestTranslationCoordinator(TranslateSelectionUseCase useCase, Executor uiExecutor,
      Consumer<Translation> onTranslation, Consumer<Throwable> onFailure,
      Consumer<TranslationOperation> onOperation) {
    this(useCase, uiExecutor, onTranslation, onFailure, onOperation, null);
  }

  public LatestTranslationCoordinator(TranslateSelectionUseCase useCase, Executor uiExecutor,
      Consumer<Translation> onTranslation, Consumer<Throwable> onFailure,
      Consumer<TranslationOperation> onOperation,
      com.easytranslate.config.OnlineTranslationPolicy onlinePolicy) {
    this(useCase, uiExecutor, onTranslation, onFailure, onOperation, onlinePolicy, null);
  }

  /** Opt-in boundary; legacy production wiring does not collect or save vocabulary. */
  public LatestTranslationCoordinator(TranslateSelectionUseCase useCase, Executor uiExecutor,
      Consumer<Translation> onTranslation, Consumer<Throwable> onFailure,
      Consumer<TranslationOperation> onOperation,
      com.easytranslate.config.OnlineTranslationPolicy onlinePolicy,
      VocabularySaveBoundary vocabulary) {
    this.vocabulary = vocabulary;
    this.onlinePolicy = onlinePolicy;
    this.onOperation = Objects.requireNonNull(onOperation, "onOperation");
    this.useCase = Objects.requireNonNull(useCase, "useCase");
    this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
    this.onTranslation = Objects.requireNonNull(onTranslation, "onTranslation");
    this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
  }

  /** Assign identity before scheduling selection, not after obtaining text or an HTTP response. */
  public synchronized void trigger() {
    if (closed) return;
    long intent = ++latestIntent;
    if (vocabulary != null) vocabulary.invalidate();
    var permit = onlinePolicy == null ? null : onlinePolicy.permit();
    activePermit = permit;
    if (permit != null && !permit.isAllowed()) {
      activePermit = null;
      queueOperation(intent, TranslationOperation.ONLINE_DISABLED);
      return;
    }
    queueOperation(intent, TranslationOperation.READING);
    try {
      useCase.execute(() -> isCurrent(intent), operation -> queueOperation(intent, operation), permit)
          .whenComplete((translation, error) ->
              uiExecutor.execute(() -> publishIfCurrent(intent, translation, error)));
    } catch (RuntimeException error) {
      // A rejected worker must not leave the latest operation stuck in READING.
      uiExecutor.execute(() -> publishIfCurrent(intent, Optional.empty(), error));
    }
  }

  // The UI executor must be serial FIFO (Platform.runLater in production).
  private void queueOperation(long intent, TranslationOperation operation) {
    uiExecutor.execute(() -> publishOperationIfCurrent(intent, operation));
  }

  private synchronized void publishOperationIfCurrent(long intent, TranslationOperation operation) {
    if (isCurrent(intent)) onOperation.accept(operation);
  }

  private synchronized boolean isCurrent(long intent) {
    return !closed && intent == latestIntent && (activePermit == null || activePermit.isAllowed());
  }

  private synchronized void publishIfCurrent(long intent,
      Optional<Translation> translation, Throwable error) {
    // Recheck INSIDE the UI task. Trigger/close and publication are serialized by this monitor.
    if (!isCurrent(intent)) return;
    if (error != null) {
      onOperation.accept(TranslationOperation.FAILED);
      if (isCurrent(intent)) onFailure.accept(error);
    } else translation.ifPresent(value -> {
      onTranslation.accept(value);
      // A consumer may reenter trigger/close; never publish this intent's status afterwards.
      if (isCurrent(intent)) onOperation.accept(TranslationOperation.SUCCEEDED);
      if (isCurrent(intent) && vocabulary != null) vocabulary.displayed(intent, value);
    });
  }

  /** Call after an explicit settings decision. Never resumes old work or sends automatically. */
  public synchronized void refreshOnlineAccess() {
    if (closed || onlinePolicy == null) return;
    long intent = ++latestIntent;
    if (vocabulary != null) vocabulary.invalidate();
    activePermit = null;
    queueOperation(intent, onlinePolicy.permit().isAllowed()
        ? TranslationOperation.IDLE : TranslationOperation.ONLINE_DISABLED);
  }

  /** Invalidate pending selection/service/UI work. This does not cancel an in-flight HTTP request. */
  @Override
  public synchronized void close() {
    closed = true;
    if (vocabulary != null) vocabulary.close();
  }
}
