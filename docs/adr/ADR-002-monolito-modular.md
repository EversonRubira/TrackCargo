# ADR-002: Monólito modular, não microsserviços

**Estado:** aceite pelo Everson em 19/10/2026.

> Nota: o ADR-001 (stack, desempenho e custo) está proposto e ainda não
> foi levado para o repositório. A numeração deste fica em 002 de
> propósito, para não colidir com ele.

## Contexto

O TrackCargo vai crescer: contas e empresas, convites, comentários,
notificações, anexos e, mais tarde, rastreio automático da carga. Hoje é
um único programa Spring Boot com uma base PostgreSQL, e quem o
desenvolve é uma só pessoa. A pergunta é como organizar o crescimento
desde o início sem pagar o custo de complexidade antes de haver razão
para isso.

## Decisão

Um só programa e uma só base de dados, organizado em módulos por domínio
com fronteiras claras: `conta`, `pedido` e, quando chegarem, `convite`,
`comentario`, `notificacao`, `anexo` e `rastreio`. O pacote `config` é
transversal e não conta como módulo.

### Regras de fronteira

1. Um módulo não usa o repositório nem as entidades de outro. Fala com
   ele pelo service.
2. Entre módulos referem-se por id (por exemplo `empresa_exportadora_id`
   no pedido, sem `@ManyToOne` para a empresa).
3. Quando um módulo precisa de reagir ao que acontece noutro (um
   documento enviado gera uma notificação), usa eventos internos do
   Spring em vez de chamadas diretas. Isto só entra quando existir o
   primeiro caso real, no degrau das notificações.
4. Cada módulo é dono das suas tabelas. Só ele lê e escreve nelas.

   **Exceções à regra 4:**
   - Chaves estrangeiras entre módulos são permitidas (por exemplo
     `pedido.empresa_exportadora_id` → `empresa.id`): protegem a
     integridade dos dados sem acoplar código Java.
   - As migrações Flyway podem tocar tabelas de outro módulo (por
     exemplo a V7, que insere a empresa padrão), porque o histórico de
     migrações é um só para a base inteira.
   - Custo: se um módulo for separado mais tarde, as chaves estrangeiras
     que o ligam aos outros têm de cair.

## Porquê

- Microsserviços resolvem um problema de equipas e de escala
  independente. Aqui há uma pessoa e nenhuma carga que o justifique.
- Pedido, documentos, comentários e notificações partilham o mesmo
  estado e as mesmas transações. Separados em serviços, passam a ter
  consistência eventual, que é uma fonte de bugs difíceis de reproduzir.
- Cada serviço traria o seu deploy, a sua monitorização e a sua fatura,
  e os testes de ponta a ponta ficariam mais lentos e mais frágeis.
- Respeita o princípio Hashimoto: não se constrói nenhuma abstração
  nova. São convenções sobre o que já existe, e é o que permite separar
  um módulo mais tarde sem reescrever.

## O que custa

- As regras são convenções, garantidas por revisão de PR. Nada impede
  tecnicamente um módulo de importar o repositório de outro. Se uma
  violação aparecer na prática, é aí (e só aí) que se acrescenta um teste
  que a barre, por exemplo com ArchUnit.

  Isso já aconteceu: o `PedidoController` importava a entidade
  `conta.Empresa`. Por isso existe o `FronteiraModulosTest` (ArchUnit),
  com uma só regra: `pedido` não depende de entidades nem de
  repositórios de `conta`, e vice-versa. Se gerar ruído, remove-se.
- Um só programa significa um só deploy: uma falha pode afetar tudo.
  Aceitável nesta fase.

## Alternativas consideradas

- **Microsserviços desde já:** rejeitado pelos motivos acima.
- **Monólito sem módulos (tudo misturado):** mais simples hoje, mas cada
  degrau novo aumenta o acoplamento e torna a separação futura uma
  reescrita.

## Quando rever

Só com um motivo concreto e medido: uma integração externa que falha ou
tem picos (rastreio automático, push), mais de uma pessoa a desenvolver
em paralelo, ou uma parte do sistema que precise de escalar à parte.
Mesmo aí, o primeiro passo é um processo separado no mesmo repositório,
não um microsserviço completo.
