# Verificação de Marcas e Nomes — PlayBox Offline
_Data: 18/10/2026 • Feita por/Gerida pelo agente dev • Documento interno do projeto_

> **Resumo:** disponível para decisão antes da publicação. Nomes de exibição no aplicativo podem ser trocados mudando apenas arquivos de dados/texto — o custo de refazer depois de publicar é alto (impossibilidade de reescrow a instalação). Portanto: nomes serão finalizados e verificados na Fase 6; o documento fixa a posição de hoje.

---

## 1. Termo por termo (evidência de mercado + avaliação)

| Termo | Onde usado | Evidência de colisão (busca pública) | Avaliação |
|---|---|---|---|
| **PlayBox** (nome do app) | App principal título | **Há apps já publicados na Play com o mesmo nome exato e conceito próximo**: `com.spheratech.talkplay` ("Play Box – gaming hub with games without installing"), `com.zenvarainfotech.playbox` ("PlayBox: Multi-Game App"), `com.roaa.playbox` (player), além de PlayBox HD etc. | ⚠️ **Risco alto de conflito/desaparecimento na busca.** Deve trocar de nome antes de publicar. (Em dev interno, ok provisório.) |
| **Offline** (sufixo) | Título/listagem | Descrição comum — não é marca registrável isoladamente | ✅ OK como **descritivo** (não oferece exclusividade; não é o problema) |
| **Damas** | Título do jogo no catálogo | Termo **comum/genérico** do jogo | ✅ OK — usar apenas como título do jogo, nunca como marca do app |
| **Xadrez** | Título do jogo no catálogo | Termo **comum/genérico** do jogo | ✅ OK — idem |
| **Império Imobiliário** | Título do jogo no catálogo | **Existe jogo Android do mesmo gênero publicado: "Império imobiliário ZingPlay"** (jogo de tabuleiro, marca ZingPlay). Colisão vertical forte no mesmo segmento | ⚠️ **Risco alto.** Trocar antes do lançamento |
| **Festival de Sushi** | Título do jogo no catálogo | Só uso **descritivo** (restaurantes/eventos de comida, classe 43). Nenhum jogo Android encontrado com este nome exato | 🟡 Risco baixo/pendente de consulta formal — nome **fraco/descritivo** para registro; ok para lançamento após verificação |
| **Monopoly / Banco Imobiliário** | **NÃO usar** em texto/artedo app | Marcas de terceiros (Hasbro/Estrela) | ❌ Proibido — nem em descrições que serão publicadas; narrative apenas "mecânicas clássicas" |
| **Sushi Go!** | **NÃO usar** | Marca/artes de terceiros (Gamewright) | ❌ Proibido — o app de drafting é original (nome, cartas e arte próprios) |

**Observação sobre id de pacote (`com.playbox.offline`):** package name não é nome comercial da loja; mesmo assim, o id final deve ser decidido **antes** da primeira publicação (depois não dá para mudar sem publicar app novo).

---

## 2. Ações obrigatórias antes da publicação (Fase 6)

1. **Escolher nome final do app** (sem "PlayBox"). Sugestões de partida (nomes dálacos/arbitrários, mais fortes para registro):
   - *Núcleo de Jogos* (composito claro, ainda descritivo — força de marca menor, mas sem conflito visto hoje)
   - *Baúbox* (invenção) — sugerido
   - *JogoGaveta* (composito)
   - *Clube Offline*
   - (a decisão é sua; nome definido bloqueia 2 e 3)
2. **Verificar disponibilidade de nome na Play Console** (criação de novo app — a checar manualmente, o console avisa se o nome já está em uso na loja — mas isso NÃO é verificação de marca).
3. **Buscar no INPI** (inpi.gov.br → Busca de marcas, classe 9 / 41 / 42) pelo nome final do app e pelos títulos de jogos originais usados em público.
4. **Registrar** a marca final no INPI antes do crescimento (recomendado).
5. **Definir appId** final (`com.???.??`) e aplicar.
6. **Rodar grep no projeto** antes do build final garantindo que os nomes proibidos não apareçam em nenhum recurso que será publicado (string resources, catálogo JSON, telas, README público).

---

## 3. Trigger para os jogos originais (design guardrails)

- **Império Imobiliário (ou novo nome)** — tabuleiro com layout próprio: **não** reproduzir sequência de cores/grupos, cantos (GO/cadeia/estacionamento), fileiras com estações/utilidades no mesmo padrão; nomes de ruas próprios; cartas com layout/cores próprios; peças/lidos originais.
- **Festival de Sushi (ou novo nome)** — cartas com nomes/efeitos/valores próprios; ilustrações próprias; sem copiar iconografia ou composição visual de jogos de draft comerciais existentes.
- Documentar a variante de regras adotada nas telas "Como jogar" (texto nosso).

---

## 4. Como atualizar nomes (baixo custo neste estágio)

- Nome do app: `app/src/main/res/values/strings.xml` (`app_name`) + ícone (futuro).
- Títulos dos jogos é categorias: `core/catalog/src/main/assets/catalog/catalog.json`.
- id de pacote: `app/build.gradle.kts` (`applicationId`) — **decidido antes da Play**.
