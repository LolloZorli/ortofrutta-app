# Ortofrutta

Gestionale web per negozi di frutta e verdura: cassa, magazzino, ordini e clienti in un'interfaccia mobile-first installabile come PWA.

## Panoramica

- **Cassa**: vendita di più prodotti, ricerca per nome o codice a barre, pagamenti, clienti e storni.
- **Magazzino**: lotti con costo e scadenza, carichi, inventario, scarti e movimenti tracciati. Le vendite consumano i lotti in ordine FIFO.
- **Ordini**: acquisti dai fornitori e prenotazioni dei clienti, con ritiro o consegna a domicilio.
- **Gestione**: anagrafiche, report, chiusura contanti, cartellini prezzo, ruoli e permessi, registro attività.
- **Portale clienti**: account personale indipendente dal negozio; il cliente sceglie il negozio, cerca nel catalogo anche con refusi e gestisce i propri ordini.
- **Multi-azienda**: cataloghi, ordini e permessi aziendali sono isolati per negozio; l'identità cliente è globale e il profilo nel negozio viene creato al primo ordine.

### Tecnologie

Frontend React e TypeScript; backend Java 21 e Spring Boot; database PostgreSQL con migrazioni Flyway. La ricerca catalogo usa `pg_trgm` di PostgreSQL, senza un servizio Elasticsearch esterno. Il frontend è una PWA e comunica con l'API Spring.

## Pubblicazione: GitHub, Render e Neon

Il repository è collegato a Render tramite `render.yaml`; Render ospita il frontend statico e il backend, mentre Neon fornisce PostgreSQL.

1. Le modifiche vengono sviluppate sul branch `collaudo`. Il push esegue i controlli CI, ma non aggiorna la produzione.
2. Quando i controlli sono verdi, si apre una pull request da `collaudo` a `master`.
3. Il merge su `master` esegue la CI. Se i controlli passano, Render distribuisce frontend e backend dal repository.
4. Il backend usa il profilo `prod`; Flyway applica le migrazioni al database all'avvio.

Il Blueprint configura il backend sul piano gratuito, il frontend statico e i collegamenti URL fra i servizi. Per il database configura in Render:

| Variabile | Valore |
|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC Neon, ad esempio `jdbc:postgresql://HOST/DATABASE?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | Utente database Neon |
| `SPRING_DATASOURCE_PASSWORD` | Password database Neon |
| `ADMIN_PASSWORD` | Password robusta iniziale per l'amministratore |

Render genera `JWT_SECRET`. Il Blueprint collega `VITE_API_BASE_URL`, `APP_CORS_ALLOWED_ORIGINS` e `PASSWORD_RESET_BASE_URL` agli URL pubblici dei servizi. Non impostare questi valori con `localhost` in produzione e non committare file `.env` o credenziali.

Dopo il deploy:

- controlla i log backend: Flyway deve completare e Spring deve avviarsi senza errori di connessione;
- verifica `https://<backend>/actuator/health` e `https://<backend>/actuator/health/readiness`;
- apri il frontend, accedi come admin e cambia la password iniziale.

### Limiti del piano gratuito

Il backend gratuito può andare in sleep dopo 15 minuti senza traffico e impiegare circa un minuto a riattivarsi. La PWA conserva la shell dell'interfaccia, ma vendite, ordini e autenticazione richiedono il backend raggiungibile: non è una modalità offline. Filesystem locale e database in-memory non sono persistenti: i dati applicativi devono restare su Neon. Verifica le quote correnti di Render e Neon; il piano gratuito è adatto a prove e progetti personali, non offre garanzie di disponibilità o backup.

## Sviluppo locale

Prerequisiti: Docker Compose, Java 21 e Node.js 22.

Per avviare database, backend e frontend in container dalla radice:

```bash
docker compose --profile app up --build -d
```

Apri <http://localhost:3000>. L'API è su `http://localhost:8081`; il database locale usa la porta `5433`. Questo ambiente carica dati demo e usa credenziali predefinite: non riutilizzarle in produzione.

Per fermare i container mantenendo i dati:

```bash
docker compose --profile app down
```

Per sviluppo con frontend e backend avviati separatamente, avvia PostgreSQL con `docker compose up -d db`, poi:

```bash
mvn -f backend/pom.xml spring-boot:run
npm --prefix frontend ci
npm --prefix frontend run dev
```

La build frontend verifica i tipi TypeScript; `npm test` esegue i test unitari. I test backend di integrazione usano Testcontainers e richiedono Docker:

```bash
npm --prefix frontend test
npm --prefix frontend run build
mvn -B -f backend/pom.xml verify
```

## Configurazione e funzionalità opzionali

Per lo sviluppo locale, `docker-compose.yml` fornisce le credenziali del database, il profilo demo e i valori predefiniti. Non copiare questi valori in Render.

Il reset password e le notifiche email richiedono un server SMTP configurato tramite `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME` e `SPRING_MAIL_PASSWORD`. I destinatari delle notifiche si impostano con `NOTIFICATION_RECIPIENT_EMAILS`. Sul piano gratuito, che può sospendere il backend per inattività, le notifiche pianificate non sono affidabili: non usarle come unico avviso operativo.

Le aliquote IVA sono configurabili dall'amministratore. Verificale con il commercialista prima di utilizzare dati reali: la migrazione iniziale assegna il 4% ai prodotti esistenti. Il riepilogo dei corrispettivi è gestionale e non sostituisce un registratore di cassa fiscale.

### Backup manuale Neon

Per una copia manuale servono i PostgreSQL client (`pg_dump` e `pg_restore`) e la connection string Neon. In una shell Bash, salva la stringa in una variabile d'ambiente senza inserirla nei comandi condivisi o nel repository:

```bash
export NEON_DATABASE_URL='postgresql://...'
pg_dump --format=custom --no-owner --file="$HOME/ortofrutta-backup.dump" "$NEON_DATABASE_URL"
```

Conserva il dump in un luogo privato e protetto, non nel repository. Per ripristinarlo, crea prima un database Neon vuoto e usa la sua connection string come destinazione:

```bash
export RESTORE_DATABASE_URL='postgresql://...'
pg_restore --no-owner --dbname="$RESTORE_DATABASE_URL" "$HOME/ortofrutta-backup.dump"
```

Non eseguire il ripristino sul database in uso: questa procedura è per un database di destinazione vuoto. Verifica periodicamente il backup ripristinandolo in un ambiente separato e collauda le migrazioni prima di applicarle ai dati reali.
