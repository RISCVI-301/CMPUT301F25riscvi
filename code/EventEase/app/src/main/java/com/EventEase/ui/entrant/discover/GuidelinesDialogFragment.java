package com.EventEase.ui.entrant.discover;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.EventEase.R;

/**
 * Dialog fragment for displaying event selection guidelines.
 * Shows event-specific rules and requirements in a modal dialog.
 */
public class GuidelinesDialogFragment extends DialogFragment {

    private static final String TAG = "GuidelinesDialog";
    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_BODY = "arg_body";

    public static void show(@NonNull FragmentManager fragmentManager,
                            @Nullable String title,
                            @Nullable String body) {
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }
        GuidelinesDialogFragment fragment = new GuidelinesDialogFragment();
        Bundle args = new Bundle();
        if (!TextUtils.isEmpty(title)) {
            args.putString(ARG_TITLE, title);
        }
        if (!TextUtils.isEmpty(body)) {
            args.putString(ARG_BODY, body);
        }
        fragment.setArguments(args);
        fragment.show(fragmentManager, TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, R.style.ThemeOverlay_EventEase_Dialog_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.entrant_dialog_guidelines, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String title = args != null ? args.getString(ARG_TITLE) : null;
        String body = args != null ? args.getString(ARG_BODY) : null;

        if (TextUtils.isEmpty(title) && getContext() != null) {
            title = getString(R.string.event_details_guidelines_title);
        }
        if (TextUtils.isEmpty(body) && getContext() != null) {
            body = getString(R.string.event_details_guidelines_body);
        }

        TextView contentView = view.findViewById(R.id.tvDialogContent);
        TextView titleView = view.findViewById(R.id.tvDialogTitle);
        Button okButton = view.findViewById(R.id.btnDialogOk);
        View card = view.findViewById(R.id.dialogCard);

        if (contentView != null && !TextUtils.isEmpty(body)) {
            contentView.setText(body);
        }
        if (titleView != null && !TextUtils.isEmpty(title)) {
            titleView.setText(title);
        }
        if (okButton != null) {
            okButton.setOnClickListener(v -> dismissAllowingStateLoss());
        }
        if (card != null) {
            card.setOnClickListener(v -> {
                // consume clicks so dialog only dismisses on explicit actions or outside taps
            });
        }

        View blur = view.findViewById(R.id.dialogBlurBackground);
        if (blur != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blur.setRenderEffect(RenderEffect.createBlurEffect(45f, 45f, Shader.TileMode.CLAMP));
            }
            blur.setOnClickListener(v -> dismissAllowingStateLoss());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() != null ? getDialog().getWindow() : null;
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(70);
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(0.25f);
            }
        }
    }
}
