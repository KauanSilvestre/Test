package com.fadcam.kcam.schedule;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Persists schedules as a JSON array in a private SharedPreferences file. */
public final class ScheduleStore {

    private static final String PREFS = "kcam_schedules";
    private static final String KEY = "schedules";
    private static final String KEY_NEXT_ID = "next_id";

    private final SharedPreferences prefs;

    public ScheduleStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<RecordingSchedule> all() {
        List<RecordingSchedule> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(RecordingSchedule.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {
            // corrupt data → treat as empty rather than crash the alarm receiver
        }
        return out;
    }

    public synchronized RecordingSchedule get(int id) {
        for (RecordingSchedule s : all()) if (s.id == id) return s;
        return null;
    }

    public synchronized int newId() {
        int id = prefs.getInt(KEY_NEXT_ID, 1);
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply();
        return id;
    }

    public synchronized void put(RecordingSchedule s) {
        List<RecordingSchedule> list = all();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == s.id) {
                list.set(i, s);
                replaced = true;
            }
        }
        if (!replaced) list.add(s);
        save(list);
    }

    public synchronized void remove(int id) {
        List<RecordingSchedule> list = all();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id == id) list.remove(i);
        save(list);
    }

    private void save(List<RecordingSchedule> list) {
        JSONArray arr = new JSONArray();
        try {
            for (RecordingSchedule s : list) arr.put(s.toJson());
        } catch (Exception ignored) {
        }
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    /** Remembers which schedule started the current recording, so its stop alarm is valid. */
    public void setActive(int id) {
        prefs.edit().putInt("active_id", id).apply();
    }

    public int getActive() {
        return prefs.getInt("active_id", -1);
    }

}
