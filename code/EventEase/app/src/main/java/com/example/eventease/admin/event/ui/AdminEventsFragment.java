package com.example.eventease.admin.event.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
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
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.eventease.R;
import com.example.eventease.admin.event.data.AdminEventDatabaseController;
import com.example.eventease.admin.event.data.Event;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for admin users to view and manage all events in the system.
 * 
 * <p>This fragment displays a list of all events from the Firestore database and provides
 * administrative functionality to view and delete events. Admins can see all events regardless
 * of which organizer created them.
 * 
 * <p>Features:
 * <ul>
 *   <li>View all events in a scrollable list</li>
 *   <li>Delete events with confirmation dialog</li>
 *   <li>Refresh event list after deletion</li>
 * </ul>
 * 
 * <p>The fragment uses AdminEventDatabaseController to fetch events and perform deletions.
 * Only users with admin role can access this functionality.
 */
public class AdminEventsFragment extends Fragment {

    private final AdminEventDatabaseController AEDC = new AdminEventDatabaseController();
    private RecyclerView rv;
    private EventAdapter adapter;
    private final List<Event> allEvents = new ArrayList<>();

    // Search / filter state
    private EditText searchInput;
    private String searchQuery = "";

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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.admin_event_management, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rv = view.findViewById(R.id.rvEvents);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new EventAdapter(requireContext(), new ArrayList<>(), this::deleteEventAndRefresh);
            rv.setAdapter(adapter);
        }

        // Set up search input
        searchInput = view.findViewById(R.id.etAdminSearch);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    updateSearchQuery(s != null ? s.toString() : "");
                }
            });
        }

        // Set up filter button
        View filterButton = view.findViewById(R.id.admin_filter);
        if (filterButton != null) {
            filterButton.setOnClickListener(v -> showFilterDialog());
        }

        // Async load; update UI when data is received
        AEDC.fetchEvents(new AdminEventDatabaseController.EventsCallback() {
            @Override
            public void onLoaded(@NonNull List<Event> data) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        allEvents.clear();
                        allEvents.addAll(data);
                        applyFiltersAndUpdateUI();
                    });
                }
            }

            @Override
            public void onError(@NonNull Exception e) {
                // TODO: show error state/snackbar if desired
            }
        });
    }

    private void deleteEventAndRefresh(@NonNull Event e) {
        AEDC.deleteEvent(e);
        // Re-fetch to refresh the list after delete
        AEDC.fetchEvents(new AdminEventDatabaseController.EventsCallback() {
            @Override
            public void onLoaded(@NonNull List<Event> data) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        allEvents.clear();
                        allEvents.addAll(data);
                        applyFiltersAndUpdateUI();
                    });
                }
            }
            @Override public void onError(@NonNull Exception ex) { /* optionally report */ }
        });
    }

    private void updateSearchQuery(@NonNull String query) {
        String cleaned = query.trim();
        if (cleaned.equals(searchQuery)) {
            return;
        }
        searchQuery = cleaned;
        applyFiltersAndUpdateUI();
    }

    private void applyFiltersAndUpdateUI() {
        if (adapter == null) return;

        List<Event> filtered = new ArrayList<>();
        for (Event e : allEvents) {
            if (passesDateFilter(e) && passesSearchFilter(e)) {
                filtered.add(e);
            }
        }

        adapter.submitList(filtered);
    }

    private boolean passesSearchFilter(@NonNull Event e) {
        if (TextUtils.isEmpty(searchQuery)) {
            return true;
        }
        String q = searchQuery.toLowerCase(Locale.getDefault());

        if (!TextUtils.isEmpty(e.getTitle()) && e.getTitle().toLowerCase(Locale.getDefault()).contains(q)) {
            return true;
        }
        if (!TextUtils.isEmpty(e.getDescription()) && e.getDescription().toLowerCase(Locale.getDefault()).contains(q)) {
            return true;
        }
        if (!TextUtils.isEmpty(e.getGuidelines()) && e.getGuidelines().toLowerCase(Locale.getDefault()).contains(q)) {
            return true;
        }
        return false;
    }

    private boolean passesDateFilter(@NonNull Event e) {
        if (activeDateFilter == DateFilterOption.ANY_DATE) {
            return true;
        }

        long regStart = e.getRegistrationStart();
        if (regStart <= 0L) {
            return true; // If no date, don't filter it out
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        int todayYear = cal.get(Calendar.YEAR);
        int todayMonth = cal.get(Calendar.MONTH);
        int todayDay = cal.get(Calendar.DAY_OF_MONTH);

        Calendar eventCal = Calendar.getInstance();
        eventCal.setTimeInMillis(regStart);

        switch (activeDateFilter) {
            case TODAY:
                return eventCal.get(Calendar.YEAR) == todayYear
                        && eventCal.get(Calendar.MONTH) == todayMonth
                        && eventCal.get(Calendar.DAY_OF_MONTH) == todayDay;
            case THIS_MONTH:
                return eventCal.get(Calendar.YEAR) == todayYear
                        && eventCal.get(Calendar.MONTH) == todayMonth;
            case CUSTOM:
                if (customDateFilterStartMs <= 0L) {
                    return true;
                }
                Calendar customDay = Calendar.getInstance();
                customDay.setTimeInMillis(customDateFilterStartMs);
                return eventCal.get(Calendar.YEAR) == customDay.get(Calendar.YEAR)
                        && eventCal.get(Calendar.MONTH) == customDay.get(Calendar.MONTH)
                        && eventCal.get(Calendar.DAY_OF_MONTH) == customDay.get(Calendar.DAY_OF_MONTH);
            default:
                return true;
        }
    }

    private void showFilterDialog() {
        if (!isAdded()) return;

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

        restoreDateSelection(radioAnyDate, radioToday, radioThisMonth, radioCustom, chooseDateValue);
        setDateSelectionHandlers(radioAnyDate, radioToday, radioThisMonth, radioCustom, chooseDateRow, chooseDateValue);

        Button applyButton = dialog.findViewById(R.id.btnApplyFilters);
        if (applyButton != null) {
            applyButton.setOnClickListener(v -> {
                applyFiltersAndUpdateUI();
                dialog.dismiss();
            });
        }

        dialog.show();

        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> dialog.dismiss());
        }
    }

    private void restoreDateSelection(RadioButton any, RadioButton today,
                                      RadioButton thisMonth, RadioButton custom,
                                      TextView chooseDateValue) {
        if (any == null || today == null || thisMonth == null || custom == null) return;

        any.setChecked(false);
        today.setChecked(false);
        thisMonth.setChecked(false);
        custom.setChecked(false);

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
                if (chooseDateValue != null && customDateFilterStartMs > 0L) {
                    chooseDateValue.setText(filterDateFormat.format(new Date(customDateFilterStartMs)));
                }
                break;
        }
    }

    private void setDateSelectionHandlers(RadioButton any, RadioButton today,
                                          RadioButton thisMonth, RadioButton custom,
                                          View chooseDateRow, TextView chooseDateValue) {
        if (any == null || today == null || thisMonth == null || custom == null) return;

        View.OnClickListener listener = v -> {
            any.setChecked(false);
            today.setChecked(false);
            thisMonth.setChecked(false);
            custom.setChecked(false);

            if (v == any) {
                any.setChecked(true);
                activeDateFilter = DateFilterOption.ANY_DATE;
            } else if (v == today) {
                today.setChecked(true);
                activeDateFilter = DateFilterOption.TODAY;
            } else if (v == thisMonth) {
                thisMonth.setChecked(true);
                activeDateFilter = DateFilterOption.THIS_MONTH;
            } else if (v == custom) {
                custom.setChecked(true);
                activeDateFilter = DateFilterOption.CUSTOM;
                openDatePicker(chooseDateValue);
            }

            if (chooseDateRow != null) {
                chooseDateRow.setVisibility(activeDateFilter == DateFilterOption.CUSTOM ? View.VISIBLE : View.GONE);
            }
        };

        any.setOnClickListener(listener);
        today.setOnClickListener(listener);
        thisMonth.setOnClickListener(listener);
        custom.setOnClickListener(listener);

        if (chooseDateRow != null) {
            chooseDateRow.setOnClickListener(v -> openDatePicker(chooseDateValue));
        }
    }

    private void openDatePicker(TextView chooseDateValue) {
        if (!isAdded()) return;

        final Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog picker = new DatePickerDialog(requireContext(),
                (DatePicker view, int y, int m, int d) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(y, m, d, 0, 0, 0);
                    chosen.set(Calendar.MILLISECOND, 0);
                    customDateFilterStartMs = chosen.getTimeInMillis();
                    if (chooseDateValue != null) {
                        chooseDateValue.setText(filterDateFormat.format(chosen.getTime()));
                    }
                }, year, month, day);
        picker.show();
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
        try {
            android.renderscript.RenderScript rs = android.renderscript.RenderScript.create(requireContext());
            android.renderscript.Allocation input = android.renderscript.Allocation.createFromBitmap(rs, bitmap);
            android.renderscript.Allocation output = android.renderscript.Allocation.createTyped(rs, input.getType());
            android.renderscript.ScriptIntrinsicBlur script = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs));
            script.setRadius(radius);
            script.setInput(input);
            script.forEach(output);
            output.copyTo(bitmap);
            rs.destroy();
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }

}

