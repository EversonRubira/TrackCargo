package com.eversonrubira.exporttracking.conta;

import java.util.UUID;

// Empresa criada pela migracao V7 para os pedidos anteriores ao degrau 1.
// Unico sitio do Java com este id (o SQL da V7 repete-o, e o
// EmpresaRepositoryTest confirma que os dois batem).
//
// Classe so com a constante, separada da entidade Empresa (ADR-002, regra
// 1): o modulo pedido pode usar este id sem depender da entidade de conta.
// No bloco C o PedidoController deixa de a usar (passa a empresa do
// utilizador autenticado).
public final class EmpresaPadrao {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private EmpresaPadrao() {
    }
}
