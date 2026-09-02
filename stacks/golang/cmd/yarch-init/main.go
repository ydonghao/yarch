// yarch-init 业务工程生成器（java maven-archetype:generate 的 golang 对偶）。
//
// 安装/使用（本仓未发版阶段）：
//
//	cd yarch/stacks/golang
//	go run ./cmd/yarch-init -module github.com/you/your-svc -out ~/code/your-svc
//
// 发版后等价命令（Go 官方工具）：
//
//	go run golang.org/x/tools/cmd/gonew@latest github.com/yuandonghao/yarch-go/template github.com/you/your-svc ~/code/your-svc
//
// 生成内容：coze-studio 同构 DDD 七包（main.go / api / application / domain /
// crossdomain / infra / pkg / types / conf）+ 迁移 + compose + Dockerfile + Makefile。
package main

import (
	"flag"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

const (
	oldModule  = "github.com/yuandonghao/yarch-go/template"
	oldService = "my-svc"
)

var servicePattern = regexp.MustCompile(`^[a-z][a-z0-9-]{1,31}$`)

// 禁裸通用词（registry.md 一-1 口径，摘录高频项；完整表以 contract/registry.md 为准）。
var genericWords = map[string]bool{
	"api": true, "app": true, "service": true, "server": true, "backend": true,
	"web": true, "admin": true, "main": true, "common": true, "system": true, "demo": true,
}

func main() {
	var (
		module = flag.String("module", "", "新工程 module path（必填，如 github.com/you/your-svc）")
		out    = flag.String("out", "", "输出目录（必填，须不存在或为空）")
		src    = flag.String("src", "./template", "模板源目录（默认本仓 stacks/golang/template）")
	)
	flag.Parse()

	if *module == "" || *out == "" {
		flag.Usage()
		os.Exit(2)
	}
	service := filepath.Base(*module)
	if !servicePattern.MatchString(service) {
		fatal("服务名 %q 须过 registry.md 一-1 校验：^[a-z][a-z0-9-]{1,31}$", service)
	}
	if genericWords[service] {
		fatal("服务名 %q 是裸通用词，禁止使用（见 contract/registry.md 一-1）", service)
	}
	if entries, _ := os.ReadDir(*out); len(entries) > 0 {
		fatal("输出目录 %s 非空", *out)
	}
	if _, err := os.Stat(*src); err != nil {
		fatal("模板源目录不可达：%v（在 yarch 仓 stacks/golang/ 下运行，或用 -src 指定）", err)
	}

	absSrc, err := filepath.Abs(*src)
	if err != nil {
		fatal("模板源路径解析失败：%v", err)
	}
	n, err := generate(*src, *out, *module, service)
	if err != nil {
		fatal("生成失败：%v", err)
	}
	// 生成的工程 go.mod：发版前 replace 指向本仓平台源码；
	// yarch-go 正式发 tag 后，删除 replace 并把版本改为正式号（README 有说明）。
	gomod := "module " + *module + "\n\ngo 1.24\n\nrequire github.com/yuandonghao/yarch-go v0.0.0\n\nreplace github.com/yuandonghao/yarch-go => " + filepath.Dir(absSrc) + "\n"
	if err := os.WriteFile(filepath.Join(*out, "go.mod"), []byte(gomod), 0o644); err != nil {
		fatal("写 go.mod 失败：%v", err)
	}

	fmt.Printf(`✅ 已生成 %s（%d 个文件）

下一步：
  1. cd %s && docker compose up -d && go mod tidy && go run .
  2. main.go 中 service 常量已置为 %q——去 yarch 仓 contract/registry.md 登记该服务名
  3. types/errno 业务码段（3xxx+）在你的仓库 docs 登记后方可使用
  4. yarch-go 正式发版后：删除 go.mod 的 replace 行，版本改正式号（发版式升级 = go get 升版）
`, *out, n, *out, service)
}

func generate(src, dst, newModule, service string) (int, error) {
	n := 0
	err := filepath.WalkDir(src, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		rel, _ := filepath.Rel(src, path)
		target := filepath.Join(dst, rel)
		if d.IsDir() {
			return os.MkdirAll(target, 0o755)
		}
		b, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		if strings.HasSuffix(d.Name(), ".go") || d.Name() == "go.mod" ||
			d.Name() == "Makefile" || d.Name() == "README.md" || d.Name() == ".env.example" {
			s := string(b)
			s = strings.ReplaceAll(s, oldModule, newModule)
			s = strings.ReplaceAll(s, oldService, service)
			b = []byte(s)
		}
		if err := os.WriteFile(target, b, 0o644); err != nil {
			return err
		}
		n++
		return nil
	})
	return n, err
}

func fatal(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "yarch-init: "+format+"\n", args...)
	os.Exit(1)
}
