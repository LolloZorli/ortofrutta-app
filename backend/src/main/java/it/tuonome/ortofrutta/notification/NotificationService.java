package it.tuonome.ortofrutta.notification;

import it.tuonome.ortofrutta.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class NotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private final JdbcClient jdbc;
    private final JavaMailSender mailSender;
    private final AppProperties props;

    public NotificationService(JdbcClient jdbc, JavaMailSender mailSender, AppProperties props) {
        this.jdbc = jdbc;
        this.mailSender = mailSender;
        this.props = props;
    }

    @Scheduled(cron = "${NOTIFICATION_CRON:0 0 8 * * *}", zone = "Europe/Rome")
    public void sendStockAlerts() {
        String recipients = props.notifications() == null ? null : props.notifications().recipientEmails();
        if (recipients == null || recipients.isBlank()) return;

        List<String> lowStock = jdbc.sql("""
                select c.name || ': ' || p.name || ' (' || coalesce(sum(l.remaining_quantity), 0) || '/' || p.min_stock || ')'
                from products p
                join companies c on c.id = p.company_id
                join units u on u.id = p.unit_id and u.company_id = p.company_id
                left join lots l on l.product_id = p.id and l.company_id = p.company_id
                where p.active and p.min_stock > 0
                group by c.name, p.name, p.min_stock
                having coalesce(sum(l.remaining_quantity), 0) < p.min_stock
                order by c.name, p.name
                """).query((rs, row) -> rs.getString(1)).list();

        List<String> expiring = jdbc.sql("""
                select c.name || ': ' || p.name || ' (' || l.remaining_quantity || ' ' || u.short_label || ') scade il ' || l.expires_at
                from lots l
                join companies c on c.id = l.company_id
                join products p on p.id = l.product_id and p.company_id = l.company_id
                join units u on u.id = p.unit_id and u.company_id = p.company_id
                where l.remaining_quantity > 0 and l.expires_at is not null
                  and l.expires_at <= current_date + interval '3 days'
                order by c.name, l.expires_at, p.name
                """).query((rs, row) -> rs.getString(1)).list();

        if (lowStock.isEmpty() && expiring.isEmpty()) return;

        StringBuilder body = new StringBuilder("Avvisi Ortofrutta\n\n");
        if (!lowStock.isEmpty()) {
            body.append("Prodotti sotto scorta:\n").append(String.join("\n", lowStock)).append("\n\n");
        }
        if (!expiring.isEmpty()) {
            body.append("Lotti in scadenza entro 3 giorni:\n").append(String.join("\n", expiring));
        }

        String[] recipientList = Arrays.stream(recipients.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
        if (recipientList.length == 0) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientList);
            message.setSubject("Ortofrutta - avvisi scorte e scadenze");
            message.setText(body.toString());
            mailSender.send(message);
        } catch (RuntimeException mailError) {
            LOGGER.warn("Impossibile inviare gli avvisi giornalieri scorte/scadenze", mailError);
        }
    }
}
