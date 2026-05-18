---
name: design-feature
description: Use after requirement clarification is complete, to produce a technical design document before any code is written. Not for simple tasks that don't need architecture planning.
---

# 功能设计

在需求澄清完成后，产出技术设计文档。不写代码，只出设计。

## 何时用

用：
- 01-requirement.md 已完成且澄清完毕
- 新功能需要架构决策
- 涉及多模块协作
- 数据模型变更

不用：
- 简单配置修改
- 单文件小改动
- 纯 bug 修复

## 核心流程

1. **读项目风格** — 先读 `AGENTS.md`，理解项目的架构约定、命名规范、包结构、通信方式（Feign/RocketMQ）。
2. **读需求** — 读 `docs/feature/当次日期/01-requirement.md`，尤其是 `📋 需求总结`。
3. **探索现有关联模块** — 搜索项目中与新需求相关的已有模块，找到可复用的模式和风格。如果 AGENTS.md 没有覆盖到某项风格，去代码里找类似实现。
4. **提 2-3 个方案** — 不同架构思路，列优缺点，给出推荐。
5. **写设计文档** — 保存到 `docs/feature/YYYY-MM-DD-<feature-slug>/02-design.md`。

## 设计文档结构

遵循 `./design-template.md`。路径和文件编号不可变动。

必须覆盖每一项：架构、组件/模块划分、数据模型、数据流、错误处理、测试策略。每个组件要能回答：做什么、怎么用、依赖谁。每个组件应可独立理解和测试。

## 设计原则

- **隔离优先** — 拆分为独立单元，每个单元单一职责，接口清晰
- **尊重现有** — 遵循项目中已有模式，不要在不相干的地方引入新风格
- **YAGNI** — 只设计需求要求的部分，不过度设计
- **2-3 方案** — 提出多个思路再选，推荐理由要写清楚

## 自审

写完文档后逐条检查：
1. 有无 TBD/TODO/占位符？修复。
2. 各部分之间有无矛盾？架构描述与模块划分一致吗？
3. 范围是否聚焦，需要进一步拆分吗？
4. 有无歧义？两种解读？取其一并写明确。

修改后直接进入下一步，无需再审。

## 输出

返回 `docs/feature/YYYY-MM-DD-<feature-slug>/02-design.md` 的路径给 Master。到此停止。
