# Logto Schema Inventory 规范

## 1. 目标

建立可审计的 Logto PostgreSQL schema 清单，比较 Roseto compatibility mode 与 Logto source snapshot 的结构差异。

## 2. Inventory 范围

必须记录：

```text
tables
columns
data types
nullability
defaults
primary keys
foreign keys
unique constraints
indexes
sequences
triggers
functions
extensions
RLS enable flags
RLS policies
PostgreSQL roles
```


```json
{
  "object": "tenants",
  "kind": "table",
  "source": "packages/schemas/models/tenants.ts",
  "sourceCommit": "abd6327065e0c178bc621c66fcfeb386bb49d7cf",
  "rosetoStatus": "required",
  "definitionSha256": "..."
}
```

字段名 `rossetoStatus` 实际实现时必须修正为 `rosetoStatus`。

## 4. 比较命令

```bash
pg_dump --schema-only --no-owner --no-privileges "$LOGTO_DB_URL" > actual-schema.sql
sha256sum actual-schema.sql
./mvnw -pl roseto-infrastructure test
```

测试必须比较：

```text
expected Logto snapshot
actual PostgreSQL 18 schema
Roseto compatibility schema
```

差异分类：

```text
missing      → fail
changed      → fail
unexpected   → fail for Logto tables; allowed only for roseto_* tables
seed-only    → compare through seed manifest
```

## 5. Seed manifest

Seed manifest 只记录确定性字段：

```text
tenant relationships
application type
redirect URI
resource indicator
scope name
sign-in experience settings
languageInfo
OIDC config keys (not secret values)
```

私钥、cookie key、密码 hash、随机 ID 只验证格式和存在性，不比较具体值。
