---
name: review-code
description: Use when worker has completed implementation and code needs systematic review before proceeding. Works for both same-model and cross-model review.
---

# 代码审查

对 worker 提交的代码变更进行系统审查。不修改代码，只出审查报告。

## 何时用

- worker 完成一个任务，Master 需要审查
- Layer 1（同模型）或 Layer 2（异模型）均用此 skill

## 审查流程

1. **读上游文档** — 读 `docs/feature/当次日期/03-plan.md`，确认本次任务的范围。
2. **读项目规范** — 读 `AGENTS.md`，理解代码风格、命名约定、包结构。
3. **读代码变更** — 默认使用 `git status --short` 查看范围，`git diff` 查看未暂存变更，`git diff --staged` 查看已暂存变更。仅当 Master 明确说明代码已提交时，才使用 `git diff HEAD~1`。
4. **逐维度审查** — 按下面维度检查。
5. **写审查报告** — 返回 `./review-template.md` 格式的报告给 Master。

## 审查维度

| 维度 | 检查什么 |
|------|---------|
| **正确性** | 实现与计划一致吗？逻辑有漏洞吗？边界条件处理了吗？ |
| **设计** | 符合 02-design.md 的架构吗？新代码与现有模块的边界清晰吗？ |
| **风格** | 符合 AGENTS.md 和项目现有代码风格吗？命名、包结构、异常处理方式一致吗？ |
| **测试** | 测试覆盖了关键路径和边界吗？测试真实可运行还是 mock 一切？ |
| **安全** | 输入校验、权限检查、SQL 注入、敏感信息泄露 |
| **性能** | N+1 查询、不必要的数据库调用、大循环内的重复操作 |

## 审查报告

按 `./review-template.md` 格式出报告。报告开头必须有 `Verdict` 裁决字段（PASS / PASS_WITH_NOTES / BLOCKED）及各等级问题计数。

## 红线

- 不修改代码
- 不跑构建、测试、安装、生成、写入等会改变环境或文件系统的命令；只允许只读的 `git status` / `git diff` 类命令用于审查变更范围
- 不确定的问题标注"待确认"，不假装知道
- Critical 必须明确，不敷衍
