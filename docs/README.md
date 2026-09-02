# docs/ · 工程文档

| 内容 | 文件 | 说明 |
|---|---|---|
| 架构设计 | [architecture.md](architecture.md) | 仓库结构的权威落盘：定位 / 契约层 / 分界线 / 施工节奏 |
| 架构图 | [architecture-diagram.svg](architecture-diagram.svg) / [.png](architecture-diagram.png) | 架构总览图（源文件与 2x 位图） |
| 参考资料 | [references/](references/) | 规约的来源解析与审阅材料 |

## references/ · 解析与参考资料

**定位**：服务于规约评审的解析材料（原文逐条展开、正反例、方言详情、决策清单）。**规范性条文一律以 [../contract/](../contract/README.md) 为准**，本组不承载权威条文。

| 文件 | 内容 |
|---|---|
| [alibaba-java-manual-digest.md](references/alibaba-java-manual-digest.md) | 《Java 开发手册（黄山版 1.7.1）》全书解析；**D1-D6 契约决策清单在此**（API 四件套定稿的前置） |
| [alibaba-mysql-digest.md](references/alibaba-mysql-digest.md) | 阿里 MySQL 章 50 条全量解析 + ORM G1-G10 四栈方言表（条文已定稿为 [../contract/infra/mysql.md](../contract/infra/mysql.md)） |
| [ruoyi-yudao-coli-gap-digest.md](references/ruoyi-yudao-coli-gap-digest.md) | ruoyi/芋道/coli 脚手架对标缺口解析；**G1-G10 对齐决策清单在此**（java 栈精细打磨的前置） |

新解析材料（Nacos、消息队列等研究）按需增补进本组，命名 `{来源}-{主题}-digest.md`。
