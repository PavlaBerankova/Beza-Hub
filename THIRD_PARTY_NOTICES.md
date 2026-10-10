# Použité materiály třetích stran

Zdrojový kód aplikace je pod licencí MIT (viz [LICENSE](LICENSE)). Aplikace ale obsahuje následující materiály
s vlastními licencemi. Úplné texty licencí jsou ve složce [`licenses/`](licenses).

## Fonty

| Font | Autor | Licence | Kde je |
|---|---|---|---|
| **Inter** | The Inter Project Authors (Rasmus Andersson), <https://github.com/rsms/inter> | SIL Open Font License 1.1 – [text](licenses/Inter-OFL-1.1.txt) | `app/src/main/res/font/inter.ttf` |
| **Oxanium** | The Oxanium Project Authors (Severin Meyer), <https://github.com/sevmeyer/oxanium> | SIL Open Font License 1.1 – [text](licenses/Oxanium-OFL-1.1.txt) | `app/src/main/res/font/oxanium.ttf` |

## Ikony

Většina ikon v aplikaci (spodní lišta, ovládání přehrávače, akce apod.) je z **Material Symbols** (styl Rounded)
od Googlu, <https://github.com/google/material-design-icons>, licence Apache 2.0 –
[text](licenses/Material-Symbols-Apache-2.0.txt). Jejich vektorové cesty jsou přepsané do souboru
`app/src/main/java/com/berpod/app/ui/dot/DotIcons.kt`. Několik dalších ikon (např. vizualizace vlnky v přehrávači
a „přehrává se“) je vlastní.

## Obrázky v README

Screenshoty ve složce `docs/screenshots` ukazují obálky podcastů, náhledy videí a titulky článků jejich autorů.
Slouží jen k ukázce vzhledu aplikace a nejsou součástí licence tohoto projektu.

## Knihovny

Závislosti (Jetpack Compose, Room, Media3, WorkManager, OkHttp, Coil, rssparser, android-youtube-player,
Reorderable…) se při sestavení stahují přes Gradle ze svých zdrojů, mají vlastní licence a nejsou součástí tohoto
repozitáře. Jejich seznam je v [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
