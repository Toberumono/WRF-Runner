package toberumono.wrf.timing;

import java.util.Calendar;

import toberumono.wrf.Simulation2;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.rounding.Rounding;

/**
 * This interface defines the accessor methods used to interact with {@link Timing} Objects.
 * 
 * @author Toberumono
 */
public interface Timing {
	
	/**
	 * @return the {@link Calendar} from which {@link #getStart() start} and {@link #getEnd() end} are derived
	 */
	public Calendar getBase();
	
	/**
	 * @return the {@link Calendar} denoting the time that the {@link Simulation2} will start
	 */
	public Calendar getStart();
	
	/**
	 * @return the {@link Calendar} denoting the time that the {@link Simulation2} will end
	 */
	public Calendar getEnd();
	
	/**
	 * @return the {@link Offset} added to the rounded {@link #getBase() base} to derive {@link #getStart() start}
	 */
	public Offset getOffset();
	
	/**
	 * @return the {@link Rounding} applied to {@link #getBase() base} to derive {@link #getStart() start}
	 */
	public Rounding getRounding();
	
	/**
	 * @return the {@link Duration} of the {@link Simulation2}
	 */
	public Duration getDuration();
	
	/**
	 * @return the {@link Clear} of the {@link Simulation2}
	 */
	public Clear getClear();
}
