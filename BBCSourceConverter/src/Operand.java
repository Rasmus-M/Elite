import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Operand {

    public static final String numberRegEx = "^[&$%]?[0-9A-F]+$";
    public static final String symbolRegEx = "^[0-9A-Za-z_%]+$";
    public static final String lowerCaseSymbolRegEx = "^[0-9a-z_]+$";
    public static final String expressionRegEx = "[&$%0-9A-Za-z+\\-*/_]+|'.'|'.'\\+1|LO\\(.+?\\)|HI\\(.+?\\)";
    private static final Pattern numberPattern = Pattern.compile(numberRegEx);
    private static final Pattern accumulatorPattern = Pattern.compile("^A$");
    private static final Pattern absolutePattern = Pattern.compile("^(" + expressionRegEx + ")$");
    private static final Pattern absoluteXIndexedPattern = Pattern.compile("^(" + expressionRegEx + "),X$");
    private static final Pattern absoluteYIndexedPattern = Pattern.compile("^(" + expressionRegEx + "),Y$");
    private static final Pattern immediatePattern = Pattern.compile("^#(" + expressionRegEx + ")$");
    private static final Pattern indirectPattern = Pattern.compile("^\\((" + expressionRegEx + ")\\)$");
    public static final Pattern xIndexedIndirectPattern = Pattern.compile("^\\((" + expressionRegEx + "),X\\)$");
    public static final Pattern indirectYIndexedPattern = Pattern.compile("^\\((" + expressionRegEx + ")\\),Y$");
    private static final Pattern relativePattern = Pattern.compile("^(" + expressionRegEx + ")$");
    private static final Pattern zeroPagePattern = Pattern.compile("^(" + expressionRegEx + ")$");
    private static final Pattern zeroPageXIndexedPattern = Pattern.compile("^(" + expressionRegEx + "),X$");
    private static final Pattern zeroPageYIndexedPattern = Pattern.compile("^(" + expressionRegEx + "),Y$");

    public enum Type {
        Accumulator,
        AbsoluteOrZeroPage,
        AbsoluteXIndexed,
        AbsoluteYIndexed,
        Immediate,
        Implied,
        Indirect,
        XIndexedIndirect,
        IndirectYIndexed,
        Relative,
        ZeroPageXIndexed,
        ZeroPageYIndexed,
        Other
    }

    private final String value;
    private final Type type;
    private Integer number;
    private String register;
    private String expression;

    public Operand(String value, String opcode) {
        this.value = value;
        Matcher m;
        m = accumulatorPattern.matcher(value);
        if (m.find()) {
            type = Type.Accumulator;
            return;
        }
        m = immediatePattern.matcher(value);
        if (m.find()) {
            type = Type.Immediate;
            expression = m.group(1);
            number = parseExpression();
            return;
        }
        m = absoluteXIndexedPattern.matcher(value);
        if (m.find()) {
            register = "X";
            type = Type.AbsoluteXIndexed;
            expression = m.group(1);
            number = parseExpression();
            return;
        }
        m = absoluteYIndexedPattern.matcher(value);
        if (m.find()) {
            register = "Y";
            type = Type.AbsoluteYIndexed;
            expression = m.group(1);
            number = parseExpression();
            return;
        }
        m = xIndexedIndirectPattern.matcher(value);
        if (m.find()) {
            register = "X";
            type = Type.XIndexedIndirect;
            expression = m.group(1);
            number = parseExpression();
            return;
        }
        m = indirectYIndexedPattern.matcher(value);
        if (m.find()) {
            register = "Y";
            type = Type.IndirectYIndexed;
            expression = m.group(1);
            number = parseExpression();
            return;
        }
        m = indirectPattern.matcher(value);
        if (m.find()) {
            type = Type.Indirect;
            expression = m.group(1);
            number = parseExpression();
            return;
        }
        m = zeroPageXIndexedPattern.matcher(value);
        if (m.find()) {
            register = "X";
            type = Type.ZeroPageXIndexed;
            expression = m.group(1);
            number = parseExpression();
            return;
        }
        m = zeroPageYIndexedPattern.matcher(value);
        if (m.find()) {
            register = "Y";
            type = Type.ZeroPageYIndexed;
            expression = m.group(1);
            number = parseExpression();
            return;
        }
        m = relativePattern.matcher(value);
        if (m.find() && (opcode.equals("BCC") || opcode.equals("BCS") || opcode.equals("BEQ") || opcode.equals("BMI") || opcode.equals("BNE") || opcode.equals("BPL") || opcode.equals("BVC") || opcode.equals("BVS"))) {
            type = Type.Relative;
            expression = m.group(1);
            number = parseExpression();
            return;
        }
        m = absolutePattern.matcher(value);
        if (m.find()) {
            type = Type.AbsoluteOrZeroPage;
            expression = m.group(1);
            number = parseExpression();
            return;
        }
        type = Type.Other;
    }

    private Integer parseExpression() {
        if (numberPattern.matcher(expression).matches()) {
            return Util.parseInt(expression);
        } else{
            return null;
        }
    }

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public Integer getNumber() {
        return number;
    }
    
    public String getRegister() {
        return register;
    }

    public String getExpression() {
        return expression;
    }
}
