# cc-pharmacy-dispense

处方药按批次库存分次调剂服务：管理处方、批次库存、调剂与退药，严格控制累计发药量，
并在并发下保证不超量、不超次数、库存不为负。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1
- Spring Data JPA + H2（行级悲观锁 `SELECT ... FOR UPDATE`）

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 数据模型

| 表 | 含义 |
| --- | --- |
| `prescription` | 处方：患者、药品、总剂量、单次上限、有效期、允许调剂次数；累计调剂量、已用次数、状态（ACTIVE/VOID）、乐观锁版本 |
| `inventory_batch` | 库存批次：药品、批号、数量、有效期、状态（ACTIVE/EXPIRED/RECALLED/FROZEN） |
| `dispense_record` / `dispense_line` | 调剂单及批次扣减行（不可修改） |
| `return_record` / `return_line` | 退药单及回库批次行（不可修改），指向原调剂 |
| `stock_movement` | 库存台账流水：入库 INBOUND、调剂出库 DISPENSE（负数）、退药回库 RETURN（正数），含批次记账后结余 |

## 主要业务规则

### 调剂（POST /api/dispenses）

1. **处方校验**：处方必须存在、未作废、在有效期内；单次调剂量必须为正且不超过
   **单次上限**；累计调剂量不得超过**处方剩余量**（总剂量 − 已调剂量）；
   **调剂次数**不得超过允许次数。
2. **批次资格**：只有 `ACTIVE` 且在调剂当日未到期（到期日当天仍可用）的批次参与调剂；
   **失效（EXPIRED）、召回（RECALLED）、冻结（FROZEN）批次一律不可用**。
3. **FEFO 先到期先出**：一次调剂可从多个合格批次扣减，按到期日升序（同日按批次 id）
   依次扣减。
4. **整体失败**：先在锁内计算完整扣减方案，合格库存总量不足时直接拒绝（422），
   事务回滚，**不会留下任何批次的部分扣减**，也不会产生调剂单。
5. **幂等**：`businessNo`（调剂业务号）全局唯一。同业务号 + 同处方 + 同数量的重复请求
   返回原调剂（HTTP 200，不重复扣减、不重复计次）；同业务号但内容不同返回 409。
6. **并发安全**：
   - 固定加锁顺序：**处方行（悲观写锁）→ 该药品合格批次行（按批次 id 升序）**；
   - 同一处方的并发调剂在处方行上串行，累计量、次数在同一行锁内推进，
     因此不可能超量、超次数；
   - 不同处方共享批次时在批次行上串行，库存不可能为负；
   - 行锁配合条件校验与乐观锁版本，任何异常都回滚整个事务。
7. **作废互斥**：作废（POST /api/prescriptions/{no}/void）同样持有处方行写锁。
   已产生完整调剂的处方不能作废；作废与调剂并发时，结果只能是
   **“已作废”或“一笔完整调剂”二者之一**，不会出现作废后仍有调剂或半调剂。

### 退药（POST /api/returns）

1. 只能针对**已完成的调剂**退药；退药量必须为正，且不得超过该调剂的
   **原调剂量 − 已退量**，按原调剂批次行顺序逐行限制，超量整体失败。
2. 退药按原调剂批次行把库存**恢复到原批次**（即使批次后来被冻结/召回，库存仍恢复，
   只是不再参与新调剂），同时恢复处方剩余量。
3. **调剂次数不随退药恢复**（次数一旦使用即被消耗）。
4. 退药业务号 `businessNo` 幂等；内容冲突返回 409。
5. **原调剂记录与退药记录均不可修改**：实体只提供业务方法、无更新接口，
   所有变动只以追加流水（台账）的形式留存。

### 查询接口

| 接口 | 内容 |
| --- | --- |
| `GET /api/prescriptions/{no}` | 处方余额：总量、累计已调、剩余、已用/允许次数、状态、有效期 |
| `GET /api/prescriptions/{no}/dispenses` | 处方的全部调剂及各批次扣减明细 |
| `GET /api/dispenses/{businessNo}` | 单笔调剂的批次扣减明细 |
| `GET /api/dispenses/{businessNo}/returns` | 退药链：原调剂量、累计已退、可退余额、每笔退药的批次行 |
| `GET /api/prescriptions/{no}/returns` | 处方维度的全部退药记录 |
| `GET /api/inventory/ledger?drugCode=` | 库存台账流水（可按药品过滤），含每笔变动后结余 |
| `GET /api/inventory/batches?drugCode=` | 批次当前库存与状态快照 |

### 管理接口

- `POST /api/prescriptions`：创建处方
- `POST /api/prescriptions/{no}/void`：作废处方
- `POST /api/inventory/inbound`：批次入库（同时写入库台账）
- `PUT /api/inventory/batches/{id}/status`：变更批次状态（ACTIVE/EXPIRED/RECALLED/FROZEN）

错误码：404 资源不存在；422 业务规则不满足（事务已回滚，无部分扣减）；
409 并发/幂等键冲突；400 参数校验失败。

## 自动化测试

- `DispenseRulesTest`：FEFO 多批次扣减、失效/召回/冻结排除、到期日当天可用、
  库存不足整体失败、单次上限/累计量/次数限制、有效期、作废规则、幂等与查询。
- `ReturnRulesTest`：按原批次恢复库存与处方余额、分次退药、超原调剂量拒绝、
  冻结批次回库、退药幂等、退药链。
- `DispenseConcurrencyTest`：真实多线程（CyclicBarrier 同时发起）——
  同处方并发不超量/不超次数、跨处方共享批次库存不为负、作废与调剂 20 轮竞速二选一、
  同业务号并发只产生一笔调剂、并发退药不超过原调剂量。
- `PharmacyApiTest`：HTTP 全流程、201/200/400/404/422 状态码映射。
