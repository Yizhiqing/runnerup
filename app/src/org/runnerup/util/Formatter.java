/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;

import org.runnerup.R;
import org.runnerup.workout.Dimension;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class Formatter implements OnSharedPreferenceChangeListener {

    private Context context = null;
    private Resources resources = null;
    private LocaleResources cueResources = null;
    private SharedPreferences sharedPreferences = null;
    private java.text.DateFormat dateFormat = null;
    private java.text.DateFormat timeFormat = null;
    private java.text.DateFormat monthFormat = null;
    private java.text.DateFormat dayOfMonthFormat = null;
    //private HRZones hrZones = null;

    private boolean metric = true;
    private String base_unit = "km";
    private double base_meters = km_meters;

    public final static double km_meters = 1000.0;
    public final static double mi_meters = 1609.34;
    public final static double meters_per_foot = 0.3048;

    public enum Format {
        CUE,       // for text to speech
        CUE_SHORT, // brief for tts
        CUE_LONG,  // long for tts
        TXT,       // same as TXT_SHORT but without unit
        TXT_SHORT, // brief for printing
        TXT_LONG,  // long for printing
        TXT_TIMESTAMP, // For current time e.g 13:41:24
    }

    public Formatter(Context ctx) {
        context = ctx;
        resources = ctx.getResources();
        cueResources = getCueLangResources(ctx);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
        timeFormat = android.text.format.DateFormat.getTimeFormat(ctx);
        monthFormat = new SimpleDateFormat("MMM yyyy", cueResources.defaultLocale);
        dayOfMonthFormat = new SimpleDateFormat("E d", cueResources.defaultLocale);
        //hrZones = new HRZones(context);

        setUnit();
    }

    private class LocaleResources {
        final Resources resources;
        final Configuration configuration;
        final Locale defaultLocale;
        final Locale audioLocale;

        LocaleResources(Context ctx, Locale configAudioLocale) {
            resources = ctx.getResources();
            configuration = resources.getConfiguration();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    !ctx.getResources().getConfiguration().getLocales().isEmpty()) {
                defaultLocale = configuration.getLocales().get(0);
            } else {
                //noinspection deprecation
                defaultLocale = configuration.locale;
            }

            if (configAudioLocale == null) {
                audioLocale = defaultLocale;
            } else {
                audioLocale = configAudioLocale;
            }
        }

        void setLang(Locale locale) {
            if (Build.VERSION.SDK_INT >= 17) {
                configuration.setLocale(locale);
            } else {
                //noinspection deprecation
                configuration.locale = locale;
            }
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        }

        public String getString(int id) throws Resources.NotFoundException {
            setLang(audioLocale);
            String result = resources.getString(id);
            setLang(defaultLocale);
            return result;
        }

        //General getQuantityString accepts "Object ...", limit to exactly one argument (current use) to avoid runtime crashes
        public String getQuantityString(int id, int quantity, Object formatArgs) throws Resources.NotFoundException {
            setLang(audioLocale);
            String result = resources.getQuantityString(id, quantity, formatArgs);
            setLang(defaultLocale);
            return result;
        }
    }

    public static Locale getAudioLocale(Context ctx) {
        Resources res = ctx.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (prefs.contains(res.getString(R.string.pref_audio_lang))) {
            Log.e("Formatter", "Audio language: " +
                    prefs.getString(res.getString(R.string.pref_audio_lang), null));
            return new Locale(prefs.getString(res.getString(R.string.pref_audio_lang), "en"));
        }
        return null;
    }

    private LocaleResources getCueLangResources(Context ctx) {
        Locale loc = getAudioLocale(ctx);
        return new LocaleResources(ctx, loc);
    }

    public String getCueString(int msgId) {
        return cueResources.getString(msgId);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key != null && context.getString(R.string.pref_unit).contentEquals(key))
            setUnit();
    }

    private void setUnit() {
        metric = getUseMetric(context.getResources(), sharedPreferences, null);

        if (metric) {
            base_unit = "km";
            base_meters = km_meters;
        } else {
            base_unit = "mi";
            base_meters = mi_meters;
        }
    }

    public String getDistanceUnit(Format target) {
        switch (target) {
            case CUE:
            case CUE_LONG:
            case CUE_SHORT:
            case TXT_LONG:
                //No string for long - not used
                // return resources.getString(km ? R.plurals.cue_kilometer : R.plurals.cue_mile);
            case TXT:
            case TXT_SHORT:
                return resources.getString(metric ? R.string.metrics_distance_km : R.string.metrics_distance_mi);
        }
        return null;
    }

    public static boolean getUseMetric(Resources res, SharedPreferences prefs, Editor editor) {
        boolean _km;
        String unit = prefs.getString(res.getString(R.string.pref_unit), null);
        if (unit == null)
            _km = guessDefaultUnit(res, prefs, editor);
        else if (unit.contentEquals("km"))
            _km = true;
        else if (unit.contentEquals("mi"))
            _km = false;
        else
            _km = guessDefaultUnit(res, prefs, editor);

        return _km;
    }

    private static boolean guessDefaultUnit(Resources res, SharedPreferences prefs, Editor editor) {
        String countryCode = Locale.getDefault().getCountry();
        Log.e("Formatter", "guessDefaultUnit: countryCode: " + countryCode);
        if (countryCode == null)
            return true; // km;
        String key = res.getString(R.string.pref_unit);
        if ("US".contentEquals(countryCode) ||
                "GB".contentEquals(countryCode)) {
            if (editor != null)
                editor.putString(key, "mi");
            return false;
        }
        else {
            if (editor != null)
                editor.putString(key, "km");
        }
        return true;
    }

    public double getUnitMeters() {
        return this.base_meters;
    }

    public static double getUnitMeters(Resources res, SharedPreferences prefs) {
        if (getUseMetric(res, prefs, null))
            return km_meters;
        else
            return mi_meters;
    }

    public String getUnitString() {
        return this.base_unit;
    }

    public String format(Format target, Dimension dimension, double value) {
        switch (dimension) {
            case DISTANCE:
                return formatDistance(target, Math.round(value));
            case TIME:
                return formatElapsedTime(target, Math.round(value));
            case PACE:
                return formatPace(target, value);
            case HR:
                return formatHeartRate(target, value);
            case HRZ:
                return formatHeartRateZone(target, value);
            case SPEED:
                return formatSpeed(target, value);
            case CAD:
                return formatCadence(target, value);
            case TEMPERATURE:
                return formatCadence(target, value);//TODO
            case PRESSURE:
                return formatCadence(target, value);//TODO
        }
        return "";
    }

    public String formatElapsedTime(Format target, long seconds) {
        switch (target) {
            case CUE:
            case CUE_SHORT:
                return cueElapsedTime(seconds, false);
            case CUE_LONG:
                return cueElapsedTime(seconds, true);
            case TXT:
            case TXT_SHORT:
                return DateUtils.formatElapsedTime(seconds);
            case TXT_LONG:
                return txtElapsedTime(seconds);
            case TXT_TIMESTAMP:
                return formatTime(seconds);
        }
        return "";
    }

    private String cueElapsedTime(long seconds, boolean includeDimension) {
        int hours = 0;
        int minutes = 0;
        if (seconds >= 3600) {
            hours = (int)(seconds / 3600);
            seconds -= hours * 3600;
        }
        if (seconds >= 60) {
            minutes = (int)(seconds / 60);
            seconds -= minutes * 60;
        }
        StringBuilder s = new StringBuilder();
        if (hours > 0) {
            includeDimension = true;
            s.append(cueResources.getQuantityString(R.plurals.cue_hour, hours, hours));
        }
        if (minutes > 0) {
            if (hours > 0)
                s.append(" ");
            includeDimension = true;
            s.append(cueResources.getQuantityString(R.plurals.cue_minute, minutes, minutes));
        }
        if (seconds > 0) {
            if (hours > 0 || minutes > 0)
                s.append(" ");

            if (includeDimension) {
                s.append(cueResources.getQuantityString(R.plurals.cue_second, (int)seconds, (int)seconds));
            } else {
                s.append(seconds);
            }
        }
        return s.toString();
    }

    private String txtElapsedTime(long seconds) {
        long hours = 0;
        long minutes = 0;
        if (seconds >= 3600) {
            hours = seconds / 3600;
            seconds -= hours * 3600;
        }
        if (seconds >= 60) {
            minutes = seconds / 60;
            seconds -= minutes * 60;
        }
        StringBuilder s = new StringBuilder();
        if (hours > 0) {
            s.append(hours).append(" ").append(resources.getString(R.string.metrics_elapsed_h));
        }
        if (minutes > 0) {
            if (hours > 0)
                s.append(" ");
            if (hours > 0 || seconds > 0)
                s.append(minutes).append(" ").append(resources.getString(R.string.metrics_elapsed_m));
            else
                s.append(minutes).append(" ").append(resources.getString(R.string.metrics_elapsed_min));
        }
        if (seconds > 0) {
            if (hours > 0 || minutes > 0)
                s.append(" ");
            s.append(seconds).append(" ").append(resources.getString(R.string.metrics_elapsed_s));
        }
        return s.toString();
    }

    /**
     * Format heart rate
     *
     * @param target
     * @param heart_rate
     * @return
     */
    public String formatHeartRate(Format target, double heart_rate) {
        int bpm = (int) Math.round(heart_rate);
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return cueResources.getQuantityString(R.plurals.cue_bpm, bpm, bpm);
            case TXT:
            case TXT_SHORT:
            case TXT_LONG:
                return Integer.toString(bpm);
        }
        return "";
    }

    /**
     * Format cadence
     *
     * @param target
     * @param val
     * @return
     */
    public String formatCadence(Format target, double val) {
        int val2 = (int) Math.round(val);
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return cueResources.getQuantityString(R.plurals.cue_rpm, val2, val2);
            case TXT:
            case TXT_SHORT:
            case TXT_LONG:
                return Integer.toString((int) val2);
        }
        return "";
    }

    private String formatHeartRateZone(Format target, double hrZone) {
        switch (target) {
            case TXT:
            case TXT_SHORT:
                return Integer.toString((int) Math.round(hrZone));
            case TXT_LONG:
                return Double.toString(Math.round(10.0 * hrZone) / 10.0);
            case CUE_SHORT:
                return cueResources.getString(R.string.heart_rate_zone) + " "
                        + Integer.toString((int) Math.floor(hrZone));
            case CUE:
            case CUE_LONG:
                return cueResources.getString(R.string.heart_rate_zone) + " "
                        + Double.toString(Math.floor(10.0 * hrZone) / 10.0);
        }
        return "";
    }

    /**
     * Format pace
     * 
     * @param target
     * @param seconds_per_meter
     * @return
     */
    public String formatPace(Format target, double seconds_per_meter) {
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return cuePace(seconds_per_meter);
            case TXT:
            case TXT_SHORT:
                return txtPace(seconds_per_meter, false);
            case TXT_LONG:
                return txtPace(seconds_per_meter, true);
        }
        return "";
    }

    /**
     * @return pace unit string
     */
    public String getPaceUnit() {//Resources resources, SharedPreferences sharedPreferences) {
        int du = metric ? R.string.metrics_distance_km : R.string.metrics_distance_mi;
        return resources.getString(R.string.metrics_elapsed_min) + "/" + resources.getString(du);
    }

    /**
     * @param seconds_per_meter
     * @return string suitable for printing according to settings
     */
    private String txtPace(double seconds_per_meter, boolean includeUnit) {
        long val = Math.round(base_meters * seconds_per_meter);
        String str = DateUtils.formatElapsedTime(val);
        if (!includeUnit)
            return str;
        else {
            int res = metric ? R.string.metrics_distance_km : R.string.metrics_distance_mi;
            return str + "/" + resources.getString(res);
        }
    }

    private String cuePace(double seconds_per_meter) {
        int seconds_per_unit = (int)Math.round(base_meters * seconds_per_meter);
        int hours_per_unit = 0;
        int minutes_per_unit = 0;
        if (seconds_per_unit >= 3600) {
            hours_per_unit = seconds_per_unit / 3600;
            seconds_per_unit -= hours_per_unit * 3600;
        }
        if (seconds_per_unit >= 60) {
            minutes_per_unit = seconds_per_unit / 60;
            seconds_per_unit -= minutes_per_unit * 60;
        }
        StringBuilder s = new StringBuilder();
        if (hours_per_unit > 0) {
            s.append(cueResources.getQuantityString(R.plurals.cue_hour, hours_per_unit, hours_per_unit));
        }
        if (minutes_per_unit > 0) {
            if (hours_per_unit > 0)
                s.append(" ");
            s.append(cueResources.getQuantityString(R.plurals.cue_minute, minutes_per_unit, minutes_per_unit));
        }
        if (seconds_per_unit > 0) {
            if (hours_per_unit > 0 || minutes_per_unit > 0)
                s.append(" ");
            s.append(cueResources.getQuantityString(R.plurals.cue_second, seconds_per_unit, seconds_per_unit));
        }
        s.append(" ").append(cueResources.getString(metric? R.string.cue_perkilometer : R.string.cue_permile));
        return s.toString();
    }

    /**
     * Format Speed
     *
     * @param target
     * @param seconds_per_meter
     * @return
     */
    private String formatSpeed(Format target, double seconds_per_meter) {
        switch (target) {
            case CUE:
            case CUE_SHORT:
            case CUE_LONG:
                return cueSpeed(seconds_per_meter);
            case TXT:
            case TXT_SHORT:
                return txtSpeed(seconds_per_meter, false);
            case TXT_LONG:
                return txtSpeed(seconds_per_meter, true);
        }
        return "";
    }

    /**
     * @param meter_per_seconds
     * @return string suitable for printing according to settings
     */
    private String txtSpeed(double meter_per_seconds, boolean includeUnit) {
        double distance_per_hour = meter_per_seconds * 3600 / base_meters;
        String str = String.format(cueResources.defaultLocale, "%.1f", distance_per_hour);
        if (!includeUnit)
            return str;
        else {
            int res = metric ? R.string.metrics_distance_km : R.string.metrics_distance_mi;
            return str +
                    resources.getString(res) +
                    "/" +
                    resources.getString(R.string.metrics_elapsed_h);
        }
    }

    private String cueSpeed(double meter_per_seconds) {
        double distance_per_hour = meter_per_seconds  * 3600 / base_meters;
        String str = String.format(cueResources.audioLocale, "%.1f", distance_per_hour);
        return cueResources.getQuantityString(metric ? R.plurals.cue_kilometers_per_hour : R.plurals.cue_miles_per_hour,
                (int)distance_per_hour, str);
    }

    /**
     * @param date date to format
     * @return month and year as a string (e.g. "Feb 2000")
     */
    public String formatMonth(Date date) {
        return monthFormat.format(date);
    }

    /**
     * @param date date to format
     * @return day of the week and day of the month as a string (e.g. "Fri 13")
     */
    public String formatDayOfMonth(Date date) {
        return dayOfMonthFormat.format(date);
    }

    /**
     * @param seconds_since_epoch
     * @return date and time as a string
     */
    public String formatDateTime(long seconds_since_epoch) {
        // ignore target
        // milliseconds
                                                                 // as argument
        return dateFormat.format(seconds_since_epoch * 1000) +
                " " +
                timeFormat.format(seconds_since_epoch * 1000);
    }

    /**
     * Format "elevation" like distances
     * @param target
     * @param meters
     * @return Formatted string with unit
     */
    public String formatElevation(Format target, double meters) {
        // TODO add (plural) strings and handle Format, cues
        DecimalFormat df = new DecimalFormat("#.0");
        if (metric) {
            return df.format(meters) + " m";
        } else {
            return df.format(meters / meters_per_foot) + " ft";
        }
    }

    /**
     * @param target
     * @param meters
     * @return
     */
    public String formatDistance(Format target, long meters) {
        switch (target) {
            case CUE:
            case CUE_LONG:
            case CUE_SHORT:
                return formatDistance(meters, false);
            case TXT:
                return formatDistanceInKmOrMiles(meters);
            case TXT_SHORT:
                return formatDistance(meters, true);
            case TXT_LONG:
                return Long.toString(meters) + " m";
        }
        return null;
    }

    private double getRoundedDistanceInKmOrMiles(long meters) {
        double decimals = 2;
        return round(meters/base_meters, decimals);
    }

    private String formatDistanceInKmOrMiles(long meters) {
        return String.format(cueResources.defaultLocale, "%.2f", getRoundedDistanceInKmOrMiles(meters));
    }

    private String formatDistance(long meters, boolean txt) {
        String res;
        if (meters >= base_meters) {
            double val = getRoundedDistanceInKmOrMiles(meters);
            if (txt) {
                res = String.format(cueResources.defaultLocale, "%.2f %s", val,
                        resources.getString(metric ? R.string.metrics_distance_km : R.string.metrics_distance_mi));
            } else {
                // Get a localized presentation string, used with the localized plurals string
                String v2 = String.format(cueResources.audioLocale, "%.2f", val);
                res = cueResources.getQuantityString(metric ? R.plurals.cue_kilometer : R.plurals.cue_mile, (int)val, v2);
            }
        } else {
            // Present distance in meters if less than 1km/1mi (no strings for feet)
            if (txt) {
                res = String.format(cueResources.defaultLocale, "%d %s", meters, resources.getString(R.string.metrics_distance_m));
            }
            else {
                res = cueResources.getQuantityString(R.plurals.cue_meter, (int)meters, meters);
            }
        }
        return res;
    }

    public String formatRemaining(Format target, Dimension dimension, double value) {
        switch (dimension) {
            case DISTANCE:
                return formatRemainingDistance(target, value);
            case TIME:
                return formatRemainingTime(target, value);
            case PACE:
            case SPEED:
            case HR:
            case CAD:
            case TEMPERATURE:
            case PRESSURE:
            default:
                break;
        }
        return "";
    }

    private String formatRemainingTime(Format target, double value) {
        return formatElapsedTime(target, Math.round(value));
    }

    private String formatRemainingDistance(Format target, double value) {
        return formatDistance(target, Math.round(value));
    }

    public String formatName(String first, String last) {
        if (first != null && last != null)
            return first + " " + last;
        else if (first == null && last != null)
            return last;
        else if (first != null /*&& last == null*/)
            return first;
        return "";
    }

    private String formatTime(long seconds_since_epoch) {
        return timeFormat.format(seconds_since_epoch * 1000);
    }

    public static double round(double base, double decimals) {
        double exp = Math.pow(10, decimals);
        return Math.round(base * exp) / exp;
    }

    public static double getUnitMeters(Context mContext) {
        return getUnitMeters(mContext.getResources(),
                PreferenceManager.getDefaultSharedPreferences(mContext));
    }
}
