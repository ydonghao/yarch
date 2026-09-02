package io.github.yuandonghao.yarch.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yuandonghao.yarch.common.code.BusinessException;
import io.github.yuandonghao.yarch.common.code.ErrorCode;
import io.github.yuandonghao.yarch.common.code.GlobalErrorCode;
import io.github.yuandonghao.yarch.common.trace.TraceIds;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 下游调用契约件（logging-trace.md 传播矩阵「出口」行 + rest-conventions A7）： 1. 超时强制——无超时的远程调用是故障最常见根因（默认 connect
 * 1s / read 3s）； 2. traceparent（W3C）优先注入，X-Trace-Id 兜底——上下文无 traceId 时为本调用生成； 3. RestResponse
 * 信封解包——data 直达业务类型，code != 0 透传下游业务码； 4. 网络层异常转译：超时 → 1008，不可达/5xx → 1009。
 */
public class YarchRestClient {

    private static final Logger log = LoggerFactory.getLogger(YarchRestClient.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private YarchRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> T get(String path, Class<T> dataType) {
        return exchange("GET", path, null, dataType).data();
    }

    public <T> T post(String path, Object body, Class<T> dataType) {
        return exchange("POST", path, body, dataType).data();
    }

    /** 全语义交换：返回信封四要素（data 已映射为业务类型） */
    public <T> HttpResult<T> exchange(String method, String path, Object body, Class<T> dataType) {
        String traceId = TraceIds.currentOrNull();
        boolean generated = traceId == null;
        if (generated) {
            traceId = TraceIds.newTraceId();
        }
        String traceParent = "00-" + traceId + "-" + randomSpanId() + "-01";

        String response;
        try {
            var spec =
                    restClient
                            .method(org.springframework.http.HttpMethod.valueOf(method))
                            .uri(URI.create(path))
                            .header("traceparent", traceParent)
                            .header("X-Trace-Id", traceId);
            if (body != null) {
                spec = spec.body(body);
            }
            response = spec.retrieve().body(String.class);
        } catch (ResourceAccessException e) {
            throw translate(e);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // 下游非 2xx 且非信封：按依赖不可用/超时转译
            throw translateStatus(e.getStatusCode());
        }
        return unwrap(response, dataType);
    }

    private <T> HttpResult<T> unwrap(String response, Class<T> dataType) {
        try {
            JsonNode root = mapper.readTree(response);
            int code = root.path("code").asInt(-1);
            if (code == -1) {
                throw new BusinessException(
                        GlobalErrorCode.UPSTREAM_TIMEOUT, "下游响应非 RestResponse 信封");
            }
            if (code != 0) {
                throw new BusinessException(
                        new RemoteErrorCode(code, root.path("message").asText("下游错误")));
            }
            JsonNode data = root.get("data");
            T typed = data == null || data.isNull() ? null : mapper.treeToValue(data, dataType);
            return new HttpResult<>(
                    code, root.path("message").asText(), typed, root.path("traceId").asText(""));
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(GlobalErrorCode.MALFORMED_BODY, "下游响应解析失败");
        }
    }

    private BusinessException translate(ResourceAccessException e) {
        if (e.getCause() instanceof SocketTimeoutException) {
            return new BusinessException(GlobalErrorCode.UPSTREAM_TIMEOUT, e.getMessage());
        }
        return new BusinessException(GlobalErrorCode.UNAVAILABLE, e.getMessage());
    }

    private BusinessException translateStatus(HttpStatusCode status) {
        if (status.value() == 504) {
            return new BusinessException(
                    GlobalErrorCode.UPSTREAM_TIMEOUT, String.valueOf(status.value()));
        }
        return new BusinessException(GlobalErrorCode.UNAVAILABLE, String.valueOf(status.value()));
    }

    private static String randomSpanId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(16);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** 下游业务码透传（动态 ErrorCode；跨服务语义同源——同一错误码表） */
    record RemoteErrorCode(int code, String message) implements ErrorCode {

        @Override
        public int code() {
            return code;
        }

        @Override
        public String identifier() {
            return "REMOTE_" + code;
        }

        @Override
        public String defaultMessage() {
            return message;
        }

        @Override
        public int httpStatus() {
            return 500;
        }
    }

    public record HttpResult<T>(int code, String message, T data, String traceId) {}

    public static class Builder {

        private final RestClient.Builder restBuilder = RestClient.builder();
        private Duration connectTimeout = Duration.ofSeconds(1);
        private Duration readTimeout = Duration.ofSeconds(3);

        public Builder baseUrl(String baseUrl) {
            restBuilder.baseUrl(baseUrl);
            return this;
        }

        /** A7 强制：默认 1s；显式可调但不可关闭 */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /** A7 强制：默认 3s */
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        public YarchRestClient build() {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) connectTimeout.toMillis());
            factory.setReadTimeout((int) readTimeout.toMillis());
            restBuilder.requestFactory(factory);
            return new YarchRestClient(restBuilder.build());
        }
    }
}
