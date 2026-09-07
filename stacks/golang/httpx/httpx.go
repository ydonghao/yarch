// Package httpx 是下游调用契约件（契约「跨进程传播矩阵」出口行 + A7 远程调用必超时）：
// 超时强制、traceparent 注入（优先）+ X-Trace-Id（兜底）、RestResponse 信封解包、
// 下游故障 → 1008（上游超时）/ 1009（暂不可用）转译。
package httpx

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"time"

	"github.com/ydonghao/yarch/stacks/golang/errcode"
	"github.com/ydonghao/yarch/stacks/golang/logx"
	"github.com/ydonghao/yarch/stacks/golang/response"
	"github.com/ydonghao/yarch/stacks/golang/xerror"
)

const (
	// DefaultTimeout 默认超时（A7：远程调用必超时；可缩短不可免除）。
	DefaultTimeout = 5 * time.Second
	// MaxTimeout 超时上限（防误配长超时拖垮上游）。
	MaxTimeout = 30 * time.Second
)

// Client 下游 HTTP 客户端。
type Client struct {
	hc *http.Client
}

// New 构造下游客户端。timeout ≤ 0 用默认 5s；> MaxTimeout 截断为 30s（超时强制）。
func New(timeout time.Duration) *Client {
	if timeout <= 0 {
		timeout = DefaultTimeout
	}
	if timeout > MaxTimeout {
		timeout = MaxTimeout
	}
	return &Client{hc: &http.Client{Timeout: timeout}}
}

// Do 发起请求并解包信封：
//   - 注入 traceparent（W3C：trace-id 继承当前 ctx，span-id 新生成）+ X-Trace-Id；
//   - 下游成功（code=0）→ 返回 data；
//   - 下游业务失败（code≠0）→ *xerror.BizError（透传下游 code/message）；
//   - 超时 → 1008；连接/传输/非信封 → 1009。
func Do[T any](c *Client, ctx context.Context, method, url string, body any, header map[string]string) (T, *xerror.BizError) {
	var zero T

	var reader io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return zero, xerror.New(errcode.InternalError, "marshal request: "+err.Error())
		}
		reader = bytes.NewReader(b)
	}
	req, err := http.NewRequestWithContext(ctx, method, url, reader)
	if err != nil {
		return zero, xerror.New(errcode.Unavailable, err.Error())
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	for k, v := range header {
		req.Header.Set(k, v)
	}
	injectTrace(ctx, req)

	resp, err := c.hc.Do(req)
	if err != nil {
		return zero, translateTransport(err)
	}
	defer func() { _ = resp.Body.Close() }()

	raw, err := io.ReadAll(io.LimitReader(resp.Body, 8<<20))
	if err != nil {
		return zero, translateTransport(err)
	}

	var env response.Response[T]
	if err := json.Unmarshal(raw, &env); err != nil {
		// 非 RestResponse 信封（进程级宕机、网关裸响应）→ 1009；
		// 正常故障路径的信封由网关层保证（rest-response.md 网关故障面）。
		return zero, xerror.New(errcode.Unavailable)
	}
	if env.Code != 0 {
		return zero, &xerror.BizError{Code: env.Code, Message: env.Message}
	}
	if env.Data == nil {
		return zero, nil
	}
	return *env.Data, nil
}

// Get / Post 便捷封装。
func Get[T any](c *Client, ctx context.Context, url string, header map[string]string) (T, *xerror.BizError) {
	return Do[T](c, ctx, http.MethodGet, url, nil, header)
}

func Post[T any](c *Client, ctx context.Context, url string, body any, header map[string]string) (T, *xerror.BizError) {
	return Do[T](c, ctx, http.MethodPost, url, body, header)
}

// injectTrace 出口传播：traceparent 优先（契约传播矩阵 HTTP/RPC 行）。
func injectTrace(ctx context.Context, req *http.Request) {
	tid := logx.TraceID(ctx)
	if tid == "" {
		return
	}
	var b [8]byte
	if _, err := rand.Read(b[:]); err != nil {
		req.Header.Set("X-Trace-Id", tid)
		return
	}
	req.Header.Set("traceparent", fmt.Sprintf("00-%s-%s-01", tid, hex.EncodeToString(b[:])))
	req.Header.Set("X-Trace-Id", tid)
}

// translateTransport 传输层错误 → 1008/1009。
func translateTransport(err error) *xerror.BizError {
	if isTimeout(err) {
		return xerror.New(errcode.UpstreamTimeout)
	}
	return xerror.New(errcode.Unavailable)
}

func isTimeout(err error) bool {
	if errors.Is(err, context.DeadlineExceeded) {
		return true
	}
	var ne net.Error
	if errors.As(err, &ne) {
		return ne.Timeout()
	}
	return false
}
