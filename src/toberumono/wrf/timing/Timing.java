package toberumono.wrf.timing;

import java.util.Calendar;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.NamelistSection;
import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.duration.NamelistDuration;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.rounding.Rounding;

public class Timing {
	private final Calendar base, start, end;
	private final Offset offset;
	private final Rounding rounding;
	private final Duration duration;
	
	public Timing(NamelistSection timeControl) {
		this.base = Calendar.getInstance();
		timecontrolParser(getBase(), timeControl, "start");
		offset = WRFRunnerComponentFactory.getDisabledComponentInstance(Offset.class);
		rounding = WRFRunnerComponentFactory.getDisabledComponentInstance(Rounding.class);
		duration = new NamelistDuration(timeControl);
		start = getOffset().apply(getRounding().apply(getBase()));
		end = getDuration().apply(getStart());
	}
	
	private void timecontrolParser(Calendar cal, NamelistSection tc, String prefix) {
		prefix = prefix.endsWith("_") ? prefix : prefix + "_";
		cal.set(cal.YEAR, ((Number) tc.get(prefix + "year").get(0).value()).intValue());
		cal.set(cal.MONTH, ((Number) tc.get(prefix + "month").get(0).value()).intValue() - 1);
		cal.set(cal.DAY_OF_MONTH, ((Number) tc.get(prefix + "day").get(0).value()).intValue());
		cal.set(cal.HOUR_OF_DAY, ((Number) tc.get(prefix + "hour").get(0).value()).intValue());
		cal.set(cal.MINUTE, ((Number) tc.get(prefix + "minute").get(0).value()).intValue());
		cal.set(cal.SECOND, ((Number) tc.get(prefix + "second").get(0).value()).intValue());
	}
	
	public Timing(JSONObject parameters, Calendar base) {
		this.base = base;
		offset = WRFRunnerComponentFactory.generateComponent(Offset.class, (JSONObject) parameters.get("offset"), null);
		rounding = WRFRunnerComponentFactory.generateComponent(Rounding.class, (JSONObject) parameters.get("rounding"), null);
		duration = WRFRunnerComponentFactory.generateComponent(Duration.class, (JSONObject) parameters.get("duration"), null);
		start = getOffset().apply(getRounding().apply(getBase()));
		end = getDuration().apply(getStart());
	}
	
	public Timing(JSONObject parameters, Timing parent) { //TODO implement existence checks
		base = parent.getBase();
		offset = WRFRunnerComponentFactory.generateComponent(Offset.class, (JSONObject) parameters.get("offset"), parent != null ? parent.getOffset() : null);
		rounding = WRFRunnerComponentFactory.generateComponent(Rounding.class, (JSONObject) parameters.get("rounding"), parent != null ? parent.getRounding() : null);
		duration = WRFRunnerComponentFactory.generateComponent(Duration.class, (JSONObject) parameters.get("duration"), parent != null ? parent.getDuration() : null);
		start = getOffset().apply(getRounding().apply(getBase()));
		end = getDuration().apply(getStart());
	}
	
	public Calendar getBase() {
		return base;
	}
	
	public Calendar getStart() {
		return start;
	}
	
	public Calendar getEnd() {
		return end;
	}
	
	public Offset getOffset() {
		return offset;
	}
	
	public Rounding getRounding() {
		return rounding;
	}
	
	public Duration getDuration() {
		return duration;
	}
}
