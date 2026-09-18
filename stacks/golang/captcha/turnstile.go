package captcha

import (
	"encoding/json"
	"net/http"
	"net/url"
	"strings"
)

// TurnstileConfig turnstile 档接入配置（二-4）：SecretKey 非空时该档才装配。
type TurnstileConfig struct {
	SiteKey   string
	SecretKey string
	// VerifyURL 上游 siteverify 地址（默认 Cloudflare 官方端点）
	VerifyURL string
}

// SiteVerifyCaller siteverify 调用器（测试打桩位）。
type SiteVerifyCaller interface {
	Call(verifyURL, secret, response, remoteIP string) bool
}

// TurnstileProvider turnstile 档（contract/api/captcha.md 二-4，REMOTE）：
// siteKey 透出 + siteverify 外委校验；token 不落存储（三-5：REMOTE 型不产生存储 key）。
type TurnstileProvider struct {
	cfg    TurnstileConfig
	caller SiteVerifyCaller
}

func NewTurnstileProvider(cfg TurnstileConfig) *TurnstileProvider {
	return &TurnstileProvider{cfg: cfg, caller: siteVerifyCaller{}}
}

// NewTurnstileProviderWithCaller 构造（caller 注入，测试打桩位）。
func NewTurnstileProviderWithCaller(cfg TurnstileConfig, caller SiteVerifyCaller) *TurnstileProvider {
	return &TurnstileProvider{cfg: cfg, caller: caller}
}

func (p *TurnstileProvider) ID() string       { return "turnstile" }
func (p *TurnstileProvider) Mode() VerifyMode { return ModeRemote }

func (p *TurnstileProvider) Issue(req IssueRequest) (IssuedChallenge, error) {
	// REMOTE 档：无服务端答案（二-1），分发即接入配置透出（二-4：前端嵌 widget 自取 token）
	return IssuedChallenge{SecretAnswer: "", Payload: map[string]string{"siteKey": p.cfg.SiteKey}}, nil
}

func (p *TurnstileProvider) Matches(stored, attempted string) bool { return false } // REMOTE 档不适用

// VerifyRemote 外委校验：空 token 即败；上游不可达 fail closed（一律 false）。
func (p *TurnstileProvider) VerifyRemote(token, remoteIP string) bool {
	if strings.TrimSpace(token) == "" {
		return false
	}
	verifyURL := p.cfg.VerifyURL
	if verifyURL == "" {
		verifyURL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"
	}
	return p.caller.Call(verifyURL, p.cfg.SecretKey, token, remoteIP)
}

// siteVerifyCaller 默认调用器：POST siteverify（form 编码），解析 {"success":bool}。
type siteVerifyCaller struct{}

func (siteVerifyCaller) Call(verifyURL, secret, response, remoteIP string) bool {
	form := url.Values{}
	form.Set("secret", secret)
	form.Set("response", response)
	if remoteIP != "" {
		form.Set("remoteip", remoteIP)
	}
	resp, err := http.PostForm(verifyURL, form)
	if err != nil {
		return false
	}
	defer func() { _ = resp.Body.Close() }()
	var out struct {
		Success bool `json:"success"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return false
	}
	return out.Success
}
