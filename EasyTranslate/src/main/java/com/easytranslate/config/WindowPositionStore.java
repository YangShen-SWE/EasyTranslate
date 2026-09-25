package com.easytranslate.config;

import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.prefs.Preferences;

public class WindowPositionStore
{
  //记住窗口关闭时候的位置
  private static final String X_KEY = "window.x";
  private static final String Y_KEY = "window.y";

  private final Preferences preferences = Preferences.userNodeForPackage(
      WindowPositionStore.class);
  //preferences持久化
  public void restore(Stage stage){
    double x = preferences.getDouble(X_KEY,Double.NaN);//double.nan表示尚未保存坐标
    double y = preferences.getDouble(Y_KEY,Double.NaN);
    //在第一次启动没有记录时，javafx自己决定窗口初始位置

    if(Double.isFinite(x)&&Double.isFinite(y)&&!Screen.getScreensForRectangle(x,y,1,1).isEmpty()){
      stage.setX(x);
      stage.setY(y);
      //屏幕检查，避免窗口恢复到看不见的位置
    }
  }
  public void save(Stage stage){
    preferences.putDouble(X_KEY,stage.getX());
    preferences.putDouble(Y_KEY,stage.getY());
  }
}
