ALTER TABLE pedido RENAME COLUMN numero_invoice TO numero_pedido;
ALTER INDEX uk_pedido_numero_invoice RENAME TO uk_pedido_numero_pedido;

ALTER TABLE pedido ADD COLUMN porto_origem VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE pedido ALTER COLUMN porto_origem DROP DEFAULT;

ALTER TABLE pedido ADD COLUMN incoterm VARCHAR(10) NOT NULL DEFAULT 'CFR';
ALTER TABLE pedido ALTER COLUMN incoterm DROP DEFAULT;

ALTER TABLE pedido ADD COLUMN forma_pagamento VARCHAR(30) NOT NULL DEFAULT 'TT_ANTECIPADO';
ALTER TABLE pedido ALTER COLUMN forma_pagamento DROP DEFAULT;

ALTER TABLE pedido ADD COLUMN percentual_parcial NUMERIC(5,2) NOT NULL DEFAULT 30.00;
ALTER TABLE pedido ALTER COLUMN percentual_parcial DROP DEFAULT;

ALTER TABLE checklist_documento ADD COLUMN descricao VARCHAR(200);
