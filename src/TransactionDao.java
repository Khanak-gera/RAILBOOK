import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionDao {

    // All transactions — newest first
    public List<Map<String, String>> findAll(Connection con) throws SQLException {
        String sql = "SELECT tx.*, t.status AS ticket_status, t.fare AS ticket_fare, " +
                "p.name AS passenger_name, tr.train_name, tr.source, tr.destination " +
                "FROM transactions tx " +
                "JOIN tickets t ON tx.pnr = t.pnr " +
                "JOIN passengers p ON t.passenger_id = p.passenger_id " +
                "JOIN trains tr ON t.train_number = tr.train_number " +
                "ORDER BY tx.transaction_at DESC";
        return query(con, sql);
    }

    // Transactions for a specific PNR
    public List<Map<String, String>> findByPnr(Connection con, String pnr) throws SQLException {
        String sql = "SELECT tx.*, t.status AS ticket_status, t.fare AS ticket_fare, " +
                "p.name AS passenger_name, tr.train_name, tr.source, tr.destination " +
                "FROM transactions tx " +
                "JOIN tickets t ON tx.pnr = t.pnr " +
                "JOIN passengers p ON t.passenger_id = p.passenger_id " +
                "JOIN trains tr ON t.train_number = tr.train_number " +
                "WHERE tx.pnr = ? ORDER BY tx.transaction_at DESC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, pnr);
            return extractList(ps);
        }
    }

    // Successful transactions only
    public List<Map<String, String>> findSuccessful(Connection con) throws SQLException {
        String sql = "SELECT tx.*, t.status AS ticket_status, t.fare AS ticket_fare, " +
                "p.name AS passenger_name, tr.train_name, tr.source, tr.destination " +
                "FROM transactions tx " +
                "JOIN tickets t ON tx.pnr = t.pnr " +
                "JOIN passengers p ON t.passenger_id = p.passenger_id " +
                "JOIN trains tr ON t.train_number = tr.train_number " +
                "WHERE tx.status = 'SUCCESS' ORDER BY tx.transaction_at DESC";
        return query(con, sql);
    }

    // Currently travelling passengers (confirmed tickets)
    public List<Map<String, String>> findTravelling(Connection con) throws SQLException {
        String sql = "SELECT t.pnr, p.name, p.age, p.gender, " +
                "tr.train_name, tr.train_number, tr.source, tr.destination, " +
                "t.seat_number, t.fare " +
                "FROM tickets t " +
                "JOIN passengers p ON t.passenger_id = p.passenger_id " +
                "JOIN trains tr ON t.train_number = tr.train_number " +
                "WHERE t.status = 'CONFIRMED' " +
                "ORDER BY tr.train_number, t.seat_number";
        List<Map<String, String>> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, String> map = new HashMap<>();
                map.put("pnr", rs.getString("pnr"));
                map.put("name", rs.getString("name"));
                map.put("age", String.valueOf(rs.getInt("age")));
                map.put("gender", rs.getString("gender"));
                map.put("trainName", rs.getString("train_name"));
                map.put("trainNumber", String.valueOf(rs.getInt("train_number")));
                map.put("source", rs.getString("source"));
                map.put("destination", rs.getString("destination"));
                map.put("seatNumber", String.valueOf(rs.getInt("seat_number")));
                map.put("fare", String.valueOf(rs.getDouble("fare")));
                list.add(map);
            }
        }
        return list;
    }

    private List<Map<String, String>> query(Connection con, String sql) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            return extractList(ps);
        }
    }

    private List<Map<String, String>> extractList(PreparedStatement ps) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    private Map<String, String> mapRow(ResultSet rs) throws SQLException {
        Map<String, String> map = new HashMap<>();
        map.put("transactionId", String.valueOf(rs.getInt("transaction_id")));
        map.put("pnr", rs.getString("pnr"));
        map.put("amount", String.valueOf(rs.getDouble("amount")));
        map.put("paymentMode", rs.getString("payment_mode"));
        map.put("transactionType", rs.getString("transaction_type"));
        map.put("status", rs.getString("status"));
        Timestamp ts = rs.getTimestamp("transaction_at");
        map.put("transactionAt", ts != null ? ts.toString() : "");
        map.put("ticketStatus", rs.getString("ticket_status"));
        map.put("passengerName", rs.getString("passenger_name"));
        map.put("trainName", rs.getString("train_name"));
        map.put("source", rs.getString("source"));
        map.put("destination", rs.getString("destination"));
        return map;
    }
}
