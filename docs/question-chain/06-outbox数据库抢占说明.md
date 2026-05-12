# outbox 数据库抢占说明

## 1. 先说结论

题目异步派发链路需要为 outbox 增加数据库抢占能力。

推荐方案是在 `question_outbox_event.event_status` 中增加 `SENDING` 状态，让调度器在发送 MQ 前先通过条件更新抢占单条 outbox 事件。

抢占成功的实例继续发送 MQ，抢占失败的实例直接跳过。

这项改动只收敛 outbox 投递层的并发窗口，不扩展 `question.process_status`，也不改变题目快照状态机。

---

## 2. 当前链路的并发窗口

当前 outbox 派发顺序是：

1. 调度器扫描到 `NEW / RETRYABLE` 事件
2. 读取 task 和 question
3. 调用 `syncSend(...)`
4. MQ 返回成功后，把 outbox 推进到 `SENT`
5. 再推进 task 与 question 状态

单实例运行时，这个顺序可以保证待发送事件可恢复。

多实例运行时，多个调度器可能在同一轮扫描中读到同一条 outbox。由于当前发送动作发生在本地状态推进之前，两个实例都可能先完成 MQ 发送，随后只有一个实例成功把 outbox 标记为 `SENT`。

这会带来额外重复投递。下游幂等可以收敛重复消息，但系统仍会产生不必要的消息噪音、AI 处理压力和排查成本。

---

## 3. 数据库抢占的必要性

数据库抢占的目标是把“是否允许发送 MQ”变成一条原子数据库更新。

调度器扫描到候选事件后，先执行类似下面的条件更新：

```sql
update question_outbox_event
set event_status = 'SENDING',
    updated_time = now()
where id = ?
  and event_status in ('NEW', 'RETRYABLE')
  and (next_retry_time is null or next_retry_time <= now())
```

返回影响行数为 `1` 时，说明当前实例抢占成功，可以发送 MQ。

返回影响行数为 `0` 时，说明这条事件已经被其他实例抢走、状态已经变化，当前实例必须跳过。

数据库对单行条件更新具备原子性，因此不需要额外引入分布式锁组件，也能避免多实例同时发送同一条 outbox。

---

## 4. 为什么状态加在 outbox 层

`SENDING` 描述的是一条 outbox 事件正在被某个实例投递。

它属于发送账本的内部状态，和题目对外展示阶段不是同一个层次。

题目快照状态仍然保持：

```text
WAITING / COMPLETED -> DISPATCHING -> PROCESSING -> REVIEW_PENDING -> COMPLETED
```

outbox 状态扩展为：

```text
NEW / RETRYABLE -> SENDING -> SENT
                         |
                         -> RETRYABLE
                         -> FAILED
```

因此本次功能只需要扩展 `OutboxEventStatusEnum`、outbox mapper 和派发编排逻辑。

---

## 5. 仍需保留 at-least-once 语义

数据库抢占可以减少多实例并发扫描导致的重复投递，但它不会消灭所有重复投递窗口。

例如：

1. 实例抢占成功，outbox 进入 `SENDING`
2. MQ 发送成功
3. 实例在 `SENDING -> SENT` 前宕机

这时系统无法仅凭本地数据库判断 MQ 是否已经收到消息。后续恢复时仍可能再次发送。

所以链路语义仍然是 `at-least-once`。下游继续按 `task_id` 做幂等消费，回包侧继续按 `callback_key / task_id` 做幂等回写。

---

## 6. 需要配套的恢复能力

引入 `SENDING` 后，必须处理抢占成功后进程退出的问题。

推荐规则是：调度器同时扫描长期停留在 `SENDING` 的事件，并在超时后重新抢占。

超时重抢占可以通过 `updated_time` 判断，例如：

```text
event_status = SENDING
and updated_time <= now() - sendingTimeout
```

抢占成功后继续发送 MQ。发送失败时，仍按现有退避策略推进到 `RETRYABLE` 或 `FAILED`。

这保证了 `SENDING` 不会成为永久卡死状态。

---

## 7. 最终收益

引入数据库抢占后，链路收益是：

- 多实例部署时，同一条 outbox 同一时刻只会被一个实例发送
- 不依赖 Redis、Redisson 或其他外部分布式锁组件
- 锁粒度落在单条 outbox 事件上，便于后续水平扩展
- 题目状态机不继续膨胀，投递细节留在 outbox 层
- 保留 `at-least-once` 与下游幂等的整体语义

这项改动属于 outbox 派发链路的并发控制增强，适合作为多实例部署前的基础能力。
