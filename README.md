# SaaedPay SoftPOS SDK — Demo App

A working Android sample app showing how to integrate the SaaedPay SoftPOS SDK as a third-party
developer. Every call in this app maps 1:1 to what a merchant integration looks like in production.

---

## Project structure

```
DEMO/
├── app/
│   └── src/main/
│       ├── kotlin/sa/saaedpay/demo/
│       │   ├── DemoApp.kt          ← SDK initialization
│       │   ├── MainActivity.kt     ← Setup, purchase, reset flows
│       │   └── util/
│       │       └── JsonFormatter.kt ← Pretty-print SDK responses
│       ├── res/
│       │   ├── layout/activity_main.xml
│       │   └── values/{colors,strings,themes}.xml
│       └── AndroidManifest.xml
├── libs/
│   └── saaedpay-sdk.aar   ← SaaedPay SoftPOS SDK
├── app/build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 1. How to import the AAR

Copy the AAR file into the `libs/` folder at the project root (already done in this demo):

```
DEMO/libs/saaedpay-sdk.aar
```

---

## 2. Gradle configuration

### `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        flatDir { dirs("libs") }              // resolves the local AAR
        maven {  }
    }
}
```

### `app/build.gradle.kts`

```kotlin
android {
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // SaaedPay SoftPOS SDK
    implementation(name = "saaedpay-sdk", ext = "aar")

    // Required runtime components
    implementation("io.nearpay:nearpay-sdk:2.1.94")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

---

## 3. Initializing the SDK

Call `SaaedPay.init()` **once** in your `Application.onCreate()`. All subsequent calls are no-ops.

```kotlin
import sa.saaedpay.softpos.SaaedPay
import sa.saaedpay.softpos.api.config.SaaedPayConfig

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SaaedPay.init(
            context = this,
            config = SaaedPayConfig(
                enableLogging = BuildConfig.DEBUG
            )
        )
    }
}
```

Register it in `AndroidManifest.xml`:

```xml
<application android:name=".MyApp" .../>
```

---

## 4. Setup — `setup()`

Call `setup()` once per terminal session. Pass the terminal UUID from your SaaedPay dashboard.

```kotlin
SaaedPay.getInstance().setup(
    terminalUuid = "your-uuid-here",
    callback = object : SaaedPaySetupCallback {
        override fun onSuccess(result: SaaedPaySetupResult) {
            // result.terminal.title    — terminal display name
            // result.terminal.tid      — terminal ID
            // result.terminal.isSandbox
            // result.fromCache         — true = no network call was made
        }
        override fun onFailure(error: SaaedPayError) {
            // error.code, error.type (SaaedPayErrorType), error.message
        }
    }
)
```

**Caching:** If setup succeeded previously with the same UUID, calling `setup()` again returns the
cached result immediately without a network round-trip.

**Auth failure:** If `error.type == AUTHENTICATION_FAILED`, re-call `setup()` to re-authenticate.

---

## 5. Purchase — `purchase()`

`setup()` must complete successfully before calling `purchase()`.

```kotlin
val request = SaaedPayPurchaseRequest(
    amount = 1000L,                        // halalas (100 halalas = 1.00 SAR)
    customerReferenceNumber = "ORD-001",   // optional
    enableReceiptUi = true,
    enableReversal = true,
    finishTimeOut = 60L,
    isUiDismissible = true
)

SaaedPay.getInstance().purchase(
    request = request,
    callback = object : SaaedPayPurchaseCallback {
        override fun onSuccess(result: SaaedPayPurchaseResult) {
            // result.status           → "Payment Success"
            // result.transactionId    → unique transaction ID
            // result.approvalCode     → issuer approval code
            // result.amount           → authorized amount
            // result.currency         → e.g. "SAR"
            // result.receipts         → List<SaaedPayReceipt>
        }
        override fun onFailure(error: SaaedPayError) {
            when (error.type) {
                SaaedPayErrorType.PURCHASE_DECLINED     -> { /* declined */ }
                SaaedPayErrorType.AUTHENTICATION_FAILED -> { /* re-run setup() */ }
                SaaedPayErrorType.OPERATION_IN_PROGRESS -> { /* already running */ }
                else -> { /* generic failure */ }
            }
        }
    }
)
```

---

## 6. Reset — `reset()`

Clears all local state: JWT, terminal config, session. Call when logging out or switching terminals.

```kotlin
SaaedPay.getInstance().reset()
```

No callback — fire and forget. You must call `setup()` again before the next purchase.

---

## 7. Required permissions

The SDK merges these automatically. If you disabled manifest merging, add them manually:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 8. Min SDK / Target SDK

| Setting | Value |
|---------|-------|
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` | 34 (Android 14) |
| `compileSdk` | 34 |

---

## 9. Troubleshooting guide

### "SaaedPay is not initialized"
You called `SaaedPay.getInstance()` before `SaaedPay.init()`. Move `init()` to `Application.onCreate()`.

### "Terminal UUID must not be blank"
The UUID field was empty. Provide the UUID from your SaaedPay merchant dashboard.

### Build fails: "Unresolved reference: SaaedPay"
- Confirm `saaedpay-sdk.aar` is in `DEMO/libs/`
- Confirm `flatDir { dirs("libs") }` is in `settings.gradle.kts` (not in `build.gradle.kts`)
- Run `./gradlew clean` then rebuild

### Build fails: class not found at runtime
The AAR does not bundle its runtime dependencies. Make sure all entries in the **Required runtime
components** block of `build.gradle.kts` are present.

### Setup always returns from cache
This is correct behavior — calling `setup()` with the same UUID after a successful setup returns
the cached result. Call `reset()` first to force a fresh setup.

---

## 10. Common errors

| Code | Type | Fix |
|------|------|-----|
| 1000 | `NETWORK_UNAVAILABLE` | Check internet connection |
| 1001 | `BACKEND_ERROR` | Retry; check terminal UUID is valid |
| 1002 | `INVALID_TERMINAL_UUID` | UUID was blank |
| 2001 | `PAYMENT_APP_NOT_INSTALLED` | Payment service failed to install; device may not be compatible |
| 2002 | `AUTHENTICATION_FAILED` | Call `reset()` then `setup()` again |
| 2003 | `INVALID_STATUS` | Terminal failed pre-flight checks (NFC off, permissions missing, etc.) |
| 3000 | `PURCHASE_DECLINED` | Card declined by issuer |
| 3001 | `PURCHASE_REJECTED` | Transaction rejected |
| 4000 | `NOT_INITIALIZED` | Call `setup()` before `purchase()` |
| 4001 | `OPERATION_IN_PROGRESS` | Wait for the current operation to finish |

---

## 11. Release build notes

- Set `isMinifyEnabled = true` in your release build type for production.
- The SDK ships `consumer-rules.pro` which is auto-applied — no extra ProGuard config needed.
- Do not strip the `sa.saaedpay.softpos` package in custom ProGuard rules.

---

## 12. ProGuard notes

The SDK's consumer rules are applied automatically when you depend on the AAR. If you use a
custom ProGuard configuration, add:

```pro
-keep class sa.saaedpay.softpos.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
```

To preserve Gson serialization of response models:

```pro
-keep class sa.saaedpay.softpos.api.response.** { *; }
-keep class sa.saaedpay.softpos.domain.error.** { *; }
```
