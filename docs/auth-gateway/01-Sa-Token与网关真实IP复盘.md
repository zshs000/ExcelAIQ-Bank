# Sa-Token 与网关真实 IP 复盘

## 1. 今晚这 2 个小时到底做了什么

表面上看，只是给 `eaqb-gateway` 补了一个透传真实客户端 IP 的过滤器，并补了一组单测。

但更准确地说，今晚完成的是一条完整的推导链：

1. 从 Sa-Token 底层上下文抽象出发，试图真正搞懂它在不同运行环境里的工作方式。
2. 为了理解 Sa-Token 的上下文实现，反过来补 Servlet 基础，重新梳理 `HttpServletRequest`、`HttpServletResponse`、`HttpSession`、request attribute 这些概念。
3. 回看自己之前的单体项目，发现虽然鉴权已经改成 JWT，但部分接口仍然注入了 `HttpServletRequest`。
4. 继续追代码，发现这些接口注入 `HttpServletRequest` 的目的不是为了 session 鉴权，而是为了读取客户端原始 IP，例如用于注册/登录/验证码限流。
5. 再对照 EAQB 项目，发现验证码发送同样存在 IP 维度限流。
6. 由于 EAQB 是微服务架构，请求先经过 gateway 再进入 `auth`，于是必须继续确认：`auth` 里拿到的 IP 到底是不是用户真实 IP。
7. 继续检查后发现，当前网关并没有显式透传 `X-Forwarded-For` 和 `X-Real-IP`。
8. 最终落地修复：在网关新增全局过滤器，将真实客户端 IP 透传给下游服务，并补单测验证。

这不是“顺手改了个小问题”，而是一次从框架理解一路落到业务安全链路的真实闭环。

## 2. 推导链为什么成立

这条链路成立的关键，不在于“我恰好发现了一个 bug”，而在于几个原本分散的知识点被连起来了。

### 2.1 Sa-Token 不是只靠 ThreadLocal 魔法

Sa-Token 上层统一使用 `SaRequest`、`SaResponse`、`SaStorage` 三个接口抽象请求上下文。

`SaTokenContextForThreadLocalStorage` 做的事情，本质上只是把这三个接口对象封进一个 `Box`，再放进 `ThreadLocal`。它解决的是“上层如何统一读取上下文”的问题，不是“所有运行环境都天然适合 ThreadLocal”的问题。

也就是说，真正的关键点从来不是 `ThreadLocal` 本身，而是：

- Servlet 环境下，当前请求和当前线程绑定得更紧；
- Reactor / Gateway 环境下，线程会切换，所以上下文不能简单依赖线程局部变量；
- 框架必须把“运行时上下文载体”桥接成 Sa-Token 能理解的那套抽象。

### 2.2 理解 Sa-Token，必须顺手搞懂 Servlet

在 Servlet 环境里，Sa-Token 的 `SaStorageForServlet` 底层直接包装的是 `HttpServletRequest`。

这说明：

- `request` 本身不只是“拿参数”的对象；
- 它还是一次 HTTP 请求在服务端生命周期内的重要上下文载体；
- `request.setAttribute()` 本质是 request 作用域内的临时存储；
- `HttpSession` 则是跨请求共享的数据容器，作用域和 request 不一样。

只有把这些概念分清，才能看懂为什么 Sa-Token 的 Servlet 适配层会这么设计。

### 2.3 JWT 不等于不需要 HttpServletRequest

这是今晚一个很关键的认知点。

之前单体项目已经改成 JWT 鉴权，但某些接口仍然保留了 `HttpServletRequest` 参数。继续追之后发现，这些接口并不是在用它做 session 鉴权，而是在做别的事情，例如：

- 读取 `X-Forwarded-For`
- 读取 `X-Real-IP`
- 回退 `request.getRemoteAddr()`

也就是说：

- JWT 解决的是“身份凭证如何携带和校验”；
- `HttpServletRequest` 解决的是“服务端如何拿到这次 HTTP 请求的完整上下文”。

两者不是替代关系。

## 3. 为什么最后会落到修 EAQB 的 gateway

当“JWT 项目里为什么还会出现 `HttpServletRequest`”这个问题想通之后，就自然会联想到 EAQB。

`eaqb-auth` 的验证码发送逻辑里，本来就有 IP 维度限流。其读取顺序是：

1. `X-Forwarded-For`
2. `X-Real-IP`
3. `request.getRemoteAddr()`

这在单体项目里通常没问题，因为应用自己就是 HTTP 入口。

但在微服务项目里，`auth` 前面还有 gateway，于是问题变成：

- `auth` 读到的是用户真实 IP？
- 还是网关作为上一跳的 IP？

继续检查后发现，当前 `eaqb-gateway` 并没有显式补充真实客户端 IP 请求头。这样一来，下游 `auth` 的 IP 限流逻辑虽然“代码上存在”，但在经过网关之后可能失真。

这就不是一个代码洁癖问题了，而是一个实际的安全与风控正确性问题。

## 4. 今晚的实际改动

### 4.1 网关补充客户端真实 IP

新增文件：

- `eaqb-gateway/src/main/java/com/zhoushuo/eaqb/gateway/filter/ClientIpHeaderFilter.java`

作用：

- 优先保留已有的 `X-Forwarded-For`
- 优先保留已有的 `X-Real-IP`
- 若上游未携带，则回退到 `exchange.getRequest().getRemoteAddress()`
- 将最终结果写回请求头，供下游服务读取

这样，下游 `auth` 的验证码限流逻辑就能拿到正确的客户端来源信息。

### 4.2 为过滤器补单测

新增测试：

- `eaqb-gateway/src/test/java/com/zhoushuo/eaqb/gateway/filter/ClientIpHeaderFilterTest.java`

覆盖两条主链路：

- 已有 `X-Forwarded-For` / `X-Real-IP` 时保留原值
- 没有代理头时，使用 `remoteAddress` 回填

### 4.3 顺手发现并修掉了一个编译问题

在补测试时，单测先把一个隐藏问题打出来了：`ClientIpHeaderFilter` 文件里混入了一行脏字符，导致网关模块无法编译。

这件事本身也说明了一个现实问题：

- 不写测试，很多“看起来写完了”的代码其实还没有真正闭环。

## 5. 今晚真正获得的认知

### 5.1 技术认知

- Sa-Token 的核心价值之一，不是某个具体 API，而是“上层统一抽象 + 底层环境适配”的设计。
- Servlet 和 Reactor 的最大差异，不是语法风格，而是上下文传播模型不同。
- JWT、Session、HttpServletRequest、Gateway 转发头，这些概念虽然分属不同层次，但在线上链路里会真实地串在一起。

### 5.2 工程认知

- 单体里成立的“读取客户端 IP”逻辑，迁移到微服务里后不一定自动成立。
- 一条安全链路要真正有效，不能只看业务服务代码，还要看入口层是否把上下文完整传下来。
- 网关不是只做路由和鉴权，它还是请求上下文治理的关键入口。

### 5.3 面试表达认知

今晚这段经历是可以讲的，而且不是那种空泛的“我看了很多源码”。

更准确的表达方式应该是：

“我在研究 Sa-Token 上下文设计时，顺带补了 Servlet 和响应式上下文传播的底层理解。之后我回看自己之前的 JWT 单体项目，发现部分接口虽然不依赖 session 鉴权，但仍要注入 `HttpServletRequest` 获取原始客户端 IP 做限流。这个点让我反向检查了当前的微服务项目，发现验证码发送同样有 IP 限流，但网关没有把真实 IP 透传给下游，导致限流语义可能失真。最终我在 gateway 增加了全局过滤器补齐 `X-Forwarded-For` / `X-Real-IP` 透传，并补单测验证，完成了从底层理解到安全链路修复的闭环。” 

这个说法是成立的，而且有因果链，有源码依据，也有落地改动。

## 6. 这次学习值不值

值。

因为今晚得到的不是一个零散结论，而是一个以后反复能用的判断框架：

- 为什么这里还会出现 `HttpServletRequest`
- 为什么 JWT 场景仍然离不开 HTTP 请求上下文
- 为什么 Gateway 后面要关注 `X-Forwarded-For`
- 为什么响应式环境里的安全框架要特别关注上下文传播

这些问题以后再出现，不会再是靠记忆硬背，而是能顺着链路自己推出来。

## 7. 下一步可以继续补的点

1. 再彻底梳理一次 `SaReactorFilter`、`SaReactorSyncHolder` 和 Reactor Context 的桥接过程，形成一版更硬核的源码级笔记。
2. 明确网关前面如果未来接 Nginx / Ingress，`X-Forwarded-For` 的拼接与信任边界应该怎么定义。
3. 补一版“单体请求上下文”和“微服务请求上下文”的对照图，帮助后续面试表达更稳定。
4. 复查其他依赖客户端来源信息的功能，确认是否也存在“经过网关后语义失真”的风险。
