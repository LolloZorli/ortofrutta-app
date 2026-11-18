package it.tuonome.ortofrutta.company;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Resolves X-Company-Slug before bearer authentication so membership checks use the selected shop. */
public class CompanyContextFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Company-Slug";
    private final CompanyRepository companies;
    private final TransactionTemplate transactions;
    private final JdbcClient jdbc;

    public CompanyContextFilter(CompanyRepository companies, TransactionTemplate transactions, JdbcClient jdbc) {
        this.companies = companies;
        this.transactions = transactions;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String slug = request.getHeader(HEADER);
        Company company;
        if (slug == null || slug.isBlank()) {
            company = companies.findById(CompanyContext.LEGACY_COMPANY_ID).orElse(null);
        } else {
            company = companies.findBySlugIgnoreCaseAndEnabledTrue(slug.trim()).orElse(null);
        }
        if (company == null || !company.isEnabled()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Azienda non trovata\",\"status\":404}");
            return;
        }
        CompanyContext.set(company.getId());
        try {
            transactions.execute(status -> {
                jdbc.sql("select set_config('app.company_id', :companyId, true)")
                        .param("companyId", company.getId().toString()).query(String.class).single();
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    status.setRollbackOnly();
                    throw new FilterFailure(e);
                }
                return null;
            });
        } catch (FilterFailure e) {
            if (e.getCause() instanceof IOException io) throw io;
            if (e.getCause() instanceof ServletException servlet) throw servlet;
            throw e;
        } catch (UnexpectedRollbackException e) {
            // MVC may translate a service exception to a 4xx response while the joined service
            // transaction marks this request transaction rollback-only. Preserve that response.
            if (response.getStatus() < 400) throw e;
        } finally {
            CompanyContext.clear();
        }
    }

    private static final class FilterFailure extends RuntimeException {
        FilterFailure(Exception cause) { super(cause); }
    }
}
