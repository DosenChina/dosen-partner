# dosen-partner

## 项目定位

**GitHub 仓库地址**：[https://github.com/DosenChina/dosen-partner.git](https://github.com/DosenChina/dosen-partner.git)

dosen-partner是一个基于Spring Boot和Spring AI开发的智能伙伴系统，提供了LLM集成、RAG（检索增强生成）、多模型支持等功能，旨在为用户提供智能对话和知识管理能力。

## 核心特性

- **多模型支持**：集成了OpenAI兼容API（如Deepseek）和本地Ollama模型
- **RAG能力**：基于Qdrant向量数据库的检索增强生成，提高模型回答的准确性
- **MongoDB存储**：用于存储聊天历史和用户配置
- **Redis缓存**：提高系统性能
- **Web界面**：提供直观的用户交互界面
- **MCP集成**：支持MCP工具调用，扩展系统能力

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MongoDB 4.0+
- Redis 6.0+
- Qdrant 1.0+（可选，用于RAG）
- Ollama（可选，用于本地模型运行，下载地址：https://ollama.com/）

### Ollama常用命令

如果使用Ollama运行本地模型，以下是常用命令：

```bash
# 拉取模型（示例：拉取deepseek-r1:8b模型）
ollama pull deepseek-r1:8b

# 运行模型（示例：运行qwen3.5:4b模型）
ollama run qwen3.5:4b

# 列出所有本地模型
ollama list
# 示例输出：
# NAME                     ID              SIZE      MODIFIED 
# qwen3.5:4b               2a654d98e6fb    3.4 GB    26 hours ago 
# gemma4:e2b               7fbdbf8f5e45    7.2 GB    26 hours ago 
# deepseek-r1:8b           6995872bfe4c    5.2 GB    27 hours ago 
# qwen3-embedding:8b       64b933495768    4.7 GB    30 hours ago 

# 查看正在运行的模型
ollama ps
# 示例输出：
# NAME                  ID              SIZE     PROCESSOR          CONTEXT    UNTIL
# qwen3-embedding:8b    64b933495768    15 GB    70%/30% CPU/GPU    40960      4 minutes from now
```

### 安装步骤

1. **克隆仓库**

```bash
git clone https://github.com/DosenChina/dosen-partner.git
cd dosen-partner
```

2. **配置环境变量**

创建`.env`文件，配置以下环境变量：

```
# OpenAI/Deepseek API配置
SPRING_AI_OPENAI_API_KEY=your-api-key
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com
SPRING_AI_OPENAI_MODEL=deepseek-reasoner

# MongoDB配置
SPRING_DATA_MONGODB_URI=mongodb://username:password@host:port/database?authSource=admin

# Redis配置
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_DATABASE=0

# Qdrant配置（可选）
SPRING_AI_VECTORSTORE_QDRANT_API_KEY=your-qdrant-api-key
SPRING_AI_VECTORSTORE_QDRANT_HOST=localhost
SPRING_AI_VECTORSTORE_QDRANT_PORT=6334
```

3. **构建项目**

```bash
mvn clean package
```

4. **运行应用**

```bash
java -jar partner-agent/target/partner-agent-1.0.1.jar
```

5. **访问界面**

打开浏览器，访问 `http://localhost:5678`

## 运维相关

### Docker部署相关组件（如果没有镜像文件建议去docker hub拉取）
``` sh
# 配置docker镜像加速
cat /etc/docker/daemon.json
{
  "registry-mirrors": [
    "https://docker.unsee.tech",
    "https://dockerpull.org",
    "https://docker.1panel.live",
    "https://dockerhub.icu",
    "https://docker.nju.edu.cn",
    "https://registry.docker-cn.com",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com",
    "https://5tqw56kt.mirror.aliyuncs.com",
    "https://docker.hpcloud.cloud",
    "http://mirrors.ustc.edu.cn",
    "https://docker.chenby.cn",
    "https://docker.ckyl.me",
    "http://mirror.azure.cn",
    "https://hub.rat.dev",
    "https://docker.m.daocloud.io",
    "https://docker.mirrors.ustc.edu.cn",
    "https://registry.cn-shanghai.aliyuncs.com"
  ],
  "insecure-registries": ["0.0.0.0/0"],
  "exec-opts": ["native.cgroupdriver=systemd"]
}

```

#### Redis

```bash
docker run -di \
  --name dcredis \
  -p 16379:6379 \
  --restart always \
  -v /mydata/dcredis/data:/data \
  -v /mydata/dcredis/conf/redis.conf:/etc/redis/redis.conf:ro \
  cc1partner:85/dosen/redis:latest \
  redis-server /etc/redis/redis.conf
```

#### MongoDB

```bash
docker run -d \
  --name mongodb \
  --restart unless-stopped \
  -p 27017:27017 \
  -e MONGODB_INITDB_ROOT_USERNAME=admin \
  -e MONGODB_INITDB_ROOT_PASSWORD=qazwsx123 \
  -e MONGODB_INITDB_DATABASE=demo2 \
  -v /mydata/mongodb/data:/data/db \
  -v /mydata/mongodb/config:/data/configdb \
  -v /mydata/mongodb/logs:/var/log/mongodb \
  --ulimit nofile=64000:64000 \
  --security-opt no-new-privileges=true \
  mongodb/mongodb-community-server:8.2-ubi9-slim \
  mongod --bind_ip_all --quiet
```

#### Qdrant

```bash
docker run -d \
  --name qdrant \
  --restart unless-stopped \
  -p 6333:6333 \
  -p 6334:6334 \
  -v /mydata/qdrant/storage:/qdrant/storage \
  -v /mydata/qdrant/snapshots:/qdrant/snapshots \
  -v /mydata/qdrant/config:/qdrant/config \
  -e TZ=Etc/UTC \
  -e RUN_MODE=production \
  cc1partner:85/devops/qdrant:latest \
  ./entrypoint.sh
```

### Web UI 访问地址

- **Qdrant Dashboard**：http://192.168.77.117:6333/dashboard

## 技术栈

- **后端框架**：Spring Boot 3.5.5
- **AI框架**：Spring AI 1.1.4, LangChain4j 1.12.2
- **数据库**：MongoDB, Redis
- **向量数据库**：Qdrant
- **前端**：Thymeleaf, HTML, CSS
- **工具库**：Lombok, Hutool, TinyPinyin

## 项目结构

```
dosen-partner/
├── partner-agent/        # 核心模块
│   ├── src/main/java/    # Java源码
│   ├── src/main/resources/ # 资源文件
│   └── pom.xml           # 模块依赖
├── pom.xml               # 项目依赖
├── LICENSE               # 许可证文件
├── NOTICE                # 第三方依赖声明
└── README.md             # 项目说明
```

## 联系方式

- **GitHub Issues**：https://github.com/DosenChina/dosen-partner/issues
- **Email**：zzggcheng@outlook.com

## License

本项目采用 [Apache License 2.0](LICENSE) 开源许可证。