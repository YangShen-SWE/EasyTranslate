# Core tests / 核心增量测试

## What these tests check / 测试范围

The core JUnit tests run without starting JavaFX, Windows hooks, UI Automation, or a real translation API. They use in-memory services through the existing interfaces. They do not prove that selection capture or real API requests work on a user's computer.

核心JUnit测试使用 SelectedTextService / TranslationService 接口注入内存假服务，直接验证真实 TranslateSelectionUseCase 与 LatestTranslationCoordinator。不启动JavaFX工具包、全局监听、Windows UI Automation，不读取个人配置或密钥，也不联网调用翻译API。FloatingViewModelTest仅使用JavaFX属性，不启动窗口。D01建立测试基线，D02加入生产请求协调逻辑，D03扩展本次操作状态与保留结果标记；不能把历史基线范围当成当前范围。

- LatestTranslationCoordinatorTest：20个JUnit测试方法，使用真实协调器与用例，控制工作/UI队列及有上限闩锁。保留D02的13项逆序/旧失败/空选区/关闭回归；D03新增7项读取→翻译→成功顺序、None/Unknown不同终态、选区异常、服务失败、过时进度/终态、关闭后所有状态丢弃、工作执行器拒绝后的失败。A/B在途逆序测试同时验证旧回调不能覆盖新状态。没有把循环中的输入条数当成额外测试数。
- TranslateSelectionUseCaseTest：Found只读取/翻译一次并传递文本，None/Unknown不调用翻译，读取/翻译异常保持失败，工作离开调用线程，读取未结束时execute仍返回Future。7个JUnit测试方法；使用有上限的Future/闩锁等待，不靠sleep碰运气。原execute()兼容入口仍默认异步，不注入直接执行器来伪称异步通过。
- SelectionResultTest：Found去除首尾Unicode空白、保留内部空格/换行、拒绝空/空白及非法状态数据，None/Unknown不含文本。8个JUnit测试方法；部分方法包含多个输入断言，输入条数不当成额外测试数。
- FloatingViewModelTest：7个JUnit方法，仅用JavaFX属性不启动工具包；验证初始等待、五类非成功状态、保留原文/译文且标上次结果、成功替换/清标记、相同译文再次成功、错误提示恢复以及非法参数不变更展示。
- FloatingWindowSmokeTest：独立JavaFX main检查器，不是JUnit用例；D03实际89项检查，不能与42项核心JUnit测试数相加。
- NativeInputProbe：原生输入观察探针，不证明所有应用、组合键或输入法候选态均已通过。

## Run only affected tests / 只运行受影响测试

在项目的 EasyTranslate 模块目录运行（仓库根与模块根不同）。先指定你实际安装的 JDK 25；本机此次核验路径为 C:\Program Files\Java\jdk-25.0.4-full。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4-full'
./mvnw.cmd '-Dtest=LatestTranslationCoordinatorTest,TranslateSelectionUseCaseTest,SelectionResultTest,FloatingViewModelTest' test
```

只改选区结果契约时，可缩小为：

```powershell
./mvnw.cmd '-Dtest=SelectionResultTest' test
```

只改请求协调器时至少执行 LatestTranslationCoordinatorTest；改翻译用例时同时执行 TranslateSelectionUseCaseTest 与协调器测试；跨选区/用例/展示状态边界时运行上面的四类。D02历史执行28项：13+7+8；D03执行42项：20+7+8+7，失败/错误/跳过均为0。若只改展示模型，可定向FloatingViewModelTest；控制器或FXML同时变动仍需独立UI检查。

协调器在Tab通知进入trigger时分配意图编号，编号闭包贯穿读取、服务与UI发布；即使新意图没有有效文本，也先使旧意图失效。工作开始前及选区读取结束后检查当前编号；UI真正执行时在同一锁内复查编号并提交成功/错误。主窗口WINDOW_HIDING和Application.stop都关闭协调器。旧execute()入口保持兼容，但应用入口应使用协调器，不能绕过它再直接发布结果。

这不是HTTP取消器：已在途服务仍可能执行/计费；检查通过到真正开始服务之间也存在并发窗口，不能保证过时请求完全不发送。选区仍在后台捕获，编号证明通知意图顺序，不证明捕获的是按键瞬间的选区。D03中None/Unknown分别显示未选中文字/无法读取选区；读取、翻译、失败时同样保留之前展示并标“上次结果”，只有最新成功移除标记。状态与成功/错误同样在UI执行时检查意图编号。UI执行器契约为串行FIFO（生产Platform.runLater），不支持任意乱序线程池。状态标记不证明捕获准确或取消了HTTP。

pom显式使用Surefire 3.5.4运行JUnit 5，测试JVM使用classpath，避免为了测试去扩大生产模块exports/opens；生产编译目标和module-info保持原样。failIfNoTests=true，不允许空测试集当作成功。

需要联网下载的仅是缺失Maven依赖，并非翻译服务。默认缓存在用户的.m2目录；若沙箱限制写入，先申请原测试命令所需权限，不修改系统ACL、不偷偷绕过。不要把插件解析/缓存权限失败当成业务断言失败。

## Evidence and expansion / 证据与扩大范围

- 核对命令退出码、Surefire结果中的 Tests run / Failures / Errors / Skipped，以及 target/surefire-reports 对应类的报告。成功必须是真正执行了JUnit测试且失败/错误为0；仅test-compile或Tests run: 0不是通过。
- Maven可能增量编译所有受影响测试源（包括原UI检查器），但本命令只执行指定JUnit测试，不启动UI检查器，不使用clean。
- 修改FXML、控制器、窗口或焦点行为时，再按需运行 scripts/check-window.ps1 -JavaHome <实际JDK路径>，并单独记录UI结果。D02因应用入口/UI回调接线改变，新增6项协调器与真实FX队列/文本绑定检查并执行现有窗口检查，输出UI_SMOKE_PASSED: 63 checks（独立main，不计入28项JUnit）。
- 本机直接执行.ps1被Windows执行策略拒绝时，不更改执行策略或添加Bypass。D02先直接执行脚本中相同的Maven test-compile / dependency:build-classpath生成target/ui-classpath.txt，再将其内容作为--module-path，按脚本同样的参数直接启动FloatingWindowSmokeTest；最终退出码0。脚本启动被拒绝不冒充脚本成功，也不把test-compile算JUnit。可复核启动参数见本版本说明。
- D03改变展示模型、控制器与FXML状态栏，新增26项UI断言；相同Java入口实际输出UI_SMOKE_PASSED: 89 checks / 退出码0，覆盖真实队列状态、上次结果标记、错误恢复、相同译文再次成功、状态灯及小窗口换行/按钮可见。仅test-compile不能当UI通过，真实剪贴板复制未执行，相关反馈文本仅代码审查。不再次尝试已知被执行策略拒绝的.ps1，不使用Bypass。
- 修改共享配置/模块依赖/构建系统等横切链路时扩大关联回归；最后一天按累计diff回归关键路径，不要求每次无差别全量执行。
- Maven 3.8.5在JDK25上可能给出Jansi native-access及Guava Unsafe弃用警告。记录它们，不据此谎报测试失败，也不随意给测试增加无关权限开关。

真实DeepSeek API、实际选区捕获、输入法、焦点、多屏与干净安装仍需独立验证。核心测试通过不代表这些已验证。
