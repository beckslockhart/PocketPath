# PocketPath

PocketPath is an Android budgeting application designed to help users record income and expenses, organise spending into categories, set monthly financial goals and monitor their budgeting progress.

The application was developed in Kotlin using Android Studio. It combines local Android functionality with Firebase online storage so that financial information can be stored and accessed through an online database.

---

## Main Features

- User registration and login
- Persistent login sessions
- Dashboard containing a monthly financial overview
- Recording of both income and expenses
- Custom budget categories with monthly limits
- Minimum and maximum monthly spending goals
- Transaction history for a user-selected period
- Total spending per category during the selected period
- Graphical spending summaries
- Monthly goal-performance visualisation
- Optional photograph, PNG or PDF attachments
- Path Progress score
- Achievement badges
- Firebase online data storage
- Input validation and explanatory error messages
- Logging for important application actions and errors

---

## Lecturer Feedback Implemented

The final application incorporates feedback received after the previous development phase.

### Persistent login

A user’s login session is retained after the application is closed. The user is taken directly to the dashboard when reopening PocketPath unless they previously logged out.

### Income and expenses

The transaction system supports both incoming and outgoing transactions, allowing PocketPath to provide a more complete view of the user’s finances.

### Numerical monthly-goal inputs

The original SeekBars were removed from the Monthly Goals screen. Users now enter exact numerical minimum and maximum amounts, providing greater accuracy and making larger amounts easier to set.

### Category totals

The Expense History screen displays the total amount spent in every category during the period selected by the user.

### Additional attachment types

Transactions can include supported attachments such as photographs, PNG images and PDF documents.

### Consistent interface

The login, dashboard, categories, goals and history screens use a consistent PocketPath colour palette, typography, cards, buttons and input controls.

---

## Original Features

Two features originating from PocketPath’s planning and design phase were implemented to distinguish the application from a basic expense tracker.

### Path Progress

Path Progress evaluates the user’s recent budgeting behaviour. It considers information such as recorded transactions, category limits and progress towards financial goals.

The feature provides a clear progress score and feedback to help the user understand their current budgeting habits.

### Achievement Badges

PocketPath awards achievement badges when users complete positive financial actions. Examples include recording transactions consistently, remaining within category limits and reaching a financial goal.

Unlocked achievements give users additional motivation to continue building healthy financial habits.

---

## Dashboard

The dashboard provides an overview of the logged-in user’s finances. Depending on the available data, it displays:

- Total income
- Total expenditure
- Current balance
- Minimum and maximum monthly goals
- Monthly goal progress
- Recent transactions
- Navigation to the application’s main features

Dashboard information updates when the user adds or changes financial data.

---

## Transactions

Users can record transactions with information such as:

- Transaction type
- Amount
- Description
- Category
- Date
- Start time
- End time
- Optional attachment

Input is validated before a transaction is saved. Invalid or missing values produce an explanatory error message instead of causing the application to crash.

---

## Budget Categories

Users can create, edit and delete budget categories. Each category contains:

- A category name
- A monthly spending limit
- The amount spent in the category
- The remaining or exceeded amount
- A visual progress indicator

Category progress uses colour to communicate the user’s position relative to the monthly limit.

---

## Monthly Goals

Users can enter an exact minimum and maximum monthly goal using numerical fields.

PocketPath validates that:

- Both amounts are valid numbers
- Both amounts are greater than zero
- The minimum amount is not greater than the maximum amount

Saved goals are displayed on the dashboard and included in the application’s financial-progress visualisations.

---

## Expense History and Period Filtering

The Expense History screen opens on the current calendar month. Users can select a different starting and ending date using the date controls.

Changing the selected period updates:

- The transaction list
- The total for the selected period
- Spending totals for every category
- Related spending visualisations

The application prevents the user from selecting an ending date before the starting date.

---

## Spending Graphs and Goal Visualisation

PocketPath presents financial information visually to make spending patterns easier to understand.

The application includes:

- Spending totals grouped by category
- A graph for the user-selected period
- A visual comparison between monthly spending and the user’s minimum and maximum goals
- Clear indicators showing whether the user is below, within or above the desired range

---

## Attachments

A transaction can optionally include a supported attachment.

Supported formats include:

- Camera photographs
- PNG image files
- PDF documents

Only the stored attachment reference is associated with the transaction. If an attachment is unavailable, PocketPath displays an appropriate message rather than crashing.

---

## Online Data Storage

PocketPath uses Firebase to provide online data storage. The Android application is registered with the PocketPath Firebase project through:

```text
app/google-services.json
```

Firebase allows application data to be stored online and demonstrated through the Firebase Console.

Room may also be used for supported local application data and offline Android persistence.

---

## Technology

PocketPath uses:

- Kotlin
- Android Studio
- XML layouts
- Material Components
- Firebase
- Room Database
- Kotlin coroutines
- Kotlin Flow
- RecyclerView
- ListAdapter and DiffUtil
- Gradle
- Git and GitHub
- GitHub Actions
- JUnit automated testing

---

## Project Structure

```text
app/src/main/java/com/example/pocketpath/
├── MainActivity.kt
├── DashboardActivity.kt
├── CategoriesActivity.kt
├── MonthlyGoalsActivity.kt
├── AddExpensesActivity.kt
├── ExpenseHistoryActivity.kt
├── ExpenseAdapter.kt
├── PhotoViewerActivity.kt
├── data/
│   ├── entity/
│   ├── dao/
│   └── database/
└── util/
```

Additional activities, adapters, Firebase classes and feature-specific files may be contained within the same package structure.

---

## Building and Running

### Requirements

- Android Studio
- JDK 21 or newer
- Android SDK platform required by the project
- Android device running Android 7.0/API 24 or newer
- Internet access for Firebase features

### Clone the repository

```bash
git clone https://github.com/beckslockhart/PocketPath.git
```

### Run the application

1. Open the cloned project in Android Studio.
2. Allow Gradle to finish syncing.
3. Confirm that `google-services.json` is inside the `app` folder.
4. Connect a physical Android device or start an emulator.
5. Select the `app` run configuration.
6. Click **Run**.

The final demonstration should be performed on a physical Android phone as required by the assignment brief.

### Command-line build

On Windows:

```bash
gradlew.bat assembleDebug
```

On macOS or Linux:

```bash
./gradlew assembleDebug
```

---

## APK

A debug APK can be generated in Android Studio using:

**Build → Generate App Bundles or APKs → Generate APKs**

The generated file is normally located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The APK should be tested on a physical Android device before submission.

---

## Automated Testing

Run the JVM unit tests with:

### Windows

```bash
gradlew.bat testDebugUnitTest
```

### macOS or Linux

```bash
./gradlew testDebugUnitTest
```

The test suite includes checks for:

| Test class | Purpose |
|---|---|
| `PeriodFilterTest` | Checks complete period boundaries, month changes, leap years and invalid date ranges |
| `CategoryTotalsTest` | Checks category ordering, uncategorised spending and period totals |
| `MoneyTest` | Checks South African rand formatting, rounding and category shares |
| `BitmapSamplingTest` | Checks image-scaling calculations and unreadable images |

The HTML test report is generated at:

```text
app/build/reports/tests/testDebugUnitTest/index.html
```

---

## Continuous Integration

The GitHub Actions workflow is stored at:

```text
.github/workflows/build.yml
```

The workflow runs when code is pushed or when a pull request is opened for the `main` branch. It:

1. Checks out the repository
2. Configures the required JDK
3. Runs the automated tests
4. Builds the debug APK
5. Uploads the APK and test report as workflow artifacts

This verifies that PocketPath can build successfully outside the developers’ local computers.

---

## Logging and Error Handling

PocketPath uses Android logging to record important events and failures. Database, authentication, Firebase and file-related operations use error handling to reduce crashes and provide understandable feedback.

Logs can be viewed through **Logcat** in Android Studio.

Sensitive information such as passwords should not be printed to the log.

---

## Team

PocketPath was developed as a four-person group project.

| Member | GitHub |
|---|---|
| Rebecca Lockhart | `beckslockhart` |
| Oliver Mann | `Dxrk-Reality` |
| Gift Zowa | `ClownBeatz` |
| Callum Schultz | `Callumschultz` |

Development was coordinated through GitHub using regular commits and pushes. Team members contributed to application functionality, interface development, Firebase integration, automated testing, documentation and final testing.

---

## Demonstration Video

The final demonstration-video link will be added here after the Part 3 video has been uploaded:

```text
PART 3 VIDEO LINK TO BE ADDED
```

The demonstration includes:

- The application running on a physical Android device
- Registration and login
- Persistent user sessions
- Income and expense transactions
- Budget categories
- Numerical monthly goals
- Period filtering and category totals
- Spending graphs and goal visualisation
- Firebase online data
- Attachments
- Path Progress
- Achievement badges

---

## Previous Demonstration

The Part 2 demonstration is available at:

https://youtu.be/Ibuf9NRiFfo

---

## Licence and Academic Use

PocketPath was developed as an academic group project. External libraries and technologies used by the application remain subject to their respective licences.