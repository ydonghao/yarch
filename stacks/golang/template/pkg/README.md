# pkg · 工程内无业务语义工具

放本工程复用、且不依赖 api/domain/infra 的工具（对齐 coze-studio pkg/ 与 yagent pkg/）。
通用到跨项目级别的件应上沉 yarch-go 平台 module（response/errcode/xerror/logx/web/…），不在业务工程 pkg/ 重复实现。
