package it.tuonome.ortofrutta.customer;

import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.lookup.LookupService;
import it.tuonome.ortofrutta.lookup.LookupType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;
    private final LookupService lookups;

    public CustomerService(CustomerRepository customers, LookupService lookups) {
        this.customers = customers;
        this.lookups = lookups;
    }

    public List<CustomerDtos.Response> list() {
        return customers.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CustomerDtos.Response create(CustomerDtos.Request r) {
        checkType(r.typeId(), null);
        return toResponse(customers.save(new Customer(r.name(), r.phone(), r.notes(), r.typeId())));
    }

    @Transactional
    public CustomerDtos.Response update(Long id, CustomerDtos.Request r) {
        Customer c = customers.findById(id).orElseThrow(() -> new NotFoundException("Cliente", id));
        checkType(r.typeId(), c.getTypeId());
        c.update(r.name(), r.phone(), r.notes(), r.typeId());
        return toResponse(c);
    }

    private void checkType(Long typeId, Long currentTypeId) {
        if (typeId != null && !typeId.equals(currentTypeId)) {
            lookups.requireActive(LookupType.CUSTOMER_TYPE, typeId, "Tipo di cliente");
        }
    }

    private CustomerDtos.Response toResponse(Customer c) {
        return new CustomerDtos.Response(c.getId(), c.getName(), c.getPhone(), c.getNotes(),
                c.getTypeId(), lookups.name(LookupType.CUSTOMER_TYPE, c.getTypeId()));
    }

    /** Un cliente con prenotazioni non si può eliminare: il vincolo del database risponde con HTTP 409. */
    @Transactional
    public void delete(Long id) {
        if (!customers.existsById(id)) {
            throw new NotFoundException("Cliente", id);
        }
        customers.deleteById(id);
        customers.flush();
    }
}
