# cc-pharmacy-dispense

处方与药品批次库存管理服务：处方药按批次库存分次调剂，严格控制累计发药量。

## 主要业务规则

### 处方与库存模型

- **处方**记录患者、药品、总剂量、单次调剂上限、有效期（起止日期）和允许调剂次数，
  并跟踪累计已调剂量与已调剂次数；状态为 `ACTIVE / COMPLETED / CANCELLED`。
- **药品库存按批次**记录入库量、当前剩余量、有效期与状态（`NORMAL / FROZEN / RECALLED`），
  同一药品的批次号唯一。

### 调剂规则

- **失效、召回或冻结批次不能用于调剂**；只有正常状态、未过期（到期日当天仍可用）、
  有剩余库存的批次参与调剂。
- 一次调剂可**从多个合格批次扣减**，按 **FEFO**（较早到期优先，同日按批次 id 顺序）分配。
- 单次调剂量不得超过处方单次上限；**累计调剂量不得超过处方剩余量**；
  调剂次数不得超过允许次数。剂量或次数用尽后处方自动置为 `COMPLETED`。
- **原子性**：任何批次库存不足或校验失败，整个事务回滚，不会留下部分扣减。

### 幂等与并发

- 每次调剂/退药携带**业务号（bizNo）**，数据库唯一约束兜底：
  同一 bizNo 重复或并发提交只会落一笔记录，其余请求重放返回原结果；
  同 bizNo 但参数不一致返回 409 冲突。
- 并发控制采用**悲观行锁**：先锁处方行（串行化同一处方上的调剂/作废/退药），
  再按批次 id 升序锁批次行（统一加锁顺序避免死锁）。因此并发调剂同一处方
  **不会超量、超次数，库存不会为负**。
- **处方作废与调剂并发**时，由处方行锁保证结果只能是“作废成功”或“一次完整调剂”之一；
  已有调剂记录的处方不能作废（须先退药）。作废操作本身幂等。

### 退药规则

- 已完成调剂只能通过**退药记录**恢复对应批次库存和处方余额；
  退药按原调剂的批次构成回补，**累计退药量不得超过原调剂量**。
- 剂量余额每次退药都回补；调剂次数仅在该次调剂被**全额退清**时回补一次。
- **原调剂记录和退药记录均不可修改、不可删除**（JPA 实体回调在更新/删除时直接拒绝）。

### 查询接口

- `GET /api/prescriptions/{id}` — 处方余额（剩余剂量、剩余次数、状态）
- `GET /api/prescriptions/{id}/dispenses` — 处方全部调剂及每次扣减的批次明细
- `GET /api/dispenses/{id}/returns` — 退药链（一张调剂的全部退药及批次回补明细）
- `GET /api/batches?drugCode=` — 库存台账（批次入库量、当前量、状态、有效期）

### 写接口

- `POST /api/prescriptions` — 开立处方
- `POST /api/batches` — 批次入库
- `PATCH /api/batches/{id}/status` — 冻结/召回/恢复正常（`{"status":"FROZEN"}`）
- `POST /api/prescriptions/{id}/dispenses` — 调剂（`{"bizNo":"...","quantity":N}`）
- `POST /api/dispenses/{id}/returns` — 退药（`{"bizNo":"...","quantity":N}`）
- `POST /api/prescriptions/{id}/cancel` — 作废处方

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（Spring Framework 7 / Jackson 3）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

默认使用 H2 内存库（`jdbc:h2:mem:pharmacy`），无需外部数据库即可启动。

## 代码结构

- `domain/` — 处方、批次、调剂/退药记录及明细实体（记录类实体带不可变回调）
- `repository/` — Spring Data JPA 仓库，含悲观锁查询
- `service/PharmacyService` — 无事务外观：幂等预检、唯一约束冲突重放、只读查询
- `service/PharmacyTxOperations` — 事务边界：加锁、校验、FEFO 扣减、退药回补
- `web/` — REST 接口与全局异常映射（404 / 409 / 422 / 400）
- `src/test/` — 规则测试、不可变性测试、并发安全测试、HTTP 端到端测试
