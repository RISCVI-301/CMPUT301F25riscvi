package com.EventEase.util;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.EventEase.R;

/**
 * Utility class for showing custom toast messages with logo
 */
public class ToastUtil {
    
    /**
     * Shows a custom toast with the app logo
     * @param context The context to show the toast in
     * @param message The message to display
     * @param duration Toast.LENGTH_SHORT or Toast.LENGTH_LONG
     */
    public static void showToast(Context context, String message, int duration) {
        if (context == null) return;
        
        // Inflate custom toast layout
        LayoutInflater inflater = LayoutInflater.from(context);
        View toastView = inflater.inflate(R.layout.entrant_custom_toast, null);
        
        // Set message text
        TextView textView = toastView.findViewById(R.id.toast_message);
        if (textView != null && message != null) {
            textView.setText(message);
        }
        
        // Load logo into image view
        ImageView logoView = toastView.findViewById(R.id.toast_logo);
        if (logoView != null) {
            Glide.with(context)
                .load(R.drawable.entrant_logo)
                .into(logoView);
        }
        
        // Create and show toast
        Toast toast = new Toast(context);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 100);
        toast.setDuration(duration);
        toast.setView(toastView);
        toast.show();
    }
    
    /**
     * Shows a short toast with logo
     */
    public static void showShort(Context context, String message) {
        showToast(context, message, Toast.LENGTH_SHORT);
    }
    
    /**
     * Shows a long toast with logo
     */
    public static void showLong(Context context, String message) {
        showToast(context, message, Toast.LENGTH_LONG);
    }
    
    /**
     * Shows a toast with logo using string resource ID
     */
    public static void show(Context context, int messageResId, int duration) {
        if (context == null) return;
        String message = context.getString(messageResId);
        showToast(context, message, duration);
    }
}

