# WINGO v5.13 FIXPACK 20261019

पूरा संशोधित सोर्स और Windows build kit।

## डाउनलोड

[WINGO_v5.13_FIXPACK_20261019_FULL_SOURCE.zip](WINGO_v5.13_FIXPACK_20261019_FULL_SOURCE.zip) खोलें, फिर GitHub का **Download raw file** बटन दबाएँ।

- [हिंदी बिल्ड निर्देश](README_FIXPACK_HINDI.txt)
- [परीक्षण और बिल्ड स्थिति](BUILD_STATUS_HINDI.txt)
- [फाइल चेकसम](SHA256SUMS.txt)
- [बदलावों की सूची](FIXPACK_CHANGES.json)
- [सोर्स diff](PATCH_FROM_V5_13.diff)
- [ZIP सत्यापन रिपोर्ट](ARTIFACT_VERIFICATION.json)

ZIP SHA256:

```text
7d07971a8e62a53747fb17a46cb60cdcbe1a1abd00fc26505ffe87a31b5f3cbd
```

16/16 QA समूह सफल हुए। ZIP की CRC, हर manifest file का hash और परीक्षण किए गए सोर्स से मिलान सफल हुआ। मूल backup में बदलाव नहीं किया गया है।

यह v5.13 पर आधारित source/build kit है। Windows EXE/installer का संकलन यहाँ नहीं हुआ है। Windows build scripts और manual GitHub Actions workflow ZIP के अंदर हैं। Windows UAC/DPAPI, वास्तविक broker login तथा v5.14.0.16 पर सत्यापन बाकी है।
