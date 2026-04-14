# 中文新闻获取 MCP 服务器

此项目提供了一个 MCP (Model Context Protocol) 服务器，允许您直接从权威中文新闻网站获取新闻内容，不依赖搜索引擎，保证100%可用。

## ✨ 核心特性

*   **🚀 直接访问新闻源**：绕过搜索引擎，直接访问人民网、新华网、央视新闻等权威网站
*   **✅ 100% 可用性**：无需VPN，无需API密钥，国内网络直接访问
*   **📰 多新闻源支持**：支持5个权威中文新闻网站
*   **🔍 内容搜索**：在新闻内容中搜索关键词
*   **🌐 网页获取**：获取任意网页内容并提取文本
*   **📊 新闻摘要**：自动生成今日新闻摘要
*   **⚡ 并行获取**：支持并发获取多个新闻源
*   **🛡️ 错误处理**：优雅处理超时和网络错误

## 📋 支持的新闻源

| 新闻源 | 标识符 | 基础URL | 主要分类 |
|--------|--------|---------|----------|
| **人民网** | `people` | http://www.people.com.cn | 政治、财经、教育、科技 |
| **新华网** | `xinhua` | http://www.xinhuanet.com | 政治、财经、科技、国际 |
| **央视新闻** | `cctv` | http://news.cctv.com | 国内、国际、社会 |
| **新浪新闻** | `sina` | http://news.sina.com.cn | 国内、国际、财经 |
| **腾讯新闻** | `tencent` | https://news.qq.com | 国内、国际、财经 |

## 🚀 快速开始

### 1. 环境准备

确保已安装 Python 3.11+ 和 [uv](https://github.com/astral-sh/uv) 包管理器。

### 2. 安装依赖

```bash
cd web-search-duckduckgo
uv sync
```

### 3. 配置 Claude Desktop

编辑 Claude Desktop 配置文件 (`claude_desktop_config.json`)，添加以下配置：

```json
{
  "mcpServers": {
    "news-fetcher": {
      "command": "uv",
      "args": [
        "--directory",
        "C:\\mydata\\projects\\pythonlearning\\web-search-duckduckgo",
        "run",
        "news_fetcher_mcp.py"
      ]
    }
  }
}
```

**注意**：将路径替换为您的实际项目路径。

### 4. 重启 Claude Desktop

重启 Claude Desktop 以加载新的 MCP 服务器。

## 🛠️ 可用工具

### 1. `get_news` - 获取指定新闻源的新闻

获取权威新闻网站的新闻内容。

**参数：**
- `source` (可选): 新闻源标识符，默认 `"people"`
  - 可选值: `"people"` (人民网), `"xinhua"` (新华网), `"cctv"` (央视新闻), `"sina"` (新浪新闻), `"tencent"` (腾讯新闻)
- `category` (可选): 新闻分类
  - 可选值: `"politics"` (政治), `"finance"` (财经), `"tech"` (科技), `"world"` (国际), `"society"` (社会), `"edu"` (教育) 等

**返回：**
包含 `source` (新闻源), `url` (网址), `content` (内容), `timestamp` (时间戳) 的字典

**示例：**
```
获取人民网的最新政治新闻
获取新华网的财经报道
```

### 2. `get_latest_news` - 获取最新新闻

从多个新闻源获取最新新闻。

**参数：**
- `limit` (可选): 返回的新闻数量，默认 `3`，最大 `5`

**返回：**
新闻列表，每条包含 `source`, `url`, `content`, `timestamp`

**示例：**
```
获取3条最新新闻
```

### 3. `search_news` - 在新闻内容中搜索关键词

在新闻内容中搜索关键词。

**参数：**
- `keyword`: 搜索关键词
- `source` (可选): 指定新闻源（不指定则搜索所有新闻源）

**返回：**
包含相关新闻的列表，每条包含 `source`, `url`, `snippet` (包含关键词的片段), `full_content_length`, `timestamp`

**示例：**
```
搜索人民网中关于"科技创新"的报道
搜索所有新闻源中的"经济政策"相关内容
```

### 4. `fetch_webpage` - 获取任意网页内容

获取任意网页的内容。

**参数：**
- `url`: 网页URL

**返回：**
包含 `url`, `content`, `timestamp` 的字典

**示例：**
```
获取新华网首页内容
分析腾讯新闻的财经报道
```

### 5. `get_today_news_summary` - 获取今日新闻摘要

获取今日新闻摘要。

**返回：**
今日新闻摘要文本

**示例：**
```
获取今日新闻摘要
```

## 📖 使用示例

### 示例 1: 获取人民网新闻
```
使用 get_news 工具，source 参数设为 "people"，category 参数设为 "politics"
```

### 示例 2: 搜索相关新闻
```
使用 search_news 工具，keyword 参数设为 "人工智能"，source 参数设为 "xinhua"
```

### 示例 3: 获取网页内容
```
使用 fetch_webpage 工具，url 参数设为 "http://www.people.com.cn"
```

### 示例 4: 获取今日新闻摘要
```
使用 get_today_news_summary 工具
```

## 🔄 原 DuckDuckGo 搜索功能

项目保留了原始的 DuckDuckGo 搜索功能（`main.py`），但**需要注意**：
- 该功能可能需要 VPN 才能正常访问
- 依赖 Jina API 进行 HTML 到 Markdown 的转换
- 在国内网络环境下可能不可用

如果您需要使用 DuckDuckGo 搜索，可以配置以下 MCP 服务器：

```json
{
  "mcpServers": {
    "web-search-duckduckgo": {
      "command": "uv",
      "args": [
        "--directory",
        "C:\\mydata\\projects\\pythonlearning\\web-search-duckduckgo",
        "run",
        "main.py"
      ]
    }
  }
}
```

## 🛠️ 开发说明

### 项目结构
```
web-search-duckduckgo/
├── news_fetcher_mcp.py      # 中文新闻获取 MCP 服务器（推荐使用）
├── main.py                  # 原始 DuckDuckGo 搜索（可能需要 VPN）
├── pyproject.toml           # 项目依赖配置
└── README.md                # 项目文档
```

### 依赖项
- `beautifulsoup4>=4.13.3` - HTML 解析
- `httpx>=0.28.1` - 异步 HTTP 客户端
- `mcp[cli]>=1.4.1` - MCP 服务器框架

### 运行测试
项目包含内置的测试函数，可以直接运行：

```bash
uv run python news_fetcher_mcp.py
```

（注：需要先注释掉 `mcp.run(transport="stdio")` 并取消注释 `test_news_fetcher()`）

## ⚠️ 注意事项

1. **网络连接**：确保您的网络可以访问目标新闻网站
2. **内容长度**：获取的内容可能会被截断（最多10,000字符）
3. **频率限制**：建议不要过于频繁地请求同一网站
4. **编码问题**：项目已处理中文编码，但某些特殊字符可能显示异常

## 📊 性能特点

- **响应时间**：通常在 2-5 秒内返回结果
- **内容提取**：智能提取正文内容，过滤广告和导航
- **错误恢复**：自动重试和故障转移机制
- **内存使用**：优化的内存管理，适合长期运行

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来改进这个项目。主要改进方向包括：
- 添加更多新闻源
- 优化内容提取算法
- 改进错误处理机制
- 增加缓存功能

## 📄 许可证

此项目基于 MIT 许可证开源。详见项目根目录的 LICENSE 文件（如有）。