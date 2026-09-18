package captcha

// Provider SPI（contract/api/captcha.md 一-2/二）：渠道差异是框架内多态，
// 实现只管「生成挑战」与「答案比对/远程校验」；生命周期（key/存储/TTL/一次性原子消费）、
// 场景路由、错误语义归框架核心 Service。
//
// 保留字 id（一-3）：image / sms-otp / turnstile，业务自定义 Provider 禁占用。
type Provider interface {
	// ID Provider 标识（kebab-case，保留字见接口注释）
	ID() string

	// Mode 校验模式（二-1）：LOCAL=答案由框架核心托管、一次性原子消费；REMOTE=远程校验、无服务端答案存储
	Mode() VerifyMode

	// Issue 生成分发（LOCAL：产出机密答案 + 公开载体；REMOTE：答案恒空、载体为接入配置透出）
	Issue(req IssueRequest) (IssuedChallenge, error)

	// Matches LOCAL 档答案比对规则。默认 = 首尾空白 trim + 大小写不敏感（二-2 image 档）；精确档（二-3 sms-otp）覆写
	Matches(stored, attempted string) bool
}

// RemoteVerifier REMOTE 档远程校验（二-1）——REMOTE 型 Provider 额外实现
// （Go 无默认方法，LOCAL 型无需空实现）。
type RemoteVerifier interface {
	VerifyRemote(token, remoteIP string) bool
}

// VerifyMode 校验模式二分（二-1）。
type VerifyMode int

const (
	// ModeLocal 本地校验型：答案由框架核心托管进存储，一次性原子消费（三）
	ModeLocal VerifyMode = iota
	// ModeRemote 远程校验型：无服务端答案存储，一次性语义由上游保证
	ModeRemote
)

// IssueRequest 分发请求：Scene 为路由场景（四-1，可空）；Destination 为发送目标（sms-otp 档必填）。
type IssueRequest struct {
	Scene       string
	Destination string
}

// IssuedChallenge 分发产物：SecretAnswer 为机密答案（LOCAL 档进存储由框架核心托管，REMOTE 恒空）；
// Payload 为公开载体（image 的 imageBase64 / turnstile 的 siteKey / sms-otp 的脱敏目标）。
type IssuedChallenge struct {
	SecretAnswer string
	Payload      map[string]string
}

// DefaultMatches 默认比对口径（二-2）：attempted 去首尾空白 + 大小写不敏感；stored 为空即败。
func DefaultMatches(stored, attempted string) bool {
	if stored == "" {
		return false
	}
	return equalFold(stored, trimSpace(attempted))
}
