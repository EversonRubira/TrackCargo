package com.eversonrubira.exporttracking.pedido;

// Conjunto fechado por decisao de negocio - so os 3 mercados atendidos
// hoje. Ampliar (ex: JPY, GBP) e so acrescentar valor aqui, sem tocar
// em validacao propria (o handler de enum invalido no body gera a
// lista de aceitos a partir de values()).
public enum Moeda {
    USD, EUR, BRL
}
