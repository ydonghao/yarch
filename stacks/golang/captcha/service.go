package captcha

import (
	"context"
	"time"

	"github.com/ydonghao/yarch/stacks/golang/errcode"
	"github.com/ydonghao/yarch/stacks/golang/redix"
	"github.com/ydonghao/yarch/stacks/golang/xerror"
)

// Options 装配配置（contract/api/captcha.md 三-2/四-2/二-3/二-4）。
// 租户级覆盖是宿主应用层扩展位（四-3），本配置面到应用级为止。
type Options struct {
	// TTL challenge 有效期（三-2【推荐】120s；OTP 档可放宽至 300s）。零值取 DefaultTTL。
	TTL time.Duration
	// DefaultProvider 默认 Provider（四-2，空取 image）；未配置场景一律走此档。
	DefaultProvider string
	// Scenes scene → Provider 覆盖映射（四-2）；scene 为宿主自定 kebab-case，yarch 不拥有场景枚举。
	Scenes map[string]string
	// SmsSender 非 nil 时注册 sms-otp 档（二-3：分发通道宿主注入）。
	SmsSender SmsSender
	// Turnstile 非 nil 时注册 turnstile 档（二-4：接入凭据在场才装配）。
	Turnstile *TurnstileConfig
}

// Issued 编程式分发产物（六-4：sms-otp/turnstile 档首发走编程式，HTTP 端点为触发档）。
type Issued struct {
	Provider string
	Key      string
	Payload  map[string]string
}

// Challenge REST 端点形状（六-1）：provider/key/imageBase64 恒在（image 档）。
type Challenge struct {
	Provider    string `json:"provider"`
	Key         string `json:"key"`
	ImageBase64 string `json:"imageBase64"`
}

// Service 框架核心（contract/api/captcha.md 一-2）：challenge 生命周期（生成/存储/TTL/
// 一次性原子消费三）、场景路由（四）归此；生成与比对归 Provider。
type Service struct {
	store     Store
	keys      *redix.Keys
	providers map[string]Provider
	defaultID string
	scenes    map[string]string
	ttl       time.Duration
}

// NewService 构造并校验装配（fail fast）：image 恒注册；sms-otp/turnstile 按 Options 条件注册；
// DefaultProvider 与 Scenes 引用的 Provider 必须已注册，否则返回错误（配置即崩，不留运行期悬空）。
func NewService(store Store, keys *redix.Keys, opts Options) (*Service, error) {
	providers := map[string]Provider{"image": &ImageProvider{}}
	if opts.SmsSender != nil {
		providers["sms-otp"] = &SmsOtpProvider{sender: opts.SmsSender}
	}
	if opts.Turnstile != nil {
		providers["turnstile"] = NewTurnstileProvider(*opts.Turnstile)
	}
	defaultID := opts.DefaultProvider
	if defaultID == "" {
		defaultID = "image"
	}
	if _, ok := providers[defaultID]; !ok {
		return nil, xerror.Newf(errcode.InvalidArgument, "默认 Provider 未注册：%s", defaultID)
	}
	for scene, id := range opts.Scenes {
		if _, ok := providers[id]; !ok {
			return nil, xerror.Newf(errcode.InvalidArgument, "场景 %s 引用未注册 Provider：%s", scene, id)
		}
	}
	ttl := opts.TTL
	if ttl <= 0 {
		ttl = DefaultTTL
	}
	scenes := make(map[string]string, len(opts.Scenes))
	for scene, id := range opts.Scenes {
		scenes[scene] = id
	}
	return &Service{
		store:     store,
		keys:      keys,
		providers: providers,
		defaultID: defaultID,
		scenes:    scenes,
		ttl:       ttl,
	}, nil
}

// Route 场景路由（四-2）：未配置场景走默认 Provider（不报错）。
func (s *Service) Route(scene string) Provider {
	if id, ok := s.scenes[scene]; ok {
		return s.providers[id]
	}
	return s.providers[s.defaultID]
}

// Issue 分发（四-2 路由）。LOCAL 档生成安全随机 key（三-1）并带 TTL 托管答案；
// REMOTE 档零存储（三-5）。分发失败（如 OTP 通道异常）原样上抛由宿主定级。
func (s *Service) Issue(ctx context.Context, scene, destination string) (Issued, error) {
	p := s.Route(scene)
	ch, err := p.Issue(IssueRequest{Scene: scene, Destination: destination})
	if err != nil {
		return Issued{}, err
	}
	key := ""
	if p.Mode() == ModeLocal {
		key = NewID()
		if err := s.store.Issue(ctx, s.storeKey(p.ID(), key), ch.SecretAnswer, s.ttl); err != nil {
			return Issued{}, err
		}
	}
	return Issued{Provider: p.ID(), Key: key, Payload: ch.Payload}, nil
}

// Verify 校验（六-2：内联受保护业务流，不设独立端点）。
// LOCAL = 原子消费后按 Provider 比对（三-3 对/错/异常均消费；三-4 GETDEL）；
// REMOTE = 外委校验（二-1）。false 时业务报 2005 CAPTCHA_INVALID（五-2 三态合一）。
func (s *Service) Verify(ctx context.Context, scene, key, answer string) bool {
	p := s.Route(scene)
	if p.Mode() == ModeRemote {
		rv, ok := p.(RemoteVerifier)
		if !ok {
			return false
		}
		return rv.VerifyRemote(answer, "")
	}
	stored, ok, err := s.store.Consume(ctx, s.storeKey(p.ID(), key))
	if err != nil || !ok {
		return false
	}
	return p.Matches(stored, answer)
}

// Challenge REST 端点便捷面（六-1）：GET /api/v1/captcha 只服务 image 档，
// 路由到他档 = 1001 参数错（先查档位再分发——sms-otp 等档缺端点参数不在此报）。
func (s *Service) Challenge(ctx context.Context, scene string) (Challenge, *xerror.BizError) {
	if id := s.Route(scene).ID(); id != "image" {
		return Challenge{}, xerror.Newf(errcode.InvalidArgument, "场景 %s 非 image 档，不经图形分发端点", scene)
	}
	issued, err := s.Issue(ctx, scene, "")
	if err != nil {
		return Challenge{}, xerror.FromError(err)
	}
	return Challenge{
		Provider:    issued.Provider,
		Key:         issued.Key,
		ImageBase64: issued.Payload["imageBase64"],
	}, nil
}

// storeKey 存储 key（三-5）：{服务名}:captcha:{provider}:{key}——redix.Keys 强制首段服务名。
func (s *Service) storeKey(providerID, key string) string {
	return s.keys.K("captcha", providerID, key)
}
