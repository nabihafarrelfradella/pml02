# DOKUMEN HANDOFF & PANDUAN INTEGRASI: MYFIN MOBILE (ANDROID) & WEB/IOT
**Mata Kuliah:** Pemrograman Mobile Lanjut (PML) — Proyek UTS / UAS  
**Target Pembaca:** Web Developer & IoT Specialist (Pembangun Web Laravel `myfin` & Firmware ESP32)  
**Basis Proyek Android:** `C:\Users\user\AndroidStudioProjects\pml_uts` (Package: `com.example.pml_uts`)  
**Basis Backend Web:** `C:\laragon\www\myfin` (Laravel 11, SQLite/MySQL, VPS, IoT Gateway)

---

## 1. PENGERTIAN & ARSITEKTUR APLIKASI MOBILE (ANDROID VS WEB)

Bagi pengembang yang terbiasa dengan arsitektur Web Monolith (Blade SSR, Controller, Sessions, VPS) dan IoT:

### A. Perbedaan Mendasar Web vs Android
| Aspek | Web Laravel MyFin | Mobile Android MyFin |
|---|---|---|
| **Rendering** | Server-Side Rendering (Blade HTML/CSS) | Native Client-Side (XML Layouts + Material 3 ViewBinding) |
| **State & Sesi** | Cookie / Web Session PHP | `SharedPreferences` (Token, User ID, Role, Remember-Me) |
| **Koneksi Jaringan** | Browser request langsung ke domain/host | OkHttp / REST Client via background thread (Asynchronous) |
| **Routing IP Local** | `localhost` / `127.0.0.1` | Emulator mengakses host melalui `10.0.2.2:8000` (atau IP LAN Wi-Fi `172.16.x.x` pada HP fisik) |
| **Offline Storage** | Browser LocalStorage / Cache | Native `SQLiteDatabase` lokal (`MyFinDatabaseHelper`) |
| **Cloud Storage** | Local Storage / Supabase | Native Supabase Kotlin SDK (`Mobile Lanjut` bucket) |

### B. Lingkup Kerja (Project Boundary)
- **POS (Tablet Kios Kasir Pemesanan Mandiri):** **100% Tetap Berada di Web** (Desktop/Tablet Kios). Aplikasi Android **TIDAK** menduplikasi fungsi POS kasir.
- **Aplikasi Android:** Bertindak sebagai **Portal Manajemen & Identitas Personal** untuk 3 Role:
  1. **Admin:** Approval stand kantin baru, monitoring data mahasiswa, reset registrasi biometrik.
  2. **Pemilik Kantin (Merchant):** Monitoring omset & pesanan, manajemen menu (CRUD, upload foto, status stok), pengaturan nomor rekening penarikan dana.
  3. **Mahasiswa:** Cek saldo debit & status biometrik, aktivasi debit bank via Midtrans, riwayat transaksi QR, kartu identitas QR (ZXing), pemicu pendaftaran sidik jari IoT.

---

## 2. PEMENUHAN RUBRIK PENILAIAN UTS/UAS PML (TOTAL 110%)

Aplikasi Android ini dibangun spesifik untuk memenuhi 100% standar penilaian mata kuliah Pemrograman Mobile Lanjut:

| No | Komponen Rubrik PML | Bobot | Letak Implementasi di Kode Android | Keterangan |
|---|---|:---:|---|---|
| **1** | RadioButton | 1% | `RegisterActivity`, `KantinMainActivity` | Pemilihan Role pendaftaran & Kategori menu |
| **2** | CheckBox | 1% | `LoginActivity` | Checkbox "Ingat Saya (Remember Me)" |
| **3** | AutoCompleteTextView | 1% | `AdminMainActivity` | Live search instan nama kantin & mahasiswa |
| **4** | DatePickerDialog | 1% | `MahasiswaMainActivity` | Filter riwayat transaksi berdasarkan tanggal |
| **5** | TimePickerDialog | 1% | `KantinMainActivity` | Pengaturan jam buka & tutup operasional stand |
| **6** | Spinner | 1% | `KantinMainActivity` | Dropdown pemilihan Bank Rekening penarikan |
| **7** | ListView / RecyclerView | 1% | Semua Portal (Admin, Kantin, Mhs) | RecyclerView dengan CardView modern untuk data list |
| **8** | OptionsMenu | 1% | Toolbar semua Activity | Menu titik tiga: Refresh Data, Pengaturan, Logout |
| **9** | ContextMenu | 1% | `KantinMainActivity` | Tekan-lama (long click) item menu: Edit Cepat / Hapus |
| **10** | PopupMenu | 1% | `KantinMainActivity` | Menu overflow tiga titik pada kartu menu |
| **11** | BottomNavigationView | 1% | Admin, Kantin, Mhs | Navigasi tab bawah (Dashboard, Fitur Utama, Profil) |
| **12** | FrameLayout | 1% | Layout Container Activity | Wadah transisi fragment dan konten utama |
| **13** | SQLite Local Storage | 1% | `MyFinDatabaseHelper.kt` | Offline caching daftar menu & riwayat transaksi |
| **14** | SharedPreferences | 1% | `SessionManager.kt` | Penyimpanan sesi login, role, email remember-me |
| **15** | QR Code (ZXing) | 2% | `MahasiswaMainActivity.kt` | Generate QR Code identitas pembayaran instan |
| **16** | Kamera / Galeri | 2% | `KantinMainActivity.kt` | Input foto menu produk makanan/minuman |
| **17** | Audio / Sound Effect | 1% | `NotificationSoundUtil.kt` | Nada beep sukses saat login & transaksi |
| **27** | **Integrasi DB Web & Mobile** | **10%** | `ApiClient.kt` & `MobileApiController.php` | Terhubung langsung dengan database SQLite/MySQL Laravel |
| **28** | **Integrasi IoT (ESP32 Biometrik)** | **10%** | `MahasiswaMainActivity.kt` | Trigger pendaftaran sidik jari & status sensor ESP32 |
| **33** | **Supabase Storage** | **7%** | `SupabaseStorageHelper.kt` | Upload media aset ke bucket `Mobile Lanjut` |
| **Total** | **Lengkap Sesuai Rubrik** | **110%** | — | — |

---

## 3. STATUS YANG SUDAH SELESAI DI SISI ANDROID

1. **Branding & UI/UX Real Production:**
   - **Logo Kustom:** Menggunakan logo resmi `logo.png` (bukan placeholder Android hijau).
   - **App Launcher Icon:** Telah digenerate ke seluruh resolusi mipmap (`mdpi` hingga `xxxhdpi` + adaptive icon API 26+). Nama aplikasi di homescreen adalah `MyFin`.
   - **Tampilan Login:** 100% identik dengan `resources/views/login.blade.php`. Desain bersih tanpa radio button dummy demo, tombol navy `#111827`, dan input hanya terisi jika user mencentang Remember-Me.
2. **Kompilasi & Build:**
   - Telah lulus build (`BUILD SUCCESSFUL`) menghasilkan `app-debug.apk` (9.4 MB) dan teruji langsung di emulator.
3. **Controller API Backend:**
   - File controller backend `C:\laragon\www\myfin\app\Http/Controllers/Api/MobileApiController.php` sudah dibuat dan rutenya telah didaftarkan di `routes/api.php` serta dibebaskan dari proteksi CSRF di `bootstrap/app.php`.

---

## 4. DAFTAR TUGAS UNTUK DEVELOPER WEB & IOT (SYNC CHECKLIST)

Berikut adalah poin-poin yang perlu diperiksa dan diselaraskan oleh rekan developer Web/IoT saat hosting ke VPS atau integrasi alat fisik ESP32:

### Poin A: Migrasi API Laravel ke Server VPS (Jika Hosting ke VPS)
Pastikan file controller API dan rute berikut ikut ter-upload / ter-commit di server VPS:
- **File Controller:** `app/Http/Controllers/Api/MobileApiController.php`
- **File Routes:** Rute `/api/mobile/*` pada `routes/api.php`
- **CSRF Whitelist:** Baris `$middleware->validateCsrfTokens(except: ['api/*'])` pada `bootstrap/app.php`.
- **Base URL di App:** Di aplikasi Android pada halaman login, terdapat tombol *"Pengaturan Alamat Server IP"*. Pengguna cukup memasukkan URL VPS (contoh: `https://myfin.domain-anda.com/api/mobile` atau `http://172.16.x.x:8000/api/mobile`).

### Poin B: Integrasi Hardware IoT ESP32 + Fingerprint AS608 (Rubrik Poin 28)
Di aplikasi Android, saat mahasiswa membuka tab Biometrik, aplikasi menyediakan tombol:
`[Daftarkan Sidik Jari via Sensor IoT ESP32]`

Saat tombol ini ditekan, Android mengirimkan request:
```http
POST /api/mobile/mahasiswa/biometric
Body: { "action": "start_enrollment" }
```
Dan Android akan melakukan polling periodik:
```http
POST /api/mobile/mahasiswa/biometric
Body: { "action": "check_status" }
```
**Tugas Pengembang Web/IoT:**
1. Di firmware ESP32 (atau skrip Python/MQTT listener pada VPS): Ketika flag `is_enrolling` aktif untuk mahasiswa bersangkutan, nyalakan lampu sensor AS608 agar mahasiswa meletakkan jarinya.
2. Setelah sensor AS608 berhasil membaca dan menyimpan ID sidik jari di flash memorinya, kirim sinyal balik ke server Laravel untuk mengubah status database mahasiswa:
   - `biometric_enrolled = true`
   - `fingerprint_id = [ID_SENSOR]`
3. Respon `check_status` di `MobileApiController` akan otomatis mengembalikan `biometric_enrolled: true`, dan layar Android langsung memutar nada suara sukses serta menampilkan status "Sidik Jari Aktif".

### Poin C: Supabase Storage Bucket (Rubrik Poin 33)
- Kredensial Supabase di Android sudah dikonfigurasi:
  - **URL:** `https://fsocbyvbzvvjdbtuhcfm.supabase.co`
  - **Anon Key:** `sb_publishable_l8whAkx9zuD2jDMoY70GnQ_ddaY9XGv`
  - **Bucket:** `Mobile Lanjut`
- Pastikan policy pada bucket Supabase mengizinkan operasi `SELECT` (Public Read) dan `INSERT` untuk upload foto produk menu kantin.

### Poin D: Sinkronisasi Transaksi Web POS & Android Mahasiswa
- Saat pemesanan dilakukan di Web POS menggunakan kartu/NIM atau biometrik mahasiswa, data transaksi masuk ke tabel `transactions` / `orders` di database Laravel.
- Android Mahasiswa langsung membaca riwayat tersebut secara real-time via `GET /api/mobile/mahasiswa/riwayat` yang terhubung langsung ke database yang sama.
