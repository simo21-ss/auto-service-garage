package bg.softuni.partssvc.common.exception;

public class PartNotFoundException extends RuntimeException {

    public PartNotFoundException(String message) {
        super(message);
    }
}
