package it.tuonome.ortofrutta.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** Movimenti paginati; il prodotto si carica insieme (una sola query, niente N+1). */
    @EntityGraph(attributePaths = {"product"})
    Page<StockMovement> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /** Movimenti di una vendita, con i lotti da cui sono stati prelevati (per lo storno). */
    @EntityGraph(attributePaths = {"lot"})
    List<StockMovement> findBySaleId(Long saleId);
}
