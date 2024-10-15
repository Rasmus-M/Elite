import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BBCSourceConverter {

    private static final String regA = "A";
    private static final String regX = "X";
    private static final String regY = "Y";
    private static final String regSP = "SP";
    private static final String regSPLowByte = "SPlb";
    private static final String regTmp = "tmp";
    private static final String regTmpLowByte = "tmplb";
    private static final String regOne = "one";
    private static final String regMone = "mone";

    Pattern loFunctionPattern = Pattern.compile("LO\\((" + Operand.expressionRegEx + ")\\)");
    Pattern hiFunctionPattern = Pattern.compile("HI\\((" + Operand.expressionRegEx + ")\\)");

    public BBCSourceConverter() {
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
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "; " + bbcLine.getDirective()));
                    insideMacro = true;
                    break;
                case MacroEnd:
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "; " + bbcLine.getDirective()));
                    insideMacro = false;
                    break;
                case MacroCall:
                    String[] parts = bbcLine.getDirective().split(" ", 1);
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "." + parts[0].toLowerCase() + (parts.length > 1 ? " " + parts[1] : "")));
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
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, null, "equ " + convertExpression(value)));
    }

    private void createRegEquate(List<TMS9900Line> tms9900Lines, String symbol, String value, String comment) {
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Label, comment, convertSymbol(symbol)));
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, null, "requ " + convertExpression(value)));
    }

    private int convertDirective(BBCLine bbcLine, List<TMS9900Line> tms9900Lines) {
        if (bbcLine.getDirective().startsWith("ORG")) {
            tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "aorg " + convertExpression(bbcLine.getDirective().substring(4))));
        } else if (bbcLine.getDirective().startsWith("SKIP")) {
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
            case "ADC":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction(".adi (" + convertOperand(operand) + ")");
                } else {
                    switch (operand.getType()) {
                        case XIndexedIndirect:
                            tms9900Line.setInstruction(".adc_x_idx_ind " + convertOperand(operand) + "," + regA);
                            break;
                        case IndirectYIndexed:
                            tms9900Line.setInstruction(".adc_ind_y_idx " + convertOperand(operand) + "," + regA);
                            break;
                        default:
                            tms9900Line.setInstruction(".adc " + convertOperand(operand) + "," + regA);
                            break;
                    }
                }
                break;
            case "AND":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction("andi " + regA + "," + convertOperand(operand));
                } else {
                    switch (operand.getType()) {
                        case XIndexedIndirect:
                            tms9900Line.setInstruction(".and_x_idx_ind " + convertOperand(operand) + "," + regA);
                            break;
                        case IndirectYIndexed:
                            tms9900Line.setInstruction(".and_ind_y_idx " + convertOperand(operand) + "," + regA);
                            break;
                        default:
                            tms9900Line.setInstruction(".and " + convertOperand(operand));
                            break;
                    }
                }
                break;
            case "ASL":
                if (operand.getType() == Operand.Type.Accumulator) {
                    tms9900Line.setInstruction(".asla");
                } else {
                    tms9900Line.setInstruction(".asl (" + convertExpression(operand.getExpression()) + ")");
                }
                break;
            case "BCC":
                tms9900Line.setInstruction("jnc  " + convertExpression(operand.getExpression()));
                break;
            case "BCS":
                tms9900Line.setInstruction("joc  " + convertExpression(operand.getExpression()));
                break;
            case "BEQ":
                tms9900Line.setInstruction("jeq  " + convertExpression(operand.getExpression()));
                break;
            case "BIT":
                tms9900Line.setInstruction(".bit (" + convertExpression(operand.getExpression()) + ")");
                break;
            case "BMI":
                tms9900Line.setInstruction("jlt  " + convertExpression(operand.getExpression()));
                break;
            case "BNE":
                tms9900Line.setInstruction("jne  " + convertExpression(operand.getExpression()));
                break;
            case "BPL":
                tms9900Line.setInstruction("jgt  " + convertExpression(operand.getExpression()));
                break;
            case "BVC":
                tms9900Line.setInstruction("jno  " + convertExpression(operand.getExpression()));
                break;
            case "BVS":
                tms9900Line.setInstruction(".bvs " + convertExpression(operand.getExpression()));
                break;
            case "CLC":
                tms9900Line.setInstruction(".clc");
                break;
            case "CLI":
                tms9900Line.setInstruction("limi 2");
                break;
            case "CMP":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction("ci   " + regA + "," + convertOperand(operand));
                } else {
                    switch (operand.getType()) {
                        case XIndexedIndirect:
                            tms9900Line.setInstruction(".cmp_x_idx_ind " + convertOperand(operand) + "," + regA);
                            break;
                        case IndirectYIndexed:
                            tms9900Line.setInstruction(".cmp_ind_y_idx " + convertOperand(operand) + "," + regA);
                            break;
                        default:
                            tms9900Line.setInstruction("cb   " + convertOperand(operand) + "," + regA);
                            break;
                    }
                }
                break;
            case "CPX":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction("ci   " + regX + "," + convertOperand(operand));
                } else {
                    switch (operand.getType()) {
                        case XIndexedIndirect:
                            tms9900Line.setInstruction(".cmp_x_idx_ind " + convertOperand(operand) + "," + regX);
                            break;
                        case IndirectYIndexed:
                            tms9900Line.setInstruction(".cmp_ind_y_idx " + convertOperand(operand) + "," + regX);
                            break;
                        default:
                            tms9900Line.setInstruction("cb   " + convertOperand(operand) + "," + regX);
                            break;
                    }
                }
                break;
            case "CPY":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction("ci   " + regY + "," + convertOperand(operand));
                } else {
                    switch (operand.getType()) {
                        case XIndexedIndirect:
                            tms9900Line.setInstruction(".cmp_x_idx_ind " + convertOperand(operand) + "," + regY);
                            break;
                        case IndirectYIndexed:
                            tms9900Line.setInstruction(".cmp_ind_y_idx " + convertOperand(operand) + "," + regY);
                            break;
                        default:
                            tms9900Line.setInstruction("cb   " + convertOperand(operand) + "," + regY);
                            break;
                    }
                }
                break;
            case "DEC":
                tms9900Line.setInstruction("sb   one," + regA);
                break;
            case "DEX":
                tms9900Line.setInstruction("sb   one," + regX);
                break;
            case "DEY":
                tms9900Line.setInstruction("sb   one," + regY);
                break;
            case "EOR":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction(".eoi " + convertExpression(operand.getExpression()));
                } else {
                    tms9900Line.setInstruction(".eor (" + convertExpression(operand.getExpression()) + ")");
                }
                break;
            case "INC":
                tms9900Line.setInstruction("ab   one," + regA);
                break;
            case "INX":
                tms9900Line.setInstruction("ab   one," + regX);
                break;
            case "INY":
                tms9900Line.setInstruction("ab   one," + regY);
                break;
            case "JMP":
                tms9900Line.setInstruction("b    @" + convertExpression(operand.getExpression()));
                break;
            case "JSR":
                tms9900Line.setInstruction(".jsr @" + convertExpression(operand.getExpression()));
                break;
            case "LDA":
                    if (operand.getType() == Operand.Type.Immediate) {
                        tms9900Line.setInstruction("li   " + regA + "," + convertOperand(operand));
                    } else {
                        switch (operand.getType()) {
                            case XIndexedIndirect:
                                tms9900Line.setInstruction(".ld_x_idx_ind " + convertOperand(operand) + "," + regA);
                                break;
                            case IndirectYIndexed:
                                tms9900Line.setInstruction(".ld_ind_y_idx " + convertOperand(operand) + "," + regA);
                                break;
                            default:
                                tms9900Line.setInstruction("movb " + convertOperand(operand) + "," + regA);
                                break;
                        }
                    }
                break;
            case "LDX":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction("li   " + regX + "," + convertOperand(operand));
                } else {
                    tms9900Line.setInstruction("movb " + convertOperand(operand) + "," + regX);
                }
                break;
            case "LDY":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction("li   " + regY + "," + convertOperand(operand));
                } else {
                    tms9900Line.setInstruction("movb " + convertOperand(operand) + "," + regY);
                }
                break;
            case "LSR":
                if (operand.getType() == Operand.Type.Accumulator) {
                    tms9900Line.setInstruction("srl  " + regA + ",1");
                } else {
                    tms9900Line.setInstruction(".lsr " + convertOperand(operand));
                }
                break;
            case "NOP":
                tms9900Line.setInstruction("nop");
                break;
            case "ORA":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction("ori  " + regA + "," + convertOperand(operand));
                } else {
                    switch (operand.getType()) {
                        case XIndexedIndirect:
                            tms9900Line.setInstruction(".or_x_idx_ind " + convertOperand(operand) + "," + regA);
                            break;
                        case IndirectYIndexed:
                            tms9900Line.setInstruction(".or_ind_y_idx " + convertOperand(operand) + "," + regA);
                            break;
                        default:
                            tms9900Line.setInstruction("socb " + convertOperand(operand) + "," + regA);
                            break;
                    }
                }
                break;
            case "PLA":
                tms9900Line.setInstruction(".pla");
                break;
            case "PLP":
                tms9900Line.setInstruction(".plp");
                break;
            case "PHA":
                tms9900Line.setInstruction(".pha");
                break;
            case "PHP":
                tms9900Line.setInstruction(".php");
                break;
            case "ROL":
                if (operand.getType() == Operand.Type.Accumulator) {
                    tms9900Line.setInstruction(".rola");
                } else {
                    tms9900Line.setInstruction(".rol " + convertOperand(operand));
                }
                break;
            case "ROR":
                if (operand.getType() == Operand.Type.Accumulator) {
                    tms9900Line.setInstruction(".rora");
                } else {
                    tms9900Line.setInstruction(".ror " + convertOperand(operand));
                }
                break;
            case "RTS":
                tms9900Line.setInstruction(".rts");
                break;
            case "SBC":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction(".adi " + regA + ",-" + convertOperand(operand));
                } else {
                    switch (operand.getType()) {
                        case XIndexedIndirect:
                            tms9900Line.setInstruction(".sbc_x_idx_ind " + convertOperand(operand) + "," + regA);
                            break;
                        case IndirectYIndexed:
                            tms9900Line.setInstruction(".sbc_ind_y_idx " + convertOperand(operand) + "," + regA);
                            break;
                        default:
                            tms9900Line.setInstruction(".sbc " + convertOperand(operand) + "," + regA);
                            break;
                    }
                }
                break;
            case "SEC":
                tms9900Line.setInstruction(".sec");
                break;
            case "SEI":
                tms9900Line.setInstruction("limi 0");
                break;
            case "STA":
                switch (operand.getType()) {
                    case XIndexedIndirect:
                        tms9900Line.setInstruction(".st_x_idx_ind " + convertOperand(operand) + "," + regA);
                        break;
                    case IndirectYIndexed:
                        tms9900Line.setInstruction(".st_ind_y_idx " + convertOperand(operand) + "," + regA);
                        break;
                    default:
                        tms9900Line.setInstruction("movb " + regA + "," + convertOperand(operand));
                        break;
                }
                break;
            case "STX":
                tms9900Line.setInstruction("movb " + regX + "," + convertOperand(operand));
                break;
            case "STY":
                tms9900Line.setInstruction("movb " + regY + "," + convertOperand(operand));
                break;
            case "TAX":
                tms9900Line.setInstruction("movb " + regA + "," + regX);
                break;
            case "TAY":
                tms9900Line.setInstruction("movb " + regA + "," + regY);
                break;
            case "TSX":
                tms9900Line.setInstruction("movb @" + regSPLowByte + "," + regX);
                break;
            case "TXA":
                tms9900Line.setInstruction("movb " + regX + "," + regA);
                break;
            case "TYA":
                tms9900Line.setInstruction("movb " + regY + "," + regA);
                break;
            case "TXS":
                tms9900Line.setInstruction("movb " + regX + ",@" + regSPLowByte);
                break;
            default:
                tms9900Line.setInstruction("; " + instruction);
                System.out.println("Unhandled " + instruction);
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
                return "(" + convertExpression(operand.getExpression()) + ")";
            case IndirectYIndexed:
                return "(" + convertExpression(operand.getExpression()) + ")";
            case Relative:
                return convertExpression(operand.getExpression());
            case ZeroPageXIndexed:
                return "*" + regX;
            case ZeroPageYIndexed:
                return "*" + regY;
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
