# Core tests / 核心增量测试

## What these tests check / 测试范围

The core JUnit tests run without starting JavaFX, Windows hooks, UI Automation, or a real translation API. They use in-memory services through the existing interfaces. They do not prove that selection capture or real API requests work on a user's computer.

核心JUnit测试使用 SelectedTextService / TranslationService 接口注入内存假服务，直接验证真实 TranslateSelectionUseCase 与 LatestTranslationCoordinator。D04的DeepSeekTranslationServiceTest进一步在真实服务内注入假HTTP传输与假密钥提供器，验证请求构造、响应解析与错误分类，不创建真实HTTP客户端。不启动JavaFX工具包、全局监听、Windows UI Automation，不读取个人配置或密钥，也不联网调用翻译API。FloatingViewModelTest仅使用JavaFX属性，不启动窗口。D01建立测试基线，D02加入生产请求协调逻辑，D03扩展状态与保留标记，D04分类在线失败，D05扩展知情联网与版本化许可，D06增加选区超长拒绝与本地协议解码；不能把历史基线范围当成当前范围。D06的WindowsSelectedTextServiceTest不启动PowerShell/UIA，只验证真实Java解码器和生成脚本文本的结构契约，不能当作真实Windows提供者/焦点实测。D05配置/请求/协调器测试仅使用临时Preferences节点（用后删除）和自造内容，不读取个人配置/凭据；请求测试仍不创建真实HTTP客户端。

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
- FloatingWindowSmokeTest：独立JavaFX main检查器，不是JUnit用例；D03历史89、D04历史129、D05历史153、D06实际165项检查；不能与84项定向核心JUnit测试数相加。
- NativeInputProbe：原生输入观察探针，不证明所有应用、组合键或输入法候选态均已通过。

## Run only affected tests / 只运行受影响测试

在项目的 EasyTranslate 模块目录运行（仓库根与模块根不同）。先指定你实际安装的 JDK 25；本机此次核验路径为 C:\Program Files\Java\jdk-25.0.4-full。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4-full'
./mvnw.cmd '-Dtest=WindowsSelectedTextServiceTest,SelectionResultTest,TranslateSelectionUseCaseTest,LatestTranslationCoordinatorTest,FloatingViewModelTest,OnlineDeepSeekTranslationTest,OnlineTranslationCoordinatorTest' test
```

只改选区结果契约时，可缩小为：

```powershell
./mvnw.cmd '-Dtest=SelectionResultTest' test
```

上面的七类为D06实际定向范围：11+13+10+24+8+9+9=84，失败/错误/跳过均为0。覆盖选区契约、用例、协调器、展示与在线服务/许可协同；未改配置持久化/错误分类或HTTP服务实现，未重跑无关的OnlineTranslationPolicyTest、DeepSeekTranslationServiceTest、TranslationFailureTest。数量比D05少是定向集合不同，不表示删掉旧测试。D02历史28、D03历史42、D04历史68、D05历史95；这些历史结果不因当前方法增加而改写，不把多次执行相加。

只改请求协调器时至少执行LatestTranslationCoordinatorTest；改翻译用例时同时运行用例与协调器，跨选区/展示边界使用上面的相关类。涉及许可/持久化/发送准入时，在上面基础上加入OnlineTranslationPolicyTest；涉及HTTP/失败分类时加入DeepSeekTranslationServiceTest、TranslationFailureTest，不靠孤立配置测试代表共享链路通过。只改展示模型可定向FloatingViewModelTest；控制器/FXML或应用回调接线变动仍需独立UI检查。

协调器在Tab通知进入trigger时分配意图编号，编号闭包贯穿读取、服务与UI发布；即使新意图没有有效文本，也先使旧意图失效。工作开始前及选区读取结束后检查当前编号；UI真正执行时在同一锁内复查编号并提交成功/错误。主窗口WINDOW_HIDING和Application.stop都关闭协调器。旧execute()入口保持兼容，但应用入口应使用协调器，不能绕过它再直接发布结果。

这不是HTTP取消器：已在途服务仍可能执行/计费；检查通过到真正开始服务之间也存在并发窗口，不能保证过时请求完全不发送。选区仍在后台捕获，编号证明通知意图顺序，不证明捕获的是按键瞬间的选区。D03中None/Unknown分别显示未选中文字/无法读取选区；读取、翻译、失败时同样保留之前展示并标“上次结果”，只有最新成功移除标记。状态与成功/错误同样在UI执行时检查意图编号。UI执行器契约为串行FIFO（生产Platform.runLater），不支持任意乱序线程池。状态标记不证明捕获准确或取消了HTTP。

D05应用入口把同一OnlineTranslationPolicy注入控制器、协调器与真实服务。默认/拒绝不捕获选区、不读取密钥、不发送；每次触发保留版本化许可，关闭和再次开启均使旧许可永久失效。实际sendAsync准入与关闭共用短锁，等待HTTP响应在锁外；关闭后不能新准入，关闭前已准入的请求仍可能发送/完成/计费，不能撤回文本。协调器在真实UI执行时复查意图/许可，关闭后旧状态/成功/错误不发布。选择开启不自动重发，需要新的触发。许可跨越发送边界解决的是知情联网开关的准入竞争，不宣称已解决所有最新意图/选区/关闭的检查间隙。

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

真实DeepSeek API、实际选区捕获、输入法、焦点、多屏与干净安装仍需独立验证。核心测试通过不代表这些已验证。
