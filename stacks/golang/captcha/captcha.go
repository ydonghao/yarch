// Package captcha 图形验证码（对偶 java yarch-captcha-starter）：
// 生成 PNG + Redis 一次性 token（校验即消费，TTL 默认 5 分钟）。
// 标准 GET /api/v1/captcha 端点由 Handler 提供（契约 java 侧同路径）。
package captcha

import (
	"context"
	"crypto/rand"
	"errors"
	"image"
	"image/color"
	"image/png"
	"bytes"
	"encoding/base64"
	"math/big"
	"time"

	"golang.org/x/image/font"
	"golang.org/x/image/font/basicfont"
	"golang.org/x/image/math/fixed"

	"github.com/redis/go-redis/v9"
)

// Store 验证码存储接口（一次性 token 语义）。
type Store interface {
	// Issue 签发：id → code，TTL。
	Issue(ctx context.Context, id, code string, ttl time.Duration) error
	// Consume 原子取出并删除（一次性）：id 不存在或已消费返回 false。
	Consume(ctx context.Context, id string) (string, bool, error)
}

// RedisStore Redis 实现（key 由调用方经 redix.Keys 生成，首段=服务名）。
type RedisStore struct{ rdb redis.UniversalClient }

func NewRedisStore(rdb redis.UniversalClient) *RedisStore { return &RedisStore{rdb: rdb} }

func (s *RedisStore) Issue(ctx context.Context, id, code string, ttl time.Duration) error {
	return s.rdb.Set(ctx, id, code, ttl).Err()
}

// Consume GETDEL 原子取出删除——一次性保证（校验失败同样消费，防重放爆破）。
func (s *RedisStore) Consume(ctx context.Context, id string) (string, bool, error) {
	code, err := s.rdb.GetDel(ctx, id).Result()
	if errors.Is(err, redis.Nil) {
		return "", false, nil
	}
	if err != nil {
		return "", false, err
	}
	return code, true, nil
}

// alphabet 去混淆字符集（剔除 0O1I）。
const alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

const (
	// DefaultLen 验证码字符数。
	DefaultLen = 5
	// DefaultTTL 有效期。
	DefaultTTL = 5 * time.Minute
	// W/H 图片尺寸。
	W, H = 160, 48
)

// NewCode 生成随机码。
func NewCode(n int) string {
	b := make([]byte, n)
	for i := range b {
		v, _ := rand.Int(rand.Reader, big.NewInt(int64(len(alphabet))))
		b[i] = alphabet[v.Int64()]
	}
	return string(b)
}

// NewID 生成验证码 ID。
func NewID() string {
	var b [16]byte
	_, _ = rand.Read(b[:])
	const hexdigits = "0123456789abcdef"
	out := make([]byte, 32)
	for i, v := range b {
		out[i*2] = hexdigits[v>>4]
		out[i*2+1] = hexdigits[v&0xf]
	}
	return string(out)
}

// RenderPNG 渲染验证码图片（字符随机色 + 干扰线 + 噪点）。
func RenderPNG(code string) []byte {
	img := image.NewRGBA(image.Rect(0, 0, W, H))
	// 背景
	for x := 0; x < W; x++ {
		for y := 0; y < H; y++ {
			img.Set(x, y, color.RGBA{R: 245, G: 247, B: 250, A: 255})
		}
	}
	// 噪点
	for i := 0; i < 220; i++ {
		img.Set(randInt(W), randInt(H), gray())
	}
	// 干扰线
	for i := 0; i < 3; i++ {
		c := color.RGBA{R: uint8(randInt(180)), G: uint8(randInt(180)), B: uint8(randInt(180)), A: 120}
		x, y := randInt(W), randInt(H)
		for s := 0; s < W; s += 3 {
			img.Set(s, (y+s/2+randInt(5))%H, c)
			_ = x
		}
	}
	// 字符
	for i := 0; i < len(code); i++ {
		drawChar(img, rune(code[i]), 12+i*28+randInt(6), 26+randInt(8), rgb())
	}
	var buf bytes.Buffer
	_ = png.Encode(&buf, img)
	return buf.Bytes()
}

// ImageDataURL PNG → data URL（前端 <img src> 直用）。
func ImageDataURL(pngBytes []byte) string {
	return "data:image/png;base64," + base64.StdEncoding.EncodeToString(pngBytes)
}

// Verify 校验（大小写不敏感；无论对错 token 均已一次性消费）。
func Verify(ctx context.Context, s Store, id, input string) bool {
	code, ok, err := s.Consume(ctx, id)
	if err != nil || !ok {
		return false
	}
	return equalFold(code, input)
}

// drawChar 用 font.Drawer + basicfont.Face7x13 绘制单字符。
func drawChar(img *image.RGBA, ch rune, x, y int, c color.RGBA) {
	d := &font.Drawer{
		Dst:  img,
		Src:  image.NewUniform(c),
		Face: basicfont.Face7x13,
	}
	d.Dot = fixed.P(x, y)
	d.DrawString(string(ch))
}

func randInt(n int) int {
	v, _ := rand.Int(rand.Reader, big.NewInt(int64(n)))
	return int(v.Int64())
}

func gray() color.RGBA { return color.RGBA{R: 170, G: 170, B: 170, A: 255} }

func rgb() color.RGBA {
	return color.RGBA{R: uint8(40 + randInt(120)), G: uint8(40 + randInt(120)), B: uint8(40 + randInt(120)), A: 255}
}

func equalFold(a, b string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := 0; i < len(a); i++ {
		ca, cb := a[i], b[i]
		if 'A' <= ca && ca <= 'Z' {
			ca += 32
		}
		if 'A' <= cb && cb <= 'Z' {
			cb += 32
		}
		if ca != cb {
			return false
		}
	}
	return true
}
