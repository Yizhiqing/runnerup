/*
 * Copyright (C) 2012 - 2014 jonas.oreland@gmail.com
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

package org.runnerup.workout;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;

import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.util.Formatter;
import org.runnerup.util.HRZones;
import org.runnerup.util.SafeParse;
import org.runnerup.view.AudioCueSettingsActivity;
import org.runnerup.workout.Workout.StepListEntry;
import org.runnerup.workout.feedback.AudioCountdownFeedback;
import org.runnerup.workout.feedback.AudioFeedback;
import org.runnerup.workout.feedback.CoachFeedback;
import org.runnerup.workout.feedback.CountdownFeedback;
import org.runnerup.workout.feedback.HRMStateChangeFeedback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class WorkoutBuilder {

    /**
     * @return workout based on SharedPreferences
     */
    public static Workout createDefaultWorkout(Resources res, SharedPreferences prefs,
                                               Dimension target) {
        Workout w = new Workout();
        w.sport = prefs.getInt(res.getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);

        if (prefs.getBoolean(res.getString(R.string.pref_countdown_active), false))
        {
            long val = 0;
            String vals = prefs.getString(res.getString(R.string.pref_countdown_time), "0");
            try {
                val = Long.parseLong(vals);
            } catch (NumberFormatException e) {
            }
            if (val > 0) {
                Step step = Step.createPauseStep(Dimension.TIME, val);
                w.steps.add(step);
            }
        }

        Step step = new Step();
        if (prefs.getBoolean(res.getString(R.string.pref_autolap_active), false)) {
            double val = 0;
            String vals = prefs.getString(res.getString(R.string.pref_autolap), "1000");
            try {
                val = Double.parseDouble(vals);
            } catch (NumberFormatException e) {
            }
            step.setAutolap(val);
        }

        addAutoPauseTrigger(res, step, prefs);
        w.steps.add(step);

        if (target == Dimension.PACE) {
            double unitMeters = Formatter.getUnitMeters(res, prefs);
            double seconds_per_unit = (double) SafeParse.parseSeconds(
                    prefs.getString(res.getString(R.string.pref_basic_target_pace_max), "00:05:00"), 5 * 60);
            int targetPaceRange = prefs.getInt(res.getString(R.string.pref_basic_target_pace_min_range), 15);
            double targetPaceMax = seconds_per_unit / unitMeters;
            double targetPaceMin = (targetPaceMax * unitMeters - targetPaceRange) / unitMeters;
            Range range = new Range(targetPaceMin, targetPaceMax);
            step.targetType = Dimension.PACE;
            step.targetValue = range;
        } else if (target == Dimension.HRZ) {
            HRZones hrCalc = new HRZones(res, prefs);
            int zone = prefs.getInt(res.getString(R.string.pref_basic_target_hrz), -1);
            if (zone >= 0) {
                Pair<Integer, Integer> vals = hrCalc.getHRValues(zone + 1);
                if (vals != null) {
                    step.targetType = Dimension.HR;
                    step.targetValue = new Range(vals.first, vals.second);
                }
            }
        }
        /*
         *
         */
        return w;
    }

    private static void addAutoPauseTrigger(Resources res, Step step, SharedPreferences prefs) {
        boolean enableAutoPause = prefs.getBoolean(res.getString(R.string.pref_autopause_active), false);
        if (!enableAutoPause)
            return;

        float autoPauseMinSpeed = 0;
        float autoPauseAfterSeconds = 4f;

        String val = prefs.getString(res.getString(R.string.pref_autopause_minpace), "20");
        try {
            float autoPauseMinPace = Float.parseFloat(val);
            if (autoPauseMinPace > 0)
                autoPauseMinSpeed = 1000 / (autoPauseMinPace * 60);
        } catch (NumberFormatException e) {
        }
        val = prefs.getString(res.getString(R.string.pref_autopause_afterseconds), "15");
        try {
            autoPauseAfterSeconds = Float.parseFloat(val);
        } catch (NumberFormatException e) {
        }
        AutoPauseTrigger tr = new AutoPauseTrigger(autoPauseAfterSeconds, autoPauseMinSpeed);
        step.triggers.add(tr);
    }

    public static Workout createDefaultIntervalWorkout(Resources res, SharedPreferences prefs) {
        Workout w = new Workout();
        w.sport = prefs.getInt(res.getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);
        final boolean warmup = true;
        final boolean cooldown = true;
        final boolean convertRestToRecovery = prefs.getBoolean(res.getString(
                R.string.pref_convert_interval_distance_rest_to_recovery), true);

        //noinspection ConstantConditions
        if (warmup) {
            Step step = new Step();
            step.intensity = Intensity.WARMUP;
            step.durationType = null;
            addAutoPauseTrigger(res, step, prefs);
            w.steps.add(step);
        }

        int repetitions = (int) SafeParse.parseDouble(prefs.getString(res.getString(R.string.pref_interval_repetitions), "1"),
                1);

        int intervalType = prefs.getInt(res.getString(R.string.pref_interval_type), 0);
        long intervalTime = SafeParse.parseSeconds(prefs.getString(res.getString(R.string.pref_interval_time), "00:04:00"),
                4 * 60);
        double intevalDistance = SafeParse.parseDouble(prefs.getString(res.getString(R.string.pref_interval_distance), "1000"),
                1000);
        int intervalRestType = prefs.getInt(res.getString(R.string.pref_interval_rest_type), 0);
        long intervalRestTime = SafeParse.parseSeconds(
                prefs.getString(res.getString(R.string.pref_interval_rest_time), "00:01:00"), 60);
        double intevalRestDistance = SafeParse.parseDouble(
                prefs.getString(res.getString(R.string.pref_interval_rest_distance), "200"), 200);

        RepeatStep repeat = new RepeatStep();
        repeat.repeatCount = repetitions;
        {
            Step step = new Step();
            switch (intervalType) {
                case 0: // Time
                    step.durationType = Dimension.TIME;
                    step.durationValue = intervalTime;
                    break;
                case 1: // Distance
                    step.durationType = Dimension.DISTANCE;
                    step.durationValue = intevalDistance;
                    break;
            }
            repeat.steps.add(step);

            //if (true) {
            Step rest = null;
            switch (intervalRestType) {
                case 0: // Time
                    rest = Step.createPauseStep(Dimension.TIME, intervalRestTime);
                    break;
                case 1: // Distance
                    if (!convertRestToRecovery) {
                        rest = Step.createPauseStep(Dimension.DISTANCE, intevalRestDistance);
                    } else {
                        rest = new Step();
                        rest.intensity = Intensity.RECOVERY;
                        rest.durationType = Dimension.DISTANCE;
                        rest.durationValue = intevalRestDistance;
                    }
                    break;
            }
            repeat.steps.add(rest);
            //}
        }
        w.steps.add(repeat);

        //noinspection ConstantConditions
        if (cooldown) {
            Step step = new Step();
            step.intensity = Intensity.COOLDOWN;
            step.durationType = null;
            addAutoPauseTrigger(res, step, prefs);
            w.steps.add(step);
        }

        return w;
    }

    public static boolean validateSeconds(String newValue) {
        // TODO move this somewhere
        long seconds = SafeParse.parseSeconds(newValue, -1);
        long seconds2 = SafeParse.parseSeconds(DateUtils.formatElapsedTime(seconds), -1);
        return seconds == seconds2;
    }

    public static SharedPreferences getAudioCuePreferences(Context ctx, SharedPreferences pref,
                                                           String key) {
        return getSubPreferences(ctx, pref, key,
                ctx.getString(R.string.Default),
                AudioCueSettingsActivity.SUFFIX);
    }

    private static SharedPreferences getSubPreferences(Context ctx, SharedPreferences pref,
                                                       String key, String defaultVal, String suffix) {
        String name = pref.getString(key, null);
        if (name == null || name.contentEquals(defaultVal)) {
            return pref;
        }
        return ctx.getSharedPreferences(name + suffix, Context.MODE_PRIVATE);
    }

    public static void addAudioCuesToWorkout(Resources res, Workout w, SharedPreferences prefs) {
        final boolean mute = prefs.getBoolean(res.getString(R.string.pref_mute_bool), false);
        w.setMute(mute);
        addAudioCuesToWorkout(res, w.steps, prefs);
    }

    private static void addAudioCuesToWorkout(Resources res, ArrayList<Step> steps,
                                              SharedPreferences prefs) {
        final boolean skip_startstop_cue = prefs.getBoolean(
                res.getString(R.string.cueinfo_skip_startstop), false);
        ArrayList<Trigger> triggers = createDefaultTriggers(res, prefs);
        boolean silent = triggers.size() == 0;
        final boolean coaching = prefs.getBoolean(res.getString(R.string.cueinfo_target_coaching),
                true);
        if (silent && coaching) {
            for (Step s : steps) {
                if (s.getTargetType() != null) {
                    silent = false;
                    break;
                }
            }
        }

        addPauseStopResumeTriggers(res, triggers, prefs);
        if (!silent)
        {
            EventTrigger ev = new EventTrigger();
            ev.event = Event.STARTED;
            ev.scope = Scope.STEP;
            ev.maxCounter = 1;
            ev.triggerAction.add(new AudioFeedback(R.string.cue_lap_started));
            triggers.add(ev);

            if (prefs.getBoolean(res.getString(R.string.pref_lap_started), false)) {
                EventTrigger ev2 = new EventTrigger(); // for autolap
                ev2.event = Event.STARTED;
                ev2.scope = Scope.LAP;
                ev2.skipCounter = 1; // skip above
                ev2.triggerAction.add(new AudioFeedback(R.string.cue_lap_started));
                triggers.add(ev2);
            }

            if (prefs.getBoolean(res.getString(R.string.pref_cue_hrm_connection), false)) {
                HRMStateTrigger hrmState = new HRMStateTrigger();
                hrmState.triggerAction.add(new HRMStateChangeFeedback(hrmState));
                triggers.add(hrmState);
            }
        }

        Step stepArr[] = new Step[steps.size()];
        steps.toArray(stepArr);
        for (int i = 0; i < stepArr.length; i++) {
            // Step prev = i == 0 ? null : stepArr[i-1];
            Step step = stepArr[i];
            Step next = i + 1 == stepArr.length ? null : stepArr[i + 1];
            switch (step.getIntensity()) {
                case REPEAT:
                    addAudioCuesToWorkout(res, ((RepeatStep) step).steps, prefs);
                    break;
                case ACTIVE:
                    step.triggers.addAll(triggers);
                    if (!silent && (next == null || next.getIntensity() != step.getIntensity()))
                    {
                        EventTrigger ev = new EventTrigger();
                        ev.event = Event.COMPLETED;
                        ev.scope = Scope.STEP;
                        ev.triggerAction.add(new AudioFeedback(R.string.cue_lap_completed));
                        step.triggers.add(ev);

                        Trigger elt = hasEndOfLapTrigger(triggers);
                        if (elt != null) {
                            /* Add feedback after "end of lap" */
                            ev.triggerAction.addAll(elt.triggerAction);
                            /* suppress empty STEP COMPLETED */
                            ev.triggerSuppression.add(EndOfLapSuppression.EmptyLapSuppression);

                            /* And suppress last end of lap trigger */
                            elt.triggerSuppression.add(EndOfLapSuppression.EndOfLapSuppression);
                        }
                    }
                    checkDuplicateTriggers(step);
                    // {
                    // Log.e("WorkoutBuilder", "triggers: ");
                    // for (Trigger t : step.triggers) {
                    // System.err.print(t + " ");
                    // }
                    // Log.e("WorkoutBuilder", "");
                    // }

                    break;
                case RECOVERY:
                case RESTING: {
                    if (step.durationType != null) {
                        IntervalTrigger trigger = new IntervalTrigger();
                        trigger.dimension = step.durationType;
                        trigger.first = 1;
                        trigger.interval = 1;
                        trigger.scope = Scope.STEP;
                        trigger.triggerAction.add(new CountdownFeedback(Scope.STEP, step.durationType));
                        step.triggers.add(trigger);
                    }
                    addPauseStopResumeTriggers(res, step.triggers, prefs);

                    if (!silent) {
                        createAudioCountdown(step);
                    }
                    break;
                }
                case WARMUP:
                case COOLDOWN:
                    addPauseStopResumeTriggers(res, step.triggers, prefs);
                    if (!skip_startstop_cue) {
                        EventTrigger ev = new EventTrigger();
                        ev.event = Event.STARTED;
                        ev.scope = Scope.STEP;
                        ev.triggerAction.add(new AudioFeedback(step.getIntensity() == Intensity.WARMUP ?
                                R.string.cue_warmup_started : R.string.cue_cooldown_started));
                        step.triggers.add(ev);
                    }
                    break;
            }

            if (coaching && step.getTargetType() != null) {
                Range range = step.getTargetValue();
                int averageSeconds = SafeParse.parseInt(prefs.getString(
                        res.getString(R.string.pref_target_pace_moving_average_seconds), "20"), 20);
                int graceSeconds = SafeParse.parseInt(
                        prefs.getString(res.getString(R.string.pref_target_pace_grace_seconds), "30"),
                        30);
                TargetTrigger tr = new TargetTrigger(step.getTargetType(), averageSeconds,
                        graceSeconds);
                tr.scope = Scope.STEP;
                tr.range = range;
                tr.triggerAction.add(new CoachFeedback(Scope.ACTIVITY, step.getTargetType(), range,
                        tr));
                step.triggers.add(tr);
            }
        }
    }

    interface TriggerFilter {
        boolean match(Trigger trigger);
    }

    private static Trigger hasTrigger(List<Trigger> triggers, TriggerFilter filter) {
        for (Trigger t : triggers) {
            if (filter.match(t))
                return t;
        }
        return null;
    }

    private static Trigger hasEndOfLapTrigger(List<Trigger> triggers) {
        return hasTrigger(triggers, new TriggerFilter() {

            @Override
            public boolean match(Trigger trigger) {
                if (trigger == null)
                    return false;

                if (!(trigger instanceof EventTrigger))
                    return false;
                EventTrigger et = (EventTrigger) trigger;
                return (et.event == Event.COMPLETED && et.scope == Scope.LAP);
            }
        });
    }

    private static void checkDuplicateTriggers(Step step) {
        if (hasEndOfLapTrigger(step.triggers) != null) {
            Log.e("WorkoutBuilder", "hasEndOfLapTrigger()");
            /*
             * The end of lap trigger can be a duplicate of a distance based
             * interval trigger 1) in a step with distance duration, that is a
             * multiple of the interval-distance e.g interval-trigger-distance =
             * 100m duration = 1000m, then set max count = 9 2) in a step with
             * autolap 500m and interval-trigger-distance 1000 then remove the
             * trigger
             */
            ArrayList<TriggerSuppression> list = new ArrayList<>();
            if (step.getAutolap() > 0) {
                list.add(new EndOfLapSuppression(step.getAutolap()));
            }

            if (step.getDurationType() == Dimension.DISTANCE) {
                list.add(new EndOfLapSuppression(step.getDurationValue()));
            }
            for (Trigger t : step.triggers) {
                if (!(t instanceof IntervalTrigger))
                    continue;
                IntervalTrigger it = (IntervalTrigger) t;
                if (it.dimension != Dimension.DISTANCE)
                    continue;
                it.triggerSuppression.addAll(list);
            }
        }
    }

    private static ArrayList<Trigger> createDefaultTriggers(Resources res, SharedPreferences prefs) {
        ArrayList<Feedback> feedback = new ArrayList<>();
        ArrayList<Trigger> triggers = new ArrayList<>();

        if (prefs.getBoolean(res.getString(R.string.cue_time), false)) {
            long val = 0;
            String vals = prefs.getString(res.getString(R.string.cue_time_intervall), "120");
            try {
                val = Long.parseLong(vals);
            } catch (NumberFormatException e) {
            }
            if (val > 0) {
                IntervalTrigger t = new IntervalTrigger();
                t.first = val;
                t.interval = val;
                t.scope = Scope.STEP;
                t.dimension = Dimension.TIME;
                triggers.add(t);
            }
        }

        if (prefs.getBoolean(res.getString(R.string.cue_distance), false)) {
            long val = 0;
            String vals = prefs.getString(res.getString(R.string.cue_distance_intervall), "1000");
            try {
                val = Long.parseLong(vals);
            } catch (NumberFormatException e) {
            }
            if (val > 0) {
                IntervalTrigger t = new IntervalTrigger();
                t.first = val;
                t.interval = val;
                t.scope = Scope.STEP;
                t.dimension = Dimension.DISTANCE;
                triggers.add(t);
            }
        }

        if (prefs.getBoolean(res.getString(R.string.cue_end_of_lap), false)) {
            EventTrigger ev = new EventTrigger();
            ev.event = Event.COMPLETED;
            ev.scope = Scope.LAP;
            triggers.add(ev);
        }

        addFeedbackFromPreferences(prefs, res, feedback);

        for (Trigger t : triggers) {
            t.triggerAction = feedback;
            /* suppress empty laps */
            t.triggerSuppression.add(EndOfLapSuppression.EmptyLapSuppression);
        }

        return triggers;
    }

    private static void addPauseStopResumeTriggers(Resources res, ArrayList<Trigger> list,
                                                   SharedPreferences prefs) {
        if (!prefs.getBoolean(res.getString(R.string.cueinfo_skip_startstop), false)) {
            {
                EventTrigger p = new EventTrigger();
                p.event = Event.PAUSED;
                p.scope = Scope.STEP;
                p.triggerAction.add(new AudioFeedback(R.string.cue_activity_paused));
                list.add(p);
            }

            {
                EventTrigger r = new EventTrigger();
                r.event = Event.RESUMED;
                r.scope = Scope.STEP;
                r.triggerAction.add(new AudioFeedback(R.string.cue_activity_resumed));
                list.add(r);
            }

            {
                EventTrigger ev = new EventTrigger();
                ev.event = Event.STOPPED;
                ev.scope = Scope.STEP;
                ev.triggerAction.add(new AudioFeedback(R.string.cue_activity_stopped));
                list.add(ev);
            }
        }
    }

    private static void createAudioCountdown(Step step) {
        if (step.getDurationType() == null) {
            return;
        }

        double first;
        ArrayList<Double> list = new ArrayList<>();
        switch (step.getDurationType()) {
            case TIME:
                first = 60; // 1 minute
                Double tmp0[] = {
                        60d, 30d, 10d, 5d, 3d, 2d, 1d
                };
                list.addAll(Arrays.asList(tmp0));
                break;
            case DISTANCE:
                first = 100; // 100 meters
                Double tmp1[] = {
                        100d, 50d, 20d, 10d
                };
                list.addAll(Arrays.asList(tmp1));
                break;
            default:
                return;
        }

        if (step.getDurationValue() > first) {
            /*
             * If longer than limit...create a Interval trigger for ">" part
             */
            IntervalTrigger trigger = new IntervalTrigger();
            trigger.dimension = step.getDurationType();
            trigger.scope = Scope.STEP;
            trigger.first = first;
            trigger.interval = first;
            trigger.triggerAction
                    .add(new AudioCountdownFeedback(Scope.STEP, step.getDurationType()));
            step.triggers.add(trigger);
        }

        /*
         * Then create a list trigger for reminder...
         */
        ArrayList<Double> triggerTimes = new ArrayList<>();
        for (Double d : list) {
            if (d >= step.getDurationValue())
                continue;
            double val = step.getDurationValue() - d;
            if ((val % first) == 0) {
                continue; // handled by interval trigger
            }
            double margin = 0.4d; // add a bit of margin, NOTE: less than 0.5
            triggerTimes.add(d + margin);
        }

        {
            ListTrigger trigger = new ListTrigger();
            trigger.remaining = true;
            trigger.dimension = step.getDurationType();
            trigger.scope = Scope.STEP;
            trigger.triggerTimes = triggerTimes;
            trigger.triggerAction
                    .add(new AudioCountdownFeedback(Scope.STEP, step.getDurationType()));
            step.triggers.add(trigger);
        }


        /*
         * Add add information just when pause step starts...
         */
        EventTrigger ev = new EventTrigger();
        ev.event = Event.STARTED;
        ev.scope = Scope.STEP;
        ev.triggerAction.add(new AudioCountdownFeedback(Scope.STEP, step.getDurationType()));
        step.triggers.add(ev);
    }

    public static void prepareWorkout(Resources res, SharedPreferences prefs, Workout w,
                                      boolean basic) {
        List<StepListEntry> steps = w.getStepList();

        /*
         * Add/remove autolap
         */
        boolean autolap = prefs.getBoolean(res.getString(R.string.pref_autolap_active), false);
        if (!basic) {
            autolap = prefs.getBoolean(res.getString(R.string.pref_step_autolap_active), autolap);
        }
        if (autolap) {
            double val = 0;
            String vals = prefs.getString(res.getString(R.string.pref_autolap), "1000");
            try {
                val = Double.parseDouble(vals);
            } catch (NumberFormatException e) {
            }
            Log.i("WorkoutBuilder", "setAutolap(" + val + ")");
            for (StepListEntry s : steps) {
                s.step.setAutolap(0); // reset
                switch (s.step.getIntensity()) {
                    case ACTIVE:
                        s.step.setAutolap(val);
                        break;
                    case RECOVERY:
                    case RESTING:
                    case WARMUP:
                    case COOLDOWN:
                    case REPEAT:
                        break;
                }
            }
        }

        /*
         * Add countdowns after steps with duration "until pressed"
         * - if next is not a countdown
         * - and current is not rest
         */
        if (prefs.getBoolean(res.getString(R.string.pref_step_countdown_active), true))
        {
            long val = 15; // default 15s
            String vals = prefs.getString(res.getString(R.string.pref_step_countdown_time), "15");
            try {
                val = Long.parseLong(vals);
            } catch (NumberFormatException e) {
            }
            if (val > 0) {
                StepListEntry stepArr[] = new StepListEntry[steps.size()];
                steps.toArray(stepArr);
                for (int i = 0; i < stepArr.length; i++) {
                    // Step prev = i == 0 ? null : stepArr[i-1];
                    Step step = stepArr[i].step;
                    Step next = i + 1 == stepArr.length ? null : stepArr[i + 1].step;

                    if (step.durationType != null)
                        continue;

                    if (step.intensity == Intensity.REPEAT || step.intensity == Intensity.RESTING)
                        continue;

                    if (next == null)
                        continue;

                    if (next.durationType == Dimension.TIME && next.intensity == Intensity.RESTING)
                        continue;

                    Step s = Step.createPauseStep(Dimension.TIME, val);
                    if (stepArr[i].parent == null) {
                        w.steps.add(i + 1, s);
                        Log.e("WorkoutBuilder", "Added step at index: " + (i + 1));
                    } else {
                        RepeatStep rs = (RepeatStep) stepArr[i].parent;
                        int idx = rs.steps.indexOf(step);
                        rs.steps.add(idx, s);
                        Log.e("WorkoutBuilder", "Added step at index: " + (i + 1) + " repeat index: "
                                + (idx + 1));
                    }
                }
            }
        }
    }

    public static void addFeedbackFromPreferences(SharedPreferences prefs,
                                                  Resources res, ArrayList<Feedback> feedback) {
        int feedbackStart = feedback.size();

        /* TOTAL */
        if (prefs.getBoolean(res.getString(R.string.cueinfo_total_distance), false)) {
            feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.DISTANCE));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_total_time), false)) {
            feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.TIME));
        }
        if (Dimension.SPEED_CUE_ENABLED
                && prefs.getBoolean(res.getString(R.string.cueinfo_total_speed), false)) {
            feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.SPEED));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_total_pace), false)) {
            feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.PACE));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_total_hr), false)) {
            feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.HR));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_total_hrz), false)) {
            feedback.add(new AudioFeedback(Scope.ACTIVITY, Dimension.HRZ));
        }

        /* STEP */
        if (prefs.getBoolean(res.getString(R.string.cueinfo_step_distance), false)) {
            feedback.add(new AudioFeedback(Scope.STEP, Dimension.DISTANCE));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_step_time), false)) {
            feedback.add(new AudioFeedback(Scope.STEP, Dimension.TIME));
        }
        if (Dimension.SPEED_CUE_ENABLED
                && prefs.getBoolean(res.getString(R.string.cueinfo_step_speed), false)) {
            feedback.add(new AudioFeedback(Scope.STEP, Dimension.SPEED));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_step_pace), false)) {
            feedback.add(new AudioFeedback(Scope.STEP, Dimension.PACE));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_step_hr), false)) {
            feedback.add(new AudioFeedback(Scope.STEP, Dimension.HR));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_step_hrz), false)) {
            feedback.add(new AudioFeedback(Scope.STEP, Dimension.HRZ));
        }

        /* LAP */
        if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_distance), false)) {
            feedback.add(new AudioFeedback(Scope.LAP, Dimension.DISTANCE));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_time), false)) {
            feedback.add(new AudioFeedback(Scope.LAP, Dimension.TIME));
        }
        if (Dimension.SPEED_CUE_ENABLED
                && prefs.getBoolean(res.getString(R.string.cueinfo_lap_speed), false)) {
            feedback.add(new AudioFeedback(Scope.LAP, Dimension.SPEED));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_pace), false)) {
            feedback.add(new AudioFeedback(Scope.LAP, Dimension.PACE));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_hr), false)) {
            feedback.add(new AudioFeedback(Scope.LAP, Dimension.HR));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_lap_hrz), false)) {
            feedback.add(new AudioFeedback(Scope.LAP, Dimension.HRZ));
        }

        /* CURRENT */
        if (prefs.getBoolean(res.getString(R.string.cueinfo_current_pace), false)) {
            feedback.add(new AudioFeedback(Scope.CURRENT, Dimension.PACE));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_current_speed), false)) {
            feedback.add(new AudioFeedback(Scope.CURRENT, Dimension.SPEED));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_current_hr), false)) {
            feedback.add(new AudioFeedback(Scope.CURRENT, Dimension.HR));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_current_hrz), false)) {
            feedback.add(new AudioFeedback(Scope.CURRENT, Dimension.HRZ));
        }
        if (prefs.getBoolean(res.getString(R.string.cueinfo_current_cad), false)) {
            feedback.add(new AudioFeedback(Scope.CURRENT, Dimension.CAD));
        }

        // Insert Scope
        for (int i=feedbackStart; i<feedback.size(); i++)
        {
            if (feedback.get(i) instanceof AudioFeedback &&
                    (i == 0 ||
                            feedback.get(i-1) == null ||
                            !(feedback.get(i-1) instanceof AudioFeedback) ||
                            ((AudioFeedback) feedback.get(i-1)).getScope() == null ||
                            ((AudioFeedback) feedback.get(i-1)).getScope() != ((AudioFeedback) feedback.get(i)).getScope())) {
                feedback.add(i, new AudioFeedback(((AudioFeedback)feedback.get(i)).getScope()));
                i++;
            }
        }
    }
}