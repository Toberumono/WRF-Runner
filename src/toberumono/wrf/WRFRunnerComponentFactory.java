package toberumono.wrf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import toberumono.wrf.scope.Scope;
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
			if (type.equals("disabled") || (parameters != null && parameters.containsKey("enabled") && parameters.get("enabled") instanceof Boolean && !((Boolean) parameters.get("enabled"))))
				return getDisabledComponentInstance(parameters, parent);
			if (parameters == null || (parameters.containsKey("inherit") && parameters.get("inherit") instanceof Boolean && ((Boolean) parameters.get("inherit"))))
				return rootType.isInstance(parent) ? rootType.cast(parent) : null; //TODO possibly throw an exception about an invalid type hierarchy instead of null
			return components.get(type != null ? type : defaultComponentType).apply(parameters, parent);
		}
	}
	
	public static boolean willInherit(ScopedMap parameters) {
		return parameters == null || (parameters.containsKey("inherit") && parameters.get("inherit") instanceof Boolean && ((Boolean) parameters.get("inherit")));
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
