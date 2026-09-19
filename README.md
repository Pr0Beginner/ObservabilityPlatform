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

#### Logging：日志上下文

`logging` 负责接收业务服务上报的原始日志，将不同格式的日志解析为统一结构，并完成敏感信息脱敏、日志级别标准化和错误指纹生成。处理后的 `LogEntry` 写入 Elasticsearch，供研发人员检索，也为 Incident 提供错误观测数据。Logging 只管理日志及其处理规则，不负责决定故障生命周期，也不保存诊断报告。

#### Incident：故障上下文

`incident` 负责把离散的错误日志聚合成可管理的故障。它根据服务、环境、错误指纹和时间窗口生成去重键，在错误数量达到阈值后创建或更新 `Incident`，并维护故障状态、严重程度、负责人和解决结论。Incident 保存的是结构化故障信息，持久化到 MySQL；原始日志仍由 Logging 和 Elasticsearch 管理。

#### Diagnosis：诊断上下文

`diagnosis` 负责围绕已有 Incident 创建诊断任务、控制任务状态和版本、向 Diagnosis Agent 发布诊断请求，并接收 Agent 返回的根因、证据、建议和工具调用记录。诊断任务与报告持久化到 MySQL。Diagnosis 不直接修改日志，也不负责判断错误是否应升级为故障。

#### 基础设施职责

- Kafka 连接异步处理阶段，传递原始日志批次、诊断请求和诊断完成事件，使接入、处理和 Agent 执行不必在同一个请求线程内完成。
- Elasticsearch 保存数量大、需要全文检索和多条件过滤的日志数据。
- MySQL 保存 Incident、诊断任务和诊断报告等需要事务一致性和明确关系的数据。
- gRPC Context Service 向 Diagnosis Agent 提供受控的故障与相关日志查询，避免 Agent 直接访问数据库或搜索引擎。

### 职责关系

一次完整链路由三个业务上下文顺序协作：

1. 业务服务通过 HTTP 把日志交给 Logging；Logging 校验批次后将其发布到 Kafka，由日志消费者异步解析、脱敏、生成指纹并写入 Elasticsearch。
2. 日志成功写入后，Logging 将错误观测交给 Incident；Incident 聚合相同错误，达到规则阈值后创建或更新故障并写入 MySQL。
3. 研发人员为 Incident 发起诊断；Diagnosis 创建任务并通过 Kafka 通知 Diagnosis Agent。
4. Diagnosis Agent 通过 gRPC Context Service 查询故障和相关日志，完成分析后通过 Kafka 返回结果。
5. Diagnosis 校验任务状态，保存报告并把任务标记为成功或失败。

业务关系可以概括为：`Logging` 提供事实日志，`Incident` 将事实聚合为故障，`Diagnosis` 围绕故障组织分析过程。三个上下文各自维护自己的模型，通过应用命令、查询接口或事件协作，不共享数据库实体。

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

### 错误响应

所有 API 错误都返回稳定错误码和 `retryable` 字段：

```json
{
  "code": "DEPENDENCY_TIMEOUT",
  "message": "A downstream dependency timed out",
  "retryable": true,
  "timestamp": "2026-09-19T10:00:00Z",
  "details": []
}
```

| 错误码 | HTTP 状态 | 可重试 | 含义 |
| --- | ---: | :---: | --- |
| `INVALID_REQUEST` | 400 | 否 | 请求格式、字段校验或领域参数不合法 |
| `RESOURCE_NOT_FOUND` | 404 | 否 | 指定资源不存在 |
| `BUSINESS_CONFLICT` | 409 | 否 | 当前领域状态不允许执行该操作 |
| `DEPENDENCY_RATE_LIMITED` | 503 | 是 | 下游依赖触发限流 |
| `DEPENDENCY_TIMEOUT` | 504 | 是 | 下游依赖调用超时 |
| `DEPENDENCY_UNAVAILABLE` | 503 | 是 | MySQL、Kafka 或 Elasticsearch 等依赖暂时不可用 |
| `DEPENDENCY_REJECTED` | 502 | 否 | 下游明确拒绝请求，原请求不变时重试无效 |
| `INTERNAL_ERROR` | 500 | 否 | 未分类的内部错误，默认禁止自动重试以避免重试风暴 |

客户端只应对 `retryable=true` 且自身操作具备幂等性的请求执行有限次数重试，并使用指数退避和随机抖动；不可重试错误需要修改请求、业务状态或程序逻辑后再提交。

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
