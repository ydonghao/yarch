# tools/

模板定位器与将来的 `yarch init` CLI。

## locate-scaffolds.cjs

```bash
node tools/locate-scaffolds.cjs            # 列出全部脚手架
node tools/locate-scaffolds.cjs java        # 打印 stacks/java 绝对路径
node tools/locate-scaffolds.cjs --json      # 机器可读
```

退出码：0 正常；1 传入了未知的栈名（stderr 列出可用值）。
