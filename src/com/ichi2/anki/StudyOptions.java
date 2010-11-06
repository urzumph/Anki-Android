/***************************************************************************************
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class StudyOptions extends Activity {
    /**
     * Default database.
     */
    public static final String OPT_DB = "com.ichi2.anki.deckFilename";

    /**
     * Filename of the sample deck to load
     */
    private static final String SAMPLE_DECK_NAME = "country-capitals.anki";

    /**
     * Menus
     */
    private static final int MENU_OPEN = 1;
    private static final int SUBMENU_DOWNLOAD = 2;
    private static final int MENU_DOWNLOAD_PERSONAL_DECK = 21;
    private static final int MENU_DOWNLOAD_SHARED_DECK = 22;
    private static final int MENU_SYNC = 3;
    private static final int MENU_MY_ACCOUNT = 4;
    private static final int MENU_PREFERENCES = 5;
    private static final int MENU_ADD_FACT = 6;
    private static final int MENU_ABOUT = 7;

    /**
     * Available options performed by other activities
     */
    private static final int PICK_DECK_REQUEST = 0;
    private static final int PREFERENCES_UPDATE = 1;
    private static final int REQUEST_REVIEW = 2;
    private static final int DOWNLOAD_PERSONAL_DECK = 3;
    private static final int DOWNLOAD_SHARED_DECK = 4;
    private static final int REPORT_ERROR = 5;

    /**
     * Constants for selecting which content view to display
     */
    private static final int CONTENT_NO_DECK = 0;
    private static final int CONTENT_STUDY_OPTIONS = 1;
    private static final int CONTENT_CONGRATS = 2;
    private static final int CONTENT_DECK_NOT_LOADED = 3;
    private static final int CONTENT_SESSION_COMPLETE = 4;
    public static final int CONTENT_NO_EXTERNAL_STORAGE = 5;

    /**
     * Download Manager Service stub
     */
    // private IDownloadManagerService mService = null;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private boolean sdCardAvailable = AnkiDroidApp.isSdCardMounted();

    /**
     * Preferences
     */
    private String prefDeckPath;
    private boolean prefStudyOptions;
    // private boolean deckSelected;
    private boolean inDeckPicker;
    private String deckFilename;

    private int mCurrentContentView;

    /**
     * Alerts to inform the user about different situations
     */
    private ProgressDialog mProgressDialog;
    private AlertDialog mNoConnectionAlert;
    private AlertDialog mUserNotLoggedInAlert;
    private AlertDialog mConnectionErrorAlert;

    /**
     * UI elements for "Study Options" view
     */
    private View mStudyOptionsView;
    private Button mButtonStart;
    private TextView mTextTitle;
    private TextView mTextDeckName;
    private TextView mTextReviewsDue;
    private TextView mTextNewToday;
    private TextView mTextNewTotal;
    private EditText mEditNewPerDay;
    private EditText mEditSessionTime;
    private EditText mEditSessionQuestions;

    /**
     * UI elements for "More Options" dialog
     */
    private AlertDialog mDialogMoreOptions;
    private Spinner mSpinnerNewCardOrder;
    private Spinner mSpinnerNewCardSchedule;
    private Spinner mSpinnerRevCardOrder;
    private Spinner mSpinnerFailCardOption;

    /**
     * UI elements for "No Deck" view
     */
    private View mNoDeckView;
    private TextView mTextNoDeckTitle;
    private TextView mTextNoDeckMessage;

    /**
     * UI elements for "Congrats" view
     */
    private View mCongratsView;
    private TextView mTextCongratsMessage;
    private Button mButtonCongratsLearnMore;
    private Button mButtonCongratsReviewEarly;
    private Button mButtonCongratsFinish;

    /**
     * UI elements for "No External Storage Available" view
     */
    private View mNoExternalStorageView;

    /**
     * Callbacks for UI events
     */
    private View.OnClickListener mButtonClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.studyoptions_start:
                    // finish();
                    Intent reviewer = new Intent(StudyOptions.this, Reviewer.class);
                    reviewer.putExtra("deckFilename", deckFilename);
                    startActivityForResult(reviewer, REQUEST_REVIEW);
                    return;
                case R.id.studyoptions_more:
                    showMoreOptionsDialog();
                    return;
                case R.id.studyoptions_load_sample_deck:
                    loadSampleDeck();
                    return;
                case R.id.studyoptions_load_other_deck:
                    openDeckPicker();
                    return;
                default:
                    return;
            }
        }
    };


    private Boolean isValidInt(String test) {
        try {
            Integer.parseInt(test);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    private Boolean isValidLong(String test) {
        try {
            Long.parseLong(test);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private View.OnFocusChangeListener mEditFocusListener = new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            Deck deck = AnkiDroidApp.deck();
            if (!hasFocus) {
                String inputText = ((EditText) v).getText().toString();

                switch (v.getId()) {
                    case R.id.studyoptions_new_cards_per_day:
                        if (isValidInt(inputText)) {
                            deck.setNewCardsPerDay(Integer.parseInt(inputText));
                            updateValuesFromDeck();
                        } else {
                            ((EditText) v).setText(Integer.toString(deck.getNewCardsPerDay()));
                        }
                        return;
                    case R.id.studyoptions_session_minutes:
                        if (isValidLong(inputText)) {
                            deck.setSessionTimeLimit(Long.parseLong(inputText) * 60);
                        } else {
                            ((EditText) v).setText(Long.toString(deck.getSessionTimeLimit() / 60));
                        }

                        return;
                    case R.id.studyoptions_session_questions:
                        if (isValidLong(inputText)) {
                            deck.setSessionRepLimit(Long.parseLong(inputText));
                        } else {
                            ((EditText) v).setText(Long.toString(deck.getSessionRepLimit()));
                        }
                        return;
                    default:
                        return;
                }
            }
        }
    };

    private DialogInterface.OnClickListener mDialogSaveListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            Deck deck = AnkiDroidApp.deck();
            deck.setNewCardOrder(mSpinnerNewCardOrder.getSelectedItemPosition());
            deck.setNewCardSpacing(mSpinnerNewCardSchedule.getSelectedItemPosition());
            deck.setRevCardOrder(mSpinnerRevCardOrder.getSelectedItemPosition());

            dialog.dismiss();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(AnkiDroidApp.TAG, "StudyOptions Activity");

        if (hasErrorFiles()) {
            Intent i = new Intent(this, ErrorReporter.class);
            startActivityForResult(i, REPORT_ERROR);
        }

        SharedPreferences preferences = restorePreferences();
        registerExternalStorageListener();

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        initAllContentViews();
        initAllDialogs();

        Intent intent = getIntent();
        if ("android.intent.action.VIEW".equalsIgnoreCase(intent.getAction()) && intent.getDataString() != null) {
            deckFilename = Uri.parse(intent.getDataString()).getPath();
            Log.i(AnkiDroidApp.TAG, "onCreate - deckFilename from intent: " + deckFilename);
        } else if (savedInstanceState != null) {
            // Use the same deck as last time Ankidroid was used.
            deckFilename = savedInstanceState.getString("deckFilename");
            Log.i(AnkiDroidApp.TAG, "onCreate - deckFilename from savedInstanceState: " + deckFilename);
        } else {
            Log.i(AnkiDroidApp.TAG, "onCreate - " + preferences.getAll().toString());
            deckFilename = preferences.getString("deckFilename", null);
            Log.i(AnkiDroidApp.TAG, "onCreate - deckFilename from preferences: " + deckFilename);
        }

        if (!sdCardAvailable) {
            showContentView(CONTENT_NO_EXTERNAL_STORAGE);
        } else {
            if (deckFilename == null || !new File(deckFilename).exists()) {
                showContentView(CONTENT_NO_DECK);
            } else {
                // Load previous deck.
                loadPreviousDeck();
            }
        }
    }


    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications. The intent will call
     * closeExternalStorageFiles() if the external media is going to be ejected, so applications can clean up any files
     * they have open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        Log.i(AnkiDroidApp.TAG, "mUnmountReceiver - Action = Media Eject");
                        closeOpenedDeck();
                        showContentView(CONTENT_NO_EXTERNAL_STORAGE);
                        sdCardAvailable = false;
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        Log.i(AnkiDroidApp.TAG, "mUnmountReceiver - Action = Media Mounted");
                        sdCardAvailable = true;
                        if (!inDeckPicker) {
                            loadPreviousDeck();
                        }
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(AnkiDroidApp.TAG, "StudyOptions - onDestroy()");
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    private void loadPreviousDeck() {
        Intent deckLoadIntent = new Intent();
        deckLoadIntent.putExtra(OPT_DB, deckFilename);
        onActivityResult(PICK_DECK_REQUEST, RESULT_OK, deckLoadIntent);
    }


    private void closeOpenedDeck() {
        if (AnkiDroidApp.deck() != null && sdCardAvailable) {
            AnkiDroidApp.deck().closeDeck();
            AnkiDroidApp.setDeck(null);
        }
    }


    private boolean hasErrorFiles() {
        for (String file : this.fileList()) {
            if (file.endsWith(".stacktrace")) {
                return true;
            }
        }

        return false;
    }


    private void initAllContentViews() {
        // The main study options view that will be used when there are reviews left.
        mStudyOptionsView = getLayoutInflater().inflate(R.layout.studyoptions, null);

        mTextTitle = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_title);
        mTextDeckName = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_deck_name);

        mButtonStart = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_start);
        mStudyOptionsView.findViewById(R.id.studyoptions_more).setOnClickListener(mButtonClickListener);

        mTextReviewsDue = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_reviews_due);
        mTextNewToday = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new_today);
        mTextNewTotal = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new_total);

        mEditNewPerDay = (EditText) mStudyOptionsView.findViewById(R.id.studyoptions_new_cards_per_day);
        mEditSessionTime = (EditText) mStudyOptionsView.findViewById(R.id.studyoptions_session_minutes);
        mEditSessionQuestions = (EditText) mStudyOptionsView.findViewById(R.id.studyoptions_session_questions);

        mButtonStart.setOnClickListener(mButtonClickListener);
        mEditNewPerDay.setOnFocusChangeListener(mEditFocusListener);
        mEditSessionTime.setOnFocusChangeListener(mEditFocusListener);
        mEditSessionQuestions.setOnFocusChangeListener(mEditFocusListener);

        mDialogMoreOptions = createDialog();

        // The view to use when there is no deck loaded yet.
        // TODO: Add and init view here.
        mNoDeckView = getLayoutInflater().inflate(R.layout.studyoptions_nodeck, null);

        mTextNoDeckTitle = (TextView) mNoDeckView.findViewById(R.id.studyoptions_nodeck_title);
        mTextNoDeckMessage = (TextView) mNoDeckView.findViewById(R.id.studyoptions_nodeck_message);

        mNoDeckView.findViewById(R.id.studyoptions_load_sample_deck).setOnClickListener(mButtonClickListener);
        mNoDeckView.findViewById(R.id.studyoptions_load_other_deck).setOnClickListener(mButtonClickListener);

        // The view that shows the congratulations view.
        mCongratsView = getLayoutInflater().inflate(R.layout.studyoptions_congrats, null);

        mTextCongratsMessage = (TextView) mCongratsView.findViewById(R.id.studyoptions_congrats_message);
        mButtonCongratsLearnMore = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_learnmore);
        mButtonCongratsReviewEarly = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_reviewearly);
        mButtonCongratsFinish = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_finish);

        // The view to use when there is no external storage available
        mNoExternalStorageView = getLayoutInflater().inflate(R.layout.studyoptions_nostorage, null);
    }


    /**
     * Create AlertDialogs used on all the activity
     */
    private void initAllDialogs() {
        Resources res = getResources();

        // Init alert dialogs
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(res.getString(R.string.connection_error_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.connection_needed));
        builder.setPositiveButton(res.getString(R.string.ok), null);
        mNoConnectionAlert = builder.create();

        builder.setTitle(res.getString(R.string.connection_error_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.no_user_password_error_message));
        builder.setPositiveButton(res.getString(R.string.log_in), new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                Intent myAccount = new Intent(StudyOptions.this, MyAccount.class);
                startActivity(myAccount);
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), null);
        mUserNotLoggedInAlert = builder.create();

        builder.setTitle(res.getString(R.string.connection_error_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.connection_error_message));
        builder.setPositiveButton(res.getString(R.string.retry), new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                syncDeck();
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), null);
        mConnectionErrorAlert = builder.create();
    }


    private AlertDialog createDialog() {
        // Custom view for the dialog content.
        View contentView = getLayoutInflater().inflate(R.layout.studyoptions_more_dialog_contents, null);
        mSpinnerNewCardOrder = (Spinner) contentView.findViewById(R.id.studyoptions_new_card_order);
        mSpinnerNewCardSchedule = (Spinner) contentView.findViewById(R.id.studyoptions_new_card_schedule);
        mSpinnerRevCardOrder = (Spinner) contentView.findViewById(R.id.studyoptions_rev_card_order);
        mSpinnerFailCardOption = (Spinner) contentView.findViewById(R.id.studyoptions_fail_card_option);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.studyoptions_more_dialog_title);
        builder.setPositiveButton(R.string.studyoptions_more_save, mDialogSaveListener);
        builder.setView(contentView);

        return builder.create();
    }


    private void showMoreOptionsDialog() {
        // Update spinner selections from deck prior to showing the dialog.
        Deck deck = AnkiDroidApp.deck();
        mSpinnerNewCardOrder.setSelection(deck.getNewCardOrder());
        mSpinnerNewCardSchedule.setSelection(deck.getNewCardSpacing());
        mSpinnerRevCardOrder.setSelection(deck.getRevCardOrder());
        mSpinnerFailCardOption.setVisibility(View.GONE); // TODO: Not implemented yet.

        mDialogMoreOptions.show();
    }


    private void showContentView(int which) {
        mCurrentContentView = which;
        showContentView();
    }


    private void showContentView() {

        switch (mCurrentContentView) {
            case CONTENT_NO_DECK:
                setTitle(R.string.app_name);
                mTextNoDeckTitle.setText(R.string.studyoptions_nodeck_title);
                mTextNoDeckMessage.setText(String.format(
                        getResources().getString(R.string.studyoptions_nodeck_message), prefDeckPath));
                setContentView(mNoDeckView);
                break;
            case CONTENT_DECK_NOT_LOADED:
                setTitle(R.string.app_name);
                mTextNoDeckTitle.setText(R.string.studyoptions_deck_not_loaded_title);
                mTextNoDeckMessage.setText(R.string.studyoptions_deck_not_loaded_message);
                setContentView(mNoDeckView);
                break;
            case CONTENT_STUDY_OPTIONS:
                updateValuesFromDeck();
                mButtonStart.setText(R.string.studyoptions_start);
                mTextTitle.setText(R.string.studyoptions_title);
                setContentView(mStudyOptionsView);
                break;
            case CONTENT_SESSION_COMPLETE:
                updateValuesFromDeck();
                mButtonStart.setText(R.string.studyoptions_continue);
                mTextTitle.setText(R.string.studyoptions_well_done);
                setContentView(mStudyOptionsView);
                break;
            case CONTENT_CONGRATS:
                updateValuesFromDeck();
                setContentView(mCongratsView);
                break;
            case CONTENT_NO_EXTERNAL_STORAGE:
                setTitle(R.string.app_name);
                setContentView(mNoExternalStorageView);
                break;
        }
    }


    private void updateValuesFromDeck() {
        Deck deck = AnkiDroidApp.deck();
        DeckTask.waitToFinish();
        if (deck != null) {
            deck.checkDue();
            int reviewCount = deck.getDueCount();
            String unformattedTitle = getResources().getString(R.string.studyoptions_window_title);
            setTitle(String.format(unformattedTitle, deck.getDeckName(), reviewCount, deck.getCardCount()));

            mTextDeckName.setText(deck.getDeckName());
            mTextReviewsDue.setText(String.valueOf(reviewCount));
            mTextNewToday.setText(String.valueOf(deck.getNewCountToday()));
            mTextNewTotal.setText(String.valueOf(deck.getNewCount()));

            mEditNewPerDay.setText(String.valueOf(deck.getNewCardsPerDay()));
            mEditSessionTime.setText(String.valueOf(deck.getSessionTimeLimit() / 60));
            mEditSessionQuestions.setText(String.valueOf(deck.getSessionRepLimit()));
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.i(AnkiDroidApp.TAG, "onSaveInstanceState: " + deckFilename);
        // Remember current deck's filename.
        if (deckFilename != null) {
            outState.putString("deckFilename", deckFilename);
        }
        Log.i(AnkiDroidApp.TAG, "onSaveInstanceState - Ending");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.add(Menu.NONE, MENU_OPEN, Menu.NONE, R.string.menu_open_deck);
        item.setIcon(R.drawable.ic_menu_manage);
        SubMenu downloadDeckSubMenu = menu.addSubMenu(Menu.NONE, SUBMENU_DOWNLOAD, Menu.NONE,
                R.string.menu_download_deck);
        downloadDeckSubMenu.setIcon(R.drawable.ic_menu_download);
        downloadDeckSubMenu
                .add(Menu.NONE, MENU_DOWNLOAD_PERSONAL_DECK, Menu.NONE, R.string.menu_download_personal_deck);
        downloadDeckSubMenu.add(Menu.NONE, MENU_DOWNLOAD_SHARED_DECK, Menu.NONE, R.string.menu_download_shared_deck);
        item = menu.add(Menu.NONE, MENU_SYNC, Menu.NONE, R.string.menu_sync);
        item.setIcon(R.drawable.ic_menu_refresh);
        item = menu.add(Menu.NONE, MENU_MY_ACCOUNT, Menu.NONE, R.string.menu_my_account);
        item.setIcon(R.drawable.ic_menu_home);
        item = menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE, R.string.menu_preferences);
        item.setIcon(R.drawable.ic_menu_preferences);
        item.setIcon(R.drawable.ic_menu_archive);
        item = menu.add(Menu.NONE, MENU_ADD_FACT, Menu.NONE, R.string.menu_add_card);
        item.setIcon(R.drawable.ic_input_add);
        item = menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.menu_about);
        item.setIcon(R.drawable.ic_menu_info_details);

        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean deckLoaded = AnkiDroidApp.deck() != null;
        menu.findItem(MENU_OPEN).setEnabled(sdCardAvailable);
        menu.findItem(SUBMENU_DOWNLOAD).setEnabled(sdCardAvailable);
        // menu.findItem(MENU_DECK_PROPERTIES).setEnabled(deckLoaded && sdCardAvailable);
        menu.findItem(MENU_SYNC).setEnabled(deckLoaded && sdCardAvailable);
        return true;
    }


    /** Handles item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(AnkiDroidApp.TAG, "Item = " + item.getItemId());
        switch (item.getItemId()) {
            case MENU_OPEN:
                openDeckPicker();
                return true;

            case MENU_DOWNLOAD_PERSONAL_DECK:
                if (AnkiDroidApp.isUserLoggedIn()) {
                    startActivityForResult(
                            new Intent(StudyOptions.this, PersonalDeckPicker.class), DOWNLOAD_PERSONAL_DECK);
                } else {
                    mUserNotLoggedInAlert.show();
                }
                return true;

            case MENU_DOWNLOAD_SHARED_DECK:
                startActivityForResult(
                        new Intent(StudyOptions.this, SharedDeckPicker.class), DOWNLOAD_SHARED_DECK);
                return true;

            case MENU_SYNC:
                syncDeck();
                return true;

            case MENU_MY_ACCOUNT:
                startActivity(new Intent(StudyOptions.this, MyAccount.class));
                return true;

            case MENU_PREFERENCES:
                startActivityForResult(
                        new Intent(StudyOptions.this, Preferences.class),
                        PREFERENCES_UPDATE);
                return true;

            case MENU_ADD_FACT:
                startActivity(new Intent(StudyOptions.this, FactAdder.class));
                return true;

            case MENU_ABOUT:
                startActivity(new Intent(StudyOptions.this, About.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void openDeckPicker() {
        closeOpenedDeck();
        // deckLoaded = false;
        Intent decksPicker = new Intent(StudyOptions.this, DeckPicker.class);
        inDeckPicker = true;
        startActivityForResult(decksPicker, PICK_DECK_REQUEST);
        // Log.i(AnkiDroidApp.TAG, "openDeckPicker - Ending");
    }


    public void openSharedDeckPicker() {
        if (AnkiDroidApp.deck() != null)// && sdCardAvailable)
        {
            AnkiDroidApp.deck().closeDeck();
            AnkiDroidApp.setDeck(null);
        }
        // deckLoaded = false;
        Intent intent = new Intent(StudyOptions.this, SharedDeckPicker.class);
        startActivityForResult(intent, DOWNLOAD_SHARED_DECK);
    }


    private void loadSampleDeck() {
        File sampleDeckFile = new File(prefDeckPath, SAMPLE_DECK_NAME);

        if (!sampleDeckFile.exists()) {
            // Create the deck.
            try {
                // Copy the sample deck from the assets to the SD card.
                InputStream stream = getResources().getAssets().open(SAMPLE_DECK_NAME);
                boolean written = Utils.writeToFile(stream, sampleDeckFile.getAbsolutePath());
                stream.close();
                if (!written) {
                    openDeckPicker();
                    Log.i(AnkiDroidApp.TAG, "onCreate - The copy of country-capitals.anki to the sd card failed.");
                    return;
                }
                Log.i(AnkiDroidApp.TAG, "onCreate - The copy of country-capitals.anki to the sd card was sucessful.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Intent deckLoadIntent = new Intent();
        deckLoadIntent.putExtra(OPT_DB, sampleDeckFile.getAbsolutePath());
        onActivityResult(PICK_DECK_REQUEST, RESULT_OK, deckLoadIntent);
    }


    private void syncDeck() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());

        String username = preferences.getString("username", "");
        String password = preferences.getString("password", "");

        if (AnkiDroidApp.isUserLoggedIn()) {
            Deck deck = AnkiDroidApp.deck();

            Log.i(AnkiDroidApp.TAG,
                    "Synchronizing deck " + deckFilename + " with username " + username + " and password " + password);
            Connection.syncDeck(syncListener, new Connection.Payload(new Object[] { username, password, deck,
                    deckFilename }));
        } else {
            mUserNotLoggedInAlert.show();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == CONTENT_NO_EXTERNAL_STORAGE) {
            showContentView(CONTENT_NO_EXTERNAL_STORAGE);
        } else if (requestCode == PICK_DECK_REQUEST || requestCode == DOWNLOAD_PERSONAL_DECK
                || requestCode == DOWNLOAD_SHARED_DECK) {
            // Clean the previous card before showing the first of the new loaded deck (so the transition is not so
            // abrupt)
            // updateCard("");
            // hideSdError();
            // hideDeckErrors();
            inDeckPicker = false;

            if (resultCode != RESULT_OK) {
                Log.e(AnkiDroidApp.TAG, "onActivityResult - Deck browser returned with error");
                // Make sure we open the database again in onResume() if user pressed "back"
                // deckSelected = false;
                displayProgressDialogAndLoadDeck();
                return;
            }
            if (intent == null) {
                Log.e(AnkiDroidApp.TAG, "onActivityResult - Deck browser returned null intent");
                // Make sure we open the database again in onResume()
                // deckSelected = false;
                displayProgressDialogAndLoadDeck();
                return;
            }
            // A deck was picked. Save it in preferences and use it.
            Log.i(AnkiDroidApp.TAG, "onActivityResult = OK");
            deckFilename = intent.getExtras().getString(OPT_DB);
            savePreferences();

            // Log.i(AnkiDroidApp.TAG, "onActivityResult - deckSelected = " + deckSelected);
            boolean updateAllCards = (requestCode == DOWNLOAD_SHARED_DECK);
            displayProgressDialogAndLoadDeck(updateAllCards);

        } else if (requestCode == PREFERENCES_UPDATE) {
            restorePreferences();
            showContentView();
            // If there is no deck loaded the controls have not to be shown
            // if(deckLoaded && cardsToReview)
            // {
            // showOrHideControls();
            // showOrHideAnswerField();
            // }
        } else if (requestCode == REQUEST_REVIEW) {
            Log.i(AnkiDroidApp.TAG, "Result code = " + resultCode);
            switch (resultCode) {
                case Reviewer.RESULT_SESSION_COMPLETED:
                    showContentView(CONTENT_SESSION_COMPLETE);
                    break;
                case Reviewer.RESULT_NO_MORE_CARDS:
                    showContentView(CONTENT_CONGRATS);
                    break;
                default:
                    showContentView(CONTENT_STUDY_OPTIONS);
                    break;
            }
        }
    }


    private void savePreferences() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        Editor editor = preferences.edit();
        editor.putString("deckFilename", deckFilename);
        editor.commit();
    }


    private SharedPreferences restorePreferences() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        prefDeckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory());
        prefStudyOptions = preferences.getBoolean("study_options", true);

        return preferences;
    }


    private void displayProgressDialogAndLoadDeck() {
        displayProgressDialogAndLoadDeck(false);
    }


    private void displayProgressDialogAndLoadDeck(boolean updateAllCards) {
        Log.i(AnkiDroidApp.TAG, "displayProgressDialogAndLoadDeck - Loading deck " + deckFilename);

        // Don't open database again in onResume() until we know for sure this attempt to load the deck is finished
        // deckSelected = true;

        // if(isSdCardMounted())
        // {
        if (deckFilename != null && new File(deckFilename).exists()) {
            // showControls(false);

            if (updateAllCards) {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK_AND_UPDATE_CARDS, mLoadDeckHandler,
                        new DeckTask.TaskData(deckFilename));
            } else {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK, mLoadDeckHandler, new DeckTask.TaskData(
                        deckFilename));
            }
        } else {
            if (deckFilename == null) {
                Log.i(AnkiDroidApp.TAG, "displayProgressDialogAndLoadDeck - SD card unmounted.");
            } else if (!new File(deckFilename).exists()) {
                Log.i(AnkiDroidApp.TAG, "displayProgressDialogAndLoadDeck - The deck " + deckFilename + " does not exist.");
            }

            // Show message informing that no deck has been loaded
            // displayDeckNotLoaded();
        }
        // } else
        // {
        // Log.i(AnkiDroidApp.TAG, "displayProgressDialogAndLoadDeck - SD card unmounted.");
        // deckSelected = false;
        // Log.i(AnkiDroidApp.TAG, "displayProgressDialogAndLoadDeck - deckSelected = " + deckSelected);
        // displaySdError();
        // }
    }

    DeckTask.TaskListener mLoadDeckHandler = new DeckTask.TaskListener() {

        public void onPreExecute() {
            // if(updateDialog == null || !updateDialog.isShowing())
            // {
            mProgressDialog = ProgressDialog.show(StudyOptions.this, "", getResources()
                    .getString(R.string.loading_deck), true);
            // }
        }


        public void onPostExecute(DeckTask.TaskData result) {

            // Close the previously opened deck.
            // if (AnkidroidApp.deck() != null)
            // {
            // AnkidroidApp.deck().closeDeck();
            // AnkidroidApp.setDeck(null);
            // }

            switch (result.getInt()) {
                case DeckTask.DECK_LOADED:
                    // Set the deck in the application instance, so other activities
                    // can access the loaded deck.
                    AnkiDroidApp.setDeck(result.getDeck());
                    if (prefStudyOptions) {
                        showContentView(CONTENT_STUDY_OPTIONS);
                    } else {
                        startActivityForResult(new Intent(StudyOptions.this, Reviewer.class), REQUEST_REVIEW);
                    }

                    break;

                case DeckTask.DECK_NOT_LOADED:
                    showContentView(CONTENT_DECK_NOT_LOADED);
                    break;

                case DeckTask.DECK_EMPTY:
                    // displayNoCardsInDeck();
                    break;
            }

            // This verification would not be necessary if onConfigurationChanged it's executed correctly (which seems
            // that emulator does not do)
            if (mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                } catch (Exception e) {
                    Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                }
            }
        }


        public void onProgressUpdate(DeckTask.TaskData... values) {
            // Pass
        }
    };

    Connection.TaskListener syncListener = new Connection.TaskListener() {

        public void onDisconnected() {
            if (mNoConnectionAlert != null) {
                mNoConnectionAlert.show();
            }
        }


        public void onPostExecute(Payload data) {
            Log.i(AnkiDroidApp.TAG, "onPostExecute");
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            if (data.success) {
                // closeDeck();
                // if(AnkiDroidApp.deck() != null )//&& sdCardAvailable)
                // {
                // AnkiDroidApp.deck().closeDeck();
                AnkiDroidApp.setDeck(null);
                // }

                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK, mLoadDeckHandler, new DeckTask.TaskData(
                        deckFilename));
            } else {
                // connectionFailedAlert.show();
                if (mConnectionErrorAlert != null) {
                    mConnectionErrorAlert.show();
                }
            }

        }


        public void onPreExecute() {
            // Pass
        }


        public void onProgressUpdate(Object... values) {
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = ProgressDialog.show(StudyOptions.this, (String) values[0], (String) values[1]);
            } else {
                mProgressDialog.setTitle((String) values[0]);
                mProgressDialog.setMessage((String) values[1]);
            }
        }

    };
}
