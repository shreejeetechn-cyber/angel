# WINGO v5.13 FIXPACK 20261019 — DHAN GUI R2

पूरा संशोधित सोर्स और Windows build kit।

## डाउनलोड

[नया पूरा सोर्स ZIP — DHAN GUI R2](dhan-gui-r2/WINGO_v5.13_FIXPACK_20261019_DHAN_GUI_R2_FULL_SOURCE.zip) खोलें, फिर GitHub का **Download raw file** बटन दबाएँ।

- [हिंदी बिल्ड और Dhan login निर्देश](dhan-gui-r2/README_FIXPACK_HINDI.txt)
- [परीक्षण और बिल्ड स्थिति](dhan-gui-r2/BUILD_STATUS_HINDI.txt)
- [फाइल चेकसम](dhan-gui-r2/SHA256SUMS.txt)
- [बदलावों की सूची](dhan-gui-r2/FIXPACK_CHANGES.json)
- [सोर्स diff](dhan-gui-r2/PATCH_FROM_V5_13.diff)
- [ZIP सत्यापन रिपोर्ट](dhan-gui-r2/ARTIFACT_VERIFICATION.json)

ZIP SHA256:

```text
601c277190adf342b1e8bbd361e8c3cea334a80c8ea0d4f71f4a4967c29ce508
```

18/18 QA समूह सफल हुए। इनमें 20 नए offline Dhan auth tests और वास्तविक Qt GUI/settings/background-worker परीक्षण शामिल हैं। ZIP की CRC, हर manifest file का hash और परीक्षण किए गए सोर्स से मिलान सफल हुआ। मूल backup सुरक्षित है।

## Dhan login

पुराना WINGO/Core बंद करें। नया ZIP अलग फ़ोल्डर में extract करके VS Code में वह फ़ोल्डर खोलें जिसमें `unified_main.py` है। Requirements उसी Python environment में स्थापित हों। Terminal में:

```powershell
python unified_main.py --initial-mode desktop
```

Authentication → **DHAN LOGIN**, या Settings → **Broker Login → DHAN LOGIN** चुनें। अपना Dhan Client ID और आधिकारिक Dhan account से बना access token दें, फिर **VALIDATE & SAVE** दबाएँ। **CHECK SAVED LOGIN** और **RENEW TOKEN** भी उपलब्ध हैं। Token/account validation background में होती है और गलत account/token पुराना saved login नहीं बदलता।

पहला R1 ZIP इतिहास के लिए इसी directory में उपलब्ध है; उसमें Dhan GUI login नहीं था। नया **DHAN GUI R2** ZIP इस्तेमाल करें।

यह v5.13 पर आधारित source/build kit है। Windows EXE/installer का संकलन यहाँ नहीं हुआ है। Windows build scripts और manual GitHub Actions workflow ZIP के अंदर हैं। Windows UAC/DPAPI, वास्तविक broker login तथा v5.14.0.16 पर सत्यापन बाकी है।
