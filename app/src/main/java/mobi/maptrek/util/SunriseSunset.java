package mobi.maptrek.util;

import com.skedgo.converter.TimezoneMapper;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * http://williams.best.vwh.net/sunrise_sunset_algorithm.htm
 */
public class SunriseSunset {
    @SuppressWarnings("unused")
    private static final double ASTRONOMICAL = 108d;
    @SuppressWarnings("unused")
    private static final double NAUTICAL = 102d;
    @SuppressWarnings("unused")
    private static final double CIVIL = 96d;
    private static final double OFFICIAL = 90.8333d; // 90deg 50'

    private Calendar mCalendar;
    @SuppressWarnings("FieldCanBeLocal")
    private double zenith = OFFICIAL;
    private int N;
    private double lngHour;
    private double latRad;
    private double tzOffset;

    public SunriseSunset() {
        mCalendar = Calendar.getInstance();
        // 1. first calculate the day of the year
        N = mCalendar.get(Calendar.DAY_OF_YEAR);
        tzOffset = (mCalendar.get(Calendar.ZONE_OFFSET) + mCalendar.get(Calendar.DST_OFFSET)) * 1d / 3600000;
    }

    public void setLocation(double latitude, double longitude) {
        String timeZoneId = TimezoneMapper.latLngToTimezoneString(latitude, longitude);
        TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
        tzOffset = timeZone.getOffset(mCalendar.getTimeInMillis()) * 1d / 3600000;

        latRad = Math.toRadians(latitude);
        // 2a. convert the longitude to hour value
        lngHour = longitude / 15;
    }

    public double compute(boolean sunrise) {
        // 2b. calculate an approximate time
        double t = N + (((sunrise ? 6 : 18) - lngHour) / 24);
        // 3. calculate the Sun's mean anomaly
        double M = 0.9856 * t - 3.289;
        double radM = Math.toRadians(M);
        // 4. calculate the Sun's true longitude
        double L = M + (1.916 * Math.toDegrees(Math.sin(radM))) +
                (0.020 * Math.toDegrees(Math.sin(2 * radM))) + 282.634;
        L = adjustDegrees(L);
        // 5a. calculate the Sun's right ascension
        double RA = Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(L))));
        RA = adjustDegrees(RA);
        // 5b. right ascension value needs to be in the same quadrant as L
        double lQuadrant = Math.floor(L / 90) * 90;
        double rQuadrant = Math.floor(RA / 90) * 90;
        // 5c. right ascension value needs to be converted into hours
        RA = (RA + lQuadrant - rQuadrant) / 15;
        // 6. calculate the Sun's declination
        double sinDecRad = 0.39782 * Math.sin(Math.toRadians(L));
        double cosDecRad = Math.cos(Math.asin(sinDecRad));
        // 7a. calculate the Sun's local hour angle
        double cosH = (Math.cos(Math.toRadians(zenith)) - (sinDecRad * Math.sin(latRad))) / (cosDecRad * Math.cos(latRad));
        if (cosH > 1 || cosH < -1)
            return Double.NaN;
        // 7b. finish calculating H and convert into hours
        double H = Math.toDegrees(Math.acos(cosH));
        if (sunrise)
            H = 360 - H;
        H = H / 15;
        // 8. calculate local mean time of rising/setting
        double T = H + RA - (0.06571 * t) - 6.622;
        // 9. adjust back to UTC
        return adjustTime(T - lngHour);
    }

    private static double adjustDegrees(double degrees) {
        if (degrees >= 360d)
            degrees -= 360d;
        if (degrees < 0)
            degrees += 360d;
        return degrees;
    }

    private static double adjustTime(double time) {
        if (time >= 24d)
            time -= 24d;
        if (time < 0)
            time += 24d;
        return time;
    }

    /**
     * Converts UTC time to local time and outputs it as string.
     */
    public CharSequence formatTime(double time) {
        return StringFormatter.timeR((int) (adjustTime(time + tzOffset) * 60));
    }

    public double getUtcOffset() {
        return tzOffset;
    }
}
