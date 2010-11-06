/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Rick Gruber-Riemer <rick@vanosten.net>                            *
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

import android.database.Cursor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Card model. Card models are used to make question/answer pairs for the information you add to facts. You can display
 * any number of fields on the question side and answer side.
 *
 * @see http://ichi2.net/anki/wiki/ModelProperties#Card_Templates
 */
public class CardModel implements Comparator<CardModel> {

    // TODO: Javadoc.
    // TODO: Methods for reading/writing from/to DB.

    public static final int DEFAULT_FONT_SIZE = 20;
    public static final int DEFAULT_FONT_SIZE_RATIO = 100;
    public static final String DEFAULT_FONT_FAMILY = "Arial";
    public static final String DEFAULT_FONT_COLOR = "#000000";
    public static final String DEFAULT_BACKGROUND_COLOR = "#FFFFFF";

    /** Regex pattern used in removing tags from text before diff */
    private static final Pattern sFactPattern = Pattern.compile("%\\([tT]ags\\)s");
    private static final Pattern sModelPattern = Pattern.compile("%\\(modelTags\\)s");
    private static final Pattern sTemplPattern = Pattern.compile("%\\(cardModel\\)s");

    // BEGIN SQL table columns
    private long mId; // Primary key
    private int mOrdinal;
    private long mModelId; // Foreign key models.id
    private String mName;
    private String mDescription = "";
    private int mActive = 1;
    // Formats: question/answer/last (not used)
    private String mQformat;
    private String mAformat;
    private String mLformat;
    // Question/answer editor format (not used yet)
    private String mQedformat;
    private String mAedformat;
    private int mQuestionInAnswer = 0;
    // Display
    private String mQuestionFontFamily = DEFAULT_FONT_FAMILY;
    private int mQuestionFontSize = DEFAULT_FONT_SIZE;
    private String mQuestionFontColour = DEFAULT_FONT_COLOR;
    private int mQuestionAlign = 0;
    private String mAnswerFontFamily = DEFAULT_FONT_FAMILY;
    private int mAnswerFontSize = DEFAULT_FONT_SIZE;
    private String mAnswerFontColour = DEFAULT_FONT_COLOR;
    private int mAnswerAlign = 0;
    // Not used
    private String mLastFontFamily = DEFAULT_FONT_FAMILY;
    private int mLastFontSize = DEFAULT_FONT_SIZE;
    // Used as background colour
    private String mLastFontColour = DEFAULT_BACKGROUND_COLOR;
    private String mEditQuestionFontFamily = "";
    private int mEditQuestionFontSize = 0;
    private String mEditAnswerFontFamily = "";
    private int mEditAnswerFontSize = 0;
    // Empty answer
    private int mAllowEmptyAnswer = 1;
    private String mTypeAnswer = "";
    // END SQL table entries

    /**
     * Backward reference
     */
    private Model mModel;


    /**
     * Constructor.
     */
    public CardModel(String name, String qformat, String aformat, boolean active) {
        mName = name;
        mQformat = qformat;
        mAformat = aformat;
        mActive = active ? 1 : 0;
        mId = Utils.genID();
    }


    /**
     * Constructor.
     */
    public CardModel() {
        this("", "q", "a", true);
    }

    /** SELECT string with only those fields, which are used in AnkiDroid */
    private static final String SELECT_STRING = "SELECT id, ordinal, modelId, name, description, active, qformat, "
            + "aformat, questionInAnswer, questionFontFamily, questionFontSize, questionFontColour, questionAlign, "
            + "answerFontFamily, answerFontSize, answerFontColour, answerAlign, lastFontColour"
            + " FROM cardModels";


    /**
     * @param modelId
     * @param models will be changed by adding all found CardModels into it
     * @return unordered CardModels which are related to a given Model and eventually active put into the parameter
     *         "models"
     */
    protected static final void fromDb(Deck deck, long modelId, TreeMap<Long, CardModel> models) {
        Cursor cursor = null;
        CardModel myCardModel = null;
        try {
            StringBuffer query = new StringBuffer(SELECT_STRING);
            query.append(" WHERE modelId = ");
            query.append(modelId);

            cursor = AnkiDatabaseManager.getDatabase(deck.getDeckPath()).getDatabase().rawQuery(query.toString(), null);

            if (cursor.moveToFirst()) {
                do {
                    myCardModel = new CardModel();

                    myCardModel.mId = cursor.getLong(0);
                    myCardModel.mOrdinal = cursor.getInt(1);
                    myCardModel.mModelId = cursor.getLong(2);
                    myCardModel.mName = cursor.getString(3);
                    myCardModel.mDescription = cursor.getString(4);
                    myCardModel.mActive = cursor.getInt(5);
                    myCardModel.mQformat = cursor.getString(6);
                    myCardModel.mAformat = cursor.getString(7);
                    myCardModel.mQuestionInAnswer = cursor.getInt(8);
                    myCardModel.mQuestionFontFamily = cursor.getString(9);
                    myCardModel.mQuestionFontSize = cursor.getInt(10);
                    myCardModel.mQuestionFontColour = cursor.getString(11);
                    myCardModel.mQuestionAlign = cursor.getInt(12);
                    myCardModel.mAnswerFontFamily = cursor.getString(13);
                    myCardModel.mAnswerFontSize = cursor.getInt(14);
                    myCardModel.mAnswerFontColour = cursor.getString(15);
                    myCardModel.mAnswerAlign = cursor.getInt(16);
                    myCardModel.mLastFontColour = cursor.getString(17);
                    models.put(myCardModel.mId, myCardModel);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }


    /**
     * @param cardModelId
     * @return the modelId for a given cardModel or 0, if it cannot be found
     */
    protected static final long modelIdFromDB(Deck deck, long cardModelId) {
        Cursor cursor = null;
        long modelId = -1;
        try {
            String query = "SELECT modelId FROM cardModels WHERE id = " + cardModelId;
            cursor = AnkiDatabaseManager.getDatabase(deck.getDeckPath()).getDatabase().rawQuery(query, null);
            cursor.moveToFirst();
            modelId = cursor.getLong(0);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return modelId;
    }


    // XXX Unused
//    /**
//     * Return a copy of this object.
//     */
//    public CardModel copy() {
//        CardModel cardModel = new CardModel(mName, mQformat, mAformat, (mActive == 1) ? true : false);
//        cardModel.mOrdinal = mOrdinal;
//        cardModel.mModelId = mModelId;
//        cardModel.mDescription = mDescription;
//        cardModel.mLformat = mLformat;
//        cardModel.mQedformat = mQedformat;
//        cardModel.mAedformat = mAedformat;
//        cardModel.mQuestionInAnswer = mQuestionInAnswer;
//        cardModel.mQuestionFontFamily = mQuestionFontFamily;
//        cardModel.mQuestionFontSize = mQuestionFontSize;
//        cardModel.mQuestionFontColour = mQuestionFontColour;
//        cardModel.mQuestionAlign = mQuestionAlign;
//        cardModel.mAnswerFontFamily = mAnswerFontFamily;
//        cardModel.mAnswerFontSize = mAnswerFontSize;
//        cardModel.mAnswerFontColour = mAnswerFontColour;
//        cardModel.mAnswerAlign = mAnswerAlign;
//        cardModel.mLastFontFamily = mLastFontFamily;
//        cardModel.mLastFontSize = mLastFontSize;
//        cardModel.mLastFontColour = mLastFontColour;
//        cardModel.mEditQuestionFontFamily = mEditQuestionFontFamily;
//        cardModel.mEditQuestionFontSize = mEditQuestionFontSize;
//        cardModel.mEditAnswerFontFamily = mEditAnswerFontFamily;
//        cardModel.mEditAnswerFontSize = mEditAnswerFontSize;
//        cardModel.mAllowEmptyAnswer = mAllowEmptyAnswer;
//        cardModel.mTypeAnswer = mTypeAnswer;
//        cardModel.mModel = null;
//
//        return cardModel;
//    }


    public static HashMap<String, String> formatQA(Fact fact, CardModel cm, String[] tags) {

        // Not pretty, I know.
        String question = cm.mQformat;
        String answer = cm.mAformat;

        // First deal with the tag fields:
        // %(tags)s = factTags tags where src = 0
        // %(modelTags)s = modelTags tags where src = 1
        // %(cardModel)s = templateTags tags where src = 2
        Matcher tagMatcher;
        // fact tags %(tags)s or %(Tags)s
        tagMatcher = sFactPattern.matcher(question);
        question = tagMatcher.replaceAll(tags[Card.TAGS_FACT]);
        tagMatcher = sFactPattern.matcher(answer);
        answer = tagMatcher.replaceAll(tags[Card.TAGS_FACT]);
        // modelTags %(modelTags)s
        tagMatcher = sModelPattern.matcher(question);
        question = tagMatcher.replaceAll(tags[Card.TAGS_MODEL]);
        tagMatcher = sModelPattern.matcher(answer);
        answer = tagMatcher.replaceAll(tags[Card.TAGS_MODEL]);
        // templateTags %(cardModel)s
        tagMatcher = sTemplPattern.matcher(question);
        question = tagMatcher.replaceAll(tags[Card.TAGS_TEMPL]);
        tagMatcher = sTemplPattern.matcher(answer);
        answer = tagMatcher.replaceAll(tags[Card.TAGS_TEMPL]);

        int replaceAt = question.indexOf("%(");
        while (replaceAt != -1) {
            question = replaceField(question, fact, replaceAt, true);
            replaceAt = question.indexOf("%(");
        }

        replaceAt = answer.indexOf("%(");
        while (replaceAt != -1) {
            answer = replaceField(answer, fact, replaceAt, true);
            replaceAt = answer.indexOf("%(");
        }

        HashMap<String, String> returnMap = new HashMap<String, String>();
        returnMap.put("question", question);
        returnMap.put("answer", answer);

        return returnMap;
    }


    private static String replaceField(String replaceFrom, Fact fact, int replaceAt, boolean isQuestion) {
        int endIndex = replaceFrom.indexOf(")", replaceAt);
        String fieldName = replaceFrom.substring(replaceAt + 2, endIndex);
        char fieldType = replaceFrom.charAt(endIndex + 1);
        if (isQuestion) {
            String replace = "%(" + fieldName + ")" + fieldType;
            String with = "<span class=\"fm" + Long.toHexString(fact.getFieldModelId(fieldName)) + "\">"
                    + fact.getFieldValue(fieldName) + "</span>";
            replaceFrom = replaceFrom.replace(replace, with);
        } else {
            replaceFrom.replace(
                    "%(" + fieldName + ")" + fieldType,
                    "<span class=\"fma" + Long.toHexString(fact.getFieldModelId(fieldName)) + "\">"
                            + fact.getFieldValue(fieldName) + "</span");
        }
        return replaceFrom;
    }


    /**
     * Implements Comparator by comparing the field "ordinal".
     * @param object1
     * @param object2
     * @return
     */
    public int compare(CardModel object1, CardModel object2) {
        return object1.mOrdinal - object2.mOrdinal;
    }


    /**
     * @return the id
     */
    public long getId() {
        return mId;
    }


    /**
     * @return the ordinal
     */
    public int getOrdinal() {
        return mOrdinal;
    }


    /**
     * @return the questionInAnswer
     */
    public int getQuestionInAnswer() {
        return mQuestionInAnswer;
    }


    /**
     * @return the lastFontColour
     */
    public String getLastFontColour() {
        return mLastFontColour;
    }


    /**
     * @return the questionFontFamily
     */
    public String getQuestionFontFamily() {
        return mQuestionFontFamily;
    }


    /**
     * @return the questionFontSize
     */
    public int getQuestionFontSize() {
        return mQuestionFontSize;
    }


    /**
     * @return the questionFontColour
     */
    public String getQuestionFontColour() {
        return mQuestionFontColour;
    }


    /**
     * @return the questionAlign
     */
    public int getQuestionAlign() {
        return mQuestionAlign;
    }

    /**
     * @return the answerFontFamily
     */
    public String getAnswerFontFamily() {
        return mAnswerFontFamily;
    }


    /**
     * @return the answerFontSize
     */
    public int getAnswerFontSize() {
        return mAnswerFontSize;
    }


    /**
     * @return the answerFontColour
     */
    public String getAnswerFontColour() {
        return mAnswerFontColour;
    }


    /**
     * @return the answerAlign
     */
    public int getAnswerAlign() {
        return mAnswerAlign;
    }
}
