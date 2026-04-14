# MCP服务器安装指南

本文档提供了安装和运行 thirdmcpserver 目录下 MCP 服务器的基本流程，包括 Node.js 21+ 和 Python 3 的安装步骤。

## 目录

- [MCP服务器安装指南](#mcp服务器安装指南)
  - [目录](#目录)
  - [环境要求](#环境要求)
  - [Python 3 安装](#python-3-安装)
  - [Node.js 21+ 安装](#nodejs-21-安装)
  - [运行 MCP 服务器](#运行-mcp-服务器)
    - [MySQL MCP 服务器](#mysql-mcp-服务器)
    - [News Fetcher MCP 服务器](#news-fetcher-mcp-服务器)
  - [故障排除](#故障排除)

## 环境要求

- Python 3.8+（用于运行 Python 编写的 MCP 服务器）
- Node.js 21+（用于运行 Node.js 编写的 MCP 服务器）
- npm 或 yarn（Node.js 包管理器）

## Python 3 安装

### Windows 系统

1. **下载安装包**
   - 访问 [Python 官方网站](https://www.python.org/downloads/windows/)
   - 下载 Python 3.8+ 的最新版本
   - 选择与您系统匹配的安装包（64位或32位）

2. **安装 Python**
   - 运行安装包
   - 勾选 "Add Python to PATH" 选项
   - 点击 "Install Now" 完成安装

3. **验证安装**
   - 打开命令提示符
   - 运行 `python --version` 或 `python3 --version`
   - 运行 `pip --version` 验证 pip 包管理器是否安装

### macOS 系统

1. **使用 Homebrew 安装**
   - 打开终端
   - 运行 `brew install python`

2. **验证安装**
   - 运行 `python3 --version`
   - 运行 `pip3 --version`

### Linux 系统

1. **使用包管理器安装**
   - Ubuntu/Debian: `sudo apt update && sudo apt install python3 python3-pip`
   - CentOS/RHEL: `sudo yum install python3 python3-pip`
   - Fedora: `sudo dnf install python3 python3-pip`

2. **验证安装**
   - 运行 `python3 --version`
   - 运行 `pip3 --version`

## Node.js 21+ 安装

### Windows 系统

1. **下载安装包**
   - 访问 [Node.js 官方网站](https://nodejs.org/en/download/)
   - 下载 Node.js 21+ 的最新版本
   - 选择 Windows 安装包

2. **安装 Node.js**
   - 运行安装包
   - 按照安装向导完成安装

3. **验证安装**
   - 打开命令提示符
   - 运行 `node --version`
   - 运行 `npm --version`

### macOS 系统

1. **使用 Homebrew 安装**
   - 打开终端
   - 运行 `brew install node@21`
   - 运行 `brew link --overwrite node@21`

2. **验证安装**
   - 运行 `node --version`
   - 运行 `npm --version`

### Linux 系统

1. **使用 nvm 安装**（推荐）
   - 安装 nvm: `curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash`
   - 重启终端
   - 安装 Node.js 21: `nvm install 21`
   - 设置默认版本: `nvm use 21`

2. **使用包管理器安装**
   - Ubuntu/Debian: 
     ```bash
     curl -fsSL https://deb.nodesource.com/setup_21.x | sudo -E bash -
     sudo apt-get install -y nodejs
     ```
   - CentOS/RHEL:
     ```bash
     curl -fsSL https://rpm.nodesource.com/setup_21.x | sudo bash -
     sudo yum install -y nodejs
     ```

3. **验证安装**
   - 运行 `node --version`
   - 运行 `npm --version`

## 运行 MCP 服务器

### MySQL MCP 服务器

1. **进入目录**
   ```bash
   cd thirdmcpserver/mysql_mcp_server
   ```

2. **安装依赖**
   ```bash
   pip install -r requirements.txt
   ```

3. **运行服务器**
   ```bash
   python -m src.mysql_mcp_server.server
   ```

### News Fetcher MCP 服务器

1. **进入目录**
   ```bash
   cd thirdmcpserver/news-fetcher
   ```

2. **安装依赖**
   ```bash
   pip install -r requirements.txt
   ```

3. **运行服务器**
   ```bash
   python -m src.news_fetcher.server
   ```

## 故障排除

### Python 相关问题

- **缺少依赖**：运行 `pip install -r requirements.txt` 安装所有依赖
- **虚拟环境问题**：可以使用 `python -m venv venv` 创建虚拟环境，然后激活并安装依赖
- **权限问题**：在 Linux/macOS 上，可能需要使用 `sudo` 或调整权限

### Node.js 相关问题

- **版本不兼容**：确保使用 Node.js 21+ 版本
- **依赖安装失败**：尝试使用 `npm install --force` 或清除 npm 缓存后重试
- **端口冲突**：检查是否有其他服务占用了相同的端口

### 网络问题

- **API 访问失败**：确保网络连接正常，能够访问外部 API
- **防火墙设置**：检查防火墙是否阻止了服务器的网络访问

如果遇到其他问题，请参考各 MCP 服务器的 README.md 文件或提交 Issue 寻求帮助。
