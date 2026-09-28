# 悬浮窗外观更新

这次更新保留现有翻译服务、ViewModel、Tab 监听和窗口位置存储，集中修改 JavaFX 界面。

## 文件与职责

| 文件 | 修改内容 |
| --- | --- |
| `EasyTranslate/src/main/resources/com/easytranslate/view/floating-view.fxml` | 顶部装饰、标题栏、原文与译文双栏、独立滚动区、复制和底部入口。 |
| `EasyTranslate/src/main/resources/com/easytranslate/view/floating-window.css` | 石墨灰主题、薄荷绿强调、圆角、阴影、字体、滚动条、按钮状态以及辅助窗口样式。 |
| `EasyTranslate/src/main/java/com/easytranslate/view/FloatingViewController.java` | 空状态、数据绑定、复制反馈、置顶开关、长文高度限制、标题栏拖动、单词本空状态窗口、可保存的外观设置。 |
| `EasyTranslate/src/main/java/com/easytranslate/view/WindowIcon.java` | 读取随项目分发的 Lucide SVG，并渲染为 JavaFX 图形。 |
| `EasyTranslate/src/main/java/com/easytranslate/EasyTranslateApplication.java` | 透明 Stage 和 Scene，使圆角及顶部装饰外部真正透明；接入外观配置。 |
| `EasyTranslate/src/main/java/module-info.java` | 增加 JDK 自带的 java.xml，用于读取图标。 |
| `EasyTranslate/src/main/resources/com/easytranslate/view/assets/` | 小猫、花盆和咖啡杯透明 PNG 及来源说明。 |
| `EasyTranslate/src/main/resources/com/easytranslate/view/icons/` | 6 个 Lucide 图标原文件及许可证。 |
| `EasyTranslate/src/test/java/com/easytranslate/view/FloatingWindowSmokeTest.java` | 独立 JavaFX 界面检查程序和离线预览。 |
| `EasyTranslate/scripts/check-window.ps1` | 编译并运行界面检查的入口。 |
| `design-qa.md`、`docs/ui/` | 设计目标、实际窗口截图、视觉验收记录。 |

## 已可使用

- 默认原文与译文同时显示；设置里的“显示原文”关闭后，译文占满正文宽度，中间分隔线隐藏；重新开启恢复双栏并保留翻译内容。切换时宽度和左上角位置不变。
- 复制译文，切换置顶，标题栏拖动，关闭窗口。
- 设置中的显示原文、正文字号（16–26 px）、置顶和装饰显示立即生效并保存到本机 Preferences。设置窗口沿用深灰圆角、薄荷绿开关、细线图标和小猫装饰；支持标题栏拖动、关闭按钮和 Escape。
- 单词本按钮打开独立窗口，明确显示功能准备中；重复点击不会创建多个窗口。

没有增加词目存储、自动复习或在线服务设置。在线翻译仍使用项目已有配置。

## 动态效果更新

- 保留原有小猫、花盆和咖啡杯图片。左右键区对应左右爪轻拍，空格双爪轻拍；鼠标左右键对应爪子轻拍并短暂亮起薄荷绿。
- 咖啡热气约每 8 秒轻轻上浮、淡出；猫头、植物、杯子和正文保持稳定。主窗口按钮悬停使用 120 毫秒透明度过渡。
- 设置新增“动态效果”开关，立即生效并保存。关闭动效、隐藏桌面装饰或关闭窗口会停止动画并释放独立的键鼠监听。
- `CompanionMotion.java` 管理图片分片、动画与生命周期；`InputActivityBuffer.java` 合并活动信号并抑制长按重复；`WindowsInputActivityService.java` 使用被动 Windows 钩子，原样传递输入，不记录文字、鼠标坐标或输入历史。
- 控制器与 FXML 接入动效容器和开关；应用入口启用动效监听，并移除了一个重复构造但未启动的旧 Tab 观察器。现有 Tab 翻译监听实现保持不变。
- `NativeInputProbe.java` 提供独立的 Windows 输入检查窗口，不调用翻译或保存输入。

## 如何检查

在 `EasyTranslate` Maven 项目目录执行：

```powershell
.\scripts\check-window.ps1 -JavaHome 'C:\Program Files\Java\jdk-25.0.4-full'
```

出现 `UI_SMOKE_PASSED: 44 checks` 后窗口自动关闭。增加 `-Preview` 可保留仅译文样例与设置窗口；预览启用被动键鼠动效监听，不触发全局 Tab 翻译，也不会调用翻译服务。设置使用临时节点，在关闭预览时删除。正常使用仍运行 `EasyTranslateApplication`。

普通 Maven `test` 仅完成当前项目编译检查，不自动执行这个独立 JavaFX 验证程序。
