package it.tuonome.ortofrutta.delivery;

import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.company.CompanyContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DeliveryService {
    private final JdbcClient jdbc;

    public DeliveryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<DeliveryDtos.ZoneResponse> listZones() {
        long companyId = CompanyContext.requireId();
        return jdbc.sql("""
                select id, city, delivery_fee, minimum_order_subtotal, active
                from delivery_zones where company_id = :companyId order by lower(city), id
                """).param("companyId", companyId).query((rs, row) ->
                new DeliveryDtos.ZoneResponse(rs.getLong("id"), rs.getString("city"),
                        rs.getBigDecimal("delivery_fee"), rs.getBigDecimal("minimum_order_subtotal"),
                        rs.getBoolean("active"), slots(companyId, rs.getLong("id")))).list();
    }

    @Transactional
    public DeliveryDtos.ZoneResponse create(DeliveryDtos.ZoneRequest request) {
        long companyId = CompanyContext.requireId();
        String city = clean(request.city());
        ensureCityAvailable(companyId, city, null);
        Long zoneId = jdbc.sql("""
                insert into delivery_zones(company_id, city, delivery_fee, minimum_order_subtotal, active)
                values (:companyId, :city, :fee, :minimum, :active) returning id
                """).param("companyId", companyId).param("city", city).param("fee", request.deliveryFee())
                .param("minimum", request.minimumOrderSubtotal()).param("active", request.active())
                .query(Long.class).single();
        saveSlots(companyId, zoneId, request.slots());
        return getZone(companyId, zoneId);
    }

    @Transactional
    public DeliveryDtos.ZoneResponse update(long id, DeliveryDtos.ZoneRequest request) {
        long companyId = CompanyContext.requireId();
        lockZone(companyId, id);
        String city = clean(request.city());
        ensureCityAvailable(companyId, city, id);
        jdbc.sql("""
                update delivery_zones set city = :city, delivery_fee = :fee,
                    minimum_order_subtotal = :minimum, active = :active
                where company_id = :companyId and id = :id
                """).param("city", city).param("fee", request.deliveryFee())
                .param("minimum", request.minimumOrderSubtotal()).param("active", request.active())
                .param("companyId", companyId).param("id", id).update();
        saveSlots(companyId, id, request.slots());
        return getZone(companyId, id);
    }

    public DeliveryDtos.OptionsResponse options(String city, LocalDate date) {
        if (city == null || city.isBlank() || date == null) {
            return new DeliveryDtos.OptionsResponse(false, city, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        long companyId = CompanyContext.requireId();
        var zone = jdbc.sql("""
                select id, city, delivery_fee, minimum_order_subtotal
                from delivery_zones
                where company_id = :companyId and active and lower(trim(city)) = lower(trim(:city))
                """).param("companyId", companyId).param("city", city).query((rs, row) ->
                new ZoneRow(rs.getLong("id"), rs.getString("city"), rs.getBigDecimal("delivery_fee"),
                        rs.getBigDecimal("minimum_order_subtotal"))).optional();
        if (zone.isEmpty()) return new DeliveryDtos.OptionsResponse(false, city, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        ZoneRow z = zone.get();
        List<DeliveryDtos.AvailableSlot> available = jdbc.sql("""
                select s.id, s.name, s.max_orders - count(o.id) as remaining
                from delivery_slots s
                left join customer_orders o on o.company_id = s.company_id
                    and o.delivery_zone_id = s.zone_id and o.delivery_slot_id = s.id
                    and o.pickup_date = :date and o.status <> 'CANCELLED'
                where s.company_id = :companyId and s.zone_id = :zoneId and s.active
                group by s.id, s.name, s.max_orders
                having s.max_orders > count(o.id)
                order by s.id
                """).param("date", date).param("companyId", companyId).param("zoneId", z.id())
                .query((rs, row) -> new DeliveryDtos.AvailableSlot(
                        rs.getLong("id"), rs.getString("name"), rs.getInt("remaining"))).list();
        return new DeliveryDtos.OptionsResponse(true, z.city(), z.fee(), z.minimum(), available);
    }

    /**
     * Locks the matching zone until the order is committed. Every order for a zone takes the same
     * lock before counting capacity, so concurrent submissions cannot overbook a slot/date.
     */
    @Transactional
    public DeliveryDtos.Terms reserve(String city, LocalDate date, Long slotId, BigDecimal subtotal) {
        long companyId = CompanyContext.requireId();
        ZoneRow zone = jdbc.sql("""
                select id, city, delivery_fee, minimum_order_subtotal from delivery_zones
                where company_id = :companyId and active and lower(trim(city)) = lower(trim(:city))
                for update
                """).param("companyId", companyId).param("city", city).query((rs, row) ->
                new ZoneRow(rs.getLong("id"), rs.getString("city"), rs.getBigDecimal("delivery_fee"),
                        rs.getBigDecimal("minimum_order_subtotal"))).optional()
                .orElse(null);
        if (zone == null) {
            return new DeliveryDtos.Terms(null, null, null, BigDecimal.ZERO);
        }
        if (subtotal.compareTo(zone.minimum()) < 0) {
            throw new BusinessException("L'importo minimo per la consegna è " + zone.minimum());
        }
        var slot = jdbc.sql("""
                select id, name, max_orders from delivery_slots
                where company_id = :companyId and zone_id = :zoneId and id = :slotId and active
                """).param("companyId", companyId).param("zoneId", zone.id()).param("slotId", slotId)
                .query((rs, row) -> new SlotRow(rs.getLong("id"), rs.getString("name"), rs.getInt("max_orders")))
                .optional().orElseThrow(() -> new BusinessException("La fascia di consegna selezionata non è disponibile"));
        Long count = jdbc.sql("""
                select count(*) from customer_orders
                where company_id = :companyId and delivery_zone_id = :zoneId and delivery_slot_id = :slotId
                  and pickup_date = :date and status <> 'CANCELLED'
                """).param("companyId", companyId).param("zoneId", zone.id()).param("slotId", slot.id()).param("date", date)
                .query(Long.class).single();
        if (count >= slot.capacity()) throw new BusinessException("La fascia di consegna selezionata è esaurita");
        return new DeliveryDtos.Terms(zone.id(), slot.id(), slot.name(), zone.fee());
    }

    private List<DeliveryDtos.SlotResponse> slots(long companyId, long zoneId) {
        return jdbc.sql("""
                select id, name, max_orders, active from delivery_slots
                where company_id = :companyId and zone_id = :zoneId order by id
                """).param("companyId", companyId).param("zoneId", zoneId).query((rs, row) ->
                new DeliveryDtos.SlotResponse(rs.getLong("id"), rs.getString("name"),
                        rs.getInt("max_orders"), rs.getBoolean("active"))).list();
    }

    private void saveSlots(long companyId, long zoneId, List<DeliveryDtos.SlotRequest> requested) {
        List<Long> retained = new java.util.ArrayList<>();
        for (DeliveryDtos.SlotRequest slot : requested) {
            String name = clean(slot.name());
            if (slot.id() == null) {
                var existing = jdbc.sql("""
                        select id from delivery_slots
                        where company_id = :companyId and zone_id = :zoneId and lower(name) = lower(:name)
                        """).param("companyId", companyId).param("zoneId", zoneId).param("name", name)
                        .query(Long.class).optional();
                if (existing.isPresent()) {
                    jdbc.sql("""
                            update delivery_slots set name = :name, max_orders = :capacity, active = :active
                            where company_id = :companyId and zone_id = :zoneId and id = :id
                            """).param("name", name).param("capacity", slot.maxOrders()).param("active", slot.active())
                            .param("companyId", companyId).param("zoneId", zoneId).param("id", existing.get()).update();
                    retained.add(existing.get());
                } else {
                    Long id = jdbc.sql("""
                            insert into delivery_slots(company_id, zone_id, name, max_orders, active)
                            values (:companyId, :zoneId, :name, :capacity, :active) returning id
                            """).param("companyId", companyId).param("zoneId", zoneId).param("name", name)
                            .param("capacity", slot.maxOrders()).param("active", slot.active())
                            .query(Long.class).single();
                    retained.add(id);
                }
            } else {
                int updated = jdbc.sql("""
                        update delivery_slots set name = :name, max_orders = :capacity, active = :active
                        where company_id = :companyId and zone_id = :zoneId and id = :id
                        """).param("name", name).param("capacity", slot.maxOrders()).param("active", slot.active())
                        .param("companyId", companyId).param("zoneId", zoneId).param("id", slot.id()).update();
                if (updated != 1) throw new NotFoundException("Fascia di consegna", slot.id());
                retained.add(slot.id());
            }
        }
        if (retained.isEmpty()) throw new BusinessException("Configura almeno una fascia di consegna");
        jdbc.sql("update delivery_slots set active = false where company_id = :companyId and zone_id = :zoneId and id not in (:ids)")
                .param("companyId", companyId).param("zoneId", zoneId).param("ids", retained).update();
    }

    private DeliveryDtos.ZoneResponse getZone(long companyId, long id) {
        return jdbc.sql("""
                select id, city, delivery_fee, minimum_order_subtotal, active from delivery_zones
                where company_id = :companyId and id = :id
                """).param("companyId", companyId).param("id", id).query((rs, row) ->
                new DeliveryDtos.ZoneResponse(rs.getLong("id"), rs.getString("city"), rs.getBigDecimal("delivery_fee"),
                        rs.getBigDecimal("minimum_order_subtotal"), rs.getBoolean("active"), slots(companyId, id))).single();
    }

    private void lockZone(long companyId, long id) {
        if (jdbc.sql("select id from delivery_zones where company_id = :companyId and id = :id for update")
                .param("companyId", companyId).param("id", id).query(Long.class).optional().isEmpty()) {
            throw new NotFoundException("Zona di consegna", id);
        }
    }

    private void ensureCityAvailable(long companyId, String city, Long exceptId) {
        var query = exceptId == null
                ? jdbc.sql("""
                        select id from delivery_zones
                        where company_id = :companyId and lower(trim(city)) = lower(trim(:city))
                        """).param("companyId", companyId).param("city", city)
                : jdbc.sql("""
                        select id from delivery_zones
                        where company_id = :companyId and lower(trim(city)) = lower(trim(:city)) and id <> :exceptId
                        """).param("companyId", companyId).param("city", city).param("exceptId", exceptId);
        if (query.query(Long.class).optional().isPresent()) {
            throw new BusinessException("Esiste già una zona per questa città");
        }
    }

    private static String clean(String value) { return value.trim(); }
    private record ZoneRow(long id, String city, BigDecimal fee, BigDecimal minimum) {}
    private record SlotRow(long id, String name, int capacity) {}
}
