# MPV

### MPV 0.20.0

- Sort keeps one stable sheet size while Card columns and Fields expand; List and Card retain independent field selections. Card defaults favour thumbnails, duration and counts, with paths off.
- Player pane transitions prevent outgoing pages from accepting late taps; scrollbars handle variable-height content and invalid input safely.
- Colour Accent gains directional edge masks. Shared dialogs use their actual option icon without a backing tile and a restrained reflective rim.
- Advanced → Backup & Restore exports typed XML and validates the entire import before one atomic commit. Credentials and vault contents are excluded.
- Secure Folder (Android 6+) uses a six-digit PIN, a device-bound Android Keystore wrapping key, encrypted names and authenticated 64 KB media chunks. It creates encrypted copies and preserves originals. Remember the PIN; vault data has no recovery or cloud backup. Private playback uses a temporary app-private file that is removed when the player finishes or the process next starts; it is excluded from recents/history, PiP, background playback and screenshots.
- Landscape Controls is a standalone settings page with per-region drag ordering, accessible move buttons and a live preview. Seekbar Style offers six direct radio choices and native previews.
- Home playback actions appear as individual animated pills and account for system gesture insets. Instagram opens the installed app or its website.
- About and startup preparation have refreshed layouts; the existing measured warmup and readiness checks remain in use.


مشغل Android مشتق من [mpvKt](https://github.com/abdallahmehiz/mpvKt)، بمحرك
`io.github.abdallahmehiz:mpv-android-lib:0.1.9` مع تصحيح أصلي لـPause وواجهة Compose بألوان ليلية ونعناعية.
المصدر الأصلي وترخيص Apache-2.0 محفوظان في `NOTICE` و`LICENSE`.

## ما تغير في 0.19.0

- توحيد تمدد كروت المشغل مع الإعدادات، ومفتاح ترجمة أكثر استدارة يليه شريط أدوات من أربعة أزرار.
- حقول موحدة بارتفاع 48dp، ونتائج عناوين عمودية، وأدوات موسم وحلقة تتحرك مع قائمة نتائج الترجمة.
- ستة أنماط لشريط التقدم في Landscape Controls، مع الاحتفاظ بمسار السحب وإمكانية الوصول.
- فرز حسب المدة وخيارات قائمة/شبكة وأعمدة تلقائية أو مخصصة، وحقول معلومات متعددة في صف قابل للتمرير بحواف متلاشية.
- أيقونات دلالية لخيارات الإعدادات من نفس Iconsax Outline/Broken، مع الحفاظ على رسمة مجلدات الصفحة الرئيسية.
- زر تشغيل عائم لفتح ملف أو رابط أو السجل الحديث، وربط السجل بخيار مسح سجل التشغيل.

## ما تغير في 0.18.0

- نقل أدوات ضبط الترجمة إلى أعلى اللوحة، مع زر مدمج لإضافة ملفات الترجمة الخارجية.
- مفتاح Primary / Secondary بمؤشر متحرك وانتقال خفيف لقائمة المسارات.
- بحث الترجمة على مراحل: حقول البحث واللغة والفلاتر، ثم شبكة الملصقات، ثم نتائج الترجمة.
- تعرض المسلسلات اختيار الموسم والحلقة أعلى النتائج مع بحث صريح، وتُحمّل ترجمات الفيلم عند اختياره.
- رجوع موحد بين مراحل البحث مع حفظ نتائج العناوين وإلغاء الطلبات القديمة.

## ما تغير في 0.17.4

- تصغير دوائر Colour Accent من 76 إلى 56dp مع حلقة تحديد أنحف ومسافات متوازنة، مع الحفاظ على مساحة لمس مريحة.

## ما تغير في 0.17.3

- عرض Colour Accent كدوائر مقسمة في صف أفقي قابل للتمرير داخل إعدادات المظهر.
- حلقة تحديد متحركة وتطبيق فوري للون، مع معاينات تتوافق مع الوضع الفاتح والداكن.

## ما تغير في 0.17.1

- استعادة مكتبة mpvKt السابقة وإزالة اعتماد gyrolet.
- تصحيح OpenSLES ليحفظ الصوت المخزن عند Pause ويستأنفه دون مسح العينات، مع معالجة Seek أثناء Pause والاستئناف بعد انقطاع تغذية الصوت.
- إعادة بناء ملفَي libmpv لمعمارَي ARM فقط، مع إبقاء FFmpeg وطبقة JNI وباقي AAR الأصلية دون تغيير.
- تحقق أصلي بلغة C من فقدان المخزن القديم وحفظه بعد التصحيح، إضافة إلى اختبارات التطبيق والفحوص المعتادة.
- استعادة الحد الأدنى Android 5.0 (API 21).
- [تفاصيل التنفيذ والاختبارات وحدودها](docs/PAUSE-RESTORE-0.17.md)، و[مصادر التصحيح وتعليمات إعادة البناء](third_party/mpv-pause/README.md).

## ما تغير في 0.16

- استبدال مكتبة المحرك القديمة بالمكتبة الرسمية المستخدمة في mpvRx، مع تثبيت الإصدار والتحقق من بصمة AAR.
- اشتراكات خصائص مستقرة عند إعادة فتح المحرك، وتنظيف Surface والمراقبين عند إغلاقه.
- الحفاظ على الواجهة وإعدادات المشغل والأيقونات ومنطق تأكيد Pause.
- يتطلب هذا المحرك Android 7.0 (API 24) فأحدث.
- [تفاصيل التوافق والتحقق وحدود الاختبارات](docs/MPVLIB-MIGRATION-0.16.md).

## ما تغير في 0.10

- صفحة تقديم مع صلاحيات الفيديوهات، وتجهيز الأيقونات وتجميع المجلدات في الخلفية قبل الدخول.
- ذاكرة Vector مشتركة لتجنب إعادة تحليل الأيقونات عند فتح الواجهات واللوحات؛ مع تجهيز محدود للصور المصغرة.
- Quick Menu بألوان وحواف الصورة المرجعية، وبداية الحركة بعد القياس، وحدود آمنة للنوافذ الصغيرة.
- Header ثابت مع الأسماء الطويلة، وبحث يتوسع ويتلاشى بخفة، وحفظ موضع التمرير عند الرجوع.
- تحريك ترتيب النتائج، وتمدد عند نهاية القائمة، ولوحة مشغل أعرض قليلًا مع ارتداد مخمد وفصل القياس عن بدء الدخول.
- [سبب التقطع المحتمل والتفاصيل والفحوص وحدود القياس](docs/STARTUP-AND-MOTION-0.10.md).

## ما تغير في 0.9

- Quick Menu للفرز كما في المعاينة المعتمدة: دون إطار أو علامة صح، بمؤشر مستدير وكروت تحديد متناسقة.
- Title وDate وSize وCount مع اتجاه A–Z / Z–A وحفظ الاختيار. Count للمجلدات، وSize يجمع أحجام فيديوهاتها.
- انزلاق وتلاشي قصيران لقائمة الفرز، وارتداد مخمد وخفيف لدخول Slide Panel مع إبقاء عرضه وأشرطته الحالية.
- [تفاصيل المعاني والحركات والتحقق](docs/SORT-AND-MOTION-0.9.md).

## ما تغير في 0.8.1

- لوحة جانبية أعرض قليلًا ودخول متدرج دون اهتزاز الارتداد.
- حاوية ثابتة: Filters وتأخير الصوت وإعدادات الترجمة ومؤقت النوم تغيّر المحتوى داخل اللوحة نفسها، مع انتقال أفقي قصير.
- الرجوع من الخيار الفرعي يعيد القائمة السابقة وموضعها وتبويبها؛ الإغلاق يمسح سجل اللوحة بالكامل.
- [التفاصيل والفحص](docs/PLAYER-PANE-0.8.1.md).

## ما تغير في 0.8

- تمدد مرن لعناوين الإعدادات، وتحديث للمجلدات والفيديوهات مع السحب والإفلات.
- مسار موحّد للتحكم بالتشغيل خارج خيط الواجهة، مع حركة Play/Pause قصيرة، ومعالجة تعارض التقديم والتركيز الصوتي والضغطات السريعة.
- لوحات More وSubtitle وAudio جانبية من اليمين، بعناوين ثابتة وتمرير رفيع وترتيب يحفظ الخيارات الحالية والترجمة المزدوجة.
- تصحيح مراقبة موضع الفيديو بعد الخلفية وإعادة إنشاء المحرك، وإغلاق اللوحات الفرعية عند دخول PiP.
- [تقرير التنفيذ والمشاكل المعالجة والتحقق](docs/UI-PLAYBACK-0.8.md).

## الأيقونات المحفوظة

- **Iconsax Outline** افتراضيًا، مع اختيار **Broken** من Settings → Appearance → Icon style. يُحفظ الاختيار ويطبَّق على الواجهة والمشغل واللوحات والإشعارات وPiP.
- كتالوج Vector واحد مشترك بين Compose وموارد Android، دون خط أو مكتبة أيقونات كاملة.
- المجلد في الرئيسية ثابت: نفس الرسم واللون والحجم 88×76dp، خارج التبديل بين النمطين.
- أُزيل نظام Font Awesome وكل رسوماته الأخرى، مع إبقاء نسبة مصدر رسم المجلد المحفوظ وترخيصه وحده.
- [تفاصيل التنفيذ والتحقق والحجم](docs/ICONS-0.7.md) و[تقرير سلوك المشغل السابق](docs/PLAYER-AUDIT-0.6.md).

## بناء APK دون Secrets

من تبويب **Actions → Build MPV APKs → Run workflow**، أو عند push/pull request،
يعمل [.github/workflows/build-apk.yml](.github/workflows/build-apk.yml) على:

1. تجهيز JDK 17 وAndroid SDK 36 وBuild Tools 35.
2. التحقق من كتالوج الأيقونات وثبات رسم المجلد، ثم تشغيل `verifyMpvRuntime testDebugUnitTest lintDebug assemblePreview`.
3. تشغيل اختبار انحدار Pause الأصلي والتحقق من توقيع APK ومكتبات `.so` لكل معمارية.
4. رفع artifacts للمعماريات الثلاث، ونشر APKs مباشرة في Release عند دفع وسم إصدار، دون ملف SHA256SUMS.txt.

لا توجد أي مراجع إلى `secrets` في YAML. نسخ Preview قابلة للتثبيت بتوقيع debug تلقائي،
ومعرفها `io.github.jntynho.luma.preview`. لا تحتاج المتاجر أو GitHub Releases إلى تفعيل لاستخدام artifacts.
**هذه ليست نسخ نشر إنتاجية:** المفتاح التجريبي قد يتغير بين تشغيلات CI، لذلك قد تحتاج إزالة النسخة السابقة
قبل تثبيت نسخة جديدة. النشر الرسمي بتحديثات ثابتة يحتاج مفتاح توقيع دائمًا وخطة توزيع مستقلة.

## التطوير المحلي

يتطلب JDK 17، Android SDK platform 36 وbuild-tools 35.0.0، واتصالًا بـGoogle Maven وMaven Central
وGradle وGitHub لتنزيل المحرك الرسمي. تم تضمين مصدر Seeker وFSAF بإصدارات محددة وتراخيصهما داخل third_party، لذلك لا حاجة إلى JitPack. استخدم checkout الموجود مباشرة، دون إنشاء worktree إضافي.

```sh
chmod +x gradlew
./gradlew --no-daemon --max-workers=1 verifyMpvRuntime testDebugUnitTest lintDebug assemblePreview
```

توجد ملفات APK تحت `app/build/outputs/apk/preview/` بعد نجاح البناء.
لا حاجة إلى API keys أو خدمات خارجية لتشغيل الوسائط المحلية. الحد الأدنى Android 5.0 (API 21).

## حالة التحقق

الإصدار 0.17.1 يستخدم المكتبة السابقة مع تصحيح أصلي لمسار Pause/Play. يتحقق البناء من بصمة المكتبة الأصلية، ومن اقتصار الاستبدال على ملفَي libmpv لمعمارَي ARM، ومن غياب اعتماد gyrolet. يتحقق اختبار C من حفظ مخزن الصوت وساعته ومن استئناف الصوت بعد Seek أثناء Pause.

اجتازت النسخة **138 اختبارًا** وLint دون أخطاء، إضافة إلى اختبار الانحدار الأصلي. نجح بناء APKs الثلاث وفحص التوقيع ومحاذاة ZIP وربط JNI والمعماريات. بقي رسم المجلد والأيقونات Outline/Broken كما هي.

لا يوجد هاتف أو محاكي متصل؛ اختبارات C تستخدم جهازًا وساعة مصطنعين، واختبارات الرسم تستخدم Robolectric. يلزم تأكيد النتيجة بتجربة تشغيل فعلية على الهاتف. [الفحوص والحدود بالتفصيل](docs/PAUSE-RESTORE-0.17.md).

## GitHub Releases

عند دفع وسم يبدأ بـ `v`، ينشئ workflow إصدار GitHub Release بعد نجاح البناء والفحوص،
ويرفق فقط `arm64-v8a.apk` و`armeabi-v7a.apk` و`universal.apk` مباشرةً دون ZIP أو SHA256SUMS.txt. لا تحتاج إضافة Secrets؛
يستخدم النشر رمز GitHub التلقائي بصلاحية كتابة محتويات المستودع.
توقيع APK يظل توقيع اختبار تلقائيًا؛ إنشاء GitHub Release لا يحوله إلى مفتاح إنتاج دائم.

[تفاصيل أيقونات Iconsax والتبديل بين النمطين](docs/ICONS-0.7.md).
