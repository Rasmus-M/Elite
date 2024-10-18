import java.util.regex.Pattern;

public class BBCLine {

    public enum Type {
        Empty,
        Directive,
        Label,
        Comment,
        ContinuationComment,
        Data,
        Instruction,
        MacroStart,
        MacroEnd,
        MacroCall,
        ForStart,
        ForEnd,
        Variable
    }

    private Type type;
    private char typeChar;
    private String instruction;
    private String directive;
    private String comment;
    private String label;

    public BBCLine(String line) {
        parseLine(line);
    }

    private void parseLine(String line) {
        if (line.isEmpty()) {
            type = Type.Empty;
        } else {
            typeChar = line.charAt(0);
            if (typeChar == '\\') {
                comment = line.length() > 1 ? line.substring( 1).trim() : "";
                type = Type.Comment;
            } else if (typeChar == '.') {
                label = line.length() > 1 ? line.substring( 1).trim() : "";
                type = Type.Label;
            } else {
                int commentPos = line.indexOf(" \\");
                if (commentPos != -1) {
                    if (line.length() > commentPos + 2) {
                        comment = line.substring(commentPos + 2).trim();
                    } else {
                        comment = "";
                    }
                    line = line.substring(0, commentPos);
                }
                if (line.trim().isEmpty()) {
                    type = Type.ContinuationComment;
                }
                if (type == null) {
                    instruction = line.trim();
                    if (instruction.startsWith("MACRO")) {
                        directive = instruction;
                        type = Type.MacroStart;
                    } else if (instruction.startsWith("ENDMACRO")) {
                        directive = instruction;
                        type = Type.MacroEnd;
                    } else if (instruction.startsWith("CHAR") || instruction.startsWith("TWOK") || instruction.startsWith("RTOK") || instruction.startsWith("CONT") || instruction.startsWith("ITEM") || instruction.startsWith("VERTEX") || instruction.startsWith("EDGE") || instruction.startsWith("FACE")) {
                        directive = instruction;
                        type = Type.MacroCall;
                    } else if (instruction.trim().startsWith("FOR")) {
                        directive = instruction;
                        type = Type.ForStart;
                    } else if (instruction.trim().startsWith("NEXT")) {
                        directive = instruction;
                        type = Type.ForEnd;
                    } else if (instruction.startsWith("ORG") || instruction.startsWith("SKIP") || instruction.startsWith("PRINT") || instruction.startsWith("INCLUDE") || instruction.startsWith("SAVE") || instruction.startsWith("GUARD") || instruction.startsWith("IF") || instruction.startsWith("ELIF") || instruction.startsWith("ELSE") || instruction.startsWith("ENDIF")) {
                        directive = instruction;
                        type = Type.Directive;
                    } else if (instruction.startsWith("EQUB") || instruction.startsWith("EQUW") || instruction.startsWith("EQUD") || instruction.startsWith("EQUS")) {
                        type = Type.Data;
                    } else if (instruction.contains("=")) {
                        type = Type.Variable;
                    } else {
                        type = Type.Instruction;
                    }
                }
            }
        }
    }

    public Type getType() {
        return type;
    }

    public char getTypeChar() {
        return typeChar;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getDirective() {
        return directive;
    }

    public String getComment() {
        return comment;
    }

    public String getLabel() {
        return label;
    }
}
