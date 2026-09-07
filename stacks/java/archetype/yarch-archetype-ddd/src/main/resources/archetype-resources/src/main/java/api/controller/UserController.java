package ${package}.api.controller;

import ${package}.api.assembler.UserAssembler;
import ${package}.api.dto.CreateUserRequest;
import ${package}.api.dto.UserResponse;
import ${package}.application.command.CreateUserCommand;
import ${package}.application.service.UserApplicationService;
import ${package}.domain.model.User;
import io.github.ydonghao.yarch.common.web.PageData;
import io.github.ydonghao.yarch.common.web.RestResponse;
import io.github.ydonghao.yarch.web.PageQuery;
import io.github.ydonghao.yarch.web.idempotency.Idempotent;
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

/** api 层只做接入：参数校验、信封封装、HTTP 语义（rest-conventions.md v1.0）。 业务逻辑零——编排进 application，规则进 domain。 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService users;
    private final UserAssembler assembler;

    @PostMapping
    @Idempotent(resource = "users")
    public ResponseEntity<RestResponse<UserResponse>> create(
            @Valid @RequestBody CreateUserRequest request) {
        User created = users.create(new CreateUserCommand(request.email(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id()))
                .body(RestResponse.ok(assembler.toResponse(created)));
    }

    @GetMapping
    public RestResponse<PageData<UserResponse>> list(@Valid PageQuery query) {
        PageData<User> page = users.page(query);
        return RestResponse.ok(
                PageData.of(
                        page.getList().stream().map(assembler::toResponse).toList(),
                        page.getTotal(),
                        page.getPage(),
                        page.getPageSize()));
    }

    @GetMapping("/{userId}")
    public RestResponse<UserResponse> get(@PathVariable Long userId) {
        return RestResponse.ok(assembler.toResponse(users.requireById(userId)));
    }

    @DeleteMapping("/{userId}")
    public RestResponse<Void> delete(@PathVariable Long userId) {
        users.delete(userId);
        return RestResponse.ok();
    }
}
