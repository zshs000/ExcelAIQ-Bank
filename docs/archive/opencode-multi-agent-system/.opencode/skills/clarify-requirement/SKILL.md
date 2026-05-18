---
name: clarify-requirement
description: Use when the user submits a new feature request or complex change that needs structured clarification before design. Not for simple bug fixes, config changes, or minor edits that can go directly to implementation.
---

# 需求澄清

把所有疑问一次性写入需求讨论文档。不写一行代码。

## 何时用

用：
- 新功能请求
- 大范围行为变更
- 需求模糊不明确
- 用户说"我要..."但没给细节

不用：
- 简单 bug 修复（预期行为明确）
- 配置修改（值明确）
- 小改动（范围清晰）

## 核心流程

先探索项目上下文，再把**所有疑问一次性**写入 `docs/feature/YYYY-MM-DD-<feature-slug>/01-requirement.md`。

**文档格式：** 严格遵循 `./requirement-template.md`。目录路径和文件编号不可变动。

## 步骤

1. **探索项目上下文** — 读 AGENTS.md，检查相关模块、文档、最近提交。理解架构、现有模式、约束。
2. **写 Round 1 所有疑问** — 按 `./requirement-template.md` 格式，把所有问题批量写入 `01-requirement.md`。聚焦目的、约束、成功标准。优先出选择题（A/B/C）。一行一个问题。问题的维度由探索结果自然决定，不预设分类。
3. **返回文档路径给 Master** — 到此停止。不要回答自己的问题。
4. **被再次调用时** — 如果 Master 需要更多轮澄清，写 Round N 追问。所有轮次完成后，写 `00-overview.md`（一句话概述今天确定了什么），再写 `📋 需求总结` 到 `01-requirement.md` 末尾。

## 问题质量要求

- 一行一个问题，不是一段
- 优先用 A/B/C 选项
- 聚焦：目的、约束、成功标准
- 绝不假设。拿不准就问。
- 如果某个模块或模式可能相关，在问题中明确提及。

## 目录规定

- 根路径：`docs/feature/YYYY-MM-DD-<feature-slug>/`（用当天日期）
- 文件命名：`00-overview.md`、`01-requirement.md`、`02-design.md`、`03-plan.md`
- 不可更改这些路径或文件名。
