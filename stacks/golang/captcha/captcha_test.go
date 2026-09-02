package captcha_test

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"image/png"
	"log/slog"
	"testing"
	"time"

	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/common/ut"

	"github.com/yuandonghao/yarch-go/captcha"
)

// memStore 内存实现（Redis 行为级集成在 testcontainers 子 module）。
type memStore struct{ m map[string]string }

func (s *memStore) Issue(ctx context.Context, id, code string, ttl time.Duration) error {
	s.m[id] = code
	return nil
}
func (s *memStore) Consume(ctx context.Context, id string) (string, bool, error) {
	c, ok := s.m[id]
	delete(s.m, id)
	return c, ok, nil
}

// 端点断言：200 信封 + id/image 字段 + image 是合法 PNG 的 dataURL。
func TestHandlerIssue(t *testing.T) {
	store := &memStore{m: map[string]string{}}
	h := server.Default(server.WithHostPorts(":0"))
	slog.SetLogLoggerLevel(slog.LevelError)
	ch := captcha.NewHandler(store, func(id string) string { return "mysvc:captcha:" + id }, 0)
	h.GET("/api/v1/captcha", ch.Issue)

	w := ut.PerformRequest(h.Engine, "GET", "/api/v1/captcha", nil)
	var env struct {
		Code int `json:"code"`
		Data struct {
			ID    string `json:"id"`
			Image string `json:"image"`
		} `json:"data"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &env); err != nil {
		t.Fatalf("not envelope: %v", err)
	}
	if w.Code != 200 || env.Code != 0 || env.Data.ID == "" {
		t.Fatalf("resp = %d %+v", w.Code, env)
	}
	const prefix = "data:image/png;base64,"
	if len(env.Data.Image) <= len(prefix) || env.Data.Image[:len(prefix)] != prefix {
		t.Fatalf("image not dataURL: %.40s", env.Data.Image)
	}
	// 解 base64 → PNG magic
	img := decodeB64(t, env.Data.Image[len(prefix):])
	if !bytes.HasPrefix(img, []byte{0x89, 'P', 'N', 'G'}) {
		t.Fatal("not PNG magic")
	}
	if _, err := png.Decode(bytes.NewReader(img)); err != nil {
		t.Fatalf("png decode: %v", err)
	}
}

// 一次性 + 大小写不敏感 + 失败同样消费。
func TestVerifySemantics(t *testing.T) {
	store := &memStore{m: map[string]string{}}
	h := captcha.NewHandler(store, func(id string) string { return id }, 0)
	ctx := context.Background()

	store.Issue(ctx, "k1", "AB3CD", time.Minute)
	if !h.Verify(ctx, "k1", "ab3cd") {
		t.Fatal("大小写不敏感应通过")
	}
	if h.Verify(ctx, "k1", "AB3CD") {
		t.Fatal("一次性：二次校验必须失败")
	}

	store.Issue(ctx, "k2", "XY9ZK", time.Minute)
	if h.Verify(ctx, "k2", "WRONG") {
		t.Fatal("错码必须失败")
	}
	if h.Verify(ctx, "k2", "XY9ZK") {
		t.Fatal("失败后 token 已消费，防重放爆破")
	}
}

func TestNewCode(t *testing.T) {
	c := captcha.NewCode(captcha.DefaultLen)
	if len(c) != captcha.DefaultLen {
		t.Fatalf("len = %d", len(c))
	}
	for i := 0; i < len(c); i++ {
		for _, bad := range []byte{'0', 'O', '1', 'I'} {
			if c[i] == bad {
				t.Fatalf("混淆字符 %q 出现: %s", bad, c)
			}
		}
	}
	if captcha.NewCode(5) == captcha.NewCode(5) {
		t.Fatal("两次生成不应相同（概率级）")
	}
}

func decodeB64(t *testing.T, s string) []byte {
	t.Helper()
	b, err := base64.StdEncoding.DecodeString(s)
	if err != nil {
		t.Fatalf("base64: %v", err)
	}
	return b
}
