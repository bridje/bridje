package rho;

public class Panic extends RuntimeException {


    public static Panic panic(String message) {
        return panic(message, null);
    }

    public static Panic panic(String message, Throwable cause) {
        return new Panic(message, cause);
    }

    public Panic(String message, Throwable cause) {
        super(message, cause);
    }
}
