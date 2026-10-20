# Como jogar Xadrez (PlayBox Offline)

Xadrez clássico 8×8 com TODAS as regras completas, conforme implementadas
no app. Brancas começam embaixo e fazem sempre o primeiro lance.

## Objetivo
Dar **xeque-mate** no rei adversário: deixar o rei sob ataque (xeque) sem
nenhum lance legal que escape.

## Movimentos das peças
- **Peão**: anda uma casa para frente; no primeiro movimento pode andar duas.
  Captura em diagonal, uma casa. Quando chega à última linha, é promovido
  à dama, torre, bispo ou cavalo (no app, um diálogo pergunta qual peça
  você quer).
- **Cavalo**: em “L”: duas casas em uma direção e uma na perpendicular;
  salta por cima das outras peças.
- **Bispo**: qualquer distância nas diagonais.
- **Torre**: qualquer distância em linha e coluna.
- **Dama**: qualquer distância em linha, coluna ou diagonal.
- **Rei**: uma casa em qualquer direção.

## Regras especiais
- **Roque**: o rei anda duas casas para uma torre e a torre salta para o
  outro lado. Só é permitido se rei e torre nunca se moveram antes, não
  há peças entre eles, o rei não está em xeque e ele não atravessa (nem
  pousa em) casa atacada. Pequeno = para a torre do rei; grande = para a
  torre da dama.
- **En passant**: se um peão adversário anda duas casas e para ao lado do
  seu peão, você pode capturá-lo como se ele tivesse parado uma casa
  antes — só no lance seguinte.
- **Promoção**: peão que chega à última linha vira a peça escolhida.

## Empates (todos implementados)
- **Pat**: quem move não está em xeque mas não tem lance legal.
- **Regra dos 50 lances**: 50 lances de cada lado sem captura nem
  movimento de peão.
- **Material insuficiente**: só os reis, ou rei contra rei e um cavalo
  ou um bispo.
- **Repetição**: a mesma posição aparecendo três vezes na mesma partida.

## No app
- No modo “Contra o app”, você joga de brancas; o app tem três
  dificuldades (fácil, médio, difícil).
- No modo “2 jogadores”, ambos jogam no mesmo aparelho.
- Toque na peça para ver os destinos marcados (verde = movimento,
  vermelho = captura). O rei em xeque recebe um contorno vermelho.
- O histórico dos últimos lances aparece sob o tabuleiro.
