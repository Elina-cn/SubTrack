# WORKFLOW.md — Çalışma Düzeni

## 1. Rol Dağılımı

| | **Claude (sohbet)** | **Claude Code** |
|---|---|---|
| Görev | Planlama, karar, prompt üretimi | Kod yazma, düzenleme, derleme |
| Kod yazar mı? | Hayır — sadece örnek/kavram için | Evet, tek yazan o |
| Belge yazar mı? | Evet, `docs/` altındakileri | Sadece `PROGRESS.md` ve güncellemeler |
| Onay bekler mi? | — | Hayır, prompt zaten onaydır |

**Kural:** Kod hakkındaki her karar önce sohbette verilir, sonra prompt olarak
Claude Code'a gider. Claude Code kendi başına mimari karar almaz.

---

## 2. Bir Fazın Akışı

```
1. Sohbette faz açılır
   → ROADMAP'teki maddeler gözden geçirilir
   → varsa belirsizlikler netleştirilir
   → Claude Code promptu üretilir

2. Kullanıcı branch açar
   git checkout -b feat/phase-N-kisa-ad

3. Prompt Claude Code'a yapıştırılır
   → CC kısa açıklama yapar, kodu yazar, derler, özet verir

4. Kullanıcı elle test eder
   → CC'nin söylediği test adımları uygulanır

5. Commit
   → küçük adımlarda birkaç adımda bir
   → faz sonunda mutlaka

6. Sonuç sohbete bildirilir
   → sorun varsa düzeltme promptu üretilir
   → sorun yoksa PROGRESS.md güncellenir, faz kapanır, merge
```

---

## 3. Prompt Formatı

Sohbette üretilen her Claude Code promptu şu iskeleti taşır:

```
[FAZ N — Başlık]

BAĞLAM
Neyi neden yapıyoruz, 1-2 cümle.

GÖREVLER
1. ...
2. ...

KURALLAR
- CLAUDE.md ve docs/ARCHITECTURE.md'ye uy
- (varsa faza özel kısıtlar)

DOKUNMA
- (kapsam dışı bırakılan dosyalar)

DOĞRULAMA
- Derle, --rerun-tasks ile zorla
- Bana elle test etmem gereken adımları söyle
```

Prompt kapsamı dar tutulur. "Room'u kur ve UI'a bağla" gibi iki fazlık
istek tek promptta verilmez.

---

## 4. Sohbete Rapor Formatı

Claude Code'un çıktısını sohbete getirirken şu üçü yeterli:

1. CC'nin özet mesajı
2. Elle test sonucu: çalıştı / şu hatayı aldım
3. Takıldığın veya anlamadığın nokta

Tüm kod diff'ini yapıştırmaya gerek yok; gerekirse istenecek.

---

## 5. Belge Bakımı

| Dosya | Kim günceller | Ne zaman |
|---|---|---|
| `PROJECT_SPEC.md` | Sohbet | Kapsam değişirse |
| `ARCHITECTURE.md` | Sohbet | Mimari karar değişirse |
| `ROADMAP.md` | Claude Code | Faz bitince işaretler |
| `PROGRESS.md` | Claude Code | Her faz sonunda kayıt ekler |
| `CLAUDE.md` | Sohbet | Çalışma kuralı değişirse |

Belgeler kodla çelişirse **belge güncellenir** — kod belgeye uydurulmaz,
çünkü gerçek koddur. Ama çelişki fark edilir edilmez kapatılır.

---

## 6. Git Düzeni

- `main` — daima çalışan sürüm
- `feat/phase-N-*` — faz branch'i, bitince merge
- Commit: Conventional Commits, İngilizce, imperative
- Küçük adımlarda birkaç adımda bir commit; büyük/riskli adımdan sonra mutlaka
- İçerik taşımayan commit atılmaz

Faz merge edildikten sonra branch silinebilir.

---

## 7. Takılma Durumları

**Claude Code bir şeyi yanlış anladıysa:** Düzeltme promptu yaz, sıfırdan
başlatma. `git diff` ile ne yaptığını gör, gerekirse `git restore`.

**İki faz birbirine karıştıysa:** Branch'i bırak, `main`'e dön, fazı daha
küçük parçalara bölüp yeniden başla.

**Bir kavramı anlamadıysan:** Claude Code'a değil, sohbete sor. CC'nin işi
kod yazmak, kavram anlatmak değil.

**Belgelerde olmayan bir karar gerekiyorsa:** CC durup soracak. Cevabı
sohbette üret, önce belgeye ekle, sonra CC'ye ilet.
