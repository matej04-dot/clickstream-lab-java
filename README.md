# Praćenje klikova u Javi

Samostalna Spring Boot aplikacija: preglednik šalje klik, aplikacija ga sprema
izravno u PostgreSQL i prikazuje broj spremljenih klikova po gumbu.

## Pokretanje u WSL-u

Potrebni su JDK 21 i Docker Desktop s uključenom integracijom za Ubuntu WSL.
Maven Wrapper preuzima Maven i ovisnosti pri prvom pokretanju.

Iz korijena projekta:

```bash
cp -n .env.example .env
```

U `.env` zamijenite predložak lozinke vlastitom lokalnom lozinkom. Za naredbe
ispod koristite Bash zapise `IME=vrijednost`; posebne znakove stavite unutar
jednostrukih navodnika. `source` izvršava sadržaj datoteke, pa učitajte samo
vlastitu, pouzdanu konfiguraciju.

```bash
set -a
source .env
set +a
docker compose config --quiet
docker stop clickstream-java-postgres-1
docker compose up -d --wait
./mvnw spring-boot:run
```

Otvorite <http://localhost:8080/>. Stranica ima tri gumba i statistiku koja se
učitava pri otvaranju stranice i osvježava nakon uspješnog spremanja klika. Sesija se čuva u
`sessionStorage`; svaki klik dobiva novi UUID. Ako zahtjev ne uspije, stranica
prikazuje pogrešku i ne tvrdi da je klik spremljen. Nema automatskog ponovnog
slanja; mrežni timeout može nastati i nakon potvrđenog upisa u bazu.

Compose i Java aplikacija učitavaju `.env` iz korijena projekta. Java ga čita
kao properties datoteku: koristite `IME=vrijednost` bez navodnika za vrijednosti.
Izvoz varijabli nije potreban za `./mvnw spring-boot:run`; već izvezene varijable
imaju prednost pred `.env`. Zadani portovi su 8080 za aplikaciju
i 5433 za PostgreSQL. Za promjenu koristite `SERVER_PORT` i `POSTGRES_PORT`.
Podržane su i standardne `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`
i `SPRING_DATASOURCE_PASSWORD` postavke.

Compose koristi postojeći volumen `click-monitoring_postgres_data` i stari
PostgreSQL projekt `click-monitoring`. Prije pokretanja zaustavite novu bazu
`clickstream-java-postgres-1`, koja zauzima isti port. Postojeći volumen mora
već postojati; Compose neće stvoriti praznu zamjensku bazu. U `.env` koristite
pristupne podatke stare baze i `SPRING_FLYWAY_ENABLED=false` kako bi aplikacija
koristila postojeću tablicu bez pokretanja migracija. Stare podatke možete
provjeriti naredbom:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT count(*) FROM click_events;"'
```

Kada je Flyway uključen, pri pokretanju stvara tablicu `click_events` i bilježi migracije.
Ako baza pri pokretanju nije dostupna ili migracija ne uspije, aplikacija se
ne pokreće. Za prekid veze tijekom rada API vraća 503. Ostale neočekivane
pogreške vraćaju 500 bez SQL-a i pristupnih podataka u odgovoru.

## Slojevi i tijek zahtjeva

`ClickstreamApplication` pokreće Spring i embedded HTTP poslužitelj.

1. `ClickEventController` (`@RestController`) prima JSON, provodi validaciju
   i pretvara rezultat u HTTP odgovor.
2. `ClickEventService` (`@Service`) upravlja spremanjem i dohvatom. Metoda
   spremanja ima `@Transactional`: INSERT i dohvat spremljenog retka dio su
   iste transakcije, koja se potvrđuje prije povratka rezultata controlleru.
3. `ClickEventRepository` (`@Repository`) izvršava parametrizirane upite preko
   `JdbcTemplate`. HikariCP upravlja skupom veza s PostgreSQL-om.
4. `ClickEventRowMapper` (`@Component`) mapira SQL retke u Java record.

Sve ovisnosti primaju se kroz konstruktor. Component je pomoćna komponenta
repositoryja; nije dodatni korak kroz koji mora prolaziti svaki zahtjev.
JSON nazivi ostaju snake_case, a Java polja koriste camelCase.

UUID je primarni ključ. `INSERT ... ON CONFLICT DO NOTHING` atomarno sprečava
duplikate i pri istodobnim zahtjevima. Ponovljeni ID vraća izvorni događaj,
čak i kada se novi sadržaj razlikuje. Statistika broji sve pohranjene događaje,
neovisno o stranici i sesiji, sortirano prema `element_id`.

## API

| Metoda i ruta | Odgovor |
| --- | --- |
| `GET /` | HTML stranica |
| `POST /events/click` | 201 za novi događaj, 200 za duplikat; spremljeni događaj |
| `GET /events/click/{eventId}` | 200 i događaj ili 404 bez tijela |
| `GET /stats/clicks` | `[{"element_id":"gumb-č","count":1}]`; prazna baza: `[]` |

POST uključuje `X-Event-Inserted: true` ili `false`. Ne postoji Redisov ID.
Sva polja obvezna su: UUID `event_id`, cijeli broj `schema_version` jednak 1,
ISO datum `occurred_at` s vremenskom zonom i tekstovi `session_id`, `page`,
`element_id`. Nakon uklanjanja okolnih bjelina sesija i element imaju 1–128,
a stranica 1–2048 Unicode znakova. Dodatna polja, pogrešni tipovi i nevaljane
vrijednosti vraćaju 400 s tijelom `{"detail":"Neispravan zahtjev."}`.
PostgreSQL čuva vremenski trenutak s mikrosekundnom preciznošću; originalni
tekstualni offset nije sačuvan.

```bash
curl -i http://localhost:8080/events/click \
  -H 'Content-Type: application/json' \
  -d '{"event_id":"c46de8cb-9280-4db2-9b7e-d00e7c79cb68","schema_version":1,"occurred_at":"2026-10-06T12:00:00+02:00","session_id":"session-1","page":"/products","element_id":"gumb-č"}'
curl -i http://localhost:8080/events/click/c46de8cb-9280-4db2-9b7e-d00e7c79cb68
curl -i http://localhost:8080/stats/clicks
```

Ponovite isti POST: status postaje 200, a broj klikova ostaje isti.
Za novi klik koristite novi UUID.

## Testiranje i pakiranje

```bash
./mvnw test
./mvnw verify -Pintegration
./mvnw package
java -jar target/clickstream-java-0.0.1-SNAPSHOT.jar
```

Obični testovi koriste MockMvc i zamjenski service, bez baze i Dockera.
Integracijski profil zahtijeva Docker: Testcontainers pokreće zaseban
PostgreSQL i provjerava Flyway, stvarnu pohranu, konkurentne duplikate,
Unicode, vremenske trenutke, SQL parametre, statistiku i HTTP tijek.
Integracijski testovi ne koriste razvojnu bazu i ne preskaču se ako Docker
nedostaje: profil tada prijavljuje pogrešku.

Ručno provjerite sva tri gumba, ponovno učitavanje stranice i očuvanje brojeva
nakon restarta. Za provjeru prekida veze zaustavite bazu dok aplikacija radi:

```bash
docker compose stop postgres
```

Klik i statistika trebaju prijaviti pogrešku. Nakon `docker compose start
postgres` osvježavanje ponovno prikazuje podatke.

## Trajnost i zaustavljanje

Aplikaciju zaustavite s Ctrl+C. `docker compose down` uklanja kontejnere,
ali čuva podatke u volumenu projekta `clickstream-java`. Ponovno pokretanje
koristi istu bazu. `docker compose down --volumes` briše podatke.
Promjena lozinke u `.env` ne mijenja lozinku u već inicijaliziranoj bazi.

Projekt je lokalni primjer za učenje, bez autentikacije i prijenosa podataka
iz starog projekta. Samo PostgreSQL port objavljen je na lokalnoj adresi.
