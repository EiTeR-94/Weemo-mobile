# Weeno mobile — apps **natives** (pas WebView)

**Règle produit :** l’IPA et l’APK sont des clients **natifs** (UI SwiftUI / Jetpack Compose + API REST Weeno).  
Ce ne sont **pas** des coques WebView de la webapp.

| Plateforme | Stack | Entrée |
|------------|--------|--------|
| **iOS** | SwiftUI | `WineNativeApp` → `RootView` → `MainView` → `WineWizardView` |
| **Android** | Compose | `MainActivity` → `WineApp` |
| **Web / PWA** | HTML/JS | `https://eiter.freeboxos.fr/wine/app` |

Les trois parlent au **même backend** (`/wine` API).  
Features « web only » (ex. mémoire étiquettes IndexedDB v1) **ne se reportent pas** toutes seules sur IPA/APK — il faut les porter en natif.

> Note : un ancien fichier `WeenoWebShellView.swift` existe encore dans le dépôt ; **ce n’est pas** le point d’entrée de l’IPA.

## Build

**Ordre obligatoire (IPA plus longue) :**

1. **Push IPA d’abord** — `git push origin main` (paths `native-ios/**` → workflow `ios-ipa.yml` ~45 min)
2. **Puis APK** en local pendant le CI iOS — `./scripts/build-release-apk.sh --publish`
3. Quand la release GitHub est prête : `/home/eiter/scripts/wine-mobile-sync-github.sh` (ou timer)

Ne pas attendre la fin de l’APK pour pusher l’IPA.

```bash
# 1) commit + push (déclenche IPA)
git add native-ios native-android … && git commit -m "…" && git push origin main

# 2) APK (serveur, pendant le CI iOS)
./scripts/build-release-apk.sh --publish

# 3) sync IPA depuis GitHub Releases
/home/eiter/scripts/wine-mobile-sync-github.sh
```

Portail : https://eiter.freeboxos.fr/mobile/wine/
