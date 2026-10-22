# 当前开发版：应用镜像、首用与卸载数据

适用：源码v2.1.12及本次本地Windows x64诊断应用镜像；不是GitHub已发布安装器。README下载仍是历史2.1.0，不包含本周期全部修复。不要把下面的默认关闭同意机制套到旧包上。新镜像未发布/签名，不提供伪造的新下载链接。

## 开发者构建（无需安装器/WiX）

在Maven模块目录，选择已核验的完整JDK25.0.4（内置JavaFX25.0.4）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4-full'
./mvnw.cmd '-Dtest=DesktopLauncherTest,AppImageVerifierTest,BuildArtifactVerifierTest,OnlineTranslationPolicyTest,ApiKeyStoreTest' package dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/runtime-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'package failed' }
& "$env:JAVA_HOME/bin/java.exe" -cp target/test-classes com.easytranslate.build.BuildArtifactVerifier --stamp-module-version
if ($LASTEXITCODE -ne 0) { throw 'JAR gate failed' }
& "$env:JAVA_HOME/bin/java.exe" --add-modules javafx.controls -cp target/test-classes com.easytranslate.build.AppImageBuilder $env:JAVA_HOME target/runtime-classpath.txt
if ($LASTEXITCODE -ne 0) { throw 'image gate failed; inspect the latest app-image work directory diagnostic.txt' }
```

工具从当前pom读取版本，每次在target/app-image-<版本>-<UUID>新目录工作，不覆盖/删除旧产物，不调用clean。输入只有当前已验JAR与三份Jackson、两份JNA运行依赖；拒绝JUnit/未知/重复依赖。Maven JavaFX jar明确排除，用该JDK的jmods执行jlink，避免两套同名JavaFX。明确加入JNA实际需要的java.logging，以及FX/HTTP/Prefs/XML/本地化/加密/unsupported闭包；随后jpackage --type app-image，主入口DesktopLauncher，app-version同pom。

当前是便于诊断的console启动器，不是最终静默GUI安装器；普通无参启动才进入原主界面。依赖在应用classpath，JavaFX只在自带runtime的系统模块，不沿用混合的开发module-path。没有升级pom依赖；此镜像实际JavaFX25.0.4，不代表Maven21.0.6独立环境通过。jlink生成legal内容；再分发前仍须审核完整JDK/JavaFX/第三方许可、native依赖与签名，不能以本地构建成功代替发布合规。

## 不碰真实配置的安装诊断

构建器实际启动生成的EasyTranslate.exe --diagnose，并要求退出0及版本/Java/JavaFX/JNA输出匹配；同时核对启动配置、当前JAR元数据、必需运行模块、拒绝混入JavaFX jar。它会读取随包FXML/CSS存在性、载入依赖类和JNA native库。构建器从已解析的JNA jar内固定win32-x86-64资源提取jnidispatch.dll到镜像app/native；启动配置固定jna.boot.library.path为$APPDIR/native，设置jna.nounpack=true、jna.nosys=true，禁止默认临时解包/系统库搜索，构建时核对复制前后DLL及应用JAR字节一致。它不载入FXML控制器、不创建HTTP客户端、不安装全局钩子、不读取Preferences或密钥、不启动主界面。

verification.txt只表示这一镜像在本机离线入口诊断通过；不证明FXML渲染、真实点击/首次联网、DPAPI存储、UIA/IME/多屏、退出清理或干净机已通过。归档版本/资源存在也不证明每条业务路径。不得把一个EXE文件单独拷走：app和runtime必须完整保留。

## 本次本机实际结果

2026-10-22 13:25后（Europe/Copenhagen），最终55项JUnit失败/错误/跳过0，package和JAR门禁退出0。jlink/jpackage镜像构建、静态门禁、实际随包EXE --diagnose退出0，报告version=2.1.12、java=25.0.4+9-LTS、javafx=25.0.4、jna=5.18.1。再次将整个镜像复制到target/D12 offline 中文 path，在该子进程JAVA_HOME设为不存在的路径、PATH只留Windows系统目录后，EXE --diagnose仍退出0；证明这次诊断不用外置Java，不等同干净Windows/主GUI通过。

首轮镜像真实诊断失败，没有把静态检查当启动成功：jdeps确认JNA需要java.logging，补齐并增加缺模块拒绝测试；之后明确诊断定位native阶段UnsatisfiedLinkError，隔离探针确认默认JNA临时解包报“Failed to create temporary file ... 拒绝访问”。没有调整系统目录权限/注册表/环境全局值；改为显式随包DLL、禁止临时解包和系统库搜索，再次重建及搬迁后均通过。临时目录具体权限原因未诊断/修复，不宣称ACL已解决。

最终JAR SHA-256：75092d6fe81725a4d114f9be6e660a60633cf66b72a04a6d0f7826c562a0414f。镜像未上传，target本地证据不会入Git。完整细节见版本说明。

## 首用人工验收（全部待干净Windows验证）

使用隔离Windows账户/VM快照，不借用已有个人配置和真实凭据，不删除真实注册表。记录OS/架构/显示缩放/镜像版本、结果和证据；没有环境就保持待验。

1. 在无外置Java/JDK环境，复制整个镜像至含空格和中文的可写路径，以普通用户执行--diagnose；应通过且不显示主窗。额外PATH中的Java不应改变随包版本。SmartScreen/杀毒提示、签名、VC运行库依赖均需单独记录，不提供绕过建议。
2. 无参数打开主窗，设置/复制禁用态/键盘进入/关闭可用；不抢已有源应用焦点。首次联网默认关闭；拒绝后重启仍关闭；开启动作本身不自动捕获或发送。主窗外Tab/UIA/IME需按各自待验矩阵验证。
3. 用测试账户自造无效假密钥检查本地保存/删除/重启反馈，不使用私人密钥，不把“保存成功”写成服务鉴权成功。实际Windows DPAPI/账号切换和磁盘持久性需单独记录；假密钥不得发真实API。
4. 在线服务完整首用闭环需用户另行在隔离环境自行配置授权测试凭据、确认文本/费用后操作；本次不执行真实API。关闭在线开关不撤回已准入HTTP/文本或费用。
5. 更改外观、关闭重启，核对保存反馈；损坏配置/写入失败只在隔离测试账户注入。多屏位置找回、实际hook安装失败、重复启停和进程退出要独立记录。
6. 应用镜像没有系统安装登记/卸载器，退出后删除整个测试镜像目录即移除程序文件；不要只删EXE。若未来制作安装器，安装路径、快捷方式、升级、旧版共存、标准卸载与重装仍需另验，不能用镜像删除代替安装器卸载通过。

## 数据位置及卸载保留

以下由源码的Preferences.userNodeForPackage推导，未读取本机真实值。默认Windows Java Preferences通常映射到当前用户HKCU\Software\JavaSoft\Prefs，后端/平台可能不同：

| 数据 | Java逻辑节点 | 含义 |
|---|---|---|
| API Key、窗口坐标 | /com/easytranslate/config | deepseekApiKey为DPAPI当前用户保护后的Base64；window.x/window.y为位置 |
| 联网决定 | /com/easytranslate/config/privacy | onlineTranslationConsent，版本化同意/拒绝，不随镜像目录移除 |
| 六项外观 | /com/easytranslate/view/appearance | 置顶/原文/装饰/动效/字号/窗口档位 |

镜像不是“所有数据便携地放同目录”：复制/删程序目录不迁移/清除这些用户配置；重装可能保留以前同意，不能保证每次重装都重新弹首次状态。DPAPI密文不能保证跨账户/机器可解密，不建议复制密钥节点。当前词本仍是占位，不存在本周期新增词库数据库/保存记录；选区/译文只在当前UI状态，本次没有新增持久日志。

移除前若要撤销使用，先在应用设置拒绝/关闭在线翻译，删除API Key并查看本地成功反馈，再退出；删除不是介质安全擦除/服务商密钥吊销，不撤回已发送文本，也不删除服务商账户及账单。需要吊销请用户自行在服务商账户操作，不提供密钥给助手。

当前没有“一键清空全部配置”功能，也未验证安装器是否主动清理Preferences。因此按保留处理，不承诺卸载自动删除数据。本次不运行任何注册表删除；不要删除整个JavaSoft\Prefs（其他Java应用共用）。完全清理只能在用户明确授权和备份/隔离条件下仅处理上述所属节点，步骤另审，不在本周期自动实施。

参见[构建版本门禁](build-verification.md)、[v2.1.12](releases/v2.1.12.md)、[选区矩阵](selection-compatibility.md)、[Tab安全](tab-trigger-safety.md)、[键盘焦点](keyboard-focus.md)。
