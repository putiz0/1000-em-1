# Como jogar Damas (PlayBox Offline)

Damas brasileiras em tabuleiro 8×8. Cada lado começa com 12 peças nas casas
escuras. Este texto descreve exatamente as regras implementadas no app.

## Objetivo
Capturar (ou bloquear) todas as peças do adversário.

## Movimento
- **Peão**: anda uma casa em diagonal, sempre para frente
  (as claras sobem; as escuras descem).
- **Dama**: anda qualquer número de casas vazio em qualquer diagonal.
  É a dama “voadora”.

## Captura
- **Peão**: captura para frente **e para trás**, saltando uma peça inimiga
  que esteja encostada, na diagonal, e pousando na casa vazia imediatamente
  depois dela.
- **Dama**: captura qualquer peça inimiga na diagonal — a qualquer distância —
  e pode pousar em qualquer casa vazia DEPOIS da peça capturada.
- A captura é **obrigatória**. Se o jogador tiver alguma captura disponível,
  não pode fazer lance simples.
- **Lei da maioria**: quando houver mais de uma captura possível, é obrigatório
  escolher a que captura o maior número de peças.
- A cadeia continua: se, depois de uma captura, a MESMA peça tiver outra
  captura disponível, é obrigatório continuar capturando com ela.
- A peça capturada sai do tabuleiro apenas no fim do lance: durante a cadeia
  ela continua como obstáculo e não pode ser saltada duas vezes.

## Coroação (promoção)
O peão que termina o lance na última linha (a de origem do adversário)
vira dama. Se o peão passar pela última linha no meio de uma cadeia de
capturas, continua a captura como peão e só coroa se PARAR lá.

## Fim de partida
- **Vitória**: o adversário fica sem peças, ou com peças mas sem nenhum
  lance legal (bloqueio total).
- **Empate**: 30 lances de cada lado (60 meios-lances seguidos) sem nenhuma
  captura em todo o tabuleiro.

## No app
- No modo “Contra o app”, você joga com as peças claras (começam embaixo)
  e o aplicativo com as escuras. Há três dificuldades (fácil, médio, difícil).
- No modo “2 jogadores”, ambos jogam no mesmo aparelho, alternando a vez.
- Toque numa peça para destacar os destinos possíveis; os destinos vermelhos
  são capturas. Tocar de novo em outro lugar desmarca a peça.
- O botão “Reiniciar” recomeça a partida com as regras e cores deste pacote.
