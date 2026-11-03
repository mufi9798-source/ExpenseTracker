
package com.expensetracker;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/transactions")
public class TransactionServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // Replace these with your Oracle database details.
  private static final String URL =
        System.getenv("ORACLE_DB_URL");

private static final String USER =
        System.getenv("ORACLE_DB_USER");

private static final String PASSWORD =
        System.getenv("ORACLE_DB_PASSWORD");

    private final Gson gson = new Gson();

    private Connection getConnection() throws SQLException {

    if (URL == null || USER == null || PASSWORD == null) {
        throw new SQLException("Database configuration missing.");
    }

    try {
        Class.forName("oracle.jdbc.OracleDriver");
    } catch (ClassNotFoundException e) {
        throw new SQLException("Oracle JDBC driver not found.", e);
    }

    return DriverManager.getConnection(URL, USER, PASSWORD);
}
    }

    // Send JSON responses to the browser.
    private void sendJson(
            HttpServletResponse response,
            int status,
            Object data) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(gson.toJson(data));
    }

    // GET: Display all transactions.
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        List<Transaction> transactions = new ArrayList<>();

        String sql = """
                SELECT id, transaction_type, category,
                       amount, description,
                       TO_CHAR(transaction_date, 'YYYY-MM-DD') AS transaction_date
                FROM transactions
                ORDER BY transaction_date DESC, id DESC
                """;

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Transaction t = new Transaction();

                t.id = rs.getLong("id");
                t.type = rs.getString("transaction_type");
                t.category = rs.getString("category");
                t.amount = rs.getBigDecimal("amount");
                t.description = rs.getString("description");
                t.date = rs.getString("transaction_date");

                transactions.add(t);
            }

            sendJson(response, HttpServletResponse.SC_OK, transactions);

        } catch (SQLException e) {
            getServletContext().log("Unable to load transactions", e);
            sendJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    new ErrorMessage("Unable to load transactions. Check database settings."));
        }
    }

    // POST: Add a new transaction.
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        try {
            Transaction t = gson.fromJson(
                    request.getReader(), Transaction.class);

            if (t == null || t.type == null || t.category == null
                    || t.amount == null || t.date == null) {
                sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        new ErrorMessage("Please fill in all required fields."));
                return;
            }

            t.type = t.type.trim().toUpperCase();
            t.category = t.category.trim();
            t.date = t.date.trim();

            if (!t.type.equals("INCOME") && !t.type.equals("EXPENSE")) {
                sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        new ErrorMessage("Type must be INCOME or EXPENSE."));
                return;
            }

            if (t.category.isEmpty() || t.category.length() > 50) {
                sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        new ErrorMessage("Category must contain 1 to 50 characters."));
                return;
            }

            if (t.amount.compareTo(BigDecimal.ZERO) <= 0
                    || t.amount.scale() > 2
                    || t.amount.precision() > 10) {
                sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        new ErrorMessage("Enter a positive amount with up to 2 decimal places."));
                return;
            }

            if (!t.date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        new ErrorMessage("Date must use YYYY-MM-DD format."));
                return;
            }

            Date sqlDate;
            try {
                sqlDate = Date.valueOf(t.date);
            } catch (IllegalArgumentException e) {
                sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        new ErrorMessage("Enter a valid date."));
                return;
            }

            if (t.description != null && t.description.length() > 200) {
                sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        new ErrorMessage("Description cannot exceed 200 characters."));
                return;
            }

            String sql = """
                    INSERT INTO transactions
                    (transaction_type, category, amount,
                     description, transaction_date)
                    VALUES (?, ?, ?, ?, ?)
                    """;

            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setString(1, t.type);
                ps.setString(2, t.category);
                ps.setBigDecimal(3, t.amount);
                ps.setString(4, t.description);
                ps.setDate(5, sqlDate);

                ps.executeUpdate();
            }

            sendJson(response, HttpServletResponse.SC_CREATED,
                    new Message("Transaction added successfully."));

        } catch (SQLException e) {
            getServletContext().log("Unable to add transaction", e);
            sendJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    new ErrorMessage("Unable to add transaction. Check database settings."));
        } catch (com.google.gson.JsonParseException e) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    new ErrorMessage("Invalid transaction data."));
        }
    }

    // DELETE: Remove a transaction using its ID.
    @Override
    protected void doDelete(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        String idValue = request.getParameter("id");

        if (idValue == null || !idValue.matches("\\d+")) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    new ErrorMessage("A valid transaction ID is required."));
            return;
        }

        try {
            long id = Long.parseLong(idValue);

            if (id <= 0) {
                sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        new ErrorMessage("Transaction ID must be positive."));
                return;
            }

            String sql = "DELETE FROM transactions WHERE id = ?";

            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setLong(1, id);
                int rows = ps.executeUpdate();

                if (rows == 0) {
                    sendJson(response, HttpServletResponse.SC_NOT_FOUND,
                            new ErrorMessage("Transaction not found."));
                    return;
                }
            }

            sendJson(response, HttpServletResponse.SC_OK,
                    new Message("Transaction deleted successfully."));

        } catch (NumberFormatException e) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    new ErrorMessage("Invalid transaction ID."));
        } catch (SQLException e) {
            getServletContext().log("Unable to delete transaction", e);
            sendJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    new ErrorMessage("Unable to delete transaction. Check database settings."));
        }
    }

    // Transaction data structure.
    private static class Transaction {
        long id;
        String type;
        String category;
        BigDecimal amount;
        String description;
        String date;
    }

    private static class Message {
        String message;

        Message(String message) {
            this.message = message;
        }
    }

    private static class ErrorMessage {
        String error;

        ErrorMessage(String error) {
            this.error = error;
        }
    }
}
