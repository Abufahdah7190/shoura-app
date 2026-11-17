# شورى

تطبيق اجتماعي لأندرويد مع خادم API. افتح هذا المجلد كاملًا في VS Code لتشغيل الخادم، وافتح `android_app` في Android Studio لبناء تطبيق الهاتف.

## تشغيل محلي من VS Code

1. ثبّت [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0).
2. افتح مجلد المشروع `shoura_app` في VS Code.
3. من قائمة **Terminal → Run Task** اختر **تشغيل خادم شورى محليًا**. أول تشغيل قد يحتاج اتصال NuGet لاستعادة الحزم.
4. افتح `http://localhost:5126/health` لفحص الخادم، و`http://localhost:5126/openapi/v1.json` لواجهة API، أو `http://localhost:5126/account-deletion.html` لصفحة حذف الحساب.

الخادم المحلي يستخدم SQLite تلقائيًا وينشئ قاعدة `backend/Shoura.API/shoura.db`. للاتصال من محاكي Android استخدم العنوان `http://10.0.2.2:5126/` المجهز لإصدار Debug. الهاتف الحقيقي يحتاج عنوان IP لجهاز التطوير على الشبكة نفسها، مع السماح للمنفذ 5126 في جدار الحماية. لا تستخدم HTTP خارج التطوير.

## قاعدة البيانات والتوسع

SQLite مخصص للتطوير المحلي فقط. أضفت خيار PostgreSQL، وترحيلات قاعدة البيانات، وفهرسًا للخلاصة، وتحميل المنشورات على صفحات. هذا أساس مناسب للنقل إلى خادم قاعدة بيانات مُدار وتشغيل أكثر من نسخة API، لكنه **ليس ضمانًا مختبرًا لـ100 ألف مستخدم متزامن**. عدد 100 ألف حساب مسجل يختلف عن 100 ألف اتصال نشط؛ يلزم اختبار ضغط على بيئة الإنتاج وتحديد قدرة الخادم وقاعدة البيانات وتكلفة الوسائط قبل إطلاقها بهذا الحجم.

لتجربة PostgreSQL عبر Docker (يجب تثبيت Docker Desktop):

```powershell
$env:SHOURA_POSTGRES_PASSWORD = "ضع-كلمة-مرور-محلية-قوية-هنا"
docker compose up -d postgres
$env:SHOURA_DATABASE_PROVIDER = "Postgres"
$env:SHOURA_CONNECTION_STRING = "Host=localhost;Port=55432;Database=shoura;Username=shoura;Password=$env:SHOURA_POSTGRES_PASSWORD"
$env:SHOURA_AUTH_SIGNING_KEY = "ضع-سرًا-عشوائيًا-طويلًا-هنا-لا-تشاركه"
dotnet run --project backend/Shoura.API/Shoura.API.csproj --no-launch-profile -- --urls http://localhost:5126
```

الوسائط حاليًا تحفظ على القرص المحلي؛ في الإنتاج تحتاج تخزينًا دائمًا مشتركًا أو خدمة ملفات كائنية خلف CDN. حد الرفع الحالي 100 ميغابايت. حدود الطلبات الحالية داخل الذاكرة لكل نسخة، ولذلك تحتاج حماية موزعة في بوابة API أو Redis عند تشغيل عدة نسخ. أسرار الإنتاج يجب أن تحفظ في مدير أسرار، لا في الملفات أو Git. ملف `compose.yaml` يشغل PostgreSQL للتطوير فقط ولا يمثل إعداد نشر إنتاجي.

### استخدام Supabase PostgreSQL

الخادم يستخدم PostgreSQL عبر Npgsql، لذلك يمكن توجيه قاعدة البيانات إلى Supabase:

1. أنشئ مشروعًا في Supabase، ومن **Connect** اختر سلسلة اتصال **.NET/Npgsql**. للخادم طويل التشغيل استخدم الاتصال المباشر إذا كانت بيئة الاستضافة تدعم IPv6، أو **Session pooler** عند الحاجة.
2. انسخ قيم الاتصال كما تظهر في لوحة التحكم، ولا تضعها في تطبيق Android أو Git. فعّل TLS وتحقق من شهادة الخادم وفق إعدادات Supabase.
3. على الخادم، عيّن المتغيرات التالية مع استبدال القيم ببيانات مشروعك:

```powershell
$env:SHOURA_DATABASE_PROVIDER = "Postgres"
$env:SHOURA_CONNECTION_STRING = "Host=اسم-المضيف;Port=5432;Database=postgres;Username=اسم-المستخدم-من-Supabase;Password=كلمة-المرور;SSL Mode=VerifyFull"
$env:SHOURA_AUTH_SIGNING_KEY = "سر-عشوائي-خاص-بالخادم-لا-تشاركه"
dotnet run --project backend/Shoura.API/Shoura.API.csproj --no-launch-profile -- --urls http://localhost:5126
```

الخادم يطبق ترحيلات EF Core عند بدء التشغيل. لم أختبر الاتصال على مشروع Supabase فعلي. نقل قاعدة البيانات لا ينقل ملفات الفيديو والصوت؛ يلزم ربط Supabase Storage أو تخزين ملفات دائم قبل النشر.

## بناء تطبيق أندرويد

1. ثبّت Android Studio مع JDK 17 وAndroid SDK Platform 36 وBuild Tools.
2. افتح `android_app` في Android Studio، ثم نفّذ Gradle Sync.
3. شغّل التطبيق على محاكي. لإنتاج حزمة المتجر استخدم مهمة Gradle `bundleRelease`، وحدد رابط HTTPS للخادم مثل `-PshouraApiUrl=https://api.your-domain.example/`.

إصدار التطبيق مضبوط على `compileSdk=36` و`targetSdk=36`، ونسخة Android الدنيا 24. يجب توقيع الإصدار بمفتاح رفع محفوظ بأمان ثم إعداد Play App Signing من Play Console. لا ترفع ملف المفاتيح أو كلمات المرور إلى Git.

## حالة متطلبات Google Play

أعددت مستوى API المطلوب حاليًا للتطبيقات الجديدة، وصفحة حذف حساب خارج التطبيق على `/account-deletion.html`، وخيار الحذف داخل التطبيق. الحذف يمسح سجل الحساب والمنشورات والرسائل من قاعدة البيانات. قبل النشر، اختبر تدفق الحذف وتأكد من حذف كل البيانات والوسائط وفق سياسة الخصوصية، واستضف صفحة الحذف على عنوان HTTPS عام وأدخل رابطها في Play Console.

التطبيق شبكة اجتماعية بمحتوى ينشئه المستخدمون؛ يلزم قبل النشر إضافة الإبلاغ عن المنشورات والمستخدمين، وحظر المستخدمين، ومراجعة المحتوى، وشروط الاستخدام وسياسة خصوصية منشورة. هذه الأدوات ليست مكتملة بعد. كما يجب إكمال نموذج Data safety، وتصنيف المحتوى، وصفحة المتجر، والتوقيع، وإعداد حساب المطوّر.

## خدمات لم تُربط بعد

- **Google Sign-In:** يحتاج مشروع Firebase باسم الحزمة `com.shoura.app`، تفعيل Google، بصمات التوقيع، ملف `google-services.json`، ومصادقة Firebase في الخادم. المراجع: [Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg) و[Firebase على Android](https://firebase.google.com/docs/auth/android/google-signin).
- **الإشعارات الفعلية:** خيار الإشعارات يحفظ التفضيل ويطلب إذن Android فقط. يلزم Firebase Cloud Messaging وتسجيل رموز الأجهزة وإرسالها من الخادم.
- **الموقع:** الإذن اختياري، ولا يقرأ التطبيق الإحداثيات حاليًا.
- **الذكاء الاصطناعي:** أضفت نقاط شرح الفيديو وترجمة الفويس عبر Gemini. عيّن `GEMINI_API_KEY` على الخادم فقط؛ حجم الملف المدعوم 10 ميغابايت ويطلب التطبيق موافقة قبل الإرسال. لم تختبر هذه الخدمة دون مفتاحك.

## الفحوص التي أجريت

بناء الخادم نجح عبر `dotnet build`، وبدأ التشغيل المحلي وطبّق ترحيل فهرس الخلاصة. تعذر فحص عناوين `localhost` من بيئة العمل بسبب تقييد اتصالات الشبكة، ولم تتوفر أدوات Android SDK وGradle وADB هنا؛ لذلك لم يتم بناء APK/AAB أو تجربة التطبيق على محاكي أو هاتف. تحذير استعادة NuGet الحالي متعلق بعدم الوصول إلى فهرس معلومات الثغرات، وليس خطأ بناء.
