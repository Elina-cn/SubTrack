# PROJECT_SPEC.md — SubTrack Ürün Tanımı

## 1. Uygulama Nedir

SubTrack, kullanıcının düzenli ödediği abonelikleri (Netflix, Spotify, Adobe,
spor salonu, sigorta vb.) tek yerde toplayıp aylık toplam yükünü görmesini
sağlayan bir Android uygulamasıdır.

**Çözdüğü problem:** İnsanlar 8-12 aboneliğe sahip ama toplamda ne kadar
ödediklerini bilmiyor; kullanmadıkları abonelikler sessizce yenilenmeye
devam ediyor.

**Temel vaat:** "Ayda tam olarak ne kadar abonelik ödüyorum?" sorusuna
3 saniyede cevap vermek.

---

## 2. Hedef Kullanıcı

- Türkiye'de yaşayan, birden fazla dijital abonelik kullanan bireyler
- Farklı para birimlerinde ödeme yapanlar (TL, USD, EUR karışık)
- Bütçesini takip etmek isteyen ama karmaşık finans uygulaması istemeyen kişiler

**Hedef olmayan:** Şirket muhasebesi, ortak/aile bütçe yönetimi, banka
entegrasyonu isteyen kullanıcılar.

---

## 3. Ürün İlkeleri

1. **Veri kullanıcınındır.** Uygulama tamamen çevrimdışı çalışır. Hesap yok,
   sunucu yok, analytics yok. Play Store gizlilik beyanı bunu net söyler.
2. **Girdi hızlı olmalı.** Bir abonelik eklemek 15 saniyeden uzun sürmemeli.
3. **Sayı doğru olmalı.** Para hesabında yuvarlama hatası kabul edilemez.
4. **Sade kalmalı.** Her özellik "ayda ne kadar ödüyorum" sorusuna hizmet
   etmiyorsa kapsam dışıdır.

---

## 4. Sürüm Kapsamları

### v1.0 — Play Store'a çıkacak ilk sürüm

| Özellik | Açıklama |
|---|---|
| Abonelik listesi | Ad, tutar, para birimi, ikon ile liste görünümü |
| Abonelik ekleme | Bottom sheet üzerinden form |
| Abonelik silme | Kaydırarak sil + geri al (undo) |
| Toplam tutar | Aylık toplam, tek para birimine normalize |
| Para birimi seçimi | Abonelik başına TL / USD / EUR / GBP |
| Kalıcı depolama | Room veritabanı, uygulama kapansa da veri durur |
| Otomatik ikon | Bilinen servislere (Netflix, Spotify vb.) ikon ataması |
| Boş durum ekranı | Hiç abonelik yokken yönlendirici ekran |
| Açık/koyu tema | Sistem temasını takip eder |
| Türkçe + İngilizce | Tüm metinler `strings.xml` üzerinden |

### v1.1 — Yenileme takibi
- Sonraki ödeme tarihi
- Yaklaşan ödeme bildirimi (yerel notification, sunucu yok)

### v1.2 — Sınıflandırma
- Kategori (Eğlence, Üretkenlik, Sağlık, Diğer)
- Kategoriye göre filtreleme

### v1.3 — Ödeme periyodu
- Aylık / yıllık / haftalık ayrımı
- Yıllık abonelikleri aylık maliyete çevirerek toplama katma

### v1.4 — İstatistik
- Kategori dağılımı grafiği
- Aylık harcama trendi

### v1.5 — Düzenleme
- Mevcut aboneliği düzenleme ekranı

> **Şema notu:** v1.1–v1.5 özelliklerinin veritabanı alanları **v1.0 şemasında
> baştan tanımlanacak** (`nextPaymentDate`, `category`, `billingPeriod`).
> UI daha sonra gelse de migration yükünden böylece kurtuluruz.

> **Sıra notu:** Düzenleme ekranı (v1.5) mantıken CRUD'un parçası ve daha erken
> alınabilir. Kullanıcı isterse öne çekilir; şu anki sıra kullanıcının tercihidir.

---

## 5. Kapsam Dışı

Bu maddeler bilinçli olarak **yapılmayacak**:

- Kullanıcı hesabı, giriş, bulut senkronizasyonu
- Banka veya kredi kartı entegrasyonu
- Otomatik abonelik tespiti (SMS/e-posta okuma)
- Reklam, analytics, üçüncü parti SDK
- Widget, Wear OS, tablet-özel layout (v2 değerlendirmesi)
- Canlı döviz kuru API'si (kur elle girilir veya sabit tanımlanır)

---

## 6. Kritik Teknik Kararlar

| Karar | Gerekçe |
|---|---|
| Para = kuruş cinsinden `Long` | `Double` ile 0.1 + 0.2 ≠ 0.3; finansal veride kabul edilemez |
| Para birimi kodu ISO 4217 (`TRY`, `USD`) | Standart, formatlama kütüphaneleriyle uyumlu |
| Tarih = epoch millis `Long` | Room'da basit saklanır, `Instant`'a dönüştürülür |
| Tamamen çevrimdışı | Gizlilik ilkesi + Play Store beyanı sadeliği |
| Tek Activity, Compose Navigation | Modern Android standardı |

---

## 7. "Bitti" Tanımı (v1.0)

Aşağıdakilerin hepsi doğruysa v1.0 hazırdır:

- [ ] Uygulama çökmeden kurulup çalışıyor
- [ ] Abonelik eklenip listeleniyor, uygulama kapatılıp açıldığında duruyor
- [ ] Silme çalışıyor, yanlışlıkla silme geri alınabiliyor
- [ ] Toplam tutar doğru hesaplanıyor (kuruş hatası yok)
- [ ] Ekran döndürmede state kaybolmuyor
- [ ] Koyu temada tüm ekranlar okunabilir
- [ ] Hiç hardcoded metin veya renk kalmamış
- [ ] ViewModel ve DAO için birim testleri geçiyor
- [ ] Release build imzalanmış, ProGuard ile küçültülmüş, çalışıyor
- [ ] Gizlilik politikası yayınlanmış, Play Console formu doldurulmuş
