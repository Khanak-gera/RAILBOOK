import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TicketDao {

    // Lowest free seat number, or throws your exception if the train is full
    public int allocateSeat(Connection con, int trainNumber, int totalSeats)
            throws SQLException, SeatNotAvailableException {
        List<Integer> taken = new ArrayList<>();
        String sql = "SELECT seat_number FROM tickets WHERE train_number = ? AND status = 'CONFIRMED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, trainNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    taken.add(rs.getInt("seat_number"));
                }
            }
        }
        for (int seat = 1; seat <= totalSeats; seat++) {
            if (!taken.contains(seat)) return seat;
        }
        throw new SeatNotAvailableException("No seats available on train " + trainNumber);
    }

    // Saves the passenger and returns the new passenger_id
    public int insertPassenger(Connection con, Passenger p) throws SQLException {
        String sql = "INSERT INTO passengers (name, age, gender) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getName());
            ps.setInt(2, p.getAge());
            ps.setString(3, p.getGender());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    // Next PNR: PNR1001, PNR1002, ...
    public String newPnr(Connection con) throws SQLException {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(pnr, 4) AS UNSIGNED)), 1000) + 1 FROM tickets";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return "PNR" + rs.getLong(1);
        }
    }

    // seat = 0 means "no seat" (waiting)
    public void insertTicket(Connection con, String pnr, int trainNumber, int passengerId,
                             int seat, String status, double fare) throws SQLException {
        String sql = "INSERT INTO tickets (pnr, train_number, passenger_id, seat_number, status, fare) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, pnr);
            ps.setInt(2, trainNumber);
            ps.setInt(3, passengerId);
            if (seat == 0) ps.setNull(4, Types.INTEGER); else ps.setInt(4, seat);
            ps.setString(5, status);
            ps.setDouble(6, fare);
            ps.executeUpdate();
        }
    }

    // Uses a JOIN to get the ticket together with its passenger
    public Ticket findByPnr(Connection con, String pnr) throws SQLException {
        String sql = "SELECT k.pnr, k.train_number, k.seat_number, k.status, k.fare, p.name, p.age, p.gender " +
                     "FROM tickets k JOIN passengers p ON k.passenger_id = p.passenger_id WHERE k.pnr = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, pnr);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapTicket(rs) : null;
            }
        }
    }

    // All tickets with their passengers (newest first)
    public List<Ticket> findAll(Connection con) throws SQLException {
        String sql = "SELECT k.pnr, k.train_number, k.seat_number, k.status, k.fare, p.name, p.age, p.gender " +
                     "FROM tickets k JOIN passengers p ON k.passenger_id = p.passenger_id " +
                     "ORDER BY CAST(SUBSTRING(k.pnr, 4) AS UNSIGNED) DESC";
        List<Ticket> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapTicket(rs));
            }
        }
        return list;
    }

    // The waiting passenger who booked first (this is the queue, in SQL)
    public Ticket firstWaiting(Connection con, int trainNumber) throws SQLException {
        String sql = "SELECT k.pnr, k.train_number, k.seat_number, k.status, k.fare, p.name, p.age, p.gender " +
                     "FROM tickets k JOIN passengers p ON k.passenger_id = p.passenger_id " +
                     "WHERE k.train_number = ? AND k.status = 'WAITING' " +
                     "ORDER BY CAST(SUBSTRING(k.pnr, 4) AS UNSIGNED) LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, trainNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapTicket(rs) : null;
            }
        }
    }

    public void markCancelled(Connection con, String pnr) throws SQLException {
        String sql = "UPDATE tickets SET status = 'CANCELLED', seat_number = NULL WHERE pnr = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, pnr);
            ps.executeUpdate();
        }
    }

    public void confirm(Connection con, String pnr, int seat) throws SQLException {
        String sql = "UPDATE tickets SET status = 'CONFIRMED', seat_number = ? WHERE pnr = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, seat);
            ps.setString(2, pnr);
            ps.executeUpdate();
        }
    }

    private Ticket mapTicket(ResultSet rs) throws SQLException {
        try {
            Passenger p = new Passenger(rs.getString("name"), rs.getInt("age"), rs.getString("gender"));
            return new Ticket(rs.getString("pnr"), p, rs.getInt("train_number"),
                    rs.getInt("seat_number"),            // gives 0 when the seat is empty
                    Ticket.Status.valueOf(rs.getString("status")), rs.getDouble("fare"));
        } catch (InvalidPassengerException e) {
            throw new SQLException("Invalid passenger data in database: " + e.getMessage(), e);
        }
    }
}