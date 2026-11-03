
"use strict";

const API_URL = "api/transactions";

const form = document.getElementById("transactionForm");
const typeInput = document.getElementById("type");
const categoryInput = document.getElementById("category");
const amountInput = document.getElementById("amount");
const dateInput = document.getElementById("date");
const descriptionInput = document.getElementById("description");

const balanceElement = document.getElementById("balance");
const incomeElement = document.getElementById("income");
const expenseElement = document.getElementById("expense");
const transactionList = document.getElementById("transactionList");
const transactionCount = document.getElementById("transactionCount");
const statusMessage = document.getElementById("statusMessage");
const submitButton = document.getElementById("submitButton");

// Display today's date in the header and date input.
function initializeDates() {
    const now = new Date();

    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const day = String(now.getDate()).padStart(2, "0");
    const today = `${year}-${month}-${day}`;

    dateInput.value = today;
    document.getElementById("todayDate").textContent =
        now.toLocaleDateString("en-IN", {
            day: "numeric",
            month: "short",
            year: "numeric"
        });
}

// Format amounts as Indian Rupees.
function formatCurrency(amount) {
    return new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(Number(amount) || 0);
}

// Display success, error or information messages.
function showMessage(message, type = "info") {
    statusMessage.textContent = message;
    statusMessage.className = `status-message ${type}`;
    statusMessage.hidden = false;
}

// Hide the message.
function clearMessage() {
    statusMessage.hidden = true;
    statusMessage.textContent = "";
}

// Safely create a table cell with text.
function createCell(text, className = "") {
    const cell = document.createElement("td");
    cell.textContent = text ?? "";

    if (className) {
        cell.className = className;
    }

    return cell;
}

// Update the income, expense and balance cards.
function updateSummary(transactions) {
    let totalIncome = 0;
    let totalExpense = 0;

    transactions.forEach(transaction => {
        const amount = Number(transaction.amount) || 0;

        if (transaction.type === "INCOME") {
            totalIncome += amount;
        } else if (transaction.type === "EXPENSE") {
            totalExpense += amount;
        }
    });

    balanceElement.textContent =
        formatCurrency(totalIncome - totalExpense);

    incomeElement.textContent = formatCurrency(totalIncome);
    expenseElement.textContent = formatCurrency(totalExpense);

    transactionCount.textContent =
        `${transactions.length} ${transactions.length === 1 ? "record" : "records"}`;
}

// Display transactions in the history table.
function displayTransactions(transactions) {
    transactionList.replaceChildren();

    if (transactions.length === 0) {
        const row = document.createElement("tr");
        const cell = createCell("No transactions yet. Add your first record!");
        cell.colSpan = 4;
        cell.className = "empty-state";
        row.appendChild(cell);
        transactionList.appendChild(row);
        return;
    }

    transactions.forEach(transaction => {
        const row = document.createElement("tr");

        // Date column
        let formattedDate = transaction.date || "";
        if (formattedDate) {
            const parts = formattedDate.split("-");
            if (parts.length === 3) {
                formattedDate = `${parts[2]}-${parts[1]}-${parts[0]}`;
            }
        }

        row.appendChild(createCell(formattedDate));

        // Category and description column
        const detailsCell = document.createElement("td");

        const category = document.createElement("span");
        category.className = "transaction-category";
        category.textContent = transaction.category || "Uncategorized";
        detailsCell.appendChild(category);

        if (transaction.description) {
            const description = document.createElement("span");
            description.className = "transaction-description";
            description.textContent = transaction.description;
            detailsCell.appendChild(description);
        }

        row.appendChild(detailsCell);

        // Amount column
        const amount = Number(transaction.amount) || 0;
        const amountCell = createCell(
            `${transaction.type === "INCOME" ? "+" : "-"}${formatCurrency(amount)}`,
            transaction.type === "INCOME"
                ? "amount-income"
                : "amount-expense"
        );
        row.appendChild(amountCell);

        // Delete button column
        const actionCell = document.createElement("td");
        const deleteButton = document.createElement("button");

        deleteButton.type = "button";
        deleteButton.className = "delete-button";
        deleteButton.textContent = "Delete";
        deleteButton.setAttribute(
            "aria-label",
            `Delete transaction ${transaction.id}`
        );

        deleteButton.addEventListener("click", () => {
            deleteTransaction(transaction.id, deleteButton);
        });

        actionCell.appendChild(deleteButton);
        row.appendChild(actionCell);

        transactionList.appendChild(row);
    });
}

// Load all transactions from the Java servlet.
async function loadTransactions() {
    try {
        const response = await fetch(API_URL, {
            headers: {
                "Accept": "application/json"
            }
        });

        const data = await response.json().catch(() => null);

        if (!response.ok) {
            throw new Error(
                data?.error || "Could not load transactions."
            );
        }

        if (!Array.isArray(data)) {
            throw new Error("Unexpected response from the server.");
        }

        updateSummary(data);
        displayTransactions(data);
    } catch (error) {
        transactionList.replaceChildren();

        const row = document.createElement("tr");
        const cell = createCell(
            "Unable to load transactions. Check the server and Oracle database."
        );
        cell.colSpan = 4;
        cell.className = "empty-state";
        row.appendChild(cell);
        transactionList.appendChild(row);

        showMessage(error.message, "error");
    }
}

// Add a new transaction to Oracle.
form.addEventListener("submit", async function (event) {
    event.preventDefault();
    clearMessage();

    const amount = Number(amountInput.value);

    if (!Number.isFinite(amount) || amount <= 0) {
        showMessage("Please enter a valid amount greater than zero.", "error");
        return;
    }

    if (!categoryInput.value) {
        showMessage("Please select a category.", "error");
        return;
    }

    const transaction = {
        type: typeInput.value,
        category: categoryInput.value,
        amount: amount,
        date: dateInput.value,
        description: descriptionInput.value.trim()
    };

    submitButton.disabled = true;
    submitButton.textContent = "Saving...";

    try {
        const response = await fetch(API_URL, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json"
            },
            body: JSON.stringify(transaction)
        });

        const data = await response.json().catch(() => null);

        if (!response.ok) {
            throw new Error(
                data?.error || "Could not save the transaction."
            );
        }

        form.reset();
        initializeDates();

        showMessage("Transaction added successfully!", "success");
        await loadTransactions();
    } catch (error) {
        showMessage(
            `${error.message} Check your Oracle connection and servlet.`,
            "error"
        );
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "+ Add Transaction";
    }
});

// Delete a transaction from Oracle.
async function deleteTransaction(id, button) {
    if (!window.confirm("Are you sure you want to delete this transaction?")) {
        return;
    }

    button.disabled = true;

    try {
        const response = await fetch(
            `${API_URL}?id=${encodeURIComponent(id)}`,
            {
                method: "DELETE",
                headers: {
                    "Accept": "application/json"
                }
            }
        );

        const data = await response.json().catch(() => null);

        if (!response.ok) {
            throw new Error(
                data?.error || "Could not delete the transaction."
            );
        }

        showMessage("Transaction deleted successfully!", "success");
        await loadTransactions();
    } catch (error) {
        showMessage(error.message, "error");
        button.disabled = false;
    }
}

// Start the application.
initializeDates();
loadTransactions();
