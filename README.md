# Herobrine AI (NeoForge 1.21.1)

Herobrine, który się uczy. Zamiast pojawiać się losowo, obserwuje Twoją sytuację i reakcje, a potem dobiera taktykę straszenia.

## Jak "myśli"

Pętla działa na serwerze, w klasie `Director`:

1. **Obserwuje** – światło, pora dnia, czy jesteś pod ziemią, czy masz bazę w pobliżu (łóżko, piec, skrzynia, stół), ile masz pochodni, ile masz życia.
2. **Decyduje** – waży taktyki: nauczona skuteczność × dopasowanie do sytuacji × brak przyzwyczajenia.
   Dodatkowo dopasowuje intensywność do Twojego poziomu strachu: gdy mocno się boisz, odpuszcza i buduje napięcie subtelnymi zagrywkami; gdy jesteś odporny, eskaluje.
3. **Działa** – jedna z 7 taktyk.
4. **Uczy się** – mierzy Twoją reakcję (gwałtowne szarpnięcia kamery, ucieczka sprintem, odwaga/atak) i zapisuje ją w profilu gracza. Im bardziej się boisz, tym dłuższa cisza przed następnym razem.

Profil jest zapisywany razem z graczem i zostaje po śmierci.

## Taktyki

| Taktyka | Co robi |
|---|---|
| `whisper` | Dziwny dźwięk za plecami, czasem wiadomość na czacie |
| `footsteps` | Kroki zbliżają się zza pleców i nagle cichną |
| `torch_out` | Gasną pochodnie (tylko gdy nie patrzysz; wymaga `allowWorldTampering`) |
| `watcher` | Stoi w oddali i patrzy, znika gdy spojrzysz |
| `stalker` | Idzie za Tobą, ale tylko gdy na niego nie patrzysz |
| `behind` | Pojawia się tuż za plecami, znika gdy się odwrócisz |
| `rush` | Biegnie na Ciebie i znika tuż przed Tobą (krzyk + 3 s ciemności) |

Herobrine **nigdy nie zadaje obrażeń**. Uderzenie go sprawia, że znika (i liczy się jako odwaga).

## Budowanie

Wymagany Java 21.

```bash
gradle wrapper      # tylko za pierwszym razem, jeśli nie ma gradlew
./gradlew build     # gotowy mod: build/libs/herobrine_ai-1.0.0.jar
./gradlew runClient # odpala Minecrafta z modem do testów
```

Gotowy `.jar` wrzucasz do folderu `mods` w Minecraft 1.21.1 z zainstalowanym NeoForge 21.1.x.

## Testowanie (komendy dla operatora)

- `/herobrine haunt` – Herobrine sam wybiera taktykę
- `/herobrine haunt rush` – wymuś konkretną taktykę
- `/herobrine profile` – zobacz, czego się o Tobie nauczył
- `/herobrine reset` – zapomnij wszystko

Domyślnie pierwsze straszenie następuje po 8 minutach gry, a potem co 4–14 minut (zależnie od Twojego strachu).

## Konfiguracja

`config/herobrine_ai-common.toml` (po pierwszym uruchomieniu): włączanie/wyłączanie, przerwy, czy może gasić pochodnie, czy ma pisać na czacie, czy ma dawać ciemność po `rush`.

## Uwaga

Kod celuje dokładnie w **Minecraft 1.21.1 / NeoForge 21.1.x**. Nowsze wersje 1.21.x zmieniły część API (np. `hurt` → `hurtServer`).
