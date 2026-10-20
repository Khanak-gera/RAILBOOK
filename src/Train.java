public abstract class Train {
    private final int trainNumber;
    private final String trainName;
    private final String source;
    private final String destination;
    private final int totalSeats;
    private final double baseFare;
    private int availableSeats;   // filled from the database
    private int waitingCount;     // filled from the database

    public Train(int trainNumber, String trainName, String source,
                 String destination, int totalSeats, double baseFare) {
        this.trainNumber = trainNumber;
        this.trainName = trainName;
        this.source = source;
        this.destination = destination;
        this.totalSeats = totalSeats;
        this.baseFare = baseFare;
    }

    public abstract double calculateFare();   // each child decides its own fare

    public int getTrainNumber() { return trainNumber; }
    public String getTrainName() { return trainName; }
    public String getSource() { return source; }
    public String getDestination() { return destination; }
    public int getTotalSeats() { return totalSeats; }
    public double getBaseFare() { return baseFare; }
    public int getAvailableSeats() { return availableSeats; }
    public int getWaitingCount() { return waitingCount; }
    public void setAvailableSeats(int availableSeats) { this.availableSeats = availableSeats; }
    public void setWaitingCount(int waitingCount) { this.waitingCount = waitingCount; }
}


