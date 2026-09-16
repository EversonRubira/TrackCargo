CREATE TABLE pedido (
    id UUID PRIMARY KEY,
    numero_invoice VARCHAR(50) NOT NULL,
    cliente VARCHAR(200) NOT NULL,
    pais_destino VARCHAR(100) NOT NULL,
    porto_destino VARCHAR(100) NOT NULL,
    produto VARCHAR(100) NOT NULL,
    quantidade NUMERIC(12,2) NOT NULL,
    unidade_medida VARCHAR(10) NOT NULL,
    cia_maritima VARCHAR(100),
    numero_container VARCHAR(30),
    preco_acordado NUMERIC(14,2) NOT NULL,
    moeda VARCHAR(3) NOT NULL DEFAULT 'USD',
    estado VARCHAR(40) NOT NULL,
    pagamento_parcial_confirmado_em TIMESTAMP,
    pagamento_saldo_confirmado_em TIMESTAMP,
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_pedido_numero_invoice ON pedido (numero_invoice);

CREATE TABLE checklist_documento (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedido(id),
    tipo_documento VARCHAR(40) NOT NULL,
    enviado_em TIMESTAMP,
    aceito_em TIMESTAMP,
    CONSTRAINT uk_checklist_pedido_tipo UNIQUE (pedido_id, tipo_documento)
);

CREATE TABLE pedido_transicao (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedido(id),
    estado_anterior VARCHAR(40),
    estado_novo VARCHAR(40) NOT NULL,
    ocorrido_em TIMESTAMP NOT NULL
);

CREATE INDEX ix_pedido_transicao_pedido_id ON pedido_transicao (pedido_id);
