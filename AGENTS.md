# AGENTS.md — Excel AI Q-Bank (eaqb)

## 1. 项目概述

ExcelAIQ-Bank 是一个基于 **Spring Cloud Alibaba** 微服务架构的智能题库系统。用户上传 Excel 文件，系统自动解析并导入题目到题库。预留 AI 服务集成接口（通过 RocketMQ 异步通信）。技术栈：Java 17 + Spring Boot 3 + Spring Cloud Alibaba + MySQL + Redis + Nacos + RocketMQ + MinIO/阿里云 OSS。

```
Excel AI Q-Bank (根 pom.xml)
├── eaqb-gateway/                    # API 网关 (Spring Cloud Gateway)
├── eaqb-auth/                       # 认证服务 (Sa-Token、验证码、登录)
├── eaqb-user/                       # 用户服务
│   ├── eaqb-user-api/               #   API 接口定义
│   └── eaqb.user.biz/               #   业务实现
├── eaqb-question-bank/              # 题库核心服务
│   ├── eaqb-question-bank-api/      #   API 接口定义
│   └── eaqb-question-bank-biz/      #   业务实现
├── eaqb-excel-parser/               # Excel 解析服务
│   ├── eaqb-excel-parser-api/       #   API 接口定义
│   └── eaqb-excel-parser.biz/       #   业务实现
├── eaqb-oss/                        # 对象存储服务
│   ├── eaqb-oss-api/                #   API 接口定义
│   └── eaqb-oss-biz/                #   业务实现
├── eaqb-distributed-id-generator/   # 分布式 ID 生成器
│   ├── eaqb-distributed-id-generator-api/
│   └── eaqb-distributed-id-generator-biz/
├── eaqb-framework/                  # 公共框架层
│   ├── eaqb-common/                 #   公共工具、常量
│   ├── eaqb-spring-boot-starter-biz-context/    # 上下文 Starter
│   ├── eaqb-spring-boot-starter-biz-operationlog/ # 操作日志 Starter
│   └── eaqb-spring-boot-starter-jackson/        # Jackson 配置 Starter
├── docs/                            # 详细设计文档
└── data/                            # 数据/资源文件
```

## 2. 快速命令

```bash
# 编译全部（跳过测试）
mvn clean install -DskipTests

# 编译单个模块
mvn clean install -pl eaqb-auth -am -DskipTests

# 运行测试
mvn test

# 运行单个模块测试
mvn test -pl eaqb-auth

# 启动服务（推荐顺序）
# eaqb-distributed-id-generator → eaqb-auth → eaqb-oss
# → eaqb-question-bank → eaqb-excel-parser → eaqb-gateway
```

### 环境配置

- 配置文件位置：各模块 `src/main/resources/config/application-{dev,prod}.yml`
- 配置中心：Nacos（服务启动后从 Nacos 拉取配置）
- 需要的中间件：MySQL 8.0+、Redis、Nacos、RocketMQ

## 3. 后端架构

### 包结构（以 eaqb-auth 为例）

```
com.zhoushuo.eaqb.auth
├── controller/      # REST 接口层
├── service/         # 业务逻辑层
│   ├── impl/        #   实现类
│   ├── factory/     #   工厂模式
│   └── strategy/    #   策略模式
│       └── impl/
├── rpc/             # Feign 远程调用
├── config/          # Spring 配置类
├── constant/        # 常量定义
├── enums/           # 枚举
├── exception/       # 自定义异常
├── model/           # 数据模型
│   └── vo/          #   View Object
│       ├── user/
│       └── verificationcode/
└── alarm/           # 告警组件
    └── impl/
```

### 核心子系统

| 子系统 | 职责 | 关键技术 |
|--------|------|----------|
| **认证 (auth)** | 验证码登录、Token 管理、会话 | Sa-Token、Redis |
| **题库 (question-bank)** | 题目 CRUD、导入结果管理、异步 AI 处理 | MySQL、RocketMQ Outbox/Inbox |
| **Excel 解析 (excel-parser)** | Excel 校验→解析→导入两阶段流程 | EasyExcel、分布式锁 |
| **对象存储 (oss)** | 文件上传/下载（MinIO/阿里云 OSS） | MinIO SDK |
| **网关 (gateway)** | 统一入口、路由、鉴权、IP 处理 | Spring Cloud Gateway |
| **分布式 ID (id-generator)** | 全局唯一 ID 生成 | 独立微服务 |

### 服务间通信

- **同步调用**：OpenFeign（内部调用带签名验证）
- **异步通信**：RocketMQ（Excel 导入→AI 处理，Outbox 模式保证 At-Least-Once）
- **服务注册/配置**：Nacos

## 4. 前端架构

> 暂无独立前端模块，前端相关内容待补充。

## 5. 关键约定

1. **API 包与 Biz 包分离**：每个服务拆为 `xxx-api`（接口定义）和 `xxx-biz`（实现），其他服务通过依赖 `xxx-api` 进行 Feign 调用。
2. **内部调用签名**：服务间 Feign 调用必须带签名验证，防止未授权访问。
3. **Sa-Token 认证**：网关统一鉴权，Token 通过 Redis 共享，禁止在业务服务中重复校验。
4. **异常不吞**：微服务间调用错误必须向上传递，禁止吞掉下游错误码。
5. **分层规范**：Controller → Service → Mapper/Repository，禁止跨层调用。
6. **配置走 Nacos**：所有环境相关配置放 Nacos，本地 application.yml 只保留 bootstrap 配置。
7. **Git 提交规范**：commit 信息不要添加 `Co-Authored-By` 标记，遵循 `type(scope): 中文描述` 格式。
8. **代码审查**：审查前，先读 `docs/review/审查指南.md` 和 `docs/review/已知上下文.md`。
9. **方案评估约束**：本项目许多设计点已经过显式权衡；提出方案或质疑前，必须先充分阅读相关代码、文档与已知上下文，若仍不确定，应先询问作者，不要基于局部实现贸然下结论。

## 6. 异常处理规范

### 核心原则

1. **统一使用 BizException**：业务异常必须抛出 `com.zhoushuo.framework.common.exception.BizException`，禁止使用其他异常类型（如 `RuntimeException`、`IllegalArgumentException` 等）表示业务错误。
2. **异常不吞**：微服务间调用错误必须向上传递，禁止吞掉下游错误码。
3. **异常透传**：跨服务调用时，`BizException` 必须直接透传，不能被包装成其他异常（参见 `docs/ExcelParserUtil_异常码折叠问题说明.md`）。

### 异常码规范

- **格式**：`模块前缀-数字`（如 `AUTH-10000`、`EXCEL-20001`）
- **模块前缀**：每个微服务使用独立前缀（AUTH、EXCEL、QUESTION、OSS 等）
- **数字分段**：
  - `1xxxx`：系统级错误（如 `SYSTEM_ERROR`、`PARAM_NOT_VALID`）
  - `2xxxx`：业务级错误（如 `VERIFICATION_CODE_ERROR`）
  - `4xx`：HTTP 状态码相关（如 `UNAUTHORIZED`）

### 异常码枚举

每个模块定义自己的 `ResponseCodeEnum` 实现 `BaseExceptionInterface` 接口：

```java
@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {
    SYSTEM_ERROR("AUTH-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("AUTH-10001", "参数错误"),
    // ... 业务异常码
    ;
    private final String errorCode;
    private final String errorMessage;
}
```

### 全局异常处理

每个服务都有 `GlobalExceptionHandler`（`@ControllerAdvice`）统一捕获异常：

- `BizException` → 返回业务错误码和消息
- `MethodArgumentNotValidException` → 参数校验错误
- `IllegalArgumentException` → 参数错误（Guava 校验）
- `Exception` → 系统未知错误（返回 `SYSTEM_ERROR`）

### 响应格式

统一使用 `Response<T>` 封装返回结果：

```java
@Data
public class Response<T> {
    private boolean success = true;  // 是否成功
    private String message;          // 响应消息
    private String errorCode;        // 异常码
    private T data;                  // 响应数据
}
```

成功：`Response.success(data)`
失败：`Response.fail(errorCode, errorMessage)` 或 `Response.fail(bizException)`

---

## 7. 本地开发及验证流程

### 开发闭环

```
改代码 → mvn clean install -DskipTests → 启动服务 → 调用接口验证
```

### 验证模板

```bash
# 1. 健康检查（网关）
curl http://localhost:端口/actuator/health

# 2. 登录获取 Token
curl -X POST http://localhost:端口/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone": "xxx", "code": "xxx"}'

# 3. 带 Token 调用业务接口
curl http://localhost:端口/api/xxx \
  -H "Authorization: Bearer {token}"
```

### 日志路径

- 应用日志：各服务 `logs/` 目录
- 根目录 `logs/` 也存在部分日志

## 8. 质量检查

| 检查项 | 命令 |
|--------|------|
| 全量编译 | `mvn clean install -DskipTests` |
| 全量测试 | `mvn test` |
| 单模块测试 | `mvn test -pl eaqb-auth` |
| 单模块编译 | `mvn clean install -pl eaqb-auth -am -DskipTests` |

## 9. 参考项目约定

- 包名根路径：`com.zhoushuo.eaqb`
- 服务端口、数据库名等基础设施配置以 Nacos 为准
- 详细设计文档在 `docs/` 下按功能目录组织
- **代码优先原则**：当文档与代码冲突时，以代码为准。文档可能滞后于当前实现，后续会逐步矫正。

## 10. 文档导航

| 目录 | 用途 |
|------|------|
| `docs/excel-import/` | Excel 导入核心文档（全链路、两阶段、contentHash、异常码） |
| `docs/question-chain/` | 题目处理链路（AI 契约、状态机、outbox、异步派发） |
| `docs/auth-gateway/` | 认证与网关（Sa-Token、内部调用签名） |
| `docs/review/` | 代码审查体系（审查指南、已知上下文、审查日志） |
| `docs/blog/` | 项目反思与博客素材 |
| `docs/reference/` | 外部参考资料 |
| `docs/archive/` | 过时文档归档 |
| `sql/` | 数据库迁移脚本 |

详细设计文档在 `docs/` 下按功能目录组织，优先查阅对应目录。
