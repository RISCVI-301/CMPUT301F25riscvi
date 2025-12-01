package com.example.eventease.ui.entrant.profile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom view that draws a dark overlay with a transparent circle in the center.
 * Used for circle crop dialogs.
 */
public class CircleOverlayView extends View {
    private Paint overlayPaint;
    private Paint clearPaint;
    private int circleRadius;
    
    public CircleOverlayView(Context context) {
        super(context);
        init();
    }
    
    public CircleOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public CircleOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(0x80000000); // Semi-transparent black
        
        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setColor(0x00000000); // Transparent
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        
        setLayerType(View.LAYER_TYPE_SOFTWARE, null); // Required for PorterDuff
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Circle should be slightly smaller than the view
        circleRadius = Math.min(w, h) / 2 - 20;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw dark overlay
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
        
        // Clear the circle area
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        canvas.drawCircle(centerX, centerY, circleRadius, clearPaint);
    }
}

