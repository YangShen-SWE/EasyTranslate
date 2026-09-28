# What Changed in the Floating Window

The old window could show text, but it was still some way from the desktop pet I had in mind. This time I used GPT to help design and modify the interface, settings, and animations. Translation still uses the existing DeepSeek service; the tab-to-selection flow was not rebuilt as part of the visual update.

## The window and settings

It now has dark gray rounded corners with mint green accents, a cat on the left, and a plant and coffee cup on the right. The original and translation appear in separate scrolling columns by default. Turning off “Show original text” gives the translation the full text area. Turning it back on restores both columns without clearing the content or changing the window width and top-left position.

The title bar can be dragged. There are copy, pin, and close buttons, plus vocabulary and settings entry points below. The vocabulary button currently opens a “Coming soon” window; repeated clicks reuse it.

Settings opens a separate window in the same style. It offers a base font size of 16–26 px, small/medium/large window presets, and switches for the original text, always-on-top, decorations, and animations. Changes take effect immediately and are saved locally through Preferences. The settings window can be dragged and closed with its close button or Escape.

## How the cat and coffee move

The original images are kept, with the paws and steam separated for animation. Left and right keyboard regions trigger the corresponding paw, and space triggers both. Left and right mouse clicks also trigger a paw and a brief mint green glow. Coffee steam rises and fades roughly every 8 seconds, and buttons use a 120 ms opacity transition on hover. The head, plant, cup, and text stay still.

Short bursts of input are combined, and holding a key does not queue endless animations. Effects use a separate passive listener that does not block keys or record typed text or mouse coordinates. Disabling effects, hiding decorations, or closing the window stops animations and releases that listener.

## Where the code lives

These paths are relative to the repository root, so I can find things again when I want to change them.

| File | What it does |
| --- | --- |
| `EasyTranslate/src/main/resources/com/easytranslate/view/floating-view.fxml` | Main layout, decoration containers, columns, and buttons |
| `EasyTranslate/src/main/resources/com/easytranslate/view/floating-window.css` | Colors, fonts, rounded corners, scrollbars, and auxiliary windows |
| `EasyTranslate/src/main/java/com/easytranslate/view/FloatingViewController.java` | Display bindings, original-text switch, saved settings, dragging, and auxiliary windows |
| `EasyTranslate/src/main/java/com/easytranslate/view/CompanionMotion.java` | Image pieces, paw taps, steam, hover transitions, and starting/stopping effects |
| `EasyTranslate/src/main/java/com/easytranslate/service/hotkey/InputActivityBuffer.java` | Left/right key mapping, repeat suppression, and combining activity |
| `EasyTranslate/src/main/java/com/easytranslate/service/hotkey/WindowsInputActivityService.java` | Separate passive Windows keyboard and mouse listener |
| `EasyTranslate/src/main/java/com/easytranslate/view/WindowIcon.java` | Loading Lucide SVG icons |
| `EasyTranslate/src/main/java/com/easytranslate/EasyTranslateApplication.java` | Connecting the transparent window, styles, and effects listener |
| `EasyTranslate/src/main/java/module-info.java` | Adding java.xml for icon loading |
| `EasyTranslate/src/main/resources/com/easytranslate/view/assets/` | Cat, plant, and coffee images, with source notes |
| `EasyTranslate/src/main/resources/com/easytranslate/view/icons/` | Original icons and license |
| `EasyTranslate/src/test/java/com/easytranslate/view/FloatingWindowSmokeTest.java` | Standalone UI checks and sample preview |
| `EasyTranslate/src/test/java/com/easytranslate/view/NativeInputProbe.java` | Native keyboard and mouse input probe |
| `EasyTranslate/scripts/check-window.ps1` | Compiling and launching the UI checks |

The application entry point also had a redundant Tab observer that was constructed but never started. That was removed; the existing Tab listener implementation stayed the same.

## Trying it out

Run this from the inner `EasyTranslate` Maven project directory:

```powershell
.\scripts\check-window.ps1 -JavaHome 'Your JDK 25 directory'
```

The earlier run reported `UI_SMOKE_PASSED: 44 checks`. Adding `-Preview` keeps the translation-only sample and settings window open. It needs no API key and makes no online translation request. The preview enables passive keyboard and mouse effects and uses temporary settings, which are cleaned up on close.

A normal Maven `test` does not automatically run this standalone JavaFX program. Successful compilation is not the same as passing the UI checks. For normal use, run `EasyTranslateApplication`.

Screenshots and check records are in the [UI check notes](../design-qa.md), and the thinking behind this version is in the [Version 2 development notes](开发笔记（第二版）.md). Vocabulary storage, review, and online service settings were not completed as part of this visual update.

---

# 这次悬浮窗改了什么

之前的窗口能把字放出来，但是离我想要的桌宠样子还差一点。这次用 GPT 帮忙设计和修改，主要折腾了界面、设置和动效。翻译还是走原来的 DeepSeek 服务，tab 读取选区的流程没有因为美化重新做一遍。

## 窗口和设置

现在是深灰圆角配薄荷绿，小猫趴在左边，右边放花盆和咖啡杯。原文与译文默认双栏，各自可以滚动；关掉“显示原文”后，译文占满正文区域，再打开就恢复，内容不会清空，窗口宽度和左上角位置也不跟着变。

标题栏可以拖动，有复制译文、置顶和关闭按钮，底下有单词本和设置入口。单词本现在只打开“准备中”的窗口，重复点也不会一直新建。

设置单独开一个同样风格的窗口，可以调正文字号（16–26 px），选择小、中、大三个窗口档位，开关原文、置顶、装饰和动态效果。设置即时生效，用本机 Preferences 保存；设置窗口可以拖动，也能用关闭按钮或 Escape 关掉。

## 小猫和咖啡怎么动

保留原来的图片，把爪子和热气分出来做动画。左右键区对应左右爪，空格双爪；鼠标左右键也会拍一下，并短暂亮起薄荷绿。咖啡热气大约每 8 秒上浮、淡出一次，按钮悬停有 120 毫秒的明暗过渡，猫头、花盆、杯身和正文保持不动。

短时间里的键鼠活动会合并，长按不会排一长串动画。动效有独立的被动监听，不拦截按键，也不记录输入文字或鼠标坐标。关闭动效、隐藏装饰或关闭窗口时会停止动画并释放这套监听。

## 代码放在哪里

这里留个位置，后面想改的时候方便找。下面的路径都以仓库根目录为准。

| 文件 | 负责什么 |
| --- | --- |
| `EasyTranslate/src/main/resources/com/easytranslate/view/floating-view.fxml` | 主窗口布局、装饰容器、双栏和按钮 |
| `EasyTranslate/src/main/resources/com/easytranslate/view/floating-window.css` | 颜色、字体、圆角、滚动条和辅助窗口样式 |
| `EasyTranslate/src/main/java/com/easytranslate/view/FloatingViewController.java` | 显示绑定、原文开关、设置保存、拖动和辅助窗口 |
| `EasyTranslate/src/main/java/com/easytranslate/view/CompanionMotion.java` | 图片分片、拍爪、热气、悬停过渡和启停 |
| `EasyTranslate/src/main/java/com/easytranslate/service/hotkey/InputActivityBuffer.java` | 左右键区映射、长按抑制和活动合并 |
| `EasyTranslate/src/main/java/com/easytranslate/service/hotkey/WindowsInputActivityService.java` | 独立的 Windows 被动键鼠监听 |
| `EasyTranslate/src/main/java/com/easytranslate/view/WindowIcon.java` | 读取 Lucide SVG 图标 |
| `EasyTranslate/src/main/java/com/easytranslate/EasyTranslateApplication.java` | 透明窗口、样式和动效监听的接入 |
| `EasyTranslate/src/main/java/module-info.java` | 增加读取图标需要的 java.xml |
| `EasyTranslate/src/main/resources/com/easytranslate/view/assets/` | 小猫、花盆和咖啡杯图片与来源说明 |
| `EasyTranslate/src/main/resources/com/easytranslate/view/icons/` | 图标原文件和许可证 |
| `EasyTranslate/src/test/java/com/easytranslate/view/FloatingWindowSmokeTest.java` | 独立界面检查和样例预览 |
| `EasyTranslate/src/test/java/com/easytranslate/view/NativeInputProbe.java` | 原生键鼠输入检查窗口 |
| `EasyTranslate/scripts/check-window.ps1` | 编译并启动界面检查 |

应用入口还顺手移除了一个重复构造、没有启动的 Tab 观察器，原有 Tab 监听实现没有改。

## 怎么看看效果

在里面的 `EasyTranslate` Maven 项目目录运行：

```powershell
.\scripts\check-window.ps1 -JavaHome '你的 JDK 25 目录'
```

之前这一轮输出了 `UI_SMOKE_PASSED: 44 checks`。加上 `-Preview` 会保留仅译文样例和设置窗口，不需要 API Key，也不请求在线翻译。预览会开启被动键鼠动效监听，使用临时设置，关闭时清理。

普通 Maven `test` 不会自动运行这个独立 JavaFX 检查程序，编译成功不能直接当成界面检查成功。正常使用还是运行 `EasyTranslateApplication`。

截图和检查记录在 [界面检查笔记](../design-qa.md)，做这版时的想法在 [第二版开发笔记](开发笔记（第二版）.md)。词本、复习、在线服务设置还没有跟着这次美化一起做完。
