# PocketPath

An offline budget tracker for Android. PocketPath lets a user record what they
spend, group it into budget categories, set monthly spending goals, and look
back over any period to see where the money actually went.

Everything is stored locally on the device with Room, so the app works with no
network connection and no account beyond the local login.

---

## Features

| Feature | Where it lives |
|---|---|
| Register and log in with a username and password | `MainActivity` |
| Dashboard showing the month's spending against your goals | `DashboardActivity` |
| Create, edit and delete budget categories with a monthly limit | `CategoriesActivity` |
| Set a minimum and maximum monthly spending goal | `MonthlyGoalsActivity` |
| Record an expense with date, start and end times, description, category and an optional photograph | `AddExpensesActivity` |
| List every expense in a period you choose | `ExpenseHistoryActivity` |
| See the total spent in each category over that same period | `ExpenseHistoryActivity` |
| Open an expense photograph full screen from the history list | `PhotoViewerActivity` |

### Choosing a period

The expense history screen opens on the current calendar month. Tapping either
date field opens a picker, and the list, the per category totals and the period
total all update together.

A period always covers whole days at both ends. Expenses are stored at midnight
on the day they happened, so an end bound taken from the current time of day
would silently hide anything recorded on the closing day of the period. Picking
an end date earlier than the start date is reported on the field rather than
quietly showing an empty list.

### Photographs

Photographs are captured through the camera into the app's own private storage
and shared with the camera app through a `FileProvider`. Only the URI is kept in
the database. Because the files live inside the app's own directory, no storage
permission is required to read them back.

Photographs are decoded scaled down to the size they are actually drawn at, so
a multi megapixel camera image does not stutter the history list while it
scrolls. If a photograph's file has been removed from the device since the
expense was saved, the viewer says so instead of showing an empty screen.

---

## Building and running

**Requirements**

- Android Studio (the version bundled with JDK 21 or newer)
- Android SDK platform 37
- A device or emulator running Android 7.0 (API 24) or newer

**Steps**

1. Clone the repository:

   ```bash
   git clone https://github.com/beckslockhart/PocketPath.git
   ```

2. Open the folder in Android Studio and let Gradle sync. Android Studio writes
   the `local.properties` file pointing at your SDK; it is deliberately not
   committed, because that path differs on every machine.

3. Run the `app` configuration on a device or emulator.

From the command line:

```bash
./gradlew assembleDebug
```

---

## Testing

The automated tests run on the JVM, so they need no emulator and run in CI on
every push.

```bash
./gradlew testDebugUnitTest
```

The suite covers the rules that are easy to get wrong and expensive to catch by
hand:

| Test class | What it covers |
|---|---|
| `PeriodFilterTest` | Period boundaries — that a period includes its whole opening and closing day, spans month ends, handles a leap year February, and rejects an end date before the start date |
| `CategoryTotalsTest` | Per category totals — ordering by spend, dropping categories with nothing spent, keeping uncategorised spending, and adding up the period total |
| `MoneyTest` | Rand formatting and rounding, and the share each category makes up of the period total |
| `BitmapSamplingTest` | The shrink factor used when decoding photographs, including unreadable images |

An HTML report is written to
`app/build/reports/tests/testDebugUnitTest/index.html` after a run, and is also
uploaded as an artifact by the CI workflow.

---

## Continuous integration

`.github/workflows/build.yml` runs on every push and pull request to `main`, and
can also be started by hand from the Actions tab. It:

1. Checks out the repository on a clean Ubuntu runner
2. Sets up JDK 21 — matching the toolchain pinned in
   `gradle/gradle-daemon-jvm.properties`
3. Runs the unit tests
4. Builds the debug APK
5. Uploads the test report and the APK as downloadable artifacts

The test report is uploaded even when the tests fail, so a red run can be read
without reproducing it locally.

---

## Project structure

```
app/src/main/java/com/example/pocketpath/
├── MainActivity.kt              Login and registration
├── DashboardActivity.kt         Monthly overview and navigation
├── CategoriesActivity.kt        Budget categories and limits
├── MonthlyGoalsActivity.kt      Minimum and maximum monthly goals
├── AddExpensesActivity.kt       Recording an expense
├── ExpenseHistoryActivity.kt    Period filtering and category totals
├── ExpenseAdapter.kt            Expense history list rows
├── PhotoViewerActivity.kt       Full screen photograph
├── data/
│   ├── entity/                  User, Category, Expense, MonthlyGoal
│   ├── dao/                     Room queries
│   └── database/                PocketPathDatabase
└── util/
    ├── PeriodFilter.kt          Period boundary rules
    ├── CategoryTotals.kt        Per category totals for a period
    ├── Money.kt                 Rand formatting
    ├── PhotoLoader.kt           Reading photographs from the file provider
    └── BitmapSampling.kt        Photograph shrink factor
```

### Data model

Four Room tables, all scoped to the logged in user:

- **users** — username and password, with a unique index on the username
- **categories** — name and monthly limit, unique per user
- **expenses** — amount, description, date, start and end times, optional
  photograph URI, linked to a user and a category
- **monthly_goals** — minimum and maximum monthly spend, one row per user

Deleting a user cascades to their data. Deleting a category leaves its expenses
in place with no category, so spending history is never lost; those expenses
appear as *Uncategorised* in the totals.

### Technology

- Kotlin
- Room for local persistence, with KSP
- Kotlin coroutines and `Flow`, so screens update themselves when data changes
- Material Components views with XML layouts
- RecyclerView with `ListAdapter` and `DiffUtil` for the expense history

---

## Team

This app was built as a group project. The work was split as follows:

- **Project structure, Room database, navigation, login and dashboard**
- **Budget categories and monthly minimum/maximum goals**
- **Add Expense screen, including date, times, category, description and photograph**
- **Expense history, period filtering, category totals, photo viewing, automated tests, README and GitHub Actions**

## Demonstration video

*(Link to the demonstration video to be added before submission.)*
