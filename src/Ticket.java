import java.io.Serializable;

public class Ticket implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { CONFIRMED, WAITING, CANCELLED }

    private String pnr;
    private Passenger passenger;
    private int trainNumber;
    private int seatNumber;      
    private Status status;
    private double fare;

    public Ticket(String pnr, Passenger passenger, int trainNumber,
                  int seatNumber, Status status, double fare) {
        this.pnr = pnr;
        this.passenger = passenger;
        this.trainNumber = trainNumber;
        this.seatNumber = seatNumber;
        this.status = status;
        this.fare = fare;
    }

    public String getPnr() { return pnr; }
    public Passenger getPassenger() { return passenger; }
    public int getTrainNumber() { return trainNumber; }
    public int getSeatNumber() { return seatNumber; }
    public Status getStatus() { return status; }
    public double getFare() { return fare; }

    public void setSeatNumber(int seatNumber) { this.seatNumber = seatNumber; }
    public void setStatus(Status status) { this.status = status; }

    @Override
    public String toString() {
        String seat = (seatNumber == 0) ? "-" : String.valueOf(seatNumber);
        return "PNR: " + pnr
             + " | " + passenger.getName() + " (" + passenger.getAge() + ", " + passenger.getGender() + ")"
             + " | Train: " + trainNumber
             + " | Seat: " + seat
             + " | Status: " + status
             + " | Fare: Rs." + String.format("%.2f", fare);
    }
}