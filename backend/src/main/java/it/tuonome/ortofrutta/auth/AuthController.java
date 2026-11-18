package it.tuonome.ortofrutta.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody AuthDtos.CustomerRegistrationRequest request) {
        service.registerCustomer(request);
    }

    @PostMapping("/register-employee")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthDtos.EmployeeRegistrationResponse registerEmployee(
            @Valid @RequestBody AuthDtos.EmployeeRegistrationRequest request) {
        return service.registerEmployee(request);
    }

    @GetMapping("/me")
    public AuthDtos.Profile me(@AuthenticationPrincipal Jwt jwt) {
        return service.profile(jwt.getSubject());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        service.changePassword(jwt.getSubject(), request);
    }
}
