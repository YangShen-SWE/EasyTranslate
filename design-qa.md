# UI Check Notes

Updated through 2026-09-28. I used GPT to help design the interface and turn it into a JavaFX window. This page records what was actually looked at and what the check program covered, so there is something to compare against if a later change breaks it.

## What stayed in the design

Dark gray `#252b32` with mint green `#8be6c0`, a transparent outside area, and the cat, plant, and coffee cup on top. Fonts are Microsoft YaHei UI / Segoe UI. The original defaults to 22 and the translation to 23, with adjustable sizing.

The original and translation sit side by side with independent scrolling. Hiding the original leaves only the translation. The title bar handles dragging while buttons handle their own clicks, so opening settings should not drag the window away. Short text stays compact; longer text gets a height limit and scrolling.

The first white-outline cat was hard to see on light backgrounds. The artwork was changed to have dark gray interiors while keeping the outside transparent. Spacing was tightened too: the minimum short-text viewport went from 84 to 64, with a maximum of 220 for longer content. Generated typography will not exactly match system fonts; readable, unclipped text comes first.

“Review later” was removed because that feature does not exist yet. The empty state says “Waiting for selection,” results say “Translation result,” and the vocabulary window clearly says “Coming soon.”

## Comparing the designs with actual windows

| View | Design reference | Actual screenshot |
| --- | --- | --- |
| Two-column window | [Design](docs/ui/design-reference.png) | [Window](docs/ui/floating-window.png) |
| Translation only | [Design](docs/ui/translation-only-reference.png) | [Window](docs/ui/translation-only.png) |
| Settings | [Design](docs/ui/appearance-settings-reference.png) | [Window](docs/ui/appearance-settings.png) |

The two-column reference is 1564 × 1006 and the actual screenshot is 588 × 289. The translation-only reference is 1564 × 1006; settings is 1254 × 1254. Comparisons use the panel itself, excluding the desktop background around the generated design. At the time, the main panel was 560 wide and settings was 420, or roughly 588 and 448 including outer spacing. The code now also has small, medium, and large presets; those older screenshots only show the large window from that stage.

The sample was “Take it one step at a time.” / “一步一步来。” Text, buttons, and settings labels were not clipped, and decorations were visible on a light background. Always-on-top was off in the translation-only screenshot, which is a valid switch state.

The settings slider still uses a native gray track with a mint green thumb, without exactly copying the filled track in the mockup. The main visibility and spacing problems in the static UI were addressed. These screenshots predate the animation switch, so they do not verify the newest settings layout or each animation frame.

## What the check program covered

It started with 16 checks, grew to 28 with the original-text switch, and reached 44 with animations. A run reported `UI_SMOKE_PASSED: 44 checks`.

- The initial checks covered resource loading, bindings, empty copy state, short/long text layout, vocabulary-window reuse, and saved settings.
- Original-text checks covered hiding the divider, expanding the translation, preventing hidden long text from increasing height, stable position and width, and restoring content.
- Animation checks covered left/right mapping, space triggering both paws, repeat suppression, combining input, movement and reset, stable window dimensions, saved settings, reset on disable, and native listener startup and re-enabling.

The entry point is `EasyTranslate/scripts/check-window.ps1`; the command is in the [window update notes](docs/floating-window-update.md). Checks use temporary Preferences, do not call online translation, and do not write vocabulary. They now temporarily enable passive effects hooks and release them on exit. Normal Maven compilation or `test` does not mean these 44 checks ran.

## What still needs checking

The native input probe saw mouse and left-side keyboard activity, and the text field received `a` normally. Right-side mapping was checked in code, but a complete manual native-input check remains.

There is no reliable new animation screenshot from this round. Online translation, multiple monitors, fullscreen, elevated apps, and different focus transitions were not tested again. Passing the program's checks only covers what those checks actually exercise, not every Windows scenario.

A light theme and stacked columns for a narrow window can wait. For now I am keeping this horizontal style. Translation and compatibility deserve more testing next; the remaining work is on the [issue list](docs/待解决问题清单.md).

---

# 界面检查笔记

更新到 2026-09-28。这次用 GPT 帮我设计界面，再改成 JavaFX 窗口。这里记一下实际看过什么、程序检查过什么，后面改坏了也方便回来对照。

## 外观最后留下了什么

深灰色 `#252b32` 配薄荷绿 `#8be6c0`，窗口外部透明，小猫、花盆和咖啡杯放在上边。字体用 Microsoft YaHei UI / Segoe UI，默认原文 22、译文 23，字号可以调。

原文和译文默认左右放，分别滚动；关闭原文后只留译文。标题栏用来拖动，按钮自己处理点击，避免点个设置把窗口拖跑了。短文字保持紧凑，长文字限制显示高度再滚动。

最开始白线小猫放在浅色背景上不太看得清，后来把图案内部换成深灰色，外部仍然透明。也收紧了留白，短文本视口下限从 84 调到 64，长内容高度上限保留 220。生成图的字体和实际系统字体不会完全一样，先保证字能看清、不被截掉。

“稍后复习”已经去掉了，现在没做这个功能。没结果时显示“等待划词”，有结果时显示“翻译结果”，单词本明确写着“准备中”。

## 图和实际窗口怎么对照

| 内容 | 设计参考 | 实际截图 |
| --- | --- | --- |
| 双栏悬浮窗 | [设计图](docs/ui/design-reference.png) | [窗口截图](docs/ui/floating-window.png) |
| 仅显示译文 | [设计图](docs/ui/translation-only-reference.png) | [窗口截图](docs/ui/translation-only.png) |
| 设置窗口 | [设计图](docs/ui/appearance-settings-reference.png) | [窗口截图](docs/ui/appearance-settings.png) |

双栏参考图是 1564 × 1006，实际截图是 588 × 289。仅译文参考图是 1564 × 1006，设置参考图是 1254 × 1254。对照时只比面板部分，不把生成图周围的桌面背景一起算进去。当时主窗口面板宽 560，设置面板宽 420，带上外部边距分别约 588 和 448。现在代码还加了小、中、大三个窗口档位，这组旧截图只对应当时的大窗口。

当时实际窗口用了 “Take it one step at a time.” / “一步一步来。” 这组样例。正文、按钮和设置项没有截字，浅色背景上的装饰也能看见。仅译文截图里置顶是关闭的，是开关的一种正常状态。

设置滑块还保留了原生灰色轨道和薄荷绿滑块，没有完全照着效果图填色。之前静态界面的主要可见性和留白问题已经修过；这些截图是在新增动效开关之前截的，不代表最新设置窗口或逐帧动画已经验收。

## 检查程序跑过什么

最早只有 16 项，后来加原文开关变成 28 项，这次加动效后是 44 项，已输出过 `UI_SMOKE_PASSED: 44 checks`。

- 原有检查包括资源加载、数据绑定、复制空状态、长短文本布局、单词本窗口复用和设置保存。
- 原文开关检查了分隔线隐藏、译文占满宽度、隐藏的长原文不撑高窗口、位置和宽度不变，以及重新打开后恢复内容。
- 动效检查了左右键映射、空格双爪、长按抑制、输入合并、爪子位移和归位、正文窗口尺寸不变、开关保存、停止后复位以及原生监听启动和重新启用。

运行入口是 `EasyTranslate/scripts/check-window.ps1`，具体命令看 [窗口更新说明](docs/floating-window-update.md)。检查使用临时 Preferences，不调用在线翻译、不写词本；现在会临时启用被动动效钩子，退出时释放。普通 Maven 编译或 `test` 不等于跑过这 44 项。

## 还有哪些没检查完

原生输入探针确实看到了鼠标活动和左键区活动，输入框也正常收到 `a`。右键区映射有程序检查，但手动原生输入还没完整试完。

这次没有拿到可靠的新动效截图，也没有重测在线翻译、多显示器、全屏、提权应用和各种焦点切换。程序检查通过，只能说明检查覆盖到的部分通过了，不能直接写成所有 Windows 场景都没问题。

浅色主题和窄窗口上下排版先不做，继续保持现在这个横向窗口。下一步更该花时间检查翻译和兼容性，具体记在 [问题清单](docs/待解决问题清单.md)。
