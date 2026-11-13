# 更新日志 · Changelog

纸间阅读 **Paperlight** —— Apple Books 风格的 Android EPUB 阅读器。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)。

## [1.1.0] - 2026-11-13

### 新增

- **点击功能菜单**：点击页面中央唤出/隐藏菜单——顶栏（返回、书名、整书进度百分比），底栏（页数信息、目录、书签、批注、界面）
- **双翻页模式**：上下滚动（章节 + 百分比进度）/ 左右翻页（第 x / y 页），在「界面」面板切换并记忆
- **书签**：底栏一键添加当前位置书签；目录面板内「书签」标签页可查看、跳转、删除
- **批注**：长按/划选文字 → 写笔记 → 原文高亮标记，重开自动恢复；批注列表支持跳转与删除；一键导出为 .txt（系统存储对话框）
- **字号调节**：80%–160% 滑条，实时生效并记忆

### 优化

- 禁用双击缩放，阅读不受干扰
- 目录来自 EPUB 内置目录解析，点击章节直接跳转

## [1.0.0] - 2026-11-13

首个正式版本。

### 功能

- 中文优先界面，支持中文/英文双语（默认中文）
- EPUB 阅读：翻页阅读、进度记忆、「阅读」页
- 双通道导入：网页链接导入 + 本机文件浏览导入（SAF，无需存储权限）
- Apple Books 风格设计：纸感底色、悬浮 Dock 导航、书架网格
- 元数据编辑、calibre metadata.db 兼容、CJK 封面/PDF 导出
- 内置 15 本中文公版典籍样书

[1.1.0]: https://github.com/wangtongxue1/paperlight/releases/tag/v1.1.0
[1.0.0]: https://github.com/wangtongxue1/paperlight/releases/tag/v1.0.0
