package it.tuonome.ortofrutta.customerorder;

import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.customer.Customer;
import it.tuonome.ortofrutta.customer.CustomerRepository;
import it.tuonome.ortofrutta.auth.UserRepository;
import it.tuonome.ortofrutta.auth.AppUser;
import it.tuonome.ortofrutta.company.CompanyContext;
import it.tuonome.ortofrutta.company.CompanyMembershipRepository;
import it.tuonome.ortofrutta.product.Product;
import it.tuonome.ortofrutta.product.ProductRepository;
import it.tuonome.ortofrutta.lookup.LookupService;
import it.tuonome.ortofrutta.lookup.LookupType;
import it.tuonome.ortofrutta.sale.SaleItem;
import it.tuonome.ortofrutta.sale.SaleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ordini dei clienti: NEW → READY → PICKED_UP/DELIVERED.
 * Al completamento la merce viene venduta (scalata dai lotti in FIFO): se manca, l'operazione viene rifiutata.
 */
@Service
@Transactional(readOnly = true)
public class CustomerOrderService {

    private static final Set<CustomerOrderStatus> OPEN =
            Set.of(CustomerOrderStatus.NEW, CustomerOrderStatus.READY);

    private final CustomerOrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final SaleService sales;
    private final LookupService lookups;
    private final UserRepository users;
    private final CompanyMembershipRepository memberships;

    public CustomerOrderService(CustomerOrderRepository orders, CustomerRepository customers,
                                ProductRepository products, SaleService sales, LookupService lookups, UserRepository users,
                                CompanyMembershipRepository memberships) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.sales = sales;
        this.lookups = lookups;
        this.users = users;
        this.memberships = memberships;
    }

    /** Aperte: tutte. Storico: le ultime 100. */
    public List<CustomerOrderDtos.Response> list(boolean onlyOpen) {
        List<CustomerOrder> list = onlyOpen
                ? orders.findByStatusInOrderByPickupDateAscIdAsc(OPEN)
                : orders.findAllByOrderByPickupDateDescIdDesc(PageRequest.of(0, 100));
        return list.stream().map(o -> CustomerOrderDtos.Response.from(o, lookups)).toList();
    }

    public List<CustomerOrderDtos.Response> myOrders(String username) {
        Long customerId = customerIdFor(username);
        return orders.findAllByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(o -> CustomerOrderDtos.Response.from(o, lookups)).toList();
    }

    @Transactional
    public CustomerOrderDtos.Response createForCustomer(String username, CustomerOrderDtos.CustomerRequest request) {
        if (request.pickupDate().isBefore(java.time.LocalDate.now())) {
            throw new BusinessException("La data di ritiro non può essere nel passato");
        }
        Customer customer = customers.findById(customerIdFor(username))
                .orElseThrow(() -> new NotFoundException("Profilo cliente", null));
        validateDelivery(request.fulfillmentType(), request.deliveryAddress(), request.deliveryCity(),
                request.deliveryPostalCode(), request.deliveryPhone());
        CustomerOrder order = new CustomerOrder(customer, request.pickupDate(), request.notes(),
                request.fulfillmentType(), request.deliveryAddress(), request.deliveryCity(),
                request.deliveryPostalCode(), request.deliveryPhone(), request.deliveryNotes());
        for (CustomerOrderDtos.CustomerLineRequest line : request.lines()) {
            Product product = products.findById(line.productId())
                    .orElseThrow(() -> new NotFoundException("Prodotto", line.productId()));
            if (!product.isActive()) throw new BusinessException("Il prodotto \"" + product.getName() + "\" non è disponibile");
            var unit = lookups.find(LookupType.UNIT, product.getUnitId());
            if (unit != null && Boolean.FALSE.equals(unit.allowDecimals())
                    && line.quantity().stripTrailingZeros().scale() > 0) {
                throw new BusinessException("Per \"" + product.getName() + "\" la quantità deve essere intera");
            }
            order.addLine(product, line.quantity(), product.getSalePrice());
        }
        return CustomerOrderDtos.Response.from(orders.save(order), lookups);
    }

    @Transactional
    public CustomerOrderDtos.Response create(CustomerOrderDtos.Request r, boolean canOverridePrice) {
        Customer customer = customers.findById(r.customerId())
                .orElseThrow(() -> new NotFoundException("Cliente", r.customerId()));
        validateDelivery(r.fulfillmentType(), r.deliveryAddress(), r.deliveryCity(), r.deliveryPostalCode(), r.deliveryPhone());
        CustomerOrder order = new CustomerOrder(customer, r.pickupDate(), r.notes(), r.fulfillmentType(),
                r.deliveryAddress(), r.deliveryCity(), r.deliveryPostalCode(), r.deliveryPhone(), r.deliveryNotes());
        for (CustomerOrderDtos.LineRequest l : r.lines()) {
            Product p = products.findById(l.productId())
                    .orElseThrow(() -> new NotFoundException("Prodotto", l.productId()));
            order.addLine(p, l.quantity(),
                    canOverridePrice && l.unitPrice() != null ? l.unitPrice() : p.getSalePrice());
        }
        return CustomerOrderDtos.Response.from(orders.save(order), lookups);
    }

    @Transactional
    public CustomerOrderDtos.Response markReady(Long id) {
        CustomerOrder o = find(id);
        if (o.getStatus() != CustomerOrderStatus.NEW) {
            throw new BusinessException("Solo una prenotazione nuova può essere segnata come pronta");
        }
        o.setStatus(CustomerOrderStatus.READY);
        return CustomerOrderDtos.Response.from(o, lookups);
    }

    @Transactional
    public CustomerOrderDtos.Response pickUp(Long id, Long paymentMethodId) {
        CustomerOrder o = find(id);
        if (o.isDelivery()) throw new BusinessException("Questo ordine è previsto per la consegna a domicilio");
        if (!OPEN.contains(o.getStatus())) {
            throw new BusinessException("Questa prenotazione è già chiusa");
        }
        // Il ritiro diventa una vendita di cassa (con metodo di pagamento): compare nei corrispettivi e si può stornare
        List<SaleItem> items = new ArrayList<>();
        for (CustomerOrderLine line : o.getLines()) {
            items.add(new SaleItem(line.getProduct().getId(), line.getQuantity(), line.getUnitPrice()));
        }
        sales.create(items, paymentMethodId, o.getCustomer().getId());
        o.setStatus(CustomerOrderStatus.PICKED_UP);
        return CustomerOrderDtos.Response.from(o, lookups);
    }

    @Transactional
    public CustomerOrderDtos.Response deliver(Long id, Long paymentMethodId) {
        CustomerOrder o = find(id);
        if (!o.isDelivery()) throw new BusinessException("Questo ordine è previsto per il ritiro in negozio");
        if (!OPEN.contains(o.getStatus())) throw new BusinessException("Questo ordine è già chiuso");
        List<SaleItem> items = new ArrayList<>();
        for (CustomerOrderLine line : o.getLines()) {
            items.add(new SaleItem(line.getProduct().getId(), line.getQuantity(), line.getUnitPrice()));
        }
        sales.create(items, paymentMethodId, o.getCustomer().getId());
        o.setStatus(CustomerOrderStatus.DELIVERED);
        return CustomerOrderDtos.Response.from(o, lookups);
    }

    @Transactional
    public CustomerOrderDtos.Response cancel(Long id) {
        CustomerOrder o = find(id);
        if (!OPEN.contains(o.getStatus())) {
            throw new BusinessException("Questa prenotazione è già chiusa");
        }
        o.setStatus(CustomerOrderStatus.CANCELLED);
        return CustomerOrderDtos.Response.from(o, lookups);
    }

    private CustomerOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Prenotazione", id));
    }

    private Long customerIdFor(String username) {
        AppUser user = users.findByUsername(username).orElseThrow(() -> new NotFoundException("Account", null));
        Long customerId = memberships.findActiveByCompanyAndUserId(CompanyContext.requireId(), user.getId())
                .map(m -> m.getCustomerId()).orElse(null);
        if (customerId == null) throw new BusinessException("L'account non è collegato a un profilo cliente in questa azienda");
        return customerId;
    }

    private static void validateDelivery(FulfillmentType type, String address, String city, String postalCode, String phone) {
        if (type == FulfillmentType.DELIVERY && (blank(address) || blank(city) || blank(postalCode) || blank(phone))) {
            throw new BusinessException("Per la consegna indica indirizzo, città, CAP e telefono");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
