package toberumono.wrf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Calendar.*;

public class SimulationConstants {
	public static final List<String> TIMING_FIELD_NAMES = Collections.unmodifiableList(Arrays.asList("milliseconds", "seconds", "minutes", "hours", "days", "months", "years"));
	public static final List<Integer> TIMING_FIELDS = Collections.unmodifiableList(Arrays.asList(MILLISECOND, SECOND, MINUTE, HOUR_OF_DAY, DAY_OF_MONTH, MONTH, YEAR));
	public static final String NAMELIST_FIELD_NAME = "namelist";
	public static final String LOGGER_ROOT = "WRFRunner.Simulation";
}
