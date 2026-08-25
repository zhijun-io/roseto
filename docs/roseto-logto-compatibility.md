# Roseto Logto 兼容性规格

## 1. 兼容目标

Roseto 的兼容目标不是只兼容 OIDC，而是让 Logto Console 和 Hosted Sign-in Experience 在不修改前端源码的情况下，将 Roseto 作为后端使用。

兼容层必须覆盖：

```text
HTTP 路径
HTTP method
query / form / JSON 参数
响应 JSON 字段
状态码
错误码
Cookie
CORS / CSRF
Accept-Language / Content-Language
OIDC Discovery / JWKS / Token
Management API
Experience API
```

Logto OSS 源码行为是兼容参考；OIDC、OAuth 和 HTTP 安全规范优先于 Logto 非标准行为。

## 2. 兼容模式

Roseto 提供两个模式：

### Logto-compatible mode

- 使用 Logto 兼容表名、字段名、seed 数据和 route contract；
- 支持 Logto Console / Experience 前端；
- 不要求 Rose 依赖；
- MyBatis Plus mapper 直接映射兼容 schema。

### Native Roseto mode

- 使用 Roseto 自有 domain API；
- 可使用 Roseto 扩展表；
- 不承诺 Logto 前端无修改接入。

默认开发和验收使用 `Logto-compatible mode`。

## 3. 一致性边界

“数据库表结构和数据一致”定义为：

- Logto OSS 当前 schema 的表、列、类型、约束、索引、触发器、RLS policy 和 seed 语义必须兼容；
- Roseto 不删除、重命名或改变 Logto 兼容表中的字段语义；
- Logto seed 的默认 Tenant、admin Tenant、OIDC config、signing key、Management API resource / scope、Console Application 和 Organization 初始数据必须可重建；
- Roseto 扩展字段不得直接添加到兼容表，使用 `roseto_*` 扩展表或独立 namespace；
- 每次 Logto source snapshot 更新必须重新生成 schema diff 和 seed diff。

这不是“复制任意 Docker volume 的运行时 UUID”。数据一致指结构、默认值、关系、状态和 seed 语义一致；环境生成的私钥、cookie key 和随机 ID 必须重新生成。

## 4. 不依赖 Rose

Roseto 的 compile-time 依赖中不得出现 Rose。与 Rose 的兼容只通过：

```text
OIDC Discovery
JWKS
JWT claims
HTTP Resource Server contract
```

Roseto 后端可以独立启动、测试和发布。
