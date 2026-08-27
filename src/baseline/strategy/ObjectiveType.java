package baseline.strategy;

public enum ObjectiveType {
    RETURN("RETURN"),
    INTERCEPT("INTERCEPT"),
    CAPTURE("CAPTURE"),
    RETREAT("RETREAT"),
    DEFEND("DEFEND"),
    ENGAGE("ENGAGE"),
    CRUMBS("CRUMBS"),
    BROADCAST("BROADCAST"),
    EXPLORE("EXPLORE");

    public final String code;

    ObjectiveType(String code) {
        this.code = code;
    }
}
