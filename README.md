# MPV

مشغل Android مشتق من [mpvKt](https://github.com/abdallahmehiz/mpvKt)، بمحرك
`io.github.abdallahmehiz:mpv-android-lib:0.1.9` نفسه، وواجهة Compose جديدة بألوان ليلية ونعناعية.
لم نغيّر إصدار المحرك دون اختبار توافقه. المصدر الأصلي وترخيص Apache-2.0 محفوظان في `NOTICE` و`LICENSE`.

## ما تغير في 0.4

- الرئيسية تعرض مجلدات كبيرة ممتلئة مع مساراتها؛ داخلها فيديوهات بصور مصغرة ومسارات ومدد.
- الرأس يحتوي البحث والفرز والإعدادات فقط؛ أزيلت قائمة المزيد ووظائفها والتبويبات والأعداد والعبارة التعريفية.
- السحب للأسفل لتحديث المكتبة.
- انتقالات mpvKt الأصلية 220ms للدخول والرجوع، وحركة الرجوع التنبؤية الأصلية.
- حذف كود قديم غير مستخدم وتقليص الموارد، مع إبقاء محرك mpv ومكتبات الميزات المستخدمة.

[تفاصيل الفحص وتقليل الحجم وحدود التحقق](docs/UI-0.4.md).
معاينات التصميم السابقة تاريخية ولا تمثل الواجهة الحالية.

## بناء APK دون Secrets

من تبويب **Actions → Build MPV APKs → Run workflow**، أو عند push/pull request،
يعمل [.github/workflows/build-apk.yml](.github/workflows/build-apk.yml) على:

1. تجهيز JDK 17 وAndroid SDK 36 وBuild Tools 35.
2. تشغيل `testDebugUnitTest lintDebug assembleDebug assemblePreview`.
3. فحص توقيع APK ووجود مكتبات `.so` لكل معمارية.
4. رفع ملفات **MPV-arm64-64bit** و**MPV-armv7-32bit** ونسخة universal مع SHA-256.

لا توجد أي مراجع إلى `secrets` في YAML. نسخ Preview قابلة للتثبيت بتوقيع debug تلقائي،
ومعرفها `io.github.jntynho.luma.preview`. لا تحتاج المتاجر أو GitHub Releases إلى تفعيل لاستخدام artifacts.
**هذه ليست نسخ نشر إنتاجية:** المفتاح التجريبي قد يتغير بين تشغيلات CI، لذلك قد تحتاج إزالة النسخة السابقة
قبل تثبيت نسخة جديدة. النشر الرسمي بتحديثات ثابتة يحتاج مفتاح توقيع دائمًا وخطة توزيع مستقلة.

## التطوير المحلي

يتطلب JDK 17، Android SDK platform 36 وbuild-tools 35.0.0، واتصالًا بـGoogle Maven وMaven Central
وGradle. تم تضمين مصدر Seeker وFSAF بإصدارات محددة وتراخيصهما داخل third_party، لذلك لا حاجة إلى JitPack. استخدم checkout الموجود مباشرة، دون إنشاء worktree إضافي.

```sh
chmod +x gradlew
./gradlew --no-daemon --max-workers=2 testDebugUnitTest lintDebug assembleDebug assemblePreview
```

توجد ملفات APK تحت `app/build/outputs/apk/preview/` بعد نجاح البناء.
لا حاجة إلى API keys أو خدمات خارجية لتشغيل الوسائط المحلية. الحد الأدنى Android 5.0 (API 21).

## حالة التحقق

نجحت اختبارات Gradle الستة: اختبارا روابط الوسائط واختبار تقسيم شريط التقدم.
مكتبة mpv تتضمن ARM64 وARMv7، ومقاطع ELF لمكتبات ARM64 تدعم محاذاة 16KB.
نجح البناء المحلي الكامل: testDebugUnitTest وlintDebug وassembleDebug وassemblePreview. نجحت الاختبارات السبعة دون إخفاق، وLint دون أخطاء (مع تحذيرات منها الترجمة غير المكتملة). تم التحقق من توقيع APK ومحاذاة ZIP بمقدار 16KB لنسختي ARM64 وARMv7. راجع أيضًا آخر نتيجة GitHub Actions وتقارير MPV-validation-reports.
المحاولات السابقة كشفت مشاكل إعداد CI، وحظر JitPack محليًا؛ استُبدل الاعتماد عليه بمصدر المكتبتين.

لم يُختبر تشغيل الوسائط على جهاز أو محاكي. الصورة تصميم تصوري وليست لقطة تشغيل.
بعد نجاح البناء، اختبر فيديو محليًا وبثًا شبكيًا والترجمة والصوت والتدوير وPiP على ARM64 وARMv7.

## GitHub Releases

عند دفع وسم يبدأ بـ `v`، ينشئ workflow إصدار GitHub Release بعد نجاح البناء والفحوص،
ويرفق ملفات APK مباشرةً دون ZIP، مع ملف SHA256SUMS.txt. لا تحتاج إضافة Secrets؛
يستخدم النشر رمز GitHub التلقائي بصلاحية كتابة محتويات المستودع.
توقيع APK يظل توقيع اختبار تلقائيًا؛ إنشاء GitHub Release لا يحوله إلى مفتاح إنتاج دائم.

[معاينة الأيقونات الفعلية](docs/icons-preview.png). هذه لوحة للأيقونات المستخدمة وليست لقطة تشغيل Android.
