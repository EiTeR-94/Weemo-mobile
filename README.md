# Weeno mobile — coque WebView = webapp

**Règle produit :** l’IPA et l’APK affichent **strictement** la webapp Weeno  
(`https://eiter.freeboxos.fr/wine/app`) — mêmes couleurs, mêmes étapes **1 Vin / 2 Photo / 3 Note**, même JS.

Pas d’UI native type Beer (scan EAN / Beerquest).  
Coque native minimale : WebView + cookies + caméra + deep links `/wine/join/`.

## Build

```bash
# APK (serveur)
./scripts/build-release-apk.sh --publish

# IPA (GitHub Actions → sync)
# push native-ios/** → workflow ios-ipa.yml
/home/eiter/scripts/wine-mobile-sync-github.sh
```

Portail : https://eiter.freeboxos.fr/mobile/wine/
