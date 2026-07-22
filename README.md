# Weeno mobile — IPA / APK

Apps natives **Weeno** (fork structurel de Beer mobile).

| | |
|--|--|
| **Portail** | https://eiter.freeboxos.fr/mobile/wine/ |
| **APK** | https://eiter.freeboxos.fr/mobile/wine/WeenoOff.apk |
| **IPA** | https://eiter.freeboxos.fr/mobile/wine/WeenoOff.ipa (via GitHub → sync) |
| **API** | `https://eiter.freeboxos.fr/wine/` |
| **Package Android** | `fr.eiter.plexiwine` |
| **Bundle iOS** | `fr.eiter.plexiwine` |

## Arborescence

```
wine-mobile/
├── native-android/     # Kotlin / Compose — build local serveur
├── native-ios/         # SwiftUI — build GitHub Actions (macos)
├── scripts/
│   ├── build-release-apk.sh   # APK signée dist
│   ├── build-native-ipa.sh    # IPA (Mac / CI)
│   ├── write-native-config.js
│   ├── write-mobile-versions.sh
│   └── publish-to-plexi.sh
├── web-portal/         # index.html + versions.json
└── .github/workflows/ios-ipa.yml
```

## APK (local, serveur plexi)

Comme Beer : keystore dist dans `/etc/plexi/secrets/plexi-wine-release.env`.

```bash
cd /home/eiter/wine-mobile
./scripts/build-release-apk.sh           # → dist/WeenoOff.apk
./scripts/build-release-apk.sh --publish # → /var/www/wine-mobile/
```

## IPA (GitHub Actions)

1. Créer le repo GitHub (ex. `EiTeR-94/Weemo-mobile`)
2. Pousser ce dossier sur `main`
3. Actions → **Build iOS IPA** (push `native-ios/**` ou workflow_dispatch)
4. Secret optionnel : aucun requis si `WINE_SERVER_URL` est en dur dans le workflow
5. Sur le serveur, sync :

```bash
# une fois le repo créé :
export WINE_MOBILE_GITHUB_REPO="EiTeR-94/Weemo-mobile"
/home/eiter/scripts/wine-mobile-sync-github.sh
```

Timer optionnel (comme beer-mobile-sync) :

```bash
# réutiliser le même rythme que beer si besoin
# systemctl list-timers | grep mobile
```

## Invités 4G/5G

1. Télécharger APK/IPA depuis le portail
2. Lien d’invitation Weeno → app → **Invitation** → coller → email → Activer
3. Header `X-PlexiWine-Client: native-android|native-ios` + Bearer

## Notes

- **Weeno Quest** (RPG) : stub côté app (`enabled=false`) jusqu’au backend Quest
- Domaine vin : search Vivino, scan étiquette Gemini, champs `wine_name` / `producer` / `wine_color`
- Backend : `POST /api/login` JSON requis pour owners LAN/VPN

## SideStore (même source que Beer)

URL **fixe** (déjà dans SideStore) :

```
https://raw.githubusercontent.com/EiTeR-94/Beer-mobile/main/sidestore/eb96c143657bffaa0525017bf1046b52bdb356c4e8b5c3da/source.json
```

Après la 1ʳᵉ IPA (`WeenoOff.ipa`), la source liste **Beer Quest + Weeno**.

```bash
# sync IPA depuis GitHub Releases (Weemo-mobile)
/home/eiter/scripts/wine-mobile-sync-github.sh
# régénère source multi-app
/home/eiter/scripts/beer-sidestore-source-generate.sh
```

Repo GitHub : https://github.com/EiTeR-94/Weemo-mobile
