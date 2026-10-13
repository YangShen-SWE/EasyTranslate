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
  private long latestIntent;
  private boolean closed;

  public LatestTranslationCoordinator(TranslateSelectionUseCase useCase, Executor uiExecutor,
      Consumer<Translation> onTranslation, Consumer<Throwable> onFailure) {
    this(useCase, uiExecutor, onTranslation, onFailure, operation -> {});
  }

  public LatestTranslationCoordinator(TranslateSelectionUseCase useCase, Executor uiExecutor,
      Consumer<Translation> onTranslation, Consumer<Throwable> onFailure,
      Consumer<TranslationOperation> onOperation) {
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
    queueOperation(intent, TranslationOperation.READING);
    try {
      useCase.execute(() -> isCurrent(intent), operation -> queueOperation(intent, operation))
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
    return !closed && intent == latestIntent;
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
    });
  }

  /** Invalidate pending selection/service/UI work. This does not cancel an in-flight HTTP request. */
  @Override
  public synchronized void close() {
    closed = true;
  }
}
