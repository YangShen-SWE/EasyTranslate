# Core tests / 核心增量测试

## What these tests check / 测试范围

The core JUnit tests run without starting JavaFX, Windows hooks, UI Automation, or a real translation API. They use in-memory services through the existing interfaces. They do not prove that selection capture or real API requests work on a user's computer.

核心JUnit测试使用 SelectedTextService / TranslationService 接口注入内存假服务，直接验证真实 TranslateSelectionUseCase 与 LatestTranslationCoordinator。不启动 JavaFX、全局监听、Windows UI Automation，不读取个人配置或密钥，也不联网调用翻译 API。D01仅建立测试基线；D02加入生产请求协调逻辑，不能再把基线范围当成当前范围。

- LatestTranslationCoordinatorTest：13个JUnit测试方法，使用真实协调器与用例，控制工作/UI队列及有上限闩锁。覆盖A/B服务返回逆序、旧成功/旧失败晚到、UI已排队后再触发、None/Unknown使旧意图失效、身份先于异步读取分配、读取期间失效不再开始旧翻译、最新成功/失败，以及关闭前排队/关闭后服务返回/后续触发。没有把循环中的输入条数当成额外测试数。
- TranslateSelectionUseCaseTest：Found只读取/翻译一次并传递文本，None/Unknown不调用翻译，读取/翻译异常保持失败，工作离开调用线程，读取未结束时execute仍返回Future。7个JUnit测试方法；使用有上限的Future/闩锁等待，不靠sleep碰运气。原execute()兼容入口仍默认异步，不注入直接执行器来伪称异步通过。
- SelectionResultTest：Found去除首尾Unicode空白、保留内部空格/换行、拒绝空/空白及非法状态数据，None/Unknown不含文本。8个JUnit测试方法；部分方法包含多个输入断言，输入条数不当成额外测试数。
- FloatingWindowSmokeTest：原有独立JavaFX main检查器，不是JUnit用例；UI的44/57项历史检查数不能与核心JUnit测试数相加。
- NativeInputProbe：原生输入观察探针，不证明所有应用、组合键或输入法候选态均已通过。

## Run only affected tests / 只运行受影响测试

在项目的 EasyTranslate 模块目录运行（仓库根与模块根不同）。先指定你实际安装的 JDK 25；本机此次核验路径为 C:\Program Files\Java\jdk-25.0.4-full。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4-full'
./mvnw.cmd '-Dtest=LatestTranslationCoordinatorTest,TranslateSelectionUseCaseTest,SelectionResultTest' test
```

只改选区结果契约时，可缩小为：

```powershell
./mvnw.cmd '-Dtest=SelectionResultTest' test
```

只改请求协调器时至少执行 LatestTranslationCoordinatorTest；改翻译用例时同时执行 TranslateSelectionUseCaseTest 与协调器测试；跨选区/用例边界时运行上面的三类。D02实际执行28项：13+7+8，失败/错误/跳过均为0。

协调器在Tab通知进入trigger时分配意图编号，编号闭包贯穿读取、服务与UI发布；即使新意图没有有效文本，也先使旧意图失效。工作开始前及选区读取结束后检查当前编号；UI真正执行时在同一锁内复查编号并提交成功/错误。主窗口WINDOW_HIDING和Application.stop都关闭协调器。旧execute()入口保持兼容，但应用入口应使用协调器，不能绕过它再直接发布结果。

这不是HTTP取消器：已在途服务仍可能执行/计费；检查通过到真正开始服务之间也存在并发窗口，不能保证过时请求完全不发送。选区仍在后台捕获，编号证明通知意图顺序，不证明捕获的是按键瞬间的选区。None/Unknown仍保留之前展示，清晰的本次状态/旧译文标记留给D03。

pom显式使用Surefire 3.5.4运行JUnit 5，测试JVM使用classpath，避免为了测试去扩大生产模块exports/opens；生产编译目标和module-info保持原样。failIfNoTests=true，不允许空测试集当作成功。

需要联网下载的仅是缺失Maven依赖，并非翻译服务。默认缓存在用户的.m2目录；若沙箱限制写入，先申请原测试命令所需权限，不修改系统ACL、不偷偷绕过。不要把插件解析/缓存权限失败当成业务断言失败。

## Evidence and expansion / 证据与扩大范围

- 核对命令退出码、Surefire结果中的 Tests run / Failures / Errors / Skipped，以及 target/surefire-reports 对应类的报告。成功必须是真正执行了JUnit测试且失败/错误为0；仅test-compile或Tests run: 0不是通过。
- Maven可能增量编译所有受影响测试源（包括原UI检查器），但本命令只执行指定JUnit测试，不启动UI检查器，不使用clean。
- 修改FXML、控制器、窗口或焦点行为时，再按需运行 scripts/check-window.ps1 -JavaHome <实际JDK路径>，并单独记录UI结果。D02因应用入口/UI回调接线改变，新增6项协调器与真实FX队列/文本绑定检查并执行现有窗口检查，输出UI_SMOKE_PASSED: 63 checks（独立main，不计入28项JUnit）。
- 本机直接执行.ps1被Windows执行策略拒绝时，不更改执行策略或添加Bypass。D02先直接执行脚本中相同的Maven test-compile / dependency:build-classpath生成target/ui-classpath.txt，再将其内容作为--module-path，按脚本同样的参数直接启动FloatingWindowSmokeTest；最终退出码0。脚本启动被拒绝不冒充脚本成功，也不把test-compile算JUnit。可复核启动参数见本版本说明。
- 修改共享配置/模块依赖/构建系统等横切链路时扩大关联回归；最后一天按累计diff回归关键路径，不要求每次无差别全量执行。
- Maven 3.8.5在JDK25上可能给出Jansi native-access及Guava Unsafe弃用警告。记录它们，不据此谎报测试失败，也不随意给测试增加无关权限开关。

真实DeepSeek API、实际选区捕获、输入法、焦点、多屏与干净安装仍需独立验证。核心测试通过不代表这些已验证。
