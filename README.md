# 简记 · Android 记账 App

原生 Android 离线记账项目，当前版本 **1.2.0**，支持消费类型管理、多种统计图表、图内分类占比标注及账单导入导出。

- [开发文档](docs/开发文档.md)：功能范围、交互、数据模型、统计规则、架构与验收标准。
- [详细设计](docs/详细设计.md)：功能类、接口契约、状态机、调用时序、事务和异常处理。
- [开发环境配置](docs/开发环境配置.md)：安装、构建、IDE 与真机调试方法。
- [环境验证记录](docs/环境验证记录.md)：本机实际安装和验证结果。
- [实现与验证记录](docs/实现与验证记录.md)：当前实现结构、验证结果与后续范围。
- [1.0.0 发布说明](docs/发布说明-1.0.0.md)：正式安装包、签名保管、升级限制与发布验证。
- [1.1.0 更新说明](docs/更新说明-1.1.0.md)：类型增删改、图表切换、UI 改进与验证结果。
- [1.1.1 更新说明](docs/更新说明-1.1.1.md)：环形图与饼图的类型、百分比标注。
- [1.2.0 更新说明](docs/更新说明-1.2.0.md)：JSON 合并导入/导出、CSV 导出、校验预览和事务流程。

## 当前交付范围

支持添加、编辑、删除消费、日期和备注；初始提供 8 种消费类型，可添加、改名、换色、删除及恢复停用类型。明细按日期分页展示，可按日期范围、类型和备注筛选。统计支持环形图、饼图、柱状图和时间趋势折线图，保存图表选择，点击类型查看对应明细。提供统一的卡片布局以及浅色、深色和跟随系统主题。

数据保存在本机 Room 数据库，无需账户和联网。金额使用整数分，事务保证写入一致性，重复请求不会重复记账，过期编辑会提示冲突。删除已关联账单的类型会将其停用，保留历史和统计；未关联账单的类型直接删除。没有预置虚假账单；预算尚未实现。

在「设置 → 账单导入与导出」中保存全部账单的 JSON 副本，或导出 CSV 表格。JSON 导入先预览再合并，相同 ID 且内容一致的记录跳过，冲突时不覆盖；CSV 暂不支持导入。单文件上限 10 MiB、10,000 笔账单、200 个类型，文件为明文。

## Windows 快速开始

在项目根目录的 PowerShell 中执行：

```powershell
# 首次配置；需要联网。Android SDK 许可须在提示时阅读并自行接受。
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-android.ps1

# 每个新终端载入项目内的工具路径。
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
. .\scripts\env.ps1
.\scripts\doctor.ps1
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。

自动化验证和实现信息见[实现与验证记录](docs/实现与验证记录.md)。真机调试时连接设备后执行 `adb install -r app\build\outputs\apk\debug\app-debug.apk`。Debug 与 release 使用不同签名，同包名不能直接互相覆盖安装。

## 1.2.0 Release

正式签名 APK：`dist/1.2.0/jianji-1.2.0-release.apk`；应用商店用 AAB：`dist/1.2.0/jianji-1.2.0-release.aab`。沿用原正式签名，versionCode 升为 6，可覆盖升级正式版并保留账本。已开启 R8 优化、混淆和资源压缩，关闭调试。当前为本地发布交付，尚未上传应用商店。

重建：载入环境后执行 `.\gradlew.bat assembleRelease bundleRelease lintRelease testDebugUnitTest`，输出位于 `app/build/outputs/apk/release` 和 `app/build/outputs/bundle/release`。需要本地 `keystore.properties` 和对应正式签名密钥；参考 `keystore.properties.example`。密钥不随源码分发，务必单独安全备份，后续版本更新必须沿用同一签名。

**已安装旧 debug 版且有记账数据时，不要为了安装 release 版直接卸载。** 两者签名不同，系统会拒绝覆盖。可先升级到同签名的新版 debug 包，导出 JSON 并核对文件后再安排迁移；卸载会删除本机账本。

技术基线：JDK 17、Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.20、Compose；minSdk 26、compileSdk / targetSdk 35。发布上架前重新核对 Google Play 当期 target API 政策。

