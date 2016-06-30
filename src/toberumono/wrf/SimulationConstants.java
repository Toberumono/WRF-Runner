package toberumono.wrf;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import toberumono.namelist.parser.Namelist;

import static java.util.Calendar.*;

/**
 * A collection of constant values used throughout this library. They are collected here to make updating them safer.
 * 
 * @author Toberumono
 */
public class SimulationConstants {
	/**
	 * The names of the valid timing fields. Used in conjunction with {@link #TIMING_FIELD_IDS}.
	 * 
	 * @see #TIMING_FIELD_IDS
	 */
	public static final List<String> TIMING_FIELD_NAMES = Collections.unmodifiableList(Arrays.asList("milliseconds", "seconds", "minutes", "hours", "days", "months", "years"));
	/**
	 * The IDs of the valid timing fields as they are defined in {@link Calendar}. Used in conjunction with {@link #TIMING_FIELD_NAMES}.
	 * 
	 * @see #TIMING_FIELD_NAMES
	 */
	public static final List<Integer> TIMING_FIELD_IDS = Collections.unmodifiableList(Arrays.asList(MILLISECOND, SECOND, MINUTE, HOUR_OF_DAY, DAY_OF_MONTH, MONTH, YEAR));
	/**
	 * The name of the field used to identify the relative path to a {@link Module Module's} {@link Namelist}
	 */
	public static final String NAMELIST_FIELD_NAME = "namelist";
	/**
	 * The name of the timing subsection in the configuration JSON
	 */
	public static final String TIMING_FIELD_NAME = "timing";
	/**
	 * The name root {@link Logger} for all simulation components
	 */
	public static final String SIMULATION_LOGGER_ROOT = "WRFRunner.Simulation";
}
