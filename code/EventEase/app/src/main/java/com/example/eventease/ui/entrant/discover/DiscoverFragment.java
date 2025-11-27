package com.example.eventease.ui.entrant.discover;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.model.Event;
import com.example.eventease.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for discovering and browsing available events.
 * 
 * <p>This fragment displays a list of events that are currently open for registration.
 * An event is considered open if the current time falls within its registration period
 * (between registrationStart and registrationEnd).
 * 
 * <p>Features:
 * <ul>
 *   <li>Real-time event updates using Firestore listeners</li>
 *   <li>Event cards showing title, date, location, and poster image</li>
 *   <li>Click to view event details and join waitlist</li>
 *   <li>Empty state message when no events are available</li>
 * </ul>
 * 
 * <p>The fragment automatically updates when events are added, modified, or removed from Firestore.
 */
public class DiscoverFragment extends Fragment {

    private DiscoverAdapter adapter;
    private ListenerRegistration eventsRegistration;
    private ProgressBar progressView;
    private TextView emptyView;
    private EditText searchInput;
    private String searchQuery = "";
    private String locationFilter = "";
    private final List<Event> allEvents = new ArrayList<>();

    private enum DateFilterOption {
        ANY_DATE,
        TODAY,
        THIS_MONTH,
        CUSTOM
    }

    private DateFilterOption activeDateFilter = DateFilterOption.ANY_DATE;
    private long customDateFilterStartMs = 0L;
    private final SimpleDateFormat filterDateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.entrant_fragment_discover, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rvDiscover);
        progressView = view.findViewById(R.id.discoverProgress);
        emptyView = view.findViewById(R.id.discoverEmpty);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(true);

        adapter = new DiscoverAdapter(event -> {
            Intent intent = new Intent(requireContext(), EventDetailsDiscoverActivity.class);
            intent.putExtra(EventDetailsDiscoverActivity.EXTRA_EVENT_TITLE, event.getTitle());
            intent.putExtra(EventDetailsDiscoverActivity.EXTRA_EVENT_ID, event.getId());
            startActivity(intent);
        });
        rv.setAdapter(adapter);

        View filterButton = view.findViewById(R.id.discover_filter);
        if (filterButton != null) {
            filterButton.setOnClickListener(v -> showFilterDialog());
        }

        searchInput = view.findViewById(R.id.etDiscoverSearch);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    updateSearchQuery(s != null ? s.toString() : "");
                }
            });
        }

        listenForEvents();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Ensure bottom nav is visible when this fragment is shown (safety check)
        ensureBottomNavVisible();
    }
    
    private void ensureBottomNavVisible() {
        if (getActivity() == null) return;
        
        // Check if user is authenticated - simply check if Firebase Auth has a current user
        // "Remember Me" only affects persistence across app restarts, not current authentication state
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        boolean isAuthenticated = currentUser != null;
        
        if (isAuthenticated) {
            View bottomNav = getActivity().findViewById(R.id.include_bottom);
            View topBar = getActivity().findViewById(R.id.include_top);
            if (bottomNav != null) {
                bottomNav.setVisibility(View.VISIBLE);
            }
            if (topBar != null) {
                topBar.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (eventsRegistration != null) {
            eventsRegistration.remove();
            eventsRegistration = null;
        }
        adapter = null;
        progressView = null;
        emptyView = null;
    }

    private void showFilterDialog() {
        if (!isAdded()) {
            return;
        }

        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.entrant_dialog_discover_filter);
        dialog.setCanceledOnTouchOutside(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
            layoutParams.dimAmount = 0f;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        Bitmap screenshot = captureScreenshot();
        if (screenshot != null) {
            Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
            if (blurredBitmap != null) {
                View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
                if (blurBackground != null) {
                    blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
                }
            }
        }

        View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        ImageButton closeButton = dialog.findViewById(R.id.btnFilterClose);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dialog.dismiss());
        }

        RadioButton radioAnyDate = dialog.findViewById(R.id.radioAnyDate);
        RadioButton radioToday = dialog.findViewById(R.id.radioToday);
        RadioButton radioThisMonth = dialog.findViewById(R.id.radioTomorrow);
        RadioButton radioCustom = dialog.findViewById(R.id.radioCustomDate);
        TextView chooseDateValue = dialog.findViewById(R.id.textChooseDateValue);
        View chooseDateRow = dialog.findViewById(R.id.chooseDateRow);
        EditText etFilterLocation = dialog.findViewById(R.id.etFilterLocation);
        if (etFilterLocation != null) {
            etFilterLocation.setText(locationFilter);
        }

        restoreDateSelection(radioAnyDate, radioToday, radioThisMonth, radioCustom, chooseDateValue);
        setDateSelectionHandlers(radioAnyDate, radioToday, radioThisMonth, radioCustom, chooseDateRow, chooseDateValue);

        Button applyButton = dialog.findViewById(R.id.btnApplyFilters);
        if (applyButton != null) {
            applyButton.setOnClickListener(v -> {
                if (etFilterLocation != null) {
                    locationFilter = etFilterLocation.getText() != null
                            ? etFilterLocation.getText().toString().trim()
                            : "";
                }
                applyFiltersAndUpdateUI();
                dialog.dismiss();
            });
        }

        dialog.show();

        View card = dialog.findViewById(R.id.dialogCardView);
        if (blurBackground != null && card != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_zoom_in);

            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
        }
    }

    private void restoreDateSelection(@Nullable RadioButton any, @Nullable RadioButton today,
                                      @Nullable RadioButton thisMonth, @Nullable RadioButton custom,
                                      @Nullable TextView chooseDateValue) {
        clearDateChecks(any, today, thisMonth, custom);
        if (any == null || today == null || thisMonth == null || custom == null) return;

        switch (activeDateFilter) {
            case ANY_DATE:
                any.setChecked(true);
                break;
            case TODAY:
                today.setChecked(true);
                break;
            case THIS_MONTH:
                thisMonth.setChecked(true);
                break;
            case CUSTOM:
                custom.setChecked(true);
                break;
        }

        updateChooseDateLabel(chooseDateValue);
    }

    private void openDatePicker(@Nullable TextView chooseDateValue, @Nullable RadioButton customRadio) {
        if (!isAdded()) return;
        Calendar cal = Calendar.getInstance();
        if (customDateFilterStartMs > 0) {
            cal.setTimeInMillis(customDateFilterStartMs);
        }

        DatePickerDialog picker = new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, dayOfMonth, 0, 0, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            customDateFilterStartMs = chosen.getTimeInMillis();
            activeDateFilter = DateFilterOption.CUSTOM;
            updateChooseDateLabel(chooseDateValue);
            if (customRadio != null) {
                customRadio.setChecked(true);
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        picker.show();
    }

    private void updateChooseDateLabel(@Nullable TextView chooseDateValue) {
        if (chooseDateValue == null) return;
        if (customDateFilterStartMs > 0) {
            chooseDateValue.setText(filterDateFormat.format(new Date(customDateFilterStartMs)));
        } else {
            chooseDateValue.setText("No date selected");
        }
    }

    private void setDateSelectionHandlers(@Nullable RadioButton any, @Nullable RadioButton today,
                                          @Nullable RadioButton thisMonth, @Nullable RadioButton custom,
                                          @Nullable View chooseDateRow, @Nullable TextView chooseDateValue) {
        View.OnClickListener anyHandler = v -> setDateChoice(DateFilterOption.ANY_DATE, any, today, thisMonth, custom, chooseDateValue, false);
        View.OnClickListener todayHandler = v -> setDateChoice(DateFilterOption.TODAY, today, any, thisMonth, custom, chooseDateValue, false);
        View.OnClickListener thisMonthHandler = v -> setDateChoice(DateFilterOption.THIS_MONTH, thisMonth, any, today, custom, chooseDateValue, false);
        View.OnClickListener customHandler = v -> setDateChoice(DateFilterOption.CUSTOM, custom, any, today, thisMonth, chooseDateValue, true);

        if (any != null) any.setOnClickListener(anyHandler);
        if (today != null) today.setOnClickListener(todayHandler);
        if (thisMonth != null) thisMonth.setOnClickListener(thisMonthHandler);
        if (custom != null) custom.setOnClickListener(customHandler);
        if (chooseDateRow != null) chooseDateRow.setOnClickListener(customHandler);
    }

    private void setDateChoice(DateFilterOption option, @Nullable RadioButton target,
                               @Nullable RadioButton other1, @Nullable RadioButton other2, @Nullable RadioButton other3,
                               @Nullable TextView chooseDateValue, boolean launchPickerIfNeeded) {
        clearDateChecks(target, other1, other2, other3);
        if (target != null) {
            target.setChecked(true);
        }
        activeDateFilter = option;
        if (option == DateFilterOption.CUSTOM) {
            if (customDateFilterStartMs <= 0 || launchPickerIfNeeded) {
                openDatePicker(chooseDateValue, target);
            } else {
                updateChooseDateLabel(chooseDateValue);
            }
        } else {
            updateChooseDateLabel(chooseDateValue);
        }
    }

    private void clearDateChecks(@Nullable RadioButton... radios) {
        if (radios == null) return;
        for (RadioButton rb : radios) {
            if (rb != null) {
                rb.setChecked(false);
            }
        }
    }

    private Bitmap captureScreenshot() {
        try {
            if (getActivity() == null || getActivity().getWindow() == null) return null;
            View rootView = getActivity().getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap blurBitmap(Bitmap bitmap, float radius) {
        if (bitmap == null || getContext() == null) return null;

        try {
            int width = Math.round(bitmap.getWidth() * 0.4f);
            int height = Math.round(bitmap.getHeight() * 0.4f);
            Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

            RenderScript rs = RenderScript.create(getContext());
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);

            blurScript.setRadius(radius);
            blurScript.setInput(tmpIn);
            blurScript.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);

            rs.destroy();

            return Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), true);
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    private void listenForEvents() {
        setLoading(true);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        // Query all events without orderBy to ensure all events are returned
        // We'll sort them in memory after fetching
        Query query = firestore.collection("events");

        eventsRegistration = query.addSnapshotListener((snapshots, error) -> {
            if (!isAdded()) return;
            if (error != null) {
                setLoading(false);
                handleError(error);
                return;
            }
            setLoading(false);
            handleSnapshot(snapshots);
        });
    }

    private void handleSnapshot(@Nullable QuerySnapshot snapshots) {
        android.util.Log.d("DiscoverFragment", "handleSnapshot docs=" + (snapshots != null ? snapshots.size() : -1));
        if (adapter == null) return;
        if (snapshots == null) {
            adapter.clear();
            showEmptyState(true);
            return;
        }

        long currentTime = System.currentTimeMillis();
        List<Event> events = new ArrayList<>();
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            android.util.Log.d("DiscoverFragment", "Doc id=" + doc.getId() + " data=" + doc.getData());
            Event event = Event.fromMap(doc.getData());
            if (event == null) continue;
            if (TextUtils.isEmpty(event.id)) {
                event.id = doc.getId();
            }
            
            // Filter out events where the start date has already passed
            long eventStartTime = event.getStartsAtEpochMs();
            if (eventStartTime > 0 && eventStartTime < currentTime) {
                android.util.Log.d("DiscoverFragment", "Event " + event.getTitle() + " (id: " + event.getId() + ") has already started/passed, excluding from discover");
                continue;
            }
            
            events.add(event);
        }
        
        // Sort events by start time in memory (events without startsAtEpochMs will be sorted last)
        Collections.sort(events, new Comparator<Event>() {
            @Override
            public int compare(Event e1, Event e2) {
                // Events with startsAtEpochMs = 0 or missing will be sorted to the end
                if (e1.startsAtEpochMs == 0 && e2.startsAtEpochMs == 0) return 0;
                if (e1.startsAtEpochMs == 0) return 1;
                if (e2.startsAtEpochMs == 0) return -1;
                return Long.compare(e1.startsAtEpochMs, e2.startsAtEpochMs);
            }
        });
        
        android.util.Log.d("DiscoverFragment", "Parsed " + events.size() + " upcoming events (filtered out past events)");
        allEvents.clear();
        allEvents.addAll(events);
        applyFiltersAndUpdateUI();
    }

    private void applyFiltersAndUpdateUI() {
        if (adapter == null) return;

        List<Event> filtered = new ArrayList<>();
        for (Event event : allEvents) {
            if (passesDateFilter(event) && passesLocationFilter(event) && passesSearchFilter(event)) {
                filtered.add(event);
            }
        }

        adapter.submit(filtered);
        showEmptyState(filtered.isEmpty());
    }

    private void handleError(@NonNull FirebaseFirestoreException error) {
        if (adapter != null) {
            adapter.clear();
        }
        showEmptyState(true);
        if (getContext() != null) {
            String message = error.getMessage();
            if (TextUtils.isEmpty(message)) {
                message = "Unable to load events right now.";
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void setLoading(boolean loading) {
        if (progressView != null) {
            progressView.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyView != null) {
            emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show && emptyView.getText().length() == 0) {
                emptyView.setText(R.string.discover_empty_state);
            }
        }
    }

    private boolean passesLocationFilter(@NonNull Event event) {
        if (TextUtils.isEmpty(locationFilter)) {
            return true;
        }
        String eventLocation = event.location;
        if (TextUtils.isEmpty(eventLocation)) {
            return false;
        }
        return eventLocation.toLowerCase(Locale.getDefault())
                .contains(locationFilter.toLowerCase(Locale.getDefault()));
    }

    private boolean passesSearchFilter(@NonNull Event event) {
        if (TextUtils.isEmpty(searchQuery)) {
            return true;
        }
        String queryLower = searchQuery.toLowerCase(Locale.getDefault());
        String title = event.getTitle();
        if (!TextUtils.isEmpty(title) && title.toLowerCase(Locale.getDefault()).contains(queryLower)) {
            return true;
        }
        List<String> interests = event.getInterests();
        if (interests == null || interests.isEmpty()) {
            return false;
        }
        for (String interest : interests) {
            if (interest != null && interest.toLowerCase(Locale.getDefault()).contains(queryLower)) {
                return true;
            }
        }
        return false;
    }

    private boolean passesDateFilter(@NonNull Event event) {
        if (activeDateFilter == DateFilterOption.ANY_DATE) {
            return true;
        }

        long eventDate = getEventDateForFilter(event);
        if (eventDate <= 0) {
            return false;
        }

        switch (activeDateFilter) {
            case TODAY:
                return isSameDay(eventDate, startOfDay(System.currentTimeMillis()));
            case THIS_MONTH:
                return isSameMonth(eventDate, System.currentTimeMillis());
            case CUSTOM:
                if (customDateFilterStartMs <= 0) return true;
                return isSameDay(eventDate, customDateFilterStartMs);
            default:
                return true;
        }
    }

    private long getEventDateForFilter(@NonNull Event event) {
        if (event.getStartsAtEpochMs() > 0) {
            return event.getStartsAtEpochMs();
        }
        return event.registrationStart > 0 ? event.registrationStart : 0;
    }

    private boolean isSameDay(long timestampMs, long dayStartMs) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(dayStartMs);

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestampMs);

        return day.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && day.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isSameMonth(long timestampMs, long referenceMs) {
        Calendar ref = Calendar.getInstance();
        ref.setTimeInMillis(referenceMs);
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestampMs);
        return ref.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && ref.get(Calendar.MONTH) == target.get(Calendar.MONTH);
    }

    private long startOfDay(long timeMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void updateSearchQuery(@NonNull String query) {
        String cleaned = query.trim();
        if (cleaned.equals(searchQuery)) {
            return;
        }
        searchQuery = cleaned;
        applyFiltersAndUpdateUI();
    }
}
