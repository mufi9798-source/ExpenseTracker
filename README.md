# 💰 Expense Tracker

A lightweight personal finance web app for recording income and expenses, built with **Java Servlets**, **Oracle Database** and a **vanilla JavaScript** front end. Amounts are shown in Indian Rupees (₹).

<!-- Add a screenshot: save it as docs/screenshot.png and uncomment the line below -->
<!-- ![Expense Tracker dashboard](docs/screenshot.png) -->

## Features

- **Dashboard summary** with total balance, total income and total expenses, updated after every change
- **Add transactions** with type (income or expense), category, amount, date and an optional description
- **Transaction history** sorted newest first, with a record count
- **Delete transactions** with a confirmation prompt
- **Server-side validation** of type, category length, amount (positive, max 2 decimals), date format and description length
- **Parameterised SQL** (`PreparedStatement`) throughout, and text rendered with `textContent` in the browser to avoid injection and XSS
- **Responsive, accessible UI** with ARIA live status messages
- **No credentials in code**: database settings come from environment variables

## Tech stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Jakarta Servlet 6.0 (`@WebServlet`) |
| Database | Oracle Database via JDBC (`ojdbc11`) |
| JSON | Gson 2.11 |
| Frontend | HTML5, CSS3, vanilla JavaScript (Fetch API) |
| Build | Maven (WAR packaging) |
| Server | Any Jakarta EE 10 servlet container, such as Apache Tomcat 10.1+ |

## Project structure

```
expensetracker/
├── pom.xml
└── src/main/
    ├── java/com/expensetracker/
    │   └── TransactionServlet.java    # REST-style JSON API (GET, POST, DELETE)
    └── webapp/
        ├── index.html                 # Single-page UI
        ├── css/style.css
        └── js/app.js                  # Fetch calls and DOM rendering
```

## Getting started

### Prerequisites

- JDK 17 or later
- Maven 3.8+
- Apache Tomcat 10.1+ (Jakarta namespace; Tomcat 9 will not work)
- An Oracle Database instance you can connect to

### 1. Create the table

The repository doesn't include a schema, so run something like this (adjust to your Oracle version):

```sql
CREATE TABLE transactions (
    id               NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_type VARCHAR2(10)  NOT NULL CHECK (transaction_type IN ('INCOME', 'EXPENSE')),
    category         VARCHAR2(50)  NOT NULL,
    amount           NUMBER(10,2)  NOT NULL CHECK (amount > 0),
    description      VARCHAR2(200),
    transaction_date DATE          NOT NULL
);
```

### 2. Configure the database connection

Set these environment variables before starting Tomcat:

| Variable | Example |
|----------|---------|
| `ORACLE_DB_URL` | `jdbc:oracle:thin:@//localhost:1521/XEPDB1` |
| `ORACLE_DB_USER` | `expense_user` |
| `ORACLE_DB_PASSWORD` | `your_password` |

```bash
export ORACLE_DB_URL="jdbc:oracle:thin:@//localhost:1521/XEPDB1"
export ORACLE_DB_USER="expense_user"
export ORACLE_DB_PASSWORD="your_password"
```

### 3. Build and deploy

```bash
git clone https://github.com/mufi9798-source/expensetracker.git
cd expensetracker
mvn clean package
cp target/ExpenseTracker.war $CATALINA_HOME/webapps/
$CATALINA_HOME/bin/startup.sh
```

Open **http://localhost:8080/ExpenseTracker/** in your browser.

## API reference

Base path: `/ExpenseTracker/api/transactions`

| Method | Endpoint | Description | Success |
|--------|----------|-------------|---------|
| `GET` | `/api/transactions` | List all transactions, newest first | `200` + JSON array |
| `POST` | `/api/transactions` | Add a transaction (JSON body) | `201` |
| `DELETE` | `/api/transactions?id={id}` | Delete a transaction by ID | `200` |

**Example `POST` body**

```json
{
  "type": "EXPENSE",
  "category": "Food",
  "amount": 249.50,
  "date": "2026-11-04",
  "description": "Lunch"
}
```

Validation failures return `400` with `{ "error": "..." }`; missing records return `404`; database problems return `500`.

## Roadmap

- [ ] User accounts and per-user data
- [ ] Edit existing transactions
- [ ] Filter and search by date range, category or type
- [ ] Monthly charts and category breakdowns
- [ ] CSV export
- [ ] Docker Compose setup with an Oracle XE container
- [ ] Unit and integration tests

## Contributing

Issues and pull requests are welcome. For larger changes, please open an issue first to discuss what you'd like to change.

## Author

Built by [mufi9798-source](https://github.com/mufi9798-source).
