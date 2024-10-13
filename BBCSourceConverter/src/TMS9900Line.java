public class TMS9900Line {

    public enum Type {
        Empty,
        Label,
        Directive,
        Comment,
        ContinuationCommentData,
        ContinuationCommentInstruction,
        Data,
        Instruction
    }

    private final Type type;
    private String label;
    private String directive;
    private String instruction;
    private String bbcInstruction;
    private String comment;

    public TMS9900Line(Type type) {
        this.type = type;
    }

    public TMS9900Line(Type type, String comment) {
        this.type = type;
        this.comment = comment;
    }

    public TMS9900Line(Type type, String comment, String instruction) {
        this.type = type;
        this.comment = comment;
        this.instruction = instruction;
    }

    public Type getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDirective() {
        return directive;
    }

    public void setDirective(String directive) {
        this.directive = directive;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getBbcInstruction() {
        return bbcInstruction;
    }

    public void setBbcInstruction(String bbcInstruction) {
        this.bbcInstruction = bbcInstruction;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String toString() {
        switch (type) {
            case Empty:
                return "";
            case Label:
                String labelWithColon = label + (label.equals("!") ? "" : ":");
                return labelWithColon + (comment != null ? Util.space(59 - labelWithColon.length()) + "; " + comment : "");
            case Directive:
                String directiveCommentIndent = Util.space(Math.max(69 - 17 - directive.length(), 1));
                return Util.space(7) + directive + (comment != null ? directiveCommentIndent + "; " + comment : "");
            case Comment:
                return "* " + comment;
            case ContinuationCommentData:
                return Util.space(69) + "; " + comment;
            case ContinuationCommentInstruction:
                return Util.space(59) + "; " + comment;
            case Data:
                String dataCommentIndent = Util.space(Math.max(69 - 7 - instruction.length(), 1));
                return Util.space(7) + instruction + (comment != null ? dataCommentIndent + "; " + comment : "");
            case Instruction:
                String commentIndent = Util.space(Math.max(39 - 7 - instruction.length(), 1));
                String combinedComment = Util.fit(bbcInstruction != null ? bbcInstruction : "", 17) + " " + (comment != null ? "; " + comment : "");
                return Util.space(7) + instruction + commentIndent + "; " + combinedComment;
        }
        return "";
    }
}
