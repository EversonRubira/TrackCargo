# TrackCargo

Rastreamento de pedidos de exportação de commodity (proteína animal), do lado do fornecedor — com cobertura real de testes (unitário/API/E2E) e um módulo de triagem por IA.

Projeto-bandeira de portfólio para transição a QA Automation + AI Engineering, construído com Spec-Driven Development (SDD).

## Sobre o projeto

O TrackCargo acompanha o ciclo de vida completo de um pedido de exportação: da criação até a entrega, passando pelo checklist de documentos obrigatórios (invoice, packing list, bill of lading, certificado sanitário), pagamento parcial/saldo e histórico de transições de estado.

Máquina de estados do pedido:

```
criado → documentação enviada → documentação aceita →
pagamento parcial recebido → embarcado → pagamento saldo recebido →
documentos originais enviados → entregue
```

`cancelado` é possível em qualquer estado anterior a `embarcado`, nunca depois.

A recusa de um documento do checklist **nunca** altera o estado do pedido — o motivo e o reenvio ficam registrados no próprio documento.

## Stack

**Backend:** Java 21, Spring Boot 3.x, Spring Data JPA (Hibernate), PostgreSQL, Flyway (migrations), Bean Validation.

**Frontend:** React + Vite + TypeScript, Tailwind CSS v4, i18next/react-i18next.

**Testes:** JUnit 5 (unitário), REST Assured/Postman (API), GitHub Actions (CI).

**IA de triagem:** Claude API.

## Funcionalidades implementadas

- Ciclo de vida completo do pedido, com histórico de transições
- Checklist de documentos (envio, aceite, recusa com motivo)
- Pagamento parcial e saldo
- Dados logísticos (companhia marítima, número de container, incoterm, forma de pagamento)
- Numeração automática de pedidos (NNNNN/AAAA)
- Geração de PDF de status sob demanda
- Validação de campos: moeda (enum USD/EUR/BRL), quantidade e preço positivos, percentual parcial (0-100), número de container com dígito verificador ISO 6346
- **Multilinguagem completa (pt-BR / en / es)**: interface, mensagens de erro traduzidas por código, PDF de status no idioma escolhido, datas/números/moeda formatados por locale

## Como rodar localmente

### Pré-requisitos
- Java 21
- Node.js
- Docker (ou PostgreSQL 16 nativo como alternativa — ver nota abaixo)

### Banco de dados

```bash
docker compose up -d
```

> Em ambientes sem daemon Docker disponível, é possível usar um PostgreSQL 16 nativo com as mesmas credenciais/porta do `application.yml` (`export_tracking`/`export_tracking`, porta 5432) no lugar do container.

### Backend

```bash
mvn spring-boot:run
```
Sobe em `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```
Sobe em `http://localhost:5173`.

### Testes

```bash
mvn test
```

## Documentação

- [`docs/PRD.md`](docs/PRD.md) — requisitos do produto
- [`docs/SPEC.md`](docs/SPEC.md) — spec técnica (schema, endpoints, tabela critério↔teste)
- [`docs/PLAN.md`](docs/PLAN.md) — plano de implementação por fases
- [`docs/STATUS.md`](docs/STATUS.md) — estado atual e ponto de retomada

## Metodologia

Desenvolvimento guiado por spec (SDD), bloco a bloco, com branch + PR sempre (nunca commit direto na `main`), critérios de aceitação como contrato de "pronto" e exigência de evidência real de execução (não autorrelato) a cada etapa.

## Licença

*(a definir)*
