# 💗 Pengingat Sayang — Panduan Lengkap (untuk pemula)

Kamu yang mengisi pengingat dari **halaman web admin** (bisa dari HP atau laptop).
HP Android pacar kamu akan **berdering seperti alarm** tepat pada jamnya — tetap bunyi walau HP terkunci, layar mati, atau mode senyap — sampai dia tekan **Matikan** atau **Tunda 5 menit**.
Kamu juga bisa lihat apakah alarmnya sudah dia matikan. 😉

```
 Kamu (halaman admin)  ──►  Firebase (database gratis)  ──►  HP pacar (aplikasi Android)
                                                              ↳ berdering seperti alarm
```

**Total waktu setup: ±30–45 menit, sekali saja.** Semuanya gratis. Kamu tidak perlu install aplikasi apa pun di laptop.

Yang kamu butuhkan:
- Akun Google (untuk Firebase)
- Akun GitHub (gratis, untuk membuat file APK dan menaruh halaman admin)
- HP pacar (Android 8 ke atas) untuk dipasangi aplikasi

---

## BAGIAN 1 — Buat database di Firebase (±10 menit)

### 1.1 Buat project
1. Buka **https://console.firebase.google.com** dan login dengan akun Google.
2. Klik **Create a project** (atau *Add project*).
3. Nama project: `pengingat-sayang` → **Continue**.
4. Matikan **Google Analytics** (tidak perlu) → **Create project** → tunggu → **Continue**.

### 1.2 Aktifkan login untuk kamu (admin)
1. Menu kiri: **Build → Authentication** → **Get started**.
2. Tab **Sign-in method** → pilih **Email/Password** → aktifkan saklar pertama (**Enable**) → **Save**.
3. Tab **Users** → **Add user** → isi email kamu dan sebuah password → **Add user**.
   *Catat password ini — dipakai untuk login di halaman admin.*

### 1.3 Buat database Firestore
1. Menu kiri: **Build → Firestore Database** → **Create database**.
2. Kalau ditanya *edition*, pilih **Standard**.
3. Location: pilih **asia-southeast2 (Jakarta)** → **Next**.
4. Pilih **Start in production mode** → **Create**.

### 1.4 Pasang aturan keamanan (Rules)
1. Masih di Firestore, buka tab **Rules**.
2. Hapus semua isinya, lalu **salin seluruh isi file `firestore.rules`** (ada di folder project ini) dan tempel di sana.
3. Klik **Publish**.

> Aturan ini membuat: hanya kamu (yang login) yang bisa menambah/mengubah/menghapus pengingat; HP pacar hanya bisa membaca dan melaporkan "sudah dimatikan".

### 1.5 Ambil konfigurasi web
1. Klik ikon **⚙️ (roda gigi)** di kiri atas → **Project settings**.
2. Scroll ke **Your apps** → klik ikon **`</>`** (Web).
3. Nickname: `admin` → **jangan** centang Firebase Hosting → **Register app**.
4. Akan muncul kode berisi `const firebaseConfig = { apiKey: "...", ... };`
   **Salin blok itu** (simpan dulu di Notes/WhatsApp ke diri sendiri). Nanti dipakai di Bagian 3.

---

## BAGIAN 2 — Upload ke GitHub supaya APK & halaman admin dibuat otomatis (±15 menit)

GitHub akan "memasak" kode ini menjadi file aplikasi (APK) dan sekaligus menayangkan halaman admin kamu — gratis, otomatis, tanpa install apa-apa.

### 2.1 Buat akun & repository
1. Daftar di **https://github.com** (kalau belum punya).
2. Klik **+** (kanan atas) → **New repository**.
3. Repository name: `pengingat-sayang`
4. Pilih **Public** (wajib supaya halaman admin gratis; tenang, tidak ada data rahasia di dalam kode — semua kunci kamu isi sendiri di halaman admin).
5. Klik **Create repository**.

### 2.2 Upload semua file
1. Extract file zip `pengingat-sayang.zip` di laptop.
2. Di halaman repository yang baru, klik link **uploading an existing file**.
3. Buka folder hasil extract, **pilih semua isinya** (folder `android`, `web`, `.github`, file `firestore.rules`, `README.md`) lalu **drag ke halaman GitHub**.
4. Tunggu sampai semua ter-upload → klik **Commit changes**.

> ⚠️ **Cek folder `.github`!** Di Mac/Windows, folder yang diawali titik kadang tersembunyi sehingga tidak ikut ter-upload.
> Setelah upload, lihat daftar file di GitHub: harus ada folder **`.github`**. Kalau **tidak ada**:
> klik **Add file → Create new file**, ketik nama `.github/workflows/build.yml`, lalu buka file `build.yml` dari folder `.github/workflows` di laptop dengan Notepad, salin semua isinya, tempel → **Commit changes**.

### 2.3 Aktifkan GitHub Pages
1. Di repository, klik **Settings** → menu kiri **Pages**.
2. Bagian **Build and deployment → Source**: pilih **GitHub Actions**.

### 2.4 Jalankan build
1. Klik tab **Actions** di repository.
2. Klik workflow **"Build APK & Halaman Admin"** di kiri → tombol **Run workflow** → **Run workflow**.
   (Kalau sudah ada run sebelumnya yang gagal karena Pages belum aktif, jalankan ulang saja.)
3. Tunggu ±5 menit sampai tanda ✅ hijau.

Setelah ✅, kamu punya dua alamat (ganti `USERNAME` dengan username GitHub kamu):
- **Halaman admin:** `https://USERNAME.github.io/pengingat-sayang/`
- **File aplikasi:** `https://USERNAME.github.io/pengingat-sayang/pengingat-sayang.apk`

> ❌ Kalau ada tanda merah: klik run-nya → klik langkah yang merah → salin pesan error-nya dan kirim ke Claude.

---

## BAGIAN 3 — Siapkan halaman admin (±3 menit)

1. Buka `https://USERNAME.github.io/pengingat-sayang/` (simpan sebagai bookmark / "Add to Home screen" di HP kamu).
2. Tempel **firebaseConfig** dari langkah 1.5 → isi **nama kamu** (muncul di layar alarm pacar, mis. "💌 dari Budi") → **Simpan**.
3. Login dengan email & password dari langkah 1.2.
4. Buka panel **📱 Hubungkan HP pacar** → di sana ada **kode setup** (diawali `PS1:`). Pakai di Bagian 4.

> Setup ini tersimpan di browser. Kalau buka dari browser/HP lain, ulangi langkah 2 (tempel firebaseConfig lagi).

---

## BAGIAN 4 — Pasang di HP pacar (±10 menit)

### 4.1 Install aplikasi
1. Di HP pacar, buka **Chrome** → ketik alamat `https://USERNAME.github.io/pengingat-sayang/pengingat-sayang.apk` → unduh.
2. Buka file yang terunduh → kalau muncul *"Untuk keamanan, HP tidak diizinkan menginstal aplikasi tak dikenal"* → **Setelan** → izinkan untuk Chrome → kembali → **Instal**.
3. Kalau **Play Protect** memperingatkan ("aplikasi tidak dikenal") → **Detail lainnya → Tetap instal**. Ini wajar untuk aplikasi buatan sendiri yang tidak ada di Play Store.

### 4.2 Hubungkan
1. Di halaman admin (HP/laptop kamu), tekan **Kirim via WhatsApp** dan kirim kodenya ke WhatsApp pacar
   — atau scan QR di halaman admin pakai kamera HP pacar lalu salin teksnya.
2. Di HP pacar, salin kode tersebut → buka aplikasi **Pengingat Sayang** → **Tempel dari clipboard** → **Hubungkan**.
3. Izinkan notifikasi kalau ditanya.

### 4.3 Pastikan semua ✅ (penting!)
Di aplikasi ada kartu **"Supaya alarm pasti bunyi"**. Tekan **Atur** di setiap baris yang masih ⚠️:
- Notifikasi diizinkan
- Alarm tepat waktu
- Layar alarm saat HP terkunci
- Tidak dibatasi penghemat baterai
- Volume alarm tidak nol

**HP Xiaomi / Redmi / POCO / Oppo / Realme / Vivo / Infinix:** aplikasi akan menampilkan tombol **Buka Info aplikasi**. Di sana:
- aktifkan **Mulai otomatis / Autostart**,
- set **Penghemat baterai** ke **Tidak ada batasan / Tidak dibatasi**.
Tanpa ini, HP merek tersebut bisa "membunuh" aplikasi di latar belakang.

### 4.4 Tes!
Tekan **🔔 Tes alarm (bunyi 10 detik lagi)** → langsung **kunci layar HP** → dalam 10 detik HP harus berdering dengan layar alarm penuh. 🎉

Di halaman admin kamu, kartu status akan berubah menjadi **"HP pacar terhubung 💗"**.

Setelah itu, pacar kamu **tidak perlu membuka aplikasinya lagi**. Semua kamu atur dari halaman admin.

---

## Cara pakai sehari-hari

Di halaman admin:
- **Judul** (wajib) + **Pesan** (opsional, muncul di layar alarm) — ada tombol cepat: Minum air, Makan siang, Minum obat, dll.
- **Ulangi:** *Sekali* (pilih tanggal), *Setiap hari*, atau *Hari tertentu* (mis. Sen–Jum).
- **Jam** → **Simpan pengingat**.
- Saklar di tiap pengingat untuk menonaktifkan sementara; **Edit** / **Hapus**.
- Di bawah tiap pengingat terlihat status terakhir: ✅ *Dimatikan dia*, 😴 *Ditunda*, atau ⚠️ *Tidak direspons* (alarm berhenti sendiri setelah 10 menit tanpa disentuh).

**Sinkron otomatis & langsung:** aplikasi di HP pacar tersambung langsung ke database. Begitu kamu menekan Simpan / Edit / Hapus / saklar, perubahannya masuk ke HP-nya **dalam hitungan detik** — tidak ada tombol sinkron. Kalau sambungan sempat putus (mis. tanpa internet), HP tetap mengecek ulang otomatis tiap ±15 menit dan langsung menyusul begitu online lagi.

Karena tersambung terus, di HP pacar ada **notifikasi kecil "Pengingat Sayang · Tersambung"** (di bagian bawah daftar notifikasi, tanpa suara). Ini wajib dari Android untuk aplikasi yang terus berjalan. Kalau mengganggu, tekan lama notifikasi itu → matikan kategori **"Status sambungan"** — sambungan dan alarm tetap jalan. Pemakaian baterainya sangat kecil (setara aplikasi chat).

Alarm yang sudah tersimpan di HP **tetap bunyi walau tanpa internet**, dan otomatis dipasang ulang setelah HP restart.

---

## Kalau ada masalah

| Masalah | Solusi |
|---|---|
| Status admin: "sudah lama tidak tersambung" | HP mati/tanpa internet, atau aplikasi dibatasi penghemat baterai. Minta dia buka aplikasinya, cek semua ✅ dan Autostart. |
| Perubahan tidak langsung masuk | Di aplikasi, bagian bawah harus 🟢 *Tersambung langsung*. Kalau tidak, pastikan baris "Tidak dibatasi penghemat baterai" ✅ dan (HP Xiaomi/Oppo/Vivo dkk.) Autostart aktif. |
| Alarm tidak bunyi saat HP terkunci | Cek baris "Layar alarm saat HP terkunci" dan "Volume alarm" di aplikasi. Jalankan tes alarm. |
| Admin: "Ditolak Rules Firestore" | Ulangi langkah 1.4, pastikan klik **Publish**. |
| Admin: "Email atau password salah" | Cek user di Firebase → Authentication → Users (langkah 1.2). |
| Aplikasi: "Kode tidak valid" | Salin ulang kode secara lengkap, mulai dari `PS1:` sampai karakter terakhir. |
| Build di GitHub merah ❌ | Salin pesan error dari langkah yang merah, kirim ke Claude. |

**Update aplikasi nanti:** cukup upload file yang diubah ke GitHub → APK baru otomatis dibuat → unduh & instal di atas versi lama (data & kode setup tetap).

**Sudah pernah pasang versi 1.0?** Setelah install versi 1.1, salin ulang **kode setup** dari halaman admin (kodenya sekarang sedikit lebih panjang) dan tempel lagi di aplikasi → **Hubungkan**.

**Catatan kebijakan Android:** mulai 2027, Google bertahap mewajibkan aplikasi yang diinstal dari luar Play Store berasal dari developer terverifikasi. Untuk aplikasi pribadi seperti ini, Google menyediakan akun *limited distribution* gratis (untuk dibagikan ke maksimal 20 perangkat). Aplikasi yang **sudah terinstal** tidak terpengaruh; ini hanya relevan kalau nanti install ulang/update.

---

## Isi folder

```
android/            kode aplikasi Android (Java + library Firestore untuk sinkron langsung)
web/index.html      halaman admin
firestore.rules     aturan keamanan database (langkah 1.4)
.github/workflows/  resep otomatis GitHub: build APK + pasang halaman admin
```

- Ganti nama aplikasi: `android/app/src/main/res/values/strings.xml`
- Lama alarm berbunyi sebelum berhenti sendiri (10 menit): `AUTO_STOP_MS` di `AlarmService.java`
- Lama tunda (5 menit): `SNOOZE_MS` di `Scheduler.java`
