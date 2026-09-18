package captcha_test

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"image/png"
	"log/slog"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/common/ut"

	"github.com/ydonghao/yarch/stacks/golang/captcha"
	"github.com/ydonghao/yarch/stacks/golang/errcode"
	"github.com/ydonghao/yarch/stacks/golang/middleware"
	"github.com/ydonghao/yarch/stacks/golang/redix"
)

// atomicMemStore 并发安全内存实现（Redis 行为级在 testcontainers 子 module）。
// Consume 加锁取出即删——为 V6 提供原子语义底座。
type atomicMemStore struct {
	mu sync.Mutex
	m  map[string]string
	tt map[string]time.Duration
}

func newMemStore() *atomicMemStore {
	return &atomicMemStore{m: map[string]string{}, tt: map[string]time.Duration{}}
}

func (s *atomicMemStore) Issue(ctx context.Context, id, code string, ttl time.Duration) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.m[id] = code
	s.tt[id] = ttl
	return nil
}

func (s *atomicMemStore) Consume(ctx context.Context, id string) (string, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	c, ok := s.m[id]
	delete(s.m, id)
	return c, ok, nil
}

func (s *atomicMemStore) keys() []string {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]string, 0, len(s.m))
	for k := range s.m {
		out = append(out, k)
	}
	return out
}

// fakeSmsSender V8：捕获发送（目标, 内容）。
type fakeSmsSender struct {
	mu   sync.Mutex
	sent [][2]string
}

func (f *fakeSmsSender) Send(destination, content string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.sent = append(f.sent, [2]string{destination, content})
	return nil
}

func (f *fakeSmsSender) last() (string, string) {
	f.mu.Lock()
	defer f.mu.Unlock()
	pair := f.sent[len(f.sent)-1]
	return pair[0], pair[1]
}

// stubCaller V9：turnstile siteverify 打桩。
type stubCaller struct{ ok bool }

func (s stubCaller) Call(verifyURL, secret, response, remoteIP string) bool { return s.ok }

// newSvc 构造测试 Service（存储 key 形状断言用 redix.NewKeys 真实服务名纪律）。
func newSvc(t *testing.T, store captcha.Store, opts captcha.Options) *captcha.Service {
	t.Helper()
	keys, err := redix.NewKeys("mysvc")
	if err != nil {
		t.Fatalf("NewKeys: %v", err)
	}
	svc, err := captcha.NewService(store, keys, opts)
	if err != nil {
		t.Fatalf("NewService: %v", err)
	}
	return svc
}

// V1/V2/V3/V4/V5：image 档生命周期——一次性、失败同消费、trim + 大小写不敏感。
func TestImageLifecycleVectors(t *testing.T) {
	store := newMemStore()
	svc := newSvc(t, store, captcha.Options{})
	ctx := context.Background()

	issued, err := svc.Issue(ctx, "", "")
	if err != nil {
		t.Fatalf("issue: %v", err)
	}
	if issued.Provider != "image" || issued.Key == "" {
		t.Fatalf("issued = %+v", issued)
	}
	answer := store.m["mysvc:captcha:image:"+issued.Key]
	if answer == "" || len(answer) != captcha.DefaultLen {
		t.Fatalf("托管答案异常: %q", answer)
	}
	if !svc.Verify(ctx, "", issued.Key, " "+strings.ToLower(answer)+" ") {
		t.Fatal("V5：trim + 大小写不敏感应通过")
	}
	if svc.Verify(ctx, "", issued.Key, answer) {
		t.Fatal("V2：一次性——二次校验必败")
	}

	issued2, _ := svc.Issue(ctx, "", "")
	if svc.Verify(ctx, "", issued2.Key, "XXXX") {
		t.Fatal("V3：错答案必须失败")
	}
	answer2 := store.m["mysvc:captcha:image:"+issued2.Key]
	if svc.Verify(ctx, "", issued2.Key, answer2) {
		t.Fatal("V3：错答案后 key 已消费（防重放枚举）")
	}

	if svc.Verify(ctx, "", "00000000000000000000000000000000", "ABCD") {
		t.Fatal("V4：不存在 key 必败")
	}
}

// V7：TTL 默认 120s（【推荐】档），Options.TTL 可配。
func TestTtlVector(t *testing.T) {
	store := newMemStore()
	svc := newSvc(t, store, captcha.Options{})
	issued, _ := svc.Issue(context.Background(), "", "")
	if got := store.tt["mysvc:captcha:image:"+issued.Key]; got != 2*time.Minute {
		t.Fatalf("V7：默认 TTL = %v, want 120s", got)
	}

	store2 := newMemStore()
	svc2 := newSvc(t, store2, captcha.Options{TTL: 300 * time.Second})
	issued2, _ := svc2.Issue(context.Background(), "", "")
	if got := store2.tt["mysvc:captcha:image:"+issued2.Key]; got != 300*time.Second {
		t.Fatalf("V7：自定义 TTL = %v, want 300s", got)
	}
}

// V6：并发校验恰一个成功（原子消费）。
func TestConcurrentVerifyVector(t *testing.T) {
	store := newMemStore()
	svc := newSvc(t, store, captcha.Options{})
	ctx := context.Background()
	issued, _ := svc.Issue(ctx, "", "")
	answer := store.m["mysvc:captcha:image:"+issued.Key]

	const threads = 8
	var wg sync.WaitGroup
	results := make([]bool, threads)
	start := make(chan struct{})
	for i := 0; i < threads; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			<-start
			results[i] = svc.Verify(ctx, "", issued.Key, answer)
		}(i)
	}
	close(start)
	wg.Wait()
	wins := 0
	for _, r := range results {
		if r {
			wins++
		}
	}
	if wins != 1 {
		t.Fatalf("V6：并发校验应恰一过，实际 %d", wins)
	}
}

// V8/V10：sms-otp 档（scene 路由映射）——6 位数字、宿主通道发送、目标脱敏、精确比对。
func TestSmsOtpVector(t *testing.T) {
	store := newMemStore()
	sender := &fakeSmsSender{}
	svc := newSvc(t, store, captcha.Options{Scenes: map[string]string{"login": "sms-otp"}, SmsSender: sender})
	ctx := context.Background()

	issued, err := svc.Issue(ctx, "login", "13800138000")
	if err != nil {
		t.Fatalf("issue: %v", err)
	}
	if issued.Provider != "sms-otp" {
		t.Fatalf("V10：scene 路由应命中 sms-otp，实际 %s", issued.Provider)
	}
	dest, otp := sender.last()
	if dest != "13800138000" {
		t.Fatalf("V8：OTP 应经宿主通道发送，目标 %s", dest)
	}
	if len(otp) != 6 {
		t.Fatalf("V8：6 位数字 OTP，实际 %q", otp)
	}
	for _, r := range otp {
		if r < '0' || r > '9' {
			t.Fatalf("V8：非数字 OTP %q", otp)
		}
	}
	if got := issued.Payload["destination"]; got != "138****8000" {
		t.Fatalf("V8：目标脱敏回显 = %q", got)
	}
	if !svc.Verify(ctx, "login", issued.Key, otp) {
		t.Fatal("V8：正确 OTP 应通过")
	}
	issued2, _ := svc.Issue(ctx, "login", "13800138000")
	_, otp2 := sender.last()
	wrong := "000000"
	if otp2 == wrong {
		wrong = "111111"
	}
	if svc.Verify(ctx, "login", issued2.Key, wrong) {
		t.Fatal("V8：错码必须失败（精确比对）")
	}
	if svc.Verify(ctx, "login", issued2.Key, otp2) {
		t.Fatal("V3：错试后已消费")
	}

	// 二-3：缺发送目标 = 1001 上抛
	if _, err := svc.Issue(ctx, "login", ""); err == nil {
		t.Fatal("sms-otp 缺目标应报错")
	}
}

// V9：REMOTE 档 stub 两分支 + 空 token + 无服务端答案 + 零存储。
func TestTurnstileVector(t *testing.T) {
	store := newMemStore()
	p := captcha.NewTurnstileProviderWithCaller(
		captcha.TurnstileConfig{SiteKey: "site-1", SecretKey: "sec-1"}, stubCaller{ok: true})

	ch, err := p.Issue(captcha.IssueRequest{})
	if err != nil {
		t.Fatalf("issue: %v", err)
	}
	if ch.SecretAnswer != "" {
		t.Fatal("V9：REMOTE 档无服务端答案（二-1）")
	}
	if ch.Payload["siteKey"] != "site-1" {
		t.Fatalf("V9：siteKey 透出，实际 %v", ch.Payload)
	}
	if len(store.keys()) != 0 {
		t.Fatal("V9：REMOTE 档全程零存储（三-5）")
	}
	if !p.VerifyRemote("tok", "10.0.0.1") {
		t.Fatal("V9：stub 通过分支")
	}
	fail := captcha.NewTurnstileProviderWithCaller(
		captcha.TurnstileConfig{SiteKey: "s", SecretKey: "x"}, stubCaller{ok: false})
	if fail.VerifyRemote("tok", "") {
		t.Fatal("V9：stub 失败分支")
	}
	if p.VerifyRemote("  ", "") || p.VerifyRemote("", "") {
		t.Fatal("V9：空 token 必败")
	}
}

// V10：场景路由——未配置场景走默认；默认档可配；未注册引用构造即败（fail fast）。
func TestSceneRoutingVector(t *testing.T) {
	store := newMemStore()
	svc := newSvc(t, store, captcha.Options{Scenes: map[string]string{"login": "sms-otp"}, SmsSender: &fakeSmsSender{}})
	if got := svc.Route("register").ID(); got != "image" {
		t.Fatalf("V10：未配置场景应走默认 image，实际 %s", got)
	}
	if got := svc.Route("").ID(); got != "image" {
		t.Fatalf("V10：空场景应走默认，实际 %s", got)
	}

	svc2 := newSvc(t, newMemStore(), captcha.Options{DefaultProvider: "sms-otp", SmsSender: &fakeSmsSender{}})
	if got := svc2.Route("anything").ID(); got != "sms-otp" {
		t.Fatalf("V10：默认档覆盖应生效，实际 %s", got)
	}

	keys, _ := redix.NewKeys("mysvc")
	if _, err := captcha.NewService(newMemStore(), keys, captcha.Options{Scenes: map[string]string{"login": "nope"}}); err == nil {
		t.Fatal("场景引用未注册 Provider 应构造失败")
	}
	if _, err := captcha.NewService(newMemStore(), keys, captcha.Options{DefaultProvider: "nope"}); err == nil {
		t.Fatal("默认档引用未注册 Provider 应构造失败")
	}
}

// 端点（六-1）：非 image 档场景 1001；默认档字段恒在 + 裸 base64 PNG。
func TestHandlerEndpoint(t *testing.T) {
	slog.SetLogLoggerLevel(slog.LevelError)
	svc := newSvc(t, newMemStore(), captcha.Options{Scenes: map[string]string{"login": "sms-otp"}, SmsSender: &fakeSmsSender{}})
	h := server.Default(server.WithHostPorts(":0"))
	handler := captcha.NewHandler(svc)
	h.GET("/api/v1/captcha", middleware.RateLimit(1000, 1000), handler.Issue)

	// 非 image 档场景经图形端点 = 1001（六-1）
	w := ut.PerformRequest(h.Engine, "GET", "/api/v1/captcha?scene=login", nil)
	var env struct {
		Code int `json:"code"`
		Data struct {
			Provider    string `json:"provider"`
			Key         string `json:"key"`
			ImageBase64 string `json:"imageBase64"`
		} `json:"data"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &env); err != nil {
		t.Fatalf("not envelope: %v", err)
	}
	if w.Code != 400 || env.Code != int(errcode.InvalidArgument) {
		t.Fatalf("非 image 场景应 400+1001：%d %+v", w.Code, env)
	}

	// 默认档：字段恒在（provider 增量 + key/imageBase64 存量形态）
	w2 := ut.PerformRequest(h.Engine, "GET", "/api/v1/captcha", nil)
	if err := json.Unmarshal(w2.Body.Bytes(), &env); err != nil {
		t.Fatalf("not envelope: %v", err)
	}
	if w2.Code != 200 || env.Code != 0 || env.Data.Provider != "image" || env.Data.Key == "" {
		t.Fatalf("resp = %d %+v", w2.Code, env)
	}
	if strings.HasPrefix(env.Data.ImageBase64, "data:") {
		t.Fatal("二-2：裸 base64 禁 data: 前缀")
	}
	raw, err := base64.StdEncoding.DecodeString(env.Data.ImageBase64)
	if err != nil {
		t.Fatalf("imageBase64 应为裸 base64: %v", err)
	}
	if !bytes.HasPrefix(raw, []byte{0x89, 'P', 'N', 'G'}) {
		t.Fatal("not PNG magic")
	}
	if _, err := png.Decode(bytes.NewReader(raw)); err != nil {
		t.Fatalf("png decode: %v", err)
	}
}

// V11：生成端点限流（五-1：注册时挂限流中间件，超限 429+1006）。
func TestHandlerRateLimited(t *testing.T) {
	slog.SetLogLoggerLevel(slog.LevelError)
	svc := newSvc(t, newMemStore(), captcha.Options{})
	h := server.Default(server.WithHostPorts(":0"))
	h.GET("/api/v1/captcha", middleware.RateLimit(0.0001, 2), captcha.NewHandler(svc).Issue)
	for i := 0; i < 2; i++ {
		if w := ut.PerformRequest(h.Engine, "GET", "/api/v1/captcha", nil); w.Code != 200 {
			t.Fatalf("名额内应 200，第 %d 次 = %d", i+1, w.Code)
		}
	}
	w := ut.PerformRequest(h.Engine, "GET", "/api/v1/captcha", nil)
	var env struct {
		Code int `json:"code"`
	}
	_ = json.Unmarshal(w.Body.Bytes(), &env)
	if w.Code != 429 || env.Code != int(errcode.RateLimited) {
		t.Fatalf("V11：超限应 429+1006，实际 %d %+v", w.Code, env)
	}
}

// 字符集与长度（二-2【强制】：4 位、去混淆 32 字符集）。
func TestNewCode(t *testing.T) {
	c := captcha.NewCode(captcha.DefaultLen)
	if len(c) != captcha.DefaultLen {
		t.Fatalf("len = %d", len(c))
	}
	for i := 0; i < len(c); i++ {
		if !strings.ContainsRune("23456789ABCDEFGHJKLMNPQRSTUVWXYZ", rune(c[i])) {
			t.Fatalf("字符 %q 不在契约字符集: %s", rune(c[i]), c)
		}
	}
	a, b := captcha.NewCode(8), captcha.NewCode(8)
	if a == b {
		t.Fatal("两次生成不应相同（概率级）")
	}
}

// 2005 码位锚定（五-2 三态合一：业务侧 false → xerror.New(errcode.CaptchaInvalid)）。
func TestCaptchaInvalidCodePinned(t *testing.T) {
	if errcode.CaptchaInvalid != 2005 {
		t.Fatalf("2005 码位漂移: %d", int(errcode.CaptchaInvalid))
	}
}
