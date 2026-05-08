# OSS 上传边界重构设计

## 背景

当前项目的文件上传边界存在三个核心问题：

1. `oss-service` 只接收 `MultipartFile`，再由自己根据文件类型推断目录和对象名。
2. `excel-parser` 采用“先上传 OSS，再生成 `fileId`，再插入 `t_file_info`”的顺序，存在“OSS 成功但本地还没记账”的窗口。
3. `user` 的头像、背景图也走“先上传，再更新用户表”，且当前 OSS 并不知道“头像”和“背景图”是两个不同槽位。

这些问题叠加后，会带来：

- 调用方失去稳定 key 控制权，无法可靠重试。
- `oss-service` 承担了本应由业务服务决定的路径语义。
- 上传成功但本地未落库时，会产生孤儿对象。

## 目标

本次重构目标：

1. 收回上传路径的业务决策权，让业务服务显式决定稳定对象名。
2. 让 `oss-service` 只负责“校验类型 + 取当前用户 + 按规则上传”。
3. 让 Excel 上传链路先建立本地正式记录，再执行远程上传，缩小不可恢复窗口。
4. 让头像、背景图上传具备固定槽位覆盖语义。

## 非目标

本次不做：

1. Seata / XA / Saga / outbox 级别的强一致方案。
2. OSS 孤儿对象的异步清理任务。
3. 独立图片资源表或头像历史版本管理。
4. 现有下载访问凭证接口之外的更大范围文件访问体系重构。

## 设计结论

### 1. OSS 上传接口改为显式业务接口

保留“业务服务决定资源语义，OSS 服务负责上传”的分工。

新增三个上传接口：

1. `uploadExcel(file, objectName)`
2. `uploadAvatar(file)`
3. `uploadBackground(file)`

约束：

- `uploadExcel` 的 `objectName` 必传，为空直接失败。
- `uploadAvatar` / `uploadBackground` 不再接收自由命名参数。
- OSS 不再自行随机生成对象名。
- OSS 不再通过“图片/Excel”推断具体业务槽位。

### 2. OSS 内部路径规则固定

OSS 服务自己从登录上下文读取 `userId`，调用方不传 `userId`。

最终对象路径固定为：

- Excel：`excel/{userId}/{objectName}`
- 头像：`image/{userId}/avatar`
- 背景图：`image/{userId}/background`

这样可以保证：

- Excel 同一 `fileId` 的重试始终写入同一路径。
- 头像与背景图始终覆盖固定槽位。
- 上游无法伪造其他用户目录。

### 3. Excel 上传链路改为“先本地记录，再远程上传”

当前“先上传再落库”的顺序改为：

1. 本地完成 Excel 校验：
   - 文件大小
   - 扩展名
   - 魔数
   - 模板结构
2. 若校验失败：
   - 仅写 `excel_pre_upload_record`
   - 返回 `preUploadId`
   - 不生成正式文件记录
3. 若校验成功：
   - 先获取 `fileId`
   - 先插入 `t_file_info`
   - 初始状态置为 `UPLOADING`
   - 调 `oss-service.uploadExcel(file, "{fileId}.xlsx")`
   - 上传成功后回填 `objectKey`，并改状态为 `UPLOADED`
   - 上传失败后改状态为 `UPLOAD_FAILED`

这并不能消灭“远端成功、本地未来得及更新”的窗口，但能保证：

- 该窗口至少对应一条正式文件记录；
- 后续可基于 `fileId + status` 做对账或补偿；
- 不再出现“OSS 已有文件，但系统完全不知道这份文件存在过”的情况。

### 4. Excel 记录改存 objectKey

`t_file_info` 不再持久化厂商访问 URL，而是持久化对象定位信息：

- `object_key = excel/{userId}/{fileId}.xlsx`

读取文件时，再由 OSS 服务根据 `objectKey` 生成限时下载访问凭证。

### 5. Excel 状态补充

`t_file_info.status` 新增一个上传阶段状态：

- `UPLOADING`
- `UPLOAD_FAILED`
- `UPLOADED`
- `PARSING`
- `PARSED`
- `FAILED`

语义区分：

- `UPLOADING`：正式文件记录已创建，OSS 上传尚未完成。
- `UPLOAD_FAILED`：上传阶段失败。
- `FAILED`：解析阶段失败，继续保留原有语义。

### 6. 用户图片上传走固定槽位覆盖

`user` 服务内新增图片校验工具，至少覆盖：

- 非空校验
- 大小限制
- 扩展名白名单
- 魔数或内容类型兜底校验

校验通过后：

- 头像调用 `uploadAvatar(file)`
- 背景图调用 `uploadBackground(file)`

OSS 返回成功后，再更新 `t_user.avatar` / `t_user.background_img`。

本次不引入图片资源表，也不记录头像历史版本。语义保持为：

- 一个用户一个头像槽位
- 一个用户一个背景图槽位

## 模块改动范围

### `eaqb-oss-api`

需要把原先的单一上传 Feign 接口拆成三个显式上传接口。

### `eaqb-oss-biz`

需要修改：

- `FileController`
- `FileService`
- `FileServiceImpl`
- `FileStrategy`
- `MinioFileStrategy`
- `AliyunOSSFileStrategy`

职责变化：

- 由“接收文件后自己猜类型和路径”
- 改为“按具体接口执行对应上传策略”

### `eaqb-excel-parser-biz`

需要修改：

- `OssRpcService`
- `ExcelFileServiceImpl`
- `FileInfoDOMapper.xml`
- 相关测试

重点：

- 先拿 `fileId`
- 先写 `UPLOADING`
- 再远程上传
- 上传结果再推进状态

### `eaqb-user-biz`

需要修改：

- `OssRpcService`
- `UserServiceImpl`
- 新增图片校验工具类
- 相关测试

重点：

- 本地先校验图片
- 分别调用头像 / 背景图上传接口
- 成功后再更新用户表

## 错误处理约束

### Excel

- 校验失败：继续沿用 `preUploadId + 错误详情` 模型。
- 上传失败：返回上传失败，不创建孤立的“成功文件记录”。
- 上传成功但回填状态失败：本次不做分布式补偿，但记录会停留在 `UPLOADING`，后续可恢复。

### 图片

- 校验失败：直接返回，不调用 OSS。
- 上传失败：直接返回，不更新用户表。
- 上传成功但更新用户表失败：仍可能残留孤儿图片，但由于槽位路径固定，重复上传时会覆盖同一路径，不会无限扩散。

## 为什么不用一个 `uploadImage(file, slot)` 通用接口

当前业务只有两个固定图片槽位：

- `avatar`
- `background`

因此本次不引入额外的 `slot` 字符串协议，而是直接暴露：

- `uploadAvatar(file)`
- `uploadBackground(file)`

这样可以减少：

- 参数传错
- 额外分支判断
- 接口歧义

并让调用方与测试更直观。

## 为什么不让上游传完整 `objectKey`

本次只让 Excel 传 `objectName`，图片接口不接收自由命名参数，原因是：

1. `userId` 属于认证上下文，应由 OSS 自己读取。
2. 目录前缀 `image/`、`excel/` 属于 OSS 内部固定规则。
3. 完整 `objectKey` 暴露给调用方后，容易伪造其他用户目录或引入路径漂移。

因此当前设计是：

- Excel 允许上传调用方决定对象名，例如 `{fileId}.xlsx`
- 图片完全固定为两个槽位名，不开放自由命名

## 兼容性策略

本次建议直接替换旧上传接口，不长期保留双轨。

原因：

- 旧接口的“随机 UUID + 类型猜测”语义会继续制造理解噪音。
- 当前调用方只有 `excel-parser` 与 `user` 两处，影响面可控。

## 测试要求

至少补充或更新以下测试：

1. OSS API / service
   - `uploadExcel` 空 `objectName` 失败
   - `uploadAvatar` / `uploadBackground` 走固定对象路径
   - 类型不匹配时失败
2. Excel 上传
   - 校验失败只产生 `preUploadId`
   - 校验成功后先写 `UPLOADING`
   - OSS 成功后状态推进为 `UPLOADED`
   - OSS 失败后状态推进为 `UPLOAD_FAILED`
3. 用户图片上传
   - 非法图片被本地校验拦截
   - 头像调用头像接口
   - 背景图调用背景图接口

## 实施顺序

1. 先改 `eaqb-oss-api` 与 `eaqb-oss-biz`
2. 再改 `excel-parser`，打通 `UPLOADING -> UPLOADED/UPLOAD_FAILED`
3. 再改 `user` 图片上传链路
4. 最后收测试与常量

## 预期收益

完成后，系统将具备以下改进：

1. 上传对象路径由隐式猜测变为显式规则。
2. Excel 上传具备稳定 `fileId` 路径与正式记录前置能力。
3. 头像、背景图具备固定槽位覆盖语义。
4. `oss-service` 的职责边界更清晰，业务服务重新掌握命名与幂等控制权。
