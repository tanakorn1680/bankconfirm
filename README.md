# Test Notification App

แอป Android สำหรับทดสอบว่า NotificationListenerService สามารถอ่าน Notification จากแอปอื่นได้จริงหรือไม่

แสดงข้อมูล Raw Notification บนหน้าจอเท่านั้น — ไม่มี Backend, Database, หรือการส่งข้อมูลออก Internet

---

## วิธีใช้งาน

### 1. Build APK จาก GitHub Actions

1. Push โค้ดขึ้น GitHub
2. ไปที่ **Actions** → **Build APK**
3. กด **Run workflow** → รอ Build เสร็จ (~3–5 นาที)
4. กด **test-notification-app-apk** ใต้ Artifacts เพื่อ Download

### 2. ติดตั้ง APK

```
adb install app-debug.apk
```

หรือ copy APK ไปที่โทรศัพท์แล้วเปิดผ่าน File Manager (ต้องเปิด Install Unknown Apps)

### 3. เปิดสิทธิ์ Notification Access

1. เปิดแอป **Test Notification App**
2. กปุ่ม **Open Notification Access Settings**
3. หา **Test Notification App** ในรายการ แล้วเปิดสลับ
4. กด Allow ในกล่องยืนยัน
5. กดปุ่ม Back กลับเข้าแอป

สถานะควรเปลี่ยนเป็น `Notification Access: ON ✓`

### 4. ทดสอบ

1. กด Home แล้วเปิดแอปใดก็ได้ที่ส่ง Notification (SMS, LINE, Email ฯลฯ)
2. รับ Notification
3. กลับเข้า **Test Notification App**
4. Notification จะปรากฏบนหน้าจอแบบ Raw ทันที

---

## Package Filter

ค่าเริ่มต้น `SHOW_ALL = true` → รับ Notification จากทุกแอป (เหมาะสำหรับทดสอบ Service)

เมื่อพิสูจน์แล้วว่า Service ทำงาน ให้เปลี่ยนใน `TestNotificationService.kt`:

```kotlin
const val SHOW_ALL = false   // เปลี่ยนจาก true → false

val TARGET_PACKAGES = setOf(
    "com.kasikorn.retail.mbanking.wap",   // K PLUS / KBank
    "com.truemoney.android.wallet"         // TrueMoney
)
```

### Package Name จริงของแอปธนาคาร

| แอป | Package Name |
|-----|-------------|
| K PLUS (KBank) | `com.kasikorn.retail.mbanking.wap` |
| TrueMoney Wallet | `com.truemoney.android.wallet` |

> ตรวจสอบ Package Name จริงได้จาก Play Store URL หรือใช้ `adb shell pm list packages`

---

## Privacy

- Notification ทั้งหมดเก็บเฉพาะใน Memory ของแอป
- ปิดแอป = ข้อมูลหายทั้งหมด
- ไม่มีการส่งข้อมูลออก Internet
- ไม่มี Database, Firebase, Analytics

---

## โครงสร้างโปรเจกต์

```
TestNotificationApp/
├── .github/workflows/build-apk.yml
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/testnotification/
│   │   ├── MainActivity.kt
│   │   ├── data/
│   │   │   ├── AppState.kt
│   │   │   └── NotificationLog.kt
│   │   └── service/
│   │       └── TestNotificationService.kt
│   └── res/
│       ├── layout/activity_main.xml
│       ├── layout/item_notification_log.xml
│       └── values/{strings,colors,themes}.xml
├── gradle/libs.versions.toml
└── README.md
```
