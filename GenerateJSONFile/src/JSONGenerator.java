import java.io.*;

public class JSONGenerator {

    public static int BYTES_PER_LINE = 64;

    private final String inputFilePath;
    private final String outputFilePath;

    public JSONGenerator(String inputFilePath, String outputFilePath) {
        this.inputFilePath = inputFilePath;
        this.outputFilePath = outputFilePath;
    }

    public void generate(int startAddress) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    \"name\": \"").append("Elite").append("\",\n");
        sb.append("    \"startAddress\": \"0x").append(toHexString(startAddress, 4)).append("\",\n");
        sb.append("    \"memoryBlocks\": [\n");
        String filePath = inputFilePath;
        File inputFile = new File(inputFilePath);
        boolean first = true;
        while (inputFile.exists()) {
            byte[] buffer = new byte[0x10000];
            FileInputStream in = new FileInputStream(inputFile);
            int length = in.read(buffer);
            in.close();
            if (!first) {
                sb.append(",\n");
            }
            System.out.println(inputFile.getAbsolutePath());
            generateMemoryBlock(buffer, length, sb);
            filePath = filePath.substring(0, filePath.length() - 1) + (char) (filePath.charAt(filePath.length() - 1) + 1);
            inputFile = new File(filePath);
            first = false;
        }
        sb.append("\n");
        sb.append("    ]\n");
        sb.append("}");
        FileOutputStream out = new FileOutputStream(outputFilePath);
        PrintWriter writer = new PrintWriter(out);
        writer.write(sb.toString());
        writer.flush();
        out.close();
    }

    private void generateMemoryBlock(byte[] bytes, int length, StringBuilder sb) throws Exception {
        int blockLength = ((bytes[2] << 8) | (bytes[3] & 0xff)) & 0xffff;
        if (blockLength != length) {
            throw new Exception("Length mismatch: " + toHexString(length, 4) + " <> " + toHexString(blockLength, 4));
        }
        int blockStart =  ((bytes[4] << 8) | (bytes[5] & 0xff)) & 0xffff;
        System.out.println("Start: >" + toHexString(blockStart, 4) + " length: >" + toHexString(blockLength, 4));
        sb.append("        {\n");
        sb.append("            \"address\": \"0x").append(toHexString(blockStart, 4)).append("\",\n");
        sb.append("            \"data\": [\n");
        sb.append(toHexString(bytes, 6, length - 6));
        sb.append("            ]\n");
        sb.append("        }");
    }

    private String toHexString(byte[] bytes, int start, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int b = bytes[start + i];
            if (i % BYTES_PER_LINE == 0) sb.append("                \"");
            sb.append(toHexString(b & 0xff, 2));
            if (i % BYTES_PER_LINE == (BYTES_PER_LINE - 1)) {
                sb.append("\"");
                if (i < length - 1) sb.append(",");
                sb.append("\n");
            }
        }
        if (length % BYTES_PER_LINE != 0) {
            sb.append("\"\n");
        }
        return sb.toString();
    }

    private String toHexString(int n, int length) {
        String value = Integer.toHexString(n).toUpperCase();
        while (value.length() < length) {
            value = "0" + value;
        }
        return value;
    }
}
