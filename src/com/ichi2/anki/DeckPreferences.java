/****************************************************************************************
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Preferences for the current deck.
 */
public class DeckPreferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    public class DeckPreferenceHack implements SharedPreferences {

        private Map<String, String> mValues = new HashMap<String, String>();


        public DeckPreferenceHack() {
            this.cacheValues();
        }


        protected void cacheValues() {
            Log.i(AnkiDroidApp.TAG, "DeckPreferences - CacheValues");
            mValues.put("newCardsPDay", String.valueOf(AnkiDroidApp.deck().getNewCardsPerDay()));
            mValues.put("sessionQLimit", String.valueOf(AnkiDroidApp.deck().getSessionRepLimit()));
            mValues.put("sessionTLimit", String.valueOf(AnkiDroidApp.deck().getSessionTimeLimit() / 60));
            mValues.put("newCardOrder", String.valueOf(AnkiDroidApp.deck().getNewCardOrder()));
            mValues.put("newCardSpacing", String.valueOf(AnkiDroidApp.deck().getNewCardSpacing()));
            mValues.put("revCardOrder", String.valueOf(AnkiDroidApp.deck().getRevCardOrder()));
        }

        public class Editor implements SharedPreferences.Editor {

            private ContentValues mUpdate = new ContentValues();


            public SharedPreferences.Editor clear() {
                Log.d(AnkiDroidApp.TAG, "clear()");
                mUpdate = new ContentValues();
                return this;
            }


            public boolean commit() {
                Log.d(AnkiDroidApp.TAG, "DeckPreferences - commit() changes back to database");

                // make sure we refresh the parent cached values
                // cacheValues();

                for (Entry<String, Object> entry : mUpdate.valueSet()) {
                    if (entry.getKey().equals("newCardsPDay")) {
                        AnkiDroidApp.deck().setNewCardsPerDay(Integer.parseInt(entry.getValue().toString()));
                    } else if (entry.getKey().equals("sessionQLimit")) {
                        AnkiDroidApp.deck().setSessionRepLimit(Long.parseLong(entry.getValue().toString()));
                    } else if (entry.getKey().equals("sessionTLimit")) {
                        AnkiDroidApp.deck().setSessionTimeLimit(60 * Long.parseLong(entry.getValue().toString()));
                    } else if (entry.getKey().equals("newCardOrder")) {
                        AnkiDroidApp.deck().setNewCardOrder(Integer.parseInt(entry.getValue().toString()));
                    } else if (entry.getKey().equals("newCardSpacing")) {
                        AnkiDroidApp.deck().setNewCardSpacing(Integer.parseInt(entry.getValue().toString()));
                    } else if (entry.getKey().equals("revCardOrder")) {
                        AnkiDroidApp.deck().setRevCardOrder(Integer.parseInt(entry.getValue().toString()));
                    }
                }
                // make sure we refresh the parent cached values
                cacheValues();

                // and update any listeners
                for (OnSharedPreferenceChangeListener listener : listeners) {
                    listener.onSharedPreferenceChanged(DeckPreferenceHack.this, null);
                }

                return true;
            }


            public SharedPreferences.Editor putBoolean(String key, boolean value) {
                return this.putString(key, Boolean.toString(value));
            }


            public SharedPreferences.Editor putFloat(String key, float value) {
                return this.putString(key, Float.toString(value));
            }


            public SharedPreferences.Editor putInt(String key, int value) {
                return this.putString(key, Integer.toString(value));
            }


            public SharedPreferences.Editor putLong(String key, long value) {
                return this.putString(key, Long.toString(value));
            }


            public SharedPreferences.Editor putString(String key, String value) {
                Log.d(this.getClass().toString(), String.format("Editor.putString(key=%s, value=%s)", key, value));
                mUpdate.put(key, value);
                return this;
            }


            public SharedPreferences.Editor remove(String key) {
                Log.d(this.getClass().toString(), String.format("Editor.remove(key=%s)", key));
                mUpdate.remove(key);
                return this;
            }

        }


        public boolean contains(String key) {
            return mValues.containsKey(key);
        }


        public Editor edit() {
            return new Editor();
        }


        public Map<String, ?> getAll() {
            return mValues;
        }


        public boolean getBoolean(String key, boolean defValue) {
            return Boolean.valueOf(this.getString(key, Boolean.toString(defValue)));
        }


        public float getFloat(String key, float defValue) {
            return Float.valueOf(this.getString(key, Float.toString(defValue)));
        }


        public int getInt(String key, int defValue) {
            return Integer.valueOf(this.getString(key, Integer.toString(defValue)));
        }


        public long getLong(String key, long defValue) {
            return Long.valueOf(this.getString(key, Long.toString(defValue)));
        }


        public String getString(String key, String defValue) {
            Log.d(this.getClass().toString(), String.format("getString(key=%s, defValue=%s)", key, defValue));

            if (!mValues.containsKey(key)) {
                return defValue;
            }
            return mValues.get(key);
        }

        public List<OnSharedPreferenceChangeListener> listeners = new LinkedList<OnSharedPreferenceChangeListener>();


        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            listeners.add(listener);
        }


        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            listeners.remove(listener);
        }

    }

    private DeckPreferenceHack mPref;


    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        Log.d(this.getClass().toString(), String.format("getSharedPreferences(name=%s)", name));
        return mPref;
    }


    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (AnkiDroidApp.deck() == null) {
            Log.i(AnkiDroidApp.TAG, "DeckPreferences - Selected Deck is NULL");
            finish();
        } else {
            // requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

            mPref = new DeckPreferenceHack();
            mPref.registerOnSharedPreferenceChangeListener(this);

            this.addPreferencesFromResource(R.layout.deck_preferences);
            // this.updateSummaries();
        }
    }


    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // update values on changed preference
        // this.updateSummaries();
    }    
    
    protected void updateSummaries() {
        // for all text preferences, set summary as current database value
        for (String key : mPref.mValues.keySet()) {
            Preference pref = this.findPreference(key);
            if (pref == null) {
                continue;
            }
            if (pref instanceof CheckBoxPreference) {
                continue;
            }
            pref.setSummary(this.mPref.getString(key, ""));
        }
    }
}
