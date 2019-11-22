package util;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtil 
{
	public static final Timestamp END_OF_TIME = Timestamp.valueOf("9999-12-31 00:00:00");
	
	public static Timestamp getSqlDateNow()
	{
		return new Timestamp(System.currentTimeMillis());
	}
	
	public static String getEndOfTimeSqlString()
	{
		return "'9999-12-31 00:00:00'";
	}
	
	public static String formatHHMMSS(double timePlayed) 
	{
		int seconds = (int)Math.floor((timePlayed/1000)%60);
		int minutes = (int)Math.floor((timePlayed/60000)%60);
		int hours = (int)Math.floor(timePlayed/3600000);
		
		String secondsStr = "" + seconds;
		String minutesStr = "" + minutes;
		String hoursStr = "" + hours;
		
		if (seconds < 10)
		{
			secondsStr = "0" + seconds;
		}
		if (minutes < 10)
		{
			minutesStr = "0" + minutes;
		}
		if (hours < 10)
		{
			hoursStr = "0" + hours;
		}
		
		return hoursStr + ":" + minutesStr + ":" + secondsStr;
	}
	
	public static Date stripTimeComponent(Date date)
	{
		Calendar cal = Calendar.getInstance();
		
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		
		return cal.getTime();
	}
	
	public static boolean isEndOfTime(Timestamp dt)
	{
		return dt.equals(END_OF_TIME);
	}
	
	public static String formatTimestamp(Timestamp dt)
	{
		if (dt == null
		 || isEndOfTime(dt))
		{
			return "";
		}
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm");
		return dateFormat.format(dt);
	}
}
