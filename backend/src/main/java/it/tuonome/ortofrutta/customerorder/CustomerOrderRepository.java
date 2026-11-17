package it.tuonome.ortofrutta.customerorder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = {"customer"})
    List<CustomerOrder> findByStatusInOrderByPickupDateAscIdAsc(Collection<CustomerOrderStatus> statuses);

    /** Storico: solo le più recenti (pagina richiesta), non tutta la tabella. */
    @EntityGraph(attributePaths = {"customer"})
    List<CustomerOrder> findAllByOrderByPickupDateDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "lines", "lines.product"})
    List<CustomerOrder> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
