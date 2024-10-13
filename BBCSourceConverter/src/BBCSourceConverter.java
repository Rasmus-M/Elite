import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BBCSourceConverter {

    private static final String zeroPage = "ZERO_PAGE";
    private static final String regA = "A";
    private static final String regX = "X";
    private static final String regY = "Y";
    private static final String regTmp = "TMP";
    private static final String regTmpLowByte = "R3LB";
    Pattern loFunctionPattern = Pattern.compile("LO\\((" + Operand.expressionRegEx + ")\\)");
    Pattern hiFunctionPattern = Pattern.compile("HI\\((" + Operand.expressionRegEx + ")\\)");

    private final boolean hexOutput;

    public BBCSourceConverter(boolean hexOutput) {
        this.hexOutput = hexOutput;
    }

    public void convert(File bbcFile, File tms9900File) throws IOException  {
        List<BBCLine> bbcLines = readBBCFile(bbcFile);
        System.out.println(bbcLines.size() + " lines read from: " + bbcFile.getPath());
        List<TMS9900Line> tms9900Lines = convert(bbcLines);
        writeTMS9900File(tms9900File, tms9900Lines);
        System.out.println(tms9900Lines.size() + " lines written to: " + tms9900File.getPath());
    }

    private List<TMS9900Line> convert(List<BBCLine> bbcLines) {
        List<TMS9900Line> tms9900Lines = new ArrayList<>();
        createRegEquate(tms9900Lines, regA, "0", null);
        createRegEquate(tms9900Lines, regX, "1", null);
        createRegEquate(tms9900Lines, regY, "2", null);
        createRegEquate(tms9900Lines, regTmp, "3", null);
        createEquate(tms9900Lines, "_MAX_COMMANDER", "0", null);
        createEquate(tms9900Lines, zeroPage, ">8300", null);
        createEquate(tms9900Lines, regTmpLowByte, ">8307", null);
        boolean insideMacro = false;
        boolean insideFor = false;
        int i = 0;
        while (i < bbcLines.size()) {
            BBCLine bbcLine = bbcLines.get(i);
            switch (bbcLine.getType()) {
                case Empty:
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Empty));
                    break;
                case MacroStart:
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), ".defm " + bbcLine.getDirective().split(" ")[0]));
                    insideMacro = true;
                    break;
                case MacroEnd:
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), ".endm"));
                    insideMacro = false;
                    break;
                case ForStart:
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "; " + bbcLine.getDirective()));
                    insideFor = true;
                    break;
                case ForEnd:
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "; " + bbcLine.getDirective()));
                    insideFor = false;
                    break;
                default:
                    if (!insideMacro && !insideFor) {
                        switch (bbcLine.getType()) {
                            case Directive:
                                i += convertDirective(bbcLine, tms9900Lines);
                                break;
                            case Label:
                                tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Label, bbcLine.getComment(), convertSymbol(bbcLine.getLabel())));
                                break;
                            case Comment:
                                tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Comment, bbcLine.getComment()));
                                break;
                            case ContinuationComment:
                                int j = i;
                                BBCLine previousLine = j > 0 ? bbcLines.get(--j) : null;
                                while (previousLine != null && previousLine.getType() == BBCLine.Type.ContinuationComment && j > 0) {
                                    previousLine = bbcLines.get(--j);
                                }
                                BBCLine.Type previousLineType = previousLine != null ? previousLine.getType() : null;
                                tms9900Lines.add(new TMS9900Line(previousLineType == BBCLine.Type.Data ? TMS9900Line.Type.ContinuationCommentData : TMS9900Line.Type.ContinuationCommentInstruction, bbcLine.getComment()));
                                break;
                            case Data:
                                i += convertData(bbcLine, tms9900Lines);
                                break;
                            case Instruction:
                                i += convertInstruction(bbcLine, tms9900Lines);
                                break;
                            case Variable:
                                String[] variableAndValue = bbcLine.getInstruction().split("=");
                                if (variableAndValue.length == 2) {
                                    createEquate(tms9900Lines, variableAndValue[0].trim(), variableAndValue[1].trim(), bbcLine.getComment());
                                }
                                break;
                        }
                    } else {
                        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Instruction, bbcLine.getComment(), "; " + bbcLine.getInstruction()));
                    }
            }
            i++;
        }
        return tms9900Lines;
    }

    private void createEquate(List<TMS9900Line> tms9900Lines, String symbol, String value, String comment) {
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Label, comment, convertSymbol(symbol)));
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, null, " equ " + convertExpression(value)));
    }

    private void createRegEquate(List<TMS9900Line> tms9900Lines, String symbol, String value, String comment) {
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Label, comment, convertSymbol(symbol)));
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, null, " requ " + convertExpression(value)));
    }

    private int convertDirective(BBCLine bbcLine, List<TMS9900Line> tms9900Lines) {
        if (bbcLine.getDirective().startsWith("SKIP")) {
            tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "bss " + convertExpression(bbcLine.getDirective().substring(5))));
        }
        return 0;
    }

    private int convertData(BBCLine bbcLine, List<TMS9900Line> tms9900Lines) {
        String bbcInstruction = bbcLine.getInstruction();
        String[] parts = bbcInstruction.split(" ");
        String valuePart = parts.length > 1 ? parts[1] : "";
        String[] values = valuePart.split(",");
        String instruction = null;
        if (bbcInstruction.startsWith("EQUB")) {
            for (int i = 0; i < values.length; i++) {
                values[i] = Util.tiHexByte(Util.parseInt(values[i]));
            }
            instruction = "byte " + String.join(",", values);
        } else if (bbcInstruction.startsWith("EQUW")) {
            for (int i = 0; i < values.length; i++) {
                values[i] = Util.tiHexWord(Util.parseInt(values[i]), true);
            }
            instruction = "data " + String.join(",", values);
        } else if (bbcInstruction.startsWith("EQUD")) {
            for (int i = 0; i < values.length; i++) {
                int v = Util.parseInt(values[i]);
                values[i] = Util.tiHexWord(i & 0xffff, true) + ", " + Util.tiHexWord((i & 0xffff0000) >>> 16, true);
            }
            instruction = "data " + String.join(",", values);
        } else if (bbcInstruction.startsWith("EQUS")) {
            instruction = "text " + bbcInstruction.substring(5).replace("\"", "'");
        }
        if (instruction != null) {
            tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Data, bbcLine.getComment(), instruction));
        }
        return 0;
    }

    private int convertInstruction(BBCLine bbcLine, List<TMS9900Line> tms9900Lines) {
        Instruction instruction = new Instruction(bbcLine.getInstruction());
        String opcode = instruction.getOpcode();
        Operand operand = instruction.getOperand();
        TMS9900Line tms9900Line = new TMS9900Line(TMS9900Line.Type.Instruction, bbcLine.getComment());
        tms9900Line.setBbcInstruction(bbcLine.getInstruction());
        List<TMS9900Line> additionalLines = new ArrayList<>();
        switch (opcode) {
            case "BEQ":
                tms9900Line.setInstruction("jeq  " + convertExpression(operand.getExpression()));
                break;
            case "BNE":
                tms9900Line.setInstruction("jne  " + convertExpression(operand.getExpression()));
                break;
            case "LDA":
                    if (operand.getType() == Operand.Type.Immediate) {
                        tms9900Line.setInstruction("li   " + regA + "," + convertOperand(operand));
                    } else {
                        switch (operand.getType()) {
                            case XIndexedIndirect:
                                tms9900Line.setInstruction("movb @" + zeroPage + "(" + regX + "),@" + regTmpLowByte);
                                additionalLines.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "movb @" + zeroPage+1 + "(" + regX + ")," + regTmp));
                                additionalLines.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "movb *" + regTmp + "," + regA));
                                break;
                            case IndirectYIndexed:
                                tms9900Line.setInstruction("movb @" + zeroPage + "+" + convertExpression(operand.getExpression()) + ",@" + regTmpLowByte);
                                additionalLines.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "movb @" + zeroPage + "+" + convertExpression(operand.getExpression()) + "+1," + regTmp));
                                additionalLines.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "movb *" + regTmp + "," + regA));
                                break;
                            default:
                                tms9900Line.setInstruction("movb " + convertOperand(operand) + "," + regA);
                                break;
                        }
                    }
                break;
            default:
                tms9900Line.setInstruction("; " + instruction);
                break;
        }
        tms9900Lines.add(tms9900Line);
        tms9900Lines.addAll(additionalLines);
        return 0;
    }

    private String convertOperand(Operand operand) {
        switch (operand.getType()) {
            case Accumulator:
                return regA;
            case AbsoluteOrZeroPage:
                return "@" + convertExpression(operand.getExpression());
            case AbsoluteXIndexed:
                return "@" + convertExpression(operand.getExpression()) + "(" + regX + ")";
            case AbsoluteYIndexed:
                return "@" + convertExpression(operand.getExpression()) + "(" + regY + ")";
            case Immediate:
                return (operand.getNumber() != null ? Util.tiHexByte(operand.getNumber()) : "(" + convertExpression(operand.getExpression()) + ")") + "*256";
            case Implied:
                return "";
            case Indirect:
                return "*" + convertExpression(operand.getExpression());
            case XIndexedIndirect:
                return "";
            case IndirectYIndexed:
                return "";
            case Relative:
                return convertExpression(operand.getExpression());
            case ZeroPageXIndexed:
                return "@" + zeroPage + "(" + regX + ")";
            case ZeroPageYIndexed:
                return "@" + zeroPage + "(" + regY + ")";
            case Other:
            default:
                return operand.getValue();
        }
    }

    private String convertExpression(String expression) {
        if (expression != null) {
            expression = expression.replace("&", ">");
            expression = expression.replace("P%", "$");
            if (expression.startsWith("%")) {
                expression = expression.replaceFirst("%", ":");
            }
            StringBuilder result = new StringBuilder();
            StringTokenizer tokenizer = new StringTokenizer(expression, " +-*/", true);
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                if (token.matches(Operand.symbolRegEx)) {
                    token = convertSymbol(token);
                } else {
                    Matcher loMatcher = loFunctionPattern.matcher(token);
                    if (loMatcher.find()) {
                        token = "(" + loMatcher.group(1) + ")%256";
                    }
                    Matcher hiMatcher = hiFunctionPattern.matcher(token);
                    if (hiMatcher.find()) {
                        token = "(" + hiMatcher.group(1) + ")/256";
                    }
                }
                result.append(token);
            }
            expression = result.toString();
        }
        return expression;
    }

    private String convertSymbol(String symbol) {
        if (symbol.matches(Operand.lowerCaseSymbolRegEx) && !symbol.matches(Operand.numberRegEx)) {
            symbol += "_";
        }
        return symbol.replace("%", ".");
    }

    private String getLastInstruction(List<TMS9900Line> tms9900Lines) {
        for (int i = tms9900Lines.size() - 1; i >= 0; i--) {
            TMS9900Line tms9900Line = tms9900Lines.get(i);
            if (tms9900Line.getType() == TMS9900Line.Type.Instruction) {
                return tms9900Line.getInstruction();
            }
        }
        return "";
    }

    private boolean lastLineWasALabel(List<TMS9900Line> tms9900Lines) {
        for (int i = tms9900Lines.size() - 1; i >= 0; i--) {
            TMS9900Line tms9900Line = tms9900Lines.get(i);
            TMS9900Line.Type type = tms9900Line.getType();
            if (type == TMS9900Line.Type.Label) {
                return true;
            } else if (type == TMS9900Line.Type.Instruction || type == TMS9900Line.Type.Data) {
                return false;
            }
        }
        return false;
    }

    private List<BBCLine> readBBCFile(File bbcFile) throws IOException {
        List<BBCLine> bbcLine = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(bbcFile));
        String line = reader.readLine();
        while (line != null) {
            bbcLine.add(new BBCLine(line));
            line = reader.readLine();
        }
        return bbcLine;
    }

    private void writeTMS9900File(File tms9900File, List<TMS9900Line> tms9900Lines) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(tms9900File));
        for (TMS9900Line tms9900Line : tms9900Lines) {
            writer.write(tms9900Line.toString());
            writer.newLine();
        }
        writer.close();
    }
}
