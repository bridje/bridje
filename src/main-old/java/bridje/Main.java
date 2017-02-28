package bridje;

import bridje.runtime.Env;
import bridje.runtime.EvalResult;
import bridje.runtime.FQSymbol;
import bridje.runtime.Var;
import org.apache.commons.cli.*;

import java.io.PrintWriter;

import static bridje.runtime.FQSymbol.fqSym;
import static bridje.runtime.NS.ns;
import static bridje.runtime.Symbol.symbol;
import static org.apache.commons.cli.HelpFormatter.*;

public class Main {


    private static final Options OPTIONS = new Options()
        .addOption(Option.builder("h").longOpt("help").hasArg(false).desc("Prints this help").build())
        .addOption(Option.builder("r").longOpt("repl").hasArg(false).desc("Starts a REPL").build())
        .addOption(Option.builder("m").longOpt("main").hasArg(true).optionalArg(false).numberOfArgs(1).argName("main-ns").build());

    private static void printHelp() {
        PrintWriter pw = new PrintWriter(System.err);
        new HelpFormatter().printHelp(pw, DEFAULT_WIDTH, "java <java-opts> bridje.Main <opts> <args>", null, OPTIONS, DEFAULT_LEFT_PAD, DEFAULT_DESC_PAD, null);
        pw.close();
    }

    public static void main(String[] args) {
        try {
            CommandLine commandLine = new DefaultParser().parse(OPTIONS, args);

            if (commandLine.hasOption('h') || !(commandLine.hasOption('m') || commandLine.hasOption('r'))) {
                printHelp();

                return;
            }

            if (commandLine.hasOption('m')) {
                String mainNsName = commandLine.getOptionValue('m');

                FQSymbol mainFn;

                int idx = mainNsName.indexOf('/');
                int lastIndex = mainNsName.lastIndexOf('/');
                if (idx != lastIndex) {
                    System.err.printf("Invalid main-ns/main-fn: '%s'%n", mainNsName);
                    return;
                } else if (idx != -1) {
                    mainFn = fqSym(ns(mainNsName.substring(0, idx)), symbol(mainNsName.substring(idx)));
                } else {
                    mainFn = fqSym(ns(mainNsName), symbol("-main"));
                }

                EvalResult requireResult = Env.eval(env -> new EvalResult(env.require(mainFn.ns), mainFn.ns));
                Var var = requireResult.env.vars.get(mainFn);

                if (var != null && var.fnMethod != null) {

                    throw new UnsupportedOperationException();
                } else {
                    System.err.printf("Can't find valid main-fn: %s/%s, expecting [Str] ", mainFn.ns.name, mainFn.symbol);
                }

                return;
            }

            if (commandLine.hasOption('r')) {
                throw new UnsupportedOperationException();
            }

        } catch (ParseException e) {
            System.err.printf("Unable to parse command-line args: %s%n", e.getMessage());
            printHelp();
        }
    }
}
