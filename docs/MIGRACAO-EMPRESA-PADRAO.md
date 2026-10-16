# Passar os pedidos da empresa padrão para uma empresa real

Passo manual, feito **uma única vez**, depois de a migração
`V7__pedido_empresa_exportadora.sql` correr. Não é funcionalidade do
produto: é um problema de migração única (ver `docs/SPEC-degrau-1.md`,
"Migração dos dados já existentes").

## Porquê

A V7 pôs todos os pedidos que já existiam na **empresa padrão**
(`id = 00000000-0000-0000-0000-000000000001`, constante
`Empresa.ID_PADRAO`). Essa empresa não tem utilizador nenhum: a migração
não cria senha de propósito, porque ficaria um segredo no histórico do
repositório. Quando o bloco C filtrar os pedidos pela empresa de quem
está autenticado, ninguém veria esses pedidos. Este procedimento dá-lhes
uma dona com quem consegue fazer login.

Enquanto o bloco C não estiver feito, os pedidos novos também vão para a
empresa padrão (`TODO bloco C` no `PedidoController.criar`). Por isso o
ideal é correr este procedimento **depois** de o bloco C estar em
produção, para apanhar também os pedidos criados até lá. Pode ser
corrido mais de uma vez sem problema: cada vez move o que estiver na
empresa padrão nesse momento.

## Procedimento

1. Criar a conta real (cria empresa e utilizador numa só transação):

   ```bash
   curl -i -X POST http://localhost:8080/auth/registo \
     -H 'Content-Type: application/json' \
     -d '{"nomeEmpresa":"<nome da empresa>","nomeUsuario":"<o teu nome>","email":"<o teu email>","senha":"<senha>"}'
   ```

   Esperado: `201`. A senha vai só no pedido HTTP e fica guardada como
   hash; não entra em nenhum ficheiro do repositório.

2. Abrir o `psql` na base (com o `docker compose`):

   ```bash
   docker exec -it export-tracking-db psql -U export_tracking -d export_tracking
   ```

3. Mover os pedidos, numa transação, conferindo antes do `COMMIT`:

   ```sql
   BEGIN;

   SELECT empresa_exportadora_id, count(*) FROM pedido GROUP BY 1;

   UPDATE pedido
   SET empresa_exportadora_id = (SELECT empresa_id FROM usuario WHERE email = '<o teu email>')
   WHERE empresa_exportadora_id = '00000000-0000-0000-0000-000000000001';

   SELECT empresa_exportadora_id, count(*) FROM pedido GROUP BY 1;

   COMMIT;   -- ou ROLLBACK; se a contagem não bater
   ```

   O id da empresa real é lido pelo email com um subselect, para não
   copiar UUIDs à mão. Se o email estiver errado, o subselect devolve
   `NULL`, o `NOT NULL` da coluna recusa o `UPDATE` e nada muda (a
   transação fica em erro: basta `ROLLBACK;`).

   O que conferir: o total de pedidos é o mesmo nos dois `SELECT`, e no
   segundo já não aparece a empresa padrão.

## O que fica para trás

A linha da empresa padrão continua na tabela `empresa`, sem pedidos nem
utilizadores. Não a apagues: a V7 e o `EmpresaRepositoryTest` contam com
ela, e é o destino dos pedidos criados até o bloco C entrar.
