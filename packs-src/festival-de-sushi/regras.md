# Festival de Sushi — regras implementadas

Jogo original de draft de cartas do PlayBox Offline, para **2 assentos**:
você contra o **App** (IA heurística determinística) ou **2 humanos** no
mesmo aparelho. Cartas, nomes e efeitos são próprios.

## Mesa
- **3 rodadas**. Em cada rodada a mesa tem **16 cartas**, divididas em duas
  mãos de **8 cartas** (uma para cada assento), a partir da composição do
  pacote: 3 niguiri, 3 rolinho, 2 temaki, 2 mochi, 2 wasabi, 1 gengibre,
  2 chá e 1 pudim.
- Cada assento tem o **prato da rodada própria**, que pontua no fim dela;
  **pudins não entram no prato** e ficam guardados até o fim da partida.

## Lance
1. Cada assento escolhe **1 carta** da própria mão (contra o app, você escolhe
   e o App responde na hora).
2. Quando o par completa: as duas cartas **se revelam**;
   entram no prato (ou viram pudim guardado);
   as **mãos restantes trocam de lado**.
3. Repete até a mão esvaziar (8 pares por rodada). Quando esvazia:
   **fim da rodada** e há pontuação.

## Prato (pontuação da rodada, parâmetros do pacote)
- **Niguiri: 2 pontos cada.**
- **Rolinho: 2 pontos cada.** **Wasabi:** cada conjunto de um rolinho com um
  wasabi passa a valer **4** (combos limitados pelo menor entre wasabis e
  rolinhos; wasabi sem rolinho não pontua).
- **Gengibre: 3 pontos cada.**
- **Temaki:** se houver **3 temakis ou mais** (meta 3), valem **12**; menos
  que isso, 0.
- **Mochi:** cada **par completo** vale **5** (2 mochis = 5; 3 mochis = 5;
  4 mochis = 10; sobra solta = 0).
- **Chá:** cada chá vale **1 ponto por carta do prato da rodada** (contando
  os próprios chás). Dois chás num prato de 4 cartas = 8 pontos.
- O prato da rodada não fica acumulado: ele pontua e **só os totais seguem**.

## Pudim
- Pudim **não pontua na rodada** e fica em contagem separada.
- **No fim da partida:** quem tem **mais pudins** recebe **+6**; em
  empate, **cada um recebe +3**.

## Fim
Após a 3ª rodada, o placar final é a soma dos pontos de rodadas com o bônus
de pudim. Empate total é possível e termina sem campeão (o diálogo mostra
“Empate!”). “Reiniciar” zera tudo.

## Turno do App (IA heurística)
O App escolhe com prioridade fixa, em ordem (a primeira disponível vale):
1. temaki, quando falta 1 para a meta;
2. mochi, quando o prato tem quantidade ímpar (fecha o par);
3. wasabi, enquanto houver rolinho no prato sem wasabi;
4. pudim (guarda para o bônus final);
5. chá na rodada final;
6. rolinho; 7. mochi; 8. temaki; 9. niguiri; 10. gengibre;
11. primeira carta da mão.

Sem aleatoriedade: o mesmo prato e mão sempre geram a mesma escolha.
