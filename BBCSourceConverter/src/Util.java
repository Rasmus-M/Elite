public class Util {

    public static String hexString(int i, int length) {
        StringBuilder hex = new StringBuilder(Integer.toHexString(i));
        while (hex.length() < length) {
            hex.insert(0, "0");
        }
        return hex.toString();
    }

    public static String hexString(long i, int length) {
        StringBuilder hex = new StringBuilder(Long.toHexString(i));
        while (hex.length() < length) {
            hex.insert(0, "0");
        }
        return hex.toString();
    }

    public static String tiHexByte(int i) {
        return tiHexString(i, 2, false);
    }

    public static String tiHexWord(int i, boolean swapByteOrder) {
        return tiHexString(i, 4, swapByteOrder);
    }

    private static String tiHexString(long i, int length, boolean swapByteOrder) {
        String s = hexString(i, length);
        if (swapByteOrder) {
            StringBuilder sb = new StringBuilder();
            for (int n = 0; n < length; n += 2) {
                sb.append(s, length - n - 2, length - n);
            }
            s = sb.toString();
        }
        return ">" + s;
    }

    public static String space(int n) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            stringBuilder.append(" ");
        }
        return stringBuilder.toString();
    }

    public static String fit(String s, int n) {
        if (s.length() > n) {
            return s.substring(0, n);
        } else if (s.length() < n) {
            return s + space(n - s.length());
        } else {
            return s;
        }
    }

    public static Integer parseInt(String s) {
        s = s.trim();
        long value;
        try {
            if (s.startsWith("$") || s.startsWith("&")) {
                value = Long.parseLong(s.substring(1).toLowerCase(), 16);
            } else if (s.startsWith("%")) {
                value = Long.parseLong((s.substring(1)), 2);
            } else {
                value = Long.parseLong((s));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return (int) value;
    }
}
