# PlayBox Offline

Biblioteca de jogos offline para Android: o usuário baixa **somente os jogos que quiser** e joga **sem internet**. Nome provisório do projeto (fácil de trocar em `strings.xml` + ícone antes da publicação).

> **Estado atual:** ✅ **As fases 3 e 4 estão concluídas (v0.3.0).** O app tem o motor de downloads real (catálogo remoto, pause/retomada/cancelamento, `Range`, SHA‑256, instalação atômica) **e os 5 jogos da Fase 4 implementados de verdade** — Damas, Xadrez, Dominó, Império Imobiliário e Festival de Sushi, cada um com motor próprio, tela completa, IA onde cabe, pack de dados publicado e `available: true` no catálogo. **92 testes de unidade passando (0 falhas)**, verificados nos XMLs de `build\test-results\`. Nada fictício é apresentado como instalável.

## APK compartilhável

- **Arquivo:** `dist\PlayBox-Offline-v0.3.4.apk`
- **Tamanho:** ~1,79 MB (release com R8 + shrink de recursos)
- **SHA‑256 do arquivo:** `6F6757955E2A8B5705EA9E05A6184EB443F88ACF84A6E9F1DCFCFDBEFA49DA9F`
- **versionCode 11 • versionName 0.3.4** (verificado com `aapt2 dump badging`)
- **Assinado** com o keystore local `C:\Users\mikay\playbox-dev\keys\playbox-release.jks` (verificado com `apksigner`; mesmo certificado SHA‑256 `c8cabe01…` de todas as versões anteriores, portanto instala por cima). Versões futuras **precisam** desse mesmo keystore.
- **Instalação:** Android 7.0+ — toque no arquivo → autorize "Instalar apps desconhecidos" → instalar.
- **Compartilhamento:** WhatsApp/Telegram/Drive/USB etc. Não requer Play Store nem conta.

Novidades por versão:
- **v0.3.4:** a causa real do erro **"manifest.json em formato inválido (pacote corrompido)"**: o `make-pack.ps1` gravava o `manifest.json` com marca BOM (EF BB BF), embutida nos zips do Release; o parser JSON do app rejeitava o caractere. Agora os leitores JSON do app (manifest, catálogo remoto, configs dos packs) toleram BOM, e o `make-pack.ps1` passou a gravar UTF‑8 sem BOM. **Não é preciso republicar o Release** — os zips do `packs-v1` são aceitos como estão.
- **v0.3.3:** retomada usa o tamanho real do arquivo parcial em disco (nunca estende o arquivo com zeros).
- **v0.3.2:** endereço do servidor de packs **configurável** (Ajustes → "Servidor dos jogos").

Os APKs anteriores ficam guardados em `dist\` (v0.1.0 shell+catálogo, v0.2.0 motor de downloads, v0.2.1 Damas, v0.2.2 Xadrez, v0.2.3 Dominó, v0.2.4 Império Imobiliário, v0.3.0 Festival de Sushi, v0.3.1 correção do estado "Falhou", v0.3.2 servidor configurável, v0.3.3 correção da retomada, v0.3.4 tolerância a BOM).

### Testes executados (resumo real dos XMLs)

| Suite | Testes | Resultado |
|---|---|---|
| `:core:downloads` — estados de download/pausa/retomada/verificação → 11 + downloader HTTP com `Range` (retomada pelo arquivo real, sem buraco de zeros) → 5 + verificador SHA‑256 do pack (manifest com e sem BOM) → 7 | 23 | ✅ |
| `:core:catalog` — parse do catálogo + busca/filtros | 8 | ✅ |
| `:core:gameapi` — parse do RemoteCatalog (campos futuros tolerados) | 3 | ✅ |
| `:games:damas` — motor completo → 10 + IA → 3 | 13 | ✅ |
| `:games:xadrez` — motor (com perf(3) = 8902 na geração de movimentos) → 10 + IA → 3 | 13 | ✅ |
| `:games:domino` — regras (conta pontos, jogo de mesa, batida e IA) | 11 | ✅ |
| `:games:imperio` — motor completo (rolagem/duplos/prisão, compra, aluguéis com grupo completo, construção até hotel, cartas originais com/sem puxar cadeia, falência com liquidação, IA com reservas) | 15 | ✅ |
| `:games:sushi` — motor do draft (pares revelam e mãos trocam, fechamento de rodada, bônus de pudim no fim) + IA heurística | 11 | ✅ |

**Total: 97 testes de unidade passando, 0 falhas/erros em 13 suítes** (fonte de verdade: XMLs em `*\build\test-results\testReleaseUnitTest\`). Build `assembleRelease` ✅ BUILD SUCCESSFUL; assinatura verificada ✅.

Pendente intencional: teste de download/baixar-jogar em aparelho físico (precisa do celular com `adb reverse` para o modo dev, ou do Release no GitHub + endereço em Ajustes para o modo produção).

## Catálogo (o que existe de verdade hoje)

| Jogo | Pack (zip na raiz do servidor) | Conteúdo do pack |
|---|---|---|
| Damas | `damas-1.zip` | `config.json` · `manifest.json` · `regras.md` |
| Xadrez | `xadrez-1.zip` | idem |
| Dominó | `domino-1.zip` | idem |
| Império Imobiliário | `imperio-imobiliario-1.zip` | `board.json` (tabuleiro de 32 casas, cartas, preços/aluguéis) · `manifest.json` · `regras.md` |
| Festival de Sushi | `festival-de-sushi-1.zip` | `config.json` (composição da mesa e pontuções) · `manifest.json` · `regras.md` |

Catálogo remoto: `dist\packs-site\remote-catalog.json` com os 5 packs (baseUrl `http://localhost:9080`).

## Como testar downloads no aparelho (servidor do PC)

Os apontamentos do app vão para `http://localhost:9080` (HTTP puro permitido SÓ para localhost/10.0.2.2 via `network_security_config.xml`).

1. Gere um pack: `powershell -ExecutionPolicy Bypass -File tools\make-pack.ps1 -GameId <id> -ContentDir <pasta>` — por exemplo `-GameId damas -ContentDir packs-src\damas`. Ele zipa conteúdo + `manifest.json` com SHA‑256 por arquivo em `dist\packs-site\packs\{id}-{v}.zip` e atualiza o `remote-catalog.json`.
   **Importante:** ao chamar por automação (fora de um shell aberto na pasta do projeto), passe `-OutRoot C:\1000-em-1-offline\dist\packs-site` — sem isso o script usa `$PSScriptRoot\..`, que quebra quando a pasta muda de contexto.
2. Inicie o servidor: `powershell -ExecutionPolicy Bypass -File tools\dev-server.ps1` (porta 9080, GET/HEAD/`Range` completos).
3. Conecte o celular via USB e rode `adb reverse tcp:9080 tcp:9080` — o `localhost:9080` do celular passa a morar no PC.
4. No app: detalhes do jogo → **Baixar** → progresso → **Pausar/Retomar/Cancelar** → **Jogar**.

## Packs no GitHub Releases (download sem cabo)

Canal definitivo dos packs: **Releases do repositório** `https://github.com/putiz0/1000-em-1`. O app lê o catálogo remoto de qualquer endereço configurado em **Ajustes → Servidor dos jogos**.

1. **Prepare os arquivos** a partir do site local (conferindo os hashes):
   `powershell -ExecutionPolicy Bypass -File tools\make-github-release.ps1 -ReleaseUrl "https://github.com/putiz0/1000-em-1/releases/download/packs-v1"`
   → gera `dist\packs-github\` com 6 arquivos planos: `remote-catalog.json` + os 5 zips (a ferramenta recalcula o SHA‑256 de cada zip e recusa divergência).
2. **Publique o Release** (navegador, conta `putiz0`): aba **Releases** → *Draft a new release* → tag **`packs-v1`** (nova) → título "Packs v1" → arraste os **6 arquivos** de `dist\packs-github\` → *Publish release*.
3. **No aparelho:** Ajustes → **Servidor dos jogos** → cole
   `https://github.com/putiz0/1000-em-1/releases/download/packs-v1`
   → **Salvar**. Depois: detalhes do jogo → **Baixar** — funciona só com internet (Wi‑Fi/dados), sem cabo nem servidor do PC.
4. **Ao atualizar um pack** (novo jogo ou versão nova): regenere com `make-pack.ps1`, rode o passo 1 de novo e **publique um Release novo** (`packs-v2` com os arquivos novos) e atualize o endereço no app (ou simplesmente mantenha o mesmo endereço se o número da tag não muda — cada tag tem seus arquivos fixos). Recomendação: uma tag por conjuntos de packs (`packs-v1`, `packs-v2`…).

Se o endereço no Release for servido com `Accept-Ranges` (GitHub entrega), pausa/retomada continuam do ponto; caso o servidor não suporte, o download de retomada recomeça do zero por design — nunca fica corrompido porque a assinatura SHA‑256 é verificada antes de instalar.

Nota de privacidade: só HTTPS — a mesma política `network_security_config.xml` continua válida (o app não precisa de exceção cleartext para o GitHub).

## Estrutura do projeto

```
PlayBoxOffline/
├─ app/                      Aplicativo shell (navegação, telas, despachante de jogos, contêiner manual)
├─ core/
│  ├─ catalog/               Catálogo embutido (5 jogos disponíveis) + repositório + busca
│  ├─ database/              Room (estados de instalação dos jogos)
│  ├─ gameapi/               Contratos compartilhados (PackManifest, RemoteCatalog, fonte remota)
│  ├─ downloads/             Downloader HTTP Range + verificação SHA‑256 + coordenador de estados
│  ├─ packs/                 Filesystem dos packs (files/packs/{id}/{versão}, manifest, limpeza)
│  └─ designsystem/          Tema claro/escuro, componentes e ícones
├─ games/                    módulos android-library, um por jogo
│  ├─ damas/                 motor + IA + tela
│  ├─ xadrez/                motor (movimentos/lances/UA/perft) + IA + tela
│  ├─ domino/                regras + IA + tela
│  ├─ imperio/               motor do imobiliário + IA + tela (lê board.json do pack)
│  └─ sushi/                 motor do draft + IA heurística + tela (lê config.json do pack)
├─ packs-src/                conteúdo de origem dos packs (dados + regras por jogo)
├─ tools/
│  ├─ dev-server.ps1         Servidor HTTP de packs (dev, porta 9080, Range completo)
│  └─ make-pack.ps1          Gera pack .zip + manifest + remote-catalog.json
├─ dist/                     APKs compartilháveis + site local dos packs
├─ gradle/libs.versions.toml Catálogo central de versões
└─ build.ps1                 Script de build desta máquina
```

## Stack

| Camada | Tecnologia |
|---|---|
| UI | Kotlin + Jetpack Compose (Material 3, tema dinâmico no Android 12+) |
| Persistência | Room 2.8 + DataStore Preferences |
| Rede de packs | OkHttp 5.1 (download com `Range`) + `kotlinx.serialization` |
| Navegação | androidx.navigation-compose |
| Builds | Gradle 8.14.4 + AGP 8.13.2 + Kotlin 2.2.21 + KSP |
| SDK | minSdk 24 (Android 7.0+) — targetSdk/compileSdk 36 (exigência Play a partir de 31/08/2026) |

Nota: OkHttp 5.5+ exige compileSdk 37 / AGP 9.1 — migramos para a 5.1.0 (linha 2025) para manter SDK 36; a atualização da linha fica para a fase de compatibilidade futura.

## Como compilar nesta máquina

O toolchain já está instalado em `C:\Users\mikay\playbox-dev\` (JDK 21 • SDK Android 36 • Gradle 8.14.4):

```powershell
cd C:\1000-em-1-offline
.\build.ps1                            # build padrão
.\build.ps1 assembleRelease            # APK release assinado
.\build.ps1 :games:imperio:testReleaseUnitTest    # testes de um jogo
.\build.ps1 :games:damas:testReleaseUnitTest :games:xadrez:testReleaseUnitTest :games:domino:testReleaseUnitTest :games:imperio:testReleaseUnitTest :games:sushi:testReleaseUnitTest  # suíte completa dos jogos
```

O **Android Studio** não é obrigatório para compilar; para rodar em emulador, instale o Studio ou use um aparelho Android real via `adb install`.

### Instalação no aparelho

1. Ative "Fontes desconhecidas"/Depuração USB.
2. Cole o arquivo `dist\PlayBox-Offline-v0.3.0.apk` e instale (ou `adb install dist\PlayBox-Offline-v0.3.0.apk`).
3. Para os packs no servidor de dev: conecte via USB e rode `adb reverse tcp:9080 tcp:9080`, selecione um jogo no catálogo → Baixar → Jogar.

## Estados dos jogos (mapeados 1:1 na interface)

`Aguardando download` • `Baixando` (com %) • `Download pausado` • `Download concluído` • `Atualização disponível` • `Erro no download` (com mensagem) • `Arquivo corrompido` • `Removido do dispositivo` • e, fora do banco, `Disponível para baixar` / `Em breve`.

## Propriedade intelectual (resumo do plano acordado)

- **Damas/Xadrez/Dominó:** mecânicas e nomes são domínio público; textos, arte e regras escritos do zero.
- **"Império Imobiliário"** e **"Festival de Sushi"**: criações **originais** — sem nomes, manuais, cartas, ilustrações ou trade dress de edições comerciais protegidas. Propriedades, valores, cartas e tipos de comida são invenções do projeto.
- **Downloads são DADOS (não código)** — conformance com a política Google Play: o pack baixado contém conteúdo (regras, dados, tabuleiros), nunca bytecode executável; a integridade é verificada por SHA‑256 antes de usar.
- Política sempre revisada em: https://play.google/developer-content-policy/

## Próximas etapas

1. ~~Fase 3 — motor de downloads~~ ✅ concluída (v0.2.0).
2. ~~Fase 4 — os 5 jogos~~ ✅ concluída (v0.2.1 → v0.3.0: Damas, Xadrez, Dominó, Império Imobiliário, Festival de Sushi; `available: true` no catálogo e packs publicados).
3. **Fase 5** — packs publicados no GitHub Releases (`packs-v1`, endereço configurável em Ajustes); falta: testar download/jogar no aparelho físico e os testes de qualidade/acessibilidade/modo avião.
4. **Fase 6** — publicação (AAB, keystore definitivo, checklist Google Play).
