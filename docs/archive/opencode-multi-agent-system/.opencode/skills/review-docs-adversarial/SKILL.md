---
name: review-docs-adversarial
description: Use after docs-reviewer has completed compliance review, to perform adversarial/critical review of design or plan documents. Focuses on questioning decisions, finding blind spots, and checking edge cases that compliance review misses.
---

# 文档批判审查

从"找茬"视角审查设计和计划文档。假设文档有错，然后去找错。

## 何时用

- docs-reviewer 已完成合规审查且无 Critical
- 02-design.md 或 03-plan.md 需要批判性二次审查

## 前置

1. **读上游文档** — 审设计时读 01-requirement.md；审计划时读 02-design.md。
2. **读待审文档** — 通读全文。
3. **读已有合规审查报告** — 读 02.5-review-compliance.md 或 03.5-review-compliance.md，避免重复。

## 审查维度

| 维度 | 检查什么 |
|------|---------|
| **设计决策质疑** | 为什么选这个方案？被否方案真的不可行？有没有更好的第三种？ |
| **盲区搜索** | 并发？大数据量？服务降级？上下游变更？ |
| **边界条件** | 空值、零值、超大值、并发竞争、幂等性 |
| **隐性假设** | 文档中没说出口的假设是什么？成立吗？ |
| **过度/不足** | 做了不需要的？漏了必须做的？ |
| **计划可行性** | *(仅审计划时)* 任务粒度？依赖顺序？遗漏的验证步骤？ |

## 原则

- **不重复** — docs-reviewer 已发现的问题不再提
- **要具体** — 每个问题必须说明"如果不修会导致什么后果"
- **不凑数** — 如果真的没问题，明确写"未发现补充问题"
- **敢质疑** — 对设计决策提出替代方案，即使最终被否也有价值

## 输出

报告保存为独立文件：

- 审设计 → `docs/feature/YYYY-MM-DD-<feature-slug>/02.5-review-adversarial.md`
- 审计划 → `docs/feature/YYYY-MM-DD-<feature-slug>/03.5-review-adversarial.md`

格式：复用 review-docs 的 `./review-template.md`，标题注明"批判性审查"。

报告开头必须有裁决字段：
```
Verdict: PASS | PASS_WITH_NOTES | BLOCKED
Critical: N
Important: N
Minor: N
```

每个问题标注 Critical / Important / Minor。
