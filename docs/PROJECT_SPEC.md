# PROJECT_SPEC.md — SubTrack Ürün Tanımı

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

- Türkiye'de yaşayan, birden fazla dijital abonelik kullanan bireyler
- Farklı para birimlerinde ödeme yapanlar (TL, USD, EUR karışık)
- Bütçesini takip etmek isteyen ama karmaşık finans uygulaması istemeyen kişiler

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

### v1.0 — Play Store'a çıkacak ilk sürüm

| Özellik | Açıklama |
|---|---|
| Abonelik listesi | Ad, tutar, para birimi, ikon ile liste görünümü |
| Abonelik ekleme | Bottom sheet üzerinden form |
| Abonelik silme | Kaydırarak sil + geri al (undo) |
| Abonelik düzenleme | Mevcut aboneliğin alanlarını değiştirme |
| Toplam tutar | Aylık toplam, tek para birimine normalize |
| Para birimi seçimi | Abonelik başına TRY / USD / EUR / GBP |
| Kur yönetimi | Sabit varsayılan kurlar, ayarlardan elle düzenlenebilir |
| Yenileme tarihi | Sonraki ödeme tarihi + "X gün kaldı" göstergesi |
| Hatırlatma | Yaklaşan ödeme için yerel bildirim (sunucu yok) |
| Kategoriler | Eğlence / Üretkenlik / Sağlık / Diğer + kategoriye göre filtreleme |
| Ödeme periyodu | Aylık / yıllık / haftalık; aylık maliyete normalize edilerek toplanır |
| Geçmiş takibi | Aylık toplamın anlık görüntüleri, zaman içindeki değişim |
| İstatistik | Kategori dağılımı, aylık trend |
| Kalıcı depolama | Room veritabanı, uygulama kapansa da veri durur |
| Otomatik ikon | Bilinen servislere (Netflix, Spotify vb.) ikon ataması |
| Boş durum ekranı | Hiç abonelik yokken yönlendirici ekran |
| Açık/koyu tema | Sistem temasını takip eder |
| Türkçe + İngilizce | Tüm metinler `strings.xml` üzerinden |

### v1.1 — Ağ bağlantısı

- Otomatik döviz kuru güncellemesi
- `INTERNET` izni **bu sürümde** ekleniyor
- Gizlilik politikası ve Play Console Data Safety formu güncelleniyor
- Ağ yokken son bilinen kur kullanılır; uygulama çevrimdışı da çalışmaya devam eder

### v1.2 — Veri taşınabilirliği

- Dışa aktarma ve içe aktarma (CSV veya JSON)

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

**Kalıcı bir taahhüt değil.** v1.1'de ağ ekleniyor.

### "v1.0'da hesap yok"

Geçmiş takibi ve istatistik **yerel olarak** çözülüyor: Room'da aylık anlık
görüntü tablosu. Hesap yalnızca çoklu cihaz senkronizasyonu için gerekli, o
talep henüz doğrulanmadı.

### "Ücretlendirme — henüz karar verilmedi"

Seçenekler: peşin ücretli, ücretsiz + premium, tamamen ücretsiz.

Karar **Faz 16'ya kadar bekleyebilir**; kod tarafında bir etkisi yok. Premium
seçilirse hangi özelliğin premium olacağı ayrıca kararlaştırılacak — en olası
aday istatistik ekranı.

---

## 6. Kalıcı Kapsam Dışı

Bu maddeler bilinçli olarak **yapılmayacak**:

- Banka veya kredi kartı entegrasyonu
- Otomatik abonelik tespiti (SMS/e-posta okuma)
- Reklam
- Kullanıcı verisinin üçüncü taraflarla paylaşılması
- Widget, Wear OS, tablet-özel layout (v2 değerlendirmesi)

> **Not:** "Kullanıcı hesabı" ve "canlı döviz kuru API'si" bu listeden
> çıkarıldı — artık planlı özellikler (sırasıyla v2.0 ve v1.1).

---

## 7. Kritik Teknik Kararlar

| Karar | Gerekçe |
|---|---|
| Para = kuruş cinsinden `Long` | `Double` ile 0.1 + 0.2 ≠ 0.3; finansal veride kabul edilemez |
| Para birimi kodu ISO 4217 (`TRY`, `USD`) | Standart, formatlama kütüphaneleriyle uyumlu |
| Tarih = epoch millis `Long` | Room'da basit saklanır, `Instant`'a dönüştürülür |
| Geçmiş veri yerel | Sunucu maliyeti ve gizlilik yükümlülüğü olmadan aynı değeri veriyor |
| Tek Activity, Compose Navigation | Modern Android standardı |

> **Şema notu:** v1.1 ve sonrasının veritabanı alanları **v1.0 şemasında baştan
> tanımlanır**. v1.0 şeması ayrıca **geçmiş takibi tablosunu** da içerecek
> şekilde tasarlanacak. UI daha sonra gelse de migration yükünden böylece
> kurtuluruz.

---

## 8. "Bitti" Tanımı (v1.0)

Aşağıdakilerin hepsi doğruysa v1.0 hazırdır:

- [ ] Uygulama çökmeden kurulup çalışıyor
- [ ] Abonelik eklenip listeleniyor, uygulama kapatılıp açıldığında duruyor
- [ ] Silme çalışıyor, yanlışlıkla silme geri alınabiliyor
- [ ] Mevcut bir abonelik düzenlenebiliyor
- [ ] Toplam tutar doğru hesaplanıyor (kuruş hatası yok)
- [ ] Karışık para birimli abonelikler tek para birimine doğru normalize ediliyor
- [ ] Kurlar ayarlardan düzenlenebiliyor ve düzenleme toplama yansıyor
- [ ] Aylık/yıllık/haftalık abonelikler aylık maliyete doğru çevriliyor
- [ ] Yenileme tarihi ve "X gün kaldı" doğru gösteriliyor
- [ ] Yaklaşan ödeme bildirimi geliyor; bildirim izni reddedilse de uygulama çalışıyor
- [ ] Kategoriye göre filtreleme çalışıyor
- [ ] Aylık toplamlar geçmişe kaydediliyor, önceki aylarla karşılaştırılabiliyor
- [ ] İstatistik ekranı kategori dağılımını ve aylık trendi doğru gösteriyor
- [ ] Ekran döndürmede state kaybolmuyor
- [ ] Koyu temada tüm ekranlar okunabilir
- [ ] Hiç hardcoded metin veya renk kalmamış
- [ ] ViewModel ve DAO için birim testleri geçiyor
- [ ] Release build imzalanmış, ProGuard ile küçültülmüş, çalışıyor
- [ ] Gizlilik politikası yayınlanmış, Play Console formu doldurulmuş
