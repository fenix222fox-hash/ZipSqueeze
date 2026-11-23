# ZipSqueeze

> **Projeto inicial:** ZipSqueeze é um protótipo Android em estágio inicial. A versão 1.0.0 comprova um fluxo básico de extração; ainda não é uma solução de produção para arquivos grandes.

## Sobre o app

O ZipSqueeze abre arquivos ZIP pelo seletor de documentos do Android, extrai as entradas em streaming para uma pasta escolhida pelo usuário e verifica o CRC de cada entrada. Também permite pré-visualizar nomes de até 250 itens e, se selecionado, excluir o ZIP de origem somente depois de uma extração concluída com sucesso.

A interface usa o Storage Access Framework (SAF), então pastas em provedores compatíveis podem ser usadas como origem e destino. O app é implementado com APIs nativas do Android e `java.util.zip`.

## Limitação principal

**Esta versão não libera progressivamente o espaço ocupado pelo ZIP original.** Ela mantém o arquivo de origem durante a extração e só oferece a opção de excluí-lo no final, após a validação. Portanto, pode ser necessário ter espaço para manter o ZIP e os arquivos extraídos ao mesmo tempo. O critério de 5 GB de ZIP com apenas 2 GB livres não está atendido.

## O que funciona nesta versão

- Selecionar ZIP e pasta de destino com os pickers SAF.
- Extrair entradas com buffer limitado de 64 KiB, sem carregar o ZIP inteiro na memória.
- Rejeitar caminhos absolutos e entradas com `..` para evitar path traversal.
- Validar CRC ao terminar cada entrada; preservar o ZIP de origem em caso de erro ou cancelamento.
- Pré-visualizar até 250 nomes de entradas e abrir ZIPs recebidos por intents Android.
- Excluir opcionalmente o ZIP original depois que todas as entradas forem extraídas e validadas.

## Limitações conhecidas

- Sem liberação incremental do espaço do ZIP, retomada após encerramento do processo, extração em segundo plano ou estimativa confiável de espaço/progresso.
- Sem suporte a senhas, ZIP AES, RAR, 7z, TAR, GZ, BZ2 ou XZ.
- Sem Shizuku, Room, WorkManager, Hilt, Compose ou testes automatizados nesta build.
- A extração não deve ser considerada segura contra ZIP bombs; evite arquivos de origem não confiável.
- O build desta workspace usa Java e as APIs do Android por causa das limitações do toolchain disponível no telefone.

## APK

A versão inicial 1.0.0 foi compilada no Termux em um dispositivo Android. Baixe-a pela [release v1.0.0](https://github.com/fenix222fox-hash/ZipSqueeze/releases/tag/v1.0.0).

## Captura enviada

![Captura do Termux durante uma tentativa inicial de instalação dos pacotes](docs/screenshots/termux-initial-build-attempt.jpg)

*Registro intermediário da configuração: a tentativa mostrada falhou ao solicitar um pacote `zipalign` inexistente no espelho usado. O script foi ajustado e a APK da release foi compilada depois dessa captura.*

## English

ZipSqueeze is an early Android ZIP extractor prototype. It streams ordinary, unencrypted ZIP entries through Android's Storage Access Framework, checks entry CRCs, blocks basic path traversal, and can delete the source archive after successful extraction.

**The initial release does not progressively reclaim the ZIP's storage blocks.** The source remains in place until extraction and CRC checks complete, so the device may need room for both the archive and extracted output. It does not meet the 5 GB archive / 2 GB free-space target. Password-protected ZIPs and RAR/7z/TAR/GZ/BZ2/XZ are unsupported; crash recovery, background work, Shizuku, ZIP-bomb protection, and automated tests are also not implemented. Treat this as an initial project, not production-ready software.

See the [v1.0.0 release](https://github.com/fenix222fox-hash/ZipSqueeze/releases/tag/v1.0.0) for the APK.
