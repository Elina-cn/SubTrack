# PROJECT_SPEC.md — SubTrack Ürün Tanımı

> **Durum (22 Eylül 2026):** v1.0 tamamlandı ve Google Play'e yüklendi; kapalı
> test aşamasında. §8'deki "bitti" tanımının tamamı karşılandı.

## 1. Uygulama Nedir

SubTrack, kullanıcının düzenli ödediği abonelikleri (Netflix, Spotify, Adobe,
spor salonu, sigorta vb.) tek yerde toplayıp aylık toplam yükünü görmesini ve
bu yükün zaman içinde nasıl değiştiğini takip etmesini sağlayan bir Android
uygulamasıdır.

**Çözdüğü problem:** İnsanlar 8-12 aboneliğe sahip ama toplamda ne kadar
ödediklerini bilmiyor; kullanmadıkları abonelikler sessizce yenilenmeye
devam ediyor. Artış da fark edilmiyor — zamlar tek tek küçük görünüyor,
toplamdaki etkisi görünmüyor.

**Temel vaat:** "Ayda tam olarak ne kadar abonelik ödüyorum, ve geçen aya göre
ne değişti?" sorusuna 3 saniyede cevap vermek.

---

## 2. Hedef Kullanıcı

- Birden fazla dijital abonelik kullanan bireyler — öncelikli pazar Türkiye
- Farklı para birimlerinde ödeme yapanlar (TL, USD, EUR, GBP karışık)
- Bütçesini takip etmek isteyen ama karmaşık finans uygulaması istemeyen kişiler

Uygulama tüm ülkelerde yayınlanıyor. Arayüz İngilizce ve Türkçe; cihaz dili
Türkçe değilse uygulama İngilizce açılır.

**Hedef olmayan:** Şirket muhasebesi, ortak/aile bütçe yönetimi, banka
entegrasyonu isteyen kullanıcılar.

---

## 3. Ürün İlkeleri

1. **Sayı doğru olmalı.** Para hesabında yuvarlama hatası kabul edilemez.
2. **Girdi hızlı olmalı.** Bir abonelik eklemek 15 saniyeden uzun sürmemeli.
3. **Sade kalmalı.** Her özellik "ne kadar ödüyorum" sorusuna hizmet
   etmiyorsa kapsam dışıdır.
4. **Veri kullanıcınındır.** İzinsiz toplanmaz, üçüncü taraflarla paylaşılmaz,
   reklam için kullanılmaz.

> Bu dört madde **kalıcıdır**. Aşağıdaki §5'teki kapsam kararları ise sürüme
> özeldir ve değişebilir — ikisi karıştırılmamalı.

---

## 4. Sürüm Kapsamları

### v1.0 — Play Store'daki ilk sürüm ✓

| Özellik | Açıklama |
|---|---|
| Abonelik listesi | Ad, tutar, para birimi, ikon ile liste görünümü |
| Abonelik ekleme | Bottom sheet üzerinden form |
| Abonelik silme | Kaydırarak sil + geri al (undo) |
| Abonelik düzenleme | Mevcut aboneliğin alanlarını değiştirme |
| Toplam tutar | Tek para birimine normalize; aylık ve yıllık görünüm arasında geçiş |
| Para birimi seçimi | Abonelik başına TRY / USD / EUR / GBP |
| Kur yönetimi | Sabit varsayılan kurlar, ayarlardan elle düzenlenebilir (canlı kur yok) |
| Yenileme tarihi | Sonraki ödeme tarihi + "X gün kaldı" göstergesi; ödeme günü geçince bir sonraki döneme kendiliğinden ilerler |
| Hatırlatma | Ödemeden bir gün önce ve ödeme günü yerel bildirim; günde en fazla bir bildirim (sunucu yok) |
| Kategoriler | Eğlence / Üretkenlik / Sağlık / Diğer + kategoriye göre filtreleme |
| Ödeme periyodu | Aylık / yıllık / haftalık; aylık maliyete normalize edilerek toplanır |
| Geçmiş takibi | Aylık toplamın anlık görüntüleri, zaman içindeki değişim |
| İstatistik | Kategori dağılımı, en pahalı abonelikler, aylık trend, geçen aya göre değişim |
| Kalıcı depolama | Room veritabanı, uygulama kapansa da veri durur |
| Yedekleme | Android'in otomatik yedeklemesi (kullanıcının kendi Google hesabı) ve yeni telefona cihazdan cihaza aktarım |
| Otomatik ikon | Bilinen servislere (Netflix, Spotify vb.) ikon ataması |
| Boş durum ekranı | Hiç abonelik yokken yönlendirici ekran |
| Tema | Sistem / açık / koyu tercihi; Android 12 ve üstünde isteğe bağlı duvar kâğıdı renkleri |
| Türkçe + İngilizce | Tüm metinler string kaynaklarından; varsayılan dil İngilizce |

### v1.1 — Veri taşınabilirliği (ilk güncelleme)

Dışa ve içe aktarma. Kararlar verildi:

- Format **JSON**
- Kapsam: abonelikler + ayarlar + kurlar. Aylık anlık görüntüler **hariç**
  (geçmiş kayıt, uygulama bunları yeniden üretiyor)
- İçe aktarma mevcut veriyi **üzerine yazar**; öncesinde sayılı onay ister
  ("12 aboneliğin silinecek, 8 yüklenecek")
- Dosya konumunu kullanıcı **sistem dosya seçicisiyle** belirler. Sabit klasör
  Android 10+ kısıtları yüzünden çalışmıyor; uygulamanın kendi klasörü de
  uygulama silinince yedekle birlikte gider
- **Şifreleme yok**; dışa aktarma ekranında "bu dosya okunabilir, paylaşırken
  dikkat et" uyarısı
- Ayarlar altında ayrı bir "Yedekleme" ekranı — içe aktarma yıkıcı bir işlem,
  ayar listesinde tek satır olmamalı
- Gizlilik politikasına eklenecek: dosya kullanıcının seçtiği yere kaydedilir,
  güvenliği onun sorumluluğundadır

### v1.2 — Ağ bağlantısı (zamanlaması açık)

- Otomatik döviz kuru güncellemesi
- `INTERNET` izni **bu sürümde** ekleniyor
- Ağ yokken son bilinen kur kullanılır; uygulama çevrimdışı da çalışmaya devam eder
- Birlikte güncellenecekler: gizlilik politikası, Play Console Data Safety formu
  ve mağaza metni (v1.0 metni "internet izni bile yok" diyor)

### v2.0 — Hesap ve senkronizasyon

- Kullanıcı hesabı
- Bulut senkronizasyonu

> **Ön koşul:** Gerçek kullanıcı talebi. Altyapı maliyeti ve KVKK/GDPR
> yükümlülüğü getirdiği için talep görülmeden yapılmaz.

---

## 5. Kapsam Kararları

> Bunlar **ilke değil**, sürüme özel kararlardır. Koşullar değişirse karar da
> değişir ve bu belge güncellenir.

### "v1.0 çevrimdışı çalışır"

Gerekçe teknik, felsefi değil: v1.0'da internete çıkmayı gerektiren tek özellik
otomatik kur güncellemesi olurdu ve **çalışan bir alternatifi var** — kullanıcı
kuru elle giriyor. Tek bir özellik için `INTERNET` izni, ağ kütüphanesi, hata
yönetimi ve çevrimdışı davranış yükünü almak bu aşamada erken.

**Kalıcı bir taahhüt değil.** Ağ v1.2'de ekleniyor.

### "v1.0'da hesap yok"

Geçmiş takibi ve istatistik **yerel olarak** çözülüyor: Room'da aylık anlık
görüntü tablosu. Hesap yalnızca çoklu cihaz senkronizasyonu için gerekli, o
talep henüz doğrulanmadı.

### "Ücretlendirme — v1.0 ücretsiz"

v1.0 **ücretsiz** yayınlandı; uygulama içi satın alma ve reklam yok. Play'de
ücretsiz yayınlanan bir uygulama sonradan ücretliye çevrilemez. Gelir
gerekirse yol uygulama içi satın alma (premium) olur — en olası aday
istatistik ekranı. Karar gerçek kullanım görüldükten sonra verilecek.

### "v1.0 dil kapsamı"

- İngilizce varsayılan, Türkçe çeviri. Türkçe varsayılanken Türkçe ve
  İngilizce dışındaki bütün cihazlar uygulamayı Türkçe görüyordu; bu yüzden
  varsayılan İngilizce yapıldı.
- Uygulama içi dil seçici yok; uygulama cihaz diline göre açılır.
- Sağdan sola diller (Arapça, İbranice, Farsça) desteklenmiyor. Bu cihazlarda
  uygulama İngilizce ve soldan sağa düzende açılır. Sağdan sola bir dil
  eklendiğinde düzen de o dille birlikte tasarlanır.

### "v1.0 yayın kararları (Google Play)"

- Tüm ülkeler; mağaza sayfası İngilizce (varsayılan) ve Türkçe
- Hedef kitle 18+; içerik derecesi her yaşa uygun (3+). Küçükleri engelleme
  seçeneği kullanılmadı
- Data Safety: veri toplanmıyor, paylaşılmıyor
- Play'in otomatik koruması (dağıtılan sürüme eklenen yükleyici kontrolü)
  kapalı: kaynak kodu açık, internetsiz ve ücretsiz bir uygulamaya katkısı
  yok; Play'in dağıttığı sürüme test edilmemiş kod eklenmesini istemiyoruz

### "iOS şimdilik yok"

iOS sürümü baştan yazılmayı gerektiriyor (Kotlin/Compose iOS'ta çalışmıyor);
Mac, Xcode ve yıllık Apple geliştirici ücreti gerekiyor. Android'de talep
görülünce değerlendirilecek. Domain katmanı saf Kotlin olduğu için Kotlin
Multiplatform'a geçiş kapısı açık.

---

## 6. Kalıcı Kapsam Dışı

Bu maddeler bilinçli olarak **yapılmayacak**:

- Banka veya kredi kartı entegrasyonu
- Otomatik abonelik tespiti (SMS/e-posta okuma)
- Reklam
- Kullanıcı verisinin üçüncü taraflarla paylaşılması
- Widget, Wear OS, tablet-özel layout (v2 değerlendirmesi)

> **Not:** "Kullanıcı hesabı" ve "canlı döviz kuru API'si" bu listeden
> çıkarıldı — artık planlı özellikler (sırasıyla v2.0 ve v1.2).

---

## 7. Kritik Teknik Kararlar

| Karar | Gerekçe |
|---|---|
| Para = kuruş cinsinden `Long` | `Double` ile 0.1 + 0.2 ≠ 0.3; finansal veride kabul edilemez |
| Yuvarlama tek noktada, ara değer `BigInteger` | Periyot ve kur dönüşümü art arda yuvarlanırsa kuruş kayar; sonunda bir kez HALF_UP |
| Para birimi kodu ISO 4217 (`TRY`, `USD`) | Standart, formatlama kütüphaneleriyle uyumlu |
| Tarih = epoch millis `Long` | Room'da basit saklanır, `Instant`'a dönüştürülür |
| Tek Activity, Compose Navigation | Modern Android standardı |
| Domain katmanı saf Kotlin (Android importu yok) | Test edilebilirlik; ileride Kotlin Multiplatform kapısı |
| Hatırlatma WorkManager ile, kesin alarm yok | Günlük hatırlatma için kesin alarm gerekmiyor; kesin alarm izni Play'de gerekçe ister |
| Varsayılan dil İngilizce | Android eşleşen dil bulamayınca varsayılan kaynaklara düşer; varsayılan Türkçe olsaydı dünyanın geri kalanı uygulamayı Türkçe görürdü |

> **Şema notu:** v1.0 şeması geçmiş takibi tablosunu içeriyor (UI sonradan
> gelse de migration gerekmesin diye baştan eklendi). **v1.0 yayınlandığı için
> bundan sonraki her şema değişikliği bir Room migration'ı ve migration testi
> gerektirir** — veritabanı artık yeniden üretilemez, kullanıcıların verisi
> taşınmalı.

---

## 8. "Bitti" Tanımı (v1.0)

Aşağıdakilerin hepsi doğruysa v1.0 hazırdır:

- [x] Uygulama çökmeden kurulup çalışıyor
- [x] Abonelik eklenip listeleniyor, uygulama kapatılıp açıldığında duruyor
- [x] Silme çalışıyor, yanlışlıkla silme geri alınabiliyor
- [x] Mevcut bir abonelik düzenlenebiliyor
- [x] Toplam tutar doğru hesaplanıyor (kuruş hatası yok)
- [x] Karışık para birimli abonelikler tek para birimine doğru normalize ediliyor
- [x] Kurlar ayarlardan düzenlenebiliyor ve düzenleme toplama yansıyor
- [x] Aylık/yıllık/haftalık abonelikler aylık maliyete doğru çevriliyor
- [x] Yenileme tarihi ve "X gün kaldı" doğru gösteriliyor
- [x] Yaklaşan ödeme bildirimi geliyor; bildirim izni reddedilse de uygulama çalışıyor
- [x] Kategoriye göre filtreleme çalışıyor
- [x] Aylık toplamlar geçmişe kaydediliyor, önceki aylarla karşılaştırılabiliyor
- [x] İstatistik ekranı kategori dağılımını ve aylık trendi doğru gösteriyor
- [x] Ekran döndürmede state kaybolmuyor
- [x] Koyu temada tüm ekranlar okunabilir
- [x] Hiç hardcoded metin veya renk kalmamış
- [x] ViewModel ve DAO için birim testleri geçiyor
- [x] Release build imzalanmış, R8 ile küçültülmüş, çalışıyor
- [x] Gizlilik politikası yayınlanmış, Play Console formu doldurulmuş
- [x] Türkçe dışındaki cihaz dillerinde uygulama İngilizce açılıyor
- [x] Mağaza metnindeki her özellik iddiası kodla doğrulandı

> v1.0 bu tanımı 22 Eylül 2026'da karşıladı. Kanıtlar `PROGRESS.md`'de: tam
> regresyon (117 madde, AAB'den kurulan release build üzerinde), 330 birim +
> 19 enstrümantasyon testi, dil matrisi ve mağaza iddia denetimi.
