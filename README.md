# WeenoBis mobile — IPA / APK natives

Fork de `wine-mobile` (Weeno) :

| | Weeno | **WeenoBis** |
|--|-------|--------------|
| Backend journal | `/wine/` | `/wine-bis/` |
| Scan étiquette | Gemini serveur | **Vivino vision direct téléphone** |
| Bundle / package | `fr.eiter.plexiwine` | `fr.eiter.plexiwinebis` |

## Bearer Vivino

Admin → Outils → coller Bearer (+ user id optionnel).

- **iOS** : Keychain `AfterFirstUnlockThisDeviceOnly` (pas de backup iCloud)
- **Android** : `EncryptedSharedPreferences` (AES-256-GCM via Android Keystore), migration auto depuis l’ancien prefs clair

Jamais loggé. Champ admin masqué (password). Scan = téléphone → api.vivino.com.

## Build

```bash
# APK
cd /home/eiter/wine-bis-mobile
./scripts/build-release-apk.sh --publish   # adapter publish path si besoin

# IPA : push GitHub (workflow ios-ipa)
```

Portail : https://eiter.freeboxos.fr/mobile/wine-bis/
