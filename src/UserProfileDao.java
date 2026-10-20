import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserProfileDao {

    public int insertProfile(Connection con, String username, String fullName,
            String email, String phone, String dob, String address,
            String city, String state, String pincode,
            String idProofType, String idProofNo) throws SQLException {
        String sql = "INSERT INTO user_profiles (username, full_name, email, phone, " +
                "date_of_birth, address, city, state, pincode, id_proof_type, id_proof_no) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, fullName);
            ps.setString(3, email);
            ps.setString(4, phone);
            if (dob != null && !dob.isBlank()) ps.setDate(5, Date.valueOf(dob));
            else ps.setNull(5, Types.DATE);
            ps.setString(6, address);
            ps.setString(7, city);
            ps.setString(8, state);
            ps.setString(9, pincode);
            ps.setString(10, idProofType != null && !idProofType.isBlank() ? idProofType : "AADHAR");
            ps.setString(11, idProofNo);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public Map<String, String> findByUsername(Connection con, String username) throws SQLException {
        String sql = "SELECT * FROM user_profiles WHERE username = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    public Map<String, String> findById(Connection con, int userId) throws SQLException {
        String sql = "SELECT * FROM user_profiles WHERE user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    public List<Map<String, String>> findAll(Connection con) throws SQLException {
        String sql = "SELECT * FROM user_profiles ORDER BY created_at DESC";
        List<Map<String, String>> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public void updateProfile(Connection con, String username, String fullName,
            String email, String phone, String dob, String address,
            String city, String state, String pincode,
            String idProofType, String idProofNo) throws SQLException {
        String sql = "UPDATE user_profiles SET full_name=?, email=?, phone=?, " +
                "date_of_birth=?, address=?, city=?, state=?, pincode=?, " +
                "id_proof_type=?, id_proof_no=? WHERE username=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, phone);
            if (dob != null && !dob.isBlank()) ps.setDate(4, Date.valueOf(dob));
            else ps.setNull(4, Types.DATE);
            ps.setString(5, address);
            ps.setString(6, city);
            ps.setString(7, state);
            ps.setString(8, pincode);
            ps.setString(9, idProofType != null && !idProofType.isBlank() ? idProofType : "AADHAR");
            ps.setString(10, idProofNo);
            ps.setString(11, username);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("No user found with username: " + username);
        }
    }

    public void deleteProfile(Connection con, String username) throws SQLException {
        String sql = "DELETE FROM user_profiles WHERE username = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("No user found with username: " + username);
        }
    }

    private Map<String, String> mapRow(ResultSet rs) throws SQLException {
        Map<String, String> map = new HashMap<>();
        map.put("userId", String.valueOf(rs.getInt("user_id")));
        map.put("username", rs.getString("username"));
        map.put("fullName", rs.getString("full_name"));
        map.put("email", rs.getString("email"));
        map.put("phone", rs.getString("phone"));
        Date dob = rs.getDate("date_of_birth");
        map.put("dateOfBirth", dob != null ? dob.toString() : "");
        map.put("address", rs.getString("address") != null ? rs.getString("address") : "");
        map.put("city", rs.getString("city") != null ? rs.getString("city") : "");
        map.put("state", rs.getString("state") != null ? rs.getString("state") : "");
        map.put("pincode", rs.getString("pincode") != null ? rs.getString("pincode") : "");
        map.put("idProofType", rs.getString("id_proof_type") != null ? rs.getString("id_proof_type") : "");
        map.put("idProofNo", rs.getString("id_proof_no") != null ? rs.getString("id_proof_no") : "");
        Timestamp created = rs.getTimestamp("created_at");
        map.put("createdAt", created != null ? created.toString() : "");
        Timestamp updated = rs.getTimestamp("updated_at");
        map.put("updatedAt", updated != null ? updated.toString() : "");
        return map;
    }
}
