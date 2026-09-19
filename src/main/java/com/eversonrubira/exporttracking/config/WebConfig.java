package com.eversonrubira.exporttracking.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:5173", "https://*.app.github.dev")
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE")
                .allowedHeaders("*");
    }

    // numero_pedido pode conter "/" (padrao NNNNN/AAAA da numeracao
    // automatica) - o Tomcat rejeita %2F codificado na URL por padrao
    // (400, protecao generica contra traversal em apps que decodificam
    // e resolvem caminho de arquivo a partir da URL, o que nao e o caso
    // aqui). PASS_THROUGH repassa o "/pedidos/00001%2F2026" cru pro
    // dispatcher do Spring sem decodificar, que faz o matching de rota
    // pelos segmentos originais e so decodifica o valor de cada
    // @PathVariable depois de já ter casado o segmento - por isso nao
    // reabre a mesma falha (numero virando dois segmentos de rota).
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> permitirBarraCodificadaNaUrl() {
        return factory -> factory.addConnectorCustomizers(this::permitirBarraCodificada);
    }

    private void permitirBarraCodificada(Connector connector) {
        connector.setEncodedSolidusHandling("passthrough");
    }
}
