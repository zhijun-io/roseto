# Roseto MyBatis Plus 开发规范

## 1. 边界

MyBatis Plus 只负责 persistence adapter，不负责：

```text
建表
migration
seed
domain rule
HTTP authentication
Tenant authorization decision
```

Migration 使用 Flyway；数据库 schema 以 Logto compatibility snapshot 为准。

## 2. 分层

```text
Controller
→ Application Service
→ Domain Port
→ MyBatis Repository Adapter
→ Mapper / XML
```

Controller 禁止注入 `BaseMapper`。Domain 禁止依赖 MyBatis Plus 类型。

## 3. Entity / DTO

必须分离：

```text
Logto*DO          数据库兼容映射
*Request          HTTP 请求
*Response         HTTP 响应
Domain aggregate 领域对象
```

Logto 表字段不得因为 Java 命名策略被重命名。需要显式声明：

```java
@TableName("sign_in_experiences")
@TableField("tenant_id")
```

## 4. 类型映射

必须提供明确 TypeHandler：

```text
jsonb → Jackson JsonNode / typed value object
timestamptz → Instant
uuid → UUID
varchar enum → explicit enum handler
nullable jsonb → null 与 {} 区分
```

禁止依赖默认 handler 改变 JSONB null 语义。

## 5. Tenant 隔离

Logto-compatible mode 优先遵守 Logto schema 的 RLS / role 语义。若使用应用层 TenantContext：

- 所有 query 必须显式带 tenant_id；
- 管理员跨 Tenant 查询必须使用明确 privileged port；
- 不允许用客户端 tenantId 覆盖认证上下文；
- 请求结束必须清理 TenantContext；
- 异步任务必须显式传递 TenantContext；
- 连接池复用前后必须清理数据库 session state。

不得同时启用未经验证的 MyBatis Plus tenant interceptor 和 PostgreSQL RLS，避免 SQL 被重复改写。

## 6. SQL 规范

- 简单 CRUD 可以使用 `BaseMapper`，但只能从 Repository adapter 调用；
- 复杂查询使用 XML；
- 禁止 Controller 拼接 SQL；
- 禁止 `select *`；
- 所有跨 Tenant 关联查询显式验证关联实体 tenant_id；
- 批量写入必须定义事务边界和失败回滚行为。

## 7. 配置门禁

必须固定并记录：

```text
MyBatis Plus version
PostgreSQL JDBC driver
Jackson version
Flyway version
mapper XML location
TypeHandler package
```

启动时必须验证 Flyway checksum；schema 不匹配时 fail fast。
