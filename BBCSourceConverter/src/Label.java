public class Label {

    private final String text;

    public Label(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public String toString(boolean hex) {
        return getText();
    }
}
