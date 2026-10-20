import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class BookingService {
    private final TrainDao trainDao = new TrainDao();
    private final TicketDao ticketDao = new TicketDao();

    public List<Train> allTrains() throws SQLException {
        try (Connection con = Database.getConnection()) {
            return trainDao.findAll(con);
        }
    }

    public List<Ticket> allTickets() throws SQLException {
        try (Connection con = Database.getConnection()) {
            return ticketDao.findAll(con);
        }
    }

    public List<Train> searchTrains(String from, String to) throws SQLException {
        try (Connection con = Database.getConnection()) {
            return trainDao.search(con, from, to);
        }
    }

    public Ticket bookTicket(int trainNumber, String name, int age, String gender)
            throws SQLException, InvalidPassengerException {
        Passenger passenger = new Passenger(name, age, gender);   // validates, may throw

        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);          // start a transaction: all steps succeed or none do
            try {
                Train train = trainDao.findById(con, trainNumber);
                if (train == null) {
                    throw new IllegalArgumentException("Train " + trainNumber + " not found.");
                }
                trainDao.lock(con, trainNumber);

                int seat;
                Ticket.Status status;
                try {
                    seat = ticketDao.allocateSeat(con, trainNumber, train.getTotalSeats());
                    status = Ticket.Status.CONFIRMED;
                } catch (SeatNotAvailableException e) {
                    seat = 0;                  // train is full, so the passenger waits
                    status = Ticket.Status.WAITING;
                }

                int passengerId = ticketDao.insertPassenger(con, passenger);
                String pnr = ticketDao.newPnr(con);
                double fare = train.calculateFare();
                ticketDao.insertTicket(con, pnr, trainNumber, passengerId, seat, status.name(), fare);

                con.commit();
                return new Ticket(pnr, passenger, trainNumber, seat, status, fare);
            } catch (SQLException | RuntimeException e) {
                con.rollback();                // undo everything if anything failed
                throw e;
            }
        }
    }

    public Ticket getTicket(String pnr) throws SQLException, InvalidPNRException {
        try (Connection con = Database.getConnection()) {
            Ticket ticket = ticketDao.findByPnr(con, pnr.trim().toUpperCase());
            if (ticket == null) {
                throw new InvalidPNRException("No ticket found for PNR: " + pnr);
            }
            return ticket;
        }
    }

    public String cancelTicket(String pnr) throws SQLException, InvalidPNRException {
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try {
                Ticket ticket = ticketDao.findByPnr(con, pnr.trim().toUpperCase());
                if (ticket == null) {
                    throw new InvalidPNRException("No ticket found for PNR: " + pnr);
                }
                if (ticket.getStatus() == Ticket.Status.CANCELLED) {
                    throw new InvalidPNRException("Ticket " + ticket.getPnr() + " is already cancelled.");
                }
                trainDao.lock(con, ticket.getTrainNumber());

                boolean wasConfirmed = ticket.getStatus() == Ticket.Status.CONFIRMED;
                int freedSeat = ticket.getSeatNumber();
                ticketDao.markCancelled(con, ticket.getPnr());      // frees the seat first

                String message = "Ticket " + ticket.getPnr() + " cancelled.";
                if (wasConfirmed) {
                    Ticket next = ticketDao.firstWaiting(con, ticket.getTrainNumber());
                    if (next != null) {                             // waiting-list promotion
                        ticketDao.confirm(con, next.getPnr(), freedSeat);
                        message += " Waiting ticket " + next.getPnr()
                                 + " is now CONFIRMED (seat " + freedSeat + ").";
                    }
                }
                con.commit();
                return message;
            } catch (SQLException | InvalidPNRException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }
}