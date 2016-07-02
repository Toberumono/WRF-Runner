package toberumono.wrf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedComponent;
import toberumono.wrf.scope.ScopedFormulaProcessor;
import toberumono.wrf.scope.ScopedMap;

/**
 * A factory class that implements the methods needed to construct any implementation of {@link ScopedComponent} that uses the standard (arguments,
 * parent) constructor.<br>
 * Factories are not constructed directly - they are accessed through {@link #getFactory(Class)} and
 * {@link #createFactory(Class, String, BiFunction)}.
 * 
 * @author Toberumono
 * @param <T>
 *            the type of {@link ScopedComponent} being produced (although it technically can be anything)
 */
public class WRFRunnerComponentFactory<T> {
	private static final Map<Class<?>, WRFRunnerComponentFactory<?>> factories = new HashMap<>();
	private final Map<String, BiFunction<ScopedMap, Scope, T>> components;
	private String defaultComponentType;
	private BiFunction<ScopedMap, Scope, T> disabledComponentConstructor;
	private final Class<T> rootType;
	
	private WRFRunnerComponentFactory(Class<T> rootType, String defaultComponentType, BiFunction<ScopedMap, Scope, T> disabledComponentConstructor) {
		components = new HashMap<>();
		this.rootType = rootType;
		this.defaultComponentType = defaultComponentType;
		this.disabledComponentConstructor = disabledComponentConstructor;
	}
	
	/**
	 * Retrieves the {@link WRFRunnerComponentFactory} for the given {@link Class}.
	 * 
	 * @param <T>
	 *            the type of the component to be generated
	 * @param clazz
	 *            the {@link Class} provided when the {@link WRFRunnerComponentFactory} that is to be retrieved was created
	 * @return an instance of {@link WRFRunnerComponentFactory} that produces components that are subclasses of {@code clazz}
	 * @throws NoSuchFactoryException
	 *             if a {@link WRFRunnerComponentFactory} for the given {@link Class} does not exist
	 */
	public static <T> WRFRunnerComponentFactory<T> getFactory(Class<T> clazz) {
		synchronized (factories) {
			if (!factories.containsKey(clazz))
				throw new NoSuchFactoryException("The factory for " + clazz.getName() + " does not exist.");
			@SuppressWarnings("unchecked") WRFRunnerComponentFactory<T> factory = (WRFRunnerComponentFactory<T>) factories.get(clazz);
			return factory;
		}
	}
	
	/**
	 * Retrieves the {@link WRFRunnerComponentFactory} for the given {@link Class} if the {@link WRFRunnerComponentFactory} exists and updates its
	 * {@code defaultComponentType} and {@code disabledComponentConstructor} if they are not {@code null}. Otherwise, it creates a
	 * {@link WRFRunnerComponentFactory} for the given {@link Class} with the given {@code defaultComponentType} and
	 * {@code disabledComponentConstructor}.
	 * 
	 * @param <T>
	 *            the type of the component to be generated
	 * @param clazz
	 *            the root {@link Class} of the component type for which the {@link WRFRunnerComponentFactory} is being created or retrieved
	 * @param defaultComponentType
	 *            the name of the default component type as a {@link String}
	 * @param disabledComponentConstructor
	 *            a {@link BiFunction} that returns an instance of the component that performs no action or equates to a disabled state
	 * @return an instance of {@link WRFRunnerComponentFactory} that produces components that are subclasses of {@code clazz}
	 */
	public static <T> WRFRunnerComponentFactory<T> createFactory(Class<T> clazz, String defaultComponentType, BiFunction<ScopedMap, Scope, T> disabledComponentConstructor) {
		synchronized (factories) {
			if (factories.containsKey(clazz)) {
				@SuppressWarnings("unchecked") WRFRunnerComponentFactory<T> factory = (WRFRunnerComponentFactory<T>) factories.get(clazz);
				if (defaultComponentType != null)
					factory.setDefaultComponentType(defaultComponentType);
				if (disabledComponentConstructor != null)
					factory.setDisabledComponentConstructor(disabledComponentConstructor);
				return factory;
			}
			WRFRunnerComponentFactory<T> factory = new WRFRunnerComponentFactory<>(clazz, defaultComponentType, disabledComponentConstructor);
			factories.put(clazz, factory);
			return factory;
		}
	}
	
	/**
	 * Adds the constructor for a named component type to the {@link WRFRunnerComponentFactory} for the given {@link Class}.
	 * 
	 * @param <T>
	 *            the type of the component to be constructed
	 * @param clazz
	 *            the {@link Class} provided when the {@link WRFRunnerComponentFactory} was created
	 * @param type
	 *            the name of the component type
	 * @param constructor
	 *            a {@link BiFunction} that takes the parameters that describe the component (as a {@link ScopedMap}) and the component's parent (as a
	 *            {@link Scope}) and constructs the component accordingly. For most components with implementing class {@code T}, this function should
	 *            be equivalent to {@code T::new}.
	 * @throws NoSuchFactoryException
	 *             if a {@link WRFRunnerComponentFactory} for the given {@link Class} does not exist
	 */
	public static <T> void addComponentConstructor(Class<T> clazz, String type, BiFunction<ScopedMap, Scope, T> constructor) {
		getFactory(clazz).addComponentConstructor(type, constructor);
	}
	
	/**
	 * Adds the constructor for a named component type to the {@link WRFRunnerComponentFactory}.
	 * 
	 * @param type
	 *            the name of the component type
	 * @param constructor
	 *            a {@link BiFunction} that takes the parameters that describe the component as a {@link ScopedMap} and the component's parent as a
	 *            {@link Scope} and constructs the component accordingly. For a component with implementing class {@code T}, this function should be
	 *            equivalent to {@code T::new}.
	 */
	public void addComponentConstructor(String type, BiFunction<ScopedMap, Scope, T> constructor) {
		synchronized (components) {
			components.put(type, constructor);
		}
	}
	
	/**
	 * Uses the {@link WRFRunnerComponentFactory} for the given {@link Class} to generate a new instance of the component defined by the given
	 * {@code parameters} and {@code parent}.
	 * 
	 * @param <T>
	 *            the type of the component to be returned
	 * @param clazz
	 *            the {@link Class} provided when the {@link WRFRunnerComponentFactory} was created
	 * @param parameters
	 *            the parameters that describe the component as a {@link ScopedMap}
	 * @param parent
	 *            the component's parent as a {@link Scope}
	 * @return a new instance of the component defined by the given {@code parameters} and {@code parent}
	 * @see #generateComponent(ScopedMap, Scope)
	 * @throws NoSuchFactoryException
	 *             if a {@link WRFRunnerComponentFactory} for the given {@link Class} does not exist
	 */
	public static <T> T generateComponent(Class<T> clazz, ScopedMap parameters, Scope parent) {
		return getFactory(clazz).generateComponent(parameters, parent);
	}
	
	/**
	 * Uses the {@link WRFRunnerComponentFactory} to generate a new instance of the component defined by the given {@code parameters} and
	 * {@code parent}.
	 * 
	 * @param parameters
	 *            the parameters that describe the component as a {@link ScopedMap}
	 * @param parent
	 *            the component's parent as a {@link Scope}
	 * @return a new instance of the component defined by the given {@code parameters} and {@code parent}
	 * @see #generateComponent(Class, ScopedMap, Scope)
	 */
	public T generateComponent(ScopedMap parameters, Scope parent) {
		return generateComponent(parameters != null && parameters.containsKey("type") ? parameters.get("type").toString() : defaultComponentType, parameters, parent);
	}
	
	/**
	 * Uses the {@link WRFRunnerComponentFactory} for the given {@link Class} to generate a new instance of the component defined by the given
	 * {@code type}, {@code parameters}, and {@code parent}.
	 * 
	 * @param <T>
	 *            the type of the component to be returned
	 * @param clazz
	 *            the {@link Class} provided when the {@link WRFRunnerComponentFactory} was created
	 * @param type
	 *            the name of the type of component described by the given parameters (this overrides the type field in the given parameters if it
	 *            exists
	 * @param parameters
	 *            the parameters that describe the component as a {@link ScopedMap}
	 * @param parent
	 *            the component's parent as a {@link Scope}
	 * @return a new instance of the component defined by the given {@code type}, {@code parameters}, and {@code parent}
	 * @throws NoSuchFactoryException
	 *             if a {@link WRFRunnerComponentFactory} for the given {@link Class} does not exist
	 * @see #generateComponent(String, ScopedMap, Scope)
	 */
	public static <T> T generateComponent(Class<T> clazz, String type, ScopedMap parameters, Scope parent) {
		return getFactory(clazz).generateComponent(type, parameters, parent);
	}
	
	/**
	 * Uses the {@link WRFRunnerComponentFactory} to generate a new instance of the component defined by the given {@code type}, {@code parameters},
	 * and {@code parent}.
	 * 
	 * @param type
	 *            the name of the type of component described by the given parameters (this overrides the type field in the given parameters if it
	 *            exists
	 * @param parameters
	 *            the parameters that describe the component as a {@link ScopedMap}
	 * @param parent
	 *            the component's parent as a {@link Scope}
	 * @return a new instance of the component defined by the given {@code type}, {@code parameters}, and {@code parent}
	 * @see #generateComponent(Class, String, ScopedMap, Scope)
	 */
	public T generateComponent(String type, ScopedMap parameters, Scope parent) {
		synchronized (components) {
			if (type != null && type.equals("disabled"))
				return getDisabledComponentInstance(parameters, parent);
			if (parameters == null)
				return implicitInheritance(parent);
			if (parameters.get("enabled") instanceof Boolean && !((Boolean) parameters.get("enabled")))
				return getDisabledComponentInstance(parameters, parent);
			Object inherit = parameters.get("inherit");
			if (inherit != null) {
				if (inherit instanceof String) //Then this is an explicit inheritance
					inherit = ScopedFormulaProcessor.process((String) inherit, parameters, null);
				if (inherit instanceof Boolean && (Boolean) inherit)
					return implicitInheritance(parent);
				if (inherit instanceof ScopedMap) //Then this is scope-based inheritance
					return generateComponent((ScopedMap) inherit, parent);
				if (rootType.isInstance(inherit))
					return rootType.cast(inherit);
			}
			return components.get(type != null ? type : defaultComponentType).apply(parameters, parent);
		}
	}
	
	private T implicitInheritance(Scope parent) {
		if (rootType.isInstance(parent))
			return rootType.cast(parent);
		throw new TypeHierarchyException("Cannot perform implicit full-object inheritance when the parent is of a different type.");
	}
	
	/**
	 * A convenience method for use with more complex inheritance structures, primarily in the form of custom scoping.
	 * 
	 * @param parameters
	 *            parameters that define a component as a {@link ScopedMap}
	 * @return {@code true} iff the component will be entirely inherited from its parent
	 */
	public static boolean willInherit(ScopedMap parameters) {
		return parameters == null || (parameters.containsKey("inherit") && (!(parameters.get("inherit") instanceof Boolean) || ((Boolean) parameters.get("inherit"))));
	}
	
	/**
	 * Sets the default component type for the {@link WRFRunnerComponentFactory} for the given {@link Class}.
	 * 
	 * @param clazz
	 *            the {@link Class} provided when the {@link WRFRunnerComponentFactory} was created
	 * @param type
	 *            the name of the new default component type as a {@link String}
	 * @throws NoSuchFactoryException
	 *             if a {@link WRFRunnerComponentFactory} for the given {@link Class} does not exist
	 * @see #setDefaultComponentType(String)
	 */
	public static void setDefaultComponentType(Class<?> clazz, String type) {
		getFactory(clazz).setDefaultComponentType(type);
	}
	
	/**
	 * Sets the default component type for the {@link WRFRunnerComponentFactory}.
	 * 
	 * @param type
	 *            the name of the new default component type as a {@link String}
	 * @see #setDefaultComponentType(Class, String)
	 */
	public void setDefaultComponentType(String type) {
		synchronized (components) {
			defaultComponentType = type;
		}
	}
	
	/**
	 * Sets the constructor used to create disabled instances of the component in the {@link WRFRunnerComponentFactory} for the given {@link Class}.
	 * 
	 * @param <T>
	 *            the type of the component to be constructed
	 * @param clazz
	 *            the {@link Class} provided when the {@link WRFRunnerComponentFactory} was created
	 * @param disabledComponentConstructor
	 *            a {@link BiFunction} that returns an instance of the component that performs no action and/or equates to a disabled state
	 * @throws NoSuchFactoryException
	 *             if a {@link WRFRunnerComponentFactory} for the given {@link Class} does not exist
	 * @see #setDisabledComponentConstructor(BiFunction)
	 */
	public static <T> void setDisabledComponentConstructor(Class<T> clazz, BiFunction<ScopedMap, Scope, T> disabledComponentConstructor) {
		getFactory(clazz).setDisabledComponentConstructor(disabledComponentConstructor);
	}
	
	/**
	 * Sets the constructor used to create disabled instances of the component in the {@link WRFRunnerComponentFactory}.
	 * 
	 * @param disabledComponentConstructor
	 *            a {@link BiFunction} that returns an instance of the component that performs no action and/or equates to a disabled state
	 * @see #setDisabledComponentConstructor(BiFunction)
	 */
	public void setDisabledComponentConstructor(BiFunction<ScopedMap, Scope, T> disabledComponentConstructor) {
		synchronized (components) {
			this.disabledComponentConstructor = disabledComponentConstructor;
		}
	}
	
	/**
	 * Uses the {@link WRFRunnerComponentFactory} for the given {@link Class} to generate a new disabled instance of the component described by the
	 * given {@code parameters} and {@code parent}.
	 * 
	 * @param <T>
	 *            the type of the component to be returned
	 * @param clazz
	 *            the {@link Class} provided when the {@link WRFRunnerComponentFactory} was created
	 * @param parameters
	 *            the parameters that describe the component as a {@link ScopedMap}
	 * @param parent
	 *            the component's parent as a {@link Scope}
	 * @return a new instance of the disabled type described by the given {@code parameters} and {@code parent}
	 * @throws NoSuchFactoryException
	 *             if a {@link WRFRunnerComponentFactory} for the given {@link Class} does not exist
	 * @see #getDisabledComponentInstance(ScopedMap, Scope)
	 */
	public static <T> T getDisabledComponentInstance(Class<T> clazz, ScopedMap parameters, Scope parent) {
		return getFactory(clazz).getDisabledComponentInstance(parameters, parent);
	}
	
	/**
	 * Uses the {@link WRFRunnerComponentFactory} to generate a new disabled instance of the component described by the given {@code parameters} and
	 * {@code parent}.
	 * 
	 * @param parameters
	 *            the parameters that describe the component as a {@link ScopedMap}
	 * @param parent
	 *            the component's parent as a {@link Scope}
	 * @return a new instance of the disabled type described by the given {@code parameters} and {@code parent}
	 * @see #getDisabledComponentInstance(Class, ScopedMap, Scope)
	 */
	public T getDisabledComponentInstance(ScopedMap parameters, Scope parent) {
		synchronized (components) {
			return disabledComponentConstructor.apply(parameters, parent);
		}
	}
}
