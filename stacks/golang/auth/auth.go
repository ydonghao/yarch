// Package auth 是 JWT 机制件（对偶 java yarch-auth-starter）：
// 签发/解析（HS256 默认，RS/ES 可扩展）+ 认证中间件（2xxx 契约映射）。
// 账号/权限模型归业务工程——本包只做凭证校验与角色断言机制。
package auth

import (
	"context"
	"errors"
	"time"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/golang-jwt/jwt/v5"

	"github.com/ydonghao/yarch/stacks/golang/errcode"
	"github.com/ydonghao/yarch/stacks/golang/middleware"
	"github.com/ydonghao/yarch/stacks/golang/xerror"
)

// Claims 载荷：sub=用户 ID（不透明 string），roles 附带角色（RequireRoles 用）。
type Claims struct {
	UserID string   `json:"sub"`
	Roles  []string `json:"roles,omitempty"`
	jwt.RegisteredClaims
}

// Signer 签发与解析。
type Signer struct {
	method jwt.SigningMethod
	key    any // HMAC 密钥 / RSA·ECDSA 私钥（签发）或公钥（仅解析）
	verifyKey any
	now    func() time.Time
}

// NewHS256 对称签名（默认档；密钥 ≥ 32 字节）。
func NewHS256(secret []byte) (*Signer, error) {
	if len(secret) < 32 {
		return nil, errors.New("auth: HS256 密钥必须 ≥ 32 字节")
	}
	return &Signer{method: jwt.SigningMethodHS256, key: secret, verifyKey: secret, now: time.Now}, nil
}

// Sign 签发 token（userID 不透明 string；ttl 建议 ≤ 2h；refresh 语义归业务）。
func (s *Signer) Sign(userID string, roles []string, ttl time.Duration) (string, error) {
	now := s.now()
	claims := Claims{
		UserID: userID,
		Roles:  roles,
		RegisteredClaims: jwt.RegisteredClaims{
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(ttl)),
		},
	}
	return jwt.NewWithClaims(s.method, claims).SignedString(s.key)
}

// ErrExpired / ErrInvalid 解析错误的契约映射锚点。
var (
	ErrExpired = xerror.New(errcode.CredentialsExpired) // 2002：凭证已过期
	ErrInvalid = xerror.New(errcode.Unauthorized)       // 2001：未认证
)

// Parse 解析并校验 token。过期 → 2002；签名/格式无效 → 2001（客户端应引导重登录，禁无脑重试）。
func (s *Signer) Parse(token string) (*Claims, error) {
	var claims Claims
	_, err := jwt.ParseWithClaims(token, &claims, func(t *jwt.Token) (any, error) {
		return s.verifykey(), nil
	}, jwt.WithValidMethods([]string{s.method.Alg()}))
	if err != nil {
		if errors.Is(err, jwt.ErrTokenExpired) {
			return nil, ErrExpired
		}
		return nil, ErrInvalid
	}
	return &claims, nil
}

func (s *Signer) verifykey() any {
	if s.verifyKey != nil {
		return s.verifyKey
	}
	return s.key
}

const (
	// ClaimsKey RequestContext 存储键：认证通过后 c.Get(auth.ClaimsKey) 断言为 *Claims 取完整载荷。
	ClaimsKey = "yarch.claims"
	// SubjectKey RequestContext 存储键：认证通过后以 string 写入 claims.UserID（sub）。
	// 供操作日志等只需 userId 的消费方 c.GetString(auth.SubjectKey) 直接读取——
	// ClaimsKey 存的是 *Claims 结构体，GetString 取不到。
	SubjectKey = "yarch.subject"
	// BearerPrefix Authorization 头前缀（契约认证协议：Bearer JWT）。
	BearerPrefix = "Bearer "
)

// RequireAuth 认证中间件：无凭证/无效签名 → 401+2001；过期 → 401+2002。
func RequireAuth(s *Signer) app.HandlerFunc {
	return func(ctx context.Context, c *app.RequestContext) {
		h := string(c.GetHeader("Authorization"))
		if len(h) <= len(BearerPrefix) || h[:len(BearerPrefix)] != BearerPrefix {
			middleware.WriteError(c, errcode.Unauthorized, "")
			c.Abort()
			return
		}
		claims, err := s.Parse(h[len(BearerPrefix):])
		if err != nil {
			be := xerror.FromError(err)
			middleware.WriteError(c, be.Code, "")
			c.Abort()
			return
		}
		c.Set(ClaimsKey, claims)
		c.Set(SubjectKey, claims.UserID)
		c.Next(ctx)
	}
}

// RequireRoles 角色断言（须挂在 RequireAuth 之后）：无所需角色 → 403+2003。
// 账号禁用（2004）属账号模型，由业务侧在认证钩子中自行判定。
func RequireRoles(roles ...string) app.HandlerFunc {
	return func(ctx context.Context, c *app.RequestContext) {
		v, ok := c.Get(ClaimsKey)
		if !ok {
			middleware.WriteError(c, errcode.Unauthorized, "")
			c.Abort()
			return
		}
		claims, ok := v.(*Claims)
		if !ok || !hasAny(claims.Roles, roles) {
			middleware.WriteError(c, errcode.Forbidden, "")
			c.Abort()
			return
		}
		c.Next(ctx)
	}
}

func hasAny(got, want []string) bool {
	if len(want) == 0 {
		return true
	}
	set := map[string]struct{}{}
	for _, r := range got {
		set[r] = struct{}{}
	}
	for _, r := range want {
		if _, ok := set[r]; ok {
			return true
		}
	}
	return false
}
