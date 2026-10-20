
public class LocalTrain extends Train {
    private static final long serialVersionUID = 1L;

    public LocalTrain(int trainNumber, String trainName, String source,
                      String destination, int totalSeats, double baseFare) {
        super(trainNumber, trainName, source, destination, totalSeats, baseFare);
    }

    @Override
    public double calculateFare() {
        return getBaseFare();
    }
}