# Core tests / 核心增量测试

## D12镜像与离线入口（2026-10-22提前）

最终`./mvnw.cmd '-Dtest=DesktopLauncherTest,AppImageVerifierTest,BuildArtifactVerifierTest,OnlineTranslationPolicyTest,ApiKeyStoreTest' package`：55JUnit，失败/错误/跳过0，BUILD SUCCESS / 退出0（13:25:54+02:00），五份报告已读取。入口3项（默认委托/非法参数不启动/非打包诊断拒绝）、镜像16项（启动配置/版本/模块/DLL/禁临时解包/拒混FX/诊断输出及运行依赖筛选）、归档13、在线许可10、假密钥13；循环不是额外方法。扩大到首用配置/密钥边界，但不冒充真实进程/干净机持久化。

之后真实jlink/jpackage --type app-image、镜像静态门禁、随包EXE --diagnose成功，APP_IMAGE_VERIFIED / 退出0。实际Java25.0.4+9-LTS、JavaFX25.0.4、JNA5.18.1、源码/入口2.1.12；搬迁到含中文空格路径且JAVA_HOME无效/PATH无外置Java后诊断仍退出0。单一系统JavaFX，排除Maven FX jar，实际加载随包JNA DLL；不运行FX主窗、全局钩子、HTTP、个人配置或真实密钥。该独立镜像诊断不计入55JUnit。

首轮镜像启动失败暴露JNA java.logging依赖及临时解包拒绝访问；加入日志模块拒绝测试、显式随包DLL与jna.nounpack/nosys门禁后重建通过。未禁断言/改系统权限/伪称临时目录问题已修复。未改既有控制器/FXML/CSS/主应用业务接线，未重跑D10独立FX171/52/40，不拿历史结果充本次UI。完整首用、卸载保留/干净Windows待验见[安装首用指南](installation-first-use.md)与[v2.1.12](releases/v2.1.12.md)。

## D11构建门禁（2026-10-22补跑）

`./mvnw.cmd '-Dtest=BuildArtifactVerifierTest' package`最终13项JUnit，失败/错误/跳过0，退出0 / BUILD SUCCESS（00:38:09+02:00），Surefire文本报告已读取。仅构建测试，不启动FX/原生/HTTP/个人配置：JDK工具创建真实微型模块描述符，验证文件名/内嵌坐标/Manifest/资源/旧模块版本拒绝，以及显式模块版本准备和拒绝修改旧坐标包。初轮测试夹具直接引用javax.tools导致java.compiler模块不可见，改为java.base的ToolProvider查找JDK javac，不扩大生产module-info；编译失败不算断言失败或通过。

真实package之后运行显式`--stamp-module-version`准备，再独立只读校验，均退出0；JAR模块描述与文件名/Manifest/Maven元数据一致2.1.11。发现增量编译仅改pom不更新module-info.class的真实偏差，默认门禁拒绝，显式准备只给坐标等已验证归档写模块版本，不伪造其他元数据。步骤和环境证据见[构建检查](build-verification.md)与[v2.1.11](releases/v2.1.11.md)。13项不是完整业务回归/运行镜像证明。D11无生产Java/FXML/CSS变动，未重跑D10窗口171/键盘52/偏好40，不把历史通过算本次UI。完整JDK内置JavaFX25.0.4会优先于Maven声明21.0.6：既有本机FX证据不应标成实际运行21.0.6，依赖声明未改。

## D10补跑（2026-10-22）

WindowGeometryTest 9项、HotkeySessionTest 6项，加TabTriggerFilterTest 18、TabTriggerFlowTest 9、LatestTranslationCoordinatorTest 24、OnlineTranslationCoordinatorTest 9，共75JUnit，失败/错误/跳过0。假屏幕矩形和假原生会话，不等于实机拔插/混合DPI或真实hook压力测试。窗口独立main本次171（新增位置6）、键盘52、偏好40均退出0，各自独立不合计；命令与边界见[v2.1.10](releases/v2.1.10.md)。历史数字保持原执行含义。

## What these tests check / 测试范围

The core JUnit tests run without starting JavaFX, Windows hooks, UI Automation, or a real translation API. They use in-memory services through the existing interfaces. They do not prove that selection capture or real API requests work on a user's computer.

核心JUnit测试使用 SelectedTextService / TranslationService 接口注入内存假服务，直接验证真实 TranslateSelectionUseCase 与 LatestTranslationCoordinator。D04的DeepSeekTranslationServiceTest进一步在真实服务内注入假HTTP传输与假密钥提供器，验证请求构造、响应解析与错误分类，不创建真实HTTP客户端。不启动JavaFX工具包、全局监听、Windows UI Automation，不读取个人配置或密钥，也不联网调用翻译API。FloatingViewModelTest仅使用JavaFX属性，不启动窗口。D01建立测试基线，D02加入生产请求协调逻辑，D03扩展状态与保留标记，D04分类在线失败，D05扩展知情联网与版本化许可，D06增加选区超长拒绝与本地协议解码，D07增加纯Tab筛选与离线触发链，D08增加自窗口通知排除与独立FX键盘/焦点检查，D09增加外观配置安全回退/flush确认、密钥持久化与独立FX重建检查；不能把历史基线范围当成当前范围。D06的WindowsSelectedTextServiceTest不启动PowerShell/UIA，只验证真实Java解码器和生成脚本文本的结构契约，不能当作真实Windows提供者/焦点实测。D05配置/请求/协调器测试仅使用临时Preferences节点（用后删除）和自造内容，不读取个人配置/凭据；请求测试仍不创建真实HTTP客户端。

- AppearancePreferencesTest：11个JUnit方法，完全内存Preferences后端，验证6项既有外观键保存后flush/假后端重建、缺失默认值、损坏逐项回退而不覆盖原数据、字号16–26/窗口档位、可观察读取失败、put/flush失败不报保存成功及重试。另覆盖AbstractPreferences吞掉SPI读异常返回默认值时只能安全回退，不能辨识为错误。
- ApiKeyStoreTest：13个JUnit方法，内存Preferences+假保护算法（不是DPAPI安全证明）；验证假密钥保护/严格UTF-8读取、flush后假后端重建/删除、空输入、损坏Base64/解密失败/空明文、读/写/flush异常与尽力回滚重试。保护算法仅包级测试注入，生产公开构造器仍走Windows DPAPI，不读个人配置/密钥。密钥缺失与被后端吞掉的读异常无法总是区分，均不发明密钥。
- TabTriggerFilterTest：18个JUnit方法（D07为14，D08新增4）；新增假前台禁止、切窗后仍按住不连发、每次新按下采样一次、前台查询失败锁定与组合键不查询；合成事件/假修饰键读数，覆盖物理Tab单次/长按、五类修饰键、Alt上下文/系统消息、注入标志、初始已按住、负hookCode、非Tab/未知消息、异常读数及重新允许。循环不累计为测试方法。没有调用User32或安装钩子，不证明真实修饰键采样/输入法候选行为。
- TabTriggerFlowTest：9个JUnit方法（D07为7，D08新增2）；新增自身窗口Tab不通知/捕获/发送且外部新按下恢复、内部遍历不使已接受外部结果失效；真实筛选器+用例+协调器+InputActivityBuffer，手动通知/工作/UI队列与假选区/服务。覆盖长按一次、组合键拒绝不捕获、None/Unknown/TooLong不翻译、新空选区丢弃旧UI成功、拒绝的组合键不撤销已接受意图、动效位独立以及协调器关闭。此为离线组合证据，不是原生按键传递/真实动效钩子证据。
- OnlineTranslationPolicyTest：10个JUnit方法，临时Preferences验证默认关闭、明确同意/拒绝后重建恢复、损坏/其他说明版本不启用、关闭及重新开启使旧许可失效、禁止晚到准入、等待中响应不阻塞关闭，以及配置读取/保存异常时当前进程保持关闭。重建对象不等于已做干净安装/真实进程重启。
- WindowsSelectedTextServiceTest：11个JUnit方法，真实本地协议解码覆盖999/1000完整、1001/更大拒绝、空白/None/Unknown、UTF-16补充字符、多行Unicode、损坏Base64/UTF-8；脚本文本契约检查1001探测、长度拒绝先于编码、父节点前台归属/输出前句柄复查、多段拒绝。后面三项是静态结构检查，不是脚本实际运行或跨应用通过。
- OnlineDeepSeekTranslationTest：9个JUnit方法，真实DeepSeek请求/解析逻辑配异步假HTTP与假密钥；默认/拒绝不读密钥或发送，同意仅一次发送，准备期间关闭/重开旧许可不再准入，等待响应不阻塞关闭，异步超时保持D04分类；真实服务+用例+协调器联动验证旧HTTP结果不能在重开后发布、需新触发恢复；D06新增超长选区不加载密钥/不准入传输，下一次完整1000单位文本恢复。不创建真实HTTP客户端或服务端。
- OnlineTranslationCoordinatorTest：9个JUnit方法，控制工作/UI队列与有上限闩锁；覆盖拒绝不捕获/不调服务、开启不自动发送、读取前/中关闭、已排队成功/失败、尚未通知协调器时许可已失效，以及在途成功/失败在重开后仍丢弃。
- DeepSeekTranslationServiceTest：17个JUnit方法，注入内存HttpResponse/传输及假密钥；覆盖文本/密钥前置、密钥读失败与非法请求头、成功请求契约、401/403、429、5xx及其他状态、网络/超时/中断、空内容、损坏JSON/尾随JSON、结构缺失/错误与非文本内容。计数验证每次服务调用至多一次send，下一次手动调用重新加载密钥并能恢复；不启动本地HTTP服务器或调用真实服务。
- TranslationFailureTest：7个JUnit方法；本地文案互异、异步包装保留分类而非最深层诊断、未知异常安全回退、不信任原始消息前缀、null、过深包装有界回退及非法kind。
- LatestTranslationCoordinatorTest：24个JUnit测试方法，使用真实协调器与用例，控制工作/UI队列及有上限闩锁。保留D02/D03/D04的22项；D06新增超长新意图使在途旧成功/失败失效、超长状态不调用翻译且下一次短选区恢复。旧状态已排队/关闭回归也扩展TooLong输入。没有把循环中的输入条数当成额外测试数。
- TranslateSelectionUseCaseTest：10个JUnit方法，保留原7项异步/异常/None/Unknown行为；D06新增超长结束且不发TRANSLATING/不调服务、999/1000完整传递一次、确认空选区发布NO_SELECTION而非成功。使用有上限的Future/闩锁等待，不靠sleep碰运气。原execute()兼容入口仍默认异步。
- SelectionResultTest：13个JUnit方法，保留原8项空白与状态契约；D06新增999/1000允许、1001拒绝不留前缀、直接构造不可绕过、原始空白/补充字符长度、TooLong不含文本。非Found契约同时覆盖新状态。循环输入不当成额外测试数。
- FloatingViewModelTest：8个JUnit方法，仅用JavaFX属性不启动工具包；保留原7项展示/恢复行为，新增超长原因、UTF-16上限与未发送文案、保留结果和下次成功恢复。
- FloatingWindowSmokeTest：独立JavaFX main检查器，不是JUnit用例；D03历史89、D04历史129、D05历史153、D06历史165项检查。D07未改生产控制器/FXML/窗口/FX焦点代码，未重跑此入口。D08改动这些链路后重新实际运行：UI_SMOKE_PASSED: 165 checks / 退出码0；独立main不得与JUnit相加。
- FloatingKeyboardSmokeTest：D08新增独立JavaFX main，实际KEYBOARD_SMOKE_PASSED: 52 checks / 退出码0；真实FXML/场景/皮肤、合成本地KeyEvent检查前进/后退、禁用/隐藏跳过、Space复制、局部快捷键、长文翻页、辅助窗口Esc与主窗返回、自造源窗口在状态/结果/失败及resize时保留焦点/选区。临时Preferences与假剪贴板写入器；不安装钩子/调用User32、不读写真实剪贴板或个人配置，不等同跨进程/物理键盘/IME验证。初次运行因TextField首次聚焦会调整选区而失败，修正为聚焦后建立并先断言哨兵选区；保留发布后不变断言，再次运行通过，未跳过检查。
- FloatingPreferencesSmokeTest：D09新增独立FX main，实际PREFERENCES_SMOKE_PASSED: 40 checks / 退出码0。真实FXML、控件、Stage与生产DPAPI仅处理自造假密钥，完全内存Preferences后端，验证六项外观更改/重建、损坏回退、保存/删除失败及重试、清空密码输入、保存不清除先前失败/上次结果、不自动同意联网。假后端重建+Stage重建不等于真实进程重启/注册表磁盘持久化测试；真实个人配置/密钥不触碰。D09也重跑窗口165与键盘52，不复用D08历史通过冒充本次结果。
- NativeInputProbe：D07扩展了两个输入框、合格Tab通知次数和原有动效位，供人工核对原输入/焦点遍历。没有选区/翻译/持久化；D07/D08仅test-compile编译，未启动、未做真实按键或输入法操作，不证明原生通过。D08诊断显式使用false关闭生产默认的同进程排除，才能在自身输入框计数；主程序继续用默认true。详见[Tab安全待验矩阵](tab-trigger-safety.md)。

## Run only affected tests / 只运行受影响测试

在项目的 EasyTranslate 模块目录运行（仓库根与模块根不同）。先指定你实际安装的 JDK 25；本机此次核验路径为 C:\Program Files\Java\jdk-25.0.4-full。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4-full'
./mvnw.cmd '-Dtest=AppearancePreferencesTest,ApiKeyStoreTest,OnlineTranslationPolicyTest,DeepSeekTranslationServiceTest,OnlineDeepSeekTranslationTest,TranslationFailureTest,OnlineTranslationCoordinatorTest,FloatingViewModelTest' test
```

只改选区结果契约时，可缩小为：

```powershell
./mvnw.cmd '-Dtest=SelectionResultTest' test
```

上面的八类为D09实际定向范围：11+13+10+17+9+7+9+8=84，失败/错误/跳过均为0，退出码0 / BUILD SUCCESS（2026-10-19T18:07:27+02:00），八份Surefire文本报告已读取。首轮82项中有2个读取失败断言失败：定位到AbstractPreferences.get会吞掉getSpi异常并返回默认值；修正假后端在公开get边界抛出可观察故障，保留原故障断言，另增2项真实SPI吞异常的局限断言。不是删除/放宽断言、重跑碰运气或修改生产逻辑掩盖失败。随后独立FX main输出PREFERENCES_SMOKE_PASSED: 40 checks、UI_SMOKE_PASSED: 165 checks、KEYBOARD_SMOKE_PASSED: 52 checks，整个作业退出码0；不得相加冒充单一测试集。选择配置/密钥/在线准入/HTTP错误/展示相关链路，没有改原生筛选和选区协议；真实FX补足控制器接线与重建恢复验证，非全部端到端证明。命令与限制见[v2.1.9](releases/v2.1.9.md)。

D08历史六类实际定向范围：18+9+10+24+9+8=78，失败/错误/跳过均为0，退出码0 / BUILD SUCCESS（2026-10-18T20:19:08+02:00），六份Surefire文本报告已读取。相同Maven命令还执行dependency:build-classpath生成/复用target/ui-classpath.txt；随后test-compile同步最新焦点样式，按既有Java参数依次运行键盘52与窗口165，两种main检查不能与78项JUnit或彼此累计成同一测试集。选择核心触发回归加展示模型，再执行真实FXML/控制器/焦点路径；未改HTTP/选区协议/持久化格式，不重跑其无关独立类。见[键盘操作与证据边界](keyboard-focus.md)。

D07实际定向五类（不含FloatingViewModelTest）范围：14+7+10+24+9=64，失败/错误/跳过均为0，最终命令退出码0 / BUILD SUCCESS（2026-10-17T14:38:48+02:00）；五份Surefire文本报告已核对。新增21项筛选/组合链，回归43项用例/协调器/在线开关协同。没有改选区解码、模型/FX展示、配置格式、HTTP实现或模块依赖，未重跑这些独立类；不是缩小已有断言或跳过失败。D06实际定向七类为WindowsSelectedTextServiceTest、SelectionResultTest、TranslateSelectionUseCaseTest、LatestTranslationCoordinatorTest、FloatingViewModelTest、OnlineDeepSeekTranslationTest、OnlineTranslationCoordinatorTest，共84项。D01历史15、D02历史28、D03历史42、D04历史68、D05历史95、D06历史84均保留原执行含义；不同增量集合不能相加或解释为测试被删除。

D07筛选器只决定是否发翻译通知，Windows回调仍统一CallNextHookEx，不吞键；GetAsyncKeyState高位采样不是所有修饰键的原子快照。合成事件不能证明系统真实传递或候选兼容，无修饰候选Tab仍不能识别。实际Windows/输入法/快捷键案例全标待验；敏感/候选使用前先在设置关闭在线翻译，已准入请求不能撤回。详见[Tab安全与手动验收](tab-trigger-safety.md)。

只改请求协调器时至少执行LatestTranslationCoordinatorTest；改翻译用例时同时运行用例与协调器，跨选区/展示边界加入SelectionResultTest、WindowsSelectedTextServiceTest、FloatingViewModelTest等相关类。涉及许可/持久化/发送准入时，在协调器回归基础上加入OnlineTranslationPolicyTest、OnlineDeepSeekTranslationTest；涉及HTTP/失败分类时加入DeepSeekTranslationServiceTest、TranslationFailureTest，不靠孤立配置测试代表共享链路通过。只改展示模型可定向FloatingViewModelTest；控制器/FXML或应用回调接线变动仍需独立UI检查。修改原生键盘筛选需TabTriggerFilterTest+TabTriggerFlowTest及协调器/用例回归，同时保留原生待验矩阵；离线通过不能代替人工快捷键/IME验收。

协调器在Tab通知进入trigger时分配意图编号，编号闭包贯穿读取、服务与UI发布；即使新意图没有有效文本，也先使旧意图失效。工作开始前及选区读取结束后检查当前编号；UI真正执行时在同一锁内复查编号并提交成功/错误。主窗口WINDOW_HIDING和Application.stop都关闭协调器。旧execute()入口保持兼容，但应用入口应使用协调器，不能绕过它再直接发布结果。

这不是HTTP取消器：已在途服务仍可能执行/计费；检查通过到真正开始服务之间也存在并发窗口，不能保证过时请求完全不发送。选区仍在后台捕获，编号证明通知意图顺序，不证明捕获的是按键瞬间的选区。D03中None/Unknown分别显示未选中文字/无法读取选区；读取、翻译、失败时同样保留之前展示并标“上次结果”，只有最新成功移除标记。状态与成功/错误同样在UI执行时检查意图编号。UI执行器契约为串行FIFO（生产Platform.runLater），不支持任意乱序线程池。状态标记不证明捕获准确或取消了HTTP。

D05应用入口把同一OnlineTranslationPolicy注入控制器、协调器与真实服务。默认/拒绝不捕获选区、不读取密钥、不发送；每次触发保留版本化许可，关闭和再次开启均使旧许可永久失效。实际sendAsync准入与关闭共用短锁，等待HTTP响应在锁外；关闭后不能新准入，关闭前已准入的请求仍可能发送/完成/计费，不能撤回文本。协调器在真实UI执行时复查意图/许可，关闭后旧状态/成功/错误不发布。选择开启不自动重发，需要新的触发。许可跨越发送边界解决的是知情联网开关的准入竞争，不宣称已解决所有最新意图/选区/关闭的检查间隙。

D09AppearancePreferences只读原有6项外观键；损坏/可观察异常逐项默认，不启动时覆盖原数据，写入经flush后才提示保存。外观保存失败可已改当前缓存，不能保证后端实际未持久化。ApiKeyStore保存/删除成功要求flush；失败尽力恢复旧值但不能保证后端故障下的持久回滚。公开构造器始终DPAPI；storedState的本地解密/可读不代表联网验证，损坏可删除重存。保存不自动同意/发请求、不把错误标成功，删除不撤回在途HTTP，也不关闭已同意的在线开关。AbstractPreferences可能静默吞底层读取故障，此时只能按缺失安全回退，不能承诺逐一诊断。在线同意继续使用独立D05版本化策略；触发规则仍固定，不新增未承诺的快捷键偏好。窗口坐标/多屏修复仍归后续D10，不混入本次外观6键契约。

pom显式使用Surefire 3.5.4运行JUnit 5，测试JVM使用classpath，避免为了测试去扩大生产模块exports/opens；生产编译目标和module-info保持原样。failIfNoTests=true，不允许空测试集当作成功。

需要联网下载的仅是缺失Maven依赖，并非翻译服务。默认缓存在用户的.m2目录；若沙箱限制写入，先申请原测试命令所需权限，不修改系统ACL、不偷偷绕过。不要把插件解析/缓存权限失败当成业务断言失败。

## Evidence and expansion / 证据与扩大范围

- 核对命令退出码、Surefire结果中的 Tests run / Failures / Errors / Skipped，以及 target/surefire-reports 对应类的报告。成功必须是真正执行了JUnit测试且失败/错误为0；仅test-compile或Tests run: 0不是通过。
- Maven可能增量编译所有受影响测试源（包括原UI检查器），但本命令只执行指定JUnit测试，不启动UI检查器，不使用clean。
- 修改FXML、控制器、窗口或焦点行为时，再按需运行 scripts/check-window.ps1 -JavaHome <实际JDK路径>，并单独记录UI结果。D02因应用入口/UI回调接线改变，新增6项协调器与真实FX队列/文本绑定检查并执行现有窗口检查，输出UI_SMOKE_PASSED: 63 checks（独立main，不计入28项JUnit）。
- 本机直接执行.ps1被Windows执行策略拒绝时，不更改执行策略或添加Bypass。D02先直接执行脚本中相同的Maven test-compile / dependency:build-classpath生成target/ui-classpath.txt，再将其内容作为--module-path，按脚本同样的参数直接启动FloatingWindowSmokeTest；最终退出码0。脚本启动被拒绝不冒充脚本成功，也不把test-compile算JUnit。可复核启动参数见本版本说明。
- D03改变展示模型、控制器与FXML状态栏，新增26项UI断言；相同Java入口实际输出UI_SMOKE_PASSED: 89 checks / 退出码0，覆盖真实队列状态、上次结果标记、错误恢复、相同译文再次成功、状态灯及小窗口换行/按钮可见。仅test-compile不能当UI通过，真实剪贴板复制未执行，相关反馈文本仅代码审查。不再次尝试已知被执行策略拒绝的.ps1，不使用Bypass。
- D04应用错误回调使用TranslationFailure.userMessage；新增40项独立UI断言，同一Java入口实际输出UI_SMOKE_PASSED: 129 checks / 退出码0。覆盖12类本地建议/保留结果/非成功灯、420px小窗长建议换行、真实FX队列的超时分类与手动成功恢复。核心测试不证明真实HTTP栈、TLS、服务商状态或费用；本次无应用层自动重试，已在途HTTP仍未取消。
- D05控制器新增常显服务商/选区/网络/费用/拒绝与撤回限制说明、显式同意复选框和拒绝按钮，默认关闭。相同Java入口输出UI_SMOKE_PASSED: 153 checks / 退出码0；比D04多24项（新增ONLINE_DISABLED文案带来3项通用分类回归、21项知情联网检查）。真实FX队列覆盖持久化同意/拒绝、无自动发送、关闭前已排队成功不覆盖上次结果/关闭状态、禁用不捕获，以及重新开启后新触发恢复。UI不证明真实HTTP栈、键盘操作或整机重启已验收。
- D06选区/状态共享边界新增独立FX检查12项；同一Java入口实际输出UI_SMOKE_PASSED: 165 checks / 退出码0。验证超长不调服务、不显示截断前缀、保留结果/非成功灯/复制仍可用、420px小窗长提示换行与设置按钮可见、下一次完整1000单位文本成功。只注入自造选区，不证明真实UIA捕获。跨应用矩阵和焦点风险见[选区兼容待验记录](selection-compatibility.md)，四类原生手动案例全部待验证。
- 修改共享配置/模块依赖/构建系统等横切链路时扩大关联回归；最后一天按累计diff回归关键路径，不要求每次无差别全量执行。
- Maven 3.8.5在JDK25上可能给出Jansi native-access及Guava Unsafe弃用警告。记录它们，不据此谎报测试失败，也不随意给测试增加无关权限开关。

真实DeepSeek API、实际选区捕获、输入法、跨进程/物理键盘焦点、多屏与干净安装仍需独立验证。D08同进程自造源窗口的自动化焦点保持通过，不应扩大解释为全部真实应用无抢焦点。核心测试通过不代表原生已验证。
