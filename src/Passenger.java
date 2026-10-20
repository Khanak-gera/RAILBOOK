
import java.io.Serializable;

public class Passenger implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private int age;
    private String gender;

    public Passenger(String name, int age, String gender) throws InvalidPassengerException {
        if (name == null || name.trim().isEmpty())
            throw new InvalidPassengerException("Name cannot be empty.");
        if (age < 1 || age > 120)
            throw new InvalidPassengerException("Age must be between 1 and 120.");
        String g = gender.trim().toUpperCase();
        if (!g.equals("M") && !g.equals("F") && !g.equals("O"))
            throw new InvalidPassengerException("Gender must be M, F or O.");

        this.name = name.trim();
        this.age = age;
        this.gender = g;
    }

    public String getName() { return name; }
    public int getAge() { return age; }
    public String getGender() { return gender; }
}