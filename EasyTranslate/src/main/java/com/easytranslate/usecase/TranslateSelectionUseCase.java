package com.easytranslate.usecase;

import com.easytranslate.model.Translation;
import com.easytranslate.service.selection.SelectedTextService;
import com.easytranslate.service.selection.SelectionResult;
import com.easytranslate.service.translation.TranslationService;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class TranslateSelectionUseCase
{
  private final SelectedTextService selectionService;
  private final TranslationService translationService;
  private final Executor worker;

  public TranslateSelectionUseCase(SelectedTextService selectionService,TranslationService translationService){
    this(selectionService, translationService, ForkJoinPool.commonPool());
  }

  public TranslateSelectionUseCase(SelectedTextService selectionService,
      TranslationService translationService, Executor worker) {
    this.selectionService = Objects.requireNonNull(selectionService, "selectionService");
    this.translationService = Objects.requireNonNull(translationService, "translationService");
    this.worker = Objects.requireNonNull(worker, "worker");
  }

  public CompletableFuture<Optional<Translation>> execute() {
    return execute(() -> true);
  }

  /** The same trigger guard follows selection and service work; final UI gating belongs to the coordinator. */
  public CompletableFuture<Optional<Translation>> execute(BooleanSupplier isCurrent) {
    return execute(isCurrent, operation -> {});
  }

  /** Progress runs on the worker; callers must gate and dispatch it before touching the UI. */
  public CompletableFuture<Optional<Translation>> execute(BooleanSupplier isCurrent,
      Consumer<TranslationOperation> onProgress) {
    return execute(isCurrent, onProgress, null);
  }

  public CompletableFuture<Optional<Translation>> execute(BooleanSupplier isCurrent,
      Consumer<TranslationOperation> onProgress,
      com.easytranslate.config.OnlineTranslationPolicy.Permit permit) {
    Objects.requireNonNull(isCurrent, "isCurrent");
    Objects.requireNonNull(onProgress, "onProgress");
    return CompletableFuture.supplyAsync(() -> {
      if (!isCurrent.getAsBoolean()) return Optional.empty();
      SelectionResult result = selectionService.getSelectedText();
      if (!isCurrent.getAsBoolean()) return Optional.empty();
      switch (result.status()) {
        case None -> {
          onProgress.accept(TranslationOperation.NO_SELECTION);
          return Optional.empty();
        }
        case Unknown -> {
          onProgress.accept(TranslationOperation.UNREADABLE);
          return Optional.empty();
        }
        case TooLong -> {
          onProgress.accept(TranslationOperation.SELECTION_TOO_LONG);
          return Optional.empty();
        }
        case Found -> onProgress.accept(TranslationOperation.TRANSLATING);
      }
      if (!isCurrent.getAsBoolean()) return Optional.empty();
      return Optional.of(translationService.translate(result.text(), permit));
    }, worker);
  }
}
