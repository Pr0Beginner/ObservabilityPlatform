# Observability Platform

面向微服务场景的日志观测与故障诊断平台，提供日志接入与检索、故障管理、诊断任务编排和诊断报告管理能力。

## 系统架构

![Observability Platform 系统架构](docs/architecture.png)

### 模块职责

| 模块 | 职责 |
| --- | --- |
| `logging` | 日志接入、解析、脱敏、指纹生成、索引与查询 |
| `incident` | 异常聚合、故障去重与生命周期管理 |
| `diagnosis` | 诊断任务编排、Agent 协作与报告管理 |
| Kafka | 日志与诊断事件的异步传递 |
| Elasticsearch | 日志 Data Stream、条件过滤与全文检索 |
| MySQL | 故障、诊断任务和报告持久化 |

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 应用框架 | Java 17、Spring Boot、Spring WebFlux |
| 消息系统 | Kafka |
| 日志检索 | Elasticsearch 9.5、Data Stream |
| 关系数据库 | MySQL、R2DBC |
| 服务通信 | HTTP、gRPC、Protocol Buffers |
| 部署与测试 | Docker Compose、JUnit、k6 |

## 快速开始

### 完整环境

安装 Docker 与 Docker Compose 后运行：

```bash
docker compose up --build
```

默认服务地址：

- HTTP API：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`
- gRPC：`localhost:9090`
- Kafka：`localhost:29092`
- Elasticsearch：`http://localhost:9200`
- MySQL：`localhost:3306`

Elasticsearch 使用 `logs-observability-default` Data Stream 保存日志。应用启动后会自动安装索引模板，配置字段映射和默认 30 天数据保留周期；可通过 `ELASTICSEARCH_DATA_STREAM` 与 `ELASTICSEARCH_RETENTION` 调整。

Docker Compose 为本地开发关闭了 Elasticsearch 身份认证。生产环境应启用认证与 TLS，并通过受控凭据访问集群。

停止服务：

```bash
docker compose down
```

### 本地内存模式

本地运行需要 JDK 17 和 Maven 3.6.3+：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--app.grpc.enabled=false"
```

该模式不依赖外部中间件，进程退出后数据不会保留。

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/logs/batch` | 批量接收日志 |
| `GET` | `/api/v1/logs` | 查询日志 |
| `GET` | `/api/v1/incidents` | 查询故障列表 |
| `GET` | `/api/v1/incidents/{incidentId}` | 查询故障详情 |
| `POST` | `/api/v1/incidents/{incidentId}/diagnoses` | 创建诊断任务 |
| `GET` | `/api/v1/diagnoses/{taskId}` | 查询诊断任务与报告 |
| `GET` | `/actuator/health` | 健康检查 |

日志接入示例：

```bash
curl -X POST http://localhost:8080/api/v1/logs/batch \
  -H "Content-Type: application/json" \
  -d '{
    "batchId": "demo-001",
    "service": "orders-service",
    "environment": "local",
    "logs": [
      {
        "timestamp": "2026-09-16T10:00:00Z",
        "content": "{\"level\":\"ERROR\",\"message\":\"database connection timed out\",\"traceId\":\"trace-001\"}",
        "format": "JSON"
      }
    ]
  }'
```

## 代码结构

```text
src/main/java/org/zmy/observabilityplatform
├─ logging
├─ incident
├─ diagnosis
├─ bootstrap
└─ shared

业务模块
├─ interfaces       HTTP、Kafka、gRPC 等协议入口
├─ application      用例编排、Command、Query 和 DTO
├─ domain           领域模型、领域服务、事件和 Repository 接口
└─ infrastructure   数据库、消息和其他技术实现
```

## 测试

```bash
mvn test
k6 run load-test/log-ingestion.js
```
