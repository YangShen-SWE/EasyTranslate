# Tab触发安全与原生待验矩阵

D07 / 源码v2.1.7，2026-10-17。本文区分离线自动化证据与真实Windows输入证据，不表示所有软件或输入法兼容。没有读取个人选区、安装新输入法、注入按键或调用翻译API。

## 本次修复

原WindowsTabObserverService只过滤Alt与系统消息，没有过滤Shift/Ctrl/Win：例如焦点后退或浏览器标签切换也可能触发翻译。本次在原生钩子通知前使用独立TabTriggerFilter，按如下规则**只决定是否通知翻译**：

- 物理、普通WM_KEYDOWN的Tab，且采样时Shift/Ctrl/Alt/左右Win都没有按下，才接受第一次按下。
- 一次按住只通知一次，收到物理Tab抬起后才重新允许。被组合键/系统消息拒绝的按下也锁定至抬起，避免松开修饰键而仍按住Tab时连发。
- 安装监听前采样到Tab已经按住时，先等抬起；不把本来已按住的键当作新意图。
- 带Windows injected/lower-integrity injected标志的Tab忽略；注入抬起不能重新允许尚在按住的物理Tab。辅助键盘、自动化工具等产生的合成输入因此不支持此触发方式，不是“识别了全部模拟来源”。
- 负hookCode、其他键、未知消息不产生通知。修饰键采样异常交由原观察器的RuntimeException防护，已锁定的Tab不会因重复消息再触发。

Windows适配使用GetAsyncKeyState的高位（当前按下），不是“自上次查询后曾按过”的低位；Shift/Control/Alt用通用键码覆盖任意一侧，左右Win分别查询，Alt上下文标志与WM_SYSKEYDOWN另外拒绝。**此处是采样保护，不是原子快照**：快速事件、安装采样与钩子实际安装之间仍有时间窗口，未证实真实硬件的所有序列。

原回调仍在筛选/通知分支之后统一CallNextHookEx，不返回非零拦截键，不发送替代按键，不阻止原软件Tab焦点遍历/组合键。不修改WindowsInputActivityService/InputActivityBuffer/CompanionMotion的生产逻辑；动效监听可记录“爪子活动位”，没有翻译回调。拒绝翻译的键仍可能播放动效，这是设计行为。接受的翻译通知仍经过既有协调器、在线许可与选区边界；不是每个物理Tab都保证产生可用选区。

被拒绝的组合键不是新翻译意图，不撤回之前已接受的请求/结果。无选区/Unknown/TooLong的新**合格Tab**仍是新意图，会使旧回调失效，但不调用翻译；已在途HTTP仍不能取消/撤回/停止计费。

## 自动化证据的边界

TabTriggerFilterTest用合成事件和假修饰键读数检查生产筛选器。TabTriggerFlowTest用真实筛选器、用例、协调器与InputActivityBuffer、手动通知/工作/UI队列，检查长按至多一次、拒绝事件不捕获、无有效选区不翻译、旧结果失效、动效独立与关闭保护。这不是原生回调、系统按键传递或UIA/API端到端测试；测试中不会安装钩子或运行输入法。相同既有用例/协调器/在线许可测试用于回归共享链路。

NativeInputProbe已扩展为人工离线探针：生产Tab监听只递增计数，另一个动效监听只显示活动位；两个普通输入框用于观察焦点遍历/候选文字是否正常到达。探针不读选区、不构造翻译服务、不读个人配置、无输入内容日志；输入仅留在临时控件内，关闭即丢弃。**本次仅编译，没有人工启动/操作或验收此探针。**

## 当前安全用法与未解决项

候选态中的**无修饰Tab**仍可能被当成翻译意图，本次没有可靠的跨进程IME候选检测，不宣称冲突解决。使用输入法候选、频繁Tab补全/焦点遍历或其他敏感场景时，先在设置中**关闭在线翻译**，确认显示关闭状态；D05的许可守卫阻止后续选区捕获/请求，动效仍可继续。关闭不能撤回此前已准入的请求。真正停止全局翻译监听可退出主程序；实际主进程退出/原生释放仍留D10实机验收，不能以单元测试替代。

没有实测足以选择一个全系统更安全的新快捷键，本次不擅自把默认改为另一组组合键，也不假称提供了候选安全的替代触发。若下述实测仍冲突，应先保持在线关闭，后续在有证据时增加显式的可关闭/可配置独立触发；默认变更和持久化体验另行验收，不引入输入法接管或模拟复制。

## 原生手动验收（全部待验证）

只在用户主动选择的自造文本/离线探针中执行；不要在密码框、私密文档或真实服务开启时操作。每行记录日期、Windows/键盘/输入法版本、原应用、动作、通知增量、焦点/字符是否正常和实测结论。没有人工证据的行不得改为通过。

| 场景 | 安全操作与预期 | 本次状态 |
|---|---|---|
| 单次Tab/长按 | 探针第一个框按Tab一次、按住约2秒再抬起：各只增加1通知；原焦点遍历/重复行为仍由JavaFX/Windows处理，不要求焦点停留原位 | 待验证；只有合成事件通过 |
| Shift+Tab | 从第二框按组合键，通知不增加，焦点正常后退；按住Tab时松Shift不产生通知，完全抬起后新Tab增加1 | 待验证；只有假读数通过 |
| Ctrl+Tab / Ctrl+Shift+Tab | 使用无私密数据的浏览器标签/IDE标签，通知不增加，原标签切换照常 | 待验证 |
| Alt+Tab / AltGr+Tab | 系统窗口切换不拦截/不翻译；AltGr按键布局下单独核对Ctrl/Alt采样 | 待验证 |
| Win+Tab | 任务视图正常，通知不增加；左右Win分别人工核对 | 待验证 |
| 监听启动前Tab已按住 | 使用可控启动方式确认不会因首次重复触发，抬起再按才增加通知；不在敏感窗口操作 | 待验证；只测初始化参数分支 |
| 无有效选区 | 真正应用保持在线关闭以免外发；离线合成链已证实None/Unknown/TooLong不翻译，原生取值正确性另见选区矩阵 | 待验证；不因无选区假定一定安全 |
| 中文输入法候选 | 在线关闭，在探针/自造临时编辑器输入候选并按Tab、Shift+Tab、确认/取消；字符/候选/焦点不能被劫持；观察裸Tab计数但不据此称支持 | 待验证；候选识别未实现 |
| 动效开关与触发 | 同样的合格Tab在动效启用/禁用时翻译通知规则一致；其他键/鼠标只影响活动位、不触发翻译 | 待验证；仅离线组合测试 |
| 合成/辅助输入 | 注入标志事件不通知，但原输入仍可到达；辅助设备若无标志须另行记录，不保证可区分 | 待验证；本次不主动注入系统按键 |

### 如何运行人工探针

先在模块目录用JDK25编译并生成类路径：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4-full'
./mvnw.cmd -q test-compile dependency:build-classpath '-Dmdep.outputFile=target/ui-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Probe compile failed' }
$probeDependencies = (Get-Content 'target/ui-classpath.txt' -Raw).Trim()
& "$env:JAVA_HOME/bin/java.exe" '--enable-native-access=javafx.graphics,com.sun.jna' '--module-path' $probeDependencies '--add-modules' 'javafx.controls,javafx.fxml,com.sun.jna,com.sun.jna.platform' '-cp' 'target/classes;target/test-classes' 'com.easytranslate.view.NativeInputProbe'
if ($LASTEXITCODE -ne 0) { throw 'Probe exited with failure' }
```

这是**待用户主动执行的命令**，不是本次执行结果。启动会安装两个临时全局观察器，不记录按键内容，窗口关闭释放；不后台常驻。不要同时运行另一个探针/主程序来混淆结果；若启动报错先记录，不修改系统策略或绕过权限。只启动探针不做动作不算验收通过。

关联：[选区兼容矩阵](selection-compatibility.md)、[核心测试](core-tests.md)、[v2.1.7说明](releases/v2.1.7.md)。
