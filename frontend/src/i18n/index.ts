import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import pt from './locales/pt.json'
import en from './locales/en.json'
import es from './locales/es.json'

export const IDIOMAS_SUPORTADOS = ['pt', 'en', 'es'] as const
export type Idioma = (typeof IDIOMAS_SUPORTADOS)[number]

// Recursos embutidos direto (sem backend HTTP) - i18next inicializa
// sincrono, sem precisar de Suspense pra carregar traducao.
i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      pt: { translation: pt },
      en: { translation: en },
      es: { translation: es },
    },
    fallbackLng: 'pt',
    supportedLngs: IDIOMAS_SUPORTADOS,
    nonExplicitSupportedLngs: true, // "en-US"/"pt-BR" do navegador caem em "en"/"pt"
    interpolation: { escapeValue: false }, // React ja escapa JSX, nao precisa escapar de novo
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'], // seletor manual (i18n.changeLanguage) persiste aqui tambem
    },
  })

export default i18n
