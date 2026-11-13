# 纸间阅读 Paperlight · 开发指南

> 给在本项目中工作的 AI 助手 / 新加入的开发者：这里记录了构建、签名、发布和验证的全部约定。改代码前先读完本文件。

## 项目概况

- **产品**：纸间阅读 Paperlight —— Apple Books 风格的 Android EPUB 阅读器，中文优先，中英双语
- **技术栈**：Kotlin + Jetpack Compose，AGP 8.7.3 / Gradle 8.11.1 / JDK 21，minSdk 26，targetSdk 35
- **包名**：`com.zhijian.reading`
- **阅读引擎**：`app/src/main/assets/reader.js`（WebView 内的 CSS 多列分页 + tap 三区检测 + 选区上报）
- **仓库**：https://github.com/wangtongxue1/paperlight
- **样书**：内置 15 本中文公版典籍（`SampleLibrary.kt`，首启 DB 空时自动 seed），样书同时注册 EPUB + PDF 双格式，所以详情页「开始阅读」会弹出格式菜单

## 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+（推荐 21） | AGP 8.7.3 要求；设置 `JAVA_HOME` |
| Android SDK | platforms;android-35、build-tools;34.0.0+、platform-tools | `local.properties` 写 `sdk.dir=`；或装 Android Studio 全自动 |
| Gradle | 不用装 | 项目带完整 wrapper（腾讯云镜像下载），`gradlew` 首次运行自动获取 |

## 构建命令

```bash
gradlew assembleDebug        # debug 包 → app/build/outputs/apk/debug/
gradlew assembleRelease      # 正式签名包（需要 keystore/ 目录，见下）
gradlew compileDebugKotlin   # 只验证编译
```

## 签名（敏感，不在 Git 里）

`keystore/` 目录（`zhijian.jks` + `signing.properties`）被 .gitignore 排除，**换电脑必须手动拷贝**，否则 release 无法用同一签名（签名不一致会覆盖安装失败）。没有它 debug 构建不受影响。

## 发布流程（先问用户，未经用户明确指令不得推送 GitHub / 发布 Release）

1. `app/build.gradle.kts` 升 `versionCode`(+1) 与 `versionName`
2. 同步 `MineScreen.kt` 里硬编码的版本号字符串
3. `CHANGELOG.md` 按 Keep a Changelog 增加条目
4. Release 资产命名（用户指定，沿用）：文件名 `Paperlight-<版本>.apk`，显示名 `Paperlight <版本>.apk`（带空格）
5. Release 正文样式：下载表格+SHA256 → 本版亮点 → CHANGELOG 链接 → 「数据在哪」加粗提示
6. 资产 label 更新端点用 `PATCH /repos/.../releases/assets/{asset_id}`（用 `/releases/{release_id}/assets/{id}` 会 404）
7. `git push` 遇 502/schannel 断连时走 Git Data API 兜底：blobs → tree(base_tree) → commit → PATCH refs/heads/main

## 已知坑与验证要点

- **分页布局**：列宽必须 = `pager.clientWidth - 2*PAD`，翻页步进 = 列宽 + GAP（全局变量 STEP），偏移/页码/总页数全部用 STEP 计算——改动任何一处都会让多页书籍逐页错位（历史 bug，勿回退）
- **样书双格式**：自动化测试点「开始阅读」后必须 dump 弹出的格式菜单再点 EPUB，不是直接进阅读器
- **模拟器输入失效**：长时间运行后 `input tap` 注入会失效（keyevent 仍正常），用 `input touchscreen tap`；若 app 被 Cached App Freezer 冻结，`adb root` 后 `echo 0 > /sys/fs/cgroup/uid_10221/pid_*/cgroup.freeze` 解冻，根治用 `adb shell device_config put activity_manager_native_boot freeze_process_cache_enabled false` + 重启
- **抓动画帧**：连拍用设备端 shell 循环 `screencap`（注意单引号防本地变量展开）；必要时临时把 tween 调成 8000ms 抓帧、验证后改回
- **验证翻页对齐**：paged 模式连翻多页后检查：恰好一栏、无左侧裁切残留、无横向滚动条、页码与内容对应

## 约定

- 界面文案全部走 `ui/L10n.kt`（接口 + 中文 + 英文三处同步）
- 图标风格：24dp 网格、1.8 描边、圆角端点的手绘矢量（`ic_r_*` 阅读器、`ic_a_*` 应用层），不使用 Material 自带图标
- 发布节奏听用户指令：**先本地验证 + 出 APK 交用户确认，用户点头后才推 GitHub / 发 Release**
