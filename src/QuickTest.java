public class QuickTest {
    public static void main(String[] args) throws Exception {
        BookingService service = new BookingService();

        for (Train t : service.allTrains()) {
            System.out.println(t.getTrainNumber() + "  " + t.getTrainName()
                    + "  seats left: " + t.getAvailableSeats()
                    + "  waiting: " + t.getWaitingCount()
                    + "  fare: " + t.calculateFare());
        }

        Ticket ticket = service.bookTicket(12402, "Test User", 22, "F");
        System.out.println(ticket);
        System.out.println(service.cancelTicket(ticket.getPnr()));
    }
}