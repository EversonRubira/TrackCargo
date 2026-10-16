package com.eversonrubira.exporttracking.conta;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

// "Como se confere uma senha". Separado do config/SecurityConfig ("que
// rotas exigem sessao") de proposito: o SecurityConfig e carregado
// tambem pelos testes @WebMvcTest antigos (via @Import no WebConfig), e
// estes beans dependem do UsuarioRepository, que esses testes nao sobem.
@Configuration
public class AutenticacaoConfig {

    // Hoje gera hashes BCrypt com prefixo "{bcrypt}". O prefixo deixa
    // trocar de algoritmo no futuro sem invalidar as senhas ja gravadas.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // Receita da documentacao do Spring Security para expor o
    // AuthenticationManager a um endpoint de login proprio (o
    // AuthController). O DaoAuthenticationProvider ja trata de duas
    // coisas que importam aqui: devolve sempre BadCredentialsException,
    // seja email inexistente ou senha errada; e, para email inexistente,
    // gasta o mesmo tempo de um BCrypt a toa, para o tempo de resposta
    // tambem nao revelar qual dos dois falhou.
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
