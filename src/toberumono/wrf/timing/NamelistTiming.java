package toberumono.wrf.timing;

import java.util.Calendar;

import toberumono.namelist.parser.NamelistSection;
import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.AbstractScope;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.duration.NamelistDuration;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.rounding.Rounding;

public class NamelistTiming extends AbstractScope<Scope> implements Timing {
	private final Calendar base;
	private final Calendar start, end;
	private final Offset offset;
	private final Rounding rounding;
	private final Duration duration;
	private final Clear clear;
	
	public NamelistTiming(NamelistSection timeControl, Scope parent) { //No need for lazy computation - everything is either Disabled or independent
		super(parent);
		this.base = Calendar.getInstance();
		timecontrolParser(getBase(), timeControl, "start");
		offset = WRFRunnerComponentFactory.getDisabledComponentInstance(Offset.class);
		rounding = WRFRunnerComponentFactory.getDisabledComponentInstance(Rounding.class);
		duration = new NamelistDuration(timeControl);
		clear = WRFRunnerComponentFactory.getDisabledComponentInstance(Clear.class);
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
	
	@Override
	public Calendar getBase() {
		return base;
	}
	
	@Override
	public Calendar getStart() {
		return start;
	}
	
	@Override
	public Calendar getEnd() {
		return end;
	}
	
	@Override
	public Offset getOffset() {
		return offset;
	}
	
	@Override
	public Rounding getRounding() {
		return rounding;
	}
	
	@Override
	public Duration getDuration() {
		return duration;
	}

	@Override
	public Clear getClear() {
		return clear;
	}
}
