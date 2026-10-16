-- Degrau 1, bloco B: o pedido ganha uma empresa dona
-- (docs/SPEC-degrau-1.md, "Migracao dos dados ja existentes").

-- 1. Empresa padrao, com id fixo: representa quem usou o sistema antes
--    de existir o conceito de empresa. O mesmo id esta em
--    Empresa.ID_PADRAO (Java). Sem utilizador nem senha aqui de
--    proposito (seria um segredo no historico do repositorio): o passo
--    manual esta em docs/MIGRACAO-EMPRESA-PADRAO.md.
INSERT INTO empresa (id, nome, criado_em, atualizado_em)
VALUES ('00000000-0000-0000-0000-000000000001', 'Empresa padrao', now(), now());

-- 2. Coluna nova sem NOT NULL, preenchida, e so depois obrigatoria e com
--    chave estrangeira (mesma tecnica das V2/V3: nunca fica uma linha
--    antiga a violar a restricao no momento em que ela e criada).
ALTER TABLE pedido ADD COLUMN empresa_exportadora_id UUID;

UPDATE pedido SET empresa_exportadora_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE pedido ALTER COLUMN empresa_exportadora_id SET NOT NULL;

ALTER TABLE pedido ADD CONSTRAINT fk_pedido_empresa_exportadora
    FOREIGN KEY (empresa_exportadora_id) REFERENCES empresa(id);

-- 3. Indice: o bloco C vai filtrar todas as listagens por esta coluna.
CREATE INDEX ix_pedido_empresa_exportadora_id ON pedido (empresa_exportadora_id);
