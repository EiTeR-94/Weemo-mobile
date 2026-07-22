# Weeno iOS — app native SwiftUI

Build IPA sur **GitHub Actions** (macos), gratuit. APK = build local serveur.

## Secret GitHub (optionnel)

| Secret | Exemple |
|--------|---------|
| `WINE_SERVER_URL` | `https://eiter.freeboxos.fr/wine` |

Sinon le workflow utilise déjà l’URL de prod en dur.

## Build IPA

1. **Actions** → **Build iOS IPA** → **Run workflow**
2. Release `ios-build-N` avec `WeenoOff.ipa`
3. Sur le serveur (~2 min si timer) :
   ```bash
   export WINE_MOBILE_GITHUB_REPO=EiTeR-94/Weemo-mobile
   /home/eiter/scripts/wine-mobile-sync-github.sh
   ```
4. Portail : https://eiter.freeboxos.fr/mobile/wine/

## Structure

```
native-ios/
  WineNative/Sources/   ← SwiftUI + API
  project.yml           ← XcodeGen
```
