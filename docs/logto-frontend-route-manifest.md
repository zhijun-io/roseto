# Logto 前端 Route Manifest

## 1. 目标

Roseto 必须用 contract test 验证 Logto Console 和 Experience 的真实请求，而不是只验证 OIDC endpoint。

## 2. Experience 必需接口

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/.well-known/sign-in-exp` | Tenant sign-in experience settings |
| GET | `/api/.well-known/phrases` | LocalePhrase 和 Content-Language |
| POST | `/api/experience/interaction` | 创建或恢复 interaction |
| GET | `/api/experience/interaction` | 读取当前 interaction |
| POST | `/api/experience/verification/{type}` | 发送 / 校验 verification |
| POST | `/api/experience/submit` | 提交登录或注册动作 |
| GET | `/oidc/auth` | OIDC authorization entry |
| POST | `/oidc/token` | Token exchange |
| GET | `/oidc/jwks` | JWKS |

实际路径必须从固定 snapshot 的 Experience API imports 和 route declarations 生成；上表是最小验收集合，不得替代生成清单。

## 3. Console 必需接口

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/status` | Backend status |
| GET | `/api/tenants` | Tenant list |
| POST | `/api/tenants` | Create Tenant |
| GET | `/api/applications` | Application list |
| POST | `/api/applications` | Create Application |
| GET | `/api/resources` | Resource list |
| GET | `/api/roles` | Role list |
| GET | `/api/users` | User list |
| GET | `/api/organizations` | Organization list |
| GET | `/api/sign-in-experience` | Sign-in Experience settings |

Console API 的完整 path、query 和 response 必须从 `packages/console/src` 的 API client 和固定 snapshot route types 自动导出。

## 4. Static routes

```text
GET /console/
GET /console/{spa-route}
GET /console/assets/{asset}
GET /experience/
GET /experience/{spa-route}
GET /experience/assets/{asset}
```

只有 `/console/{spa-route}` 和 `/experience/{spa-route}` 允许 SPA fallback。以下路径禁止 fallback 到 HTML：

```text
/api/*
/oidc/*
/.well-known/*
/health/*
```

## 5. Manifest 字段

每条生成的 route 必须记录：

```text
method
path
sourceFile
authentication
tenantContext
requestSchema
responseSchema
errorSchema
compatibilityStatus
```

## 6. 验收

```text
构建 Logto Console / Experience
→ 指向 Roseto 单端口 origin
→ 浏览器加载页面
→ 捕获所有 same-origin API 请求
→ 请求均命中 Roseto manifest
→ 无 404、HTML 伪装 JSON、CORS 错误或错误 redirect
```
