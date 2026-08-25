# Roseto 标准与 Logto 参考对照

## 1. 使用规则

- 标准规范决定协议兼容性。
- Logto OSS 源码只作为产品行为和领域建模参考。
- Logto Cloud 行为不自动视为 OSS 能力。
- Roseto 不复制 Logto 源代码、品牌、文案或专有实现。

## 2. 对照表

| 能力 | 标准来源 | Logto 参考 | Roseto 决策 |
|---|---|---|---|
| Authorization Code | OAuth 2.1 | OIDC Provider flow | Phase 0 必须实现 |
| PKCE | RFC 7636 | Console/client flow 使用 S256 | 只接受 S256 |
| Discovery | OpenID Connect Discovery | `/.well-known/openid-configuration` | issuer-scoped |
| JWKS | RFC 7517 / OIDC | signing key endpoint | Phase 0 实现 rotation |
| ID Token | OIDC Core | OIDC claims | 以 OIDC 合同为准 |
| Access Token | OAuth 2.1 / JWT BCP | resource indicator / scopes | audience 固定 API Resource |
| UserInfo | OIDC Core | user profile endpoint | Phase 0 最小 `sub` |
| Tenant | 非标准产品概念 | Console / tenant model | Roseto 独立 domain |
| Organization | 非标准产品概念 | Organizations feature | Phase 2 后实现 |
| Cloud Tenant API | 非标准产品概念 | Cloud API route types | 独立 Cloud Control Plane |
| Subscription / Billing | 非标准产品概念 | Cloud API / Console | Phase 6，不能假设 OSS 后端 |
| Usage | 非标准产品概念 | Cloud metering APIs | 自主实现事件和聚合 |

## 3. 官方来源

### OAuth / OIDC

- OAuth 2.1 draft: https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1
- PKCE RFC 7636: https://www.rfc-editor.org/rfc/rfc7636
- OAuth Security BCP: https://www.rfc-editor.org/rfc/rfc9700
- OpenID Connect Core: https://openid.net/specs/openid-connect-core-1_0.html
- OpenID Discovery: https://openid.net/specs/openid-connect-discovery-1_0.html
- JWT RFC 7519: https://www.rfc-editor.org/rfc/rfc7519
- JWK RFC 7517: https://www.rfc-editor.org/rfc/rfc7517

### Logto

- Logto repository: https://github.com/logto-io/logto
- Authorization: https://docs.logto.io/authorization
- User management: https://docs.logto.io/user-management
- Organizations: https://docs.logto.io/organizations
- Connectors: https://docs.logto.io/connectors
- Sign-in Experience: https://docs.logto.io/concepts/sign-in-experience
- Sessions: https://docs.logto.io/sessions

## 4. 证据要求

实现文档引用 Logto 行为时必须记录：

```text
source repository or URL
commit/tag/date
file path or endpoint
observed behavior
whether OSS or Cloud
Roseto decision
```

不确定的行为标记为 `UNVERIFIED`，不得写入 MUST 合同。

## 5. 当前证据登记

| 能力 | Evidence | Observed at | Confidence | Roseto 处理 |
|---|---|---|---|---|
| Tenant `db_user` / RLS | `/tmp/logto-source/packages/cli/alteration-scripts/1.0.0_rc.1-1675788753-multi-tenancy-rls.js:50-107` | 本地 Logto OSS source snapshot | VERIFIED | 不复制到 Phase 0；保留为 RLS 备选研究 |
| Tenant schema fields | `packages/schemas/src/models/tenants.ts`；整理见 `logto-source-domain-model.md:59-75` | 本地 Logto OSS source snapshot | VERIFIED | 只作为参考字段，不作为 Roseto schema 合同 |
| Cloud route types | `packages/console/node_modules/@logto/cloud/lib/routes/index.d.ts` | 本地 Logto OSS package snapshot | VERIFIED | 仅证明 route/type 形状，不证明 Cloud backend 行为 |
| Cloud authorization | Cloud API 请求返回 missing Authorization 时为 401 | 本地运行验证记录 | VERIFIED | Roseto Cloud API 使用独立 Platform Token |
| Billing / Usage backend semantics | 只有 Cloud route/type 或前端调用线索 | 当前未取得完整 OSS backend 实现 | UNVERIFIED | 不写入 Roseto MUST 合同，必须自主定义 |

任何引用 Logto 行为的新增内容都必须注明 OSS / Cloud 边界，并且未达到 `VERIFIED` 的内容不得作为协议兼容要求。
