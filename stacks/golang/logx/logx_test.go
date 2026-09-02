package logx_test

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"strings"
	"testing"
	"time"

	"github.com/yuandonghao/yarch/stacks/golang/logx"
)

// 契约断言：ndjson 行协议字段级（logging-trace.md v1.0）。
func TestNDJSONLine(t *testing.T) {
	var buf bytes.Buffer
	l := logx.New("ysaas", "local", slog.LevelInfo, &buf)
	ctx := logx.WithTraceID(context.Background(), "0af7651916cd43dd8448eb211c80319c")

	l.InfoContext(ctx, "request completed", slog.String("method", "GET"), slog.String("path", "/api/v1/users"), slog.Int("status", 200), slog.Int("costMs", 12))

	line := strings.TrimSuffix(buf.String(), "\n")
	if strings.Count(line, "\n") != 0 {
		t.Fatalf("必须一行一条，got %q", line)
	}
	var m map[string]any
	if err := json.Unmarshal([]byte(line), &m); err != nil {
		t.Fatalf("not json: %v", err)
	}
	for _, k := range []string{"ts", "level", "service", "env", "traceId", "logger", "msg"} {
		if _, ok := m[k]; !ok {
			t.Errorf("缺少必填字段 %q: %s", k, line)
		}
	}
	if m["level"] != "INFO" || m["service"] != "ysaas" || m["env"] != "local" {
		t.Errorf("level/service/env = %v/%v/%v", m["level"], m["service"], m["env"])
	}
	if m["traceId"] != "0af7651916cd43dd8448eb211c80319c" {
		t.Errorf("traceId = %v", m["traceId"])
	}
	if m["method"] != "GET" || m["costMs"] != float64(12) {
		t.Errorf("自由键值 camelCase 透传出错: %s", line)
	}
	// ts：RFC3339 毫秒精度 UTC，恒以 Z 结尾
	ts, _ := m["ts"].(string)
	if !strings.HasSuffix(ts, "Z") || len(ts) != len("2006-01-02T15:04:05.000Z") {
		t.Errorf("ts 格式 = %q", ts)
	}
	if _, err := time.Parse("2006-01-02T15:04:05.000Z", ts); err != nil {
		t.Errorf("ts 不可解析: %v", err)
	}
}

// 契约断言：traceId 条件必填——无上下文（后台任务）时不得输出空字段。
func TestNoTraceIDOutsideRequest(t *testing.T) {
	var buf bytes.Buffer
	l := logx.New("svc", "dev", slog.LevelInfo, &buf)
	l.Info("boot")
	if strings.Contains(buf.String(), "traceId") {
		t.Fatalf("后台日志不应携带空 traceId: %s", buf.String())
	}
}

func TestLevelNames(t *testing.T) {
	cases := map[slog.Level]string{
		slog.Level(-8): "TRACE", slog.LevelDebug: "DEBUG", slog.LevelInfo: "INFO",
		slog.LevelWarn: "WARN", slog.LevelError: "ERROR",
	}
	var buf bytes.Buffer
	for lv, name := range cases {
		buf.Reset()
		l := logx.New("s", "e", slog.Level(-8), &buf)
		l.Log(context.Background(), lv, "x")
		var m map[string]any
		_ = json.Unmarshal(buf.Bytes(), &m)
		if m["level"] != name {
			t.Errorf("level %v = %v, want %s", lv, m["level"], name)
		}
	}
}

func TestSubLogger(t *testing.T) {
	var buf bytes.Buffer
	l := logx.Sub(logx.New("s", "e", slog.LevelInfo, &buf), "infra/database")
	l.Info("connected")
	var m map[string]any
	_ = json.Unmarshal(buf.Bytes(), &m)
	if m["logger"] != "infra/database" {
		t.Fatalf("logger = %v", m["logger"])
	}
}

func TestParseLevel(t *testing.T) {
	if logx.ParseLevel("trace") != slog.Level(-8) || logx.ParseLevel("ERROR") != slog.LevelError || logx.ParseLevel("") != slog.LevelInfo {
		t.Fatal("ParseLevel 映射错误")
	}
}
