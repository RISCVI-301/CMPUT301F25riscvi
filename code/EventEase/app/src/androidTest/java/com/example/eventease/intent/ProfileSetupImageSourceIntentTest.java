package com.example.eventease.intent;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.eventease.R;
import com.example.eventease.auth.ProfileSetupActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Intent/UI tests that verify the welcome profile-setup screen shows the blurred
 * image-source dialog that matches the My Account picker when the user chooses to
 * upload a profile picture. Covers US 01.02.01 (Provide personal information).
 */
@RunWith(AndroidJUnit4.class)
public class ProfileSetupImageSourceIntentTest {

    @Rule
    public ActivityScenarioRule<ProfileSetupActivity> activityRule =
            new ActivityScenarioRule<>(ProfileSetupActivity.class);

    @Test
    public void uploadPictureButton_showsBlurredImageSourceDialog() {
        onView(withId(R.id.btnUploadPicture)).perform(click());

        onView(withId(R.id.dialogCard)).check(matches(isDisplayed()));
        onView(withId(R.id.btnCamera)).check(matches(isDisplayed()));
        onView(withId(R.id.btnGallery)).check(matches(isDisplayed()));
    }
}

