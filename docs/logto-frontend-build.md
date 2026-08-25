# Logto 前端构建与单端口发布

## 1. 构建来源

固定使用 Logto snapshot 中的：

```text
packages/console
packages/experience
```

当前 snapshot：

```text
commit abd6327065e0c178bc621c66fcfeb386bb49d7cf
console 1.39.0
experience 1.21.0
```

前端构建产物和源码必须遵守 Logto MPL-2.0 义务，具体见 `logto-frontend-license.md`。

## 2. 构建目标

```bash
pnpm --filter @logto/console build
pnpm --filter @logto/experience build
```

构建后将静态资源复制到：

```text
roseto-app/src/main/resources/static/console
roseto-app/src/main/resources/static/experience
```

Node / Vite 只参与构建，不参与生产运行。

## 3. API origin

生产前端必须使用当前 origin 的相对 API URL：

```text
/api/*
/oidc/*
```

不得把开发环境的：

```text
http://localhost:3001
http://localhost:3002
http://localhost:5001
http://localhost:5002
```

写入生产构建产物。

## 4. Spring 静态资源规则

```text
/console/                 → console/index.html
/console/assets/*         → console assets
/console/{route}          → console/index.html
/experience/              → experience/index.html
/experience/assets/*      → experience assets
/experience/{route}       → experience/index.html
```

以下路径不得返回 index.html：

```text
/api/*
/oidc/*
/.well-known/*
/health/*
```

未知 API 必须返回 JSON 404。

## 5. 构建验收

```text
构建产物不包含独立 API host
静态资源 checksum 可复现
Console 首页 200
Experience 首页 200
Console SPA 路由刷新 200
Experience SPA 路由刷新 200
API 404 返回 JSON
```
