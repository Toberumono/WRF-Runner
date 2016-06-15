package toberumono.wrf.scope;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BinaryOperator;
import java.util.regex.Pattern;

import toberumono.lexer.BasicDescender;
import toberumono.lexer.BasicLexer;
import toberumono.lexer.BasicRule;
import toberumono.lexer.util.DefaultIgnorePatterns;
import toberumono.lexer.util.NumberPatterns;
import toberumono.structures.sexpressions.BasicConsType;
import toberumono.structures.sexpressions.ConsCell;
import toberumono.structures.sexpressions.ConsType;

public class ScopedFormulaProcessor {
	private static final Lock lock = new ReentrantLock();
	private static final ConsType PARENTHESES = new BasicConsType("parentheses");
	private static final ConsType VARIABLE = new BasicConsType("variable");
	private static final ConsType NUMBER = new BasicConsType("number");
	private static final ConsType STRING = new BasicConsType("string", "'", "'");
	private static final ConsType BOOLEAN = new BasicConsType("boolean");
	private static final ConsType ARRAY = new BasicConsType("array");
	private static final ConsType OBJECT = new BasicConsType("object");
	private static final ConsType NULL = new BasicConsType("null");
	private static final ConsType UNKNOWN = new BasicConsType("unknown");
	private static final ConsType OPERATOR = new BasicConsType("operator");
	private static final ConsType KEYWORD = new BasicConsType("keyword");
	
	private static Operator addition, subtraction, multiplication, division, modulus, exponent;
	
	private static BasicLexer lexer = null;
	
	public static BasicLexer getLexer() {
		try {
			lock.lock();
			if (lexer != null)
				return lexer;
			addition = new Operator(6) {
				@Override
				public Object apply(Object t, Object u) {
					if (t instanceof String) {
						if (u instanceof String)
							return ((String) t) + ((String) u);
						else
							return ((String) t) + u.toString();
					}
					if (u instanceof String)
						return t.toString() + ((String) u);
					if (t instanceof Number && u instanceof Number)
						return ((Number) t).doubleValue() + ((Number) u).doubleValue();
					throw new IllegalArgumentException(t.getClass().getName() + " and " + u.getClass().getName() + " is not a valid argument combination for the + operator.");
				}
			};
			subtraction = new Operator(6) {
				@Override
				public Object apply(Object t, Object u) {
					if (t instanceof Number && u instanceof Number)
						return ((Number) t).doubleValue() - ((Number) u).doubleValue();
					throw new IllegalArgumentException(t.getClass().getName() + " and " + u.getClass().getName() + " is not a valid argument combination for the - operator.");
				}
			};
			multiplication = new Operator(5) {
				@Override
				public Object apply(Object t, Object u) {
					if (t instanceof Number && u instanceof Number)
						return ((Number) t).doubleValue() * ((Number) u).doubleValue();
					throw new IllegalArgumentException(t.getClass().getName() + " and " + u.getClass().getName() + " is not a valid argument combination for the * operator.");
				}
			};
			division = new Operator(5) {
				@Override
				public Object apply(Object t, Object u) {
					if (t instanceof Number && u instanceof Number)
						return ((Number) t).doubleValue() / ((Number) u).doubleValue();
					throw new IllegalArgumentException(t.getClass().getName() + " and " + u.getClass().getName() + " is not a valid argument combination for the / operator.");
				}
			};
			modulus = new Operator(5) {
				@Override
				public Object apply(Object t, Object u) {
					if (t instanceof Number && u instanceof Number)
						return ((Number) t).doubleValue() % ((Number) u).doubleValue();
					throw new IllegalArgumentException(t.getClass().getName() + " and " + u.getClass().getName() + " is not a valid argument combination for the % operator.");
				}
			};
			exponent = new Operator(4) {
				@Override
				public Object apply(Object t, Object u) {
					if (t instanceof Number && u instanceof Number)
						return Math.pow(((Number) t).doubleValue(), ((Number) u).doubleValue());
					throw new IllegalArgumentException(t.getClass().getName() + " and " + u.getClass().getName() + " is not a valid argument combination for the ^ operator.");
				}
			};
			lexer = new BasicLexer(DefaultIgnorePatterns.WHITESPACE);
			lexer.addRule("string'", new BasicRule(Pattern.compile("'(([^'\\\\]+|\\\\['\\\\tbnrf\"])*)'"), (l, s, m) -> new ConsCell(m.group(1), STRING)));
			lexer.addRule("string\"", new BasicRule(Pattern.compile("\"(([^\"\\\\]+|\\\\['\\\\tbnrf\"])*)\""), (l, s, m) -> new ConsCell(m.group(1), STRING)));
			lexer.addRule("inherit", new BasicRule(Pattern.compile("inherit", Pattern.LITERAL), (l, s, m) -> new ConsCell(m.group(), KEYWORD)));
			lexer.addRule("variable", new BasicRule(Pattern.compile("([a-zA-Z_]\\w*)(\\.\\w+)*"), (l, s, m) -> new ConsCell(new VariableAccess(m.group()), VARIABLE)));
			lexer.addRule("number", new BasicRule(NumberPatterns.DOUBLE.getPattern(), (l, s, m) -> new ConsCell(Double.parseDouble(m.group()), NUMBER)));
			lexer.addRule("addition", new BasicRule(Pattern.compile("+", Pattern.LITERAL), (l, s, m) -> new ConsCell(addition, OPERATOR)));
			lexer.addRule("subtraction", new BasicRule(Pattern.compile("-", Pattern.LITERAL), (l, s, m) -> new ConsCell(subtraction, OPERATOR)));
			lexer.addRule("multiplication", new BasicRule(Pattern.compile("*", Pattern.LITERAL), (l, s, m) -> new ConsCell(multiplication, OPERATOR)));
			lexer.addRule("division", new BasicRule(Pattern.compile("/", Pattern.LITERAL), (l, s, m) -> new ConsCell(division, OPERATOR)));
			lexer.addRule("modulus", new BasicRule(Pattern.compile("%", Pattern.LITERAL), (l, s, m) -> new ConsCell(modulus, OPERATOR)));
			lexer.addRule("exponent", new BasicRule(Pattern.compile("^", Pattern.LITERAL), (l, s, m) -> new ConsCell(exponent, OPERATOR)));
			lexer.addDescender("parentheses", new BasicDescender("(", ")", PARENTHESES));
			return lexer;
		}
		finally {
			lock.unlock();
		}
	}
	
	public static ConsCell processEquation(ConsCell input, Scope scope, String fieldName) throws InvalidVariableAccessException {
		ConsCell equation;
		if (input.getCarType() == OPERATOR) {
			Object accessed = new VariableAccess(fieldName).accessScope(scope);
			equation = new ConsCell(accessed, getTypeForObject(accessed));
		}
		else {
			equation = new ConsCell();
		}
		ConsCell head = equation;
		for (ConsCell current = input; current != null; current = current.getNext()) {
			if (current.getCarType() == PARENTHESES)
				head = head.append(processEquation((ConsCell) current.getCar(), scope, fieldName));
			else if (current.getCarType() == KEYWORD) {
				switch ((String) current.getCar()) {
					case "inherit":
						Object accessed = new VariableAccess("parent." + fieldName).accessScope(scope);
						head = head.append(new ConsCell(accessed, getTypeForObject(accessed)));
						break;
					default:
						throw new IllegalArgumentException(current.getCar() + " is not a valid keyword.");
				}
			}
			else if (current.getCarType() == VARIABLE) {
				Object accessed = ((VariableAccess) current.getCar()).accessScope(scope);
				head = head.append(new ConsCell(accessed, getTypeForObject(accessed)));
			}
			else
				head = head.append(current.singular());
		}
		head = equation;
		ConsCell previous = null, left = null, right = head.getNext(), next = right == null ? null : right.getNext();
		while (head != null && head.getCarType() != OPERATOR) {
			previous = left;
			left = head;
			head = right;
			right = next;
			next = right == null ? null : right.getNext();
		}
		if (head == null) //Then there were no operators
			return equation;
		for (int move = getMovementDirection(previous, head, next); head != null && (!head.isFirst() || !head.isLast()); move = getMovementDirection(previous, head, next)) {
			if (move == 0) {
				Object out = ((Operator) head.getCar()).apply(left.getCar(), right.getCar());
				left.remove();
				right.remove();
				head.setCar(out, getTypeForObject(out));
				if (equation == left)
					equation = head;
				if (previous != null) { //Move two steps to the left
					right = left;
					next = head;
					head = previous;
					left = head.getPrevious();
					previous = left != null ? left.getPrevious() : null;
				}
				else if (next != null) { //Move two steps to the right
					left = right;
					previous = head;
					head = next;
					right = head.getNext();
					next = right != null ? right.getNext() : null;
				}
				else { //There isn't anywhere left to move
					break;
				}
			}
		}
		return equation;
	}
	
	private static int getMovementDirection(ConsCell prev, ConsCell head, ConsCell next) {
		int h = ((Operator) head.getCar()).getPrecedence();
		if (prev != null && prev.getCar() instanceof Operator && ((Operator) prev.getCar()).getPrecedence() < h)
			return -1;
		if (next != null && next.getCar() instanceof Operator && ((Operator) next.getCar()).getPrecedence() < h)
			return 1;
		return 0;
	}
	
	private static ConsType getTypeForObject(Object o) {
		if (o == null)
			return NULL;
		if (o instanceof Number)
			return NUMBER;
		if (o instanceof String)
			return STRING;
		if (o instanceof Boolean)
			return BOOLEAN;
		if (o instanceof List)
			return ARRAY;
		if (o instanceof Map)
			return OBJECT;
		return UNKNOWN;
	}
}

abstract class Operator implements BinaryOperator<Object> {
	private final int precedence;
	
	public Operator(int precedence) {
		this.precedence = precedence;
	}
	
	public int getPrecedence() {
		return precedence;
	}
}