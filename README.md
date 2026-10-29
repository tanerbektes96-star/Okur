# Okur — Android E-Kitap Okuyucu

Telefon ve tabletlerde tam uyumlu, akıcı, göz dostu bir EPUB / PDF / TXT okuyucu.
Kotlin + Jetpack Compose (Material 3) ile yazıldı; tamamen çevrimdışı çalışır.

## Özellikler

### Okuma
- **EPUB 2/3** (kendi hafif ayrıştırıcısı), **PDF** (Android PdfRenderer), **TXT** (otomatik bölüm algılama, UTF‑8 / Windows‑1254 Türkçe kodlama desteği)
- **Akıcı sayfa çevirme:** parmağı takip eden kaydırma, hızlı kaydırmayı (fling) algılama, yumuşak animasyon
- **Sayfa çevirme veya dikey kaydırma** modu
- **Tablette çift sütun (EPUB) / çift sayfa (PDF)** yatay modda
- PDF: sürekli kaydırma veya sayfa modu, **iki parmakla ve çift dokunarak yakınlaştırma**, gece modunda PDF renklerini temaya uyarlama
- İçindekiler (iç içe başlıklar), **yer imleri + notlar**, **kitap içi arama** (sonuç vurgulama)
- **Sesli okuma (TTS):** bulunduğun sayfadan başlar, okunan paragrafı ekranda tutar, bölümden bölüme otomatik geçer, hız ayarı
- 7 okuma teması (Beyaz, Kağıt, Sepya, Nane, Alacakaranlık, Koyu, AMOLED Siyah)
- Yazı boyutu, yazı tipi, satır aralığı, kenar boşluğu, iki yana yaslama, heceleme
- Ses tuşlarıyla ve ekran kenarlarına dokunarak sayfa çevirme, tam ekran okuma, ekran yönü kilidi, okuma ekranına özel parlaklık
- Kaldığın yeri otomatik hatırlama (yazı boyutu değişse bile aynı paragrafta kalır)

### Akıllı Göz desteği
- **Akıllı Göz (ön kamera, cihaz üzerinde ML Kit):**
  - Ekrana bakarken ekranı açık tutar, bakmayınca normal şekilde kapanmasına izin verir
  - **Mesafe uyarısı:** ekrana çok yaklaşınca uyarır
  - **Göz kırpma takibi:** kırpma hızı düşünce göz kuruluğuna karşı hatırlatır
  - Uzaklaşınca ekranı karartır, mola sayacını duraklatır
  - Görüntüler kaydedilmez, internete gönderilmez; kamera yalnızca okuma ekranında ve isteğe bağlı çalışır
- **Mavi ışık filtresi:** kapalı / her zaman / zamanlanmış (ör. 21:00–07:00), ayarlanabilir yoğunluk
- **Ekstra karartma:** sistem minimum parlaklığının altına
- **20‑20‑20 göz molası** hatırlatıcısı (geri sayımlı mola penceresi, erteleme)
- Gece saatlerinde otomatik koyu okuma teması

### Kitaplık ve istatistikler
- Izgara / liste görünümü (tablette daha fazla sütun), arama, filtreler (Okunuyor, Bitti, Favoriler, biçim), sıralama
- "Okumaya devam et" kartı, günlük okuma hedefi ve **okuma serisi**
- Kapak çıkarma (EPUB kapağı / PDF ilk sayfa), kapak yoksa şık otomatik kapak
- Dosya yöneticisinden **"Birlikte aç → Okur"** ve paylaş menüsünden ekleme
- İstatistikler: son 14 gün grafiği, toplam süre, bitirilen kitaplar, en çok okunanlar
- Telefonlarda alt gezinme çubuğu, tabletlerde yan gezinme rayı

## Derleme

Gereksinimler: JDK 17, Android SDK (compileSdk 35). Android Studio (Ladybug veya yenisi) ile açıp çalıştırabilirsiniz ya da:

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Depoyu GitHub'a gönderdiğinizde `.github/workflows/build.yml` iş akışı debug ve release APK'larını otomatik derleyip **Actions → Artifacts** altında yayınlar.

> Not: release derlemesi, kendi imza anahtarınızı tanımlayana kadar debug anahtarıyla imzalanır (Play Store için kendi anahtarınızı `signingConfigs` içine ekleyin).

## Mimari

```
app/src/main/
├── assets/reader/reader.js     WebView içi okuma motoru: sayfalama, jestler, arama, TTS metni
└── java/com/okur/reader/
    ├── data/                   Room (kitaplar, yer imleri, okuma oturumları), DataStore ayarları, içe aktarma
    ├── format/                 EPUB ayrıştırıcı, TXT bölümleyici, PDF oluşturucu (önbellekli, tek iş parçacığı)
    ├── reader/                 Okuyucu CSS'i, ses tuşu köprüsü
    ├── eyecare/                Akıllı Göz (CameraX + ML Kit), mavi ışık / karartma katmanı
    ├── tts/                    Sesli okuma
    └── ui/                     Compose ekranları: kitaplık, okuyucu, istatistik, göz bakımı, ayarlar
```

EPUB bölümleri, ZIP içinden doğrudan `WebView`'e sunulur (dış ağ istekleri engellenir). Sayfalama CSS sütunlarıyla yapılır; bu sayede yayıncı biçimlendirmesi, resimler ve gömülü yazı tipleri korunur.
