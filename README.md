# CyPOS Sales (SalesMan) - Android Mobile POS

CyPOS Sales is a robust, enterprise-grade Android application designed for sales representatives and mobile POS operations. It streamlines the sales process with features like product cataloging, cart management, offline session handling, and advanced security layers.

## 🚀 Key Features

- **Advanced Security**: Integrated PIN and Biometric (Fingerprint/Face) authentication gate for fast app re-entry.
- **Smart Session Management**: Automatic idle-timeout locking and local session preservation for offline capability.
- **Sales & Order Management**:
  - Browse products with real-time stock levels.
  - Multi-unit support (PKT, Unit, etc.).
  - Add to cart and flexible checkout.
  - **Parked Carts**: Save current carts and resume later.
- **Customer Management**: Create and select customers for personalized sales.
- **Enterprise Logging**: 
  - Full API request/response tracking.
  - User activity and system lifecycle logging.
  - One-click log export for support and debugging.
- **Theming**: System-wide Dark Mode support.
- **Dynamic Configuration**: Easily update API endpoints within the app settings.

## 🛠 Tech Stack

- **Language**: Java
- **Networking**: Retrofit 2 & OkHttp 3
- **Database**: SQLite (via custom `Db` helper)
- **UI Components**: Material Components for Android
- **Auth**: BiometricPrompt API
- **Others**: Toasty (Toasts), Gson (JSON Parsing), Glide (Image Loading)

## 📦 Project Structure

```text
com.js.salesman
├── clients          # API Client & Retrofit Configuration
├── interfaces       # API Interface definitions
├── models           # POJO Data models
├── ui
│   ├── activities   # BaseActivity, MainActivity, AuthGate, etc.
│   └── fragments    # Home, Product, Cart, Settings, Reports, etc.
└── utils
    ├── managers     # Session, Settings, Log, and GPS Managers
    └── Db.java      # Local Database management
```

## ⚙️ Setup & Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/your-repo/SalesMan.git
   ```
2. **Open in Android Studio**:
   - Select "Open an existing project".
   - Wait for Gradle Sync to complete.
3. **Configure API**:
   - On first launch, use the `ConfigActivity` to set your server's base URL.
   - Alternatively, change it in **Settings > API Endpoint**.

## 🔐 Authentication Flow

The app uses a tiered authentication system:
1. **Initial Login**: Email/Password + Token generation.
2. **Fast Access**: Once logged in, the `AuthGateActivity` handles re-entry via Biometric or PIN.
3. **Session Auto-Lock**: Based on the timer set in Settings, the app will trigger `LockActivity` after inactivity.

## 📝 Logging System

The app maintains two primary log files in internal storage:
- `app_logs.txt`: Activity logs (User actions) and System logs (Lifecycle events).
- `api_logs.txt`: Full history of every API call including request headers/bodies and response payloads.

**How to export**: Go to **Settings > Debug Support > Export Logs**. This will generate a unified `.txt` file for sharing.

## 🤝 Contributing

Contributions make the open-source community an amazing place to learn, inspire, and create.
1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

## 📞 Support & API Access

The API source code for this project is currently private. If you are interested in learning how to build a system like this, or if you would like to request access to the API source code, feel free to reach out.

**Contact me via WhatsApp**:
[+254 757 146 341](https://wa.me/254757146341?text=Hi,%20I%27m%20interested%20in%20learning%20about%20the%20CyPOS%20Sales%20system)

---
*Developed with ❤️ by Tosby*
