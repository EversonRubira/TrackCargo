package com.eversonrubira.exporttracking.conta.web;

import com.eversonrubira.exporttracking.conta.ContaService;
import com.eversonrubira.exporttracking.conta.Usuario;
import com.eversonrubira.exporttracking.conta.web.dto.LoginRequest;
import com.eversonrubira.exporttracking.conta.web.dto.RegistoRequest;
import com.eversonrubira.exporttracking.conta.web.dto.UsuarioAutenticadoResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Login e logout como endpoints JSON proprios, em vez do formLogin do
// Spring Security (que espera um formulario HTML e responde com
// redirecionamentos). A verificacao da senha continua a ser do Spring
// (AuthenticationManager); aqui so se guarda o resultado na sessao.
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final ContaService contaService;
    private final AuthenticationManager authenticationManager;

    // Onde o Spring Security procura a autenticacao a cada pedido: na
    // HttpSession. Desde o Spring Security 6 guardar ai e explicito,
    // por isso o login chama saveContext() (sem isto, o login "funciona"
    // so naquele pedido e o seguinte ja chega anonimo).
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(ContaService contaService, AuthenticationManager authenticationManager) {
        this.contaService = contaService;
        this.authenticationManager = authenticationManager;
    }

    // Regista e ja deixa a sessao autenticada (spec: "devolve sessao ja
    // autenticada"), passando pelo mesmo caminho do login para so haver
    // uma forma de criar sessao.
    @PostMapping("/registo")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioAutenticadoResponse registar(@Valid @RequestBody RegistoRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        Usuario usuario = contaService.registar(request.nomeEmpresa(), request.nomeUsuario(),
                request.email(), request.senha());
        autenticarNaSessao(usuario.getEmail(), request.senha(), httpRequest, httpResponse);
        return UsuarioAutenticadoResponse.de(usuario);
    }

    // Email inexistente ou senha errada: o AuthenticationManager lanca
    // BadCredentialsException nos dois casos, e o GlobalExceptionHandler
    // responde 401 CREDENCIAIS_INVALIDAS, igual para ambos.
    @PostMapping("/login")
    public UsuarioAutenticadoResponse login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse httpResponse) {
        Authentication autenticado = autenticarNaSessao(request.email(), request.senha(), httpRequest, httpResponse);
        return UsuarioAutenticadoResponse.de(contaService.buscarPorEmail(autenticado.getName()));
    }

    // So chega aqui com sessao (SecurityConfig exige autenticacao nesta
    // rota). Invalida a HttpSession e limpa o contexto de seguranca.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication,
                       HttpServletRequest httpRequest,
                       HttpServletResponse httpResponse) {
        logoutHandler.logout(httpRequest, httpResponse, authentication);
    }

    @GetMapping("/me")
    public UsuarioAutenticadoResponse me(Authentication authentication) {
        return UsuarioAutenticadoResponse.de(contaService.buscarPorEmail(authentication.getName()));
    }

    private Authentication autenticarNaSessao(String email, String senha,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        Authentication autenticado = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, senha));

        // Protecao contra fixacao de sessao: se ja havia uma sessao antes
        // do login (id que um atacante podia ter plantado), troca o id
        // dela. Sem sessao previa, o saveContext abaixo cria uma nova.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticado);
        SecurityContextHolder.setContext(contexto);
        securityContextRepository.saveContext(contexto, httpRequest, httpResponse);
        return autenticado;
    }
}
