# JARVIS Android — Etapa 3 final

A escuta por "Hey Jarvis" é iniciada automaticamente depois que o microfone é autorizado.

Fluxo esperado:
1. Abrir o JARVIS.
2. Permitir o microfone.
3. O serviço de voz é iniciado automaticamente.
4. Dizer "Hey Jarvis".
5. JARVIS responde "Sim, senhor.".
6. Dizer o comando.
7. JARVIS executa e fala a resposta.
8. Depois da resposta, o Wake Word volta a ficar ativo.

O botão "ATIVAR VOZ EM SEGUNDO PLANO" continua disponível para iniciar a escuta manualmente.

O GitHub Actions baixa o Sherpa-ONNX static-link, a API Kotlin e o Kokoro INT8 oficial durante o build.
