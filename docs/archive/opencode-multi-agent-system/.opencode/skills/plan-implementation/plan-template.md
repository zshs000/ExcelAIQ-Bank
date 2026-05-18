# 实施计划：<功能标题>

> 文件路径：`docs/feature/YYYY-MM-DD-<feature-slug>/03-plan.md`

**目标：** [一句话描述要构建什么]

**技术栈：** Spring Boot 3.0.2 / MyBatis / 等

---

## 任务依赖概览

```
Task 1 → Task 2 → Task 4
       ↘ Task 3 ↗   (Task 2,3 可并行)
```

---

### Task 1: [组件名称] `[串行]`

**文件：**
- Create: `eaqb-xxx/.../XxxController.java`
- Create: `eaqb-xxx/.../XxxService.java`
- Create: `eaqb-xxx/.../XxxMapper.java`
- Test: `eaqb-xxx/.../XxxServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
void testSpecificBehavior() {
    // ...
    assertEquals(expected, actual);
}
```

- [ ] **Step 2: 跑测试验证失败**

```bash
mvn test -pl eaqb-xxx -Dtest=XxxServiceTest#testSpecificBehavior
```
期望：FAIL

- [ ] **Step 3: 写最小实现**

```java
// XxxService.java
public Result doSomething(Input input) {
    // ...
}
```

- [ ] **Step 4: 跑测试验证通过**

```bash
mvn test -pl eaqb-xxx -Dtest=XxxServiceTest#testSpecificBehavior
```
期望：PASS

- [ ] **Step 5: 检查清单**

```text
- [ ] 所有测试通过
- [ ] 无编译警告
- [ ] 代码未违反 AGENTS.md 中的项目规范
```

> 完成检查后返回结果给 Master，不提交。提交由 Master 在审查通过后统一执行。

---

### Task 2: [组件名称] `[并行-1]`

...（同上结构）

---

### Task 3: [组件名称] `[并行-1]`

与 Task 2 可并行执行。...（同上结构）
