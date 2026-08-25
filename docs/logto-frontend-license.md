# Logto 前端复用与许可证规范

## 1. 适用范围

Logto Console、Experience 和相关 phrases package 当前 snapshot 标注为 MPL-2.0。Roseto 若分发修改后的前端构建产物，必须由发布流程保留对应许可证、版权和修改说明。

## 2. 使用模式

### 兼容测试

可以在本地构建 Logto 前端，指向 Roseto 后端进行 contract / browser test。测试产物不作为 Roseto 发布物分发。

### 发布修改版前端

如果发布包含 Logto 源码修改的 Console / Experience：

- 保留原始许可证和版权声明；
- 记录修改文件和修改内容；
- 按 MPL-2.0 要求提供适用的源代码或获取方式；
- 不把 Logto 品牌和 Roseto 品牌混写；
- 发布包附带 `NOTICE` 或许可证说明；
- 发布前完成单独的许可证审查。

## 3. Roseto 自有代码

Roseto 新写的 Java 后端、兼容层和前端修改必须有独立版权和许可证声明。Logto API 行为兼容不等于复制 Logto 源码。

## 4. 构建记录

每个发布版本必须记录：

```text
Logto source commit
packages included
modified files
license files
built asset checksum
Roseto backend compatibility version
```
