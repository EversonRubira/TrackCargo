package com.eversonrubira.exporttracking.config;

import com.eversonrubira.exporttracking.config.web.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import tools.jackson.databind.ObjectMapper;

// "Que rotas exigem sessao". Carregado tambem nos testes @WebMvcTest
// (via @Import no WebConfig), por isso nao depende de nada alem do
// HttpSecurity e do ObjectMapper, que esses testes ja tem.
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                // Usa a configuracao CORS do WebConfig (allowCredentials
                // incluido). Precisa estar aqui tambem: o filtro de
                // seguranca corre antes do Spring MVC, e sem isto um
                // preflight OPTIONS a /auth/me levaria 401 antes de o
                // CORS do MVC responder.
                .cors(Customizer.withDefaults())

                // TODO bloco C: ligar o CSRF (CookieCsrfTokenRepository
                // .withHttpOnlyFalse(), receita do Spring Security para
                // SPA, ver docs/SPEC-degrau-1.md) junto com o frontend
                // que reenvia o token. Desligado no bloco A porque ligado
                // ja quebraria todos os POST/PATCH dos testes antigos e o
                // frontend atual, que ainda nao manda token nenhum.
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(regras -> regras
                        .requestMatchers("/auth/me", "/auth/logout").authenticated()
                        // TODO bloco C: trocar por .authenticated() e
                        // deixar publicos so /auth/registo e /auth/login.
                        // No bloco A os endpoints existentes de pedido
                        // continuam abertos de proposito, para os 131
                        // testes antigos nao quebrarem todos de uma vez.
                        .anyRequest().permitAll())

                // Sem sessao: 401 com o mesmo corpo ErrorResponse do resto
                // da API (codigo NAO_AUTENTICADO), em vez do 403 vazio que
                // o Spring Security devolve por padrao sem formLogin.
                .exceptionHandling(erros -> erros.authenticationEntryPoint(responderNaoAutenticado(objectMapper)))

                // Logout e feito no AuthController (POST /auth/logout).
                // Desliga o /logout automatico do Spring para nao haver
                // duas rotas de logout.
                .logout(AbstractHttpConfigurer::disable)

                // O RequestCache guarda o pedido recusado para "voltar a
                // ele depois do login" num fluxo de pagina HTML com
                // redirecionamento. Numa API JSON nao serve, e criaria uma
                // sessao a cada 401.
                .requestCache(cache -> cache.disable());

        return http.build();
    }

    // numero_pedido pode ter "/" (NNNNN/AAAA), que chega na URL como %2F.
    // O Tomcat ja deixa passar (encodedSolidusHandling no WebConfig), mas
    // o Spring Security tem o seu proprio firewall (StrictHttpFirewall) que
    // recusa %2F por padrao com 400, antes de chegar ao controller. A
    // protecao existe contra ambiguidade de caminho: o mesmo recurso
    // escrito de duas formas para escapar a uma regra de acesso por URL.
    // Aqui o risco e baixo, porque nenhuma regra de acesso depende do
    // caminho de um pedido (so /auth/me e /auth/logout tem regra propria).
    // Basta ser um @Bean: o Spring Security usa-o no lugar do padrao.
    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        // 1. Aceita "%2F" na URL crua.
        firewall.setAllowUrlEncodedSlash(true);
        // 2. O firewall tambem olha o caminho ja decodificado pelo Tomcat,
        // e la um "%" costuma ser sinal de dupla codificacao. So que o
        // passthrough do Tomcat deixa o "%2F" de proposito por decodificar,
        // por isso esse "%" e esperado. Tira-se so "%" dessa lista, em vez
        // de setAllowUrlEncodedPercent(true): assim "%25" (dupla
        // codificacao, como "%252F") continua recusado na URL crua.
        firewall.getDecodedUrlBlocklist().remove("%");
        return firewall;
    }

    private AuthenticationEntryPoint responderNaoAutenticado(ObjectMapper objectMapper) {
        return (request, response, excecao) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ErrorResponse.de("NAO_AUTENTICADO", "Sessao ausente ou expirada"));
        };
    }
}
