package toberumono.wrf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import toberumono.wrf.scope.ScopedConfiguration;

public class WRFRunnerComponentFactory<T> {
	private static final Map<Class<?>, WRFRunnerComponentFactory<?>> factories = new HashMap<>();
	private final Map<String, BiFunction<ScopedConfiguration, T, T>> components;
	private String defaultComponentType;
	private Supplier<T> disabledComponentInstanceSupplier;
	
	private WRFRunnerComponentFactory(String defaultComponentType, Supplier<T> disabledComponentInstanceSupplier) {
		components = new HashMap<>();
		this.defaultComponentType = defaultComponentType;
		this.disabledComponentInstanceSupplier = disabledComponentInstanceSupplier;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> WRFRunnerComponentFactory<T> getFactory(Class<T> clazz) {
		synchronized (factories) {
			return (WRFRunnerComponentFactory<T>) factories.get(clazz);
		}
	}
	
	public static <T> WRFRunnerComponentFactory<T> getFactory(Class<T> clazz, String defaultComponentType, Supplier<T> disabledComponentInstanceSupplier) {
		synchronized (factories) {
			if (factories.containsKey(clazz)) {
				@SuppressWarnings("unchecked") WRFRunnerComponentFactory<T> factory = (WRFRunnerComponentFactory<T>) factories.get(clazz);
				factory.setDefaultComponentType(defaultComponentType);
				return factory;
			}
			WRFRunnerComponentFactory<T> factory = new WRFRunnerComponentFactory<>(defaultComponentType, disabledComponentInstanceSupplier);
			factories.put(clazz, factory);
			return factory;
		}
	}
	
	public static <T> void addComponentConstructor(Class<T> clazz, String type, BiFunction<ScopedConfiguration, T, T> constructor) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		factory.addComponentConstructor(type, constructor);
	}
	
	public void addComponentConstructor(String type, BiFunction<ScopedConfiguration, T, T> constructor) {
		synchronized (components) {
			components.put(type, constructor);
		}
	}
	
	public static <T> T generateComponent(Class<T> clazz, ScopedConfiguration parameters, T parent) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		return factory.generateComponent(parameters, parent);
	}
	
	public T generateComponent(ScopedConfiguration parameters, T parent) {
		if (parameters == null)
			return getDisabledComponentInstance();
		return generateComponent(parameters.containsKey("type") ? parameters.get("type").toString() : defaultComponentType, parameters, parent);
	}
	
	public static <T> T generateComponent(Class<T> clazz, String type, ScopedConfiguration parameters, T parent) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		return factory.generateComponent(type, parameters, parent);
	}
	
	public T generateComponent(String type, ScopedConfiguration parameters, T parent) { //TODO figure out how to implement inheritance of the form "grib.timing.constant"
		synchronized (components) {
			if (parameters.containsKey("enabled") && parameters.get("enabled") instanceof Boolean && !((Boolean) parameters.get("enabled")))
				return getDisabledComponentInstance();
			if (parameters.containsKey("inherit") && parameters.get("inherit") instanceof Boolean && ((Boolean) parameters.get("inherit")))
				return parent;
			return components.get(type != null ? type : defaultComponentType).apply(parameters, parent);
		}
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
	
	public static <T> void setDisabledComponentInstance(Class<T> clazz, Supplier<T> disabledComponentInstanceSupplier) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		factory.setDisabledComponentInstanceSupplier(disabledComponentInstanceSupplier);
	}
	
	public void setDisabledComponentInstanceSupplier(Supplier<T> disabledComponentInstanceSupplier) {
		synchronized (components) {
			this.disabledComponentInstanceSupplier = disabledComponentInstanceSupplier;
		}
	}
	
	public static <T> T getDisabledComponentInstance(Class<T> clazz) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		return factory.getDisabledComponentInstance();
	}
	
	public T getDisabledComponentInstance() {
		synchronized (components) {
			return disabledComponentInstanceSupplier.get();
		}
	}
}
