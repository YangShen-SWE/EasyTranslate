# What I Want EasyTranslate to Become

Updated through 2026-09-28, requirements v0.4.

I originally got interested because of an input method with translation. Then I found out how much work it could take just to read selected text, so I decided to get desktop selection translation working first. I want to keep the ideas, but cannot list everything I want as something that already works. This is the plan reorganized around the current progress.

## 1. Get desktop translation working first

This is a standalone Windows floating window, something like bongocat, used alongside other apps. It is not a browser or PDF plugin, and I am not planning to build a full reader.

The current flow is: select an English word or continuous phrase in another app, press tab, read the valid selection, call DeepSeek, and display the original with a Chinese translation. The code currently specifies deepseek-flash. Longer selections can also be sent, but selection reading has a 1,000-character limit, so this is not yet a complete long-text translator.

The JavaFX window, Windows UI Automation selection reading, passive Tab listener, online translation, and appearance settings are in place. The vocabulary book is only an entry point and a “Coming soon” window. Database storage, review playback, and input-method assistance are not implemented.

Browsers, text-based PDFs, Notepad, and IDEs are compatibility goals. Chrome and some desktop apps were tried before, but IDE editors remain unreliable. This is not universal system-wide support. OCR for images and scanned PDFs, and speech scoring, are outside this version.

## 2. Keep the current window style

Keep the dark gray rounded panel, mint green accents, simple cat, plant, and coffee cup. GPT helped design and implement the UI. New windows should follow the same style rather than feeling like a different app each time.

The original and translation appear in independently scrolling columns by default. Settings can hide the original so only the translation remains, and restore both columns without losing content. That switch should not change the width or top-left position, and hidden original text should not keep making the window taller.

The window can be dragged, pinned, and restored to its saved position. It provides copy, vocabulary, settings, and close actions. New translations should not steal keyboard focus from the original app; this still needs testing across apps.

Settings currently offers original-text visibility, base font size, small/medium/large window presets, always-on-top, decorations, and animations. Changes apply immediately and are saved locally. Base font size is 16–26 px, with translation text 1 px larger. The three size presets exist; free dragging to resize, transparency, and mouse click-through remain possible later additions. Click-through needs testing so the window remains easy to move and operate.

With no content, show “Waiting for selection”; after success, show “Translation result.” Failures should get a short message that does not interrupt work, but that flow is not fully connected yet. Until the vocabulary book works, say it is coming soon instead of showing fake entries or an unimplemented “Review later” button.

## 3. Let the cat move without making text harder to read

Left and right keyboard regions trigger their respective paws; space triggers both. Left and right mouse clicks give the corresponding paw a small tap and glow. Coffee steam rises and fades occasionally, and buttons gently brighten on hover. The head, plant, cup, text, and window position stay still.

These effects are connected and reuse pieces of the original images. Input activity only triggers motion. It does not save typed content, mouse coordinates, or activity history, and does not count as a vocabulary lookup.

Continuous input should be combined, and holding a key must not queue a long chain of animations. The listener passes events through without changing what the original app receives. Disabling effects, hiding decorations, or closing the window stops animations and releases the effects listener. This mapping is desktop-pet feedback, not an implemented input method.

## 4. Rules selection translation still needs to follow

Keep “select, then press tab” for now. Automatic translation after a stable selection is a possible later mode and is not implemented. If added, it must wait for selection to settle rather than sending requests continuously while the mouse is dragged.

Only process text explicitly selected for this operation, without continuously reading whole documents or ordinary typing. The API currently receives the selected fragment. Future context reading should temporarily use only necessary nearby text, explain that behavior, and not store it in the vocabulary book by default.

Tab is currently observed, so the original app still receives it. Key repeat and Alt+Tab have been treated separately, but input-method candidate routing is not implemented. The eventual goal is to prioritize candidate operations and preserve normal Tab behavior without a valid selection. If plain Tab cannot coexist safely in practice, choose another trigger explicitly instead of silently changing the rule.

Empty selections, cancellations, unreadable selections, and failed requests do not count as successful translations. Repeated callbacks from one action should update and record only once. Only the latest request should update the UI; the old-result-overwriting-new-result problem is still pending.

An inaccessible, protected, or non-text selection should get a short message, without repeated attempts. Current reading uses UI Automation, with the element under the mouse as a fallback entry point; it is not OCR. Simulated copying is only an option to investigate. Clipboard restoration, images, rich text, multiple formats, and history must be verified first.

## 5. Vocabulary comes next; keep the recording rules clear

The plan is local MySQL connected through JDBC. That is not connected in the application yet. Configuration stays on the user's machine; schema setup, initialization, and backup instructions will also be needed.

Only a successfully displayed selection translation counts as a “reading help” event. Future explicitly submitted input-method candidates count as “input help.” Failures, cancellations, ordinary typing, viewing candidates, review playback, and animation events do not increase counts.

Each entry should keep a word or continuous phrase, language, a short corresponding meaning, reading and input counts, last-help time, review weight, and state. Each help event should separately record its time, source, submission method, and unique operation ID for deduplication and recalculating statistics.

Case and outer whitespace can be normalized, but phrases should not be split casually. Different meanings with the same spelling should not be merged automatically, and Chinese and English entries should not be merged just because they translate each other. Later, allow manual meaning corrections, duplicate merging, and marking entries as learned.

The vocabulary book should support search and history, show the most requested entries over the last 7 days and overall, and separate reading from input. Frequent requests measure requests for help, not proficiency.

## 6. Idle review remains a later plan

The earlier idea was to keep a new translation for a while, provisionally 1 minute by default, with an adjustable duration. Start timing after the new translation is successfully displayed; failures, empty selections, and duplicate callbacks should not reset it. Once time runs out without a new result, cycle through existing entries. A new successful translation stops review and restarts the timer.

That timer and playback do not exist yet, and neither does “Review later.” Eventually review can be paused or disabled. Whether disabling it leaves the last translation visible or returns to idle can be a setting.

Entry selection should consider recent help, reading/input sources, and recent display, not just lifetime counts. Frequently and recently requested entries get priority; entries not requested for a long time may lose weight. Avoid showing the same entry back to back. The formula and cooldown can be decided through testing later.

Playback should show at least the word or phrase and its existing short corresponding meaning, without crowding the window with counts. Later controls can include speed, previous/next, skipping, history, and marking learned. Playback itself never increases help counts; display time can be stored separately to avoid repetition.

## 7. Archiving and input methods come further down the list

Archiving should be optional: for example, after 30 days without a help request, move an entry from the learning list into an automatic archive while keeping the entry and history. Automatic archiving does not mean it has been learned. Learned status stays separate. Neither state participates in normal playback by default; archived entries could optionally be included.

Requesting an archived entry restores the existing entry with its old counts instead of creating a duplicate. The past year's archive records should be viewable and manually restorable. That year is a display filter, not a rule to delete data after a year.

Input methods are interesting, but not connected yet, and desktop translation should not have to wait for them. Keep the previously agreed candidate-stage rules here:

| Action | Intended result | Record input help? |
| --- | --- | --- |
| Normal space submission | Follow the input method's normal rules | No |
| Tab confirms a Chinese candidate | The Chinese text is actually committed | Once after success |
| Tab+Space pressed together | The English candidate is actually committed | Once after success |
| Direct English typing or browsing candidates | Normal input | No |

For “混凝土 / concrete,” Tab commits Chinese and Tab+Space commits English. The chord must take priority; it must not first commit Chinese or trigger selection translation before handling English. No valid candidate or no actual commit means no event.

Candidate handling takes priority over selection translation. Ordinary typing is not logged. Start with properly licensed local dictionaries instead of sending every pinyin keystroke online. Online candidate supplementation must be explicitly triggered.

Reading and input eventually share one local vocabulary book. Input events do not interrupt the current translation by default. Whether to briefly show the newly committed word remains undecided, as does replacing Chinese with English after it has already been committed. Integration is unverified and currently does not depend on Rime. Reliable handoff of input events while the main program is closed also needs validation before implementation.

## 8. Data and online services

The vocabulary book should keep only explicitly requested words or phrases, necessary short meanings, events, and states by default. Do not store full sentences, PDFs, page contents, window titles, app histories, ordinary keystrokes, uncommitted candidates, or keys.

Reading one selection does not justify continuous screenshots, clipboard reading, or collection of other apps' text. Temporary access is limited to the current action, and animation signals are not written to disk.

Online translation is currently connected to DeepSeek using an API key saved in settings and encrypted for the current Windows user. Provider information, an explanation of what is sent before enabling it, and an option to disable online translation still need work.

If automatic translation is added, explain that finishing a selection may send it online, and allow switching back to key confirmation. Do not test with passwords, personal sensitive information, or confidential material. Keep real vocabulary data, coursework PDFs, clipboard contents, and keys out of the repository. Use made-up examples for demos.

Images, icons, code, and dictionaries need their sources and licenses retained. Attribution alone does not necessarily permit redistribution.

## 9. What to work on next

First fix request ordering and failure messages, then test selection compatibility, Tab conflicts, and focus. After that, add JDBC storage and a real vocabulary UI. Review weights, archiving, input methods, and packaging come later. The online service is already connected, so improve its configuration and error handling rather than listing provider selection as unstarted work.

The current stage needs actual checks for:

- Selection reading in browsers, text-based PDF readers, Notepad, and IDEs, using both words and continuous phrases, without damaging input, focus, or the clipboard on failure.
- Always-on-top, dragging, position restoration, original-text visibility, font size, and saved preferences, plus multiple monitors, scaling, fullscreen, and the taskbar area.
- Rapid lookups, cleared selections, offline conditions, and API failures, with only the appropriate result displayed.
- Effects that leave input intact, do not accumulate on key hold, and release listeners when disabled or closed.

The standalone UI program previously passed 44 checks; their scope is in the [UI check notes](../design-qa.md). That does not mean all of the scenarios above have been verified.

Later vocabulary checks need successful-display-only recording, callback deduplication, no records on failure, search, and history. Playback checks need timing, interruption by new translations, no extra counts from display, and avoiding repetition. Archive testing should use simulated time rather than waiting 30 real days.

For input methods, start with one local “混凝土 → concrete” mapping in Notepad, browser fields, and an IDE. Verify candidate commits, chord priority, and deduplication before expanding.

## 10. A few ideas to keep for later

After translating a long passage, perhaps the app could pick out useful linking expressions, sentence patterns, and special phrase uses, or highlight words already in the vocabulary book. It should not automatically save the whole passage or every word, or increase help counts just because a word appeared.

“Appeared in the original” and “I actually asked for help again” need to stay separate. Which fragments are worth saving, whether I should confirm them, and how to keep only necessary pieces of sentence patterns are still undecided. Online analysis that sends longer text also needs its own explanation.

Stable-selection detection, a copying fallback, click-through, review timing and speed, input-method candidate display, replacing committed text, and dictionary licensing are still open questions. For now, keep the selected cat and window style and make translation and vocabulary work reliably, one step at a time.

---

# EasyTranslate 现在想做成什么样

更新到 2026-09-28，需求记录 v0.4。

最开始是被带翻译的输入法吸引，后来发现光是读取选中文字就能折腾很久，所以还是先把桌面划词翻译做好。想法先留着，但是不能把想做的全写成已经能用的。这里按现在的进度重新整理一下，之后改功能也照着这份看。

## 1. 先把桌面翻译做好

这是一个独立的 Windows 悬浮窗，类似 bongocat，放在其他软件上面用。不是浏览器或 PDF 插件，也不打算自己做一个完整阅读器。

当前主要流程是：在其他程序里选中英文单词或连续词组，按 tab，读取有效选区，调用 DeepSeek，再把原文和中文译文显示出来。当前代码配置的是 deepseek-flash。较长片段也可以送去翻译，但选区读取目前有 1000 字符上限，还不能当成完整长文翻译工具。

现在已有 JavaFX 窗口、Windows UI Automation 选区读取、被动 Tab 监听、在线翻译和外观设置。单词本只有入口和“准备中”窗口，数据库读写、复习轮播、输入法辅助都没实现。

希望覆盖浏览器、文字型 PDF、记事本和 IDE，但这是兼容目标。之前试过 Chrome 和部分桌面软件，IDE 编辑器仍不可靠，不能写成已经全系统通用。图片、扫描 PDF 的 OCR 和口语评分先不做。

## 2. 窗口就沿用现在这套

保留深灰色圆角、薄荷绿强调、简洁的小猫，以及花盆和咖啡杯。UI 用 GPT 辅助设计和实现，后面新增窗口也保持这套风格，不要每开一个窗口就像换了个软件。

原文和译文默认左右放，各自滚动。设置里能关闭原文，只显示译文；恢复双栏时保留内容。切换不改变窗口宽度和左上角位置，隐藏的原文也不该继续撑高窗口。

窗口能拖动、开关置顶、记住位置，提供复制译文、单词本、设置和关闭入口。新翻译到来时不应该抢走原应用的键盘焦点，这一点还需要跨应用实测。

现在设置里有显示原文、正文字号、窗口大小（小、中、大）、置顶、桌面装饰和动态效果，立即生效并保存在本机。字号基准范围是 16–26 px，译文比原文大 1 px。窗口大小已有小、中、大三个档位；自由拖拽缩放、透明度和鼠标穿透先作为后续可选项，鼠标穿透得先验证不会让窗口没法操作。

没有内容时显示“等待划词”，成功后显示“翻译结果”。失败时希望能有简短、不打断操作的提示，这部分还没接完整。单词本没做完就明确写准备中，不展示假词条，也不放没有实现的“稍后复习”按钮。

## 3. 小猫可以动，但是别影响看字

左右键区对应左右爪，空格双爪；鼠标左右键也给对应爪子一点轻拍和亮光。咖啡热气偶尔上浮、淡出，按钮悬停柔和提亮。猫头、植物、杯身、正文和窗口位置保持稳定。

这套效果已经接入，继续使用原来的图片分片。输入活动只用来触发动画，不保存具体键入内容、鼠标坐标或活动历史，也不算查词次数。

连续输入要合并，长按不能堆积一长串动画。监听原样放行事件，不改变原应用收到的输入。关闭动态效果、隐藏装饰或关闭窗口时停止动画并释放对应监听。这里的键鼠映射只是桌宠反馈，不代表输入法功能已经做了。

## 4. 划词翻译还需要守住哪些规则

现在先保留“选中后按 tab”，选区稳定后自动翻译是后续可选模式，暂时没做。以后如果加自动模式，也要等选区稳定，不能拖着选区就连续请求。

只处理这次明确选中的内容，不持续读取整篇文档或普通输入。现在接口发送的是选中片段；以后要读上下文，也只临时读必要的邻近文字，并明确告知，不默认写进词本。

Tab 现在只是被观察，原应用仍会收到按键。长按重复和 Alt+Tab 做过区分，但输入法候选阶段的分流还没做。最终希望候选操作优先，没有有效选区时不影响原应用的 Tab；如果单独 Tab 实测无法安全共存，就重新确定触发方式，不能悄悄换规则。

空选区、取消、无法读取和失败请求都不能当成一次成功翻译。同一次操作的重复回调只能更新和记账一次；连续请求只允许最新请求更新界面，旧结果覆盖新结果的问题目前待修。

选区无法访问、受保护或不是文字时，给个简短提示就够了，不要反复尝试。当前使用 UI Automation，鼠标位置的控件是读取后备入口，不是 OCR。模拟复制还只是备选，必须先验证剪贴板恢复、图片、富文本、多格式和历史记录都不受破坏。

## 5. 词本是下一阶段，先把记录规则留好

计划用本机 MySQL，通过 JDBC 连接，当前程序还没接好。连接配置留在本机，之后还需要补建表、初始化和备份说明。

只有成功显示的划词翻译才算一次“阅读求助”；以后输入法明确提交的候选才算“输入求助”。失败、取消、普通键入、候选曝光、轮播展示和动效事件都不记次数。

每个词目想保留词或连续词组、语言、简短对应释义、阅读和输入次数、最后求助时间、复习权重和状态。每次求助另外记时间、来源、提交方式和唯一操作标识，方便去重和重新统计。

大小写和首尾空格可以规范化，不随便拆开词组；同样拼写的不同义项不要直接合并，中文和英文也不能因为互为翻译就自动并成一条。以后允许手动更正释义、合并重复词目和标记已掌握。

词本希望能搜索、查看历史，看最近 7 天和累计求助最多的内容，也能区分阅读与输入。查得多只能说明求助多，不能直接叫熟练度。

## 6. 空闲时复习，先留作后续计划

之前想的是新翻译保留一段时间，暂定默认 1 分钟，时间可调。计时从成功显示新翻译开始，失败、空选区和重复回调不重置。到时间没有新结果，再轮播已有词目；新的成功翻译一到，就停止轮播并重新计时。

现在没有这套计时和轮播，也没有“稍后复习”操作。以后可以暂停或关闭轮播，关闭后继续留着最后译文还是进入空闲，再通过设置确定。

选词不能只看累计次数，还要考虑最近求助、阅读或输入来源、近期是否展示。频繁且最近还在求助的词优先，长时间没查可以降权，同一个词不要连续出现。公式和冷却时间以后试出来再定。

轮播至少显示词或词组和已有的简短对应释义，次数等信息别挤满小窗口。以后可以调速度、前后切换、暂时跳过、看历史和标记已掌握。轮播本身不增加求助次数，可以单独记展示时间避免重复。

## 7. 归档和输入法再往后放

归档想做成可选：比如 30 天没再求助，就从待学移到自动归档，保留词目和历史，不直接删除。自动归档不等于已经学会，已掌握也要单独标记；两种状态默认不参加普通轮播，自动归档是否参加可以再设。

归档词又被求助时恢复原词目，保留旧次数，不新建重复词。最近一年归档记录可查看和手动恢复，这个一年只是界面筛选范围，不代表一年后删数据。

输入法很感兴趣，但目前不接入，也不让桌面翻译等它做完。之前约定的候选阶段规则保留在这里：

| 操作 | 希望发生什么 | 是否记录输入求助 |
| --- | --- | --- |
| 普通空格提交 | 按输入法原规则上屏 | 不记 |
| Tab 确认中文候选 | 中文实际上屏 | 成功后记一次 |
| 同时按 Tab＋空格 | 英文候选实际上屏 | 成功后记一次 |
| 直接输入英文或只看候选 | 正常输入 | 不记 |

比如“混凝土／concrete”，Tab 提交中文，Tab＋空格提交英文。组合键必须优先，不能先提交中文或触发划词翻译再补处理英文。没有有效候选、候选没上屏都不记账。

候选阶段优先于划词翻译，普通键入不存日志。候选先用有合法授权的本地词库，不在每次拼音按键时把内容发到网上；在线补充要用户明确触发。

阅读与输入最后汇进同一本本地词本。输入事件默认不打断当前翻译显示；要不要短暂展示刚上屏的词，之后再定。中文已经上屏后替换成英文也还没决定。接入方式待验证，目前不依赖 Rime；主程序关闭时输入事件怎么可靠交接也要在实现前验证。

## 8. 数据和在线服务

词本默认只保留明确求助的词或词组、必要简短释义、事件和状态，不保存整句、完整 PDF、网页正文、窗口标题、应用历史、普通键入、未提交候选或密钥。

不能为了取一次选区就持续截屏、持续读取剪贴板或收集其他应用文字。临时访问的内容只用于这次操作，动效信号也不落盘。

目前在线翻译已接入 DeepSeek，API Key 在设置中保存，并为当前 Windows 用户加密。服务说明、启用前的发送范围提示和关闭在线翻译的入口还需要补。

以后加自动翻译时，要说明完成划选就可能上传选区，并允许切回按键确认。不要拿密码、个人敏感信息或保密材料测试。仓库不放真实词本、课程 PDF、剪贴板内容和密钥，演示用自造样例。

图片、图标、代码和词库都要保留对应来源及许可证，仅写来源不一定就能随意分发。

## 9. 接下来按什么顺序做

先解决请求先后顺序和失败提示，补选区、Tab 冲突及焦点的兼容测试；再做 JDBC 存词和真正的词本界面；最后才是复习权重、归档、输入法和安装发布。已经接上的在线服务继续完善配置与错误处理，不再列成尚未开始的选型任务。

当前阶段要实际检查：

- 浏览器、文字型 PDF、记事本和 IDE 的读取能力，单词和连续词组都试，失败时不破坏输入、焦点或剪贴板。
- 置顶、拖动、位置恢复、原文开关、字号和偏好保存；还要试多显示器、缩放、全屏和任务栏附近。
- 连续查词、取消选区、断网和接口失败，窗口只显示该显示的结果。
- 键鼠动效不影响输入，长按不积压，关闭和退出释放监听。

独立界面程序已经通过过 44 项检查，具体范围看 [界面检查笔记](../design-qa.md)。这不等于上面所有场景都验收了。

后续词本要检查成功显示才记一次、重复回调去重、失败不记、搜索和历史；轮播要检查计时、新翻译打断、展示不增次数和避免重复。归档用模拟时间验证，不必真的等 30 天。

输入法先拿“混凝土 → concrete”一条本地映射，在记事本、浏览器输入框和 IDE 验证候选上屏、组合键优先与去重，再扩大范围。

## 10. 还有一些想法先记着

大段翻译后，也许可以挑出值得学的连接表达、句式和词组特殊用法，或者标出词本里已经有的词。但不能把整段和里面每个单词都自动存进去，也不能只因为出现过就增加求助次数。

要区分“在原文里出现”和“我真的又求助了一次”。哪些片段值得存、是否需要我确认、句式怎样只留必要部分，之后再决定；如果在线分析需要上传更长原文，也要单独说明。

自动翻译怎么判断选完、是否增加复制后备、鼠标穿透、复习时长和速度、输入法候选显示、上屏后的中英替换、词库授权这些都还没最终定。当前先沿用已经选好的小猫和窗口风格，把翻译和词本一步一步做稳。
