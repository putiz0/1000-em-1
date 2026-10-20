# Império Imobiliário — regras implementadas

Jogo original do PlayBox Offline, de propriedades em um tabuleiro de 32 casas.
Cada partida tem **2 assentos**: você contra o **App** (IA simples que joga sozinha)
ou **2 humanos** no mesmo aparelho, alternando a vez.

## Mesa
- Tabuleiro de 32 casas: 20 propriedades em 8 grupos de cor (a–h), 3 casas de
  **Sorte**, 3 de **Imprevisto**, 2 cobranças de **imposto** (100 cada), a
  **Largada**, a **Prisão**, a casa **“Vá para a Prisão”** e a casa **livre**
  (Parque da Fonte).
- Cada assento começa com **caixa 1500**, na Largada.
- Movimento com **dois dados** (2–12 casas por lance).

## Turno
1. O jogador da vez **rola os dados** (para presos, veja “Prisão”).
2. Avança a soma; **cruzando ou parando na Largada recebe salário 200**.
3. A casa de chegada é resolvida (compra, aluguel, imposto, carta...).
4. **Duplo (dados iguais):** depois de resolver o que deu, o MESMO assento rola
   de novo. O **3º duplo seguido manda preso** e encerra o turno.
5. Compras abertas e cartas puxadas precisam de confirmação (“Avançar”) antes do
   próximo passo — nada acontece escondido.

## Propriedades
- Casa **sem dono**: o jogador pode **comprar** ao preço da tabela (opcional;
  sem caixa suficiente, nada acontece — não há leilão nesta versão).
- Casa **com dono** (outro assento): paga **aluguel automático** pela tabela.
  **Grupo completo e sem construção: aluguel dobrado.** Quem pisa na própria
  casa não paga nada.
- Indo preso não passa pela Largada (não recebe salário).

## Construção
- Possível **antes de rolar os dados**, clicando em “Construir”.
- Exige: o **grupo inteiro** em suas mãos, caixa >= custo da casa e nível
  abaixo do hotel.
- Cada nível custa o “custo de casa” do grupo e aumenta o aluguel na tabela
  (colunas 1–4). O **hotel é o 5º nível** (coluna 5 da tabela).
- Simplesmente: as propriedades do grupo **não exigem construção igualitária**
  nesta versão (pode construir numa só até o hotel).

## Prisão
- Quem fica preso: parar na casa “Vá para a Prisão”, carta de prisão ou
  3 duplos seguidos.
- Formas de sair (em ANTES do lance, conforme a opção):
  - **rolar** e tirar duble: sai e anda (sem novo lance por esse duplo);
  - **pagar a fiança 50** e andar normalmente;
  - **usar carta de saída da prisão** (se tiver) e andar normalmente.
- Sem duplo na tentativa: conta uma tentativa. **Na 3ª tentativa sem duplo, a
  fiança é cobrada automaticamente** e o jogador anda (se não tiver caixa,
  entram as regras de falência).
- Preso ainda **cobra aluguéis**, **paga aluguéis/impostos** e pode construir.

## Cartas (todos os 16, na ordem do pacote)
**Sorte:** 1) receber 50; 2) ir até a Largada (recebe salário 200 ao passar);
3) cartão de saída da prisão (guarda); 4) receber 25; 5) ir até o Quiosque da
Maré; 6) pagar 30; 7) receber 100; 8) ir até a casa livre.
**Imprevisto:** 1) pagar 50; 2) ir direto para a prisão; 3) pagar 40 por casa e
100 por hotel (hotel conta como 1 hotel; casas 1–4 contam por nível); 4) pagar
60; 5) receber 150; 6) ir até a Largada (com salário); 7) ir até o Imposto da
Barra (o imposto daquela casa é cobrado na chegada); 8) cartão de saída.

Cartas com “vá até” **avançam pela mesa** (recebem salário ao cruzar a Largada)
e resolvem **só a casa de destino** — não puxam outra carta.

## Falência
- **Dívida maior que a caixa:** primeiro **liquida construções** (reembolso = 
  metade do custo da casa, das mais baratas às mais caras) até cobrir.
- Se ainda faltar, **falência**: o **credor recebe as propriedades** (níveis
  zerados) e **todo o caixa restante**. Dívida com o banco (impostos/cartas):
  as propriedades voltam livres ao tabuleiro.
- Restando **um único assento não falido**, ele vence e a partida termina.
- Não há hipoteca nem venda de propriedade entre jogadores nesta versão.

## Turno do App (IA)
O App decide sozinho, com estas regras (fixas no código):
- **Compra** a propriedade aberta quando caixa >= preço + 200 (ou preço + 100
  se já tem outra propriedade do mesmo grupo);
- **Constrói** antes de rolar, do custo mais barato para o mais caro, sempre
  mantendo caixa >= 150;
- **Na prisão:** usa carta de saída; senão paga a fiança se caixa >= 250;
  senão tenta sair com duplos;
- fora disso apenas rola e avança.

## Início
Sem limite de rodadas: a partida segue até terminar em falência. “Reiniciar”
zera tudo (caixas, donos, prisões e log).
