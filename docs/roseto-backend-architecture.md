# Roseto 单端口后端架构规格

## 1. 运行形态

Roseto 后端只监听一个 HTTP 端口：

```text
server.port=3000
```

同一端口提供：

```text
/console/*                         Console 静态资源
/experience/*                      Hosted Experience 静态资源
/api/*                             Management / Experience API
/oidc/*                            OIDC endpoints
/t/{tenantKey}/...                 Tenant-scoped OIDC endpoints
/.well-known/*                     Instance discovery
/health/*                          readiness / liveness
```

生产环境不要求独立 Node dev server、API port 或 CORS proxy。开发环境可以使用 Vite proxy，但最终验收必须使用单端口。

## 2. 三个 Spring Maven 模块

```text
roseto-domain
  └─ Tenant、User、Application、Authorization、Session、Cloud use cases / ports

roseto-infrastructure
  └─ MyBatis Plus、Flyway、PostgreSQL、Redis、key store、OIDC adapter

roseto-app
  └─ Spring Boot、HTTP controllers、compatibility layer、OIDC、
     Management API、Experience API、Console / Experience static resources
```

Cloud、Console、Experience、OIDC、Management API 都是 `roseto-app` 内的 package boundary，不单独创建 Maven module。

依赖方向：

```text
roseto-app → roseto-infrastructure → roseto-domain
roseto-app → roseto-domain
```

`roseto-domain` 不依赖 Spring Boot、MyBatis Plus、PostgreSQL、Redis、Rose 或 Web 层。

## 3. 请求处理顺序

```text
HTTP request
→ requestId / correlation ID
→ path / tenant resolver
→ authentication
→ TenantContext
→ compatibility controller
→ application service
→ domain port
→ MyBatis Plus repository
→ response mapper
```

Controller 不得直接调用 `BaseMapper`。TenantContext 必须在请求结束时清理，避免连接池线程污染。

## 4. 前端连接合同

Logto 前端配置 API base URL 为当前 origin。Roseto 必须保持前端实际调用的路径和 header：

```text
/api/.well-known/sign-in-exp
/api/.well-known/phrases
Accept-Language
Content-Language
Cookie
X-Request-ID
```

静态资源和 API 必须使用同源 URL，避免生产环境依赖 CORS。若提供独立前端开发端口，只允许通过 Vite proxy 转发到 3000。

## 5. 代码规范

- Java 21；
- Spring Boot 4.1；
- MyBatis Plus 作为唯一 ORM / mapper 框架；
- 禁止 JPA Entity 与 MyBatis Entity 混用；
- DTO、DO、Domain Object 分离；
- 所有公共 API 使用不可变 request / response DTO；
- Service 方法通过事务边界声明一致性；
- SQL 复杂查询放在 mapper XML，不在 Controller 拼接 SQL；
- 统一错误码和异常映射；
- 每个兼容接口必须有 contract test；
- 不为兼容 Logto 引入 Rose 依赖。

## 6. 单端口验收

```bash
./mvnw verify
./mvnw -pl roseto-app spring-boot:run
curl -f http://localhost:3000/health/readiness
curl -f http://localhost:3000/api/.well-known/phrases
curl -f http://localhost:3000/api/.well-known/sign-in-exp
curl -f http://localhost:3000/console/
curl -f http://localhost:3000/experience/
```
