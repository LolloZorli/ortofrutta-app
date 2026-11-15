package it.tuonome.ortofrutta.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LotRepository extends JpaRepository<Lot, Long> {

    /** Lotti con merce residua, dal più vecchio (FIFO), bloccati per evitare vendite concorrenti sullo stesso lotto. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.product.id = :productId and l.remainingQuantity > 0 "
            + "order by l.receivedAt asc, l.id asc")
    List<Lot> findAvailableForUpdate(@Param("productId") Long productId);

    @Query("select l from Lot l join fetch l.product left join fetch l.supplier "
            + "where l.remainingQuantity > 0 order by l.expiresAt asc nulls last, l.receivedAt asc, l.id asc")
    List<Lot> findAllAvailable();

    @Query("select l.product.id as productId, sum(l.remainingQuantity) as quantity "
            + "from Lot l group by l.product.id")
    List<StockSum> sumByProduct();

    @Query("select sum(l.remainingQuantity) from Lot l where l.product.id = :productId")
    BigDecimal sumRemaining(@Param("productId") Long productId);

    default BigDecimal stockOf(Long productId) {
        BigDecimal v = sumRemaining(productId);
        return v == null ? BigDecimal.ZERO : v;
    }
}
