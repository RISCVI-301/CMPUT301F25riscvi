package com.EventEase.ui.entrant.discover;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.example.eventease.R;

/**
 * Full-screen confirmation dialog shown after an entrant taps Join Waitlist.
 */
public class JoinWaitlistDialogFragment extends DialogFragment {

    private static final String TAG = "JoinWaitlistDialog";

    public static void show(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) == null) {
            new JoinWaitlistDialogFragment().show(fragmentManager, TAG);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, R.style.ThemeOverlay_EventEase_Dialog_Fullscreen);
        setCancelable(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.waitlist_guidelines, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Button okButton = view.findViewById(R.id.btnDialogOk);
        okButton.setOnClickListener(v -> dismissAllowingStateLoss());

        View card = view.findViewById(R.id.dialogCard);
        if (card != null) {
            card.setOnClickListener(v -> { /* consume card taps */ });
        }

        View blurBackground = view.findViewById(R.id.dialogBlurBackground);
        if (blurBackground != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBackground.setRenderEffect(
                        RenderEffect.createBlurEffect(45f, 45f, Shader.TileMode.CLAMP));
            }
            blurBackground.setOnClickListener(v -> dismissAllowingStateLoss());
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
