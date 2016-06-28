package toberumono.wrf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedFormulaProcessor;
import toberumono.wrf.scope.ScopedMap;

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
	
	@SuppressWarnings("unchecked")
	public static <T> WRFRunnerComponentFactory<T> getFactory(Class<T> clazz) {
		synchronized (factories) {
			return (WRFRunnerComponentFactory<T>) factories.get(clazz);
		}
	}
	
	public static <T> WRFRunnerComponentFactory<T> getFactory(Class<T> clazz, String defaultComponentType, BiFunction<ScopedMap, Scope, T> disabledComponentConstructor) {
		synchronized (factories) {
			if (factories.containsKey(clazz)) {
				@SuppressWarnings("unchecked") WRFRunnerComponentFactory<T> factory = (WRFRunnerComponentFactory<T>) factories.get(clazz);
				factory.setDefaultComponentType(defaultComponentType);
				return factory;
			}
			WRFRunnerComponentFactory<T> factory = new WRFRunnerComponentFactory<>(clazz, defaultComponentType, disabledComponentConstructor);
			factories.put(clazz, factory);
			return factory;
		}
	}
	
	public static <T> void addComponentConstructor(Class<T> clazz, String type, BiFunction<ScopedMap, Scope, T> constructor) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		factory.addComponentConstructor(type, constructor);
	}
	
	public void addComponentConstructor(String type, BiFunction<ScopedMap, Scope, T> constructor) {
		synchronized (components) {
			components.put(type, constructor);
		}
	}
	
	public static <T> T generateComponent(Class<T> clazz, ScopedMap parameters, Scope parent) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		return factory.generateComponent(parameters, parent);
	}
	
	public T generateComponent(ScopedMap parameters, Scope parent) {
		return generateComponent(parameters != null && parameters.containsKey("type") ? parameters.get("type").toString() : defaultComponentType, parameters, parent);
	}
	
	public static <T> T generateComponent(Class<T> clazz, String type, ScopedMap parameters, Scope parent) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		return factory.generateComponent(type, parameters, parent);
	}
	
	public T generateComponent(String type, ScopedMap parameters, Scope parent) {
		synchronized (components) {
			if (type.equals("disabled"))
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
	
	public static boolean willInherit(ScopedMap parameters) {
		return parameters == null || (parameters.containsKey("inherit") && (!(parameters.get("inherit") instanceof Boolean) || ((Boolean) parameters.get("inherit"))));
	}
	
	public static <T> void setDefaultComponentType(Class<T> clazz, String type) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		factory.setDefaultComponentType(type);
	}
	
	public void setDefaultComponentType(String type) {
		synchronized (components) {
			defaultComponentType = type;
		}
	}
	
	public static <T> void setDisabledComponentInstance(Class<T> clazz, BiFunction<ScopedMap, Scope, T> disabledComponentConstructor) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		factory.setDisabledComponentConstructor(disabledComponentConstructor);
	}
	
	public void setDisabledComponentConstructor(BiFunction<ScopedMap, Scope, T> disabledComponentConstructor) {
		synchronized (components) {
			this.disabledComponentConstructor = disabledComponentConstructor;
		}
	}
	
	public static <T> T getDisabledComponentInstance(Class<T> clazz, ScopedMap parameters, Scope parent) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		return factory.getDisabledComponentInstance(parameters, parent);
	}
	
	public T getDisabledComponentInstance(ScopedMap parameters, Scope parent) {
		synchronized (components) {
			return disabledComponentConstructor.apply(parameters, parent);
		}
	}
}
