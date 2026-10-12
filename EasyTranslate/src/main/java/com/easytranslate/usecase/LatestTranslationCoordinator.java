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
  private long latestIntent;
  private boolean closed;

  public LatestTranslationCoordinator(TranslateSelectionUseCase useCase, Executor uiExecutor,
      Consumer<Translation> onTranslation, Consumer<Throwable> onFailure) {
    this.useCase = Objects.requireNonNull(useCase, "useCase");
    this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
    this.onTranslation = Objects.requireNonNull(onTranslation, "onTranslation");
    this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
  }

  /** Assign identity before scheduling selection, not after obtaining text or an HTTP response. */
  public synchronized void trigger() {
    if (closed) return;
    long intent = ++latestIntent;
    useCase.execute(() -> isCurrent(intent)).whenComplete((translation, error) ->
        uiExecutor.execute(() -> publishIfCurrent(intent, translation, error)));
  }

  private synchronized boolean isCurrent(long intent) {
    return !closed && intent == latestIntent;
  }

  private synchronized void publishIfCurrent(long intent,
      Optional<Translation> translation, Throwable error) {
    // Recheck INSIDE the UI task. Trigger/close and publication are serialized by this monitor.
    if (!isCurrent(intent)) return;
    if (error != null) onFailure.accept(error);
    else translation.ifPresent(onTranslation);
  }

  /** Invalidate pending selection/service/UI work. This does not cancel an in-flight HTTP request. */
  @Override
  public synchronized void close() {
    closed = true;
  }
}
