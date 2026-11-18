package it.tuonome.ortofrutta.company;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companies;

    public CompanyController(CompanyService companies) {
        this.companies = companies;
    }

    @GetMapping
    public List<CompanyDtos.PublicShop> shops() {
        return companies.shops();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyDtos.RegisteredShop register(@Valid @RequestBody CompanyDtos.RegisterRequest request) {
        return companies.register(request);
    }
}
