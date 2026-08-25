# Roseto 文档导航

这些文档共同描述 Roseto 的产品方案，以及 Logto 的参考模型、用户流程和 API 实现线索。

## 推荐阅读顺序

```text
1. roseto-plan.md
   确定产品边界、架构约束和阶段顺序

2. roseto-phase0-requirements.md
   冻结 Phase 0 功能需求和验收场景

3. roseto-phase0-schema.md
   冻结 Phase 0 数据表、约束和 migration 顺序

4. roseto-phase0-api.md
   阅读 OIDC、Management API 和错误合同

5. openapi/roseto-phase0.yaml
   使用机器可读的 Phase 0 API 定义

6. roseto-phase0-test-matrix.md
   按测试矩阵实现验证

7. roseto-cloud-requirements.md
   在 Phase 0 完成后阅读 Cloud Control Plane 需求

8. roseto-cloud-schema.md / roseto-cloud-api.md
   冻结 Cloud 数据和接口合同

9. reference-matrix.md
   区分标准规范、Logto OSS 事实和 Cloud 未验证行为
```

## 文档职责

| 文档 | 回答的问题 | 不负责的内容 |
|---|---|---|
| [`roseto-plan.md`](roseto-plan.md) | Roseto 要做什么，阶段如何划分？ | 不作为字段级实现合同 |
| [`roseto-phase0-requirements.md`](roseto-phase0-requirements.md) | Phase 0 必须交付哪些用户可观察行为？ | 不定义 Cloud 功能 |
| [`roseto-phase0-schema.md`](roseto-phase0-schema.md) | Phase 0 如何持久化？ | 不定义完整 Phase 1 授权模型 |
| [`roseto-phase0-api.md`](roseto-phase0-api.md) | Phase 0 OIDC 和管理 API 如何交互？ | 不替代 OpenAPI 校验 |
| [`openapi/roseto-phase0.yaml`](openapi/roseto-phase0.yaml) | API 的机器可读合同是什么？ | 不描述数据库内部实现 |
| [`roseto-phase0-test-matrix.md`](roseto-phase0-test-matrix.md) | 如何证明 Phase 0 已完成？ | 不替代实现代码 |
| [`roseto-cloud-requirements.md`](roseto-cloud-requirements.md) | Cloud Control Plane 提供什么行为？ | 不描述 Tenant Core 用户目录 |
| [`roseto-cloud-schema.md`](roseto-cloud-schema.md) | Cloud 数据如何持久化和保持幂等？ | 不定义前端页面 |
| [`roseto-cloud-api.md`](roseto-cloud-api.md) | Cloud API 如何认证和返回结果？ | 不描述计费供应商内部实现 |
| [`reference-matrix.md`](reference-matrix.md) | 哪些是标准，哪些是 Logto 参考？ | 不作为 Roseto 功能需求 |
| [`roseto-logto-compatibility.md`](roseto-logto-compatibility.md) | Roseto 兼容 Logto 的边界是什么？ | 不复制 Logto 私有实现 |
| [`roseto-internationalization.md`](roseto-internationalization.md) | Logto 前端如何选择语言和加载 phrases？ | 不替代完整翻译内容 |
| [`roseto-logto-database-compatibility.md`](roseto-logto-database-compatibility.md) | 如何保持 PostgreSQL schema / seed 兼容？ | 不把 Docker 随机数据当固定数据 |
| [`roseto-backend-architecture.md`](roseto-backend-architecture.md) | 如何用 MyBatis Plus 单端口提供兼容后端？ | 不定义产品领域需求 |
| [`logto-compatibility-snapshot.md`](logto-compatibility-snapshot.md) | 当前 Logto 版本和 source snapshot 是什么？ | 不代表发布许可 |
| [`logto-schema-inventory.md`](logto-schema-inventory.md) | 如何生成和比较完整 schema 清单？ | 不替代实际 migration |
| [`logto-frontend-route-manifest.md`](logto-frontend-route-manifest.md) | Logto 前端实际调用哪些接口？ | 不实现接口 |
| [`logto-frontend-build.md`](logto-frontend-build.md) | 如何把前端构建产物放进单端口后端？ | 不定义后端业务合同 |
| [`roseto-mybatis-plus-conventions.md`](roseto-mybatis-plus-conventions.md) | MyBatis Plus 如何映射兼容 schema？ | 不替代 domain 设计 |
| [`logto-frontend-license.md`](logto-frontend-license.md) | 如何合法复用 Logto 前端？ | 不构成法律意见 |
| [`logto-user-flow.md`](logto-user-flow.md) | 管理员和终端用户如何使用 Logto？ | 不作为接口字段级规范 |
| [`logto-api-flow.md`](logto-api-flow.md) | Logto API 如何校验、改变状态并签发 Token？ | 不抄录全部 OpenAPI 定义 |

## 统一术语

- **Tenant**：数据和配置隔离边界。
- **Platform / admin Tenant**：控制面管理身份和 Tenant 的上下文；不等于业务终端用户。
- **Application**：OIDC / OAuth Client。
- **User**：Tenant-local 用户主体。
- **User Identity**：用户的密码、Email、Phone、Social 或 Enterprise SSO 登录身份。
- **Resource**：业务 API 的 audience / resource indicator。
- **Scope / Permission**：访问 API 的具体权限。
- **Role**：Permission 的集合。
- **Session**：浏览器或登录交互的认证状态。
- **Grant**：用户、Client、Scope 和 Token 生命周期的授权关系。
- **Interaction**：Hosted Sign-in Experience 中尚未完成的注册或登录交互。

## 使用规则

1. 方案冲突时，以 `roseto-plan.md` 中的 Roseto 已决策为准。
2. Logto 字段和流程事实以源码路径、SQL schema、OpenAPI 和集成测试为准。
3. `logto-user-flow.md` 适合产品和流程理解；`logto-api-flow.md` 适合实现和接口拆解。
4. 参考 Logto 的协议、模型和公开行为，但不复制 Logto 源代码、文案、品牌资源或专有实现。
5. Logto 源码版本变化后，需要重新核对字段和 API 行为，不假设文档永久兼容。
