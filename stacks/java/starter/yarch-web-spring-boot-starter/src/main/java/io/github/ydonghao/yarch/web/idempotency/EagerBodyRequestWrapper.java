package io.github.ydonghao.yarch.web.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 提前读取请求体的包装器：幂等摘要需要在 handler 执行前拿到 body（同键异参判据）， 读取后以字节数组回放给后续的 @RequestBody 解析。适用于 JSON 请求体（yarch
 * REST 契约口径）。
 */
public final class EagerBodyRequestWrapper extends HttpServletRequestWrapper {

    private static final long MAX_CACHED_BODY_BYTES = 1024 * 1024;

    private final byte[] body;

    EagerBodyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        long declared = request.getContentLengthLong();
        if (declared > MAX_CACHED_BODY_BYTES) {
            throw new IOException("request body exceeds idempotency cache limit: " + declared);
        }
        this.body = request.getInputStream().readAllBytes();
    }

    public byte[] body() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream buffer = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return buffer.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // 同步读取，无需异步通知
            }

            @Override
            public int read() {
                return buffer.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset charset =
                getCharacterEncoding() == null
                        ? StandardCharsets.UTF_8
                        : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
}
