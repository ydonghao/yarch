// Package testx 是业务工程的契约断言与容器测试基座（对偶 java yarch-test-starter）：
// 信封形状断言、ndjson 行协议断言、PG/Redis 容器基座（testcontainers-go）。
// 依赖仅在测试态引入（业务工程 go.mod 的测试依赖）。
package testx

import (
	"encoding/json"
	"strings"
	"testing"
)

// Envelope 断言信封四字段存在（rest-response.md）。返回解析后的 map 供继续断言。
func Envelope(t *testing.T, body string) map[string]any {
	t.Helper()
	var m map[string]any
	if err := json.Unmarshal([]byte(body), &m); err != nil {
		t.Fatalf("testx: 响应不是合法信封 JSON: %v (%s)", err, body)
	}
	for _, k := range []string{"code", "message", "data", "traceId"} {
		if _, ok := m[k]; !ok {
			t.Fatalf("testx: 信封缺少字段 %q: %s", k, body)
		}
	}
	if code, _ := m["code"].(float64); code != 0 {
		if m["data"] != nil {
			t.Fatalf("testx: code=%v 时 data 必须为 null: %s", m["code"], body)
		}
	}
	return m
}

// EnvelopeTrace 断言信封 traceId 与响应头 X-Trace-Id 恒等（logging-trace.md 出口规则）。
func EnvelopeTrace(t *testing.T, body, headerTraceID string) {
	t.Helper()
	m := Envelope(t, body)
	if m["traceId"] != headerTraceID {
		t.Fatalf("testx: body.traceId(%v) != X-Trace-Id(%q)", m["traceId"], headerTraceID)
	}
}

// NDJSONLine 断言一行日志符合 ndjson 行协议必填字段（logging-trace.md）。
func NDJSONLine(t *testing.T, line string) map[string]any {
	t.Helper()
	line = strings.TrimSuffix(line, "\n")
	if strings.Contains(line, "\n") {
		t.Fatalf("testx: 日志必须一行一条: %q", line)
	}
	var m map[string]any
	if err := json.Unmarshal([]byte(line), &m); err != nil {
		t.Fatalf("testx: 日志行不是 JSON: %v (%s)", err, line)
	}
	for _, k := range []string{"ts", "level", "service", "env", "logger", "msg"} {
		if _, ok := m[k]; !ok {
			t.Errorf("testx: 日志行缺少必填字段 %q: %s", k, line)
		}
	}
	return m
}

// PageData 断言分页负载形状（list 非 null、total/page/pageSize 齐备）。
func PageData(t *testing.T, body string) (list []any, total float64) {
	t.Helper()
	m := Envelope(t, body)
	data, _ := m["data"].(map[string]any)
	if data == nil {
		t.Fatalf("testx: 分页响应 data 不得为 null: %s", body)
	}
	l, ok := data["list"].([]any)
	if !ok {
		t.Fatalf("testx: 分页响应 list 缺失或为 null: %s", body)
	}
	total, ok = data["total"].(float64)
	if !ok {
		t.Fatalf("testx: 分页响应 total 缺失: %s", body)
	}
	if _, ok := data["page"]; !ok {
		t.Fatalf("testx: 分页响应 page 缺失: %s", body)
	}
	if _, ok := data["pageSize"]; !ok {
		t.Fatalf("testx: 分页响应 pageSize 缺失: %s", body)
	}
	return l, total
}
