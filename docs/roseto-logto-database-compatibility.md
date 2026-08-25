# Roseto Logto PostgreSQL 兼容规格

## 1. PostgreSQL 18

Roseto 目标数据库为 PostgreSQL 18。Logto OSS schema 当前安装脚本要求 PostgreSQL 14 或更高版本，因此 PostgreSQL 18 属于兼容范围，但必须执行完整 migration 和 integration test，不能仅凭版本号推断兼容。

Docker 验收镜像固定为：

```text
postgres:18
```

生产和测试不得使用 `latest`。

## 2. Schema 来源

Logto 兼容 schema 的唯一来源顺序：

```text
Logto packages/schemas/tables/*.sql
Logto packages/schemas/models/*.ts
Logto alteration scripts
Logto seed commands
Logto integration tests
```

`docker-compose.yml` 只负责启动数据库，不是完整 schema 定义。schema 由 seed / alteration 生成。

## 3. Migration 策略

Roseto 不把 Logto schema 手工简化成少数 Phase 0 表。兼容模式必须：

1. 固定 Logto source snapshot commit；
2. 导入完整基础表、索引、触发器和 RLS policy；
3. 使用 Flyway 执行有序 migration；
4. 使用 MyBatis Plus 映射现有表；
5. 对每个 migration 保存 schema checksum；
6. 运行 schema diff，发现表、列、类型、约束或索引变化时失败。

Roseto 独有表使用 `roseto_` 前缀。不得修改 Logto 兼容表以承载 Roseto 独有字段。

## 4. Seed 数据

Seed 分为：

```text
base seed
cloud seed
test seed
```

默认生产启动只执行 base seed；Cloud 和 test seed 必须显式开启。随机私钥、cookie key、初始密码和随机 ID 不要求与 Logto Docker 数值相同，但：

- 表结构和字段语义相同；
- 必需记录存在；
- 默认 Tenant / admin Tenant 关系相同；
- OIDC config、API resource、scope、Console Application 语义相同；
- seed 可重复执行且不产生重复数据。

## 5. MyBatis Plus 约束

- Mapper 只能依赖兼容表的实体映射；
- 不使用 MyBatis Plus 自动建表；
- 不使用 `BaseMapper` 绕过 TenantContext；
- 所有 Tenant 查询必须有显式 tenant predicate 或由 RLS 强制；
- 复杂 Logto JSONB 字段使用明确的 TypeHandler；
- JSONB 的字段名、null 语义和默认值不得被 Java bean 自动命名策略改变；
- Migration 由 Flyway 管理，MyBatis Plus 只负责 CRUD。

## 6. Schema 验收

```text
空 PostgreSQL 18
→ Flyway migrate
→ base seed
→ schema diff 与 Logto snapshot 一致
→ Roseto 启动
→ Logto Console / Experience API smoke test
→ PostgreSQL 备份恢复后再次通过 schema diff
```
