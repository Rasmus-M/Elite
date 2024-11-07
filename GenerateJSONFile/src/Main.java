public class Main {
    public static void main(String[] args) {
        JSONGenerator generator = new JSONGenerator("bin/elite", "elite.json");
        try {
            generator.generate(0xFE00);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }
}
