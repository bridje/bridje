package bridje;

public class Panic extends RuntimeException {


    public static Panic panic(String fmt, Object... args) {
        return panic(null, fmt, args);
    }

    public static Panic panic(Throwable cause, String fmt, Object... args) {
        return new Panic(String.format(fmt, args), cause);
    }

    public Panic(String message, Throwable cause) {
        super(message, cause);
    }
}
