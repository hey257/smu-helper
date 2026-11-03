# SMU Helper

An Android client for the academic system at Southern Medical University (`zhjw.smu.edu.cn`). The web version works, but it was built for a desktop browser — dense tables, tiny links, no real mobile layout. This wraps the same endpoints in something you can use with a thumb.

Unofficial. Not affiliated with the school. Read the disclaimer at the bottom before using the automation parts.

[Download the APK](https://github.com/hey257/smu-helper/releases/latest) — Android 8.0 or newer, no Play Store listing. Signed with a debug key, so uninstall any earlier build first.

## What's in it

**Timetable.** A period grid instead of clock slots, so a class that runs periods 3–5 is one block, not three separate entries. Swipe left or right on the grid to move a week, or tap the date range to jump anywhere. Weeks with exams are marked. Self-study and other entries with no classroom get a green outline so you can tell at a glance which days you don't have to show up.

**Exams.** The school's own "exam schedule" page returns nothing for most students, so this reads exams out of the timetable's teaching-stage field (`考试（理论）` / `考试（实操）`) and converts week + weekday into a date. Each entry also gets the seat number and the 网考号 (online exam ID), which comes from the course-task list.

**Course evaluation.** Opens the questionnaire, fills it, submits it. Two rules the school enforces are handled: at least two star questions can't be full marks, and the total has to stay under the ceiling. Free-text questions are filled with a sentence you set in Settings.

**Class evaluation (随堂).** Pick a date, load that day's sessions, submit one at a time or all at once. Can also run unattended at a fixed time if you turn that on.

**Course selection (选课).** Shows open batches, the courses available in each, and what you've already taken. You can put courses on a watch list; when the round opens it submits them for you.

**Home screen widget.** A 4x3 "today" widget: each class with its period, room and start time, with finished ones faded out. It reads the local cache, so it renders instantly and doesn't need the app to be running.

## How login works

The app opens the school's own login page in a WebView. Whatever that page asks for — password, captcha, QR code, unified auth — you do it there, and the app never sees your password.

When the page finishes loading, the session cookie is read out of `CookieManager` and handed to OkHttp. Every request afterwards goes through the desktop `/new/...` JSON endpoints with that cookie.

The mobile API under `/dev-api/appapi` is not used. It authenticates with a token that only the official mobile login issues, so a web session cookie won't get you in there. That was one of the first dead ends in this project.

A worker hits an authenticated page every 15 minutes to keep the session alive. That is all it does — no foreground service, no permanent notification.

## Build

Android Studio Ladybug or newer, JDK 17.

Open the `android/` folder as the project (not the repository root) and run. `minSdk` is 26, `compileSdk`/`targetSdk` are 34.

`settings.gradle.kts` lists Aliyun mirrors ahead of Google and Maven Central. That's just because builds were painfully slow from a mainland connection; fallback repositories are declared right after, so it resolves fine anywhere else too.

## Known limits

- If the school logs you out, you log in again. Automating that would mean solving the login captcha, which I'm not going to write — it's a bot check, and working around it is the whole point of it existing.
- The selection *submit* call has been checked against the site's own JavaScript, not against a live selection round. Reading batches and course lists works; actually firing `add` has not been observed in the wild. The log line on that screen is there so a failure tells you something.
- Only fields this school returns are parsed. Other schools running the same software will need small changes.

## Disclaimer

Use it at your own risk. The app talks to the school's servers with your own credentials and does what you tell it to. Evaluation and selection submissions are write operations — some can't be undone, and the final selection round in particular often disables withdrawal. Check your school's rules on third-party clients first.

MIT licensed.
