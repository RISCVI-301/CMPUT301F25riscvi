package com.EventEase.auth;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
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

import com.EventEase.util.ToastUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.EventEase.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.regex.Pattern;

public class ForgotPasswordFragment extends Fragment {

    private EditText emailInput;
    private Button btnRecoverPassword;
    private ProgressBar progress;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    
    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.entrant_fragment_forgot_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        emailInput = view.findViewById(R.id.emailInput);
        btnRecoverPassword = view.findViewById(R.id.btnRecoverPassword);
        progress = view.findViewById(R.id.progress);

        btnRecoverPassword.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            
            if (email.isEmpty()) {
                ToastUtil.showShort(requireContext(), "Please enter your email address");
                return;
            }
            
            // Validate email format
            if (!isValidEmailFormat(email)) {
                ToastUtil.showShort(requireContext(), "Please enter a valid email address");
                return;
            }

            // Check if email exists in Firebase Auth and then send reset email
            validateAndSendResetEmail(email);
        });
    }

    /**
     * Validates email format
     */
    private boolean isValidEmailFormat(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Checks if email exists in Firestore users collection, then sends reset email if valid
     * Since Firestore queries are case-sensitive but emails should be matched case-insensitively,
     * we query all users and check in memory, or use Firebase Auth's fetchSignInMethodsForEmail
     * which handles case-insensitive email matching.
     */
    private void validateAndSendResetEmail(String email) {
        setLoading(true);
        
        String trimmedEmail = email.trim();
        
        // Use Firebase Auth's fetchSignInMethodsForEmail which handles case-insensitive matching
        // Note: Some Firebase projects may have email enumeration disabled, which can cause this to fail
        // even for valid emails. In that case, we'll fall back to trying to send the reset email.
        auth.fetchSignInMethodsForEmail(trimmedEmail)
            .addOnSuccessListener(signInMethods -> {
                // If signInMethods is not null, email exists
                // Even if methods list is empty, the email exists (might be a disabled account)
                // Proceed to send reset email
                sendPasswordResetEmail(trimmedEmail);
            })
            .addOnFailureListener(e -> {
                // Check the error message to determine if it's a "user not found" error
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                
                // Check for explicit "user not found" errors
                boolean isUserNotFound = errorMsg.contains("there is no user record") || 
                                        errorMsg.contains("no user record") ||
                                        errorMsg.contains("user not found") ||
                                        errorMsg.contains("invalid-user") ||
                                        (e instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException);
                
                if (isUserNotFound) {
                    // Definitely no user found
                    setLoading(false);
                    ToastUtil.showLong(requireContext(), "No account found with this email address");
                } else {
                    // For other errors (might be privacy settings preventing email enumeration),
                    // try sending the reset email anyway - Firebase will validate it
                    // If email doesn't exist, sendPasswordResetEmail will fail with appropriate error
                    android.util.Log.d("ForgotPasswordFragment", "fetchSignInMethodsForEmail failed (possibly due to privacy settings), trying to send reset email anyway: " + e.getMessage());
                    sendPasswordResetEmail(trimmedEmail);
                }
            });
    }
    
    /**
     * Sends password reset email to the validated email address
     */
    private void sendPasswordResetEmail(String email) {
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
                    if (msg.contains("no user record") || 
                        msg.contains("there is no user record") || 
                        msg.contains("invalid-user") ||
                        msg.contains("user not found")) {
                        errorMessage = "No account found with this email address";
                    } else if (msg.contains("network") || msg.contains("unavailable")) {
                        errorMessage = "Network error. Please check your connection";
                    } else if (msg.contains("badly formatted") || msg.contains("invalid-email")) {
                        errorMessage = "Invalid email format";
                    }
                }
                
                ToastUtil.showLong(requireContext(), errorMessage);
            });
    }

    private void showConfirmationDialog() {
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.entrant_dialog_password_reset_confirmation);
        
        // Set window properties for full screen blur
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
            layoutParams.dimAmount = 0f;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        
        // Capture screenshot and blur it for the background
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

        // Make the background clickable to dismiss
        View blurBackground = dialog.findViewById(R.id.dialogBlurBackground);
        if (blurBackground != null) {
            blurBackground.setOnClickListener(v -> {
                dialog.dismiss();
                navigateToLogin();
            });
        }

        Button btnOk = dialog.findViewById(R.id.btnDialogOk);
        if (btnOk != null) {
            btnOk.setOnClickListener(v -> {
                dialog.dismiss();
                // Navigate back to login
                navigateToLogin();
            });
        }

        dialog.show();
        
        // Apply animations after dialog is shown
        View card = dialog.findViewById(R.id.dialogCard);
        if (blurBackground != null && card != null) {
            android.view.animation.Animation fadeIn = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.entrant_dialog_fade_in);
            android.view.animation.Animation zoomIn = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.entrant_dialog_zoom_in);
            
            blurBackground.startAnimation(fadeIn);
            card.startAnimation(zoomIn);
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
        if (bitmap == null || requireContext() == null) return null;
        
        try {
            // Scale down for better performance
            int width = Math.round(bitmap.getWidth() * 0.4f);
            int height = Math.round(bitmap.getHeight() * 0.4f);
            Bitmap inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);
            
            RenderScript rs = RenderScript.create(requireContext());
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

