# 可重复执行的构建和版本门禁

本指南固化的是构建步骤与可核对的版本契约，不宣称跨机器、不同时间的ZIP字节完全一致。源码版本唯一来自模块pom.xml的project.version；不要手改历史下载链接或把旧JAR改名当新包。

## 当前环境与JavaFX实际来源

2026-10-22实测：C:\Program Files\Java\jdk-25.0.4-full 的java/javac/jpackage均25.0.4；此完整JDK内置JavaFX25.0.4。Maven声明仍为JavaFX21.0.6，没有升级依赖。使用既有UI检查器的module-path后，`java --list-modules`仍报告同名系统模块javafx.controls/fxml/graphics/base为25.0.4；Maven通用无平台jar另外表现为21.0.6的Empty自动模块。

所以“依赖声明21.0.6”不等于“本机UI实际运行21.0.6”。历史UI检查的通过只证明其实际环境，不是21/25两套均已测试。不能把JDK自带jmods和Maven同名JavaFX jar混在jlink输入中碰运气。纯JDK25+Maven21路径、另一机器和完整运行镜像仍待单独验证；本周期不升级依赖或悄悄更换JDK。

## 标准本地构建门禁

模块目录执行（无需clean，不启动应用/安装器）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4-full'
./mvnw.cmd '-Dtest=BuildArtifactVerifierTest' package
if ($LASTEXITCODE -ne 0) { throw 'package failed' }
& "$env:JAVA_HOME/bin/java.exe" '-cp' 'target/test-classes' 'com.easytranslate.build.BuildArtifactVerifier' '--stamp-module-version'
if ($LASTEXITCODE -ne 0) { throw 'artifact preparation failed' }
& "$env:JAVA_HOME/bin/java.exe" '-cp' 'target/test-classes' 'com.easytranslate.build.BuildArtifactVerifier'
if ($LASTEXITCODE -ne 0) { throw 'artifact verification failed' }
```

构建定向测试仅是构建契约，不代替业务回归；业务源码有变仍按测试指南选择相关链路。门禁不读个人配置、不联网翻译；Maven缺失依赖解析可能联网。

校验器使用安全XML解析读取当前pom的直接groupId/artifactId/version，不接受外部版本覆盖。JAR必须是target/<artifact>-<version>.jar；核对嵌入Maven坐标、Manifest Implementation-Version、module-info实际版本，以及模块描述符/应用类/FXML/CSS必需条目；最终输出SHA-256，只核验当前这份产物，不把旧target目录其他版本当失败或删除。

显式`--stamp-module-version`是有依据的准备步骤：本机增量编译在仅改pom版本时保留旧module-info.class，曾实测文件名/Maven坐标为2.1.11而模块仍2.1.10。先验证文件名/嵌入坐标/Manifest/条目，不允许修复这些旧值或重命名旧包，再调用当前JDK内置jar工具给已确认的归档写入当前模块版本，最后完整校验。默认无参数只校验，绝不悄悄修改。它不改target/classes中的描述符，所以下次package覆盖后仍须执行准备步骤。没有clean/删除用户产物。

maven-jar-plugin固定原本实际使用的2.4并增加Implementation-Version，不升级运行依赖。ZIP时间/工具链和文件顺序等仍可能影响哈希；SHA不是可复现字节/签名/安全审计证明。校验条目存在不等于类/资源内容与当前全部源码一致，也不是运行镜像启动证明。

## 打包/发布边界

当前D11只创建并检查开发JAR；未jlink、未jpackage安装器、未启动主程序（避免真实配置/全局监听）、未发布GitHub Release。JAR不是自带运行时的Windows安装包。后续D12须选择并记录唯一JavaFX运行时来源、校验应用镜像/入口版本，再做首用/卸载/保留数据检查；没有干净Windows就保留待验。所有jpackage的app-version/产物名须从同一pom值导出，不另抄常量；未发布前README现有2.1.0下载保持原样。
