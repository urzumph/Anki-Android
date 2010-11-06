/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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
import android.database.Cursor;
import android.util.Log;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * Anki fact. A fact is a single piece of information, made up of a number of fields. See
 * http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Facts
 */
public class Fact {

    // TODO: Javadoc.
    // TODO: Finish porting from facts.py.
    // TODO: Methods to read/write from/to DB.

    private long mId;
    private long mModelId;
    private double mCreated;
    private double mModified;
    private String mTags;
    private double mSpaceUntil;

    private Model mModel;
    private TreeSet<Field> mFields;
    private Deck mDeck;


    /*
     * public Fact(Deck deck, Model model) { this.deck = deck; this.model = model; this.id = Utils.genID(); if (model !=
     * null) { Iterator<FieldModel> iter = model.fieldModels.iterator(); while (iter.hasNext()) { this.fields.add(new
     * Field(iter.next())); } } }
     */

    // Generate fact object from its ID
    public Fact(Deck deck, long id) {
        mDeck = deck;
        fromDb(id);
        // TODO: load fields associated with this fact.
    }


    /**
     * @return the fields
     */
    public TreeSet<Field> getFields() {
        return mFields;
    }


    private boolean fromDb(long id) {
        mId = id;
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath());
        Cursor cursor = null;

        try {
            cursor = ankiDB.getDatabase().rawQuery("SELECT id, modelId, created, modified, tags, spaceUntil "
                    + "FROM facts " + "WHERE id = " + id, null);
            if (!cursor.moveToFirst()) {
                Log.w(AnkiDroidApp.TAG, "Fact.java (constructor): No result from query.");
                return false;
            }

            mId = cursor.getLong(0);
            mModelId = cursor.getLong(1);
            mCreated = cursor.getDouble(2);
            mModified = cursor.getDouble(3);
            mTags = cursor.getString(4);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        Cursor fieldsCursor = null;
        try {
            fieldsCursor = ankiDB.getDatabase().rawQuery("SELECT id, factId, fieldModelId, value " + "FROM fields "
                    + "WHERE factId = " + id, null);

            mFields = new TreeSet<Field>(new FieldOrdinalComparator());
            while (fieldsCursor.moveToNext()) {
                long fieldId = fieldsCursor.getLong(0);
                long fieldModelId = fieldsCursor.getLong(2);
                String fieldValue = fieldsCursor.getString(3);

                Cursor fieldModelCursor = null;
                FieldModel currentFieldModel = null;
                try {
                    // Get the field model for this field
                    fieldModelCursor = ankiDB.getDatabase().rawQuery("SELECT id, ordinal, modelId, name, description "
                            + "FROM fieldModels " + "WHERE id = " + fieldModelId, null);

                    fieldModelCursor.moveToFirst();
                    currentFieldModel = new FieldModel(fieldModelCursor.getLong(0), fieldModelCursor.getInt(1),
                            fieldModelCursor.getLong(2), fieldModelCursor.getString(3), fieldModelCursor.getString(4));
                } finally {
                    if (fieldModelCursor != null) {
                        fieldModelCursor.close();
                    }
                }
                mFields.add(new Field(fieldId, id, currentFieldModel, fieldValue));
            }
        } finally {
            if (fieldsCursor != null) {
                fieldsCursor.close();
            }
        }
        // Read Fields
        return true;
    }


    public String getFieldValue(String fieldModelName) {
        Iterator<Field> iter = mFields.iterator();
        while (iter.hasNext()) {
            Field f = iter.next();
            if (f.mFieldModel.getName().equals(fieldModelName)) {
                return f.mValue;
            }
        }
        return null;
    }


    public long getFieldModelId(String fieldModelName) {
        Iterator<Field> iter = mFields.iterator();
        while (iter.hasNext()) {
            Field f = iter.next();
            if (f.mFieldModel.getName().equals(fieldModelName)) {
                return f.mFieldModel.getId();
            }
        }
        return 0;
    }


    public void toDb() {
        double now = Utils.now();

        // update facts table
        ContentValues updateValues = new ContentValues();
        updateValues.put("modified", now);

        // update fields table
        Iterator<Field> iter = mFields.iterator();
        while (iter.hasNext()) {
            Field f = iter.next();

            updateValues = new ContentValues();
            updateValues.put("value", f.mValue);
            AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().update("fields", updateValues, "id = ?",
                    new String[] { "" + f.mFieldId });
        }
    }


    public LinkedList<Card> getUpdatedRelatedCards() {
        // TODO return instances of each card that is related to this fact
        LinkedList<Card> returnList = new LinkedList<Card>();

        Cursor cardsCursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                "SELECT id, factId FROM cards " + "WHERE factId = " + mId, null);

        while (cardsCursor.moveToNext()) {
            Card newCard = new Card(mDeck);
            newCard.fromDB(cardsCursor.getLong(0));
            newCard.loadTags();
            HashMap<String, String> newQA = CardModel.formatQA(this, newCard.getCardModel(), newCard.splitTags());
            newCard.setQuestion(newQA.get("question"));
            newCard.setAnswer(newQA.get("answer"));

            returnList.add(newCard);
        }
        if (cardsCursor != null) {
            cardsCursor.close();
        }

        return returnList;
    }

    public static final class FieldOrdinalComparator implements Comparator<Field> {
        public int compare(Field object1, Field object2) {
            return object1.mOrdinal - object2.mOrdinal;
        }
    }

    public class Field {

        // TODO: Javadoc.
        // Methods for reading/writing from/to DB.

        // BEGIN SQL table entries
        private long mFieldId; // Primary key id, but named fieldId to no hide Fact.id
        private long mFactId; // Foreign key facts.id
        private long mFieldModelId; // Foreign key fieldModel.id
        private int mOrdinal;
        private String mValue;
        // END SQL table entries

        // BEGIN JOINed entries
        private FieldModel mFieldModel;
        // END JOINed entries

        // Backward reference
        private Fact mFact;


        // for creating instances of existing fields
        // XXX Unused
        public Field(long id, long factId, FieldModel fieldModel, String value) {
            mFieldId = id;
            mFactId = factId;
            mFieldModel = fieldModel;
            mValue = value;
            mFieldModel = fieldModel;
            mOrdinal = fieldModel.getOrdinal();
        }


        // For creating new fields
        public Field(FieldModel fieldModel) {
            if (fieldModel != null) {
                mFieldModel = fieldModel;
                mOrdinal = fieldModel.getOrdinal();
            }
            mValue = "";
            mFieldId = Utils.genID();
        }


        /**
         * @param value the value to set
         */
        public void setValue(String value) {
            mValue = value;
        }


        /**
         * @return the value
         */
        public String getValue() {
            return mValue;
        }


        /**
         * @return the fieldModel
         */
        public FieldModel getFieldModel() {
            return mFieldModel;
        }
    }

}
