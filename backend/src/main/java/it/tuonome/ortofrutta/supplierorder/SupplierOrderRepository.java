package it.tuonome.ortofrutta.supplierorder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    /** Paginata; il fornitore si carica insieme. Le righe si caricano a blocchi (batch fetch), senza N+1. */
    @EntityGraph(attributePaths = {"supplier"})
    Page<SupplierOrder> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
