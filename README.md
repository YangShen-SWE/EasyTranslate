# EasyTranslate

EasyTranslate is a small Windows floating translator I made for reading English in other apps. Select a word or phrase, press Tab, and the floating window shows the original text beside a Chinese translation. It uses DeepSeek's `deepseek-flash` model, so translation needs an internet connection and your own DeepSeek API Key.

The window also has a little cat that reacts to keyboard and mouse activity. I wanted it to be pleasant to keep on the desktop, but the text is still the main part.

## What it can do

- Read a valid text selection from another Windows app and translate it after you press Tab.
- Show the original and translation side by side, with a button to copy the translation.
- Pin the floating window above other windows, or move it out of the way.
- Set the window to small, medium, or large (large is the default), change the text size, or hide the original text.
- Show or hide the cat, plant, and coffee cup, and turn their motion off if it gets distracting.
- Save a DeepSeek API Key in settings and delete it there later. The key is encrypted for the current Windows user; the input box does not display a saved key.

## Install and use

1. Download `EasyTranslate-2.1.0.exe` from [GitHub Releases](https://github.com/YangShen-SWE/EasyTranslate/releases) and run the installer. The installer includes the Java runtime.
2. Open EasyTranslate, click **设置**, enter your DeepSeek API Key, and click **保存**. You need to obtain the key from DeepSeek yourself; it is not included with the app.
3. Select English text in another app and press **Tab**. If that app exposes its selection to Windows, the translation appears in the floating window.
4. Use **复制译文** to copy the result. In **设置**, you can also choose the window size and adjust the other appearance options. To remove your saved key, click **删除** next to the API Key field.

## About API cost

DeepSeek charges for input and output tokens, not for each word. As of 2026-09-28, the [official `deepseek-flash` pricing](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/) lists peak rates of ¥2 per million uncached input tokens and ¥8 per million output tokens; off-peak rates are half of that. This app requests non-thinking translations. If I look up words one at a time and *assume* roughly 20–50 input tokens and 3–12 output tokens per request, ¥10 works out to about **50,000–150,000 English word lookups** at peak rates. That is a rough budget estimate, not a measured promise: actual usage depends on the selected text, the reply, caching, and when the request runs. Check your DeepSeek usage for the real cost.

Tab still reaches the original app. If no text is selected, there is nothing to translate. Selection reading works differently across apps, so some editors and scanned PDFs may not work. The vocabulary book button currently opens a placeholder; it does not save words yet. Light and dark mode switching and a custom translation shortcut are on the [issue list](docs/待解决问题清单.md).

The selected text is sent to DeepSeek when you request a translation. Keyboard and mouse activity used for the cat animation is not saved as typed text. For the current scope and other limits, see the [requirements](docs/requirements.md). My development notes are separate: [Version 1](docs/开发笔记（第一版）.md) and [Version 2](docs/开发笔记（第二版）.md).

---

# EasyTranslate 中文说明

EasyTranslate 是我做的一个 Windows 悬浮翻译小工具，主要用来看其他软件里的英文。选中单词或词组后按 Tab，悬浮窗会把原文和中文译文放在一起。翻译目前使用 DeepSeek 的 `deepseek-flash` 模型，需要联网，也需要你自己的 DeepSeek API Key。

窗口上还有一只会跟着键盘和鼠标轻轻动的小猫。我想让它放在桌面上看着舒服一点，不过重点还是读译文。

## 现在能做什么

- 在其他 Windows 软件里读取有效的文字选区，按 Tab 后请求翻译。
- 左右显示原文和译文，也可以一键复制译文。
- 让悬浮窗保持置顶，或者把它拖到不挡视线的位置。
- 选择小、中、大三档窗口大小（默认是大），调整字号，或者关掉原文只看译文。
- 显示或隐藏小猫、花盆和咖啡杯；觉得动效分心，也可以单独关掉。
- 在设置里保存或删除 DeepSeek API Key。密钥会为当前 Windows 用户加密保存，输入框不会回显已经保存的密钥。

## 安装和使用

1. 在 [GitHub Releases](https://github.com/YangShen-SWE/EasyTranslate/releases) 下载 `EasyTranslate-2.1.0.exe` 并运行安装程序。安装包已经带上 Java 运行环境。
2. 打开 EasyTranslate，点击**设置**，输入自己的 DeepSeek API Key，再点**保存**。密钥需要自己去 DeepSeek 获取，软件不附带密钥。
3. 在其他软件里选中英文，再按 **Tab**。如果那个软件允许 Windows 读取选区，译文就会出现在悬浮窗里。
4. 点**复制译文**可以复制结果。窗口大小和其他外观选项也在**设置**里；要移除密钥，点 API Key 输入框旁边的**删除**。

## API 费用大概多少

DeepSeek 按输入和输出的 token 计费，不是按单词数收费。按 2026-09-28 的[官方 `deepseek-flash` 价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)，高峰时段缓存未命中的输入是每百万 token 2 元，输出是每百万 token 8 元，低峰时段减半。现在的程序使用非思考模式。如果每次只查一个单词，并且*假设*每次请求用了约 20～50 个输入 token、3～12 个输出 token，那么按高峰价格估算，**10 元大约能查 5 万～15 万个英文单词**。这只是算预算用的估计，不是实测或保证；选区长度、译文长度、缓存和调用时段都会影响实际花费，最终还是看 DeepSeek 的用量记录。

目前 Tab 也会继续传给原软件；没有有效选区时不会翻译。不同软件暴露选区的方式不一样，所以部分编辑器和扫描版 PDF 可能读不到。单词本现在只有一个“准备中”的窗口，还不能保存词条。白天／夜间模式和自定义翻译快捷键已写进[待解决问题清单](docs/待解决问题清单.md)。

发起翻译时，选中的文字会发送给 DeepSeek。小猫动效用到的键鼠活动不会作为输入文字保存。当前范围和其他限制见[需求文档](docs/requirements.md)。开发过程单独记在[第一版笔记](docs/开发笔记（第一版）.md)和[第二版笔记](docs/开发笔记（第二版）.md)。
