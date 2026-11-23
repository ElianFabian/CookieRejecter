# CookieRejecter

## Why this fork exists

Some Android apps put **Accept all** on the first screen and bury refusal behind several menus and a long list of vendor switches. CookieRejecter was built to make it easier to refuse optional tracking and see what changed before saving.

The fork adds a tap-to-start review you can pause or stop, plus an activity log grouped by app that records switch changes. On supported Google forms, it checks both the data and vendor panels and asks you to confirm once it has verified the switches are off. CookieRejecter builds on [ConsentGuard by macbuildssys](https://github.com/macbuildssys/ConsentGuard).

## What it does

CookieRejecter detects supported cookie and tracking consent prompts and shows a floating **Reject optional choices** button. Tap it to run the refusal for that window.

- It works in Chrome, embedded WebViews, and native consent dialogues such as the Google consent form many apps show. What it can see varies between apps and websites.
- On a simple banner it taps the reject option, or the necessary only option.
- On forms with individual switches, it turns off the optional ones it can verify and prompts you before submitting. Switches marked as required or essential are left on.
- It reads banners in 17 languages at once, including German, French, Spanish, Kiswahili and several other African languages, so it also works on pages translated by the browser. See [languages](docs/LANGUAGES.md).
- It never accepts. On a screen that offers only accept or pay, it does nothing.
- It requires no internet permission. The in-app activity log stays in memory and is cleared when the process exits.

## Install a local build

Build with `./gradlew assembleDebug` on Linux or macOS, or `gradlew.bat assembleDebug` on Windows. Install `app/build/outputs/apk/debug/app-debug.apk` and open **CookieRejecter** to check the accessibility service status.

### Install on Android 13 and later

Android can block accessibility services in apps installed from outside Google Play. This is called a restricted setting. If CookieRejecter is greyed out in the accessibility list, or Android shows a "Restricted setting" message when you try to switch it on, allow it like this:

1. Open the **Settings** app.
2. Tap **Apps**.
3. Tap **CookieRejecter**. If you cannot find it, tap **See all apps** or **App info** first.
4. Tap the three dot menu (**More**) and choose **Allow restricted settings**.
5. Follow the on screen instructions.
6. Go back to **Settings > Accessibility > Installed apps > CookieRejecter** and turn it on.

If the **Allow restricted settings** option is not there, first try turning the service on once. Android then shows the restricted setting message, and the option appears in the app's menu afterwards.

The exact wording and location can vary between Android versions and device makers. Google describes the steps here: [Restricted settings on Android](https://support.google.com/android/answer/12623953).

## Using it

Once the service is on, tap the floating button when a supported prompt appears. Use **Pause** or **Stop** while CookieRejecter works. **Resume** rechecks the form so it can account for changes made during the pause. When the review is ready to submit, tap **Confirm choices** in the floating prompt. Open **Activity log** to see observed switch states, named changes, and forms that need review. On Android 11 or later, recognized Google forms are checked visually in both **Data preferences** and **Vendor preferences**. CookieRejecter keeps each screenshot in memory only long enough to read the switch pixels.

- If CookieRejecter cannot verify the required switch states, it leaves the form open for review.
- Add the **CookieRejecter** quick settings tile to pause it for 5 minutes. Tap it again to resume.
- **Apps to skip**: search installed apps by name or package, then use the switch beside the app icon. Changes take effect immediately. **Show skipped apps only** shows both your choices and apps CookieRejecter always skips for protection.
- **Banking, payment, email and chat apps** recognized by the built-in rules are skipped before their screen is read. The installed-app list marks them as always skipped. Screens that look like payment or sign-in forms are also left alone. See [docs/PRIVACY.md](docs/PRIVACY.md).
- **App language** changes CookieRejecter’s settings, floating controls, and activity messages. The Google review matches labels in the consent form’s own language. See [language coverage](docs/LANGUAGES.md) for supported forms and test details.
- **Completion alerts** opens Android’s notification settings. When enabled, a notification and sound tell you when a Google consent review has verified all vendor switches are off and is ready for you to confirm.
- It does not block ads or close popups. It only deals with cookie consent panels. For ad blocking, use uBlock Origin in Firefox, the Brave or DuckDuckGo browser, or set Private DNS in Android settings to `dns.adguard-dns.com`.
- To update, install a newer CookieRejecter APK signed with the same key, then check that the service is still switched on.
- To stop it, switch it off under **Settings > Accessibility > Installed apps > CookieRejecter**, or uninstall the app.

## Building

```
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`. It is signed when a `keystore.properties` file is present (see `app/build.gradle.kts`), and left unsigned otherwise. The build is pinned to JDK 17 in `gradle.properties`, because JDK 21 as the system default makes the Android build fail with a jlink error. Change that path if your JDK 17 lives somewhere else.

### Publishing on GitHub

The [release workflow](.github/workflows/release.yml) builds and signs an APK, then attaches it to a GitHub prerelease when you push a numeric tag such as `0.0.2`. The tag must match `versionName` in `app/build.gradle.kts`. Increase `versionCode` for each update so Android can install it over the previous release.

Set these repository secrets under **Settings → Secrets and variables → Actions**.

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded contents of the Android release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias of the signing key |
| `ANDROID_KEY_PASSWORD` | Signing key password. Use the keystore password if both are the same |

Keep a private backup of the keystore and reuse it for updates. After committing the workflow and version changes to your CookieRejecter repository, push the matching tag to that repository. For example, if `origin` points to your CookieRejecter repo and `versionName` is `0.0.2`:

```sh
git tag 0.0.2
git push origin 0.0.2
```

If a workflow fails before publication, open **Actions → Release APK → Run workflow**, select `main`, and enter the existing version tag.

## Checking the build

The tools in `tools/` are Kotlin scripts. They need the Kotlin command line compiler, version 1.9 or later (`sudo snap install --classic kotlin` on Ubuntu, or `sdk install kotlin` with SDKMAN). Run them from anywhere inside the project.

- `kotlinc -script tools/check_no_network.kts` confirms the app asks for no network permission.
- `kotlinc -script tools/check_trust.kts` confirms the promises in [docs/PRIVACY.md](docs/PRIVACY.md) still hold in the source.
- `kotlinc -script tools/check_languages.kts` checks the language packs for clashes.
- `kotlinc -script tools/check_safe_zone.kts` checks the bank and payment list.
- `kotlinc -script tools/build_english.kts` and `kotlinc -script tools/build_languages.kts` rebuild the wording in `app/src/main/assets/` after a change to the word lists.
- The Gradle build leaves out the dependency info block, so building the same source gives the same APK.

## Best effort

CookieRejecter works on a best effort basis. It can miss some banners and switches. Read the full [best effort statement](docs/BEST_EFFORT.md).

## License

CookieRejecter is distributed under the MIT License and retains macbuildssys’s original copyright and license notice. See [LICENSE](LICENSE).
