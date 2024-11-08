import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BBCSourceConverter {

    private static final String regA = "ra";
    private static final String regX = "rx";
    private static final String regY = "ry";
    private static final String regSP = "rsp";
    private static final String regSPLowByte = "rsplb";
    private static final String regTmp = "rtmp";
    private static final String regTmpLowByte = "rtmplb";
    private static final String regOne = "rone";
    private static final String regMone = "rmone";
    private static final String regZero = "rzero";

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
                    if (!lastLineWasALabel(tms9900Lines)) {
                        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Empty));
                    }
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
                    String[] parts = bbcLine.getDirective().split(" ", 2);
                    if (parts.length > 0) {
                        String args = parts.length > 1 ? parts[1] : "";
                        String macroName = parts[0];
                        switch (macroName) {
                            case "TWOK":
                                tms9900Lines.add(convertMacroTWOK(charParam(args, 0), charParam(args, 1), bbcLine.getDirective() + " " + bbcLine.getComment()));
                                break;
                            case "ITEM":
                                tms9900Lines.add(convertMacroITEM(intParam(args, 0), intParam(args, 1), charParam(args, 2), intParam(args, 3), intParam(args, 4), bbcLine.getComment()));
                                break;
                            case "VERTEX":
                                tms9900Lines.add(convertMacroVERTEX(intParam(args, 0), intParam(args, 1), intParam(args, 2), intParam(args, 3), intParam(args, 4), intParam(args, 5), intParam(args, 6), intParam(args, 7), bbcLine.getComment()));
                                break;
                            case "EDGE":
                                tms9900Lines.add(convertMacroEDGE(intParam(args, 0), intParam(args, 1), intParam(args, 2), intParam(args, 3), intParam(args, 4), bbcLine.getComment()));
                                break;
                            case "FACE":
                                tms9900Lines.add(convertMacroFACE(intParam(args, 0), intParam(args, 1), intParam(args, 2), intParam(args, 3), bbcLine.getComment()));
                                break;
                            default:
                                tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "." + parts[0].toLowerCase() + (parts.length > 1 ? " " + parts[1] : "")));
                                break;
                        }
                    } else {
                        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "; " + bbcLine.getDirective()));
                    }
                    break;
                case ForStart:
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "; " + bbcLine.getDirective()));
                    insideFor = true;
                    break;
                case ForEnd:
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "; " + bbcLine.getDirective()));
                    insideFor = false;
                    convertForLoop(tms9900Lines);
                    break;
                default:
                    if (!insideMacro && !insideFor) {
                        switch (bbcLine.getType()) {
                            case Directive:
                                i += convertDirective(bbcLine, tms9900Lines);
                                break;
                            case Label:
                                if (lastLineWasALabel(tms9900Lines)) {
                                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, null, "equ  $"));
                                }
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
                                String[] variableAndValue = bbcLine.getInstruction().split("=", 2);
                                if (variableAndValue.length == 2) {
                                    createEquate(tms9900Lines, variableAndValue[0].trim(), variableAndValue[1], bbcLine.getComment());
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

    private char charParam(String args, int n) {
        return args.split(",")[n].replace("'", "").trim().charAt(0);
    }

    private int intParam(String args, int n) {
        return Util.parseInt(args.split(",")[n].trim());
    }

    private void createEquate(List<TMS9900Line> tms9900Lines, String symbol, String value, String comment) {
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Label, comment, convertSymbol(symbol)));
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, null, "equ " + convertExpression(value).trim()));
    }

    private int convertDirective(BBCLine bbcLine, List<TMS9900Line> tms9900Lines) {
        if (bbcLine.getDirective().startsWith("ORG")) {
            tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "aorg " + convertExpression(bbcLine.getDirective().substring(4))));
        } else if (bbcLine.getDirective().startsWith("SKIP")) {
            tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "bss " + convertExpression(bbcLine.getDirective().substring(5))));
        } else if (bbcLine.getDirective().startsWith("IF")) {
            tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), ".ifeq " + convertExpression(bbcLine.getDirective().substring(3)) + ", 1"));
        } else if (bbcLine.getDirective().startsWith("ELIF")) {
            tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getDirective(), ".else"));
        } else if (bbcLine.getDirective().startsWith("ELSE")) {
            tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), ".else"));
        } else if (bbcLine.getDirective().startsWith("ENDIF")) {
            tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), ".endif"));
        } else {
            tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Directive, bbcLine.getComment(), "; " + bbcLine.getDirective()));
        }
        return 0;
    }

    private int convertData(BBCLine bbcLine, List<TMS9900Line> tms9900Lines) {
        String bbcInstruction = bbcLine.getInstruction();
        String[] parts = bbcInstruction.split(" ", 2);
        String valuePart = parts.length > 1 ? parts[1] : "";
        String[] values = valuePart.split(",");
        String instruction = null;
        if (bbcInstruction.startsWith("EQUB")) {
            for (int i = 0; i < values.length; i++) {
                String value = values[i];
                Integer integer = Util.parseInt(value);
                if (integer != null) {
                    values[i] = Util.tiHexByte(integer);
                } else {
                    values[i] = convertExpression(value);
                }
            }
            instruction = "byte " + String.join(",", values);
        } else if (bbcInstruction.startsWith("EQUW")) {
            for (int i = 0; i < values.length; i++) {
                String value = values[i];
                Integer integer = Util.parseInt(value);
                if (integer != null) {
                    values[i] = Util.tiHexWord(integer, true);
                } else {
                    values[i] = convertExpression(value);
                }
            }
            instruction = "data " + String.join(",", values);
        } else if (bbcInstruction.startsWith("EQUD")) {
            for (int i = 0; i < values.length; i++) {
                String value = values[i];
                Integer integer = Util.parseInt(value);
                if (integer != null) {
                    values[i] = Util.tiHexWord(i & 0xffff, true) + ", " + Util.tiHexWord((i & 0xffff0000) >>> 16, true);
                } else {
                    values[i] = convertExpression(value);
                }
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
        List<TMS9900Line> linesBefore = new ArrayList<>();
        List<TMS9900Line> linesAfter = new ArrayList<>();
        switch (opcode) {
            case "ADC":
                switch (operand.getType()) {
                    case Immediate:
                        tms9900Line.setInstruction(".adi (" + convertOperand(operand) + ")");
                        break;
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
                break;
            case "AND":
                switch (operand.getType()) {
                    case Immediate:
                        tms9900Line.setInstruction("andi " + regA + "," + convertOperand(operand));
                        break;
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
                break;
            case "ASL":
                if (operand.getType() == Operand.Type.Accumulator) {
                    tms9900Line.setInstruction(".asla");
                } else {
                    tms9900Line.setInstruction(".asl " + convertOperand(operand));
                }
                break;
            case "BCC":
                tms9900Line.setInstruction("jnc  " + convertOperand(operand));
                break;
            case "BCS":
                tms9900Line.setInstruction("joc  " + convertOperand(operand));
                break;
            case "BEQ":
                tms9900Line.setInstruction("jeq  " + convertOperand(operand));
                break;
            case "BIT":
                tms9900Line.setInstruction(".bit " + convertOperand(operand));
                break;
            case "BMI":
                tms9900Line.setInstruction("jlt  " + convertOperand(operand));
                break;
            case "BNE":
                tms9900Line.setInstruction("jne  " + convertOperand(operand));
                break;
            case "BPL":
                tms9900Line.setInstruction("jgt  " + convertOperand(operand));
                break;
            case "BVC":
                tms9900Line.setInstruction("jno  " + convertOperand(operand));
                break;
            case "BVS":
                tms9900Line.setInstruction(".bvs " + convertOperand(operand));
                break;
            case "CLC":
                tms9900Line.setInstruction(".clc");
                break;
            case "CLI":
                tms9900Line.setInstruction("limi 2");
                break;
            case "CMP":
                switch (operand.getType()) {
                    case Immediate:
                        tms9900Line.setInstruction("ci   " + regA + "," + convertOperand(operand));
                        break;
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
                break;
            case "CPX":
                switch (operand.getType()) {
                    case Immediate:
                        tms9900Line.setInstruction("ci   " + regX + "," + convertOperand(operand));
                        break;
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
                break;
            case "CPY":
                switch (operand.getType()) {
                    case Immediate:
                        tms9900Line.setInstruction("ci   " + regY + "," + convertOperand(operand));
                        break;
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
                break;
            case "DEC":
                tms9900Line.setInstruction("sb   " + regOne + "," + regA);
                break;
            case "DEX":
                tms9900Line.setInstruction("sb   " + regOne + "," + regX);
                break;
            case "DEY":
                tms9900Line.setInstruction("sb   " + regOne + "," + regY);
                break;
            case "EOR":
                if (operand.getType() == Operand.Type.Immediate) {
                    tms9900Line.setInstruction(".eoi (" + convertOperand(operand) + ")");
                } else {
                    tms9900Line.setInstruction(".eor " + convertOperand(operand));
                }
                break;
            case "INC":
                tms9900Line.setInstruction("ab   " + regOne + "," + regA);
                break;
            case "INX":
                tms9900Line.setInstruction("ab   " + regOne + "," + regX);
                break;
            case "INY":
                tms9900Line.setInstruction("ab   " + regOne + "," + regY);
                break;
            case "JMP":
                if (operand.getType() == Operand.Type.Indirect) {
                    tms9900Line.setInstruction(".jmpi " + convertOperand(operand));
                } else {
                    tms9900Line.setInstruction("b    " + convertOperand(operand));
                }
                break;
            case "JSR":
                tms9900Line.setInstruction("li   " + regTmp + "," + convertOperand(operand).replaceFirst("@", ""));
                linesAfter.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "bl   @jsr"));
                break;
            case "LDA":
                switch (operand.getType()) {
                    case Immediate:
                        tms9900Line.setInstruction("li   " + regA + "," + convertOperand(operand));
                        break;
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
                switch (operand.getType()) {
                    case Immediate:
                        tms9900Line.setInstruction("ori  " + regA + "," + convertOperand(operand));
                        break;
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
                    tms9900Line.setInstruction("bl   @rola");
                } else {
                    tms9900Line.setInstruction("li   rarg1," + convertExpression(operand.getExpression()));
                    linesAfter.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "bl   @rol"));
                }
                break;
            case "ROR":
                if (operand.getType() == Operand.Type.Accumulator) {
                    tms9900Line.setInstruction("bl   @rora");
                } else {
                    tms9900Line.setInstruction("li   rarg1," + convertExpression(operand.getExpression()));
                    linesAfter.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "bl   @ror"));
                }
                break;
            case "RTS":
                tms9900Line.setInstruction("b    @rts");
                break;
            case "SBC":
                switch (operand.getType()) {
                    case Immediate:
                        tms9900Line.setInstruction(".sbi (" + convertOperand(operand) + ")");
                        break;
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
        tms9900Lines.addAll(linesBefore);
        tms9900Lines.add(tms9900Line);
        tms9900Lines.addAll(linesAfter);
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
                return "@" + convertExpression(operand.getExpression());
            case XIndexedIndirect:
                return "@" + convertExpression(operand.getExpression());
            case IndirectYIndexed:
                return "@" + convertExpression(operand.getExpression());
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
            // expression = expression.replace("P%", "$");
            expression = expression.replaceAll("([ +-])P%", "$1\\$");
            expression = expression.replace("&", ">");
            if (expression.startsWith("%")) {
                expression = expression.replaceFirst("%", ":");
            }
            if (loFunctionPattern.matcher(expression).matches()) {
                expression = convertLo(expression);
            } else if (hiFunctionPattern.matcher(expression).matches()) {
                expression = convertHi(expression);
            } else {
                StringBuilder result = new StringBuilder();
                StringTokenizer tokenizer = new StringTokenizer(expression, " +-*/", true);
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken();
                    token = token.replace("%", ".");
                    if (token.equals("AND")) {
                        token = "&";
                    } else if (token.equals("OR")) {
                        token = "|";
                    } else if (token.equals("EOR")) {
                        token = "^";
                    } else if (token.matches(Operand.symbolRegEx)) {
                        token = convertSymbol(token);
                    } else {
                        token = convertLo(token);
                        token = convertHi(token);
                    }
                    result.append(token);
                }
                expression = result.toString();
            }
        }
        return expression;
    }

    String convertLo(String expression) {
        Matcher loMatcher = loFunctionPattern.matcher(expression);
        if (loMatcher.find()) {
            expression = "(" + convertExpression(loMatcher.group(1)) + ")%256";
        }
        return expression;
    }

    String convertHi(String expression) {
        Matcher hiMatcher = hiFunctionPattern.matcher(expression);
        if (hiMatcher.find()) {
            expression = "(" + convertExpression(hiMatcher.group(1)) + ")/256";
        }
        return expression;
    }

    private String convertSymbol(String symbol) {
        if (symbol.matches(Operand.lowerCaseSymbolRegEx) && !symbol.matches(Operand.numberRegEx)) {
            symbol += "_";
        }
        return symbol.replace("%", ".");
    }

    private TMS9900Line convertMacroTWOK(char t, char k, String comment) {
        int ch = 0;
        if ( t == 'A' && k == 'L') { ch = 128; }
        if ( t == 'L' && k == 'E') { ch = 129; }
        if ( t == 'X' && k == 'E') { ch = 130; }
        if ( t == 'G' && k == 'E') { ch = 131; }
        if ( t == 'Z' && k == 'A') { ch = 132; }
        if ( t == 'C' && k == 'E') { ch = 133; }
        if ( t == 'B' && k == 'I') { ch = 134; }
        if ( t == 'S' && k == 'O') { ch = 135; }
        if ( t == 'U' && k == 'S') { ch = 136; }
        if ( t == 'E' && k == 'S') { ch = 137; }
        if ( t == 'A' && k == 'R') { ch = 138; }
        if ( t == 'M' && k == 'A') { ch = 139; }
        if ( t == 'I' && k == 'N') { ch = 140; }
        if ( t == 'D' && k == 'I') { ch = 141; }
        if ( t == 'R' && k == 'E') { ch = 142; }
        if ( t == 'A' && k == '?') { ch = 143; }
        if ( t == 'E' && k == 'R') { ch = 144; }
        if ( t == 'A' && k == 'T') { ch = 145; }
        if ( t == 'E' && k == 'N') { ch = 146; }
        if ( t == 'B' && k == 'E') { ch = 147; }
        if ( t == 'R' && k == 'A') { ch = 148; }
        if ( t == 'L' && k == 'A') { ch = 149; }
        if ( t == 'V' && k == 'E') { ch = 150; }
        if ( t == 'T' && k == 'I') { ch = 151; }
        if ( t == 'E' && k == 'D') { ch = 152; }
        if ( t == 'O' && k == 'R') { ch = 153; }
        if ( t == 'Q' && k == 'U') { ch = 154; }
        if ( t == 'A' && k == 'N') { ch = 155; }
        if ( t == 'T' && k == 'E') { ch = 156; }
        if ( t == 'I' && k == 'S') { ch = 157; }
        if ( t == 'R' && k == 'I') { ch = 158; }
        if ( t == 'O' && k == 'N') { ch = 159; }
        return new TMS9900Line(TMS9900Line.Type.Data, comment, "byte " + ch + " ^ RE");
    }

    private TMS9900Line convertMacroITEM(int price, int factor, char units, int quantity, int mask, String comment) {
        int s = factor < 0 ? 1 << 7 : 0;
        int u;
        if (units == 't') {
            u = 0;
        } else if (units == 'k') {
            u = 1 << 5;
        } else {
            u = 1 << 6;
        }
        int e = Math.abs(factor);
        return new TMS9900Line(TMS9900Line.Type.Data, comment, "byte " + Util.tiHexByte(price) + ", " + Util.tiHexByte(s + u + e) + ", " + Util.tiHexByte(quantity) + ", " + Util.tiHexByte(mask));
    }

    private TMS9900Line convertMacroVERTEX(int x, int y, int z, int face1, int face2, int face3, int face4, int visibility, String comment) {
        int sx = x < 0 ? 1 << 7 : 0;
        int sy = y < 0 ? 1 << 6 : 0;
        int sz = z < 0 ? 1 << 5 : 0;
        int s = sx | sy | sz | visibility;
        int f1 = face1 + (face2 << 4);
        int f2 = face3 + (face4 << 4);
        int ax = Math.abs(x);
        int ay = Math.abs(y);
        int az = Math.abs(z);
        return new TMS9900Line(TMS9900Line.Type.Data, comment, "byte " + Util.tiHexByte(ax) + ", " + Util.tiHexByte(ay) + ", " + Util.tiHexByte(az) + ", " + Util.tiHexByte(s) + ", " + Util.tiHexByte(f1) + ", " + Util.tiHexByte(f2));
    }

    private TMS9900Line convertMacroEDGE(int vertex1, int vertex2, int face1, int face2, int visibility, String comment) {
        int f = face1 + (face2 << 4);
        return new TMS9900Line(TMS9900Line.Type.Data, comment, "byte " + Util.tiHexByte(visibility) + ", " + Util.tiHexByte(f) + ", " + Util.tiHexByte(vertex1 << 2) + ", " + Util.tiHexByte(vertex2 << 2));
    }

    private TMS9900Line convertMacroFACE(int normal_x, int normal_y, int normal_z, int visibility, String comment) {
        int sx = normal_x < 0 ? 1 << 7 : 0;
        int sy = normal_y < 0 ? 1 << 6 : 0;
        int sz = normal_z < 0 ? 1 << 5 : 0;
        int s = sx | sy | sz | visibility;
        int ax = Math.abs(normal_x);
        int ay = Math.abs(normal_y);
        int az = Math.abs(normal_z);
        return new TMS9900Line(TMS9900Line.Type.Data, comment, "byte " + Util.tiHexByte(s) + ", " + Util.tiHexByte(ax) + ", " + Util.tiHexByte(ay) + ", " + Util.tiHexByte(az));
    }

    private void convertForLoop(List<TMS9900Line> tms9900Lines) {
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Empty));
        String label = getLastLabel(tms9900Lines);
        if (label != null) {
            switch (label) {
                case "UNIV":
                    for (int i = 0; i <= 12; i++) {
                        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Data, null, "byte (K. + " + i + " * NI.) % 256, (K. + " + i + " * NI.) / 256"));
                    }
                    break;
                case "SNE":
                    for (int i = 0; i <= 31; i++) {
                        double n = Math.abs(Math.sin((i / 64.0) * 2 * Math.PI));
                        int b;
                        if (n >= 1) {
                            b = 255;
                        } else {
                            b = (int) Math.floor(256 * n + 0.5);
                        }
                        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Data, null, "byte " + Util.tiHexByte(b)));
                    }
                    break;
                case "ACT":
                    for (int i = 0; i <= 31; i++) {
                        int b = (int) Math.floor((128 / Math.PI) * Math.atan((i / 32.0) + 0.5));
                        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Data, null, "byte " + Util.tiHexByte(b)));
                    }
                    break;
            }
        }
    }

    private boolean lastLineWasALabel(List<TMS9900Line> tms9900Lines) {
        for (int i = tms9900Lines.size() - 1; i >= 0; i--) {
            TMS9900Line tms9900Line = tms9900Lines.get(i);
            TMS9900Line.Type type = tms9900Line.getType();
            if (type == TMS9900Line.Type.Label) {
                return true;
            } else if (type == TMS9900Line.Type.Instruction || type == TMS9900Line.Type.Data || type == TMS9900Line.Type.Directive) {
                return false;
            }
        }
        return false;
    }

    private String getLastLabel(List<TMS9900Line> tms9900Lines) {
        for (int i = tms9900Lines.size() - 1; i >= 0; i--) {
            TMS9900Line tms9900Line = tms9900Lines.get(i);
            if (tms9900Line.getType() == TMS9900Line.Type.Label) {
                return tms9900Line.getLabel();
            }
        }
        return null;
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
