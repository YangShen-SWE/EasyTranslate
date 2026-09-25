package com.easytranslate.view;
import com.easytranslate.viewmodel.FloatingViewModel;
import javafx.scene.control.Label;
import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class FloatingViewController
{
  @FXML
  private Label sourceLabel;
  @FXML
  private Label translatedLabel;
  @FXML
  private VBox root;

  private double dragOffsetX;
  private double dragOffsetY;
  //property 对比set/get的优势是不仅可以读取修改，还可以监听变化以及和其他property绑定
  //bind 是单向绑定，viewModel改变label改变  但是label改变不会影响viewModel
  public void setViewModel(FloatingViewModel viewModel){
    sourceLabel.textProperty().bind(viewModel.sourceTextProperty());
    translatedLabel.textProperty().bind(viewModel.translatedTextProperty());
  }
  @FXML
  private void onDragStarted(MouseEvent event){
    Stage stage = getStage();
    dragOffsetX = event.getScreenX() - stage.getX();
    dragOffsetY = event.getScreenY() - stage.getY();
  }

  @FXML
  private void onDragging(MouseEvent event){
    //拖动窗口实现
    //鼠标按下时，记住“鼠标在窗口内部的哪个位置”。
    //鼠标移动时，用“鼠标当前屏幕坐标 − 刚才记录的位置”计算窗口的新位置。
    Stage stage = getStage();
    stage.setX(event.getScreenX() - dragOffsetX);//dragOffsetX是鼠标相对于窗口的位置
    stage.setY(event.getScreenY() - dragOffsetY);
  }

  @FXML
  private void onClose(){
    getStage().close();
  }

  private Stage getStage(){
    return (Stage) root.getScene().getWindow();
  }
}
