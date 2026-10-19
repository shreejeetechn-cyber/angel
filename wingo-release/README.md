# WINGO v5.13.1 — Dhan Login

पूरा संशोधित सोर्स और Windows build kit।

## डाउनलोड

[WINGO_v5.13.1_FULL_SOURCE.zip](v5.13.1/WINGO_v5.13.1_FULL_SOURCE.zip) खोलें, फिर GitHub का **Download raw file** बटन दबाएँ। ZIP के अंदर source folder `WINGO_v5.13.1` है।

- [हिंदी बिल्ड और Dhan login निर्देश](v5.13.1/WINGO_v5.13.1_README_HINDI.txt)
- [परीक्षण और बिल्ड स्थिति](v5.13.1/WINGO_v5.13.1_BUILD_STATUS_HINDI.txt)
- [विस्तृत परीक्षण रिपोर्ट](v5.13.1/WINGO_v5.13.1_VALIDATION.json)
- [फाइल चेकसम](v5.13.1/WINGO_v5.13.1_SHA256SUMS.txt)
- [बदलावों की सूची](v5.13.1/WINGO_v5.13.1_CHANGES.json)
- [सोर्स diff](v5.13.1/WINGO_v5.13.1_PATCH.diff)
- [ZIP सत्यापन रिपोर्ट](v5.13.1/WINGO_v5.13.1_ARTIFACT_VERIFICATION.json)
- [आगे की incremental version नीति](v5.13.1/WINGO_v5.13.1_RELEASE_POLICY_HINDI.txt)

ZIP SHA256:

```text
99e13dca4e2b01f31708643332c91caed6761b0fc4a5047d358c3d516414e890
```

18/18 QA समूह सफल हुए। इनमें 20 नए offline Dhan auth tests और वास्तविक Qt GUI/settings/background-worker परीक्षण शामिल हैं। ZIP की CRC, हर manifest file का hash और परीक्षण किए गए सोर्स से मिलान सफल हुआ। मूल backup सुरक्षित है।

## Dhan login

पुराना WINGO/Core बंद करें। नया ZIP अलग फ़ोल्डर में extract करके VS Code में वह फ़ोल्डर खोलें जिसमें `unified_main.py` है। Requirements उसी Python environment में स्थापित हों। Terminal में:

```powershell
python unified_main.py --initial-mode desktop
```

Authentication → **DHAN LOGIN**, या Settings → **Broker Login → DHAN LOGIN** चुनें। अपना Dhan Client ID और आधिकारिक Dhan account से बना access token दें, फिर **VALIDATE & SAVE** दबाएँ। **CHECK SAVED LOGIN** और **RENEW TOKEN** भी उपलब्ध हैं। Token/account validation background में होती है और गलत account/token पुराना saved login नहीं बदलता।

पुराने R1 और Dhan GUI R2 ZIP इतिहास के लिए सुरक्षित हैं। वर्तमान रिलीज़ **v5.13.1** इस्तेमाल करें। हर अगली रिलीज़ का नया numeric version और नया folder/file नाम होगा; अगली patch रिलीज़ **v5.13.2** होगी।

यह v5.13 मूल सोर्स पर आधारित **v5.13.1 source/build kit** है। Windows EXE/installer का संकलन यहाँ नहीं हुआ है। Windows build scripts और manual GitHub Actions workflow ZIP के अंदर हैं; वे VERSION.txt से version पढ़ते हैं। Windows UAC/DPAPI, वास्तविक broker login तथा v5.14.0.16 पर सत्यापन बाकी है।
