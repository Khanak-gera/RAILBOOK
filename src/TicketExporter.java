import java.io.FileWriter;
import java.io.IOException;

public class TicketExporter {
    // Writes the ticket to a text file and returns the file name
    public static String save(Ticket t) throws IOException {
        String fileName = "ticket_" + t.getPnr() + ".txt";
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write("===== RAILWAY E-TICKET =====\n");
            writer.write("PNR: " + t.getPnr() + "\n");
            writer.write("Passenger: " + t.getPassenger().getName()
                    + " (" + t.getPassenger().getAge() + ", " + t.getPassenger().getGender() + ")\n");
            writer.write("Train: " + t.getTrainNumber() + "\n");
            writer.write("Seat: " + (t.getSeatNumber() == 0 ? "Not assigned" : String.valueOf(t.getSeatNumber())) + "\n");
            writer.write("Status: " + t.getStatus() + "\n");
        }
        return fileName;
    }
}