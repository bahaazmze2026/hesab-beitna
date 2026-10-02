# بناء التطبيق وتوقيعه

## الأدوات

JDK 17، Android SDK Platform 35، Build Tools 35.0.0، وGradle 8.9. يستخدم المشروع AGP 8.7.3 وKotlin 2.0.21. افتح المجلد في Android Studio واختر JDK 17 وثبت SDK المطلوب. ضع مساره في local.properties خارج Git:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

أول بناء يحتاج الإنترنت لتنزيل الاعتماديات؛ التطبيق نفسه يعمل محليًا دون إذن إنترنت.

المشروع يتضمن Gradle Wrapper الرسمي: gradlew وgradlew.bat وgradle/wrapper/gradle-wrapper.jar من Gradle v8.9.0. بصمة JAR SHA-256:

```text
498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17
```

## الاختبارات والبناء

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
./gradlew --no-daemon connectedDebugAndroidTest
```

الأمر الثاني يحتاج محاكيًا أو هاتف اختبار متصلًا. على Windows استخدم gradlew.bat. APK التجريبي في app/build/outputs/apk/debug/app-debug.apk. في الإصدار 1.1.0 يحمل معرّف com.hesabbeitna.app.preview ويُثبّت بجانب التطبيق الأصلي دون استبدال بياناته. release يحتفظ بالمعرّف الأصلي.

## توقيع التحديثات

استخدم owner.jks وكلمة المرور من حزمة المالك الخاصة المستلمة. لا تنشئ مفتاحًا آخر لتحديث التطبيق الموجود. احفظ الحزمة خارج المستودع، ولا تنشرها ولا كلمة مرورها.

أنشئ signing.properties محليًا في جذر المشروع، وهو مستبعد من Git:

```properties
storeFile=/safe/location/owner.jks
storePassword=PASSWORD_FROM_PRIVATE_BUNDLE
keyAlias=owner
keyPassword=PASSWORD_FROM_PRIVATE_BUNDLE
```

ثم:

```sh
./gradlew --no-daemon assembleRelease
```

الناتج الموقع في app/build/outputs/apk/release/app-release.apk. دون signing.properties يكون release غير موقع. يمكن توقيع الملف غير الموقع باستعمال apksigner.jar من Build Tools:

```sh
java -jar /path/to/apksigner.jar sign --ks /safe/location/owner.jks --ks-pass file:/safe/location/owner-password.txt --ks-key-alias owner --out hesab-beitna-signed.apk app/build/outputs/apk/release/app-release-unsigned.apk
java -jar /path/to/apksigner.jar verify --verbose --print-certs hesab-beitna-signed.apk
```

كلمة مرور المفتاح هي كلمة مرور المخزن نفسها؛ يكفي تمرير ملفها مرة واحدة. بصمة شهادة المالك SHA-256:

```text
4b22d18570b9aa5075d32b2b984430bbb50a6c3b00396f6d357296ba3e0cacf0
```

## GitHub Actions

المسار يبني debug وrelease ويشغل فحوص Java والوحدات وlint ومحاكي API 35، ويرفع التقارير وصورة الاختبار. لإنتاج release موقع في CI أضف أسرار المستودع:

- ANDROID_KEYSTORE_B64: ملف owner.jks ممثلًا بـBase64؛ هذا التمثيل ليس تشفيرًا.
- ANDROID_STORE_PASSWORD
- ANDROID_KEY_ALIAS: owner
- ANDROID_KEY_PASSWORD

دون الأسرار ينتج APK release غير موقع. إنشاء مفتاح جديد تلقائيًا معطل. لا ترفع signing.properties أو المفاتيح الخاصة إلى Git. راجع SIGNING-RECOVERY.md لاستعادة حزمة المالك المشفرة عند الحاجة.

## التثبيت والتحقق

انقل APK الموقع للهاتف وافتحه، أو استخدم adb install -r مع USB debugging. تحديثات التطبيق تحتاج التوقيع نفسه وزيادة versionCode. حذف التطبيق يحذف البيانات؛ احفظ نسخة احتياطية قبل الحذف أو الانتقال من debug إلى release.

اجتاز الإصدار 1.1.0 التجميع والوحدات وlint وفحص manifest وتوقيع debug. تعذّر تشغيل المحاكي في هذه البيئة؛ اختبارات الواجهة المعدلة لم تُنفذ ولا توجد لقطات تشغيل للشاشات. تجربة الهاتف والاستعادة عبر منتقي الملفات وترقية تثبيت ببيانات كثيرة غير مختبرة. راجع brand-verification.md؛ تقرير VERIFICATION.md تاريخي ويخص 1.0.0.
