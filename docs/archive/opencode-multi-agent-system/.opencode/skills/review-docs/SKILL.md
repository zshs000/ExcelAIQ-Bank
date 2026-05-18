---
name: review-docs
description: Use when a design document or implementation plan has been produced and needs compliance review before adversarial review. Checks coverage, consistency, feasibility against upstream documents.
---

# 文档合规审查

对照上游文档审查下游文档的覆盖性、一致性、可行性。不审代码，只审文档。

## 何时用

- 02-design.md 写完，进入下一步前
- 03-plan.md 写完，派 worker 前

## 审查流程

1. **读上游文档** — 审设计时读 01-requirement.md（尤其是需求总结）；审计划时读 02-design.md。
2. **读待审文档** — 通读全文。
3. **逐条审查** — 按下面维度检查。
4. **写审查报告** — 保存到同目录下，文件名格式见输出。

## 审查维度

| 维度 | 检查什么 |
|------|---------|
| **覆盖性** | 上游文档的要求全部覆盖了吗？有无遗漏？ |
| **一致性** | 各部分之间有无矛盾？与上游文档一致吗？ |
| **可行性** | 技术方案在当前架构下能实现吗？有忽略的限制吗？ |
| **清晰度** | 有歧义吗？两处描述同一个东西名字/类型一致吗？ |
| **并行正确性** | *(仅审计划时)* 并行标记合理吗？有所需未声明的共享依赖吗？ |

## 输出格式

按 `./review-template.md` 格式，保存审查报告到 `01-requirement.md` 同目录：

- 审设计 → `docs/feature/YYYY-MM-DD-<feature-slug>/02.5-review-compliance.md`
- 审计划 → `docs/feature/YYYY-MM-DD-<feature-slug>/03.5-review-compliance.md`

每个问题标注：**Critical**（阻断）、**Important**（建议修）、**Minor**（可忽略）。报告开头必须有 `Verdict` 裁决字段（PASS / PASS_WITH_NOTES / BLOCKED）及各等级问题计数。

## 红线

- 不修改被审文档本身
- 不写代码
- 不提出超出上游文档范围的新需求
- 不做批判性挑刺（那是 doc-cross-reviewer 的职责）
- Critical 问题必须明确指出，不允许"看起来没问题"的敷衍结论
