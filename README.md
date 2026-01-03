# Integrační Middleware (Connector)

Tento projekt slouží jako integrační middleware (konektor), který zajišťuje komunikaci mezi interními systémy a externími API. Je postaven na **Java 21**, **Spring Boot** (s využitím reaktivního stacku **WebFlux**) a využívá **RabbitMQ** pro asynchronní komunikaci.

## Použité technologie

*   **Java 21**
*   **Spring Boot 3.5.8**
    *   `spring-boot-starter-webflux` (Reaktivní REST API)
    *   `spring-boot-starter-amqp` (RabbitMQ integrace)
    *   `spring-boot-starter-aop` (Aspektově orientované programování)
    *   `spring-boot-starter-security` (Zabezpečení)
    *   `spring-retry` (Opakování neúspěšných požadavků)
*   **RabbitMQ** (Message Broker)
*   **Maven** (Build tool)

## Konfigurace

Hlavní konfigurace se nachází v souboru `src/main/resources/application.yml`.

### Klíčová nastavení:

*   **Server Port**: `8080`
*   **Externí API**:
    *   Base URL: `http://localhost:9090` 
*   **Retry logika** (pro volání externích služeb):
    *   Maximální počet pokusů: 3
    *   Prodleva mezi pokusy: 1500 ms
*   **Zabezpečení**:
    *   Aplikace vyžaduje API klíč v hlavičce `X-API-KEY`.
    *   Výchozí klíč: `moje-tajne-heslo-12345`

## Požadavky

Před spuštěním se ujistěte, že máte nainstalováno:

*   Java Development Kit (JDK) 21
*   Maven
*   Běžící instanci RabbitMQ (pokud je vyžadována pro plnou funkčnost, dle závislostí)

## Instalace a spuštění

1.  **Klonování repozitáře:**
    ```bash
    git clone <url-repozitare>
    cd IntegracniMiddleware
    ```

2.  **Sestavení projektu:**
    ```bash
    mvn clean install
    ```

3.  **Spuštění aplikace:**
    ```bash
    mvn spring-boot:run
    ```

Po spuštění bude aplikace dostupná na `http://localhost:8080`.

## API a Použití

Aplikace funguje jako prostředník. Pro volání endpointů chráněných API klíčem nezapomeňte přidat hlavičku:

```http
X-API-KEY: moje-tajne-heslo-12345
```
