package com.EventEase.auth;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.eventease.R;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordFragment extends Fragment {

    private EditText emailInput;
    private Button btnRecoverPassword;
    private ProgressBar progress;
    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_forgot_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        auth = FirebaseAuth.getInstance();

        emailInput = view.findViewById(R.id.emailInput);
        btnRecoverPassword = view.findViewById(R.id.btnRecoverPassword);
        progress = view.findViewById(R.id.progress);

        btnRecoverPassword.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            
            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your email address", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!email.contains("@")) {
                Toast.makeText(requireContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            sendPasswordResetEmail(email);
        });
    }

    private void sendPasswordResetEmail(String email) {
        setLoading(true);
        
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener(aVoid -> {
                setLoading(false);
                showConfirmationDialog();
            })
            .addOnFailureListener(e -> {
                setLoading(false);
                String errorMessage = "Failed to send reset email";
                
                // Parse Firebase errors
                if (e.getMessage() != null) {
                    String msg = e.getMessage().toLowerCase();
                    if (msg.contains("no user record")) {
                        errorMessage = "No account found with this email address";
                    } else if (msg.contains("badly formatted")) {
                        errorMessage = "Invalid email format";
                    } else if (msg.contains("network")) {
                        errorMessage = "Network error. Please check your connection";
                    }
                }
                
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            });
    }

    private void showConfirmationDialog() {
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_password_reset_confirmation);
        
        // Apply blur to background
        if (dialog.getWindow() != null) {
            // Capture and blur the background
            View rootView = getView();
            if (rootView != null) {
                Bitmap blurredBitmap = captureAndBlurView(rootView);
                if (blurredBitmap != null) {
                    BitmapDrawable blurredDrawable = new BitmapDrawable(getResources(), blurredBitmap);
                    dialog.getWindow().setBackgroundDrawable(blurredDrawable);
                } else {
                    // Fallback to transparent with dim
                    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }
            } else {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            
            // Add dim overlay
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0.3f); // Light dim since we have blur
            
            // Set dialog to fill screen
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setAttributes(layoutParams);
        }

        Button btnOk = dialog.findViewById(R.id.btnDialogOk);
        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            // Navigate back to login
            navigateToLogin();
        });

        dialog.show();
    }

    private Bitmap captureAndBlurView(View view) {
        try {
            // Create bitmap from view
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            
            // Apply blur
            return blurBitmap(bitmap, 25f);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap blurBitmap(Bitmap bitmap, float radius) {
        try {
            // Create output bitmap
            Bitmap outputBitmap = Bitmap.createBitmap(bitmap);
            
            // Use RenderScript for blur
            RenderScript rs = RenderScript.create(requireContext());
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            
            Allocation tmpIn = Allocation.createFromBitmap(rs, bitmap);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
            
            blurScript.setRadius(radius);
            blurScript.setInput(tmpIn);
            blurScript.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);
            
            // Cleanup
            rs.destroy();
            
            return outputBitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    private void navigateToLogin() {
        try {
            if (isAdded() && getView() != null) {
                NavHostFragment.findNavController(this).navigate(R.id.action_forgotPassword_to_login);
            }
        } catch (Exception e) {
            // Fallback: just pop back stack
            if (isAdded()) {
                NavHostFragment.findNavController(this).popBackStack();
            }
        }
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRecoverPassword.setEnabled(!loading);
        emailInput.setEnabled(!loading);
    }
}

