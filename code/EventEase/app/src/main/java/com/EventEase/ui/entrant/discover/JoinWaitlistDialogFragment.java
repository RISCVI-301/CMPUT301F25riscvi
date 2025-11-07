package com.EventEase.ui.entrant.discover;

import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
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

import com.EventEase.R;

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
        return inflater.inflate(R.layout.entrant_waitlist_guidelines, container, false);
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

        // Capture screenshot and blur it for the background
        View blurBackground = view.findViewById(R.id.dialogBlurBackground);
        if (blurBackground != null) {
            if (getActivity() != null) {
                Bitmap screenshot = captureScreenshot();
                if (screenshot != null) {
                    Bitmap blurredBitmap = blurBitmap(screenshot, 25f);
                    if (blurredBitmap != null) {
                        blurBackground.setBackground(new BitmapDrawable(getResources(), blurredBitmap));
                    }
                }
            }
            
            // Fallback: Use RenderEffect for API 31+ if screenshot blur fails
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurBackground.getBackground() == null) {
                blurBackground.setRenderEffect(
                        RenderEffect.createBlurEffect(45f, 45f, Shader.TileMode.CLAMP));
            }
            
            blurBackground.setOnClickListener(v -> dismissAllowingStateLoss());
        }
    }
    
    private Bitmap captureScreenshot() {
        if (getActivity() == null) return null;
        
        try {
            android.view.View rootView = getActivity().getWindow().getDecorView().getRootView();
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
        if (bitmap == null || getActivity() == null) return null;
        
        try {
            // Scale down for better performance
            int width = Math.round(bitmap.getWidth() * 0.4f);
            int height = Math.round(bitmap.getHeight() * 0.4f);
            Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);
            
            RenderScript rs = RenderScript.create(getActivity());
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
            
            blurScript.setRadius(radius);
            blurScript.setInput(tmpIn);
            blurScript.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);
            
            rs.destroy();
            
            // Scale back up
            return Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), true);
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
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
        
        // Apply animations after dialog is shown
        View view = getView();
        if (view != null) {
            View blurBackground = view.findViewById(R.id.dialogBlurBackground);
            View card = view.findViewById(R.id.dialogCard);
            
            if (blurBackground != null && card != null) {
                android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_fade_in);
                android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.entrant_dialog_zoom_in);
                
                blurBackground.startAnimation(fadeIn);
                card.startAnimation(zoomIn);
            }
        }
    }
}
