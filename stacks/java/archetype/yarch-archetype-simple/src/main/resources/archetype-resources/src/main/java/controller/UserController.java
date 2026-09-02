package ${package}.controller;

import ${package}.model.dto.UserCreateRequest;
import ${package}.model.dto.UserDTO;
import ${package}.service.UserService;
import io.github.yuandonghao.yarch.common.web.PageData;
import io.github.yuandonghao.yarch.common.web.RestResponse;
import io.github.yuandonghao.yarch.web.PageQuery;
import io.github.yuandonghao.yarch.web.idempotency.Idempotent;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Web 层（阿里分层纪律）：转发与基本参数校验，业务逻辑在 service（ArchUnit 守护单向依赖） */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @Idempotent(resource = "users")
    public ResponseEntity<RestResponse<UserDTO>> create(
            @Valid @RequestBody UserCreateRequest request) {
        UserDTO created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id()))
                .body(RestResponse.ok(created));
    }

    @GetMapping
    public RestResponse<PageData<UserDTO>> list(@Valid PageQuery query) {
        return RestResponse.ok(userService.page(query));
    }

    @GetMapping("/{userId}")
    public RestResponse<UserDTO> get(@PathVariable Long userId) {
        return RestResponse.ok(userService.requireById(userId));
    }

    @DeleteMapping("/{userId}")
    public RestResponse<Void> delete(@PathVariable Long userId) {
        userService.delete(userId);
        return RestResponse.ok();
    }
}
