package com.gitpush.app.data;

/** AI helpers for the terminal pad and codespaces (uses the user's own AI key via AiClient). */
public final class CsAi {
    private CsAi() {}

    private static String cut(String s, int max) { return s == null ? "" : s.length() <= max ? s : s.substring(s.length() - max); }

    /** Natural language -> shell command(s) for a Linux codespace (Ubuntu, bash). */
    public static String command(Store s, String request, String repoHint) throws ApiException {
        String sys = "You write shell commands for a GitHub Codespace (Ubuntu Linux, bash, user 'codespace', no sudo password needed). "
                + "Answer with ONLY the command(s) in one ```bash fenced block, nothing else. Prefer one safe command or a short && chain. "
                + "Never use destructive commands (rm -rf on broad paths, force pushes, curl|sh) unless the user explicitly asked.";
        return AiClient.complete(s, sys, (repoHint == null || repoHint.isEmpty() ? "" : "Repository: " + repoHint + "\n") + "Task: " + request, 600);
    }

    /** Explains terminal output / an error / a command in simple words. */
    public static String explain(Store s, String text, String language) throws ApiException {
        String sys = "You explain terminal output, errors and shell commands to a developer working on a phone. Be short and concrete: "
                + "1) what it means, 2) the most likely cause, 3) the exact fix command or edit. Reply in " + (language == null ? "simple English" : language) + ".";
        return AiClient.complete(s, sys, cut(text, 6000), 900);
    }

    /** Returns corrected full source code for the given language (code block only). */
    public static String fixCode(Store s, String lang, String code, String output) throws ApiException {
        String sys = "You fix a program. Return ONLY the complete corrected " + lang + " source in one fenced code block. Keep behaviour and style; change as little as needed.";
        String user = "Code:\n" + cut(code, 8000) + "\n\nOutput/error when run:\n" + cut(output, 3000);
        return AiClient.complete(s, sys, user, 3000);
    }

    /** devcontainer.json for a repo, based on file names and key files. */
    public static String devcontainer(Store s, String repoInfo) throws ApiException {
        String sys = "You create a minimal, valid .devcontainer/devcontainer.json (JSON with comments allowed) for GitHub Codespaces. "
                + "Use image mcr.microsoft.com/devcontainers/universal:2 only if several languages are needed, otherwise a specific language image or features. "
                + "Add forwardPorts for dev servers you can detect, postCreateCommand to install dependencies, and a few useful VS Code extensions. Return ONLY the JSON in one ```json block.";
        return AiClient.complete(s, sys, cut(repoInfo, 7000), 1200);
    }
}
