package com.aricneto.twistytimer.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;

import com.aricneto.twistytimer.adapter.BottomSheetSpinnerAdapter;
import com.aricneto.twistytimer.fragment.dialog.BottomSheetCategoryDialog;
import com.aricneto.twistytimer.fragment.dialog.BottomSheetSpinnerDialog;
import com.aricneto.twistytimer.listener.DialogListenerMessage;
import com.google.android.material.tabs.TabLayout;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.core.content.ContextCompat;
import androidx.loader.content.Loader;
import androidx.viewpager.widget.ViewPager;

import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.aricneto.twistify.R;
import com.aricneto.twistytimer.TwistyTimer;
import com.aricneto.twistytimer.activity.MainActivity;
import com.aricneto.twistytimer.layout.LockedViewPager;
import com.aricneto.twistytimer.listener.OnBackPressedInFragmentListener;
import com.aricneto.twistytimer.stats.Statistics;
import com.aricneto.twistytimer.stats.StatisticsCache;
import com.aricneto.twistytimer.stats.StatisticsLoader;
import com.aricneto.twistytimer.utils.PuzzleUtils;
import com.aricneto.twistytimer.utils.Wrapper;
import com.github.ksoichiro.android.observablescrollview.CacheFragmentStatePagerAdapter;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

import static com.aricneto.twistytimer.utils.TTIntent.ACTION_CHANGED_CATEGORY;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_CHANGED_THEME;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_DELETE_SELECTED_TIMES;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_HISTORY_TIMES_SHOWN;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_SCROLLED_PAGE;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_SELECTION_MODE_OFF;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_SELECTION_MODE_ON;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_SESSION_TIMES_SHOWN;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_TIMER_STARTED;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_TIMER_STOPPED;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_TIME_SELECTED;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_TIME_UNSELECTED;
import static com.aricneto.twistytimer.utils.TTIntent.ACTION_TOOLBAR_RESTORED;
import static com.aricneto.twistytimer.utils.TTIntent.CATEGORY_TIME_DATA_CHANGES;
import static com.aricneto.twistytimer.utils.TTIntent.CATEGORY_UI_INTERACTIONS;
import static com.aricneto.twistytimer.utils.TTIntent.TTFragmentBroadcastReceiver;
import static com.aricneto.twistytimer.utils.TTIntent.broadcast;
import static com.aricneto.twistytimer.utils.TTIntent.registerReceiver;
import static com.aricneto.twistytimer.utils.TTIntent.unregisterReceiver;

public class TimerFragmentMain extends BaseFragment implements OnBackPressedInFragmentListener {
    /**
     * Flag to enable debug logging for this class.
     */
    private static final boolean DEBUG_ME = false;

    /**
     * A "tag" to identify this class in log messages.
     */
    private static final String TAG = TimerFragmentMain.class.getSimpleName();

    /**
     * The zero-based position of the timer fragment/tab/page.
     */
    public static final int TIMER_PAGE = 0;

    /**
     * The zero-based position of the timer list fragment/tab/page.
     */
    public static final int LIST_PAGE = 1;

    /**
     * The zero-based position of the timer graph fragment/tab/page.
     */
    public static final int GRAPH_PAGE = 2;

    /**
     * The total number of pages.
     */
    private static final int NUM_PAGES = 3;

    private static final String KEY_SAVEDSUBTYPE = "savedSubtype";

    private Unbinder mUnbinder;

    @BindView(R.id.toolbar)       RelativeLayout         mToolbar;
    @BindView(R.id.pager)         LockedViewPager viewPager;
    @BindView(R.id.main_tabs)     TabLayout       tabLayout;
    @BindView(R.id.puzzleSpinner) View puzzleSpinnerLayout;

    @BindView(R.id.nav_button_settings) View      navButtonSettings;
    @BindView(R.id.nav_button_category) View      navButtonCategory;
    @BindView(R.id.nav_button_history) ImageView navButtonHistory;

    @BindView(R.id.puzzleCategory) TextView puzzleCategoryText;
    @BindView(R.id.puzzleName) TextView puzzleNameText;

    ActionMode actionMode;

    private LinearLayout      tabStrip;
    private NavigationAdapter viewPagerAdapter;

    private MaterialDialog removeSubtypeDialog;
    private MaterialDialog subtypeDialog;
    private MaterialDialog createSubtypeDialog;
    private MaterialDialog renameSubtypeDialog;

    // Stores the current puzzle being timed/shown
    private String currentPuzzle        = PuzzleUtils.TYPE_333;
    private String currentPuzzleSubtype = "Normal";
    // Stores the current state of the list switch
    boolean history = false;

    int currentPage = TIMER_PAGE;
    private boolean pagerEnabled;

    private int originalContentHeight;
    private int selectCount = 0;

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.nav_button_category:
                    BottomSheetCategoryDialog categoryDialog = BottomSheetCategoryDialog.newInstance(currentPuzzle, currentPuzzleSubtype);
                    categoryDialog.setDialogListener(new DialogListenerMessage() {
                        @Override
                        public void onUpdateDialog(String text) {
                            currentPuzzleSubtype = text;
                            broadcast(CATEGORY_UI_INTERACTIONS, ACTION_CHANGED_CATEGORY);
                        }
                    });
                    categoryDialog.show(getFragmentManager(), "bottom_sheet_category_dialog");
                    break;
                case R.id.nav_button_history:
                    history = !history;

                    updateHistorySwitchItem();

                    broadcast(CATEGORY_TIME_DATA_CHANGES,
                              history ? ACTION_HISTORY_TIMES_SHOWN : ACTION_SESSION_TIMES_SHOWN);
                    break;
                case R.id.nav_button_settings:
                    getMainActivity().openDrawer();
                    break;
            }
        }
    };

    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {

        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.menu_list_callback, menu);

            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //getActivity().getWindow().setStatusBarColor(ThemeUtils.fetchAttrColor(getContext(), R.attr.colorPrimaryDark));
            //}
            return true; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete:
                    // Receiver will delete times and then broadcast "ACTION_TIMES_MODIFIED".
                    broadcast(CATEGORY_UI_INTERACTIONS, ACTION_DELETE_SELECTED_TIMES);
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    };

    // Receives broadcasts about changes to the time user interface.
    private TTFragmentBroadcastReceiver mUIInteractionReceiver
            = new TTFragmentBroadcastReceiver(this, CATEGORY_UI_INTERACTIONS) {
        @Override
        public void onReceiveWhileAdded(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_CHANGED_THEME:
                    try {
                        // If the theme has been changed, then the activity will need to be recreated. The
                        // theme can only be applied properly during the inflation of the layouts, so it has
                        // to go back to "Activity.onCreate()" to do that.
                        ((MainActivity) getActivity()).onRecreateRequired();
                    } catch (Exception e) {}
                    break;
                case ACTION_TIMER_STARTED:
                    viewPager.setPagingEnabled(false);
                    activateTabLayout(false);
                    mToolbar.animate()
                            .translationY(-mToolbar.getHeight())
                            .alpha(0)
                            .setDuration(300);

                    tabLayout.animate()
                             .translationY(tabLayout.getHeight())
                             .alpha(0)
                             .setDuration(300);
                    break;

                case ACTION_TIMER_STOPPED:
                    mToolbar.animate()
                            .translationY(0)
                            .alpha(1)
                            .setDuration(300);

                    tabLayout.animate()
                            .translationY(0)
                            .alpha(1)
                            .setDuration(300)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    broadcast(CATEGORY_UI_INTERACTIONS, ACTION_TOOLBAR_RESTORED);
                                }
                            });

                    activateTabLayout(true);
                    if (pagerEnabled)
                        viewPager.setPagingEnabled(true);
                    else
                        viewPager.setPagingEnabled(false);
                    break;

                case ACTION_SELECTION_MODE_ON:
                    selectCount = 0;
                    actionMode = mToolbar.startActionMode(actionModeCallback);
                    break;

                case ACTION_SELECTION_MODE_OFF:
                    selectCount = 0;
                    if (actionMode != null)
                        actionMode.finish();
                    break;

                case ACTION_TIME_SELECTED:
                    selectCount += 1;
                    actionMode.setTitle(getResources().getQuantityString(R.plurals.selected_list, selectCount, selectCount));
                    break;

                case ACTION_TIME_UNSELECTED:
                    selectCount -= 1;
                    actionMode.setTitle(getResources().getQuantityString(R.plurals.selected_list, selectCount, selectCount));
                    break;

                case ACTION_CHANGED_CATEGORY:
                    viewPager.setAdapter(viewPagerAdapter);
                    viewPager.setCurrentItem(currentPage);
                    updatePuzzleSpinnerHeader();
                    handleStatisticsLoader();
                    break;
            }
        }
    };

    private void updatePuzzleSpinnerHeader() {
        history = false;
        updateHistorySwitchItem();
        puzzleCategoryText.setText(currentPuzzleSubtype.toLowerCase());
        puzzleNameText.setText(PuzzleUtils.getPuzzleNameFromType(currentPuzzle));
    }

    public TimerFragmentMain() {
        // Required empty public constructor
    }

    public static TimerFragmentMain newInstance() {
        final TimerFragmentMain fragment = new TimerFragmentMain();
        if (DEBUG_ME) Log.d(TAG, "newInstance() -> " + fragment);
        return fragment;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (DEBUG_ME) Log.d(TAG, "onSaveInstanceState()");
        super.onSaveInstanceState(outState);
        outState.putString("puzzle", currentPuzzle);
        outState.putString("subtype", currentPuzzleSubtype);
        outState.putBoolean("history", history);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG_ME) Log.d(TAG, "onCreate(savedInstanceState=" + savedInstanceState + ")");
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentPuzzle = savedInstanceState.getString("puzzle");
            currentPuzzleSubtype = savedInstanceState.getString("subtype");
            history = savedInstanceState.getBoolean("history");
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (DEBUG_ME) Log.d(TAG, "onCreateView(savedInstanceState=" + savedInstanceState + ")");
        View root = inflater.inflate(R.layout.fragment_timer_main, container, false);
        mUnbinder = ButterKnife.bind(this, root);

        setupHistorySwitchItem();

        navButtonCategory.setOnClickListener(clickListener);
        navButtonHistory.setOnClickListener(clickListener);
        navButtonSettings.setOnClickListener(clickListener);

        pagerEnabled = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(
                getString(R.string.pk_tab_swiping_enabled), true);

        if (pagerEnabled)
            viewPager.setPagingEnabled(true);
        else
            viewPager.setPagingEnabled(false);


        viewPagerAdapter = new NavigationAdapter(getChildFragmentManager());
        viewPager.setAdapter(viewPagerAdapter);
        viewPager.setOffscreenPageLimit(NUM_PAGES - 1);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.setSelectedTabIndicator(0);
        tabLayout.setTabIconTint(AppCompatResources.getColorStateList(getContext(), R.color.tab_color));
        tabStrip = ((LinearLayout) tabLayout.getChildAt(0));

        if (savedInstanceState == null) {
            updateCurrentSubtype();
        }

        // Handle spinner AFTER reading from savedInstanceState, so we can correctly
        // fill the category field in the spinner
        handleHeaderSpinner();

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                setupPage(position, inflater);
                currentPage = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                broadcast(CATEGORY_UI_INTERACTIONS, ACTION_SCROLLED_PAGE);
            }
        });

        // Sets up the toolbar with the icons appropriate to the current page.
        mToolbar.post(new Runnable() {
            @Override
            public void run() {
                setupPage(currentPage, inflater);
            }
        });

        // Register a receiver to update if something has changed
        registerReceiver(mUIInteractionReceiver);

        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        handleStatisticsLoader();

    }

    private void handleStatisticsLoader() {
        // The "StatisticsLoader" is managed from this fragment, as it has the necessary access to
        // the puzzle type, subtype and history values.
        //
        // "restartLoader" ensures that any old loader with the wrong puzzle type/subtype will not
        // be reused. For now, those arguments are just passed via their respective fields to
        // "onCreateLoader".

        if (DEBUG_ME)
            Log.d(TAG, "Puzzle and subtype: " + currentPuzzle + " // " + currentPuzzleSubtype);
        if (DEBUG_ME) Log.d(TAG, "onActivityCreated -> restartLoader: STATISTICS_LOADER_ID");
        getLoaderManager().restartLoader(MainActivity.STATISTICS_LOADER_ID, null,
                                         new LoaderManager.LoaderCallbacks<Wrapper<Statistics>>() {
                                             @Override
                                             public Loader<Wrapper<Statistics>> onCreateLoader(int id, Bundle args) {
                                                 if (DEBUG_ME)
                                                     Log.d(TAG, "onCreateLoader: STATISTICS_LOADER_ID");
                                                 return new StatisticsLoader(getContext(), Statistics.newAllTimeStatistics(),
                                                                             currentPuzzle, currentPuzzleSubtype);
                                             }

                                             @Override
                                             public void onLoadFinished(Loader<Wrapper<Statistics>> loader,
                                                                        Wrapper<Statistics> data) {
                                                 if (DEBUG_ME)
                                                     Log.d(TAG, "onLoadFinished: STATISTICS_LOADER_ID");
                                                 // Other fragments can get the statistics from the cache when they are
                                                 // created and can register themselves as observers of further updates.
                                                 StatisticsCache.getInstance().updateAndNotify(data.content());
                                             }

                                             @Override
                                             public void onLoaderReset(Loader<Wrapper<Statistics>> loader) {
                                                 if (DEBUG_ME)
                                                     Log.d(TAG, "onLoaderReset: STATISTICS_LOADER_ID");
                                                 // Clear the cache and notify all observers that the statistics are "null".
                                                 StatisticsCache.getInstance().updateAndNotify(null);
                                             }
                                         });
    }

    private void activateTabLayout(boolean b) {
        tabStrip.setEnabled(b);
        for (int i = 0; i < tabStrip.getChildCount(); i++) {
            tabStrip.getChildAt(i).setClickable(b);
        }
    }

    @Override
    public void onResume() {
        if (DEBUG_ME) Log.d(TAG, "onResume() : currentPage=" + currentPage);
        super.onResume();
    }

    /**
     * Passes on the "Back" button press event to subordinate fragments and indicates if any
     * fragment consumed the event.
     *
     * @return {@code true} if the "Back" button press was consumed and no further action should be
     * taken; or {@code false} if the "Back" button press was ignored and the caller should
     * propagate it to the next interested party.
     */
    @Override
    public boolean onBackPressedInFragment() {
        if (DEBUG_ME) Log.d(TAG, "onBackPressedInFragment()");

        return viewPagerAdapter != null && viewPagerAdapter.dispatchOnBackPressedInFragment();
    }

    @Override
    public void onDetach() {
        if (DEBUG_ME) Log.d(TAG, "onDetach()");
        super.onDetach();
        unregisterReceiver(mUIInteractionReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mUnbinder.unbind();
    }

    Drawable[] onOff = new Drawable[2];

    private void setupHistorySwitchItem() {
        Context context = getContext();

        if (context != null) {
            onOff[0] = ContextCompat.getDrawable(context, R.drawable.ic_history_on);
            onOff[1] = ContextCompat.getDrawable(context, R.drawable.ic_history_off);
        }
    }

    private void updateHistorySwitchItem() {
        if (history) {
            navButtonHistory.setImageDrawable(onOff[0]);
            navButtonHistory.animate()
                    .rotation(-135)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setDuration(300)
                    .start();
        } else {
            navButtonHistory.setImageDrawable(onOff[1]);
            navButtonHistory.animate()
                    .rotation(0)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setDuration(300)
                    .start();
        }
    }

    /**
     * Sets the page's toolbar buttons and other things
     *
     * @param pageNum
     * @param inflater
     */
    private void setupPage(int pageNum, LayoutInflater inflater) {
        if (DEBUG_ME) Log.d(TAG, "setupPage(pageNum=" + pageNum + ")");

        if (actionMode != null)
            actionMode.finish();

        if (mToolbar == null) {
            return;
        }

        //mToolbar.getMenu().clear();

        switch (pageNum) {
            case TIMER_PAGE:
                navButtonHistory.animate()
                        .withStartAction(() -> navButtonHistory.setEnabled(false))
                        .alpha(0)
                        .setDuration(200)
                        .withEndAction(() -> navButtonHistory.setVisibility(View.GONE))
                        .start();
                break;

            case LIST_PAGE:
            case GRAPH_PAGE:
                navButtonHistory.setVisibility(View.VISIBLE);
                navButtonHistory.animate()
                        .withStartAction(() -> navButtonHistory.setEnabled(true))
                        .alpha(1)
                        .setDuration(200)
                        .start();
                break;
        }
    }

    /**
     * The app saves the last subtype used for each puzzle. This function is called to both update
     * the last subtype when it's changed, and to set the subtipe.
     */
    private void updateCurrentSubtype() {
        SharedPreferences        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(TwistyTimer.getAppContext());
        SharedPreferences.Editor editor            = sharedPreferences.edit();
        final List<String> subtypeList
                = TwistyTimer.getDBHandler().getAllSubtypesFromType(currentPuzzle);
        if (subtypeList.size() == 0) {
            currentPuzzleSubtype = "Normal";
            editor.putString(KEY_SAVEDSUBTYPE + currentPuzzle, "Normal");
            editor.apply();
        } else {
            currentPuzzleSubtype = sharedPreferences.getString(KEY_SAVEDSUBTYPE + currentPuzzle, "Normal");
        }
    }

    private void handleHeaderSpinner() {

        // Create item arrays
        String[] titles = {
                getString(R.string.cube_333),
                getString(R.string.cube_222),
                getString(R.string.cube_444),
                getString(R.string.cube_555),
                getString(R.string.cube_666),
                getString(R.string.cube_777),
                getString(R.string.cube_clock),
                getString(R.string.cube_mega),
                getString(R.string.cube_pyra),
                getString(R.string.cube_skewb),
                getString(R.string.cube_sq1)
        };

        int[] icons = {
                R.drawable.ic_outline_grid_on_24px,
                R.drawable.ic_outline_grid_on_2_24px,
                R.drawable.ic_outline_looks_4_24px,
                R.drawable.ic_outline_looks_5_24px,
                R.drawable.ic_outline_filter_6_24px,
                R.drawable.ic_outline_filter_7_24px,
                R.drawable.ic_outline_radio_button_unchecked_24px,
                R.drawable.ic_outline_megaminx,
                R.drawable.ic_pyra,
                R.drawable.ic_outline_crop_free_24px,
                R.drawable.ic_outline_looks_one_24px,
        };

        // Setup spinner dialog and adapter
        BottomSheetSpinnerDialog bottomSheetSpinnerDialog = BottomSheetSpinnerDialog.newInstance();
        BottomSheetSpinnerAdapter bottomSheetSpinnerAdapter = new BottomSheetSpinnerAdapter(getContext(), titles, icons);

        bottomSheetSpinnerDialog.setTitle(getString(R.string.dialog_select_puzzle), R.drawable.ic_outline_casino_24px);

        bottomSheetSpinnerDialog.setListAdapter(bottomSheetSpinnerAdapter);

        bottomSheetSpinnerDialog.setListClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (DEBUG_ME) Log.d(TAG, "onItemSelected(position=" + position + ")");

                bottomSheetSpinnerDialog.dismiss();

                currentPuzzle = PuzzleUtils.getPuzzleInPosition(position);
                updateCurrentSubtype();
                viewPager.setAdapter(viewPagerAdapter);
                viewPager.setCurrentItem(currentPage);

                // update titles
                updatePuzzleSpinnerHeader();

                handleStatisticsLoader();
            }
        });

        // Setup action bar click listener
        puzzleSpinnerLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bottomSheetSpinnerDialog.show(getFragmentManager(), "puzzle_spinner_dialog_fragment");
            }
        });

        updatePuzzleSpinnerHeader();

    }

    protected class NavigationAdapter extends CacheFragmentStatePagerAdapter {

        public NavigationAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        protected Fragment createItem(int position) {
            if (DEBUG_ME) Log.d(TAG, "NavigationAdapter.createItem(" + position + ")");

            switch (position) {
                case TIMER_PAGE:
                    return TimerFragment.newInstance(currentPuzzle, currentPuzzleSubtype);
                case LIST_PAGE:
                    return TimerListFragment.newInstance(
                            currentPuzzle, currentPuzzleSubtype, history);
                case GRAPH_PAGE:
                    return TimerGraphFragment.newInstance(
                            currentPuzzle, currentPuzzleSubtype, history);
            }
            return TimerFragment.newInstance(PuzzleUtils.TYPE_333, "Normal");
        }

        /**
         * Notifies each fragment (that is listening) that the "Back" button has been pressed.
         * Stops when the first fragment consumes the event.
         *
         * @return {@code true} if any fragment consumed the "Back" button press event; or {@code false}
         * if the event was not consumed by any fragment.
         */
        public boolean dispatchOnBackPressedInFragment() {
            if (DEBUG_ME) Log.d(TAG, "NavigationAdapter.dispatchOnBackPressedInFragment()");
            boolean isConsumed = false;

            for (int p = 0; p < NUM_PAGES && !isConsumed; p++) {
                final Fragment fragment = getItemAt(p);

                if (fragment instanceof OnBackPressedInFragmentListener) { // => not null
                    isConsumed = ((OnBackPressedInFragmentListener) fragment)
                            .onBackPressedInFragment();
                }
            }

            return isConsumed;
        }

        @Override
        public int getCount() {
            return NUM_PAGES;
        }
    }
}
