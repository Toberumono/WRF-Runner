package toberumono.wrf.scope;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.Pattern;

import toberumono.lexer.BasicDescender;
import toberumono.lexer.BasicLanguage;
import toberumono.lexer.BasicLexer;
import toberumono.lexer.BasicRule;
import toberumono.lexer.base.Lexer;
import toberumono.lexer.util.DefaultIgnorePatterns;
import toberumono.lexer.util.NumberPatterns;
import toberumono.structures.sexpressions.BasicConsType;
import toberumono.structures.sexpressions.ConsCell;
import toberumono.structures.sexpressions.ConsType;
import toberumono.structures.tuples.Pair;

/**
 * Processing logic that allows the {@link Scope} system to work.
 * 
 * @author Toberumono
 */
public class ScopedFormulaProcessor {
	private static final Lock lock = new ReentrantLock();
	private static final ConsType PARENTHESES = new BasicConsType("parentheses", "(", ")");
	private static final ConsType VARIABLE = new BasicConsType("variable");
	private static final ConsType NUMBER = new BasicConsType("number");
	private static final ConsType STRING = new BasicConsType("string", "'", "'");
	private static final ConsType BOOLEAN = new BasicConsType("boolean");
	private static final ConsType ARRAY = new BasicConsType("array");
	private static final ConsType OBJECT = new BasicConsType("object");
	private static final ConsType NULL = new BasicConsType("null");
	private static final ConsType UNKNOWN = new BasicConsType("unknown");
	private static final ConsType OPERATOR = new BasicConsType("operator");
	private static final ConsType ACCESSOR = new BasicConsType("accessor");
	private static final ConsType ASSIGNMENT = new BasicConsType("assignment");
	private static final ConsType COLON = new BasicConsType("colon");
	private static final ConsType QUESTION = new BasicConsType("question");
	private static final ConsType PAIR = new BasicConsType("pair");
	private static final ConsType KEYWORD = new BasicConsType("keyword");
	
	private static Operator addition, subtraction, multiplication, division, modulus, exponent, accessor;
	private static Operator array, ternary, colon, compareTo, lt, lteq, gt, gteq, eq, neq, bitwiseAnd, bitwiseOr, bitwiseXor;
	
	private static BasicLexer lexer = null;
	
	/**
	 * @return the initialized {@link Lexer} used by the {@link ScopedFormulaProcessor}
	 */
	public static BasicLexer getLexer() {
		if (ScopedFormulaProcessor.lexer != null)
			return ScopedFormulaProcessor.lexer;
		try {
			lock.lock();
			if (ScopedFormulaProcessor.lexer != null)
				return ScopedFormulaProcessor.lexer;
			addition = new ArithmaticOperator(6, "+", BigDecimal::add, BigInteger::add) {
				@Override
				public Object apply(Object t, Object u) {
					return (t instanceof String || u instanceof String) ? t.toString() + u.toString() : super.apply(t, u);
				}
			};
			subtraction = new ArithmaticOperator(6, "-", BigDecimal::subtract, BigInteger::subtract);
			multiplication = new ArithmaticOperator(5, "*", BigDecimal::multiply, BigInteger::multiply);
			division = new ArithmaticOperator(5, "/", BigDecimal::divide, BigInteger::divide);
			modulus = new ArithmaticOperator(5, "%", BigDecimal::remainder, BigInteger::mod);
			exponent = new Operator(4, "**") {
				@Override
				public Object apply(Object t, Object u) {
					if (t instanceof Number && u instanceof Number) {
						Double res = Math.pow(((Number) t).doubleValue(), ((Number) u).doubleValue());
						if (isMathematicalInteger((Number) t) && isMathematicalInteger((Number) u))
							return res.intValue();
					}
					throw makeInvalidArgumentCombinationException(t, u);
				}
			};
			accessor = new Operator(1, ".") {
				@Override
				public Object apply(Object t, Object u) {
					if (!(t instanceof Scope))
						throw new IllegalArgumentException("The first argument to the accessor operator must implement Scope");
					if (!(u instanceof String))
						throw new IllegalArgumentException("The second argument to the accessor operator must be a String");
					return accessScope((String) u, (Scope) t);
				}
			};
			compareTo = new RelationalOperator(8, "compareTo", s -> s); //Using the identity function here, while possibly slightly less efficient, allows us to re-use the code in RelationalOperator
			lt = new RelationalOperator(8, "<", s -> s < 0);
			lteq = new RelationalOperator(8, "<=", s -> s <= 0);
			gt = new RelationalOperator(8, ">", s -> s > 0);
			gteq = new RelationalOperator(8, ">=", s -> s >= 0);
			eq = new Operator(9, "==") {
				@Override
				public Object apply(Object t, Object u) {
					if (t instanceof Number && u instanceof Number)
						return compareNumbers((Number) t, (Number) u) == 0;
					return t == u || (t != null && t.equals(u));
				}
			};
			neq = new Operator(9, "!=") {
				@Override
				public Object apply(Object t, Object u) {
					if (t instanceof Number && u instanceof Number)
						return compareNumbers((Number) t, (Number) u) != 0;
					return t == null ? t != u : !t.equals(u);
				}
			};
			bitwiseAnd = new BitwiseOperator(10, "&", BigInteger::and, Boolean::logicalAnd);
			bitwiseOr = new BitwiseOperator(11, "|", BigInteger::or, Boolean::logicalOr);
			bitwiseXor = new BitwiseOperator(12, "^", BigInteger::xor, Boolean::logicalXor);
			ternary = new Operator(1, "ternary") { //Although ternary is technically evaluated after all other operators, this will work because of how the arguments to ternary are evaluated
				@Override
				public Object apply(Object t, Object u) {
					if (!(t instanceof Boolean))
						throw new IllegalArgumentException("The first argument to the ternary operator must be a Boolean");
					if (!(u instanceof Pair))
						throw new IllegalArgumentException("The second argument to the ternary operator must be a Pair");
					return ((Boolean) t) ? ((Pair<?, ?>) u).getX() : ((Pair<?, ?>) u).getY();
				}
			};
			colon = new Operator(17, "colon") {
				@Override
				public Object apply(Object t, Object u) {
					return new Pair<>(t, u);
				}
			};
			array = new Operator(1, "[]") { //This works because of when parenthetical statements are evaluated
				@Override
				public Object apply(Object t, Object u) { //TODO maybe implement sublists?
					if (!(t instanceof List))
						throw new IllegalArgumentException("An array access operator must be preceeded by an object that implements List");
					if (!(u instanceof Number))
						throw new IllegalArgumentException("The index into an array access must be a number");
					return ((List<?>) t).get(((Number) u).intValue());
				}
			};
			BasicLexer lexer = new BasicLexer(DefaultIgnorePatterns.WHITESPACE);
			lexer.addRule("string'", new BasicRule(Pattern.compile("'(([^'\\\\]+|\\\\['\\\\tbnrf\"])*)'"), (l, s, m) -> new ConsCell(m.group(1), STRING)));
			lexer.addRule("string\"", new BasicRule(Pattern.compile("\"(([^\"\\\\]+|\\\\['\\\\tbnrf\"])*)\""), (l, s, m) -> new ConsCell(m.group(1), STRING)));
			lexer.addRule("inherit", new BasicRule(Pattern.compile("inherit", Pattern.LITERAL), (l, s, m) -> new ConsCell(m.group(), KEYWORD)));
			lexer.addRule("compareTo", new BasicRule(Pattern.compile("compareTo", Pattern.LITERAL), (l, s, m) -> new ConsCell(compareTo, OPERATOR)));
			lexer.addRule("boolean", new BasicRule(Pattern.compile("(true|false)"), (l, s, m) -> new ConsCell(m.group().equals("true"), BOOLEAN)));
			lexer.addRule("accessor", new BasicRule(Pattern.compile(".", Pattern.LITERAL), (l, s, m) -> new ConsCell(accessor, ACCESSOR)));
			lexer.addRule("variable", new BasicRule(Pattern.compile("([a-zA-Z_]\\w*)"), (l, s, m) -> new ConsCell(m.group(), VARIABLE)));
			lexer.addRule("integer", new BasicRule(NumberPatterns.SIGNLESS_INTEGER, (l, s, m) -> new ConsCell(Integer.parseInt(m.group()), NUMBER)));
			lexer.addRule("double", new BasicRule(NumberPatterns.SIGNLESS_DOUBLE, (l, s, m) -> new ConsCell(Double.parseDouble(m.group()), NUMBER)));
			addOperators(lexer, addition, subtraction, multiplication, division, modulus, exponent, lt, lteq, gt, gteq, eq, neq, bitwiseAnd, bitwiseOr, bitwiseXor);
			lexer.addDescender("parentheses", new BasicDescender("(", ")", (l, s, m) -> s.pushLanguage(l.getLanguage()), (l, s, m) -> { //We have to reset the language here
				s.popLanguage();
				return new ConsCell(m, PARENTHESES);
			}));
			lexer.addDescender("array", new BasicDescender("[", "]", (l, s, m) -> s.pushLanguage(l.getLanguage()), (l, s, m) -> { //We have to reset the language here
				s.popLanguage();
				return new ConsCell(array, OPERATOR, new ConsCell(m, PARENTHESES));
			}));
			
			//All non-assignment rules must go above this line
			final BasicLanguage colonLang = (BasicLanguage) lexer.getLanguage().clone(); //This language CANNOT include assignment operators
			
			//Assignment operators can go here
			
			final BasicLanguage ternaryLang = (BasicLanguage) lexer.getLanguage().clone();
			BasicRule colonOperatorRule = new BasicRule(Pattern.compile(":", Pattern.LITERAL), (l, s, m) -> new ConsCell(colon, OPERATOR));
			lexer.addRule("colon", colonOperatorRule);
			colonLang.addRule("colon", colonOperatorRule);
			BasicRule ternaryQuestion = new BasicRule(Pattern.compile("?", Pattern.LITERAL), (l, s, m) -> {
				ConsCell condition = new ConsCell(), current;
				while (s.getLast() != null && s.getLast().getCarType() != COLON && s.getLast().getCarType() != QUESTION && s.getLast().getCarType() != ASSIGNMENT) {
					(current = s.popLast()).insert(condition);
					condition = current;
				}
				s.pushLanguage(ternaryLang);
				return new ConsCell(condition, PARENTHESES, new ConsCell(ternary, QUESTION));
			});
			lexer.addRule("ternary?", ternaryQuestion);
			colonLang.addRule("ternary?", ternaryQuestion);
			ternaryLang.addRule("ternary?", ternaryQuestion);
			BasicRule ternaryColon = new BasicRule(Pattern.compile(":", Pattern.LITERAL), (l, s, m) -> {
				ConsCell left = s.popLast(), current, oldLast; //TODO check to be sure that there is at least one token between the ? and :
				while (s.getLast().getCarType() != QUESTION) { //We only stop on QUESTION because that is, by definition, the only valid stopping point for this token
					(current = s.popLast()).insert(left);
					left = current;
				}
				s.getLast().setCar(s.getLast().getCar(), OPERATOR); //The QUESTION flag is no longer needed
				s.popLanguage(); //This rule can only be reached if the current language is ternaryLang, which must be popped when the next colon is found
				oldLast = s.getLast(); //Store the current last ConsCell for later
				s.appendMatch(new ConsCell(m.group(), COLON)); //We append a bogus ConsCell with a carType of COLON to provide a stop-location for Ternary operators
				current = s.getLast(); //current is now the ConsCell that we just appended
				s.pushLanguage(colonLang); //This Language doesn't contain assignment operators because the rightmost part of the ternary operator doesn't support them
				l.lex(s); //We don't need the return value from this - s retains the state information that we need.
				s.popLanguage(); //We need to remove the assignment operator-free language that we just pushed
				s.setLast(oldLast); //Set the last ConsCell to be the value that it was before we appended the extraneous ConsCell
				ConsCell right = current.split().remove();
				for (current = right; current != null && current.getCarType() != ASSIGNMENT && current.getCarType() != COLON; current = current.getNext());
				if (current == null) //If it hits the end of the input without needing to split the input, return without splitting anything
					return new ConsCell(new Pair<>(left, right), PAIR);
				return new ConsCell(new Pair<>(left, right), PAIR, current.split());
			});
			ternaryLang.addRule("ternary:", ternaryColon);
			return ScopedFormulaProcessor.lexer = lexer;
		}
		finally {
			lock.unlock();
		}
	}
	
	/*
	 * This should ONLY be called from within getLexer()
	 */
	private static void addOperators(BasicLexer lexer, Operator... operators) {
		for (Operator operator : operators)
			lexer.addRule(operator.getSymbol(), new BasicRule(Pattern.compile(operator.getSymbol(), Pattern.LITERAL), (l, s, m) -> new ConsCell(operator, OPERATOR)));
	}
	
	/**
	 * Performs the preprocessing step for processing scoped formulae. This is essentially just converting the {@link String} to a {@link ConsCell}
	 * tree.
	 * 
	 * @param input
	 *            the formula to convert
	 * @return the formula's tokenized representation as a {@link ConsCell} tree
	 */
	public static ConsCell preProcess(String input) {
		return getLexer().lex(input);
	}
	
	/**
	 * Processes the scoped formula represented by the {@link String}.
	 * 
	 * @param input
	 *            the formula as a {@link String}
	 * @param scope
	 *            the formula's {@link Scope}
	 * @param fieldName
	 *            the name of the field that the formula was assigned to (this can be {@code null})
	 * @return the result of evaluating the formula
	 * @throws InvalidVariableAccessException
	 *             if an invalid variable access is attempted in the course of processing the formula
	 */
	public static Object process(String input, Scope scope, String fieldName) throws InvalidVariableAccessException {
		return process(preProcess(input), scope, fieldName).getCar();
	}
	
	/**
	 * Processes the scoped formula represented by the {@link ConsCell} tree.
	 * 
	 * @param input
	 *            the formula as a {@link ConsCell} tree
	 * @param scope
	 *            the formula's {@link Scope}
	 * @param fieldName
	 *            the name of the field that the formula was assigned to (this can be {@code null})
	 * @return a {@link ConsCell} containing the result of evaluating the formula
	 * @throws InvalidVariableAccessException
	 *             if an invalid variable access is attempted in the course of processing the formula
	 */
	public static ConsCell process(ConsCell input, Scope scope, String fieldName) throws InvalidVariableAccessException {
		ConsCell equation;
		if (input.getCarType() == OPERATOR) {
			if (scope.getParent() == null)
				throw new InvalidVariableAccessException("The current scope does not have a parent");
			Object accessed = accessScope(fieldName, scope.getParent());
			equation = new ConsCell(accessed, getTypeForObject(accessed));
		}
		else {
			equation = new ConsCell();
		}
		
		int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
		for (ConsCell current = input, head = equation; current != null; current = current.getNext()) {
			if (current.getCarType() == PARENTHESES) {
				head = head.append(process((ConsCell) current.getCar(), scope, fieldName));
			}
			else if (current.getCarType() == VARIABLE && head.getCarType() != ACCESSOR) { //We only convert a variable name into a scope if it isn't preceded by an accessor
				Object accessed = accessScope((String) current.getCar(), scope);
				head = head.append(new ConsCell(accessed, getTypeForObject(accessed)));
			}
			else if (current.getCarType() == KEYWORD) {
				switch ((String) current.getCar()) {
					case "inherit":
						if (scope.getParent() == null)
							throw new InvalidVariableAccessException("The current scope does not have a parent");
						Object accessed = accessScope(fieldName, scope.getParent());
						head = head.append(new ConsCell(accessed, getTypeForObject(accessed)));
						break;
					default:
						throw new IllegalArgumentException(current.getCar() + " is not a valid keyword.");
				}
			}
			else {
				if (current.getCar() instanceof Operator) {
					int precedence = ((Operator) current.getCar()).getPrecedence();
					if (precedence < lowest)
						lowest = precedence;
					if (precedence > highest)
						highest = precedence;
				}
				head = head.append(current.singular());
			}
		}
		
		for (int i = lowest; i <= highest; i++) { //For each precedence
			for (ConsCell head = equation.getNext(); head != null; head = head.getNext()) {
				if (head.getCar() instanceof Operator && ((Operator) head.getCar()).getPrecedence() == i) {
					ConsCell left = head.getPrevious(), right = head.getNext();
					Object result = ((Operator) head.getCar()).apply(left.getCar(), right.getCar());
					head.setCar(result, getTypeForObject(result));
					left.remove();
					if (left == equation)
						equation = head;
					right.remove();
				}
			}
		}
		return equation;
	}
	
	private static Object accessScope(String name, Scope scope) {
		StringBuilder nme = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			if (Character.isUpperCase(name.charAt(i)))
				nme.append('-').append(Character.toLowerCase(name.charAt(i)));
			else
				nme.append(name.charAt(i));
		}
		name = nme.toString();
		switch (name) {
			case "super":
			case "parent":
				scope = scope.getParent();
				if (scope == null)
					throw new InvalidVariableAccessException("The current scope does not have a parent");
				return scope;
			case "this":
			case "current":
				return scope; //Don't change the scope in this case
			default:
				return scope.getScopedValueByName(name);
		}
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
	
	/**
	 * Comparison function for to {@link Number Numbers} from
	 * <a href="http://stackoverflow.com/a/12884075/4618965">http://stackoverflow.com/a/12884075/4618965</a>
	 * 
	 * @param x
	 *            the first {@link Number} to compare
	 * @param y
	 *            the second {@link Number} to compare
	 * @return -1, 0, or 1 as per the rules specified in {@link Comparable#compareTo(Object)}
	 */
	public static int compareNumbers(Number x, Number y) {
		return (isSpecial(x) || isSpecial(y)) ? Double.compare(x.doubleValue(), y.doubleValue()) : toBigDecimal(x).compareTo(toBigDecimal(y));
	}
	
	/**
	 * Based on the function enumerated in <a href="http://stackoverflow.com/a/12884075/4618965">http://stackoverflow.com/a/12884075/4618965</a> with
	 * slight modifications made by Toberumono to avoid some potentially unnecessary comparison calls.
	 * 
	 * @param x
	 *            the {@link Number} to test
	 * @return {@code true} iff x is NaN or infinite
	 */
	public static boolean isSpecial(Number x) {
		return (x instanceof Double && (Double.isNaN((Double) x) || Double.isInfinite((Double) x))) || (x instanceof Float && (Float.isNaN((Float) x) || Float.isInfinite((Float) x)));
	}
	
	/**
	 * @param x
	 *            the {@link Number} to test
	 * @return {@code true} iff x is of a type that is guaranteed to represent a value that is mathematically an integer
	 */
	public static boolean isMathematicalInteger(Number x) {
		return x instanceof Byte || x instanceof Short || x instanceof Integer || x instanceof Long || x instanceof BigInteger;
	}
	
	/**
	 * Converts a {@link Number} to a {@link BigDecimal}. If the given {@link Number} is already a {@link BigDecimal}, the given value is
	 * returned.<br>
	 * Based on the function enumerated in <a href="http://stackoverflow.com/a/12884075/4618965">http://stackoverflow.com/a/12884075/4618965</a> with
	 * slight modifications made by Toberumono as per the information in
	 * <a href= "http://stackoverflow.com/questions/2683202/comparing-the-values-of-two-generic-numbers#comment20441103_12884075">
	 * http://stackoverflow.com/questions/2683202/comparing-the-values-of-two-generic-numbers#comment20441103_12884075</a>
	 * 
	 * @param number
	 *            the {@link Number} to convert to a {@link BigDecimal}
	 * @return a {@link BigDecimal} with the same value as the given {@link Number}
	 */
	public static BigDecimal toBigDecimal(Number number) {
		if (number instanceof BigDecimal)
			return (BigDecimal) number;
		if (number instanceof BigInteger)
			return new BigDecimal((BigInteger) number);
		if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long)
			return BigDecimal.valueOf(number.longValue());
		if (number instanceof Float || number instanceof Double)
			return BigDecimal.valueOf(number.doubleValue());
		
		try {
			return new BigDecimal(number.toString());
		}
		catch (final NumberFormatException e) {
			throw new RuntimeException("The given number (\"" + number + "\" of class " + number.getClass().getName() + ") does not have a parsable string representation", e);
		}
	}
}

abstract class Operator implements BinaryOperator<Object> {
	private final int precedence;
	private final String symbol;
	
	public Operator(int precedence, String symbol) {
		this.precedence = precedence;
		this.symbol = symbol;
	}
	
	public int getPrecedence() {
		return precedence;
	}
	
	public String getSymbol() {
		return symbol;
	}
	
	protected IllegalArgumentException makeInvalidArgumentCombinationException(Object t, Object u) {
		return new IllegalArgumentException(
				"(" + (t != null ? t.getClass().getName() : null) + ", " + (u != null ? u.getClass().getName() : null) + ") is not a valid argument combination for the " + getSymbol() + " operator.");
	}
	
	@Override
	public String toString() {
		return getSymbol();
	}
}

class ArithmaticOperator extends Operator {
	private final BinaryOperator<BigDecimal> doubleOperation;
	private final BinaryOperator<BigInteger> intOperation;
	
	public ArithmaticOperator(int precedence, String symbol, BinaryOperator<BigDecimal> doubleOperation, BinaryOperator<BigInteger> intOperation) {
		super(precedence, symbol);
		this.doubleOperation = doubleOperation;
		this.intOperation = intOperation;
	}
	
	@Override
	public Object apply(Object t, Object u) { //TODO add support for infinities
		if (!(t instanceof Number) || !(u instanceof Number))
			throw makeInvalidArgumentCombinationException(t, u);
		if (ScopedFormulaProcessor.isMathematicalInteger((Number) t) && ScopedFormulaProcessor.isMathematicalInteger((Number) u)) //TODO should we convert the value back to autoboxable types?
			return intOperation.apply(BigInteger.valueOf(((Number) t).longValue()), BigInteger.valueOf(((Number) u).longValue()));
		if ((t instanceof Double && ((Double) t).isNaN()) || (u instanceof Double && ((Double) u).isNaN()))
			return Double.NaN;
		if ((t instanceof Float && ((Float) t).isNaN()) || (u instanceof Float && ((Float) u).isNaN()))
			return Float.NaN;
		return doubleOperation.apply(ScopedFormulaProcessor.toBigDecimal((Number) t), ScopedFormulaProcessor.toBigDecimal((Number) u)); //TODO should we convert the value back to autoboxable types?
	}
}

class RelationalOperator extends Operator {
	private final Function<Integer, Object> sign;
	
	public RelationalOperator(int precedence, String symbol, Function<Integer, Object> sign) {
		super(precedence, symbol);
		this.sign = sign;
	}
	
	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public Object apply(Object t, Object u) {
		if (t instanceof Comparable && t.getClass().isInstance(u))
			return sign.apply(((Comparable) t).compareTo(((Comparable) t).getClass().cast(u)));
		if (u instanceof Comparable && u.getClass().isInstance(t))
			return sign.apply(-((Comparable) u).compareTo(((Comparable) u).getClass().cast(t))); //We have to negate the result because u.compareTo(t) == -t.compareTo(u)
		if (t instanceof Number && u instanceof Number)
			return sign.apply(ScopedFormulaProcessor.compareNumbers((Number) t, (Number) u));
		throw makeInvalidArgumentCombinationException(t, u);
	}
}

class BitwiseOperator extends Operator {
	private final BinaryOperator<BigInteger> intOperation;
	private final BinaryOperator<Boolean> booleanOperation;
	
	public BitwiseOperator(int precedence, String symbol, BinaryOperator<BigInteger> intOperation, BinaryOperator<Boolean> booleanOperation) {
		super(precedence, symbol);
		this.intOperation = intOperation;
		this.booleanOperation = booleanOperation;
	}
	
	@Override
	public Object apply(Object t, Object u) {
		if (t instanceof Character)
			t = Integer.valueOf((Character) t);
		if (u instanceof Character)
			u = Integer.valueOf((Character) u);
		if (t instanceof Number && u instanceof Number && ScopedFormulaProcessor.isMathematicalInteger((Number) t) && ScopedFormulaProcessor.isMathematicalInteger((Number) u))
			return intOperation.apply(BigInteger.valueOf(((Number) t).longValue()), BigInteger.valueOf(((Number) u).longValue())); //TODO should we convert the value back to autoboxable types?
		if (!(t instanceof Boolean) || !(u instanceof Boolean))
			throw makeInvalidArgumentCombinationException(t, u);
		return booleanOperation.apply((Boolean) t, (Boolean) u);
	}
}
