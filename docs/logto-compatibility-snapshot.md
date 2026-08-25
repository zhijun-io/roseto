# Logto 兼容基线快照

## 1. 当前已核对基线

| 项目 | 值 |
|---|---|
| Source repository | `/tmp/logto-source` |
| Git commit | `abd6327065e0c178bc621c66fcfeb386bb49d7cf` |
| Git describe | `abd6327` |
| Root license | MPL-2.0 |
| `@logto/schemas` | 1.42.0 |
| `@logto/console` | 1.39.0 |
| `@logto/experience` | 1.21.0 |
| Docker compose app ports | 3001, 3002 |
| Docker compose PostgreSQL | `postgres:17-alpine` |

该快照是当前兼容分析基线，不代表 Roseto 可以直接复制 Logto 源代码。

## 2. PostgreSQL 18 决策

Logto 示例 Docker Compose 当前使用 PostgreSQL 17，Roseto 目标使用 PostgreSQL 18：

```text
Logto source behavior baseline: PostgreSQL 17
Roseto runtime target: PostgreSQL 18
```

因此必须执行 PostgreSQL 17 → 18 的 schema、seed、RLS、trigger 和 integration compatibility test。不能把 `postgres:17-alpine` 改成 `postgres:18` 后直接宣称兼容。

## 3. Snapshot 产物

每次更新基线必须生成：

```text
logto-schema.sql
logto-seed-manifest.json
logto-route-manifest.json
logto-phrase-manifest.json
logto-snapshot-checksums.json
```

checksum 文件必须记录 source commit、生成命令和输入目录。

## 4. 更新门禁

Logto source commit 变化时，必须重新检查：

```text
schemas/tables
schemas/models
schemas/alterations
schemas/seeds
console routes and API calls
experience routes and API calls
phrases package
```

未更新 snapshot diff 前，不得修改 Roseto compatibility contract。
