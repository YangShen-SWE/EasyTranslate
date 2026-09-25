package com.easytranslate;

import com.easytranslate.service.hotkey.GlobalHotkeyService;
import com.easytranslate.usecase.TranslateSelectionUseCase;
import com.easytranslate.service.selection.WindowsSelectedTextService;
import com.easytranslate.service.translation.MockTranslationService;
import com.easytranslate.view.FloatingViewController;
import com.easytranslate.viewmodel.FloatingViewModel;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.easytranslate.config.WindowPositionStore;
import javafx.util.Duration;
import com.easytranslate.service.hotkey.GlobalHotkeyService;
import com.easytranslate.service.hotkey.WindowsTabObserverService;
import com.easytranslate.service.selection.SelectedTextService;
import com.easytranslate.service.selection.SelectionResult;

import java.io.IOException;

public class EasyTranslateApplication extends Application
{
  private GlobalHotkeyService tabObserver;
  @Override
  public void start(Stage stage) throws IOException {

    FloatingViewModel viewModel = new FloatingViewModel();
    FXMLLoader loader = new FXMLLoader(EasyTranslateApplication.class.getResource("/com/easytranslate/view/floating-view.fxml"));
    Parent root = loader.load();
    FloatingViewController controller = loader.getController();
    controller.setViewModel(viewModel);
    stage.setTitle("EasyTranslate");
    stage.setAlwaysOnTop(true);
    stage.initStyle(StageStyle.UNDECORATED);
    //伪无边框窗口（同时不包含拖动功能得自己做）
    Scene scene = new Scene(root);
    scene.getStylesheets().add(EasyTranslateApplication.class.getResource(
        "/com/easytranslate/view/floating-window.css").toExternalForm());
    stage.setScene(scene);
    WindowPositionStore positionStore = new WindowPositionStore();
    positionStore.restore(stage);
    //读取旧坐标，设置窗口位置
    stage.setOnHiding(event -> positionStore.save(stage));
    //登记窗口即将关闭时执行保存
    stage.show();
//    TranslateSelectionUseCase translateSelection = new TranslateSelectionUseCase(new WindowsSelectedTextService(),new MockTranslationService());
//    PauseTransition timer = new PauseTransition(Duration.seconds(8));
//    timer.setOnFinished(event ->
//        translateSelection.execute().thenAccept(maybeTranslation ->
//            maybeTranslation.ifPresent(translation -> Platform.runLater(() ->
//                viewModel.showTranslation(translation))
//            )
//        )
//    );
//    timer.play();
    tabObserver = new WindowsTabObserverService();
    TranslateSelectionUseCase translateSelection = new TranslateSelectionUseCase(
        new WindowsSelectedTextService(),
        new MockTranslationService()
    );

    tabObserver = new WindowsTabObserverService();
    tabObserver.start(() ->
        translateSelection.execute().thenAccept(maybeTranslation ->
            maybeTranslation.ifPresent(translation ->
                Platform.runLater(() -> viewModel.showTranslation(translation))
            )
        )
    );
//    SelectedTextService selectionService = new WindowsSelectedTextService();
//    tabObserver.start(() -> {
//      SelectionResult result = selectionService.getSelectedText();
//      switch (result.status()){
//        case Found -> System.out.println("Tab读取到： " + result.text());
//        case None -> System.out.println("tab确认后没有选区");
//        case Unknown -> System.out.println("Tab 后无法判断选区");
//      }
//    });
  }
  @Override
  public void stop(){
    if(tabObserver != null){
      tabObserver.close();
    }
  }
  public static void main(String[] args){
    launch(args);
  }

}
