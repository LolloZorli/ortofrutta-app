package it.tuonome.ortofrutta.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;
    private final RoleService roleService;

    public UserController(UserService service, RoleService roleService) {
        this.service = service;
        this.roleService = roleService;
    }

    @GetMapping
    public List<UserDtos.Response> list() {
        return service.list();
    }

    /** I ruoli tra cui scegliere nel modulo utente. */
    @GetMapping("/role-options")
    public List<RoleDtos.Option> roleOptions() {
        return roleService.options();
    }

    @PostMapping("/{id}/approve")
    public UserDtos.Response approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDtos.Response create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public UserDtos.Response update(@PathVariable Long id, @Valid @RequestBody UserDtos.UpdateRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, request, jwt.getSubject());
    }

    @PostMapping("/{id}/reset-password")
    public UserDtos.Response resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody UserDtos.ResetPasswordRequest request) {
        return service.resetPassword(id, request);
    }
}
