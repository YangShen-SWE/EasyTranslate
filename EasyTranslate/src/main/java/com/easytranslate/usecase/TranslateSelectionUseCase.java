package com.easytranslate.usecase;

import com.easytranslate.model.Translation;
import com.easytranslate.service.selection.SelectedTextService;
import com.easytranslate.service.selection.SelectionResult;
import com.easytranslate.service.translation.TranslationService;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class TranslateSelectionUseCase
{
  private final SelectedTextService selectionService;
  private final TranslationService translationService;

  public TranslateSelectionUseCase(SelectedTextService selectionService,TranslationService translationService){
    this.selectionService = selectionService;
    this.translationService = translationService;
  }

  public CompletableFuture<Optional<Translation>> execute() {
    return CompletableFuture.supplyAsync(() -> {
      SelectionResult result = selectionService.getSelectedText();

      if (result.status() != SelectionResult.Status.Found) {
        return Optional.empty();
      }

      return Optional.of(translationService.translate(result.text()));
    });
  }
}
