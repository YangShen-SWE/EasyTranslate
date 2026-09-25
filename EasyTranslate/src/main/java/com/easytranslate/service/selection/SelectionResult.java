package com.easytranslate.service.selection;
import java.util.Objects;
public record SelectionResult(Status status,String text)
{
  public enum Status{
    Found,//读取到了文字
    None,//没有选中文字
    Unknown //无法判断目标软件的选区
  }
  public SelectionResult{
    Objects.requireNonNull(status);
    if(status == Status.Found && (text == null || text.isBlank())){
      throw new IllegalArgumentException("Found 必须包含文字");
    }
    if(status != Status.Found && text != null){
      throw new IllegalArgumentException("非Found 状态不能包含文字");
    }
  }
  public static SelectionResult found(String text){
    return new SelectionResult(Status.Found,text.strip());
  }
  public static SelectionResult none(){
    return new SelectionResult(Status.None,null);
  }
  public static SelectionResult unknown(){
    return new SelectionResult(Status.Unknown,null);
  }
}
