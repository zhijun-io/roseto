# Roseto Phase 0 Spec

## 1. 目标

实现一个可被标准 OIDC Client 接入的 Roseto 最小身份平台：

```text
Tenant
→ Application
→ Password User
→ Authorization Code + PKCE
→ ID Token / Access Token
→ Discovery / JWKS
→ UserInfo
→ Resource Server 验证
```

Roseto 是独立构建、测试和发布的 Java 单体应用，不引入 Rose 依赖。

## 2. 范围

### 包含

- Tenant bootstrap；
- Web Application registration；
- 精确 redirect URI 校验；
- Password User；
- 内置最小 HTML 登录页；
- Authorization Code + PKCE；
- OIDC Discovery；
- JWKS；
- ID Token；
- Access Token；
- UserInfo；
- Logout；
- Signing key rotation；
- Management API；
- 管理 JWT；
- 应用层 `TenantContext`；
- SQL 显式 `tenant_id`；
- Spring Security Resource Server sample fixture；
- PostgreSQL 18；
- MyBatis Plus；
- Flyway；
- Redis 健康检查和必要状态存储。

### 不包含

- Refresh Token；
- Social / OIDC / SAML Connector；
- Organization；
- MFA；
- 完整 Console；
- Cloud Control Plane；
- SCIM；
- 多区域；
- 微服务拆分；
- Rose Maven 依赖。

## 3. 模块

```text
roseto-domain
  └─ domain、application、spi

roseto-infrastructure
  └─ MyBatis Plus、Flyway、PostgreSQL、Redis、密钥和密码实现

roseto-app
  └─ Spring Boot、HTTP、OIDC、Management API、内置登录页
```

依赖方向：

```text
roseto-app → roseto-infrastructure → roseto-domain
roseto-app → roseto-domain
```

`roseto-domain` 不依赖 Spring、MyBatis Plus、PostgreSQL、Redis 或 Rose。

## 4. 认证与管理入口

### Management API

首次启动使用一次性 bootstrap secret 创建管理账户。

之后使用独立 audience 的管理 JWT：

```text
aud = roseto-management
```

管理 JWT 不得作为 End User 登录凭据。

### 用户登录

`roseto-app` 提供最小 HTML 登录页，不先接入 Console / Experience 前端。

登录页只负责用户名和密码提交；认证事务、授权码和会话由后端管理。

## 5. 租户隔离

Phase 0 使用：

```text
TenantContext
+ 所有 SQL 显式 tenant_id
```

要求：

- tenant 从认证上下文或可信路径解析；
- 不接受客户端任意传入的 `tenantId` 覆盖上下文；
- Repository 查询必须显式带 `tenant_id`；
- 缓存 key 必须包含 tenant；
- 请求结束时清理 `TenantContext`；
- Phase 0 不启用 PostgreSQL RLS。

## 6. Acceptance Criteria

- **AC-01** Tenant bootstrap 能创建首个 Tenant 和管理主体，并在成功后关闭 bootstrap 通道。
- **AC-02** Application 只能使用已登记的精确 redirect URI。
- **AC-03** Authorization Code 流程必须要求 PKCE。
- **AC-04** 缺失、错误或已使用的 code_verifier 必须拒绝 token 请求。
- **AC-05** Authorization Code 只能消费一次，并绑定 tenant、client、redirect URI 和用户。
- **AC-06** Discovery 返回正确的 issuer、authorization_endpoint、token_endpoint、userinfo_endpoint 和 jwks_uri。
- **AC-07** JWKS 能验证 Roseto 签发的 ID Token 和 Access Token。
- **AC-08** Access Token 的 issuer、audience、tenant 和过期时间校验失败时请求被拒绝。
- **AC-09** scope 不足时 Resource Server 返回 403，token 无效或过期时返回 401。
- **AC-10** Signing key rotation 后，新 token 使用新 kid；旧 token 在有效期内仍可验证。
- **AC-11** Management JWT 只能访问 Management API，不能替代 End User Access Token。
- **AC-12** 跨 tenant 的用户、Application、授权码和会话访问必须被拒绝。
- **AC-13** 密码、私钥、bootstrap secret、authorization code 和 token 不得明文写入日志或 API 响应。
- **AC-14** Roseto 可以在不引入 Rose 的情况下完成编译、测试、启动和 Resource Server 集成验证。
- **AC-15** PostgreSQL 18 migration、MyBatis Plus 映射和关键流程通过 Testcontainers 集成测试。

## 7. 验收场景

```text
创建 Tenant
→ 注册 Application
→ 配置 redirect URI
→ 创建 Password User
→ 浏览器访问 /authorize
→ 内置 HTML 登录
→ 返回 authorization code
→ 使用 PKCE 换取 token
→ 调用 UserInfo
→ sample API 验证 JWT
→ 验证 scope 403
→ 验证过期 token 401
→ 轮换 signing key
→ 验证旧 kid 和新 kid
```

## 8. 技术约束

- Java 21；
- Spring Boot 4.1，正式建项目前确认可用版本；
- PostgreSQL 18；
- Redis；
- MyBatis Plus 只负责 persistence adapter；
- Flyway 负责 schema migration；
- Spring Authorization Server 只位于 infrastructure / protocol adapter 层；
- 不复制 Logto 源代码；
- Logto 仅作为行为、字段和协议兼容参考。
