# 项目发现记录

## 2026-03-30 分布式系统正确性专项审查启动
- 本轮目标从“全项目泛审”收敛为“微服务边界与分布式正确性专项审查”。
- 重点问题类型：
  - 分布式事务是否闭环，是否存在部分成功、部分失败、补偿缺失、状态不一致。
  - 服务拆分后，下游错误语义是否被吞掉、弱化或统一包装，导致上游无法区分真实失败原因。
  - 同步调用的超时、重试、幂等、鉴权信任边界是否合理。
  - 异步消息链路是否具备发送补偿、消费幂等、状态机闭环和失败可追踪性。
- 审查范围覆盖所有下游微服务调用点，以及 MQ、Redis、OSS、数据库状态推进等跨边界交互；文档仅作旁证。

## 2026-03-30 第一轮调用地图
- 当前已确认的同步跨服务调用：
  - `auth -> user`：`UserRpcService` 封装 `UserFeignApi`
  - `excel-parser -> question-bank`：`QuestionBankRpcService` 封装 `QuestionFeign`
  - `excel-parser -> oss`：`OssRpcService` 封装 `FileFeignApi`
  - `excel-parser / user / question-bank -> distributed-id-generator`：各自封装 `DistributedIdGeneratorFeignApi`
  - `user -> oss`：`OssRpcService` 封装 `FileFeignApi`
- 当前已确认的异步跨服务调用：
  - `question-bank` 使用 `RocketMQTemplate.syncSend(...)` 发送题目处理消息
  - `question-bank` 使用 `AIProcessResultConsumer` 消费 AI 回包并回写数据库
- 当前已确认的上下文透传机制：
  - 网关 `TrustedInternalCallGatewayFilter` 给下游补 `userId + callerService + timestamp + signature`
  - 业务服务入口 `TrustedInternalRequestFilter` 验签通过后再写入 `LoginUserContextHolder`
- 第一批值得继续深挖的错误语义风险：
  - `excel-parser` 的 `QuestionBankRpcService` 会把下游失败映射为“批量导入结果失败对象”，而不是直接把下游错误上抛，可能弱化真实失败来源。
  - `excel-parser` 与 `user` 的 OSS RPC 层多处把下游失败压成 `null`，上层如果没有区分“空结果”和“远程失败”，会掩盖故障语义。
  - `auth` 的 `UserRpcService` 只对 `registerUser` 做有限重试，且失败最终统一映射成 `AUTH-RPC-500` 一类通用错误，是否丢失下游细粒度错误要继续核对上层处理。

## 2026-03-30 第二轮专项发现
- `question-bank` 的 outbox 扫描器在多实例场景下没有分布式锁或 claim 机制；代码里已经直接写了 TODO。
  - 更关键的是，`dispatchTask()` 先 `syncSend`，后把 outbox 从 `NEW/RETRYABLE` 改成 `SENT`。
  - 这意味着两个实例同时扫到同一条 outbox 时，可能先后都把消息发给 MQ，随后只有一个实例成功把 outbox 标记为 `SENT`，形成“消息已重复发送，但本地状态只记录一次”的错位。
- `auth` 存在明显的跨服务局部提交问题：
  - 验证码登录先消费验证码，再调用 `user` 服务注册/查用户；若下游注册阶段失败，验证码已不可重用。
  - 修改密码也先消费验证码，再调用 `user` 服务改密；若下游改密失败，验证码同样已经被消费。
  - 这不是传统数据库事务 bug，而是典型的“本地副作用先提交、远程写操作后失败”的分布式边界问题。
- `excel-parser -> question-bank` 的错误语义被双重压平：
  - `QuestionBankRpcService` 把下游 `Response.fail(...)` 改写成一个 `BatchImportQuestionResponseDTO.success=false` 的普通结果对象。
  - `ExcelFileServiceImpl.parseExcelFileById()` 收到这个失败对象后，仍返回外层 `Response.success(...)`，只是内部 `processStatus=FAILED`。
  - 结果是 API 调用在 HTTP/外层协议层看起来成功，但真实失败源头其实是下游 `question-bank`。
- `OSS` RPC 层对下游失败普遍只返回 `null`：
  - `excel-parser` 的上传与下载凭证调用，在 RPC 层只区分“有值 / null / 抛 RuntimeException”，上层最终多半被统一映射成“文件服务暂时不可用”。
  - `user` 的头像/背景图上传和图片签名 URL 获取也只返回 `null`；其中 `getCurrentUserProfile()` 会直接把图片 URL 置空，调用方无法区分“用户未设置图片”和“OSS 服务故障”。
- 内部调用签名目前只绑定 `callerService + userId + timestamp + method`，没有把 URI、请求体摘要或 nonce 纳入签名。
  - 这意味着它更像“受信调用身份校验”，而不是严格的请求级防篡改/防重放协议。
  - 在 300 秒时间窗内，只要签名头被截获，理论上就存在同方法请求的重放空间。

## 2026-04-02 docs 全量通读启动
- 用户明确要求把 `docs` 目录“全部看一遍”，不能再只靠抽样判断文档质量或复盘数量。
- 当前 `docs` 目录共有 26 个 Markdown 文档，按路径初步分为 4 组：
  - `docs/` 根目录当前主文档 11 篇
  - `docs/archive/` 历史归档文档 5 篇
  - `docs/review-2026-03-20-project-audit/` 审查包 5 篇
  - `docs/superpowers/` 设计/计划文档 5 篇
- 需要特别纠正此前的一个偏差：
  - 之前只重点阅读了 6 篇强复盘文，不能代表整个 `docs` 目录。
  - `docs/从单体全局异常处理到微服务错误契约_为什么不能吞下游错误.md`、`docs/内部调用签名设计与落地说明.md`、`docs/异步派发-at-least-once与分布式锁说明.md`、`docs/题目快照状态机与流程表职责说明.md` 都属于当前主文档，必须纳入整体判断。
- 已确认 6 篇核心复盘文确实存在且内容扎实：
  - `docs/异步AI链路-task-outbox-inbox复盘.md`
  - `docs/项目初衷与微服务实践反思.md`
  - `docs/Excel导入为什么要先校验再解析_一次链路重构后的反思.md`
  - `docs/Sa-Token与网关真实IP复盘.md`
  - `docs/从存URL到存objectKey_一次OSS上传链路重构背后的抽象反思.md`
  - 根目录 `这两天我被验证码登录狠狠上了一课.md`（不在 `docs` 目录，但属于同一批复盘写作）

## 2026-04-02 docs 当前主文档补读（第一批）
- `docs/从单体全局异常处理到微服务错误契约_为什么不能吞下游错误.md`
  - 这篇不是普通“异常处理说明”，而是把单体异常模型和微服务错误契约明确拆成两层：
    - 进程内是异常控制流
    - 服务边界是 `Response<T>` 失败 DTO
  - 文档明确强调 RPC 适配层的职责是“协议恢复”和“错误语义恢复”，不能把下游失败吞成 `null`。
  - 这篇和当前代码风险是直接对齐的：此前源码审查里发现的 `OSS RPC -> null`、`question-bank 失败被 DTO 化再外层 success 化`，正是它在理论上反对的做法。
- `docs/内部调用签名设计与落地说明.md`
  - 这篇不是简单的落地说明，而是把“部署层隔离不足 -> 代码层补内网验真 -> 适合下沉为全局过滤器/拦截器”的决策链讲清楚了。
  - 文档明确说明当前签名方案的边界：只做到受信内部调用验真，没有做到更严格的请求级防篡改/防重放。
  - 这和当前源码现状一致：签名字段只包含 `callerService/userId/timestamp/method`，没有 path/body/nonce。
- 结论修正：
  - `docs` 当前主文档里，至少有一部分不是“感想型文章”，而是把设计前提、边界、取舍和未解决点写清楚的工程说明文。
  - 这些文档与源码现状有明显对应关系，不是脱离代码的空谈。

## 2026-04-02 docs 当前主文档补读（第二批）
- `docs/异步派发-at-least-once与分布式锁说明.md`
  - 这篇明确把当前异步派发链路定性为：
    - 发送侧 `at-least-once`
    - 下游按 `task_id` 幂等消费
    - 回包侧按 `callback_key / task_id` 幂等回写
  - 文档也清楚说明了“分布式锁是减少并发扫描型重复发送的优化项，不是 exactly-once 保证，也不是当前正确性的前提”。
  - 这与源码里的 TODO 能对上，但文档口径比代码注释更完整。
- `docs/题目快照状态机与流程表职责说明.md`
  - 这篇把 `question.process_status` 定义为“题目快照”，而不是完整流程真相。
  - 文档明确区分：
    - 快照层：`question` + `QuestionStatusStateMachine`
    - 流程层：`task / validation_record / outbox / inbox`
  - 这篇对后续理解题目域非常关键，因为它解释了为什么当前状态机不是工作流引擎，也解释了为什么不能继续往题目状态枚举里硬塞所有流程语义。
- 当前判断进一步收敛：
  - `docs/` 根目录当前主文档里，至少有 4 篇属于“架构边界 / 正确性 / 抽象职责”说明，不是简单复盘或面试稿。
  - 这些文档整体上围绕一个共同主题：把原来被混在一起的职责拆开，并明确哪些问题当前已经解决，哪些只是工程优化项。

## 2026-04-02 docs 当前主文档补读（第三批，根目录收口）
- `docs/3.18晚重构日记：从验证码并发复用到登录凭证解耦.md`
  - 这篇和根目录 `这两天我被验证码登录狠狠上了一课.md` 是同主题但更短的版本，核心链路一致：
    - 先修验证码并发复用
    - Lua 原子消费
    - 再意识到“验证码已删但下游失败”的分布式边界问题
    - 最后把 `login_ticket` 定位成职责解耦方向
  - 从文档体系角度看，它更像“阶段性重构总结”，而根目录那篇更像完整长复盘。
- `docs/interview-qa-2026-03-23.md`
  - 这篇不是复盘文，也不是当前实现说明书，而是“基于当前代码的面试表达稿”。
  - 它的特点是：对很多问题都明确区分了“项目真实落地”和“原理扩展回答”，避免把项目没做的东西硬说成做过。
  - 文档里也保留了一些历史口径痕迹，例如 MQ 章节仍提到“当前代码没有 outbox”；因此它更适合作为当时的面试准备稿，不适合作为当前实现事实的唯一依据。
- 当前主文档整体画像：
  - `docs/` 根目录 11 篇文档里，大致可以分成三类：
    - 强复盘/反思：`异步AI链路...复盘`、`项目初衷...反思`、`Excel导入...反思`、`Sa-Token...复盘`、`从存URL到存objectKey...反思`
    - 架构说明/边界说明：`从单体全局异常处理到微服务错误契约...`、`内部调用签名设计与落地说明`、`异步派发-at-least-once与分布式锁说明`、`题目快照状态机与流程表职责说明`
    - 过程总结/表达稿：`3.18晚重构日记...`、`interview-qa-2026-03-23.md`
  - 结论：`docs` 根目录不是“随便写的感想集合”，而是一套已经成形的知识沉淀体系。

## 2026-04-02 review 审查包补读（第一批）
- `docs/review-2026-03-20-project-audit/00-总览审查.md`
  - 这是一个明确面向“项目审查输出”的总览文，核心作用是给出一句话结论、亮点、主要不足和优先级。
  - 它不是复盘，不是设计文，更像一个项目审计摘要页。
- `docs/review-2026-03-20-project-audit/01-核心链路梳理.md`
  - 这篇是围绕当时代码现状整理的主流程说明，覆盖登录鉴权、Excel 导入和题目 AI 审核三条链路。
  - 文档中仍保留了部分旧口径，如把 `send-to-queue` 直接描述为 `WAITING -> PROCESSING + 发 MQ`，没有纳入后续 `task/outbox/inbox` 重构后的最新模型。
  - 说明 `review-2026-03-20-project-audit` 这组文档更适合看“某一时点的项目审查结论”，不适合作为 2026-04-02 的唯一现状说明。
- 初步判断：
  - `review` 目录价值在于“审查视角的外部表达”，不是当前实现的权威事实源。
  - 这组文档能看出项目在持续被审视，但也存在时间戳语义，阅读时必须和当前代码、当前主文档分开。

## 2026-04-02 review 审查包补读（第二批）
- `docs/review-2026-03-20-project-audit/02-亮点与不足.md`
  - 这篇基本就是一版项目审查结论摘要，口径与 2026-03-20 的代码现状绑定。
  - 亮点集中在：
    - Excel 链路分层
    - 题目状态机
    - AI 契约意识
    - 单测投入
  - 不足集中在：
    - 默认密码
    - 鉴权边界押注网关
    - 配置治理
    - 微服务对当前业务略重
- `docs/review-2026-03-20-project-audit/03-问题与证据清单.md`
  - 这篇是带代码证据的风险清单，形式上最接近标准审查报告。
  - 但它同样带有明显时间戳：
    - 里面仍把“自动注册用户固定默认密码”列为 P0，而当前源码已经改成空密码。
    - 说明该文档记录的是 2026-03-20 的问题快照，而不是当前仓库最终现状。
- 收敛判断：
  - `review` 目录不是“空文档”或“水文”，而是真正做过一次像样的项目审查。
  - 但它的阅读方式必须是“看项目如何被审视、看问题如何被发现”，不能把它当当前现状直接复述。

## 2026-04-02 review 与 archive 交界补读
- `docs/review-2026-03-20-project-audit/04-测试与文档校验.md`
  - 这篇记录了当时实际跑过的测试命令和覆盖判断，能证明那轮审查不是只看代码不验证。
  - 但其中“网关过滤器没看到自动化测试”等结论，如今已经被后续新增测试部分修正。
- `docs/archive/question-bank_excel-parser_问题审查与修改建议.md`
  - 这篇明显属于更早一轮历史审查，里面存在大量已被后续代码和文档修掉/推翻的问题：
    - 编译阻塞
    - `parse-by-id` 越权
    - 旧的 MQ/AI 回写模型
  - 它的价值主要是展示项目经历过哪些低成熟度阶段，不适合再作为当前项目评价依据。
- 进一步收敛：
  - `archive` 目录的文档应默认视为“历史材料”，除非和当前主文档或当前代码再次互证。
  - 这也解释了为什么用户前面会质疑“你是不是没全看”：如果只看当前主文档，和把 archive 混在一起看，得出的结论会差很多。

## 2026-04-02 archive 补读（第二批）
- `docs/archive/question-bank_excel-parser_契约规范问题文档.md`
  - 这篇把早期 `question-bank` 与 `excel-parser` 的问题整理成“生产者/消费者契约失配”视角，核心关注点包括：
    - 双轨 success 语义
    - Excel 模板字段口径不一致
    - MQ schema 与文档能力不一致
    - 持久层字段与 Service 假设断裂
  - 它反映的是一轮非常偏“契约治理”的阶段。
- `docs/archive/question-bank_excel-parser_实施规范与改造清单_v1.md`
  - 这是对应的行动文档，把前述契约问题转成单一裁决和分阶段待办。
  - 里面很多事项后来已经在代码里落地或被后续设计替换，因此它更像“整改路线图快照”。
- 当前历史轨迹判断：
  - `archive` 不是杂乱堆积，而是能看出一条清晰路径：
    - 先发现编译/实现/契约问题
    - 再形成统一规范和改造清单
    - 再在后续主文档里沉淀成更成熟的设计说明和复盘

## 2026-04-02 archive 补读（第三批，归档收口）
- `docs/archive/question-chain_业务规则与字段规范.md`
  - 这篇标题里已经写明“推测版”，内容也保留了明显历史口径：
    - `WAITING -> PROCESSING`
    - 旧版 MQ 消息字段
    - 旧版 AI 回包能力
  - 它更多反映的是“当时如何尝试把题目链路字段说清楚”，现在已经不能直接当事实文档。
- `docs/archive/题目链路文档索引.md`
  - 这篇很有价值，因为它说明作者自己已经意识到要给历史问题文档做分工：
    - 问题诊断
    - 契约视角
    - 字段与规则
    - 实施清单
  - 也就是说，归档区不是无序堆文档，而是当时就已经开始做结构化沉淀。
- archive 总结：
  - `docs/archive` 应视为“题目链路早期治理阶段”的历史资料库。
  - 它的主要价值不是提供当前事实，而是证明这个项目经历过系统性的诊断、裁决和整改过程。

## 2026-04-02 superpowers 文档补读（第一批）
- `docs/superpowers/specs/2026-03-29-oss-upload-boundary-design.md`
  - 这是比较完整的一份正式设计稿，结构上包含：
    - 背景
    - 目标 / 非目标
    - 设计结论
    - 模块改动范围
    - 错误处理约束
    - 兼容策略
    - 测试要求
  - 它不是对外项目介绍，而是一次具体边界重构的设计资产。
- `docs/superpowers/plans/2026-03-29-oss-upload-boundary-plan.md`
  - 这是对应的任务执行计划，使用英文任务分解和勾选步骤。
  - 它明显属于 agent/workflow 过程文件，而不是面向项目读者的说明文。
- 对 `docs/superpowers` 的初步判断：
  - 这组文档应归类为“开发过程资产”。
  - 它们能证明项目不仅写总结，还保留了设计和实施分离的过程材料。

## 2026-04-02 superpowers 文档补读（第二批，收口）
- `docs/superpowers/plans/2026-03-24-async-chain-reliability.md`
  - 这份计划文档对应的是题目 AI 异步链路从“直接发 MQ + 直接回写”的旧形态，继续收敛到 `task / outbox / inbox` 驱动模型的实施拆解。
  - 它的价值不在于对外可读性，而在于能看见作者是先把可靠性目标、改造边界、步骤和验证点拆出来，再去落代码。
  - 这和当前 `question-bank` 里已经成形的流程表与回包链路是能对上的，不是事后补文档。
- `docs/superpowers/plans/2026-03-28-question-service-refactor.md`
  - 这份计划的核心是把 `QuestionServiceImpl` 这种重编排入口继续拆成 facade + app service 的结构，减少上帝类风险。
  - 文档里已经明确到“哪些职责应该迁走、迁到哪一层、按什么顺序拆”，说明作者对服务边界问题是有自觉治理的。
  - 结合当前代码，可以看出这不是空想方案，而是和现有 `QuestionDispatchAppService / QuestionCallbackAppService / QuestionCrudAppService` 的拆分方向一致。
- `docs/superpowers/plans/2026-03-30-private-user-image-objectkey-plan.md`
  - 这份计划对应用户图片链路从“直接存 URL”切到“存 `objectKey` + 按需签名访问”的重构。
  - 它和根目录那篇 `从存URL到存objectKey...反思` 形成前后呼应：一边是实施前的计划资产，一边是实施后的抽象复盘。
  - 这说明文档体系不是只有结果复盘，也保存了“设计 -> 执行 -> 反思”的完整链条。
- `docs/superpowers` 总结修正：
  - 这组文档不能算普通项目文档，更像“开发过程资产库”。
  - 但它们非常有价值，因为它们证明这个项目的演进不是拍脑袋改，而是有过设计稿、实施计划和后续复盘三段式沉淀。

## 2026-04-02 docs 全量通读总判断
- `docs` 目录整体不是“几篇复盘文”，而是已经分成 4 层：
  - 当前主文档：复盘、边界说明、表达稿
  - `archive`：早期问题诊断、契约治理、整改清单
  - `review-2026-03-20-project-audit`：时间点明确的审查包
  - `superpowers`：设计/计划过程资产
- 这套文档体系最强的地方不是数量，而是能看见一条完整工程轨迹：
  - 先发现问题
  - 再做契约/边界裁决
  - 再产出设计和实施计划
  - 最后形成复盘和对外表达材料
- 因此，对这个项目的判断也需要修正：
  - 它不只是“代码写了一些，然后补了几篇总结”。
  - 它已经具备比较明显的工程化知识沉淀能力，尤其是微服务边界、错误语义、异步可靠性和职责拆分这几个主题。

## 2026-03-18 后端审查启动
- 本轮目标不是继续重构，而是基于当前代码审查后端主要流程是否仍有实现风险。
- 审查范围限定为后端服务与配置，明确排除前端页面和静态资源。
- 优先检查三条主流程：
  - 登录与鉴权
  - Excel 上传/解析/导入
  - 题目发送 AI、回写、人工审核

## 2026-03-20 全量项目审查启动
- 这次目标从“修某几个点”切换成“从头到尾梳理项目”，最终要产出新的独立审查文档目录。
- 根目录已有大量历史分析文档和未提交改动，因此所有判断都需要回到代码本身。
- 当前代码事实先记下三点：
  - `excel-parser` 的 `parseExcelFileById()` 已经落了原子状态抢占，能拦住重复解析。
  - `question-bank` 已把发送 AI、回写、审核、编辑、删除收进状态机/CAS 语义里，主链路比旧文档稳。
  - `auth/user/gateway/config` 仍能看到若干安全与工程化问题，需要继续下钻确认影响面。

## 2026-03-20 第一轮代码事实
- Excel 处理链路当前形态：
  - 上传阶段同时校验扩展名、魔术头、模板结构。
  - 模板失败不会直接抛 500，而是持久化 `preUploadId + errorMessages`，便于前端回查明细。
  - 解析阶段先做“文件归属校验”，再用 `tryMarkParsing(fileId, userId)` 抢占 `PARSING` 状态，避免同文件被并发重复导入。
- 题目与 AI 链路当前形态：
  - `QuestionStatusStateMachine` 明确维护 `WAITING -> PROCESSING -> REVIEW_PENDING -> COMPLETED` 主流程和失败/驳回支路。
  - `QuestionServiceImpl.sendQuestionsToQueue()` 先按用户、模式、状态筛选，再用条件更新把题目推进到 `PROCESSING`，发送失败会回滚到 `WAITING`。
  - `AIProcessResultConsumer` 已改成直接消费回写数据库，不再绕 Redis + 定时任务。
- 第一批不足/风险：
  - `VerificationCodeServiceImpl` 仍把明文验证码打到日志里，属于直接敏感信息泄露。
  - 多个 `application*.yml`、`bootstrap.yml`、`generatorConfig.xml` 仍存在明文或可逆密钥/密码，配置治理较弱。
  - `gateway` 的 `TrustedInternalCallGatewayFilter` 当时仍保留大量调试日志，每次请求都会打印线程、路径、登录用户信息，噪音和泄露面都偏大。

## 2026-03-20 第二轮审查结论
- 鉴权边界：
  - 网关负责 `Sa-Token` 登录校验和角色校验。
  - 下游服务此前通过旧的入站上下文过滤器直接信任请求头里的 `userId`；当前代码已替换为 `TrustedInternalRequestFilter` 验签后再写入 ThreadLocal。
  - 结论：这是典型的“信任网关边界”设计；若业务服务端口被直接暴露或被旁路访问，调用方可以伪造 `userId` 取得他人权限。
- 账户安全：
  - `user` 服务给验证码登录自动注册的新用户统一下发固定默认密码 `123456`。
  - `auth` 服务又允许直接用“手机号 + 密码”登录。
  - 结论：只要用户从未主动改密，其账号就具备可猜测默认口令，属于高风险弱口令设计。
- 文档与代码一致性：
  - 根 `README.md` 仍把 Redis 描述成“缓存 AI 处理结果”，这已经落后于当前代码。
  - 题目链路文档和 AI 契约文档相对更新，和代码基本一致，可作为理解主流程的有效旁证。
- 测试覆盖：
  - `excel-parser`、`question-bank`、`user` 的核心服务单测都比较像“行为测试”，能覆盖主要分支和并发收口语义。
  - `auth` 的验证码登录策略和参数校验有测试，但 `AuthServiceImplTest` 很薄，验证码发送服务、网关过滤器、配置边界基本没有自动化验证。

## 2026-03-18 审查中间记录
- 登录链路初步确认两个风险点：
  - `AuthServiceImpl.loginAndRegister()` 验证码校验通过后未删除 Redis 中的验证码，存在验证码在有效期内被重复使用的窗口。
  - `UserServiceImpl.updatePassword()` 会把加密后的密码写入日志，属于敏感信息泄露。
- `UserServiceImpl.register()` 仍是“先查后插”模式，是否存在手机号并发注册竞争，需要结合表约束和异常处理继续核实。

## 2026-03-18 审查结论
- `excel-parser` 的 `parse-by-id` 主流程缺少文件状态门禁和条件更新：
  - 代码会直接把文件状态改成 `PARSING`，随后再次导入题目。
  - 当前实现没有限制 `UPLOADED/FAILED` 才能触发，也没有防并发的 CAS。
  - 结果是同一 `fileId` 可被重复解析，导致题库重复导入。
- `question-bank` 的编辑 / 删除状态约束不是原子操作：
  - 服务层先查询题目并判断 `WAITING/PROCESS_FAILED`，随后执行普通 `update/delete`。
  - 若在校验后、提交前被另一请求推进到 `PROCESSING/REVIEW_PENDING`，当前请求仍可能修改或删除题目。
- 登录验证码在成功使用后未失效：
  - 验证码发送服务设置了 3 分钟有效期。
  - 登录成功后只比对 Redis 中的验证码，没有删除键值。
  - 同一验证码在有效期内可重复登录。
- 敏感信息日志问题仍然存在：
  - 验证码发送时会把明文验证码写日志。
  - 修改密码时会把加密后的密码写日志。
- 对手机号注册的并发保护仍然不足：
  - 代码仍是“先 `selectByPhone` 再 `insert`”。
  - 我没有在仓库里看到配套的唯一约束处理逻辑。
  - 推断：并发请求下，要么插入重复用户，要么其中一个请求直接失败，不能稳定收敛到“同手机号返回同一用户”。

## 2026-03-18 本轮已落地修复
- `excel-parser` 已对“进入 `PARSING`”补上原子抢占：
  - 新增 `FileInfoDOMapper.tryMarkParsing(fileId, userId)`
  - SQL 条件限制为：`id + user_id + status in (UPLOADED, FAILED)`
  - `parseExcelFileById()` 现在先抢占解析资格，抢占失败直接返回“文件状态已变化，无法重复解析”
- 保留了原有职责边界：

## 2026-03-26 面试优化优先级建议
- 当前项目已经具备“可讲清的完整主链路”，不值得继续横向扩论坛、错题本、推荐系统一类新业务。
- 对面试更有价值的补强方向是：
  - 可靠性：多实例 outbox 扫描保护、重试上限、死信/失败明细
  - 可验证性：补关键单测、补验证命令和结果证据
  - 可解释性：补架构图、链路图、设计取舍与演进路线
- `task/outbox/inbox + 状态机` 可以讲成“第一层可靠异步工程化”，但不能讲成事务消息级或完整生产级一致性。
- Excel 导入、AI 回写、鉴权边界和配置安全是最值得优先准备追问答案的区域。

## 2026-03-27 全项目总览梳理
- 本轮目标不是只盯某个 bug，而是面向“第一次接手这个仓库的人”重建一份从聚合层到核心链路的整体理解。
- 已确认项目根目录存在完整历史 planning 与多份专题文档，因此本轮会把旧文档当索引，但所有判断仍以源码现状为准。
- 根 `pom.xml` 证实仓库是 Maven 聚合工程，核心模块仍是 `auth / gateway / user / excel-parser / question-bank / oss / distributed-id-generator / framework`。
- 根 `README.md` 仍能说明项目目标和部署依赖，但其中把 Redis 描述为“缓存 AI 处理结果”，这与当前代码里“消费者直接回写数据库”的实现不完全一致。
- 可运行服务入口目前有 7 个：
  - `eaqb-auth`
  - `eaqb-gateway`
  - `eaqb-user-biz`
  - `eaqb-excel-parser-biz`
  - `eaqb-question-bank-biz`
  - `eaqb-oss-biz`
  - `eaqb-distributed-id-generator-biz`
- 核心链路主实现文件已定位到：
  - `AuthServiceImpl`
  - `TrustedInternalCallGatewayFilter`
  - `ExcelFileServiceImpl`
  - `QuestionServiceImpl`
  - `AIProcessResultConsumer`
  - `QuestionStatusStateMachine`
- 登录鉴权边界的当前代码事实：
  - `AuthServiceImpl` 走 `LoginStrategyFactory -> LoginStrategy -> StpUtil.login`，自己负责认证编排与发 token，不直接管理用户持久化。
  - `TrustedInternalCallGatewayFilter` 会把 `userId + callerService + timestamp + signature` 注入下游请求头，未登录请求也会透传受信签名。
  - `TrustedInternalRequestFilter` 会在业务服务入口强制验签、校验时间戳新鲜度，通过后才把 `userId` 写入 `LoginUserContextHolder`。
  - `UserServiceImpl.register()` 已补上 `DuplicateKeyException` 兜底回查，手机号并发注册不再只是“先查后插”的裸奔模式。
- 账户安全的当前代码事实：
  - 验证码登录自动注册的新用户已改为默认空密码，不再初始化可枚举的固定默认口令。
  - `VerificationCodeServiceImpl` 已使用 Redis Lua 做验证码原子消费，但发送验证码时仍把明文验证码写入日志，仍属于明显的敏感信息泄露点。
- Excel 与题目主链路的当前代码事实：
  - `ExcelFileServiceImpl` 仍保持“上传校验 + 保存文件记录”和“按 `fileId` 解析导入”两阶段设计；模板校验失败会落 `preUploadId + errorMessages` 供前端回查。
  - `parseExcelFileById()` 进入解析前会先 `tryMarkParsing(fileId, userId)` 抢占 `PARSING` 状态，抢占失败直接拒绝重复解析。
  - `QuestionServiceImpl.sendQuestionsToQueue()` 现在不是直接发 MQ，而是先做权限/状态/模式过滤，再交给 `QuestionDispatchService.prepareQuestionDispatch(...)` 建立 task/outbox 异步派发。
  - `QuestionStatusStateMachine` 的主干流转已明确为 `WAITING/COMPLETED -> DISPATCHING -> PROCESSING -> REVIEW_PENDING -> COMPLETED/WAITING/PROCESS_FAILED`。
- `AIProcessResultConsumer` 只做消息入口，真正的成功/失败回包处理都在 `QuestionServiceImpl.batchUpdateSuccessQuestions / batchUpdateFailedQuestions` 中，以事务方式推进题目状态、task 状态和 callback inbox 状态。

## 2026-03-27 深挖研究
- 本轮继续下钻四块：运行配置、数据库与流程表、测试分布、文档与代码一致性。
- 目标不是继续泛泛“介绍项目”，而是判断这个仓库现在离“能稳定运行、能讲清楚、能继续演进”分别还差什么。
- 当前仓库里存在大量 `target/classes` 产物副本，配置和 mapper 同时在 `src` 与 `target` 出现；研究时需要只认源码目录，避免把构建产物误当事实。
- `README_DEPLOY.md` 仍能说明运行依赖：JDK 17、Maven、MySQL、Nacos、RocketMQ、Redis，以及外部 Python AI 服务；但它对 AI 链路的描述仍偏旧，没有体现当前 `task/outbox/inbox` 语义。
- 配置层目前确认了几个重要事实：
  - `gateway` 在本地 `application.yml` 里直接写了 Redis 密码，且负责 `auth/user/excel-parser/question-bank` 四条核心路由。
  - `question-bank` 开发环境默认 `feature.mq.enabled=false`、`mock-enabled=true`，说明“不起 RocketMQ 也能本地联调主流程”是当前开发假设。
  - `leaf.properties` 里直接保留了本地 MySQL 用户名密码，`snowflake` 默认关闭、`segment` 默认开启。
  - 多处配置依然保留明文或可逆密钥/默认值，配置治理仍然是明显短板。
- 注册中心和异步流程表当前也已落地：
  - 各服务通过 `bootstrap.yml` 接 Nacos；不同服务在 `namespace`、认证配置上并不完全统一，存在配置风格不一致问题。
  - `question_process_task` 已包含 `mode / attempt_no / task_status / callback_key / source_question_status / failure_reason`。
  - `question_outbox_event` 负责派发事件状态与重试计数。
  - `question_callback_inbox` 负责回包幂等消费状态。
  - `docs/sql/2026-03-26-add-source-question-status-to-question-process-task.sql` 说明这套异步流程表还在持续演进，而不是一次性定死的老设计。
- 数据模型层的当前判断：
  - `t_question_bank` 现在更像“题目快照表”，通过 `process_status + last_review_mode + answer` 承载当前展示态。
  - `question_validation_record` 独立保存校验轮次里的 `original_answer_snapshot / ai_suggested_answer / validation_result / review_status / review_decision`，说明 VALIDATE 模式已经不是简单覆盖答案。
  - `t_file_info` 通过 `status` 维护上传文件生命周期，`tryMarkParsing` 直接把并发门禁下沉到 SQL。
  - `t_user` 的 mapper 本身看不出唯一索引定义，唯一约束更像是依赖数据库实际表结构而不是 XML 明示；代码层已用 `DuplicateKeyException` 做兜底收敛。
- 测试与生成配置层的当前判断：
  - 自动化测试主要集中在 `auth / question-bank / excel-parser / gateway/internal-auth filter`，并非“全仓库高覆盖”。
  - `question-bank` 已有 `QuestionDispatchServiceImplTest / QuestionOutboxDispatchSchedulerTest / AIProcessResultConsumerTest`，说明异步链路关键部位是有专门测试的。
  - `generatorConfig.xml` 仍直接写本地数据库 `root` 凭据，进一步说明这个仓库对开发机友好，但对配置安全和开源清理不够克制。
- 从测试方法名能进一步确认：
  - `QuestionServiceImplTest` 不只测 happy path，还覆盖了状态变化竞争、旧 task 回包、attempt 不匹配、validation record 创建失败、task 状态推进失败等异常路径。
  - `ExcelFileServiceImplTest` 已覆盖解析抢占失败、短链接为空/异常、导入失败回写状态等边界。
  - `AIProcessResultConsumerTest` 明确覆盖了“服务抛错时向 MQ 重新抛出异常以触发重试”。
  - `AuthServiceImplTest` 相比之下偏薄，当前公开可见的方法级测试点明显少于验证码服务和登录策略测试。
  - `markFileStatus(...)` 仍用于普通状态写回
  - `markFileStatusQuietly(...)` 仍用于异常分支的收尾回写
  - 没有把它们改造成混合语义的条件更新方法
- `question-bank` 已对“编辑 / 删除”的最终落库补上条件约束：
  - 删除改为按 `created_by + status in (WAITING, PROCESS_FAILED) + ids` 条件删除
  - 编辑改为按 `created_by + expectedStatus` 条件更新
  - 这样即使前置查询时题目还可编辑，真正落库时若状态已被并发推进，也会安全失败而不是误改/误删
- `ExcelFileServiceImpl` 已补充关键辅助方法注释，明确区分：
  - 普通状态写回
  - 收尾型 quiet 更新
  - 进入 `PARSING` 的原子抢占
- `auth` 已把验证码消费改成 Redis 原子 compare-and-delete：
  - 先做普通验证码预校验，避免错误验证码触发注册
  - 注册成功后再执行 Lua compare-and-delete，防止同验证码被并发请求重复消费
  - 若注册失败，不会提前删除验证码

## 2026-03-17 初步结构
- 项目根目录是一个 Maven 聚合工程，不是 Gradle。
- 主要模块包括：
  - `eaqb-auth`：认证与验证码相关
  - `eaqb-gateway`：网关与鉴权透传
  - `eaqb-user`：用户与权限
  - `eaqb-question-bank`：题库核心业务
  - `eaqb-excel-parser`：Excel 解析与导入
  - `eaqb-oss`：文件上传下载
  - `eaqb-distributed-id-generator`：分布式 ID 服务
  - `eaqb-framework`：公共基础组件
- 根目录存在多份中文设计/整改文档，说明这个项目近期做过一轮问题梳理和修复。

## 初步判断
- 从模块命名看，题库主业务可能集中在 `question-bank + excel-parser`。
- `distributed-id-generator` 单独拆服务，意味着该项目有明显的“中后台分布式系统练习”痕迹。

## 2026-03-17 根文档与聚合 POM
- `README.md` 将项目定义为“Spring Cloud Alibaba 微服务架构的智能题库系统”，核心卖点不是题库业务本身，而是：
  - Excel 导入
  - 预留 AI 处理
  - Java + Python 异构服务
  - RocketMQ 异步解耦
- 根 `pom.xml` 证实这是 Maven 聚合工程，核心技术栈包括：
  - Java 17
  - Spring Boot 3.0.2
  - Spring Cloud 2022.0.0
  - Spring Cloud Alibaba 2022.0.0.0
  - Sa-Token
  - MyBatis
  - Redis
  - RocketMQ
  - EasyExcel
  - MinIO / Aliyun OSS
- 根模块清单显示该仓库基本围绕“微服务全家桶”展开，而不是围绕复杂业务域展开。

## 更具体的判断
- 这个项目的实际意义，当前看更像“用题库业务承载一套微服务基础设施演示”。
- 也就是说，业务简单不是 bug，反而像是有意为之：作者想把学习重点放在服务拆分、网关、认证、异步消息、对象存储、分布式 ID、AI 扩展点上。

## 2026-03-17 模块层级补充
- `eaqb-framework` 是一个聚合模块，包含：
  - `eaqb-common`
  - `eaqb-spring-boot-starter-biz-operationlog`
  - `eaqb-spring-boot-starter-jackson`
  - `eaqb-spring-boot-starter-biz-context`
- `eaqb-user`、`eaqb-question-bank`、`eaqb-excel-parser` 都采用了 `api + biz` 双层拆分：
  - `api`：Feign 接口、DTO、常量
  - `biz`：真正业务实现
- 这说明项目不只是“服务拆分”，还在每个服务内部进一步做了“接口层 / 实现层”拆分。

## 当前判断修正
- 仅看服务名还不够，需要重点分析 `*-biz` 模块的依赖，因为聚合 `pom.xml` 只说明了模块边界，不说明真实耦合。
- 这种 `service -> api/biz` 的二次拆分，在当前业务复杂度下很可能偏重，但要结合实际调用链再下结论。

## 2026-03-17 关键依赖关系
- `eaqb-gateway`：只做网关、Redis 会话、Sa-Token 鉴权，不直接依赖业务服务 API。
- `eaqb-auth`：依赖 `eaqb-user-api`，说明认证服务通过 RPC 调用户服务，不直接管用户表。
- `eaqb-user-biz`：依赖 `eaqb-oss-api` 和 `eaqb-distributed-id-generator-api`，说明用户服务会调文件服务和 ID 服务。
- `eaqb-question-bank-biz`：依赖 `eaqb-oss-api`、`eaqb-distributed-id-generator-api`、RocketMQ。
- `eaqb-excel-parser-biz`：依赖 `eaqb-question-bank-api`、`eaqb-oss-api`、`eaqb-distributed-id-generator-api`、RocketMQ、EasyExcel。
- `eaqb-oss-biz`：相对独立，主要承接对象存储。
- `eaqb-distributed-id-generator-biz`：明显是基础设施型服务，带有 Zookeeper/Leaf 风格实现。

## 2026-03-17 登录与鉴权链路
- 网关通过 `application.yml` 配置路由，将 `/auth/**`、`/user/**`、`/excel-parser/**`、`/question-bank/**` 转发到对应服务。
- `eaqb-gateway` 的 `TrustedInternalCallGatewayFilter` 会从 Sa-Token 取当前登录用户 ID，并补充受信内部调用签名，供下游服务验签后读取。
- `eaqb-auth` 的 `AuthServiceImpl` 只负责：
  - 验证码或密码校验
  - 调用用户服务注册/查用户/改密码
  - 调用 Sa-Token 建立登录态
- `eaqb-user` 的 `UserServiceImpl` 负责：
  - 注册用户
  - 调 ID 服务生成 `userId` 与 `eaqbId`
  - 给新用户分配默认角色
  - 将角色写入 Redis
- 因此登录链路本质上是：
  - `gateway` 负责认证透传
  - `auth` 负责认证流程编排
  - `user` 负责账户数据落库

## 对这条链路的评价
- 这种拆分在“认证中心 + 用户中心”场景下并非完全不合理。
- 但结合当前业务体量，它更像是在练习“职责分离 + 服务间调用”模式，而不是被真实复杂度逼出来的拆分。

## 2026-03-17 Excel 导入与题目链路
- `eaqb-excel-parser` 的职责被拆成两段：
  - `/upload`：校验 Excel 文件格式、模板、大小，校验通过后上传到 OSS，并保存文件记录。
  - `/parse-by-id`：按 `fileId` 取 OSS 地址，下载文件，解析 Excel，再调用题库服务批量导入题目。
- `ExcelFileServiceImpl` 的导入链路实际是：
  - 用户上传文件
  - 校验扩展名和魔术头
  - EasyExcel 校验模板
  - 调 `oss` 服务存文件
  - 调 `distributed-id-generator` 生成 `fileId` / `preUploadId`
  - 本地保存文件记录
  - 解析时再下载文件
  - 调 `question-bank` 的批量导入接口
- `eaqb-question-bank` 的 `batchImportQuestions()` 本质很简单：
  - 为每道题生成 ID
  - 落库
  - 初始状态设为 `WAITING`

## 2026-03-17 题目状态机与 AI 异步链路
- `QuestionStatusStateMachine` 定义了题目状态流转：
  - `WAITING -> PROCESSING -> REVIEW_PENDING -> COMPLETED`
  - 失败分支：`PROCESSING -> PROCESS_FAILED -> WAITING`
- `QuestionExternalController` 暴露了题目 CRUD、审核、发送到队列接口。
- `sendQuestionsToQueue()` 会：
  - 校验题目归属
  - 校验当前状态是否允许发送
  - 更新状态为 `PROCESSING`
  - 发送 RocketMQ 消息
- `AIProcessResultConsumer` 并不直接更新数据库，而是：
  - 先消费 AI 回包
  - 缓存在本地
  - 批量写入 Redis
- `BatchProcessorService` 每 5 分钟从 Redis 读取 AI 结果批次，再统一更新题目状态和答案。

## 对主业务链路的评价
- 真实业务核心其实只有两件事：
  - 把 Excel 里的题导进来
  - 把题送去 AI 处理后再审核
- 但围绕这两件事，项目引入了：
  - 网关
  - 认证中心
  - 用户中心
  - OSS 服务
  - 分布式 ID 服务
  - MQ
  - Redis 批处理回流
- 这进一步说明项目目标更像“演示一套微服务协作模式”，而不是“解决一个复杂题库业务问题”。

## 2026-03-17 OSS 服务实现补充
- `eaqb-oss` 通过 `FileStrategy` 抽象文件上传与短链生成能力。
- `FileStrategyFactory` 通过读取 `storage.type` 配置选择 `MinioFileStrategy` 或 `AliyunOSSFileStrategy`。
- 更准确地说，这是“基于配置中心 + 工厂模式切换策略”，不是严格意义上的“Nacos 动态注册实现类”。
- 当前实现具备工程抽象价值，但还不应过度包装为“成熟可插拔平台”，原因包括：
  - 工厂里仍然是手动创建策略实例
  - 策略选择仍主要依赖配置判断
  - 文件 URL 解析兼容逻辑还写在服务层
- `getShortUrl` 这个命名不准确，真实语义是“生成对象存储私有文件的预签名访问 URL”。
- 目前 MinIO 和阿里云 OSS 两套策略都已支持生成预签名访问 URL，但两者的文件 URL 结构不同，服务层需要分别解析 bucket 和 objectName。

## 2026-03-17 AI 异步链路重构结论
- 原实现存在三个明显问题：
  - 文档/测试保留 `GENERATE + VALIDATE` 双模式，但服务实现被收缩成只按 `GENERATE` 处理。
  - AI 回包链路绕了“消费者 -> 本地缓存 -> Redis -> 定时批处理 -> 数据库”一整圈，复杂度过高。
  - 简历里的“熔断降级策略”“雪崩风险”等表述，与实际代码实现不匹配。
- 本轮代码收敛后：
  - `sendQuestionsToQueue()` 恢复真实双模式支持：
    - `GENERATE` 只发送无答案题目
    - `VALIDATE` 只发送已有答案题目
  - AI 回包由消费者直接更新数据库状态，不再经过 Redis 批处理链路。
  - 本地 mock 模式保留，用于 MQ 关闭场景的前端联调。
- 当前更准确的技术定性是：
  - `RocketMQ 异步解耦 + 状态机 + 直接消费回写`
  - 而不是“完整熔断降级架构”

## 2026-03-17 Excel 解析到题库导入链路收敛
- 上传阶段的“严格校验 + 预上传错误明细查询”被明确保留，作为本次重构的冻结原则。
- `parseExcelFileById()` 已从“大一锅端”主方法收敛为几个内部步骤：
  - 加载并校验文件归属
  - 获取对象存储访问链接
  - 下载 Excel 文件
  - 解析题目数据
  - 构造批量导入请求
  - 调题库服务导入
  - 聚合导入结果为 `ExcelProcessVO`
- 业务失败语义得到修正：
  - 文件不存在、无权限、访问链接缺失等业务失败不再被统一吞成 `SYSTEM_ERROR`
  - `parseExcelFileById()` 现在会对 `BizException` 直接返回 `Response.fail(...)`
- `ExcelProcessVO` 已开始填充更完整的处理结果字段：
  - `fileId`
  - `finishTime`
  - `processTimeMs`
- `excel-parser` 单测已补充并通过：
  - 文件存在且导入成功
  - 文件不存在
  - 文件越权
  - 下游题库导入失败
- 文件状态流转已在解析阶段真正落地：
  - `UPLOADED -> PARSING -> PARSED/FAILED`
  - 这让“文件目前处理到哪一步”从注释约定变成了实际行为
- 后续又对 `parseExcelFileById()` 做了一次小幅抽象优化：
  - 下载层不再返回 `Pair<InputStream, CloseableHttpResponse>`
  - 改为返回 `DownloadedExcelResource implements AutoCloseable`
  - 业务层改成 `try-with-resources`
- 这样做的价值不是“功能变多了”，而是把 HTTP 响应生命周期从业务流程里拿掉，让 `parseExcelFileById()` 更接近纯编排方法。

## 2026-03-17 题目管理与 AI 审核链路第二轮收敛
- 本轮确认这条链路的主要剩余问题不在“功能缺失”，而在“状态更新不够原子”：
  - 发送到 MQ 前后
  - AI 回包回写
  - 人工审核
  这三个节点都在修改 `process_status`，如果只是“先查再直接改”，并发下容易出现旧状态覆盖新状态。
- 已改为基于预期状态的 CAS 更新：
  - 发送前：`WAITING -> PROCESSING`
  - AI 成功：`PROCESSING -> REVIEW_PENDING`
  - AI 失败：`PROCESSING -> PROCESS_FAILED`
  - 审核通过：`REVIEW_PENDING -> COMPLETED`
  - 审核驳回：`REVIEW_PENDING -> WAITING`
- 发送到 MQ 的链路补上了失败补偿：
  - 若消息发送失败，会把已推进到 `PROCESSING` 的题目回滚到 `WAITING`
  - 这样至少避免“数据库显示处理中，但消息根本没发出去”的假状态
- 旧的 `updateQuestionStatusToReview(...)` 已删除，因为当前真实主链路已经是“消费者直接回写数据库”，继续保留该方法只会增加理解噪音。
- 当前更准确的工程描述应是：
  - `状态机 + CAS 状态流转 + MQ 失败补偿 + 消费者直接回写`
  - 而不是单纯“异步发送 + 人工审核”

## 2026-03-17 题目编辑 / 删除状态约束落地
- 题目编辑与删除现在不再是“只要是本人题目就能操作”，而是受状态约束：
  - 允许：`WAITING`、`PROCESS_FAILED`
  - 禁止：`PROCESSING`、`REVIEW_PENDING`、`COMPLETED`
- 这样限制的核心原因是语义一致性：
  - `PROCESSING` 表示题目已经发给 AI，修改题干/答案会让“库中数据”和“AI 实际处理内容”脱节。
  - `REVIEW_PENDING` 表示 AI 结果已经与当前题目形成待审上下文，继续编辑会破坏审核对象。
  - `COMPLETED` 表示人工确认后的终态，若还允许原地改删，会让“完成态”失去约束意义。
- 服务层已真正落地该限制，前端调试页也同步隐藏了不该出现的“编辑/删除”按钮。
- 当前更稳的业务口径是：
  - “草稿态/失败态可修订”
  - “处理中/待审核/已完成不可直接改删”

## 2026-03-23 面试题定向梳理启动
- 当前目标不是继续改代码，而是围绕面试题反向取证，确保回答能落到具体实现和真实边界。
- 第一轮定位已经确认本轮最值得优先读取的专题文档有两份：
  - `docs/Sa-Token与网关真实IP复盘.md`
  - `docs/内部调用签名设计与落地说明.md`
- 这说明“Gateway + Sa-Token + 下游透传”这一题，项目里除了代码实现外，还有专门复盘文档可以作为旁证。

## 2026-03-23 网关鉴权与上下文透传代码事实
- `eaqb-gateway` 的 `TrustedInternalCallGatewayFilter` 当前做的事情很明确：
  - 在响应式网关入口显式调用 `SaReactorSyncHolder.setContext(exchange)`；
  - 然后通过 `StpUtil.getLoginIdAsLong()` 取当前登录用户；
  - 再把 `callerService + userId + timestamp + method` 做 `HMAC-SHA256` 签名；
  - 最终重写 `userId / X-Caller-Service / X-Call-Timestamp / X-Call-Signature` 请求头转发给下游。
- 项目当前没有自己实现一套 Reactor Context 容器，而是依赖 Sa-Token 的 Reactor 适配能力，并在网关过滤器里手动补上下文桥接。
- 下游服务的实际落地不是 WebFlux Filter，而是 Servlet `OncePerRequestFilter`：
  - `TrustedInternalRequestFilter` 先验签、校验时间窗口；
  - 验签通过后才把 `userId` 写入 `LoginUserContextHolder`；
  - `LoginUserContextHolder` 底层仍是 `TransmittableThreadLocal`。
- 这意味着项目真实形态应被描述为：
  - “响应式 Gateway 取登录态 + 受信请求头透传”
  - “Servlet 下游验签后写入 ThreadLocal/TTL”
  - 而不是“所有下游服务都已经全链路 Reactor Context 化”。
- `TrustedFeignRequestInterceptor` 进一步说明服务间传播依赖的是：
  - 下游 Servlet 过滤器先把 `userId` 放进 `LoginUserContextHolder`
  - 后续 Feign 出站再从 `LoginUserContextHolder` 取出 `userId` 重新签名逐跳透传

## 2026-03-23 RocketMQ + 状态机链路代码事实
- `QuestionServiceImpl.sendQuestionsToQueue()` 的发送链路已被收成：
  - 先按“本人题目 + mode + 状态机允许 SEND”筛选；
  - 再逐题执行 `transitStatus(id, WAITING, PROCESSING)` 做 CAS 抢占；
  - 抢占成功后才 `rocketMQTemplate.syncSend(...)`；
  - 若发送失败，再执行 `transitStatus(id, PROCESSING, WAITING)` 做补偿回滚。
- `QuestionDOMapper.xml` 明确说明这里的“CAS”就是 SQL 条件更新：
  - `where id = ? and process_status = ?`
  - 成功回包时可走 `transitStatusAndAnswer`
  - 失败回包或审核走 `transitStatus / transitStatusAndClearAnswerByExpectedStatus`
- `AIProcessResultConsumer` 没有 Redis 缓冲或批处理兜底，当前真实实现是：
  - 直接消费 `AIProcessResultTopic`
  - 成功时调用 `batchUpdateSuccessQuestions(Map.of(questionId, result))`
  - 失败时调用 `batchUpdateFailedQuestions(Map.of(questionId, reason))`
  - 消费异常直接抛出 `IllegalStateException` 交由 MQ 重试
- 这意味着项目当前的一致性策略更准确地说是：
  - “状态机 + CAS 条件更新 + 发送失败补偿 + 消费端幂等回写”
  - 不是“严格意义上的 Exactly-Once 事务消息闭环”
- 代码里没有看到以下能力已经落地：
  - RocketMQ 事务消息
  - outbox 本地消息表
  - 消息去重表 / messageId 幂等表
  - 消费积压监控、自动扩容控制器
- 因此如果被追问“你怎么保证 Exactly-Once”，更稳妥的真实回答应该是：
  - RocketMQ 本身提供 at-least-once；
  - 项目靠状态列 CAS 把重复投递/重复回包收敛成幂等更新；
  - 发送侧再用失败补偿避免“库里 PROCESSING 但消息根本没发出去”的假状态；
  - 这属于“业务层幂等 + 至少一次投递”方案，不应夸大成纯粹的 Exactly-Once。

## 2026-03-23 Redis Lua / Excel 导入 / OSS / Leaf 代码事实
- 验证码 compare-and-delete 已真实落地在 `VerificationCodeServiceImpl`：
  - 通过 `DefaultRedisScript<Long>` 执行 Lua：
    - `GET key`
    - 值相等则 `DEL key` 并返回 `1`
    - 否则返回 `0`
  - `consumeLoginVerificationCode()` / `consumePasswordUpdateVerificationCode()` 都复用这段原子脚本。
- 当前 Excel 导入实现不是“10w 行分片事务导入”：
  - `excel-parser` 会先完整解析 Excel，再组装 `BatchImportQuestionRequestDTO`
  - `question-bank` 端 `batchImportQuestions()` 整体加了 `@Transactional`
  - 然后一次 `questionDOMapper.batchInsert(questionDOList)` 批量落库
  - 所以现状更像“单批次单事务批量插入”，并没有现成的分批提交/总任务表/最终一致性补偿设计。
- `EasyExcel` 的“流式解析”要讲准：
  - `ExcelParserUtil` 确实用了 `QuestionExcelListener extends AnalysisEventListener`
  - 但 listener 最终还是把题目聚合到内存列表 `listener.getQuestions()`
  - 因此当前实现是“按流读取，最后一次性组装列表导入”，不是“边读边分批落库”的真正大文件导入方案。
- OSS 抽象的真实形态：
  - `FileStrategyFactory` 读取 `storage.type`，在 `@Bean` 方法里手动 `new MinioFileStrategy()` 或 `new AliyunOSSFileStrategy()`
  - 工厂和 Bean 都加了 `@RefreshScope`
  - 这支持“配置刷新后重建 Bean”，但不是成熟的动态插件注册，更不是无缝热切换中的连接平滑迁移。
- 目前没有看到 OSS 限流降级相关实现：
  - 没有熔断器
  - 没有降级到备用存储
  - 没有本地重试队列或上传补偿任务
  - 因此“OSS 突然限流怎么办”只能按扩展设计来答，不能说项目已实现。
- Leaf 目前确实选的是号段模式：
  - `leaf.properties` 中 `leaf.segment.enable=true`
  - `leaf.snowflake.enable=false`
  - 业务服务中对外调用的也是 `/id/segment/get/{key}` 对应的 biz tag
- 号段模式实现直接复用了美团 Leaf 的典型思路：
  - 双 buffer
  - 低水位时异步预加载下一个 segment
  - 根据号段消耗时长动态调整 step
  - 监控页可查看 cache buffer 和 `leaf_alloc` 表状态
- 当前仓库里尚未发现“1000 QPS 平均 3ms”这类压测报告或测试代码证据，因此涉及具体压测数字时必须谨慎，不能硬认。

## 2026-03-24 题库 MQ 发送与回包链路复核
- 当前发送主链路的真实状态机已经是：
  - `WAITING -> DISPATCHING -> PROCESSING -> REVIEW_PENDING -> COMPLETED`
  - 发送失败补偿走 `DISPATCHING -> WAITING`
- 这和部分旧笔记里写的“发送前直接推进到 PROCESSING”已经不一致；本轮审查必须以现代码为准。
- `sendQuestionsToQueue()` 的发送侧当前做法是：
  - 先按归属、状态机 `SEND`、模式是否匹配答案形态做过滤
  - 再逐题 CAS 抢占 `WAITING -> DISPATCHING`
  - `syncSend` 成功后再推进 `DISPATCHING -> PROCESSING`
  - 发送异常时回滚 `DISPATCHING -> WAITING`
- `AIProcessResultConsumer` 当前不是批量消费，也没有 Redis 中转，是真正的“单条消息 -> 直接调 service 回写数据库”。
- `VALIDATE` 成功回包当前会先插入 `question_validation_record(review_status=PENDING)`，再把题目推进到 `REVIEW_PENDING`；这段的事务一致性和脏记录风险需要继续核对。
- `MQConstants.TOPIC_TEST = "TestTopic"` 仍然是生产发送主题名，命名上明显带测试语义，后续需要判断这是否只是命名问题，还是会造成环境/运维误判。
- 已确认的发送/回包链路问题：
  - 发送成功后存在 `DISPATCHING` 中间态窗口：Broker 已确认收消息后，代码才把题目从 `DISPATCHING` 推进到 `PROCESSING`；若这一步 CAS 失败，代码只记失败计数，不会回滚，也不会补偿重试，题目会卡在 `DISPATCHING`。
  - 回包侧只接受 `PROCESSING -> REVIEW_PENDING/PROCESS_FAILED`；因此只要 AI 回包先于本地 `DISPATCHING -> PROCESSING` 落库，消费者就会把它当成“非法流转”直接跳过。
  - `batchUpdateSuccessQuestions()` / `batchUpdateFailedQuestions()` 在单条处理内部吞掉全部异常并返回计数，导致消费者拿到 `updatedCount=0` 也会正常 ACK；一旦数据库短暂异常或状态窗口错过，消息不会重试，属于真实丢消息风险。
- 失败回包携带的 `reason` 当前只在 consumer 里组装后传给 service，但 `batchUpdateFailedQuestions()` 完全未使用该值，失败原因被静默丢弃。
  - `VALIDATE` 成功回包是“先插校验记录，再改题目状态”；当状态 CAS 失败时，方法只记 warn 并继续，事务不会回滚，这会留下孤儿 `PENDING` 校验记录。

## 2026-03-27 全量代码审查与上帝类评估
- 本轮审查目标从“深挖某条链路”切换成“完整阅读整个仓库”，包括源码、测试、配置、SQL 与说明文档，但判断优先级仍是代码 > 文档。
- 用户明确暂时排除两类问题：
  - 打印敏感信息
  - 硬编码配置
- 因此本轮问题清单会重点记录：
  - 主链路逻辑错误
  - 状态流转漏洞
  - 并发/幂等/一致性缺口
  - 职责堆积与上帝类风险
- 已确认仓库存在较多历史审查文档和未提交修改；这些材料只能作为索引，不能代替再次阅读源码。
- 基础框架组（`framework / gateway / auth`）当前代码事实：
  - `gateway -> framework` 的内部调用签名链条已经完整：网关重写签名头，下游统一验签，Feign 再逐跳续签，设计骨架成立。
  - `TrustedInternalRequestFilter` 明确把“是否信任 userId 请求头”收口到统一过滤器，而不是散在各业务控制器里，这部分职责边界是清楚的。
  - `auth` 已把登录分发拆成 `LoginStrategyFactory + VerificationCodeLoginStrategy + PasswordLoginStrategy`，登录方式扩展点不算混乱。
  - 但 `auth` 里仍能看到一些“设计已经写出影子但没有真正落地”的代码，例如 `AlarmConfig.alarmHelper()` 是配置方法却没有 `@Bean`，更像半成品扩展点而非真实运行链路。

## 2026-03-28 巡检续扫启动
- 本轮不是新开任务，而是接着上次“全量代码审查与上帝类评估”继续往下读，重点仍是当前代码而不是历史文档。
- 根目录仍保留多轮审查文档、计划文件和大量未提交改动；因此这次巡检的第一原则仍是“只基于现工作区事实，不回滚、不假设”。
- 这轮会先重新建立模块阅读地图，再继续看高复杂度入口和仍未完整读透的模块边界。
- 根 `README.md` 仍明显偏旧：
  - 仍把 Redis 描述成“缓存 AI 处理结果”；
  - 题库服务与 AI 的交互图仍是老式“题目服务直连 MQ，再由 Python 服务回 MQ”，没有体现当前 `task/outbox/inbox` 语义。
- 当前按文件体量重新确认的高复杂度热点主要是：
  - `QuestionCallbackAppService`
  - `ExcelFileServiceImpl`
  - `QuestionDispatchAppService`
  - `QuestionCrudAppService`
  - `UserServiceImpl`
  - `VerificationCodeServiceImpl`
  这说明题库回包编排、Excel 导入编排、用户服务和验证码服务仍是最值得继续深读的实现中心。
- 新补出的代码事实：
  - `gateway` 仍把 `/auth/verification/code/password-update/send` 放在匿名白名单里，但 `VerificationCodeServiceImpl.sendPasswordUpdateCode()` 实际第一步就是通过内部 RPC 读取“当前登录用户手机号”，并不支持匿名语义。
  - `user` 的资料更新链路和 `excel-parser` 的文件记录链路都还保留“先把文件传到 OSS，再更新/插入数据库”的顺序；一旦后半段失败，会留下孤儿对象。
  - `UserServiceImpl.updateUserInfo()` 当前没有像改密那样校验更新行数，也没有先确认当前用户仍是有效未删除状态；对应的 `UserDOMapper.updateByPrimaryKeySelective` 也没有 `is_deleted = 0` 条件。
  - `FileInfoDOMapper.selectByPrimaryKey` 也没有过滤 `is_deleted`，说明文件记录一旦以后真正启用软删除，当前读取/解析链路会把已删记录继续当作有效文件处理。

## 2026-03-28 巡检续扫第二轮
- 当前已确认的新增问题：
  - 鉴权边界不一致：改密验证码发送接口在网关是匿名放行，但服务实现依赖登录上下文，匿名请求会穿过网关后在内部 RPC 层才失败，返回语义会漂成“查不到当前用户”而不是“未登录”。
  - 用户资料更新存在假成功/脏写窗口：当前实现直接按主键更新，既不兜底当前用户是否仍有效，也不检查更新行数；若账号已被软删、上下文脏掉或并发删除，接口仍可能返回成功，甚至改到已删除记录。
  - OSS 资源一致性仍偏弱：Excel 上传、头像上传、背景图上传都属于“先上传对象存储，再落业务记录/更新主表”，没有失败补偿或删除回滚；DB/ID/RPC 后半段一旦出错，会留下前端不可见的孤儿文件。

## 2026-03-29 OSS 上传边界重构设计结论
- 设计已收敛为“显式业务上传接口”，不再保留“OSS 自己猜类型和路径”的模式。
- 推荐接口拆分为：
  - `uploadExcel(file, objectName)`
  - `uploadAvatar(file)`
  - `uploadBackground(file)`
- `userId` 继续由 OSS 服务自己从上下文读取，不由调用方透传。
- OSS 内部固定路径规则：
  - Excel：`excel/{userId}/{objectName}`
  - 头像：`image/{userId}/avatar`
  - 背景图：`image/{userId}/background`
- Excel 上传链路改为“先本地正式记录，再远程上传”：
  - 校验失败只写 `excel_pre_upload_record`
  - 校验成功后先拿 `fileId`、先插 `t_file_info(status=UPLOADING)`，再上传 OSS
  - 上传成功后推进到 `UPLOADED`
  - 上传失败后推进到 `UPLOAD_FAILED`
- 图片链路保持轻量：
  - 先在 `user` 服务做图片校验
  - 再调固定槽位上传接口
  - 成功后再更新用户表
- 本次设计明确不引入 Seata / Saga / outbox，只先把稳定 key、职责边界和可恢复入口收好。

## 2026-03-25 发送/回包复核二次结论
- 回包主链路现在已经明显比上一版稳：
  - 先校验 callback 是否应处理
  - 再校验 `taskId/questionId/attemptNo/current active task`
  - 再插 inbox `RECEIVED`
  - 处理 question/task/validation record
  - 最后推进 inbox `PROCESSED`
  - 中间异常会抛出并触发事务回滚，不再留下脏 `RECEIVED`
- 旧的“回包半消费”问题本轮已基本修掉。
- 当前剩余的主要风险集中在发送侧 `QuestionDispatchServiceImpl.dispatchTask()`：
  - 如果 `syncSend` 成功后，`outbox -> SENT`、`task -> DISPATCHED` 已推进，但 `question DISPATCHING -> PROCESSING` 失败，catch 会把 outbox 再改成 `RETRYABLE` 并返回 false；
  - 由于异常被方法内部吞掉，事务会提交，最终可能留下：
    - task = `DISPATCHED`
    - question = `DISPATCHING`
    - outbox = `RETRYABLE`
  - 这等于“消息大概率已经发出，但 outbox 又允许重试”，存在重复派发同一 task 的风险。
