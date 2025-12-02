## CMPUT 301 F25 – Team RISCVI – EventEase

### Team Members

| Name                    | CCID      | GitHub Username   |
|-------------------------|-----------|-------------------|
| Affan Nazir             | mnazir1   | M-Affan-Nazir     |
| Basvi Chunara           | basviyog  | basviyog          |
| Muhammad Salaar Butt    | msbutt1   | msbutt1           |
| Muhammad Zain Asad      | masad4    | MuhammadZain2005  |
| Sanika Verma            | sanika3   | SanikaVerma       |
| Swayam Sagar            | swayam2   | Swayam1129        |

---

### Project Description – EventEase

EventEase is a mobile application that connects organizers and entrants around real‑world events.  
Organizers can create events, manage registrations and waitlists, and automatically select entrants, while entrants can discover nearby events, join waitlists, track invitations, and receive real‑time notifications about their status.

The app focuses on a smooth first‑time experience (device + profile setup, location & notifications), robust event selection logic (including replacements and deadlines), and clear communication through push notifications and in‑app views. Organizers also get tools to visualize entrant locations on a map to support verification when needed.

---

### Features

#### Event Discovery & Participation
- **Discover Events**: Entrants can browse events with rich cards (title, date, location, poster).
- **Join Waitlists**: Join events that use waitlists; see up‑to‑date waitlist counts.
- **Invitations & Selection**:
  - Receive invitations when selected from the waitlist.
  - Accept or decline within organizer‑defined deadlines.
  - Automatic selection and sorry notifications via background services and Cloud Functions.

#### Organizer Tools
- **Create & Edit Events**:
  - Set capacity, sample size, registration window, and selection deadlines.
  - Configure event start time, guidelines, and notes.
- **Manage Entrants**:
  - View waitlisted, selected, admitted, cancelled, and non‑selected entrants.
  - Run automatic selection and manual replacements while respecting capacity/sample size.
- **Entrant Location Map**:
  - View waitlisted entrants on a Google Map (where entrants have granted location permission).

#### Permissions & Onboarding
- **Profile & Device Setup**:
  - First‑time flow for profile picture and basic details.
  - Device‑based authentication to keep users signed in.
- **Location & Notifications**:
  - Custom explanation screen for why location and notifications are needed.
  - Requests runtime permissions in a clear, staged flow.

#### Notifications
- **Selection / Replacement Notifications**:
  - Push notifications when an entrant is selected or replaced.
  - Tapping a selection notification opens the invitation detail page with accept/decline.
- **Sorry Notifications**:
  - Sent to non‑selected entrants before the event starts or when deadlines are missed.
- **Grouped Notification Handling** via a centralized `NotificationHelper` and Cloud Functions.

---

### Technology Stack

- **Platform**: Android (Java, AndroidX, Material Components)
- **Backend / Data**:
  - Firebase Authentication
  - Cloud Firestore
  - Firebase Storage
  - Cloud Functions for Firebase (Node.js)
- **Notifications**:
  - Firebase Cloud Messaging (FCM)
  - Android notification channels & grouping
- **Maps & Location**:
  - Google Maps SDK for Android
  - Fused Location Provider API

---

### Key Screens & Flows

- **Main Activity**:
  - Hosts entrant navigation (Discover, My Events, Account).
  - Handles deep links and notification navigation (regular event vs invitation view).

- **Auth & Onboarding**:
  - Welcome / Login / Signup flow.
  - Profile picture upload and device/location permission flow.

- **My Events**:
  - Shows upcoming, previous, and invited events for the current entrant.
  - Opens `EventDetailActivity` with or without invitation context.

- **Event Detail**:
  - Displays event info, poster, guidelines, and dynamic waitlist count.
  - Shows accept/decline buttons when opened with an invitation.

- **Organizer Views**:
  - Organizer event list, waitlist management, selection helpers, entrant location map.

---

### Setup Instructions

#### 1. Firebase Configuration

1. **Create Firebase Project**
   - Go to the Firebase Console.
   - Create a new project or reuse an existing one.

2. **Enable Products**
   - Authentication (Email/Password).
   - Firestore (Native mode).
   - Storage.
   - Cloud Functions (for full notification behavior).

3. **Android App Registration**
   - In Project Settings → Your Apps → Android:
     - Package name: `com.example.eventease`.
   - Download the generated `google-services.json`.
   - Place it at: `code/EventEase/app/google-services.json`.

4. **Firestore & Storage Rules**
   - Use the provided `firestore.rules` and `storage.rules` in `code/EventEase/`.
   - In the Firebase Console, open Rules and paste the contents of those files (if required by the assignment spec).

5. **Cloud Functions (Optional but recommended)**
   - From the repo root:
     ```bash
     cd code/EventEase/functions
     npm install
     # To deploy (if you have Firebase CLI configured):
     # firebase deploy --only functions
     ```

#### 2. Google Maps SDK Setup

The app uses Google Maps to display entrant locations for organizers. The API key is **not** committed; it is read from `local.properties`.

1. **Get your debug SHA‑1**
   ```bash
   keytool -list -v \
     -keystore ~/.android/debug.keystore \
     -alias androiddebugkey \
     -storepass android -keypass android | grep SHA1
   ```

2. **Create / Configure Maps API Key**
   - Go to Google Cloud Console.
   - Ensure **“Maps SDK for Android”** is enabled.
   - Create or edit an API key under **APIs & Services → Credentials**.
   - Add an Android application restriction:
     - Package: `com.example.eventease`
     - SHA‑1: your debug SHA‑1 from step 1.
   - Optionally restrict the key to **Maps SDK for Android** only.

3. **Add API Key to `local.properties`**
   - Open `code/EventEase/local.properties`.
   - Ensure it contains:
     ```properties
     sdk.dir=/path/to/your/Android/sdk
     google.maps.api.key=YOUR_GOOGLE_MAPS_API_KEY_HERE
     ```

#### 3. Running the App

1. Open the project in Android Studio from the repo root (`CMPUT301F25riscvi`).
2. Let Gradle sync complete.
3. Select the `EventEase` app module.
4. Connect an Android device or start an emulator.
5. Click **Run** to build and install the app.

---

### Documentation

- [**Wiki**](https://github.com/RISCVI-301/CMPUT301F25riscvi/wiki)

- [**UI Mockups**](https://github.com/RISCVI-301/CMPUT301F25riscvi/wiki/Updated-UI-Mockup-(PART-4))

- [**UI Storyboard**](https://github.com/RISCVI-301/CMPUT301F25riscvi/wiki/Updated-Storyboard-Sequence-(PART-4))

- [**Sprint Planning** ](https://github.com/RISCVI-301/CMPUT301F25riscvi/wiki/Sprint-Planning-and-Review)

- [**UML**](https://github.com/RISCVI-301/CMPUT301F25riscvi/wiki/Final-UML-Class-Diagram-Based-on-CodeBase)

