# FinCore — Banking Transaction Management System

FinCore is a small, full-stack banking **demonstration** project. A customer can register, sign in, open accounts, record simulated deposits and withdrawals, transfer demo funds to another FinCore account, and review a transaction ledger. A responsive web dashboard shows account balances, recent transactions, monthly cash flow, and six months of account activity, formatted in Indian rupees (INR).

The application is designed to demonstrate backend engineering: REST APIs, authentication, SQL access, transaction safety, indexing, migrations, and unit tests. It does not connect to a real bank or move real money.

## Contents

- [What it does](#what-it-does)
- [Tools and technology choices](#tools-and-technology-choices)
- [How the application works](#how-the-application-works)
- [Project files](#project-files)
- [Run on Windows from scratch](#run-on-windows-from-scratch)
- [Configuration reference](#configuration-reference)
- [Use the dashboard](#use-the-dashboard)
- [Demonstrate it during evaluation](#demonstrate-it-during-evaluation)
- [REST API guide](#rest-api-guide)
- [View the stored MySQL data](#view-the-stored-mysql-data)
- [Database design and query performance](#database-design-and-query-performance)
- [Run tests and package the app](#run-tests-and-package-the-app)
- [Skills demonstrated and why this stack](#skills-demonstrated-and-why-this-stack)
- [Troubleshooting](#troubleshooting)
- [Limitations and safe use](#limitations-and-safe-use)

## What it does

- Register and sign in with an email and password.
- Create a checking account at registration and add savings or checking accounts later.
- See total balance, account balances, recent activity, month-to-date inflows/outflows, and a six-month cash-flow chart.
- Record deposits and withdrawals in Indian rupees.
- Transfer funds between active FinCore accounts.
- Browse newest-first transaction history for each owned account.
- Reject invalid amounts, unauthorized account access, inactive accounts, and withdrawals/transfers that exceed the available balance.
- Store customer credentials, accounts, balances, and ledger entries in MySQL.

## Tools and technology choices

| Tool or technology | What it does here | Why it is used |
| --- | --- | --- |
| Java 17+ | Implements the application and banking rules | Strong typing, classes, records, and the Java ecosystem make business logic easy to structure and test. |
| Spring Boot 3.5 | Starts the web application and wires application components | Provides embedded Tomcat, configuration, dependency injection, and a straightforward way to build REST APIs. |
| Spring Web / REST | Exposes `/api/...` HTTP routes and serves the dashboard | Lets the browser and other clients use the same backend through JSON requests. |
| Spring Security | Protects banking routes and validates bearer tokens | Keeps customer-only endpoints behind authentication. |
| JWT with HS256 | Carries the signed customer identity between requests | Allows stateless API authentication; the backend verifies the signature on each protected request. |
| BCrypt | Hashes passwords before they are stored | The database stores a password hash rather than the original password. |
| Spring JDBC (`JdbcTemplate`) | Executes parameterized SQL and maps query results | Keeps the SQL visible and allows direct control over joins, row locks, and indexes. This project intentionally does not use an ORM. |
| MySQL 8.4 | Persists customers, accounts, balances, and transactions | Provides a relational store, constraints, indexes, and transactional updates. |
| MySQL Connector/J | Connects the Java app to MySQL | Implements the JDBC driver used by Spring JDBC. |
| Flyway | Applies versioned SQL migrations at startup | Creates the schema consistently and tracks upgrades so existing databases can advance safely. |
| Maven Wrapper | Downloads/runs the pinned Maven toolchain | You can build without installing Maven globally. |
| Docker Compose | Runs a local MySQL container and stores its data in a volume | Gives the project a repeatable local database setup. Docker Desktop must have its engine running. |
| HTML, CSS, JavaScript | Implements the responsive browser dashboard | Keeps the demo UI lightweight and served by the same Spring Boot app; no separate frontend server is required. |
| JUnit 5 and Mockito | Test business rules and token generation | Verifies amount checks, insufficient-fund behavior, ledger updates, and JWT claims without needing a live database for each unit test. |

### Why keep the stack focused?

Each technology has a clear job in this project. A separate React app, ORM, cache, message queue, or payment gateway would add setup and maintenance without helping demonstrate the requested core banking flows. JDBC is used instead of JPA so the SQL and transaction locking are easy to inspect during an evaluation. Docker is only used for the local MySQL server; the Spring Boot application runs directly on Java.

## How the application works

```text
Browser dashboard
  ├─ public signup/login ──> AuthController ──> AuthService
  │                                          ├─ BCrypt password hash/check
  │                                          └─ HS256 JWT issued to browser
  └─ authenticated API calls ──> Spring Security validates JWT
                                  └─ BankingController ──> BankingService
                                                            └─ JDBC SQL ──> MySQL
```

1. Registration validates the submitted name, email, and password. The backend normalizes the email, BCrypt-hashes the password, creates a customer and opens a checking account.
2. The backend signs a JWT with HS256 and returns it to the browser. The browser keeps the session token locally and sends it in the `Authorization: Bearer ...` header for protected requests.
3. Spring Security verifies the JWT. Banking endpoints read the customer ID from its subject and use that ID when selecting or changing accounts.
4. The service validates each amount and account. A deposit or withdrawal locks the account row, updates the balance, and inserts a ledger row in one database transaction.
5. A transfer locks both accounts in ascending account-ID order, verifies ownership, status, currency, and sufficient funds, updates both balances, and inserts matching debit/credit ledger rows with one reference. If an operation fails, the database transaction rolls back.
6. The dashboard requests live data from `/api/dashboard`. MySQL aggregates the current month's cash flow and monthly totals from ledger rows. Transfers between a customer's own accounts are excluded from income/expense totals; transfers between different customers are included.

## Project files

```text
fincore/
├── pom.xml                         Maven dependencies and Java version
├── mvnw.cmd, mvnw                  Maven Wrapper launchers
├── .mvn/wrapper/                   Wrapper version and launcher
├── .env.example                    Local application configuration template
├── docker-compose.yml              MySQL container and persistent volume
├── src/main/java/com/fincore/
│   ├── FinCoreApplication.java     Spring Boot entry point
│   ├── auth/                       Signup, login, and JWT session creation
│   ├── banking/                    Account and transaction API/business rules
│   ├── config/SecurityConfig.java  JWT, password hashing, and route protection
│   └── api/                        Consistent API error responses
├── src/main/resources/
│   ├── application.yml             App port, MySQL, Flyway, and JWT settings
│   ├── db/migration/               Versioned MySQL schema migrations
│   └── static/                     Dashboard HTML, CSS, and JavaScript
├── src/test/java/                  JUnit and Mockito tests
└── docs/query-plan.sql             EXPLAIN ANALYZE examples
```

## Run on Windows from scratch

### 1. Install/check prerequisites

- Install **Java 17 or newer**. Verify in PowerShell or Command Prompt:

  ```cmd
  java -version
  ```

- Set `JAVA_HOME` to your JDK folder if it is not already set. In **PowerShell**:

  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-23"
  ```

  In **Command Prompt** (not PowerShell):

  ```cmd
  set "JAVA_HOME=C:\Program Files\Java\jdk-23"
  ```

  Use the folder that exists on your machine; the JDK version may differ. `JAVA_HOME` should point to the JDK folder, not its `bin` directory.

- Install and start **Docker Desktop**. Wait for it to report that the engine is running. Check with:

  ```cmd
  docker info
  ```

  The output should contain both `Client` and `Server` sections. A missing Docker server means Docker Desktop's engine is not ready yet.

### 2. Create the local environment file

From the FinCore folder:

```powershell
Copy-Item .env.example .env
notepad .env
```

Choose a private random `JWT_SECRET` of at least 32 characters. The example credentials are only for local development. The `.env` file is ignored by Git.

### 3. Start MySQL

```powershell
docker compose up -d mysql
docker compose ps
```

Wait until the `mysql` service shows `healthy`. Docker publishes its MySQL port as **3307 on your computer** and uses **3306 inside the container**. The named Docker volume retains your data between container restarts.

### 4. Start FinCore

In the same terminal, from the FinCore folder:

```powershell
.\mvnw.cmd spring-boot:run
```

On Command Prompt, use:

```cmd
mvnw.cmd spring-boot:run
```

On first use, the wrapper downloads Maven and project dependencies. Spring Boot connects to MySQL and Flyway applies any pending migrations. Wait for a log message saying `Started FinCoreApplication`.

Open [http://localhost:8080](http://localhost:8080) in your browser. Keep the app terminal open while using the site. Press **Ctrl+C** in that terminal to stop the app.

### If port 8080 is already taken

Run the app on another port. In PowerShell:

```powershell
$env:PORT = "8082"
.\mvnw.cmd spring-boot:run
```

Then open [http://localhost:8082](http://localhost:8082). In Command Prompt, set the port with `set PORT=8082` before running `mvnw.cmd spring-boot:run`.

Stop the database container (data is retained) with:

```powershell
docker compose down
```

**Avoid `docker compose down -v` unless you intentionally want to delete the local database volume and all data in it.**

## Configuration reference

Spring Boot reads operating-system environment variables and the optional project `.env` file through `spring.config.import` in `application.yml`. Operating-system environment variables take precedence over `.env` values.

| Setting | Default/example | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port for the Spring Boot app. Use `8082` if 8080 is busy. |
| `DB_URL` | `jdbc:mysql://localhost:3307/fincore?...` | JDBC URL. Port 3307 is the Docker host port from `docker-compose.yml`. |
| `DB_USERNAME` | `fincore` | MySQL application user. |
| `DB_PASSWORD` | `fincore` | MySQL application password for local development. |
| `JWT_SECRET` | Replace the sample value | HS256 signing key; must be at least 32 bytes. Use a unique secret outside a local demo. |
| `JWT_EXPIRATION` | `PT8H` | JWT lifetime in ISO-8601 duration format; `PT8H` means eight hours. |
| `MYSQL_DATABASE` | `fincore` in Compose | Database created by the MySQL image on first initialization. |
| `MYSQL_USER` | `fincore` in Compose | Local database user created by the MySQL image. |
| `MYSQL_PASSWORD` | `fincore` in Compose | Local user's password. |
| `MYSQL_ROOT_PASSWORD` | `localroot` in Compose | MySQL container root password for local administration/health checks. |

The MySQL values in `docker-compose.yml` are local demo defaults. If you change the database/user/password there, update `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in `.env` to match. Existing Docker volumes keep the credentials created on the first initialization; changing Compose variables later does not automatically change an already-initialized database user's password.

## Use the dashboard

1. Select **Create an account** and register with a name, email, and password of at least eight characters.
2. You land on the dashboard with an empty checking account and a generated account number.
3. Choose **Add money** to record a simulated deposit. Amounts are entered as rupees, for example `50000` means ₹50,000.
4. Use **Withdraw** to record a cash withdrawal. The backend rejects a withdrawal larger than the available balance.
5. Choose **New account** to add checking or savings. The account dialog submits directly to the backend.
6. Choose **Transfer** and enter a recipient's FinCore account number. For a self-transfer demonstration, use the account number of another account you own. For a customer-to-customer transfer, register a second customer in another browser/incognito session and use their account number.
7. Open **Transactions** to inspect each account's history and balance-after values. Return to **Overview** for the monthly ledger totals and cash-flow chart.

## Demonstrate it during evaluation

### A 3–5 minute walkthrough

1. **Introduce the project:** “FinCore is a Spring Boot and MySQL banking transaction demo. It exposes authenticated REST APIs and a browser dashboard for INR accounts and transaction history.”
2. **Register:** Create a customer and point out the generated checking account. Explain that a JWT is signed automatically after registration; no separate JWT account/service is required.
3. **Deposit:** Add a simulated ₹50,000 deposit. Show that the account balance and ledger history update from the backend.
4. **Withdraw:** Withdraw ₹5,000. Show the new balance and monthly money-out value.
5. **Show validation:** Attempt to withdraw more than the balance. Explain the rejection and that the balance remains unchanged.
6. **Transfer:** Open a second account or use a second customer, transfer an amount, and show the paired debit/credit entries and shared transaction reference in MySQL.
7. **Show the database:** In Workbench or the MySQL CLI, run the account and transaction queries in [View the stored MySQL data](#view-the-stored-mysql-data).
8. **Close with engineering choices:** Mention row locking and `@Transactional` for safe balance updates, indexes for account/transaction lookups, and JUnit/Mockito tests for core rules and JWT claims.

### Short presentation script

> “FinCore is a Java Spring Boot banking transaction demo backed by MySQL. A customer signs in and receives a signed JWT, which the API uses to identify the owner of each account. Banking operations use JDBC and database transactions; transfers lock both accounts and write both ledger entries atomically so a partial transfer cannot be committed. The dashboard reads actual account and ledger data and formats it as INR. I use Flyway for schema changes and JUnit with Mockito for business-rule tests. Deposits and transfers are simulated in this project and do not move real money.”

### Questions evaluators may ask

- **Why not use JPA?** JDBC makes the SQL, joins, aggregates, and `FOR UPDATE` row locks explicit for this project.
- **How are transfers kept consistent?** The backend locks both rows in ID order, validates funds and status, then updates balances and writes both ledger rows in the same `@Transactional` method.
- **How do you prevent a user from reading another user's account?** The customer ID comes from the verified JWT subject and is included in account ownership checks; the API does not trust a customer ID submitted by the browser.
- **How are passwords protected?** BCrypt hashes the password before storage; login compares the entered value against that hash.
- **How are dashboard figures calculated?** SQL aggregates deposits, withdrawals, and external customer transfers from ledger records. The chart groups the same transaction types by month. Self-transfers are omitted from customer income/expense totals.
- **Is this connected to a real bank?** No. Deposits are simulated ledger entries and transfers only move balances between FinCore demo accounts.

## REST API guide

Signup and login are public. All other `/api` routes need this header:

```http
Authorization: Bearer <token returned from signup or login>
```

### Endpoints

| Method | Path | Authentication | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Public | Create a customer, initial checking account, and session token. |
| `POST` | `/api/auth/login` | Public | Validate credentials and return a new token. |
| `GET` | `/api/dashboard` | Required | Return total balance, account list, six latest transactions, month-to-date totals, and six-month cash flow. |
| `GET` | `/api/accounts` | Required | List the signed-in customer's accounts. |
| `POST` | `/api/accounts` | Required | Create a checking or savings account. |
| `GET` | `/api/accounts/{id}/transactions?page=0&size=20` | Required | Return newest-first history for an account owned by the customer. Page size is 1–100. |
| `POST` | `/api/accounts/{id}/deposit` | Required | Record a simulated deposit. |
| `POST` | `/api/accounts/{id}/withdraw` | Required | Record a withdrawal if the balance is sufficient. |
| `POST` | `/api/transfers` | Required | Transfer funds to another active FinCore account. |

### Request body examples

Register:

```json
{"name":"Asha Rao","email":"asha@example.test","password":"DemoPass123!"}
```

Login:

```json
{"email":"asha@example.test","password":"DemoPass123!"}
```

Create account:

```json
{"accountType":"SAVINGS"}
```

Deposit or withdraw:

```json
{"amount":2500.00,"description":"Monthly savings"}
```

Transfer:

```json
{"fromAccountId":1,"toAccountNumber":"FC1234567890","amount":250.00,"description":"Lunch"}
```

### Try signup and dashboard from PowerShell

With the server running, open a **second** PowerShell window and run:

```powershell
$base = "http://localhost:8080/api"
$signup = @{ name = "Asha Demo"; email = "asha-demo@example.test"; password = "DemoPass123!" } | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType "application/json" -Body $signup
$headers = @{ Authorization = "Bearer $($session.token)" }
Invoke-RestMethod -Uri "$base/dashboard" -Headers $headers | ConvertTo-Json -Depth 6
```

If you selected port 8082, change `$base` to `http://localhost:8082/api`. Choose an unused email for each registration attempt. The response contains the new customer's account number; use that number as the transfer recipient in the UI or API.

## View the stored MySQL data

### Option A: MySQL Workbench

Create a Standard TCP/IP connection:

| Field | Value |
| --- | --- |
| Connection name | `FinCore local` |
| Hostname | `127.0.0.1` |
| Port | `3307` |
| Username | `fincore` |
| Password | `fincore` (local Compose default) |

Click **Test Connection**, then **OK** to save the connection. **OK returns to Workbench's home page; it does not open the query editor.** Double-click the saved `FinCore local` tile to open the SQL editor. If Workbench closes unexpectedly, use the CLI below; the app/database do not depend on Workbench.

### Option B: MySQL command-line client inside Docker

From the FinCore folder:

```powershell
docker compose exec mysql mysql -u fincore -p fincore
```

At the password prompt, enter `fincore`. Then run these read-only queries:

```sql
SELECT id, full_name, email, created_at
FROM customers
ORDER BY id DESC;

SELECT c.full_name, a.account_number, a.account_type,
       a.balance, a.currency, a.status
FROM accounts a
JOIN customers c ON c.id = a.customer_id
ORDER BY a.id DESC;

SELECT c.full_name, a.account_number, t.transaction_type,
       t.amount, t.balance_after, t.description, t.reference, t.created_at
FROM transactions t
JOIN accounts a ON a.id = t.account_id
JOIN customers c ON c.id = a.customer_id
ORDER BY t.created_at DESC, t.id DESC
LIMIT 25;
```

Exit the SQL client with `exit`. Do not query or display `customers.password_hash` during a demo.

### Query plan examples

Run [docs/query-plan.sql](docs/query-plan.sql) on a populated database to see `EXPLAIN ANALYZE` output for account history and account listing queries. These statements show the database's actual execution plan; no benchmark improvement number is claimed by this demo.

## Database design and query performance

- `customers` stores the normalized email and BCrypt password hash. Email has a unique constraint.
- `accounts` stores the owner, account number, account type, currency, status, and current `DECIMAL(19,2)` balance. Account numbers are unique; a database check prevents negative balances.
- `transactions` is the ledger. Each row records a positive amount, type, account, balance after the operation, description, timestamp, and reference. Transfers create a debit row and a credit row with one shared reference.
- `idx_accounts_customer_created` supports a customer's account list.
- `idx_transactions_account_created` supports newest-first transaction history for an account.
- `idx_transactions_counterparty` supports counterparty lookup.
- The customer/account and account/transaction relationships use foreign keys.
- V1 creates the initial schema. V2 changes the default currency and changes existing demo account currency labels from USD to INR. It retains the numeric amount; it does **not** calculate an exchange rate. V1 is kept unchanged because databases may already have applied it.

## Run tests and package the app

From the project folder, with `JAVA_HOME` set:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

The tests cover HS256 JWT signing/claims, positive amount validation, insufficient funds, and deposit balance/ledger updates. The executable jar is created at `target/fincore-1.0.0.jar`.

To start the packaged jar (keep MySQL running):

```powershell
java -jar target\fincore-1.0.0.jar
```

## Skills demonstrated and why this stack

- **Java and object-oriented design:** Application entry point, controllers, services, request records, and constructor-injected dependencies.
- **REST API design:** HTTP methods, JSON request/response bodies, input validation, authentication, and centralized error responses.
- **Authentication/security basics:** BCrypt password hashing, JWT signing/verification with HS256, protected routes, and ownership checks.
- **JDBC and SQL:** Parameterized SQL, joins, aggregate queries, row locking, and explicit balance/ledger writes.
- **Relational database design:** Foreign keys, unique/check constraints, precise decimal money values, and useful indexes.
- **Transaction management:** Atomic writes and consistent row-lock ordering to prevent partial or overspending transfers.
- **Database migration/versioning:** Flyway schema upgrades that work against a database that has already applied an earlier migration.
- **Testing:** JUnit/Mockito unit tests for security token contents and banking invariants.
- **Frontend fundamentals:** Responsive layouts, accessible form labels, browser-side validation, API requests, and INR formatting.
- **Local development and debugging:** Maven Wrapper, Docker Compose, logs, API smoke checks, and MySQL Workbench/CLI inspection.

The project keeps this skill set limited to what the requirements need. It avoids an additional frontend framework and ORM so the project is easier to start and the core SQL/transaction behavior stays visible. External banking integrations, payment processors, production-grade operations, and multi-currency conversion are outside this demo's scope.

## Troubleshooting

### Docker says it cannot connect to `dockerDesktopLinuxEngine`

The Docker CLI is installed, but Docker Desktop's engine is not running. Open Docker Desktop, wait for **Engine running**, then verify with `docker info` (check that a `Server` section appears). Retry `docker compose up -d mysql` afterward.

### MySQL connection is refused on port 3307

Check `docker compose ps` and wait for the MySQL service to show `healthy`. This app uses host port 3307 because port 3306 may already be occupied by a locally installed MySQL service.

### Spring Boot reports port 8080 is already in use

Run with `$env:PORT = "8082"` in PowerShell, then start `.\mvnw.cmd spring-boot:run`, and use `http://localhost:8082`.

### The browser says “Internal Server Error” during signup/login

Check the terminal running Spring Boot and find the first `ERROR` line and its deepest `Caused by` message. The app must be restarted after code changes. The JWT code explicitly signs with HS256; no separate JWT account or third-party authentication setup is required.

### MySQL Workbench closes when I click OK

OK normally saves the connection and returns to Workbench's home screen. Double-click the saved connection tile to open the SQL editor. If the whole application crashes, use the Docker MySQL CLI instructions above. Workbench logs on Windows are under `%APPDATA%\MySQL\Workbench\log`; never share passwords or tokens from a log.

### I changed `.env`, but the database still rejects the credentials

The MySQL Docker volume stores its initial database user and password. Compose environment changes do not reset credentials in an already-initialized volume. Keep the existing local credentials or deliberately recreate the database only if you are willing to lose its data; `docker compose down -v` deletes the volume.

## Limitations and safe use

FinCore is for education and portfolio demonstration, not for real financial use. Deposits are simulated; transfers only update FinCore's local MySQL database. There is no bank/payment-rail integration, FX conversion, fraud monitoring, regulatory workflow, distributed audit service, rate-limiting policy, or production secrets management. Use HTTPS, strong managed secrets, security review, operational monitoring, and appropriate legal/compliance work before building anything that handles real customer funds.
