package captcha

import "fmt"

// ImageProvider image 档（contract/api/captcha.md 二-2，LOCAL 默认档）：
// 4 位去混淆字符集【强制】；比对默认口径（trim + 大小写不敏感）；渲染样式自由（CP9）。
type ImageProvider struct{}

func (p *ImageProvider) ID() string       { return "image" }
func (p *ImageProvider) Mode() VerifyMode { return ModeLocal }

func (p *ImageProvider) Issue(req IssueRequest) (IssuedChallenge, error) {
	answer := NewCode(DefaultLen)
	return IssuedChallenge{
		SecretAnswer: answer,
		Payload:      map[string]string{"imageBase64": ImageBase64(RenderPNG(answer))},
	}, nil
}

// Matches 默认口径：trim + 大小写不敏感（二-2）。
func (p *ImageProvider) Matches(stored, attempted string) bool {
	return DefaultMatches(stored, attempted)
}

// SmsSender OTP 分发通道 SPI（二-3）：发送通道由宿主注入——yarch 不背通知渠道抽象
// （通知领域契约排队中 EP7，届时对齐）。宿主提供实现后经 Options.SmsSender 注册 sms-otp 档。
type SmsSender interface {
	// Send 发送 OTP 到目标（手机号）。destination 明文仅在此处出现；回显侧已由框架脱敏。
	Send(destination, content string) error
}

// SmsOtpProvider sms-otp 档（contract/api/captcha.md 二-3，LOCAL）：
// 6 位数字 OTP（安全随机源）；精确匹配（禁宽松比对）；目标回显脱敏。
type SmsOtpProvider struct{ sender SmsSender }

func (p *SmsOtpProvider) ID() string       { return "sms-otp" }
func (p *SmsOtpProvider) Mode() VerifyMode { return ModeLocal }

func (p *SmsOtpProvider) Issue(req IssueRequest) (IssuedChallenge, error) {
	if req.Destination == "" {
		return IssuedChallenge{}, fmt.Errorf("sms-otp 档须提供发送目标（1001）")
	}
	otp := fmt.Sprintf("%06d", randInt(1_000_000))
	if err := p.sender.Send(req.Destination, otp); err != nil {
		return IssuedChallenge{}, err
	}
	return IssuedChallenge{
		SecretAnswer: otp,
		Payload:      map[string]string{"destination": MaskDestination(req.Destination)},
	}, nil
}

// Matches 精确匹配（二-3 禁宽松比对）：仅去首尾空白，无大小写折中。
func (p *SmsOtpProvider) Matches(stored, attempted string) bool {
	return stored != "" && stored == trimSpace(attempted)
}

// MaskDestination 目标脱敏回显（二-3）：≥7 位留前 3 后 4，其余全遮。
func MaskDestination(destination string) string {
	if len(destination) < 7 {
		return "****"
	}
	return destination[:3] + "****" + destination[len(destination)-4:]
}
