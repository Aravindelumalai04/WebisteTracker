# Order Alert

Monitors https://asraaz.com/signup/generate/dashboard/orders.php and sends a
push notification whenever an order shows **Payment Type = paid** and
**Status = pending**. Checks every 15 minutes in the background.

## How it works

1. On first launch, the app shows the asraaz.com login page inside the app.
   Log in with your normal email/password, same as in a browser.
2. Once logged in, tap **Start Monitoring**. The app uses WorkManager to
   check the orders page every 15 minutes in the background, using the same
   login session (cookie) you created in step 1.
3. Whenever a new order appears with `paid` + `pending`, you get a
   notification. Each order only triggers one notification (no repeats).
4. If your session expires, you'll get a "session expired" notification —
   just reopen the app and log in again, then tap Start Monitoring.
5. **Check Now** runs an immediate check instead of waiting for the next
   15-minute cycle — useful for testing.

## Setting up the GitHub repo (no local Android Studio needed)

1. Create a new repository on GitHub (public or private, either works).
2. Upload all these files/folders exactly as they are (keep the folder
   structure — `app/`, `.github/`, `build.gradle`, `settings.gradle`, etc.)
   using the GitHub web UI "Add file → Upload files".
3. Go to the **Actions** tab of your repo. A workflow called "Build APK"
   will run automatically after the upload.
4. Once it finishes (green check), click into the workflow run, scroll to
   **Artifacts**, and download `OrderAlert-debug-apk`. Unzip it — inside is
   `app-debug.apk`.
5. Transfer `app-debug.apk` to your phone and install it (you may need to
   allow "install unknown apps" for whichever app you use to open the file).

## Important notes

- **Battery optimization**: Android may delay background checks if the app
  is battery-optimized. After installing, go to
  Settings → Apps → Order Alert → Battery, and set it to "Unrestricted" so
  the 15-minute checks stay reliable.
- **Column matching**: The app reads the table by matching column header
  text ("Order ID", "Payment Type", "Status", etc.), the same headers shown
  in your screenshot. If asraaz.com ever renames those columns, the matching
  logic in `OrderParser.kt` (`Constants.kt` for the header names) will need
  a one-line update.
- **Credentials**: Nothing is hardcoded in the code or committed to GitHub.
  Your login happens inside the in-app WebView, exactly like using a normal
  browser, and only the resulting session cookie is reused for background
  checks.
