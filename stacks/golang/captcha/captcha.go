// Package captcha 验证码框架（contract/api/captcha.md v1.0）：
// Provider SPI 三档（image/sms-otp/turnstile）+ 框架核心（challenge 生命周期一次性原子消费、
// 场景路由、错误语义 2005）。标准 GET /api/v1/captcha 端点由 Handler 提供（六-1），
// 校验内联业务流不设独立端点（六-2）。
package captcha

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"image"
	"image/color"
	"image/png"
	"math/big"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"

	"golang.org/x/image/font"
	"golang.org/x/image/font/basicfont"
	"golang.org/x/image/math/fixed"
)

// Store 验证码存储接口（一次性 token 语义）。
type Store interface {
	// Issue 签发：id → code，TTL。
	Issue(ctx context.Context, id, code string, ttl time.Duration) error
	// Consume 原子取出并删除（一次性，三-3/三-4）：id 不存在或已消费返回 false。
	Consume(ctx context.Context, id string) (string, bool, error)
}

// RedisStore Redis 实现（full key 由框架核心 Service 经 redix.Keys 生成：
// 服务名:captcha:{provider}:{key}，首段=服务名纪律三-5）。
type RedisStore struct{ rdb redis.UniversalClient }

func NewRedisStore(rdb redis.UniversalClient) *RedisStore { return &RedisStore{rdb: rdb} }

func (s *RedisStore) Issue(ctx context.Context, id, code string, ttl time.Duration) error {
	return s.rdb.Set(ctx, id, code, ttl).Err()
}

// Consume GETDEL 原子取出删除——一次性保证（校验失败同样消费，防重放爆破三-3）。
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

// alphabet 去混淆字符集（二-2【强制】：32 字符，去 0/O/1/I——跨栈 conformance 断言对象）。
const alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

const (
	// DefaultLen 验证码字符数（二-2【强制】：4 位）。
	DefaultLen = 4
	// DefaultTTL 有效期默认（三-2【推荐】120s，Options.TTL 可配）。
	DefaultTTL = 2 * time.Minute
	// W/H 图片尺寸（渲染样式自由，CP9）。
	W, H = 160, 48
)

// NewCode 生成随机码（安全随机源，三-1 同源要求）。
func NewCode(n int) string {
	b := make([]byte, n)
	for i := range b {
		v, _ := rand.Int(rand.Reader, big.NewInt(int64(len(alphabet))))
		b[i] = alphabet[v.Int64()]
	}
	return string(b)
}

// NewID 生成验证码 ID（16 字节安全随机 hex = 128 bit 熵，三-1：禁业务可预测值）。
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
		_, y := randInt(W), randInt(H)
		for s := 0; s < W; s += 3 {
			img.Set(s, (y+s/2+randInt(5))%H, c)
		}
	}
	// 字符
	for i := 0; i < len(code); i++ {
		drawChar(img, rune(code[i]), 24+i*32+randInt(6), 30+randInt(8), rgb())
	}
	var buf bytes.Buffer
	_ = png.Encode(&buf, img)
	return buf.Bytes()
}

// ImageBase64 PNG → 裸 base64（二-2/六-1：无 data: 前缀，前端自行拼装 dataURL）。
func ImageBase64(pngBytes []byte) string {
	return base64.StdEncoding.EncodeToString(pngBytes)
}

// trimSpace 去首尾空白（比对口径，二-2）。
func trimSpace(s string) string { return strings.TrimSpace(s) }

// equalFold ASCII 大小写不敏感比较（二-2）。
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
