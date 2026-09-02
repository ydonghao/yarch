package io.github.yuandonghao.yarch.web;

import io.github.yuandonghao.yarch.common.code.BusinessException;
import io.github.yuandonghao.yarch.common.code.ErrorCode;
import io.github.yuandonghao.yarch.common.code.GlobalErrorCode;
import io.github.yuandonghao.yarch.common.web.RestResponse;
import jakarta.validation.ConstraintViolation;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 分层异常纪律的统一兜底（阿里手册分层规约的 Web 层职责，融合进 yarch DDD/web 分层）： 一切异常在这里收敛为 RestResponse 信封；HTTP
 * 状态由错误码表固定映射，服务端不得自行发挥。 业务可预期失败打 warn（无堆栈）；未预期失败打 error（logstash 编码器折叠为 stack 单字段）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<RestResponse<Void>> business(BusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        log.warn("business error: code={} message={}", ec.code(), ex.getMessage());
        return ResponseEntity.status(ec.httpStatus()).body(RestResponse.fail(ec, ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ResponseEntity<RestResponse<Void>> invalidBody(MethodArgumentNotValidException ex) {
        String detail =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        return invalidArgument(detail);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<RestResponse<Void>> handlerValidation(
            HandlerMethodValidationException ex) {
        String detail =
                ex.getAllErrors().stream()
                        .map(e -> e.getDefaultMessage() == null ? "参数不合法" : e.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        return invalidArgument(detail);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<RestResponse<Void>> constraintViolation(
            jakarta.validation.ConstraintViolationException ex) {
        String detail =
                ex.getConstraintViolations().stream()
                        .map(v -> lastPathNode(v) + " " + v.getMessage())
                        .collect(Collectors.joining("; "));
        return invalidArgument(detail);
    }

    @ExceptionHandler(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RestResponse<Void>> typeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        return invalidArgument(ex.getName() + " 类型不正确");
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<RestResponse<Void>> missingParts(Exception ex) {
        return invalidArgument(ex.getMessage());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<RestResponse<Void>> unreadable(Exception ex) {
        return respond(GlobalErrorCode.MALFORMED_BODY);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RestResponse<Void>> unsupportedMediaType(Exception ex) {
        // 状态码白名单无 415，按契约收敛到 400 + 1001
        return invalidArgument("Content-Type 不支持");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RestResponse<Void>> methodNotSupported(Exception ex) {
        // 状态码白名单无 405：按「该方法+路径的资源不存在」语义收敛到 404 + 1004
        return respond(GlobalErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RestResponse<Void>> noResource(Exception ex) {
        return respond(GlobalErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(
            org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
    public ResponseEntity<RestResponse<Void>> asyncTimeout(Exception ex) {
        return respond(GlobalErrorCode.UPSTREAM_TIMEOUT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestResponse<Void>> unexpected(Throwable ex) {
        // message 固定「内部错误」，细节只进日志（契约）；堆栈由 ndjson 编码器折叠进 stack 字段
        log.error("internal error", ex);
        return respond(GlobalErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<RestResponse<Void>> invalidArgument(String detail) {
        return ResponseEntity.badRequest()
                .body(
                        RestResponse.fail(
                                GlobalErrorCode.INVALID_ARGUMENT,
                                GlobalErrorCode.INVALID_ARGUMENT.defaultMessage() + "：" + detail));
    }

    private ResponseEntity<RestResponse<Void>> respond(ErrorCode ec) {
        return ResponseEntity.status(ec.httpStatus()).body(RestResponse.fail(ec));
    }

    private static String lastPathNode(ConstraintViolation<?> v) {
        String path = v.getPropertyPath().toString();
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
