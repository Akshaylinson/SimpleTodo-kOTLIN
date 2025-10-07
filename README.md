:

📝 SimpleTodo — Android To-Do App with Voice & Smart Reminders

A clean and modern to-do list app built with Kotlin and Android Studio, featuring:

Voice input

Local JSON-based storage

Smart date/time reminders using AlarmManager

Undo for deletions

Empty-state animation

Priority-based sorting

🚀 Features

✅ Add / Edit / Delete todos
✅ Set date & time reminders
✅ Voice input using Google Speech API
✅ Drag & drop reordering
✅ Mark important/starred items
✅ Persistent local storage (via SharedPreferences + Gson)
✅ Light & modern UI (Material Design 3)

🧩 Tech Stack

Language: Kotlin

UI: XML (Material Components)

Persistence: Gson + SharedPreferences

Notifications: AlarmManager + BroadcastReceiver

Min SDK: 21 (Android 5.0)

Target SDK: 34

📂 Project Structure
SimpleTodo/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/akshay/simpletodo/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── Todo.kt
│   │   │   │   ├── TodoAdapter.kt
│   │   │   │   ├── AlarmReceiver.kt
│   │   │   │   └── (helper utils)
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   ├── item_todo.xml
│   │   │   │   ├── values/
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── themes.xml
│   │   │   │   │   └── styles.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
└── README.md

🛠️ Setup & Run

Clone this repo

git clone https://github.com/<your-username>/SimpleTodo-App.git
cd SimpleTodo-App


Open in Android Studio

Wait for Gradle sync

Click ▶️ Run to build & install on your device

🧠 Future Enhancements

Add cloud sync (Firebase)

Add dark mode support

Export / import tasks

Material You theming

👨‍💻 Author

Akshay [Codeless Technologies]
🚀 GitHub Profile

📄 License

This project is licensed under the MIT License – feel free to use and modify!

✅ Next Steps for You

Save your MainActivity.kt, Todo.kt, and layouts.

Add the .gitignore file.

Commit and push to GitHub.
