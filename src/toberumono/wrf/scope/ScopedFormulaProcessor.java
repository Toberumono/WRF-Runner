package toberumono.wrf.scope;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import toberumono.lexer.BasicDescender;
import toberumono.lexer.BasicLanguage;
import toberumono.lexer.BasicLexer;
import toberumono.lexer.BasicRule;
import toberumono.lexer.base.Language;
import toberumono.lexer.base.Lexer;
import toberumono.lexer.util.DefaultIgnorePatterns;
import toberumono.lexer.util.NumberPatterns;
import toberumono.structures.sexpressions.BasicConsType;
import toberumono.structures.sexpressions.ConsCell;
import toberumono.structures.sexpressions.ConsType;
import toberumono.structures.sexpressions.CoreConsType;
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
	
	private static volatile BiOperator addition, subtraction, multiplication, division, modulus, exponent, accessor, logicalAnd, logicalOr;
	private static volatile BiOperator array, ternary, colon, compareTo, lt, lteq, gt, gteq, eq, neq, bitwiseAnd, bitwiseOr, bitwiseXor;
	private static volatile UnOperator bitwiseNot, logicalNot, unaryPlus, unaryMinus;
	
	private static volatile BasicLexer lexer = null;
	
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
				public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
					Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process);
					return (t instanceof String || u instanceof String) ? t.toString() + u.toString() : super.apply(left, right, process);
				}
			};
			subtraction = new ArithmaticOperator(6, "-", BigDecimal::subtract, BigInteger::subtract);
			multiplication = new ArithmaticOperator(5, "*", BigDecimal::multiply, BigInteger::multiply);
			division = new ArithmaticOperator(5, "/", BigDecimal::divide, BigInteger::divide);
			modulus = new ArithmaticOperator(5, "%", BigDecimal::remainder, BigInteger::mod);
			exponent = new BiOperator(4, "**", Associativity.LEFT) {
				@Override
				public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
					Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process);
					if (t instanceof Number && u instanceof Number) {
						Double res = Math.pow(((Number) t).doubleValue(), ((Number) u).doubleValue());
						if (isMathematicalInteger((Number) t) && isMathematicalInteger((Number) u))
							return t instanceof Long || u instanceof Long ? res.longValue() : res.intValue();
					}
					throw makeInvalidArgumentCombinationException(t, u);
				}
			};
			accessor = new BiOperator(1, ".", Associativity.LEFT) {
				@Override
				public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
					Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process);
					if (!(t instanceof Scope))
						throw new IllegalArgumentException("The first argument to the accessor operator must implement Scope");
					if (!(u instanceof String))
						throw new IllegalArgumentException("The second argument to the accessor operator must be a String");
					return accessScope((String) u, (Scope) t);
				}
			};
			unaryPlus = new UnOperator(2, "+", Associativity.RIGHT) {
				@Override
				public Object apply(ConsCell arg, UnaryOperator<ConsCell> process) {
					Object a = processCarIfConsCell(arg, process);
					if (!(a instanceof Number))
						throw new IllegalArgumentException("Arguments to the unary plus (+) operator must be an instanceof Number");
					return a;
				}
			};
			unaryMinus = new UnOperator(2, "-", Associativity.RIGHT) {
				@Override
				public Object apply(ConsCell arg, UnaryOperator<ConsCell> process) {
					Object a = processCarIfConsCell(arg, process);
					if (!(a instanceof Number))
						throw new IllegalArgumentException("Arguments to the unary plus (+) operator must be an instanceof Number");
					return returnToOriginal(toBigDecimal((Number) a).negate(), ((Number) a).getClass());
				}
			};
			bitwiseNot = new UnOperator(2, "~", Associativity.RIGHT) {
				@Override
				public Object apply(ConsCell arg, UnaryOperator<ConsCell> process) {
					Object a = processCarIfConsCell(arg, process);
					if ((a instanceof Number) && ScopedFormulaProcessor.isMathematicalInteger((Number) a)) {
						if (a instanceof BigInteger)
							return ((BigInteger) a).not();
						else if (a instanceof Long)
							return ~((Long) a);
						else if (a instanceof Byte)
							return ~((Byte) a);
						else if (a instanceof Short)
							return ~((Short) a);
						else if (a instanceof Integer)
							return ~((Integer) a);
					}
					throw new IllegalArgumentException("The bitwise not (~) operator can only be applied to mathematical integer values (byte, short, int, long, BigInteger)");
				}
			};
			logicalNot = new UnOperator(2, "!", Associativity.RIGHT) {
				@Override
				public Object apply(ConsCell arg, UnaryOperator<ConsCell> process) {
					Object a = processCarIfConsCell(arg, process);
					if (!(a instanceof Boolean))
						throw new IllegalArgumentException("The logical not (!) operator can only be applied to boolean values");
					return !((Boolean) a);
				}
			};
			compareTo = new RelationalOperator(8, "compareTo", s -> s); //Using the identity function here, while possibly slightly less efficient, allows us to re-use the code in RelationalOperator
			lt = new RelationalOperator(8, "<", s -> s < 0);
			lteq = new RelationalOperator(8, "<=", s -> s <= 0);
			gt = new RelationalOperator(8, ">", s -> s > 0);
			gteq = new RelationalOperator(8, ">=", s -> s >= 0);
			eq = new BiOperator(9, "==", Associativity.LEFT) {
				@Override
				public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
					Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process);
					if (t instanceof Number && u instanceof Number)
						return compareNumbers((Number) t, (Number) u) == 0;
					return t == u || (t != null && t.equals(u));
				}
			};
			neq = new BiOperator(9, "!=", Associativity.LEFT) {
				@Override
				public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
					Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process);
					if (t instanceof Number && u instanceof Number)
						return compareNumbers((Number) t, (Number) u) != 0;
					return t == null ? t != u : !t.equals(u);
				}
			};
			bitwiseAnd = new BitwiseOperator(10, "&", BigInteger::and, Boolean::logicalAnd);
			bitwiseXor = new BitwiseOperator(11, "^", BigInteger::xor, Boolean::logicalXor);
			bitwiseOr = new BitwiseOperator(12, "|", BigInteger::or, Boolean::logicalOr);
			logicalAnd = new BiOperator(13, "&&", Associativity.LEFT) {
				@Override
				public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
					Object t = processCarIfConsCell(left, process);
					if (!(t instanceof Boolean))
						throw new IllegalArgumentException("The first argument to the logical and (&&) operator must be or evaluate to a Boolean");
					if (!((Boolean) t))
						return false;
					Object u = processCarIfConsCell(right, process);
					if (!(u instanceof Boolean))
						throw new IllegalArgumentException("The second argument to the logical and (&&) operator must be or evaluate to a Boolean");
					return u;
				}
			};
			logicalOr = new BiOperator(14, "||", Associativity.LEFT) {
				@Override
				public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
					Object t = processCarIfConsCell(left, process);
					if (!(t instanceof Boolean))
						throw new IllegalArgumentException("The first argument to the logical or (||) operator must be or evaluate to a Boolean");
					if ((Boolean) t)
						return true;
					Object u = processCarIfConsCell(right, process);
					if (!(u instanceof Boolean))
						throw new IllegalArgumentException("The second argument to the logical or (||) operator must be or evaluate to a Boolean");
					return u;
				}
			};
			ternary = new BiOperator(1, "ternary", Associativity.RIGHT) { //Although ternary is technically evaluated after all other operators, this will work because of how the arguments to ternary are evaluated
				@Override
				public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
					Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process);
					if (!(t instanceof Boolean))
						throw new IllegalArgumentException("The first argument to the ternary operator must be a Boolean");
					if (!(u instanceof Pair))
						throw new IllegalArgumentException("The second argument to the ternary operator must be a Pair");
					return ((Boolean) t) ? ((Pair<?, ?>) u).getX() : ((Pair<?, ?>) u).getY();
				}
			};
			colon = new BiOperator(17, "colon", Associativity.LEFT) {
				@Override
				public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
					Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process);
					return new Pair<>(t, u);
				}
			};
			array = new BiOperator(1, "[]", Associativity.LEFT) { //This works because of when parenthetical statements are evaluated
				@Override
				public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
					Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process); //TODO maybe implement sublists?
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
			lexer.addRule("boolean", new BasicRule(Pattern.compile("(true|false)"), (l, s, m) -> new ConsCell(m.group().equals("true"), BOOLEAN)));
			lexer.addRule("accessor", new BasicRule(Pattern.compile(".", Pattern.LITERAL), (l, s, m) -> new ConsCell(accessor, ACCESSOR)));
			lexer.addRule("integer", new BasicRule(NumberPatterns.SIGNLESS_INTEGER, (l, s, m) -> new ConsCell(Integer.parseInt(m.group()), NUMBER)));
			lexer.addRule("double", new BasicRule(NumberPatterns.SIGNLESS_DOUBLE, (l, s, m) -> new ConsCell(Double.parseDouble(m.group()), NUMBER)));
			lexer.addRule("+", new BasicRule(Pattern.compile("+", Pattern.LITERAL), (l, s, m) -> {
				ConsType type = s.getLast() == null ? null : s.getLast().getCarType();
				return new ConsCell(type == null || type == OPERATOR || type == QUESTION || type == COLON || type == ASSIGNMENT ? unaryPlus : addition, OPERATOR);
			}));
			lexer.addRule("-", new BasicRule(Pattern.compile("-", Pattern.LITERAL), (l, s, m) -> {
				ConsType type = s.getLast() == null ? null : s.getLast().getCarType();
				return new ConsCell(type == null || type == OPERATOR || type == QUESTION || type == COLON || type == ASSIGNMENT ? unaryMinus : subtraction, OPERATOR);
			}));
			addOperators(lexer, bitwiseNot, logicalNot, multiplication, division, modulus, exponent, compareTo, lt, lteq, gt, gteq, eq, neq, bitwiseAnd, bitwiseXor, bitwiseOr,
					logicalAnd, logicalOr);
			lexer.addRule("variable", new BasicRule(Pattern.compile("([a-zA-Z_]\\w*)"), (l, s, m) -> new ConsCell(m.group(), VARIABLE)));
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
	private static void addOperators(Language<ConsCell, ConsType, BasicRule, BasicDescender, ?> language, Operator... operators) {
		for (Operator operator : operators)
			language.addRule(operator.getSymbol(), new BasicRule(Pattern.compile(operator.getSymbol(), Pattern.LITERAL), (l, s, m) -> new ConsCell(operator, OPERATOR)));
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
		return recursivePreProcess(getLexer().lex(input));
	}
	
	private static ConsCell recursivePreProcess(ConsCell input) {
		for (ConsCell current = input; current != null; current = current.getNext()) {
			if (current.getCar() instanceof ConsCell)
				current.setCar(recursivePreProcess((ConsCell) current.getCar()), current.getCarType());
			else if (current.getCar() instanceof Pair) {
				@SuppressWarnings("unchecked") Pair<Object, Object> pair = (Pair<Object, Object>) current.getCar();
				if (pair.getX() instanceof ConsCell)
					pair.setX(recursivePreProcess((ConsCell) pair.getX()));
				if (pair.getY() instanceof ConsCell)
					pair.setY(recursivePreProcess((ConsCell) pair.getY()));
			}
			else if (current.getCar() == logicalAnd || current.getCar() == logicalOr) {
				int precedence = ((Operator) current.getCar()).getPrecedence();
				ConsCell head = current.getPrevious();
				for (; head != input && (!(head.getCar() instanceof Operator) || ((Operator) head.getCar()).getPrecedence() < precedence); head = head.getPrevious());
				ConsCell left, right, mid = current.split();
				if (head == input) {
					left = head;
					input = new ConsCell(left.hasLength(2) ? recursivePreProcess(left) : left, PARENTHESES, mid);
				}
				else {
					ConsCell temp = head.getPrevious();
					left = head.split();
					temp.append(new ConsCell(left.hasLength(2) ? recursivePreProcess(left) : left, PARENTHESES, mid));
				}
				right = mid.getNext().split();
				for (head = right; !head.isLast() && (!(head.getCar() instanceof Operator) || ((Operator) head.getCar()).getPrecedence() < precedence); head = head.getNext());
				if (head.isLast() && (!(head.getCar() instanceof Operator) || ((Operator) head.getCar()).getPrecedence() < precedence))
					mid.append(new ConsCell(right.hasLength(2) ? recursivePreProcess(right) : right, PARENTHESES));
				else {
					head.split();
					mid.append(new ConsCell(right.hasLength(2) ? recursivePreProcess(right) : right, PARENTHESES, head));
				}
				current = mid.getNext();
			}
		}
		return input;
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
		if (input.getCarType() == OPERATOR && input.getCar() instanceof BiOperator) {
			if (scope == null || scope.getParent() == null)
				throw new InvalidVariableAccessException("The current scope does not have a parent");
			Object accessed = process(fieldName, scope.getParent(), null);
			equation = new ConsCell(accessed, getTypeForObject(accessed));
		}
		else {
			equation = new ConsCell();
		}
		
		int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
		for (ConsCell current = input, head = equation; current != null; current = current.getNext()) {
			if (current.getCarType() == VARIABLE && head.getCarType() != ACCESSOR) { //We only convert a variable name into a scope if it isn't preceded by an accessor
				Object accessed = accessScope((String) current.getCar(), scope);
				head = head.append(new ConsCell(accessed, getTypeForObject(accessed)));
			}
			else if (current.getCarType() == KEYWORD) {
				switch ((String) current.getCar()) {
					case "inherit":
						if (scope == null || scope.getParent() == null)
							throw new InvalidVariableAccessException("Cannot inherit a value from a non-existent parent");
						Object accessed = process(fieldName, scope.getParent(), null);
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
		
		UnaryOperator<ConsCell> process = c -> process(c, scope, fieldName);
		for (int i = lowest; i <= highest; i++) { //For each precedence
			Stack<Integer> skipStack = new Stack<>();
			for (ConsCell head = equation; head != null; head = (skipStack.size() == 0 ? head.getNext() : head.getNext(skipStack.pop()))) {
				if (head.getCar() instanceof Operator && ((Operator) head.getCar()).getPrecedence() == i) {
					//This algorithm guarantees that all left-associative operators with precedence <= i will have been processed by this point
					while (((Operator) head.getCar()).getAssociativity() == Associativity.RIGHT) {
						ConsCell next = head.getNext();
						if (next != null) {
							if (!(next.getCar() instanceof Operator))
								next = next.getNext();
							if (next != null && next.getCar() instanceof Operator && ((Operator) next.getCar()).getPrecedence() == i) {
								head = next;
								skipStack.push(-1); //We need to step one item back once we are done processing the next operator
								continue; //Check again
							}
						}
						break; //If next didn't satisfy the lookahead conditions, we're done here
					}
					if (head.getCar() instanceof UnOperator) {
						if (((UnOperator) head.getCar()).getAssociativity() == Associativity.LEFT) {
							ConsCell left = head.getPrevious();
							Object result = ((UnOperator) head.getCar()).apply(left, process);
							head.setCar(result, getTypeForObject(result));
							left.remove();
							if (left == equation)
								equation = head;
						}
						else { //Then this is right-associative
							ConsCell right = head.getNext();
							Object result = ((UnOperator) head.getCar()).apply(right, process);
							head.setCar(result, getTypeForObject(result));
							right.remove();
						}
					}
					else if (head.getCar() instanceof BiOperator) {
						ConsCell left = head.getPrevious(), right = head.getNext();
						Object result = ((BiOperator) head.getCar()).apply(left, right, process);
						head.setCar(result, getTypeForObject(result));
						left.remove();
						if (left == equation)
							equation = head;
						right.remove();
					}
				}
			}
		}
		return equation.getCarType() == PARENTHESES ? process((ConsCell) equation.getCar(), scope, fieldName) : equation;
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
		Object out = scope;
		for (String n : name.split("\\.")) {
			if (!(out instanceof Scope))
				throw new InvalidVariableAccessException(out.getClass().getName() + " is not an instance of Scope.");
			switch (n) {
				case "super":
				case "parent":
					out = ((Scope) out).getParent();
					if (out == null)
						throw new InvalidVariableAccessException("The current scope does not have a parent");
					break;
				case "this":
				case "current":
					break; //Don't change the scope in this case
				default:
					out = ((Scope) out).getScopedValueByName(n);
			}
		}
		return out;
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
		if (o instanceof ConsCell)
			return CoreConsType.CONS_CELL;
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
	
	/**
	 * Helper method that is used to revert {@link BigDecimal} wrapping in some of the numeric operators.
	 * 
	 * @param number
	 *            the wrapped {@link Number}
	 * @param clazz
	 *            the {@link Class} of the original {@link Number}
	 * @param <T>
	 *            the type of {@link Number} to be returned
	 * @return the (effectively) unwrapped {@link Number}
	 */
	public static <T extends Number> T returnToOriginal(BigDecimal number, Class<T> clazz) {
		if (clazz.isInstance(number))
			return clazz.cast(number);
		else if (clazz == Byte.class)
			return clazz.cast(number.byteValueExact());
		else if (clazz == Short.class)
			return clazz.cast(number.shortValueExact());
		else if (clazz == Integer.class)
			return clazz.cast(number.intValueExact());
		else if (clazz == Long.class)
			return clazz.cast(number.longValueExact());
		else if (clazz == Float.class)
			return clazz.cast(number.floatValue());
		else if (clazz == Double.class)
			return clazz.cast(number.doubleValue());
		else if (clazz == BigInteger.class)
			return clazz.cast(number.toBigIntegerExact());
		throw new IllegalArgumentException("The provided number could not be converted to its original form.");
	}
	
	/**
	 * Helper method that is used to revert {@link BigInteger} wrapping in some of the numeric operators.
	 * 
	 * @param number
	 *            the wrapped {@link Number}
	 * @param clazz
	 *            the {@link Class} of the original {@link Number}
	 * @param <T>
	 *            the type of {@link Number} to be returned
	 * @return the (effectively) unwrapped {@link Number}
	 */
	public static <T extends Number> T returnToOriginal(BigInteger number, Class<T> clazz) {
		if (clazz.isInstance(number))
			return clazz.cast(number);
		else if (clazz == Byte.class)
			return clazz.cast(number.byteValueExact());
		else if (clazz == Short.class)
			return clazz.cast(number.shortValueExact());
		else if (clazz == Integer.class)
			return clazz.cast(number.intValueExact());
		else if (clazz == Long.class)
			return clazz.cast(number.longValueExact());
		else if (clazz == Float.class)
			return clazz.cast(number.floatValue());
		else if (clazz == Double.class)
			return clazz.cast(number.doubleValue());
		else if (clazz == BigDecimal.class)
			return clazz.cast(new BigDecimal(number));
		throw new IllegalArgumentException("The provided number could not be converted to its original form.");
	}
}

enum Associativity {
	LEFT,
	RIGHT;
}

abstract class Operator {
	private final int precedence;
	private final String symbol;
	private final Associativity associativity;
	
	public Operator(int precedence, String symbol, Associativity associativity) {
		this.precedence = precedence;
		this.symbol = symbol;
		this.associativity = associativity;
	}
	
	public int getPrecedence() {
		return precedence;
	}
	
	public String getSymbol() {
		return symbol;
	}
	
	@Override
	public String toString() {
		return getSymbol();
	}
	
	public Associativity getAssociativity() {
		return associativity;
	}
	
	protected Object processCarIfConsCell(ConsCell cell, UnaryOperator<ConsCell> process) {
		return cell != null ? (cell.getCar() instanceof ConsCell ? process.apply((ConsCell) cell.getCar()).getCar() : cell.getCar()) : null;
	}
}

abstract class BiOperator extends Operator {
	
	public BiOperator(int precedence, String symbol, Associativity associativity) {
		super(precedence, symbol, associativity);
	}
	
	protected IllegalArgumentException makeInvalidArgumentCombinationException(Object t, Object u) {
		return new IllegalArgumentException(
				"(" + (t != null ? t.getClass().getName() : null) + ", " + (u != null ? u.getClass().getName() : null) + ") is not a valid argument combination for the " + getSymbol() + " operator.");
	}
	
	public abstract Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process);
}

abstract class UnOperator extends Operator {
	
	public UnOperator(int precedence, String symbol, Associativity associativity) {
		super(precedence, symbol, associativity);
	}
	
	public abstract Object apply(ConsCell arg, UnaryOperator<ConsCell> process);
}

class ArithmaticOperator extends BiOperator {
	private final BinaryOperator<BigDecimal> doubleOperation;
	private final BinaryOperator<BigInteger> intOperation;
	
	public ArithmaticOperator(int precedence, String symbol, BinaryOperator<BigDecimal> doubleOperation, BinaryOperator<BigInteger> intOperation) {
		super(precedence, symbol, Associativity.LEFT);
		this.doubleOperation = doubleOperation;
		this.intOperation = intOperation;
	}
	
	@Override
	public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) { //TODO add support for infinities
		Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process);
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

class RelationalOperator extends BiOperator {
	private final Function<Integer, Object> sign;
	
	public RelationalOperator(int precedence, String symbol, Function<Integer, Object> sign) {
		super(precedence, symbol, Associativity.LEFT);
		this.sign = sign;
	}
	
	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
		Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process);
		if (t instanceof Comparable && t.getClass().isInstance(u))
			return sign.apply(((Comparable) t).compareTo(((Comparable) t).getClass().cast(u)));
		if (u instanceof Comparable && u.getClass().isInstance(t))
			return sign.apply(-((Comparable) u).compareTo(((Comparable) u).getClass().cast(t))); //We have to negate the result because u.compareTo(t) == -t.compareTo(u)
		if (t instanceof Number && u instanceof Number)
			return sign.apply(ScopedFormulaProcessor.compareNumbers((Number) t, (Number) u));
		throw makeInvalidArgumentCombinationException(t, u);
	}
}

class BitwiseOperator extends BiOperator {
	private final BinaryOperator<BigInteger> intOperation;
	private final BinaryOperator<Boolean> booleanOperation;
	
	public BitwiseOperator(int precedence, String symbol, BinaryOperator<BigInteger> intOperation, BinaryOperator<Boolean> booleanOperation) {
		super(precedence, symbol, Associativity.LEFT);
		this.intOperation = intOperation;
		this.booleanOperation = booleanOperation;
	}
	
	@Override
	public Object apply(ConsCell left, ConsCell right, UnaryOperator<ConsCell> process) {
		Object t = processCarIfConsCell(left, process), u = processCarIfConsCell(right, process);
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
