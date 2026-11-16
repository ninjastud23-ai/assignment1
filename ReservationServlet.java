package edu.ajman.int322;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

@WebServlet("/ReservationServlet")
public class ReservationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final BigDecimal FACULTY_DISCOUNT = new BigDecimal("0.15");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        // Decode input before reading request parameters.
        req.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType("text/html;charset=UTF-8");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String userName;
        String userRole;
        String itemCategory;
        int quantity;
        int bookingDays;
        BigDecimal dailyRate;
        try {
            String rawName = req.getParameter("userName");
            userName = rawName == null ? "" : rawName.trim().toUpperCase(Locale.ROOT);
            if (userName.isEmpty() || userName.length() > 100) {
                throw new IllegalArgumentException("Name must contain 1 to 100 characters.");
            }
            String rawRole = req.getParameter("userRole");
            userRole = rawRole == null ? "" : rawRole.trim().toUpperCase(Locale.ROOT);
            if (!userRole.equals("STUDENT") && !userRole.equals("FACULTY")) {
                throw new IllegalArgumentException("Role must be STUDENT or FACULTY.");
            }
            String rawCategory = req.getParameter("itemCategory");
            itemCategory = rawCategory == null ? "" : rawCategory.trim().toUpperCase(Locale.ROOT);
            switch (itemCategory) {
                case "LAPTOP": dailyRate = new BigDecimal("50.00"); break;
                case "PROJECTOR": dailyRate = new BigDecimal("80.00"); break;
                case "POD": dailyRate = new BigDecimal("100.00"); break;
                default: throw new IllegalArgumentException("Choose LAPTOP, PROJECTOR, or POD.");
            }
            quantity = parsePositiveInt(req.getParameter("quantity"), "Quantity", 1000);
            bookingDays = parsePositiveInt(req.getParameter("bookingDays"), "Booking days", 365);
        } catch (IllegalArgumentException ex) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            showMessage(res, "Invalid Reservation", ex.getMessage());
            return;
        }

        BigDecimal subtotal = dailyRate.multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(bookingDays)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = userRole.equals("FACULTY")
                ? subtotal.multiply(FACULTY_DISCOUNT).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal netTotal = subtotal.subtract(discount);

        String dbUrl = setting("CAMPUS_DB_URL", "jdbc:mysql://localhost:3306/campus_facility_db?useSSL=false&serverTimezone=UTC");
        String dbUser = setting("CAMPUS_DB_USER", "root");
        String dbPassword = setting("CAMPUS_DB_PASSWORD", "");
        String sql = "INSERT INTO reservations "
                + "(user_name, user_role, item_category, quantity, booking_days, daily_rate, subtotal, net_total) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        long reservationId;
        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, userName);
            statement.setString(2, userRole);
            statement.setString(3, itemCategory);
            statement.setInt(4, quantity);
            statement.setInt(5, bookingDays);
            statement.setBigDecimal(6, dailyRate);
            statement.setBigDecimal(7, subtotal);
            statement.setBigDecimal(8, netTotal);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                reservationId = keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (SQLException ex) {
            // Log internal details for the server owner; do not leak credentials or SQL to clients.
            getServletContext().log("Failed to save reservation", ex);
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            showMessage(res, "Reservation Failed", "Could not save the reservation. Please contact the administrator.");
            return;
        }

        try (PrintWriter out = res.getWriter()) {
            startHtml(out, "Booking Receipt");
            out.println("<h1>Booking Confirmed</h1>");
            out.println("<p>Your reservation was successfully saved in MySQL.</p>");
            out.println("<table>");
            row(out, "Reservation ID", reservationId > 0 ? Long.toString(reservationId) : "Saved");
            row(out, "Name", userName);
            row(out, "Role", userRole);
            row(out, "Item Category", itemCategory);
            row(out, "Quantity", Integer.toString(quantity));
            row(out, "Booking Days", Integer.toString(bookingDays));
            row(out, "Daily Rate", money(dailyRate));
            row(out, "Subtotal", money(subtotal));
            row(out, "Discount", userRole.equals("FACULTY") ? "15%" : "0%");
            row(out, "Discount Amount", money(discount));
            row(out, "Net Total", money(netTotal));
            out.println("</table><p><a href='booking_form.html'>Make another booking</a></p>");
            endHtml(out);
        }
    }

    private static int parsePositiveInt(String input, String field, int maximum) {
        try {
            if (input == null || !input.trim().matches("[0-9]+")) {
                throw new NumberFormatException();
            }
            int parsed = Integer.parseInt(input.trim());
            if (parsed < 1 || parsed > maximum) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a whole number from 1 to " + maximum + ".");
        }
    }

    private static String setting(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " AED";
    }

    private static void row(PrintWriter out, String name, String value) {
        out.println("<tr><th>" + escapeHtml(name) + "</th><td>" + escapeHtml(value) + "</td></tr>");
    }

    private static void startHtml(PrintWriter out, String title) {
        out.println("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + escapeHtml(title) + "</title><style>"
                + "body{font-family:Arial,sans-serif;background:#f1f5f9;color:#1e293b;padding:30px 15px}"
                + "main{max-width:650px;margin:auto;background:white;padding:30px;border-radius:12px}"
                + "h1{color:#164e80}table{width:100%;border-collapse:collapse}th,td{padding:12px;text-align:left;border-bottom:1px solid #cbd5e1}"
                + "th{width:45%}a{color:#17609c}</style></head><body><main>");
    }

    private static void endHtml(PrintWriter out) {
        out.println("</main></body></html>");
    }

    private static void showMessage(HttpServletResponse res, String title, String message) throws IOException {
        try (PrintWriter out = res.getWriter()) {
            startHtml(out, title);
            out.println("<h1>" + escapeHtml(title) + "</h1>");
            out.println("<p>" + escapeHtml(message) + "</p>");
            out.println("<p><a href='booking_form.html'>Back to the booking form</a></p>");
            endHtml(out);
        }
    }
}
