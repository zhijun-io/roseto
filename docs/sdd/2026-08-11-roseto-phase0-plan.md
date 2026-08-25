# Roseto Phase 0 Implementation Plan

关联规格：`docs/sdd/2026-08-11-roseto-phase0-spec.md`

## Slice 状态

- [x] Slice 1：三模块 Maven 基线
- [ ] Slice 2：Tenant Bootstrap 与 Management JWT
- [ ] Slice 3：Application、User 与精确 Redirect URI
- [ ] Slice 4：Authorization Code + PKCE + 内置登录页
- [ ] Slice 5：Discovery、JWKS、Token 与 UserInfo
- [ ] Slice 6：Resource Server、Scope 与 Key Rotation

## Slice 1 结果

已创建：

```text
pom.xml
roseto-domain/
roseto-infrastructure/
roseto-app/
```

验证：

```text
/tmp/apache-maven-3.9.11/bin/mvn -q test  → 通过
```

结果：三模块编译通过，Spring Boot context test 通过。后续测试环境已统一切换为 PostgreSQL 与 Redis Testcontainers，不再使用 H2。

## 关闭验证

尚未执行。所有后续 slice 完成后运行完整 Maven 验证并补充结果。
