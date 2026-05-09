# 部署说明

## 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.6.3+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| Nacos | 2.x |
| RocketMQ | 5.x |

## 数据库准备

1. 创建 MySQL 数据库
2. 执行 `sql/` 目录下的迁移脚本（按日期顺序）

## Nacos 配置

启动 Nacos Server，在各模块的 `application-dev.yml` 中配置 Nacos 连接信息。服务启动后从 Nacos 拉取运行时配置。

## 编译

```bash
# 编译全部（跳过测试）
mvn clean install -DskipTests

# 编译单个模块
mvn clean install -pl eaqb-auth -am -DskipTests
```

## 启动顺序

```
eaqb-distributed-id-generator
  → eaqb-auth
    → eaqb-oss
      → eaqb-user
        → eaqb-question-bank
          → eaqb-excel-parser
            → eaqb-gateway
```

## 验证

```bash
# 健康检查
curl http://localhost:8000/actuator/health

# 登录获取 Token
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone": "xxx", "code": "xxx"}'

# 带 Token 调用接口
curl http://localhost:8000/api/question/page \
  -H "Authorization: Bearer {token}"
```
