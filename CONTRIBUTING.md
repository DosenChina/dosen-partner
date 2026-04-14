# 贡献指南

感谢您考虑为 dosen-partner 项目做出贡献！以下是贡献代码的流程和规范。

## 目录

- [贡献指南](#贡献指南)
  - [目录](#目录)
  - [行为准则](#行为准则)
  - [环境搭建](#环境搭建)
  - [开发流程](#开发流程)
  - [提交规范](#提交规范)
  - [代码风格](#代码风格)
  - [测试](#测试)
  - [PR 流程](#pr-流程)
  - [Issue 规范](#issue-规范)
  - [许可证](#许可证)

## 行为准则

请遵循我们的 [行为准则](CODE_OF_CONDUCT.md)，确保社区的友好和包容性。

## 环境搭建

1. **克隆仓库**

```bash
git clone https://github.com/DosenChina/dosen-partner.git
cd dosen-partner
```

2. **安装依赖**

```bash
mvn install
```

3. **配置开发环境**

创建 `.env` 文件，配置必要的环境变量（参考 README.md 中的环境变量配置）。

4. **运行开发服务器**

```bash
mvn spring-boot:run -pl partner-agent
```

## 开发流程

1. **创建分支**

从 `main` 分支创建新的特性分支或修复分支：

```bash
git checkout -b feature/your-feature-name
# 或
git checkout -b fix/your-fix-name
```

2. **编写代码**

实现您的特性或修复，确保代码符合项目的代码风格要求。

3. **运行测试**

确保您的代码通过所有测试：

```bash
mvn test -pl partner-agent
```

4. **提交代码**

使用符合规范的提交信息提交代码（参考 [提交规范](#提交规范)）。

5. **创建 PR**

推送您的分支到远程仓库并创建 Pull Request。

## 提交规范

我们使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范来格式化提交信息。提交信息应该遵循以下格式：

```
<类型>(<范围>): <描述>

[可选的正文]

[可选的脚注]
```

### 类型

- `feat`：新特性
- `fix`：修复 bug
- `docs`：文档更新
- `style`：代码风格调整（不影响代码功能）
- `refactor`：代码重构（不添加新特性或修复 bug）
- `test`：添加或更新测试
- `chore`：构建过程或辅助工具的变动

### 示例

```
feat(rag): 添加 Qdrant 向量存储支持

添加了对 Qdrant 向量数据库的集成，用于 RAG 功能的实现。

BREAKING CHANGE: 改变了向量存储的配置方式
```

## 代码风格

- 遵循 Java 代码风格规范
- 使用 4 个空格进行缩进
- 每行代码不超过 120 个字符
- 使用 Lombok 简化代码
- 为公共方法添加 Javadoc 注释

## 测试

- 为新功能编写单元测试
- 确保测试覆盖率不低于 80%
- 运行 `mvn test` 确保所有测试通过

## PR 流程

1. **创建 PR**

在 GitHub 上创建 Pull Request，填写 PR 描述，包括：

- 功能或修复的描述
- 相关的 Issue 编号（如果有）
- 测试结果
- 任何破坏性变更

2. **代码审查**

项目维护者会对您的 PR 进行代码审查，可能会要求您进行一些修改。

3. **合并**

当 PR 通过代码审查并通过所有测试后，项目维护者会将其合并到 `main` 分支。

## Issue 规范

### 提交 Issue

当您发现 bug 或有新功能建议时，请创建 Issue：

1. **Bug 报告**

- 描述 bug 的详细情况
- 提供复现步骤
- 包含预期行为和实际行为
- 提供相关的日志和截图

2. **功能请求**

- 描述您希望添加的功能
- 解释为什么这个功能对项目有价值
- 提供可能的实现方案

### Issue 标签

我们使用以下标签来分类 Issue：

- `bug`：bug 报告
- `feature`：新功能请求
- `enhancement`：功能增强
- `documentation`：文档相关
- `question`：问题咨询
- `help wanted`：需要帮助

## 许可证

通过贡献代码，您同意您的贡献将在 [Apache License 2.0](LICENSE) 下发布。
