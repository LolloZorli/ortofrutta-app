package it.tuonome.ortofrutta.sale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Page<Sale> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Sale> findByCreatedByOrderByCreatedAtDescIdDesc(String createdBy, Pageable pageable);
    List<Sale> findAllByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, SaleStatus status);
}
