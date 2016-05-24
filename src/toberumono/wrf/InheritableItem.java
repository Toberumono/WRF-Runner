package toberumono.wrf;

public abstract class InheritableItem<T> {
	private final T parent;
	
	public InheritableItem(T parent) {
		this.parent = parent;
	}
	
	protected T getParent() {
		return parent;
	}
}
