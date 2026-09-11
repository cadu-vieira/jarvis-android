# JARVIS Android — base completa

Projeto Android leve, sem Android Studio obrigatório no computador do usuário.

## O que já existe
- Interface HUD escura.
- Entrada por texto.
- Reconhecimento de voz em pt-BR.
- Voz de resposta (TTS).
- Serviço de microfone em primeiro plano.
- Comandos locais básicos.
- Estrutura preparada para IA e ferramentas.
- Build automático por GitHub Actions.

## Importante
O Android impõe restrições ao uso do microfone em segundo plano. Ativar o serviço enquanto o app está visível permite que ele continue ativo depois que a tela for bloqueada, mas um app comum não pode simplesmente ligar o microfone a qualquer momento quando está totalmente em segundo plano.

A próxima camada do projeto deve conectar o núcleo de IA por um backend seguro, adicionar memória persistente, ferramentas, confirmações e integração com recursos do sistema.

NUNCA coloque uma chave de API diretamente no código ou no repositório.
