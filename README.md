# GlucoTrack CGM — Android (Jetpack Compose + BLE)

Android app that **simulates Continuous Glucose Monitoring (CGM)** and visualizes live readings with a polished UI, trend arrows, color zones, alerts, history graph, and CSV export for analysis. Optionally connects to an **ESP32-C3** over **Bluetooth Low Energy (BLE)**.

> Built by **Mohammed** (CSE-DS ’27). Focus: Data Engineering for IoT/Sensor streams.

---

## ✨ Features
- **Live readings** (simulation every 30s)  
- **Trend arrows** (↑ ↓ →) and **color zones**:  
  - < 70 mg/dL (Hypo)  
  - 70–180 mg/dL (In-range)  
  - > 180 mg/dL (Hyper)
- **Alerts** for low/high glucose
- **History graph** (MPAndroidChart) with pan/zoom
- **Stats** (avg / min / max / last 24h)
- **Export to CSV** for Python/R analysis
- **Optional BLE** read from ESP32-C3 (custom service/characteristic UUIDs)

---

## 🏗️ Tech Stack
- **Language/UI:** Kotlin, Jetpack Compose, ViewModel, Coroutines
- **Charts:** MPAndroidChart
- **Storage:** Room (or CSV)
- **Connectivity:** Android BLE APIs (optional)
- **Build:** Gradle (KTS)

---

## 📁 Project Structure
app/
src/main/java/...
ble/ # BLE manager (optional)
data/ # entities, repositories (Room/CSV)
ui/ # Compose screens & components
util/ # formatters, thresholds, helpers
assets/
screenshots/ # put PNGs here
demo.gif # short 10–20s screen recording
.gradle/ # (ignored)
.gitignore
build.gradle.kts
settings.gradle.kts
README.md

---

## 🚀 Quick Start
1. **Clone**
   ```bash
   git clone https://github.com/MohdAli21/GlucoTrack-CGM-Android.git
2. **Open in latest Android Studio.**
3. **Run on a device/emulator.**

---

🔗 **BLE (Optional)**

  Default mode uses simulated readings.
  To enable BLE, set your UUIDs in ble/BleManager.kt:

    val SERVICE_UUID = UUID.fromString("XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX")
    val CHAR_UUID    = UUID.fromString("YYYYYYYY-YYYY-YYYY-YYYY-YYYYYYYYYYYY")


  The app subscribes to notifications and parses bytes → mg/dL.

---

📤 **Data Export**
 
  From History screen, export readings to CSV:
    Timestamp, glucose (mg/dL), trend.
  Analyze with:
    Python: pandas, matplotlib
    R: readr, ggplot2

Example (Python):

    import pandas as pd
    df = pd.read_csv("glucose_history.csv", parse_dates=["timestamp"])
    print(df.describe())

---

🧪 **Testing Ideas**
      
      Simulated series with steady rise/fall → verify trend arrows.
      Boundary alerts at 69/70 and 180/181.
      Long session (>= 30 min) → history chart + CSV integrity.

---

🛣️ **Roadmap**

 BLE: auto-reconnect + battery level
 Cloud sync (Firestore/Supabase)
 Alert tuning & snooze
 Unit tests + CI (GitHub Actions)
 Multi-sensor / multiple profiles

---

📸 **Screenshots / Demo**
![GlucoTrack - 1](https://github.com/user-attachments/assets/4c333f05-90f8-4760-9afc-3118ee3f7d76)
![GlucoTrack - 2](https://github.com/user-attachments/assets/052a8649-11a3-42d6-900a-e2d97814a64c)
![GlucoTrack - 3](https://github.com/user-attachments/assets/dbdb0561-b3e5-457e-8756-97ec79f9f409)
![GlucoTrack - 4](https://github.com/user-attachments/assets/e7592d00-cf96-4f4e-b6f0-0c4c9f30b3da)
![GlucoTrack - 5](https://github.com/user-attachments/assets/0664b289-9cf0-42c9-8ba4-a541d0cd420f)
![GlucoTrack - 6](https://github.com/user-attachments/assets/5ca89f53-b90a-4a32-888d-711637d4a0f9)
![GlucoTrack - 7](https://github.com/user-attachments/assets/bff6548d-533a-452e-8548-eaddf43a6230)

--

GrowthX Off Campus Code: UMVZUH
