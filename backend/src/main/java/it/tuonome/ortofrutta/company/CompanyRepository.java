package it.tuonome.ortofrutta.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findBySlugIgnoreCaseAndEnabledTrue(String slug);

    List<Company> findAllByEnabledTrueOrderByNameAsc();

    boolean existsBySlugIgnoreCase(String slug);
}
