---
name: plan-implementation
description: Use when technical design is complete and approved, to produce a bite-sized implementation plan with exact file paths, code, and commands.
---

# 实施计划

将技术设计拆解为逐任务实施计划。每步精确到文件路径、Java 代码、Maven 命令。假设执行者对此项目零上下文。

## 何时用

用：
- 02-design.md 已完成且审批通过
- 需要多个文件/模块协作实现

不用：
- 单文件小改动（直接派 worker）

## 前置

1. **读项目风格** — 读 `AGENTS.md`
2. **读设计文档** — 读 `docs/feature/当次日期/02-design.md`
3. **文件结构映射** — 列出将创建/修改的文件及各自职责，锁定分解决策。大文件能拆就拆。

## 任务粒度

每步一个动作（2-5 分钟）：

- 写失败测试
- 跑测试验证失败
- 写最小实现
- 跑测试验证通过
- 自检清单（不提交）

## 任务结构

遵循 `./plan-template.md`。每个任务包含：

- 文件列表（Create/Modify/Test），精确路径
- `- [ ]` 步骤 + 实际 Java 代码块
- Maven 命令 + 期望输出
- **并行标记**：`[串行]` 或 `[并行-N]`（N=分组号，同组可并行）

## 零占位符 — 以下全部禁止

- "TBD"、"TODO"、"稍后实现"
- "添加适当错误处理" / "完善校验"
- "编写测试"（不给测试代码）
- "类似 Task N"（重复代码，执行者可能乱序读任务）
- 步骤描述干什么但不展示怎么干
- 引用任务中未定义的类型或方法

## 并行规则

- 修改不同文件且无接口依赖 → 标记同一 `[并行-N]` 组
- 修改同一文件或存在接口依赖 → `[串行]`，按依赖排先后
- 每个并行组不超过 3 个任务

## 自审

写完计划后逐条检查：

1. **设计覆盖** — 对照 02-design.md，每个设计要求都能指到对应任务？
2. **占位符扫描** — 搜索 TBD/TODO/模糊描述，修复。
3. **类型一致性** — 前任务定义的类名/方法名，后任务引用一致吗？

修复后直接完成，无需再审。

## 输出

返回 `docs/feature/YYYY-MM-DD-<feature-slug>/03-plan.md` 的路径给 Master。到此停止。
