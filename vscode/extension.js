import { spawn } from "child_process";
import { LanguageClient } from "vscode-languageclient/node.js";
import { fileURLToPath } from "url";
import { dirname } from "path";
import { window } from "vscode";

/** @param {import('vscode').ExtensionContext} context */
function activate(context) {

  const outputChannel = window.createOutputChannel("Bridje LSP");

  const serverProcess = spawn("/usr/bin/java", ["-jar", "../lsp/build/libs/bridje-lsp.jar"], {
    cwd: dirname(fileURLToPath(import.meta.url))
  });

  const serverOptions = () => Promise.resolve({
    reader: serverProcess.stdout,
    writer: serverProcess.stdin,
  });


  serverProcess.stderr.on("data", (data) => {
    outputChannel.append(data.toString());
  });

  serverProcess.on('error', (err) => {
    outputChannel.appendLine(`Spawn error: ${err.message}`);
  });

  const clientOptions = {
    documentSelector: [{ scheme: "file", language: "bridje" }],
    outputChannel: window.createOutputChannel("Bridje LSP Client"),
  };

  const client = new LanguageClient(
    "bridjeLspClient",
    "Bridje LSP Client",
    serverOptions,
    clientOptions
  );

  context.subscriptions.push(client.start());
  context.subscriptions.push(outputChannel);
}

export { activate };
