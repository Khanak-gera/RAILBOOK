import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TrainDao {
    // Seats left and waiting count are worked out by SQL from the tickets table
    private static final String BASE_SQL =
        "SELECT t.*, " +
        "t.total_seats - (SELECT COUNT(*) FROM tickets k WHERE k.train_number = t.train_number AND k.status = 'CONFIRMED') AS available_seats, " +
        "(SELECT COUNT(*) FROM tickets k WHERE k.train_number = t.train_number AND k.status = 'WAITING') AS waiting_count " +
        "FROM trains t ";

    public List<Train> findAll(Connection con) throws SQLException {
        return query(con, BASE_SQL + "ORDER BY t.train_number");
    }

    public List<Train> search(Connection con, String source, String destination) throws SQLException {
        return query(con, BASE_SQL +
                "WHERE LOWER(t.source) = LOWER(?) AND LOWER(t.destination) = LOWER(?) ORDER BY t.train_number",
                source.trim(), destination.trim());
    }

    public Train findById(Connection con, int trainNumber) throws SQLException {
        List<Train> list = query(con, BASE_SQL + "WHERE t.train_number = ?", trainNumber);
        return list.isEmpty() ? null : list.get(0);
    }

    // Locks this train's row until the transaction ends, so two people can't take the same seat
    public void lock(Connection con, int trainNumber) throws SQLException {
        String sql = "SELECT train_number FROM trains WHERE train_number = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, trainNumber);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
        }
    }

    private List<Train> query(Connection con, String sql, Object... params) throws SQLException {
        List<Train> trains = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trains.add(map(rs));
                }
            }
        }
        return trains;
    }

    // Turns one database row into the right Train child class (polymorphism)
    private Train map(ResultSet rs) throws SQLException {
        int no = rs.getInt("train_number");
        String name = rs.getString("train_name");
        String src = rs.getString("source");
        String dst = rs.getString("destination");
        int seats = rs.getInt("total_seats");
        double fare = rs.getDouble("base_fare");

        Train t;
        if ("EXPRESS".equals(rs.getString("train_type"))) {
            t = new ExpressTrain(no, name, src, dst, seats, fare);
        } else {
            t = new LocalTrain(no, name, src, dst, seats, fare);
        }
        t.setAvailableSeats(rs.getInt("available_seats"));
        t.setWaitingCount(rs.getInt("waiting_count"));
        return t;
    }
}