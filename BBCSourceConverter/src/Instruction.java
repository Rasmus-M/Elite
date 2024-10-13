public class Instruction {

    private String instruction;
    private String opcode;
    private String operands;
    private Operand operand;

    public Instruction(String instruction) {
        this.instruction = instruction;
        String[] instrParts = instruction.split(" ", 2);
        opcode = instrParts[0];
        operands = instrParts.length > 1 ? instrParts[1] : null;
        operand = operands != null ? new Operand(operands, opcode) : null;
    }

    public String getOpcode() {
        return opcode;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getOperands() {
        return operands;
    }

    public Operand getOperand() {
        return operand;
    }

    public String toString() {
        return instruction;
    }
}
