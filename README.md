# MPV

مشغل Android مشتق من [mpvKt](https://github.com/abdallahmehiz/mpvKt)، بمحرك
`io.github.abdallahmehiz:mpv-android-lib:0.1.9` نفسه، وواجهة Compose جديدة بألوان ليلية ونعناعية.
لم نغيّر إصدار المحرك دون اختبار توافقه. المصدر الأصلي وترخيص Apache-2.0 محفوظان في `NOTICE` و`LICENSE`.

## ما تغير في 0.6

- استبدال جميع أيقونات التطبيق والمشغل وخياراته وPiP والإشعارات والمجلدات بأيقونات **Font Awesome Free Classic Solid 7.3.1**، بما فيها أيقونة التطبيق. لا Regular أو Pro.
- إصلاح اختيار اتجاه الهاتف، وزاوية دوران الفيديو، وزر التدوير، وإعادة ضبط Stretch بعد تغير أبعاد السطح.
- إصلاح استعادة السرعة وتأخير الترجمة والصوت، وسباق استعادة حالة الفيديو السابق، والتعامل مع Intents وheaders غير المكتملة.
- إصلاحات PiP وتركيز الصوت وخدمة تشغيل الخلفية وجلسة الوسائط ولوحات الصوت والترجمة.
- [لوحة جميع الأيقونات](docs/fontawesome-solid-preview.png) و[تقرير المشاكل والإصلاحات والقيود المتبقية](docs/PLAYER-AUDIT-0.6.md).
- الإبقاء على الرجوع الموحد والحركة الأفقية والتدرج السفلي من الإصدار السابق.

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

نجح البناء المحلي والاختبارات الـ15 وLint دون أخطاء، مع بقاء تحذيرات. تشمل الاختبارات منطق الدوران وPiP واستعادة التشغيل وheaders وdecoder، وحسابات الإيماءات والأيقونات ومدد الفيديو وتقسيم شريط التقدم. تفاصيل نطاق التحقق وقيوده في تقرير التدقيق.
تم التحقق من توقيع APK ومحاذاة 16KB ومطابقة أقسام الكود التنفيذي والرموز الديناميكية لمكتبات mpv الأصلية العشرين بعد تجريد رموز التصحيح.

قياس الإصدار 0.4 — APK ‏ARM64: ‏24.42 → 21.83 MB. ‏ARM32: ‏23.66 → 21.16 MB. النسخة الشاملة ARM فقط: ‏34.06 MB.
[التقرير الكامل وحدود الاختبار](docs/UI-0.4.md). لا يوجد هاتف أو محاكي متصل؛ التشغيل الفعلي والإيماءات والصور المصغرة تحتاج اختبار جهاز. الملفات التالفة أو الترميزات التي لا يستخرج Android صورها ستعرض رمزًا بديلًا.

قياس البناء المحلي 0.6: ‏ARM64 ‏20.23 MB، ‏ARM32 ‏19.55 MB، والشاملة ‏32.45 MB.

## GitHub Releases

عند دفع وسم يبدأ بـ `v`، ينشئ workflow إصدار GitHub Release بعد نجاح البناء والفحوص،
ويرفق ملفات APK مباشرةً دون ZIP، مع ملف SHA256SUMS.txt. لا تحتاج إضافة Secrets؛
يستخدم النشر رمز GitHub التلقائي بصلاحية كتابة محتويات المستودع.
توقيع APK يظل توقيع اختبار تلقائيًا؛ إنشاء GitHub Release لا يحوله إلى مفتاح إنتاج دائم.

[معاينة أيقونات Font Awesome Solid الحالية](docs/fontawesome-solid-preview.png). هذه لوحة للرسومات الفعلية المستخدمة وليست لقطة تشغيل Android.
