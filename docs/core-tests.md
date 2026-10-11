# Core tests / 核心增量测试

## What these tests check / 测试范围

The core JUnit tests run without starting JavaFX, Windows hooks, UI Automation, or a real translation API. They use in-memory services through the existing interfaces. They do not prove that selection capture or real API requests work on a user's computer.

核心测试使用现有 SelectedTextService / TranslationService 接口注入内存假服务，直接验证真实 TranslateSelectionUseCase。此次不改生产业务代码，不启动 JavaFX、全局监听、Windows UI Automation，不读取个人配置或密钥，也不联网调用翻译 API。

- TranslateSelectionUseCaseTest：Found只读取/翻译一次并传递文本，None/Unknown不调用翻译，读取/翻译异常保持失败，工作离开调用线程，读取未结束时execute仍返回Future。7个JUnit测试方法；使用有上限的Future/闩锁等待，不靠sleep碰运气。
- SelectionResultTest：Found去除首尾Unicode空白、保留内部空格/换行、拒绝空/空白及非法状态数据，None/Unknown不含文本。8个JUnit测试方法；部分方法包含多个输入断言，输入条数不当成额外测试数。
- FloatingWindowSmokeTest：原有独立JavaFX main检查器，不是JUnit用例；UI的44/57项历史检查数不能与核心JUnit测试数相加。
- NativeInputProbe：原生输入观察探针，不证明所有应用、组合键或输入法候选态均已通过。

## Run only affected tests / 只运行受影响测试

在项目的 EasyTranslate 模块目录运行（仓库根与模块根不同）。先指定你实际安装的 JDK 25；本机此次核验路径为 C:\Program Files\Java\jdk-25.0.4-full。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4-full'
./mvnw.cmd '-Dtest=TranslateSelectionUseCaseTest,SelectionResultTest' test
```

只改选区结果契约时，可缩小为：

```powershell
./mvnw.cmd '-Dtest=SelectionResultTest' test
```

只改翻译用例时，至少执行 TranslateSelectionUseCaseTest；跨选区/用例边界时运行两类，未来有新依赖后加入相关类。未来新增请求协调器，应补其专门乱序测试，而不是把当前用例通过写成乱序已经修复。

pom显式使用Surefire 3.5.4运行JUnit 5，测试JVM使用classpath，避免为了测试去扩大生产模块exports/opens；生产编译目标和module-info保持原样。failIfNoTests=true，不允许空测试集当作成功。

需要联网下载的仅是缺失Maven依赖，并非翻译服务。默认缓存在用户的.m2目录；若沙箱限制写入，先申请原测试命令所需权限，不修改系统ACL、不偷偷绕过。不要把插件解析/缓存权限失败当成业务断言失败。

## Evidence and expansion / 证据与扩大范围

- 核对命令退出码、Surefire结果中的 Tests run / Failures / Errors / Skipped，以及 target/surefire-reports 对应类的报告。成功必须是真正执行了JUnit测试且失败/错误为0；仅test-compile或Tests run: 0不是通过。
- Maven可能增量编译所有受影响测试源（包括原UI检查器），但本命令只执行指定JUnit测试，不启动UI检查器，不使用clean。
- 修改FXML、控制器、窗口或焦点行为时，再按需运行 scripts/check-window.ps1 -JavaHome <实际JDK路径>，并单独记录UI结果。
- 修改共享配置/模块依赖/构建系统等横切链路时扩大关联回归；最后一天按累计diff回归关键路径，不要求每次无差别全量执行。
- Maven 3.8.5在JDK25上可能给出Jansi native-access及Guava Unsafe弃用警告。记录它们，不据此谎报测试失败，也不随意给测试增加无关权限开关。

真实DeepSeek API、实际选区捕获、输入法、焦点、多屏与干净安装仍需独立验证。核心测试通过不代表这些已验证。
