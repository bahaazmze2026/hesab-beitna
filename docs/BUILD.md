# بناء التطبيق وتوقيعه

## المتطلبات

- Android Studio بإصدار يدعم Android Gradle Plugin 8.7.3.
- JDK 17. استخدام JDK 17 هو مسار البناء المرجعي، حتى لو توفرت Java 21.
- Gradle 8.9.
- Android SDK Platform 35 وAndroid SDK Build Tools 35.0.0.
- SDK Platform Tools لتثبيت APK باستخدام adb، أو تثبيت الملف مباشرة من الهاتف.
- اتصال HTTPS بخوادم Google Maven وMaven Central وGradle في أول بناء.

إصدارات Kotlin وCompose والاعتماديات محددة داخل ملفات Gradle. لا يحتاج التطبيق اتصالًا بالإنترنت أثناء الاستخدام.

## إعداد Android Studio

1. افتح مجلد المشروع `hesab-beitna`.
2. ثبت SDK Platform 35 وBuild Tools 35.0.0 من SDK Manager.
3. اختر JDK 17 في إعدادات Gradle.
4. ضع مسار SDK في ملف محلي غير مرفق بالتسليم:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

5. نفذ المزامنة ثم الاختبارات والبناء. يجب إصلاح أي خطأ تجميع أو lint قبل اعتماد APK.

## المشغل المحمول

بسبب تعذر تنزيل Gradle في بيئة الجلسة، لا يحتوي المشروع الآن على ملف `gradle-wrapper.jar` الرسمي. ملف `gradlew` المرفق **مشغل محمول مخصص** ينزل التوزيعة الرسمية Gradle 8.9 ويتحقق من SHA-256 المنشور عبر HTTPS، أو يستخدم Gradle المثبت محليًا. عند استخدام Gradle مثبت يجب التأكد من أنه 8.9.

لإنشاء Wrapper الرسمي بعد توفير الأدوات:

```sh
gradle wrapper --gradle-version 8.9 --distribution-type bin
```

هذا يولد المشغلات وملف JAR الرسمي ويستبدل المشغل المحمول. على Windows يمكن تثبيت Gradle 8.9 وتشغيل أوامر `gradle` مباشرة ثم إنشاء Wrapper الرسمي.

## APK تجريبي

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

الناتج المتوقع:

```text
app/build/outputs/apk/debug/app-debug.apk
```

لتثبيته على هاتف اختبار مع تفعيل USB debugging:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

أو انقل الملف للهاتف وافتحه واسمح بالتثبيت من مصدر الملف. لا تحتاج حساب Google Play.

## توقيع release بمفتاح المالك

أنشئ المفتاح مرة واحدة واحفظه وكلمات مروره خارج المستودع. الأمر التالي يطلب كلمات المرور تفاعليًا ولا يضعها في سطر الأوامر:

```sh
keytool -genkeypair -keystore /safe/location/hesab-beitna-owner.jks -alias owner -keyalg RSA -keysize 3072 -validity 10000
```

أنشئ `signing.properties` محليًا في جذر المشروع، دون إضافته إلى Git:

```properties
storeFile=/safe/location/hesab-beitna-owner.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=owner
keyPassword=YOUR_KEY_PASSWORD
```

ثم:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleRelease
```

إذا لم يوجد ملف التوقيع، ناتج release سيكون غير موقع ولا يصلح للتسليم كتطبيق قابل للتثبيت. لا تستخدم مفتاح debug كتوقيع إصدار نهائي.

تحقق من التوقيع والبصمة:

```sh
"$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner" verify --verbose app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

احفظ مفتاح المالك لإصدار تحديثات مستقبلية. APK الموقع بمفتاح مختلف لا يحدث التطبيق الموجود؛ حذف التطبيق للتبديل بين debug وrelease يحذف بياناته، لذا خذ نسخة احتياطية أولًا.

## البناء على GitHub Actions

المسار المرفق يبني debug ويشغل فحوص Java واختبارات Kotlin وlint. لا يشغل اختبارات الهاتف تلقائيًا. لإنتاج release أضف أسرار المستودع التالية عبر واجهة GitHub، دون إدراج قيمها في ملفات الكود:

- `ANDROID_KEYSTORE_B64`: ملف توقيع المالك مشفرًا تمثيليًا بصيغة Base64؛ Base64 ليس تشفيرًا أمنيًا.
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

لا يُنشأ مفتاح مالك مؤقت في المسار الآلي. لا تحتوي حزمة المصدر على مفاتيح توقيع أو كلمات مرور.

## بوابة التسليم

قبل تسليم APK نهائي يجب:

1. نجاح التجميع واختبارات الوحدات وlint.
2. تشغيل `connectedDebugAndroidTest` على محاكي أو هاتف اختبار.
3. مراجعة عربية RTL وخط كبير على Samsung S25 Ultra أو جهاز مكافئ.
4. اختبار التثبيت النظيف وتحديث إصدار بنفس المفتاح.
5. اختبار نسخة احتياطية واستعادة على جهاز آخر، والقفل والإشعارات وCSV وPDF.
6. التأكد من عدم وجود إذن الإنترنت في manifest المدمج، وليس المصدر فقط.
7. التحقق من توقيع release وبصمة SHA-256، وتسجيل النتائج.

لم تُنجز هذه البوابة في بيئة الجلسة الحالية.
