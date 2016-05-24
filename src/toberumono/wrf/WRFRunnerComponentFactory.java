package toberumono.wrf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import toberumono.json.JSONObject;

public class WRFRunnerComponentFactory<T> {
	private static final Map<Class<?>, WRFRunnerComponentFactory<?>> factories = new HashMap<>();
	private final Map<String, BiFunction<JSONObject, T, T>> components;
	private String defaultComponentType;
	private T disabledComponentInstance;
	
	private WRFRunnerComponentFactory(String defaultComponentType, T disabledInstance) {
		components = new HashMap<>();
		this.defaultComponentType = defaultComponentType;
		this.disabledComponentInstance = disabledInstance;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> WRFRunnerComponentFactory<T> getFactory(Class<T> clazz) {
		synchronized (factories) {
			return (WRFRunnerComponentFactory<T>) factories.get(clazz);
		}
	}
	
	public static <T> WRFRunnerComponentFactory<T> getFactory(Class<T> clazz, String defaultComponentType, T disabledComponentInstance) {
		synchronized (factories) {
			if (factories.containsKey(clazz)) {
				@SuppressWarnings("unchecked") WRFRunnerComponentFactory<T> factory = (WRFRunnerComponentFactory<T>) factories.get(clazz);
				factory.setDefaultComponentType(defaultComponentType);
				return factory;
			}
			WRFRunnerComponentFactory<T> factory = new WRFRunnerComponentFactory<>(defaultComponentType, disabledComponentInstance);
			factories.put(clazz, factory);
			return factory;
		}
	}
	
	public static <T> void addComponentConstructor(Class<T> clazz, String type, BiFunction<JSONObject, T, T> constructor) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		factory.addComponentConstructor(type, constructor);
	}
	
	public void addComponentConstructor(String type, BiFunction<JSONObject, T, T> constructor) {
		synchronized (components) {
			components.put(type, constructor);
		}
	}
	
	public static <T> T generateComponent(Class<T> clazz, JSONObject parameters, T parent) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		return factory.generateComponent(parameters, parent);
	}
	
	public T generateComponent(JSONObject parameters, T parent) {
		if (parameters == null)
			return getDisabledComponentInstance();
		return generateComponent(parameters.containsKey("type") ? parameters.get("type").toString() : defaultComponentType, parameters, parent);
	}
	
	public static <T> T generateComponent(Class<T> clazz, String type, JSONObject parameters, T parent) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		return factory.generateComponent(type, parameters, parent);
	}
	
	public T generateComponent(String type, JSONObject parameters, T parent) { //TODO figure out how to implement inheritance of the form "grib.timing.constant"
		synchronized (components) {
			if (parameters.containsKey("enabled") && parameters.get("enabled").value() instanceof Boolean && !((Boolean) parameters.get("enabled").value()))
				return getDisabledComponentInstance();
			if (parameters.containsKey("inherit") && parameters.get("inherit").value() instanceof Boolean && !((Boolean) parameters.get("inherit").value()))
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
	
	public static <T> void setDisabledComponentInstance(Class<T> clazz, T instance) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		factory.setDisabledComponentInstance(instance);
	}
	
	public void setDisabledComponentInstance(T instance) {
		synchronized (components) {
			this.disabledComponentInstance = instance;
		}
	}
	
	public static <T> T getDisabledComponentInstance(Class<T> clazz) {
		WRFRunnerComponentFactory<T> factory = getFactory(clazz);
		return factory.getDisabledComponentInstance();
	}
	
	public T getDisabledComponentInstance() {
		synchronized (components) {
			return disabledComponentInstance;
		}
	}
}
