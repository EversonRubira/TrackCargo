package com.eversonrubira.exporttracking.conta;

import com.eversonrubira.exporttracking.conta.exception.EmailJaRegistadoException;
import com.eversonrubira.exporttracking.conta.exception.SenhaCurtaException;
import com.eversonrubira.exporttracking.conta.exception.SenhaLongaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unitario puro, igual ao PedidoServiceTest: repositories mockados, sem
// banco nem contexto Spring. O PasswordEncoder e o real (nao mock): o
// ponto do teste e provar o que de facto chega a base de dados.
@ExtendWith(MockitoExtension.class)
class ContaServiceTest {

    private static final String SENHA_VALIDA = "senha-forte-123";

    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private ContaService contaService;

    @BeforeEach
    void setUp() {
        contaService = new ContaService(empresaRepository, usuarioRepository, passwordEncoder);
        lenient().when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registarGravaSoOHashDaSenhaNuncaOTextoPuro() {
        contaService.registar("Exportadora A", "Ana", "ana@exportadora-a.com", SENHA_VALIDA);

        ArgumentCaptor<Usuario> gravado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(gravado.capture());
        String senhaHash = gravado.getValue().getSenhaHash();

        assertThat(senhaHash).isNotEqualTo(SENHA_VALIDA).doesNotContain(SENHA_VALIDA);
        assertThat(senhaHash).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(SENHA_VALIDA, senhaHash)).isTrue();
    }

    @Test
    void registarCriaEmpresaEUtilizadorLigadoAEla() {
        Usuario usuario = contaService.registar("Exportadora A", "Ana", "ana@exportadora-a.com", SENHA_VALIDA);

        verify(empresaRepository).save(any(Empresa.class));
        assertThat(usuario.getEmpresa().getNome()).isEqualTo("Exportadora A");
        assertThat(usuario.getNome()).isEqualTo("Ana");
    }

    @Test
    void registarGuardaEmailEmMinusculasESemEspacos() {
        Usuario usuario = contaService.registar("Exportadora A", "Ana", "  Ana@Exportadora-A.COM ", SENHA_VALIDA);

        assertThat(usuario.getEmail()).isEqualTo("ana@exportadora-a.com");
    }

    @Test
    void registarComEmailJaUsadoEmMaiusculasDiferentesERejeitado() {
        when(usuarioRepository.existsByEmail("ana@exportadora-a.com")).thenReturn(true);

        assertThatExceptionOfType(EmailJaRegistadoException.class)
                .isThrownBy(() -> contaService.registar("Outra", "Ana", "ANA@Exportadora-A.com", SENHA_VALIDA));
        verify(empresaRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void senhaComMenosDe10CaracteresERejeitadaAntesDeGravarQualquerCoisa() {
        assertThatExceptionOfType(SenhaCurtaException.class)
                .isThrownBy(() -> contaService.registar("Exportadora A", "Ana", "ana@a.com", "123456789"))
                .satisfies(ex -> assertThat(ex.getMinimo()).isEqualTo(10));
        verify(empresaRepository, never()).save(any());
    }

    @Test
    void senhaCom10CaracteresEAceite() {
        contaService.registar("Exportadora A", "Ana", "ana@a.com", "1234567890");

        verify(usuarioRepository).save(any(Usuario.class));
    }

    // 37 "ç" = 37 caracteres (passa o minimo) mas 74 bytes em UTF-8,
    // acima do limite de 72 do BCrypt.
    @Test
    void senhaAcimaDe72BytesERejeitadaEmVezDeRebentarNoBcrypt() {
        String senha = "ç".repeat(37);

        assertThatExceptionOfType(SenhaLongaException.class)
                .isThrownBy(() -> contaService.registar("Exportadora A", "Ana", "ana@a.com", senha));
        verify(usuarioRepository, never()).save(any());
    }
}
