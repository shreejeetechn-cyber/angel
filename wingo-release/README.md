# WINGO v5.13.3 — Centralized Login + Dhan PIN/TOTP Login

पूरा संशोधित सोर्स और Windows build kit।

## डाउनलोड

[WINGO_v5.13.3_FULL_SOURCE.zip](v5.13.3/WINGO_v5.13.3_FULL_SOURCE.zip) खोलें, फिर GitHub का **Download raw file** बटन दबाएँ। ZIP के अंदर source folder `WINGO_v5.13.3` है।

- [हिंदी बिल्ड और Dhan login निर्देश](v5.13.3/WINGO_v5.13.3_README_HINDI.txt)
- [परीक्षण और बिल्ड स्थिति](v5.13.3/WINGO_v5.13.3_BUILD_STATUS_HINDI.txt)
- [विस्तृत परीक्षण रिपोर्ट](v5.13.3/WINGO_v5.13.3_VALIDATION.json)
- [फाइल चेकसम](v5.13.3/WINGO_v5.13.3_SHA256SUMS.txt)
- [बदलावों की सूची](v5.13.3/WINGO_v5.13.3_CHANGES.json)
- [सोर्स diff](v5.13.3/WINGO_v5.13.3_PATCH.diff)
- [ZIP सत्यापन रिपोर्ट](v5.13.3/WINGO_v5.13.3_ARTIFACT_VERIFICATION.json)
- [आगे की incremental version नीति](v5.13.3/WINGO_v5.13.3_RELEASE_POLICY_HINDI.txt)

ZIP SHA256:

```text
9255a7114b843a3d1f7e09240d3558a08a8c445bb31175589b613d84dba148e7
```

19/19 QA समूह सफल हुए। 14 नए centralized-login regressions भी सफल हैं। इनमें 33 offline Dhan auth tests और वास्तविक Qt GUI/settings/background-worker परीक्षण शामिल हैं। ZIP की CRC, हर manifest file का hash और परीक्षण किए गए सोर्स से मिलान सफल हुआ। मूल backup सुरक्षित है।

## केंद्रीय login

हर source version एक ही `C:\Wingo\.env` इस्तेमाल करता है जब source folders `C:\Wingo\` के नीचे हों। सामान्य mode **CENTRAL** है; पुराना `WINGO_ENV_FILE` test override इसे दूसरी file की ओर नहीं मोड़ता। Authentication में पूरी effective file path दिखती है। Validate/save/renew उसी shared file में जाते हैं और अन्य broker settings सुरक्षित रहती हैं।

VS Code में नया folder खोलकर सामान्य command चलाएँ या **WINGO v5.13.3 — Central Login** configuration से F5 दबाएँ। QA के लिए अलग **ISOLATED** mode अनिवार्य है।

## Dhan login

पुराना WINGO/Core बंद करें। नया ZIP अलग फ़ोल्डर में extract करके VS Code में वह फ़ोल्डर खोलें जिसमें `unified_main.py` है। Requirements उसी Python environment में स्थापित हों। Terminal में:

```powershell
python unified_main.py --initial-mode desktop
```

Authentication → **DHAN LOGIN**, या Settings → **Broker Login → DHAN LOGIN** चुनें। केंद्रीय `.env` में `DHAN_CLIENT_ID` रखें। **DHAN LOGIN → PIN / TOTP Login** खोलें, छह-अंकीय Dhan PIN और authenticator का वर्तमान छह-अंकीय TOTP दें, फिर **LOGIN WITH PIN / TOTP** दबाएँ। Token generation और account/profile validation सफल होने पर नया token उसी केंद्रीय file में सहेजा जाता है। Access token paste करना जरूरी नहीं है। PIN/TOTP किसी `.env` में सहेजे नहीं जाते और submit, tab change, close/hide तथा success/failure पर साफ होते हैं। **Token Management** में optional manual token validation/save उपलब्ध है। **CHECK SAVED LOGIN** और **RENEW TOKEN** भी उपलब्ध हैं। Token/account validation background में होती है और गलत account/token पुराना saved login नहीं बदलता।

पुराने R1, Dhan GUI R2, v5.13.1 और v5.13.2 ZIP इतिहास के लिए सुरक्षित हैं। वर्तमान रिलीज़ **v5.13.3** इस्तेमाल करें। हर अगली रिलीज़ का नया numeric version और नया folder/file नाम होगा; अगली patch रिलीज़ **v5.13.4** होगी।

यह v5.13 मूल सोर्स पर आधारित **v5.13.3 source/build kit** है। Windows EXE/installer का संकलन यहाँ नहीं हुआ है। Windows build scripts और manual GitHub Actions workflow ZIP के अंदर हैं; वे VERSION.txt से version पढ़ते हैं। Windows UAC/DPAPI, वास्तविक broker login तथा v5.14.0.16 पर सत्यापन बाकी है।
