package com.eversonrubira.exporttracking.conta;

import com.eversonrubira.exporttracking.conta.exception.EmailJaRegistadoException;
import com.eversonrubira.exporttracking.conta.exception.SenhaCurtaException;
import com.eversonrubira.exporttracking.conta.exception.SenhaLongaException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class ContaService {

    static final int SENHA_TAMANHO_MINIMO = 10;

    // Limite do proprio BCrypt: BCryptPasswordEncoder.encode() lanca
    // IllegalArgumentException ("password cannot be more than 72 bytes")
    // acima disto. Sem esta verificacao, uma senha longa viraria um 500.
    // Em bytes (UTF-8), nao em caracteres: "ç" ocupa 2.
    static final int SENHA_BYTES_MAXIMO = 72;

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public ContaService(EmpresaRepository empresaRepository,
                        UsuarioRepository usuarioRepository,
                        PasswordEncoder passwordEncoder) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Uma transacao so: se gravar o utilizador falhar, a empresa tambem
    // nao fica gravada (nunca sobra uma empresa sem ninguem).
    @Transactional
    public Usuario registar(String nomeEmpresa, String nomeUsuario, String email, String senha) {
        validarSenha(senha);

        String emailNormalizado = Usuario.normalizarEmail(email);
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new EmailJaRegistadoException(emailNormalizado);
        }

        Empresa empresa = empresaRepository.save(new Empresa(nomeEmpresa));
        String senhaHash = passwordEncoder.encode(senha);
        return usuarioRepository.save(new Usuario(empresa, nomeUsuario, emailNormalizado, senhaHash));
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(Usuario.normalizarEmail(email))
                .orElseThrow(() -> new IllegalStateException("Utilizador da sessao nao existe mais: " + email));
    }

    private void validarSenha(String senha) {
        if (senha.length() < SENHA_TAMANHO_MINIMO) {
            throw new SenhaCurtaException(SENHA_TAMANHO_MINIMO);
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > SENHA_BYTES_MAXIMO) {
            throw new SenhaLongaException(SENHA_BYTES_MAXIMO);
        }
    }
}
