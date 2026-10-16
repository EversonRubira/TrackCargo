-- Degrau 1, bloco A: empresas e contas (docs/SPEC-degrau-1.md).
-- So as duas tabelas novas: o dono do pedido
-- (pedido.empresa_exportadora_id) e a empresa padrao para os dados
-- antigos ficam para o bloco B, numa migracao propria.

CREATE TABLE empresa (
    id UUID PRIMARY KEY,
    nome VARCHAR(200) NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL
);

CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    empresa_id UUID NOT NULL REFERENCES empresa(id),
    nome VARCHAR(200) NOT NULL,
    email VARCHAR(200) NOT NULL,
    senha_hash VARCHAR(100) NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL
);

-- Unico sem distinguir maiusculas: o indice e sobre LOWER(email), nao
-- sobre email. A aplicacao ja grava o email em minusculas
-- (Usuario.normalizarEmail), mas a garantia final fica na base, para
-- valer mesmo contra um INSERT feito a mao ou um bug futuro.
CREATE UNIQUE INDEX uk_usuario_email ON usuario (LOWER(email));
