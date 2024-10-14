import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        new BBCSourceConverter().convert(
            new File("BBC-source/elite-source-mod.asm"),
            new File("src/elite.a99")
        );
    }
}
