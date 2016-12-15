package soot.spl.ifds;

public class SolverOperation {
	private String operand1;
	private String operand2;
	private Operator operator;
	
	public enum Operator {
		AND, OR, NEGATE, EQ, DUMMY, EQUIVALENCE, CNF, IMPLIES
	}
	
	@Override
	public String toString() {
		return "SolverOperation [operand1=" + operand1 + ", operand2="
				+ operand2 + ", operator=" + operator + "]";
	}

	public SolverOperation(String operand1, Operator operator) {
		
		if(operand1 == null)
		{
			throw new IllegalArgumentException("operand must not be null");
		}
		
		if(operator != Operator.NEGATE && operator != Operator.DUMMY && operator != Operator.CNF)
		{
			throw new IllegalArgumentException("NEGATE is the only supported unary operator");
		}
		
		this.operand1 = operand1;
		this.operand2 = null;
		this.operator = operator;
	}	

	public SolverOperation(String operand1, String operand2, Operator operator) {
		
		if(operand1 == null || operand2 == null)
		{
			throw new IllegalArgumentException("operand must not be null");
		}
		
		if(operator == null)
		{
			throw new IllegalArgumentException("operator must not be null");
		}
		
		this.operand1 = operand1;
		this.operand2 = operand2;
		this.operator = operator;
	}

	public String getOperand1() {
		return operand1;
	}

	public String getOperand2() {
		return operand2;
	}

	public Operator getOperator() {
		return operator;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ operand1.hashCode();
		result = prime * result
				+ (operand2 == null ? 0 : operand2.hashCode());
		result = prime * result
				+ operator.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SolverOperation other = (SolverOperation) obj;
		if (!operand1.equals(other.operand1))
			return false;
		if(operand2 == null) {
			if(other.operand2 != null) {
				return false;
			}
		} else if (!operand2.equals(other.operand2))
			return false;
		if (operator != other.operator)
			return false;
		return true;
	}

	
	
}
