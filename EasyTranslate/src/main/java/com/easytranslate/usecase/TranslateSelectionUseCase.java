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
    Objects.requireNonNull(isCurrent, "isCurrent");
    return CompletableFuture.supplyAsync(() -> {
      if (!isCurrent.getAsBoolean()) return Optional.empty();
      SelectionResult result = selectionService.getSelectedText();

      if (!isCurrent.getAsBoolean() || result.status() != SelectionResult.Status.Found) {
        return Optional.empty();
      }

      return Optional.of(translationService.translate(result.text()));
    }, worker);
  }
}
