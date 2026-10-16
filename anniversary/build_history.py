"""Export auditable anniversary data from origin/master, regardless of checkout."""
import collections
import datetime as dt
import json
import pathlib
import subprocess

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
TZ = dt.timezone(dt.timedelta(hours=8))


def git(*args):
    return subprocess.check_output(["git", "-c", "core.quotepath=false", *args], cwd=ROOT).decode("utf-8").strip()


SOURCE_REF = "origin/master"
SOURCE_HEAD = git("rev-parse", "--verify", f"{SOURCE_REF}^{{commit}}")

commits = []
for sha in git("rev-list", "--reverse", SOURCE_HEAD).splitlines():
    raw = git("show", "-s", "--format=%H%x1f%aI%x1f%cI%x1f%P%x1f%B", sha).split("\x1f", 4)
    full, authored, committed, parents, body = raw
    commits.append(dict(sha=full, hash=full[:7], date=dt.datetime.fromisoformat(authored).astimezone(TZ).isoformat(),
                        committed=committed, subject=body.splitlines()[0], body=body,
                        merge=len(parents.split()) > 1))
by_hash = {c["hash"]: c for c in commits}

# Curated explanations are editorial; quoted subjects/bodies below are Git originals.
events = [
    ("ea03d90", "第一条提交", "开场", "2025 年 10 月 16 日 21:59:49，Initial commit。十六秒后，微服务骨架提交。故事从这里开始。", 0),
    ("6b52f5e", "第一次登录成功", "搭建", "接入 Sa-Token 和 JWT。早期的进展，是一个组件接上了、一个接口跑通了。", 0),
    ("fae696f", "服务开始说话", "搭建", "Nacos、网关、对象存储陆续接上；OpenFeign 把服务之间的调用串了起来，并透传 userId。", 0),
    ("76f085b", "第一次拆开重整", "边界", "把用户逻辑从认证服务移到用户服务，也处理了线程切换后的 Sa-Token 上下文问题。组件接好之后，边界与运行细节开始冒出来。", 0),
    ("9742943", "最最最最重要的一步", "用户", "终于能把完整的校验错误返回给用户。这份兴奋，与实习时遇到笼统导入报错的经历相呼应。", 1),
    ("e829bde", "服务器被入侵", "踩坑", "提交记录写下了无密码 Redis 被利用的经历，也记录了当时的修复。部署环境带来的教训，直接进了提交日记。", 1),
    ("e08dc88", "“明天就差不多了”", "兴奋", "用 AI 辅助搭建 FastAPI 服务，接上 Nacos 和 MQ。原文里的三个感叹号，保留了那天觉得项目快完成的心情。", 1),
    ("3b11f04", "承认 MVC 没学透", "踩坑", "处理错误参数时碰到循环视图，补上 @ResponseBody。作者在提交里直接写明：未对 MVC 模型有充分了解。", 1),
    ("04e77e7", "回来，先补漏洞", "重看", "二月集中修正分页拷贝、落库路径、接口契约与文件归属校验。回到项目，先清理原来未对齐的细节。", 2),
    ("600de08", "重复解析要抢占", "并发", "进入 PARSING 改为带用户和原状态条件的数据库更新。同一个文件被并发触发，不能各做各的。", 2),
    ("158e321", "验证码只用一次", "并发", "把比较与删除验证码合成 Lua 原子操作。随后继续追问下游超时、用户注册幂等与登录恢复。", 2),
    ("55ff0eb", "身份传递加上签名", "信任", "网关 ID 透传引入数字签名校验。开始认真回答：下游收到的用户身份，凭什么可信？", 2),
    ("00aa160", "图片留下 objectKey", "语义", "持久化对象身份，按场景签发访问 URL。把长期资源标识与有时效的访问地址分开。", 2),
    ("eeba783", "导入有了中间态", "导入", "按 chunk 追加到临时表，串联 create、append、finish、commit，并增加清理任务。分批传输与正式提交有了各自的位置。", 3),
    ("479f858", "凌晨两点的修补", "事务", "两阶段导入落地后的第二天，继续修批次失败回滚，取号改成批量操作。大改之后，还有具体边界要补齐。", 3),
    ("fe55faf", "消息有了账本", "可靠性", "引入 task / outbox / inbox。先在本地保存任务与发送意图，再异步投递；回包按记录处理幂等。", 4),
    ("101ee76", "多实例也要协调", "恢复", "outbox 增加数据库抢占与 SENDING 状态，超时事件可以恢复。重试流程开始考虑多个实例同时运行。", 4),
    ("d7b00ed", "让 CI 来检查", "工程", "加入 GitHub Actions 编译与测试。五月也建立了审查上下文、整理文档，并开始更清楚地保留设计理由。", 5),
    ("31bb91c", "把过度自动化收回来", "判断", "归档旧版多智能体协作配置。后来在《项目初心》中写明：保留流程、上下文和审查记录，关键取舍由人判断。", 5),
    ("c399d6b", "转正在数据库内完成", "导入", "finish 分页绑定 formal_id，commit 改为 INSERT INTO … SELECT …，避免 Java 全量加载临时题目。", 3),
    ("530eaa0", "超时，不等于没成功", "幂等", "按 fileId + userId 恢复已有批次；同一批次串行提交，COMMITTED 幂等返回。补上 commit 响应丢失之后的重试语义。", 6),
    ("6f44ce5", "再收紧 finish 边界", "边界", "重复导入修复后继续检查 finish 的状态约束。恢复链路、调用方和收尾步骤一起检查。", 6),
    ("e5b4047", "日志也需要边界", "安全", "操作日志为密码、验证码等字段脱敏，随后改为 JSON 树遍历、覆盖响应与回归测试。", 5),
    ("19084e1", "重看自己的结论", "判断", "九月重新比较验证码限流的实际改造成本，最终用短 Lua 脚本合并资格检查、验证码创建和额度更新。旧理由也值得重新检验。", 7),
]
milestones = []
for ref, title, tag, detail, chapter in events:
    c = by_hash[ref]
    milestones.append(dict(ref=ref, title=title, tag=tag, detail=detail, chapter=chapter, date=c["date"][:10]))
milestones.sort(key=lambda x: (x["date"], next(i for i, c in enumerate(commits) if c["hash"] == x["ref"])))

monthly = collections.Counter(c["date"][:7] for c in commits)
snapshots = []
for ref, label in [("3b11f04", "2025.11"), ("04e77e7", "2026.02"), ("eeba783", "2026.04.10"), (SOURCE_HEAD, "2026.09")]:
    files = git("ls-tree", "-r", "--name-only", ref).splitlines()
    tests = [p for p in files if "/src/test/" in p and p.endswith(".java")]
    snapshots.append(dict(ref=ref, label=label, javaTestFiles=len(tests)))

result = dict(repo="https://github.com/zshs000/ExcelAIQ-Bank", branch="master", sourceRef=SOURCE_REF,
              head=SOURCE_HEAD, anniversary="2026-10-16", first=commits[0]["date"],
              count=len(commits), nonMergeCount=sum(not c["merge"] for c in commits),
              allRefCount=int(git("rev-list", "--all", "--count")),
              commits=commits, milestones=milestones, monthly=dict(sorted(monthly.items())), snapshots=snapshots)
(HERE / "history.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({k: result[k] for k in ["count", "nonMergeCount", "allRefCount", "monthly", "snapshots"]}, ensure_ascii=False))
