# Roseto Phase 0 测试矩阵

## 1. 测试层级

| 层级 | 目标 | 依赖 |
|---|---|---|
| Domain unit | 状态转换、PKCE、claims、scope | 无 Spring、无数据库 |
| Application integration | Tenant 隔离、事务、错误映射 | PostgreSQL Testcontainer |
| Protocol integration | Discovery、authorize、token、userinfo | PostgreSQL + Redis |
| Resource server contract | issuer、JWKS、audience、scope | Roseto + Rose sample |
| Security regression | 重放、越权、泄漏、错误配置 | 全部必要组件 |

## 2. 核心矩阵

| ID | 场景 | 输入 | 预期 |
|---|---|---|---|
| T-01 | Tenant 隔离 | A 查询 B 的 user | 404 或 403，不返回数据 |
| T-02 | Application 隔离 | A 使用 B 的 client_id | 400/401 |
| T-03 | 精确 redirect URI | 完全匹配 | 授权成功 |
| T-04 | URI path 变化 | path 不同 | 拒绝且不重定向 |
| T-05 | URI query 变化 | query 不同 | 拒绝 |
| T-06 | 正常 PKCE | S256 + 正确 verifier | token 200 |
| T-07 | 错误 verifier | challenge 不匹配 | invalid_grant |
| T-08 | plain PKCE | method=plain | invalid_request |
| T-09 | code 重放 | 第二次兑换同一 code | invalid_grant |
| T-10 | code 过期 | expires_at < now | invalid_grant |
| T-11 | 并发兑换 | 两个并发 token 请求 | 只有一个成功 |
| T-12 | nonce | ID Token nonce 匹配 | 成功 |
| T-13 | issuer | 伪造 issuer | Resource Server 401 |
| T-14 | audience | 其他 API audience | Resource Server 401 |
| T-15 | scope 足够 | 包含所需 scope | Resource Server 2xx |
| T-16 | scope 不足 | 缺少 scope | Resource Server 403 |
| T-17 | Token 过期 | exp < now | 401 |
| T-18 | JWKS rotation | 新旧 kid | 新旧 Token 均在窗口内可验证 |
| T-19 | disabled user | 用户禁用后新授权 | 拒绝登录 |
| T-20 | 错误消息 | 不存在用户 / 错误密码 | 不泄漏差异 |
| T-21 | bootstrap 重放 | 二次使用 bootstrap | 拒绝 |
| T-22 | 私钥日志 | 触发签名错误 | 日志不含私钥 |

## 3. 数据库测试

- 每个 Repository 查询必须验证 tenant predicate。
- 跨 Tenant 外键引用必须失败。
- authorization code 状态更新必须是原子操作。
- `UNIQUE(application_id, uri)` 必须阻止重复 URI。
- signing key 每 Tenant 只能有一个 active key。
- Flyway 从空数据库执行成功，并可在已有数据库升级。

## 4. 运行门禁

```bash
./mvnw -DskipTests compile
./mvnw test
./mvnw verify
```

合并前必须通过：

```text
Domain unit tests
PostgreSQL Testcontainers
Redis Testcontainers
Protocol integration tests
Resource server contract tests
ArchUnit dependency checks
```

## 5. 测试数据原则

- 每个测试生成独立 Tenant。
- 不共享真实密码、私钥或 Token。
- Token 使用短 TTL fixture，不使用睡眠等待完成过期测试。
- 测试断言行为、状态码、claims 和隔离结果，不断言实现类名或 SQL 文本。

## 6. 修复项回归测试

```text
ManagementAuthTest
  → bootstrap token 只能成功一次
  → management audience 错误返回 401
  → management scope 不足返回 403
  → Token tenant 与路径不一致返回 403

ScopeResourceBindingTest
  → 未知 scope 返回 invalid_scope
  → openid 不授予业务 API 权限
  → api:read / api:write 映射到固定 audience

UserInfoAudienceTest
  → UserInfo audience Token 成功
  → Resource Server audience Token 访问 UserInfo 返回 401

LogoutSessionTest
  → 当前 cookie 只撤销当前 user/application Session
  → 非法 post_logout_redirect_uri 不重定向
  → 无 Session 时不影响其他 Session

KeyEncryptionTest
  → KMS 解密失败时生产启动失败
  → 日志和错误响应不包含私钥载荷
```
```text
InternationalizationCompatibilityTest
  → fallback language 和 Content-Language 正确

LogtoFrontendCompatibilityTest
  → 单端口加载 /console/ 和 /experience/
  → /api/.well-known/sign-in-exp 返回 Logto 字段
  → /api/.well-known/phrases 返回 LocalePhrase
  → Accept-Language、ui_locales 和 Content-Language 一致
  → PostgreSQL 18 schema diff 与固定 Logto snapshot 一致
```
