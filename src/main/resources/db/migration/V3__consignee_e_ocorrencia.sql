ALTER TABLE pedido ADD COLUMN consignee VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE pedido ALTER COLUMN consignee DROP DEFAULT;

ALTER TABLE checklist_documento ADD COLUMN reaberto_em TIMESTAMP;
ALTER TABLE checklist_documento ADD COLUMN motivo_reabertura VARCHAR(500);

CREATE TABLE pedido_ocorrencia (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedido(id),
    tipo VARCHAR(40) NOT NULL,
    descricao VARCHAR(500) NOT NULL,
    ocorrido_em TIMESTAMP NOT NULL
);

CREATE INDEX ix_pedido_ocorrencia_pedido_id ON pedido_ocorrencia (pedido_id);
