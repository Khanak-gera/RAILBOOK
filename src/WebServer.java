import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {
    private static final BookingService service = new BookingService();
    private static final UserProfileDao profileDao = new UserProfileDao();
    private static final TransactionDao transactionDao = new TransactionDao();

    // One small piece of work the server can do, e.g. "list trains"
    private interface Action {
        String run(Map<String, String> params) throws Exception;
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Web page files (index.html, style.css, app.js)
        server.createContext("/", ex -> serveFile(ex));

        // List or search trains
        server.createContext("/api/trains", ex -> handle(ex, p -> {
            String from = p.getOrDefault("from", "");
            String to = p.getOrDefault("to", "");
            List<Train> list = (from.isBlank() || to.isBlank())
                    ? service.allTrains() : service.searchTrains(from, to);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(trainJson(list.get(i)));
            }
            return sb.append("]").toString();
        }));

        // Book a ticket
        server.createContext("/api/book", ex -> handle(ex, p -> {
            int trainNo = number(p, "trainNumber", "Train number");
            int age = number(p, "age", "Age");
            Ticket t = service.bookTicket(trainNo, p.getOrDefault("name", ""), age,
                    p.getOrDefault("gender", ""));
            return ticketJson(t);
        }));

        // Look up a ticket by PNR
        server.createContext("/api/ticket", ex -> handle(ex, p ->
                ticketJson(service.getTicket(p.getOrDefault("pnr", "")))));

        // Cancel a ticket (and promote the first waiting passenger)
        server.createContext("/api/cancel", ex -> handle(ex, p -> {
            String pnr = p.getOrDefault("pnr", "");
            String message = service.cancelTicket(pnr);
            return "{\"message\":" + quote(message)
                    + ",\"ticket\":" + ticketJson(service.getTicket(pnr)) + "}";
        }));

        // Save a ticket to a text file (file handling)
        server.createContext("/api/save", ex -> handle(ex, p -> {
            Ticket t = service.getTicket(p.getOrDefault("pnr", ""));
            String fileName = TicketExporter.save(t);
            return "{\"message\":" + quote("Ticket saved to file " + fileName) + "}";
        }));

        // ---------- NEW: All bookings (needed by the frontend's "My Tickets" page) ----------
        server.createContext("/api/bookings", ex -> handle(ex, p -> {
            List<Ticket> list = service.allTickets();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(ticketJson(list.get(i)));
            }
            return sb.append("]").toString();
        }));

        // ---------- NEW: User Profile CRUD ----------

        // GET profile by username
        server.createContext("/api/profile/get", ex -> handle(ex, p -> {
            String username = p.getOrDefault("username", "").trim();
            if (username.isBlank()) throw new IllegalArgumentException("Username is required.");
            try (Connection con = Database.getConnection()) {
                Map<String, String> profile = profileDao.findByUsername(con, username);
                if (profile == null) throw new IllegalArgumentException("No profile found for: " + username);
                return mapToJson(profile);
            }
        }));

        // GET all profiles
        server.createContext("/api/profile/all", ex -> handle(ex, p -> {
            try (Connection con = Database.getConnection()) {
                List<Map<String, String>> profiles = profileDao.findAll(con);
                return listMapToJson(profiles);
            }
        }));

        // POST create profile
        server.createContext("/api/profile/create", ex -> handle(ex, p -> {
            String username = p.getOrDefault("username", "").trim();
            if (username.isBlank()) throw new IllegalArgumentException("Username is required.");
            try (Connection con = Database.getConnection()) {
                int id = profileDao.insertProfile(con,
                        username,
                        p.getOrDefault("fullName", ""),
                        p.getOrDefault("email", ""),
                        p.getOrDefault("phone", ""),
                        p.getOrDefault("dateOfBirth", ""),
                        p.getOrDefault("address", ""),
                        p.getOrDefault("city", ""),
                        p.getOrDefault("state", ""),
                        p.getOrDefault("pincode", ""),
                        p.getOrDefault("idProofType", "AADHAR"),
                        p.getOrDefault("idProofNo", ""));
                return "{\"message\":" + quote("Profile created successfully") + ",\"userId\":" + id + "}";
            }
        }));

        // POST update profile
        server.createContext("/api/profile/update", ex -> handle(ex, p -> {
            String username = p.getOrDefault("username", "").trim();
            if (username.isBlank()) throw new IllegalArgumentException("Username is required.");
            try (Connection con = Database.getConnection()) {
                profileDao.updateProfile(con,
                        username,
                        p.getOrDefault("fullName", ""),
                        p.getOrDefault("email", ""),
                        p.getOrDefault("phone", ""),
                        p.getOrDefault("dateOfBirth", ""),
                        p.getOrDefault("address", ""),
                        p.getOrDefault("city", ""),
                        p.getOrDefault("state", ""),
                        p.getOrDefault("pincode", ""),
                        p.getOrDefault("idProofType", "AADHAR"),
                        p.getOrDefault("idProofNo", ""));
                return "{\"message\":" + quote("Profile updated successfully") + "}";
            }
        }));

        // POST delete profile
        server.createContext("/api/profile/delete", ex -> handle(ex, p -> {
            String username = p.getOrDefault("username", "").trim();
            if (username.isBlank()) throw new IllegalArgumentException("Username is required.");
            try (Connection con = Database.getConnection()) {
                profileDao.deleteProfile(con, username);
                return "{\"message\":" + quote("Profile deleted successfully") + "}";
            }
        }));

        // ---------- NEW: Transaction History ----------

        // GET all transactions
        server.createContext("/api/transactions", ex -> handle(ex, p -> {
            try (Connection con = Database.getConnection()) {
                List<Map<String, String>> txns = transactionDao.findAll(con);
                return listMapToJson(txns);
            }
        }));

        // GET successful transactions only
        server.createContext("/api/transactions/successful", ex -> handle(ex, p -> {
            try (Connection con = Database.getConnection()) {
                List<Map<String, String>> txns = transactionDao.findSuccessful(con);
                return listMapToJson(txns);
            }
        }));

        // ---------- NEW: Currently Travelling Passengers ----------

        server.createContext("/api/travelling", ex -> handle(ex, p -> {
            try (Connection con = Database.getConnection()) {
                List<Map<String, String>> passengers = transactionDao.findTravelling(con);
                return listMapToJson(passengers);
            }
        }));

        server.start();
        System.out.println("RailBook is running. Open http://localhost:8080 in your browser.");
    }

    // ---------- helpers ----------

    // Runs an action, sends its JSON result, or sends {"error": "..."} if it fails
    private static void handle(HttpExchange ex, Action action) throws IOException {
        try {
            String json = action.run(readParams(ex));
            send(ex, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Something went wrong." : e.getMessage();
            String json = "{\"error\":" + quote(msg) + "}";
            send(ex, 400, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static int number(Map<String, String> p, String key, String label) {
        try {
            return Integer.parseInt(p.getOrDefault(key, "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    // Reads values from the address (?a=1&b=2) and from a submitted form
    private static Map<String, String> readParams(HttpExchange ex) throws IOException {
        Map<String, String> map = new HashMap<>();
        parseInto(map, ex.getRequestURI().getRawQuery());
        if ("POST".equals(ex.getRequestMethod())) {
            parseInto(map, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        }
        return map;
    }

    private static void parseInto(Map<String, String> map, String text) {
        if (text == null || text.isEmpty()) return;
        for (String pair : text.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, value);
        }
    }

    private static void serveFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        Path root = Files.isDirectory(Paths.get("web")) ? Paths.get("web") : Paths.get("../web");
        Path file = root.resolve(path.substring(1)).normalize();
        if (!file.startsWith(root.normalize()) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String type = path.endsWith(".css") ? "text/css; charset=utf-8"
                    : path.endsWith(".js") ? "application/javascript; charset=utf-8"
                    : "text/html; charset=utf-8";
        send(ex, 200, type, Files.readAllBytes(file));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ---------- turning Java objects into JSON text ----------

    private static String trainJson(Train t) {
        return "{\"trainNumber\":" + t.getTrainNumber()
             + ",\"trainName\":" + quote(t.getTrainName())
             + ",\"source\":" + quote(t.getSource())
             + ",\"destination\":" + quote(t.getDestination())
             + ",\"type\":" + quote(t instanceof ExpressTrain ? "Express" : "Local")
             + ",\"totalSeats\":" + t.getTotalSeats()
             + ",\"availableSeats\":" + t.getAvailableSeats()
             + ",\"waitingCount\":" + t.getWaitingCount()
             + ",\"fare\":" + t.calculateFare() + "}";
    }

    private static String ticketJson(Ticket t) {
        return "{\"pnr\":" + quote(t.getPnr())
             + ",\"name\":" + quote(t.getPassenger().getName())
             + ",\"age\":" + t.getPassenger().getAge()
             + ",\"gender\":" + quote(t.getPassenger().getGender())
             + ",\"trainNumber\":" + t.getTrainNumber()
             + ",\"seat\":" + t.getSeatNumber()
             + ",\"status\":" + quote(t.getStatus().name())
             + ",\"fare\":" + t.getFare() + "}";
    }

    // Converts a Map to a JSON object string
    private static String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(quote(entry.getKey())).append(":").append(quote(entry.getValue()));
        }
        return sb.append("}").toString();
    }

    // Converts a List of Maps to a JSON array string
    private static String listMapToJson(List<Map<String, String>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(mapToJson(list.get(i)));
        }
        return sb.append("]").toString();
    }

    // Puts text in quotes and escapes special characters so the JSON stays valid
    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}

