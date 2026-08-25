# Roseto 国际化实现规格

## 1. Logto 当前实现事实

Logto Console 使用 `i18next`、`react-i18next` 和浏览器语言检测：

```text
packages/console/src/i18n/init.ts:8-38
```

关键行为：

- fallback language 为 `en`；
- 语言偏好保存于 localStorage / sessionStorage；
- Console 资源分为 `translation`、`errors`、`experience` namespace；
- Hosted Experience 使用 `/api/.well-known/phrases` 动态加载语言资源；
- 请求使用 `Accept-Language`；
- 响应使用 `Content-Language`；
- `ui_locales` 优先于浏览器检测结果；
- `ui_locales` 支持空格分隔的语言优先级列表；
- 不支持的语言回退到 Tenant fallback language，再回退 `en`。

源码证据：

```text
packages/experience/src/i18n/utils.ts:12-37
packages/experience/src/i18n/utils.ts:59-87
packages/experience/src/apis/settings.ts:40-63
packages/core/src/i18n/detect-language.ts:9-48
```

## 2. Roseto API 合同

```http
GET /api/.well-known/phrases?lng=zh-CN
Accept-Language: zh-CN,zh;q=0.9,en;q=0.8
```

成功响应：

```text
200 OK
Content-Type: application/json
Content-Language: zh-CN
Vary: Accept-Language
```

响应 JSON 必须保持 Logto `LocalePhrase` 结构，不能转换成 Roseto 自有 `{key, value}` 数组。

## 3. 语言选择算法

按以下顺序选择最终语言：

```text
1. query.lng 或 OIDC ui_locales 第一项
2. Accept-Language 按 q 值降序排列
3. Tenant sign-in experience.languageInfo.fallbackLanguage
4. en
```

语言 tag 使用 BCP 47。匹配支持：

```text
zh-CN → zh-CN → zh → fallback
fr-CA → fr-CA → fr → fallback
```

不允许把 `zh_CN`、任意脚本字符串或未注册语言直接作为 Content-Language 返回。

## 4. 数据模型

兼容 Logto 的 Sign-in Experience 数据必须保留 `languageInfo`：

```json
{
  "autoDetect": true,
  "fallbackLanguage": "en"
}
```

语言包分为：

```text
Console built-in phrases
Experience built-in phrases
Tenant custom phrases
Error phrase keys
```

Tenant custom phrases 必须按 Tenant 隔离、可缓存并支持版本失效。缓存 key 至少包含：

```text
tenant_id
language_tag
phrase_namespace
phrase_version
```

## 5. 前端兼容验收

必须验证：

```text
Console 首次加载默认英文
Console 切换语言后刷新仍保持语言
Experience 使用 ui_locales=fr-CA fr 时优先返回 fr-CA 或 fr
Accept-Language q 值影响选择
不支持语言回退到 Tenant fallbackLanguage
响应包含正确 Content-Language
Phrase 接口失败时前端可回退内置 en
```
