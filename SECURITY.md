# 安全披露政策

## 漏洞披露流程

我们重视项目的安全性，欢迎安全研究人员和用户报告潜在的安全漏洞。以下是漏洞披露的流程：

### 报告方式

请通过以下方式报告安全漏洞：

- **电子邮件**：zzggcheng@outlook.com
- **GPG 加密**：如果您希望加密您的报告，请使用我们的 GPG 密钥（见下方）

### 报告内容

请在报告中包含以下信息：

- 漏洞的详细描述
- 复现步骤
- 受影响的版本
- 潜在的影响范围
- 可能的修复方案（如果有）

### 响应时间

我们承诺在收到漏洞报告后的 24 小时内确认收到，并在 7 天内提供初步分析结果。

### 修复时间

- **严重漏洞**：7 天内提供修复
- **高危漏洞**：14 天内提供修复
- **中危漏洞**：30 天内提供修复
- **低危漏洞**：在下一个版本中修复

### 公开披露

在漏洞修复后，我们会在以下渠道公开披露：

- 项目的 GitHub Issues
- 项目的 CHANGELOG.md 文件
- 项目的安全公告

## GPG 密钥

用于加密安全漏洞报告的 GPG 密钥：

```
-----BEGIN PGP PUBLIC KEY BLOCK-----

mDMEad5AwBYJKwYBBAHaRw8BAQdAoXIhVoJYKDk9JmFKQCVRKQxu8+Uf99cfgtzt
vQWre3i0KEd1YW5nY2hlbmcgemhhbmcgPHp6Z2djaGVuZ0BvdXRsb29rLmNvbT6I
mQQTFgoAQRYhBM9LnQ+Xhw4Iz94/PCTlasnKAsq9BQJp3kDAAhsDBQkFpGcABQsJ
CAcCAiICBhUKCQgLAgQWAgMBAh4HAheAAAoJECTlasnKAsq9A9cA/1cCDnEySj8m
L0IUETpTB7qNJlqEO2Z6gyo4BT6Tkz1zAP0Wp8a36ODLoC8Y93EqgSLU9ULowWOh
AOeU0SZieLyfCrg4BGneQMASCisGAQQBl1UBBQEBB0BKoslsb3eeMxL/v/diGMru
HdEhgiSWB1SRkgz97buHdgMBCAeIfgQYFgoAJhYhBM9LnQ+Xhw4Iz94/PCTlasnK
Asq9BQJp3kDAAhsMBQkFpGcAAAoJECTlasnKAsq9R+EA/173WeVotDCDLj13EmHa
uQq2BeLJ4/vf/fl8/+pAN3HuAPwNdkjR8Ctnw/w/4S9dCJZQaGJt6af+wXX9BIE6
Th17Bg==
=ZrAx
-----END PGP PUBLIC KEY BLOCK-----
```

## 漏洞赏金计划

目前，我们暂未启动漏洞赏金计划。但我们会对报告漏洞的安全研究人员表示感谢，并在修复公告中提及他们的贡献（如果他们同意）。

## 安全更新

我们会定期更新项目依赖，以修复已知的安全漏洞。建议用户及时更新到最新版本。

## 安全最佳实践

我们建议用户：

- 保持项目依赖的更新
- 使用强密码和安全的认证方式
- 限制对敏感资源的访问
- 定期备份数据
- 监控系统日志，及时发现异常行为
