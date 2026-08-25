# Roseto Cloud Control Plane 需求

## 1. 范围

Cloud Control Plane 管理 Platform Account、Tenant 生命周期、Tenant membership、Region、Subscription、Quota 和 Usage。它不是 Tenant Core 的用户目录，也不替代 Tenant 内部 Management API。

## 2. 角色与权限

| 角色 | 主要能力 |
|---|---|
| Platform Admin | 创建、修改、删除 Tenant；管理 Region、Subscription、Quota |
| Platform Collaborator | 读取已授权 Tenant，执行被授予的 Tenant 操作 |
| Tenant Admin | 管理本 Tenant 成员、邀请和设置 |
| Tenant Collaborator | 执行被授予的成员和资源管理操作 |
| Billing Operator | 读取和更新订阅、发票、用量 |

## 3. 需求

### C-FR-01 Platform Account

- Platform Account 独立于 Tenant User。
- 登录成功后签发 audience 为 Cloud API 的 Platform Token。
- Platform Token 只能访问 membership 覆盖的 Tenant。
- Token 不接受客户端自行修改 tenantId、role 或 scope。

### C-FR-02 Tenant Provisioning

- 创建 Tenant 必须幂等。
- 创建事务同时生成 Tenant、creator membership、region assignment 和初始 subscription。
- 外部 provisioning 失败时状态为 `failed`，不得伪装为 `ready`。
- 删除是异步状态机，不直接硬删除正在使用的 Tenant。

### C-FR-03 Membership and Invitation

- Tenant Admin 可以邀请成员。
- invitation 只能被目标邮箱或已认证身份接受。
- invitation 过期后不可接受。
- 移除最后一个 Tenant Admin 必须拒绝。
- 跨 Tenant 读取和修改必须拒绝。

### C-FR-04 Subscription and Quota

- 每个 Tenant 至少有一个 subscription plan。
- quota 由 plan、add-on 和 override 计算。
- 超出 quota 的写操作返回稳定错误码。
- Billing 变更必须留下审计事件。

### C-FR-05 Usage

- Usage event 必须带 Tenant、usage key、quantity、时间和幂等键。
- 重试不能重复计费。
- 聚合任务可重复执行，结果不变。
- Usage 查询不能读取其他 Tenant 数据。

## 4. 状态机

```text
Tenant: requested → provisioning → ready → suspended → deleting → deleted
                         └────────→ failed
Invitation: pending → accepted | expired | revoked
Subscription: trialing → active → past_due → canceled
```

## 5. 验收

```text
Platform Account 登录
→ GET /api/tenants
→ POST /api/tenants with Idempotency-Key
→ creator membership 自动生成
→ GET /api/tenants/{id}/members
→ 邀请并接受成员
→ 无 membership 的请求返回 403
→ 重复请求不产生第二个 Tenant
→ provisioning 失败可观察且可重试
```

## 6. Tenant 删除行为（修正版）

```text
ready → deleting → deleted
ready → deleting → failed
suspended → deleting → deleted
```

`DELETE /api/tenants/{tenantId}` 返回 `202` 和当前 provisioning 状态。

`deleting` 状态：

- 禁止新用户登录；
- 禁止创建 Application、User、Invitation 和 Usage Event；
- 允许 Platform Admin 查询删除进度；
- 允许审计读取；
- 不允许普通 Tenant API 写入。

删除失败进入 `failed`，必须保存 `last_error_code` 和可重试时间。恢复操作只能由 Platform Admin 执行。

`deleted` 状态保留最小审计元数据，不再暴露业务用户和 Secret；资源清理完成后才允许物理清理。
