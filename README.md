# Bloomboard

Clavier et pavé Bluetooth, plus une télécommande Android TV, en Kotlin.
L’interface suit Material 3 Expressive : touches larges, coins arrondis, couleur dynamique, bouton emoji.

English: a phone-sized Bluetooth HID keyboard and touchpad, with an Android TV remote. Pick QWERTY, AZERTY, QWERTZ, Colemak, Dvorak, or BÉPO in settings.

## Utilisation

1. Installez l’APK `universal`, ou celui qui correspond au processeur du téléphone.
2. **Clavier** : allumez le clavier, rendez le téléphone détectable, puis associez-le depuis l’ordinateur. La disposition choisie dans les réglages doit être la même que celle de l’ordinateur : les touches envoient la position physique, pas un caractère Unicode.
3. **Pavé** : glisser déplace le pointeur, un tap clique, deux doigts font défiler.
4. **Emoji** : la touche ☺ ouvre le panneau. Si la TV est connectée, l’emoji est envoyé au champ texte. Sinon il est copié sur le téléphone. Le mode Unicode Linux (réglages) tape `Ctrl+Shift+U` pour IBus.
5. **TV** : indiquez l’adresse du téléviseur, ou cherchez-le sur le réseau. L’appariement affiche un code de 6 caractères **sur votre télé**. Le certificat n’est enregistré qu’après vérification de ce code. Ensuite le pavé directionnel, le volume, la lecture et un champ texte fonctionnent à distance.

Le téléphone doit rester autorisé à tourner en arrière-plan pendant la session : une notification discrète le signale.

## Dispositions

QWERTY, AZERTY, QWERTZ, Colemak, Dvorak et BÉPO. BÉPO suit les positions ISO de la disposition xkb (touche supplémentaire `ê` à côté de Maj).

## Télécommande

Le protocole est celui du service Android TV Remote (ports 6466 et 6467, TLS, code affiché sur la télé). Bloomboard refuse le HTTP en clair. Une adresse publique déclenche un avertissement : ne continuez que si c’est votre téléviseur.

## Releases

Chaque tag `v*` lance GitHub Actions et publie :

| Fichier | Architecture |
| --- | --- |
| `*armeabi-v7a*` | téléphones 32 bits |
| `*arm64-v8a*` | téléphones et tablettes récents |
| `*x86*` | émulateur 32 bits |
| `*x86_64*` | émulateur 64 bits |
| `*universal*` | les quatre |

L’application est en Kotlin. Une bibliothèque native minuscule sert uniquement à ce que chaque APK de split corresponde à une architecture. Sans keystore dans les secrets du dépôt, l’APK de release est signé avec la clé de debug de la CI, ce qui suffit pour installer, pas pour une mise à jour par-dessus une signature de production.

Secrets du dépôt, si vous signez les releases vous-même :

- `BLOOMBOARD_KEYSTORE_BASE64`
- `BLOOMBOARD_STORE_PASSWORD`
- `BLOOMBOARD_KEY_ALIAS`
- `BLOOMBOARD_KEY_PASSWORD`

## Build

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew test assembleDebug
./gradlew assembleRelease
```

Min SDK 28. Compile SDK 37. JDK 21.
